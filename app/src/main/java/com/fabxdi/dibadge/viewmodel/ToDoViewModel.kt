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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

class ToDoViewModel(application: Application) : AndroidViewModel(application) {
    private val reminderDao = AppDatabase.getDatabase(application).reminderDao()
    private val alarmScheduler = AlarmScheduler(application)
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private var firestoreListener: ListenerRegistration? = null
    private var currentSyncedUserId: String? = null

    val allReminders: Flow<List<ToDoEntity>> = reminderDao.getAllReminders()
    val allHomeEntries: StateFlow<List<HomeEntryEntity>> = reminderDao.getAllHomeEntries()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val currentDateFlow = flow {
        while (true) {
            emit(LocalDate.now())
            delay(30_000L) // Refresh every 30 seconds for midnight 12:00 AM rollover
        }
    }

    val todayRemindersCount: StateFlow<Int> = combine(allReminders, currentDateFlow) { reminders, today ->
        val todayStr = today.toString()
        reminders.count { reminder ->
            val isDoneToday = reminder.completedDates.contains(todayStr)
            if (isDoneToday) false
            else isTaskActiveForToday(reminder, today)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val hasOverdueOrTodayTasks: StateFlow<Boolean> = combine(allReminders, currentDateFlow) { reminders, today ->
        val todayStr = today.toString()
        reminders.any { reminder ->
            val isDoneToday = reminder.completedDates.contains(todayStr)
            if (isDoneToday) false
            else isTaskActiveForTodayOrOverdue(reminder, today)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (user != null) {
            if (currentSyncedUserId != user.uid) {
                setupFirestoreSync(user.uid)
            }
        } else {
            removeFirestoreSync()
            viewModelScope.launch(Dispatchers.IO) {
                reminderDao.deleteAllReminders()
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
                val querySnapshot = firestore.collection("users")
                    .document(userId)
                    .collection("tasks")
                    .get()
                    .await()

                for (doc in querySnapshot.documents) {
                    val mapData = doc.data ?: continue
                    val entity = mapData.toToDoEntity(doc.id)
                    if (entity != null) {
                        reminderDao.insertReminder(entity)
                        if (!entity.time.isNullOrBlank() && entity.isAlarmEnabled) {
                            alarmScheduler.schedule(entity)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore offline error - local Room DB persists cached data
            }

            firestoreListener = firestore.collection("users")
                .document(userId)
                .collection("tasks")
                .addSnapshotListener { snapshot, e ->
                    if (e != null || snapshot == null) return@addSnapshotListener
                    viewModelScope.launch(Dispatchers.IO) {
                        for (doc in snapshot.documents) {
                            val mapData = doc.data ?: continue
                            val entity = mapData.toToDoEntity(doc.id)
                            if (entity != null) {
                                reminderDao.insertReminder(entity)
                                if (!entity.time.isNullOrBlank() && entity.isAlarmEnabled) {
                                    alarmScheduler.schedule(entity)
                                }
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

    fun saveHomeEntry(title: String, subtitle: String, initials: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val generatedId = (System.currentTimeMillis().toInt() and 0x7FFFFFFF) + (1..999).random()
            val finalSubtitle = subtitle.trim()

            val entry = HomeEntryEntity(
                id = generatedId,
                title = title,
                subtitle = finalSubtitle,
                initials = initials
            )
            reminderDao.insertHomeEntry(entry)

            // Write creator as owner member in Firestore personalGroups/{groupId}
            val currentUid = auth.currentUser?.uid ?: "me"
            try {
                val groupData = mapOf(
                    "id" to generatedId.toString(),
                    "name" to title,
                    "title" to title,
                    "subtitle" to finalSubtitle,
                    "creatorId" to currentUid,
                    "createdBy" to currentUid,
                    "ownerId" to currentUid,
                    "members" to listOf(currentUid),
                    "roles" to mapOf(currentUid to "owner"),
                    "createdAt" to FieldValue.serverTimestamp()
                )
                firestore.collection("personalGroups")
                    .document(generatedId.toString())
                    .set(groupData, SetOptions.merge())
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
        id: Int = 0,
        remindDate: LocalDate? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val taskPrimaryId = if (id == 0) {
                (System.currentTimeMillis().toInt() and 0x7FFFFFFF) + (1..999).random()
            } else id

            var reminder = ToDoEntity(
                id = taskPrimaryId,
                title = title,
                content = content,
                date = date,
                startDate = startDate,
                endDate = endDate,
                repeatType = repeatType,
                time = time,
                isAlarmEnabled = isAlarmEnabled,
                attachments = attachments,
                remindDate = remindDate
            )

            val finalId = if (id == 0) {
                val newId = reminderDao.insertReminder(reminder).toInt()
                reminder = reminder.copy(id = newId)
                if (!reminder.time.isNullOrBlank()) {
                    alarmScheduler.schedule(reminder)
                }
                newId
            } else {
                reminderDao.updateReminder(reminder)
                alarmScheduler.cancel(id)
                if (!reminder.time.isNullOrBlank()) {
                    alarmScheduler.schedule(reminder)
                }
                id
            }

            val userId = auth.currentUser?.uid
            if (userId != null) {
                try {
                    firestore.collection("users")
                        .document(userId)
                        .collection("tasks")
                        .document(finalId.toString())
                        .set(reminder.toFirestoreMap(), SetOptions.merge())
                } catch (e: Exception) {}
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

    fun toggleTaskImportant(reminder: ToDoEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = reminder.copy(isImportant = !reminder.isImportant)
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

fun isTaskActiveForToday(reminder: ToDoEntity, today: LocalDate): Boolean {
    if (reminder.date != null && reminder.date.isEqual(today)) return true
    if (reminder.remindDate != null && reminder.remindDate.isEqual(today)) return true
    if (reminder.repeatType.lowercase() != "once" && reminder.repeatType.isNotBlank()) {
        return isReminderOnDate(reminder, today)
    }
    if (reminder.date == null) {
        val start = reminder.startDate ?: LocalDate.now()
        return !start.isAfter(today)
    }
    return false
}

fun isTaskActiveForTodayOrOverdue(reminder: ToDoEntity, today: LocalDate): Boolean {
    if (reminder.date != null && reminder.date.isBefore(today)) return true
    return isTaskActiveForToday(reminder, today)
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
        "isImportant" to isImportant,
        "remindDate" to remindDate?.toString(),
        "updatedAt" to System.currentTimeMillis()
    )
}

fun Map<String, Any?>.toToDoEntity(documentId: String): ToDoEntity? {
    val rawId = (this["id"] as? Long)?.toInt() ?: documentId.toIntOrNull() ?: documentId.hashCode().let { if (it < 0) -it else it }
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
    val isImportantVal = this["isImportant"] as? Boolean ?: false
    val remindDateVal = (this["remindDate"] as? String)?.let { try { LocalDate.parse(it) } catch (e: Exception) { null } }

    return ToDoEntity(
        id = if (rawId == 0) (documentId.hashCode().let { if (it < 0) -it else it }) else rawId,
        title = titleVal,
        content = contentVal,
        date = dateVal,
        startDate = startDateVal,
        endDate = endDateVal,
        repeatType = repeatTypeVal,
        time = timeVal,
        isAlarmEnabled = isAlarmEnabledVal,
        attachments = attachmentsVal,
        completedDates = completedDatesVal,
        isImportant = isImportantVal,
        remindDate = remindDateVal
    )
}

typealias ReminderViewModel = ToDoViewModel
