package com.fabxdi.dibadge.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fabxdi.dibadge.data.AppDatabase
import com.fabxdi.dibadge.data.FormEntity
import com.fabxdi.dibadge.data.FormLogosData
import com.fabxdi.dibadge.data.FormQuestionData
import com.fabxdi.dibadge.data.FormSigner
import com.fabxdi.dibadge.data.FormStamp
import com.fabxdi.dibadge.data.FormSubmissionEntity
import com.fabxdi.dibadge.data.GroupFormEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class FormViewModel(application: Application) : AndroidViewModel(application) {
    private val formDao = AppDatabase.getDatabase(application).formDao()
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    val allUserForms: Flow<List<FormEntity>> = formDao.getAllUserForms()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            syncUserFormsFromFirestore()
        }
    }

    fun getFormsForGroup(groupId: String): Flow<List<GroupFormEntity>> {
        viewModelScope.launch(Dispatchers.IO) {
            syncGroupFormsFromFirestore(groupId)
        }
        return formDao.getFormsForGroup(groupId)
    }

    fun getSubmissionsForGroup(groupId: String): Flow<List<FormSubmissionEntity>> {
        viewModelScope.launch(Dispatchers.IO) {
            syncFormSubmissionsFromFirestore(groupId)
        }
        return formDao.getSubmissionsForGroup(groupId)
    }

    fun getSubmissionsForGroupForm(groupId: String, formId: String): Flow<List<FormSubmissionEntity>> {
        return formDao.getSubmissionsForGroupForm(groupId, formId)
    }

    fun saveUserForm(
        formId: String?,
        title: String,
        description: String,
        questions: List<FormQuestionData>,
        onSaved: (FormEntity) -> Unit = {}
    ) {
        val userUid = auth.currentUser?.uid ?: "me"
        viewModelScope.launch(Dispatchers.IO) {
            val isNew = formId.isNull_or_empty_custom()
            val id = if (isNew) UUID.randomUUID().toString() else formId!!
            val existing = if (!isNew) formDao.getUserFormById(id) else null
            val newVersion = if (existing != null) existing.version + 1 else 1
            val questionsJsonStr = encodeQuestionsToJson(questions)

            val updatedForm = FormEntity(
                id = id,
                creatorUid = userUid,
                title = title.trim(),
                description = description.trim(),
                questionsJson = questionsJsonStr,
                addedGroupIdsJson = existing?.addedGroupIdsJson ?: "[]",
                version = newVersion,
                createdAtMs = existing?.createdAtMs ?: System.currentTimeMillis(),
                updatedAtMs = System.currentTimeMillis()
            )

            formDao.insertUserForm(updatedForm)
            syncFormToFirestore(updatedForm)

            // If form was already added to groups, update snapshot in group_forms
            val addedGroupIds = parseStringListFromJson(updatedForm.addedGroupIdsJson)
            val currentUserName = auth.currentUser?.displayName ?: "Owner"
            for (gId in addedGroupIds) {
                val groupForm = GroupFormEntity(
                    id = "${gId}_${id}",
                    groupId = gId,
                    formId = id,
                    ownerUid = userUid,
                    title = updatedForm.title,
                    description = updatedForm.description,
                    questionsJson = updatedForm.questionsJson,
                    version = updatedForm.version,
                    addedBy = currentUserName,
                    addedByUid = userUid,
                    addedAtMs = System.currentTimeMillis()
                )
                formDao.insertGroupForm(groupForm)
                syncGroupFormToFirestore(groupForm)
            }

            launch(Dispatchers.Main) {
                onSaved(updatedForm)
            }
        }
    }

    fun deleteUserForm(form: FormEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            formDao.deleteUserForm(form)
            formDao.deleteGroupFormsByFormId(form.id)

            val uid = auth.currentUser?.uid
            if (!uid.isNull_or_empty_custom()) {
                try {
                    firestore.collection("users")
                        .document(uid!!)
                        .collection("forms")
                        .document(form.id)
                        .delete()
                } catch (e: Exception) {}
            }
        }
    }

    fun duplicateUserForm(form: FormEntity, onDone: (FormEntity) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val duplicated = FormEntity(
                id = UUID.randomUUID().toString(),
                creatorUid = form.creatorUid,
                title = "Copy of ${form.title}",
                description = form.description,
                questionsJson = form.questionsJson,
                addedGroupIdsJson = "[]",
                version = 1,
                createdAtMs = System.currentTimeMillis(),
                updatedAtMs = System.currentTimeMillis()
            )
            formDao.insertUserForm(duplicated)
            syncFormToFirestore(duplicated)
            launch(Dispatchers.Main) {
                onDone(duplicated)
            }
        }
    }

    fun addFormToGroups(
        form: FormEntity,
        targetGroupIds: List<String>,
        currentUserName: String,
        currentUserUid: String,
        onDone: (Int) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val existingGroupIds = parseStringListFromJson(form.addedGroupIdsJson).toMutableSet()
            var countAdded = 0

            for (groupId in targetGroupIds) {
                if (!existingGroupIds.contains(groupId)) {
                    existingGroupIds.add(groupId)
                    countAdded++

                    val groupForm = GroupFormEntity(
                        id = "${groupId}_${form.id}",
                        groupId = groupId,
                        formId = form.id,
                        ownerUid = form.creatorUid,
                        title = form.title,
                        description = form.description,
                        questionsJson = form.questionsJson,
                        version = form.version,
                        addedBy = currentUserName,
                        addedByUid = currentUserUid,
                        addedAtMs = System.currentTimeMillis()
                    )
                    formDao.insertGroupForm(groupForm)
                    syncGroupFormToFirestore(groupForm)
                }
            }

            val updatedForm = form.copy(addedGroupIdsJson = encodeStringListToJson(existingGroupIds.toList()))
            formDao.insertUserForm(updatedForm)
            syncFormToFirestore(updatedForm)

            launch(Dispatchers.Main) {
                onDone(countAdded)
            }
        }
    }

    fun removeFormFromGroup(groupId: String, formId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            formDao.deleteGroupFormByIds(groupId, formId)
            val form = formDao.getUserFormById(formId)
            if (form != null) {
                val groupIds = parseStringListFromJson(form.addedGroupIdsJson).filter { it != groupId }
                val updatedForm = form.copy(addedGroupIdsJson = encodeStringListToJson(groupIds))
                formDao.insertUserForm(updatedForm)
                syncFormToFirestore(updatedForm)
            }
        }
    }

    fun submitForm(
        groupId: String,
        formId: String,
        formTitle: String = "",
        version: Int,
        submitterUid: String,
        submitterName: String,
        answersMap: Map<String, String>,
        onSubmitted: () -> Unit = {}
    ) {
        submitFormWithSignatures(
            groupId = groupId,
            formId = formId,
            formTitle = formTitle,
            version = version,
            submitterUid = submitterUid,
            submitterName = submitterName,
            answersMap = answersMap,
            status = "submitted",
            workersJson = "[]",
            supervisorJson = "{}",
            onSubmitted = onSubmitted
        )
    }

    fun submitFormWithSignatures(
        groupId: String,
        formId: String,
        formTitle: String = "",
        version: Int,
        submitterUid: String,
        submitterName: String,
        answersMap: Map<String, String>,
        status: String = "submitted",
        workersJson: String = "[]",
        supervisorJson: String = "{}",
        onSubmitted: () -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val submission = FormSubmissionEntity(
                id = UUID.randomUUID().toString(),
                groupId = groupId,
                formId = formId,
                formTitle = formTitle,
                version = version,
                submitterUid = submitterUid,
                submitterName = submitterName,
                answersJson = encodeMapToJson(answersMap),
                status = status,
                workersJson = workersJson,
                supervisorJson = supervisorJson,
                isComplete = status == "submitted",
                submittedAtMs = System.currentTimeMillis()
            )
            formDao.insertFormSubmission(submission)
            try {
                firestore.collection("groups")
                    .document(groupId)
                    .collection("formSubmissions")
                    .document(submission.id)
                    .set(submission.toFirestoreMap(), SetOptions.merge())
            } catch (e: Exception) {}

            launch(Dispatchers.Main) {
                onSubmitted()
            }
        }
    }

    private suspend fun syncUserFormsFromFirestore() {
        val uid = auth.currentUser?.uid ?: return
        try {
            val snapshot = firestore.collection("users")
                .document(uid)
                .collection("forms")
                .get()
                .await()

            for (doc in snapshot.documents) {
                val mapData = doc.data ?: continue
                val form = mapData.toFormEntity(doc.id)
                if (form != null) {
                    formDao.insertUserForm(form)
                }
            }
        } catch (e: Exception) {}
    }

    private suspend fun syncGroupFormsFromFirestore(groupId: String) {
        try {
            val snapshot = firestore.collection("groups")
                .document(groupId)
                .collection("forms")
                .get()
                .await()

            for (doc in snapshot.documents) {
                val mapData = doc.data ?: continue
                val groupForm = mapData.toGroupFormEntity(doc.id)
                if (groupForm != null) {
                    formDao.insertGroupForm(groupForm)
                }
            }
        } catch (e: Exception) {}
    }

    private suspend fun syncFormSubmissionsFromFirestore(groupId: String) {
        try {
            val snapshot = firestore.collection("groups")
                .document(groupId)
                .collection("formSubmissions")
                .get()
                .await()

            for (doc in snapshot.documents) {
                val mapData = doc.data ?: continue
                val submission = mapData.toFormSubmissionEntity(doc.id, groupId)
                if (submission != null) {
                    formDao.insertFormSubmission(submission)
                }
            }
        } catch (e: Exception) {}
    }

    private fun syncFormToFirestore(form: FormEntity) {
        val uid = auth.currentUser?.uid ?: return
        try {
            firestore.collection("users")
                .document(uid)
                .collection("forms")
                .document(form.id)
                .set(form.toFirestoreMap(), SetOptions.merge())
        } catch (e: Exception) {}
    }

    private fun syncGroupFormToFirestore(groupForm: GroupFormEntity) {
        try {
            firestore.collection("groups")
                .document(groupForm.groupId)
                .collection("forms")
                .document(groupForm.formId)
                .set(groupForm.toFirestoreMap(), SetOptions.merge())
        } catch (e: Exception) {}
    }
}

