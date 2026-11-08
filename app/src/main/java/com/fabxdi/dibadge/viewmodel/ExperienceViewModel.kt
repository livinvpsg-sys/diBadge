package com.fabxdi.dibadge.viewmodel

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * One job in the Experience section of the profile.
 * Dates are stored as "yyyy-MM-dd" text. [endDate] is empty while [isCurrent] is true.
 */
data class ExperienceItem(
    val id: String = "",
    val role: String = "",
    val company: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val isCurrent: Boolean = false,
    val description: String = "",
    val createdAt: Long = 0L
)

/**
 * Keeps the Experience list in users/{uid}/experience/{id}.
 * It saves on its own (right after Add / Edit / Delete), so it does not use the profile Save button.
 */
class ExperienceViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private var listener: ListenerRegistration? = null
    private var listeningUid: String? = null

    private val _items = MutableStateFlow<List<ExperienceItem>>(emptyList())

    private val _error = MutableStateFlow<String?>(null)

    /** Set when Firestore refuses to load or save (for example the rules are missing). */
    val error: StateFlow<String?> = _error.asStateFlow()

    fun clearError() { _error.value = null }

    /** Newest first: the job you currently hold, then by start date. The first item is the latest. */
    val items: StateFlow<List<ExperienceItem>> = _items.asStateFlow()

    private val authListener = FirebaseAuth.AuthStateListener { a ->
        val uid = a.currentUser?.uid
        if (uid == null) {
            detach()
            _items.value = emptyList()
        } else if (uid != listeningUid) {
            attach(uid)
        }
    }

    init {
        auth.addAuthStateListener(authListener)
    }

    private fun attach(uid: String) {
        detach()
        listeningUid = uid
        _items.value = emptyList()
        listener = db.collection("users").document(uid).collection("experience")
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    _error.value = "Couldn't load experience: ${e.message}"
                    // Firestore stops a listener after an error, so drop it; ensureListening() starts a new one
                    listener?.remove()
                    listener = null
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                _items.value = snap.documents.map { d ->
                    ExperienceItem(
                        id = d.id,
                        role = d.getString("role") ?: "",
                        company = d.getString("company") ?: "",
                        startDate = d.getString("startDate") ?: "",
                        endDate = d.getString("endDate") ?: "",
                        isCurrent = d.getBoolean("isCurrent") ?: false,
                        description = d.getString("description") ?: "",
                        createdAt = d.getLong("createdAt") ?: 0L
                    )
                }.sortedWith(
                    compareByDescending<ExperienceItem> { it.isCurrent }
                        .thenByDescending { it.startDate }
                        .thenByDescending { it.createdAt }
                )
            }
    }

    /** Starts listening again if an earlier error (for example missing rules) stopped it. */
    fun ensureListening() {
        val uid = auth.currentUser?.uid ?: return
        if (listener == null || listeningUid != uid) attach(uid)
    }

    private fun detach() {
        listener?.remove()
        listener = null
        listeningUid = null
    }

    fun save(item: ExperienceItem, onDone: (Boolean) -> Unit = {}) {
        val uid = auth.currentUser?.uid ?: return onDone(false)
        val id = item.id.ifBlank { UUID.randomUUID().toString() }
        val data = mapOf(
            "role" to item.role.trim(),
            "company" to item.company.trim(),
            "startDate" to item.startDate,
            "endDate" to if (item.isCurrent) "" else item.endDate,
            "isCurrent" to item.isCurrent,
            "description" to item.description.trim(),
            "createdAt" to (if (item.createdAt > 0) item.createdAt else System.currentTimeMillis())
        )
        db.collection("users").document(uid).collection("experience").document(id)
            .set(data, SetOptions.merge())
            .addOnSuccessListener { onDone(true) }
            .addOnFailureListener { e ->
                _error.value = "Couldn't save experience: ${e.message}"
                onDone(false)
            }
    }

    fun delete(item: ExperienceItem, onDone: (Boolean) -> Unit = {}) {
        val uid = auth.currentUser?.uid ?: return onDone(false)
        if (item.id.isBlank()) return onDone(false)
        db.collection("users").document(uid).collection("experience").document(item.id)
            .delete()
            .addOnSuccessListener { onDone(true) }
            .addOnFailureListener { onDone(false) }
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authListener)
        detach()
    }
}
