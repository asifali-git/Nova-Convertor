package com.nova.converter.core

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Statistics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** FFmpeg engine - EzConvert backend (any -> any conversion, trim, extract, watermark ...) */
object FfmpegEngine {

    fun run(
        command: String,
        durationMs: Long = 0L,
        onProgress: ((Float) -> Unit)? = null,
        onDone: ((Boolean, String) -> Unit)? = null
    ): Long {
        // koi bhi unexpected error (linker/native init) job ko fail kare, app crash nahi
        return try {
            // stderr ka tail — failStackTrace sirf config-exception par aata hai,
            // normal ffmpeg errors (bad flags, missing codec...) yahin se aate hain
            val errTail = StringBuilder()
            val session = FFmpegKit.executeAsync(
                command,
                { s ->
                    val code = s.returnCode
                    val ok = ReturnCode.isSuccess(code)
                    val cancelled = code != null && ReturnCode.isCancel(code)
                    val msg = when {
                        ok -> "completed"
                        cancelled -> "cancelled"
                        else -> {
                            val tail = errTail.toString().trim()
                            val detail = s.failStackTrace ?: tail.ifEmpty { "unknown error" }
                            "failed(${code?.value}): ${detail.take(500)}"
                        }
                    }
                    onDone?.invoke(ok, msg)
                },
                { log ->
                    // Level.getValue(): ffmpeg numeric level (ERROR=16, WARNING=24, INFO=32)
                    if (log.level.value <= android.util.Log.WARN) {
                        errTail.append(log.message).append('\n')
                        if (errTail.length > 800) errTail.delete(0, errTail.length - 800)
                    }
                },
                { st: Statistics ->
                    if (onProgress != null) {
                        val p = if (durationMs > 0) {
                            (st.time * 100.0 / durationMs).toFloat().coerceIn(0f, 99f)
                        } else 0f
                        onProgress(p)
                    }
                }
            )
            session.sessionId
        } catch (t: Throwable) {
            onDone?.invoke(false, "engine error: ${t.message ?: t.javaClass.simpleName}")
            -1L
        }
    }

    fun cancel(sessionId: Long) = FFmpegKit.cancel(sessionId)
    fun cancelAll() = FFmpegKit.cancel()
}

/** AI Engine - on-device ML models (RealEsrgan, etc.) */
object AiEngine {

    /** Run an AI task that reports progress via callbacks */
    fun run(
        name: String,
        onProgress: ((Float) -> Unit)? = null,
        onDone: ((Boolean, String) -> Unit)? = null,
        task: () -> Unit
    ): Long {
        return try {
            val sessionId = System.currentTimeMillis().toLong()
            Thread({
                try {
                    task()
                    onDone?.invoke(true, "completed")
                } catch (t: Throwable) {
                    onDone?.invoke(false, "failed: ${t.message ?: t.javaClass.simpleName}")
                }
            }, "nova-ai-$name").apply { isDaemon = true }.start()
            sessionId
        } catch (t: Throwable) {
            onDone?.invoke(false, "engine error: ${t.message ?: t.javaClass.simpleName}")
            -1L
        }
    }

    fun cancel(engineName: String) {
        // AI tasks check isRunning/cancel flags internally
        when (engineName) {
            "realesrgan" -> RealEsrgan.cancel()
        }
    }
}

/**
 * Task queue - SEQUENTIAL background worker (one job at a time).
 * Jobs are queued and processed one by one to avoid device overload.
 * UI shows queue position, next job auto-starts when current finishes.
 */
object Queue {

    enum class State { QUEUED, RUNNING, DONE, FAILED, CANCELLED }

    data class Task(
        val id: Int,
        val name: String,
        val command: String,
        val output: String,
        val mimeType: String,
        val durationMs: Long = 0L,
        val verb: String = "",
        val progress: Float = 0f,
        val state: State = State.QUEUED,
        val message: String = "",
        val inputStats: MediaStats? = null,
        val outputStats: MediaStats? = null,
        val procStats: ProcessingStats? = null,
        val resultStats: ResultStats? = null,
        val toolId: String = ""
    )

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val pending = LinkedHashMap<Int, Task>()
    private val sessionMap = HashMap<Int, Long>()
    private var nextId = 1
    private var appCtx: Context? = null
    private var isProcessing = false

