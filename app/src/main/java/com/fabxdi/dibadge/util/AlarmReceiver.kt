package com.fabxdi.dibadge.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.fabxdi.dibadge.MainActivity
import com.fabxdi.dibadge.R

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val title = intent.getStringExtra("REMINDER_TITLE") ?: "Task"
        val content = intent.getStringExtra("REMINDER_CONTENT") ?: ""
        val id = intent.getIntExtra("REMINDER_ID", 0)

        if (action == "ACTION_SNOOZE_TASK") {
            // Dismiss current notification
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(id)

            // Schedule Snooze alarm in 10 minutes
            val snoozeIntent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("REMINDER_ID", id)
                putExtra("REMINDER_TITLE", title)
                putExtra("REMINDER_CONTENT", content)
                putExtra("IS_ALARM_ENABLED", true)
            }
            val pendingSnooze = PendingIntent.getBroadcast(
                context,
                id + 100000,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val triggerAt = System.currentTimeMillis() + 10 * 60 * 1000L // 10 minutes later
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingSnooze)
            return
        }

        val isAlarmEnabled = intent.getBooleanExtra("IS_ALARM_ENABLED", false)
        showNotification(context, id, title, content, isAlarmEnabled)
    }

    private fun showNotification(
        context: Context,
        id: Int,
        title: String,
        content: String,
        isAlarmEnabled: Boolean
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val displayTitle = if (title.isNotBlank()) title else "Task"

        // Target activity intent when tapping notification -> opens task screen directly
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("OPEN_REMINDER_ID", id)
            putExtra("OPEN_MY_TASKS", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (isAlarmEnabled) {
            // ALARM ON: Standard Phone Alarm Tone + Snooze Action Button
            val channelId = "task_alarm_channel"
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Task Alarms",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    setSound(
                        alarmSound,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            // Snooze Action Intent (Snooze 10 minutes)
            val snoozeActionIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = "ACTION_SNOOZE_TASK"
                putExtra("REMINDER_ID", id)
                putExtra("REMINDER_TITLE", displayTitle)
                putExtra("REMINDER_CONTENT", content)
            }
            val snoozePendingIntent = PendingIntent.getBroadcast(
                context,
                id + 500000,
                snoozeActionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(displayTitle)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setSound(alarmSound)
                .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_launcher_foreground, "Snooze", snoozePendingIntent)
                .build()

            notificationManager.notify(id, notification)
        } else {
            // ALARM OFF: Silent Notification (No Sound, No Vibration)
            val channelId = "task_silent_channel"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Task Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(displayTitle)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSound(null)
                .setVibrate(longArrayOf(0L))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(id, notification)
        }
    }
}
