package com.fabxdi.dibadge.call

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fabxdi.dibadge.MainActivity
import com.fabxdi.dibadge.R
import com.fabxdi.dibadge.util.NotifyGroup
import com.fabxdi.dibadge.util.PushNotifier
import java.text.DateFormat
import java.util.Date

/**
 * "Missed voice call" / "Missed video call" with two buttons: Call back (rings them now) and Message
 * (opens the chat). Tapping the notification opens the chat too.
 */
object MissedCallNotifier {
    const val CHANNEL_ID = "calls_missed"

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Missed calls", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Calls you did not answer"
            }
        )
    }

    fun notificationId(callId: String) = 0x3000_0000 or (callId.hashCode() and 0x0FFF_FFFF)

    fun show(context: Context, callId: String, callerName: String, groupId: String, video: Boolean, at: Long) {
        if (callId.isBlank()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)

        val id = notificationId(callId)
        val whenMs = if (at > 0) at else System.currentTimeMillis()

        fun openChat(requestOffset: Int): PendingIntent {
            val open = Intent(context, MainActivity::class.java).apply {
                if (groupId.isNotBlank()) putExtra(PushNotifier.EXTRA_OPEN_CHAT, groupId)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context, id + requestOffset, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        // Call back: opens the call screen and rings the person who called
        val callBack = Intent(context, CallActivity::class.java).apply {
            putExtra(CallActivity.EXTRA_CALL_BACK_ID, callId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val callBackPending = PendingIntent.getActivity(
            context, id + 1, callBack, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val name = callerName.ifBlank { "Someone" }
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(whenMs))
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_dibadge)
            .setColor(0xFF3A6FE0.toInt())
            .setSubText("Missed call")
            .setContentTitle(name)
            .setContentText(if (video) "Missed video call" else "Missed voice call")
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(whenMs)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setGroup(NotifyGroup.KEY)
            .setContentIntent(openChat(0))
            .addAction(
                NotificationCompat.Action.Builder(0, "Call back", callBackPending)
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_CALL)
                    .build()
            )
        if (groupId.isNotBlank()) {
            builder.addAction(
                NotificationCompat.Action.Builder(0, "Message", openChat(2)).build()
            )
        }
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
            NotifyGroup.postSummary(context)
        } catch (e: SecurityException) {
        }
    }
}