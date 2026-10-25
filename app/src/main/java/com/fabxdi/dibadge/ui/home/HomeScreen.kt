package com.fabxdi.dibadge.ui.home

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabxdi.dibadge.R
import com.fabxdi.dibadge.data.HomeEntryEntity
import com.fabxdi.dibadge.ui.theme.AppThemeMode
import com.fabxdi.dibadge.ui.components.DiBadgeFab
import com.fabxdi.dibadge.ui.calendar.leave.LeaveFormScreen
import com.fabxdi.dibadge.ui.calendar.extrahours.ExtraHoursFormScreen
import com.fabxdi.dibadge.ui.connections.ConnectionsScreen
import com.fabxdi.dibadge.data.InboxEntity
import com.fabxdi.dibadge.ui.inbox.InboxScreen
import com.fabxdi.dibadge.ui.inbox.MessageDetailScreen
import com.fabxdi.dibadge.viewmodel.InboxViewModel
import com.fabxdi.dibadge.ui.home.home_entry.chat.ChatScreen
import com.fabxdi.dibadge.ui.home.QrScannerScreen
import com.fabxdi.dibadge.ui.home.MyQrCodeScreen
import com.fabxdi.dibadge.ui.my_activity.MyActivityScreen
import com.fabxdi.dibadge.ui.my_docs.MyDocsScreen
import com.fabxdi.dibadge.ui.my_forms.MyFormsScreen
import com.fabxdi.dibadge.ui.my_tasks.MyTasksScreen
import com.fabxdi.dibadge.ui.my_tasks.to_do.ReminderFormScreen
import com.fabxdi.dibadge.ui.profile.ProfileScreen
import com.fabxdi.dibadge.ui.settings.SettingsScreen
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.viewmodel.LeaveOvertimeViewModel
import com.fabxdi.dibadge.viewmodel.ToDoViewModel
import kotlinx.coroutines.launch

