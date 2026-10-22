package com.fabxdi.dibadge.data

import androidx.room.Entity
import androidx.room.PrimaryKey

data class InboxSigner(
    val name: String,
    val role: String = "",
    val hasAnswered: Boolean = false,
    val statusText: String = "Waiting to approve",
    val stamp: InboxStamp? = null
)

data class InboxStamp(
    val name: String,
    val badgeNumber: String,
    val signatureUrl: String,
    val actionText: String, // "Signed", "Approved", "Rejected", "Accepted", "Declined", "Acknowledged"
    val timestampText: String // "22 Oct 2026, 10:02 am"
)

@Entity(tableName = "inbox_items")
data class InboxEntity(
    @PrimaryKey val id: String,
    val type: String, // "organisation_message", "organisation_confirmation", "organisation_request", "group_request"
    val senderName: String, // e.g. "diBadge Inc.", "Tuas Roof Works"
    val departmentOrRequester: String, // e.g. "HR", "Livin Varghese"
    val groupId: String? = null,
    val groupName: String? = null,
    val permitNumber: String? = null,
    val subject: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val requiredAction: String = "none", // "none", "acknowledge", "verify", "sign", "approve"
    val actionStatus: String = "pending", // "pending", "acknowledged", "accepted", "declined", "signed", "approved", "rejected"
    val signers: List<InboxSigner> = emptyList(),
    val stamps: List<InboxStamp> = emptyList()
)

typealias InboxItem = InboxEntity
