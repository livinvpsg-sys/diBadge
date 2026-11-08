package com.fabxdi.dibadge.ui.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.components.DiBadgeDatePicker
import com.fabxdi.dibadge.ui.settings.SettingsSwitch
import com.fabxdi.dibadge.viewmodel.ExperienceItem
import com.fabxdi.dibadge.viewmodel.ExperienceViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun parseExpDate(s: String): LocalDate? =
    try { if (s.isBlank()) null else LocalDate.parse(s) } catch (e: Exception) { null }

private fun monthYear(s: String): String {
    val d = parseExpDate(s) ?: return ""
    return d.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))
}

private fun rangeText(item: ExperienceItem): String {
    val start = monthYear(item.startDate)
    val end = if (item.isCurrent) "Present" else monthYear(item.endDate)
    return listOf(start, end).filter { it.isNotBlank() }.joinToString(" – ")
}

/**
 * Experience card for the profile. It looks like the other profile cards: a small label on top,
 * the value under it, the same padding and the same gap.
 * Closed: shows only the latest role and how many earlier roles there are. Tap it to open the full list.
 * Open: every role. Tap a role to edit or delete it.
 * Saves straight away; it is not part of the profile Save button.
 */
@Composable
fun ProfileExperienceCard(
    viewModel: ExperienceViewModel,
    cardBgColor: Color,
    dividerColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    tabInactiveColor: Color,
    tealAccent: Color,
    errorColor: Color,
    onShowToast: (String) -> Unit = {}
) {
    val items by viewModel.items.collectAsState()
    val error by viewModel.error.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.ensureListening() }
    LaunchedEffect(error) {
        error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }
    var expanded by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ExperienceItem?>(null) }

    // Same placeholder colour as the other profile fields
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val dimColor = if (isDark) Color(0xFF6E757C) else Color(0xFF8A929A)

    val openAdd = {
        editing = null
        showSheet = true
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = cardBgColor
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val first = items.firstOrNull()

            // First block: label, then the latest role (or the empty hint), like any other profile row
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (first == null) {
                                openAdd()
                            } else if (expanded || items.size == 1) {
                                // A single role, or an open list: edit. A closed list with earlier roles: open it.
                                editing = first
                                showSheet = true
                            } else {
                                expanded = true
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Experience",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = secondaryTextColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    if (first == null) {
                        Text(
                            text = "Add your experience",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            color = dimColor
                        )
                    } else {
                        ExperienceRowContent(
                            item = first,
                            showDescription = expanded,
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor
                        )
                    }
                }

                Text(
                    text = "Add",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = tealAccent,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clickable(onClick = openAdd)
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp)
                )
            }

            if (expanded) {
                items.drop(1).forEach { item ->
                    HorizontalDivider(thickness = 1.dp, color = dividerColor)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                editing = item
                                showSheet = true
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        ExperienceRowContent(
                            item = item,
                            showDescription = true,
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor
                        )
                    }
                }
            }

            if (items.size > 1) {
                HorizontalDivider(thickness = 1.dp, color = dividerColor)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val earlier = items.size - 1
                    Text(
                        text = if (expanded) "Show less"
                        else if (earlier == 1) "1 earlier role" else "$earlier earlier roles",
                        fontSize = 14.sp,
                        color = secondaryTextColor
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = secondaryTextColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }

    if (showSheet) {
        ExperienceSheet(
            existing = editing,
            cardBgColor = cardBgColor,
            dividerColor = dividerColor,
            primaryTextColor = primaryTextColor,
            secondaryTextColor = secondaryTextColor,
            tabInactiveColor = tabInactiveColor,
            tealAccent = tealAccent,
            errorColor = errorColor,
            onSave = { item ->
                viewModel.save(item) { ok ->
                    if (ok) Toast.makeText(context, "Experience saved", Toast.LENGTH_SHORT).show()
                }
                showSheet = false
            },
            onDelete = { item ->
                viewModel.delete(item) { ok ->
                    Toast.makeText(context, if (ok) "Experience removed" else "Couldn't remove. Try again.", Toast.LENGTH_SHORT).show()
                }
                showSheet = false
            },
            onDismiss = { showSheet = false }
        )
    }
}

