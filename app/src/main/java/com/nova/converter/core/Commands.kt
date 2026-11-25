package com.nova.converter.core

/**
 * FFmpeg command builders - all features in one place (frontend never duplicates them).
 * Sources: EzConvert (convert/trim/frame/compress), Compressor (presets/bitrate),
 * mpv/NextPlayer (speed) + extras (merge, metadata, watermark).
 */
object Commands {

    val VIDEO_TARGETS = listOf("mp4", "mkv", "webm", "avi", "flv", "mov", "3gp", "gif")
    val AUDIO_TARGETS = listOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus")
    // webp output nahi hai — hamari FFmpeg build me libwebp encoder hi nahi hai.
    // webp INPUT jaari hai (native decoder maujood hai), sirf export option hataya gaya.
    val IMAGE_TARGETS = listOf("jpg", "png", "bmp")

    fun q(path: String) = "\"$path\""

    fun vCodec(format: String) = when (format) {
        "webm" -> "libvpx-vp9"
        "avi", "flv" -> "mpeg4"
        else -> "libx264"
    }

    fun vAudioCodec(format: String) = when (format) {
        "webm" -> "libopus"
        "avi", "flv" -> "libmp3lame"
        else -> "aac"
    }

    fun aCodec(format: String) = when (format) {
        "mp3" -> "libmp3lame"
        "m4a", "aac" -> "aac"
        "wav" -> "pcm_s16le"
        "flac" -> "flac"
        "ogg" -> "libvorbis"
        "opus" -> "libopus"
        else -> "aac"
    }

    /** Quality presets */
    fun crfFor(quality: String) = when (quality) {
        "High" -> "18"
        "Low" -> "30"
        else -> "23"
    }

    /**
     * Lossy audio codecs ke liye explicit target bitrate — `wav`/`flac` lossless hain,
     * unke liye koi bitrate flag nahi. Yahan kabhi `-q:a` nahi lagana: libmp3lame par
     * wo VBR-quality option hai aur selected `-b:a` ko override kar deta tha.
     */
    fun audioBitrateFor(format: String, quality: String): String {
        val codec = aCodec(format)
        if (codec == "pcm_s16le" || codec == "flac") return ""
        val k = when (quality) {
            "Low" -> 128
            "Medium" -> 160
            else -> 192
        }
        return "-b:a ${k}k"
    }

    /** Video -> any video / any audio / any image / gif */
    fun convert(input: String, output: String, target: String, quality: String): String {
        val i = q(input); val o = q(output)
        return when {
            target == "gif" ->
                "-y -i $i -vf fps=12,scale=480:-1:flags=lanczos -loop 0 $o"
            target in IMAGE_TARGETS -> {
                // png = lossless (koi quality flag nahi), baaki -q:v 2..9 (lower = better)
                // -frames:v 1: animated input (gif/webp) se ek hi frame export ho,
                // warna image2 muxer single filename pe multiple frame likh nahi pata.
                val qv = if (target == "png") "" else "-q:v ${if (quality == "High") "2" else "5"} "
                "-y -i $i -frames:v 1 $qv$o"
            }
            target in AUDIO_TARGETS ->
                "-y -i $i -vn -c:a ${aCodec(target)} ${audioBitrateFor(target, quality)} $o"
            else ->
                "-y -i $i -c:v ${vCodec(target)} -preset veryfast -crf ${crfFor(quality)} " +
                        "-c:a ${vAudioCodec(target)} -b:a 128k -movflags +faststart $o"
        }
    }

    /** Custom compression (resolution + bitrate) - FFmpeg path, single pass */
    fun compressCustom(
        input: String,
        output: String,
        height: Int,
        bitrateKbps: Int,
        audio: Boolean
    ): String {
        val i = q(input); val o = q(output)
        val vf = "-vf scale=-2:$height"
        val a = if (audio) "-c:a aac -b:a 96k" else "-an"
        return "-y -i $i $vf -c:v libx264 -preset veryfast -b:v ${bitrateKbps}k $a -movflags +faststart $o"
    }

    /**
     * Frame-accurate video trim (re-encode) - timeline cursor se exact frame pe cut.
     * Input-side -ss fast seek + re-encode = precise cut point.
     */
    fun trimVideo(input: String, output: String, startSec: Float, durSec: Float): String {
        val ext = output.substringAfterLast('.', "mp4")
        val d = durSec.coerceAtLeast(0.05f)
        return "-y -ss $startSec -i ${q(input)} -t $d " +
                "-c:v ${vCodec(ext)} -preset veryfast -crf 20 " +
                "-c:a ${vAudioCodec(ext)} -b:a 160k -movflags +faststart ${q(output)}"
    }

    /** Frame-accurate audio trim (re-encode) */
    fun trimAudio(input: String, output: String, startSec: Float, durSec: Float): String {
        val ext = output.substringAfterLast('.', "mp3")
        val d = durSec.coerceAtLeast(0.05f)
        return "-y -ss $startSec -i ${q(input)} -t $d " +
                "-c:a ${aCodec(ext)} ${q(output)}"
    }

