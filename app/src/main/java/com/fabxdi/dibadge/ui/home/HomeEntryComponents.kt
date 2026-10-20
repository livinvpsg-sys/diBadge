package com.fabxdi.dibadge.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.data.HomeEntryEntity
import com.fabxdi.dibadge.util.UserColorUtils
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeEntryCard(
    entry: HomeEntryEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant

    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()) }
    
    // Stable color hash for group ID
    val avatarColor = remember(entry.id) {
        UserColorUtils.getColorForName(entry.id.toString())
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = if (isSelected) primaryTextColor.copy(alpha = 0.10f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: 44dp Avatar Circle
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = avatarColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = entry.title.take(1).uppercase(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Middle: Name and Last Message / Description
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = entry.subtitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right: Time or Selection Indicator
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = primaryTextColor,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text(
                    text = entry.timestamp.format(timeFormatter),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = secondaryTextColor
                )
            }
        }
    }
}
