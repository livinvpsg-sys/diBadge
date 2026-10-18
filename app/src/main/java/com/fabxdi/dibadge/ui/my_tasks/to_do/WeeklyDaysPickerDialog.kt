package com.fabxdi.dibadge.ui.my_tasks.to_do

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    onDismiss: () -> Unit
) {
    val daysOfWeek = listOf(
        DayOfWeek.SUNDAY to "S",
        DayOfWeek.MONDAY to "M",
        DayOfWeek.TUESDAY to "T",
        DayOfWeek.WEDNESDAY to "W",
        DayOfWeek.THURSDAY to "T",
        DayOfWeek.FRIDAY to "F",
        DayOfWeek.SATURDAY to "S"
    )
    val selectedDays = remember { mutableStateListOf<DayOfWeek>().apply { addAll(initialDays) } }

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val tealAccent = diBadgeColors.accent

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = colors.surface,
        shape = MaterialTheme.shapes.extraLarge,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = colors.outline)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Weekly",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = primaryTextColor
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 7 Round Day Chips (S M T W T F S, 38dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                daysOfWeek.forEach { (dayOfWeek, chipLabel) ->
                    val isSelected = selectedDays.contains(dayOfWeek)
                    Surface(
                        modifier = Modifier
                            .size(38.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (isSelected) {
                                    if (selectedDays.size > 1) {
                                        selectedDays.remove(dayOfWeek)
                                    }
                                } else {
                                    selectedDays.add(dayOfWeek)
                                }
                            },
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = if (isSelected) BorderStroke(1.dp, primaryTextColor) else null
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = chipLabel,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) primaryTextColor else secondaryTextColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

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
        }
    }
}
