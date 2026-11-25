package com.nova.converter.core

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.nova.converter.R

/**
 * Tool parameter fields — Home card click → ToolDetailScreen isi se form banata hai.
 * `label`/`hint` ab R.string resource id hain (localization ke liye).
 * Chips ke `options` ke andar ke VALUES angrezi hi rehte hain (params map me jaate
 * hain aur lambda wahi compare karte hain) — sirf dikhne wala text `optionLabels`
 * se translate hota hai.
 */
sealed class TField {
    abstract val key: String
    abstract val label: Int

    data class Chips(
        override val key: String,
        override val label: Int,
        /** Stable internal values (params map me ye jaate hain) */
        val options: List<String>,
        val def: String,
        /** Optional: value → R.string jo dropdown me dikhega */
        val optionLabels: Map<String, Int> = emptyMap()
    ) : TField()

    data class Num(
        override val key: String,
        override val label: Int,
        val def: String,
        val hint: Int = 0
    ) : TField()

    data class TextF(
        override val key: String,
        override val label: Int,
        val def: String
    ) : TField()

    data class Slide(
        override val key: String,
        override val label: Int,
        val min: Float,
        val max: Float,
        val def: Float,
        val steps: Int = 0,
        /** Value formatter (unit symbols — MB, %, x — universal hai) */
        val fmt: (Float) -> String
    ) : TField()

    data class Toggle(
        override val key: String,
        override val label: Int,
        val def: Boolean
    ) : TField()

    /**
     * Speed 0.5x – 100x. Chips ki jagah proper control chahiye tha: range itni badi
     * hai ki 6 chips (0.5/0.75/1.5/2/3/4) se 100x tak pahunch hi nahi sakte the.
     *
     * Slider **piecewise curve** pe chalta hai (log scale poora range linear nahi
     * karta — 0.5x..2x me hissa 50% travel, 2x..100x me baaki 50%), isliye
     * slow-motion side fine control milta hai aur fast-forward side bhi 100x
     * tak pohochti hai. Value raw Float params map me chalti hai (string nahi) —
     * round-trip se precision nahi jaani chahiye.
     */
    data class Speed(
        override val key: String,
        override val label: Int,
        val min: Float = 0.5f,
        val max: Float = 100f,
        val def: Float = 1.5f
    ) : TField()
}

/**
 * Slider position (0..1) ↔ speed factor ka non-linear map.
 *
 * Pehla half (t 0..0.5) = 0.5x..2x linear, doosra half (t 0.5..1) = 2x..max log —
 * isse 1.0x slider ke ~25% pe aata hai (aadha khaali low-end pe nahi jaata) aur
 * 100x bhi reach hota hai. Piecewise rakhna isliye zaroori hai ki pure-log map me
 * 0.5→2 range poore slider ka sirf ~13% leta aur normal speeds (1x/1.5x/2x)
 * jaldi-ji khatam ho jate.
 */
object SpeedScale {
    private const val SPLIT = 2f

    fun toSlider(value: Float, min: Float = 0.5f, max: Float = 100f): Float {
        val v = value.coerceIn(min, max)
        if (v <= SPLIT) {
            return 0.5f * ((v - min) / (SPLIT - min)).coerceIn(0f, 1f)
        }
        val t = kotlin.math.ln(v / SPLIT) / kotlin.math.ln(max / SPLIT)
        return 0.5f + 0.5f * t.coerceIn(0f, 1f)
    }

    fun fromSlider(t: Float, min: Float = 0.5f, max: Float = 100f): Float {
        val x = t.coerceIn(0f, 1f)
        return if (x <= 0.5f) {
            min + (SPLIT - min) * (x / 0.5f)
        } else {
            SPLIT * kotlin.math.exp((x - 0.5f) / 0.5f * kotlin.math.ln(max / SPLIT))
        }
    }

