package com.fabxdi.dibadge.util

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fabxdi.dibadge.R

/**
 * Puts all diBadge notifications (messages, connections, tasks, announcements, missed calls) in one
 * group with one summary, so the phone's status bar shows a single diBadge icon however many there are.
 * Ringing call notifications stay outside the group.
 */
object NotifyGroup {
    const val KEY = "dibadge_all"
    private const val SUMMARY_ID = 0x7000_0001

    /** Removes the "diBadge" summary when no real notification is left in the group. */
    fun cleanup(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val hasChild = nm.activeNotifications.any {
                it.notification.group == KEY && (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
            }
            if (!hasChild) nm.cancel(SUMMARY_ID)
        } catch (e: Exception) {
        }
    }

    /** Call after posting a notification that uses [KEY]. Android removes the summary when the last one is cleared. */
    fun postSummary(context: Context) {
        try {
            PushNotifier.ensureChannel(context)
            val summary = NotificationCompat.Builder(context, PushNotifier.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_dibadge)
                .setColor(0xFF3A6FE0.toInt())
                .setContentTitle("diBadge")
                .setGroup(KEY)
                .setGroupSummary(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(SUMMARY_ID, summary)
        } catch (e: SecurityException) {
            // Notifications were switched off a moment ago: nothing to show
        }
    }
}