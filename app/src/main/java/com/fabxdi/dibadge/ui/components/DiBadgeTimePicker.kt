package com.fabxdi.dibadge.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiBadgeTimePicker(
    initialTime: LocalTime? = null,
    minTime: LocalTime? = null,
    maxTime: LocalTime? = null,
    minuteStep: Int = 1,
    title: String = "Select time",
    onTimeSelected: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val is24HourFormat = remember(context) { DateFormat.is24HourFormat(context) }

    val startLocalTime = initialTime ?: LocalTime.now()

    var selectedHour24 by remember { mutableIntStateOf(startLocalTime.hour) }
    var selectedMinute by remember { mutableIntStateOf((startLocalTime.minute / minuteStep) * minuteStep) }

    // Live Clock for "Now" header
    var nowTime by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowTime = LocalTime.now()
            delay(1000L * 60)
        }
    }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val outlineColor = MaterialTheme.colorScheme.outline
    val primaryTextColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = DiBadgeTheme.colors.accent
    val dragHandleColor = DiBadgeTheme.colors.tabInactive

    // Current clock time string
    val nowTimeFormatted = remember(nowTime, is24HourFormat) {
        if (is24HourFormat) {
            nowTime.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
        } else {
            nowTime.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
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
            // 1. Title ("Select time")
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = secondaryTextColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 2. Top Line: Current Clock Time + "Now"
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = nowTimeFormatted,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryTextColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Now",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal,
                    color = secondaryTextColor,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(thickness = 1.dp, color = outlineColor)

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Wheels Container (5 rows visible, 48dp row height = 240dp total height)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (is24HourFormat) {
                    // 24h Mode: Hours (00-23)
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

                    Text(
                        text = ":",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Minutes (00-59 in steps)
                    val minutesList = remember(minuteStep) {
                        (0..59 step minuteStep).map { String.format(Locale.getDefault(), "%02d", it) }
                    }
                    val selectedMinIndex = (selectedMinute / minuteStep).coerceIn(0, minutesList.lastIndex)

                    WheelPicker(
                        items = minutesList,
                        selectedIndex = selectedMinIndex,
                        onSelectedIndexChanged = { selectedMinute = it * minuteStep },
                        modifier = Modifier.width(80.dp),
                        primaryColor = primaryTextColor,
                        contentDescriptionLabel = "Minute"
                    )
                } else {
                    // 12h Mode: Hours (12, 01..11)
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

                    Text(
                        text = ":",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Minutes (00-59)
                    val minutesList = remember(minuteStep) {
                        (0..59 step minuteStep).map { String.format(Locale.getDefault(), "%02d", it) }
                    }
                    val selectedMinIndex = (selectedMinute / minuteStep).coerceIn(0, minutesList.lastIndex)

                    WheelPicker(
                        items = minutesList,
                        selectedIndex = selectedMinIndex,
                        onSelectedIndexChanged = { selectedMinute = it * minuteStep },
                        modifier = Modifier.width(72.dp),
                        primaryColor = primaryTextColor,
                        contentDescriptionLabel = "Minute"
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    // AM / PM Wheel
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

            Spacer(modifier = Modifier.height(24.dp))

            // 4. Bottom Row Action Button: OK on right
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
                        onTimeSelected(chosenTime)
                        onDismiss()
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
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

@Composable
fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChanged: (Int) -> Unit,
    isInfinite: Boolean = true,
    modifier: Modifier = Modifier,
    primaryColor: Color,
    contentDescriptionLabel: String = ""
) {
    val itemCount = items.size
    val totalCount = if (isInfinite) itemCount * 1000 else itemCount
    val targetIndex = if (isInfinite) {
        val middleBase = (totalCount / 2) - ((totalCount / 2) % itemCount)
        val validSelected = selectedIndex.coerceIn(0, itemCount - 1)
        middleBase + validSelected
    } else {
        selectedIndex.coerceIn(0, itemCount - 1)
    }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = targetIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    // Center item is the visible item closest to viewport center
    val centerIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                targetIndex
            } else {
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                visibleItems.minByOrNull { item ->
                    Math.abs((item.offset + item.size / 2) - viewportCenter)
                }?.index ?: targetIndex
            }
        }
    }

    LaunchedEffect(centerIndex) {
        val actualIndex = (centerIndex % itemCount + itemCount) % itemCount
        if (actualIndex in items.indices && actualIndex != selectedIndex) {
            onSelectedIndexChanged(actualIndex)
        }
    }

    Box(
        modifier = modifier.height(240.dp),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(totalCount) { index ->
                val actualIndex = (index % itemCount + itemCount) % itemCount
                val distance = Math.abs(index - centerIndex)

                val (fontSize, alphaVal, fontW) = when (distance) {
                    0 -> Tuple3(38.sp, 1.0f, FontWeight.Bold)
                    1 -> Tuple3(22.sp, 0.5f, FontWeight.Normal)
                    else -> Tuple3(18.sp, 0.2f, FontWeight.Normal)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onSelectedIndexChanged(actualIndex)
                        }
                        .semantics {
                            contentDescription = if (contentDescriptionLabel.isNotBlank()) {
                                "$contentDescriptionLabel, ${items[actualIndex]}"
                            } else {
                                items[actualIndex]
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = items[actualIndex],
                        fontSize = fontSize,
                        fontWeight = fontW,
                        color = primaryColor.copy(alpha = alphaVal)
                    )
                }
            }
        }
    }
}

private data class Tuple3<A, B, C>(val a: A, val b: B, val c: C)
