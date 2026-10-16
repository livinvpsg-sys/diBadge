package com.fabxdi.dibadge.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiBadgeDateRangePicker(
    initialStartDate: LocalDate? = null,
    initialEndDate: LocalDate? = null,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
    title: String = "Select dates",
    onRangeSelected: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    var startDate by remember { mutableStateOf(initialStartDate) }
    var endDate by remember { mutableStateOf(initialEndDate) }

    val initialPickerMonth = remember {
        YearMonth.from(initialStartDate ?: minDate ?: LocalDate.now())
    }
    var pickerMonth by remember { mutableStateOf(initialPickerMonth) }
    var showYearPicker by remember { mutableStateOf(false) }

    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val sheetBgColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)
    val primaryTextColor = if (isDark) Color(0xFFFFFFFF) else Color(0xFF111318)
    val secondaryTextColor = if (isDark) Color(0xFF8A8A8E) else Color(0xFF6B7078)
    val tealAccent = if (isDark) Color(0xFF5CC9C0) else Color(0xFF17645E)
    val inRangeBandColor = if (isDark) Color(0x335CC9C0) else Color(0x2417645E)

    val tealGradient = remember {
        Brush.linearGradient(
            colors = listOf(Color(0xFF5CC9C0), Color(0xFF17645E))
        )
    }

    val monthYearFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }
    val dayMonthFormatter = remember { DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()) }

    val daysInMonth = remember(pickerMonth) { pickerMonth.lengthOfMonth() }
    val firstDayOfMonth = remember(pickerMonth) { pickerMonth.atDay(1).dayOfWeek.value % 7 }

    val calendarDays = remember(daysInMonth, firstDayOfMonth) {
        val days = mutableListOf<LocalDate?>()
        repeat(firstDayOfMonth) { days.add(null) }
        for (i in 1..daysInMonth) { days.add(pickerMonth.atDay(i)) }
        while (days.size % 7 != 0) { days.add(null) }
        days.chunked(7)
    }

    val isOkEnabled = startDate != null && endDate != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = sheetBgColor,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = if (isDark) Color(0xFF5A5A5E) else Color(0xFFC9CCD4))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            // 1. Top Title ("Select dates")
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = secondaryTextColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 2. Range Line and Hint / Day Count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val startText = startDate?.format(dayMonthFormatter) ?: "Start date"
                    val startColor = if (startDate != null) primaryTextColor else secondaryTextColor

                    Text(
                        text = startText,
                        fontSize = if (startDate == null && endDate == null) 22.sp else 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = startColor
                    )

                    Text(
                        text = " – ",
                        fontSize = if (startDate == null && endDate == null) 22.sp else 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (startDate != null && endDate != null) primaryTextColor else secondaryTextColor
                    )

                    val endText = endDate?.format(dayMonthFormatter) ?: "End date"
                    val endColor = if (endDate != null) primaryTextColor else secondaryTextColor

                    Text(
                        text = endText,
                        fontSize = if (startDate == null && endDate == null) 22.sp else 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = endColor
                    )
                }

                // Right side hint or day count
                val totalDays = if (startDate != null && endDate != null) {
                    ChronoUnit.DAYS.between(startDate, endDate) + 1
                } else null

                if (totalDays != null) {
                    Text(
                        text = if (totalDays == 1L) "1 day" else "$totalDays days",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tealAccent
                    )
                } else if (startDate != null) {
                    Text(
                        text = "Pick end date",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = secondaryTextColor
                    )
                } else {
                    Text(
                        text = "Pick start date",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = secondaryTextColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(thickness = 1.dp, color = if (isDark) Color(0xFF3A3A3C) else Color(0xFFE3E5EA))

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Month & Year Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { showYearPicker = !showYearPicker }
                ) {
                    Text(
                        text = pickerMonth.format(monthYearFormatter),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select Year",
                        tint = primaryTextColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (showYearPicker) {
                val currentYear = pickerMonth.year
                val years = remember { (currentYear - 20..currentYear + 20).toList() }
                val listState = rememberLazyListState(initialFirstVisibleItemIndex = 18)

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(years) { yearVal ->
                        val isSelectedYear = yearVal == currentYear
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    pickerMonth = pickerMonth.withYear(yearVal)
                                    showYearPicker = false
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = yearVal.toString(),
                                fontSize = if (isSelectedYear) 20.sp else 16.sp,
                                fontWeight = if (isSelectedYear) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelectedYear) tealAccent else primaryTextColor
                            )
                        }
                    }
                }
            } else {
                // Weekday Headers (S M T W T F S)
                val daysOfWeek = listOf("S", "M", "T", "W", "T", "F", "S")
                Row(modifier = Modifier.fillMaxWidth()) {
                    daysOfWeek.forEach { day ->
                        Text(
                            text = day,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = secondaryTextColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Date Cells Grid with In-Range Band Drawing
                calendarDays.forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        week.forEachIndexed { dayIndex, date ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (date != null) {
                                    val isStart = startDate != null && date.isEqual(startDate)
                                    val isEnd = endDate != null && date.isEqual(endDate)
                                    val isInRange = startDate != null && endDate != null &&
                                            date.isAfter(startDate) && date.isBefore(endDate)
                                    val isToday = date.isEqual(LocalDate.now())

                                    val isBeforeMin = minDate != null && date.isBefore(minDate)
                                    val isAfterMax = maxDate != null && date.isAfter(maxDate)
                                    val isDisabled = isBeforeMin || isAfterMax

                                    // In-Range Band Background
                                    if (isInRange || isStart || isEnd) {
                                        val isRangeActive = startDate != null && endDate != null && !startDate!!.isEqual(endDate)
                                        if (isRangeActive) {
                                            val shape = RoundedCornerShape(
                                                topStart = if (isStart || dayIndex == 0) 20.dp else 0.dp,
                                                bottomStart = if (isStart || dayIndex == 0) 20.dp else 0.dp,
                                                topEnd = if (isEnd || dayIndex == 6) 20.dp else 0.dp,
                                                bottomEnd = if (isEnd || dayIndex == 6) 20.dp else 0.dp
                                            )

                                            val paddingStart = if (isStart) 20.dp else 0.dp
                                            val paddingEnd = if (isEnd) 20.dp else 0.dp

                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(start = paddingStart, end = paddingEnd)
                                                    .background(color = inRangeBandColor, shape = shape)
                                            )
                                        }
                                    }

                                    // Day Circle
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .alpha(if (isDisabled) 0.35f else 1.0f)
                                            .clickable(enabled = !isDisabled) {
                                                if (startDate == null || (startDate != null && endDate != null)) {
                                                    // Start new range
                                                    startDate = date
                                                    endDate = null
                                                } else {
                                                    // Waiting for end date
                                                    if (date.isBefore(startDate)) {
                                                        // Tap before start -> becomes new start date
                                                        startDate = date
                                                        endDate = null
                                                    } else {
                                                        // Tap on or after start -> picks end date
                                                        endDate = date
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isStart || isEnd) {
                                            Surface(
                                                modifier = Modifier.fillMaxSize(),
                                                shape = CircleShape,
                                                color = Color.Transparent
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(brush = tealGradient, shape = CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = date.dayOfMonth.toString(),
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        } else {
                                            Surface(
                                                modifier = Modifier.fillMaxSize(),
                                                shape = CircleShape,
                                                color = Color.Transparent,
                                                border = if (isToday) BorderStroke(1.5.dp, tealAccent) else null
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = date.dayOfMonth.toString(),
                                                        fontSize = 15.sp,
                                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                                        color = primaryTextColor
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bottom Actions: OK on right (NO Cancel button!)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        if (isOkEnabled) {
                            onRangeSelected(startDate!!, endDate!!)
                            onDismiss()
                        }
                    },
                    enabled = isOkEnabled
                ) {
                    Text(
                        text = "OK",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOkEnabled) tealAccent else secondaryTextColor.copy(alpha = 0.35f)
                    )
                }
            }
        }
    }
}
