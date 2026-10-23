package com.fabxdi.dibadge.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "user_documents")
data class DocEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String, // "id", "certificate", "licence", "other"
    val number: String = "",
    val expiryDate: LocalDate? = null,
    val noExpiry: Boolean = false,
    val files: List<String> = emptyList(), // Store URI or file paths
    val fileNames: List<String> = emptyList(),
    val fileSizes: List<Long> = emptyList(),
    val fileIsPdf: List<Boolean> = emptyList(),
    val sharedOrganisations: List<String> = emptyList(),
    val sharedDates: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
