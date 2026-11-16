package com.fabxdi.dibadge.call

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Where a call is. Stored as text in calls/{callId}.status. */
object CallStatus {
    const val RINGING = "ringing"
    const val ACCEPTED = "accepted"
    const val DECLINED = "declined"
    const val CANCELLED = "cancelled" // the caller hung up before it was answered
    const val MISSED = "missed"       // nobody answered in time
    const val ENDED = "ended"         // an answered call that finished

    fun isOver(status: String) = status == DECLINED || status == CANCELLED || status == MISSED || status == ENDED
}

/** One call, as saved in Firestore (calls/{callId}). */
data class CallDoc(
    val id: String,
    val callerUid: String,
    val calleeUid: String,
    val callerName: String,
    val type: String,          // "voice" or "video"
    val status: String,
    val groupId: String,       // the 1:1 chat this call belongs to
    val createdAt: Long,
    val startedAt: Long,       // when it was answered, 0 if it was not
    val endedAt: Long,
    val endedBy: String,
    val offer: SessionDesc?,   // the caller's connection details
    val answer: SessionDesc?   // the answer from the person called
) {
    val isVideo get() = type == "video"
}

/** A WebRTC session description (offer or answer). */
data class SessionDesc(val type: String, val sdp: String)

/** A WebRTC connection candidate. */
data class CandidateDoc(val sdpMid: String?, val sdpMLineIndex: Int, val candidate: String)

/** A relay (TURN) server list from the Worker. */
data class IceServer(val urls: List<String>, val username: String?, val credential: String?)

/**
 * Everything that saves and reads a call. The calling itself (WebRTC) comes in the next step and uses this.
 */
class CallRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val uid get() = auth.currentUser?.uid.orEmpty()

    private fun calls() = db.collection("calls")

    /** Starts a call: saves it as "ringing" and returns its id. Ring the other phone with [CallApi.ring] after. */
    suspend fun startCall(groupId: String, calleeUid: String, callerName: String, video: Boolean): String {
        val callId = UUID.randomUUID().toString()
        calls().document(callId).set(
            mapOf(
                "callerUid" to uid,
                "calleeUid" to calleeUid,
                "callerName" to callerName,
                "type" to if (video) "video" else "voice",
                "status" to CallStatus.RINGING,
                "groupId" to groupId,
                "createdAt" to System.currentTimeMillis()
            )
        ).await()
        return callId
    }

    /** Live updates of one call. Emits null while it does not exist. */
    fun observeCall(callId: String): Flow<CallDoc?> = callbackFlow {
        val reg: ListenerRegistration = calls().document(callId).addSnapshotListener { snap, err ->
            if (err != null) {
                Log.w(TAG, "observeCall: ${err.message}")
                return@addSnapshotListener
            }
            trySend(snap?.takeIf { it.exists() }?.let { toCall(it.id, it.data.orEmpty()) })
        }
        awaitClose { reg.remove() }
    }

    suspend fun getCall(callId: String): CallDoc? {
        val snap = calls().document(callId).get().await()
        return if (snap.exists()) toCall(snap.id, snap.data.orEmpty()) else null
    }

    // ---- the caller

    suspend fun setOffer(callId: String, offer: SessionDesc) {
        calls().document(callId).update("offer", mapOf("type" to offer.type, "sdp" to offer.sdp)).await()
    }

    /** The caller hung up before the other person answered. */
    suspend fun cancel(callId: String) = finish(callId, CallStatus.CANCELLED)

    /** Nobody answered in time. */
    suspend fun markMissed(callId: String) = finish(callId, CallStatus.MISSED)

    // ---- the person called

    suspend fun accept(callId: String, answer: SessionDesc) {
        calls().document(callId).update(
            mapOf(
                "answer" to mapOf("type" to answer.type, "sdp" to answer.sdp),
                "status" to CallStatus.ACCEPTED,
                "startedAt" to System.currentTimeMillis()
            )
        ).await()
    }

    suspend fun decline(callId: String) = finish(callId, CallStatus.DECLINED)

    // ---- either side

    /** Ends an answered call. */
    suspend fun end(callId: String) = finish(callId, CallStatus.ENDED)

    private suspend fun finish(callId: String, status: String) {
        calls().document(callId).update(
            mapOf("status" to status, "endedAt" to System.currentTimeMillis(), "endedBy" to uid)
        ).await()
    }

    // ---- connection candidates

    /** [asCaller] is true on the caller's phone, false on the phone of the person called. */
    suspend fun addCandidate(callId: String, asCaller: Boolean, c: CandidateDoc) {
        calls().document(callId).collection(if (asCaller) "callerCandidates" else "calleeCandidates").add(
            mapOf("sdpMid" to c.sdpMid, "sdpMLineIndex" to c.sdpMLineIndex, "candidate" to c.candidate)
        ).await()
    }

    /** New candidates from the other side. [fromCaller] is true when you are the person called. */
    fun observeCandidates(callId: String, fromCaller: Boolean): Flow<CandidateDoc> = callbackFlow {
        val reg = calls().document(callId).collection(if (fromCaller) "callerCandidates" else "calleeCandidates")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.w(TAG, "observeCandidates: ${err.message}")
                    return@addSnapshotListener
                }
                snap?.documentChanges?.forEach { ch ->
                    if (ch.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val d = ch.document.data
                        trySend(
                            CandidateDoc(
                                sdpMid = d["sdpMid"] as? String,
                                sdpMLineIndex = (d["sdpMLineIndex"] as? Number)?.toInt() ?: 0,
                                candidate = d["candidate"] as? String ?: return@forEach
                            )
                        )
                    }
                }
            }
        awaitClose { reg.remove() }
    }

    // ---- the line in the chat

    /**
     * Leaves a short line in the chat when a call is over: "Voice call · 2:41", "Video call · no answer"...
     * Only the caller's phone writes it, so there is one line per call. It is not a push.
     */
    suspend fun writeChatLine(call: CallDoc, finalStatus: String, durationSec: Long) {
        if (uid != call.callerUid || call.groupId.isBlank()) return
        val kind = if (call.isVideo) "Video call" else "Voice call"
        val text = when (finalStatus) {
            CallStatus.ENDED -> "$kind · ${formatDuration(durationSec)}"
            CallStatus.DECLINED -> "$kind · declined"
            CallStatus.CANCELLED, CallStatus.MISSED -> "$kind · no answer"
            else -> return
        }
        val id = "call_${call.id}" // a retry replaces the line instead of adding a second one
        db.collection("personalGroups").document(call.groupId).collection("messages").document(id).set(
            mapOf(
                "id" to id,
                "text" to text,
                "msgType" to "callNotice",
                "senderName" to call.callerName,
                "senderUid" to call.callerUid,
                "attachments" to emptyList<String>(),
                "attachmentDurations" to emptyMap<String, Long>(),
                "attachmentThumbs" to emptyList<String>(),
                "timestamp" to System.currentTimeMillis(),
                "isEdited" to false
            )
        ).await()
    }

    private fun toCall(id: String, d: Map<String, Any?>): CallDoc {
        fun sd(v: Any?): SessionDesc? {
            val m = v as? Map<*, *> ?: return null
            val sdp = m["sdp"] as? String ?: return null
            return SessionDesc(m["type"] as? String ?: "", sdp)
        }
        return CallDoc(
            id = id,
            callerUid = d["callerUid"] as? String ?: "",
            calleeUid = d["calleeUid"] as? String ?: "",
            callerName = d["callerName"] as? String ?: "",
            type = if (d["type"] == "video") "video" else "voice",
            status = d["status"] as? String ?: "",
            groupId = d["groupId"] as? String ?: "",
            createdAt = (d["createdAt"] as? Number)?.toLong() ?: 0L,
            startedAt = (d["startedAt"] as? Number)?.toLong() ?: 0L,
            endedAt = (d["endedAt"] as? Number)?.toLong() ?: 0L,
            endedBy = d["endedBy"] as? String ?: "",
            offer = sd(d["offer"]),
            answer = sd(d["answer"])
        )
    }

    companion object {
        private const val TAG = "CallRepository"

        fun formatDuration(sec: Long): String {
            val s = sec.coerceAtLeast(0)
            val h = s / 3600
            val m = (s % 3600) / 60
            val r = s % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, r) else "%d:%02d".format(m, r)
        }
    }
}