    /** Speed tool ka range — UI (slider) aur command builder ek hi source se. */
    const val SPEED_MIN = 0.5f
    const val SPEED_MAX = 100f

    /** Locale pe depend na kare — ar locale me Arabic-Indic digits aa jate hain (FFmpeg toot jata hai) */
    private fun n6(v: Float) = String.format(java.util.Locale.US, "%.6f", v)
    private fun n4(v: Float) = String.format(java.util.Locale.US, "%.4f", v)

    /**
     * `atempo` ka per-instance range (0.5, 2.0] hai, to >2x ke liye chain chahiye.
     * Factor ko (0.5, 2.0] ke andar karke list wapas deta hai — product = factor.
     */
    fun atempoChain(factor: Float): List<Float> {
        val out = ArrayList<Float>(8)
        var f = factor.coerceIn(SPEED_MIN, SPEED_MAX)
        while (f > 2.0f) { out.add(2.0f); f /= 2f }
        while (f < 0.5f) { out.add(0.5f); f *= 2f }
        out.add(f)
        return out
    }

    /**
     * Speed change — 0.5x … 100x.
     *
     * Do load-bearing bugs isse pehle the (field report: "3x/4x pe error"):
     *
     *  1. **Chain me filter ka naam repeat nahi hota tha.** Purana code
     *     `atempo=2.0,1.500` banta tha — FFmpeg ka filtergraph parser ise
     *     `atempo=2.0` + ek bogus filter "1.500" padhta hai →
     *     `No such filter: '1.500'` → har >2x speed fail. 0.5/0.75/1.5/2 ek
     *     instance the isliye chal rahe the. Ab har link `atempo=...` hai.
     *  2. **`String.format` default locale lete hain.** App `Locale.setDefault`
     *     karta hai (MainActivity), to `ar`/`ur`/`hi` locale me `"%.3f"` Arabic-Indic
     *     digits de deta tha (`PTS/٣٫٠`) → FFmpeg parse fail, **koi bhi speed** nahi.
     *     Ab har number `Locale.US` se.
     *
     *  3. **Args ko string se chipkaaya ja raha tha.** Audio branch
     *     `"-y -i $i -vn $audio$o"` banata tha, jahan `audioBitrateFor()` ka
     *     return `-b:a 160k` (trailing space ke bina) tha — iska matlab output
     *     filename `160k"out.mp3"` ban jaati thi. FFmpeg usse ek hi argument
     *     samajhta hai → har audio speed pe error. Ab args list se join hote
     *     hain, isliye spacing ka sawaal hi nahi banta.
     *
     * Video side: `setpts` timestamps ko factor se compress karta hai — output fps
     * naturally badhta hai (30fps @ 4x → 120fps). Yahan `-r`/`-vsync cfr`
     * NAHI lagana: cfr input fps ko target maankar frames *duplicate* karta hai
     * (speed-up me har frame 4 baar aani chahiye), file bhari ban jati hai aur
     * duration galat padta hai. `-fps_mode passthrough` sabse sahi hai — sabhi
     * frames apne naye timestamps ke saath pas ho jati hain. (`-vsync` FFmpeg 7
     * me deprecated hai, isliye use nahi kiya.)
     *
     * Codecs ab explicit hain — pehle container defaults par depend the, jo
     * device-specific the (mkv me mp4 default hi nahi).
     */
    fun speed(
        input: String,
        output: String,
        factor: Float,
        hasVideo: Boolean = true,
        hasAudio: Boolean = true
    ): String {
        val f = factor.coerceIn(SPEED_MIN, SPEED_MAX)
        val ext = output.substringAfterLast('.', "mp4")
        val atempo = atempoChain(f).joinToString(",") { "atempo=${n4(it)}" }

        // Args hamesha list me — string interpolation se chipke "160k\"out.mp3\""
        // ban sakta hai, jo FFmpeg me ek hi (garbage) argument ban jata hai.
        val args = mutableListOf("-y", "-i", q(input))
        if (hasVideo) args += listOf("-filter:v", "setpts=PTS/${n6(f)}")
        else args += "-vn"

        if (hasAudio) {
            args += listOf("-filter:a", atempo, "-c:a", if (hasVideo) vAudioCodec(ext) else aCodec(ext))
            val bitrate = if (hasVideo) "-b:a 160k" else audioBitrateFor(ext, "Medium")
            if (bitrate.isNotBlank()) args += bitrate
        } else {
            args += "-an"
        }

        if (hasVideo) {
            args += listOf(
                "-fps_mode", "passthrough",
                "-c:v", vCodec(ext),
                "-preset", "veryfast",
                "-crf", crfFor("Medium"),
                "-movflags", "+faststart"
            )
        }
        args += q(output)
        return args.joinToString(" ")
    }

    fun frame(input: String, output: String, timeSec: Float): String =
        "-y -ss $timeSec -i ${q(input)} -frames:v 1 -q:v 2 ${q(output)}"

