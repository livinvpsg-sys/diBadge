package com.fabxdi.dibadge.util

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Tells the diBadge push Worker that a chat message was just saved, so it can notify the
 * other members. Fire and forget: a failure here never affects sending the message.
 */
object PushApi {

    /** The address printed when you deployed push_worker (no trailing slash). */
    const val BASE_URL = "https://dibadge-push.dibadge.workers.dev"

    private const val TAG = "PushApi"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun messageSent(groupId: String, messageId: String) {
        if (groupId.isBlank() || messageId.isBlank()) return
        scope.launch {
            val body = JSONObject().put("groupId", groupId).put("messageId", messageId).toString()
            for (attempt in 0..1) {
                try {
                    val token = FirebaseAuth.getInstance().currentUser
                        ?.getIdToken(attempt > 0)?.await()?.token ?: return@launch
                    val request = Request.Builder()
                        .url("$BASE_URL/notify/message")
                        .header("Authorization", "Bearer $token")
                        .post(body.toRequestBody("application/json".toMediaType()))
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) return@launch
                        Log.w(TAG, "push failed ${response.code}")
                        if (response.code != 401 && response.code < 500) return@launch
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "push error: ${e.message}")
                }
                delay(3000)
            }
        }
    }
}