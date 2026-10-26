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
    val dueTime: String? = null,
    val note: String = "",
    val isCompleted: Boolean = false,
    val completedBy: String? = null,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null
) {
    val notes: String get() = note
}
