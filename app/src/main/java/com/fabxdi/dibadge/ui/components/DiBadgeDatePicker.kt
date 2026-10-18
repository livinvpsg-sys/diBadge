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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.ui.theme.disableBottomSheetDrag
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiBadgeDatePicker(
    selectedDate: LocalDate? = null,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
    disabledDates: Set<LocalDate> = emptySet(),
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

    // Year in the header control tracks top visible month in scrolling list
    val currentTopMonthIndex by remember { derivedStateOf { monthListState.firstVisibleItemIndex } }
    val currentTopYearMonth = remember(currentTopMonthIndex) {
        startYearMonth.plusMonths(currentTopMonthIndex.coerceIn(0, totalMonthsCount - 1).toLong())
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
            // 1. Label Row ("Select date")
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = secondaryTextColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 2. Value Line + Year Control Row ("Fri, 23 Oct" and "2026 ▾")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = tempSelectedDate.format(headlineFormatter),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryTextColor,
                    modifier = Modifier.weight(1f, fill = false)
                )

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
                                        week.forEach { date ->
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (date != null) {
                                                    val isSelected = date.isEqual(tempSelectedDate)

                                                    val isBeforeMin = minDate != null && date.isBefore(minDate)
                                                    val isAfterMax = maxDate != null && date.isAfter(maxDate)
                                                    val isCustomDisabled = disabledDates.contains(date)
                                                    val isDisabled = isBeforeMin || isAfterMax || isCustomDisabled

                                                    val fullDescription = remember(date, isSelected) {
                                                        val fmt = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())
                                                        val baseDesc = date.format(fmt)
                                                        if (isSelected) "$baseDesc, selected" else baseDesc
                                                    }

                                                    Box(
                                                        modifier = Modifier
                                                            .size(40.dp)
                                                            .alpha(if (isDisabled) 0.45f else 1.0f)
                                                            .semantics { contentDescription = fullDescription }
                                                            .clickable(
                                                                interactionSource = remember { MutableInteractionSource() },
                                                                indication = null,
                                                                enabled = !isDisabled
                                                            ) {
                                                                tempSelectedDate = date
                                                                onDateSelected(date)
                                                                onDismiss()
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
        }
        }
    }
}
