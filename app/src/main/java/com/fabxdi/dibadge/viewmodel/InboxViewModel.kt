package com.fabxdi.dibadge.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fabxdi.dibadge.data.ActivityLogEntity
import com.fabxdi.dibadge.data.AppDatabase
import com.fabxdi.dibadge.data.InboxEntity
import com.fabxdi.dibadge.data.InboxSigner
import com.fabxdi.dibadge.data.InboxStamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

class InboxViewModel(application: Application) : AndroidViewModel(application) {
    private val inboxDao = AppDatabase.getDatabase(application).inboxDao()
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private var firestoreListener: ListenerRegistration? = null
    private var currentSyncedUserId: String? = null

    val allInboxItems: Flow<List<InboxEntity>> = inboxDao.getAllInboxItems()

    val unreadCount: StateFlow<Int> = allInboxItems.map { list ->
        list.count { !it.isRead }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val pendingActionsCount: StateFlow<Int> = allInboxItems.map { list ->
        list.count { it.actionStatus == "pending" && it.requiredAction != "none" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (user != null) {
            if (currentSyncedUserId != user.uid) {
                setupFirestoreSync(user.uid)
            }
        } else {
            removeFirestoreSync()
            viewModelScope.launch(Dispatchers.IO) {
                inboxDao.deleteAllInboxItems()
            }
        }
    }

    init {
        auth.addAuthStateListener(authStateListener)
        auth.currentUser?.let { setupFirestoreSync(it.uid) }
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authStateListener)
        removeFirestoreSync()
    }

    private fun setupFirestoreSync(userId: String) {
        removeFirestoreSync()
        currentSyncedUserId = userId

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val snapshot = firestore.collection("users")
                    .document(userId)
                    .collection("inbox")
                    .get()
                    .await()

                if (snapshot.documents.isEmpty()) {
                    // Populate initial sample inbox items for new user
                    val sampleItems = createSampleInboxItems()
                    inboxDao.insertAllInboxItems(sampleItems)
                    for (item in sampleItems) {
                        firestore.collection("users")
                            .document(userId)
                            .collection("inbox")
                            .document(item.id)
                            .set(item.toFirestoreMap(), SetOptions.merge())
                    }
                } else {
                    for (doc in snapshot.documents) {
                        val mapData = doc.data ?: continue
                        val item = mapData.toInboxEntity(doc.id)
                        if (item != null) {
                            inboxDao.insertInboxItem(item)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore offline or firestore error
            }

            firestoreListener = firestore.collection("users")
                .document(userId)
                .collection("inbox")
                .addSnapshotListener { snapshot, e ->
                    if (e != null || snapshot == null) return@addSnapshotListener
                    viewModelScope.launch(Dispatchers.IO) {
                        for (doc in snapshot.documents) {
                            val mapData = doc.data ?: continue
                            val item = mapData.toInboxEntity(doc.id)
                            if (item != null) {
                                inboxDao.insertInboxItem(item)
                            }
                        }
                    }
                }
        }
    }

    private fun removeFirestoreSync() {
        firestoreListener?.remove()
        firestoreListener = null
        currentSyncedUserId = null
    }

    fun markAsRead(item: InboxEntity) {
        if (item.isRead) return
        viewModelScope.launch(Dispatchers.IO) {
            val updated = item.copy(isRead = true)
            inboxDao.updateInboxItem(updated)
            syncItemToFirestore(updated)
        }
    }

    fun performAction(
        item: InboxEntity,
        actionType: String, // "acknowledge", "accepted", "declined", "signed", "approved", "rejected"
        userName: String,
        userBadgeNumber: String,
        userSignatureUrl: String,
        reason: String = ""
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val dateText = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
            val timeText = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
            val fullTimestampStr = "$dateText, $timeText"

            val actionLabel = when (actionType) {
                "acknowledge" -> "Acknowledged"
                "accepted" -> "Accepted"
                "declined" -> "Declined"
                "signed" -> "Signed"
                "approved" -> "Approved"
                "rejected" -> "Rejected"
                else -> actionType.replaceFirstChar { it.uppercase() }
            }

            val newStamp = InboxStamp(
                name = userName,
                badgeNumber = userBadgeNumber,
                signatureUrl = userSignatureUrl,
                actionText = actionLabel,
                timestampText = fullTimestampStr
            )

            val newStamps = item.stamps + newStamp
            val updatedSigners = item.signers.map { signer ->
                if (signer.name == userName || signer.name.contains(userName, ignoreCase = true)) {
                    signer.copy(hasAnswered = true, statusText = actionLabel, stamp = newStamp)
                } else signer
            }

            val isComplete = updatedSigners.isEmpty() || updatedSigners.all { it.hasAnswered }

            val updated = item.copy(
                isRead = true,
                actionStatus = if (actionType == "decline" || actionType == "declined" || actionType == "reject" || actionType == "rejected") "declined" else actionType,
                signers = updatedSigners,
                stamps = newStamps
            )

            inboxDao.updateInboxItem(updated)
            syncItemToFirestore(updated)

            // Log entry into ActivityLogDao
            val resultLabel = when (actionType) {
                "acknowledge" -> "Confirmed"
                "accepted" -> "Approved"
                "declined" -> "Rejected"
                "signed" -> "Signed"
                "approved" -> "Approved"
                "rejected" -> "Rejected"
                else -> "Confirmed"
            }

            val kindLabel = when {
                item.permitNumber != null || item.subject.contains("permit", ignoreCase = true) -> "permit"
                item.subject.contains("toolbox", ignoreCase = true) -> "toolbox"
                else -> "message"
            }

            val refLabel = item.permitNumber ?: if (kindLabel == "toolbox") "TBM-0412" else "Letter"

            val activityLog = ActivityLogEntity(
                id = UUID.randomUUID().toString(),
                result = resultLabel,
                kind = kindLabel,
                title = item.subject,
                source = item.senderName,
                reference = refLabel,
                relatedId = item.id,
                timestamp = System.currentTimeMillis()
            )

            AppDatabase.getDatabase(getApplication()).activityLogDao().insertActivityLog(activityLog)
        }
    }

    fun deleteInboxItems(items: List<InboxEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            val deletable = items.filter { it.actionStatus != "pending" || it.requiredAction == "none" }
            val ids = deletable.map { it.id }
            inboxDao.deleteInboxItemsByIds(ids)

            val userId = auth.currentUser?.uid
            if (userId != null) {
                for (id in ids) {
                    try {
                        firestore.collection("users")
                            .document(userId)
                            .collection("inbox")
                            .document(id)
                            .delete()
                    } catch (e: Exception) {}
                }
            }
        }
    }

