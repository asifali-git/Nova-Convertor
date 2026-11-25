package com.nova.converter.features.tool

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import java.util.concurrent.atomic.AtomicReference
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.converter.R
import com.nova.converter.core.FastCompressor
import com.nova.converter.core.Files
import com.nova.converter.core.History
import com.nova.converter.core.MediaStats
import com.nova.converter.core.Notifications
import com.nova.converter.core.Outputs
import com.nova.converter.core.ProgressBus
import com.nova.converter.core.Queue
import com.nova.converter.core.RealEsrgan
import com.nova.converter.core.SpeedScale
import com.nova.converter.core.TField
import com.nova.converter.core.Tools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Tool detail — card click pe file picker AUTO khulta hai, pick karte hi
 * file auto-load ho jaati hai, phir params + RUN + realtime % progress.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ToolDetailScreen(
    toolId: String,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    initialPath: String? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val tool = remember(toolId) { Tools.byId(toolId) }

    var uri by remember { mutableStateOf<Uri?>(null) }
    var uri2 by remember { mutableStateOf<Uri?>(null) }
    var inputPath by remember { mutableStateOf<String?>(null) }
    var inputPath2 by remember { mutableStateOf<String?>(null) }
    var inputName by remember { mutableStateOf("") }
    var inputName2 by remember { mutableStateOf("") }
    var inputSize by remember { mutableStateOf(0L) }
    var loading by remember { mutableStateOf(false) }
    var durationMs by remember { mutableStateOf(0L) }
    var durationMs2 by remember { mutableStateOf(0L) }
    var thumb by remember { mutableStateOf<Bitmap?>(null) }
    var probeW by remember { mutableIntStateOf(1280) }
    var probeH by remember { mutableIntStateOf(720) }
    var hasAudioA by remember { mutableStateOf(true) }
    var hasAudioB by remember { mutableStateOf(true) }
    var probeFps by remember { mutableFloatStateOf(30f) }
    var timelineFrames by remember { mutableStateOf<List<Pair<Long, Bitmap>>>(emptyList()) }
    // seek cursor — State object bina read kiye child ko pass hota hai (drag pe screen recompose nahi)
    val previewMsState = remember { mutableLongStateOf(0L) }
    var previewMs by previewMsState

    val params = remember(toolId) {
        mutableStateMapOf<String, Any>().apply {
            tool?.fields?.forEach { f ->
                put(f.key, when (f) {
                    is TField.Chips -> f.def
                    is TField.Num -> f.def
                    is TField.TextF -> f.def
                    is TField.Slide -> f.def.toString()
                    is TField.Toggle -> f.def.toString()
                    // raw Float rakhna zaroori — string round-trip se 1.37 jaisi
                    // values 1.369999885 pe gir jaati hain aur label galat dikhta hai
                    is TField.Speed -> f.def
                })
            }
        }
    }

    // ---- run state ----
    var taskId by remember { mutableStateOf<Int?>(null) }
    var hwKey by remember { mutableStateOf("hw") }
    var hwRunning by remember { mutableStateOf(false) }
    var hwProgress by remember { mutableFloatStateOf(0f) }
    var hwDone by remember { mutableStateOf<String?>(null) }
    var hwError by remember { mutableStateOf<String?>(null) }
    var runningCmd by remember { mutableStateOf(false) }
    var doneOutput by remember { mutableStateOf<String?>(null) }
    var failMsg by remember { mutableStateOf<String?>(null) }
    var confirmCancel by remember { mutableStateOf(false) }
    var confirmDeleteResult by remember { mutableStateOf<String?>(null) }
    // native Real-ESRGAN run ho rahi hai? (cancel/progress alag handle)
    var aiRunning by remember { mutableStateOf(false) }
    var runLabel by remember { mutableStateOf("") }
    val hwCompressor = remember { androidx.compose.runtime.mutableStateOf<FastCompressor?>(null) }

    var needSecondPick by remember { mutableStateOf(false) }
    var repick by remember { mutableStateOf(false) }
    
    // ---- Batch processing state ----
    var batchInputUris by remember { mutableStateOf<List<android.net.Uri>?>(null) }
    var batchProcessing by remember { mutableStateOf(false) }
    var batchTotal by remember { mutableIntStateOf(0) }
    var batchCompleted by remember { mutableIntStateOf(0) }
    // submitted job ids — inke terminal states se hi batchCompleted count hota hai
    val batchJobIds = remember { mutableStateListOf<Int>() }

    // ---- auto load (pick hote hi file cache mein + thumbnail + probe) ----
    fun applyPrimary(path: String?, name: String, size: Long) {
        inputPath = path
        inputName = name
        inputSize = size
        loading = false
        doneOutput = null
        hwDone = null
        hwError = null
        taskId = null
        runningCmd = false
        if (path == null) {
            snackbarMessage(scope, snackbar, context.getString(R.string.err_file_load))
            return
        }
        if (tool?.id == "audio_cutter" || tool?.id == "video_cutter") {
            params["start_ms"] = "0"
            params["end_ms"] = durationMs.toString()
            previewMs = 0L
        } else if (tool?.twoInputs == true) needSecondPick = true
    }

    fun autoLoad(u: Uri, slot: Int?) {
        loading = true
        scope.launch(Dispatchers.IO) {
            val f = Files.copyToCache(context, u)
            val path = f?.absolutePath
            val size = f?.length() ?: 0L
            val wantVideoFrames = slot == null &&
                tool?.mimes?.firstOrNull()?.startsWith("video") == true
            // ek hi retriever pass: duration + dims + audio + fps (pehle 5 alag open the)
            val probe = path?.let { probeAll(it) }
            val dur = probe?.durationMs ?: 0L
            val bitmap = if (slot == null && path != null) frameAt(path, dur) else null
            val dims = (probe?.w ?: 1280) to (probe?.h ?: 720)
            val audio = probe?.hasAudio ?: false
            val fps = probe?.fps ?: 30f
            val frames = if (slot == null && path != null && dur > 0 && wantVideoFrames) {
                extractFrames(path, dur, 8)
            } else emptyList()
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                if (slot == null) {
                    probeW = dims.first; probeH = dims.second
                    hasAudioA = audio
                    durationMs = dur
                    thumb = bitmap
                    timelineFrames = frames
                    probeFps = fps
                    previewMsState.longValue = 0L
                    applyPrimary(path, Files.queryName(context, u) ?: "file", size)
                } else {
                    inputPath2 = path
                    inputName2 = Files.queryName(context, u) ?: "file"
                    durationMs2 = dur
                    hasAudioB = audio
                    loading = false
                    if (path == null) snackbarMessage(scope, snackbar, context.getString(R.string.err_file_load))
                }
            }
        }
    }

    // ---- direct load (history/metadata entry se — bina picker) ----
    fun loadFromPath(path: String) {
        loading = true
        scope.launch(Dispatchers.IO) {
            val f = File(path)
            val probe = probeAll(path)
            val dur = probe.durationMs
            val bitmap = frameAt(path, dur)
            val frames = if (dur > 0 && tool?.mimes?.firstOrNull()?.startsWith("video") == true) {
                extractFrames(path, dur, 8)
            } else emptyList()
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                probeW = probe.w; probeH = probe.h
                hasAudioA = probe.hasAudio
                durationMs = dur
                thumb = bitmap
                timelineFrames = frames
                previewMsState.longValue = 0L
                applyPrimary(path, f.name, f.length())
            }
        }
    }

    // ---- Batch submit: process multiple files sequentially via queue ----
    fun submitBatch() {
        val uris = batchInputUris ?: return
        if (uris.isEmpty()) return
        val activeTool = tool ?: return
        batchProcessing = true
        batchTotal = uris.size
        batchCompleted = 0
        batchJobIds.clear()

        scope.launch {
            for ((index, uri) in uris.withIndex()) {
                if (!batchProcessing) break // user cancelled batch

                val f = Files.copyToCache(context, uri)
                val path = f?.absolutePath ?: continue
                val size = f?.length() ?: 0L

                val probe = probeAll(path)
                val dur = probe.durationMs
                val mime = FastCompressor.guessMime(uri.toString())

                // Generate unique output name for each file
                val baseName = Tools.baseName(uri.lastPathSegment ?: "file")
                val outExt = Tools.outputFileName(activeTool, baseName, params).substringAfterLast('.', "")
                val outName = "${baseName}_${index + 1}.$outExt"

                val toolTitle = "${context.getString(activeTool.title)} (${index + 1}/${uris.size})"
                val inputStats = MediaStats.probe(context, path, mime)

                val cmd = activeTool.build(params, path, null, dur / 1000f)
                if (cmd.isNotBlank()) {
                    val jid = Queue.submit(
                        context = context,
                        label = toolTitle,
                        command = cmd,
                        durationMs = dur,
                        mime = FastCompressor.guessMime(outName.substringAfterLast('.', "")),
                        outputName = outName,
                        verb = Notifications.verbFor(context, activeTool.id),
                        inputStats = inputStats,
                        toolId = activeTool.id
                    )
                    batchJobIds.add(jid)
                }
            }
            batchProcessing = false
            batchInputUris = null
        }
    }

    // batch jobs ke terminal states → batchCompleted count (pehle kabhi increment hi nahi hota tha)
    LaunchedEffect(Unit) {
        Queue.tasks.collect { list ->
            if (batchJobIds.isEmpty()) return@collect
            val done = batchJobIds.count { id ->
                when (list.find { it.id == id }?.state) {
                    Queue.State.DONE, Queue.State.FAILED, Queue.State.CANCELLED -> true
                    else -> false
                }
            }
            batchCompleted = done
        }
    }

    // ---- auto file picker (card click ke turant baad) ----
    val pick1 = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) { uri = u; autoLoad(u, null) }
    }
    val pick2 = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) { uri2 = u; autoLoad(u, 2) }
    }
    
    // ---- Batch file picker: sirf select karo (system multi-select checkboxes).
    // Abhi RUN nahi — files list dikhegi, options set hoke user khud Run dabayega.
    val pickMultiple = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            batchInputUris = uris.toList()
            // preview ke liye pehli file load karo ("1/N files" badge ke saath)
            uri = uris[0]
            autoLoad(uris[0], null)
        }
    }

    // repick: user explicitly nayi file choose kare
    LaunchedEffect(repick) {
        if (repick && tool != null) {
            pick1.launch(tool.mimes)
            repick = false
        }
    }

    LaunchedEffect(Unit) {
        if (tool != null && inputPath == null) {
            if (initialPath != null) loadFromPath(initialPath)
            else pick1.launch(tool.mimes)
        }
    }

    // video cutter: cursor move pe frame preview → FramePreview apne scope mein karta hai

    // task terminal states → sirf state change pe recompose (per-tick progress child card handle karta hai)
    LaunchedEffect(taskId) {
        val id = taskId ?: return@LaunchedEffect
        Queue.tasks.collect { list ->
            val t = list.find { it.id == id } ?: return@collect
            when (t.state) {
                Queue.State.DONE -> {
                    doneOutput = t.output
                    failMsg = null
                    runningCmd = false
                }
                Queue.State.FAILED -> {
                    // purana success output re-open na ho — failed run ke liye clear karo
                    doneOutput = null
                    hwDone = null
                    failMsg = t.message.take(200)
                    runningCmd = false
                }
                Queue.State.CANCELLED -> {
                    doneOutput = null
                    hwDone = null
                    runningCmd = false
                }
                else -> {}
            }
        }
    }

    // auto-open: job DONE → built-in player me hi khul do (image/GIF bhi).
    // Pehle image gallery (external app) pe bhejti thi — ab koi bhi file app
    // ke bahar nahi jaati.
    val autoResult = doneOutput ?: hwDone
    LaunchedEffect(autoResult) {
        val r = autoResult ?: return@LaunchedEffect
        // failed/cancelled job ke liye output file hota hi nahi — player mat kholo
        if (!java.io.File(r).exists()) return@LaunchedEffect
        try {
            val m = FastCompressor.guessMime(r.substringAfterLast('.', ""))
            com.nova.converter.features.player.PlayerController.open(r, m)
        } catch (_: Exception) {
        }
    }

    if (tool == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.err_tool_not_found)) }
        return
    }

    val running = hwRunning || runningCmd

    /**
     * Native Real-ESRGAN upscale (ncnn + Vulkan) — FFmpeg/Queue ke bajaye direct
     * process. `image_converter` ke "ai" field se trigger hota hai.
     *
     * Completion yahi karta hai jo hardware path karta hai (notification + history
     * + auto-save), isliye result card, auto-open aur cancel sab pehle jaise hi
     * chalein.
     */
    fun runRealEsrgan(src: String, aiMode: String, tta: Boolean) {
        val scale = when (aiMode) {
            "4x", "4x-anime" -> 4
            else -> 2
        }
        val model = if (aiMode == "4x-anime") "realesrgan-x4plus-anime" else "realesrgan-x4plus"
        val outFile = Outputs.claimWorkFile(context, Tools.outputFileName(tool, src, params))
        val outMime = FastCompressor.guessMime(outFile.name.substringAfterLast('.', ""))

        // --- preflight: binary / Vulkan / model missing hai to pehle batao ---
        // (RealEsrgan.upscale ye khud bhi check karta hai, par wahan process
        //  start hone ke baad pata chalta hai — error card pehle dikhna behtar hai)
        val blocker = RealEsrgan.ready(context, model)
        if (blocker != null) {
            failMsg = null
            hwError = when (blocker) {
                "no-binary" -> context.getString(R.string.err_ai_no_binary)
                "no-vulkan" -> context.getString(R.string.err_ai_no_vulkan)
                "no-model" -> context.getString(R.string.err_ai_no_model)
                else -> context.getString(R.string.err_ai_failed)
            }
            return
        }

        failMsg = null
        hwError = null
        doneOutput = null
        hwDone = null
        hwProgress = 0f
        hwRunning = true
        runningCmd = true
        hwKey = "ai-${System.nanoTime()}"
        aiRunning = true
        runLabel = context.getString(R.string.status_converting)

        RealEsrgan.upscale(
            context = context,
            input = File(src),
            output = outFile,
            scale = scale,
            model = model,
            tta = tta,
            onProgress = { pf ->
                hwProgress = pf
                ProgressBus.update(hwKey, runLabel, pf)
            },
            onDone = { ok, msg, out ->
                aiRunning = false
                hwRunning = false
                runningCmd = false
                ProgressBus.remove(hwKey)
                if (ok && out != null && File(out).length() > 0L) {
                    hwProgress = 100f
                    hwDone = out
                    val verb = Notifications.verbFor(context, tool.id)
                    Notifications.completed(context, verb, out, outMime)
                    try { History.recordSuccess(context, File(out).name, out, outMime, verb) } catch (_: Exception) {}
                    try { Outputs.save(context, File(out), outMime, File(out).name) } catch (_: Exception) {}
                } else {
                    try { outFile.delete() } catch (_: Exception) {}
                    hwError = when (msg) {
                        "no-binary" -> context.getString(R.string.err_ai_no_binary)
                        "no-vulkan" -> context.getString(R.string.err_ai_no_vulkan)
                        "no-model" -> context.getString(R.string.err_ai_no_model)
                        else -> context.getString(R.string.err_ai_failed) + " ($msg)"
                    }
                }
            }
        )
    }

    fun runTool() {
        val p = inputPath ?: return
        val p2 = inputPath2
        if (tool.twoInputs && p2 == null) {
            snackbarMessage(scope, snackbar, context.getString(R.string.err_two_files))
            return
        }
        // callbacks (onProgress/onDone) composable ke bahar chalte hain — title
        // pehle se resolve kar lo, warna R.string Int String jagah expect karega.
        val toolTitle = context.getString(tool.title)
        // output name/mime ab runtime pe banta hai — composition mein params padhne se
        // slider/chip har tick pe poora screen recompose karta tha (scroll lag)
        val outName = Tools.outputFileName(tool, p, params)
        val outMime = FastCompressor.guessMime(outName.substringAfterLast('.', ""))
        val engine = params["engine"] as? String
        val useHardware = tool.hardware && (engine == null || engine.startsWith("Fast"))
        
        // Probe input stats for enhanced tracking
        val inputStats = MediaStats.probe(context, p, outMime)

        // command build + queue submit — koi bhi unexpected exception app crash na kare
        // (Hardware fallback bhi isi ko use karta hai)
        fun submitFFmpeg(b: String?) {
            try {
                // source size har tool ke liye chahiye (merger scale, watermark font fit)
                params["_w"] = probeW
                params["_h"] = probeH
                // has-audio flags: merger ke liye dono, aur **har** tool ke liye A —
                // speed/audio path `hasAudio` se decide karta hai ki `-filter:a
                // atempo` lage ya `-an`. Ye sirf twoInputs me set hota tha, to
                // single-input tools par default `true` chala jaata tha aur
                // silent video par FFmpeg ko atempo filter mil jata tha
                // (stream match na kare to warning/error).
                params["_aA"] = hasAudioA
                // merger params: size / duration — sirf 2-input tools
                if (tool.twoInputs) {
                    params["_dB"] = durationMs2 / 1000f
                    params["_aB"] = hasAudioB
                }
                val cmd = tool.build(params, p, b, durationMs / 1000f)
                if (cmd.isBlank()) {
                    runningCmd = false
                    hwRunning = false
                    failMsg = context.getString(R.string.err_build_cmd)
                    return
                }
                runningCmd = true
                taskId = Queue.submit(
                    context = context,
                    label = toolTitle,
                    command = cmd,
                    durationMs = durationMs,
                    mime = outMime,
                    outputName = outName,
                    verb = Notifications.verbFor(context, tool.id),
                    inputStats = inputStats,
                    toolId = tool.id
                )
            } catch (t: Throwable) {
                runningCmd = false
                hwRunning = false
                failMsg = t.message ?: t.javaClass.simpleName
            }
        }

        // ---- Native Real-ESRGAN path (image_converter + "ai" != Off) ----
        val aiMode = params["ai"] as? String
        if (tool.id == "image_converter" && aiMode != null && aiMode != "Off") {
            runRealEsrgan(p, aiMode, params["ai_tta"] == "true")
            return
        }

        if (useHardware) {
            // ---- Media3 hardware path (realtime % via ProgressHolder) ----
            hwError = null
            hwDone = null
            doneOutput = null
            failMsg = null
            hwProgress = 0f
            hwRunning = true
            runningCmd = true
            hwKey = "hw-${System.nanoTime()}"
            runLabel = ""
            val size = params["size"] as? String ?: "Medium 720p"
            val h = when (size) {
                "Small 480p" -> 480
                "Large 1080p" -> 1080
                else -> 720
            }
            val mb = (params["mb"] as? String)?.toFloatOrNull() ?: 20f
            val secs = durationMs / 1000f
            val kbps = if (secs > 0f) {
                ((mb * 8f * 1000f / secs).toInt() - 128).coerceIn(150, 16_000)
            } else 2500
            val spec = FastCompressor.Spec(videoBitrate = kbps * 1000, height = h, removeAudio = false)
            // parallel-safe unique output (do jobs ek file pe na likhen)
            val hwOut = Outputs.claimWorkFile(context, outName)
            ProgressBus.bind(context)
            ProgressBus.update(hwKey, toolTitle, 0f)
            ProgressBus.startService()
            val compressor = FastCompressor(context)
            hwCompressor.value = compressor
            try {
                compressor.compress(
                    Uri.fromFile(File(p)),
                    spec,
                    outputPath = hwOut.absolutePath,
                    onProgress = { pf ->
                        hwProgress = pf
                        ProgressBus.update(hwKey, toolTitle, pf)
                    },
                    onDone = { ok, msg, out ->
                        hwRunning = false
                        runningCmd = false
                        hwCompressor.value = null
                        if (ok && out != null && File(out).exists() && File(out).length() > 0) {
                            hwProgress = 100f
                            hwDone = out
                            val verb = Notifications.verbFor(context, tool.id)
                            val outMime2 = FastCompressor.guessMime(out.substringAfterLast('.', ""))
                            Notifications.completed(context, verb, out, outMime2)
                            try {
                                History.recordSuccess(context, File(out).name, out, outMime2, verb)
                            } catch (_: Exception) {
                            }
                            try {
                                Outputs.save(context, File(out), outMime2, File(out).name)
                            } catch (_: Exception) {
                            }
                            ProgressBus.remove(hwKey)
                        } else {
                            // ---- FFmpeg fallback: hardware encoder ne fail kiya ----
                            // (kuch codecs/sizes Media3 accept nahi karta) — tool kaam
                            // karna band na kare, software path pe switch kar do.
                            //
                            // NOTE: yahan koi snackbar/notification nahi. Pehle
                            // `err_hw_fallback` toast aata tha aur Media3 ki
                            // "hardware failed" warning ke saath user ko lagta
                            // tha conversion fail ho gaya, jabki actually FFmpeg
                            // fallback chalu tha aur job successful hoti thi —
                            // yahi field ka "1 warning" confusion tha. Ab fallback
                            // silent hai; sirf log.
                            try { hwOut.delete() } catch (_: Exception) {}
                            ProgressBus.remove(hwKey)
                            hwError = null
                            hwDone = null
                            android.util.Log.i("NovaHw", "hardware encode failed -> FFmpeg fallback")
                            submitFFmpeg(p)
                        }
                    }
                )
            } catch (t: Throwable) {
                // Media3 setup/start pe sync exception (ya user cancel race) — crash na ho,
                // hardware state saaf karke seedha FFmpeg path pe switch
                try { hwCompressor.value?.cancel() } catch (_: Throwable) {}
                hwCompressor.value = null
                hwRunning = false
                runningCmd = false
                try { hwOut.delete() } catch (_: Exception) {}
                ProgressBus.remove(hwKey)
                android.util.Log.i("NovaHw", "hardware setup failed -> FFmpeg fallback")
                submitFFmpeg(p)
}
    } else {
            // ---- FFmpeg path (parallel Queue + progress %) ----
            hwError = null
            hwDone = null
            doneOutput = null
            failMsg = null
            submitFFmpeg(p2)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(tool.title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, stringResource(R.string.cd_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(tool.subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // ---------- FILE (auto load) ----------
            when {
                loading -> FileCardLoading()
                inputPath == null -> Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EmptyFileCard {
                        pick1.launch(tool.mimes)
                    }
                    // Batch select option
                    OutlinedButton(
                        onClick = { pickMultiple.launch(tool.mimes) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Filled.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_batch_select))
                    }
                }
                else -> Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FileCard(
                        name = inputName,
                        size = inputSize,
                        durationMs = durationMs,
                        thumb = thumb,
                        mime = tool.mime,
                        onRemove = {
                            uri = null; inputPath = null; inputName = ""; thumb = null
                            durationMs = 0; inputSize = 0; doneOutput = null; taskId = null
                        }
                    )
                    if (!running && !hwRunning && doneOutput == null && hwDone == null) {
                        OutlinedButton(
                            onClick = { repick = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Filled.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.btn_change_file))
                        }
                    }
                }
            }

            // ---- Preview BEFORE work start (video player / audio controls / image) ----
            if (inputPath != null && !running && !hwRunning && doneOutput == null && hwDone == null) {
                InlinePreview(
                    path = inputPath!!,
                    thumb = thumb,
                    durationMs = durationMs,
                    batchCount = batchInputUris?.size ?: 0
                )
                // dynamic recommendation: tool + input file(s) ke hisaab se
                RecommendCard(
                    toolId = tool.id,
                    w = probeW,
                    h = probeH,
                    durationMs = durationMs,
                    sizeBytes = inputSize,
                    batchCount = batchInputUris?.size ?: 0
                )
            }

            // ---- Batch selection (abhi tak sirf picked, run nahi hua) ----
            if (batchInputUris != null && !batchProcessing && batchJobIds.isEmpty()) {
                val uris = batchInputUris!!
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.batch_selected, uris.size),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    uris.take(8).forEach { u ->
                        Text(
                            u.lastPathSegment ?: "file",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (uris.size > 8) {
                        Text(
                            "+${uris.size - 8} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { submitBatch() }) {
                            Text(stringResource(R.string.btn_run_batch, uris.size))
                        }
                        OutlinedButton(onClick = { batchInputUris = null }) {
                            Text(stringResource(R.string.btn_clear_batch))
                        }
                    }
                }
            }

            // ---- Batch progress display ----
            if (batchProcessing || batchJobIds.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        stringResource(R.string.batch_processing, batchCompleted, batchTotal),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    LinearProgressIndicator(
                        progress = { if (batchTotal > 0) batchCompleted.toFloat() / batchTotal else 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        stringResource(R.string.batch_files_queued, (batchTotal - batchCompleted).coerceAtLeast(0)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            if (batchProcessing) batchProcessing = false // sirf queueing roko
                            else { batchJobIds.clear(); batchCompleted = 0; batchTotal = 0 }
                        },
                        enabled = batchProcessing || batchJobIds.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(
                                if (batchProcessing) R.string.btn_cancel_batch
                                else R.string.btn_clear_batch
                            )
                        )
                    }
                }
            }

            if (tool.twoInputs) {
                if (inputPath2 == null) {
                    OutlinedButton(
                        onClick = { pick2.launch(tool.mimes) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.btn_pick_second)) }
                } else {
                    FileCard(
                        name = inputName2,
                        size = 0L,
                        durationMs = durationMs2,
                        thumb = null,
                        mime = tool.mime,
                        onRemove = { uri2 = null; inputPath2 = null; inputName2 = "" }
                    )
                }
            }

            // ---------- TIMELINE CUTTER (dual cursor + frame step) ----------
            if (tool.id == "audio_cutter" || tool.id == "video_cutter") {
                if (inputPath != null && durationMs > 0 && !running) {
                    TimelineCutter(
                        durationMs = durationMs,
                        isVideo = tool.id == "video_cutter",
                        fps = probeFps,
                        frames = timelineFrames,
                        path = inputPath,
                        fallback = thumb,
                        seek = previewMsState,
                        params = params,
                        onPreviewMs = { previewMs = it }
                    )
                }
            }

            // ---------- PARAMS ----------
            if (tool.fields.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(stringResource(R.string.section_options), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    tool.fields.forEach { f -> FieldEditor(f, params, durationMs) }
                }
            }

            // ---------- RUN ----------
            if (!running && !hwRunning && doneOutput == null && hwDone == null) {
                Button(
                    onClick = { runTool() },
                    enabled = inputPath != null && (inputPath2 != null || !tool.twoInputs) && !loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        if (inputPath == null) stringResource(R.string.btn_pick_file) else stringResource(R.string.btn_start),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ---------- REALTIME PROGRESS (child scope — screen doesn't recompose per tick) ----------
            if (running) {
                LiveProgressCard(
                    taskId = taskId,
                    hwProvider = { hwRunning to hwProgress },
                    label = if (runLabel.isNotBlank()) runLabel else context.getString(R.string.status_converting),
                    onCancel = { confirmCancel = true },
                    onMinimize = onBack
                )
            }

            // confirm before cancel
            if (confirmCancel) {
                com.nova.converter.ui.ConfirmDialog(
                    title = stringResource(R.string.cancel_job_title),
                    message = stringResource(R.string.cancel_job_msg),
                    confirmLabel = stringResource(R.string.cancel_job_yes),
                    onConfirm = {
                        // Real-ESRGAN native process — alag handle, in-process
                        // cancel nahi hota (Process.destroy chahiye)
                        if (aiRunning) {
                            RealEsrgan.cancel()
                            aiRunning = false
                            hwRunning = false
                            hwProgress = 0f
                            ProgressBus.remove(hwKey)
                        } else if (hwRunning) {
                            hwProgress = 0f
                            hwRunning = false
                            hwCompressor.value?.cancel()
                            hwCompressor.value = null
                            ProgressBus.remove(hwKey)
                        }
                        taskId?.let { Queue.cancel(it) }
                        runningCmd = false
                    },
                    onDismiss = { confirmCancel = false }
                )
            }

            hwError?.let { ErrorCard(it) }
            failMsg?.let { ErrorCard(it) }

            // ---------- RESULT + next steps ----------
            val result = doneOutput ?: hwDone
            if (result != null && failMsg == null) {
                val resultMime = remember(result) {
                    FastCompressor.guessMime(result.substringAfterLast('.', ""))
                }
                ResultCard(
                    path = result,
                    mime = resultMime,
                    onShare = { path, mime -> shareFile(context, path, mime) },
                    onPlay = { com.nova.converter.features.player.PlayerController.open(result, resultMime) },
                    onOpenFolder = {
                        val res = com.nova.converter.core.FileActions.openFolder(context, resultMime)
                        if (res == com.nova.converter.core.FileActions.FolderOpen.COPIED) {
                            scope.launch {
                                snackbar.showSnackbar(context.getString(R.string.folder_path_copied))
                            }
                        }
                    },
                    onDelete = { confirmDeleteResult = result }
                )

                Button(
                    onClick = { repick = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(
                        Icons.Filled.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.btn_start_new_file),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                TextButton(
                    onClick = { runTool() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.hint_run_again), style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    stringResource(R.string.hint_jobs_min),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // delete a finished result (with confirmation)
    confirmDeleteResult?.let { path ->
        com.nova.converter.ui.ConfirmDialog(
            title = stringResource(R.string.dialog_delete_file),
            message = File(path).name,
            confirmLabel = stringResource(R.string.btn_delete),
            onConfirm = {
                try { File(path).delete() } catch (_: Exception) {}
                if (doneOutput == path) doneOutput = null
                if (hwDone == path) hwDone = null
                confirmDeleteResult = null
            },
            onDismiss = { confirmDeleteResult = null }
        )
    }
}

private fun snackbarMessage(scope: kotlinx.coroutines.CoroutineScope, snackbar: SnackbarHostState, msg: String) {
    scope.launch { snackbar.showSnackbar(msg) }
}

private fun frameAt(path: String?, durationMs: Long): Bitmap? {
    if (path == null) return null
    return try {
        val r = MediaMetadataRetriever()
        r.setDataSource(path)
        val t = if (durationMs > 1000) 1_000_000L else 0L
        val b = r.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        r.release()
        b?.let { scaleBitmap(it, 720) }
    } catch (e: Exception) {
        null
    }
}

private fun frameAtMs(path: String, ms: Long): Bitmap? = try {
    val r = MediaMetadataRetriever()
    r.setDataSource(path)
    val b = r.getFrameAtTime(ms * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
    r.release()
    b?.let { scaleBitmap(it, 720) }
} catch (e: Exception) {
    null
}

/** Frame ko foran chhota kar do — full-res bitmaps (4K × 8) se memory/GC lag hota tha */
private fun scaleBitmap(src: Bitmap, maxDim: Int): Bitmap {
    val w = src.width
    val h = src.height
    val m = if (w > h) w else h
    if (m <= maxDim || m <= 0) return src
    val s = maxDim.toFloat() / m
    val nw = (w * s).toInt().coerceAtLeast(1)
    val nh = (h * s).toInt().coerceAtLeast(1)
    val out = Bitmap.createScaledBitmap(src, nw, nh, true)
    if (out != src) src.recycle()
    return out
}

private data class ProbeInfo(
    val durationMs: Long,
    val w: Int,
    val h: Int,
    val hasAudio: Boolean,
    val fps: Float
)

/** Ek hi MediaMetadataRetriever open se sab metadata (pehle 5 alag open the) */
private fun probeAll(path: String): ProbeInfo {
    var dur = 0L
    var w = 0
    var h = 0
    var rot = 0
    var audio = true
    var fps = 30f
    try {
        val r = MediaMetadataRetriever()
        r.setDataSource(path)
        dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        audio = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
        fps = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 30f
        r.release()
    } catch (e: Exception) {
    }
    val (fw, fh) = if (rot == 90 || rot == 270) h to w else w to h
    // HAS_AUDIO unsupported codec pe null deta hai (false → merger me audio gayab),
    // aur setDataSource throw ho to initial `true` galat reh jata hai ([0:a] → FAIL).
    // Audio presence isliye ffprobe se confirm karo, MMR sirf fallback.
    val ffAudio = try {
        val info = com.arthenica.ffmpegkit.FFprobeKit.getMediaInformation(path)?.mediaInformation
        val streams = info?.streams
        if (streams != null) streams.any { it.type.equals("audio", ignoreCase = true) } else null
    } catch (t: Throwable) {
        null
    }
    return ProbeInfo(
        durationMs = dur,
        w = if (fw > 0) fw else 1280,
        h = if (fh > 0) fh else 720,
        hasAudio = ffAudio ?: audio,
        fps = fps
    )
}

private fun extractFrames(path: String, durMs: Long, count: Int): List<Pair<Long, Bitmap>> {
    if (durMs <= 0) return emptyList()
    val out = ArrayList<Pair<Long, Bitmap>>(count)
    val step = durMs / count
    try {
        // ek hi retriever open — pehle har frame ke liye naya open hota tha (slow)
        val r = MediaMetadataRetriever()
        r.setDataSource(path)
        for (i in 0 until count) {
            val t = i * step
            try {
                val bb = r.getFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (bb != null) out.add(t to scaleBitmap(bb, 360))
            } catch (e: Exception) {
            }
        }
        r.release()
    } catch (e: Exception) {
    }
    return out
}

private fun shareFile(context: android.content.Context, path: String, mime: String) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, context.packageName + ".fileprovider", File(path)
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = mime
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.share_via)))
    } catch (e: Exception) {
    }
}

// ---------------- UI pieces ----------------

@Composable
private fun FileCardLoading() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        Text(stringResource(R.string.status_file_loading), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EmptyFileCard(onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.empty_tap_pick), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.empty_picker_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FileCard(
    name: String,
    size: Long,
    durationMs: Long,
    thumb: Bitmap?,
    mime: String,
    onRemove: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (thumb != null) {
                Image(
                    thumb.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    if (mime.startsWith("audio")) Icons.Filled.MusicNote else Icons.Filled.Movie,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
            val meta = buildList {
                if (size > 0) add(formatSize(size))
                if (durationMs > 0) add(formatDur(durationMs))
            }.joinToString("  •  ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.status_loaded), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_remove), tint = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * Preview BEFORE work start — video = inline player, audio = play/seek controls,
 * image = thumbnail. Fullscreen icon se PlayerController popup khulta hai.
 * Batch pick hone par pehli file ka preview + "1/N files" badge.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun InlinePreview(
    path: String,
    thumb: Bitmap?,
    durationMs: Long,
    batchCount: Int
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val mime = FastCompressor.guessMime(path.substringAfterLast('.', ""))
    val kind = when {
        mime.startsWith("video") -> "video"
        mime.startsWith("audio") -> "audio"
        else -> "image"
    }

    val player = remember(kind, path) {
        if (kind == "image") null
        else ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            playWhenReady = false
            prepare()
        }
    }
    DisposableEffect(player) {
        onDispose { player?.release() }
    }

    var playing by remember { mutableStateOf(false) }
    var posMs by remember { mutableLongStateOf(0L) }
    var durMs by remember { mutableLongStateOf(durationMs.coerceAtLeast(0L)) }

    DisposableEffect(player) {
        val p = player
        if (p != null) {
            val l = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playing = isPlaying
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY && p.duration > 0) durMs = p.duration
                }
            }
            p.addListener(l)
            onDispose { p.removeListener(l) }
        } else {
            onDispose { }
        }
    }

    LaunchedEffect(player, playing) {
        val p = player ?: return@LaunchedEffect
        while (playing) {
            posMs = p.currentPosition
            delay(400)
        }
        posMs = p.currentPosition
    }

    var imgThumb by remember(path) { mutableStateOf(thumb) }
    LaunchedEffect(path) {
        if (kind == "image" && imgThumb == null) {
            imgThumb = kotlinx.coroutines.withContext(Dispatchers.IO) { decodeSmall(path, 1280) }
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        when (kind) {
            "video" -> AndroidView(
                factory = { c ->
                    PlayerView(c).apply {
                        this.player = player
                        useController = true
                        controllerAutoShow = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    }
                },
                update = { it.player = player },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
            )

            "audio" -> Row(
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(onClick = {
                    val p = player ?: return@IconButton
                    if (p.isPlaying) p.pause() else p.play()
                }) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Slider(
                        value = if (durMs > 0) (posMs.toFloat() / durMs).coerceIn(0f, 1f) else 0f,
                        onValueChange = { f -> player?.seekTo((f * durMs).toLong()) }
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            formatDur(posMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            formatDur(durMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            else -> Box(
                Modifier
                    .fillMaxWidth()
                    .height(190.dp),
                contentAlignment = Alignment.Center
            ) {
                val im = imgThumb
                if (im != null) {
                    Image(
                        im.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Filled.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // fullscreen popup (video/audio/image sab PlayerScreen me khulte hain)
        IconButton(
            onClick = { com.nova.converter.features.player.PlayerController.open(path, mime) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
        ) {
            Icon(
                Icons.Filled.Fullscreen,
                contentDescription = stringResource(R.string.preview_fullscreen),
                tint = Color.White
            )
        }

        if (batchCount > 1) {
            Text(
                stringResource(R.string.preview_badge, 1, batchCount),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

/** Scaled-down bitmap decode (image preview ke liye) */
private fun decodeSmall(path: String, maxDim: Int): Bitmap? = try {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxDim * 2 || bounds.outHeight / sample > maxDim * 2) sample *= 2
    android.graphics.BitmapFactory.decodeFile(
        path,
        android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
} catch (_: Exception) {
    null
}

/** Recommended settings — tool + selected file(s) ke hisaab se dynamic hint card */
@Composable
private fun RecommendCard(
    toolId: String,
    w: Int,
    h: Int,
    durationMs: Long,
    sizeBytes: Long,
    batchCount: Int
) {
    val inputInfo = buildList {
        if (sizeBytes > 0) add(formatSize(sizeBytes))
        if (durationMs > 0) add(formatDur(durationMs))
    }.joinToString("  •  ")
    val resInfo = if (w > 0 && h > 0) "${w}\u00d7$h" else ""

    val body = when (toolId) {
        "video_compressor", "video_converter", "video_to_gif", "video_speed", "frame_capture" -> {
            val sug = when {
                h >= 2000 -> stringResource(R.string.sug_4k)
                h >= 1000 -> stringResource(R.string.sug_1080)
                else -> stringResource(R.string.sug_low)
            }
            stringResource(
                R.string.rec_video,
                resInfo.ifBlank { "\u2014" },
                inputInfo.ifBlank { "\u2014" },
                sug
            )
        }

        "audio_converter", "video_to_audio", "audio_cutter", "audio_speed", "audio_merger" ->
            stringResource(R.string.rec_audio, inputInfo.ifBlank { "\u2014" })

        "image_converter" -> stringResource(
            R.string.rec_image,
            listOfNotNull(
                resInfo.ifBlank { null },
                formatSize(sizeBytes).takeIf { sizeBytes > 0 }
            ).joinToString(", ").ifBlank { "\u2014" },
            stringResource(R.string.sug_image)
        )

        else -> stringResource(R.string.rec_default)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                stringResource(R.string.rec_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
        if (batchCount > 1) {
            Text(
                stringResource(R.string.rec_batch_note, batchCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Realtime progress — apni state khud collect karta hai, isliye har % tick pe
 * poora tool screen recompose NAHI hota (sirf yeh card).
 */
@Composable
private fun LiveProgressCard(
    taskId: Int?,
    hwProvider: () -> Pair<Boolean, Float>,
    label: String,
    onCancel: () -> Unit,
    onMinimize: () -> Unit
) {
    val tasks by Queue.tasks.collectAsState()
    val (hwRun, hwPct) = hwProvider()
    val t = taskId?.let { id -> tasks.find { it.id == id } }
    val percent = when {
        hwRun -> hwPct
        t == null || t.state == Queue.State.QUEUED -> 0f
        else -> t.progress
    }
    val subLabel = if (t?.state == Queue.State.QUEUED) stringResource(R.string.status_waiting_queue) else label
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.size(46.dp),
                strokeWidth = 4.dp
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "%.0f%%".format(percent),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(subLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onMinimize, modifier = Modifier.weight(1f)) {
                Icon(
                    Icons.Filled.Minimize,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.btn_minimize))
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.btn_cancel)) }
        }
        LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(6.dp))
        )
        Text(
            stringResource(R.string.hint_bg_jobs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Timeline cursor cutter — do draggable handles (start/end), video thumbnail strip,
 * frame-step buttons (±1 frame / ±1 sec) + live time labels. start_ms/end_ms params
 * mein likhta hai jisse engine frame-accurate trim karta hai.
 */
@Composable
private fun TimelineCutter(
    durationMs: Long,
    isVideo: Boolean,
    fps: Float,
    frames: List<Pair<Long, Bitmap>>,
    path: String?,
    fallback: Bitmap?,
    seek: androidx.compose.runtime.State<Long>,
    params: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Any>,
    onPreviewMs: (Long) -> Unit
) {
    val stateKey = "tl$durationMs"
    var startMs by remember(stateKey) { mutableLongStateOf((params["start_ms"] as? String)?.toLongOrNull() ?: 0L) }
    var endMs by remember(stateKey) { mutableLongStateOf((params["end_ms"] as? String)?.toLongOrNull() ?: durationMs) }
    var active by remember { mutableIntStateOf(0) }
    var trackPx by remember { mutableIntStateOf(1) }
    val frameMs = (1000.0 / fps.toDouble().coerceAtLeast(1.0)).toLong().coerceAtLeast(1L)
    val minClip = if (isVideo) frameMs else 100L

    fun sync(emit: Boolean) {
        params["start_ms"] = startMs.toString()
        params["end_ms"] = endMs.toString()
        if (emit) onPreviewMs(if (active == 0) startMs else endMs)
    }

    fun stepStart(d: Long) {
        startMs = (startMs + d).coerceIn(0L, (endMs - minClip).coerceAtLeast(0L))
        active = 0
        sync(true)
    }

    fun stepEnd(d: Long) {
        endMs = (endMs + d).coerceIn((startMs + minClip).coerceAtMost(durationMs), durationMs)
        active = 1
        sync(true)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            stringResource(R.string.hint_timeline),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        if (isVideo) {
            FramePreview(path = path, seek = seek, fallback = fallback)
        }

        // ---- track (video: thumbnail strip, audio: waveform-style bars) ----
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (frames.isNotEmpty()) 64.dp else 48.dp)
                .onSizeChanged { trackPx = it.width.coerceAtLeast(1) }
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1A1F2E))
        ) {
            if (frames.isNotEmpty()) {
                Row(Modifier.fillMaxSize()) {
                    frames.forEach { (_, bmp) ->
                        Image(
                            bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            } else {
                Canvas(Modifier.fillMaxSize()) {
                    val bars = 72
                    val bw = size.width / bars
                    for (i in 0 until bars) {
                        val h = (0.22f + 0.7f * kotlin.math.abs(kotlin.math.sin(i * 0.55f))) * size.height
                        drawRoundRect(
                            color = Color(0xFF3B5FD4),
                            topLeft = Offset(i * bw + bw * 0.25f, (size.height - h) / 2f),
                            size = Size(bw * 0.5f, h),
                            cornerRadius = CornerRadius(bw * 0.25f, bw * 0.25f)
                        )
                    }
                }
            }
            // selection ke bahar dim
            Canvas(Modifier.fillMaxSize()) {
                if (durationMs > 0) {
                    val l = (startMs.toFloat() / durationMs) * size.width
                    val r = (endMs.toFloat() / durationMs) * size.width
                    if (l > 0f) drawRect(Color(0xB3000000), size = Size(l, size.height))
                    if (r < size.width) {
                        drawRect(Color(0xB3000000), topLeft = Offset(r, 0f), size = Size(size.width - r, size.height))
                    }
                }
            }
            CutterHandle(
                frac = if (durationMs > 0) startMs.toFloat() / durationMs else 0f,
                trackPx = trackPx,
                active = active == 0,
                onActivate = { active = 0; sync(false) },
                onDragPx = { dPx ->
                    val delta = (dPx / trackPx) * durationMs
                    startMs = (startMs + delta).roundToLong().coerceIn(0L, (endMs - minClip).coerceAtLeast(0L))
                    active = 0
                    sync(true)
                }
            )
            CutterHandle(
                frac = if (durationMs > 0) endMs.toFloat() / durationMs else 1f,
                trackPx = trackPx,
                active = active == 1,
                onActivate = { active = 1; sync(false) },
                onDragPx = { dPx ->
                    val delta = (dPx / trackPx) * durationMs
                    endMs = (endMs + delta).roundToLong()
                        .coerceIn((startMs + minClip).coerceAtMost(durationMs), durationMs)
                    active = 1
                    sync(true)
                }
            )
        }

        // ---- live time labels ----
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TimeChip(stringResource(R.string.tl_start), startMs)
            TimeChip(stringResource(R.string.tl_selection), (endMs - startMs).coerceAtLeast(0L))
            TimeChip(stringResource(R.string.tl_end), endMs)
        }

        // ---- frame-accurate step buttons ----
        StepRow(stringResource(R.string.tl_start), frameMs, ::stepStart)
        StepRow(stringResource(R.string.tl_end), frameMs, ::stepEnd)
    }
}

/** Video frame preview — seek state sirf yehi composable padhta hai (screen recompose nahi) */
@Composable
private fun FramePreview(
    path: String?,
    seek: androidx.compose.runtime.State<Long>,
    fallback: Bitmap?
) {
    var bmp by remember(path) { mutableStateOf(fallback) }
    val seekMs = seek.value
    LaunchedEffect(path, seekMs) {
        if (path == null) return@LaunchedEffect
        kotlinx.coroutines.delay(120)
        val b = kotlinx.coroutines.withContext(Dispatchers.IO) { frameAtMs(path, seekMs) }
        if (b != null) bmp = b
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF10131C)),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(
                bmp!!.asImageBitmap(),
                contentDescription = stringResource(R.string.cd_frame_preview),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            // panel hamesha dark hai — theme-agnostic light text (light theme me invisible na ho)
            Text(
                stringResource(R.string.label_frame_preview),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFA9B4C6)
            )
        }
    }
}

/** Draggable start/end cursor — drag ya tap pe active handle badalta hai */
@Composable
private fun BoxScope.CutterHandle(
    frac: Float,
    trackPx: Int,
    active: Boolean,
    onActivate: () -> Unit,
    onDragPx: (Float) -> Unit
) {
    val color = MaterialTheme.colorScheme.primary
    val handleColor = if (active) color else color.copy(alpha = 0.85f)
    Box(
        Modifier
            .align(Alignment.CenterStart)
            .fillMaxHeight()
            .width(22.dp)
            .offset { IntOffset((frac * trackPx).roundToInt() - 11.dp.roundToPx(), 0) }
            .pointerInput(trackPx) {
                detectHorizontalDragGestures(
                    onDragStart = { onActivate() },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        onDragPx(dragAmount)
                    }
                )
            }
            .pointerInput(trackPx) { detectTapGestures { onActivate() } }
    ) {
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
                .width(4.dp)
                .background(handleColor, RoundedCornerShape(2.dp))
        )
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 1.dp)
                .size(12.dp)
                .background(handleColor, CircleShape)
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 1.dp)
                .size(12.dp)
                .background(handleColor, CircleShape)
        )
    }
}

@Composable
private fun StepRow(label: String, frameMs: Long, onStep: (Long) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        listOf(-1000L to "\u22121s", -frameMs to "\u22121f", frameMs to "+1f", 1000L to "+1s")
            .forEach { (d, t) ->
                TextButton(
                    onClick = { onStep(d) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(t, fontSize = 12.sp)
                }
            }
    }
}

@Composable
private fun TimeChip(label: String, ms: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            msLabel(ms),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private fun msLabel(ms: Long): String {
    val m = ms / 60_000
    val s = (ms / 1000) % 60
    val f = ms % 1000
    return "%d:%02d.%03d".format(m, s, f)
}

@Composable
private fun ErrorCard(msg: String) {
    Text(
        stringResource(R.string.err_format, msg),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
            .padding(12.dp)
    )
}

@Composable
private fun ResultCard(
    path: String,
    mime: String,
    onShare: (String, String) -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onOpenFolder: () -> Unit
) {
    // GIF bhi "playable" hai — built-in viewer use animate karta hai
    val playable = mime.startsWith("video") || mime.startsWith("audio")
    val openable = playable || mime.startsWith("image")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Text(stringResource(R.string.status_done), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(File(path).name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        OutlinedButton(onClick = { onShare(path, mime) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.btn_share))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (openable) {
                OutlinedButton(onClick = onPlay, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (playable) stringResource(R.string.btn_play) else stringResource(R.string.cd_play_media))
                }
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.btn_delete))
                }
            } else {
                OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.btn_delete))
                }
            }
        }
        // File KAHAN gayi — naye user ko yehi sabse zyada chahiye. Tap karne se
        // wahi folder Files/Gallery app me khul jata hai.
        OutlinedButton(onClick = onOpenFolder, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.btn_open_folder))
        }
        Text(
            stringResource(R.string.saved_to_folder, com.nova.converter.core.FileActions.savedFolder(mime)),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.tertiary
        )
        Text(
            stringResource(R.string.output_line, File(path).name, formatSize(File(path).length())),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FieldEditor(
    f: TField,
    params: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Any>,
    durationMs: Long = 0L
) {
    when (f) {
        is TField.Chips -> {
            Text(stringResource(f.label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                f.options.forEach { opt ->
                    val sel = params[f.key] == opt
                    Box(
                        Modifier
                            .padding(bottom = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { params[f.key] = opt }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            f.optionLabels[opt]?.let { stringResource(it) } ?: opt,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
        is TField.Num -> {
            TextField(
                value = params[f.key] as? String ?: f.def,
                onValueChange = { params[f.key] = it },
                label = { Text(stringResource(f.label)) },
                supportingText = if (f.hint != 0) ({ Text(stringResource(f.hint)) }) else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        is TField.TextF -> {
            TextField(
                value = params[f.key] as? String ?: f.def,
                onValueChange = { params[f.key] = it },
                label = { Text(stringResource(f.label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        is TField.Slide -> {
            val v = (params[f.key] as? String)?.toFloatOrNull() ?: f.def
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(f.label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(
                    f.fmt(v),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Slider(
                value = v,
                onValueChange = { params[f.key] = it.toString() },
                valueRange = f.min..f.max,
                steps = f.steps
            )
        }
        is TField.Toggle -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(f.label), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Switch(
                    checked = (params[f.key] as? String) == "true",
                    onCheckedChange = { params[f.key] = it.toString() }
                )
            }
        }
        is TField.Speed -> {
            SpeedEditor(f, params, durationMs)
        }
    }
}

/**
 * Speed control 0.5x – 100x.
 *
 * Pehle sirf 6 chips the (0.5/0.75/1.5/2/3/4) — range chhoti thi *aur* 3x/4x
 * command bug ki wajah se fail hote the. Ab teen tareeke se value set hoti hai:
 *
 *  1. **Presets** (curve) — common speeds ek tap me, 0.5x se 100x tak.
 *  2. **± step buttons** — custom fine control (0.05x ke qadam chhote factors pe).
 *  3. **Numeric field** — bilkul exact value (jaise 1.37x).
 *
 * Slider [SpeedScale] ka piecewise/log map use karta hai taaki 0.5–2 range pe
 * fine control rahe aur 100x bhi reachable ho. Value upar bhi live dikhti hai
 * saath me output ka estimated length (duration ÷ factor) — user ko pata chal
 * jata hai kitna lamba/shorta hoga.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeedEditor(
    f: TField.Speed,
    params: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Any>,
    durationMs: Long
) {
    val raw = params[f.key]
    val value = when (raw) {
        is Float -> raw
        is Int -> raw.toFloat()
        is String -> raw.toFloatOrNull() ?: f.def
        else -> f.def
    }.coerceIn(f.min, f.max)

    // numeric field ka text — slider/preset ke change se sync hota hai, lekin
    // user ke type karte waqt nahi (warna "1." likhte hi turant overwrite).
    var typed by remember(f.key) { mutableStateOf(SpeedScale.label(value)) }
    // true = user abhi field me type kar raha hai. Is dauraan baaki controls
    // value badal sakte hain, par text overwrite nahi hoga — warna
    // "150" type karke preset tap karte hi text "0.50" ho jayega.
    var typing by remember { mutableStateOf(false) }

    /** value set karo + text ko bhi usi value pe force-sync karo (UI controls) */
    fun set(v: Float) {
        val c = v.coerceIn(f.min, f.max)
        params[f.key] = c
        typed = SpeedScale.label(c)
        typing = false
    }

    /** sirf value set karo, text chhude (typing ke dauraan) */
    fun setQuiet(v: Float) {
        params[f.key] = v.coerceIn(f.min, f.max)
    }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ---- header: label + live value ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(f.label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                SpeedScale.label(value),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // ---- output length estimate ----
        if (durationMs > 0) {
            val outMs = (durationMs / value).toLong()
            Text(
                "${stringResource(R.string.speed_out_hint)}: " + formatDur(outMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ---- slider (non-linear scale) ----
        val sliderCd = stringResource(R.string.speed_slider_cd)
        Slider(
            value = SpeedScale.toSlider(value, f.min, f.max),
            onValueChange = { set(SpeedScale.fromSlider(it, f.min, f.max)) },
            valueRange = 0f..1f,
            // stringResource @Composable hai — semantics{} lambda me nahi call ho sakta,
            // isliye pehle upar resolve karo
            modifier = Modifier.semantics {
                contentDescription = "$sliderCd ${SpeedScale.label(value)}"
            }
        )

        // ---- −/+ step buttons (custom fine control) ----
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val step = SpeedScale.stepFor(value)
            OutlinedButton(
                onClick = { set(value - step) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.speed_dec), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(SpeedScale.label(value - step), fontSize = 12.sp, maxLines = 1)
            }
            OutlinedButton(
                onClick = { set(value + step) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(SpeedScale.label(value + step), fontSize = 12.sp, maxLines = 1)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.speed_inc), modifier = Modifier.size(16.dp))
            }
        }

        // ---- exact numeric entry ----
        OutlinedTextField(
            value = typed,
            onValueChange = { t ->
                typing = true
                typed = t.filter { it.isDigit() || it == '.' }.take(6)
                val parsed = typed.toFloatOrNull()
                // clamp kiye hue number ko wapas text me nahi likhte —
                // "150" likhne par turant "100" dikh jayega aur user confuse
                // hoga ki Maine 150 type kiya tha ya nahi. Text waisa rahe,
                // value clamp ho; sirf error state warning dikhata hai.
                if (parsed != null) setQuiet(parsed) else if (typed.isEmpty()) setQuiet(f.min)
            },
            label = { Text(stringResource(R.string.speed_custom)) },
            singleLine = true,
            isError = typed.toFloatOrNull()?.let { it < f.min || it > f.max } == true,
            supportingText = {
                if (typed.toFloatOrNull()?.let { it < f.min || it > f.max } == true) {
                    Text(stringResource(R.string.err_speed_range))
                } else {
                    Text(stringResource(R.string.speed_range))
                }
            },
            trailingIcon = {
                if (typed.isNotEmpty() && typed != SpeedScale.label(value)) {
                    TextButton(onClick = { set(value) }) {
                        Text("OK", fontSize = 12.sp)
                    }
                }
            },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
            ),
            modifier = Modifier.fillMaxWidth().onFocusChanged { typing = it.isFocused }
        )

        // ---- preset curve chips ----
        Text(
            stringResource(R.string.speed_presets),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SpeedScale.PRESETS.forEach { p ->
                val sel = kotlin.math.abs(p - value) < 0.001f
                Box(
                    Modifier
                        .padding(bottom = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { set(p) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        SpeedScale.label(p),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

fun formatDur(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d".format(s / 60, s % 60)
}