@Composable
private fun ExperienceRowContent(
    item: ExperienceItem,
    showDescription: Boolean,
    primaryTextColor: Color,
    secondaryTextColor: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = item.role,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            color = primaryTextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = item.company,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = secondaryTextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val range = rangeText(item)
        if (range.isNotBlank()) {
            Text(text = range, fontSize = 13.sp, fontWeight = FontWeight.Normal, color = secondaryTextColor)
        }
        if (showDescription && item.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.description,
                fontSize = 13.sp,
                color = secondaryTextColor,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExperienceSheet(
    existing: ExperienceItem?,
    cardBgColor: Color,
    dividerColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    tabInactiveColor: Color,
    tealAccent: Color,
    errorColor: Color,
    onSave: (ExperienceItem) -> Unit,
    onDelete: (ExperienceItem) -> Unit,
    onDismiss: () -> Unit
) {
    var role by remember { mutableStateOf(existing?.role ?: "") }
    var company by remember { mutableStateOf(existing?.company ?: "") }
    var start by remember { mutableStateOf(parseExpDate(existing?.startDate ?: "")) }
    var end by remember { mutableStateOf(parseExpDate(existing?.endDate ?: "")) }
    var current by remember { mutableStateOf(existing?.isCurrent ?: false) }
    var description by remember { mutableStateOf(existing?.description ?: "") }

    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val endBeforeStart = !current && start != null && end != null && end!!.isBefore(start)
    val canSave = role.isNotBlank() && company.isNotBlank() && start != null &&
            (current || (end != null && !endBeforeStart))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = if (existing == null) "Add experience" else "Edit experience",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = primaryTextColor,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            ExpTextField("Role", role, "Your role", 60, primaryTextColor, secondaryTextColor, tabInactiveColor, tealAccent) { role = it }
            HorizontalDivider(thickness = 1.dp, color = dividerColor)
            ExpTextField("Company", company, "Company name", 80, primaryTextColor, secondaryTextColor, tabInactiveColor, tealAccent) { company = it }
            HorizontalDivider(thickness = 1.dp, color = dividerColor)

            ExpDateRow(
                label = "Start",
                value = start?.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())),
                enabled = true,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
                tabInactiveColor = tabInactiveColor,
                onClick = { pickStart = true }
            )
            HorizontalDivider(thickness = 1.dp, color = dividerColor)
            ExpDateRow(
                label = "End",
                value = if (current) "Present" else end?.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())),
                enabled = !current,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
                tabInactiveColor = tabInactiveColor,
                onClick = { pickEnd = true }
            )
            if (endBeforeStart) {
                Text(
                    text = "End date can't be before the start date",
                    fontSize = 12.sp,
                    color = errorColor,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            HorizontalDivider(thickness = 1.dp, color = dividerColor)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("I currently work here", fontSize = 16.sp, color = primaryTextColor)
                SettingsSwitch(checked = current, onCheckedChange = { current = it })
            }
            HorizontalDivider(thickness = 1.dp, color = dividerColor)

            ExpTextField(
                label = "Description (optional)",
                value = description,
                placeholder = "What you did",
                maxLength = 300,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor,
                tabInactiveColor = tabInactiveColor,
                tealAccent = tealAccent,
                singleLine = false
            ) { description = it }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (existing != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = errorColor)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text("Cancel", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = secondaryTextColor)
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    enabled = canSave,
                    onClick = {
                        onSave(
                            ExperienceItem(
                                id = existing?.id ?: "",
                                role = role,
                                company = company,
                                startDate = start?.toString() ?: "",
                                endDate = if (current) "" else end?.toString() ?: "",
                                isCurrent = current,
                                description = description,
                                createdAt = existing?.createdAt ?: 0L
                            )
                        )
                    }
                ) {
                    Text(
                        "Save",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (canSave) tealAccent else tabInactiveColor
                    )
                }
            }
        }
    }

    if (pickStart) {
        DiBadgeDatePicker(
            selectedDate = start ?: LocalDate.now(),
            maxDate = LocalDate.now(),
            minDate = LocalDate.now().minusYears(70),
            title = "Select start date",
            onDateSelected = {
                start = it.withDayOfMonth(1)
                pickStart = false
            },
            onDismiss = { pickStart = false }
        )
    }

    if (pickEnd) {
        DiBadgeDatePicker(
            selectedDate = end ?: LocalDate.now(),
            maxDate = LocalDate.now(),
            minDate = LocalDate.now().minusYears(70),
            title = "Select end date",
            onDateSelected = {
                end = it.withDayOfMonth(1)
                pickEnd = false
            },
            onDismiss = { pickEnd = false }
        )
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this role?", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = primaryTextColor) },
            text = { Text("It will be removed from your profile.", fontSize = 14.sp, color = secondaryTextColor) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete(existing)
                }) {
                    Text("Delete", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = errorColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = secondaryTextColor)
                }
            },
            containerColor = cardBgColor,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun ExpTextField(
    label: String,
    value: String,
    placeholder: String,
    maxLength: Int,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    tabInactiveColor: Color,
    tealAccent: Color,
    singleLine: Boolean = true,
    onChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(vertical = 10.dp)
    ) {
        Text(label, fontSize = 12.sp, color = secondaryTextColor)
        Spacer(modifier = Modifier.height(2.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            if (value.isEmpty()) {
                Text(placeholder, fontSize = 16.sp, color = tabInactiveColor)
            }
            BasicTextField(
                value = value,
                onValueChange = { if (it.length <= maxLength) onChange(it) },
                singleLine = singleLine,
                maxLines = if (singleLine) 1 else 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, color = primaryTextColor),
                cursorBrush = SolidColor(tealAccent),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ExpDateRow(
    label: String,
    value: String?,
    enabled: Boolean,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    tabInactiveColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .alpha(if (enabled) 1f else 0.40f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, color = secondaryTextColor)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value ?: "Select",
                fontSize = 16.sp,
                color = if (value != null) primaryTextColor else tabInactiveColor
            )
        }
        Icon(
            imageVector = Icons.Outlined.CalendarMonth,
            contentDescription = null,
            tint = secondaryTextColor,
            modifier = Modifier.size(22.dp)
        )
    }
}