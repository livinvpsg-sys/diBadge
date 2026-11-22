package com.fabxdi.dibadge.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fabxdi.dibadge.util.ChatSeen
import com.fabxdi.dibadge.util.fixDownloadUrl
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the home list shows for one chat: the last message, who wrote it, and how many are unread. */
data class ChatPreview(
    val text: String,
    val senderUid: String,
    val senderName: String,
    val isCall: Boolean,
    val at: Long,
    val unread: Int
)

private data class RecentMessage(
    val at: Long,
    val senderUid: String,
    val senderName: String,
    val text: String,
    val isCall: Boolean,
    val deleted: Boolean,
    /** A disconnect notice: not shown as the chat's last message and not counted as unread */
    val hidden: Boolean = false
)

/** Watches the newest messages of every chat on the home list. */
class HomeChatPreviewsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = FirebaseFirestore.getInstance()
    private val registrations = HashMap<String, ListenerRegistration>()
    private val recent = HashMap<String, List<RecentMessage>>()

    private val _previews = MutableStateFlow<Map<String, ChatPreview>>(emptyMap())
    val previews: StateFlow<Map<String, ChatPreview>> = _previews.asStateFlow()

    /** Chats that are person to person (the saved chat record says type "direct"). */
    private val _directIds = MutableStateFlow<Set<String>>(emptySet())
    val directIds: StateFlow<Set<String>> = _directIds.asStateFlow()

    /** Chat id -> photo to show: the other person's profile photo (direct chats) or the group photo. */
    private val _photos = MutableStateFlow<Map<String, String>>(emptyMap())
    val photos: StateFlow<Map<String, String>> = _photos.asStateFlow()
    private val groupRegs = HashMap<String, ListenerRegistration>()
    private val userRegs = HashMap<String, ListenerRegistration>()
    private val groupPhoto = HashMap<String, String>()
    private val otherOf = HashMap<String, String>()
    private val _others = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Chat id -> the other person's id, for direct chats. Used to colour the avatar the same everywhere. */
    val otherUids: StateFlow<Map<String, String>> = _others.asStateFlow()
    private val userPhoto = HashMap<String, String>()

    private fun publishPhotos() {
        val out = HashMap<String, String>()
        for (id in groupRegs.keys) {
            val other = otherOf[id]
            val url = if (other != null) userPhoto[other].orEmpty() else groupPhoto[id].orEmpty()
            if (url.isNotBlank()) out[id] = url
        }
        _photos.value = out
        _others.value = HashMap(otherOf)
    }

    private fun watchUser(uid: String) {
        if (userRegs.containsKey(uid)) return
        userRegs[uid] = db.collection("users").document(uid).addSnapshotListener { doc, _ ->
            userPhoto[uid] = fixDownloadUrl(doc?.getString("photoUrl").orEmpty())
            publishPhotos()
        }
    }

    private fun watchGroupDoc(id: String) {
        val me = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        groupRegs[id] = db.collection("personalGroups").document(id).addSnapshotListener { doc, err ->
            if (err != null || doc == null) return@addSnapshotListener
            val direct = doc.getString("type") == "direct"
            if (direct) {
                _directIds.value = _directIds.value + id
                val members = (doc.get("members") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                val other = members.firstOrNull { it != me }
                if (other != null) {
                    otherOf[id] = other
                    _others.value = HashMap(otherOf)
                    watchUser(other)
                }
            } else {
                otherOf.remove(id)
                groupPhoto[id] = fixDownloadUrl(doc.getString("photoUrl") ?: doc.getString("avatarUrl") ?: "")
            }
            publishPhotos()
        }
    }

    init {
        // A chat was opened or closed: recount what is unread
        viewModelScope.launch { ChatSeen.version.collect { recompute() } }
    }

    /** Starts watching the given chats and stops watching the ones that are gone. */
    fun watch(ids: Collection<String>) {
        val wanted = ids.filter { it.isNotBlank() }.toSet()
        for (id in registrations.keys.toList()) {
            if (id !in wanted) {
                registrations.remove(id)?.remove()
                recent.remove(id)
                groupRegs.remove(id)?.remove()
                groupPhoto.remove(id)
                otherOf.remove(id)
            }
        }
        for (id in wanted) {
            if (registrations.containsKey(id)) continue
            ChatSeen.ensure(getApplication(), id)
            if (!groupRegs.containsKey(id)) watchGroupDoc(id)
            registrations[id] = db.collection("personalGroups").document(id).collection("messages")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(30)
                .addSnapshotListener { snap, err ->
                    if (err != null || snap == null) return@addSnapshotListener
                    recent[id] = snap.documents.map { d ->
                        val data = d.data ?: emptyMap()
                        RecentMessage(
                            at = millisOf(data["timestamp"]),
                            senderUid = data["senderUid"] as? String ?: "",
                            senderName = data["senderName"] as? String ?: "",
                            text = previewOf(data),
                            isCall = data["msgType"] == "callNotice",
                            deleted = data["deleted"] == true,
                            hidden = data["msgType"] == "disconnectNotice" ||
                                    (data["msgType"] == "memberNotice" && (data["text"] as? String)?.contains(" disconnected from ") == true)
                        )
                    }.sortedByDescending { it.at }
                    recompute()
                }
        }
    }

    /** A message time is saved as a number (milliseconds) by some messages and as a Firestore Timestamp by others (poll, location ...). */
    private fun millisOf(v: Any?): Long = when (v) {
        is Number -> v.toLong()
        is com.google.firebase.Timestamp -> v.toDate().time
        else -> 0L
    }

    private fun recompute() {
        val me = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val out = HashMap<String, ChatPreview>()
        for ((id, list) in recent) {
            val last = list.firstOrNull { !it.deleted && !it.hidden } ?: continue
            val seen = ChatSeen.get(getApplication(), id)
            val unread = list.count { !it.deleted && !it.hidden && it.senderUid != me && it.at > seen }
            out[id] = ChatPreview(last.text, last.senderUid, last.senderName, last.isCall, last.at, unread)
        }
        _previews.value = out
    }

    override fun onCleared() {
        registrations.values.forEach { it.remove() }
        registrations.clear()
        groupRegs.values.forEach { it.remove() }
        groupRegs.clear()
        userRegs.values.forEach { it.remove() }
        userRegs.clear()
        super.onCleared()
    }

    private fun previewOf(d: Map<String, Any?>): String {
        val type = d["msgType"] as? String ?: ""
        if (type == "poll") return "Poll: " + (d["pollQuestion"] as? String ?: "")
        if (type == "location") return "Shared a location"
        if (type == "contact") return "Contact: " + (d["text"] as? String ?: "").substringBefore("\n")
        if (type == "audio" || d["isVoiceNote"] == true) return "Voice message"
        val text = (d["text"] as? String ?: "").trim().replace(Regex("\\s+"), " ")
        if (text.isNotEmpty()) return text
        val att = (d["attachments"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        if (att.isNotEmpty()) {
            val first = att[0].substringBefore("?").lowercase()
            if (Regex(".*\\.(jpe?g|png|webp|gif|heic)$").matches(first)) return if (att.size > 1) "${att.size} photos" else "Photo"
            if (Regex(".*\\.(mp4|mov|3gp|mkv|webm)$").matches(first)) return "Video"
            return if (att.size > 1) "${att.size} files" else "File"
        }
        return "Message"
    }
}