package com.fabxdi.dibadge

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabxdi.dibadge.ui.auth.AuthScreen
import com.fabxdi.dibadge.ui.calendar.CalendarScreen
import com.fabxdi.dibadge.ui.home.HomeTab
import com.fabxdi.dibadge.ui.home.MainDashboard
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.viewmodel.AuthViewModel
import com.fabxdi.dibadge.viewmodel.ReminderViewModel
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Handle permission result
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkNotificationPermission()
        val reminderIdToOpen = intent.getIntExtra("OPEN_REMINDER_ID", -1)

        setContent {
            DiBadgeTheme {
                val authViewModel: AuthViewModel = viewModel()
                val currentUser by authViewModel.currentUser.collectAsState()

                if (currentUser == null) {
                    AuthScreen(viewModel = authViewModel)
                } else {
                    val user = currentUser
                    val displayName = user?.displayName?.ifBlank { null }
                        ?: user?.email?.substringBefore("@")
                        ?: "User"

                    val reminderViewModel: ReminderViewModel = viewModel()
                    val todayRemindersCount by reminderViewModel.todayRemindersCount.collectAsState()

                    // State to manage the currently selected tab
                    var selectedTab by remember { mutableStateOf(HomeTab.Home) }
                    var initialReminderToEdit by rememberSaveable { mutableStateOf<Int?>(null) }
                    var initialDateForCalendar by remember { mutableStateOf<LocalDate?>(null) }

                    LaunchedEffect(reminderIdToOpen) {
                        if (reminderIdToOpen != -1) {
                            selectedTab = HomeTab.Calendar
                            initialReminderToEdit = reminderIdToOpen
                        }
                    }

                    if (selectedTab == HomeTab.Calendar) {
                        CalendarScreen(
                            onTabClick = { tab ->
                                selectedTab = tab
                            },
                            onBackClick = {
                                selectedTab = HomeTab.Home
                            },
                            viewModel = reminderViewModel,
                            initialReminderId = initialReminderToEdit,
                            onReminderOpened = { initialReminderToEdit = null },
                            initialSelectedDate = initialDateForCalendar
                        )
                    } else {
                        MainDashboard(
                            selectedTab = selectedTab,
                            onTabSelected = { tab ->
                                selectedTab = tab
                                if (tab != HomeTab.Calendar) {
                                    initialDateForCalendar = null
                                }
                            },
                            onCalendarTabClick = {
                                selectedTab = HomeTab.Calendar
                                initialDateForCalendar = null
                            },
                            onReminderClick = {
                                initialDateForCalendar = LocalDate.now()
                                selectedTab = HomeTab.Calendar
                            },
                            reminderCount = todayRemindersCount,
                            notificationCount = 0,
                            firstName = displayName,
                            onSignOut = { authViewModel.signOut() }
                        )
                    }
                }
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}