package com.fabxdi.dibadge.ui.my_tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.data.ReminderEntity
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTasksDatePicker(
    selectedDate: LocalDate? = null,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
    disabledDates: Set<LocalDate> = emptySet(),
    allReminders: List<ReminderEntity> = emptyList(),
    title: String = "Select date",
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    var tempSelectedDate by remember { mutableStateOf(selectedDate ?: LocalDate.now()) }
    var showYearPicker by remember { mutableStateOf(false) }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val outlineColor = MaterialTheme.colorScheme.outline
    val primaryTextColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = DiBadgeTheme.colors.accent
    val dragHandleColor = DiBadgeTheme.colors.tabInactive

    val headlineFormatter = remember { DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()) }
    val monthYearFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }

    // Range of YearMonths: 1990-01 to 2100-12 (1332 months)
    val startYearMonth = remember { YearMonth.of(1990, 1) }
    val totalMonthsCount = 1332
    val initialMonthIndex = remember(tempSelectedDate) {
        val ym = YearMonth.from(tempSelectedDate)
        ((ym.year - 1990) * 12) + (ym.monthValue - 1)
    }

    val monthListState = rememberLazyListState(initialFirstVisibleItemIndex = initialMonthIndex.coerceIn(0, totalMonthsCount - 1))
    val coroutineScope = rememberCoroutineScope()

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
            // 1. Top Title ("Select date")
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = secondaryTextColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 2. Selected Date Line
            Text(
                text = tempSelectedDate.format(headlineFormatter),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = primaryTextColor
            )

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(thickness = 1.dp, color = outlineColor)

            Spacer(modifier = Modifier.height(12.dp))

            if (showYearPicker) {
                // Vertical Year Picker List (1990 to 2100)
                val years = remember { (1990..2100).toList() }
                val currentYear = tempSelectedDate.year
                val currentYearIndex = remember(currentYear) {
                    years.indexOf(currentYear).coerceAtLeast(0)
                }
                val yearListState = rememberLazyListState(initialFirstVisibleItemIndex = (currentYearIndex - 2).coerceAtLeast(0))

                // Year Picker Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showYearPicker = false },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentYear.toString(),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropUp,
                        contentDescription = "Close Year Picker",
                        tint = primaryTextColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    state = yearListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(310.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(years) { _, yearVal ->
                        val isSelected = yearVal == currentYear

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    val newYM = YearMonth.of(yearVal, tempSelectedDate.monthValue)
                                    val targetIdx = ((newYM.year - 1990) * 12) + (newYM.monthValue - 1)
                                    showYearPicker = false
                                    coroutineScope.launch {
                                        monthListState.scrollToItem(targetIdx.coerceIn(0, totalMonthsCount - 1))
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = yearVal.toString(),
                                fontSize = if (isSelected) 36.sp else 18.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) primaryTextColor else secondaryTextColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            } else {
                // Vertical Scrollable Month List (Up and Down)
                LazyColumn(
                    state = monthListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(310.dp)
                ) {
                    items(totalMonthsCount) { monthIndex ->
                        val currentYearMonth = startYearMonth.plusMonths(monthIndex.toLong())

                        val daysInMonth = currentYearMonth.lengthOfMonth()
                        val firstDayOfMonth = currentYearMonth.atDay(1).dayOfWeek.value % 7

                        val calendarDays = remember(currentYearMonth) {
                            val days = mutableListOf<LocalDate?>()
                            repeat(firstDayOfMonth) { days.add(null) }
                            for (i in 1..daysInMonth) { days.add(currentYearMonth.atDay(i)) }
                            while (days.size % 7 != 0) { days.add(null) }
                            days.chunked(7)
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 20.dp)
                        ) {
                            // Month Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { showYearPicker = true }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = currentYearMonth.format(monthYearFormatter),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryTextColor
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Toggle Year Picker",
                                    tint = primaryTextColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Weekday Header
                            val daysOfWeek = listOf("S", "M", "T", "W", "T", "F", "S")
                            Row(modifier = Modifier.fillMaxWidth()) {
                                daysOfWeek.forEach { day ->
                                    Text(
                                        text = day,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = secondaryTextColor
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Days Grid
                            calendarDays.forEach { week ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                ) {
                                    week.forEach { date ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (date != null) {
                                                val isSelected = date.isEqual(tempSelectedDate)
                                                val hasTask = hasTasksOnDate(allReminders, date)

                                                val isBeforeMin = minDate != null && date.isBefore(minDate)
                                                val isAfterMax = maxDate != null && date.isAfter(maxDate)
                                                val isCustomDisabled = disabledDates.contains(date)
                                                val isDisabled = isBeforeMin || isAfterMax || isCustomDisabled

                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .alpha(if (isDisabled) 0.45f else 1.0f)
                                                        .clickable(
                                                            interactionSource = remember { MutableInteractionSource() },
                                                            indication = null,
                                                            enabled = !isDisabled
                                                        ) {
                                                            tempSelectedDate = date
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isSelected) {
                                                        Surface(
                                                            modifier = Modifier.fillMaxSize(),
                                                            shape = CircleShape,
                                                            color = Color.Transparent,
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
                                                        }
                                                    }

                                                    if (hasTask) {
                                                        Box(
                                                            modifier = Modifier
                                                                .align(Alignment.BottomCenter)
                                                                .padding(bottom = 4.dp)
                                                                .size(4.dp)
                                                            .background(
                                                                    color = accentColor,
                                                                    shape = CircleShape
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
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Actions Row: Today & OK
            val isTodayDisabled = remember(minDate, maxDate, disabledDates) {
                val today = LocalDate.now()
                (minDate != null && today.isBefore(minDate)) ||
                        (maxDate != null && today.isAfter(maxDate)) ||
                        disabledDates.contains(today)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        if (!isTodayDisabled) {
                            tempSelectedDate = LocalDate.now()
                            val targetIdx = ((tempSelectedDate.year - 1990) * 12) + (tempSelectedDate.monthValue - 1)
                            coroutineScope.launch {
                                monthListState.scrollToItem(targetIdx.coerceIn(0, totalMonthsCount - 1))
                            }
                        }
                    },
                    enabled = !isTodayDisabled
                ) {
                    Text(
                        text = "Today",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isTodayDisabled) accentColor.copy(alpha = 0.45f) else accentColor
                    )
                }

                TextButton(
                    onClick = {
                        onDateSelected(tempSelectedDate)
                        onDismiss()
                    }
                ) {
                    Text(
                        text = "OK",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
            }
        }
    }
}
