package com.fabxdi.dibadge.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fabxdi.dibadge.data.AppDatabase
import com.fabxdi.dibadge.data.ReminderEntity
import com.fabxdi.dibadge.data.LeaveEntity
import com.fabxdi.dibadge.data.OvertimeEntity
import com.fabxdi.dibadge.util.AlarmScheduler
import com.fabxdi.dibadge.data.HomeEntryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderViewModel(application: Application) : AndroidViewModel(application) {
    private val reminderDao = AppDatabase.Companion.getDatabase(application).reminderDao()
    private val alarmScheduler = AlarmScheduler(application)
    val allReminders: Flow<List<ReminderEntity>> = reminderDao.getAllReminders()
    val allLeaves: Flow<List<LeaveEntity>> = reminderDao.getAllLeaves()
    val allOvertime: Flow<List<OvertimeEntity>> = reminderDao.getAllOvertime()
    val allHomeEntries: Flow<List<HomeEntryEntity>> = reminderDao.getAllHomeEntries()

    val todayRemindersCount: StateFlow<Int> = allReminders.map { reminders ->
        val today = LocalDate.now()
        reminders.count { reminder ->
            try {
                when (reminder.repeatType) {
                    "once" -> reminder.date == today
                    "daily" -> true
                    "weekly" -> reminder.date?.let { it.dayOfWeek == today.dayOfWeek } ?: false
                    "monthly" -> reminder.date?.let { it.dayOfMonth == today.dayOfMonth } ?: false
                    "Yearly" -> reminder.date?.let { it.month == today.month && it.dayOfMonth == today.dayOfMonth } ?: false
                    "Custom" -> reminder.startDate?.let { start ->
                        reminder.endDate?.let { end ->
                            !today.isBefore(start) && !today.isAfter(end)
                        }
                    } ?: false
                    else -> false
                }
            } catch (e: Exception) {
                false
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        viewModelScope.launch {
            reminderDao.deletePastPendingLeaves(LocalDate.now())
        }
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

    fun saveOvertime(
        date: LocalDate,
        startTime: LocalTime,
        endTime: LocalTime,
        description: String,
        id: Int = 0,
        status: String = "Pending",
        appliedDate: LocalDate = LocalDate.now(),
        appliedDateTime: LocalDateTime = LocalDateTime.now(),
        approvedDate: LocalDate? = null
    ) {
        viewModelScope.launch {
            val overtime = OvertimeEntity(
                id = id,
                date = date,
                startTime = startTime,
                endTime = endTime,
                description = description,
                status = status,
                appliedDate = appliedDate,
                appliedDateTime = appliedDateTime,
                approvedDate = approvedDate
            )
            if (id == 0) {
                reminderDao.insertOvertime(overtime)
            } else {
                reminderDao.updateOvertime(overtime)
            }
        }
    }

    fun updateOvertime(overtime: OvertimeEntity) {
        viewModelScope.launch {
            reminderDao.updateOvertime(overtime)
        }
    }

    fun deleteOvertime(overtime: OvertimeEntity) {
        viewModelScope.launch {
            reminderDao.deleteOvertime(overtime)
        }
    }

    fun saveLeave(
        startDate: LocalDate,
        endDate: LocalDate?,
        leaveType: String,
        reason: String,
        attachments: List<String>,
        id: Int = 0,
        status: String = "Pending",
        appliedDate: LocalDate = LocalDate.now(),
        appliedDateTime: LocalDateTime = LocalDateTime.now(),
        approvedDate: LocalDate? = null
    ) {
        viewModelScope.launch {
            val leave = LeaveEntity(
                id = id,
                startDate = startDate,
                endDate = endDate,
                leaveType = leaveType,
                reason = reason,
                attachments = attachments,
                status = status,
                appliedDate = appliedDate,
                appliedDateTime = appliedDateTime,
                approvedDate = approvedDate
            )
            if (id == 0) {
                reminderDao.insertLeave(leave)
            } else {
                reminderDao.updateLeave(leave)
            }
        }
    }

    fun updateLeave(leave: LeaveEntity) {
        viewModelScope.launch {
            reminderDao.updateLeave(leave)
        }
    }

    fun deleteLeave(leave: LeaveEntity) {
        viewModelScope.launch {
            reminderDao.deleteLeave(leave)
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
            val reminder = ReminderEntity(
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
                // Cancel existing alarm and reschedule if enabled
                alarmScheduler.cancel(id)
                if (isAlarmEnabled) {
                    alarmScheduler.schedule(reminder)
                }
            }
        }
    }

    fun deleteReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            reminderDao.deleteReminder(reminder)
            alarmScheduler.cancel(reminder.id)
        }
    }
}
