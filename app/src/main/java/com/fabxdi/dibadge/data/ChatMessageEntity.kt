package com.fabxdi.dibadge.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val text: String,
    val senderName: String,
    val senderUid: String,
    val isSentByMe: Boolean,
    val attachmentsJson: String,
    val attachmentDurationsJson: String,
    val timestampMs: Long,
    val replyingToId: String? = null,
    val replyingToText: String? = null,
    val replyingToSender: String? = null,
    val replyingToAttachmentUri: String? = null,
    val isEdited: Boolean = false
)
