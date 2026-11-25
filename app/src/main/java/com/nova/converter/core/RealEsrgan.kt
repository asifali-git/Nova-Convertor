package com.nova.converter.core

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Real-ESRGAN (ncnn + Vulkan) native upscaler — **images only**.
 *
 * Pichle session me dono ABI ka binary NDK se ban gaya tha, lekin app se linked
 * nahi tha (`jniLibs` khali, koi code call nahi karta tha). Ab:
 *
 *  - binary `assets/ai/<abi>/realesrgan-ncnn-vulkan` me hai → first run pe
 *    `filesDir/bin/` me copy hota hai aur `chmod 700` milta hai.
 *    (assets isliye, jniLibs nahi — jniLibs ka merger sirf `.so` expect karta
 *    hai, aur ye executable hai; runtime copy se extractNativeLibs ka bhi koi
 *    issue nahi banta)
 *  - **models app me nahi hain.** ncnn binary ko `realesrgan-x4plus.bin` +
 *    `.param` chahiye, jo ~64 MB hain. App inhe
 *    `<filesDir>/ai/` me dhoondta hai. Model na ho to crash nahi — clean error.
 *
 * Video ke liye ye binary use nahi hota: `realesrgan-ncnn-vulkan` sirf image /
 * image-folder leta hai. Video AI upscale ke liye frame-by-frame pipeline chahiye
 * jo on-device itna slow hai ki practical nahi — isliye AI mode `image_converter`
 * me hi hai (ab koi `video_enhancer` tool nahi bacha).
 */
object RealEsrgan {

    private const val TAG = "NovaAi"
    const val BIN_NAME = "realesrgan-ncnn-vulkan"

    /** ncnn model zoo me ye hi do shipped hain */
    val MODELS = listOf("realesrgan-x4plus", "realesrgan-x4plus-anime")

    private val running = java.util.concurrent.atomic.AtomicReference<Process?>(null)

    /** Device ka ABI (jniLibs asset path se match) */
    private fun abi(): String = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: ""

    /**
     * Vulkan GPU hai? Binary Vulkan loader se start hota hai — loader na mile
     * to binary chup-chaap exit ho jaata hai (crash nahi, koi output nahi).
     *
     * `FEATURE_VULKAN_HARDWARE_ACCELERATED` (API 24+) sunne wala **manifest-declared**
     * feature hai — `hasSystemFeature()` se guarantee nahi milti isliye humein
     * `<uses-feature android:name="android.hardware.vulkan.level">` bhi check
     * karna padta hai. Isi liye `SUPPORTED_ABIS` + dono feature probes.
     */
    fun hasVulkan(context: Context): Boolean = try {
        val pm = context.packageManager
        pm.hasSystemFeature("android.hardware.vulkan.level") ||
                pm.hasSystemFeature("android.hardware.vulkan.version") ||
                pm.hasSystemFeature("android.hardware.vulkan")
    } catch (_: Throwable) {
        false
    }

    /** Binary asset is ABI ke liye hai? */
    fun supportedAbi(): Boolean = abi() in SUPPORTED_ABIS

    private val SUPPORTED_ABIS = setOf("arm64-v8a", "x86_64")

    /** Model file `<filesDir>/ai/<name>.bin` (+ `.param`) maujood hai? */
    fun hasModel(context: Context, model: String): Boolean {
        val dir = modelDir(context)
        return File(dir, "$model.bin").length() > 0L && File(dir, "$model.param").length() > 0L
    }

    fun modelDir(context: Context): File =
        File(context.filesDir, "ai").apply { mkdirs() }

    /**
     * Binary ko app-private storage me nikaal ke executable banao.
     * Pehli run pe copy hota hai (~15 MB), uske baad size match karne par skip.
     */
    fun install(context: Context): File? {
        val dst = File(File(context.filesDir, "bin"), BIN_NAME)
        val assetPath = "ai/" + abi() + "/" + BIN_NAME
        return try {
            val app = context.applicationContext
            if (!supportedAbi()) {
                Log.w(TAG, "no binary for abi=" + abi() + " (have $SUPPORTED_ABIS)")
                return null
            }
            if (dst.exists() && dst.length() > 0L) {
                dst.setExecutable(true, false)
                return dst
            }
            dst.parentFile?.mkdirs()
            app.assets.open(assetPath).use { input ->
                dst.outputStream().use { o -> input.copyTo(o, 64 * 1024) }
            }
            if (!dst.setExecutable(true, false)) {
                Log.w(TAG, "setExecutable failed: ${dst.path}")
            }
            Log.i(TAG, "installed ${dst.name} ${dst.length()} bytes from $assetPath")
            dst
        } catch (t: Throwable) {
            Log.w(TAG, "install failed ($assetPath): $t")
            null
        }
    }