    /**
     * ± step kitna ho — chhote factors pe fine, bade pe coarse (0.05x vs 1x).
     * User ke "custom speed up/down" requirement ka practical form.
     */
    fun stepFor(value: Float): Float = when {
        value < 2f -> 0.05f
        value < 10f -> 0.1f
        value < 30f -> 0.5f
        else -> 1f
    }

    /** 1.50x / 3.2x / 100x — trailing zeros sirf display pe, params raw rehta hai */
    fun label(value: Float): String =
        if (value >= 10f) {
            String.format(java.util.Locale.US, "%.1f", value)
        } else {
            String.format(java.util.Locale.US, "%.2f", value)
        } + "x"

    /** Preset curve — common playback + time-lapse speeds */
    val PRESETS = listOf(
        0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f, 4f, 6f, 8f, 10f, 16f, 25f, 50f, 100f
    )
}

enum class Section { HERO, VIDEO, AUDIO, IMAGE, MORE }

/**
 * Card icon me "media kind" ka glyph. Tool list pe sirf ek generic icon
 * (ContentCut / Speed / ...) dikhane se pata nahi chalta ki tool **kis cheez
 * par** chalta hai — "Video to Audio" aur "Audio Cutter" dono scissors jaise
 * lag sakte hain. Isliye har card apna kind-chip + operation-badge banata hai,
 * aur conversion wale tools source → target dono dikhate hain.
 */
enum class FlowKind { VIDEO, AUDIO, IMAGE, GIF, FILE }

data class NovaTool(
    val id: String,
    val title: Int,
    val subtitle: Int,
    val icon: ImageVector,
    val tint: Color,
    val section: Section,
    val mimes: Array<String>,
    val outExt: String = "",          // "" = input ke saath same extension
    val mime: String = "video/mp4",
    val twoInputs: Boolean = false,
    val hardware: Boolean = false,    // Media3 hardware path (compress)
    val fields: List<TField> = emptyList(),
    // Home card ka glyph. 2 entries = conversion (source ➜ target),
    // 1 entry = operation usi kind par (glyph me kind + operation ek hi pill me).
    val flow: List<FlowKind> = emptyList(),
    val build: (p: Map<String, Any>, a: String, b: String?, durSec: Float) -> String
) {
    override fun equals(other: Any?) = other is NovaTool && other.id == id
    override fun hashCode() = id.hashCode()
}

/** FlowKind ka Compose icon — UI sirf ye lookup karti hai, mapping ek jagah. */
fun FlowKind.glyph(): ImageVector = when (this) {
    FlowKind.VIDEO -> Icons.Filled.Movie
    FlowKind.AUDIO -> Icons.Filled.MusicNote
    FlowKind.IMAGE -> Icons.Filled.Image
    FlowKind.GIF -> Icons.Filled.Gif
    FlowKind.FILE -> Icons.Filled.InsertDriveFile
}

private fun Map<String, Any>.s(k: String, d: String = "") = this[k] as? String ?: d
private fun Map<String, Any>.i(k: String, d: Int = 0): Int = when (val v = this[k]) {
    is Int -> v
    is Long -> v.toInt()
    is Float -> v.toInt()
    is Double -> v.toInt()
    is String -> v.toFloatOrNull()?.toInt() ?: v.toIntOrNull() ?: d
    else -> d
}
private fun Map<String, Any>.f(k: String, d: Float = 0f): Float = when (val v = this[k]) {
    is Float -> v
    is Int -> v.toFloat()
    is Long -> v.toFloat()
    is Double -> v.toFloat()
    is String -> v.toFloatOrNull() ?: d
    else -> d
}
private fun Map<String, Any>.b(k: String, d: Boolean = false): Boolean = when (val v = this[k]) {
    is Boolean -> v
    is String -> v == "true"
    else -> d
}

object Tools {

    private fun c(v: Long) = androidx.compose.ui.graphics.Color(v)
    val videoMime = arrayOf("video/*")
    val audioMime = arrayOf("audio/*")
    val anyMime = arrayOf("*/*")
    val imageMime = arrayOf("image/*")

