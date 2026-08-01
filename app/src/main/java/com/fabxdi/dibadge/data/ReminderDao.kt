package com.fabxdi.dibadge.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

import java.time.LocalDate

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders")
    fun getAllReminders(): Flow<List<ReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Delete
    suspend fun deleteReminder(reminder: ReminderEntity)

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getReminderById(id: Int): ReminderEntity?

    @Query("SELECT * FROM leave_logs")
    fun getAllLeaves(): Flow<List<LeaveEntity>>

    @Query("DELETE FROM leave_logs WHERE status = 'Pending' AND ( (endDate IS NULL AND startDate < :today) OR (endDate IS NOT NULL AND endDate < :today) )")
    suspend fun deletePastPendingLeaves(today: LocalDate)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLeave(leave: LeaveEntity): Long

    @Update
    suspend fun updateLeave(leave: LeaveEntity)

    @Delete
    suspend fun deleteLeave(leave: LeaveEntity)

    @Query("SELECT * FROM overtime_logs")
    fun getAllOvertime(): Flow<List<OvertimeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOvertime(overtime: OvertimeEntity): Long

    @Update
    suspend fun updateOvertime(overtime: OvertimeEntity)

    @Delete
    suspend fun deleteOvertime(overtime: OvertimeEntity)

    @Query("SELECT * FROM home_entries ORDER BY timestamp DESC")
    fun getAllHomeEntries(): Flow<List<HomeEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHomeEntry(entry: HomeEntryEntity): Long

    @Delete
    suspend fun deleteHomeEntry(entry: HomeEntryEntity)
}
