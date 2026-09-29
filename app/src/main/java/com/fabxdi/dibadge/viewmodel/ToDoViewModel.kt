package com.fabxdi.dibadge.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fabxdi.dibadge.data.AppDatabase
import com.fabxdi.dibadge.data.ToDoEntity
import com.fabxdi.dibadge.util.AlarmScheduler
import com.fabxdi.dibadge.data.HomeEntryEntity
import com.fabxdi.dibadge.ui.my_tasks.isReminderOnDate
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
    val allReminders: Flow<List<ToDoEntity>> = reminderDao.getAllReminders()
    val allHomeEntries: Flow<List<HomeEntryEntity>> = reminderDao.getAllHomeEntries()

    val todayRemindersCount: StateFlow<Int> = allReminders.map { reminders ->
        val today = LocalDate.now()
        val todayStr = today.toString()
        reminders.count { reminder ->
            !reminder.completedDates.contains(todayStr) && isReminderOnDate(reminder, today)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

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
        viewModelScope.launch {
            val reminder = ToDoEntity(
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
            if (id == 0) {
                val newId = reminderDao.insertReminder(reminder).toInt()
                if (isAlarmEnabled) {
                    alarmScheduler.schedule(reminder.copy(id = newId))
                }
            } else {
                reminderDao.updateReminder(reminder)
                alarmScheduler.cancel(id)
                if (isAlarmEnabled) {
                    alarmScheduler.schedule(reminder)
                }
            }
        }
    }

    fun deleteReminder(reminder: ToDoEntity) {
        viewModelScope.launch {
            reminderDao.deleteReminder(reminder)
            alarmScheduler.cancel(reminder.id)
        }
    }

    fun toggleTaskCompletedForDate(reminder: ToDoEntity, targetDate: LocalDate) {
        viewModelScope.launch {
            val dateStr = targetDate.toString()
            val newCompletedDates = if (reminder.completedDates.contains(dateStr)) {
                reminder.completedDates - dateStr
            } else {
                reminder.completedDates + dateStr
            }
            val updated = reminder.copy(completedDates = newCompletedDates)
            reminderDao.updateReminder(updated)
        }
    }
}

typealias ReminderViewModel = ToDoViewModel
