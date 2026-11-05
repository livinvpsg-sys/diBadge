package com.fabxdi.dibadge.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fabxdi.dibadge.data.AppDatabase
import com.fabxdi.dibadge.data.DocEntity
import com.fabxdi.dibadge.ui.my_docs.DocFileUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

class DocViewModel(application: Application) : AndroidViewModel(application) {
    private val docDao = AppDatabase.getDatabase(application).docDao()
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

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
                doc.files.forEach { DocFileUtils.deleteStored(app, it) }
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

                for (doc in snapshot.documents) {
                    val mapData = doc.data ?: continue
                    val item = mapData.toDocEntity(doc.id)
                    if (item != null) {
                        docDao.insertDocument(item)
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
                                docDao.deleteDocumentsByIds(listOf(change.document.id))
                                continue
                            }
                            val mapData = change.document.data
                            val item = mapData.toDocEntity(change.document.id)
                            if (item != null) {
                                docDao.insertDocument(item)
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
            syncDocToFirestore(finalDoc)
        }
    }

    fun deleteDocument(doc: DocEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            docDao.deleteDocument(doc)
            doc.files.forEach { DocFileUtils.deleteStored(getApplication(), it) }
            val userId = auth.currentUser?.uid ?: return@launch
            try {
                firestore.collection("users")
                    .document(userId)
                    .collection("documents")
                    .document(doc.id)
                    .delete()
            } catch (e: Exception) {}
        }
    }

    fun deleteDocuments(docs: List<DocEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            val ids = docs.map { it.id }
            docDao.deleteDocumentsByIds(ids)
            docs.forEach { d -> d.files.forEach { DocFileUtils.deleteStored(getApplication(), it) } }
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
                .set(doc.toFirestoreMap(), SetOptions.merge())
        } catch (e: Exception) {}
    }
}

fun DocEntity.toFirestoreMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "name" to name,
        "type" to type,
        "number" to number,
        "expiryDate" to expiryDate?.toString(),
        "noExpiry" to noExpiry,
        "files" to files,
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
