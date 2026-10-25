package com.fabxdi.dibadge.ui.my_forms

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabxdi.dibadge.data.FormEntity
import com.fabxdi.dibadge.data.HomeEntryEntity
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.UserColorUtils
import com.fabxdi.dibadge.viewmodel.FormViewModel
import com.fabxdi.dibadge.viewmodel.ToDoViewModel
import com.fabxdi.dibadge.viewmodel.parseStringListFromJson
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToGroupSheet(
    form: FormEntity,
    onDismiss: () -> Unit,
    onAdded: (Int) -> Unit,
    formViewModel: FormViewModel = viewModel(),
    toDoViewModel: ToDoViewModel = viewModel()
) {
    val homeEntries by toDoViewModel.allHomeEntries.collectAsState(initial = emptyList())
    var selectedGroupIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val dimColor = diBadgeColors.tabInactive
    val tealAccent = diBadgeColors.accent
    val isDark = primaryTextColor.red > 0.5f

    val currentUserName = remember { FirebaseAuth.getInstance().currentUser?.displayName ?: "Owner" }
    val currentUserUid = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "me" }

    val alreadyAddedGroupIds = remember(form.addedGroupIdsJson) {
        parseStringListFromJson(form.addedGroupIdsJson).toSet()
    }

    val canConfirm = selectedGroupIds.isNotEmpty()

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
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "Add to group",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = primaryTextColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${form.title} · groups where you are Owner or Admin",
                fontSize = 13.sp,
                color = secondaryTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (homeEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No groups found", fontSize = 15.sp, color = secondaryTextColor)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                ) {
                    items(homeEntries, key = { it.id }) { entry ->
                        val entryIdStr = entry.id.toString()
                        val isAlreadyAdded = alreadyAddedGroupIds.contains(entryIdStr)
                        val isSelected = selectedGroupIds.contains(entryIdStr)
                        val avatarColor = remember(entry.id) { UserColorUtils.getColorForName(entry.id.toString()) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clickable(enabled = !isAlreadyAdded) {
                                    selectedGroupIds = if (isSelected) {
                                        selectedGroupIds - entryIdStr
                                    } else {
                                        selectedGroupIds + entryIdStr
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
                                            text = entry.title.take(1).uppercase(),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column {
                                    Text(
                                        text = entry.title,
                                        fontSize = 16.sp,
                                        color = if (isAlreadyAdded) dimColor else primaryTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (isAlreadyAdded) "Added" else "Owner",
                                        fontSize = 13.sp,
                                        color = dimColor
                                    )
                                }
                            }

                            if (isAlreadyAdded) {
                                Text("Added", fontSize = 14.sp, color = dimColor)
                            } else {
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
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Footer actions: Cancel and Add
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cancel",
                    fontSize = 15.sp,
                    color = secondaryTextColor,
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )

                Spacer(modifier = Modifier.width(24.dp))

                Text(
                    text = "Add",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canConfirm) tealAccent else dimColor,
                    modifier = Modifier
                        .clickable(enabled = canConfirm) {
                            formViewModel.addFormToGroups(
                                form = form,
                                targetGroupIds = selectedGroupIds.toList(),
                                currentUserName = currentUserName,
                                currentUserUid = currentUserUid,
                                onDone = onAdded
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}