private fun String?.isNull_or_empty_custom(): Boolean = this == null || this.trim().isEmpty()

fun encodeQuestionsToJson(questions: List<FormQuestionData>): String {
    val array = JSONArray()
    for (q in questions) {
        val obj = JSONObject()
        obj.put("id", q.id)
        obj.put("type", q.type)
        obj.put("label", q.label)
        obj.put("isRequired", q.isRequired)
        val optArray = JSONArray()
        for (opt in q.options) {
            optArray.put(opt)
        }
        obj.put("options", optArray)
        obj.put("hasWorkerBlock", q.hasWorkerBlock)
        obj.put("hasSupervisorBlock", q.hasSupervisorBlock)
        array.put(obj)
    }
    return array.toString()
}

fun parseQuestionsFromJson(jsonStr: String): List<FormQuestionData> {
    if (jsonStr.isBlank()) return emptyList()
    val list = mutableListOf<FormQuestionData>()
    try {
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val id = obj.optString("id")
            val type = obj.optString("type")
            val label = obj.optString("label")
            val isRequired = obj.optBoolean("isRequired")
            val optArray = obj.optJSONArray("options")
            val options = mutableListOf<String>()
            if (optArray != null) {
                for (j in 0 until optArray.length()) {
                    options.add(optArray.getString(j))
                }
            }
            val hasWorkerBlock = obj.optBoolean("hasWorkerBlock", true)
            val hasSupervisorBlock = obj.optBoolean("hasSupervisorBlock", true)

            list.add(
                FormQuestionData(
                    id = if (id.isNullOrEmpty()) UUID.randomUUID().toString() else id,
                    type = type,
                    label = label,
                    isRequired = isRequired,
                    options = options,
                    hasWorkerBlock = hasWorkerBlock,
                    hasSupervisorBlock = hasSupervisorBlock
                )
            )
        }
    } catch (e: Exception) {}
    return list
}

