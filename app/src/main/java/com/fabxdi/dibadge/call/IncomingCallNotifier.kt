package com.fabxdi.dibadge.call

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.fabxdi.dibadge.R

/**
 * The ringing notification for an incoming call. On a locked phone it opens the call screen over the
 * lock screen (full-screen intent); on an unlocked phone it drops down with Accept and Decline.
 */
object IncomingCallNotifier {
    const val CHANNEL_ID = "calls_incoming"
    private const val NOTIFICATION_ID = 0x2000_0001

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Rings when someone calls you in diBadge"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 600, 800, 600)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
    }

    fun show(context: Context, callId: String, callerName: String, video: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)

        val open = Intent(context, CallActivity::class.java).apply {
            putExtra(CallActivity.EXTRA_CALL_ID, callId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            context, 1, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Accept opens the call screen (a call needs the screen), and answers from there
        val accept = Intent(open).apply { putExtra(CallActivity.EXTRA_ACCEPT, true) }
        val acceptPending = PendingIntent.getActivity(
            context, 2, accept, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val decline = Intent(context, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_DECLINE
            putExtra(CallActivity.EXTRA_CALL_ID, callId)
        }
        val declinePending = PendingIntent.getBroadcast(
            context, 3, decline, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val caller = Person.Builder().setName(callerName).setImportant(true).build()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(callerName)
            .setContentText(if (video) "Incoming video call" else "Incoming voice call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(45_000)
            .setFullScreenIntent(openPending, true)
            .setContentIntent(openPending)
            .addPerson(caller)

        builder.addAction(0, "Decline", declinePending).addAction(0, "Accept", acceptPending)

        val notification = builder.build()
        // Keep ringing until it is answered, declined or times out
        notification.flags = notification.flags or android.app.Notification.FLAG_INSISTENT
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission was taken away a moment ago: nothing to show
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}