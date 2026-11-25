package com.nova.converter.features.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.ui.graphics.Color
import java.io.File
import kotlin.math.max

/** Player ke liye media helpers — metadata, album art, dominant color, SRT, resume, folder queue */
object MediaUtil {

    private val VIDEO_EXT = setOf(
        "mp4", "mkv", "avi", "mov", "m4v", "webm", "ts", "flv", "3gp", "wmv", "mpeg", "mpg", "rmvb", "rm"
    )
    private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "amr", "wma", "mka", "ape")
    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")

    fun ext(path: String) = path.substringAfterLast('.', "").lowercase()

    fun isVideoFile(path: String) = ext(path) in VIDEO_EXT
    fun isAudioFile(path: String) = ext(path) in AUDIO_EXT
    fun isImageFile(path: String) = ext(path) in IMAGE_EXT

    fun hasVideoTrack(path: String): Boolean = try {
        val r = MediaMetadataRetriever()
        r.setDataSource(path)
        val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        r.release()
        w != null && w.toIntOrNull() != 0
    } catch (_: Exception) {
        false
    }

    data class Meta(
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val art: Bitmap?
    )

    fun meta(path: String): Meta = try {
        val r = MediaMetadataRetriever()
        r.setDataSource(path)
        val title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            ?: File(path).nameWithoutExtension
        val artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            ?: r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            ?: "Unknown artist"
        val album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
        val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val pic = try {
            r.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        } catch (_: Exception) {
            null
        }
        r.release()
        Meta(title, artist, album, dur, pic)
    } catch (_: Exception) {
        Meta(File(path).nameWithoutExtension, "", "", 0L, null)
    }

    /**
     * Album art se dominant color (dynamic theme ke liye) — saturated rich color choose karta hai,
     * warna fallback.
     */
    fun dominantColor(bmp: Bitmap?, fallback: Color): Color {
        if (bmp == null || bmp.width <= 0 || bmp.height <= 0) return fallback
        return try {
            val small = Bitmap.createScaledBitmap(bmp, 40, 40, false)
            val px = IntArray(40 * 40)
            small.getPixels(px, 0, 40, 0, 0, 40, 40)
            if (small != bmp) small.recycle()
            val hsv = FloatArray(3)
            var best = -1f
            var bestR = 0f; var bestG = 0f; var bestB = 0f
            var sumR = 0L; var sumG = 0L; var sumB = 0L; var n = 0L
            for (p in px) {
                val r = (p shr 16 and 0xFF) / 255f
                val g = (p shr 8 and 0xFF) / 255f
                val b = (p and 0xFF) / 255f
                android.graphics.Color.RGBToHSV(
                    (r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt(), hsv
                )
                val sat = hsv[1]; val value = hsv[2]
                sumR += (r * 255).toLong(); sumG += (g * 255).toLong(); sumB += (b * 255).toLong(); n++
                if (sat in 0.35f..0.95f && value in 0.35f..0.9f) {
                    val score = sat * value
                    if (score > best) {
                        best = score
                        bestR = r; bestG = g; bestB = b
                    }
                }
            }
            if (best > 0f) Color(bestR, bestG, bestB, 1f)
            else if (n > 0) {
                Color(sumR / n / 255f, sumG / n / 255f, sumB / n / 255f, 1f)
            } else fallback
        } catch (_: Exception) {
            fallback
        }
    }

    /** Dynamic theme ke shades — base color se light/dark variants */
    fun shades(base: Color): List<Color> {
        return listOf(
            base,
            Color(
                (base.red + 1f) / 2f,
                (base.green + 1f) / 2f,
                (base.blue + 1f) / 2f,
                1f
            ),
            Color(base.red * 0.45f, base.green * 0.45f, base.blue * 0.45f, 1f)
        )
    }

    data class Cue(val startMs: Long, val endMs: Long, val text: String)

    /** SRT / simple VTT parse */
    fun parseSubtitles(text: String): List<Cue> {
        val out = ArrayList<Cue>()
        try {
            val normalized = text.replace("\r\n", "\n").replace("\r", "\n")
            val blocks = normalized.split(Regex("\n\n+"))
            for (b in blocks) {
                val lines = b.trim().lines()
                val arrowIdx = lines.indexOfFirst { it.contains("-->") }
                if (arrowIdx < 0) continue
                val range = lines[arrowIdx]
                val start = parseTime(range.substringBefore("-->").trim())
                val end = parseTime(range.substringAfter("-->").trim().substringBefore(" "))
                val content = lines.drop(arrowIdx + 1).joinToString("\n").trim()
                    .replace(Regex("<[^>]+>"), "")
                if (content.isNotEmpty() && end > start) out.add(Cue(start, end, content))
            }
        } catch (_: Exception) {
        }
        return out.sortedBy { it.startMs }
    }

    private fun parseTime(s: String): Long {
        return try {
            val clean = s.replace(',', '.')
            val parts = clean.split(":")
            when (parts.size) {
                3 -> {
                    val h = parts[0].toLongOrNull() ?: 0
                    val m = parts[1].toLongOrNull() ?: 0
                    val sec = parts[2].toDoubleOrNull() ?: 0.0
                    h * 3_600_000L + m * 60_000L + (sec * 1000).toLong()
                }
                2 -> {
                    val m = parts[0].toLongOrNull() ?: 0
                    val sec = parts[1].toDoubleOrNull() ?: 0.0
                    m * 60_000L + (sec * 1000).toLong()
                }
                else -> 0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    /** Media ke paas pada .srt/.vtt dhundho */
    fun findSubtitle(mediaPath: String): String? {
        return try {
            val f = File(mediaPath)
            val base = f.nameWithoutExtension
            val dir = f.parentFile ?: return null
            dir.listFiles()?.firstOrNull {
                val n = it.name.lowercase()
                (n == "$base.srt".lowercase() || n == "$base.vtt".lowercase()) && it.length() > 0
            }?.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun readText(path: String): String? = try {
        File(path).readText(Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }

    /** Ek folder ke andar saare media files (queue banane ke liye) */
    fun siblingMedia(mediaPath: String): List<String> {
        return try {
            val f = File(mediaPath)
            val dir = f.parentFile ?: return listOf(mediaPath)
            val video = isVideoFile(mediaPath)
            val image = isImageFile(mediaPath)
            dir.listFiles()
                ?.filter {
                    !it.isDirectory && it.absolutePath != f.absolutePath && it.length() > 0 &&
                        if (video) isVideoFile(it.absolutePath)
                        else if (image) isImageFile(it.absolutePath)
                        else isAudioFile(it.absolutePath)
                }
                ?.map { it.absolutePath }
                ?.sorted() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Resume position — file path → ms (MX jaisa playback resume) */
    private fun prefs(context: Context) =
        context.getSharedPreferences("nova_player_pos", Context.MODE_PRIVATE)

    fun savePos(context: Context, path: String, ms: Long) {
        try {
            if (ms > 3000) prefs(context).edit().putLong(path, ms).apply()
        } catch (_: Exception) {
        }
    }

    fun loadPos(context: Context, path: String): Long =
        try {
            prefs(context).getLong(path, 0L)
        } catch (_: Exception) {
            0L
        }

    fun clearPos(context: Context, path: String) {
        try {
            prefs(context).edit().remove(path).apply()
        } catch (_: Exception) {
        }
    }

    fun fileSizeLabel(bytes: Long): String = when {
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    fun uriOf(path: String): Uri = Uri.fromFile(File(path))

    fun durationOf(path: String): Long = try {
        val r = MediaMetadataRetriever()
        r.setDataSource(path)
        val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        r.release()
        d
    } catch (_: Exception) {
        0L
    }

    fun clampPos(ms: Long, duration: Long): Long =
        if (duration > 0) ms.coerceIn(0L, max(0L, duration - 1)) else ms
}
