package com.fabxdi.dibadge.viewmodel

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One announcement in a group chat.
 * Stored at personalGroups/{groupId}/announcements/{id}.
 * readAt maps a member uid to the time (ms) they tapped "Got it".
 *
 * An announcement shared to linked groups is also saved as a copy (same id) in each of those groups.
 * The copy has originGroupId / originGroupName set. The sender only ever sees a count for the copies.
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
    val readAt: Map<String, Long>,
    /** Linked groups this announcement was also sent to (only on the original). */
    val sharedGroupIds: List<String> = emptyList(),
    /** Set on a copy: the group it came from. */
    val originGroupId: String = "",
    val originGroupName: String = ""
) {
    fun isOngoing(now: Long): Boolean = endedAt == 0L && (endAt == 0L || endAt > now)

    /** When this announcement stopped being ongoing (only meaningful once it has ended). */
    fun endedOn(): Long = if (endedAt != 0L) endedAt else endAt
}

/** Read count across the linked groups of one announcement. total is 0 until a linked group has reported it. */
data class LinkedReadCount(val read: Int, val total: Int)

class AnnouncementViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _items = MutableStateFlow<List<AnnouncementItem>>(emptyList())
    val items: StateFlow<List<AnnouncementItem>> = _items.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** announcement id -> read count of the linked groups, counts only (no names). */
    private val _linkedCounts = MutableStateFlow<Map<String, LinkedReadCount>>(emptyMap())
    val linkedCounts: StateFlow<Map<String, LinkedReadCount>> = _linkedCounts.asStateFlow()

    /** Last problem (for example rules not published); the screen shows it once and clears it. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var registration: ListenerRegistration? = null
    private var listeningGroup: String = ""
    private val linkedRegistrations = HashMap<String, ListenerRegistration>()
    private val syncedTotals = HashMap<String, Int>()

    private fun col(groupId: String) =
        db.collection("personalGroups").document(groupId).collection("announcements")

    fun start(groupId: String) {
        if (groupId == listeningGroup) return
        registration?.remove()
        clearLinkedListeners()
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
                val list = snap.documents.mapNotNull { doc ->
                    val text = doc.getString("text") ?: return@mapNotNull null
                    val rawReads = doc.get("readAt") as? Map<*, *> ?: emptyMap<Any, Any>()
                    val reads = HashMap<String, Long>()
                    for ((k, v) in rawReads) {
                        val key = k as? String ?: continue
                        reads[key] = (v as? Number)?.toLong() ?: 0L
                    }
                    val shared = (doc.get("sharedGroupIds") as? List<*>)
                        ?.mapNotNull { it as? String } ?: emptyList()
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
                        readAt = reads,
                        sharedGroupIds = shared,
                        originGroupId = doc.getString("originGroupId") ?: "",
                        originGroupName = doc.getString("originGroupName") ?: ""
                    )
                }
                _items.value = list
                _loaded.value = true
                watchLinkedCounts(groupId, list)
            }
    }

    /** Listens to the per-group read counters of announcements that were shared to linked groups. */
    private fun watchLinkedCounts(groupId: String, list: List<AnnouncementItem>) {
        val shared = list.filter { it.sharedGroupIds.isNotEmpty() }.map { it.id }.toSet()
        // Stop listening to announcements that are gone
        for (id in linkedRegistrations.keys.toList()) {
            if (id !in shared) {
                linkedRegistrations.remove(id)?.remove()
                _linkedCounts.value = _linkedCounts.value - id
            }
        }
        for (id in shared) {
            if (linkedRegistrations.containsKey(id)) continue
            linkedRegistrations[id] = col(groupId).document(id).collection("linkedReads")
                .addSnapshotListener { snap, err ->
                    if (err != null || snap == null) return@addSnapshotListener
                    var read = 0
                    var total = 0
                    for (d in snap.documents) {
                        read += (d.getLong("readCount") ?: 0L).toInt()
                        total += (d.getLong("total") ?: 0L).toInt()
                    }
                    _linkedCounts.value = _linkedCounts.value + (id to LinkedReadCount(read, total))
                }
        }
    }

    private fun clearLinkedListeners() {
        linkedRegistrations.values.forEach { it.remove() }
        linkedRegistrations.clear()
        _linkedCounts.value = emptyMap()
    }

    fun clearError() { _error.value = null }

    /**
     * Posts a new announcement. The author counts as having read it.
     * targetGroupIds: linked groups that also get a copy (the screen only offers accepted links).
     */
    fun post(
        groupId: String,
        groupName: String,
        text: String,
        authorName: String,
        authorRole: String,
        mustAck: Boolean,
        autoEndDays: Int,
        targetGroupIds: List<String>,
        onDone: (Boolean) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null || text.isBlank()) { onDone(false); return }
        val now = System.currentTimeMillis()
        val endAt = if (autoEndDays > 0) now + autoEndDays * 86_400_000L else 0L
        val targets = targetGroupIds.filter { it.isNotBlank() && it != groupId }.distinct()

        val ref = col(groupId).document()
        val base = hashMapOf<String, Any>(
            "text" to text.trim(),
            "authorUid" to uid,
            "authorName" to authorName,
            "authorRole" to authorRole,
            "createdAt" to now,
            "mustAck" to mustAck,
            "endAt" to endAt,
            "endedAt" to 0L
        )
        val origin = HashMap(base).apply {
            put("readAt", hashMapOf(uid to now))
            if (targets.isNotEmpty()) put("sharedGroupIds", targets)
        }

        val batch = db.batch()
        batch.set(ref, origin)
        for (target in targets) {
            val copy = HashMap(base).apply {
                put("readAt", hashMapOf<String, Long>())
                put("originGroupId", groupId)
                put("originGroupName", groupName)
            }
            // Same id as the original, so ending or deleting the original can find the copy
            batch.set(col(target).document(ref.id), copy)
        }
        batch.commit()
            .addOnSuccessListener { onDone(true) }
            .addOnFailureListener { _error.value = "Could not post the announcement"; onDone(false) }
    }

    /** "Got it": records that the signed-in member has read this announcement. */
    fun markRead(groupId: String, item: AnnouncementItem) {
        val uid = auth.currentUser?.uid ?: return
        if (item.readAt.containsKey(uid)) return
        val batch = db.batch()
        batch.update(col(groupId).document(item.id), "readAt.$uid", System.currentTimeMillis())
        if (item.originGroupId.isNotBlank()) {
            // Tell the sending group one more person read it (a number only, no name)
            batch.set(
                col(item.originGroupId).document(item.id).collection("linkedReads").document(groupId),
                hashMapOf<String, Any>("readCount" to FieldValue.increment(1)),
                SetOptions.merge()
            )
        }
        batch.commit()
            .addOnFailureListener { _error.value = "Could not confirm. Check your connection" }
    }

    /** A member of a receiving group tells the sending group how many people are in this group. */
    fun syncLinkedTotal(groupId: String, item: AnnouncementItem, memberCount: Int) {
        if (item.originGroupId.isBlank() || memberCount <= 0) return
        val key = "$groupId/${item.id}"
        if (syncedTotals[key] == memberCount) return
        syncedTotals[key] = memberCount
        col(item.originGroupId).document(item.id).collection("linkedReads").document(groupId)
            .set(hashMapOf<String, Any>("total" to memberCount), SetOptions.merge())
            .addOnFailureListener { syncedTotals.remove(key) }
    }

    /** Moves an ongoing announcement to Previous straight away (also in the groups it was shared to). */
    fun endNow(groupId: String, item: AnnouncementItem) {
        val now = System.currentTimeMillis()
        col(groupId).document(item.id).update("endedAt", now)
            .addOnFailureListener { _error.value = "Could not end the announcement" }
        for (target in item.sharedGroupIds) {
            // A copy that was already deleted there is simply skipped
            col(target).document(item.id).update("endedAt", now)
        }
    }

    fun delete(groupId: String, item: AnnouncementItem) {
        col(groupId).document(item.id).delete()
            .addOnFailureListener { _error.value = "Could not delete the announcement" }
        for (target in item.sharedGroupIds) {
            col(target).document(item.id).delete()
        }
    }

    override fun onCleared() {
        registration?.remove()
        clearLinkedListeners()
        super.onCleared()
    }
}