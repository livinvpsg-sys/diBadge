package com.fabxdi.dibadge.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** The Decline button on the incoming-call notification. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DECLINE) return
        val callId = intent.getStringExtra(CallActivity.EXTRA_CALL_ID).orEmpty()
        val engine = CallEngine.get(context)
        IncomingCallNotifier.cancel(context)
        if (engine.state.value.callId == callId) engine.decline()
    }

    companion object {
        const val ACTION_DECLINE = "com.fabxdi.dibadge.call.DECLINE"
    }
}