    private fun syncItemToFirestore(item: InboxEntity) {
        val userId = auth.currentUser?.uid ?: return
        try {
            firestore.collection("users")
                .document(userId)
                .collection("inbox")
                .document(item.id)
                .set(item.toFirestoreMap(), SetOptions.merge())
        } catch (e: Exception) {}
    }

    private fun createSampleInboxItems(): List<InboxEntity> {
        val now = System.currentTimeMillis()
        val hourAgo = now - 3600_000L * 2
        val yesterday = now - 3600_000L * 24

        return listOf(
            InboxEntity(
                id = "inbox_1",
                type = "group_request",
                senderName = "Tuas Roof Works",
                departmentOrRequester = "Livin Varghese",
                groupId = "group_tuas_roof",
                groupName = "Tuas Roof Works",
                permitNumber = "HW-0412",
                subject = "Approve hot work permit",
                body = "Please review and approve the hot work permit HW-0412 for solar installation at Block 4B. Hot work operations scheduled from 09:00 to 17:00.",
                timestamp = now,
                isRead = false,
                requiredAction = "approve",
                actionStatus = "pending",
                signers = listOf(
                    InboxSigner(name = "Livin Varghese", role = "Safety Assessor", hasAnswered = true, statusText = "Approved"),
                    InboxSigner(name = "User", role = "Site Engineer", hasAnswered = false, statusText = "Waiting to approve")
                )
            ),
            InboxEntity(
                id = "inbox_2",
                type = "organisation_confirmation",
                senderName = "diBadge Organisation",
                departmentOrRequester = "HR Department",
                subject = "Salary Revision Letter 2026",
                body = "Dear Team Member, We are pleased to issue your updated Salary Revision Letter for 2026. Please acknowledge receipt of this notice.",
                timestamp = hourAgo,
                isRead = false,
                requiredAction = "acknowledge",
                actionStatus = "pending"
            ),
            InboxEntity(
                id = "inbox_3",
                type = "organisation_message",
                senderName = "diBadge Organisation",
                departmentOrRequester = "HR Department",
                subject = "Annual Leave Approved",
                body = "Your annual leave application for 24 October 2026 to 26 October 2026 has been approved by HR.",
                timestamp = yesterday,
                isRead = true,
                requiredAction = "none",
                actionStatus = "complete"
            ),
            InboxEntity(
                id = "inbox_4",
                type = "group_request",
                senderName = "Safety Committee",
                departmentOrRequester = "Safety Officer",
                groupId = "group_safety",
                groupName = "Safety Committee",
                subject = "Sign today's toolbox meeting",
                body = "All site personnel must review and sign today's safety briefing on working at heights and personal protective equipment.",
                timestamp = yesterday - 3600_000L * 5,
                isRead = false,
                requiredAction = "sign",
                actionStatus = "pending",
                signers = listOf(
                    InboxSigner(name = "User", role = "Worker", hasAnswered = false, statusText = "Waiting to sign")
                )
            ),
            InboxEntity(
                id = "inbox_5",
                type = "organisation_message",
                senderName = "diBadge Organisation",
                departmentOrRequester = "Finance",
                subject = "September Payslip Issued",
                body = "Your payslip for September 2026 is now available. Please check your account records.",
                timestamp = yesterday - 3600_000L * 48,
                isRead = true,
                requiredAction = "none",
                actionStatus = "complete"
            )
        )
    }
}

