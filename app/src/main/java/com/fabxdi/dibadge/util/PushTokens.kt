package com.fabxdi.dibadge.util

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Keeps this phone's push address (FCM token) in Firestore: users/{uid}/fcmTokens/{token}.
 * The push Worker reads it to know where to send notifications.
 */
object PushTokens {
    private const val TAG = "PushTokens"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Call once the user is signed in (and again after every sign-in). */
    fun register() {
        scope.launch {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                save(token)
            } catch (e: Exception) {
                Log.w(TAG, "register failed: ${e.message}")
            }
        }
    }

    /** Called by the messaging service when Firebase gives this phone a new token. */
    fun save(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (token.isBlank()) return
        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .collection("fcmTokens").document(token)
            .set(
                mapOf(
                    "token" to token,
                    "platform" to "android",
                    "updatedAt" to System.currentTimeMillis()
                )
            )
            .addOnFailureListener { Log.w(TAG, "save failed: ${it.message}") }
    }

    /**
     * Call right BEFORE signing out, so the next person on this phone does not get your messages.
     * The user id is read now, because after sign-out it is gone.
     */
    fun unregister() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            try {
                val messaging = FirebaseMessaging.getInstance()
                val token = messaging.token.await()
                FirebaseFirestore.getInstance()
                    .collection("users").document(uid)
                    .collection("fcmTokens").document(token)
                    .delete().await()
                messaging.deleteToken().await()
            } catch (e: Exception) {
                Log.w(TAG, "unregister failed: ${e.message}")
            }
        }
    }
}