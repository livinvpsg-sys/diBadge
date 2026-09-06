package com.fabxdi.dibadge.ui.home

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabxdi.dibadge.R
import com.fabxdi.dibadge.ui.calendar.leave.LeaveFormScreen
import com.fabxdi.dibadge.ui.calendar.overtime.OvertimeFormScreen
import com.fabxdi.dibadge.ui.calendar.reminder.ReminderFormScreen
import com.fabxdi.dibadge.ui.home.home_entry.chat.ChatScreen
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.ui.theme.TealGreen
import com.fabxdi.dibadge.viewmodel.ReminderViewModel
import com.fabxdi.dibadge.data.HomeEntryEntity
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.collections.minus
import kotlin.collections.plus

enum class HomeTab(
    val label: String,
    @DrawableRes val iconRes: Int
) {
    Home("Home", R.drawable.ic_home),
    Calendar("Calendar", R.drawable.ic_calendar),
    Presence("Presence", R.drawable.ic_presence),
    Note("Notes", R.drawable.ic_note)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboard(
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    onCalendarTabClick: () -> Unit,
    onReminderClick: () -> Unit = {},
    reminderCount: Int = 0,
    notificationCount: Int = 0,
    firstName: String = "User",
    viewModel: ReminderViewModel = viewModel()
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var homeSearchQuery by remember { mutableStateOf("") }
    var showQrScanner by remember { mutableStateOf(false) }
    var showAddEntryDialog by remember { mutableStateOf(false) }
    var selectedEntries by remember { mutableStateOf(setOf<HomeEntryEntity>()) }
    var showDeleteEntryDialog by remember { mutableStateOf(false) }
    var selectedEntryForChat by remember { mutableStateOf<HomeEntryEntity?>(null) }

    // Handle device back button to clear selection
    BackHandler(enabled = selectedEntries.isNotEmpty()) {
        selectedEntries = emptySet()
    }

    var isFabExpanded by remember { mutableStateOf(false) }
    var showReminderForm by remember { mutableStateOf(false) }
    var showOvertimeForm by remember { mutableStateOf(false) }
    var showLeaveForm by remember { mutableStateOf(false) }

    val allLeaves by viewModel.allLeaves.collectAsState(initial = emptyList())

    if (showReminderForm) {
        ReminderFormScreen(
            onSave = { title, content, date, start, end, repeat, time, isAlarm, attachments, id ->
                viewModel.saveReminder(
                    title,
                    content,
                    date,
                    start,
                    end,
                    repeat,
                    time,
                    isAlarm,
                    attachments,
                    id
                )
                showReminderForm = false
            },
            onDelete = { /* N/A for new */ },
            onCancel = { showReminderForm = false }
        )
        return
    }

    if (showLeaveForm) {
        LeaveFormScreen(
            existingLeaves = allLeaves,
            onApply = { startDate, endDate, type, reason, attachments, id, action ->
                viewModel.saveLeave(startDate, endDate, type, reason, attachments, id)
                showLeaveForm = false
            },
            onClose = { showLeaveForm = false }
        )
        return
    }

    if (showOvertimeForm) {
        OvertimeFormScreen(
            existingLeaves = allLeaves,
            onApply = { date, start, end, description, id ->
                viewModel.saveOvertime(date, start, end, description, id)
                showOvertimeForm = false
            },
            onDelete = { overtime ->
                viewModel.deleteOvertime(overtime)
                showOvertimeForm = false
            },
            onClose = { showOvertimeForm = false }
        )
        return
    }

    if (selectedEntryForChat != null) {
        ChatScreen(
            entry = selectedEntryForChat!!,
            onBack = { selectedEntryForChat = null }
        )
        return
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    HomeMenu(
                        firstName = firstName,
                        onCloseDrawer = { scope.launch { drawerState.close() } }
                    )
                }
            }
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Scaffold(
                    topBar = {
                        if (selectedTab == HomeTab.Home) {
                            HomeBanner(
                                firstName = firstName,
                                notificationCount = notificationCount,
                                reminderCount = reminderCount,
                                searchQuery = homeSearchQuery,
                                onSearchQueryChange = { homeSearchQuery = it },
                                onReminderClick = onReminderClick,
                                onMenuClick = { scope.launch { drawerState.open() } },
                                onQrClick = { showQrScanner = true },
                                selectedCount = selectedEntries.size,
                                onDeleteClick = { showDeleteEntryDialog = true },
                                onClearSelection = { selectedEntries = emptySet() }
                            )
                        }
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = NavigationBarDefaults.Elevation
                        ) {
                            HomeTab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = selectedTab == tab,
                                    onClick = {
                                        onTabSelected(tab)
                                        if (tab == HomeTab.Calendar) {
                                            onCalendarTabClick()
                                        }
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color.White,
                                        selectedTextColor = Color.White,
                                        indicatorColor = Color.Transparent,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    ),
                                    alwaysShowLabel = true,
                                    label = {
                                        Text(
                                            text = tab.label,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    },
                                    icon = {
                                        val iconTint = if (tab == HomeTab.Home) {
                                            if (selectedTab == tab) TealGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        } else {
                                            if (selectedTab == tab) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        }

                                        if (tab == HomeTab.Calendar) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.CalendarToday,
                                                    contentDescription = tab.label,
                                                    tint = iconTint
                                                )
                                                Text(
                                                    text = LocalDate.now().dayOfMonth.toString(),
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        lineHeight = 9.sp
                                                    ),
                                                    color = iconTint,
                                                    modifier = Modifier.padding(top = 4.dp)
                                                )
                                            }
                                        } else {
                                            Icon(
                                                painter = painterResource(id = tab.iconRes),
                                                contentDescription = tab.label,
                                                tint = iconTint
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { showAddEntryDialog = true },
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Action"
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (isFabExpanded) {
                                    isFabExpanded = false
                                } else {
                                    focusManager.clearFocus()
                                }
                            }
                    ) {
                        // Overlay to detect clicks and close the FAB menu
                        if (isFabExpanded) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        isFabExpanded = false
                                    }
                            )
                        }

                        when (selectedTab) {
                            HomeTab.Home -> {
                                val homeEntries by viewModel.allHomeEntries.collectAsState(initial = emptyList())
                                val filteredEntries = remember(homeEntries, homeSearchQuery) {
                                    homeEntries.filter { 
                                        it.title.contains(homeSearchQuery, ignoreCase = true) || 
                                        it.subtitle.contains(homeSearchQuery, ignoreCase = true)
                                    }
                                }

                                if (filteredEntries.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        val message = if (homeSearchQuery.isEmpty()) "No entries yet" else "No matches found"
                                        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(vertical = 8.dp)
                                    ) {
                                        items(filteredEntries.size) { index ->
                                            val entry = filteredEntries[index]
                                            HomeEntryCard(
                                                entry = entry,
                                                isSelected = selectedEntries.contains(entry),
                                                onClick = { 
                                                    if (selectedEntries.isNotEmpty()) {
                                                        selectedEntries = if (selectedEntries.contains(entry)) {
                                                            selectedEntries - entry
                                                        } else {
                                                            selectedEntries + entry
                                                        }
                                                    } else {
                                                        selectedEntryForChat = entry
                                                    }
                                                },
                                                onLongClick = {
                                                    selectedEntries = selectedEntries + entry
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            HomeTab.Calendar -> Text("Calendar View", style = MaterialTheme.typography.titleLarge)
                            HomeTab.Presence -> Text("Presence View", style = MaterialTheme.typography.titleLarge)
                            HomeTab.Note -> Text("Notes View", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }
    }

    if (showAddEntryDialog) {
        HomeEntryDialog(
            onDismiss = { showAddEntryDialog = false },
            onConfirm = { title, subtitle ->
                viewModel.saveHomeEntry(title, subtitle, firstName.take(1))
                showAddEntryDialog = false
            }
        )
    }

    if (showDeleteEntryDialog && selectedEntries.isNotEmpty()) {
        val firstNameToDelete = selectedEntries.first().title
        val deleteMessage = if (selectedEntries.size == 1) {
            "Are you sure you want to delete $firstNameToDelete?"
        } else {
            "Are you sure you want to delete ${selectedEntries.size} entries?"
        }

        AlertDialog(
            onDismissRequest = { showDeleteEntryDialog = false },
            title = { Text("Confirm Delete") },
            text = { Text(deleteMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedEntries.forEach { viewModel.deleteHomeEntry(it) }
                        selectedEntries = emptySet()
                        showDeleteEntryDialog = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteEntryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showQrScanner) {
        QrScannerDialog(
            onDismiss = { showQrScanner = false },
            onResult = { result ->
                // Handle scanned QR result
                homeSearchQuery = result
            }
        )
    }
}

@Composable
fun HomeFabPill(
    label: String,
    onClick: () -> Unit
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = CircleShape,
        modifier = Modifier.height(40.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainDashboardPreview() {
    DiBadgeTheme(darkTheme = false) {
        MainDashboard(
            selectedTab = HomeTab.Home,
            onTabSelected = {},
            onCalendarTabClick = {},
            reminderCount = 2,
            notificationCount = 5
        )
    }
}
