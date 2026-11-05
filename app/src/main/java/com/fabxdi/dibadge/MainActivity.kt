package com.fabxdi.dibadge

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.fabxdi.dibadge.ui.settings.PasscodeStore
import com.fabxdi.dibadge.ui.settings.PinVerifyScreen
import com.fabxdi.dibadge.ui.settings.SettingsDialog
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.AlarmSoundManager
import com.fabxdi.dibadge.util.capitalizeWords
import com.fabxdi.dibadge.util.fullName
import com.fabxdi.dibadge.util.getGreetingName
import com.fabxdi.dibadge.viewmodel.AuthViewModel
import com.fabxdi.dibadge.viewmodel.LeaveOvertimeViewModel
import com.fabxdi.dibadge.viewmodel.ReminderViewModel
import com.fabxdi.dibadge.viewmodel.ThemeViewModel
import com.google.firebase.firestore.FirebaseFirestore
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private var reminderIdToOpenState by mutableIntStateOf(-1)
    private var openSnoozeTimePickerState by mutableStateOf(false)

    /** True while the passcode screen must cover the app. */
    private var isLocked by mutableStateOf(false)

    private val passcodePrefs by lazy { getSharedPreferences("passcode_prefs", Context.MODE_PRIVATE) }
    private val appPrefs by lazy { getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _: Boolean ->
        // Nothing to do. Reminder screens explain what is missing when they need the permission.
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Opening the app from a reminder notification must silence a ringing alarm
        AlarmSoundManager.stopAlarmSound()
        val newId = intent.getIntExtra("OPEN_REMINDER_ID", -1)
        if (newId != -1) {
            reminderIdToOpenState = newId
            openSnoozeTimePickerState = intent.getBooleanExtra("OPEN_SNOOZE_TIME_PICKER", false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        checkNotificationPermission()
        AlarmSoundManager.stopAlarmSound()

        if (savedInstanceState == null) {
            // Fresh start: locked if a passcode is set
            isLocked = PasscodeStore.isSet(this)
            val initialId = intent.getIntExtra("OPEN_REMINDER_ID", -1)
            if (initialId != -1) {
                reminderIdToOpenState = initialId
                openSnoozeTimePickerState = intent.getBooleanExtra("OPEN_SNOOZE_TIME_PICKER", false)
            }
        } else {
            // Rotation or restore: keep what we had, and do not re-open a reminder that was already handled
            isLocked = savedInstanceState.getBoolean(KEY_LOCKED, PasscodeStore.isSet(this))
            reminderIdToOpenState = savedInstanceState.getInt(KEY_REMINDER_ID, -1)
            openSnoozeTimePickerState = savedInstanceState.getBoolean(KEY_SNOOZE, false)
        }

        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            val themeMode by themeViewModel.themeMode.collectAsState()

            DiBadgeTheme(themeMode = themeMode) {
                val authViewModel: AuthViewModel = viewModel()
                val currentUser by authViewModel.currentUser.collectAsState()

                var showSettingsDialog by remember { mutableStateOf(false) }

                if (showSettingsDialog) {
                    SettingsDialog(
                        currentThemeMode = themeMode,
                        onThemeModeSelected = { mode ->
                            themeViewModel.setThemeMode(mode)
                        },
                        onDismissRequest = { showSettingsDialog = false }
                    )
                }

                if (currentUser == null) {
                    AuthScreen(viewModel = authViewModel)
                } else if (isLocked) {
                    // Nothing of the app is composed until the passcode is entered
                    PinVerifyScreen(
                        title = "Enter passcode",
                        subtitle = "Enter your 6-digit passcode to open diBadge",
                        onVerified = { isLocked = false },
                        onCancel = { moveTaskToBack(true) }
                    )
                } else {
                    val user = currentUser
                    val uid = user?.uid ?: ""
                    val displayName = user?.displayName?.ifBlank { null }
                        ?: user?.email?.substringBefore("@")
                        ?: "User"

                    var userNickname by remember(uid) { mutableStateOf("") }
                    var userFirstName by remember(uid) { mutableStateOf("") }
                    var userMiddleName by remember(uid) { mutableStateOf("") }
                    var userLastName by remember(uid) { mutableStateOf("") }
                    var userPhotoUrl by remember(uid) { mutableStateOf("") }

                    DisposableEffect(uid) {
                        val listener = if (uid.isNotBlank()) {
                            FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(uid)
                                .addSnapshotListener { snapshot, error ->
                                    // On an error keep what is shown; a missing document clears the profile
                                    if (error != null || snapshot == null) return@addSnapshotListener
                                    userNickname = snapshot.getString("nickname") ?: ""
                                    userFirstName = snapshot.getString("firstName") ?: ""
                                    userMiddleName = snapshot.getString("middleName") ?: ""
                                    userLastName = snapshot.getString("lastName") ?: ""
                                    userPhotoUrl = snapshot.getString("photoUrl") ?: ""
                                }
                        } else null

                        onDispose {
                            listener?.remove()
                        }
                    }

                    val computedFullName = remember(userFirstName, userMiddleName, userLastName, displayName) {
                        val fn = fullName(userFirstName, userMiddleName, userLastName)
                        if (fn.isNotBlank()) fn.capitalizeWords() else displayName
                    }

                    val bannerGreetingName = remember(userFirstName, userNickname, displayName) {
                        getGreetingName(
                            firstName = if (userFirstName.isNotBlank()) userFirstName else displayName,
                            nickname = userNickname
                        )
                    }

                    // Keyed by user so a different account never inherits the previous account's screens
                    val reminderViewModel: ReminderViewModel = viewModel(key = "reminders_$uid")
                    val leaveOvertimeViewModel: LeaveOvertimeViewModel = viewModel(key = "leave_$uid")
                    val todayRemindersCount by reminderViewModel.todayRemindersCount.collectAsState()
                    val hasMyTasksDot by reminderViewModel.hasOverdueOrTodayTasks.collectAsState()

                    // Survive rotation and process death
                    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.Home) }
                    var showCalendarScreen by rememberSaveable { mutableStateOf(false) }
                    var calendarEpochDay by rememberSaveable { mutableLongStateOf(NO_DATE) }
                    val initialDateForCalendar: LocalDate? =
                        if (calendarEpochDay == NO_DATE) null else LocalDate.ofEpochDay(calendarEpochDay)

                    LaunchedEffect(reminderIdToOpenState) {
                        if (reminderIdToOpenState != -1) {
                            showCalendarScreen = false
                            selectedTab = HomeTab.MyTasks
                        }
                    }

                    // System Back: calendar -> dashboard, other tab -> Home tab
                    BackHandler(enabled = showCalendarScreen) {
                        showCalendarScreen = false
                    }
                    BackHandler(enabled = !showCalendarScreen && selectedTab != HomeTab.Home) {
                        selectedTab = HomeTab.Home
                    }

                    if (showCalendarScreen) {
                        CalendarScreen(
                            onBackClick = {
                                showCalendarScreen = false
                            },
                            viewModel = leaveOvertimeViewModel,
                            initialSelectedDate = initialDateForCalendar
                        )
                    } else {
                        MainDashboard(
                            selectedTab = selectedTab,
                            onTabSelected = { tab ->
                                selectedTab = tab
                                calendarEpochDay = NO_DATE
                            },
                            onCalendarTabClick = {
                                showCalendarScreen = true
                                calendarEpochDay = NO_DATE
                            },
                            onReminderClick = {
                                calendarEpochDay = LocalDate.now().toEpochDay()
                                showCalendarScreen = true
                            },
                            reminderCount = todayRemindersCount,
                            hasMyTasksDot = hasMyTasksDot,
                            notificationCount = 0, // TODO: feed the real Inbox count here
                            firstName = if (userFirstName.isNotBlank()) userFirstName else displayName,
                            fullName = computedFullName,
                            email = user?.email ?: "",
                            photoUrl = userPhotoUrl,
                            onSettingsClick = { showSettingsDialog = true },
                            onSignOut = {
                                themeViewModel.resetToSystem()
                                authViewModel.signOut()
                            },
                            initialReminderIdToEdit = if (reminderIdToOpenState != -1) reminderIdToOpenState else null,
                            openSnoozeTimePicker = openSnoozeTimePickerState,
                            onSnoozeHandled = {
                                reminderIdToOpenState = -1
                                openSnoozeTimePickerState = false
                                intent.removeExtra("OPEN_REMINDER_ID")
                                intent.removeExtra("OPEN_SNOOZE_TIME_PICKER")
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_LOCKED, isLocked)
        outState.putInt(KEY_REMINDER_ID, reminderIdToOpenState)
        outState.putBoolean(KEY_SNOOZE, openSnoozeTimePickerState)
    }

    override fun onStart() {
        super.onStart()
        applyLockAfterBackground()
        // Hide the app preview in the recent-apps list while a passcode is on (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(!PasscodeStore.isSet(this))
        }
    }

    override fun onStop() {
        super.onStop()
        // A rotation also stops the activity; that is not "leaving the app"
        if (!isChangingConfigurations && PasscodeStore.isSet(this)) {
            passcodePrefs.edit()
                .putLong(KEY_LAST_STOP, SystemClock.elapsedRealtime())
                .putBoolean(KEY_PENDING_CHECK, true)
                .apply()
        }
    }

    /** Locks again if the app was away for at least the "Lock after" time. */
    private fun applyLockAfterBackground() {
        if (!PasscodeStore.isSet(this)) {
            isLocked = false
            passcodePrefs.edit().putBoolean(KEY_PENDING_CHECK, false).apply()
            return
        }
        if (!passcodePrefs.getBoolean(KEY_PENDING_CHECK, false)) return

        val last = passcodePrefs.getLong(KEY_LAST_STOP, 0L)
        val elapsed = SystemClock.elapsedRealtime() - last
        val limit = when (passcodePrefs.getString("lock_after", "Immediately")) {
            "1 minute" -> 60_000L
            "5 minutes" -> 5 * 60_000L
            "15 minutes" -> 15 * 60_000L
            else -> 0L
        }
        // last <= 0 or a negative gap means the phone restarted: lock
        if (last <= 0L || elapsed < 0L || elapsed >= limit) isLocked = true
        passcodePrefs.edit().putBoolean(KEY_PENDING_CHECK, false).apply()
    }

    /** Asked once on first launch. Reminder screens explain what is missing if it was refused. */
    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) return
        if (appPrefs.getBoolean("asked_notification_permission", false)) return
        appPrefs.edit().putBoolean("asked_notification_permission", true).apply()
        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private companion object {
        const val KEY_LOCKED = "app_locked"
        const val KEY_REMINDER_ID = "reminder_id_to_open"
        const val KEY_SNOOZE = "open_snooze_picker"
        const val KEY_LAST_STOP = "last_stop_elapsed"
        const val KEY_PENDING_CHECK = "pending_lock_check"
        const val NO_DATE = Long.MIN_VALUE
    }
}
