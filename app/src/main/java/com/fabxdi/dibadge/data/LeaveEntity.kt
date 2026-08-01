package com.fabxdi.dibadge.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(tableName = "leave_logs")
data class LeaveEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val startDate: LocalDate,
    val endDate: LocalDate?, // null if "A day"
    val leaveType: String,
    val reason: String,
    val attachments: List<String> = emptyList(),
    val status: String = "Pending", // "Pending" or "Approved"
    val appliedDate: LocalDate = LocalDate.now(),
    val appliedDateTime: LocalDateTime = LocalDateTime.now(),
    val approvedDate: LocalDate? = null
)
