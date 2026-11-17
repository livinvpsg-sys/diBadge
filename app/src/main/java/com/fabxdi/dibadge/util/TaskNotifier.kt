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

/** Shows task notices from the push Worker: due reminder, overdue, and "task completed". */
object TaskNotifier {
    const val CHANNEL_ID = "task_reminders"

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Tasks", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Task reminders, overdue tasks and completed tasks"
            }
        )
    }

    fun show(context: Context, data: Map<String, String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val taskId = data["taskId"].orEmpty()
        val groupId = data["groupId"].orEmpty()
        val event = data["event"].orEmpty()
        val title = data["title"].orEmpty().ifBlank { "Task" }
        val body = data["body"].orEmpty()
        if (taskId.isBlank()) return

        ensureChannel(context)

        // Tapping opens the chat the task belongs to
        val open = Intent(context, MainActivity::class.java).apply {
            if (groupId.isNotBlank()) putExtra(PushNotifier.EXTRA_OPEN_CHAT, groupId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        // One notification per task and kind, so a later notice of the same kind replaces it
        val id = 0x2000_0000 or ((taskId + event).hashCode() and 0x1FFF_FFFF)
        val pending = PendingIntent.getActivity(
            context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_dibadge)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setGroup(NotifyGroup.KEY)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, notification)
            NotifyGroup.postSummary(context)
        } catch (e: SecurityException) {
            // Permission was taken away a moment ago: nothing to show
        }
    }
}