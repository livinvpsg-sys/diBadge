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
import com.fabxdi.dibadge.util.NotifyGroup

/**
 * "Name is calling the group". It rings like a normal call: on a locked phone the ring screen opens over the
 * lock screen (full-screen intent), on an unlocked phone a heads-up with Join and Decline drops down.
 * It goes away after 45 seconds. A group call you never joined is left as a "Missed group call" notice.
 */
object GroupCallNotifier {
    // A new channel id: the old "calls_group" channel was created without the insistent ring
    const val CHANNEL_ID = "calls_group_ring"
    private const val MISSED_CHANNEL_ID = "calls_group_missed"
    private const val NOTIFICATION_ID = 3100001
    private const val MISSED_ID = 3100002

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(CHANNEL_ID, "Group calls", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Rings when a call starts in one of your groups"
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
        if (nm.getNotificationChannel(MISSED_CHANNEL_ID) == null) {
            val channel = NotificationChannel(MISSED_CHANNEL_ID, "Missed group calls", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A group call that started while you were away"
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context, callId: String, callerName: String, video: Boolean) {
        if (callId.isBlank() || !canPost(context)) return
        ensureChannels(context)
        val name = callerName.ifBlank { "Someone" }

        // The ring screen (over the lock screen, or when the notification is tapped)
        val ring = Intent(context, CallActivity::class.java).apply {
            putExtra(CallActivity.EXTRA_GROUP_RING_ID, callId)
            putExtra(CallActivity.EXTRA_GROUP_RING_NAME, name)
            putExtra(CallActivity.EXTRA_GROUP_RING_VIDEO, video)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val ringPending = PendingIntent.getActivity(
            context, NOTIFICATION_ID, ring, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Join: opens the call screen and joins
        val join = Intent(context, CallActivity::class.java).apply {
            putExtra(CallActivity.EXTRA_GROUP_JOIN_ID, callId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val joinPending = PendingIntent.getActivity(
            context, NOTIFICATION_ID + 1, join, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Decline: the screen opens for an instant and closes again
        val decline = Intent(context, CallActivity::class.java).apply {
            putExtra(CallActivity.EXTRA_GROUP_DECLINE_ID, callId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val declinePending = PendingIntent.getActivity(
            context, NOTIFICATION_ID + 2, decline, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val caller = Person.Builder().setName(name).setImportant(true).build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_dibadge)
            .setColor(0xFF3A6FE0.toInt())
            .setContentTitle(name)
            .setContentText(if (video) "Group video call" else "Group voice call")
            .setSubText("Group call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(45_000)
            .setFullScreenIntent(ringPending, true)
            .setContentIntent(ringPending)
            .addPerson(caller)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, declinePending, joinPending))
            .build()
        notification.flags = notification.flags or android.app.Notification.FLAG_INSISTENT
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
        }
    }

    /** The call ended and you never joined it. */
    fun showMissed(context: Context, callerName: String, video: Boolean, at: Long) {
        if (!canPost(context)) return
        ensureChannels(context)
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            context, MISSED_ID, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val name = callerName.ifBlank { "Someone" }
        val notification = NotificationCompat.Builder(context, MISSED_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_dibadge)
            .setColor(0xFF3A6FE0.toInt())
            .setSubText("Missed group call")
            .setContentTitle(name)
            .setContentText(if (video) "Started a group video call" else "Started a group voice call")
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setWhen(if (at > 0) at else System.currentTimeMillis())
            .setShowWhen(true)
            .setAutoCancel(true)
            .setGroup(NotifyGroup.KEY)
            .setContentIntent(pending)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(MISSED_ID, notification)
            NotifyGroup.postSummary(context)
        } catch (e: SecurityException) {
        }
    }

    fun cancel(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    }
}