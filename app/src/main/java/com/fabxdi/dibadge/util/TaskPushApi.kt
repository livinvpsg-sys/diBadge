package com.fabxdi.dibadge.util

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Asks the push Worker to tell the person who assigned a task that it is done. */
object TaskPushApi {
    private const val BASE_URL = "https://dibadge-push.dibadge.workers.dev"
    private const val TAG = "TaskPushApi"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** Call after new tasks have been saved to Firestore. Tells the people they were given to. */
    suspend fun assigned(groupId: String, taskIds: List<String>) {
        if (taskIds.isEmpty()) return
        post("/task/assigned", JSONObject().put("groupId", groupId).put("taskIds", org.json.JSONArray(taskIds)))
    }

    /** Call after the completed task has been saved to Firestore. Failure is only logged. */
    suspend fun completed(groupId: String, taskId: String) {
        post("/task/completed", JSONObject().put("groupId", groupId).put("taskId", taskId))
    }

    private suspend fun post(path: String, json: JSONObject) {
        try {
            val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token ?: return
            val request = Request.Builder()
                .url("$BASE_URL$path")
                .header("Authorization", "Bearer $token")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { r ->
                    if (!r.isSuccessful) Log.w(TAG, "$path failed ${r.code} ${r.body?.string().orEmpty()}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "$path error: ${e.message}")
        }
    }
}