fun encodeStringListToJson(list: List<String>): String {
    val array = JSONArray()
    for (item in list) {
        array.put(item)
    }
    return array.toString()
}

fun parseStringListFromJson(jsonStr: String): List<String> {
    if (jsonStr.isBlank()) return emptyList()
    val list = mutableListOf<String>()
    try {
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            list.add(array.getString(i))
        }
    } catch (e: Exception) {}
    return list
}

fun encodeMapToJson(map: Map<String, String>): String {
    val obj = JSONObject()
    for ((k, v) in map) {
        obj.put(k, v)
    }
    return obj.toString()
}

fun parseMapFromJson(jsonStr: String): Map<String, String> {
    if (jsonStr.isBlank()) return emptyMap()
    val map = mutableMapOf<String, String>()
    try {
        val obj = JSONObject(jsonStr)
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = obj.optString(key)
        }
    } catch (e: Exception) {}
    return map
}

fun encodeSignersToJson(signers: List<FormSigner>): String {
    val array = JSONArray()
    for (s in signers) {
        val obj = JSONObject()
        obj.put("uid", s.uid)
        obj.put("name", s.name)
        obj.put("groupId", s.groupId)
        obj.put("isSupervisor", s.isSupervisor)
        if (s.stamp != null) {
            val stampObj = JSONObject()
            stampObj.put("signatureUrl", s.stamp.signatureUrl)
            stampObj.put("fullName", s.stamp.fullName)
            stampObj.put("badgeNumber", s.stamp.badgeNumber)
            stampObj.put("signedAtMs", s.stamp.signedAtMs)
            stampObj.put("tzOffset", s.stamp.tzOffset)
            obj.put("stamp", stampObj)
        }
        array.put(obj)
    }
    return array.toString()
}

