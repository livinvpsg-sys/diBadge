package com.fabxdi.dibadge.viewmodel

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fabxdi.dibadge.data.AppDatabase
import com.fabxdi.dibadge.data.HomeEntryEntity
import com.fabxdi.dibadge.ui.home.home_entry.chat.ChatMessage
import com.fabxdi.dibadge.util.FilePickerUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.ZoneId
import java.util.UUID
import kotlin.math.abs

data class GroupMember(
    val uid: String,
    val displayName: String,
    val email: String = "",
    val phoneNumber: String = "",
    val badgeNumber: String = "",
    val isYou: Boolean = false,
    val isAdmin: Boolean = false,
    val role: String = "member"
) {
    val id: String get() = uid
    val name: String get() = displayName
}

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
    val uploadPath: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class GroupMediaItem(
    val id: String,
    val url: String,
    val name: String = "",
    val mediaType: String = "image",
    val timestamp: Long = System.currentTimeMillis()
)

data class GroupCredentialItem(
    val id: String,
    val groupId: String,
    val ownerUid: String,
    val ownerName: String,
    val myDocId: String,
    val fileUri: String,
    val name: String,
    val expiresAt: Long? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val addedByUid: String? = null,
    val addedByName: String? = null,
    val requestId: String? = null
)

data class GroupCredentialRequestItem(
    val id: String,
    val groupId: String,
    val name: String,
    val requestedByUid: String,
    val requestedByName: String,
    val memberUid: String,
    val memberName: String,
    val status: String = "pending",
    val certIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

data class CredentialSubmitItem(
    val myDocId: String,
    val fileUri: String,
    val name: String,
    val expiresAtMs: Long?
)

data class LibraryCategoryItem(
    val id: String,
    val name: String,
    val order: Int = 0,
    val fileCount: Int = 0
)

data class LibraryRevisionItem(
    val revision: String,
    val downloadUrl: String,
    val uploadedAt: Long = System.currentTimeMillis(),
    val uploadedByName: String = ""
)

data class LibraryFileItem(
    val id: String,
    val groupId: String,
    val name: String,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val revision: String? = null,
    val downloadUrl: String,
    val storagePath: String = "",
    val sizeBytes: Long = 0,
    val mimeType: String = "",
    val uploadedByUid: String,
    val uploadedByName: String,
    val uploadedAt: Long = System.currentTimeMillis(),
    val previousRevisions: List<LibraryRevisionItem> = emptyList()
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

    private val _groupPhotoUrl = MutableStateFlow<String>("")
    val groupPhotoUrl: StateFlow<String> = _groupPhotoUrl.asStateFlow()

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

    private val _credentials = MutableStateFlow<List<GroupCredentialItem>>(emptyList())
    val credentials: StateFlow<List<GroupCredentialItem>> = _credentials.asStateFlow()

    private val _credentialRequests = MutableStateFlow<List<GroupCredentialRequestItem>>(emptyList())
    val credentialRequests: StateFlow<List<GroupCredentialRequestItem>> = _credentialRequests.asStateFlow()

    private val _libraryCategories = MutableStateFlow<List<LibraryCategoryItem>>(emptyList())
    val libraryCategories: StateFlow<List<LibraryCategoryItem>> = _libraryCategories.asStateFlow()

    private val _libraryFiles = MutableStateFlow<List<LibraryFileItem>>(emptyList())
    val libraryFiles: StateFlow<List<LibraryFileItem>> = _libraryFiles.asStateFlow()

    private val _whoCanUploadLibrary = MutableStateFlow<String>("admins_only")
    val whoCanUploadLibrary: StateFlow<String> = _whoCanUploadLibrary.asStateFlow()

    private val _isLoading = MutableStateFlow<Boolean>(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _codeLookupResult = MutableStateFlow<GroupConnectionItem?>(null)
    val codeLookupResult: StateFlow<GroupConnectionItem?> = _codeLookupResult.asStateFlow()

    private val _memberSearchCandidates = MutableStateFlow<List<GroupMember>>(emptyList())
    val memberSearchCandidates: StateFlow<List<GroupMember>> = _memberSearchCandidates.asStateFlow()

    private var currentGroupId: String = ""

    fun loadGroupDetails(groupId: String, initialName: String = "", initialSubtitle: String = "") {
        if (groupId.isBlank()) return
        currentGroupId = groupId
        _isLoading.value = true

        _groupName.value = initialName.ifBlank { "Group" }
        val isMemberCountInitial = initialSubtitle.trim().lowercase().let { it.endsWith("member") || it.endsWith("members") }
        _groupSubtitle.value = if (initialSubtitle == "test" || isMemberCountInitial) "" else initialSubtitle

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
                val name = (snapshot.getString("name") ?: snapshot.getString("title") ?: _groupName.value).ifBlank { "Group" }
                _groupName.value = name

                val rawSubtitle = snapshot.getString("subtitle") ?: snapshot.getString("description") ?: _groupSubtitle.value
                val isMemberCountRaw = rawSubtitle.trim().lowercase().let { it.endsWith("member") || it.endsWith("members") }
                val subtitle = if (rawSubtitle == "test" || isMemberCountRaw) "" else rawSubtitle
                _groupSubtitle.value = subtitle

                val photoUrl = snapshot.getString("photoUrl") ?: snapshot.getString("avatarUrl") ?: ""
                _groupPhotoUrl.value = photoUrl

                val creatorId = snapshot.getString("creatorId") ?: snapshot.getString("createdBy") ?: ""
                val ownerId = snapshot.getString("ownerId") ?: creatorId
                val currentUid = auth.currentUser?.uid ?: ""
                _isAdmin.value = (creatorId.isBlank() || creatorId == currentUid || ownerId == currentUid || currentUid.isBlank())

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

    private fun updateRoomHomeEntry(context: Context, newName: String, newSubtitle: String) {
        val id = currentGroupId.toIntOrNull() ?: return
        val dao = AppDatabase.getDatabase(context).reminderDao()
        viewModelScope.launch(Dispatchers.IO) {
            val initials = newName.take(1).uppercase()
            dao.insertHomeEntry(
                HomeEntryEntity(
                    id = id,
                    title = newName,
                    subtitle = newSubtitle,
                    initials = initials
                )
            )
        }
    }

    fun updateGroupName(context: Context, newName: String, onDone: () -> Unit = {}) {
        if (currentGroupId.isBlank()) return
        val trimmed = newName.trim()
        _groupName.value = trimmed
        updateRoomHomeEntry(context, trimmed, _groupSubtitle.value)
        onDone()

        viewModelScope.launch {
            try {
                db.collection("personalGroups").document(currentGroupId)
                    .set(mapOf("name" to trimmed, "title" to trimmed), SetOptions.merge())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateGroupSubtitle(context: Context, newSubtitle: String, onDone: () -> Unit = {}) {
        if (currentGroupId.isBlank()) return
        val trimmed = newSubtitle.trim()
        _groupSubtitle.value = trimmed
        updateRoomHomeEntry(context, _groupName.value, trimmed)
        onDone()

        viewModelScope.launch {
            try {
                db.collection("personalGroups").document(currentGroupId)
                    .set(mapOf("subtitle" to trimmed, "description" to trimmed), SetOptions.merge())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun listenToCredentials(groupId: String) {
        if (groupId.isBlank()) return
        db.collection("groups").document(groupId)
            .collection("credentials")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val certList = snapshot.documents.mapNotNull { doc ->
                    val ownerUid = doc.getString("ownerUid") ?: ""
                    val ownerName = doc.getString("ownerName") ?: "User"
                    val myDocId = doc.getString("myDocId") ?: ""
                    val fileUri = doc.getString("fileUri") ?: ""
                    val name = doc.getString("name") ?: "Credential"
                    val expiresAt = doc.getLong("expiresAt")
                    val addedAt = doc.getLong("addedAt") ?: System.currentTimeMillis()

                    val addedByUid = doc.getString("addedByUid")
                    val addedByName = doc.getString("addedByName")
                    val requestId = doc.getString("requestId")

                    GroupCredentialItem(
                        id = doc.id,
                        groupId = groupId,
                        ownerUid = ownerUid,
                        ownerName = ownerName,
                        myDocId = myDocId,
                        fileUri = fileUri,
                        name = name,
                        expiresAt = expiresAt,
                        addedAt = addedAt,
                        addedByUid = addedByUid,
                        addedByName = addedByName,
                        requestId = requestId
                    )
                }
                _credentials.value = certList
            }
    }

    fun listenToCredentialRequests(groupId: String) {
        if (groupId.isBlank()) return
        db.collection("groups").document(groupId)
            .collection("credentialRequests")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val reqList = snapshot.documents.mapNotNull { doc ->
                    val name = doc.getString("name") ?: "Credential"
                    val requestedByUid = doc.getString("requestedByUid") ?: doc.getString("requestedBy") ?: ""
                    val requestedByName = doc.getString("requestedByName") ?: "Admin"
                    val memberUid = doc.getString("memberUid") ?: ""
                    val memberName = doc.getString("memberName") ?: "Member"
                    val status = doc.getString("status") ?: "pending"
                    val certIds = (doc.get("certIds") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                    val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()

                    GroupCredentialRequestItem(
                        id = doc.id,
                        groupId = groupId,
                        name = name,
                        requestedByUid = requestedByUid,
                        requestedByName = requestedByName,
                        memberUid = memberUid,
                        memberName = memberName,
                        status = status,
                        certIds = certIds,
                        createdAt = createdAt
                    )
                }
                _credentialRequests.value = reqList
            }
    }

    fun createCredentialRequests(
        groupId: String,
        certName: String,
        targetMembers: List<GroupMember>
    ) {
        if (groupId.isBlank() || certName.isBlank() || targetMembers.isEmpty()) return
        val currentUid = auth.currentUser?.uid ?: ""
        val currentName = auth.currentUser?.displayName.takeIf { !it.isNullOrBlank() } ?: "Admin"

        viewModelScope.launch {
            for (member in targetMembers) {
                val reqId = UUID.randomUUID().toString()
                val reqData = mutableMapOf<String, Any?>(
                    "id" to reqId,
                    "groupId" to groupId,
                    "name" to certName.trim(),
                    "requestedByUid" to currentUid,
                    "requestedByName" to currentName,
                    "memberUid" to member.uid,
                    "memberName" to member.displayName,
                    "status" to "pending",
                    "certIds" to emptyList<String>(),
                    "createdAt" to System.currentTimeMillis()
                )

                try {
                    db.collection("groups").document(groupId)
                        .collection("credentialRequests")
                        .document(reqId)
                        .set(reqData)
                        .await()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun submitMultipleCredentials(
        groupId: String,
        targetMemberUid: String,
        targetMemberName: String,
        certItems: List<CredentialSubmitItem>,
        requestId: String? = null
    ) {
        if (groupId.isBlank() || certItems.isEmpty()) return
        val currentUid = if (targetMemberUid.isNotBlank()) targetMemberUid else (auth.currentUser?.uid ?: "")
        val currentName = if (targetMemberName.isNotBlank()) targetMemberName else (auth.currentUser?.displayName ?: "Me")

        viewModelScope.launch {
            val createdCertIds = mutableListOf<String>()
            for (item in certItems) {
                val certId = UUID.randomUUID().toString()
                createdCertIds.add(certId)

                val certData = mutableMapOf<String, Any?>(
                    "id" to certId,
                    "groupId" to groupId,
                    "ownerUid" to currentUid,
                    "ownerName" to currentName,
                    "myDocId" to item.myDocId,
                    "fileUri" to item.fileUri,
                    "name" to item.name.trim(),
                    "expiresAt" to item.expiresAtMs,
                    "addedAt" to System.currentTimeMillis(),
                    "requestId" to requestId
                )

                try {
                    db.collection("groups").document(groupId)
                        .collection("credentials")
                        .document(certId)
                        .set(certData)
                        .await()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (!requestId.isNullOrBlank()) {
                try {
                    db.collection("groups").document(groupId)
                        .collection("credentialRequests")
                        .document(requestId)
                        .update(
                            mapOf(
                                "status" to "fulfilled",
                                "certIds" to createdCertIds
                            )
                        )
                        .await()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun cancelCredentialRequest(groupId: String, requestId: String) {
        if (groupId.isBlank() || requestId.isBlank()) return
        viewModelScope.launch {
            try {
                db.collection("groups").document(groupId)
                    .collection("credentialRequests")
                    .document(requestId)
                    .update("status", "cancelled")
                    .await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addCredentialForMember(
        groupId: String,
        targetMemberUid: String,
        targetMemberName: String,
        myDocId: String,
        fileUri: String,
        name: String,
        expiresAtMs: Long?
    ) {
        if (groupId.isBlank()) return
        val currentUid = auth.currentUser?.uid ?: ""
        val currentName = auth.currentUser?.displayName.takeIf { !it.isNullOrBlank() } ?: "Admin"
        val certId = UUID.randomUUID().toString()

        val certData = mutableMapOf<String, Any?>(
            "id" to certId,
            "groupId" to groupId,
            "ownerUid" to targetMemberUid,
            "ownerName" to targetMemberName,
            "myDocId" to myDocId,
            "fileUri" to fileUri,
            "name" to name.trim(),
            "expiresAt" to expiresAtMs,
            "addedAt" to System.currentTimeMillis(),
            "addedByUid" to currentUid,
            "addedByName" to currentName
        )

        viewModelScope.launch {
            try {
                db.collection("groups").document(groupId)
                    .collection("credentials")
                    .document(certId)
                    .set(certData)
                    .await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addCredential(
        groupId: String,
        myDocId: String,
        fileUri: String,
        name: String,
        expiresAtMs: Long?
    ) {
        if (groupId.isBlank()) return
        val currentUid = auth.currentUser?.uid ?: ""
        val currentName = auth.currentUser?.displayName.takeIf { !it.isNullOrBlank() } ?: "Me"
        val certId = UUID.randomUUID().toString()

        val certData = mutableMapOf<String, Any?>(
            "id" to certId,
            "groupId" to groupId,
            "ownerUid" to currentUid,
            "ownerName" to currentName,
            "myDocId" to myDocId,
            "fileUri" to fileUri,
            "name" to name.trim(),
            "expiresAt" to expiresAtMs,
            "addedAt" to System.currentTimeMillis()
        )

        viewModelScope.launch {
            try {
                db.collection("groups").document(groupId)
                    .collection("credentials")
                    .document(certId)
                    .set(certData)
                    .await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateCredential(
        groupId: String,
        certId: String,
        newName: String,
        newExpiresAtMs: Long?
    ) {
        if (groupId.isBlank() || certId.isBlank()) return
        viewModelScope.launch {
            try {
                val updates = mutableMapOf<String, Any?>(
                    "name" to newName.trim(),
                    "expiresAt" to newExpiresAtMs
                )
                db.collection("groups").document(groupId)
                    .collection("credentials")
                    .document(certId)
                    .update(updates)
                    .await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun removeCredential(groupId: String, certId: String) {
        if (groupId.isBlank() || certId.isBlank()) return
        viewModelScope.launch {
            try {
                db.collection("groups").document(groupId)
                    .collection("credentials")
                    .document(certId)
                    .delete()
                    .await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun listenToLibrary(groupId: String) {
        if (groupId.isBlank()) return

        db.collection("groups").document(groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val perm = snapshot.getString("whoCanUploadLibrary") ?: "admins_only"
                _whoCanUploadLibrary.value = perm
            }

        db.collection("groups").document(groupId)
            .collection("libraryCategories")
            .orderBy("order")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val catList = snapshot.documents.mapNotNull { doc ->
                    val name = doc.getString("name") ?: return@mapNotNull null
                    val order = doc.getLong("order")?.toInt() ?: 0
                    val fileCount = _libraryFiles.value.count { it.categoryId == doc.id }
                    LibraryCategoryItem(id = doc.id, name = name, order = order, fileCount = fileCount)
                }
                _libraryCategories.value = catList
            }

        db.collection("groups").document(groupId)
            .collection("library")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val fileList = snapshot.documents.mapNotNull { doc ->
                    val name = doc.getString("name") ?: "Document"
                    val catId = doc.getString("categoryId")
                    val catName = doc.getString("categoryName")
                    val revision = doc.getString("revision")
                    val downloadUrl = doc.getString("downloadUrl") ?: doc.getString("url") ?: return@mapNotNull null
                    val storagePath = doc.getString("storagePath") ?: ""
                    val sizeBytes = doc.getLong("sizeBytes") ?: 0L
                    val mimeType = doc.getString("mimeType") ?: ""
                    val uploadedByUid = doc.getString("uploadedByUid") ?: ""
                    val uploadedByName = doc.getString("uploadedByName") ?: "Member"
                    val uploadedAt = doc.getLong("uploadedAt") ?: System.currentTimeMillis()

                    val revsRaw = doc.get("previousRevisions") as? List<Map<String, Any?>>
                    val previousRevs = revsRaw?.mapNotNull { rMap ->
                        val rev = rMap["revision"] as? String ?: return@mapNotNull null
                        val url = rMap["downloadUrl"] as? String ?: return@mapNotNull null
                        val uAt = (rMap["uploadedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                        val uName = rMap["uploadedByName"] as? String ?: ""
                        LibraryRevisionItem(revision = rev, downloadUrl = url, uploadedAt = uAt, uploadedByName = uName)
                    } ?: emptyList()

                    LibraryFileItem(
                        id = doc.id,
                        groupId = groupId,
                        name = name,
                        categoryId = catId,
                        categoryName = catName,
                        revision = revision,
                        downloadUrl = downloadUrl,
                        storagePath = storagePath,
                        sizeBytes = sizeBytes,
                        mimeType = mimeType,
                        uploadedByUid = uploadedByUid,
                        uploadedByName = uploadedByName,
                        uploadedAt = uploadedAt,
                        previousRevisions = previousRevs
                    )
                }.sortedByDescending { it.uploadedAt }

                _libraryFiles.value = fileList

                _libraryCategories.value = _libraryCategories.value.map { cat ->
                    cat.copy(fileCount = fileList.count { it.categoryId == cat.id })
                }
            }
    }

    fun updateLibraryUploadPermission(groupId: String, permissionSetting: String) {
        if (groupId.isBlank()) return
        _whoCanUploadLibrary.value = permissionSetting
        viewModelScope.launch {
            try {
                db.collection("groups").document(groupId)
                    .set(mapOf("whoCanUploadLibrary" to permissionSetting), SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addLibraryCategory(groupId: String, categoryName: String) {
        if (groupId.isBlank() || categoryName.isBlank()) return
        val catId = UUID.randomUUID().toString()
        val nextOrder = _libraryCategories.value.size
        viewModelScope.launch {
            try {
                db.collection("groups").document(groupId)
                    .collection("libraryCategories")
                    .document(catId)
                    .set(
                        mapOf(
                            "id" to catId,
                            "name" to categoryName.trim(),
                            "order" to nextOrder
                        )
                    )
                    .await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun renameLibraryCategory(groupId: String, categoryId: String, newName: String) {
        if (groupId.isBlank() || categoryId.isBlank() || newName.isBlank()) return
        viewModelScope.launch {
            try {
                db.collection("groups").document(groupId)
                    .collection("libraryCategories")
                    .document(categoryId)
                    .update("name", newName.trim())
                    .await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteLibraryCategory(groupId: String, categoryId: String, targetMoveCategoryId: String? = null, targetMoveCategoryName: String? = null) {
        if (groupId.isBlank() || categoryId.isBlank()) return
        viewModelScope.launch {
            try {
                val filesInCat = _libraryFiles.value.filter { it.categoryId == categoryId }
                for (f in filesInCat) {
                    val updates = mutableMapOf<String, Any?>(
                        "categoryId" to targetMoveCategoryId,
                        "categoryName" to targetMoveCategoryName
                    )
                    db.collection("groups").document(groupId)
                        .collection("library")
                        .document(f.id)
                        .update(updates)
                        .await()
                }

                db.collection("groups").document(groupId)
                    .collection("libraryCategories")
                    .document(categoryId)
                    .delete()
                    .await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun createSystemLibraryChatLabel(
        groupId: String,
        fileId: String? = null,
        fileName: String? = null,
        categoryId: String? = null,
        categoryName: String? = null,
        count: Int = 1,
        revision: String? = null
    ) {
        if (groupId.isBlank()) return
        val currentUid = auth.currentUser?.uid ?: ""
        val currentName = auth.currentUser?.displayName.takeIf { !it.isNullOrBlank() } ?: "Member"
        val msgId = UUID.randomUUID().toString()

        val sysMsgData = mutableMapOf<String, Any?>(
            "id" to msgId,
            "text" to "",
            "senderName" to currentName,
            "senderUid" to currentUid,
            "msgType" to "systemLibraryAdded",
            "libraryFileId" to fileId,
            "libraryFileName" to fileName,
            "libraryCategoryId" to categoryId,
            "libraryCategoryName" to categoryName,
            "libraryCount" to count,
            "libraryActorUid" to currentUid,
            "libraryActorName" to currentName,
            "libraryRevision" to revision,
            "timestamp" to FieldValue.serverTimestamp(),
            "createdAt" to System.currentTimeMillis()
        )

        viewModelScope.launch {
            try {
                db.collection("personalGroups").document(groupId)
                    .collection("messages")
                    .document(msgId)
                    .set(sysMsgData)
                    .await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun uploadFileToLibrary(
        groupId: String,
        categoryId: String?,
        categoryName: String?,
        fileUri: Uri,
        name: String,
        revision: String?,
        notifyMembers: Boolean,
        context: Context,
        onComplete: () -> Unit = {}
    ) {
        if (groupId.isBlank()) return
        val currentUid = auth.currentUser?.uid ?: ""
        val currentName = auth.currentUser?.displayName.takeIf { !it.isNullOrBlank() } ?: "Member"
        val fileName = name.ifBlank { FilePickerUtils.getFileName(context, fileUri) }
        val fileId = UUID.randomUUID().toString()

        val mimeType = try { context.contentResolver.getType(fileUri) ?: "" } catch (e: Exception) { "" }
        val ext = FilePickerUtils.getFileName(context, fileUri).substringAfterLast('.', "").lowercase()
        val isVideo = mimeType.startsWith("video/") || ext in listOf("mp4", "mov", "avi", "mkv", "3gp", "webm")

        if (isVideo) {
            Toast.makeText(context, "Videos cannot be uploaded to Library", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            try {
                val sizeBytes = FilePickerUtils.getFileSizeBytes(context, fileUri)
                if (sizeBytes > 25 * 1024 * 1024) {
                    Toast.makeText(context, "File size exceeds 25 MB limit", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val storagePath = "groups/$groupId/library/$fileId/$fileName"
                val storageRef = storage.reference.child(storagePath)
                storageRef.putFile(fileUri).await()
                val downloadUrl = storageRef.downloadUrl.await().toString()

                val fileDoc = mutableMapOf<String, Any?>(
                    "id" to fileId,
                    "groupId" to groupId,
                    "name" to fileName,
                    "categoryId" to categoryId,
                    "categoryName" to categoryName,
                    "revision" to revision?.trim(),
                    "downloadUrl" to downloadUrl,
                    "storagePath" to storagePath,
                    "sizeBytes" to sizeBytes,
                    "mimeType" to mimeType,
                    "uploadedByUid" to currentUid,
                    "uploadedByName" to currentName,
                    "uploadedAt" to System.currentTimeMillis(),
                    "previousRevisions" to emptyList<Map<String, Any>>()
                )

                db.collection("groups").document(groupId)
                    .collection("library")
                    .document(fileId)
                    .set(fileDoc)
                    .await()

                createSystemLibraryChatLabel(
                    groupId = groupId,
                    fileId = fileId,
                    fileName = fileName,
                    categoryId = categoryId,
                    categoryName = categoryName,
                    count = 1
                )

                Toast.makeText(context, "File uploaded to Library", Toast.LENGTH_SHORT).show()
                onComplete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun uploadNewRevisionToLibrary(
        groupId: String,
        fileItem: LibraryFileItem,
        fileUri: Uri,
        newRevision: String,
        notifyMembers: Boolean,
        context: Context,
        onComplete: () -> Unit = {}
    ) {
        if (groupId.isBlank() || fileItem.id.isBlank()) return
        val currentUid = auth.currentUser?.uid ?: ""
        val currentName = auth.currentUser?.displayName.takeIf { !it.isNullOrBlank() } ?: "Member"

        viewModelScope.launch {
            try {
                val sizeBytes = FilePickerUtils.getFileSizeBytes(context, fileUri)
                if (sizeBytes > 25 * 1024 * 1024) {
                    Toast.makeText(context, "File size exceeds 25 MB limit", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val fileName = fileItem.name
                val revPath = "groups/$groupId/library/${fileItem.id}_${System.currentTimeMillis()}/$fileName"
                val storageRef = storage.reference.child(revPath)
                storageRef.putFile(fileUri).await()
                val newDownloadUrl = storageRef.downloadUrl.await().toString()

                val oldRevObj = mapOf(
                    "revision" to (fileItem.revision ?: "Rev A"),
                    "downloadUrl" to fileItem.downloadUrl,
                    "uploadedAt" to fileItem.uploadedAt,
                    "uploadedByName" to fileItem.uploadedByName
                )

                val updatedRevisions = (fileItem.previousRevisions.map {
                    mapOf(
                        "revision" to it.revision,
                        "downloadUrl" to it.downloadUrl,
                        "uploadedAt" to it.uploadedAt,
                        "uploadedByName" to it.uploadedByName
                    )
                } + listOf(oldRevObj))

                val updates = mapOf(
                    "revision" to newRevision.trim(),
                    "downloadUrl" to newDownloadUrl,
                    "storagePath" to revPath,
                    "sizeBytes" to sizeBytes,
                    "uploadedByUid" to currentUid,
                    "uploadedByName" to currentName,
                    "uploadedAt" to System.currentTimeMillis(),
                    "previousRevisions" to updatedRevisions
                )

                db.collection("groups").document(groupId)
                    .collection("library")
                    .document(fileItem.id)
                    .update(updates)
                    .await()

                createSystemLibraryChatLabel(
                    groupId = groupId,
                    fileId = fileItem.id,
                    fileName = fileItem.name,
                    revision = newRevision.trim(),
                    count = 1
                )

                Toast.makeText(context, "New revision uploaded", Toast.LENGTH_SHORT).show()
                onComplete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun renameLibraryFile(groupId: String, fileId: String, newName: String) {
        if (groupId.isBlank() || fileId.isBlank() || newName.isBlank()) return
        viewModelScope.launch {
            try {
                db.collection("groups").document(groupId)
                    .collection("library")
                    .document(fileId)
                    .update("name", newName.trim())
                    .await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun moveLibraryFile(groupId: String, fileId: String, targetCategoryId: String, targetCategoryName: String) {
        if (groupId.isBlank() || fileId.isBlank()) return
        viewModelScope.launch {
            try {
                db.collection("groups").document(groupId)
                    .collection("library")
                    .document(fileId)
                    .update(
                        mapOf(
                            "categoryId" to targetCategoryId,
                            "categoryName" to targetCategoryName
                        )
                    )
                    .await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteLibraryFile(groupId: String, fileId: String) {
        if (groupId.isBlank() || fileId.isBlank()) return
        viewModelScope.launch {
            try {
                db.collection("groups").document(groupId)
                    .collection("library")
                    .document(fileId)
                    .delete()
                    .await()

                val sysMsgs = db.collection("personalGroups").document(groupId)
                    .collection("messages")
                    .whereEqualTo("libraryFileId", fileId)
                    .get()
                    .await()

                for (doc in sysMsgs.documents) {
                    doc.reference.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateGroupDetails(context: Context, newName: String, newSubtitle: String, onDone: () -> Unit = {}) {
        if (currentGroupId.isBlank()) return
        viewModelScope.launch {
            try {
                db.collection("personalGroups").document(currentGroupId)
                    .set(
                        mapOf(
                            "name" to newName.trim(),
                            "title" to newName.trim(),
                            "subtitle" to newSubtitle.trim(),
                            "description" to newSubtitle.trim()
                        ),
                        SetOptions.merge()
                    ).await()
                _groupName.value = newName.trim()
                _groupSubtitle.value = newSubtitle.trim()
                updateRoomHomeEntry(context, newName.trim(), newSubtitle.trim())
                onDone()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteMediaItem(context: Context, item: GroupMediaItem) {
        viewModelScope.launch {
            _media.value = _media.value.filter { it.id != item.id }
            _files.value = _files.value.filter { it.id != item.id && it.downloadUrl != item.url }
            Toast.makeText(context, "Media deleted", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteSelectedMediaForMe(context: Context, idsToDelete: Set<String>) {
        if (idsToDelete.isEmpty()) return
        viewModelScope.launch {
            _media.value = _media.value.filterNot { idsToDelete.contains(it.id) }
            _files.value = _files.value.filterNot { idsToDelete.contains(it.id) }
            Toast.makeText(context, "Deleted for you", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteSelectedMediaForAll(context: Context, idsToDelete: Set<String>) {
        if (idsToDelete.isEmpty()) return
        viewModelScope.launch {
            _media.value = _media.value.filterNot { idsToDelete.contains(it.id) }
            _files.value = _files.value.filterNot { idsToDelete.contains(it.id) }
            if (currentGroupId.isNotBlank()) {
                for (id in idsToDelete) {
                    try {
                        db.collection("personalGroups").document(currentGroupId).collection("media").document(id).delete()
                        db.collection("personalGroups").document(currentGroupId).collection("files").document(id).delete()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            Toast.makeText(context, "Deleted for everyone", Toast.LENGTH_SHORT).show()
        }
    }

    fun syncChatMessagesMedia(context: Context, messages: List<ChatMessage>) {
        if (messages.isEmpty()) return

        val extractedMedia = mutableListOf<GroupMediaItem>()
        val extractedFiles = mutableListOf<GroupFolderFile>()

        for (msg in messages) {
            val ts = try {
                msg.timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (e: Exception) {
                System.currentTimeMillis()
            }

            for (url in msg.attachments) {
                if (url.isBlank()) continue
                val name = try {
                    FilePickerUtils.getFileName(context, Uri.parse(url))
                } catch (e: Exception) {
                    url.substringAfterLast("/").substringBefore("?")
                }

                val mediaType = getMediaTypeFromUri(context, url)
                val mediaId = "${msg.id}_${url.hashCode()}"

                if (mediaType == "document") {
                    extractedFiles.add(
                        GroupFolderFile(
                            id = mediaId,
                            name = if (name.isBlank() || name == url) "Document" else name,
                            downloadUrl = url,
                            timestamp = ts
                        )
                    )
                } else {
                    extractedMedia.add(
                        GroupMediaItem(
                            id = mediaId,
                            url = url,
                            name = name,
                            mediaType = mediaType,
                            timestamp = ts
                        )
                    )
                }
            }
        }

        if (extractedMedia.isNotEmpty()) {
            val combinedMedia = (_media.value + extractedMedia).distinctBy { it.url }.sortedByDescending { it.timestamp }
            _media.value = combinedMedia
        }

        if (extractedFiles.isNotEmpty()) {
            val combinedFiles = (_files.value + extractedFiles).distinctBy { it.downloadUrl }.sortedByDescending { it.timestamp }
            _files.value = combinedFiles
        }
    }

    private fun getMediaTypeFromUri(context: Context, uriString: String): String {
        val lower = uriString.lowercase()
        if (lower.contains(".mp4") || lower.contains(".mkv") || lower.contains(".avi") || lower.contains(".mov") || lower.contains("video")) {
            return "video"
        }
        if (lower.contains(".pdf") || lower.contains(".doc") || lower.contains(".docx") || lower.contains(".xls") || lower.contains(".xlsx") || lower.contains(".txt") || lower.contains(".zip")) {
            return "document"
        }
        try {
            val uri = Uri.parse(uriString)
            val mimeType = context.contentResolver.getType(uri)
            if (mimeType != null) {
                if (mimeType.startsWith("video/")) return "video"
                if (mimeType.startsWith("image/")) return "image"
                if (mimeType.startsWith("application/") || mimeType.startsWith("text/")) return "document"
            }
        } catch (e: Exception) {}
        return "image"
    }

    private suspend fun ensureAuthenticated(): FirebaseUser? {
        var user = auth.currentUser
        if (user == null) {
            try {
                val result = auth.signInAnonymously().await()
                user = result.user
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return user
    }

    fun uploadGroupAvatarPhoto(context: Context, imageUri: Uri, onDone: () -> Unit = {}) {
        if (currentGroupId.isBlank()) return

        // Local update for instant UI feedback
        _groupPhotoUrl.value = imageUri.toString()
        updateRoomHomeEntry(context, _groupName.value, _groupSubtitle.value)

        viewModelScope.launch {
            try {
                ensureAuthenticated()
                val storageRef = storage.reference.child("personalGroups/$currentGroupId/avatar_${System.currentTimeMillis()}.jpg")
                storageRef.putFile(imageUri).await()
                val downloadUrl = storageRef.downloadUrl.await().toString()

                _groupPhotoUrl.value = downloadUrl

                db.collection("personalGroups").document(currentGroupId)
                    .set(mapOf("photoUrl" to downloadUrl, "avatarUrl" to downloadUrl), SetOptions.merge())
                    .await()

                Toast.makeText(context, "Group photo updated!", Toast.LENGTH_SHORT).show()
                onDone()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteGroupAvatarPhoto(context: Context) {
        _groupPhotoUrl.value = ""
        updateRoomHomeEntry(context, _groupName.value, _groupSubtitle.value)
        viewModelScope.launch {
            try {
                if (currentGroupId.isNotBlank()) {
                    db.collection("personalGroups").document(currentGroupId)
                        .set(mapOf("photoUrl" to "", "avatarUrl" to ""), SetOptions.merge())
                }
                Toast.makeText(context, "Group photo removed", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun transferGroupOwnership(context: Context, newOwnerUid: String) {
        if (currentGroupId.isBlank() || newOwnerUid.isBlank()) return
        val currentUid = auth.currentUser?.uid ?: "me"

        // Update local state immediately
        _members.value = _members.value.map { m ->
            when {
                m.uid == newOwnerUid -> m.copy(role = "owner", isAdmin = true)
                m.uid == currentUid || m.isYou -> m.copy(role = "admin", isAdmin = true)
                else -> m
            }
        }.sortedByDescending { it.role.equals("owner", ignoreCase = true) }

        viewModelScope.launch {
            try {
                db.collection("personalGroups").document(currentGroupId)
                    .set(
                        mapOf(
                            "ownerId" to newOwnerUid,
                            "creatorId" to newOwnerUid,
                            "roles.$newOwnerUid" to "owner",
                            "roles.$currentUid" to "admin"
                        ),
                        SetOptions.merge()
                    )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateMemberRole(context: Context, memberUid: String, newRole: String) {
        if (currentGroupId.isBlank() || memberUid.isBlank()) return

        _members.value = _members.value.map { m ->
            if (m.uid == memberUid) m.copy(role = newRole, isAdmin = newRole.equals("admin", ignoreCase = true) || newRole.equals("owner", ignoreCase = true))
            else m
        }

        viewModelScope.launch {
            try {
                db.collection("personalGroups").document(currentGroupId)
                    .set(mapOf("roles.$memberUid" to newRole), SetOptions.merge())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun leaveGroup(context: Context, onDone: () -> Unit) {
        if (currentGroupId.isBlank()) return
        val currentUid = auth.currentUser?.uid ?: "me"

        _members.value = _members.value.filter { it.uid != currentUid }

        viewModelScope.launch {
            try {
                db.collection("personalGroups").document(currentGroupId)
                    .update("members", FieldValue.arrayRemove(currentUid))
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                onDone()
            }
        }
    }

    private fun generateGroupCode(groupId: String): String {
        val charset = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789!@#$%&?"
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
        val currentUid = auth.currentUser?.uid ?: "me"
        val currentName = auth.currentUser?.displayName ?: "Livin Varghese"

        // Initialize with owner member so _members is NEVER empty and never shows "0 members"
        if (_members.value.isEmpty()) {
            _members.value = listOf(
                GroupMember(
                    uid = currentUid,
                    displayName = currentName,
                    isYou = true,
                    isAdmin = true,
                    role = "owner"
                )
            )
        }

        db.collection("personalGroups").document(groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                var memberUids = (snapshot.get("members") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                val creatorId = (snapshot.getString("creatorId") ?: snapshot.getString("createdBy") ?: currentUid).ifBlank { currentUid }
                val ownerId = (snapshot.getString("ownerId") ?: creatorId).ifBlank { creatorId }

                // Idempotent Repair: If memberUids is empty or missing creatorId, repair group document
                if (memberUids.isEmpty() || !memberUids.contains(creatorId)) {
                    val repairedUids = (memberUids + creatorId).distinct()
                    memberUids = repairedUids

                    db.collection("personalGroups").document(groupId)
                        .set(
                            mapOf(
                                "members" to repairedUids,
                                "creatorId" to creatorId,
                                "createdBy" to creatorId,
                                "ownerId" to ownerId
                            ),
                            SetOptions.merge()
                        )
                }

                viewModelScope.launch {
                    resolveMembers(memberUids, creatorId, ownerId)
                }
            }
    }

    private suspend fun resolveMembers(uids: List<String>, creatorId: String, ownerId: String) {
        val currentUid = auth.currentUser?.uid ?: "me"
        val currentName = auth.currentUser?.displayName ?: "Livin Varghese"
        val memberList = mutableListOf<GroupMember>()

        val safeUids = if (uids.isEmpty()) listOf(currentUid) else uids.distinct()

        for (uid in safeUids) {
            val isYou = (uid == currentUid)
            val isOwner = (uid == ownerId || uid == creatorId || (safeUids.size == 1 && isYou))
            val isAdmin = isOwner

            val roleStr = if (isOwner) "owner" else "member"

            var name = if (isYou) currentName else ""
            var email = ""
            var phone = ""

            try {
                val userDoc = db.collection("users").document(uid).get().await()
                if (userDoc.exists()) {
                    email = userDoc.getString("email") ?: ""
                    phone = userDoc.getString("phoneNumber") ?: userDoc.getString("phone") ?: ""
                    val docName = userDoc.getString("displayName") ?: userDoc.getString("name") ?: ""
                    if (!isYou || name.isBlank()) {
                        name = docName.ifBlank { email.ifBlank { phone.ifBlank { uid } } }
                    }
                }
            } catch (e: Exception) {
                if (name.isBlank()) name = uid
            }

            memberList.add(
                GroupMember(
                    uid = uid,
                    displayName = name.ifBlank { "User" },
                    email = email,
                    phoneNumber = phone,
                    isYou = isYou,
                    isAdmin = isAdmin,
                    role = roleStr
                )
            )
        }

        // Ensure Owner is first in the list
        val sortedMembers = memberList.sortedByDescending { it.role.equals("owner", ignoreCase = true) }
        _members.value = sortedMembers
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
                    val ts = doc.getTimestamp("createdAt")?.toDate()?.time
                        ?: doc.getLong("timestamp")
                        ?: System.currentTimeMillis()
                    GroupFolderFile(
                        id = doc.id,
                        name = name,
                        downloadUrl = url,
                        sizeBytes = size,
                        uploadPath = path,
                        timestamp = ts
                    )
                }.sortedByDescending { it.timestamp }

                _files.value = fileList
            }
    }

    fun uploadFileToGroupFolder(context: Context, fileUri: Uri, onComplete: () -> Unit = {}) {
        if (currentGroupId.isBlank()) return

        val fileName = FilePickerUtils.getFileName(context, fileUri).ifBlank { "Document" }
        val fileId = UUID.randomUUID().toString()
        val localFile = GroupFolderFile(
            id = fileId,
            name = fileName,
            downloadUrl = fileUri.toString(),
            timestamp = System.currentTimeMillis()
        )

        // Local update for instant UI feedback
        _files.value = (listOf(localFile) + _files.value).distinctBy { it.downloadUrl }

        viewModelScope.launch {
            try {
                ensureAuthenticated()
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
                    .document(fileId)
                    .set(fileDoc, SetOptions.merge())
                    .await()

                Toast.makeText(context, "File uploaded successfully!", Toast.LENGTH_SHORT).show()
                onComplete()
            } catch (e: Exception) {
                e.printStackTrace()
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
                    val ts = doc.getTimestamp("createdAt")?.toDate()?.time
                        ?: doc.getLong("timestamp")
                        ?: System.currentTimeMillis()

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
                        mediaType = type,
                        timestamp = ts
                    )
                }

                _media.value = mediaList
            }
    }
}
