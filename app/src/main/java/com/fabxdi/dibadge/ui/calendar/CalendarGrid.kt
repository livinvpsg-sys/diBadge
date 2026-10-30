package com.fabxdi.dibadge.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.data.ClaimEntity
import com.fabxdi.dibadge.data.LeaveEntity
import com.fabxdi.dibadge.data.OvertimeEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

data class DayRequestSummary(
    val hasApprovedLeave: Boolean = false,
    val hasPendingLeave: Boolean = false,
    val hasApprovedOt: Boolean = false,
    val hasPendingOt: Boolean = false,
    val hasApprovedClaim: Boolean = false,
    val hasPendingClaim: Boolean = false
)

@Composable
fun CalendarGrid(
    currentYearMonth: YearMonth,
    leaves: List<LeaveEntity>,
    overtimes: List<OvertimeEntity>,
    claims: List<ClaimEntity>,
    onDateClick: (LocalDate) -> Unit
) {
    val today = remember { LocalDate.now() }
    val colors = MaterialTheme.colorScheme

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val outlineColor = colors.outline
    val pageBgColor = colors.background
    val isDark = primaryTextColor.red > 0.5f

    // Days mapping for Sunday-first month grid
    val daysInMonth = remember(currentYearMonth) {
        val firstOfMonth = currentYearMonth.atDay(1)
        val firstDayOfWeekSun = firstOfMonth.dayOfWeek.value % 7 // Sun=0, Mon=1...
        val totalDays = currentYearMonth.lengthOfMonth()

        val list = mutableListOf<LocalDate?>()
        for (i in 0 until firstDayOfWeekSun) {
            list.add(null)
        }
        for (d in 1..totalDays) {
            list.add(currentYearMonth.atDay(d))
        }
        // Pad to 6 rows (42 cells)
        while (list.size < 42) {
            list.add(null)
        }
        list
    }

    // Summary map for each date in currentYearMonth
    val daySummaryMap = remember(leaves, overtimes, claims, currentYearMonth) {
        val map = mutableMapOf<LocalDate, DayRequestSummary>()

        leaves.forEach { leave ->
            var curr = leave.startDate
            val end = leave.endDate ?: leave.startDate
            val isApproved = leave.status.equals("Approved", ignoreCase = true)
            val isPending = leave.status.equals("Pending", ignoreCase = true)

            while (!curr.isAfter(end)) {
                val old = map[curr] ?: DayRequestSummary()
                map[curr] = old.copy(
                    hasApprovedLeave = old.hasApprovedLeave || isApproved,
                    hasPendingLeave = old.hasPendingLeave || isPending
                )
                curr = curr.plusDays(1)
            }
        }

        overtimes.forEach { ot ->
            val isApproved = ot.status.equals("Approved", ignoreCase = true)
            val isPending = ot.status.equals("Pending", ignoreCase = true)
            val old = map[ot.date] ?: DayRequestSummary()
            map[ot.date] = old.copy(
                hasApprovedOt = old.hasApprovedOt || isApproved,
                hasPendingOt = old.hasPendingOt || isPending
            )
        }

        claims.forEach { claim ->
            val isApproved = claim.status.equals("Approved", ignoreCase = true)
            val isPending = claim.status.equals("Pending", ignoreCase = true)
            val old = map[claim.date] ?: DayRequestSummary()
            map[claim.date] = old.copy(
                hasApprovedClaim = old.hasApprovedClaim || isApproved,
                hasPendingClaim = old.hasPendingClaim || isPending
            )
        }

        map
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Weekday Header Row (Sunday first: Sun, Mon, Tue, Wed, Thu, Fri, Sat)
        val weekdays = remember {
            listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            weekdays.forEach { dayOfWeek ->
                val dayStr = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                Text(
                    text = dayStr,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = secondaryTextColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        HorizontalDivider(thickness = 1.dp, color = outlineColor)

        // 6 Rows Month Grid
        val rows = daysInMonth.chunked(7)
        rows.forEachIndexed { rowIndex, rowDays ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                rowDays.forEach { date ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(
                                if (date != null) Modifier.clickable { onDateClick(date) } else Modifier
                            ),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        if (date != null) {
                            val isToday = date.isEqual(today)
                            val summary = daySummaryMap[date] ?: DayRequestSummary()

                            val accessibilityDesc = buildString {
                                append("${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())}")
                                if (isToday) append(", Today")
                                if (summary.hasApprovedLeave) append(", Leave approved")
                                if (summary.hasPendingLeave) append(", Leave pending")
                                if (summary.hasApprovedOt) append(", Extra hours approved")
                                if (summary.hasPendingOt) append(", Extra hours pending")
                                if (summary.hasApprovedClaim) append(", Claim approved")
                                if (summary.hasPendingClaim) append(", Claim pending")
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 4.dp)
                                    .semantics { contentDescription = accessibilityDesc },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Day Number (Fixed 24dp size for exact alignment)
                                Box(
                                    modifier = Modifier.size(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isToday) {
                                        val todayBg = if (isDark) Color(0xFFF0F3F5) else Color(0xFF11161A)
                                        val todayFg = if (isDark) Color(0xFF0E1114) else Color(0xFFF5F7F9)
                                        Surface(
                                            modifier = Modifier.size(24.dp),
                                            shape = CircleShape,
                                            color = todayBg
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = date.dayOfMonth.toString(),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = todayFg
                                                )
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = date.dayOfMonth.toString(),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = primaryTextColor
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(3.dp))

                                // Status Pills Stack (Leave, OT, Claim)
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    if (summary.hasApprovedLeave || summary.hasPendingLeave) {
                                        StatusLabelPill(
                                            text = "Leave",
                                            hasApproved = summary.hasApprovedLeave,
                                            hasPending = summary.hasPendingLeave,
                                            isDark = isDark
                                        )
                                    }
                                    if (summary.hasApprovedOt || summary.hasPendingOt) {
                                        StatusLabelPill(
                                            text = "OT",
                                            hasApproved = summary.hasApprovedOt,
                                            hasPending = summary.hasPendingOt,
                                            isDark = isDark
                                        )
                                    }
                                    if (summary.hasApprovedClaim || summary.hasPendingClaim) {
                                        StatusLabelPill(
                                            text = "Claim",
                                            hasApproved = summary.hasApprovedClaim,
                                            hasPending = summary.hasPendingClaim,
                                            isDark = isDark
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (rowIndex < rows.lastIndex) {
                HorizontalDivider(thickness = 1.dp, color = outlineColor)
            }
        }
    }
}

@Composable
fun StatusLabelPill(
    text: String,
    hasApproved: Boolean,
    hasPending: Boolean,
    isDark: Boolean
) {
    val approvedFill = if (isDark) Color(0xFF16403D) else Color(0xFFD3E9E6)
    val approvedFg = if (isDark) Color(0xFF58B5AC) else Color(0xFF1B6F69)

    val pendingFill = if (isDark) Color(0xFF262D35) else Color(0xFFDDE3E8)
    val pendingFg = if (isDark) Color(0xFFA3AEB8) else Color(0xFF4A5661)

    val isSplit = hasApproved && hasPending

    Box(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .height(18.dp)
            .clip(RoundedCornerShape(6.dp))
            .then(
                when {
                    isSplit -> Modifier
                    hasApproved -> Modifier.background(approvedFill)
                    else -> Modifier.background(pendingFill)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSplit) {
            // Split Pill: Left Approved, Right Pending
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(approvedFill)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(pendingFill)
                )
            }
        }

        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = if (hasApproved && !isSplit) approvedFg else if (!hasApproved) pendingFg else approvedFg,
            textAlign = TextAlign.Center
        )
    }
}