    /** Quality chips — value English (logic keys), label translated */
    private val qualityLabels = mapOf(
        "High" to R.string.opt_high,
        "Medium" to R.string.opt_medium,
        "Low" to R.string.opt_low
    )

    private val bitrateOptions = listOf("128", "192", "256", "320")

    val all: List<NovaTool> = listOf(
        // ---------- HERO ----------
        NovaTool(
            id = "video_to_audio",
            title = R.string.tool_video_to_audio,
            subtitle = R.string.subt_video_to_audio,
            icon = Icons.Filled.MusicNote,
            tint = c(0xFFFF5A5A),
            section = Section.HERO,
            mimes = videoMime,
            outExt = "",
            mime = "audio/mpeg",
            flow = listOf(FlowKind.VIDEO, FlowKind.AUDIO),
            fields = listOf(
                TField.Chips("fmt", R.string.field_format, Commands.AUDIO_TARGETS, "mp3"),
                TField.Chips("br", R.string.field_bitrate, bitrateOptions, "192")
            )
        ) { p, a, _, _ ->
            val fmt = p.s("fmt", "mp3")
            Commands.extractAudio(a, outQ(a, fmt), fmt, p.i("br", 192))
        },

        NovaTool(
            id = "audio_cutter",
            title = R.string.tool_audio_cutter,
            subtitle = R.string.subt_audio_cutter,
            icon = Icons.Filled.ContentCut,
            tint = c(0xFFFF7AB6),
            section = Section.HERO,
            mimes = audioMime,
            mime = "audio/mpeg",
            flow = listOf(FlowKind.AUDIO),
            fields = emptyList()
        ) { p, a, _, _ ->
            val startMs = p.f("start_ms", 0f)
            val endMs = p.f("end_ms", 30_000f)
            Commands.trimAudio(a, outQ(a, a.substringAfterLast(".", "mp3")), startMs / 1000f, (endMs - startMs) / 1000f)
        },

        NovaTool(
            id = "video_cutter",
            title = R.string.tool_video_cutter,
            subtitle = R.string.subt_video_cutter,
            icon = Icons.Filled.ContentCut,
            tint = c(0xFF7C6CFF),
            section = Section.HERO,
            mimes = videoMime,
            mime = "video/mp4",
            flow = listOf(FlowKind.VIDEO),
            fields = emptyList()
        ) { p, a, _, _ ->
            val startMs = p.f("start_ms", 0f)
            val endMs = p.f("end_ms", 30_000f)
            Commands.trimVideo(a, outQ(a, a.substringAfterLast(".", "mp4")), startMs / 1000f, (endMs - startMs) / 1000f)
        },

        // ---------- VIDEO ----------
        NovaTool(
            id = "video_converter",
            title = R.string.tool_video_converter,
            subtitle = R.string.subt_video_converter,
            icon = Icons.Filled.SwapHoriz,
            tint = c(0xFF4F8CFF),
            section = Section.VIDEO,
            mimes = videoMime,
            mime = "video/mp4",
            flow = listOf(FlowKind.VIDEO),
            fields = listOf(
                TField.Chips("fmt", R.string.field_format, listOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "gif"), "mp4"),
                TField.Chips("q", R.string.field_quality, listOf("High", "Medium", "Low"), "Medium", qualityLabels)
            )
        ) { p, a, _, _ ->
            val fmt = p.s("fmt", "mp4")
            Commands.convert(a, outQ(a, fmt), fmt, p.s("q", "Medium"))
        },