fun parseSignersFromJson(jsonStr: String): List<FormSigner> {
    if (jsonStr.isBlank()) return emptyList()
    val list = mutableListOf<FormSigner>()
    try {
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val uid = obj.optString("uid")
            val name = obj.optString("name")
            val groupId = obj.optString("groupId")
            val isSupervisor = obj.optBoolean("isSupervisor")

            var stamp: FormStamp? = null
            val stampObj = obj.optJSONObject("stamp")
            if (stampObj != null) {
                stamp = FormStamp(
                    signatureUrl = stampObj.optString("signatureUrl"),
                    fullName = stampObj.optString("fullName"),
                    badgeNumber = stampObj.optString("badgeNumber"),
                    signedAtMs = stampObj.optLong("signedAtMs", System.currentTimeMillis()),
                    tzOffset = stampObj.optString("tzOffset", "GMT+8")
                )
            }

            list.add(FormSigner(uid = uid, name = name, groupId = groupId, isSupervisor = isSupervisor, stamp = stamp))
        }
    } catch (e: Exception) {}
    return list
}

fun encodeSingleSignerToJson(signer: FormSigner?): String {
    if (signer == null) return "{}"
    return encodeSignersToJson(listOf(signer)).let {
        if (it.startsWith("[") && it.endsWith("]")) {
            val arr = JSONArray(it)
            if (arr.length() > 0) arr.getJSONObject(0).toString() else "{}"
        } else "{}"
    }
}

fun parseSingleSignerFromJson(jsonStr: String): FormSigner? {
    if (jsonStr.isBlank() || jsonStr == "{}") return null
    return parseSignersFromJson("[$jsonStr]").firstOrNull()
}

fun encodeLogosToJson(logos: FormLogosData): String {
    val obj = JSONObject()
    obj.put("leftLogoUrl", logos.leftLogoUrl)
    obj.put("centerLogoUrl", logos.centerLogoUrl)
    obj.put("rightLogoUrl", logos.rightLogoUrl)
    obj.put("size", logos.size)
    return obj.toString()
}

fun Map<String, Any?>.toFormSubmissionEntity(id: String, defaultGroupId: String): FormSubmissionEntity? {
    val groupId = (this["groupId"] as? String) ?: defaultGroupId
    val formId = (this["formId"] as? String) ?: return null
    val formTitle = (this["formTitle"] as? String) ?: ""
    val version = (this["version"] as? Number)?.toInt() ?: 1
    val submitterUid = (this["submitterUid"] as? String) ?: ""
    val submitterName = (this["submitterName"] as? String) ?: ""
    val answersJson = (this["answersJson"] as? String) ?: "{}"
    val logosJson = (this["logosJson"] as? String) ?: "{}"
    val status = (this["status"] as? String) ?: "submitted"
    val workersJson = (this["workersJson"] as? String) ?: "[]"
    val supervisorJson = (this["supervisorJson"] as? String) ?: "{}"
    val isComplete = (this["isComplete"] as? Boolean) ?: (status == "submitted")
    val submittedAtMs = (this["submittedAtMs"] as? Number)?.toLong() ?: System.currentTimeMillis()

    return FormSubmissionEntity(
        id = id,
        groupId = groupId,
        formId = formId,
        formTitle = formTitle,
        version = version,
        submitterUid = submitterUid,
        submitterName = submitterName,
        answersJson = answersJson,
        logosJson = logosJson,
        status = status,
        workersJson = workersJson,
        supervisorJson = supervisorJson,
        isComplete = isComplete,
        submittedAtMs = submittedAtMs
    )
}

