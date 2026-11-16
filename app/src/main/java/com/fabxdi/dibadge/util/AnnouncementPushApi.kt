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

/** Asks the push Worker to notify members about an announcement (owner or admin only). */
object AnnouncementPushApi {
    private const val BASE_URL = "https://dibadge-push.dibadge.workers.dev"
    private const val TAG = "AnnouncementPushApi"

    sealed class Result {
        /** [people] phones were reached; 0 means nobody to notify (or no phone registered). */
        data class Sent(val people: Int) : Result()
        object Cooldown : Result()
        object Ended : Result()
        object Failed : Result()
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** mode: "all" = every member, "pending" = members who have not read it yet. */
    suspend fun notify(groupId: String, announcementId: String, mode: String): Result {
        return try {
            val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token ?: return Result.Failed
            val body = JSONObject().put("groupId", groupId).put("announcementId", announcementId).put("mode", mode)
            val request = Request.Builder()
                .url("$BASE_URL/announcement/notify")
                .header("Authorization", "Bearer $token")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { r ->
                    val text = r.body?.string().orEmpty()
                    if (!r.isSuccessful) {
                        Log.w(TAG, "notify failed ${r.code} $text")
                        return@use Result.Failed
                    }
                    val j = JSONObject(text.ifBlank { "{}" })
                    when (j.optString("skipped")) {
                        "cooldown" -> Result.Cooldown
                        "ended" -> Result.Ended
                        else -> Result.Sent(j.optInt("people", 0))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "notify error: ${e.message}")
            Result.Failed
        }
    }
}