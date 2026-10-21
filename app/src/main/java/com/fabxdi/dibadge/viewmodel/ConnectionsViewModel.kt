package com.fabxdi.dibadge.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    init {
        loadConnections()
    }

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

    fun acceptRequest(item: ConnectionItem) {
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            try {
                firestore.collection("users").document(user.uid)
                    .collection("userConnections").document(item.id)
                    .update("status", "connected")
            } catch (e: Exception) {}
        }
    }

    fun declineRequest(item: ConnectionItem) {
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            try {
                firestore.collection("users").document(user.uid)
                    .collection("userConnections").document(item.id)
                    .delete()
            } catch (e: Exception) {}
        }
    }

    fun cancelSentRequest(item: ConnectionItem) {
        declineRequest(item)
    }

    fun removeConnection(item: ConnectionItem) {
        declineRequest(item)
    }

    fun sendInvite(item: ConnectionItem) {
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            try {
                firestore.collection("users").document(user.uid)
                    .collection("userConnections").document(item.id)
                    .set(
                        mapOf(
                            "uid" to item.uid,
                            "name" to item.name,
                            "organization" to item.organization,
                            "role" to item.role,
                            "status" to "sent",
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
            } catch (e: Exception) {}
        }
    }
}
