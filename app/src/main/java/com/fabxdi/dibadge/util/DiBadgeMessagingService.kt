package com.fabxdi.dibadge.util

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives push messages from the diBadge push Worker. The Worker sends "data" messages only,
 * so this code decides what to show (and nothing is shown for a chat that is open).
 */
class DiBadgeMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PushTokens.save(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        when (data["type"]) {
            "message" -> PushNotifier.showMessage(applicationContext, data)
        }
    }
}