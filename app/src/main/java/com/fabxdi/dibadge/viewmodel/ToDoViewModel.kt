package com.fabxdi.dibadge.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fabxdi.dibadge.data.AppDatabase
import com.fabxdi.dibadge.data.HomeEntryEntity
import com.fabxdi.dibadge.data.ToDoEntity
import com.fabxdi.dibadge.ui.my_tasks.isReminderOnDate
import com.fabxdi.dibadge.util.AlarmScheduler
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class ToDoViewModel(application: Application) : AndroidViewModel(application) {
    private val reminderDao = AppDatabase.getDatabase(application).reminderDao()
    private val alarmScheduler = AlarmScheduler(application)
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private var firestoreListener: ListenerRegistration? = null

    val allReminders: Flow<List<ToDoEntity>> = reminderDao.getAllReminders()
    val allHomeEntries: Flow<List<HomeEntryEntity>> = reminderDao.getAllHomeEntries()

    val todayRemindersCount: StateFlow<Int> = allReminders.map { reminders ->
        val today = LocalDate.now()
        val todayStr = today.toString()
        reminders.count { reminder ->
            !reminder.completedDates.contains(todayStr) && isReminderOnDate(reminder, today)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (user != null) {
            setupFirestoreSync(user.uid)
        } else {
            removeFirestoreSync()
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
        firestoreListener = firestore.collection("users")
            .document(userId)
            .collection("tasks")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                viewModelScope.launch(Dispatchers.IO) {
                    for (doc in snapshot.documents) {
                        val mapData = doc.data
                        if (mapData != null) {
                            val entity = mapData.toToDoEntity(doc.id)
                            if (entity != null) {
                                reminderDao.insertReminder(entity)
                            }
                        }
                    }
                }
            }
    }

    private fun removeFirestoreSync() {
        firestoreListener?.remove()
        firestoreListener = null
    }

    fun saveHomeEntry(title: String, subtitle: String, initials: String) {
        viewModelScope.launch {
            reminderDao.insertHomeEntry(
                HomeEntryEntity(
                    title = title,
                    subtitle = subtitle,
                    initials = initials
                )
            )
        }
    }

    fun deleteHomeEntry(entry: HomeEntryEntity) {
        viewModelScope.launch {
            reminderDao.deleteHomeEntry(entry)
        }
    }

    fun saveReminder(
        title: String,
        content: String,
        date: LocalDate?,
        startDate: LocalDate?,
        endDate: LocalDate?,
        repeatType: String,
        time: String?,
        isAlarmEnabled: Boolean,
        attachments: List<String> = emptyList(),
        id: Int = 0
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            var reminder = ToDoEntity(
                id = id,
                title = title,
                content = content,
                date = date,
                startDate = startDate,
                endDate = endDate,
                repeatType = repeatType,
                time = time,
                isAlarmEnabled = isAlarmEnabled,
                attachments = attachments
            )

            val finalId = if (id == 0) {
                val newId = reminderDao.insertReminder(reminder).toInt()
                reminder = reminder.copy(id = newId)
                if (isAlarmEnabled) {
                    alarmScheduler.schedule(reminder)
                }
                newId
            } else {
                reminderDao.updateReminder(reminder)
                alarmScheduler.cancel(id)
                if (isAlarmEnabled) {
                    alarmScheduler.schedule(reminder)
                }
                id
            }

            // Sync to Firestore under current user's tasks
            val userId = auth.currentUser?.uid
            if (userId != null) {
                try {
                    firestore.collection("users")
                        .document(userId)
                        .collection("tasks")
                        .document(finalId.toString())
                        .set(reminder.toFirestoreMap(), SetOptions.merge())
                } catch (e: Exception) {
                    // Offline or firestore error - local Room DB persists changes
                }
            }
        }
    }

    fun deleteReminder(reminder: ToDoEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            reminderDao.deleteReminder(reminder)
            alarmScheduler.cancel(reminder.id)

            val userId = auth.currentUser?.uid
            if (userId != null) {
                try {
                    firestore.collection("users")
                        .document(userId)
                        .collection("tasks")
                        .document(reminder.id.toString())
                        .delete()
                } catch (e: Exception) {}
            }
        }
    }

    fun toggleTaskCompletedForDate(reminder: ToDoEntity, targetDate: LocalDate) {
        viewModelScope.launch(Dispatchers.IO) {
            val dateStr = targetDate.toString()
            val newCompletedDates = if (reminder.completedDates.contains(dateStr)) {
                reminder.completedDates - dateStr
            } else {
                reminder.completedDates + dateStr
            }
            val updated = reminder.copy(completedDates = newCompletedDates)
            reminderDao.updateReminder(updated)

            val userId = auth.currentUser?.uid
            if (userId != null) {
                try {
                    firestore.collection("users")
                        .document(userId)
                        .collection("tasks")
                        .document(updated.id.toString())
                        .set(updated.toFirestoreMap(), SetOptions.merge())
                } catch (e: Exception) {}
            }
        }
    }
}

fun ToDoEntity.toFirestoreMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "title" to title,
        "content" to content,
        "date" to date?.toString(),
        "startDate" to startDate?.toString(),
        "endDate" to endDate?.toString(),
        "repeatType" to repeatType,
        "time" to time,
        "isAlarmEnabled" to isAlarmEnabled,
        "attachments" to attachments,
        "completedDates" to completedDates,
        "updatedAt" to System.currentTimeMillis()
    )
}

fun Map<String, Any?>.toToDoEntity(documentId: String): ToDoEntity? {
    val idVal = (this["id"] as? Long)?.toInt() ?: documentId.toIntOrNull() ?: 0
    val titleVal = this["title"] as? String ?: ""
    val contentVal = this["content"] as? String ?: ""
    val dateVal = (this["date"] as? String)?.let { try { LocalDate.parse(it) } catch (e: Exception) { null } }
    val startDateVal = (this["startDate"] as? String)?.let { try { LocalDate.parse(it) } catch (e: Exception) { null } }
    val endDateVal = (this["endDate"] as? String)?.let { try { LocalDate.parse(it) } catch (e: Exception) { null } }
    val repeatTypeVal = this["repeatType"] as? String ?: "once"
    val timeVal = this["time"] as? String
    val isAlarmEnabledVal = this["isAlarmEnabled"] as? Boolean ?: false
    val attachmentsVal = (this["attachments"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
    val completedDatesVal = (this["completedDates"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

    return ToDoEntity(
        id = idVal,
        title = titleVal,
        content = contentVal,
        date = dateVal,
        startDate = startDateVal,
        endDate = endDateVal,
        repeatType = repeatTypeVal,
        time = timeVal,
        isAlarmEnabled = isAlarmEnabledVal,
        attachments = attachmentsVal,
        completedDates = completedDatesVal
    )
}

typealias ReminderViewModel = ToDoViewModel
