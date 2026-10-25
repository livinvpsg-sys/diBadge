package com.fabxdi.dibadge.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

data class FormLogosData(
    val leftLogoUrl: String = "",
    val centerLogoUrl: String = "",
    val rightLogoUrl: String = "",
    val size: String = "medium" // "small", "medium", "large"
) {
    fun hasLogos(): Boolean = leftLogoUrl.isNotBlank() || centerLogoUrl.isNotBlank() || rightLogoUrl.isNotBlank()

    fun getLogoHeightDp(): Int = when (size) {
        "small" -> 28
        "large" -> 56
        else -> 40
    }
}

@Entity(tableName = "user_forms")
data class FormEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val creatorUid: String = "me",
    val title: String,
    val description: String = "",
    val questionsJson: String = "[]",
    val logosJson: String = "{}",
    val addedGroupIdsJson: String = "[]",
    val version: Int = 1,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "group_forms")
data class GroupFormEntity(
    @PrimaryKey val id: String, // "${groupId}_${formId}"
    val groupId: String,
    val formId: String,
    val ownerUid: String = "me",
    val title: String,
    val description: String = "",
    val questionsJson: String = "[]",
    val logosJson: String = "{}",
    val version: Int = 1,
    val addedBy: String = "",
    val addedByUid: String = "",
    val addedAtMs: Long = System.currentTimeMillis(),
    val isOrgForm: Boolean = false,
    val orgName: String = ""
)

@Entity(tableName = "form_submissions")
data class FormSubmissionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val groupId: String,
    val formId: String,
    val formTitle: String = "",
    val version: Int = 1,
    val submitterUid: String,
    val submitterName: String,
    val answersJson: String = "{}",
    val logosJson: String = "{}",
    val status: String = "submitted", // "draft", "awaitingSignatures", "submitted", "cancelled"
    val workersJson: String = "[]", // List of FormSigner
    val supervisorJson: String = "{}", // FormSigner
    val isComplete: Boolean = true,
    val submittedAtMs: Long = System.currentTimeMillis()
)

data class FormQuestionData(
    val id: String = UUID.randomUUID().toString(),
    val type: String, // "short_answer", "long_answer", "number", "date", "time", "single_choice", "multiple_choice", "dropdown", "checklist", "photos", "signatures"
    val label: String = "",
    val isRequired: Boolean = false,
    val options: List<String> = emptyList(),
    val hasWorkerBlock: Boolean = true,
    val hasSupervisorBlock: Boolean = true
)

data class FormSigner(
    val uid: String,
    val name: String,
    val groupId: String = "",
    val isSupervisor: Boolean = false,
    val stamp: FormStamp? = null
)

data class FormStamp(
    val signatureUrl: String,
    val fullName: String,
    val badgeNumber: String = "",
    val signedAtMs: Long = System.currentTimeMillis(),
    val tzOffset: String = "GMT+8"
)
