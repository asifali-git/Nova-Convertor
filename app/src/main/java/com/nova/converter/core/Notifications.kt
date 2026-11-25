package com.nova.converter.core

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.nova.converter.MainActivity
import com.nova.converter.R
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Industry-style completion notifications — har tool ke liye alag friendly title
 * ("Video compressed", "Audios merged" …), file name + size ke saath, sound + vibration.
 * Tap karne pe file khulti hai (app se Save/Share kar sakte ho).
 */
object Notifications {

    private const val CH_DONE = "nova_done"
    private const val CH_FAIL = "nova_fail"
    private const val CH_DONE_SILENT = "nova_done_silent"
    private const val CH_FAIL_SILENT = "nova_fail_silent"
    private val nextId = AtomicInteger(9000)

    /**
     * Done/fail ke liye **ek hi fixed id** — pehle har job ka apna unique id
     * tha, jisse 10 conversions = 10 alag notifications aa jate the.
     *
     * Ab wahi notification update hoti rehti hai (user ko sirf latest dikhta
     * hai, shade bhi nahi bharta), aur saath me run count bhi badh jaata hai.
     */
    private const val ID_DONE = 9101
    private const val ID_FAIL = 9102

    /** Is session me kitne "done" hue — notification me "x2" jaisa dikhta hai. */
    private val doneCount = AtomicInteger(0)

    /**
     * App abhi screen par hai? `MainActivity.onResume/onPause` se set hota hai.
     *
     * Jab user app hi dekh raha hota hai to "File completed" notification
     * bekaar hoti hai — wahi cheez screen par dikh rahi hoti hai. Isliye
     * foreground me **post nahi** hoti. Ye Android ke daily notification
     * rate-limit ko bachane ka sabse bada hissa hai.
     */
    @Volatile
    var appOnScreen: Boolean = false

    private val VERBS = mapOf(
        "video_to_audio" to R.string.verb_audio_extracted,
        "audio_cutter" to R.string.verb_audio_trimmed,
        "video_cutter" to R.string.verb_video_trimmed,
        "video_converter" to R.string.verb_video_converted,
        "video_compressor" to R.string.verb_video_compressed,
        "video_speed" to R.string.verb_speed_changed,
        "video_merger" to R.string.verb_videos_merged,
        "video_to_gif" to R.string.verb_gif_created,
        "frame_capture" to R.string.verb_frames_captured,
        "audio_converter" to R.string.verb_audio_converted,
        "audio_merger" to R.string.verb_audios_merged,
        "audio_speed" to R.string.verb_speed_changed,
        "image_converter" to R.string.verb_image_converted,
        "metadata" to R.string.verb_metadata_updated,
        "watermark" to R.string.verb_watermark_added
    )

    /** toolId se exact title, warna label se samajh ke friendly title */
    fun verbFor(context: Context, toolId: String?, label: String = ""): String {
        toolId?.let { id -> VERBS[id]?.let { return context.getString(it) } }
        val l = label.lowercase()
        return context.getString(
            when {
                l.contains("gif") -> R.string.verb_gif_created
                l.contains("watermark") -> R.string.verb_watermark_added
                l.contains("metadata") -> R.string.verb_metadata_updated
                l.contains("frame") -> R.string.verb_frames_captured
                l.contains("speed") -> R.string.verb_speed_changed
                l.contains("merge") -> R.string.verb_files_merged
                l.contains("cutter") || l.contains("trim") -> R.string.verb_file_trimmed
                l.contains("extract") || (l.contains("audio") && l.contains("video")) -> R.string.verb_audio_extracted
                l.contains("image") -> R.string.verb_image_converted
                l.contains("compress") -> R.string.verb_video_compressed
                l.contains("video") -> R.string.verb_video_converted
                l.contains("convert") -> R.string.verb_file_converted
                else -> R.string.verb_file_ready
            }
        )
    }