    private val lastPubP = HashMap<Int, Float>()
    private val lastPubT = HashMap<Int, Long>()

    private fun busKey(id: Int) = "queue:$id"

    fun submit(
        name: String,
        command: String,
        output: String,
        mimeType: String,
        durationMs: Long = 0L,
        verb: String = "",
        inputStats: MediaStats? = null,
        toolId: String = ""
    ): Int {
        val id = synchronized(lock) {
            val nid = nextId++
            pending[nid] = Task(nid, name, command, output, mimeType, durationMs, verb, 
                inputStats = inputStats, toolId = toolId)
            publishLocked()
            nid
        }
        ProgressBus.update(busKey(id), name, 0f)
        tryStartNext()
        return id
    }

    /** Frontend-friendly overload: output file cache mein banta hai (parallel-safe unique path) */
    fun submit(
        context: Context,
        label: String,
        command: String,
        durationMs: Long,
        mime: String,
        outputName: String,
        verb: String = "",
        inputStats: MediaStats? = null,
        toolId: String = ""
    ): Int {
        ProgressBus.bind(context)
        appCtx = context.applicationContext
        val defaultPath = Outputs.workFile(context, outputName).absolutePath
        val claimedPath = Outputs.claimWorkFile(context, outputName).absolutePath
        val quotedDefault = "\"$defaultPath\""
        val finalCmd: String
        if (claimedPath != defaultPath && command.contains(quotedDefault)) {
            finalCmd = command.replace(quotedDefault, "\"$claimedPath\"")
        } else {
            finalCmd = command
        }
        // AI commands output path command me nahi rakhte — claimed (unique) path hamesha output
        // rahe, warna same-named inputs ek hi file par likh dete the
        val finalOut = claimedPath
        val id = submit(
            name = label,
            command = finalCmd,
            output = finalOut,
            mimeType = mime,
            durationMs = durationMs,
            verb = verb,
            inputStats = inputStats,
            toolId = toolId
        )
        ProgressBus.startService()
        return id
    }

    /** Try to start the next queued job if none is running */
    private fun tryStartNext() {
        synchronized(lock) {
            if (isProcessing) return
            val nextTask = pending.values.firstOrNull { it.state == State.QUEUED }
            nextTask?.let {
                isProcessing = true
                scope.launch { runTask(it.id) }
            }
        }
    }

    /** Run a single task sequentially */
    private fun runTask(id: Int) {
        val task = synchronized(lock) { pending[id] } ?: run { finishCurrent(); return }
        if (task.state == State.CANCELLED) { finishCurrent(); return }
        
        val sampler = ResourceSampler.get()
        val procStats = ProcessingStats(
            startTimeMs = System.currentTimeMillis(),
            estimatedTotalMs = task.durationMs,
            framesTotal = (task.inputStats?.durationMs ?: 0L) * (task.inputStats?.fps ?: 0f).toLong() / 1000
        )
        update(id) { it.copy(state = State.RUNNING, procStats = procStats) }
        
        val samplerListener: (ProcessingStats) -> Unit = { ps ->
            update(id) { it.copy(procStats = it.procStats?.copyWith(
                elapsedMs = ps.elapsedMs,
                cpuPercent = ps.cpuPercent,
                ramUsedMb = ps.ramUsedMb,
                ramPeakMb = ps.ramPeakMb,
                ramSystemFreeMb = ps.ramSystemFreeMb,
                stage = ps.stage,
                details = ps.details
            )) }
        }
        sampler.addListener(samplerListener)
        sampler.start()
        
        val isAiTask = task.command.startsWith("AI:")
        val engineName = if (isAiTask) task.command.substringAfter("AI:").substringBefore(":") else ""

        val sessionId = try {
            if (isAiTask) {
                when (engineName) {
                    "realesrgan" -> AiEngine.run(engineName,
                        onProgress = { p -> update(id) { it.copy(progress = p) } },
                        onDone = { ok, msg ->
                            sampler.removeListener(samplerListener)
                            sampler.stop()
                            finalizeTask(id, ok, msg, task.toolId)
                        },
                        task = { runRealEsrgan(id, task) }
                    )
                    else -> {
                        sampler.removeListener(samplerListener)
                        sampler.stop()
                        finalizeTask(id, false, "unknown AI engine: $engineName", task.toolId)
                        -1L
                    }
                }
            } else {
                FfmpegEngine.run(
                    command = task.command,
                    durationMs = task.durationMs,
                    onProgress = { p -> update(id) { it.copy(progress = p) } },
                    onDone = { ok, msg ->
                        sampler.removeListener(samplerListener)
                        sampler.stop()
                        finalizeTask(id, ok, msg, task.toolId)
                    }
                )
            }
        } catch (t: Throwable) {
            sampler.removeListener(samplerListener)
            sampler.stop()
            update(id) {
                it.copy(
                    state = if (it.state == State.CANCELLED) State.CANCELLED else State.FAILED,
                    message = "engine error: ${t.message ?: t.javaClass.simpleName}"
                )
            }
            return
        }
        synchronized(lock) {
            sessionMap[id] = sessionId
            if (pending[id]?.state == State.CANCELLED) {
                if (isAiTask) AiEngine.cancel(engineName) else FfmpegEngine.cancel(sessionId)
            }
        }
    }

