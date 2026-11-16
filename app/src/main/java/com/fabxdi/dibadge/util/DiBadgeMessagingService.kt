package com.fabxdi.dibadge.util

import com.fabxdi.dibadge.call.CallEngine
import com.fabxdi.dibadge.call.CallRepository
import com.fabxdi.dibadge.call.IncomingCallNotifier
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives push messages from the diBadge push Worker. The Worker sends "data" messages only,
 * so this code decides what to show (and nothing is shown for a chat that is open).
 */
class DiBadgeMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PushTokens.save(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        when (data["type"]) {
            "message" -> PushNotifier.showMessage(applicationContext, data)
            "call" -> onIncomingCall(data)
            "task" -> TaskNotifier.show(applicationContext, data)
            "announcement" -> AnnouncementNotifier.show(applicationContext, data)
            "call_cancel" -> {
                val callId = data["callId"].orEmpty()
                CallEngine.get(applicationContext).remoteCancelled(callId)
            }
        }
    }

    private fun onIncomingCall(data: Map<String, String>) {
        val callId = data["callId"].orEmpty()
        if (callId.isBlank()) return
        val engine = CallEngine.get(applicationContext)
        if (!engine.readyForIncoming()) {
            // Already on a call: the caller hears that you declined
            scope.launch { runCatching { CallRepository().decline(callId) } }
            return
        }
        engine.showIncoming(callId)
        IncomingCallNotifier.show(
            context = applicationContext,
            callId = callId,
            callerName = data["callerName"].orEmpty().ifBlank { "Someone" },
            video = data["callType"] == "video"
        )
    }
}