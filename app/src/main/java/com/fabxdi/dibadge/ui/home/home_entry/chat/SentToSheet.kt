package com.fabxdi.dibadge.ui.home.home_entry.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme

data class GroupDeliveryStatus(
    val groupId: String,
    val groupName: String,
    val isDelivered: Boolean = true,
    val deliveredTime: String = "Just now"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SentToSheet(
    targetGroups: List<GroupDeliveryStatus>,
    onRetryGroup: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors
    val isDark = colors.onBackground.red > 0.5f

    val cardColor = if (isDark) Color(0xFF181C21) else Color(0xFFF5F7F9)
    val cardFillColor = if (isDark) Color(0xFF181C21) else Color(0xFFF5F7F9)
    val outlineColor = if (isDark) Color(0xFF2A3037) else Color(0xFFD3D9DF)
    val primaryTextColor = if (isDark) Color(0xFFF0F3F5) else Color(0xFF11161A)
    val secondaryTextColor = if (isDark) Color(0xFF8A9199) else Color(0xFF65707A)
    val errorColor = if (isDark) Color(0xFFE8837A) else Color(0xFFC4433A)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = cardColor,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(width = 36.dp, height = 4.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = outlineColor
                ) {}
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            // Title
            Text(
                text = "Sent to",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = primaryTextColor,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Group List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(targetGroups) { group ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clickable(enabled = !group.isDelivered) { onRetryGroup(group.groupId) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 28dp Rounded-Square Avatar
                        Surface(
                            modifier = Modifier.size(28.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = cardFillColor,
                            border = BorderStroke(1.dp, outlineColor)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = group.groupName.take(1).uppercase(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = primaryTextColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Group Name
                        Text(
                            text = group.groupName,
                            fontSize = 15.sp,
                            color = primaryTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Delivery Status
                        if (group.isDelivered) {
                            Text(
                                text = "Delivered ${group.deliveredTime}",
                                fontSize = 12.sp,
                                color = secondaryTextColor
                            )
                        } else {
                            Text(
                                text = "Not delivered. Tap to try again",
                                fontSize = 12.sp,
                                color = errorColor
                            )
                        }
                    }
                }
            }
        }
    }
}
