package com.fabxdi.dibadge.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarGrid(
    onDateClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    highlightDates: Set<LocalDate> = emptySet(),
    leaveStatusMap: Map<LocalDate, String> = emptyMap()
) {
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var showYearPicker by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // Get today's date for highlighting
    val today = remember { LocalDate.now() }
    // State for the currently selected date
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    // Optimization: Memoize values to avoid heavy calculations on the main thread
    val daysInMonth = remember(currentMonth) { currentMonth.lengthOfMonth() }
    val firstDayOfMonth = remember(currentMonth) { currentMonth.atDay(1).dayOfWeek.value % 7 }
    val monthName = remember(currentMonth) { currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) }
    val year = remember(currentMonth) { currentMonth.year }

    // Prepare the dates list once per month change
    val calendarDays = remember(daysInMonth, firstDayOfMonth) {
        val days = mutableListOf<Int?>()
        repeat(firstDayOfMonth) { days.add(null) }
        for (i in 1..daysInMonth) { days.add(i) }
        while (days.size % 7 != 0) { days.add(null) }
        days.chunked(7)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DiBadgeTheme.spacing.medium)
            .pointerInput(Unit) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onDragEnd = {
                        if (abs(totalDrag) > 100) { // Increased threshold for stability
                            if (totalDrag > 0) {
                                currentMonth = currentMonth.minusMonths(1)
                            } else {
                                currentMonth = currentMonth.plusMonths(1)
                            }
                        }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        totalDrag += dragAmount
                    }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(64.dp))

        // Month and Year Display
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = monthName,
                    style = MaterialTheme.typography.displayLarge
                )
                Text(
                    text = year.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.clickable { showYearPicker = true }
                )
            }

            // Month Navigation Buttons
            Row {
                IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                    Text("<", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                }
                IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                    Text(">", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }

        Spacer(modifier = Modifier.Companion.height(DiBadgeTheme.spacing.large))

        // Day Names Header
        val daysOfWeek = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        Row(modifier = Modifier.fillMaxWidth()) {
            daysOfWeek.forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.Companion.height(DiBadgeTheme.spacing.medium))

        // Dates Grid (Using Column/Row for better performance than LazyVerticalGrid for static content)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            calendarDays.forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        val date = day?.let { currentMonth.atDay(it) }
                        val isToday = date == today
                        val isSelected = date == selectedDate
                        val isHighlighted = date != null && highlightDates.contains(date)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (day != null && date != null) {
                                Surface(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clickable { 
                                            selectedDate = date
                                            onDateClick(date)
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    color = when {
                                        isSelected -> Color.White
                                        isToday -> MaterialTheme.colorScheme.surfaceVariant
                                        else -> Color.Transparent
                                    },
                                    border = if (isToday && !isSelected) {
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    } else {
                                        null
                                    }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = day.toString(),
                                                style = MaterialTheme.typography.titleLarge.copy(
                                                    fontStyle = if (isHighlighted) FontStyle.Normal else FontStyle.Italic,
                                                    fontWeight = if (isHighlighted) FontWeight.ExtraBold else FontWeight.Normal
                                                ),
                                                color = when {
                                                    isSelected -> Color.Black
                                                    leaveStatusMap[date] == "Pending" -> Color(0xFF2196F3) // Blue
                                                    leaveStatusMap[date] == "Approved" -> Color(0xFF4CAF50) // Green
                                                    isToday -> MaterialTheme.colorScheme.onSurfaceVariant
                                                    else -> MaterialTheme.colorScheme.onBackground
                                                },
                                                textAlign = TextAlign.Center
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

    // Year Picker
    if (showYearPicker) {
        ModalBottomSheet(
            onDismissRequest = { showYearPicker = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            YearPickerList(
                currentYear = year,
                onYearSelected = { selectedYear ->
                    currentMonth = currentMonth.withYear(selectedYear)
                    showYearPicker = false
                }
            )
        }
    }
}

@Composable
fun YearPickerList(currentYear: Int, onYearSelected: (Int) -> Unit) {
    val years = remember { (currentYear - 50..currentYear + 50).toList() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = 48) // Roughly center the current year

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = DiBadgeTheme.spacing.medium)
    ) {
        items(years) { year ->
            val isSelected = year == currentYear
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onYearSelected(year) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = year.toString(),
                    textAlign = TextAlign.Center,
                    style = if (isSelected)
                        MaterialTheme.typography.headlineMedium
                    else
                        MaterialTheme.typography.titleMedium,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
