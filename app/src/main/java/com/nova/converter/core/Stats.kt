package com.nova.converter.core

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Debug
import android.os.Process
import android.os.StatFs
import android.util.Log
import java.io.File

/**
 * Media file statistics — probed once, used everywhere.
 * Uses MediaMetadataRetriever (Android SDK) - no external deps.
 */
data class MediaStats(
    // Input file
    val path: String = "",
    val size: Long = 0L,
    val mimeType: String = "",
    
    // Container
    val formatName: String = "",
    val bitrate: Long = 0L,
    val durationMs: Long = 0L,
    
    // Video
    val videoCodec: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val fps: Float = 0f,
    val videoBitrate: Long = 0L,
    val hasVideo: Boolean = false,
    
    // Audio
    val audioCodec: String = "",
    val sampleRate: Int = 0,
    val channels: Int = 0,
    val audioBitrate: Long = 0L,
    val hasAudio: Boolean = false,
    
    // Subtitles
    val subtitleCount: Int = 0
) {
    val sizeStr: String get() = formatSize(size)
    val durationStr: String get() = formatDur(durationMs)
    val resolutionStr: String get() = if (hasVideo) "$width×$height" else "—"
    val fpsStr: String get() = if (hasVideo) "%.1f fps".format(fps) else "—"
    val videoBitrateStr: String get() = formatBitrate(videoBitrate)
    val audioBitrateStr: String get() = formatBitrate(audioBitrate)
    val bitrateStr: String get() = formatBitrate(bitrate)
    val aspectRatioStr: String get() = if (hasVideo) calcAspect() else "—"
    
    private fun calcAspect(): String {
        if (width <= 0 || height <= 0) return "—"
        val g = gcd(width, height)
        return "${width / g}:${height / g}"
    }
    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    
    companion object {
        private const val TAG = "MediaStats"
        
        /** Probe file with MediaMetadataRetriever */
        fun probe(context: Context, path: String, mimeHint: String = ""): MediaStats {
            val file = File(path)
            val size = file.length()
            
            var stats = probeWithMMR(path)
            
            // Fill mime from extension if missing
            val mime = if (stats.mimeType.isEmpty()) guessMimeFromPath(path, mimeHint) else stats.mimeType
            
            return stats.copy(path = path, size = size, mimeType = mime)
        }
        
        private fun probeWithMMR(path: String): MediaStats {
            var dur = 0L
            var w = 0
            var h = 0
            var rot = 0
            var audio = false
            var fps = 30f
            var vCodec = ""
            var aCodec = ""
            var sRate = 0
            var ch = 0
            var vBitrate = 0L
            var aBitrate = 0L
            
            try {
                val r = MediaMetadataRetriever()
                r.setDataSource(path)
                dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                audio = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
                fps = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 30f
                vBitrate = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
                r.release()
            } catch (e: Exception) {}
            
            val (effW, effH) = if (rot == 90 || rot == 270) h to w else w to h
            
            return MediaStats(
                path = path,
                durationMs = dur,
                width = effW,
                height = effH,
                fps = fps,
                hasVideo = w > 0 && h > 0,
                hasAudio = audio,
                videoCodec = vCodec,
                audioCodec = aCodec,
                sampleRate = sRate,
                channels = ch,
                videoBitrate = vBitrate,
                audioBitrate = aBitrate
            )
        }
        
        private fun guessMimeFromPath(path: String, hint: String): String {
            if (hint.isNotEmpty()) return hint
            val ext = path.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "mp4", "mkv", "webm", "avi", "mov", "flv", "3gp" -> "video/mp4"
                "mp3", "m4a", "aac", "wav", "flac", "ogg", "opus" -> "audio/mpeg"
                "jpg", "jpeg", "png", "bmp", "webp", "gif" -> "image/jpeg"
                else -> "*/*"
            }
        }
    }
}

