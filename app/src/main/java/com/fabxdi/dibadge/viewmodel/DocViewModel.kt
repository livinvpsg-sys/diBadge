package com.fabxdi.dibadge.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fabxdi.dibadge.data.AppDatabase
import com.fabxdi.dibadge.data.DocEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
                docDao.deleteAllDocuments()
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

    private fun setupFirestoreSync(userId: String) {
        removeFirestoreSync()
        currentSyncedUserId = userId

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val snapshot = firestore.collection("users")
                    .document(userId)
                    .collection("documents")
                    .get()
                    .await()

                if (snapshot.documents.isEmpty()) {
                    val sampleDocs = createSampleDocuments()
                    docDao.insertAllDocuments(sampleDocs)
                    for (doc in sampleDocs) {
                        firestore.collection("users")
                            .document(userId)
                            .collection("documents")
                            .document(doc.id)
                            .set(doc.toFirestoreMap(), SetOptions.merge())
                    }
                } else {
                    for (doc in snapshot.documents) {
                        val mapData = doc.data ?: continue
                        val item = mapData.toDocEntity(doc.id)
                        if (item != null) {
                            docDao.insertDocument(item)
                        }
                    }
                }
            } catch (e: Exception) {
                // Offline or firestore error
            }

            firestoreListener = firestore.collection("users")
                .document(userId)
                .collection("documents")
                .addSnapshotListener { snapshot, e ->
                    if (e != null || snapshot == null) return@addSnapshotListener
                    viewModelScope.launch(Dispatchers.IO) {
                        for (doc in snapshot.documents) {
                            val mapData = doc.data ?: continue
                            val item = mapData.toDocEntity(doc.id)
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
            val updatedOrgs = doc.sharedOrganisations + orgName
            val updatedDates = doc.sharedDates + "Shared on $dateStr"

            val updatedDoc = doc.copy(
                sharedOrganisations = updatedOrgs,
                sharedDates = updatedDates,
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

    private fun createSampleDocuments(): List<DocEntity> {
        val today = LocalDate.now()
        val now = System.currentTimeMillis()

        return listOf(
            DocEntity(
                id = "doc_1",
                name = "National Registration ID",
                type = "id",
                number = "S9876543A",
                expiryDate = null,
                noExpiry = true,
                files = listOf("sample_id_front.jpg"),
                fileNames = listOf("NRIC_Front.jpg"),
                fileSizes = listOf(1_200_000L),
                fileIsPdf = listOf(false),
                createdAt = now,
                updatedAt = now
            ),
            DocEntity(
                id = "doc_2",
                name = "Solar Installation Licence",
                type = "licence",
                number = "LIC-2026-098",
                expiryDate = today.plusDays(10),
                noExpiry = false,
                files = listOf("sample_licence.pdf"),
                fileNames = listOf("Solar_Licence.pdf"),
                fileSizes = listOf(240_000L),
                fileIsPdf = listOf(true),
                createdAt = now - 3600_000L * 24,
                updatedAt = now - 3600_000L * 24
            ),
            DocEntity(
                id = "doc_3",
                name = "Workplace Safety Certificate",
                type = "certificate",
                number = "CERT-8841",
                expiryDate = today.plusMonths(12),
                noExpiry = false,
                files = listOf("sample_cert.pdf"),
                fileNames = listOf("Safety_Cert.pdf"),
                fileSizes = listOf(350_000L),
                fileIsPdf = listOf(true),
                createdAt = now - 3600_000L * 48,
                updatedAt = now - 3600_000L * 48
            )
        )
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
        sharedDates = sharedDatesVal
    )
}
