package com.fabxdi.dibadge.viewmodel

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One announcement in a group chat.
 * Stored at personalGroups/{groupId}/announcements/{id}.
 * readAt maps a member uid to the time (ms) they tapped "Got it".
 */
data class AnnouncementItem(
    val id: String,
    val text: String,
    val authorUid: String,
    val authorName: String,
    val authorRole: String,
    val createdAt: Long,
    val mustAck: Boolean,
    /** Auto-unpin time in ms; 0 = never. */
    val endAt: Long,
    /** Time an admin ended it by hand; 0 = not ended. */
    val endedAt: Long,
    val readAt: Map<String, Long>
) {
    fun isOngoing(now: Long): Boolean = endedAt == 0L && (endAt == 0L || endAt > now)

    /** When this announcement stopped being ongoing (only meaningful once it has ended). */
    fun endedOn(): Long = if (endedAt != 0L) endedAt else endAt
}

class AnnouncementViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _items = MutableStateFlow<List<AnnouncementItem>>(emptyList())
    val items: StateFlow<List<AnnouncementItem>> = _items.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** Last problem (for example rules not published); the screen shows it once and clears it. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var registration: ListenerRegistration? = null
    private var listeningGroup: String = ""

    private fun col(groupId: String) =
        db.collection("personalGroups").document(groupId).collection("announcements")

    fun start(groupId: String) {
        if (groupId == listeningGroup) return
        registration?.remove()
        listeningGroup = groupId
        _items.value = emptyList()
        _loaded.value = false
        registration = col(groupId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    _error.value = "Could not load announcements"
                    _loaded.value = true
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                _items.value = snap.documents.mapNotNull { doc ->
                    val text = doc.getString("text") ?: return@mapNotNull null
                    val rawReads = doc.get("readAt") as? Map<*, *> ?: emptyMap<Any, Any>()
                    val reads = HashMap<String, Long>()
                    for ((k, v) in rawReads) {
                        val key = k as? String ?: continue
                        reads[key] = (v as? Number)?.toLong() ?: 0L
                    }
                    AnnouncementItem(
                        id = doc.id,
                        text = text,
                        authorUid = doc.getString("authorUid") ?: "",
                        authorName = doc.getString("authorName") ?: "",
                        authorRole = doc.getString("authorRole") ?: "member",
                        createdAt = doc.getLong("createdAt") ?: 0L,
                        mustAck = doc.getBoolean("mustAck") ?: true,
                        endAt = doc.getLong("endAt") ?: 0L,
                        endedAt = doc.getLong("endedAt") ?: 0L,
                        readAt = reads
                    )
                }
                _loaded.value = true
            }
    }

    fun clearError() { _error.value = null }

    /** Posts a new announcement. The author counts as having read it. */
    fun post(
        groupId: String,
        text: String,
        authorName: String,
        authorRole: String,
        mustAck: Boolean,
        autoEndDays: Int,
        onDone: (Boolean) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null || text.isBlank()) { onDone(false); return }
        val now = System.currentTimeMillis()
        val data = hashMapOf<String, Any>(
            "text" to text.trim(),
            "authorUid" to uid,
            "authorName" to authorName,
            "authorRole" to authorRole,
            "createdAt" to now,
            "mustAck" to mustAck,
            "endAt" to (if (autoEndDays > 0) now + autoEndDays * 86_400_000L else 0L),
            "endedAt" to 0L,
            "readAt" to hashMapOf(uid to now)
        )
        col(groupId).add(data)
            .addOnSuccessListener { onDone(true) }
            .addOnFailureListener { _error.value = "Could not post the announcement"; onDone(false) }
    }

    /** "Got it": records that the signed-in member has read this announcement. */
    fun markRead(groupId: String, announcementId: String) {
        val uid = auth.currentUser?.uid ?: return
        col(groupId).document(announcementId)
            .update("readAt.$uid", System.currentTimeMillis())
            .addOnFailureListener { _error.value = "Could not confirm. Check your connection" }
    }

    /** Moves an ongoing announcement to Previous straight away. */
    fun endNow(groupId: String, announcementId: String) {
        col(groupId).document(announcementId)
            .update("endedAt", System.currentTimeMillis())
            .addOnFailureListener { _error.value = "Could not end the announcement" }
    }

    fun delete(groupId: String, announcementId: String) {
        col(groupId).document(announcementId).delete()
            .addOnFailureListener { _error.value = "Could not delete the announcement" }
    }

    override fun onCleared() {
        registration?.remove()
        super.onCleared()
    }
}