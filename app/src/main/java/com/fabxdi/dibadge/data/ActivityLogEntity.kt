package com.fabxdi.dibadge.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activity_logs")
data class ActivityLogEntity(
    @PrimaryKey val id: String,
    val result: String, // "Signed", "Approved", "Rejected", "Confirmed"
    val kind: String,   // "permit", "toolbox", "message"
    val title: String,  // e.g. "Hot work permit", "Toolbox meeting", "Salary Revision Letter"
    val source: String, // e.g. "diBadge Inc.", "Tuas Roof Works"
    val reference: String, // e.g. "HW-149", "TBM-0412", "Letter"
    val relatedId: String,
    val timestamp: Long = System.currentTimeMillis()
)
