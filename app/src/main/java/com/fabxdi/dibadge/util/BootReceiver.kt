package com.fabxdi.dibadge.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fabxdi.dibadge.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Android clears every alarm when the phone restarts (and when the app is updated).
 * This puts the saved reminders back.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scheduler = AlarmScheduler(app)
                AppDatabase.getDatabase(app).reminderDao().getAllReminders().first().forEach { reminder ->
                    if (!reminder.time.isNullOrBlank()) {
                        scheduler.schedule(reminder, skipIfPast = true)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pending.finish()
            }
        }
    }
}
