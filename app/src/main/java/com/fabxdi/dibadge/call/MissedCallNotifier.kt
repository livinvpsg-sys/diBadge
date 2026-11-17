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
import com.fabxdi.dibadge.util.NotifyGroup
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fabxdi.dibadge.MainActivity
import com.fabxdi.dibadge.R
import com.fabxdi.dibadge.util.PushNotifier

/** "Missed voice call" / "Missed video call". Tapping it opens the chat with that person. */
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

    fun show(context: Context, callId: String, callerName: String, groupId: String, video: Boolean, at: Long) {
        if (callId.isBlank()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)

        val open = Intent(context, MainActivity::class.java).apply {
            if (groupId.isNotBlank()) putExtra(PushNotifier.EXTRA_OPEN_CHAT, groupId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val id = 0x3000_0000 or (callId.hashCode() and 0x0FFF_FFFF)
        val pending = PendingIntent.getActivity(
            context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val name = callerName.ifBlank { "Someone" }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_dibadge)
            .setContentTitle(name)
            .setContentText(if (video) "Missed video call" else "Missed voice call")
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(if (at > 0) at else System.currentTimeMillis())
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setGroup(NotifyGroup.KEY)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
            NotifyGroup.postSummary(context)
        } catch (e: SecurityException) {
        }
    }
}