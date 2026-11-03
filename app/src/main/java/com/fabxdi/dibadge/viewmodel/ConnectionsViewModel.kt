package com.fabxdi.dibadge.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class ConnectionItem(
    val id: String,
    val uid: String,
    val name: String,
    val organization: String = "",
    val role: String = "",
    val bio: String = "",
    val photoUrl: String = "",
    val status: String = "connected", // "connected", "received", "sent"
    val timestamp: Long = System.currentTimeMillis()
)

class ConnectionsViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val _connections = MutableStateFlow<List<ConnectionItem>>(emptyList())
    val connections: StateFlow<List<ConnectionItem>> = _connections.asStateFlow()

    private val _receivedRequests = MutableStateFlow<List<ConnectionItem>>(emptyList())
    val receivedRequests: StateFlow<List<ConnectionItem>> = _receivedRequests.asStateFlow()

    private val _sentRequests = MutableStateFlow<List<ConnectionItem>>(emptyList())
    val sentRequests: StateFlow<List<ConnectionItem>> = _sentRequests.asStateFlow()

    // People on diBadge found by search (not yet connected)
    private val _searchResults = MutableStateFlow<List<ConnectionItem>>(emptyList())
    val searchResults: StateFlow<List<ConnectionItem>> = _searchResults.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private var searchSeq = 0

    init {
        loadConnections()
    }

    private fun myConnections() =
        auth.currentUser?.let { firestore.collection("users").document(it.uid).collection("userConnections") }

    private fun theirConnections(uid: String) =
        firestore.collection("users").document(uid).collection("userConnections")

    fun loadConnections() {
        val user = auth.currentUser ?: return
        firestore.collection("users").document(user.uid).collection("userConnections")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                val connList = mutableListOf<ConnectionItem>()
                val recList = mutableListOf<ConnectionItem>()
                val sentList = mutableListOf<ConnectionItem>()

                for (doc in snapshot.documents) {
                    val id = doc.id
                    val targetUid = doc.getString("uid") ?: id
                    val name = doc.getString("name") ?: doc.getString("displayName") ?: "User"
                    val org = doc.getString("organization") ?: doc.getString("company") ?: ""
                    val role = doc.getString("role") ?: ""
                    val bio = doc.getString("bio") ?: ""
                    val photoUrl = doc.getString("photoUrl") ?: ""
                    val status = doc.getString("status") ?: "connected"
                    val time = doc.getLong("timestamp") ?: System.currentTimeMillis()

                    val item = ConnectionItem(
                        id = id,
                        uid = targetUid,
                        name = name,
                        organization = org,
                        role = role,
                        bio = bio,
                        photoUrl = photoUrl,
                        status = status,
                        timestamp = time
                    )

                    when (status) {
                        "connected" -> connList.add(item)
                        "received" -> recList.add(item)
                        "sent" -> sentList.add(item)
                    }
                }

                _connections.value = connList.sortedBy { it.name.lowercase() }
                _receivedRequests.value = recList.sortedByDescending { it.timestamp }
                _sentRequests.value = sentList.sortedByDescending { it.timestamp }
            }
    }

    // ---------------------------------------------------------------- Search

    private fun DocumentSnapshot.toPerson(): ConnectionItem = ConnectionItem(
        id = id,
        uid = id,
        name = getString("displayName") ?: getString("name") ?: "User",
        organization = getString("organization") ?: getString("company") ?: "",
        role = getString("role") ?: getString("jobTitle") ?: "",
        bio = getString("bio") ?: "",
        photoUrl = getString("photoUrl") ?: getString("photoURL") ?: "",
        status = "none"
    )

    /**
     * Finds people by name (starts with), exact email, or exact badge number.
     * Email is exact-match only so addresses cannot be browsed.
     */
    fun searchPeople(raw: String) {
        val q = raw.trim()
        val me = auth.currentUser?.uid
        val seq = ++searchSeq
        if (q.length < 2 || me == null) {
            _searchResults.value = emptyList()
            _searching.value = false
            return
        }
        _searching.value = true
        viewModelScope.launch {
            val lower = q.lowercase()
            val users = firestore.collection("users")
            val queries = listOf(
                users.whereGreaterThanOrEqualTo("displayNameLower", lower)
                    .whereLessThanOrEqualTo("displayNameLower", lower + ""),
                users.whereEqualTo("emailLower", lower),
                users.whereEqualTo("badgeNumber", q),
                users.whereEqualTo("badgeNumber", q.uppercase())
            )
            val found = LinkedHashMap<String, ConnectionItem>()
            for (query in queries) {
                try {
                    val snap = query.limit(20).get().await()
                    for (doc in snap.documents) {
                        if (doc.id == me) continue
                        if (doc.getBoolean("hideFromSearch") == true) continue
                        if (!found.containsKey(doc.id)) found[doc.id] = doc.toPerson()
                    }
                } catch (e: Exception) {
                    // one failing query should not hide the others
                }
            }
            if (seq == searchSeq) {
                _searchResults.value = found.values.sortedBy { it.name.lowercase() }
                _searching.value = false
            }
        }
    }

    fun clearSearch() {
        searchSeq++
        _searchResults.value = emptyList()
        _searching.value = false
    }

    // -------------------------------------------------------------- Requests

    /** Sends a connection request: "sent" on my side, "received" on theirs. */
    fun sendInvite(item: ConnectionItem) {
        val me = auth.currentUser ?: return
        val target = item.uid
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            try {
                myConnections()?.document(target)?.set(
                    mapOf(
                        "uid" to target,
                        "name" to item.name,
                        "organization" to item.organization,
                        "role" to item.role,
                        "photoUrl" to item.photoUrl,
                        "status" to "sent",
                        "timestamp" to now
                    )
                )?.await()

                val mine = try {
                    firestore.collection("users").document(me.uid).get().await()
                } catch (e: Exception) {
                    null
                }
                theirConnections(target).document(me.uid).set(
                    mapOf(
                        "uid" to me.uid,
                        "name" to (mine?.getString("displayName") ?: me.displayName ?: me.email ?: "User"),
                        "organization" to (mine?.getString("organization") ?: mine?.getString("company") ?: ""),
                        "role" to (mine?.getString("role") ?: mine?.getString("jobTitle") ?: ""),
                        "photoUrl" to (mine?.getString("photoUrl") ?: mine?.getString("photoURL") ?: ""),
                        "status" to "received",
                        "timestamp" to now
                    )
                ).await()
            } catch (e: Exception) {
            }
        }
    }

    fun acceptRequest(item: ConnectionItem) {
        val me = auth.currentUser ?: return
        viewModelScope.launch {
            try {
                myConnections()?.document(item.id)?.update("status", "connected")?.await()
                theirConnections(item.uid).document(me.uid)
                    .update("status", "connected").await()
            } catch (e: Exception) {
            }
        }
    }

    fun declineRequest(item: ConnectionItem) {
        val me = auth.currentUser ?: return
        viewModelScope.launch {
            try {
                myConnections()?.document(item.id)?.delete()?.await()
            } catch (e: Exception) {
            }
            try {
                theirConnections(item.uid).document(me.uid).delete().await()
            } catch (e: Exception) {
            }
        }
    }

    fun cancelSentRequest(item: ConnectionItem) {
        declineRequest(item)
    }

    fun removeConnection(item: ConnectionItem) {
        declineRequest(item)
    }
}
