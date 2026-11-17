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

/** "diBadge · Connection · now": a connection request (with Accept / Decline) or an accepted request. */
object ConnectionNotifier {
    const val CHANNEL_ID = "connections"

    fun notificationId(otherUid: String): Int = 0x5000_0000 or (otherUid.hashCode() and 0x0FFF_FFFF)

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Connections", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Connection requests and accepted requests"
            }
        )
    }

    fun show(context: Context, data: Map<String, String>) {
        val otherUid = data["senderUid"].orEmpty()
        if (otherUid.isBlank()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val name = capitalizeName(data["senderName"].orEmpty().ifBlank { "Someone" })
        val isRequest = data["event"] == "request"
        val text = data["body"].orEmpty().ifBlank { if (isRequest) "wants to connect with you" else "accepted your connection request" }
        val at = data["at"]?.toLongOrNull() ?: System.currentTimeMillis()
        ensureChannel(context)


        val id = notificationId(otherUid)
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_dibadge)
            .setColor(0xFF3A6FE0.toInt())
            .setSubText("Connections")
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setLargeIcon(NotifyAvatar.bitmap(name, data["senderPhoto"].orEmpty()))
            .setContentTitle(name)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setGroup(NotifyGroup.KEY)
            .setWhen(at)
            .setShowWhen(true)
            .setContentIntent(PendingIntent.getActivity(context, id, open, flags))

        if (isRequest) {
            fun action(act: String, label: String, req: Int): NotificationCompat.Action {
                val i = Intent(context, NotifyActionReceiver::class.java).apply {
                    action = act
                    putExtra(NotifyActionReceiver.EXTRA_OTHER_UID, otherUid)
                }
                return NotificationCompat.Action.Builder(0, label, PendingIntent.getBroadcast(context, id + req, i, flags)).build()
            }
            builder.addAction(action(NotifyActionReceiver.ACTION_CONN_ACCEPT, "Accept", 1))
            builder.addAction(action(NotifyActionReceiver.ACTION_CONN_DECLINE, "Decline", 2))
        }

        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
            NotifyGroup.postSummary(context)
        } catch (e: SecurityException) {
        }
    }
}