/** Runtime processing statistics */
data class ProcessingStats(
    val startTimeMs: Long = System.currentTimeMillis(),
    val elapsedMs: Long = 0L,
    val estimatedTotalMs: Long = 0L,
    val etaMs: Long = 0L,
    val speedRatio: Float = 0f,
    val cpuPercent: Float = 0f,
    val ramUsedMb: Long = 0L,
    val ramPeakMb: Long = 0L,
    val ramSystemFreeMb: Long = 0L,
    val inputBytesProcessed: Long = 0L,
    val outputBytesWritten: Long = 0L,
    val framesProcessed: Long = 0L,
    val framesTotal: Long = 0L,
    val currentFps: Float = 0f,
    val stage: String = "",
    val details: String = ""
) {
    val progress: Float get() = if (estimatedTotalMs > 0) (elapsedMs * 100f / estimatedTotalMs).coerceIn(0f, 100f) else 0f
    val etaStr: String get() = if (etaMs > 0) formatDur(etaMs) else "—"
    val elapsedStr: String get() = formatDur(elapsedMs)
    val speedStr: String get() = if (speedRatio > 0) "%.2fx".format(speedRatio) else "—"
    val ramUsedStr: String get() = "${ramUsedMb} MB"
    val ramPeakStr: String get() = "${ramPeakMb} MB"
    val framesStr: String get() = if (framesTotal > 0) "$framesProcessed / $framesTotal" else "—"
    
    fun copyWith(
        elapsedMs: Long = this.elapsedMs,
        estimatedTotalMs: Long = this.estimatedTotalMs,
        cpuPercent: Float = this.cpuPercent,
        ramUsedMb: Long = this.ramUsedMb,
        ramPeakMb: Long = this.ramPeakMb,
        ramSystemFreeMb: Long = this.ramSystemFreeMb,
        inputBytesProcessed: Long = this.inputBytesProcessed,
        outputBytesWritten: Long = this.outputBytesWritten,
        framesProcessed: Long = this.framesProcessed,
        currentFps: Float = this.currentFps,
        stage: String = this.stage,
        details: String = this.details
    ): ProcessingStats {
        val newElapsed = elapsedMs
        val newTotal = if (estimatedTotalMs > 0) estimatedTotalMs else this.estimatedTotalMs
        val newEta = if (newTotal > newElapsed) newTotal - newElapsed else 0L
        val newSpeed = if (newElapsed > 0) 1f / (newElapsed / 1000f) * (durationMs / 1000f) else speedRatio
        return copy(
            elapsedMs = newElapsed,
            estimatedTotalMs = newTotal,
            etaMs = newEta,
            speedRatio = newSpeed,
            cpuPercent = cpuPercent,
            ramUsedMb = ramUsedMb,
            ramPeakMb = maxOf(ramPeakMb, ramUsedMb),
            ramSystemFreeMb = ramSystemFreeMb,
            inputBytesProcessed = inputBytesProcessed,
            outputBytesWritten = outputBytesWritten,
            framesProcessed = framesProcessed,
            currentFps = currentFps,
            stage = stage,
            details = details
        )
    }
    
    private val durationMs: Long get() = estimatedTotalMs
}

/** Result statistics — final snapshot */
data class ResultStats(
    val inputStats: MediaStats,
    val outputStats: MediaStats?,
    val processingStats: ProcessingStats,
    val success: Boolean,
    val errorMessage: String = "",
    val toolId: String = "",
    val toolName: String = "",
    val timestampMs: Long = System.currentTimeMillis()
) {
    val sizeChange: Long get() = (outputStats?.size ?: 0L) - inputStats.size
    val sizeChangePercent: Float get() = if (inputStats.size > 0 && outputStats != null) (sizeChange.toFloat() / inputStats.size) * 100f else 0f
    val compressionRatioStr: String get() = if (inputStats.size > 0 && outputStats != null && outputStats.size > 0) String.format("%.2fx", outputStats.size.toDouble() / inputStats.size) else "-"
    
    fun summary(): String {
        val out = outputStats
        return if (success && out != null) {
            "${toolName}: ${inputStats.sizeStr} → ${out.sizeStr} (${if (sizeChangePercent >= 0) "+" else ""}${String.format("%.1f", sizeChangePercent)}%) • ${out.durationStr} • ${out.resolutionStr} • ${out.videoBitrateStr} / ${out.audioBitrateStr}"
        } else {
            "$toolName: ${errorMessage.ifEmpty { "failed" }}"
        }
    }
}

