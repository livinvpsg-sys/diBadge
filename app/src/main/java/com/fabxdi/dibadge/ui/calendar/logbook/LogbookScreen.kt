package com.fabxdi.dibadge.ui.calendar.logbook

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
fun LogbookScreen(
    leaves: List<LeaveEntity>,
    overtimes: List<OvertimeEntity>,
    onBack: () -> Unit
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
            TopAppBar(
                title = {
                    Text(
                        text = "Logbook",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        PdfSharingUtils.shareLogbookAsPdf(
                            context = context, 
                            logs = filteredLogs, 
                            startDate = dateRangeLimits.first, 
                            endDate = dateRangeLimits.second
                        )
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
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
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(filters) { filter ->
                        val isSelected = selectedFilter == filter
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedFilter = filter
                                if (filter == "Custom") {
                                    showDateRangePicker = true
                                }
                            },
                            label = {
                                if (filter == "Custom" && customStartDate != null && customEndDate != null) {
                                    Text("${customStartDate?.format(dateFormatter)} - ${customEndDate?.format(dateFormatter)}")
                                } else {
                                    Text(filter)
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                                selectedTrailingIconColor = MaterialTheme.colorScheme.primary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                selectedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                Box {
                    IconButton(onClick = { isFilterMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    DropdownMenu(
                        expanded = isFilterMenuExpanded,
                        onDismissRequest = { isFilterMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Leave", modifier = Modifier.weight(1f))
                                    Switch(
                                        checked = showLeave,
                                        onCheckedChange = { showLeave = it },
                                        modifier = Modifier.scale(0.7f)
                                    )
                                }
                            },
                            onClick = { }
                        )
                        DropdownMenuItem(
                            text = { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Overtime", modifier = Modifier.weight(1f))
                                    Switch(
                                        checked = showOvertime,
                                        onCheckedChange = { showOvertime = it },
                                        modifier = Modifier.scale(0.7f)
                                    )
                                }
                            },
                            onClick = { }
                        )
                    }
                }
            }

            // Content Area (List of logs)
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No logs found for the selected period",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = DiBadgeTheme.spacing.medium, vertical = 8.dp)
                ) {
                    groupedLogs.forEach { (date, logs) ->
                        item {
                            Text(
                                text = date.format(dateFormatter),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                            )
                        }
                        items(logs) { log ->
                            LogItemCard(log, dateFormatter, timeFormatter)
                            Spacer(modifier = Modifier.height(12.dp))
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
                        dateRangePickerState.selectedStartDateMillis?.let { start ->
                            customStartDate = Instant.ofEpochMilli(start).atZone(ZoneId.of("UTC")).toLocalDate()
                        }
                        dateRangePickerState.selectedEndDateMillis?.let { end ->
                            customEndDate = Instant.ofEpochMilli(end).atZone(ZoneId.of("UTC")).toLocalDate()
                        }
                        showDateRangePicker = false
                    },
                    enabled = dateRangePickerState.selectedEndDateMillis != null
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) { Text("Cancel") }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                title = { Text(text = "Select Range", modifier = Modifier.padding(start = 16.dp, top = 16.dp), style = MaterialTheme.typography.labelMedium) },
                headline = {
                    val startText = dateRangePickerState.selectedStartDateMillis?.let { 
                        Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate().format(dateFormatter) 
                    } ?: "Start Date"
                    val endText = dateRangePickerState.selectedEndDateMillis?.let { 
                        Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate().format(dateFormatter) 
                    } ?: "End Date"
                    Text(
                        text = "$startText - $endText",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
                        maxLines = 1
                    )
                },
                showModeToggle = false,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun LogItemCard(
    log: Any,
    dateFormatter: DateTimeFormatter,
    timeFormatter: DateTimeFormatter
) {
    val appTimeFormatter = remember { DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault()) }

    val status = when (log) {
        is LeaveEntity -> log.status
        is OvertimeEntity -> log.status
        else -> ""
    }

    val appliedDate = when (log) {
        is LeaveEntity -> log.appliedDate
        is OvertimeEntity -> log.appliedDate
        else -> LocalDate.MIN
    }

    val appliedDateTime = when (log) {
        is LeaveEntity -> log.appliedDateTime
        is OvertimeEntity -> log.appliedDateTime
        else -> LocalDateTime.now()
    }

    val approvedDate = when (log) {
        is LeaveEntity -> log.approvedDate
        is OvertimeEntity -> log.approvedDate
        else -> null
    }

    val title = when (log) {
        is LeaveEntity -> log.leaveType
        is OvertimeEntity -> "Overtime ${log.date.format(dateFormatter)}"
        else -> ""
    }

    val secondaryText = when (log) {
        is LeaveEntity -> {
            if (log.endDate != null) {
                "${log.startDate.format(dateFormatter)} - ${log.endDate.format(dateFormatter)}"
            } else {
                log.startDate.format(dateFormatter)
            }
        }
        is OvertimeEntity -> "${log.startTime.format(timeFormatter)} - ${log.endTime.format(timeFormatter)}"
        else -> ""
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side: Title + Status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Main Heading
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Status Label: Applied or Approved on Date
                Text(
                    text = if (status == "Approved" && approvedDate != null) {
                        "Approved on ${approvedDate.format(dateFormatter)}"
                    } else {
                        "applied"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            // Right side: Time of entry
            Text(
                text = appliedDateTime.format(appTimeFormatter),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Date range or Time range
        Text(
            text = secondaryText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
