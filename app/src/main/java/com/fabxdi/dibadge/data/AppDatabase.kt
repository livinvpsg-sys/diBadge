package com.fabxdi.dibadge.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [ReminderEntity::class, LeaveEntity::class, OvertimeEntity::class, HomeEntryEntity::class, ChatMessageEntity::class, InboxEntity::class, ClaimEntity::class, ActivityLogEntity::class, DocEntity::class, GroupTaskEntity::class, FormEntity::class, GroupFormEntity::class, FormSubmissionEntity::class], version = 20, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao
    abstract fun leaveOvertimeDao(): LeaveOvertimeDao
    abstract fun inboxDao(): InboxDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun docDao(): DocDao
    abstract fun groupTaskDao(): GroupTaskDao
    abstract fun formDao(): FormDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dibadge_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
