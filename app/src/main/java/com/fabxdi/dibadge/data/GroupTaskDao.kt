package com.fabxdi.dibadge.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupTaskDao {
    @Query("SELECT * FROM group_tasks WHERE groupId = :groupId ORDER BY createdAt DESC")
    fun getTasksForGroup(groupId: String): Flow<List<GroupTaskEntity>>

    @Query("SELECT * FROM group_tasks WHERE assignedToUid = :uid OR assignedTo = :name ORDER BY createdAt DESC")
    fun getTasksAssignedToUser(uid: String, name: String): Flow<List<GroupTaskEntity>>

    @Query("SELECT * FROM group_tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: String): GroupTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupTask(task: GroupTaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllGroupTasks(tasks: List<GroupTaskEntity>)

    @Update
    suspend fun updateGroupTask(task: GroupTaskEntity)

    @Delete
    suspend fun deleteGroupTask(task: GroupTaskEntity)

    @Query("DELETE FROM group_tasks WHERE id = :taskId")
    suspend fun deleteTaskById(taskId: String)
}