/** System resource sampler */
class ResourceSampler private constructor() {
    private val TAG = "ResourceSampler"
    private var running = false
    private var thread: Thread? = null
    private val listeners = mutableListOf<(ProcessingStats) -> Unit>()
    private val startTime = System.currentTimeMillis()
    private var lastCpuTime = 0L
    private var lastSysCpuTime = 0L
    
    companion object {
        @Volatile private var INSTANCE: ResourceSampler? = null
        fun get(): ResourceSampler = INSTANCE ?: synchronized(this) { INSTANCE ?: ResourceSampler().also { INSTANCE = it } }
    }
    
    fun start() {
        if (running) return
        running = true
        lastCpuTime = readCpuTime()
        lastSysCpuTime = readSystemCpuTime()
        thread = Thread({
            while (running) {
                try {
                    val stats = sample()
                    listeners.forEach { it(stats) }
                    Thread.sleep(1000)
                } catch (e: InterruptedException) { break }
                catch (e: Exception) { Log.w(TAG, "sample error: $e") }
            }
        }, "nova-res-sampler").apply { isDaemon = true; start() }
    }
    
    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
        listeners.clear()
    }
    
    fun addListener(listener: (ProcessingStats) -> Unit) { listeners.add(listener) }
    fun removeListener(listener: (ProcessingStats) -> Unit) { listeners.remove(listener) }
    
    private fun sample(): ProcessingStats {
        val now = System.currentTimeMillis()
        val elapsed = now - startTime
        
        val cpuTime = readCpuTime()
        val sysCpuTime = readSystemCpuTime()
        val cpuDelta = cpuTime - lastCpuTime
        val sysDelta = sysCpuTime - lastSysCpuTime
        lastCpuTime = cpuTime
        lastSysCpuTime = sysCpuTime
        
        val cpuPercent = if (sysDelta > 0) (cpuDelta.toFloat() / sysDelta * 100f * Runtime.getRuntime().availableProcessors()) else 0f
        
        val memInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memInfo)
        val ramUsed = (memInfo.totalPss.toLong() * 4 / 1024)
        
        val peakInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(peakInfo)
        val ramPeak = (peakInfo.totalPss.toLong() * 4 / 1024)
        
        val statFs = StatFs(android.os.Environment.getDataDirectory().absolutePath)
        val sysFree = statFs.availableBlocksLong * statFs.blockSizeLong / (1024 * 1024)
        
        return ProcessingStats(
            elapsedMs = elapsed,
            cpuPercent = cpuPercent.coerceIn(0f, 100f * Runtime.getRuntime().availableProcessors()),
            ramUsedMb = ramUsed,
            ramPeakMb = ramPeak,
            ramSystemFreeMb = sysFree,
            stage = "processing"
        )
    }
    
    private fun readCpuTime(): Long = try {
        val pid = Process.myPid()
        val file = File("/proc/$pid/stat")
        val parts = file.readText().split(" ")
        if (parts.size >= 17) (parts[13].toLongOrNull() ?: 0L) + (parts[14].toLongOrNull() ?: 0L) else 0L
    } catch (_: Exception) { 0L }
    
    private fun readSystemCpuTime(): Long = try {
        val line = File("/proc/stat").readText().lines().firstOrNull { it.startsWith("cpu ") } ?: return 0L
        line.split(" ").filter { it.isNotEmpty() }.drop(1).sumOf { it.toLongOrNull() ?: 0L }
    } catch (_: Exception) { 0L }
}

/** Format utilities */
private fun formatSize(bytes: Long): String = if (bytes < 1024) "$bytes B" else if (bytes < 1024*1024) "${bytes/1024} KB" else if (bytes < 1024*1024*1024) String.format("%.1f MB", (bytes/1024/1024).toDouble()) else String.format("%.2f GB", (bytes/1024/1024/1024).toDouble())
private fun formatDur(ms: Long): String { val s = ms/1000; val h = s/3600; val m = (s%3600)/60; val sec = s%60; return if (h>0) "%d:%02d:%02d".format(h,m,sec) else "%d:%02d".format(m,sec) }
private fun formatBitrate(bps: Long): String = if (bps<=0) "—" else if (bps<1_000_000) "${bps/1000} kbps" else String.format("%.1f Mbps", (bps/1_000_000).toDouble())
private fun String?.ifEmpty(default: String): String = if (this.isNullOrEmpty()) default else this!!