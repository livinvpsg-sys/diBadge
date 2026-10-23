package com.fabxdi.dibadge.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityLogDao {
    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC")
    fun getAllActivityLogs(): Flow<List<ActivityLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivityLog(log: ActivityLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllActivityLogs(logs: List<ActivityLogEntity>)

    @Query("DELETE FROM activity_logs")
    suspend fun deleteAllActivityLogs()
}