    /** Called when current task finishes - mark done and start next */
    private fun finishCurrent() {
        synchronized(lock) {
            isProcessing = false
        }
        tryStartNext()
    }

    /** Called when task completes (success or failure) */
    private fun finalizeTask(id: Int, ok: Boolean, msg: String, toolId: String) {
        val task = synchronized(lock) { pending[id] } ?: run { finishCurrent(); return }
        val finalOk = ok && task.state != State.CANCELLED

        if (finalOk && task.output.isNotEmpty() && appCtx != null) {
            val ctx = appCtx!!
            val inputStats = task.inputStats
            scope.launch {
                try {
                    val outStats = MediaStats.probe(ctx, task.output, task.mimeType)
                    val finalProc = task.procStats?.copyWith(elapsedMs = System.currentTimeMillis() - (task.procStats?.startTimeMs ?: 0L)) ?: ProcessingStats()
                    val result = ResultStats(
                        inputStats = inputStats ?: MediaStats(path = task.command),
                        outputStats = outStats,
                        processingStats = finalProc,
                        success = true,
                        errorMessage = "",
                        toolId = toolId,
                        toolName = task.name
                    )
                    update(id) { cur ->
                        // cancel race me state CANCELLED ho chuka ho to DONE mat likho
                        if (cur.state == State.CANCELLED) cur.copy(message = "cancelled")
                        else cur.copy(
                            progress = 100f,
                            state = State.DONE,
                            message = "completed",
                            outputStats = outStats,
                            resultStats = result
                        )
                    }
                } catch (e: Exception) {
                    Log.w("Queue", "finalizeTask probe failed: $e")
                    // probe fail ho to bhi DONE mark karo (file ban chuki hai)
                    update(id) { cur ->
                        if (cur.state == State.CANCELLED) cur.copy(message = "cancelled")
                        else cur.copy(progress = 100f, state = State.DONE, message = "completed")
                    }
                }
            }
        } else {
            update(id) { cur ->
                when {
                    // user cancel ka message/STATE kabhi mat overwrite karo (failed(255) bug)
                    cur.state == State.CANCELLED -> cur.copy(message = "cancelled")
                    finalOk -> cur.copy(progress = 100f, state = State.DONE, message = "completed")
                    else -> cur.copy(state = State.FAILED, message = msg)
                }
            }
            // History/notification update() ke andar terminal state par hota hai —
            // yahan alag se recordFailure cancelled jobs ko bhi failed dikhata tha
        }
        finishCurrent()
    }

