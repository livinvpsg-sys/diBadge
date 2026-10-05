package com.fabxdi.dibadge.ui.my_tasks.to_do

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindMePickerDialog(
    dueDate: LocalDate?,
    initialTime: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val parsedTime = initialTime?.let {
        try {
            LocalTime.parse(it, DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault()))
        } catch (e: Exception) {
            LocalTime.now()
        }
    } ?: LocalTime.now()

    val timePickerState = rememberTimePickerState(
        initialHour = parsedTime.hour,
        initialMinute = parsedTime.minute
    )
    var isAnalogTime by remember { mutableStateOf(true) }

    fun formatTime(hour: Int, minute: Int): String {
        val formatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())
        return LocalTime.of(hour, minute).format(formatter)
    }

    val selTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
    val isDueDateToday = dueDate == LocalDate.now()
    val isTimeValid = if (isDueDateToday) {
        !selTime.isBefore(LocalTime.now())
    } else {
        true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .wrapContentHeight()
                    .clickable(enabled = false) {},
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // TIME PICKER VIEW
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CompositionLocalProvider(
                            LocalDensity provides Density(
                                density = LocalDensity.current.density,
                                fontScale = 1.0f
                            )
                        ) {
                            if (isAnalogTime) {
                                TimePicker(
                                    state = timePickerState,
                                    colors = TimePickerDefaults.colors(
                                        clockDialColor = MaterialTheme.colorScheme.surfaceVariant,
                                        selectorColor = MaterialTheme.colorScheme.primary,
                                        clockDialSelectedContentColor = MaterialTheme.colorScheme.onPrimary,
                                        clockDialUnselectedContentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            } else {
                                TimeInput(
                                    state = timePickerState,
                                    colors = TimePickerDefaults.colors(
                                        timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        timeSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Bottom Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { isAnalogTime = !isAnalogTime }) {
                            Icon(
                                imageVector = if (isAnalogTime) Icons.Default.Keyboard else Icons.Default.Schedule,
                                contentDescription = "Toggle Time Picker Mode"
                            )
                        }

                        Row {
                            TextButton(onClick = onDismiss) {
                                Text("Cancel", fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            TextButton(
                                onClick = {
                                    val timeStr = formatTime(timePickerState.hour, timePickerState.minute)
                                    onConfirm(timeStr)
                                },
                                enabled = isTimeValid
                            ) {
                                Text("OK", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
