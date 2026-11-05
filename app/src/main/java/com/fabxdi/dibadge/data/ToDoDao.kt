package com.fabxdi.dibadge.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ToDoDao {
    @Query("SELECT * FROM reminders")
    fun getAllReminders(): Flow<List<ToDoEntity>>

    @Query("SELECT * FROM reminders")
    suspend fun getAllRemindersList(): List<ToDoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ToDoEntity): Long

    @Update
    suspend fun updateReminder(reminder: ToDoEntity)

    @Delete
    suspend fun deleteReminder(reminder: ToDoEntity)

    @Query("DELETE FROM reminders")
    suspend fun deleteAllReminders()

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getReminderById(id: Int): ToDoEntity?

    @Query("SELECT * FROM home_entries ORDER BY timestamp DESC")
    fun getAllHomeEntries(): Flow<List<HomeEntryEntity>>

    @Query("SELECT * FROM home_entries")
    suspend fun getAllHomeEntriesList(): List<HomeEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHomeEntry(entry: HomeEntryEntity): Long

    @Delete
    suspend fun deleteHomeEntry(entry: HomeEntryEntity)

    @Query("SELECT * FROM chat_messages WHERE groupId = :groupId ORDER BY timestampMs ASC")
    fun getChatMessagesForGroup(groupId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE id = :id")
    suspend fun getChatMessageById(id: String): ChatMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessages(messages: List<ChatMessageEntity>)

    @Query("DELETE FROM chat_messages WHERE id IN (:ids)")
    suspend fun deleteChatMessages(ids: List<String>)

    @Query("DELETE FROM chat_messages WHERE groupId = :groupId")
    suspend fun clearChatMessagesForGroup(groupId: String)
}

typealias ReminderDao = ToDoDao
