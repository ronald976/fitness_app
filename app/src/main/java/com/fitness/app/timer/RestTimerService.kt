package com.fitness.app.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.fitness.app.MainActivity
import com.fitness.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service that backs the rest-timer countdown. Posts a chronometer notification
 * (so the system shade shows a live countdown when the app is backgrounded) and plays a
 * notification sound when the rest period elapses.
 */
class RestTimerService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val seconds = intent.getIntExtra(EXTRA_SECONDS, 60).coerceAtLeast(1)
                start(seconds)
            }
            ACTION_STOP -> stop()
            else -> stop()
        }
        return START_NOT_STICKY
    }

    private fun start(seconds: Int) {
        ensureChannels()
        val endAt = System.currentTimeMillis() + seconds * 1000L
        startForeground(NOTIF_ID_COUNTDOWN, buildCountdownNotification(endAt))

        job?.cancel()
        job = scope.launch {
            delay(seconds * 1000L)
            postDoneNotification()
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stop() {
        job?.cancel()
        job = null
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java)?.cancel(NOTIF_ID_DONE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildCountdownNotification(endAt: Long): Notification =
        NotificationCompat.Builder(this, CHANNEL_COUNTDOWN)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle("Resting")
            .setContentText("Tap to return to workout")
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(endAt)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(returnIntent())
            .build()

    private fun postDoneNotification() {
        val notif = NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle("Rest done")
            .setContentText("Time for the next set")
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(returnIntent())
            .build()
        getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID_DONE, notif)
    }

    private fun returnIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun ensureChannels() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_COUNTDOWN) == null) {
            nm.createNotificationChannel(NotificationChannel(
                CHANNEL_COUNTDOWN,
                "Rest timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Countdown shown while resting between sets"
                setSound(null, null)
                enableVibration(false)
            })
        }
        if (nm.getNotificationChannel(CHANNEL_DONE) == null) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            nm.createNotificationChannel(NotificationChannel(
                CHANNEL_DONE,
                "Rest done",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Plays a sound when rest is complete"
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    attrs
                )
                enableVibration(true)
            })
        }
    }

    companion object {
        const val ACTION_START = "com.fitness.app.action.REST_START"
        const val ACTION_STOP = "com.fitness.app.action.REST_STOP"
        const val EXTRA_SECONDS = "seconds"
        const val NOTIF_ID_COUNTDOWN = 4242
        const val NOTIF_ID_DONE = 4243
        const val CHANNEL_COUNTDOWN = "rest_timer_countdown"
        const val CHANNEL_DONE = "rest_timer_done"

        fun start(context: Context, seconds: Int) {
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SECONDS, seconds)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: IllegalStateException) {
                // App was backgrounded and service isn't running — nothing to stop.
            }
        }
    }
}
