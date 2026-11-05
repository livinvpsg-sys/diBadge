package com.fabxdi.dibadge.viewmodel

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.FileProvider
import com.fabxdi.dibadge.data.AppDatabase
import com.fabxdi.dibadge.data.ChatMessageEntity
import com.fabxdi.dibadge.data.HomeEntryEntity
import com.fabxdi.dibadge.ui.home.home_entry.chat.ChatComment
import com.fabxdi.dibadge.ui.home.home_entry.chat.ChatMessage
import com.fabxdi.dibadge.ui.home.home_entry.chat.PollOptionItem
import com.fabxdi.dibadge.ui.home.home_entry.chat.stripGroupMention
import com.fabxdi.dibadge.util.AlarmReceiver
import com.fabxdi.dibadge.util.FilePickerUtils
import com.fabxdi.dibadge.util.MediaCompressor
import com.fabxdi.dibadge.ui.home.home_entry.chat.AttachmentCategory
import com.fabxdi.dibadge.ui.home.home_entry.chat.getAttachmentCategory
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

// ---------------------------------------------------------------------------------------------
// Polls, locations and voice notes carry extra data. The local chat table has no columns for it,
// so it travels as one JSON text in the spare `libraryRevision` column (only for these types).
// ---------------------------------------------------------------------------------------------
private val EXTRAS_TYPES = setOf("poll", "location", "audio")

