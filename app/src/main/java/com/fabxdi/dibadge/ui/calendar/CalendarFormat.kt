package com.fabxdi.dibadge.ui.calendar

import com.fabxdi.dibadge.data.LeaveEntity
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Hours as "1 hr 40 min", "3 hrs" or "25 min": minutes run up to 59, then the hour goes up. */
fun formatHours(hours: Double): String {
    val total = Math.round(hours * 60).toInt()
    val h = total / 60
    val m = total % 60
    return when {
        h == 0 && m == 0 -> "0 min"
        h == 0 -> "$m min"
        m == 0 -> if (h == 1) "1 hr" else "$h hrs"
        else -> "$h ${if (h == 1) "hr" else "hrs"} $m min"
    }
}

/** Days of a leave that fall inside the given month (a leave over two months counts only its part here). */
fun leaveDaysInMonth(leave: LeaveEntity, month: YearMonth): Double {
    val start = maxOf(leave.startDate, month.atDay(1))
    val end = minOf(leave.endDate ?: leave.startDate, month.atEndOfMonth())
    return if (end.isBefore(start)) 0.0 else (ChronoUnit.DAYS.between(start, end) + 1).toDouble()
}
