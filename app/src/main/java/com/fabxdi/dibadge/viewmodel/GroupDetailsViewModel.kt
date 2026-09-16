package com.fabxdi.dibadge.viewmodel

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fabxdi.dibadge.util.FilePickerUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.math.abs

data class GroupMember(
    val uid: String,
    val displayName: String,
    val email: String = "",
    val phoneNumber: String = "",
    val isYou: Boolean = false,
    val isAdmin: Boolean = false
)

data class GroupConnectionItem(
    val connectionId: String,
    val otherEntityId: String,
    val otherEntityName: String,
    val otherEntityType: String,
    val status: String,
    val isIncomingPending: Boolean = false
)

data class GroupFolderFile(
    val id: String,
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long = 0,
    val uploadPath: String = ""
)

data class GroupMediaItem(
    val id: String,
    val url: String,
    val name: String = "",
    val mediaType: String = "image"
)

class GroupDetailsViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val _groupCode = MutableStateFlow<String>("")
    val groupCode: StateFlow<String> = _groupCode.asStateFlow()

    private val _groupName = MutableStateFlow<String>("")
    val groupName: StateFlow<String> = _groupName.asStateFlow()

    private val _groupSubtitle = MutableStateFlow<String>("")
    val groupSubtitle: StateFlow<String> = _groupSubtitle.asStateFlow()

    private val _members = MutableStateFlow<List<GroupMember>>(emptyList())
    val members: StateFlow<List<GroupMember>> = _members.asStateFlow()

    private val _isAdmin = MutableStateFlow<Boolean>(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _acceptedConnections = MutableStateFlow<List<GroupConnectionItem>>(emptyList())
    val acceptedConnections: StateFlow<List<GroupConnectionItem>> = _acceptedConnections.asStateFlow()

    private val _pendingRequests = MutableStateFlow<List<GroupConnectionItem>>(emptyList())
    val pendingRequests: StateFlow<List<GroupConnectionItem>> = _pendingRequests.asStateFlow()

    private val _files = MutableStateFlow<List<GroupFolderFile>>(emptyList())
    val files: StateFlow<List<GroupFolderFile>> = _files.asStateFlow()

    private val _media = MutableStateFlow<List<GroupMediaItem>>(emptyList())
    val media: StateFlow<List<GroupMediaItem>> = _media.asStateFlow()

    private val _isLoading = MutableStateFlow<Boolean>(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _codeLookupResult = MutableStateFlow<GroupConnectionItem?>(null)
    val codeLookupResult: StateFlow<GroupConnectionItem?> = _codeLookupResult.asStateFlow()

    private val _memberSearchCandidates = MutableStateFlow<List<GroupMember>>(emptyList())
    val memberSearchCandidates: StateFlow<List<GroupMember>> = _memberSearchCandidates.asStateFlow()

    private var currentGroupId: String = ""

    fun loadGroupDetails(groupId: String) {
        if (groupId.isBlank()) return
        currentGroupId = groupId
        _isLoading.value = true

        viewModelScope.launch {
            try {
                fetchOrCreateGroupCode(groupId)
                listenToMembers(groupId)
                listenToConnections(groupId)
                listenToFiles(groupId)
                listenToMedia(groupId)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun fetchOrCreateGroupCode(groupId: String) {
        val fallbackCode = generateGroupCode(groupId)
        _groupCode.value = fallbackCode

        try {
            val groupDocRef = db.collection("personalGroups").document(groupId)
            val snapshot = groupDocRef.get().await()

            if (snapshot.exists()) {
                val name = snapshot.getString("name") ?: snapshot.getString("title") ?: "Group"
                _groupName.value = name

                val subtitle = snapshot.getString("subtitle") ?: snapshot.getString("description") ?: ""
                _groupSubtitle.value = subtitle

                val creatorId = snapshot.getString("creatorId") ?: snapshot.getString("createdBy") ?: ""
                val currentUid = auth.currentUser?.uid ?: ""
                _isAdmin.value = (creatorId == currentUid)

                val existingCode = snapshot.getString("code")
                if (!existingCode.isNullOrEmpty()) {
                    _groupCode.value = existingCode
                } else {
                    val generatedCode = fallbackCode
                    _groupCode.value = generatedCode

                    val codeDoc = mapOf(
                        "entityId" to groupId,
                        "entityType" to "personalGroup",
                        "name" to name,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    db.collection("groupCodes").document(generatedCode).set(codeDoc, SetOptions.merge())
                    groupDocRef.set(mapOf("code" to generatedCode, "creatorId" to creatorId.ifBlank { currentUid }), SetOptions.merge())
                }
            } else {
                if (_groupName.value.isBlank()) _groupName.value = "Group #$groupId"
                _isAdmin.value = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback unique code remains active
        }
    }

    fun updateGroupDetails(newName: String, newSubtitle: String, onDone: () -> Unit = {}) {
        if (currentGroupId.isBlank()) return
        viewModelScope.launch {
            try {
                db.collection("personalGroups").document(currentGroupId)
                    .set(
                        mapOf(
                            "name" to newName.trim(),
                            "title" to newName.trim(),
                            "subtitle" to newSubtitle.trim()
                        ),
                        SetOptions.merge()
                    ).await()
                _groupName.value = newName.trim()
                _groupSubtitle.value = newSubtitle.trim()
                onDone()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun generateGroupCode(groupId: String): String {
        val charset = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789!@$%*"
        var hash = abs(groupId.hashCode().toLong()) + 17L
        val sb = StringBuilder()
        for (i in 0 until 8) {
            val charIndex = abs((hash + i * 31).toInt()) % charset.length
            sb.append(charset[charIndex])
            hash = (hash * 31 + i + 7) % 1000000007
        }
        val code = sb.toString()
        return "${code.substring(0, 4)}-${code.substring(4, 8)}"
    }

    private fun CharSequence?.isNull_or_Empty(): Boolean = this == null || this.isEmpty()

    private fun listenToMembers(groupId: String) {
        db.collection("personalGroups").document(groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val memberUids = (snapshot.get("members") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                val creatorId = snapshot.getString("creatorId") ?: snapshot.getString("createdBy") ?: ""

                viewModelScope.launch {
                    resolveMembers(memberUids, creatorId)
                }
            }
    }

    private suspend fun resolveMembers(uids: List<String>, creatorId: String) {
        val currentUid = auth.currentUser?.uid ?: ""
        val memberList = mutableListOf<GroupMember>()

        for (uid in uids) {
            val isYou = (uid == currentUid)
            val isAdmin = (uid == creatorId)

            var name = if (isYou) {
                if (isAdmin) "You (admin)" else "You"
            } else ""

            var email = ""
            var phone = ""

            try {
                val userDoc = db.collection("users").document(uid).get().await()
                if (userDoc.exists()) {
                    email = userDoc.getString("email") ?: ""
                    phone = userDoc.getString("phoneNumber") ?: userDoc.getString("phone") ?: ""
                    val docName = userDoc.getString("displayName") ?: userDoc.getString("name") ?: ""
                    if (!isYou) {
                        name = docName.ifBlank { email.ifBlank { phone.ifBlank { uid } } }
                    }
                }
            } catch (e: Exception) {
                if (!isYou) name = uid
            }

            memberList.add(
                GroupMember(
                    uid = uid,
                    displayName = name.ifBlank { uid },
                    email = email,
                    phoneNumber = phone,
                    isYou = isYou,
                    isAdmin = isAdmin
                )
            )
        }

        _members.value = memberList
    }

    fun searchUserToAdd(query: String) {
        if (query.isBlank()) {
            _memberSearchCandidates.value = emptyList()
            return
        }

        val q = query.trim()
        viewModelScope.launch {
            try {
                val currentUid = auth.currentUser?.uid ?: ""
                val existingMemberUids = _members.value.map { it.uid }.toSet()
                val results = mutableListOf<GroupMember>()

                if (_isAdmin.value) {
                    // Admin can search anyone by phone or email
                    val byPhone = db.collection("users").whereEqualTo("phoneNumber", q).get().await()
                    val byEmail = db.collection("users").whereEqualTo("email", q).get().await()

                    val allDocs = (byPhone.documents + byEmail.documents).distinctBy { it.id }
                    for (doc in allDocs) {
                        if (doc.id !in existingMemberUids) {
                            val name = doc.getString("displayName") ?: doc.getString("email") ?: doc.getString("phoneNumber") ?: doc.id
                            results.add(
                                GroupMember(
                                    uid = doc.id,
                                    displayName = name,
                                    email = doc.getString("email") ?: "",
                                    phoneNumber = doc.getString("phoneNumber") ?: ""
                                )
                            )
                        }
                    }
                } else {
                    // Non-admin can only add established personal connections
                    val connSnap = db.collection("userConnections")
                        .whereArrayContains("participants", currentUid)
                        .whereEqualTo("status", "accepted")
                        .get().await()

                    for (doc in connSnap.documents) {
                        val participants = (doc.get("participants") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                        val otherUid = participants.find { it != currentUid }
                        if (otherUid != null && otherUid !in existingMemberUids) {
                            val userDoc = db.collection("users").document(otherUid).get().await()
                            if (userDoc.exists()) {
                                val name = userDoc.getString("displayName") ?: userDoc.getString("email") ?: userDoc.getString("phoneNumber") ?: otherUid
                                val phone = userDoc.getString("phoneNumber") ?: ""
                                val email = userDoc.getString("email") ?: ""
                                if (name.contains(q, ignoreCase = true) || phone.contains(q) || email.contains(q, ignoreCase = true)) {
                                    results.add(GroupMember(uid = otherUid, displayName = name, email = email, phoneNumber = phone))
                                }
                            }
                        }
                    }
                }

                _memberSearchCandidates.value = results
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addMemberToGroup(userUid: String, onDone: () -> Unit = {}) {
        if (currentGroupId.isBlank() || userUid.isBlank()) return

        viewModelScope.launch {
            try {
                db.collection("personalGroups").document(currentGroupId)
                    .update("members", FieldValue.arrayUnion(userUid))
                    .await()
                _memberSearchCandidates.value = emptyList()
                onDone()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun listenToConnections(groupId: String) {
        db.collection("groupConnections")
            .whereArrayContains("participants", groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                viewModelScope.launch {
                    val acceptedList = mutableListOf<GroupConnectionItem>()
                    val pendingList = mutableListOf<GroupConnectionItem>()

                    for (doc in snapshot.documents) {
                        val connId = doc.id
                        val initiatorId = doc.getString("initiatorId") ?: ""
                        val targetId = doc.getString("targetId") ?: ""
                        val initiatorType = doc.getString("initiatorType") ?: "personalGroup"
                        val targetType = doc.getString("targetType") ?: "personalGroup"
                        val status = doc.getString("status") ?: ""

                        if (status == "removed") continue

                        val isInitiator = (initiatorId == groupId)
                        val otherEntityId = if (isInitiator) targetId else initiatorId
                        val otherEntityType = if (isInitiator) targetType else initiatorType

                        // Fetch live name
                        var otherName = otherEntityId
                        try {
                            val otherDoc = if (otherEntityType == "personalGroup") {
                                db.collection("personalGroups").document(otherEntityId).get().await()
                            } else {
                                db.collection("projects").document(otherEntityId).get().await()
                            }
                            if (otherDoc.exists()) {
                                otherName = otherDoc.getString("name") ?: otherDoc.getString("title") ?: otherEntityId
                            }
                        } catch (e: Exception) {}

                        val item = GroupConnectionItem(
                            connectionId = connId,
                            otherEntityId = otherEntityId,
                            otherEntityName = otherName,
                            otherEntityType = otherEntityType,
                            status = status,
                            isIncomingPending = (!isInitiator && status == "pending")
                        )

                        if (status == "accepted") {
                            acceptedList.add(item)
                        } else if (status == "pending") {
                            if (!isInitiator) {
                                pendingList.add(item)
                            }
                        }
                    }

                    _acceptedConnections.value = acceptedList
                    _pendingRequests.value = pendingList
                }
            }
    }

    fun lookupGroupCode(codeToSearch: String) {
        val code = codeToSearch.trim().uppercase()
        if (code.isBlank()) {
            _codeLookupResult.value = null
            return
        }

        viewModelScope.launch {
            try {
                val codeDoc = db.collection("groupCodes").document(code).get().await()
                if (codeDoc.exists()) {
                    val targetId = codeDoc.getString("entityId") ?: ""
                    val targetType = codeDoc.getString("entityType") ?: "personalGroup"

                    if (targetId == currentGroupId) {
                        _codeLookupResult.value = null
                        return@launch
                    }

                    var liveName = codeDoc.getString("name") ?: targetId
                    try {
                        val liveDoc = if (targetType == "personalGroup") {
                            db.collection("personalGroups").document(targetId).get().await()
                        } else {
                            db.collection("projects").document(targetId).get().await()
                        }
                        if (liveDoc.exists()) {
                            liveName = liveDoc.getString("name") ?: liveDoc.getString("title") ?: liveName
                        }
                    } catch (e: Exception) {}

                    _codeLookupResult.value = GroupConnectionItem(
                        connectionId = "",
                        otherEntityId = targetId,
                        otherEntityName = liveName,
                        otherEntityType = targetType,
                        status = "lookup"
                    )
                } else {
                    _codeLookupResult.value = null
                }
            } catch (e: Exception) {
                _codeLookupResult.value = null
            }
        }
    }

    fun sendConnectRequest(targetEntityId: String, targetEntityType: String, onDone: () -> Unit = {}) {
        if (currentGroupId.isBlank() || targetEntityId.isBlank()) return

        val connectionId = if (currentGroupId < targetEntityId) "${currentGroupId}_${targetEntityId}" else "${targetEntityId}_${currentGroupId}"

        viewModelScope.launch {
            try {
                val data = mapOf(
                    "connectionId" to connectionId,
                    "initiatorId" to currentGroupId,
                    "targetId" to targetEntityId,
                    "initiatorType" to "personalGroup",
                    "targetType" to targetEntityType,
                    "participants" to listOf(currentGroupId, targetEntityId),
                    "status" to "pending",
                    "createdAt" to FieldValue.serverTimestamp()
                )

                db.collection("groupConnections").document(connectionId)
                    .set(data, SetOptions.merge())
                    .await()

                _codeLookupResult.value = null
                onDone()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateConnectionStatus(connectionId: String, newStatus: String) {
        if (connectionId.isBlank()) return

        viewModelScope.launch {
            try {
                db.collection("groupConnections").document(connectionId)
                    .update("status", newStatus, "updatedAt", FieldValue.serverTimestamp())
                    .await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun listenToFiles(groupId: String) {
        db.collection("personalGroups").document(groupId)
            .collection("files")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val fileList = snapshot.documents.mapNotNull { doc ->
                    val url = doc.getString("downloadUrl") ?: return@mapNotNull null
                    val name = doc.getString("name") ?: doc.getString("fileName") ?: "File"
                    val size = doc.getLong("size") ?: 0L
                    val path = doc.getString("uploadPath") ?: ""
                    GroupFolderFile(
                        id = doc.id,
                        name = name,
                        downloadUrl = url,
                        sizeBytes = size,
                        uploadPath = path
                    )
                }

                _files.value = fileList
            }
    }

    fun uploadFileToGroupFolder(context: Context, fileUri: Uri, onComplete: () -> Unit = {}) {
        if (currentGroupId.isBlank()) return

        viewModelScope.launch {
            try {
                val fileName = FilePickerUtils.getFileName(context, fileUri)
                val folderId = UUID.randomUUID().toString().take(8)
                val storagePath = "personalGroups/$currentGroupId/folders/$folderId/$fileName"

                val storageRef = storage.reference.child(storagePath)
                storageRef.putFile(fileUri).await()
                val downloadUrl = storageRef.downloadUrl.await().toString()

                val fileDoc = mapOf(
                    "name" to fileName,
                    "downloadUrl" to downloadUrl,
                    "uploadPath" to storagePath,
                    "uploadedBy" to (auth.currentUser?.uid ?: ""),
                    "createdAt" to FieldValue.serverTimestamp()
                )

                db.collection("personalGroups").document(currentGroupId)
                    .collection("files")
                    .add(fileDoc)
                    .await()

                Toast.makeText(context, "File uploaded successfully!", Toast.LENGTH_SHORT).show()
                onComplete()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Upload failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun listenToMedia(groupId: String) {
        db.collection("personalGroups").document(groupId)
            .collection("media")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val mediaList = snapshot.documents.mapNotNull { doc ->
                    val url = doc.getString("url") ?: doc.getString("downloadUrl") ?: return@mapNotNull null
                    val name = doc.getString("name") ?: doc.getString("fileName") ?: "File"
                    val explicitType = doc.getString("type") ?: doc.getString("mediaType")
                    
                    val type = when {
                        explicitType != null -> explicitType.lowercase()
                        doc.getBoolean("isVideo") == true || url.contains(".mp4") || url.contains("video") || name.endsWith(".mp4") -> "video"
                        name.endsWith(".pdf") || name.endsWith(".doc") || name.endsWith(".docx") || name.endsWith(".xls") || name.endsWith(".xlsx") || name.endsWith(".txt") -> "document"
                        else -> "image"
                    }

                    GroupMediaItem(
                        id = doc.id,
                        url = url,
                        name = name,
                        mediaType = type
                    )
                }

                _media.value = mediaList
            }
    }
}
