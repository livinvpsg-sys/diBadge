package com.fabxdi.dibadge.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FormDao {
    @Query("SELECT * FROM user_forms ORDER BY updatedAtMs DESC")
    fun getAllUserForms(): Flow<List<FormEntity>>

    @Query("SELECT * FROM user_forms WHERE id = :id")
    suspend fun getUserFormById(id: String): FormEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserForm(form: FormEntity)

    @Delete
    suspend fun deleteUserForm(form: FormEntity)

    @Query("SELECT * FROM group_forms WHERE groupId = :groupId ORDER BY addedAtMs DESC")
    fun getFormsForGroup(groupId: String): Flow<List<GroupFormEntity>>

    @Query("SELECT * FROM group_forms WHERE groupId = :groupId AND formId = :formId")
    suspend fun getGroupForm(groupId: String, formId: String): GroupFormEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupForm(groupForm: GroupFormEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllGroupForms(groupForms: List<GroupFormEntity>)

    @Delete
    suspend fun deleteGroupForm(groupForm: GroupFormEntity)

    @Query("DELETE FROM group_forms WHERE groupId = :groupId AND formId = :formId")
    suspend fun deleteGroupFormByIds(groupId: String, formId: String)

    @Query("DELETE FROM group_forms WHERE formId = :formId")
    suspend fun deleteGroupFormsByFormId(formId: String)

    @Query("SELECT * FROM form_submissions WHERE groupId = :groupId ORDER BY submittedAtMs DESC")
    fun getSubmissionsForGroup(groupId: String): Flow<List<FormSubmissionEntity>>

    @Query("SELECT * FROM form_submissions WHERE groupId = :groupId AND formId = :formId ORDER BY submittedAtMs DESC")
    fun getSubmissionsForGroupForm(groupId: String, formId: String): Flow<List<FormSubmissionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFormSubmission(submission: FormSubmissionEntity)
}
