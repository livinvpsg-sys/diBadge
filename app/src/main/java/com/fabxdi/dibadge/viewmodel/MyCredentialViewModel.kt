package com.fabxdi.dibadge.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fabxdi.dibadge.data.AppDatabase
import com.fabxdi.dibadge.data.DocEntity
import com.fabxdi.dibadge.ui.my_credentials.MyCredentialFileUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/** In Firestore a credential's files are stored as "storage:users/{uid}/credentials/{docId}/{fileName}". */
private const val REMOTE_PREFIX = "storage:"

private fun remotePathFor(userId: String, docId: String, fileName: String) = "users/$userId/credentials/$docId/$fileName"

class MyCredentialViewModel(application: Application) : AndroidViewModel(application) {
    private val docDao = AppDatabase.getDatabase(application).docDao()
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    // Remembers which files are already in the cloud, so they are not checked again on every start
    private val uploadPrefs = application.getSharedPreferences("credential_uploads", Context.MODE_PRIVATE)

    private var firestoreListener: ListenerRegistration? = null
    private var currentSyncedUserId: String? = null

    // Which account the documents in the local database belong to
    private val sessionPrefs = application.getSharedPreferences("dibadge_session", Context.MODE_PRIVATE)

    val allDocuments: Flow<List<DocEntity>> = docDao.getAllDocuments()

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (user != null) {
            if (currentSyncedUserId != user.uid) {
                setupFirestoreSync(user.uid)
            }
        } else {
            removeFirestoreSync()
            viewModelScope.launch(Dispatchers.IO) {
                clearLocalDocuments()
                sessionPrefs.edit().remove("docs_owner").apply()
            }
        }
    }

    init {
        auth.addAuthStateListener(authStateListener)
        auth.currentUser?.let { setupFirestoreSync(it.uid) }
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authStateListener)
        removeFirestoreSync()
    }

    /** Removes every local document and its stored files. */
    private suspend fun clearLocalDocuments() {
        try {
            val app = getApplication<Application>()
            docDao.getAllDocuments().first().forEach { doc ->
                doc.files.forEach { MyCredentialFileUtils.deleteStored(app, it) }
            }
            docDao.deleteAllDocuments()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupFirestoreSync(userId: String) {
        removeFirestoreSync()
        currentSyncedUserId = userId

        viewModelScope.launch(Dispatchers.IO) {
            // The local documents belong to whoever used the app last. Another account must not see them.
            val owner = sessionPrefs.getString("docs_owner", null)
            if (owner != null && owner != userId) {
                clearLocalDocuments()
            }
            sessionPrefs.edit().putString("docs_owner", userId).apply()

            try {
                val snapshot = firestore.collection("users")
                    .document(userId)
                    .collection("documents")
                    .get()
                    .await()

                // Documents saved before the cloud copy existed still list this phone's own file paths
                val legacyIds = mutableSetOf<String>()
                for (doc in snapshot.documents) {
                    val mapData = doc.data ?: continue
                    val item = mapData.toDocEntity(doc.id) ?: continue
                    if (item.files.any { !it.startsWith(REMOTE_PREFIX) }) legacyIds.add(item.id)
                    docDao.insertDocument(resolveFiles(userId, item))
                }

                // Make sure every file on this phone is in the cloud, and move old documents over
                withContext(NonCancellable) {
                    docDao.getAllDocuments().first().forEach { local ->
                        ensureUploaded(userId, local)
                        if (local.id in legacyIds) syncDocToFirestore(local)
                    }
                }
            } catch (e: Exception) {
                // Offline or firestore error
            }

            // Signed out or switched account while loading: do not start a listener for the old one
            if (currentSyncedUserId != userId) return@launch

            firestoreListener = firestore.collection("users")
                .document(userId)
                .collection("documents")
                .addSnapshotListener { snapshot, e ->
                    if (e != null || snapshot == null) return@addSnapshotListener
                    viewModelScope.launch(Dispatchers.IO) {
                        if (currentSyncedUserId != userId) return@launch
                        for (change in snapshot.documentChanges) {
                            if (change.type == DocumentChange.Type.REMOVED) {
                                // Deleted on another device: remove this phone's copy of the files too
                                try {
                                    docDao.getAllDocuments().first().firstOrNull { it.id == change.document.id }
                                        ?.files?.forEach { MyCredentialFileUtils.deleteStored(getApplication(), it) }
                                } catch (e: Exception) {
                                }
                                docDao.deleteDocumentsByIds(listOf(change.document.id))
                                continue
                            }
                            val mapData = change.document.data
                            val item = mapData.toDocEntity(change.document.id)
                            if (item != null) {
                                docDao.insertDocument(resolveFiles(userId, item))
                            }
                        }
                    }
                }
        }
    }

    private fun removeFirestoreSync() {
        firestoreListener?.remove()
        firestoreListener = null
        currentSyncedUserId = null
    }

    fun saveDocument(doc: DocEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val finalDoc = if (doc.id.isBlank()) doc.copy(id = UUID.randomUUID().toString()) else doc
            docDao.insertDocument(finalDoc)
            // The record first, so other devices see it at once; then the files. A file that could not be
            // uploaded now (offline) is uploaded the next time the app starts.
            syncDocToFirestore(finalDoc)
            val userId = auth.currentUser?.uid ?: return@launch
            withContext(NonCancellable) {
                ensureUploaded(userId, finalDoc)
                removeUnusedRemoteFiles(userId, finalDoc)
            }
        }
    }

    fun deleteDocument(doc: DocEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            docDao.deleteDocument(doc)
            doc.files.forEach { MyCredentialFileUtils.deleteStored(getApplication(), it) }
            val userId = auth.currentUser?.uid ?: return@launch
            try {
                firestore.collection("users")
                    .document(userId)
                    .collection("documents")
                    .document(doc.id)
                    .delete()
            } catch (e: Exception) {}
            withContext(NonCancellable) { deleteRemoteFiles(userId, doc.id) }
        }
    }

    fun deleteDocuments(docs: List<DocEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            val ids = docs.map { it.id }
            docDao.deleteDocumentsByIds(ids)
            docs.forEach { d -> d.files.forEach { MyCredentialFileUtils.deleteStored(getApplication(), it) } }
            val userId = auth.currentUser?.uid ?: return@launch
            for (id in ids) {
                try {
                    firestore.collection("users")
                        .document(userId)
                        .collection("documents")
                        .document(id)
                        .delete()
                } catch (e: Exception) {}
            }
            withContext(NonCancellable) { ids.forEach { deleteRemoteFiles(userId, it) } }
        }
    }

    fun shareDocumentWithOrg(doc: DocEntity, orgName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
            val label = "Shared on $dateStr"

            // Sharing again with the same organisation updates its date instead of adding a second entry
            val orgs = doc.sharedOrganisations.toMutableList()
            val dates = doc.sharedDates.toMutableList()
            val existing = orgs.indexOf(orgName)
            if (existing >= 0) {
                while (dates.size <= existing) dates.add("Shared")
                dates[existing] = label
            } else {
                orgs.add(orgName)
                while (dates.size < orgs.size - 1) dates.add("Shared")
                dates.add(label)
            }

            val updatedDoc = doc.copy(
                sharedOrganisations = orgs,
                sharedDates = dates,
                updatedAt = System.currentTimeMillis()
            )

            docDao.updateDocument(updatedDoc)
            syncDocToFirestore(updatedDoc)
        }
    }

    private fun syncDocToFirestore(doc: DocEntity) {
        val userId = auth.currentUser?.uid ?: return
        try {
            firestore.collection("users")
                .document(userId)
                .collection("documents")
                .document(doc.id)
                .set(doc.toFirestoreMap(userId), SetOptions.merge())
        } catch (e: Exception) {}
    }

    // ---------------------------------------------------------------- files in the cloud (Firebase Storage)

    private fun isUploaded(path: String): Boolean =
        uploadPrefs.getStringSet("paths", emptySet())?.contains(path) == true

    private fun setUploaded(path: String, uploaded: Boolean) {
        val set = (uploadPrefs.getStringSet("paths", emptySet()) ?: emptySet()).toMutableSet()
        if (if (uploaded) set.add(path) else set.remove(path)) uploadPrefs.edit().putStringSet("paths", set).apply()
    }

    /** The file name of a copy kept in this app's storage, or null for anything else. */
    private fun localFileName(entry: String): String? {
        val u = Uri.parse(entry)
        if (u.scheme != "file") return null
        return File(u.path ?: return null).name.takeIf { it.isNotBlank() }
    }

    /** Uploads every file of the document that is on this phone but not yet in the cloud. */
    private suspend fun ensureUploaded(userId: String, doc: DocEntity) {
        for (entry in doc.files) {
            val name = localFileName(entry) ?: continue
            val path = remotePathFor(userId, doc.id, name)
            if (isUploaded(path)) continue
            val ref = storage.reference.child(path)
            val alreadyThere = try {
                ref.metadata.await()
                true
            } catch (e: com.google.firebase.storage.StorageException) {
                if (e.errorCode == com.google.firebase.storage.StorageException.ERROR_OBJECT_NOT_FOUND) false else continue
            } catch (e: Exception) {
                continue // offline: tried again next time
            }
            if (!alreadyThere) {
                val file = File(Uri.parse(entry).path ?: continue)
                if (!file.exists()) continue
                try {
                    ref.putFile(Uri.fromFile(file)).await()
                } catch (e: Exception) {
                    continue
                }
            }
            setUploaded(path, true)
        }
    }

    /** Turns "storage:" entries into files on this phone, downloading the ones it does not have yet. */
    private suspend fun resolveFiles(userId: String, doc: DocEntity): DocEntity {
        val dir = File(getApplication<Application>().filesDir, "documents").apply { mkdirs() }
        val resolved = doc.files.map { entry ->
            if (!entry.startsWith(REMOTE_PREFIX)) return@map entry
            val path = entry.removePrefix(REMOTE_PREFIX)
            if (!path.startsWith("users/$userId/credentials/")) return@map entry
            val dest = File(dir, File(path).name)
            if (!dest.exists()) {
                try {
                    storage.reference.child(path).getFile(dest).await()
                } catch (e: Exception) {
                    dest.delete() // offline: downloaded the next time the app starts
                }
            }
            if (dest.exists()) setUploaded(path, true)
            Uri.fromFile(dest).toString()
        }
        return doc.copy(files = resolved)
    }

    /** After an edit: removes cloud files the document no longer has. */
    private suspend fun removeUnusedRemoteFiles(userId: String, doc: DocEntity) {
        val keep = doc.files.mapNotNull { localFileName(it) }.toSet()
        try {
            storage.reference.child("users/$userId/credentials/${doc.id}").listAll().await().items.forEach { item ->
                if (item.name !in keep) {
                    try {
                        item.delete().await()
                        setUploaded(item.path.trimStart('/'), false)
                    } catch (e: Exception) {
                    }
                }
            }
        } catch (e: Exception) {
        }
    }

    /** Removes every cloud file of a deleted document. */
    private suspend fun deleteRemoteFiles(userId: String, docId: String) {
        try {
            storage.reference.child("users/$userId/credentials/$docId").listAll().await().items.forEach { item ->
                try {
                    item.delete().await()
                    setUploaded(item.path.trimStart('/'), false)
                } catch (e: Exception) {
                }
            }
        } catch (e: Exception) {
        }
    }
}

