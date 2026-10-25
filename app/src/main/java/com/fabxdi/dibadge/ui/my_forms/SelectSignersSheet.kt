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
    isSupervisor: Boolean = false,
    members: List<GroupMember>,
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
    val searchBgColor = if (isDark) Color(0xFF222831) else Color(0xFFDDE2E7)

    val canAdd = selectedSigners.isNotEmpty()

    val filteredMembers = remember(members, searchQuery) {
        if (searchQuery.isBlank()) members
        else {
            val q = searchQuery.trim().lowercase()
            members.filter { it.name.lowercase().contains(q) }
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
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            // Header Row: Title + Subtitle
            Text(
                text = if (isSupervisor) "Select supervisor" else "Select workers",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = primaryTextColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isSupervisor) "Choose one person from this group or linked groups" else "Choose one or more from this group and linked groups",
                fontSize = 13.sp,
                color = secondaryTextColor
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Search Bar (48dp, rounded)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                color = searchBgColor
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = secondaryTextColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text("Search names", fontSize = 16.sp, color = dimColor)
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, color = primaryTextColor),
                            cursorBrush = SolidColor(tealAccent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search", tint = secondaryTextColor, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section Header: "Site team"
            Text(
                text = "Site team",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = secondaryTextColor,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
            ) {
                items(filteredMembers, key = { it.id }) { member ->
                    val isSelected = selectedSigners.any { it.uid == member.uid }
                    val avatarColor = remember(member.name) { UserColorUtils.getColorForName(member.name) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clickable {
                                if (isSupervisor) {
                                    selectedSigners = listOf(FormSigner(uid = member.uid, name = member.name, isSupervisor = true))
                                } else {
                                    selectedSigners = if (isSelected) {
                                        selectedSigners.filter { it.uid != member.uid }
                                    } else {
                                        selectedSigners + FormSigner(uid = member.uid, name = member.name, isSupervisor = false)
                                    }
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
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape,
                                color = avatarColor
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = member.name.take(1).uppercase(),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Text(
                                text = member.name,
                                fontSize = 16.sp,
                                color = primaryTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (isSupervisor) {
                            // Radio circle for supervisor
                            Surface(
                                modifier = Modifier.size(22.dp),
                                shape = CircleShape,
                                color = Color.Transparent,
                                border = BorderStroke(1.5.dp, if (isSelected) tealAccent else dimColor)
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
                        } else {
                            // Checkbox circle for workers
                            Surface(
                                modifier = Modifier.size(24.dp),
                                shape = CircleShape,
                                color = if (isSelected) tealAccent else Color.Transparent,
                                border = BorderStroke(1.5.dp, if (isSelected) tealAccent else dimColor)
                            ) {
                                if (isSelected) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = if (isDark) Color(0xFF0E1114) else Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(thickness = 1.dp, color = outlineColor)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Footer: "{n} selected" left, Add (teal text) right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${selectedSigners.size} selected",
                    fontSize = 14.sp,
                    color = secondaryTextColor
                )

                Text(
                    text = "Add",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canAdd) tealAccent else dimColor,
                    modifier = Modifier
                        .clickable(enabled = canAdd) {
                            onDone(selectedSigners)
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}
