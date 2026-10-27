package com.fabxdi.dibadge.ui.home.home_entry.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.viewmodel.GroupConnectionItem
import com.fabxdi.dibadge.viewmodel.GroupMember

enum class MentionPickerMode {
    MEMBERS, LINKED_GROUPS
}

@Composable
fun MentionPickerCard(
    mode: MentionPickerMode,
    members: List<GroupMember>,
    linkedGroups: List<GroupConnectionItem>,
    isDark: Boolean,
    cardColor: Color,
    outlineColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    dimTextColor: Color,
    onTabSelected: (MentionPickerMode) -> Unit,
    onSelectMember: (GroupMember?) -> Unit,
    onSelectLinkedGroup: (GroupConnectionItem?) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.heightIn(max = 260.dp),
        shape = RoundedCornerShape(20.dp),
        color = cardColor,
        border = BorderStroke(1.dp, outlineColor),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Two-tab row: Members | Groups
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
            ) {
                // Members Tab
                val isMembersSelected = mode == MentionPickerMode.MEMBERS
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onTabSelected(MentionPickerMode.MEMBERS) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = "Members",
                            fontSize = 14.sp,
                            fontWeight = if (isMembersSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isMembersSelected) primaryTextColor else dimTextColor
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(if (isMembersSelected) primaryTextColor else Color.Transparent)
                        )
                    }
                }

                // Groups Tab
                val isGroupsSelected = mode == MentionPickerMode.LINKED_GROUPS
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onTabSelected(MentionPickerMode.LINKED_GROUPS) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = "Groups",
                            fontSize = 14.sp,
                            fontWeight = if (isGroupsSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isGroupsSelected) primaryTextColor else dimTextColor
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(if (isGroupsSelected) primaryTextColor else Color.Transparent)
                        )
                    }
                }
            }

            // 1dp outline divider under tab row
            HorizontalDivider(color = outlineColor, thickness = 1.dp)

            // Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (mode == MentionPickerMode.MEMBERS) {
                    if (members.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No other members yet",
                                fontSize = 14.sp,
                                color = secondaryTextColor,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        // Row 0: All
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clickable { onSelectMember(null) }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(30.dp),
                                shape = CircleShape,
                                color = cardColor,
                                border = BorderStroke(1.dp, outlineColor)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "A",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = primaryTextColor
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = "All",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = primaryTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Individual members
                        members.forEach { member ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .clickable { onSelectMember(member) }
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(30.dp),
                                    shape = CircleShape,
                                    color = cardColor,
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

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                    text = member.displayName,
                                    fontSize = 15.sp,
                                    color = primaryTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                } else {
                    if (linkedGroups.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No groups yet",
                                fontSize = 14.sp,
                                color = secondaryTextColor,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        // Row 0: All
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clickable { onSelectLinkedGroup(null) }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(30.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = cardColor,
                                border = BorderStroke(1.dp, outlineColor)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Hub,
                                        contentDescription = null,
                                        tint = primaryTextColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = "All",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = primaryTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Linked group rows
                        linkedGroups.forEach { conn ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .clickable { onSelectLinkedGroup(conn) }
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(30.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    color = cardColor,
                                    border = BorderStroke(1.dp, outlineColor)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = conn.otherEntityName.take(1).uppercase(),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = primaryTextColor
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                    text = conn.otherEntityName,
                                    fontSize = 15.sp,
                                    color = primaryTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