fun DocEntity.toFirestoreMap(userId: String? = null): Map<String, Any?> {
    // With a user id, files kept on this phone are written as references to their cloud copy
    val storedFiles = if (userId == null) files else files.map { f ->
        val u = Uri.parse(f)
        val name = if (u.scheme == "file") File(u.path ?: "").name else ""
        if (name.isNotBlank()) REMOTE_PREFIX + remotePathFor(userId, id, name) else f
    }
    return mapOf(
        "id" to id,
        "name" to name,
        "type" to type,
        "number" to number,
        "expiryDate" to expiryDate?.toString(),
        "noExpiry" to noExpiry,
        "files" to storedFiles,
        "fileNames" to fileNames,
        "fileSizes" to fileSizes,
        "fileIsPdf" to fileIsPdf,
        "sharedOrganisations" to sharedOrganisations,
        "sharedDates" to sharedDates,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )
}

fun Map<String, Any?>.toDocEntity(documentId: String): DocEntity? {
    val idVal = this["id"] as? String ?: documentId
    val nameVal = this["name"] as? String ?: "Document"
    val typeVal = this["type"] as? String ?: "other"
    val numberVal = this["number"] as? String ?: ""
    val expiryDateVal = (this["expiryDate"] as? String)?.let { try { LocalDate.parse(it) } catch (e: Exception) { null } }
    val noExpiryVal = this["noExpiry"] as? Boolean ?: false
    val filesVal = (this["files"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
    val fileNamesVal = (this["fileNames"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
    val fileSizesVal = (this["fileSizes"] as? List<*>)?.mapNotNull { (it as? Long) ?: (it as? Number)?.toLong() } ?: emptyList()
    val fileIsPdfVal = (this["fileIsPdf"] as? List<*>)?.mapNotNull { it as? Boolean } ?: emptyList()
    val sharedOrgsVal = (this["sharedOrganisations"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
    val sharedDatesVal = (this["sharedDates"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

    return DocEntity(
        id = idVal,
        name = nameVal,
        type = typeVal,
        number = numberVal,
        expiryDate = expiryDateVal,
        noExpiry = noExpiryVal,
        files = filesVal,
        fileNames = fileNamesVal,
        fileSizes = fileSizesVal,
        fileIsPdf = fileIsPdfVal,
        sharedOrganisations = sharedOrgsVal,
        sharedDates = sharedDatesVal,
        createdAt = (this["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        updatedAt = (this["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
    )
}