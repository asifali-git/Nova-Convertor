package com.nova.converter.core

import android.content.Context
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.MediaInformation
import com.arthenica.ffmpegkit.MediaInformationSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * JoshAtticus/Compressor backend - fast hardware (Media3/MediaCodec) compression.
 * Presets + target-size bitrate + resolution. Software (FFmpeg) path se kaafi tez.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class FastCompressor(private val context: Context) {

    data class Spec(
        val videoBitrate: Int,
        val height: Int? = null,
        val removeAudio: Boolean = false
    )

    private var transformer: Transformer? = null
    @Volatile private var running = false
    private val finished = java.util.concurrent.atomic.AtomicBoolean(false)
    private val pollScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * @param onProgress realtime 0..100 (Transformer ProgressHolder polling)
     */
    fun compress(
        input: Uri,
        spec: Spec,
        outputPath: String? = null,
        onProgress: ((Float) -> Unit)? = null,
        onDone: (Boolean, String, String?) -> Unit
    ) {
        val out = if (outputPath != null) java.io.File(outputPath) else Outputs.workFile(context, "fast_${System.currentTimeMillis()}.mp4")
        if (out.exists()) out.delete()

        // onDone exactly ek baar — cancel/error/timeout sab ek dusre ko trigger kar sakte hain
        val finish = { ok: Boolean, msg: String, path: String? ->
            if (finished.compareAndSet(false, true)) {
                running = false
                onDone(ok, msg, path)
            }
        }

        try {
            var itemBuilder = EditedMediaItem.Builder(MediaItem.fromUri(input))
            if (spec.removeAudio) itemBuilder = itemBuilder.setRemoveAudio(true)

            val videoEffects = mutableListOf<Effect>()
            if (spec.height != null) {
                videoEffects += Presentation.createForHeight(spec.height)
            }
            itemBuilder = itemBuilder.setEffects(Effects(emptyList(), videoEffects))

            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(
                    VideoEncoderSettings.Builder()
                        .setBitrate(spec.videoBitrate)
                        .build()
                )
                .setEnableFallback(true)
                .build()

            val request = TransformationRequest.Builder()
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .build()

            val t = Transformer.Builder(context)
                .setTransformationRequest(request)
                .setEncoderFactory(encoderFactory)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, result: ExportResult) {
                        onProgress?.invoke(100f)
                        finish(true, "compressed", out.absolutePath)
                    }

                    override fun onError(
                        composition: Composition,
                        result: ExportResult,
                        exception: ExportException
                    ) {
                        finish(false, exception.message ?: "transformer error", null)
                    }
                })
                .build()

            transformer = t
            running = true
            t.start(itemBuilder.build(), out.absolutePath)

            if (onProgress != null) {
                val holder = androidx.media3.transformer.ProgressHolder()
                pollScope.launch {
                    var lastP = -1f
                    var lastChange = System.currentTimeMillis()
                    while (isActive && running) {
                        val state = try {
                            t.getProgress(holder)
                        } catch (t3: Throwable) {
                            finish(false, "hardware progress error: ${t3.message ?: t3.javaClass.simpleName}", null)
                            break
                        }
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            val cur = holder.progress.toFloat()
                            onProgress(cur)
                            if (cur != lastP) {
                                lastP = cur
                                lastChange = System.currentTimeMillis()
                            }
                        } else if (state == Transformer.PROGRESS_STATE_NO_TRANSFORMATION) {
                            break
                        }
                        // stall watchdog: 90s se zyada koi progress change nahi →
                        // transformer atak gaya, cancel karke caller ko fallback ka mauka do
                        if (System.currentTimeMillis() - lastChange >= 90_000L) {
                            try { t.cancel() } catch (_: Throwable) {}
                            finish(false, "hardware encoder stalled (timeout)", null)
                            break
                        }
                        delay(500)
                    }
                }
            }
        } catch (t2: Throwable) {
            finish(false, "hardware error: ${t2.message ?: t2.javaClass.simpleName}", null)
        }
    }

    fun cancel() {
        // user cancel: callback suppress — warna onError se FFmpeg fallback chalu ho jata
        finished.set(true)
        running = false
        transformer?.cancel()
        transformer = null
        ProgressBus.clear()
    }

    companion object {

        suspend fun probe(path: String): MediaInformation? =
            suspendCancellableCoroutine { cont ->
                FFprobeKit.getMediaInformationAsync(path) { session: MediaInformationSession ->
                    if (cont.isActive) cont.resume(session.mediaInformation)
                }
            }

        fun guessMime(format: String): String = when (format.lowercase()) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "flv" -> "video/x-flv"
            "3gp" -> "video/3gpp"
            "gif" -> "image/gif"
            "mp3" -> "audio/mpeg"
            "m4a", "aac" -> "audio/mp4"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "ogg", "opus" -> "audio/ogg"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "bmp" -> "image/bmp"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
    }
}
