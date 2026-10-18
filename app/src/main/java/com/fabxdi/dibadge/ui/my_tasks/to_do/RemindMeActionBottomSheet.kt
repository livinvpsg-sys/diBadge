package com.fabxdi.dibadge.ui.my_tasks.to_do

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindMeActionBottomSheet(
    selectedTime: String?,
    onPickTimeSelect: () -> Unit,
    onRemoveTimeSelect: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val sheetBgColor = colors.surface
    val primaryTextColor = colors.onBackground

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
                .padding(bottom = 24.dp)
        ) {
            ListItem(
                headlineContent = { Text("Pick time", color = primaryTextColor) },
                leadingContent = { Icon(Icons.Outlined.Notifications, contentDescription = null, tint = primaryTextColor) },
                modifier = Modifier.clickable { onPickTimeSelect() },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            if (selectedTime != null) {
                ListItem(
                    headlineContent = { Text("Remove time", color = colors.error) },
                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = colors.error) },
                    modifier = Modifier.clickable { onRemoveTimeSelect() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}
