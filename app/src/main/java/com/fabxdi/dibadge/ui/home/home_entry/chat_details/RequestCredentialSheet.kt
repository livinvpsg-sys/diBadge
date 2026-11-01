package com.fabxdi.dibadge.ui.home.home_entry.chat_details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.viewmodel.GroupMember

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestCredentialSheet(
    members: List<GroupMember>,
    isDark: Boolean,
    onRequestCreated: (certName: String, selectedMembers: List<GroupMember>) -> Unit,
    onDismiss: () -> Unit
) {
    var certNameInput by remember { mutableStateOf("") }

    val otherMembers = remember(members) { members.filter { !it.isYou } }
    val selectedMembers = remember { mutableStateListOf<GroupMember>() }

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val cardFillColor = if (isDark) Color(0xFF222831) else Color(0xFFDDE2E7)
    val outlineColor = colors.outline
    val tealAccent = diBadgeColors.accent
    val dimTextColor = if (isDark) Color(0xFF6E757C) else Color(0xFF8A929A)

    val isAllSelected = remember(selectedMembers.size, otherMembers.size) {
        otherMembers.isNotEmpty() && selectedMembers.size == otherMembers.size
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
                    .padding(top = 10.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(width = 36.dp, height = 4.dp),
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
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Request credentials",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = primaryTextColor,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Field: What do you need?
            Text(
                text = "What do you need?",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = secondaryTextColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(contentAlignment = Alignment.CenterStart) {
                if (certNameInput.isEmpty()) {
                    Text(
                        text = "Safety credentials",
                        fontSize = 15.sp,
                        color = dimTextColor
                    )
                }
                BasicTextField(
                    value = certNameInput,
                    onValueChange = { certNameInput = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp, color = primaryTextColor),
                    cursorBrush = SolidColor(primaryTextColor),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                thickness = 1.dp,
                color = outlineColor
            )

            // Members Header: Members list + Select All
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Members (${selectedMembers.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = secondaryTextColor
                )

                Text(
                    text = if (isAllSelected) "Deselect all" else "Select all",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = tealAccent,
                    modifier = Modifier.clickable {
                        if (isAllSelected) {
                            selectedMembers.clear()
                        } else {
                            selectedMembers.clear()
                            selectedMembers.addAll(otherMembers)
                        }
                    }
                )
            }

            // Members List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(otherMembers, key = { it.id }) { member ->
                    val isSelected = selectedMembers.contains(member)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isSelected) selectedMembers.remove(member) else selectedMembers.add(member)
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = cardFillColor,
                        border = BorderStroke(1.dp, outlineColor)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(28.dp),
                                shape = CircleShape,
                                color = cardBgColor,
                                border = BorderStroke(1.dp, outlineColor)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = member.displayName.take(1).uppercase(),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryTextColor
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Text(
                                text = member.displayName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = primaryTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            if (isSelected) {
                                Surface(
                                    modifier = Modifier.size(20.dp),
                                    shape = CircleShape,
                                    color = tealAccent
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = if (isDark) Color(0xFF0E1114) else Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .border(BorderStroke(1.5.dp, outlineColor), CircleShape)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", fontSize = 15.sp, color = secondaryTextColor)
                }

                Spacer(modifier = Modifier.width(12.dp))

                val canSend = certNameInput.isNotBlank() && selectedMembers.isNotEmpty()
                TextButton(
                    enabled = canSend,
                    onClick = {
                        if (canSend) {
                            onRequestCreated(certNameInput.trim(), selectedMembers.toList())
                        }
                    }
                ) {
                    Text(
                        text = "Request",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (canSend) tealAccent else dimTextColor
                    )
                }
            }
        }
    }
}