    private fun runRealEsrgan(id: Int, task: Task) {
        // Parse AI:realesrgan:input:output:scale:model:tta
        val parts = task.command.substringAfter("AI:realesrgan:").split(":")
        val input = File(parts[0])
        val output = File(parts[1])
        val scale = parts.getOrNull(2)?.toIntOrNull() ?: 4
        val model = parts.getOrNull(3) ?: RealEsrgan.MODELS.first()
        val tta = parts.getOrNull(4)?.toBoolean() ?: false
        // async worker — block karke sahi waqt par completion report karo
        val latch = java.util.concurrent.CountDownLatch(1)
        var resOk = false
        var resMsg = ""
        RealEsrgan.upscale(
            context = appCtx!!,
            input = input,
            output = output,
            scale = scale,
            model = model,
            tta = tta,
            onProgress = { p -> update(id) { it.copy(progress = p) } },
            onDone = { ok, msg, _ ->
                resOk = ok
                resMsg = msg
                latch.countDown()
            }
        )
        latch.await()
        if (!resOk) throw RuntimeException(resMsg.ifEmpty { "upscale failed" })
    }

    fun cancelAll() {
        synchronized(lock) {
            pending.values.toList().forEach { task ->
                if (task.state == State.QUEUED || task.state == State.RUNNING) {
                    pending[task.id] = task.copy(state = State.CANCELLED, message = "cancelled")
                }
            }
            publishLocked()
        }
        // pehle state CANCELLED, phir engines kill — ulta order race me FAILED likhwata tha
        sessionMap.values.toList().forEach { FfmpegEngine.cancel(it) }
        FfmpegEngine.cancelAll() // jin jobs ke session abhi bane hi nahi
        AiEngine.cancel("realesrgan")
        ProgressBus.clear()
    }

    fun cancel(id: Int) {
        update(id) { it.copy(state = State.CANCELLED, message = "cancelled") }
        val task = synchronized(lock) { pending[id] }
        if (task != null && task.command.startsWith("AI:")) {
            AiEngine.cancel(task.command.substringAfter("AI:").substringBefore(":"))
        } else {
            val session = synchronized(lock) { sessionMap[id] }
            session?.let { FfmpegEngine.cancel(it) }
        }
    }

    /** Retry a failed or cancelled job with same parameters — SAME card/id reuse */
    fun retry(id: Int): Int? {
        synchronized(lock) {
            val task = pending[id] ?: return null
            if (task.state != State.FAILED && task.state != State.CANCELLED) return null
            // naya id nahi — same card pe wapas QUEUED, taaki tool screen bhi isi id pe dekhe
            pending[id] = task.copy(
                progress = 0f,
                state = State.QUEUED,
                message = "",
                procStats = null,
                outputStats = null,
                resultStats = null
            )
            lastPubP.remove(id)
            lastPubT.remove(id)
            publishLocked()
        }
        val name = synchronized(lock) { pending[id]?.name } ?: return null
        ProgressBus.update(busKey(id), name, 0f)
        appCtx?.let { ProgressBus.startService() }
        tryStartNext()
        return id
    }

    fun clearFinished() {
        synchronized(lock) {
            pending.entries.removeAll {
                it.value.state == State.DONE || it.value.state == State.FAILED || it.value.state == State.CANCELLED
            }
            lastPubP.keys.retainAll(pending.keys)
            lastPubT.keys.retainAll(pending.keys)
            publishLocked()
        }
    }

    private fun publishLocked() {        _tasks.value = pending.values.sortedByDescending { it.id }
    }

