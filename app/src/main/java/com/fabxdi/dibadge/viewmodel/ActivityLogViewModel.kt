package com.fabxdi.dibadge.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fabxdi.dibadge.data.ActivityLogEntity
import com.fabxdi.dibadge.data.ActivityLogRepository
import com.fabxdi.dibadge.data.AppDatabase
import com.fabxdi.dibadge.data.toActivityLog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID

class ActivityLogViewModel(application: Application) : AndroidViewModel(application) {
    private val activityLogDao = AppDatabase.getDatabase(application).activityLogDao()
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private var firestoreListener: ListenerRegistration? = null
    private var currentSyncedUserId: String? = null
    private var uploadedLocalOnly = false

    val allActivityLogs: Flow<List<ActivityLogEntity>> = activityLogDao.getAllActivityLogs()

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (user != null) {
            if (currentSyncedUserId != user.uid) startSync(user.uid)
        } else {
            stopSync()
            viewModelScope.launch(Dispatchers.IO) { activityLogDao.deleteAllActivityLogs() }
        }
    }

    init {
        auth.addAuthStateListener(authStateListener)
        auth.currentUser?.let { startSync(it.uid) }
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authStateListener)
        stopSync()
    }

    /** One live listener: downloads everything on login and keeps this phone updated afterwards. */
    private fun startSync(userId: String) {
        stopSync()
        currentSyncedUserId = userId
        uploadedLocalOnly = false

        firestoreListener = firestore.collection("users").document(userId)
            .collection("activity_logs")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                val cloudDocs = snapshot.documents.mapNotNull { doc ->
                    doc.data?.toActivityLog(doc.id)
                }
                viewModelScope.launch(Dispatchers.IO) {
                    if (cloudDocs.isNotEmpty()) activityLogDao.insertAllActivityLogs(cloudDocs)
                    if (!uploadedLocalOnly && !snapshot.metadata.isFromCache) {
                        uploadedLocalOnly = true
                        ActivityLogRepository.uploadLocalOnly(
                            getApplication(), cloudDocs.map { it.id }.toSet()
                        )
                    }
                }
            }
    }

    private fun stopSync() {
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
            ActivityLogRepository.record(
                getApplication(),
                ActivityLogEntity(
                    id = UUID.randomUUID().toString(),
                    result = result,
                    kind = kind,
                    title = title,
                    source = source,
                    reference = reference,
                    relatedId = relatedId,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }
}