    /**
     * Merge 2 videos - robust: resolution/fps normalize, audio stream missing ho to
     * silence pad, sample-rate mismatch nahi hota. Params (via build map):
     *  "_w"/"_h" = target size (pehle video ke), "_dA"/"_dB" = durations (s),
     *  "_aA"/"_aB" = has-audio flags.
     */
    fun merge(
        a: String,
        b: String,
        output: String,
        targetW: Int,
        targetH: Int,
        durA: Float,
        durB: Float,
        aHasAudio: Boolean,
        bHasAudio: Boolean
    ): String {
        val tw = (targetW.coerceAtLeast(160) / 2) * 2
        val th = (targetH.coerceAtLeast(160) / 2) * 2
        val vNorm = "scale=$tw:$th:force_original_aspect_ratio=decrease," +
                "pad=$tw:$th:(ow-iw)/2:(oh-ih)/2,setsar=1,fps=30"
        val aNorm = "aresample=44100,aformat=sample_fmts=fltp:channel_layouts=stereo"
        fun video(idx: Int) = "[$idx:v]$vNorm[v$idx]"
        fun audio(idx: Int, hasAudio: Boolean, dur: Float): String =
            if (hasAudio) "[$idx:a]$aNorm[a$idx]"
            else "anullsrc=r=44100:cl=stereo,atrim=0:${n6(dur.coerceAtLeast(0.1f))}," +
                    "asetpts=PTS-STARTPTS[a$idx]"

        val fc = buildString {
            append(video(0)); append(";")
            append(video(1)); append(";")
            append(audio(0, aHasAudio, durA)); append(";")
            append(audio(1, bHasAudio, durB)); append(";")
            append("[v0][a0][v1][a1]concat=n=2:v=1:a=1[v][a]")
        }
        val ext = output.substringAfterLast('.', "mp4")
        return "-y -i ${q(a)} -i ${q(b)} -filter_complex \"${fc}\" " +
                "-map \"[v]\" -map \"[a]\" " +
                "-c:v ${vCodec(ext)} -preset veryfast -crf 21 " +
                "-c:a ${vAudioCodec(ext)} -b:a 160k ${q(output)}"
    }

    /** Metadata editor - tags replace karta hai, clearExisting se purane tags bhi hatta do */
    fun metadata(
        input: String,
        output: String,
        tags: List<Pair<String, String>>,
        clearExisting: Boolean
    ): String {
        val md = tags
            .filter { it.second.isNotBlank() }
            .joinToString(" ") { "-metadata ${it.first}=${q(escapeTagValue(it.second))}" }
        val clear = if (clearExisting) "-map_metadata -1 " else ""
        return "-y -i ${q(input)} $clear$md -c copy ${q(output)}"
    }

    private fun escapeTagValue(v: String) =
        v.replace("\\", "\\\\").replace("\"", "\\\"")

    /**
     * Text watermark — pehle [Watermark] Android Canvas se PNG banata hai, yahan
     * FFmpeg `overlay` filter se video pe chipka dete hain.
     *
     * `drawtext` isliye nahi: hamari FFmpeg build me wo filter compile hi nahi hai
     * (libfreetype ke hone ke bawajood), to purana command hamesha fail hota tha.
     */
    fun watermark(input: String, output: String, overlayPath: String, position: String): String {
        val (x, y) = when (position) {
            "Top left" -> "20" to "20"
            "Top right" -> "W-w-20" to "20"
            "Center" -> "(W-w)/2" to "(H-h)/2"
            "Bottom left" -> "20" to "H-h-20"
            else -> "W-w-20" to "H-h-20"     // Bottom right
        }
        val ext = output.substringAfterLast('.', "mp4")
        val fc = "[0:v][1:v]overlay=$x:$y[v]"
        return "-y -i ${q(input)} -i ${q(overlayPath)} -filter_complex \"$fc\" " +
                "-map \"[v]\" -map 0:a? " +
                "-c:v ${vCodec(ext)} -preset veryfast -crf 20 -c:a copy ${q(output)}"
    }

    /** Video → audio with exact bitrate (128k/192k/256k/320k) */
    fun extractAudio(input: String, output: String, format: String, bitrateK: Int): String {
        val codec = aCodec(format)
        val lossless = codec == "pcm_s16le" || codec == "flac"
        val br = if (lossless) "" else "-b:a ${bitrateK}k"
        return "-y -i ${q(input)} -vn -c:a $codec $br ${q(output)}"
    }

    /** Audio merger - sample-rate/channel normalize karke concat (mismatch ab fail nahi) */
    fun audioMerge(a: String, b: String, output: String): String {
        val fc = "[0:a]aresample=44100,aformat=sample_fmts=fltp:channel_layouts=stereo[a0];" +
                "[1:a]aresample=44100,aformat=sample_fmts=fltp:channel_layouts=stereo[a1];" +
                "[a0][a1]concat=n=2:v=0:a=1[a]"
        return "-y -i ${q(a)} -i ${q(b)} -filter_complex \"${fc}\" " +
                "-map \"[a]\" -c:a ${aCodec(output.substringAfterLast('.', "mp3"))} ${q(output)}"
    }

    /** Video → GIF with custom fps + width */
    fun gif(input: String, output: String, fps: Int, width: Int): String =
        "-y -i ${q(input)} -vf \"fps=$fps,scale=${width}:-1:flags=lanczos\" -loop 0 ${q(output)}"
}