    /** Sab taiyaar? Nahi to caller ko kaunsa reason batana hai woh bata deta hai. */
    fun ready(context: Context, model: String): String? = when {
        !supportedAbi() -> "no-binary"
        !hasVulkan(context) -> "no-vulkan"
        !hasModel(context, model) -> "no-model"
        install(context) == null -> "no-binary"
        else -> null
    }

    /**
     * Upscale chalao. Model files `<filesDir>/ai/` me honi chahiye — process ka
     * CWD wahi set karte hain kyunki binary `-n <name>` ko relative path se
     * dhoondti hai.
     *
     * @param onProgress 0..100 (realesrgan stderr se parse hota hai)
     * @param onDone (ok, message, outputPath)
     */
    fun upscale(
        context: Context,
        input: File,
        output: File,
        scale: Int,
        model: String = MODELS.first(),
        tta: Boolean = false,
        onProgress: ((Float) -> Unit)? = null,
        onDone: (Boolean, String, String?) -> Unit
    ) {
        val app = context.applicationContext
        val once = AtomicBoolean(false)
        val finish = { ok: Boolean, msg: String, out: String? ->
            if (once.compareAndSet(false, true)) {
                ProgressBus.remove(AI_KEY)
                onDone(ok, msg, out)
            }
        }

        val problem = ready(app, model)
        if (problem != null) {
            Log.w(TAG, "upscale refused: $problem")
            finish(false, problem, null)
            return
        }
        val bin = install(app) ?: run { finish(false, "no-binary", null); return }
        val cwd = modelDir(app)

        output.parentFile?.mkdirs()
        if (output.exists()) output.delete()

        val cmd = ArrayList<String>().apply {
            add(bin.absolutePath)
            add("-i"); add(input.absolutePath)
            add("-o"); add(output.absolutePath)
            add("-s"); add(scale.toString())
            add("-n"); add(model)
            if (tta) { add("-x"); add("-t"); add("0") }
        }

        Thread({
            try {
                val pb = ProcessBuilder(cmd).redirectErrorStream(true)
                // model .bin/.param CWD se relative resolve hote hain
                pb.directory(cwd)
                val proc = pb.start()
                running.set(proc)
                ProgressBus.bind(app)
                ProgressBus.update(AI_KEY, "AI upscale", 0f)
                ProgressBus.startService()

                proc.inputStream.bufferedReader().forEachLine { line ->
                    val p = parseProgress(line)
                    if (p != null) {
                        onProgress?.invoke(p)
                        ProgressBus.update(AI_KEY, "AI upscale", p)
                    }
                    if (line.startsWith("Error:") || line.contains("Unsupported")) {
                        Log.w(TAG, line)
                    }
                }
                val code = proc.waitFor()
                val ok = code == 0 && output.exists() && output.length() > 0L
                Log.i(TAG, "exit=$code out=${output.name} size=${output.length()}")
                if (ok) {
                    ProgressBus.update(AI_KEY, "AI upscale", 100f)
                    finish(true, "upscaled", output.absolutePath)
                } else {
                    finish(false, if (code == 0) "output-not-created" else "exit-$code", null)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "upscale crashed: $t")
                finish(false, t.message ?: t.javaClass.simpleName, null)
            } finally {
                running.set(null)
            }
        }, "nova-realesrgan").apply { isDaemon = true }.start()
    }

    /** Chal raha hua process (agar hai) — UI ka Cancel yahan se maarta hai */
    fun cancel() {
        try {
            running.getAndSet(null)?.destroy()
        } catch (_: Throwable) {
        }
        ProgressBus.remove(AI_KEY)
    }

    fun isRunning(): Boolean = running.get()?.isAlive == true

    private const val AI_KEY = "ai:realesrgan"

    /**
     * realesrgan-ncnn-vulkan ki progress line:
     * ` 12.34 =   3.50% (1/35)` → 3.5
     */
    private val RE = Regex("""=\s*([0-9]+(?:\.[0-9]+)?)\s*%""")

    private fun parseProgress(line: String): Float? {
        if (!line.contains('%')) return null
        val m = RE.find(line) ?: return null
        return m.groupValues[1].toFloatOrNull()?.coerceIn(0f, 100f)
    }
}