    private fun update(id: Int, block: (Task) -> Task) {
        val task = synchronized(lock) {
            val cur = pending[id] ?: return
            val next = block(cur)
            pending[id] = next
            // publish sirf tab jab kuch visibly badla (state ya ≥1% progress ya ≥450ms)
            val stateChanged = next.state != cur.state
            val now = System.currentTimeMillis()
            val lastP = lastPubP[id]
            val lastT = lastPubT[id]
            val significant = stateChanged ||
                lastP == null ||
                kotlin.math.abs(next.progress - lastP) >= 1f ||
                lastT == null || now - lastT >= 450
            if (significant) {
                lastPubP[id] = next.progress
                lastPubT[id] = now
                publishLocked()
            }
            next
        }
        if (task.state == State.DONE || task.state == State.FAILED || task.state == State.CANCELLED) {
            ProgressBus.remove(busKey(id))
            // completion alert — sound + friendly title + file name (user cancel pe nahi)
            if (task.state != State.CANCELLED) {
                val ctx = appCtx
                if (ctx != null) {
                    val v = task.verb.ifBlank { Notifications.verbFor(ctx, null, task.name) }
                    val outName = File(task.output).name
                    if (task.state == State.DONE) {
                        Notifications.completed(ctx, v, task.output, task.mimeType)
                        // History kabhi save ko block na kare — prefs/JSON throw ho to
                        // bhi MediaStore copy honi chahiye
                        try {
                            History.recordSuccess(ctx, outName, task.output, task.mimeType, v)
                        } catch (_: Exception) {
                        }
                        // auto-save: output hamesha device (Movies/Music/Pictures → NovaConverter)
                        try {
                            Outputs.save(ctx, File(task.output), task.mimeType, outName)
                        } catch (_: Exception) {
                        }
                    } else {
                        Notifications.failed(ctx, v, task.output, task.message)
                        History.recordFailure(ctx, outName, task.output, task.mimeType, v, task.message)
                    }
                }
            }
        } else {
            ProgressBus.update(busKey(id), task.name, task.progress)
        }
    }
}

/** Output helpers - temp file + MediaStore save */
object Outputs {

    private var appContext: Context? = null
    private val claimed = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /** MainActivity onCreate mein call hota hai (Tools object ke liye) */
    fun init(context: Context) {
        appContext = context.applicationContext
        Watermark.init(appContext!!)
    }

    /** Absolute output path (bina Context) — Tools registry isse command banata hai */
    fun workFileName(name: String): String {
        val ctx = appContext ?: error("Outputs.init() not called")
        return File(ctx.cacheDir, "nova").apply { mkdirs() }.let { File(it, name).absolutePath }
    }

    fun workFile(context: Context, name: String): File {
        val dir = File(context.cacheDir, "nova")
        dir.mkdirs()
        return File(dir, name)
    }

    /**
     * Unique (claimed) output file — parallel jobs ek hi naam pe na likhen.
     * Pehli job ko saaf naam milta hai, agle clash hue to _2, _3 … lagta hai.
     */
    fun claimWorkFile(context: Context, name: String): File {
        synchronized(claimed) {
            var n = 1
            var file = workFile(context, name)
            while (file.absolutePath in claimed) {
                n++
                file = workFile(context, tag(name, n))
            }
            claimed.add(file.absolutePath)
            return file
        }
    }

    /** Claim wapas lo (jab claimed path use hi na hua ho) */
    fun releaseWorkFile(path: String) {
        synchronized(claimed) { claimed.remove(path) }
    }

