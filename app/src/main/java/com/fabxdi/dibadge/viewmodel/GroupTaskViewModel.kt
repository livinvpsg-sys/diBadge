package com.fabxdi.dibadge.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fabxdi.dibadge.data.AppDatabase
import com.fabxdi.dibadge.data.GroupTaskEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.util.UUID

class GroupTaskViewModel(application: Application) : AndroidViewModel(application) {
    private val groupTaskDao = AppDatabase.getDatabase(application).groupTaskDao()
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    fun getTasksForGroup(groupId: String): Flow<List<GroupTaskEntity>> {
        viewModelScope.launch(Dispatchers.IO) {
            syncGroupTasksFromFirestore(groupId)
        }
        return groupTaskDao.getTasksForGroup(groupId)
    }

    fun assignTasks(
        title: String,
        groupId: String,
        assignees: List<GroupMember>,
        assignerName: String,
        assignerUid: String,
        dueDate: LocalDate?,
        dueTime: String?,
        note: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val tasksToCreate = assignees.map { member ->
                GroupTaskEntity(
                    id = UUID.randomUUID().toString(),
                    groupId = groupId,
                    title = title.trim(),
                    assignedTo = member.name,
                    assignedToUid = member.uid,
                    assignedBy = assignerName,
                    assignedByUid = assignerUid,
                    dueDate = dueDate,
                    dueTime = dueTime,
                    note = note.trim(),
                    isCompleted = false
                )
            }

            groupTaskDao.insertAllGroupTasks(tasksToCreate)

            for (task in tasksToCreate) {
                try {
                    firestore.collection("groups")
                        .document(groupId)
                        .collection("tasks")
                        .document(task.id)
                        .set(task.toFirestoreMap(), SetOptions.merge())
                } catch (e: Exception) {}
            }
        }
    }

    private suspend fun syncGroupTasksFromFirestore(groupId: String) {
        try {
            val snapshot = firestore.collection("groups")
                .document(groupId)
                .collection("tasks")
                .get()
                .await()

            if (snapshot.documents.isEmpty()) {
                val sampleTasks = createSampleGroupTasks(groupId)
                groupTaskDao.insertAllGroupTasks(sampleTasks)
                for (t in sampleTasks) {
                    firestore.collection("groups")
                        .document(groupId)
                        .collection("tasks")
                        .document(t.id)
                        .set(t.toFirestoreMap(), SetOptions.merge())
                }
            } else {
                for (doc in snapshot.documents) {
                    val mapData = doc.data ?: continue
                    val task = mapData.toGroupTaskEntity(doc.id)
                    if (task != null) {
                        groupTaskDao.insertGroupTask(task)
                    }
                }
            }
        } catch (e: Exception) {}
    }

    private fun createSampleGroupTasks(groupId: String): List<GroupTaskEntity> {
        return emptyList()
    }
}

fun GroupTaskEntity.toFirestoreMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "groupId" to groupId,
        "title" to title,
        "assignedTo" to assignedTo,
        "assignedToUid" to assignedToUid,
        "assignedBy" to assignedBy,
        "assignedByUid" to assignedByUid,
        "dueDate" to dueDate?.toString(),
        "dueTime" to dueTime,
        "note" to note,
        "isCompleted" to isCompleted,
        "completedAt" to completedAt,
        "createdAt" to createdAt
    )
}

fun Map<String, Any?>.toGroupTaskEntity(documentId: String): GroupTaskEntity? {
    val idVal = this["id"] as? String ?: documentId
    val groupIdVal = this["groupId"] as? String ?: ""
    val titleVal = this["title"] as? String ?: "Task"
    val assignedToVal = this["assignedTo"] as? String ?: "User"
    val assignedToUidVal = this["assignedToUid"] as? String ?: ""
    val assignedByVal = this["assignedBy"] as? String ?: "Assigner"
    val assignedByUidVal = this["assignedByUid"] as? String ?: ""
    val dueDateVal = (this["dueDate"] as? String)?.let { try { LocalDate.parse(it) } catch (e: Exception) { null } }
    val dueTimeVal = this["dueTime"] as? String
    val noteVal = this["note"] as? String ?: ""
    val isCompletedVal = this["isCompleted"] as? Boolean ?: false
    val completedAtVal = (this["completedAt"] as? Long)
    val createdAtVal = (this["createdAt"] as? Long) ?: System.currentTimeMillis()

    return GroupTaskEntity(
        id = idVal,
        groupId = groupIdVal,
        title = titleVal,
        assignedTo = assignedToVal,
        assignedToUid = assignedToUidVal,
        assignedBy = assignedByVal,
        assignedByUid = assignedByUidVal,
        dueDate = dueDateVal,
        dueTime = dueTimeVal,
        note = noteVal,
        isCompleted = isCompletedVal,
        completedAt = completedAtVal,
        createdAt = createdAtVal
    )
}