    /** Channels banao (idempotent) — sound + vibration ON, high importance */
    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            fun alert(id: String, name: String, desc: String, withSound: Boolean): NotificationChannel =
                NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = desc
                    if (withSound) {
                        setSound(sound, attrs)
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0, 180, 120, 180)
                        enableLights(true)
                        lightColor = Color.parseColor("#22C55E")
                    } else {
                        setSound(null, null)
                        enableVibration(false)
                        enableLights(false)
                    }
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }

            nm.createNotificationChannel(
                alert(CH_DONE, context.getString(R.string.ch_done), context.getString(R.string.ch_done_desc), true)
            )
            nm.createNotificationChannel(
                alert(CH_DONE_SILENT, context.getString(R.string.ch_done_silent), context.getString(R.string.ch_done_silent_desc), false)
            )
            nm.createNotificationChannel(
                alert(CH_FAIL, context.getString(R.string.ch_fail), context.getString(R.string.ch_fail_desc), true)
            )
            nm.createNotificationChannel(
                alert(CH_FAIL_SILENT, context.getString(R.string.ch_fail_silent), context.getString(R.string.ch_fail_silent_desc), false)
            )
        } catch (_: Throwable) {
        }
    }

    private fun channel(soundOn: Boolean, fail: Boolean): String = when {
        soundOn && fail -> CH_FAIL
        soundOn -> CH_DONE
        fail -> CH_FAIL_SILENT
        else -> CH_DONE_SILENT
    }

    /** File ban gaya — sound ke saath friendly notification + file name */
    fun completed(context: Context, verb: String, outputPath: String, mime: String) {
        // App khud screen par hai -> user ko result dikh hi raha hai, notification
        // bekaar hai. Isse ek batch me 20 conversions par sirf 1-2 notification
        // bachte hain (background me), jo Android ke daily limit se bahar rehta hai.
        if (appOnScreen) return
        try {
            val app = context.applicationContext
            ensure(app)
            val soundOn = Settings.soundEnabled.value
            val file = File(outputPath)
            val name = file.name
            val size = if (file.exists()) file.length() else 0L
            val view = viewIntent(app, outputPath, mime)
            val openApp = appIntent(app)
            val n = doneCount.incrementAndGet()
            val b = NotificationCompat.Builder(app, channel(soundOn, fail = false))
                .setSmallIcon(R.drawable.ic_stat_done)
                .setColor(Color.parseColor("#22C55E"))
                .setContentTitle(verb)
                .setContentText(context.getString(R.string.notif_file_size, name, sizeLabel(size)))
                // Batch me 2nd file se notification par "x2", "x3"... dikhta hai
                .setSubText(if (n > 1) "x$n" else null)
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        context.getString(R.string.notif_done_body, name, sizeLabel(size))
                    )
                )
                .setContentIntent(view ?: openApp)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(0, context.getString(R.string.notif_open_app), openApp)
            if (soundOn) {
                b.priority = NotificationCompat.PRIORITY_HIGH
                b.setDefaults(
                    NotificationCompat.DEFAULT_SOUND or
                        NotificationCompat.DEFAULT_VIBRATE or
                        NotificationCompat.DEFAULT_LIGHTS
                )
                b.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                b.setVibrate(longArrayOf(0, 180, 120, 180))
            } else {
                b.priority = NotificationCompat.PRIORITY_DEFAULT
                b.setSilent(true)
            }
            // Fixed id -> nayi notification purani ki jagah update hoti hai
            manager(app).notify(ID_DONE, b.build())
        } catch (_: Throwable) {
        }
    }

    /** Job fail ho gayi — alert notification */
    fun failed(context: Context, verb: String, outputPath: String, reason: String) {
        // Fail hamesha batana zaroori hai (foreground me bhi), lekin id fixed hai
        // taaki 10 failures = 10 alag notifications na ban jayein.
        try {
            val app = context.applicationContext
            ensure(app)
            val soundOn = Settings.soundEnabled.value
            val name = File(outputPath).name
            val detail = reason.take(160).ifBlank { context.getString(R.string.err_unknown) }
            val b = NotificationCompat.Builder(app, channel(soundOn, fail = true))
                .setSmallIcon(R.drawable.ic_stat_error)
                .setColor(Color.parseColor("#EF4444"))
                .setContentTitle(context.getString(R.string.history_task_failed))
                .setContentText(context.getString(R.string.notif_fail_body, name, detail))
                .setSubText(verb)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$name\n$detail"))
                .setContentIntent(appIntent(app))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(0, context.getString(R.string.notif_open_app), appIntent(app))
            if (soundOn) {
                b.priority = NotificationCompat.PRIORITY_HIGH
                b.setDefaults(
                    NotificationCompat.DEFAULT_SOUND or
                        NotificationCompat.DEFAULT_VIBRATE or
                        NotificationCompat.DEFAULT_LIGHTS
                )
                b.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                b.setVibrate(longArrayOf(0, 250, 150, 250))
            } else {
                b.priority = NotificationCompat.PRIORITY_DEFAULT
                b.setSilent(true)
            }
            manager(app).notify(ID_FAIL, b.build())
        } catch (_: Throwable) {
        }
    }

    /** POST_NOTIFICATIONS (API 33+) granted hai? */
    fun permissionGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun manager(context: Context) =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun appIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 4712,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /**
     * Notification tap → app ka built-in player (Gallery/Photos me external
     * intent pehle bhejta tha, jo app ke "sab andar hi chale" rule ko todta tha).
     * File exist nahi karti to null — caller tab app home screen kholta hai.
     */
    private fun viewIntent(context: Context, path: String, mime: String): PendingIntent? {
        return try {
            val file = File(path)
            if (!file.exists() || file.length() <= 0L) return null
            val view = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.EXTRA_OPEN_MEDIA, file.absolutePath)
                putExtra(MainActivity.EXTRA_OPEN_MEDIA_MIME, mime)
            }
            PendingIntent.getActivity(
                context, nextId.getAndIncrement(), view,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun sizeLabel(bytes: Long): String = when {
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
