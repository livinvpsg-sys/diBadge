package com.fabxdi.dibadge.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "claim_logs")
data class ClaimEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: LocalDate,
    val category: String = "Transport",
    val amount: Double = 0.0,
    val description: String = "",
    val status: String = "Pending", // "Pending" or "Approved"
    val receiptUrl: String? = null
)
