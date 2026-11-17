package com.fabxdi.dibadge.util

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Asks the push Worker to tell a person about a connection request ("request") or an accept ("accepted"). */
object ConnectionPushApi {
    private const val BASE_URL = "https://dibadge-push.dibadge.workers.dev"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    }

    /** Fire and forget. Call after the Firestore change has been saved. */
    fun notify(targetUid: String, event: String) {
        if (targetUid.isBlank()) return
        scope.launch { post(targetUid, event) }
    }

    private suspend fun post(targetUid: String, event: String) {
        try {
            val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token ?: return
            val body = JSONObject().put("targetUid", targetUid).put("event", event).toString()
            val request = Request.Builder()
                .url("$BASE_URL/connection/notify")
                .header("Authorization", "Bearer $token")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) Log.w("ConnectionPushApi", "failed ${r.code}")
            }
        } catch (e: Exception) {
            Log.w("ConnectionPushApi", "error: ${e.message}")
        }
    }
}