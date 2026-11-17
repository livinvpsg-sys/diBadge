package com.fabxdi.dibadge.util

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

/** Shows announcement pushes: "Announcement" and "Please read" reminders. */
object AnnouncementNotifier {
    const val CHANNEL_ID = "announcements"

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Announcements", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Announcements and reminders to read them"
            }
        )
    }

    fun show(context: Context, data: Map<String, String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val groupId = data["groupId"].orEmpty()
        val announcementId = data["announcementId"].orEmpty()
        if (groupId.isBlank() || announcementId.isBlank()) return
        val title = data["title"].orEmpty().ifBlank { "Announcement" }
        val body = data["body"].orEmpty()

        ensureChannel(context)

        val open = Intent(context, MainActivity::class.java).apply {
            putExtra(PushNotifier.EXTRA_OPEN_CHAT, groupId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        // One notification per announcement: a reminder replaces the earlier push
        val id = 0x1000_0000 or (announcementId.hashCode() and 0x0FFF_FFFF)
        val pending = PendingIntent.getActivity(
            context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_dibadge)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
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