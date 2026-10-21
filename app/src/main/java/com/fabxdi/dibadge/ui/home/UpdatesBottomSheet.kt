package com.fabxdi.dibadge.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesBottomSheet(
    taskCount: Int = 0,
    requestsCount: Int = 0,
    messagesCount: Int = 0,
    onTasksClick: () -> Unit,
    onRequestsClick: () -> Unit,
    onMessagesClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline

    val activeItems = mutableListOf<Triple<String, Int, () -> Unit>>()
    if (taskCount > 0) {
        activeItems.add(Triple("Tasks due today", taskCount, onTasksClick))
    }
    if (requestsCount > 0) {
        activeItems.add(Triple("Connection requests", requestsCount, onRequestsClick))
    }
    if (messagesCount > 0) {
        activeItems.add(Triple("Inbox messages", messagesCount, onMessagesClick))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = cardBgColor,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(width = 32.dp, height = 4.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = secondaryTextColor.copy(alpha = 0.40f)
                ) {}
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 28.dp)
        ) {
            activeItems.forEachIndexed { index, (label, count, onClick) ->
                val iconRes = when (label) {
                    "Tasks due today" -> R.drawable.ic_my_tasks
                    "Connection requests" -> R.drawable.ic_connections
                    else -> R.drawable.ic_inbox
                }

                val rowDesc = "$label, $count. Double tap to open"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable {
                            onDismiss()
                            onClick()
                        }
                        .padding(horizontal = 20.dp)
                        .semantics { contentDescription = rowDesc },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = null,
                            tint = secondaryTextColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = label,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            color = primaryTextColor
                        )
                    }

                    Text(
                        text = if (count >= 100) "99+" else count.toString(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor
                    )
                }

                if (index < activeItems.lastIndex) {
                    HorizontalDivider(thickness = 1.dp, color = outlineColor)
                }
            }
        }
    }
}
