package com.fabxdi.dibadge.ui.calendar

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.data.ReminderEntity
import com.fabxdi.dibadge.data.LeaveEntity
import com.fabxdi.dibadge.data.OvertimeEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(
    date: LocalDate,
    items: List<Any>,
    onItemClick: (Any) -> Unit,
    onBack: () -> Unit
) {
    BackHandler {
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = date.format(DateTimeFormatter.ofPattern("EEEE")),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = date.format(DateTimeFormatter.ofPattern("dd MMMM yyyy")),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(DiBadgeTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(DiBadgeTheme.spacing.small)
        ) {
            items(items) { item ->
                when (item) {
                    is ReminderEntity -> AgendaItemCard(reminder = item, onClick = { onItemClick(item) })
                    is LeaveEntity -> LeaveItemCard(leave = item, onClick = { onItemClick(item) })
                    is OvertimeEntity -> OvertimeItemCard(overtime = item, onClick = { onItemClick(item) })
                }
            }
        }
    }
}

@Composable
fun OvertimeItemCard(overtime: OvertimeEntity, onClick: () -> Unit) {
    val statusColor = if (overtime.status == "Approved") Color(0xFF4CAF50) else Color(0xFF2196F3)

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.Companion.padding(DiBadgeTheme.spacing.medium)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 24.dp)
                        .background(statusColor, shape = CircleShape)
                )

                Spacer(modifier = Modifier.Companion.width(DiBadgeTheme.spacing.medium))

                Column {
                    Text(
                        text = "Overtime",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "${overtime.startTime.format(DateTimeFormatter.ofPattern("hh:mm a"))} - ${overtime.endTime.format(DateTimeFormatter.ofPattern("hh:mm a"))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun LeaveItemCard(leave: LeaveEntity, onClick: () -> Unit) {
    val statusColor = if (leave.status == "Approved") Color(0xFF4CAF50) else Color(0xFF2196F3)

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.Companion.padding(DiBadgeTheme.spacing.medium)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 24.dp)
                        .background(statusColor, shape = CircleShape)
                )

                Spacer(modifier = Modifier.Companion.width(DiBadgeTheme.spacing.medium))

                Text(
                    text = leave.leaveType,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

private fun getFileName(context: Context, uri: Uri): String {
    return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex != -1 && cursor.moveToFirst()) {
            cursor.getString(nameIndex)
        } else null
    } ?: uri.path?.split("/")?.last() ?: "Unknown File"
}

private fun getFileIcon(context: Context, uri: Uri): ImageVector {
    val mimeType = context.contentResolver.getType(uri)
    return when {
        mimeType?.startsWith("image/") == true -> Icons.Default.Image
        mimeType?.startsWith("video/") == true -> Icons.Default.VideoFile
        mimeType?.startsWith("audio/") == true -> Icons.Default.AudioFile
        mimeType == "application/pdf" -> Icons.Default.PictureAsPdf
        else -> Icons.Default.Description
    }
}

@Composable
fun AgendaItemCard(reminder: ReminderEntity, onClick: () -> Unit) {
    val context = LocalContext.current
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.Companion.padding(DiBadgeTheme.spacing.medium)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Indicator Pill
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 24.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                )

                Spacer(modifier = Modifier.Companion.width(DiBadgeTheme.spacing.medium))

                Column {
                    val displayText = if (reminder.title.isNotBlank()) {
                        reminder.title
                    } else {
                        reminder.content.take(30).let { if (it.length < reminder.content.length) "$it..." else it }
                    }
                    
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1
                    )
                    
                    reminder.time?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}
