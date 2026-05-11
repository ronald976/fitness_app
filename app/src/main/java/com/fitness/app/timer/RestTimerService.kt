package com.fitness.app.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.fitness.app.MainActivity
import com.fitness.app.R
import com.fitness.app.data.preferences.AppPreferences
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground service that backs the rest-timer countdown. Posts a chronometer notification
 * (so the system shade shows a live countdown when the app is backgrounded) and plays a
 * short chime via [ToneGenerator] on the alarm stream when the rest period elapses — that
 * routes through STREAM_ALARM, which keeps playing in vibrate / silent mode where the
 * default notification sound would be muted.
 */
@AndroidEntryPoint
class RestTimerService : Service() {

    @Inject lateinit var appPreferences: AppPreferences

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
        getSystemService(NotificationManager::class.java)?.cancel(NOTIF_ID_DONE)
        val endAt = System.currentTimeMillis() + seconds * 1000L
        startForeground(NOTIF_ID_COUNTDOWN, buildCountdownNotification(endAt))

        job?.cancel()
        job = scope.launch {
            delay(seconds * 1000L)
            if (appPreferences.chimeEnabledNow()) {
                playChime()
            }
            if (!isAppForeground.get()) {
                postDoneNotification()
            }
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

    private fun playChime() {
        runCatching {
            val tg = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            // Two-burst ack tone — sounds chime-like and stays well under a second.
            tg.startTone(ToneGenerator.TONE_PROP_ACK, 400)
            scope.launch {
                try {
                    delay(600L)
                } finally {
                    tg.release()
                }
            }
        }
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
            .setTimeoutAfter(DONE_NOTIFICATION_TIMEOUT_MS)
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

        // Old "rest_timer_done" channel had its sound URI baked in at create time
        // (channel sound can't change). Drop it so existing installs pick up the new
        // silent-channel + ToneGenerator setup we use today.
        nm.getNotificationChannel(LEGACY_CHANNEL_DONE)?.let {
            nm.deleteNotificationChannel(LEGACY_CHANNEL_DONE)
        }

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
            nm.createNotificationChannel(NotificationChannel(
                CHANNEL_DONE,
                "Rest done",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Vibrates and shows a heads-up when rest is complete. " +
                    "The chime itself plays through the alarm stream."
                // Sound is muted on the channel — the service plays a short tone via
                // ToneGenerator on STREAM_ALARM so it survives vibrate/silent mode.
                setSound(null, null)
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
        const val CHANNEL_DONE = "rest_timer_done_v2"
        private const val LEGACY_CHANNEL_DONE = "rest_timer_done"
        private const val DONE_NOTIFICATION_TIMEOUT_MS = 20_000L
        private val isAppForeground = AtomicBoolean(false)

        fun setAppForeground(foreground: Boolean) {
            isAppForeground.set(foreground)
        }

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
