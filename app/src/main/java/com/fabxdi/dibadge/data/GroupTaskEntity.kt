package com.fabxdi.dibadge.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "group_tasks")
data class GroupTaskEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val title: String,
    val assignedTo: String,
    val assignedToUid: String,
    val assignedBy: String,
    val assignedByUid: String,
    val dueDate: LocalDate? = null,
    val dueTime: String? = null, // e.g. "5:00 PM" or "17:00"
    val note: String = "",
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
