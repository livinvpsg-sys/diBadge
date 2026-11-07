package com.fabxdi.dibadge.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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
    val timestamp: Long = System.currentTimeMillis(),
    val country: String = "" // ISO code, only filled for people found by search / suggestions
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

    // People I have blocked (same blockedUsers/{me}/blocked list the chat screen uses)
    private val _blockedPeople = MutableStateFlow<List<ConnectionItem>>(emptyList())
    val blockedPeople: StateFlow<List<ConnectionItem>> = _blockedPeople.asStateFlow()

    // "People you may know": a pool of people on diBadge. The screen hides anyone you already
    // have a connection or request with.
    private val _suggestions = MutableStateFlow<List<ConnectionItem>>(emptyList())
    val suggestions: StateFlow<List<ConnectionItem>> = _suggestions.asStateFlow()

    // One-off messages for the screen to show (request sent, or why something failed)
    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val message: SharedFlow<String> = _message

    private fun reportFailure(action: String, e: Exception) {
        e.printStackTrace()
        val reason = if (e.message?.contains("PERMISSION_DENIED") == true) {
            "not allowed. Check that the latest Firestore rules are published."
        } else {
            e.localizedMessage ?: "please try again."
        }
        _message.tryEmit("Couldn't $action: $reason")
    }

    private var connectionsListener: ListenerRegistration? = null
    private var blockedListener: ListenerRegistration? = null
    private var loadedForUid: String? = null

    // This ViewModel outlives a sign-out, so it follows the signed-in account itself:
    // when the account changes, the old listeners are removed and the old lists are emptied.
    private val authStateListener = FirebaseAuth.AuthStateListener { fa ->
        val uid = fa.currentUser?.uid
        if (uid == loadedForUid) return@AuthStateListener
        loadedForUid = uid

        connectionsListener?.remove(); connectionsListener = null
        blockedListener?.remove(); blockedListener = null
        _connections.value = emptyList()
        _receivedRequests.value = emptyList()
        _sentRequests.value = emptyList()
        _searchResults.value = emptyList()
        _blockedPeople.value = emptyList()
        _suggestions.value = emptyList()

        if (uid != null) {
            loadConnections()
            loadBlocked()
            loadSuggestions()
        }
    }

    init {
        auth.addAuthStateListener(authStateListener)
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authStateListener)
        connectionsListener?.remove()
        blockedListener?.remove()
    }

    private fun loadBlocked() {
        val me = auth.currentUser?.uid ?: return
        blockedListener?.remove()
        blockedListener = firestore.collection("blockedUsers").document(me).collection("blocked")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                _blockedPeople.value = snapshot.documents.map { doc ->
                    ConnectionItem(
                        id = doc.id,
                        uid = doc.getString("uid") ?: doc.id,
                        name = doc.getString("name") ?: "User",
                        organization = doc.getString("organization") ?: "",
                        status = "blocked"
                    )
                }.sortedBy { it.name.lowercase() }
            }
    }

    /**
     * Leaves out people I blocked and people who blocked me, so a blocked person can never find
     * the account by name, email or badge number. (Whether someone blocked me is read from
     * their own blocked entry, which the rules let the blocked person open.)
     */
    private suspend fun withoutBlocked(me: String, people: Collection<ConnectionItem>): List<ConnectionItem> {
        val mine = _blockedPeople.value.map { it.uid }.toSet()
        val candidates = people.filter { it.uid !in mine }
        return coroutineScope {
            candidates.map { p ->
                async {
                    val blockedMe = try {
                        firestore.collection("blockedUsers").document(p.uid)
                            .collection("blocked").document(me).get().await().exists()
                    } catch (e: Exception) {
                        false
                    }
                    if (blockedMe) null else p
                }
            }.awaitAll().filterNotNull()
        }
    }

    /** Block: remember it (chat reads the same list), then remove the connection on both sides. */
    fun blockUser(item: ConnectionItem) {
        val me = auth.currentUser ?: return
        viewModelScope.launch {
            try {
                firestore.collection("blockedUsers").document(me.uid)
                    .collection("blocked").document(item.uid)
                    .set(
                        mapOf(
                            "uid" to item.uid,
                            "name" to item.name,
                            "organization" to item.organization,
                            "createdAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    ).await()
            } catch (e: Exception) {
                reportFailure("block ${item.name}", e)
                return@launch
            }
            try {
                myConnections()?.document(item.id)?.delete()?.await()
            } catch (e: Exception) {
            }
            try {
                theirConnections(item.uid).document(me.uid).delete().await()
            } catch (e: Exception) {
            }
            _message.tryEmit("${item.name} blocked")
        }
    }

    fun unblockUser(item: ConnectionItem) {
        val me = auth.currentUser ?: return
        viewModelScope.launch {
            try {
                firestore.collection("blockedUsers").document(me.uid)
                    .collection("blocked").document(item.uid).delete().await()
                _message.tryEmit("${item.name} unblocked")
            } catch (e: Exception) {
                reportFailure("unblock ${item.name}", e)
            }
        }
    }

    private fun myConnections() =
        auth.currentUser?.let { firestore.collection("users").document(it.uid).collection("userConnections") }

    private fun theirConnections(uid: String) =
        firestore.collection("users").document(uid).collection("userConnections")

    fun loadConnections() {
        val user = auth.currentUser ?: return
        connectionsListener?.remove()
        connectionsListener = firestore.collection("users").document(user.uid).collection("userConnections")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                val connList = mutableListOf<ConnectionItem>()
                val recList = mutableListOf<ConnectionItem>()
                val sentList = mutableListOf<ConnectionItem>()

                for (doc in snapshot.documents) {
                    val id = doc.id
                    val targetUid = doc.getString("uid") ?: id
                    if (targetUid == user.uid || id == user.uid) {
                        // A request to yourself can never be valid: remove it and do not show it
                        doc.reference.delete()
                        continue
                    }
                    val name = doc.getString("name") ?: doc.getString("displayName") ?: "User"
                    val org = doc.getString("organization") ?: doc.getString("company") ?: ""
                    val role = doc.getString("role") ?: ""
                    val bio = doc.getString("bio") ?: ""
                    val country = doc.getString("country") ?: ""
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
                        timestamp = time,
                        country = country
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

    /** True when this user document is the signed-in account (same id, or same email on a duplicate document). */
    private fun DocumentSnapshot.isSignedInAccount(me: String): Boolean {
        if (id == me) return true
        val myEmail = auth.currentUser?.email?.trim()?.lowercase().orEmpty()
        if (myEmail.isBlank()) return false
        val theirs = (getString("emailLower") ?: getString("email") ?: "").trim().lowercase()
        return theirs == myEmail
    }

    private fun DocumentSnapshot.toPerson(): ConnectionItem = ConnectionItem(
        id = id,
        uid = id,
        name = getString("displayName") ?: getString("name") ?: "User",
        organization = getString("organization") ?: getString("company") ?: "",
        role = getString("role") ?: getString("jobTitle") ?: "",
        bio = getString("bio") ?: "",
        photoUrl = getString("photoUrl") ?: getString("photoURL") ?: "",
        status = "none",
        country = getString("country") ?: ""
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
                    .whereLessThanOrEqualTo("displayNameLower", lower + "\uf8ff"),
                users.whereEqualTo("emailLower", lower),
                users.whereEqualTo("badgeNumber", q),
                users.whereEqualTo("badgeNumber", q.uppercase())
            )
            val found = LinkedHashMap<String, ConnectionItem>()
            for (query in queries) {
                try {
                    val snap = query.limit(20).get().await()
                    for (doc in snap.documents) {
                        if (doc.isSignedInAccount(me)) continue
                        if (doc.getBoolean("hideFromSearch") == true) continue
                        if (!found.containsKey(doc.id)) found[doc.id] = doc.toPerson()
                    }
                } catch (e: Exception) {
                    // one failing query should not hide the others
                }
            }
            val visible = withoutBlocked(me, found.values).filter { it.uid != me }
            if (seq == searchSeq) {
                _searchResults.value = visible.sortedBy { it.name.lowercase() }
                _searching.value = false
            }
        }
    }

    /** Same-organisation people first, then recently active people. Personal accounts only. */
    fun loadSuggestions() {
        val me = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            val users = firestore.collection("users")
            val found = LinkedHashMap<String, ConnectionItem>()

            fun add(docs: List<DocumentSnapshot>) {
                for (doc in docs) {
                    if (doc.isSignedInAccount(me)) continue
                    if (doc.getBoolean("hideFromSearch") == true) continue
                    if (doc.getString("displayName").isNullOrBlank()) continue
                    if (!found.containsKey(doc.id)) found[doc.id] = doc.toPerson()
                }
            }

            try {
                val mine = users.document(me).get().await()
                val org = (mine.getString("organization") ?: mine.getString("company") ?: "").trim()
                if (org.isNotBlank()) {
                    add(users.whereEqualTo("organization", org).limit(20).get().await().documents)
                }
            } catch (e: Exception) {
                // suggestions are optional - ignore
            }
            try {
                add(
                    users.orderBy("updatedAt", Query.Direction.DESCENDING)
                        .limit(30).get().await().documents
                )
            } catch (e: Exception) {
                // suggestions are optional - ignore
            }
            _suggestions.value = withoutBlocked(me, found.values).filter { it.uid != me }
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
        if (target.isBlank() || target == me.uid) {
            _message.tryEmit("You can't send a connection request to yourself")
            return
        }
        if (_connections.value.any { it.uid == target } ||
            _sentRequests.value.any { it.uid == target } ||
            _receivedRequests.value.any { it.uid == target }
        ) {
            _message.tryEmit("Already connected or a request is pending")
            return
        }
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
                        "bio" to item.bio,
                        "country" to item.country,
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
                        "bio" to (mine?.getString("bio") ?: ""),
                        "country" to (mine?.getString("country") ?: ""),
                        "status" to "received",
                        "timestamp" to now
                    )
                ).await()
                _message.tryEmit("Request sent")
            } catch (e: Exception) {
                reportFailure("send the request", e)
            }
        }
    }

    fun acceptRequest(item: ConnectionItem) {
        val me = auth.currentUser ?: return
        if (item.uid == me.uid) {
            declineRequest(item)
            return
        }
        viewModelScope.launch {
            try {
                myConnections()?.document(item.id)?.update("status", "connected")?.await()
                theirConnections(item.uid).document(me.uid)
                    .update("status", "connected").await()
            } catch (e: Exception) {
                reportFailure("accept the request", e)
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

    /** Ends an accepted connection on both sides. The other person is not notified. */
    fun removeConnection(item: ConnectionItem) {
        val me = auth.currentUser ?: return
        viewModelScope.launch {
            try {
                myConnections()?.document(item.id)?.delete()?.await()
            } catch (e: Exception) {
                reportFailure("disconnect", e)
                return@launch
            }
            try {
                theirConnections(item.uid).document(me.uid).delete().await()
            } catch (e: Exception) {
                // Their side could not be reached; this side is already removed
            }
            _message.tryEmit("Disconnected from ${item.name}")
        }
    }
}