fun parseLogosFromJson(jsonStr: String): FormLogosData {
    if (jsonStr.isBlank() || jsonStr == "{}") return FormLogosData()
    return try {
        val obj = JSONObject(jsonStr)
        FormLogosData(
            leftLogoUrl = obj.optString("leftLogoUrl"),
            centerLogoUrl = obj.optString("centerLogoUrl"),
            rightLogoUrl = obj.optString("rightLogoUrl"),
            size = obj.optString("size", "medium")
        )
    } catch (e: Exception) {
        FormLogosData()
    }
}

fun FormEntity.toFirestoreMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "creatorUid" to creatorUid,
        "title" to title,
        "description" to description,
        "questionsJson" to questionsJson,
        "addedGroupIdsJson" to addedGroupIdsJson,
        "version" to version,
        "createdAtMs" to createdAtMs,
        "updatedAtMs" to updatedAtMs
    )
}

fun Map<String, Any?>.toFormEntity(documentId: String): FormEntity? {
    val idVal = this["id"] as? String ?: documentId
    val creatorUidVal = this["creatorUid"] as? String ?: "me"
    val titleVal = this["title"] as? String ?: "Form"
    val descriptionVal = this["description"] as? String ?: ""
    val questionsJsonVal = this["questionsJson"] as? String ?: "[]"
    val addedGroupIdsJsonVal = this["addedGroupIdsJson"] as? String ?: "[]"
    val versionVal = (this["version"] as? Long)?.toInt() ?: 1
    val createdAtMsVal = (this["createdAtMs"] as? Long) ?: System.currentTimeMillis()
    val updatedAtMsVal = (this["updatedAtMs"] as? Long) ?: System.currentTimeMillis()

    return FormEntity(
        id = idVal,
        creatorUid = creatorUidVal,
        title = titleVal,
        description = descriptionVal,
        questionsJson = questionsJsonVal,
        addedGroupIdsJson = addedGroupIdsJsonVal,
        version = versionVal,
        createdAtMs = createdAtMsVal,
        updatedAtMs = updatedAtMsVal
    )
}

fun GroupFormEntity.toFirestoreMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "groupId" to groupId,
        "formId" to formId,
        "ownerUid" to ownerUid,
        "title" to title,
        "description" to description,
        "questionsJson" to questionsJson,
        "version" to version,
        "addedBy" to addedBy,
        "addedByUid" to addedByUid,
        "addedAtMs" to addedAtMs,
        "isOrgForm" to isOrgForm,
        "orgName" to orgName
    )
}

fun Map<String, Any?>.toGroupFormEntity(documentId: String): GroupFormEntity? {
    val idVal = this["id"] as? String ?: documentId
    val groupIdVal = this["groupId"] as? String ?: ""
    val formIdVal = this["formId"] as? String ?: ""
    val ownerUidVal = this["ownerUid"] as? String ?: "me"
    val titleVal = this["title"] as? String ?: "Form"
    val descriptionVal = this["description"] as? String ?: ""
    val questionsJsonVal = this["questionsJson"] as? String ?: "[]"
    val versionVal = (this["version"] as? Long)?.toInt() ?: 1
    val addedByVal = this["addedBy"] as? String ?: "Owner"
    val addedByUidVal = this["addedByUid"] as? String ?: ""
    val addedAtMsVal = (this["addedAtMs"] as? Long) ?: System.currentTimeMillis()
    val isOrgFormVal = this["isOrgForm"] as? Boolean ?: false
    val orgNameVal = this["orgName"] as? String ?: ""

    return GroupFormEntity(
        id = idVal,
        groupId = groupIdVal,
        formId = formIdVal,
        ownerUid = ownerUidVal,
        title = titleVal,
        description = descriptionVal,
        questionsJson = questionsJsonVal,
        version = versionVal,
        addedBy = addedByVal,
        addedByUid = addedByUidVal,
        addedAtMs = addedAtMsVal,
        isOrgForm = isOrgFormVal,
        orgName = orgNameVal
    )
}

fun FormSubmissionEntity.toFirestoreMap(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "groupId" to groupId,
        "formId" to formId,
        "formTitle" to formTitle,
        "version" to version,
        "submitterUid" to submitterUid,
        "submitterName" to submitterName,
        "answersJson" to answersJson,
        "logosJson" to logosJson,
        "status" to status,
        "workersJson" to workersJson,
        "supervisorJson" to supervisorJson,
        "isComplete" to isComplete,
        "submittedAtMs" to submittedAtMs
    )
}
