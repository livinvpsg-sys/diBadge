package com.fabxdi.dibadge.ui.my_tasks.to_do

import android.text.format.DateFormat
import androidx.compose.animation.Crossfade
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
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.components.WheelPicker
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.ui.theme.disableBottomSheetDrag
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindMePickerSheet(
    initialTime: LocalTime? = null,
    initialDate: LocalDate? = null,
    dueDate: LocalDate? = null,
    isRepeating: Boolean = false,
    onConfirm: (LocalTime, LocalDate?) -> Unit,
    onResetCustomDate: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val is24HourFormat = remember(context) { DateFormat.is24HourFormat(context) }

    var sheetViewMode by remember { mutableStateOf("time") } // "time" or "date"

    val defaultDate = remember(dueDate) { dueDate ?: LocalDate.now() }
    var currentRemindDate by remember(initialDate) { mutableStateOf(initialDate) }

    val startLocalTime = initialTime ?: LocalTime.now().plusMinutes(5)
    var selectedHour24 by remember { mutableIntStateOf(startLocalTime.hour) }
    var selectedMinute by remember { mutableIntStateOf(startLocalTime.minute) }

    val effectiveDate = currentRemindDate ?: defaultDate
    val isCustomDate = currentRemindDate != null

    val surfaceColor = MaterialTheme.colorScheme.surface
    val outlineColor = MaterialTheme.colorScheme.outline
    val primaryTextColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = DiBadgeTheme.colors.accent
    val dragHandleColor = DiBadgeTheme.colors.tabInactive
    val tabInactiveColor = DiBadgeTheme.colors.tabInactive

    val dateRowFormatter = remember { DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()) }
    val timeFormatter = remember(is24HourFormat) {
        if (is24HourFormat) DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        else DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    }

    val formattedDateText = remember(effectiveDate) {
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        when {
            effectiveDate.isEqual(today) -> "Today, ${effectiveDate.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))}"
            effectiveDate.isEqual(tomorrow) -> "Tomorrow, ${effectiveDate.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))}"
            else -> effectiveDate.format(dateRowFormatter)
        }
    }

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
            Crossfade(targetState = sheetViewMode, label = "remindMeSheetMode") { mode ->
                if (mode == "time") {
                    // ==================== TIME WHEELS VIEW ====================
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (isRepeating) {
                            Text(
                                text = "Select time",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                color = secondaryTextColor,
                                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                            )
                            HorizontalDivider(thickness = 1.dp, color = outlineColor)
                        } else {
                            // Date Row
                            val dateRowDesc = if (isCustomDate) {
                                "Date, ${effectiveDate.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))}, custom date. Double tap to change."
                            } else {
                                "Date, ${effectiveDate.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))}. Double tap to change."
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .clickable { sheetViewMode = "date" }
                                    .padding(vertical = 8.dp)
                                    .semantics { contentDescription = dateRowDesc },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CalendarMonth,
                                        contentDescription = null,
                                        tint = secondaryTextColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            text = "Date",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = primaryTextColor
                                        )
                                        if (isCustomDate) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "Custom date • ",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Normal,
                                                    color = tabInactiveColor
                                                )
                                                Text(
                                                    text = "Reset",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = accentColor,
                                                    modifier = Modifier
                                                        .clickable {
                                                            currentRemindDate = null
                                                            onResetCustomDate()
                                                        }
                                                        .semantics {
                                                            contentDescription = "Reset reminder date to the due date"
                                                        }
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = formattedDateText,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = primaryTextColor
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = secondaryTextColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            HorizontalDivider(thickness = 1.dp, color = outlineColor)
                        }

                        Column(modifier = Modifier.disableBottomSheetDrag()) {
                            Spacer(modifier = Modifier.height(16.dp))

                            // Time Wheels
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (is24HourFormat) {
                                    val hours24List = remember { (0..23).map { String.format(Locale.getDefault(), "%02d", it) } }
                                    WheelPicker(
                                        items = hours24List,
                                        selectedIndex = selectedHour24,
                                        onSelectedIndexChanged = { selectedHour24 = it },
                                        modifier = Modifier.width(80.dp),
                                        primaryColor = primaryTextColor,
                                        contentDescriptionLabel = "Hour"
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(":", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = primaryTextColor)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    val minutesList = remember { (0..59).map { String.format(Locale.getDefault(), "%02d", it) } }
                                    WheelPicker(
                                        items = minutesList,
                                        selectedIndex = selectedMinute,
                                        onSelectedIndexChanged = { selectedMinute = it },
                                        modifier = Modifier.width(80.dp),
                                        primaryColor = primaryTextColor,
                                        contentDescriptionLabel = "Minute"
                                    )
                                } else {
                                    val hours12List = remember { listOf("12") + (1..11).map { String.format(Locale.getDefault(), "%02d", it) } }
                                    val current12HourIndex = if (selectedHour24 % 12 == 0) 0 else selectedHour24 % 12
                                    WheelPicker(
                                        items = hours12List,
                                        selectedIndex = current12HourIndex,
                                        onSelectedIndexChanged = { h12Idx ->
                                            val isPm = selectedHour24 >= 12
                                            val hourNum = if (h12Idx == 0) 0 else h12Idx
                                            selectedHour24 = if (isPm) (if (hourNum == 12) 12 else hourNum + 12) else (if (hourNum == 12) 0 else hourNum)
                                        },
                                        modifier = Modifier.width(72.dp),
                                        primaryColor = primaryTextColor,
                                        contentDescriptionLabel = "Hour"
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(":", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = primaryTextColor)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val minutesList = remember { (0..59).map { String.format(Locale.getDefault(), "%02d", it) } }
                                    WheelPicker(
                                        items = minutesList,
                                        selectedIndex = selectedMinute,
                                        onSelectedIndexChanged = { selectedMinute = it },
                                        modifier = Modifier.width(72.dp),
                                        primaryColor = primaryTextColor,
                                        contentDescriptionLabel = "Minute"
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    val amPmList = remember { listOf("AM", "PM") }
                                    val currentAmPmIdx = if (selectedHour24 >= 12) 1 else 0
                                    WheelPicker(
                                        items = amPmList,
                                        selectedIndex = currentAmPmIdx,
                                        onSelectedIndexChanged = { amPmIdx ->
                                            val isPm = amPmIdx == 1
                                            val h12 = selectedHour24 % 12
                                            selectedHour24 = if (isPm) h12 + 12 else h12
                                        },
                                        isInfinite = false,
                                        modifier = Modifier.width(72.dp),
                                        primaryColor = primaryTextColor,
                                        contentDescriptionLabel = "AM or PM"
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // OK Button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        val chosenTime = LocalTime.of(
                                            selectedHour24.coerceIn(0, 23),
                                            selectedMinute.coerceIn(0, 59)
                                        )
                                        onConfirm(chosenTime, currentRemindDate)
                                        onDismiss()
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("OK", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = accentColor)
                                }
                            }
                        }
                    }
                } else {
                    // ==================== DATE PICKER VIEW ====================
                    val currentSelectedTime = LocalTime.of(
                        selectedHour24.coerceIn(0, 23),
                        selectedMinute.coerceIn(0, 59)
                    )
                    val timeStr = currentSelectedTime.format(timeFormatter)

                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Time Row at top
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable { sheetViewMode = "time" }
                                .padding(vertical = 8.dp)
                                .semantics {
                                    contentDescription = "Time, $timeStr. Double tap to change."
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AccessTime,
                                    contentDescription = null,
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = "Time",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = primaryTextColor
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = timeStr,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = primaryTextColor
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        HorizontalDivider(thickness = 1.dp, color = outlineColor)

                        Column(modifier = Modifier.disableBottomSheetDrag()) {
                            Spacer(modifier = Modifier.height(12.dp))

                            // Fixed Weekday Row
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

                            // Inline Date Grid (Month Calendar)
                            RemindMeInlineDateGrid(
                                selectedDate = effectiveDate,
                                minDate = LocalDate.now(),
                                maxDate = dueDate,
                                onDateSelected = { newDate ->
                                    currentRemindDate = newDate
                                },
                                primaryTextColor = primaryTextColor,
                                secondaryTextColor = secondaryTextColor
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // OK Button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        val chosenTime = LocalTime.of(
                                            selectedHour24.coerceIn(0, 23),
                                            selectedMinute.coerceIn(0, 59)
                                        )
                                        onConfirm(chosenTime, currentRemindDate)
                                        onDismiss()
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("OK", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = accentColor)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RemindMeInlineDateGrid(
    selectedDate: LocalDate,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
    onDateSelected: (LocalDate) -> Unit,
    primaryTextColor: Color,
    secondaryTextColor: Color
) {
    val startYearMonth = remember { YearMonth.of(1990, 1) }
    val totalMonthsCount = 1332
    val initialMonthIndex = remember(selectedDate) {
        val ym = YearMonth.from(selectedDate)
        ((ym.year - 1990) * 12) + (ym.monthValue - 1)
    }

    val monthListState = rememberLazyListState(initialFirstVisibleItemIndex = initialMonthIndex.coerceIn(0, totalMonthsCount - 1))
    val monthYearFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
    ) {
        LazyColumn(
            state = monthListState,
            modifier = Modifier.fillMaxSize()
        ) {
            items(totalMonthsCount, key = { it }) { monthIndex ->
                val monthYear = startYearMonth.plusMonths(monthIndex.toLong())
                val daysInMonth = monthYear.lengthOfMonth()
                val firstDayOfMonth = monthYear.atDay(1).dayOfWeek.value % 7

                val calendarDays = remember(monthYear) {
                    val days = mutableListOf<LocalDate?>()
                    repeat(firstDayOfMonth) { days.add(null) }
                    for (i in 1..daysInMonth) { days.add(monthYear.atDay(i)) }
                    while (days.size % 7 != 0) { days.add(null) }
                    days.chunked(7)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = monthYear.format(monthYearFormatter),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor,
                        modifier = Modifier
                            .padding(top = 8.dp, bottom = 6.dp)
                            .semantics { heading() }
                    )

                    calendarDays.forEach { week ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                        ) {
                            week.forEach { date ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (date != null) {
                                        val isSelected = date.isEqual(selectedDate)
                                        val isToday = date.isEqual(LocalDate.now())

                                        val isBeforeMin = minDate != null && date.isBefore(minDate)
                                        val isAfterMax = maxDate != null && date.isAfter(maxDate)
                                        val isDisabled = isBeforeMin || isAfterMax

                                        val fullDescription = remember(date, isSelected, isToday) {
                                            val fmt = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.getDefault())
                                            var baseDesc = date.format(fmt)
                                            if (isSelected) baseDesc += ", selected"
                                            if (isToday) baseDesc += ", today"
                                            baseDesc
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .alpha(if (isDisabled) 0.45f else 1.0f)
                                                .semantics { contentDescription = fullDescription }
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                    enabled = !isDisabled
                                                ) {
                                                    onDateSelected(date)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isToday && !isSelected) {
                                                Surface(
                                                    modifier = Modifier.fillMaxSize(),
                                                    shape = CircleShape,
                                                    color = primaryTextColor.copy(alpha = 0.14f)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = date.dayOfMonth.toString(),
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Normal,
                                                            color = primaryTextColor
                                                        )
                                                    }
                                                }
                                            } else if (isSelected) {
                                                Surface(
                                                    modifier = Modifier.fillMaxSize(),
                                                    shape = CircleShape,
                                                    color = Color.Transparent,
                                                    border = BorderStroke(1.dp, primaryTextColor)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = date.dayOfMonth.toString(),
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = primaryTextColor
                                                        )
                                                    }
                                                }
                                            } else {
                                                Text(
                                                    text = date.dayOfMonth.toString(),
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Normal,
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
    }
}
