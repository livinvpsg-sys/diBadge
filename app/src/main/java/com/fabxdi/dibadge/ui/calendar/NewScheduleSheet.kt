package com.fabxdi.dibadge.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.components.DiBadgeDatePicker
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.viewmodel.GroupMember
import com.fabxdi.dibadge.viewmodel.GroupTaskViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewScheduleSheet(
    groupId: String,
    initialDate: LocalDate = LocalDate.now(),
    userName: String,
    userUid: String,
    members: List<GroupMember> = emptyList(),
    taskViewModel: GroupTaskViewModel,
    onTaskAdded: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var fromDate by remember { mutableStateOf(initialDate.let { if (it.isBefore(LocalDate.now())) LocalDate.now() else it }) }
    var toDate by remember { mutableStateOf(fromDate) }
    var notes by remember { mutableStateOf("") }

    var selectedMemberUids by remember { mutableStateOf(setOf<String>()) }
    var showMemberPicker by remember { mutableStateOf(false) }

    var showFromDatePicker by remember { mutableStateOf(false) }
    var showToDatePicker by remember { mutableStateOf(false) }

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val tealAccent = diBadgeColors.accent
    val dimColor = secondaryTextColor.copy(alpha = 0.5f)

    val canSave = title.isNotBlank()
    val daysCount = ChronoUnit.DAYS.between(fromDate, toDate).toInt() + 1

    val dateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())

    val assignedNamesStr = remember(selectedMemberUids, members) {
        if (selectedMemberUids.isEmpty()) {
            ""
        } else {
            members.filter { selectedMemberUids.contains(it.uid) }
                .joinToString(", ") { it.displayName }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
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
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Title: "New task"
            Text(
                text = "New task",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = primaryTextColor
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 1. Task title field (placeholder "Task title")
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    if (title.isEmpty()) {
                        Text(
                            text = "Task title",
                            fontSize = 15.sp,
                            color = dimColor
                        )
                    }
                    BasicTextField(
                        value = title,
                        onValueChange = { if (it.length <= 80) title = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            color = primaryTextColor
                        ),
                        cursorBrush = SolidColor(tealAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    )
                }
                HorizontalDivider(thickness = 1.dp, color = outlineColor.copy(alpha = 0.60f))
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. From field
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "From",
                    fontSize = 12.sp,
                    color = secondaryTextColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clickable { showFromDatePicker = true },
                    contentAlignment = Alignment.BottomStart
                ) {
                    Text(
                        text = fromDate.format(dateFormatter),
                        fontSize = 15.sp,
                        color = primaryTextColor,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                HorizontalDivider(thickness = 1.dp, color = outlineColor.copy(alpha = 0.60f))
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. To field
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "To",
                    fontSize = 12.sp,
                    color = secondaryTextColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clickable { showToDatePicker = true },
                    contentAlignment = Alignment.BottomStart
                ) {
                    Text(
                        text = toDate.format(dateFormatter),
                        fontSize = 15.sp,
                        color = primaryTextColor,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                HorizontalDivider(thickness = 1.dp, color = outlineColor.copy(alpha = 0.60f))
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Assigned to field (multi-select members)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Assigned to",
                    fontSize = 12.sp,
                    color = secondaryTextColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clickable { if (members.isNotEmpty()) showMemberPicker = true },
                    contentAlignment = Alignment.BottomStart
                ) {
                    Text(
                        text = assignedNamesStr.ifBlank { if (members.isEmpty()) "Everyone" else "Select assignees (optional)" },
                        fontSize = 15.sp,
                        color = if (assignedNamesStr.isNotBlank()) primaryTextColor else dimColor,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                HorizontalDivider(thickness = 1.dp, color = outlineColor.copy(alpha = 0.60f))
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Notes field (placeholder "Notes (optional)")
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 36.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    if (notes.isEmpty()) {
                        Text(
                            text = "Notes (optional)",
                            fontSize = 15.sp,
                            color = dimColor
                        )
                    }
                    BasicTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        singleLine = false,
                        maxLines = 4,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            color = primaryTextColor
                        ),
                        cursorBrush = SolidColor(tealAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    )
                }
                HorizontalDivider(thickness = 1.dp, color = outlineColor.copy(alpha = 0.60f))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Secondary line: "{n} days"
            Text(
                text = "$daysCount ${if (daysCount == 1) "day" else "days"}",
                fontSize = 12.sp,
                color = secondaryTextColor
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Actions at bottom right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cancel",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal,
                    color = secondaryTextColor,
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )

                Spacer(modifier = Modifier.width(28.dp))

                Text(
                    text = "Save",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canSave) tealAccent else dimColor,
                    modifier = Modifier
                        .clickable(enabled = canSave) {
                            taskViewModel.createScheduleTask(
                                groupId = groupId,
                                title = title,
                                startDate = fromDate,
                                endDate = toDate,
                                notes = notes,
                                assignedToNames = assignedNamesStr,
                                assignedToUids = selectedMemberUids.joinToString(","),
                                assignerName = userName,
                                assignerUid = userUid,
                                onAdded = { startDate: LocalDate ->
                                    onTaskAdded(startDate)
                                }
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }

    if (showMemberPicker) {
        AlertDialog(
            onDismissRequest = { showMemberPicker = false },
            title = {
                Text(
                    text = "Assign to",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    members.forEach { member ->
                        val isSelected = selectedMemberUids.contains(member.uid)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedMemberUids = if (isSelected) {
                                        selectedMemberUids - member.uid
                                    } else {
                                        selectedMemberUids + member.uid
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    selectedMemberUids = if (checked) {
                                        selectedMemberUids + member.uid
                                    } else {
                                        selectedMemberUids - member.uid
                                    }
                                },
                                colors = CheckboxDefaults.colors(checkedColor = tealAccent)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = member.displayName,
                                fontSize = 15.sp,
                                color = primaryTextColor
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMemberPicker = false }) {
                    Text("Done", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = tealAccent)
                }
            },
            containerColor = cardBgColor,
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showFromDatePicker) {
        DiBadgeDatePicker(
            selectedDate = fromDate,
            minDate = LocalDate.now(),
            onDateSelected = { date ->
                fromDate = date
                if (date.isAfter(toDate)) {
                    toDate = date
                }
                showFromDatePicker = false
            },
            onDismiss = { showFromDatePicker = false }
        )
    }

    if (showToDatePicker) {
        DiBadgeDatePicker(
            selectedDate = toDate,
            minDate = fromDate,
            onDateSelected = { date ->
                if (!date.isBefore(fromDate)) {
                    toDate = date
                }
                showToDatePicker = false
            },
            onDismiss = { showToDatePicker = false }
        )
    }
}
