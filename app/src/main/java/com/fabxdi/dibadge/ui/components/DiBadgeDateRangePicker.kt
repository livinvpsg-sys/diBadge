package com.fabxdi.dibadge.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
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

    val effectiveStart = if (startDate != null && endDate != null && endDate!!.isBefore(startDate)) endDate else startDate
    val effectiveEnd = if (startDate != null && endDate != null && endDate!!.isBefore(startDate)) startDate else endDate

    val initialPickerMonth = remember {
        YearMonth.from(initialStartDate ?: minDate ?: LocalDate.now())
    }
    var pickerMonth by remember { mutableStateOf(initialPickerMonth) }
    var showYearPicker by remember { mutableStateOf(false) }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val outlineColor = MaterialTheme.colorScheme.outline
    val primaryTextColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = DiBadgeTheme.colors.accent
    val dragHandleColor = DiBadgeTheme.colors.tabInactive
    val inRangeBandColor = outlineColor

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

    val isOkEnabled = effectiveStart != null && effectiveEnd != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = surfaceColor,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = dragHandleColor)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            // 1. Top Title (Dynamic: "Pick start date" / "Pick end date" / Hidden when both picked)
            val dynamicTitle = when {
                effectiveStart == null -> "Pick start date"
                effectiveEnd == null -> "Pick end date"
                else -> ""
            }

            if (dynamicTitle.isNotBlank()) {
                Text(
                    text = dynamicTitle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = secondaryTextColor
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // 2. Range Line and Day Count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val startText = effectiveStart?.format(dayMonthFormatter) ?: "Start date"
                    val startColor = if (effectiveStart != null) primaryTextColor else secondaryTextColor

                    Text(
                        text = startText,
                        fontSize = if (effectiveStart == null && effectiveEnd == null) 22.sp else 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = startColor
                    )

                    Text(
                        text = " – ",
                        fontSize = if (effectiveStart == null && effectiveEnd == null) 22.sp else 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (effectiveStart != null && effectiveEnd != null) primaryTextColor else secondaryTextColor
                    )

                    val endText = effectiveEnd?.format(dayMonthFormatter) ?: "End date"
                    val endColor = if (effectiveEnd != null) primaryTextColor else secondaryTextColor

                    Text(
                        text = endText,
                        fontSize = if (effectiveStart == null && effectiveEnd == null) 22.sp else 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = endColor
                    )
                }

                // Right side day count
                val totalDays = if (effectiveStart != null && effectiveEnd != null) {
                    ChronoUnit.DAYS.between(effectiveStart, effectiveEnd) + 1
                } else null

                if (totalDays != null) {
                    Text(
                        text = if (totalDays == 1L) "1 day" else "$totalDays days",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(thickness = 1.dp, color = outlineColor)

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Month & Year Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showYearPicker = !showYearPicker }
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
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    pickerMonth = pickerMonth.withYear(yearVal)
                                    showYearPicker = false
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = yearVal.toString(),
                                fontSize = if (isSelectedYear) 28.sp else 16.sp,
                                fontWeight = if (isSelectedYear) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelectedYear) primaryTextColor else secondaryTextColor
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
                                    val isStart = effectiveStart != null && date.isEqual(effectiveStart)
                                    val isEnd = effectiveEnd != null && date.isEqual(effectiveEnd)
                                    val isSameDay = effectiveStart != null && effectiveEnd != null && effectiveStart.isEqual(effectiveEnd)
                                    val isRangeActive = effectiveStart != null && effectiveEnd != null && !isSameDay
                                    val isToday = date.isEqual(LocalDate.now())

                                    val isBeforeMin = minDate != null && date.isBefore(minDate)
                                    val isAfterMax = maxDate != null && date.isAfter(maxDate)
                                    val isDisabled = isBeforeMin || isAfterMax

                                    // In-Range Band Background
                                    if (isRangeActive) {
                                        val isAfterOrEqualStart = !date.isBefore(effectiveStart)
                                        val isBeforeOrEqualEnd = !date.isAfter(effectiveEnd)
                                        val isInFullRange = isAfterOrEqualStart && isBeforeOrEqualEnd

                                        if (isInFullRange) {
                                            val isStartCell = date.isEqual(effectiveStart)
                                            val isEndCell = date.isEqual(effectiveEnd)

                                            val bandModifier = when {
                                                isStartCell -> {
                                                    if (dayIndex == 0 || date.dayOfMonth == 1) {
                                                        Modifier.fillMaxSize()
                                                    } else {
                                                        Modifier.fillMaxHeight().fillMaxWidth(0.5f).align(Alignment.CenterEnd)
                                                    }
                                                }
                                                isEndCell -> {
                                                    if (dayIndex == 6 || date.isEqual(pickerMonth.atEndOfMonth())) {
                                                        Modifier.fillMaxSize()
                                                    } else {
                                                        Modifier.fillMaxHeight().fillMaxWidth(0.5f).align(Alignment.CenterStart)
                                                    }
                                                }
                                                else -> Modifier.fillMaxSize()
                                            }

                                            val shouldRoundStart = (isStartCell && (dayIndex == 0 || date.dayOfMonth == 1)) || dayIndex == 0 || date.dayOfMonth == 1
                                            val shouldRoundEnd = (isEndCell && (dayIndex == 6 || date.isEqual(pickerMonth.atEndOfMonth()))) || dayIndex == 6 || date.isEqual(pickerMonth.atEndOfMonth())

                                            val shape = RoundedCornerShape(
                                                topStart = if (shouldRoundStart) 20.dp else 0.dp,
                                                bottomStart = if (shouldRoundStart) 20.dp else 0.dp,
                                                topEnd = if (shouldRoundEnd) 20.dp else 0.dp,
                                                bottomEnd = if (shouldRoundEnd) 20.dp else 0.dp
                                            )

                                            Box(
                                                modifier = bandModifier.background(color = inRangeBandColor, shape = shape)
                                            )
                                        }
                                    }

                                    // Day Circle / Cell
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .alpha(if (isDisabled) 0.45f else 1.0f)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                enabled = !isDisabled
                                            ) {
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
                                                color = outlineColor,
                                                border = BorderStroke(1.dp, primaryTextColor)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = date.dayOfMonth.toString(),
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = primaryTextColor
                                                    )
                                                }
                                            }
                                        } else {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = date.dayOfMonth.toString(),
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Normal,
                                                    color = primaryTextColor
                                                )
                                                if (isToday) {
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.BottomCenter)
                                                            .padding(bottom = 6.dp)
                                                            .width(14.dp)
                                                            .height(2.dp)
                                                            .background(
                                                                color = accentColor,
                                                                shape = RoundedCornerShape(1.dp)
                                                            )
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

            // Bottom Actions: OK on right
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
                            onRangeSelected(effectiveStart!!, effectiveEnd!!)
                            onDismiss()
                        }
                    },
                    enabled = isOkEnabled
                ) {
                    Text(
                        text = "OK",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOkEnabled) accentColor else accentColor.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }
}
