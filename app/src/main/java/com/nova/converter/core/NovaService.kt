package com.nova.converter.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nova.converter.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service — conversion chal rahi ho to app minimize karke bhi
 * process zinda rehta hai + notification pe realtime % dikhta hai.
 */
class NovaService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collectJob: Job? = null
    private var lastPercent = -1
    private var lastCount = -1

    override fun onCreate() {
        super.onCreate()
        createChannel()
        try {
            startForeground(NOTIF_ID, buildNotification("Nova Converter", 0, -1, 0))
        } catch (t: Throwable) {
            // FGS permission/type issue → service band, app zinda
            stopSelf()
            return
        }
        collectJob = scope.launch {
            ProgressBus.state.collect { snap ->
                if (snap == null) {
                    ProgressBus.markServiceStopped()
                    stopSelf()
                } else {
                    val p = snap.percent.toInt()
                    if (p != lastPercent || snap.count != lastCount) {
                        lastPercent = p
                        lastCount = snap.count
                        notificationManager().notify(
                            NOTIF_ID,
                            buildNotification(snap.label, p, p, snap.count)
                        )
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            // pehle tasks CANCELLED mark, phir engines kill — ulta order
            // completion callback ko race me FAILED likhwata tha (failed(255))
            Queue.cancelAll()
            FfmpegEngine.cancelAll()
            ProgressBus.clear()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        collectJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notificationManager() =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Conversion progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Background conversion progress" }
            notificationManager().createNotificationChannel(ch)
        }
    }

    private fun buildNotification(title: String, percent: Int, progress: Int, count: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_QUEUE, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancel = PendingIntent.getService(
            this, 1, Intent(this, NovaService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = when {
            progress < 0 -> "Preparing…"
            count > 1 -> "$count jobs • $percent% avg"
            else -> "$percent% complete"
        }
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText("Nova Converter — jobs running in background")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Cancel", cancel)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        if (progress >= 0) {
            b.setProgress(100, percent, false)
        } else {
            b.setProgress(0, 0, true)
        }
        return b.build()
    }

    companion object {
        const val CHANNEL_ID = "nova_jobs"
        const val NOTIF_ID = 4711
        const val ACTION_CANCEL = "com.nova.converter.CANCEL_JOB"

        fun start(context: Context) {
            ProgressBus.bind(context)
            ProgressBus.startService()
        }
    }
}
