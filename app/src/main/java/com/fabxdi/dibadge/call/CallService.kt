package com.fabxdi.dibadge.call

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.fabxdi.dibadge.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Keeps a call alive (microphone and camera included) while the app is in the background, and shows the
 * "Voice call · 2:41" notification with an End button. It starts when a call starts or is answered and
 * stops itself when the call is over.
 */
class CallService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watchJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val engine = CallEngine.get(this)

        if (intent?.action == ACTION_END) {
            engine.hangUp()
            return START_NOT_STICKY
        }

        ensureChannel()
        startAsForeground(buildNotification(engine.state.value))

        watchJob?.cancel()
        watchJob = scope.launch {
            engine.state.collect { s ->
                if (s.phase == CallPhase.IDLE || s.phase == CallPhase.ENDED) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID, buildNotification(s))
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startAsForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Only the types we are allowed to use right now (Android refuses a type without its permission)
            var type = 0
            if (granted(Manifest.permission.RECORD_AUDIO)) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (granted(Manifest.permission.CAMERA)) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            if (type != 0) startForeground(NOTIFICATION_ID, n, type) else startForeground(NOTIFICATION_ID, n)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun buildNotification(s: CallState): Notification {
        val kind = if (s.isVideo) "Video call" else "Voice call"
        val text = when (s.phase) {
            CallPhase.STARTING, CallPhase.RINGING_OUT -> "Calling…"
            CallPhase.CONNECTING -> "Connecting…"
            CallPhase.CONNECTED -> "$kind · ${CallRepository.formatDuration(s.seconds)}"
            else -> kind
        }
        val open = Intent(this, CallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            this, 10, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val end = Intent(this, CallService::class.java).apply { action = ACTION_END }
        val endPending = PendingIntent.getService(
            this, 11, end, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(s.peerName.ifBlank { "diBadge call" })
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(s.phase == CallPhase.CONNECTED)
            .setWhen(System.currentTimeMillis() - s.seconds * 1000)
            .setContentIntent(openPending)
            .addAction(0, "End call", endPending)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Calls in progress", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows while you are on a diBadge call"
            }
        )
    }

    companion object {
        private const val CHANNEL_ID = "calls_ongoing"
        private const val NOTIFICATION_ID = 0x2000_0002
        private const val ACTION_END = "com.fabxdi.dibadge.call.END"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, CallService::class.java))
        }
    }
}