/** Calls to the diBadge push Worker for calls (ring the other phone, relay details). */
object CallApi {
    private const val BASE_URL = "https://dibadge-push.dibadge.workers.dev"
    private const val TAG = "CallApi"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** Rings the other person's phone. Returns true when the Worker accepted it. */
    suspend fun ring(callId: String): Boolean = post("/call/ring", callId) != null

    /** Tells the other phone to stop ringing (the caller hung up first). */
    suspend fun cancel(callId: String): Boolean = post("/call/cancel", callId) != null

    /** The relay (TURN) servers for this call. Empty when the Worker could not give them. */
    suspend fun iceServers(callId: String): List<IceServer> {
        val json = post("/call/ice", callId) ?: return emptyList()
        val arr: JSONArray = json.optJSONArray("iceServers") ?: return emptyList()
        val out = mutableListOf<IceServer>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val urlsArr = o.optJSONArray("urls") ?: continue
            val urls = (0 until urlsArr.length()).map { urlsArr.getString(it) }
            out.add(
                IceServer(
                    urls = urls,
                    username = o.optString("username").takeIf { it.isNotBlank() },
                    credential = o.optString("credential").takeIf { it.isNotBlank() }
                )
            )
        }
        return out
    }

    private suspend fun post(path: String, callId: String): JSONObject? {
        val body = JSONObject().put("callId", callId).toString()
        for (attempt in 0..1) {
            try {
                val token = FirebaseAuth.getInstance().currentUser?.getIdToken(attempt > 0)?.await()?.token ?: return null
                val request = Request.Builder()
                    .url("$BASE_URL$path")
                    .header("Authorization", "Bearer $token")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    client.newCall(request).execute().use { r ->
                        val text = r.body?.string().orEmpty()
                        if (r.isSuccessful) JSONObject(text.ifBlank { "{}" }) else {
                            Log.w(TAG, "$path failed ${r.code} $text")
                            if (r.code == 401 || r.code >= 500) null else JSONObject().put("failed", r.code)
                        }
                    }
                }
                if (result != null) return if (result.has("failed")) null else result
            } catch (e: Exception) {
                Log.w(TAG, "$path error: ${e.message}")
            }
            kotlinx.coroutines.delay(1500)
        }
        return null
    }
}