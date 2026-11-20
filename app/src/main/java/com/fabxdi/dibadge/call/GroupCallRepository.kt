package com.fabxdi.dibadge.call

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
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
import java.util.concurrent.TimeUnit

/** A group call as saved in Firestore (groupCalls/{callId}). */
data class GroupCallDoc(
    val id: String,
    val groupId: String,
    val hostUid: String,
    val hostName: String,
    val type: String,            // "voice" or "video"
    val status: String,          // "active" or "ended"
    val createdAt: Long,
    val participants: List<String>
) {
    val isVideo get() = type == "video"
    val isActive get() = status == "active"
}

/** One person on a group call (groupCalls/{callId}/peers/{uid}). */
data class GroupPeer(
    val uid: String,
    val name: String,
    val sessionId: String,   // their sending session on the Cloudflare SFU
    val cameraOn: Boolean,
    val muted: Boolean
)

/** One line of a person's own group call list (users/{uid}/groupCallLog/{callId}). */
data class GroupCallLogEntry(
    val callId: String,
    val groupId: String,
    val hostUid: String,
    val hostName: String,
    val video: Boolean,
    val createdAt: Long,
    val outgoing: Boolean,
    val joined: Boolean,
    val declined: Boolean,
    val durationSec: Long
)

/** Everything that saves and reads a group call. The media itself goes through the Cloudflare SFU (see [GroupCallApi]). */
class GroupCallRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val uid get() = auth.currentUser?.uid.orEmpty()
    private fun calls() = db.collection("groupCalls")

    suspend fun create(groupId: String, hostName: String, video: Boolean): String {
        val id = java.util.UUID.randomUUID().toString()
        calls().document(id).set(
            mapOf(
                "groupId" to groupId,
                "hostUid" to uid,
                "hostName" to hostName,
                "type" to if (video) "video" else "voice",
                "status" to "active",
                "createdAt" to System.currentTimeMillis(),
                "participants" to emptyList<String>()
            )
        ).await()
        return id
    }

    suspend fun getCall(callId: String): GroupCallDoc? {
        val snap = calls().document(callId).get().await()
        return if (snap.exists()) toCall(snap.id, snap.data.orEmpty()) else null
    }

    fun observeCall(callId: String): Flow<GroupCallDoc?> = callbackFlow {
        val reg: ListenerRegistration = calls().document(callId).addSnapshotListener { snap, err ->
            if (err != null) {
                Log.w(TAG, "observeCall: ${err.message}")
                return@addSnapshotListener
            }
            trySend(snap?.takeIf { it.exists() }?.let { toCall(it.id, it.data.orEmpty()) })
        }
        awaitClose { reg.remove() }
    }

    /** The call running in this chat now, if any (for the "Join" banner). */
    fun observeActive(groupId: String): Flow<GroupCallDoc?> = callbackFlow {
        val reg = calls().whereEqualTo("groupId", groupId).whereEqualTo("status", "active")
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    if (err != null) Log.w(TAG, "observeActive: ${err.message}")
                    return@addSnapshotListener
                }
                val now = System.currentTimeMillis()
                // A call nobody is on for a while is stale (the app was killed): do not show it
                val active = snap.documents.mapNotNull { toCall(it.id, it.data.orEmpty()) }
                    .filter { it.participants.isNotEmpty() || now - it.createdAt < 90_000 }
                    .maxByOrNull { it.createdAt }
                trySend(active)
            }
        awaitClose { reg.remove() }
    }

    fun observePeers(callId: String): Flow<List<GroupPeer>> = callbackFlow {
        val reg = calls().document(callId).collection("peers").addSnapshotListener { snap, err ->
            if (err != null || snap == null) {
                if (err != null) Log.w(TAG, "observePeers: ${err.message}")
                return@addSnapshotListener
            }
            trySend(snap.documents.map { d ->
                val m = d.data.orEmpty()
                GroupPeer(
                    uid = d.id,
                    name = m["name"] as? String ?: "",
                    sessionId = m["sessionId"] as? String ?: "",
                    cameraOn = m["cameraOn"] as? Boolean ?: false,
                    muted = m["muted"] as? Boolean ?: false
                )
            })
        }
        awaitClose { reg.remove() }
    }

    /** I am on the call: others find my sending session here and pull it. */
    suspend fun join(callId: String, name: String, sessionId: String, cameraOn: Boolean) {
        calls().document(callId).collection("peers").document(uid).set(
            mapOf("name" to name, "sessionId" to sessionId, "cameraOn" to cameraOn, "muted" to false, "joinedAt" to System.currentTimeMillis())
        ).await()
        calls().document(callId).update("participants", FieldValue.arrayUnion(uid)).await()
    }

    suspend fun updatePeer(callId: String, field: String, value: Boolean) {
        calls().document(callId).collection("peers").document(uid).update(field, value).await()
    }

    /**
     * I leave. Returns the call when I was the last person (it is then ended), otherwise null.
     */
    suspend fun leave(callId: String): GroupCallDoc? {
        runCatching { calls().document(callId).collection("peers").document(uid).delete().await() }
        runCatching { calls().document(callId).update("participants", FieldValue.arrayRemove(uid)).await() }
        val doc = getCall(callId) ?: return null
        if (doc.participants.isNotEmpty() || !doc.isActive) return null
        calls().document(callId).update(
            mapOf("status" to "ended", "endedAt" to System.currentTimeMillis(), "endedBy" to uid)
        ).await()
        return doc
    }

    /** The line left in the chat when the call is over: "Group voice call · 12:34". */
    suspend fun writeChatLine(call: GroupCallDoc, text: String) {
        if (call.groupId.isBlank()) return
        val id = "gcall_${call.id}"
        db.collection("personalGroups").document(call.groupId).collection("messages").document(id).set(
            mapOf(
                "id" to id,
                "text" to text,
                "msgType" to "callNotice",
                "senderName" to call.hostName,
                "senderUid" to call.hostUid,
                "attachments" to emptyList<String>(),
                "attachmentDurations" to emptyMap<String, Long>(),
                "attachmentThumbs" to emptyList<String>(),
                "timestamp" to System.currentTimeMillis(),
                "isEdited" to false
            )
        ).await()
    }

    private fun log() = db.collection("users").document(uid).collection("groupCallLog")

    /** Notes the call in my own list. Merges, so it also fills the line the Worker made when the call rang. */
    suspend fun logMine(call: GroupCallDoc, outgoing: Boolean, joined: Boolean, durationSec: Long = 0) {
        if (uid.isBlank()) return
        val data = mutableMapOf<String, Any>(
            "groupId" to call.groupId,
            "hostUid" to call.hostUid,
            "hostName" to call.hostName,
            "callType" to call.type,
            "createdAt" to call.createdAt,
            "outgoing" to outgoing,
            "joined" to joined
        )
        if (durationSec > 0) data["durationSec"] = durationSec
        log().document(call.id).set(data, SetOptions.merge()).await()
    }

    /** I turned the ring away: it is not a missed call. */
    suspend fun markDeclined(callId: String) {
        if (uid.isBlank() || callId.isBlank()) return
        log().document(callId).set(mapOf("declined" to true), SetOptions.merge()).await()
    }

    /** My own group call list, live. */
    fun observeMyLog(): Flow<List<GroupCallLogEntry>> = callbackFlow {
        if (uid.isBlank()) { trySend(emptyList()); awaitClose { }; return@callbackFlow }
        val reg = log().addSnapshotListener { snap, err ->
            if (err != null || snap == null) {
                if (err != null) Log.w(TAG, "observeMyLog: ${err.message}")
                return@addSnapshotListener
            }
            trySend(snap.documents.map { d ->
                val m = d.data.orEmpty()
                GroupCallLogEntry(
                    callId = d.id,
                    groupId = m["groupId"] as? String ?: "",
                    hostUid = m["hostUid"] as? String ?: "",
                    hostName = m["hostName"] as? String ?: "",
                    video = m["callType"] == "video",
                    createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L,
                    outgoing = m["outgoing"] as? Boolean ?: false,
                    joined = m["joined"] as? Boolean ?: false,
                    declined = m["declined"] as? Boolean ?: false,
                    durationSec = (m["durationSec"] as? Number)?.toLong() ?: 0L
                )
            })
        }
        awaitClose { reg.remove() }
    }

    private fun toCall(id: String, d: Map<String, Any?>): GroupCallDoc = GroupCallDoc(
        id = id,
        groupId = d["groupId"] as? String ?: "",
        hostUid = d["hostUid"] as? String ?: "",
        hostName = d["hostName"] as? String ?: "",
        type = if (d["type"] == "video") "video" else "voice",
        status = d["status"] as? String ?: "",
        createdAt = (d["createdAt"] as? Number)?.toLong() ?: 0L,
        participants = (d["participants"] as? List<*>)?.filterIsInstance<String>().orEmpty()
    )

    companion object {
        private const val TAG = "GroupCallRepository"
    }
}