    private fun tag(name: String, n: Int): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) "${name.substring(0, dot)}_$n${name.substring(dot)}"
        else "${name}_$n"
    }

    /**
     * Output file device storage me save karo — **crash-proof**.
     *
     * Pehle `contentResolver.insert()` try-block ke bahar tha. Android 8/9 (API 26–28)
     * par WRITE_EXTERNAL_STORAGE runtime permission kabhi maanga nahi jaata, isliye
     * insert `SecurityException` udata tha → Save button tap pe app crash.
     *
     * Ab: (1) pura body guarded hai, (2) kisi bhi failure par file app-specific
     * folder me save ho jaati hai (wahan koi permission chahiye hi nahi), aur
     * (3) null sirf tab milta hai jab dono hi na chalein — caller ko "Save failed".
     */
    fun save(context: Context, src: File, mimeType: String, displayName: String): Uri? {
        if (!src.exists() || src.length() == 0L) {
            android.util.Log.w("NovaOut", "skip (missing/empty): ${src.path}")
            return null
        }
        val out = try {
            saveViaMediaStore(context, src, mimeType, displayName)
                ?: saveToAppFiles(context, src, mimeType, displayName).also {
                    android.util.Log.w("NovaOut", "media store null (mime=$mimeType) → app-files=$it")
                }
        } catch (t: Throwable) {
            android.util.Log.w("NovaOut", "save threw (mime=$mimeType): $t")
            try {
                saveToAppFiles(context, src, mimeType, displayName)
            } catch (_: Throwable) {
                null
            }
        }
        android.util.Log.i("NovaOut", "saved $displayName mime=$mimeType → $out")
        return out
    }

    private fun saveViaMediaStore(context: Context, src: File, mimeType: String, displayName: String): Uri? {
        // Extension ↔ mime consistent rakho — kuch OEM Gallery apps mime se type
        // nikalte hain, aur galat mime (e.g. .bmp pe image/jpeg) pe row hide ho
        // jaati hai. Extension authoritative hai (FFmpeg ne wahi likha hai).
        val ext = displayName.substringAfterLast('.', "").lowercase()
        val mime = when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "bmp" -> "image/bmp"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> mimeType
        }
        val isVideo = mime.startsWith("video/")
        val isAudio = mime.startsWith("audio/")
        val collection = when {
            isVideo -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            isAudio -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val isQPlus = Build.VERSION.SDK_INT >= 29
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.SIZE, src.length())
            put(MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis() / 1000)
            if (isQPlus) {
                val dir = when {
                    isVideo -> Environment.DIRECTORY_MOVIES
                    isAudio -> Environment.DIRECTORY_MUSIC
                    else -> Environment.DIRECTORY_PICTURES
                }
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$dir/NovaConverter")
                // IS_PENDING protocol: row insert ke baad Gallery me "pending" rehti
                // hai jab tak bytes poore na likh jayein. Iske bina kuch devices
                // (khaas taur Images collection) adhuri row ko hide kar dete hain —
                // field run me "no image row" ka sabse likely kaaran yahi tha.
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = try {
            context.contentResolver.insert(collection, values)
        } catch (t: Throwable) {
            android.util.Log.w("NovaOut", "insert threw (mime=$mime, name=$displayName): $t")
            return null
        }
        if (uri == null) {
            android.util.Log.w("NovaOut", "insert null (mime=$mime, name=$displayName)")
            return null
        }
        return try {
            val out = context.contentResolver.openOutputStream(uri)
            if (out == null) {
                context.contentResolver.delete(uri, null, null)
                android.util.Log.w("NovaOut", "openOutputStream null: $displayName")
                return null
            }
            out.use { o -> src.inputStream().use { it.copyTo(o) } }
            if (isQPlus) {
                // pending clear — ab row Gallery me visible hoti hai
                val done = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                try {
                    context.contentResolver.update(uri, done, null, null)
                } catch (t: Throwable) {
                    android.util.Log.w("NovaOut", "clear-pending failed ($displayName): $t")
                }
            }
            uri
        } catch (e: Exception) {
            try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
            android.util.Log.w("NovaOut", "copy failed (mime=$mime): $e")
            null
        }
    }

    /**
     * Fallback — koi permission chahiye hi nahi: `Android/data/<pkg>/files/`
     * (API 19+ app-specific external storage). Permission ya MediaStore ki koi bhi
     * dikkat yahan nahi aati, isliye save hamesha success hota hai.
     */
    private fun saveToAppFiles(context: Context, src: File, mimeType: String, displayName: String): Uri? {
        return try {
            val base = when {
                mimeType.startsWith("video/") -> Environment.DIRECTORY_MOVIES
                mimeType.startsWith("audio/") -> Environment.DIRECTORY_MUSIC
                else -> Environment.DIRECTORY_PICTURES
            }
            val root = context.getExternalFilesDir(base) ?: context.filesDir
            val dir = File(root, "NovaConverter").apply { mkdirs() }
            var dest = File(dir, displayName)
            var n = 1
            while (dest.exists()) { n++; dest = File(dir, tag(displayName, n)) }
            src.copyTo(dest, overwrite = true)
            Uri.fromFile(dest)
        } catch (_: Throwable) {
            null
        }
    }
}
