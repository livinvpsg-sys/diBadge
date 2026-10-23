package com.fabxdi.dibadge.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fabxdi.dibadge.data.ActivityLogEntity
import com.fabxdi.dibadge.data.AppDatabase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ActivityLogViewModel(application: Application) : AndroidViewModel(application) {
    private val activityLogDao = AppDatabase.getDatabase(application).activityLogDao()
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private var firestoreListener: ListenerRegistration? = null
    private var currentSyncedUserId: String? = null

    val allActivityLogs: Flow<List<ActivityLogEntity>> = activityLogDao.getAllActivityLogs()

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (user != null) {
            if (currentSyncedUserId != user.uid) {
                setupFirestoreSync(user.uid)
            }
        } else {
            removeFirestoreSync()
            viewModelScope.launch(Dispatchers.IO) {
                activityLogDao.deleteAllActivityLogs()
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
                    .collection("activity_logs")
                    .get()
                    .await()

                if (snapshot.documents.isEmpty()) {
                    val sampleLogs = createSampleActivityLogs()
                    activityLogDao.insertAllActivityLogs(sampleLogs)
                    for (log in sampleLogs) {
                        firestore.collection("users")
                            .document(userId)
                            .collection("activity_logs")
                            .document(log.id)
                            .set(log.toFirestoreMap(), SetOptions.merge())
                    }
                } else {
                    for (doc in snapshot.documents) {
                        val mapData = doc.data ?: continue
                        val item = mapData.toActivityLogEntity(doc.id)
                        if (item != null) {
                            activityLogDao.insertActivityLog(item)
                        }
                    }
                }
            } catch (e: Exception) {
                // Offline or firestore error
            }

            firestoreListener = firestore.collection("users")
                .document(userId)
                .collection("activity_logs")
                .addSnapshotListener { snapshot, e ->
                    if (e != null || snapshot == null) return@addSnapshotListener
                    viewModelScope.launch(Dispatchers.IO) {
                        for (doc in snapshot.documents) {
                            val mapData = doc.data ?: continue
                            val item = mapData.toActivityLogEntity(doc.id)
                            if (item != null) {
                                activityLogDao.insertActivityLog(item)
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

    fun logActivity(
        result: String,
        kind: String,
        title: String,
        source: String,
        reference: String,
        relatedId: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val log = ActivityLogEntity(
                id = UUID.randomUUID().toString(),
                result = result,
                kind = kind,
                title = title,
                source = source,
                reference = reference,
                relatedId = relatedId,
                timestamp = System.currentTimeMillis()
            )
            activityLogDao.insertActivityLog(log)

            val userId = auth.currentUser?.uid
            if (userId != null) {
                try {
                    firestore.collection("users")
                        .document(userId)
                        .collection("activity_logs")
                        .document(log.id)
                        .set(log.toFirestoreMap(), SetOptions.merge())
                } catch (e: Exception) {}
            }
        }
    }

    private fun createSampleActivityLogs(): List<ActivityLogEntity> {
        val now = System.currentTimeMillis()
        val hourAgo = now - 3600_000L * 2
        val yesterday = now - 3600_000L * 24

        return listOf(
            ActivityLogEntity(
                id = "act_1",
                result = "Approved",
                kind = "permit",
                title = "Hot work permit",
                source = "Tuas Roof Works",
                reference = "HW-149",
                relatedId = "inbox_1",
                timestamp = now
            ),
            ActivityLogEntity(
                id = "act_2",
                result = "Confirmed",
                kind = "message",
                title = "Salary Revision Letter",
                source = "diBadge Organisation",
                reference = "Letter",
                relatedId = "inbox_2",
                timestamp = hourAgo
            ),
            ActivityLogEntity(
                id = "act_3",
                result = "Signed",
                kind = "toolbox",
                title = "Toolbox meeting",
                source = "Safety Committee",
                reference = "TBM-0412",
                relatedId = "inbox_4",
                timestamp = yesterday
            )
        )
    }
}

fun ActivityLogEntity.toFirestoreMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "result" to result,
        "kind" to kind,
        "title" to title,
        "source" to source,
        "reference" to reference,
        "relatedId" to relatedId,
        "timestamp" to timestamp
    )
}

fun Map<String, Any?>.toActivityLogEntity(documentId: String): ActivityLogEntity? {
    val idVal = this["id"] as? String ?: documentId
    val resultVal = this["result"] as? String ?: "Confirmed"
    val kindVal = this["kind"] as? String ?: "message"
    val titleVal = this["title"] as? String ?: "Activity"
    val sourceVal = this["source"] as? String ?: "Organisation"
    val referenceVal = this["reference"] as? String ?: "Letter"
    val relatedIdVal = this["relatedId"] as? String ?: ""
    val timestampVal = (this["timestamp"] as? Long) ?: System.currentTimeMillis()

    return ActivityLogEntity(
        id = idVal,
        result = resultVal,
        kind = kindVal,
        title = titleVal,
        source = sourceVal,
        reference = referenceVal,
        relatedId = relatedIdVal,
        timestamp = timestampVal
    )
}
