package com.fabxdi.dibadge.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.fabxdi.dibadge.MainActivity
import com.fabxdi.dibadge.R
import org.json.JSONArray
import org.json.JSONObject

/** Which chat is open on screen right now (null when none, or when the app is in the background). */
object ActiveChat {
    @Volatile
    var groupId: String? = null

    /**
     * Marks [groupId] as the open chat while the screen is visible. Returns a function that
     * stops tracking. Opening a chat also clears its notification.
     */
    fun track(context: Context, groupId: String): () -> Unit {
        var ctx: Context? = context
        var owner: LifecycleOwner? = null
        while (ctx != null) {
            if (ctx is LifecycleOwner) { owner = ctx; break }
            ctx = (ctx as? ContextWrapper)?.baseContext
        }
        val lifecycle = owner?.lifecycle ?: return { }
        val app = context.applicationContext
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    this.groupId = groupId
                    PushNotifier.clearGroup(app, groupId)
                    ChatSeen.markNow(app, groupId)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    if (this.groupId == groupId) this.groupId = null
                    ChatSeen.markNow(app, groupId)
                }
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        return {
            lifecycle.removeObserver(observer)
            if (this.groupId == groupId) this.groupId = null
        }
    }
}

/** Shows chat message notifications that arrive from the push Worker. */
object PushNotifier {
    const val CHANNEL_ID = "chat_messages"
    const val EXTRA_OPEN_CHAT = "OPEN_CHAT_GROUP_ID"
    private const val PREFS = "push_chat_lines"
    private const val MAX_LINES = 8

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, "Chat messages", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "New messages in your chats and groups"
            enableVibration(true)
        }
        nm.createNotificationChannel(channel)
    }

    fun showMessage(context: Context, data: Map<String, String>) {
        val groupId = data["groupId"].orEmpty()
        if (groupId.isBlank()) return
        // The chat is open on screen: the person is already reading it
        if (ActiveChat.groupId == groupId) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val sender = capitalizeName(data["senderName"].orEmpty().ifBlank { "Someone" })
        val senderPhoto = data["senderPhoto"].orEmpty()
        val groupName = capitalizeFirst(data["groupName"].orEmpty())
        val groupPhoto = data["groupPhoto"].orEmpty()
        val text = data["preview"].orEmpty().ifBlank { "New message" }
        val at = data["sentAt"]?.toLongOrNull() ?: System.currentTimeMillis()

        ensureChannel(context)

        // Keep the last few lines of this chat so several messages stack in one notification
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "g_$groupId"
        val lines = try { JSONArray(prefs.getString(key, "[]")) } catch (e: Exception) { JSONArray() }
        lines.put(JSONObject().put("s", sender).put("t", text).put("at", at).put("p", senderPhoto))
        val trimmed = JSONArray()
        for (i in maxOf(0, lines.length() - MAX_LINES) until lines.length()) trimmed.put(lines.get(i))
        prefs.edit().putString(key, trimmed.toString()).apply()

        val isGroupLike = data["isDirect"] != "1" && groupName.isNotBlank() && !groupName.equals(sender, ignoreCase = true)
        val title = if (isGroupLike) groupName else sender
        // One line per message. In a group each line starts with who sent it.
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        for (i in 0 until trimmed.length()) {
            val o = trimmed.getJSONObject(i)
            val line = if (isGroupLike) capitalizeName(o.optString("s")) + ": " + o.optString("t") else o.optString("t")
            style.addLine(line)
        }

        val id = notificationId(groupId)
        val open = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_CHAT, groupId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val actionFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val replyIntent = Intent(context, NotifyActionReceiver::class.java).apply {
            action = NotifyActionReceiver.ACTION_REPLY
            putExtra(NotifyActionReceiver.EXTRA_GROUP_ID, groupId)
        }
        // Reply needs a mutable PendingIntent so Android can add what was typed
        val replyFlags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        val replyAction = NotificationCompat.Action.Builder(
            0, "Reply", PendingIntent.getBroadcast(context, id + 1, replyIntent, replyFlags)
        )
            .addRemoteInput(RemoteInput.Builder(NotifyActionReceiver.KEY_REPLY).setLabel("Reply").build())
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
        val readIntent = Intent(context, NotifyActionReceiver::class.java).apply {
            action = NotifyActionReceiver.ACTION_READ
            putExtra(NotifyActionReceiver.EXTRA_GROUP_ID, groupId)
        }
        val readAction = NotificationCompat.Action.Builder(
            0, "Mark as read", PendingIntent.getBroadcast(context, id + 2, readIntent, actionFlags)
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()

        // "diBadge · Message · now" (the app name and time are added by Android)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_dibadge)
            .setColor(0xFF3A6FE0.toInt())
            .setSubText(if (isGroupLike) "Group message" else "Message")
            .setLargeIcon(if (isGroupLike) NotifyAvatar.bitmap(groupName, groupPhoto) else NotifyAvatar.bitmap(sender, senderPhoto))
            .addAction(replyAction)
            .addAction(readAction)
            .setStyle(style)
            .setContentTitle(if (isGroupLike) groupName else sender)
            .setContentText(if (isGroupLike) "$sender: $text" else text)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setGroup(NotifyGroup.KEY)
            .setWhen(at)
            .setShowWhen(true)
            .setContentIntent(pending)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, notification)
            NotifyGroup.postSummary(context)
        } catch (e: SecurityException) {
            // Permission was taken away a moment ago: nothing to show
        }
    }

    /** Removes the notification and the saved lines of one chat (the chat was opened). */
    fun clearGroup(context: Context, groupId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("g_$groupId").apply()
        NotificationManagerCompat.from(context).cancel(notificationId(groupId))
        NotifyGroup.cleanup(context)
    }

    private fun notificationId(groupId: String): Int = 0x4000_0000 or (groupId.hashCode() and 0x3FFF_FFFF)
}