        NovaTool(
            id = "video_compressor",
            title = R.string.tool_video_compressor,
            subtitle = R.string.subt_video_compressor,
            icon = Icons.Filled.ArrowDownward,
            tint = c(0xFFFFB020),
            section = Section.VIDEO,
            mimes = videoMime,
            outExt = "mp4",
            mime = "video/mp4",
            hardware = true,
            flow = listOf(FlowKind.VIDEO),
            fields = listOf(
                TField.Chips(
                    "size", R.string.field_resolution,
                    listOf("Small 480p", "Medium 720p", "Large 1080p"), "Medium 720p",
                    mapOf(
                        "Small 480p" to R.string.opt_res_small,
                        "Medium 720p" to R.string.opt_res_medium,
                        "Large 1080p" to R.string.opt_res_large
                    )
                ),
                TField.Slide("mb", R.string.field_target_size, 2f, 200f, 20f, 48) { "%.0f MB".format(it) }
            )
        ) { p, a, _, dur ->
            val h = when (p.s("size", "Medium 720p")) {
                "Small 480p" -> 480
                "Large 1080p" -> 1080
                else -> 720
            }
            val kbps = if (dur > 0f) {
                ((p.f("mb", 20f) * 8f * 1000f / dur).toInt() - 128).coerceIn(150, 16_000)
            } else 2500
            Commands.compressCustom(a, outQ(a, "mp4"), h, kbps, true)
        },

        NovaTool(
            id = "video_speed",
            title = R.string.tool_video_speed,
            subtitle = R.string.subt_video_speed,
            icon = Icons.Filled.Speed,
            tint = c(0xFF36D399),
            section = Section.VIDEO,
            mimes = videoMime,
            outExt = "mp4",
            mime = "video/mp4",
            flow = listOf(FlowKind.VIDEO),
            fields = listOf(
                TField.Speed("factor", R.string.field_speed)
            )
        ) { p, a, _, _ ->
            val f = p.f("factor", 1.5f)
            Commands.speed(a, outQ(a, "mp4"), f, hasVideo = true, hasAudio = p.b("_aA", true))
        },

        NovaTool(
            id = "video_merger",
            title = R.string.tool_video_merger,
            subtitle = R.string.subt_video_merger,
            icon = Icons.Filled.CallMerge,
            tint = c(0xFF4FC3F7),
            section = Section.VIDEO,
            mimes = videoMime,
            twoInputs = true,
            mime = "video/mp4",
            flow = listOf(FlowKind.VIDEO),
            fields = listOf(
                TField.Chips("fmt", R.string.field_format, listOf("mp4", "mkv"), "mp4")
            )
        ) { p, a, b, dur ->
            Commands.merge(
                a, b ?: a, outQ(a, p.s("fmt", "mp4")),
                targetW = p.i("_w", 1280), targetH = p.i("_h", 720),
                durA = dur, durB = p.f("_dB", dur),
                aHasAudio = p.b("_aA", true), bHasAudio = p.b("_aB", true)
            )
        },

        NovaTool(
            id = "video_to_gif",
            title = R.string.tool_video_to_gif,
            subtitle = R.string.subt_video_to_gif,
            icon = Icons.Filled.Gif,
            tint = c(0xFFFF7AB6),
            section = Section.VIDEO,
            mimes = videoMime,
            outExt = "gif",
            mime = "image/gif",
            flow = listOf(FlowKind.VIDEO, FlowKind.GIF),
            fields = listOf(
                TField.Chips("fps", R.string.field_fps, listOf("8", "12", "15", "24"), "12"),
                TField.Chips("w", R.string.field_width, listOf("320", "480", "640", "720"), "480")
            )
        ) { p, a, _, _ ->
            Commands.gif(a, outQ(a, "gif"), p.i("fps", 12), p.i("w", 480))
        },

        NovaTool(
            id = "frame_capture",
            title = R.string.tool_frame_capture,
            subtitle = R.string.subt_frame_capture,
            icon = Icons.Filled.CameraAlt,
            tint = c(0xFF7C6CFF),
            section = Section.VIDEO,
            mimes = videoMime,
            outExt = "jpg",
            mime = "image/jpeg",
            flow = listOf(FlowKind.VIDEO, FlowKind.IMAGE),
            fields = listOf(
                TField.Num("t", R.string.field_time_s, "1", R.string.hint_time_s)
            )
        ) { p, a, _, _ ->
            Commands.frame(a, outQ(a, "jpg"), p.f("t", 1f))
        },

