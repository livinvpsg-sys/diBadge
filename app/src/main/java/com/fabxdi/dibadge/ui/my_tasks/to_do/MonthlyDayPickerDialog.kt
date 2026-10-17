package com.fabxdi.dibadge.ui.my_tasks.to_do

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyDayPickerDialog(
    initialDayNumbers: Set<Int>,
    onDaysSelected: (Set<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedDayNums = remember { mutableStateListOf<Int>().apply { addAll(initialDayNumbers) } }
    val dayNumbers = remember { (1..31).toList() }

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val sheetBgColor = colors.surface
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val tealAccent = diBadgeColors.accent

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = sheetBgColor,
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
                text = "Monthly",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = primaryTextColor
            )

            Spacer(modifier = Modifier.height(20.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(dayNumbers) { dayNum ->
                    val isSelected = selectedDayNums.contains(dayNum)
                    Surface(
                        modifier = Modifier
                            .size(38.dp)
                            .clickable {
                                if (isSelected) {
                                    if (selectedDayNums.size > 1) {
                                        selectedDayNums.remove(dayNum)
                                    }
                                } else {
                                    selectedDayNums.add(dayNum)
                                }
                            },
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = if (isSelected) BorderStroke(1.dp, primaryTextColor) else null
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = dayNum.toString(),
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
                TextButton(onClick = onDismiss) {
                    Text("Cancel", fontSize = 15.sp, fontWeight = FontWeight.Normal, color = secondaryTextColor)
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        onDaysSelected(selectedDayNums.toSet())
                        onDismiss()
                    },
                    enabled = selectedDayNums.isNotEmpty()
                ) {
                    Text("Done", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = tealAccent)
                }
            }
        }
    }
}
