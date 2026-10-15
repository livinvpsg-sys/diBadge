package com.fabxdi.dibadge.ui.my_tasks.to_do

import androidx.compose.runtime.Composable
import com.fabxdi.dibadge.ui.components.DiBadgeTimePicker
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun RemindMePickerDialog(
    dueDate: LocalDate?,
    initialTime: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val startLocalTime = initialTime?.let {
        try {
            LocalTime.parse(it, DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault()))
        } catch (e: Exception) {
            LocalTime.now()
        }
    } ?: LocalTime.now()

    DiBadgeTimePicker(
        initialTime = startLocalTime,
        title = "Select time",
        onTimeSelected = { chosenTime ->
            val timeStr = chosenTime.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault()))
            onConfirm(timeStr)
        },
        onDismiss = onDismiss
    )
}