        // ---------- AUDIO ----------
        NovaTool(
            id = "audio_converter",
            title = R.string.tool_audio_converter,
            subtitle = R.string.subt_audio_converter,
            icon = Icons.Filled.SwapHoriz,
            tint = c(0xFF36D399),
            section = Section.AUDIO,
            mimes = audioMime,
            outExt = "",
            mime = "audio/mpeg",
            flow = listOf(FlowKind.AUDIO),
            fields = listOf(
                TField.Chips("fmt", R.string.field_format, Commands.AUDIO_TARGETS, "mp3"),
                TField.Chips("br", R.string.field_bitrate, bitrateOptions, "192")
            )
        ) { p, a, _, _ ->
            val fmt = p.s("fmt", "mp3")
            Commands.extractAudio(a, outQ(a, fmt), fmt, p.i("br", 192))
        },

        NovaTool(
            id = "audio_merger",
            title = R.string.tool_audio_merger,
            subtitle = R.string.subt_audio_merger,
            icon = Icons.Filled.CallMerge,
            tint = c(0xFF4F8CFF),
            section = Section.AUDIO,
            mimes = audioMime,
            twoInputs = true,
            outExt = "mp3",
            mime = "audio/mpeg",
            flow = listOf(FlowKind.AUDIO)
        ) { _, a, b, _ ->
            Commands.audioMerge(a, b!!, outQ(a, "mp3"))
        },

        NovaTool(
            id = "audio_speed",
            title = R.string.tool_audio_speed,
            subtitle = R.string.subt_audio_speed,
            icon = Icons.Filled.Speed,
            tint = c(0xFFFF5A5A),
            section = Section.AUDIO,
            mimes = audioMime,
            outExt = "mp3",
            mime = "audio/mpeg",
            flow = listOf(FlowKind.AUDIO),
            fields = listOf(
                TField.Speed("factor", R.string.field_speed)
            )
        ) { p, a, _, _ ->
            val f = p.f("factor", 1.5f)
            Commands.speed(a, outQ(a, "mp3"), f, hasVideo = false, hasAudio = true)
        },

        // ---------- IMAGE ----------
        NovaTool(
            id = "image_converter",
            title = R.string.tool_image_converter,
            subtitle = R.string.subt_image_converter,
            icon = Icons.Filled.SwapHoriz,
            tint = c(0xFF4FC3F7),
            section = Section.IMAGE,
            mimes = imageMime,
            mime = "image/jpeg",
            flow = listOf(FlowKind.IMAGE),
            fields = listOf(
                TField.Chips("fmt", R.string.field_format, listOf("jpg", "png", "bmp"), "png"),
                TField.Chips("q", R.string.field_quality, listOf("High", "Medium", "Low"), "High", qualityLabels)
                // Real-ESRGAN AI upscale hataya: models (*.bin/*.param) APK me nahi hain
                // → hamesha err_ai_no_model fail hota tha. Ye sirf FFmpeg image convert hai.
            )
        ) { p, a, _, _ ->
            val fmt = p.s("fmt", "png")
            Commands.convert(a, outQ(a, fmt), fmt, p.s("q", "High"))
        },