/** Calls to the diBadge Worker for group calls: ring the group, and talk to the Cloudflare SFU for us. */
object GroupCallApi {
    private const val BASE_URL = "https://dibadge-push.dibadge.workers.dev"
    private const val TAG = "GroupCallApi"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    }

    suspend fun ring(callId: String): Boolean =
        post("/gcall/ring", JSONObject().put("callId", callId)) != null

    /** The last person left: the Worker tells the people who never joined that they missed it. */
    suspend fun end(callId: String): Boolean =
        post("/gcall/end", JSONObject().put("callId", callId)) != null

    /** Relay (TURN) servers, for phones on strict networks. Empty when they could not be fetched. */
    suspend fun iceServers(callId: String): List<IceServer> {
        val json = post("/gcall/ice", JSONObject().put("callId", callId)) ?: return emptyList()
        val arr = json.optJSONArray("iceServers") ?: return emptyList()
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

    /**
     * One request to the SFU. [op] is "session", "tracks", "renegotiate" or "close".
     * Returns the SFU's answer, or null when it failed.
     */
    suspend fun sfu(
        callId: String,
        op: String,
        sessionId: String? = null,
        tracks: JSONArray? = null,
        sdpType: String? = null,
        sdp: String? = null
    ): JSONObject? {
        val body = JSONObject().put("callId", callId).put("op", op)
        if (sessionId != null) body.put("sessionId", sessionId)
        if (tracks != null) body.put("tracks", tracks)
        if (sdp != null && sdpType != null) {
            body.put("sessionDescription", JSONObject().put("type", sdpType).put("sdp", sdp))
        }
        return post("/gcall/sfu", body)
    }

    private suspend fun post(path: String, body: JSONObject): JSONObject? {
        for (attempt in 0..1) {
            try {
                val token = FirebaseAuth.getInstance().currentUser?.getIdToken(attempt > 0)?.await()?.token ?: return null
                val request = Request.Builder()
                    .url("$BASE_URL$path")
                    .header("Authorization", "Bearer $token")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
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
            kotlinx.coroutines.delay(1000)
        }
        return null
    }
}