package com.fabxdi.dibadge.util

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/** Handles the buttons on notifications: Reply and Mark as read (chats), Accept and Decline (connections). */
class NotifyActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_REPLY -> reply(app, intent)
                    ACTION_READ -> {
                        val groupId = intent.getStringExtra(EXTRA_GROUP_ID).orEmpty()
                        if (groupId.isNotBlank()) {
                            ChatSeen.markNow(app, groupId)
                            PushNotifier.clearGroup(app, groupId)
                        }
                    }
                    ACTION_CONN_ACCEPT, ACTION_CONN_DECLINE -> connection(app, intent, accept = action == ACTION_CONN_ACCEPT)
                }
            } catch (e: Exception) {
                android.util.Log.w("NotifyAction", "$action failed: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun reply(context: Context, intent: Intent) {
        val groupId = intent.getStringExtra(EXTRA_GROUP_ID).orEmpty()
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)?.toString()?.trim().orEmpty()
        val user = FirebaseAuth.getInstance().currentUser
        if (groupId.isBlank() || text.isBlank() || user == null) return
        val db = FirebaseFirestore.getInstance()
        val name = try {
            db.collection("users").document(user.uid).get().await().getString("displayName")
        } catch (e: Exception) {
            null
        } ?: user.displayName ?: "Me"
        val msgId = UUID.randomUUID().toString()
        val data = mutableMapOf<String, Any>(
            "id" to msgId,
            "text" to text,
            "senderName" to capitalizeName(name),
            "senderUid" to user.uid,
            "attachments" to emptyList<String>(),
            "attachmentDurations" to emptyMap<String, Any>(),
            "attachmentThumbs" to emptyList<String>(),
            "timestamp" to System.currentTimeMillis(),
            "isEdited" to false
        )
        db.collection("personalGroups").document(groupId).collection("messages").document(msgId).set(data).await()
        PushApi.messageSent(groupId, msgId)
        ChatSeen.markNow(context, groupId)
        PushNotifier.clearGroup(context, groupId)
    }

    private suspend fun connection(context: Context, intent: Intent, accept: Boolean) {
        val otherUid = intent.getStringExtra(EXTRA_OTHER_UID).orEmpty()
        val me = FirebaseAuth.getInstance().currentUser?.uid
        if (otherUid.isBlank() || me == null) return
        val users = FirebaseFirestore.getInstance().collection("users")
        if (accept) {
            users.document(me).collection("userConnections").document(otherUid).update("status", "connected").await()
            users.document(otherUid).collection("userConnections").document(me).update("status", "connected").await()
            ConnectionPushApi.notify(otherUid, "accepted")
        } else {
            try { users.document(me).collection("userConnections").document(otherUid).delete().await() } catch (e: Exception) { }
            try { users.document(otherUid).collection("userConnections").document(me).delete().await() } catch (e: Exception) { }
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(ConnectionNotifier.notificationId(otherUid))
    }

    companion object {
        const val ACTION_REPLY = "com.fabxdi.dibadge.action.REPLY"
        const val ACTION_READ = "com.fabxdi.dibadge.action.MARK_READ"
        const val ACTION_CONN_ACCEPT = "com.fabxdi.dibadge.action.CONN_ACCEPT"
        const val ACTION_CONN_DECLINE = "com.fabxdi.dibadge.action.CONN_DECLINE"
        const val EXTRA_GROUP_ID = "groupId"
        const val EXTRA_OTHER_UID = "otherUid"
        const val KEY_REPLY = "reply_text"
    }
}