private fun extrasFromDoc(doc: DocumentSnapshot, msgType: String, myUid: String): String? {
    return try {
        when (msgType) {
            "poll" -> {
                val votes = doc.get("pollVotes") as? Map<*, *> ?: emptyMap<Any, Any>()
                val names = doc.get("pollVoterNames") as? Map<*, *> ?: emptyMap<Any, Any>()
                val opts = JSONArray()
                val mine = JSONArray()
                val voters = JSONObject()
                (doc.get("pollOptions") as? List<*>)?.forEach { raw ->
                    val m = raw as? Map<*, *> ?: return@forEach
                    val id = m["id"]?.toString() ?: return@forEach
                    val uids = (votes[id] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                    opts.put(JSONObject().put("id", id).put("t", m["text"]?.toString() ?: "").put("c", uids.size))
                    if (myUid in uids) mine.put(id)
                    voters.put(
                        id,
                        JSONArray(uids.map { if (it == myUid) "You" else (names[it]?.toString() ?: "Member") })
                    )
                }
                JSONObject()
                    .put("q", doc.getString("pollQuestion") ?: "")
                    .put("opts", opts)
                    .put("multi", doc.getBoolean("pollAllowMultiple") ?: false)
                    .put("show", doc.getBoolean("pollShowVoters") ?: true)
                    .put("closed", doc.getBoolean("isPollClosed") ?: false)
                    .put("mine", mine)
                    .put("voters", voters)
                    .toString()
            }
            "location" -> JSONObject()
                .put("lat", doc.getDouble("locationLat") ?: 0.0)
                .put("lng", doc.getDouble("locationLng") ?: 0.0)
                .put("acc", (doc.getLong("locationAccuracy") ?: 0L).toInt())
                .put("place", doc.getString("locationPlaceName") ?: "")
                .put("live", doc.getBoolean("isLiveLocation") ?: false)
                .put("exp", doc.getLong("liveExpiresAt") ?: 0L)
                .put("ended", doc.getBoolean("isLiveEnded") ?: false)
                .toString()
            "audio" -> JSONObject()
                .put("voice", doc.getBoolean("isVoiceNote") ?: true)
                .put("wave", JSONArray((doc.get("waveformArray") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList<Int>()))
                .toString()
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}

private fun ChatMessage.packExtras(): String? {
    return try {
        when (msgType) {
            "poll" -> JSONObject()
                .put("q", pollQuestion)
                .put("opts", JSONArray().also { a ->
                    pollOptions.forEach { a.put(JSONObject().put("id", it.id).put("t", it.text).put("c", it.voteCount)) }
                })
                .put("multi", pollAllowMultiple)
                .put("show", pollShowVoters)
                .put("closed", isPollClosed)
                .put("mine", JSONArray(userVotedOptionIds.toList()))
                .put("voters", JSONObject().also { o -> pollVoters.forEach { (k, v) -> o.put(k, JSONArray(v)) } })
                .toString()
            "location" -> JSONObject()
                .put("lat", locationLat).put("lng", locationLng).put("acc", locationAccuracy)
                .put("place", locationPlaceName ?: "").put("live", isLiveLocation)
                .put("exp", liveExpiresAt).put("ended", isLiveEnded)
                .toString()
            "audio" -> JSONObject().put("voice", isVoiceNote).put("wave", JSONArray(waveformArray)).toString()
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}

private fun ChatMessage.withExtras(json: String?): ChatMessage {
    if (json.isNullOrBlank() || msgType !in EXTRAS_TYPES) return this
    return try {
        val o = JSONObject(json)
        when (msgType) {
            "poll" -> {
                val opts = o.optJSONArray("opts") ?: JSONArray()
                val mine = o.optJSONArray("mine") ?: JSONArray()
                val voters = o.optJSONObject("voters") ?: JSONObject()
                copy(
                    libraryRevision = null,
                    pollQuestion = o.optString("q"),
                    pollOptions = (0 until opts.length()).map {
                        val x = opts.getJSONObject(it)
                        PollOptionItem(x.getString("id"), x.optString("t"), x.optInt("c"))
                    },
                    pollAllowMultiple = o.optBoolean("multi"),
                    pollShowVoters = o.optBoolean("show", true),
                    isPollClosed = o.optBoolean("closed"),
                    userVotedOptionIds = (0 until mine.length()).map { mine.getString(it) }.toSet(),
                    pollVoters = voters.keys().asSequence().associateWith { k ->
                        val arr = voters.optJSONArray(k) ?: JSONArray()
                        (0 until arr.length()).map { arr.getString(it) }
                    }
                )
            }
            "location" -> copy(
                libraryRevision = null,
                locationLat = o.optDouble("lat"),
                locationLng = o.optDouble("lng"),
                locationAccuracy = o.optInt("acc"),
                locationPlaceName = o.optString("place"),
                isLiveLocation = o.optBoolean("live"),
                liveExpiresAt = o.optLong("exp"),
                isLiveEnded = o.optBoolean("ended")
            )
            "audio" -> {
                val w = o.optJSONArray("wave") ?: JSONArray()
                copy(
                    libraryRevision = null,
                    isVoiceNote = o.optBoolean("voice", true),
                    waveformArray = (0 until w.length()).map { w.getInt(it) }
                )
            }
            else -> this
        }
    } catch (e: Exception) {
        this
    }
}

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
        isEdited = this.isEdited,
        isForwarded = this.isForwarded,
        msgType = this.msgType,
        libraryFileId = this.libraryFileId,
        libraryCategoryId = this.libraryCategoryId,
        libraryCategoryName = this.libraryCategoryName,
        libraryFileName = this.libraryFileName,
        libraryCount = this.libraryCount,
        libraryActorUid = this.libraryActorUid,
        libraryActorName = this.libraryActorName,
        libraryRevision = this.packExtras() ?: this.libraryRevision,
        isDeleted = this.isDeleted,
        deletedByRole = this.deletedByRole,
        isHiddenForMe = this.isHiddenForMe,
        sendStatus = this.sendStatus
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
        isEdited = this.isEdited,
        isForwarded = this.isForwarded,
        msgType = this.msgType,
        libraryFileId = this.libraryFileId,
        libraryCategoryId = this.libraryCategoryId,
        libraryCategoryName = this.libraryCategoryName,
        libraryFileName = this.libraryFileName,
        libraryCount = this.libraryCount,
        libraryActorUid = this.libraryActorUid,
        libraryActorName = this.libraryActorName,
        libraryRevision = this.libraryRevision,
        isDeleted = this.isDeleted,
        deletedByRole = this.deletedByRole,
        isHiddenForMe = this.isHiddenForMe,
        sendStatus = this.sendStatus
    ).withExtras(this.libraryRevision)
}

class ChatViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // False until the first read of the local database finishes, so the empty-chat
    // placeholder is not drawn for a moment while saved messages are still loading
    private val _messagesLoaded = MutableStateFlow(false)
    val messagesLoaded: StateFlow<Boolean> = _messagesLoaded.asStateFlow()

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
        _messagesLoaded.value = false

        messagesListener?.remove()
        roomObserveJob?.cancel()

        val dao = AppDatabase.getDatabase(context).reminderDao()

        // 1. Observe local Room DB for instant persistent messages across app restarts
        roomObserveJob = viewModelScope.launch(Dispatchers.IO) {
            dao.getChatMessagesForGroup(groupId).collect { entities ->
                // Messages the user removed with "Delete for me" are not shown
                val domainMsgs = entities.filter { !it.isHiddenForMe }.map { it.toDomain() }
                _messages.value = domainMsgs
                _messagesLoaded.value = true
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

                            val tsLong = when (val rawTs = doc.get("timestamp")) {
                                is Timestamp -> rawTs.toDate().time
                                is Number -> rawTs.toLong()
                                else -> doc.getLong("createdAt") ?: System.currentTimeMillis()
                            }
                            val isEdited = doc.getBoolean("isEdited") ?: false

                            val replyToId = doc.getString("replyingToId")
                            val replyToText = doc.getString("replyingToText")
                            val replyToSender = doc.getString("replyingToSender")
                            val replyToUri = doc.getString("replyingToAttachmentUri")

                            val msgType = doc.getString("msgType") ?: "text"
                            val libraryFileId = doc.getString("libraryFileId")
                            val libraryCategoryId = doc.getString("libraryCategoryId")
                            val libraryCategoryName = doc.getString("libraryCategoryName")
                            val libraryFileName = doc.getString("libraryFileName")
                            val libraryCount = doc.getLong("libraryCount")?.toInt() ?: 1
                            val libraryActorUid = doc.getString("libraryActorUid")
                            val libraryActorName = doc.getString("libraryActorName")
                            val libraryRevision = doc.getString("libraryRevision")

                            val isDeleted = doc.getBoolean("deleted") ?: false
                            val deletedByRole = doc.getString("deletedByRole") ?: "sender"
                            val hiddenUserUids = (doc.get("hiddenUsers") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                            val isHiddenForMe = hiddenUserUids.contains(currentUid)

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
                                    isEdited = isEdited,
                                    msgType = msgType,
                                    libraryFileId = libraryFileId,
                                    libraryCategoryId = libraryCategoryId,
                                    libraryCategoryName = libraryCategoryName,
                                    libraryFileName = libraryFileName,
                                    libraryCount = libraryCount,
                                    libraryActorUid = libraryActorUid,
                                    libraryActorName = libraryActorName,
                                    libraryRevision = extrasFromDoc(doc, msgType, currentUid) ?: libraryRevision,
                                    isDeleted = isDeleted,
                                    deletedByRole = deletedByRole,
                                    isHiddenForMe = isHiddenForMe
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
            replyingToAttachmentUri = replyingToAttachmentUri,
            sendStatus = "SENDING"
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
                            val category = getAttachmentCategory(context, uri)
                            val fileName = FilePickerUtils.getFileName(context, uri).ifBlank { "${UUID.randomUUID()}" }

                            val uploadUri: Uri
                            if (category == AttachmentCategory.IMAGE) {
                                val compResult = MediaCompressor.compressPhoto(context, uri)
                                uploadUri = compResult.fullUri
                                try {
                                    val thumbRef = storage.reference.child("personalGroups/$groupId/chat_attachments/${msgId}_thumb_${fileName}")
                                    thumbRef.putFile(compResult.thumbUri).await()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            } else if (category == AttachmentCategory.VIDEO) {
                                val compResult = MediaCompressor.compressVideo(context, uri)
                                uploadUri = compResult.fullUri
                                if (compResult.durationMs > 0L) {
                                    uploadedDurationsMap[uriString] = compResult.durationMs
                                }
                                try {
                                    val thumbRef = storage.reference.child("personalGroups/$groupId/chat_attachments/${msgId}_thumb_${fileName}")
                                    thumbRef.putFile(compResult.thumbUri).await()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            } else {
                                uploadUri = uri
                            }

                            val storageRef = storage.reference.child("personalGroups/$groupId/chat_attachments/$fileName")
                            storageRef.putFile(uploadUri).await()
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

                db.collection("personalGroups")
                    .document(groupId)
                    .collection("messages")
                    .document(msgId)
                    .set(docData)
                    .await()

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

                val sentMsg = optimisticMsg.copy(attachments = uploadedUrls, sendStatus = "SENT")
                dao.insertChatMessage(sentMsg.toEntity(groupId))

            } catch (e: Exception) {
                e.printStackTrace()
                val failedMsg = optimisticMsg.copy(sendStatus = "FAILED")
                dao.insertChatMessage(failedMsg.toEntity(groupId))
            }
        }
    }

    fun resendMessage(context: Context, groupId: String, messageId: String) {
        if (groupId.isBlank() || messageId.isBlank()) return
        val dao = AppDatabase.getDatabase(context).reminderDao()

        viewModelScope.launch(Dispatchers.IO) {
            val entity = dao.getChatMessageById(messageId) ?: return@launch
            val sendingEntity = entity.copy(sendStatus = "SENDING")
            dao.insertChatMessage(sendingEntity)

            try {
                val attachmentsList = if (sendingEntity.attachmentsJson.isBlank()) emptyList() else sendingEntity.attachmentsJson.split("|||")
                val currentUid = auth.currentUser?.uid ?: ""

                val docData = mutableMapOf<String, Any>(
                    "id" to messageId,
                    "text" to sendingEntity.text,
                    "senderName" to sendingEntity.senderName,
                    "senderUid" to currentUid,
                    "attachments" to attachmentsList,
                    "timestamp" to sendingEntity.timestampMs,
                    "isEdited" to sendingEntity.isEdited
                )

                db.collection("personalGroups")
                    .document(groupId)
                    .collection("messages")
                    .document(messageId)
                    .set(docData)
                    .await()

                dao.insertChatMessage(sendingEntity.copy(sendStatus = "SENT"))
            } catch (e: Exception) {
                e.printStackTrace()
                dao.insertChatMessage(sendingEntity.copy(sendStatus = "FAILED"))
            }
        }
    }

    fun deleteForEveryone(
        context: Context,
        groupId: String,
        selectedMessageIds: Set<String>,
        deletedByRole: String = "sender"
    ) {
        if (groupId.isBlank() || selectedMessageIds.isEmpty()) return
        val currentUid = auth.currentUser?.uid ?: ""

        viewModelScope.launch {
            val dao = AppDatabase.getDatabase(context).reminderDao()
            var failed = false
            var failReason = ""

            for (msgId in selectedMessageIds) {
                // 1. Update this phone straight away so the change shows at once
                val original = withContext(Dispatchers.IO) { dao.getChatMessageById(msgId) }
                if (original != null) {
                    withContext(Dispatchers.IO) {
                        dao.insertChatMessage(
                            original.copy(
                                isDeleted = true,
                                deletedByRole = deletedByRole,
                                text = "",
                                attachmentsJson = ""
                            )
                        )
                    }
                }

                // 2. Update the cloud copy for everyone else
                try {
                    val updates = mapOf<String, Any?>(
                        "deleted" to true,
                        "deletedBy" to currentUid,
                        "deletedByRole" to deletedByRole,
                        "deletedAt" to FieldValue.serverTimestamp(),
                        "text" to "",
                        "attachments" to emptyList<String>(),
                        "replyingToText" to null,
                        "replyingToAttachmentUri" to null
                    )

                    db.collection("personalGroups").document(groupId)
                        .collection("messages").document(msgId)
                        .update(updates)
                        .await()
                } catch (e: Exception) {
                    e.printStackTrace()
                    failed = true
                    failReason = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: (e.message ?: "unknown")
                    // Put the message back on this phone so it matches the cloud
                    if (original != null) {
                        withContext(Dispatchers.IO) { dao.insertChatMessage(original) }
                    }
                }
            }

            if (failed) {
                Toast.makeText(context, "Could not delete for everyone ($failReason). Please try again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun deleteForMe(
        context: Context,
        groupId: String,
        selectedMessageIds: Set<String>
    ) {
        if (groupId.isBlank() || selectedMessageIds.isEmpty()) return
        val currentUid = auth.currentUser?.uid ?: ""

        viewModelScope.launch {
            val dao = AppDatabase.getDatabase(context).reminderDao()

            for (msgId in selectedMessageIds) {
                // 1. Hide it on this phone straight away
                withContext(Dispatchers.IO) {
                    dao.getChatMessageById(msgId)?.let { entity ->
                        dao.insertChatMessage(entity.copy(isHiddenForMe = true))
                    }
                }

                // 2. Remember it in the cloud so it stays hidden after reinstall or on other devices
                if (currentUid.isNotBlank()) {
                    try {
                        db.collection("personalGroups").document(groupId)
                            .collection("messages").document(msgId)
                            .update("hiddenUsers", FieldValue.arrayUnion(currentUid))
                            .await()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun deleteMessages(context: Context, groupId: String, messageIds: Set<String>) {
        deleteForMe(context, groupId, messageIds)
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

    fun sendLocationMessage(
        context: Context,
        groupId: String,
        lat: Double,
        lng: Double,
        placeName: String,
        accuracy: Int,
        isLive: Boolean = false,
        durationMins: Int = 60
    ) {
        val currentUid = auth.currentUser?.uid ?: return
        val currentName = auth.currentUser?.displayName.takeIf { !it.isNullOrBlank() } ?: "Me"
        val messageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val expiresAt = if (isLive) now + durationMins * 60 * 1000L else 0L

        val docData = mutableMapOf<String, Any>(
            "id" to messageId,
            "senderUid" to currentUid,
            "senderName" to currentName,
            "timestamp" to Timestamp.now(),
            "text" to if (isLive) "Live location" else "Location",
            "msgType" to "location",
            "locationLat" to lat,
            "locationLng" to lng,
            "locationAccuracy" to accuracy,
            "locationPlaceName" to placeName,
            "isLiveLocation" to isLive,
            "liveExpiresAt" to expiresAt,
            "isLiveEnded" to false
        )

        viewModelScope.launch {
            try {
                db.collection("personalGroups")
                    .document(groupId)
                    .collection("messages")
                    .document(messageId)
                    .set(docData)
                    .await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** Casts, changes or removes the current user's vote on a poll message. */
    fun votePoll(groupId: String, message: ChatMessage, optionId: String) {
        val user = auth.currentUser ?: return
        if (message.isPollClosed) return
        val uid = user.uid
        val name = user.displayName.takeIf { !it.isNullOrBlank() } ?: "Member"

        val updates = mutableMapOf<String, Any>("pollVoterNames.$uid" to name)
        if (optionId in message.userVotedOptionIds) {
            updates["pollVotes.$optionId"] = FieldValue.arrayRemove(uid)
        } else {
            updates["pollVotes.$optionId"] = FieldValue.arrayUnion(uid)
            if (!message.pollAllowMultiple) {
                message.userVotedOptionIds.filter { it != optionId }.forEach {
                    updates["pollVotes.$it"] = FieldValue.arrayRemove(uid)
                }
            }
        }

        viewModelScope.launch {
            try {
                db.collection("personalGroups")
                    .document(groupId)
                    .collection("messages")
                    .document(message.id)
                    .update(updates)
                    .await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendPollMessage(
        context: Context,
        groupId: String,
        question: String,
        options: List<String>,
        allowMultiple: Boolean,
        showVoters: Boolean
    ) {
        val currentUid = auth.currentUser?.uid ?: return
        val currentName = auth.currentUser?.displayName.takeIf { !it.isNullOrBlank() } ?: "Me"
        val messageId = UUID.randomUUID().toString()

        val pollOptionsData = options.mapIndexed { index, text ->
            mapOf(
                "id" to index.toString(),
                "text" to text,
                "voteCount" to 0
            )
        }

        val docData = mutableMapOf<String, Any>(
            "id" to messageId,
            "senderUid" to currentUid,
            "senderName" to currentName,
            "timestamp" to Timestamp.now(),
            "text" to "Poll: $question",
            "msgType" to "poll",
            "pollQuestion" to question,
            "pollOptions" to pollOptionsData,
            "pollAllowMultiple" to allowMultiple,
            "pollShowVoters" to showVoters,
            "isPollClosed" to false
        )

        viewModelScope.launch {
            try {
                db.collection("personalGroups")
                    .document(groupId)
                    .collection("messages")
                    .document(messageId)
                    .set(docData)
                    .await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendVoiceNoteMessage(
        context: Context,
        groupId: String,
        audioFile: File,
        durationMs: Long,
        waveform: List<Int>
    ) {
        val currentUid = auth.currentUser?.uid ?: return
        val currentName = auth.currentUser?.displayName.takeIf { !it.isNullOrBlank() } ?: "Me"
        val messageId = UUID.randomUUID().toString()

        val audioUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            audioFile
        ).toString()

        val docData = mutableMapOf<String, Any>(
            "id" to messageId,
            "senderUid" to currentUid,
            "senderName" to currentName,
            "timestamp" to Timestamp.now(),
            "text" to "",
            "msgType" to "audio",
            "isVoiceNote" to true,
            "waveformArray" to waveform,
            "attachments" to listOf(audioUri),
            "attachmentDurations" to mapOf(audioUri to (durationMs / 1000L))
        )

        viewModelScope.launch {
            try {
                db.collection("personalGroups")
                    .document(groupId)
                    .collection("messages")
                    .document(messageId)
                    .set(docData)
                    .await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun forwardMessages(
        context: Context,
        selectedMessages: List<ChatMessage>,
        targetEntries: List<HomeEntryEntity>
    ) {
        val currentUid = auth.currentUser?.uid ?: ""
        val currentName = auth.currentUser?.displayName.takeIf { !it.isNullOrBlank() } ?: "Me"
        val dao = AppDatabase.getDatabase(context).reminderDao()

        viewModelScope.launch {
            for (target in targetEntries) {
                val targetGroupId = target.id.toString()
                for (msg in selectedMessages) {
                    val newMsgId = UUID.randomUUID().toString()
                    val ts = System.currentTimeMillis()

                    val forwardedMsg = ChatMessage(
                        id = newMsgId,
                        text = stripGroupMention(msg.text),
                        senderName = currentName,
                        isSentByMe = true,
                        attachments = msg.attachments,
                        attachmentDurations = msg.attachmentDurations,
                        timestamp = LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault()),
                        msgType = msg.msgType,
                        isVoiceNote = msg.isVoiceNote,
                        waveformArray = msg.waveformArray,
                        locationLat = msg.locationLat,
                        locationLng = msg.locationLng,
                        locationPlaceName = msg.locationPlaceName,
                        isForwarded = true,
                        replyingTo = null
                    )

                    withContext(Dispatchers.IO) {
                        dao.insertChatMessage(forwardedMsg.toEntity(targetGroupId))
                    }

                    try {
                        val docData = mutableMapOf<String, Any>(
                            "id" to newMsgId,
                            "text" to stripGroupMention(msg.text),
                            "senderName" to currentName,
                            "senderUid" to currentUid,
                            "timestamp" to Timestamp.now(),
                            "attachments" to msg.attachments,
                            "attachmentDurations" to msg.attachmentDurations,
                            "msgType" to msg.msgType,
                            "isVoiceNote" to msg.isVoiceNote,
                            "waveformArray" to msg.waveformArray,
                            "locationLat" to msg.locationLat,
                            "locationLng" to msg.locationLng,
                            "locationPlaceName" to (msg.locationPlaceName ?: ""),
                            "isForwarded" to true
                        )

                        db.collection("personalGroups")
                            .document(targetGroupId)
                            .collection("messages")
                            .document(newMsgId)
                            .set(docData)
                            .await()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
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