enum class HomeTab(
    val label: String,
    @DrawableRes val iconRes: Int
) {
    Home("diBadge", R.drawable.ic_dibadge_nav),
    Connections("Connections", R.drawable.ic_connections),
    MyTasks("My Tasks", R.drawable.ic_my_tasks),
    Note("Inbox", R.drawable.ic_inbox)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboard(
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    onCalendarTabClick: () -> Unit,
    onReminderClick: () -> Unit = {},
    reminderCount: Int = 0,
    hasMyTasksDot: Boolean = reminderCount > 0,
    notificationCount: Int = 0,
    firstName: String = "User",
    fullName: String = firstName,
    email: String = "",
    photoUrl: String = "",
    onSettingsClick: () -> Unit = {},
    onSignOut: () -> Unit = {},
    initialReminderIdToEdit: Int? = null,
    openSnoozeTimePicker: Boolean = false,
    onSnoozeHandled: () -> Unit = {},
    viewModel: ToDoViewModel = viewModel(),
    leaveOvertimeViewModel: LeaveOvertimeViewModel = viewModel(),
    inboxViewModel: InboxViewModel = viewModel()
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var homeSearchQuery by remember { mutableStateOf("") }
    var showQrScanner by remember { mutableStateOf(false) }
    var showQrSheet by remember { mutableStateOf(false) }
    var showNewGroupSheet by remember { mutableStateOf(false) }
    var selectedEntries by remember { mutableStateOf(setOf<HomeEntryEntity>()) }
    var showDeleteEntryDialog by remember { mutableStateOf(false) }
    var selectedEntryForChat by remember { mutableStateOf<HomeEntryEntity?>(null) }
    var selectedInboxItemForDetail by remember { mutableStateOf<InboxEntity?>(null) }

    val unreadInboxCount by inboxViewModel.unreadCount.collectAsState()

    // Handle device back button to clear selection
    BackHandler(enabled = selectedEntries.isNotEmpty()) {
        selectedEntries = emptySet()
    }

    var isFabExpanded by remember { mutableStateOf(false) }
    var showReminderForm by remember { mutableStateOf(false) }
    var showMyActivityScreen by remember { mutableStateOf(false) }
    var showMyDocsScreen by remember { mutableStateOf(false) }
    var showMyFormsScreen by remember { mutableStateOf(false) }
    var showProfileScreen by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    var showQrScannerScreen by remember { mutableStateOf(false) }
    var showMyQrCodeScreen by remember { mutableStateOf(false) }
    var showOvertimeForm by remember { mutableStateOf(false) }
    var showLeaveForm by remember { mutableStateOf(false) }

    if (showQrScannerScreen) {
        QrScannerScreen(
            userCode = "7K2M9QX4B",
            onBack = { showQrScannerScreen = false }
        )
        return
    }

    if (showMyQrCodeScreen) {
        MyQrCodeScreen(
            userFullName = fullName,
            userCode = "7K2M9QX4B",
            onBack = { showMyQrCodeScreen = false }
        )
        return
    }

    if (showMyDocsScreen) {
        MyDocsScreen(
            onBack = { showMyDocsScreen = false }
        )
        return
    }

    if (showMyFormsScreen) {
        MyFormsScreen(
            onBack = { showMyFormsScreen = false }
        )
        return
    }

    if (showSettingsScreen) {
        SettingsScreen(
            userEmail = email,
            onBack = { showSettingsScreen = false },
            onSignOut = {
                showSettingsScreen = false
                onSignOut()
            }
        )
        return
    }

    if (showProfileScreen) {
        ProfileScreen(
            onBackClick = { showProfileScreen = false }
        )
        return
    }

    if (showMyActivityScreen) {
        MyActivityScreen(
            onBack = { showMyActivityScreen = false },
            onOpenRelatedItem = { relatedId ->
                showMyActivityScreen = false
            }
        )
        return
    }

    if (showReminderForm) {
        ReminderFormScreen(
            onSave = { title, content, date, start, end, repeat, time, isAlarm, attachments, id, remindDate ->
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
                    id,
                    remindDate
                )
                showReminderForm = false
            },
            onDelete = { /* N/A for new */ },
            onCancel = { showReminderForm = false }
        )
        return
    }

    if (showLeaveForm) {
        val allLeaves by leaveOvertimeViewModel.allLeaves.collectAsState(initial = emptyList())
        LeaveFormScreen(
            existingLeaves = allLeaves,
            onApply = { startDate, endDate, type, reason, attachments, id, action ->
                leaveOvertimeViewModel.saveLeave(startDate, endDate, type, reason, attachments, id)
                showLeaveForm = false
            },
            onClose = { showLeaveForm = false }
        )
        return
    }

    if (showOvertimeForm) {
        val allLeaves by leaveOvertimeViewModel.allLeaves.collectAsState(initial = emptyList())
        ExtraHoursFormScreen(
            existingLeaves = allLeaves,
            onApply = { date, start, end, description, id ->
                leaveOvertimeViewModel.saveOvertime(date, start, end, description, id)
                showOvertimeForm = false
            },
            onDelete = { overtime ->
                leaveOvertimeViewModel.deleteOvertime(overtime)
                showOvertimeForm = false
            },
            onClose = { showOvertimeForm = false }
        )
        return
    }

    if (selectedInboxItemForDetail != null) {
        MessageDetailScreen(
            item = selectedInboxItemForDetail!!,
            onBack = { selectedInboxItemForDetail = null },
            inboxViewModel = inboxViewModel
        )
        return
    }

    if (selectedEntryForChat != null) {
        ChatScreen(
            entry = selectedEntryForChat!!,
            onBack = { selectedEntryForChat = null },
            onOpenMyForms = {
                selectedEntryForChat = null
                showMyFormsScreen = true
            }
        )
        return
    }

    if (selectedTab == HomeTab.Note) {
        InboxScreen(
            onOpenMessageDetail = { item ->
                selectedInboxItemForDetail = item
            },
            bottomBar = {
                AppBottomBar(
                    selectedTab = selectedTab,
                    onTabSelected = onTabSelected,
                    hasMyTasksDot = hasMyTasksDot,
                    hasConnectionsDot = false,
                    hasInboxDot = unreadInboxCount > 0
                )
            },
            viewModel = inboxViewModel
        )
        return
    }

    if (selectedTab == HomeTab.Connections) {
        ConnectionsScreen(
            onBack = { onTabSelected(HomeTab.Home) },
            bottomBar = { AppBottomBar(selectedTab, onTabSelected) }
        )
        return
    }

    if (selectedTab == HomeTab.MyTasks) {
        MyTasksScreen(
            onBack = { onTabSelected(HomeTab.Home) },
            bottomBar = { AppBottomBar(selectedTab, onTabSelected) },
            initialReminderIdToEdit = initialReminderIdToEdit,
            openSnoozeTimePicker = openSnoozeTimePicker,
            onSnoozeHandled = onSnoozeHandled
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
                        fullName = fullName,
                        email = email,
                        photoUrl = photoUrl,
                        onCloseDrawer = { scope.launch { drawerState.close() } },
                        onProfileClick = { showProfileScreen = true },
                        onCalendarClick = onCalendarTabClick,
                        onMyActivityClick = { showMyActivityScreen = true },
                        onMyDocsClick = { showMyDocsScreen = true },
                        onFormsClick = { showMyFormsScreen = true },
                        onSettingsClick = { showSettingsScreen = true },
                        onSignOut = onSignOut
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
                                nickname = "",
                                reminderCount = reminderCount,
                                searchQuery = homeSearchQuery,
                                onSearchQueryChange = { homeSearchQuery = it },
                                onMenuClick = { scope.launch { drawerState.open() } },
                                onQrClick = { showQrSheet = true },
                                selectedCount = selectedEntries.size,
                                onDeleteClick = { showDeleteEntryDialog = true },
                                onClearSelection = { selectedEntries = emptySet() }
                            )
                        }
                    },
                    bottomBar = {
                        AppBottomBar(
                            selectedTab = selectedTab,
                            onTabSelected = onTabSelected,
                            hasMyTasksDot = hasMyTasksDot,
                            hasConnectionsDot = false,
                            hasInboxDot = unreadInboxCount > 0
                        )
                    },
                    floatingActionButton = {
                        DiBadgeFab(
                            onClick = { showNewGroupSheet = true },
                            contentDescription = "New group",
                            modifier = Modifier.padding(end = 16.dp, bottom = 16.dp)
                        )
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
                                focusManager.clearFocus()
                            }
                    ) {
                        when (selectedTab) {
                            HomeTab.Home -> {
                                val homeEntries by viewModel.allHomeEntries.collectAsState()

                                LaunchedEffect(homeEntries) {
                                    val defaultEntries = homeEntries.filter { it.title == "diBadge Group" }
                                    for (entry in defaultEntries) {
                                        viewModel.deleteHomeEntry(entry)
                                    }
                                }

                                val filteredEntries = remember(homeEntries, homeSearchQuery) {
                                    homeEntries.filter { 
                                        it.title.contains(homeSearchQuery, ignoreCase = true) || 
                                        it.subtitle.contains(homeSearchQuery, ignoreCase = true)
                                    }
                                }

                                if (filteredEntries.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (homeSearchQuery.isEmpty()) {
                                            HomeEmptyState()
                                        } else {
                                            Text(
                                                text = "No matches found",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
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
                            HomeTab.Connections -> {}
                            HomeTab.MyTasks -> {}
                            HomeTab.Note -> {}
                        }
                    }
                }
            }
        }
    }

    if (showNewGroupSheet) {
        NewGroupBottomSheet(
            onDismiss = { showNewGroupSheet = false },
            onConfirm = { title, subtitle ->
                viewModel.saveHomeEntry(title, subtitle, firstName.take(1))
                showNewGroupSheet = false
            }
        )
    }

    if (showQrSheet) {
        QrButtonSheet(
            onScanQrClick = {
                showQrSheet = false
                showQrScannerScreen = true
            },
            onMyQrClick = {
                showQrSheet = false
                showMyQrCodeScreen = true
            },
            onDismiss = { showQrSheet = false }
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
}

@Composable
fun AppBottomBar(
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    hasMyTasksDot: Boolean = false,
    hasConnectionsDot: Boolean = false,
    hasInboxDot: Boolean = false
) {
    val diBadgeColors = DiBadgeTheme.colors
    val colors = MaterialTheme.colorScheme

    NavigationBar(
        containerColor = colors.surface,
        tonalElevation = 0.dp
    ) {
        HomeTab.entries.forEach { tab ->
            val isSelected = selectedTab == tab

            val iconSize by animateDpAsState(
                targetValue = if (isSelected) 28.dp else 22.dp,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                label = "bottomBarIconSize"
            )

            val showDot = when (tab) {
                HomeTab.MyTasks -> hasMyTasksDot
                HomeTab.Connections -> hasConnectionsDot
                HomeTab.Note -> hasInboxDot
                else -> false
            }

            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Box {
                        if (tab == HomeTab.Home && isSelected) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_dibadge_mark),
                                contentDescription = tab.label,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(iconSize)
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = tab.iconRes),
                                contentDescription = tab.label,
                                modifier = Modifier.size(iconSize)
                            )
                        }

                        if (showDot) {
                            Surface(
                                modifier = Modifier
                                    .size(12.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(x = 6.dp, y = (-3).dp),
                                shape = CircleShape,
                                color = diBadgeColors.accent,
                                border = BorderStroke(2.dp, colors.surface)
                            ) {}
                        }
                    }
                },
                label = {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.onBackground,
                    selectedTextColor = colors.onBackground,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = diBadgeColors.tabInactive,
                    unselectedTextColor = diBadgeColors.tabInactive
                )
            )
        }
    }
}

@Composable
fun HomeEmptyState(
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_people_empty),
            contentDescription = null,
            tint = DiBadgeTheme.colors.tabInactive,
            modifier = Modifier.size(44.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Get started",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Tap + to create a group, or search\nto message a connection",
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 18.2.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(name = "Home Empty State - Light", widthDp = 412, showBackground = true)
@Composable
fun HomeEmptyStateLightPreview() {
    DiBadgeTheme(themeMode = AppThemeMode.LIGHT) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                HomeEmptyState()
            }
        }
    }
}

@Preview(
    name = "Home Empty State - Dark",
    widthDp = 412,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun HomeEmptyStateDarkPreview() {
    DiBadgeTheme(themeMode = AppThemeMode.DARK) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                HomeEmptyState()
            }
        }
    }
}
