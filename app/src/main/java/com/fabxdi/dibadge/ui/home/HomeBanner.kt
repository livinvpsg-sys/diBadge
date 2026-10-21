package com.fabxdi.dibadge.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.getGreetingName
import java.time.LocalTime

@Composable
fun HomeBanner(
    firstName: String,
    nickname: String = "",
    reminderCount: Int = 0,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onMenuClick: () -> Unit = {},
    onQrClick: () -> Unit = {},
    selectedCount: Int = 0,
    onDeleteClick: () -> Unit = {},
    onClearSelection: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val accentColor = diBadgeColors.accent

    val greetingMessage = remember {
        val now = LocalTime.now()
        when (now.hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    val greetingName = remember(firstName, nickname) { getGreetingName(firstName, nickname) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        if (selectedCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClearSelection) {
                        Icon(Icons.Default.Close, contentDescription = "Clear Selection", tint = primaryTextColor)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$selectedCount selected",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryTextColor
                    )
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = colors.error
                    )
                }
            }
        } else {
            // Line 1 & Three-Dot Button (Line 1: 24sp Regular 400)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Hi $greetingName,",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Normal,
                    color = primaryTextColor,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { onMenuClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "Open sliding menu",
                        tint = primaryTextColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Line 2: Today's Tasks Count OR Time-of-day greeting (Plain text, NOT tappable)
            if (reminderCount > 0) {
                val taskWord = if (reminderCount == 1) "task" else "tasks"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        contentDescription = "$reminderCount $taskWord due today"
                    }
                ) {
                    Text(
                        text = "You have",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = secondaryTextColor
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, primaryTextColor),
                        modifier = Modifier
                            .height(26.dp)
                            .widthIn(min = 26.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = if (reminderCount >= 100) "99+" else reminderCount.toString(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryTextColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "$taskWord to do today",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = secondaryTextColor
                    )
                }
            } else {
                Text(
                    text = greetingMessage,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = secondaryTextColor
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Search Tile + QR Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search Tile (52dp high, surface fill, 14dp corners, no outline)
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                color = cardBgColor
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search icon",
                        tint = secondaryTextColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal,
                                color = secondaryTextColor
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal,
                                color = primaryTextColor
                            ),
                            cursorBrush = SolidColor(accentColor),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // QR Tile (52dp square, surface fill, 14dp corners, no outline)
            Surface(
                modifier = Modifier
                    .size(52.dp)
                    .clickable { onQrClick() },
                shape = RoundedCornerShape(14.dp),
                color = cardBgColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.QrCodeScanner,
                        contentDescription = "QR Options",
                        tint = primaryTextColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
