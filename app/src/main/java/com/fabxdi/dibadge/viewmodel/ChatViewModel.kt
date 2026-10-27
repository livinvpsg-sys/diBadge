package com.fabxdi.dibadge.viewmodel

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fabxdi.dibadge.data.AppDatabase
import com.fabxdi.dibadge.data.ChatMessageEntity
import com.fabxdi.dibadge.ui.home.home_entry.chat.ChatComment
import com.fabxdi.dibadge.ui.home.home_entry.chat.ChatMessage
import com.fabxdi.dibadge.util.AlarmReceiver
import com.fabxdi.dibadge.util.FilePickerUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

fun ChatMessage.toEntity(groupId: String): ChatMessageEntity {
    return ChatMessageEntity(
        id = this.id,
        groupId = groupId,
        text = this.text,
        senderName = this.senderName,
        senderUid = "",
        isSentByMe = this.isSentByMe,
        attachmentsJson = this.attachments.joinToString("|||"),
        attachmentDurationsJson = this.attachmentDurations.entries.joinToString("|||") { "${it.key}:::${it.value}" },
        timestampMs = this.timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        replyingToId = this.replyingTo?.id,
        replyingToText = this.replyingTo?.text,
        replyingToSender = this.replyingTo?.senderName,
        replyingToAttachmentUri = this.replyingToAttachmentUri,
        isEdited = this.isEdited
    )
}

fun ChatMessageEntity.toDomain(): ChatMessage {
    val attachmentsList = if (this.attachmentsJson.isBlank()) emptyList() else this.attachmentsJson.split("|||")
    val durationsMap = if (this.attachmentDurationsJson.isBlank()) emptyMap() else {
        this.attachmentDurationsJson.split("|||").mapNotNull {
            val parts = it.split(":::")
            if (parts.size == 2) {
                parts[0] to (parts[1].toLongOrNull() ?: 0L)
            } else null
        }.toMap()
    }

    val replyMsg = if (!this.replyingToId.isNullOrBlank()) {
        ChatMessage(
            id = this.replyingToId,
            text = this.replyingToText ?: "",
            senderName = this.replyingToSender ?: "User",
            isSentByMe = false,
            attachments = if (this.replyingToAttachmentUri != null) listOf(this.replyingToAttachmentUri) else emptyList()
        )
    } else null

    return ChatMessage(
        id = this.id,
        text = this.text,
        senderName = this.senderName,
        isSentByMe = this.isSentByMe,
        attachments = attachmentsList,
        attachmentDurations = durationsMap,
        timestamp = LocalDateTime.ofInstant(Instant.ofEpochMilli(this.timestampMs), ZoneId.systemDefault()),
        replyingTo = replyMsg,
        replyingToAttachmentUri = this.replyingToAttachmentUri,
        isEdited = this.isEdited
    )
}

class ChatViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var messagesListener: ListenerRegistration? = null
    private var roomObserveJob: Job? = null
    private var activeGroupId: String = ""

    private suspend fun ensureAuthenticated(): FirebaseUser? {
        var user = auth.currentUser
        if (user == null) {
            try {
                val result = auth.signInAnonymously().await()
                user = result.user
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return user
    }

    fun listenToChatMessages(context: Context, groupId: String) {
        if (groupId.isBlank()) return
        if (groupId == activeGroupId && messagesListener != null) return
        activeGroupId = groupId

        messagesListener?.remove()
        roomObserveJob?.cancel()

        val dao = AppDatabase.getDatabase(context).reminderDao()

        // 1. Observe local Room DB for instant persistent messages across app restarts
        roomObserveJob = viewModelScope.launch(Dispatchers.IO) {
            dao.getChatMessagesForGroup(groupId).collect { entities ->
                val domainMsgs = entities.map { it.toDomain() }
                _messages.value = domainMsgs
            }
        }

        // 2. Real-time Firestore Snapshot Listener for live cloud sync
        _isLoading.value = true

        messagesListener = db.collection("personalGroups")
            .document(groupId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                _isLoading.value = false
                if (error != null) {
                    error.printStackTrace()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val currentUid = auth.currentUser?.uid ?: ""
                    val currentName = auth.currentUser?.displayName ?: "Me"

                    val entitiesToInsert = mutableListOf<ChatMessageEntity>()

                    snapshot.documents.forEach { doc ->
                        try {
                            val id = doc.id
                            val text = doc.getString("text") ?: ""
                            val senderName = doc.getString("senderName") ?: "User"
                            val senderUid = doc.getString("senderUid") ?: ""
                            val isSentByMe = if (senderUid.isNotBlank()) senderUid == currentUid else senderName == currentName || senderName == "Me"
                            val attachments = (doc.get("attachments") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

                            val durationsMap = (doc.get("attachmentDurations") as? Map<*, *>)?.mapNotNull { (k, v) ->
                                val keyStr = k?.toString() ?: return@mapNotNull null
                                val valLong = (v as? Number)?.toLong() ?: 0L
                                keyStr to valLong
                            }?.toMap() ?: emptyMap()

                            val tsLong = doc.getLong("timestamp") ?: System.currentTimeMillis()
                            val isEdited = doc.getBoolean("isEdited") ?: false

                            val replyToId = doc.getString("replyingToId")
                            val replyToText = doc.getString("replyingToText")
                            val replyToSender = doc.getString("replyingToSender")
                            val replyToUri = doc.getString("replyingToAttachmentUri")

                            entitiesToInsert.add(
                                ChatMessageEntity(
                                    id = id,
                                    groupId = groupId,
                                    text = text,
                                    senderName = senderName,
                                    senderUid = senderUid,
                                    isSentByMe = isSentByMe,
                                    attachmentsJson = attachments.joinToString("|||"),
                                    attachmentDurationsJson = durationsMap.entries.joinToString("|||") { "${it.key}:::${it.value}" },
                                    timestampMs = tsLong,
                                    replyingToId = replyToId,
                                    replyingToText = replyToText,
                                    replyingToSender = replyToSender,
                                    replyingToAttachmentUri = replyToUri,
                                    isEdited = isEdited
                                )
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    if (entitiesToInsert.isNotEmpty()) {
                        viewModelScope.launch(Dispatchers.IO) {
                            dao.insertChatMessages(entitiesToInsert)
                        }
                    }
                }
            }
    }

    fun sendMessage(
        context: Context,
        groupId: String,
        text: String,
        attachmentUris: List<Uri>,
        durationsMap: Map<String, Long>,
        replyingTo: ChatMessage?,
        replyingToAttachmentUri: String?
    ) {
        if (groupId.isBlank()) return
        if (text.isBlank() && attachmentUris.isEmpty()) return

        val msgId = UUID.randomUUID().toString()
        val ts = System.currentTimeMillis()

        val currentUid = auth.currentUser?.uid ?: ""
        val currentName = auth.currentUser?.displayName.takeIf { !it.isNullOrBlank() } ?: "Me"

        val optimisticMsg = ChatMessage(
            id = msgId,
            text = text.trim(),
            senderName = currentName,
            isSentByMe = true,
            attachments = attachmentUris.map { it.toString() },
            attachmentDurations = durationsMap,
            timestamp = LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault()),
            replyingTo = replyingTo,
            replyingToAttachmentUri = replyingToAttachmentUri
        )

        val dao = AppDatabase.getDatabase(context).reminderDao()

        // 1. Optimistic Room DB Save for permanent local persistence
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertChatMessage(optimisticMsg.toEntity(groupId))
        }

        // 2. Upload attachments & sync to Firestore
        viewModelScope.launch {
            try {
                val user = ensureAuthenticated()
                val senderUid = user?.uid ?: currentUid
                val senderName = user?.displayName.takeIf { !it.isNullOrBlank() } ?: currentName

                val uploadedUrls = mutableListOf<String>()
                val uploadedDurationsMap = mutableMapOf<String, Long>()

                for (uri in attachmentUris) {
                    val uriString = uri.toString()
                    if (uriString.startsWith("http://") || uriString.startsWith("https://")) {
                        uploadedUrls.add(uriString)
                        durationsMap[uriString]?.let { uploadedDurationsMap[uriString] = it }
                    } else {
                        try {
                            val fileName = FilePickerUtils.getFileName(context, uri).ifBlank { "${UUID.randomUUID()}" }
                            val storageRef = storage.reference.child("personalGroups/$groupId/chat_attachments/$fileName")
                            storageRef.putFile(uri).await()
                            val downloadUrl = storageRef.downloadUrl.await().toString()
                            uploadedUrls.add(downloadUrl)
                            durationsMap[uriString]?.let { uploadedDurationsMap[downloadUrl] = it }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            uploadedUrls.add(uriString)
                        }
                    }
                }

                val docData = mutableMapOf<String, Any>(
                    "id" to msgId,
                    "text" to text.trim(),
                    "senderName" to senderName,
                    "senderUid" to senderUid,
                    "attachments" to uploadedUrls,
                    "attachmentDurations" to uploadedDurationsMap,
                    "timestamp" to ts,
                    "isEdited" to false
                )

                if (replyingTo != null) {
                    docData["replyingToId"] = replyingTo.id
                    docData["replyingToText"] = replyingTo.text
                    docData["replyingToSender"] = replyingTo.senderName
                    val replyUri = replyingToAttachmentUri ?: replyingTo.attachments.firstOrNull()
                    if (replyUri != null) {
                        docData["replyingToAttachmentUri"] = replyUri
                    }
                }

                try {
                    db.collection("personalGroups")
                        .document(groupId)
                        .collection("messages")
                        .document(msgId)
                        .set(docData)
                        .await()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val preview = when {
                    text.isNotBlank() -> text.trim()
                    uploadedUrls.isNotEmpty() -> "Attachment"
                    else -> "Message"
                }

                try {
                    db.collection("personalGroups")
                        .document(groupId)
                        .set(
                            mapOf(
                                "lastMessage" to preview,
                                "lastMessageTimestamp" to ts,
                                "updatedAt" to ts
                            ),
                            SetOptions.merge()
                        )
                } catch (e: Exception) {
                    e.printStackTrace()
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteMessages(context: Context, groupId: String, messageIds: Set<String>) {
        if (groupId.isBlank() || messageIds.isEmpty()) return
        val dao = AppDatabase.getDatabase(context).reminderDao()

        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteChatMessages(messageIds.toList())
        }

        viewModelScope.launch {
            for (id in messageIds) {
                try {
                    db.collection("personalGroups")
                        .document(groupId)
                        .collection("messages")
                        .document(id)
                        .delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun editMessage(context: Context, groupId: String, messageId: String, newText: String) {
        if (groupId.isBlank() || messageId.isBlank() || newText.isBlank()) return
        val dao = AppDatabase.getDatabase(context).reminderDao()

        viewModelScope.launch(Dispatchers.IO) {
            val msg = _messages.value.find { it.id == messageId }
            if (msg != null) {
                val updated = msg.copy(text = newText.trim(), isEdited = true)
                dao.insertChatMessage(updated.toEntity(groupId))
            }
        }

        viewModelScope.launch {
            try {
                db.collection("personalGroups")
                    .document(groupId)
                    .collection("messages")
                    .document(messageId)
                    .update("text", newText.trim(), "isEdited", true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val threadCommentsListeners = mutableMapOf<String, ListenerRegistration>()
    private val _threadCommentsMap = MutableStateFlow<Map<String, List<ChatComment>>>(emptyMap())
    val threadCommentsMap: StateFlow<Map<String, List<ChatComment>>> = _threadCommentsMap.asStateFlow()

    fun listenToComments(threadId: String) {
        if (threadId.isBlank() || threadCommentsListeners.containsKey(threadId)) return

        val listener = db.collection("sharedThreads")
            .document(threadId)
            .collection("comments")
            .orderBy("timestampMs", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    error.printStackTrace()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val commentsList = snapshot.documents.mapNotNull { doc ->
                        try {
                            ChatComment(
                                id = doc.id,
                                threadId = threadId,
                                text = doc.getString("text") ?: "",
                                senderName = doc.getString("senderName") ?: "User",
                                senderUid = doc.getString("senderUid") ?: "",
                                groupName = doc.getString("groupName"),
                                timestampMs = doc.getLong("timestampMs") ?: System.currentTimeMillis()
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }

                    _threadCommentsMap.value = _threadCommentsMap.value.toMutableMap().apply {
                        put(threadId, commentsList)
                    }
                }
            }

        threadCommentsListeners[threadId] = listener
    }

    fun postComment(threadId: String, text: String, senderName: String, senderUid: String, groupName: String? = null) {
        if (threadId.isBlank() || text.isBlank()) return
        val commentId = UUID.randomUUID().toString()
        val ts = System.currentTimeMillis()

        val commentData = mutableMapOf<String, Any>(
            "id" to commentId,
            "threadId" to threadId,
            "text" to text.trim(),
            "senderName" to senderName,
            "senderUid" to senderUid,
            "timestampMs" to ts
        )
        if (!groupName.isNullOrBlank()) {
            commentData["groupName"] = groupName
        }

        viewModelScope.launch {
            try {
                db.collection("sharedThreads")
                    .document(threadId)
                    .collection("comments")
                    .document(commentId)
                    .set(commentData)
                    .await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendMemberMentionNotification(
        context: Context,
        targetUid: String,
        senderName: String,
        groupName: String,
        body: String
    ) {
        if (targetUid.isBlank()) return
        val title = "$senderName mentioned you in $groupName"
        val content = body.ifBlank { "Mentioned you in message" }

        viewModelScope.launch {
            try {
                val notifData = mapOf(
                    "title" to title,
                    "body" to content,
                    "targetUid" to targetUid,
                    "groupName" to groupName,
                    "timestampMs" to System.currentTimeMillis(),
                    "isRead" to false
                )
                db.collection("users")
                    .document(targetUid)
                    .collection("notifications")
                    .add(notifData)

                AlarmReceiver.showSystemNotification(
                    context,
                    targetUid.hashCode(),
                    title,
                    content
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        messagesListener?.remove()
        roomObserveJob?.cancel()
        threadCommentsListeners.values.forEach { it.remove() }
    }
}
