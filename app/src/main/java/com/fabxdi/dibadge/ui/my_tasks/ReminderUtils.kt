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
    // 1. Match custom remindDate
    if (reminder.remindDate != null && targetDate.isEqual(reminder.remindDate)) {
        return true
    }

    // 2. Match due date
    if (reminder.date != null && targetDate.isEqual(reminder.date)) {
        return true
    }

    val repeat = reminder.repeatType.lowercase()

    // 3. Non-repeating task ("once" or blank)
    if (repeat == "once" || repeat.isBlank()) {
        val taskStart = reminder.startDate ?: reminder.date ?: reminder.remindDate ?: LocalDate.now()
        return targetDate.isEqual(taskStart)
    }

    // 4. Repeating task ("daily", "weekly", "monthly", "yearly", "custom")
    val baseStart = reminder.startDate ?: reminder.date ?: reminder.remindDate ?: LocalDate.now()
    val endDate = reminder.endDate ?: reminder.date

    if (targetDate.isBefore(baseStart)) return false
    if (endDate != null && targetDate.isAfter(endDate)) return false

    return when {
        repeat == "daily" -> true
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
            // 29 Feb falls back to the last day of February in non-leap years
            val effectiveDay = targetDayOfMonth.coerceAtMost(targetMonth.length(targetDate.isLeapYear))
            targetDate.month == targetMonth && targetDate.dayOfMonth == effectiveDay
        }
        repeat.startsWith("custom") -> {
            val range = parseCustomRange(reminder.repeatType)
            range != null && !targetDate.isBefore(range.first) && !targetDate.isAfter(range.second)
        }
        else -> false
    }
}

fun parseReminderTime(timeStr: String?): LocalTime {
    if (timeStr.isNullOrBlank()) return LocalTime.MAX
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
    return LocalTime.MAX
}

/** Stored form of a custom repeat range, e.g. "custom:2026-11-07/2026-11-20". */
fun customRepeatValue(start: LocalDate, end: LocalDate): String = "custom:$start/$end"

/** Returns the (start, end) of a custom repeat, or null for other or legacy values. */
fun parseCustomRange(repeatType: String?): Pair<LocalDate, LocalDate>? {
    if (repeatType == null || !repeatType.startsWith("custom:")) return null
    val parts = repeatType.substringAfter("custom:").split("/")
    if (parts.size != 2) return null
    return try {
        val s = LocalDate.parse(parts[0].trim())
        val e = LocalDate.parse(parts[1].trim())
        if (e.isBefore(s)) null else s to e
    } catch (_: Exception) {
        null
    }
}