        // ---------- MORE ----------
        NovaTool(
            id = "metadata",
            title = R.string.tool_metadata,
            subtitle = R.string.subt_metadata,
            icon = Icons.Filled.Label,
            tint = c(0xFF36D399),
            section = Section.MORE,
            mimes = anyMime,
            mime = "video/mp4",
            flow = listOf(FlowKind.FILE),
            fields = listOf(
                TField.TextF("title", R.string.field_title, ""),
                TField.TextF("artist", R.string.field_artist, ""),
                TField.TextF("album", R.string.field_album, ""),
                TField.TextF("genre", R.string.field_genre, ""),
                TField.TextF("year", R.string.field_year, ""),
                TField.TextF("comment", R.string.field_comment, ""),
                TField.Toggle("clear", R.string.field_clear_tags, false)
            )
        ) { p, a, _, _ ->
            Commands.metadata(
                a, outQ(a, a.substringAfterLast(".", "mp4")),
                listOf(
                    "title" to p.s("title"),
                    "artist" to p.s("artist"),
                    "album" to p.s("album"),
                    "genre" to p.s("genre"),
                    "date" to p.s("year"),
                    "comment" to p.s("comment")
                ),
                clearExisting = p.b("clear", false)
            )
        },

        NovaTool(
            id = "watermark",
            title = R.string.tool_watermark,
            subtitle = R.string.subt_watermark,
            icon = Icons.Filled.BrandingWatermark,
            tint = c(0xFFFF5A5A),
            section = Section.MORE,
            mimes = videoMime,
            mime = "video/mp4",
            flow = listOf(FlowKind.VIDEO),
            fields = listOf(
                TField.TextF("text", R.string.field_watermark_text, "Nova"),
                TField.Chips(
                    "pos", R.string.field_position,
                    listOf("Top left", "Top right", "Center", "Bottom left", "Bottom right"),
                    "Bottom right",
                    mapOf(
                        "Top left" to R.string.pos_top_left,
                        "Top right" to R.string.pos_top_right,
                        "Center" to R.string.pos_center,
                        "Bottom left" to R.string.pos_bottom_left,
                        "Bottom right" to R.string.pos_bottom_right
                    )
                ),
                TField.Slide("size", R.string.field_size, 2f, 12f, 6f, 10) { "%.0f%%".format(it) },
                TField.Chips(
                    "color", R.string.field_color,
                    listOf("White", "Black", "Yellow", "Red"), "White",
                    mapOf(
                        "White" to R.string.color_white,
                        "Black" to R.string.color_black,
                        "Yellow" to R.string.color_yellow,
                        "Red" to R.string.color_red
                    )
                )
            )
        ) { p, a, _, _ ->
            val w = p.i("_w", 1280)
            val h = p.i("_h", 720)
            val png = Watermark.render(
                text = p.s("text", "Nova"),
                videoWidth = w,
                videoHeight = h,
                sizePercent = p.f("size", 6f),
                colorName = p.s("color", "White")
            ) ?: return@NovaTool ""
            Commands.watermark(
                a, outQ(a, a.substringAfterLast(".", "mp4")), png,
                position = p.s("pos", "Bottom right")
            )
        }
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }

    /** Input ka base naam (bina extension) */
    fun baseName(input: String) = input.substringAfterLast('/').substringBeforeLast('.')

    /** Output file name — tool params se extension nikalta hai */
    fun outputFileName(tool: NovaTool, input: String, params: Map<String, Any>): String {
        val ext = tool.outExt.ifEmpty {
            (params["fmt"] as? String)?.lowercase() ?: input.substringAfterLast('.', "mp4")
        }
        return "${baseName(input)}.$ext"
    }

    /** Output file extension (screen ke liye) */
    fun outputExt(tool: NovaTool, input: String, params: Map<String, Any>): String {
        val name = outputFileName(tool, input, params)
        return name.substringAfterLast('.')
    }

    private fun outPath(input: String, ext: String): String =
        Outputs.workFileName("${baseName(input)}.$ext")

    // RAW path (bina quotes) — Commands.* ke har builder me output pe q() lagta hai.
    // Pehle yahan extra quote tha: Java-side ext sniff `substringAfterLast('.')` ko
    // `mp3"` deta tha → aCodec "aac" → aac-into-mp3 mux error → audio_merger/audio_cutter
    // fail. Spaces wale path bhi dobara-quote hone se parseArguments me toot jate the.
    private fun outQ(input: String, ext: String): String = outPath(input, ext)
}
