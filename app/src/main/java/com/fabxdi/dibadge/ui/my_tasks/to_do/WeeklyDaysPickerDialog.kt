package com.fabxdi.dibadge.ui.my_tasks.to_do

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import java.time.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyDaysPickerDialog(
    initialDays: Set<DayOfWeek>,
    onDaysSelected: (Set<DayOfWeek>) -> Unit,
    onDismiss: () -> Unit,
) {
    val daysOfWeek = listOf(
        DayOfWeek.SUNDAY to "Sunday",
        DayOfWeek.MONDAY to "Monday",
        DayOfWeek.TUESDAY to "Tuesday",
        DayOfWeek.WEDNESDAY to "Wednesday",
        DayOfWeek.THURSDAY to "Thursday",
        DayOfWeek.FRIDAY to "Friday",
        DayOfWeek.SATURDAY to "Saturday"
    )
    val selectedDays = remember { mutableStateListOf<DayOfWeek>().apply { addAll(initialDays) } }

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val tealAccent = diBadgeColors.accent

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        shape = MaterialTheme.shapes.extraLarge,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = colors.outline)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "Weekly",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = primaryTextColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            HorizontalDivider(thickness = 1.dp, color = colors.outline)

            Spacer(modifier = Modifier.height(4.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                daysOfWeek.forEach { (dayOfWeek, fullName) ->
                    val isSelected = selectedDays.contains(dayOfWeek)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .clickable {
                                if (isSelected) {
                                    if (selectedDays.size > 1) {
                                        selectedDays.remove(dayOfWeek)
                                    }
                                } else {
                                    selectedDays.add(dayOfWeek)
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = fullName,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = primaryTextColor
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = tealAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        onDaysSelected(selectedDays.toSet())
                        onDismiss()
                    },
                    enabled = selectedDays.isNotEmpty()
                ) {
                    Text("OK", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = tealAccent)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
