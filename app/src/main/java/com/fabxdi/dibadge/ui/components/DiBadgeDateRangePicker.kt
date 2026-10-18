package com.fabxdi.dibadge.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.ui.theme.disableBottomSheetDrag
import kotlinx.coroutines.launch
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

    val surfaceColor = MaterialTheme.colorScheme.surface
    val outlineColor = MaterialTheme.colorScheme.outline
    val primaryTextColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = DiBadgeTheme.colors.accent
    val dragHandleColor = DiBadgeTheme.colors.tabInactive
    val inRangeBandColor = outlineColor

    val monthYearFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }

    var showYearPicker by remember { mutableStateOf(false) }

    // Range of YearMonths: 1990-01 to 2100-12 (1332 months)
    val startYearMonth = remember { YearMonth.of(1990, 1) }
    val totalMonthsCount = 1332
    val initialMonthIndex = remember(initialPickerMonth) {
        ((initialPickerMonth.year - 1990) * 12) + (initialPickerMonth.monthValue - 1)
    }

    val monthListState = rememberLazyListState(initialFirstVisibleItemIndex = initialMonthIndex.coerceIn(0, totalMonthsCount - 1))
    val coroutineScope = rememberCoroutineScope()

    // Year in the header control tracks top visible month in scrolling list
    val currentTopMonthIndex by remember { derivedStateOf { monthListState.firstVisibleItemIndex } }
    val currentTopYearMonth = remember(currentTopMonthIndex) {
        startYearMonth.plusMonths(currentTopMonthIndex.coerceIn(0, totalMonthsCount - 1).toLong())
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
            // 1. Label Row (Left: "Pick start date" / "Pick end date", Right: "7 days")
            val dynamicTitle = when {
                effectiveStart == null -> "Pick start date"
                effectiveEnd == null -> "Pick end date"
                else -> ""
            }

            val totalDays = if (effectiveStart != null && effectiveEnd != null) {
                ChronoUnit.DAYS.between(effectiveStart, effectiveEnd) + 1
            } else null

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dynamicTitle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = secondaryTextColor
                )

                if (totalDays != null) {
                    Text(
                        text = if (totalDays == 1L) "1 day" else "$totalDays days",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 2. Value Line + Year Control Row ("28 Oct – 3 Nov" or "28 Dec 2026 – 3 Jan 2027" and "2026 ▾")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                val isCrossYear = effectiveStart != null && effectiveEnd != null && effectiveStart.year != effectiveEnd.year

                val startFormatter = remember(effectiveStart, effectiveEnd) {
                    if (isCrossYear) DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
                    else DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
                }
                val endFormatter = remember(effectiveStart, effectiveEnd) {
                    if (isCrossYear) DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
                    else DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
                }

                val startText = effectiveStart?.format(startFormatter) ?: "Start date"
                val endText = effectiveEnd?.format(endFormatter) ?: "End date"

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = startText,
                        fontSize = if (isCrossYear) 20.sp else if (effectiveStart == null && effectiveEnd == null) 22.sp else 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (effectiveStart != null) primaryTextColor else secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = " – ",
                        fontSize = if (isCrossYear) 20.sp else if (effectiveStart == null && effectiveEnd == null) 22.sp else 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (effectiveStart != null && effectiveEnd != null) primaryTextColor else secondaryTextColor
                    )
                    Text(
                        text = endText,
                        fontSize = if (isCrossYear) 20.sp else if (effectiveStart == null && effectiveEnd == null) 22.sp else 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (effectiveEnd != null) primaryTextColor else secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Year Control Button on the right ("2026 ▾")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .widthIn(min = 64.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            showYearPicker = !showYearPicker
                        }
                        .semantics {
                            contentDescription = "Select year, ${currentTopYearMonth.year}"
                        },
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = currentTopYearMonth.year.toString(),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (showYearPicker) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = primaryTextColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(thickness = 1.dp, color = outlineColor)

            Column(modifier = Modifier.disableBottomSheetDrag()) {
                Spacer(modifier = Modifier.height(12.dp))

                // 3. Fixed Weekday Row (S M T W T F S)
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

                // 4. Scrolling Content Area (Fixed Height 330dp with Bottom Gradient Mask Fade)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(330.dp)
                ) {
                if (showYearPicker) {
                    // Vertical Year Picker Strip (1990 to 2100)
                    val years = remember { (1990..2100).toList() }
                    val currentYear = currentTopYearMonth.year
                    val currentYearIndex = remember(currentYear) {
                        years.indexOf(currentYear).coerceAtLeast(0)
                    }
                    val yearListState = rememberLazyListState(initialFirstVisibleItemIndex = (currentYearIndex - 2).coerceAtLeast(0))

                    LazyColumn(
                        state = yearListState,
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
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
                                        val newYM = YearMonth.of(yearVal, currentTopYearMonth.monthValue)
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
                    // Vertical Scrollable Month List (Continuous Scrolling Month Titles)
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
                                // Continuous Scrolling Month Title
                                Text(
                                    text = monthYear.format(monthYearFormatter),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryTextColor,
                                    modifier = Modifier
                                        .padding(top = 10.dp, bottom = 6.dp)
                                        .semantics { heading() }
                                )

                                // Days Grid
                                calendarDays.forEach { week ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                    ) {
                                        week.forEachIndexed { dayIndex, date ->
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (date != null) {
                                                    val isStart = effectiveStart != null && date.isEqual(effectiveStart)
                                                    val isEnd = effectiveEnd != null && date.isEqual(effectiveEnd)
                                                    val isSameDay = effectiveStart != null && effectiveEnd != null && effectiveStart.isEqual(effectiveEnd)
                                                    val isRangeActive = effectiveStart != null && effectiveEnd != null && !isSameDay

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

                                                            val drawLeftBand = !isStartCell && dayIndex != 0 && date.dayOfMonth != 1
                                                            val drawRightBand = !isEndCell && dayIndex != 6 && !date.isEqual(monthYear.atEndOfMonth())

                                                            if (drawLeftBand) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .height(38.dp)
                                                                        .fillMaxWidth(0.5f)
                                                                        .align(Alignment.CenterStart)
                                                                        .background(inRangeBandColor)
                                                                )
                                                            }

                                                            if (drawRightBand) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .height(38.dp)
                                                                        .fillMaxWidth(0.5f)
                                                                        .align(Alignment.CenterEnd)
                                                                        .background(inRangeBandColor)
                                                                )
                                                            }

                                                            Box(
                                                                modifier = Modifier
                                                                    .size(38.dp)
                                                                    .align(Alignment.Center)
                                                                    .background(inRangeBandColor, CircleShape)
                                                            )
                                                        }
                                                    }

                                                    // Day Circle / Cell
                                                    val fullDescription = remember(date, isStart, isEnd) {
                                                        val fmt = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())
                                                        val baseDesc = date.format(fmt)
                                                        when {
                                                            isStart && isEnd -> "$baseDesc, start and end of range"
                                                            isStart -> "$baseDesc, start of range"
                                                            isEnd -> "$baseDesc, end of range"
                                                            else -> baseDesc
                                                        }
                                                    }

                                                    Box(
                                                        modifier = Modifier
                                                            .size(38.dp)
                                                            .alpha(if (isDisabled) 0.45f else 1.0f)
                                                            .semantics { contentDescription = fullDescription }
                                                            .clickable(
                                                                interactionSource = remember { MutableInteractionSource() },
                                                                indication = null,
                                                                enabled = !isDisabled
                                                            ) {
                                                                if (startDate == null || (startDate != null && endDate != null)) {
                                                                    startDate = date
                                                                    endDate = null
                                                                } else {
                                                                    if (date.isBefore(startDate)) {
                                                                        startDate = date
                                                                        endDate = null
                                                                    } else {
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

                    // Gradient Overlay Mask: Bottom 20% (66dp of 330dp) fades into sheet surface color
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(66.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        surfaceColor.copy(alpha = 0f),
                                        surfaceColor
                                    )
                                )
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Bottom Row (OK on right)
            Row(
                modifier = Modifier.fillMaxWidth(),
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
}
