package com.fabxdi.dibadge.ui.my_forms

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.data.FormSigner
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.UserColorUtils
import com.fabxdi.dibadge.viewmodel.GroupMember

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectSignersSheet(
    isMultiple: Boolean = true,
    members: List<GroupMember>,
    linkedGroupMembers: Map<String, List<GroupMember>> = emptyMap(),
    initialSelected: List<FormSigner>,
    onDone: (List<FormSigner>) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedSigners by remember(initialSelected) { mutableStateOf(initialSelected) }

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val dimColor = diBadgeColors.tabInactive
    val tealAccent = diBadgeColors.accent
    val isDark = primaryTextColor.red > 0.5f

    val canSend = selectedSigners.isNotEmpty()

    // Filter members by search query (name or badge number)
    val filteredThisGroup = remember(members, searchQuery) {
        if (searchQuery.isBlank()) members
        else {
            val q = searchQuery.trim().lowercase()
            members.filter { it.name.lowercase().contains(q) || it.badgeNumber.lowercase().contains(q) }
        }
    }

    val filteredLinkedGroups = remember(linkedGroupMembers, searchQuery) {
        if (searchQuery.isBlank()) linkedGroupMembers
        else {
            val q = searchQuery.trim().lowercase()
            linkedGroupMembers.mapValues { (_, list) ->
                list.filter { it.name.lowercase().contains(q) || it.badgeNumber.lowercase().contains(q) }
            }.filterValues { it.isNotEmpty() }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = cardBgColor,
        scrimColor = Color.Black.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            // 1. Title (No Subtitle)
            Text(
                text = if (isMultiple) "Select members" else "Select member",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = primaryTextColor
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Search Line (Bottom line only, no box)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = secondaryTextColor,
                        modifier = Modifier.size(22.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text("Search members", fontSize = 15.sp, color = dimColor)
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, color = primaryTextColor),
                            cursorBrush = SolidColor(tealAccent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = secondaryTextColor, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = outlineColor.copy(alpha = 0.60f))

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Lists with Sections
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
            ) {
                // Section 1: THIS GROUP
                if (filteredThisGroup.isNotEmpty()) {
                    item {
                        Text(
                            text = "THIS GROUP (${filteredThisGroup.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryTextColor,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    items(filteredThisGroup, key = { "this_${it.uid}" }) { member ->
                        val isSelected = selectedSigners.any { it.uid == member.uid }
                        val avatarColor = remember(member.name) { UserColorUtils.getColorForName(member.name) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clickable {
                                    if (isMultiple) {
                                        selectedSigners = if (isSelected) {
                                            selectedSigners.filter { it.uid != member.uid }
                                        } else {
                                            selectedSigners + FormSigner(uid = member.uid, name = member.name)
                                        }
                                    } else {
                                        selectedSigners = listOf(FormSigner(uid = member.uid, name = member.name, isSupervisor = true))
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    modifier = Modifier.size(36.dp),
                                    shape = CircleShape,
                                    color = avatarColor
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = member.name.take(1).uppercase(),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column {
                                    Text(
                                        text = member.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = primaryTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (member.badgeNumber.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = member.badgeNumber,
                                            fontSize = 12.sp,
                                            color = secondaryTextColor
                                        )
                                    }
                                }
                            }

                            if (isMultiple) {
                                // Checkbox: 22dp with 6dp rounded corners
                                Surface(
                                    modifier = Modifier.size(22.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) tealAccent else Color.Transparent,
                                    border = BorderStroke(if (isSelected) 0.dp else 2.dp, if (isSelected) tealAccent else secondaryTextColor)
                                ) {
                                    if (isSelected) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = if (isDark) Color(0xFF0E1114) else Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Radio: 22dp circle
                                Surface(
                                    modifier = Modifier.size(22.dp),
                                    shape = CircleShape,
                                    color = Color.Transparent,
                                    border = BorderStroke(2.dp, if (isSelected) tealAccent else secondaryTextColor)
                                ) {
                                    if (isSelected) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Surface(
                                                modifier = Modifier.size(10.dp),
                                                shape = CircleShape,
                                                color = tealAccent
                                            ) {}
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(thickness = 1.dp, color = outlineColor)
                    }
                }

                // Linked Groups Sections
                filteredLinkedGroups.forEach { (groupName, linkedMembers) ->
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "${groupName.uppercase()} (${linkedMembers.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondaryTextColor,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    items(linkedMembers, key = { "linked_${groupName}_${it.uid}" }) { member ->
                        val isSelected = selectedSigners.any { it.uid == member.uid }
                        val avatarColor = remember(member.name) { UserColorUtils.getColorForName(member.name) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clickable {
                                    if (isMultiple) {
                                        selectedSigners = if (isSelected) {
                                            selectedSigners.filter { it.uid != member.uid }
                                        } else {
                                            selectedSigners + FormSigner(uid = member.uid, name = member.name)
                                        }
                                    } else {
                                        selectedSigners = listOf(FormSigner(uid = member.uid, name = member.name, isSupervisor = true))
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    modifier = Modifier.size(36.dp),
                                    shape = CircleShape,
                                    color = avatarColor
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = member.name.take(1).uppercase(),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column {
                                    Text(
                                        text = member.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = primaryTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (member.badgeNumber.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = member.badgeNumber,
                                            fontSize = 12.sp,
                                            color = secondaryTextColor
                                        )
                                    }
                                }
                            }

                            if (isMultiple) {
                                Surface(
                                    modifier = Modifier.size(22.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) tealAccent else Color.Transparent,
                                    border = BorderStroke(if (isSelected) 0.dp else 2.dp, if (isSelected) tealAccent else secondaryTextColor)
                                ) {
                                    if (isSelected) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = if (isDark) Color(0xFF0E1114) else Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Surface(
                                    modifier = Modifier.size(22.dp),
                                    shape = CircleShape,
                                    color = Color.Transparent,
                                    border = BorderStroke(2.dp, if (isSelected) tealAccent else secondaryTextColor)
                                ) {
                                    if (isSelected) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Surface(
                                                modifier = Modifier.size(10.dp),
                                                shape = CircleShape,
                                                color = tealAccent
                                            ) {}
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(thickness = 1.dp, color = outlineColor)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 5. Send Request Bar pinned at bottom (centered teal text action)
            val sendText = when {
                !isMultiple -> "Send request"
                selectedSigners.isEmpty() -> "Send request"
                else -> "Send request (${selectedSigners.size})"
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = sendText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canSend) tealAccent else dimColor,
                    modifier = Modifier
                        .clickable(enabled = canSend) {
                            onDone(selectedSigners)
                        }
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
        }
    }
}
