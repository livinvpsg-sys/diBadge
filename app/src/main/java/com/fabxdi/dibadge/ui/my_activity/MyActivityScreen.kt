package com.fabxdi.dibadge.ui.my_activity

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fabxdi.dibadge.data.LeaveEntity
import com.fabxdi.dibadge.data.OvertimeEntity
import com.fabxdi.dibadge.ui.components.DiBadgeSearchBar
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.PdfSharingUtils
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyActivityScreen(
    leaves: List<LeaveEntity>,
    overtimes: List<OvertimeEntity>,
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit = {}
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("1 month") }
    val filters = listOf("1 month", "1 year", "all", "Custom")
    
    var showLeave by remember { mutableStateOf(true) }
    var showOvertime by remember { mutableStateOf(true) }
    var isFilterMenuExpanded by remember { mutableStateOf(false) }

    var showDateRangePicker by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()
    
    var customStartDate by remember { mutableStateOf<LocalDate?>(null) }
    var customEndDate by remember { mutableStateOf<LocalDate?>(null) }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault()) }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault()) }

    val dateRangeLimits = remember(selectedFilter, customStartDate, customEndDate) {
        val today = LocalDate.now()
        val start = when (selectedFilter) {
            "1 month" -> today.minusMonths(1)
            "1 year" -> today.minusYears(1)
            "all" -> LocalDate.MIN
            "Custom" -> customStartDate ?: LocalDate.MIN
            else -> LocalDate.MIN
        }
        val end = if (selectedFilter == "Custom") customEndDate ?: LocalDate.MAX else today
        start to end
    }

    val filteredLogs = remember(leaves, overtimes, searchQuery, dateRangeLimits, showLeave, showOvertime) {
        val (startDateLimit, endDateLimit) = dateRangeLimits
        val filteredLeaves = if (showLeave) {
            leaves.filter { leave ->
                val inRange = !leave.appliedDate.isBefore(startDateLimit) && !leave.appliedDate.isAfter(endDateLimit)
                val matchesSearch = leave.leaveType.contains(searchQuery, ignoreCase = true) || 
                                   leave.reason.contains(searchQuery, ignoreCase = true)
                inRange && matchesSearch
            }
        } else emptyList()

        val filteredOvertimes = if (showOvertime) {
            overtimes.filter { ot ->
                val inRange = !ot.appliedDate.isBefore(startDateLimit) && !ot.appliedDate.isAfter(endDateLimit)
                val matchesSearch = ot.description.contains(searchQuery, ignoreCase = true) || 
                                   "Overtime".contains(searchQuery, ignoreCase = true)
                inRange && matchesSearch
            }
        } else emptyList()

        (filteredLeaves + filteredOvertimes).sortedByDescending { 
            when (it) {
                is LeaveEntity -> it.appliedDate
                is OvertimeEntity -> it.appliedDate
                else -> LocalDate.MIN
            }
        }
    }

    val groupedLogs = remember(filteredLogs) {
        filteredLogs.groupBy {
            when (it) {
                is LeaveEntity -> it.appliedDate
                is OvertimeEntity -> it.appliedDate
                else -> LocalDate.MIN
            }
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                IconButton(onClick = { 
                    PdfSharingUtils.shareMyActivityAsPdf(
                        context = context, 
                        logs = filteredLogs, 
                        startDate = dateRangeLimits.first, 
                        endDate = dateRangeLimits.second
                    )
                }) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        },
        bottomBar = bottomBar
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    focusManager.clearFocus()
                }
        ) {
            // Search Box
            DiBadgeSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                modifier = Modifier.padding(
                    horizontal = DiBadgeTheme.spacing.medium,
                    vertical = 8.dp
                )
            )

            // Filter Options
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DiBadgeTheme.spacing.medium, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    items(filters) { filter ->
                        val isSelected = selectedFilter == filter
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (filter == "Custom") {
                                    showDateRangePicker = true
                                } else {
                                    selectedFilter = filter
                                    customStartDate = null
                                    customEndDate = null
                                }
                            },
                            label = {
                                if (filter == "Custom" && customStartDate != null && customEndDate != null) {
                                    Text("${customStartDate!!.format(DateTimeFormatter.ofPattern("dd/MM"))} - ${customEndDate!!.format(DateTimeFormatter.ofPattern("dd/MM"))}")
                                } else {
                                    Text(filter)
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                selectedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                selectedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                Box {
                    IconButton(onClick = { isFilterMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter Types",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    DropdownMenu(
                        expanded = isFilterMenuExpanded,
                        onDismissRequest = { isFilterMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Leave") },
                            trailingIcon = { Checkbox(checked = showLeave, onCheckedChange = null) },
                            onClick = { showLeave = !showLeave }
                        )
                        DropdownMenuItem(
                            text = { Text("Overtime") },
                            trailingIcon = { Checkbox(checked = showOvertime, onCheckedChange = null) },
                            onClick = { showOvertime = !showOvertime }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Logs List
            if (groupedLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No logs found for the selected period",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = DiBadgeTheme.spacing.medium, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    groupedLogs.forEach { (date, logs) ->
                        item {
                            Text(
                                text = date.format(dateFormatter),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        items(logs) { log ->
                            when (log) {
                                is LeaveEntity -> LeaveLogCard(leave = log, dateFormatter = dateFormatter)
                                is OvertimeEntity -> OvertimeLogCard(overtime = log, dateFormatter = dateFormatter, timeFormatter = timeFormatter)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDateRangePicker) {
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val startMillis = dateRangePickerState.selectedStartDateMillis
                        val endMillis = dateRangePickerState.selectedEndDateMillis
                        if (startMillis != null && endMillis != null) {
                            customStartDate = Instant.ofEpochMilli(startMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                            customEndDate = Instant.ofEpochMilli(endMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                            selectedFilter = "Custom"
                        }
                        showDateRangePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                modifier = Modifier.height(400.dp)
            )
        }
    }
}

@Composable
fun LeaveLogCard(leave: LeaveEntity, dateFormatter: DateTimeFormatter) {
    val dateText = if (leave.endDate != null && leave.endDate != leave.startDate) {
        "${leave.startDate.format(dateFormatter)} - ${leave.endDate.format(dateFormatter)}"
    } else {
        leave.startDate.format(dateFormatter)
    }

    val statusColor = when (leave.status) {
        "Approved" -> MaterialTheme.colorScheme.primary
        "Pending" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = leave.leaveType,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = leave.status,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = dateText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (leave.reason.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Reason: ${leave.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun OvertimeLogCard(overtime: OvertimeEntity, dateFormatter: DateTimeFormatter, timeFormatter: DateTimeFormatter) {
    val dateText = overtime.date.format(dateFormatter)
    val timeText = "${overtime.startTime.format(timeFormatter)} - ${overtime.endTime.format(timeFormatter)}"

    val statusColor = when (overtime.status) {
        "Approved" -> MaterialTheme.colorScheme.primary
        "Pending" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Overtime",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = overtime.status,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$dateText ($timeText)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (overtime.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Details: ${overtime.description}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
