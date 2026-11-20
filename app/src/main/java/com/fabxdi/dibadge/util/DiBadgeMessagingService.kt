package com.fabxdi.dibadge.util

import com.fabxdi.dibadge.call.CallEngine
import com.fabxdi.dibadge.call.CallPhase
import com.fabxdi.dibadge.call.MissedCallNotifier
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
            "gcall" -> onGroupCall(data)
            "gcall_end" -> {
                val gn = com.fabxdi.dibadge.call.GroupCallNotifier
                gn.cancel(applicationContext)
                val callId = data["callId"].orEmpty()
                val s = CallEngine.get(applicationContext).state.value
                val onIt = s.isGroup && s.callId == callId
                if (data["missed"] == "1" && !onIt) {
                    gn.showMissed(
                        applicationContext,
                        callerName = data["callerName"].orEmpty(),
                        video = data["callType"] == "video",
                        at = data["at"]?.toLongOrNull() ?: 0L
                    )
                }
            }
            "connection" -> ConnectionNotifier.show(applicationContext, data)
            "task" -> TaskNotifier.show(applicationContext, data)
            "announcement" -> AnnouncementNotifier.show(applicationContext, data)
            "call_cancel" -> {
                val callId = data["callId"].orEmpty()
                val engine = CallEngine.get(applicationContext)
                // Already talking on this call: nothing was missed
                val s = engine.state.value
                val answered = s.callId == callId &&
                        (s.phase == CallPhase.CONNECTING || s.phase == CallPhase.CONNECTED)
                engine.remoteCancelled(callId)
                if (!answered && data["missed"] == "1") {
                    MissedCallNotifier.show(
                        context = applicationContext,
                        callId = callId,
                        callerName = data["callerName"].orEmpty(),
                        groupId = data["groupId"].orEmpty(),
                        video = data["callType"] == "video",
                        at = data["at"]?.toLongOrNull() ?: 0L
                    )
                }
            }
        }
    }

    private fun onGroupCall(data: Map<String, String>) {
        val callId = data["callId"].orEmpty()
        if (callId.isBlank()) return
        // Already on a call: no ring (the call shows as missed in the Calls list)
        if (!CallEngine.get(applicationContext).readyForIncoming()) return
        com.fabxdi.dibadge.call.GroupCallNotifier.show(
            applicationContext,
            callId = callId,
            callerName = data["callerName"].orEmpty(),
            video = data["callType"] == "video"
        )
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