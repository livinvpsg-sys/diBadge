package com.fabxdi.dibadge.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "reminders")
data class ToDoEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val date: LocalDate?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val repeatType: String,
    val time: String?,
    val isAlarmEnabled: Boolean,
    val attachments: List<String> = emptyList(),
    val completedDates: List<String> = emptyList()
)

typealias ReminderEntity = ToDoEntity
