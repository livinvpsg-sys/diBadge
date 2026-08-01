package com.fabxdi.dibadge.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.fabxdi.dibadge.data.ReminderEntity
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(reminder: ReminderEntity) {
        if (!reminder.isAlarmEnabled || reminder.time == null) return

        val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())
        val localTime = LocalTime.parse(reminder.time, timeFormatter)
        
        val scheduledDateTime = when (reminder.repeatType) {
            "once" -> reminder.date?.atTime(localTime)
            "Custom" -> reminder.startDate?.atTime(localTime)
            else -> reminder.date?.atTime(localTime) // Simplified for now
        } ?: return

        if (scheduledDateTime.isBefore(LocalDateTime.now())) return

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("REMINDER_ID", reminder.id)
            putExtra("REMINDER_TITLE", reminder.title)
            putExtra("REMINDER_CONTENT", reminder.content)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = scheduledDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
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
