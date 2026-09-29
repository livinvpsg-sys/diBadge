package com.fabxdi.dibadge.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.fabxdi.dibadge.data.ReminderEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun parseFlexibleTime(timeStr: String): LocalTime? {
        val clean = timeStr.trim().uppercase()
        val formatters = listOf(
            DateTimeFormatter.ofPattern("h:mm a", Locale.US),
            DateTimeFormatter.ofPattern("hh:mm a", Locale.US),
            DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()),
            DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault()),
            DateTimeFormatter.ofPattern("H:mm", Locale.US),
            DateTimeFormatter.ofPattern("HH:mm", Locale.US),
            DateTimeFormatter.ofPattern("H:mm", Locale.getDefault()),
            DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        )
        for (fmt in formatters) {
            try {
                return LocalTime.parse(clean, fmt)
            } catch (_: Exception) {}
        }
        return null
    }

    fun schedule(reminder: ReminderEntity) {
        if (reminder.time.isNullOrBlank()) return

        val localTime = parseFlexibleTime(reminder.time) ?: return

        val targetDate = reminder.date ?: reminder.startDate ?: LocalDate.now()
        val scheduledDateTime = targetDate.atTime(localTime)

        // Ensure triggerAtMillis is at least 2 seconds in the future if set for today
        val targetMillis = scheduledDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val currentMillis = System.currentTimeMillis()
        val triggerAtMillis = if (targetMillis <= currentMillis) {
            // If the date is in the past (yesterday/earlier), don't schedule
            if (targetDate.isBefore(LocalDate.now())) return
            currentMillis + 2000L // 2 seconds from now
        } else {
            targetMillis
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("REMINDER_ID", reminder.id)
            putExtra("REMINDER_TITLE", reminder.title)
            putExtra("REMINDER_CONTENT", reminder.content)
            putExtra("IS_ALARM_ENABLED", reminder.isAlarmEnabled)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    fun cancel(reminderId: Int) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