fun InboxEntity.toFirestoreMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "type" to type,
        "senderName" to senderName,
        "departmentOrRequester" to departmentOrRequester,
        "groupId" to groupId,
        "groupName" to groupName,
        "permitNumber" to permitNumber,
        "subject" to subject,
        "body" to body,
        "timestamp" to timestamp,
        "isRead" to isRead,
        "requiredAction" to requiredAction,
        "actionStatus" to actionStatus,
        "updatedAt" to System.currentTimeMillis()
    )
}

fun Map<String, Any?>.toInboxEntity(documentId: String): InboxEntity? {
    val idVal = this["id"] as? String ?: documentId
    val typeVal = this["type"] as? String ?: "organisation_message"
    val senderNameVal = this["senderName"] as? String ?: "Organisation"
    val departmentOrRequesterVal = this["departmentOrRequester"] as? String ?: ""
    val groupIdVal = this["groupId"] as? String
    val groupNameVal = this["groupName"] as? String
    val permitNumberVal = this["permitNumber"] as? String
    val subjectVal = this["subject"] as? String ?: ""
    val bodyVal = this["body"] as? String ?: ""
    val timestampVal = (this["timestamp"] as? Long) ?: System.currentTimeMillis()
    val isReadVal = this["isRead"] as? Boolean ?: false
    val requiredActionVal = this["requiredAction"] as? String ?: "none"
    val actionStatusVal = this["actionStatus"] as? String ?: "pending"

    return InboxEntity(
        id = idVal,
        type = typeVal,
        senderName = senderNameVal,
        departmentOrRequester = departmentOrRequesterVal,
        groupId = groupIdVal,
        groupName = groupNameVal,
        permitNumber = permitNumberVal,
        subject = subjectVal,
        body = bodyVal,
        timestamp = timestampVal,
        isRead = isReadVal,
        requiredAction = requiredActionVal,
        actionStatus = actionStatusVal
    )
}
