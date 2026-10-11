package com.fabxdi.dibadge.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.components.DiBadgeSearchBar
import com.fabxdi.dibadge.ui.theme.BadgeRed
import com.fabxdi.dibadge.util.getGreetingName
import java.time.LocalTime

@Composable
fun HomeBanner(
    firstName: String,
    nickname: String = "",
    notificationCount: Int,
    reminderCount: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onReminderClick: () -> Unit,
    onMenuClick: () -> Unit,
    onQrClick: () -> Unit,
    selectedCount: Int = 0,
    onDeleteClick: () -> Unit = {},
    onClearSelection: () -> Unit = {}
) {
    val greetingMessage = remember {
        val now = LocalTime.now()
        when (now.hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        if (selectedCount > 0) {
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClearSelection) {
                        Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$selectedCount selected",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val greetingName = remember(firstName, nickname) { getGreetingName(firstName, nickname) }
                Text(
                    text = if (notificationCount > 0 || reminderCount > 0) "Hi $greetingName you have" else "Hi $greetingName,",
                    style = if (notificationCount > 0 || reminderCount > 0) {
                        MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Normal)
                    } else {
                        MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Normal,
                            fontSize = 18.sp,
                            lineHeight = 20.sp
                        )
                    },
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.offset(x = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menu",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        if (selectedCount == 0) {
            Column(
                modifier = Modifier.padding(top = 2.dp)
            ) {
                if (notificationCount > 0 || reminderCount > 0) {
                    if (reminderCount > 0) {
                        TaskCountRow(reminderCount = reminderCount)
                    }
                    if (notificationCount > 0) {
                        if (reminderCount > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        Text(
                            text = "$notificationCount badge notification",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Normal,
                                fontSize = 18.sp,
                                lineHeight = 20.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                } else {
                    Text(
                        text = greetingMessage,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Normal,
                            fontSize = 18.sp,
                            lineHeight = 20.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DiBadgeSearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                placeholder = "Search..",
                modifier = Modifier.weight(1f)
            )
            
            IconButton(onClick = onQrClick) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Scan QR",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun BadgeInfo(label: String, count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            color = BadgeRed,
            shape = CircleShape,
            modifier = Modifier.size(18.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun TaskCountRow(
    reminderCount: Int,
    modifier: Modifier = Modifier
) {
    val countText = remember(reminderCount) {
        if (reminderCount >= 100) "99+" else reminderCount.toString()
    }
    val labelText = remember(reminderCount) {
        if (reminderCount == 1) "task to do" else "tasks to do"
    }
    val accessibilityDescription = "$reminderCount $labelText"

    val tealGradient = remember {
        Brush.linearGradient(
            colors = listOf(Color(0xFF5CC9C0), Color(0xFF17645E))
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = accessibilityDescription
        }
    ) {
        // Teal Gradient Filled Circle/Pill
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(28.dp)
                .widthIn(min = 28.dp)
                .background(brush = tealGradient, shape = CircleShape)
                .padding(horizontal = 8.dp)
        ) {
            Text(
                text = countText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFF00201D)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Text
        Text(
            text = labelText,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Normal
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
