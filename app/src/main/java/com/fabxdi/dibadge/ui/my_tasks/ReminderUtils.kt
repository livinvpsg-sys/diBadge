package com.fabxdi.dibadge.ui.my_tasks

import com.fabxdi.dibadge.data.ReminderEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.format.DateTimeFormatter
import java.util.Locale

fun hasTasksOnDate(allReminders: List<ReminderEntity>, targetDate: LocalDate): Boolean {
    return allReminders.any { isReminderOnDate(it, targetDate) }
}

fun isReminderOnDate(reminder: ReminderEntity, targetDate: LocalDate): Boolean {
    val repeat = reminder.repeatType.lowercase()
    val baseStart = reminder.startDate ?: reminder.date ?: LocalDate.now()
    val endDate = reminder.endDate ?: reminder.date

    // 1. Check if targetDate is BEFORE the start date
    if (targetDate.isBefore(baseStart)) return false

    // 2. Check if targetDate is AFTER the end date (if an end date / due date is set)
    if (endDate != null && targetDate.isAfter(endDate)) return false

    // 3. Match repeat interval
    return when {
        repeat == "once" || repeat.isBlank() -> {
            val dueDate = reminder.date ?: baseStart
            targetDate == dueDate
        }
        repeat == "daily" -> {
            true // Repeats every day from baseStart up to endDate (or indefinitely if endDate == null)
        }
        repeat.startsWith("weekly") -> {
            val selectedDays = if (reminder.repeatType.contains(":")) {
                reminder.repeatType.substringAfter(":").split(",")
                    .mapNotNull {
                        try { DayOfWeek.valueOf(it.trim().uppercase()) } catch (e: Exception) { null }
                    }.toSet()
            } else {
                setOf(baseStart.dayOfWeek)
            }
            val targetDays = if (selectedDays.isEmpty()) setOf(baseStart.dayOfWeek) else selectedDays
            targetDate.dayOfWeek in targetDays
        }
        repeat.startsWith("monthly") -> {
            val selectedDayNums = if (reminder.repeatType.contains(":")) {
                reminder.repeatType.substringAfter(":").split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                    .toSet()
            } else {
                setOf(baseStart.dayOfMonth)
            }
            val targetDayNums = if (selectedDayNums.isEmpty()) setOf(baseStart.dayOfMonth) else selectedDayNums
            val actualDays = targetDayNums.map { it.coerceAtMost(targetDate.lengthOfMonth()) }.toSet()
            targetDate.dayOfMonth in actualDays
        }
        repeat.startsWith("yearly") -> {
            val (targetMonth, targetDayOfMonth) = if (reminder.repeatType.contains(":")) {
                val parts = reminder.repeatType.substringAfter(":").split("-")
                if (parts.size == 2) {
                    val m = try { Month.valueOf(parts[0].uppercase()) } catch (e: Exception) { baseStart.month }
                    val d = parts[1].toIntOrNull() ?: baseStart.dayOfMonth
                    m to d
                } else {
                    baseStart.month to baseStart.dayOfMonth
                }
            } else {
                baseStart.month to baseStart.dayOfMonth
            }
            targetDate.month == targetMonth && targetDate.dayOfMonth == targetDayOfMonth
        }
        repeat == "custom" -> {
            true
        }
        else -> targetDate == (reminder.date ?: baseStart)
    }
}

fun parseReminderTime(timeStr: String?): LocalTime {
    if (timeStr.isNullOrBlank()) return LocalTime.MAX
    return try {
        LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault()))
    } catch (e: Exception) {
        try {
            LocalTime.parse(timeStr.uppercase(), DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault()))
        } catch (e2: Exception) {
            LocalTime.MAX
        }
    }
}
