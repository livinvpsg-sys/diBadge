package com.fabxdi.dibadge.ui.my_tasks.to_do

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Month

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearlyDatePickerDialog(
    initialMonth: Month,
    initialDayNumber: Int,
    onDateSelected: (Month, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMonth by remember { mutableStateOf(initialMonth) }
    var selectedDayNum by remember { mutableIntStateOf(initialDayNumber) }

    val months = remember { Month.entries.toList() }
    val dayNumbers = remember { (1..31).toList() }

    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val sheetBgColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)
    val primaryTextColor = if (isDark) Color(0xFFFFFFFF) else Color(0xFF111318)
    val secondaryTextColor = if (isDark) Color(0xFF8A8A8E) else Color(0xFF6B7078)
    val tealAccent = if (isDark) Color(0xFF5CC9C0) else Color(0xFF17645E)

    // Max valid days in selected month (Feb allows up to 29; Apr, Jun, Sep, Nov up to 30)
    val maxValidDays = remember(selectedMonth) {
        when (selectedMonth) {
            Month.FEBRUARY -> 29
            Month.APRIL, Month.JUNE, Month.SEPTEMBER, Month.NOVEMBER -> 30
            else -> 31
        }
    }

    // Auto-adjust selected day if switching to a month with fewer days
    LaunchedEffect(selectedMonth) {
        if (selectedDayNum > maxValidDays) {
            selectedDayNum = maxValidDays
        }
    }

    // LazyRow state for month strip
    val monthListState = rememberLazyListState()
    LaunchedEffect(Unit) {
        monthListState.scrollToItem(initialMonth.ordinal)
    }

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
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            // 1. Heading "Yearly"
            Text(
                text = "Yearly",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = primaryTextColor,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Month Strip: One single horizontal row (Jan - Dec, 64dp wide x 36dp high, 8dp gap, 24dp side padding)
            LazyRow(
                state = monthListState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(months) { _, month ->
                    val isSelected = selectedMonth == month
                    val monthName = month.name.lowercase().replaceFirstChar { it.uppercase() }
                    val monthAbbrev = monthName.take(3)

                    Box(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .semantics(mergeDescendants = true) {
                                contentDescription = if (isSelected) "$monthName, selected" else monthName
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier
                                .size(width = 64.dp, height = 36.dp)
                                .clickable { selectedMonth = month },
                            shape = RoundedCornerShape(18.dp),
                            color = Color.Transparent,
                            border = if (isSelected) BorderStroke(1.dp, primaryTextColor) else null
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = monthAbbrev,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) primaryTextColor else secondaryTextColor
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Day-of-Month Grid: 1 to 31, 7 columns, 38dp circles, 8dp row gap, 24dp side padding
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(dayNumbers) { dayNum ->
                    val isSelected = selectedDayNum == dayNum
                    val isEnabled = dayNum <= maxValidDays
                    val selectedMonthName = selectedMonth.name.lowercase().replaceFirstChar { it.uppercase() }

                    Surface(
                        modifier = Modifier
                            .size(38.dp)
                            .alpha(if (isEnabled) 1.0f else 0.38f)
                            .clickable(enabled = isEnabled) { selectedDayNum = dayNum }
                            .semantics {
                                contentDescription = "$dayNum $selectedMonthName"
                            },
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = if (isSelected && isEnabled) BorderStroke(1.dp, primaryTextColor) else null
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = dayNum.toString(),
                                fontSize = 15.sp,
                                fontWeight = if (isSelected && isEnabled) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected && isEnabled) primaryTextColor else secondaryTextColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Action Row: Cancel and Done plain text buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 30.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Cancel",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = secondaryTextColor
                    )
                }
                Spacer(modifier = Modifier.width(28.dp))
                TextButton(
                    onClick = {
                        onDateSelected(selectedMonth, selectedDayNum)
                        onDismiss()
                    }
                ) {
                    Text(
                        text = "Done",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tealAccent
                    )
                }
            }
        }
    }
}
