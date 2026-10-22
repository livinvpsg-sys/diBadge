package com.fabxdi.dibadge.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface InboxDao {
    @Query("SELECT * FROM inbox_items ORDER BY timestamp DESC")
    fun getAllInboxItems(): Flow<List<InboxEntity>>

    @Query("SELECT * FROM inbox_items WHERE id = :id LIMIT 1")
    suspend fun getInboxItemById(id: String): InboxEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInboxItem(item: InboxEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInboxItems(items: List<InboxEntity>)

    @Update
    suspend fun updateInboxItem(item: InboxEntity)

    @Delete
    suspend fun deleteInboxItem(item: InboxEntity)

    @Query("DELETE FROM inbox_items WHERE id IN (:ids)")
    suspend fun deleteInboxItemsByIds(ids: List<String>)

    @Query("DELETE FROM inbox_items")
    suspend fun deleteAllInboxItems()
}
