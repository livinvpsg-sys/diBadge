package com.fabxdi.dibadge.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime

@Entity(tableName = "overtime_logs")
data class OvertimeEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val description: String,
    val status: String = "Pending", // "Pending" or "Approved"
    val appliedDate: LocalDate = LocalDate.now(),
    val appliedDateTime: LocalDateTime = LocalDateTime.now(),
    val approvedDate: LocalDate? = null
)
