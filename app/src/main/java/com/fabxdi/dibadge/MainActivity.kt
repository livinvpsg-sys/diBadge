package com.fabxdi.dibadge

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import kotlinx.coroutines.delay
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.google.firebase.firestore.FirebaseFirestore
import com.fabxdi.dibadge.util.capitalizeWords
import com.fabxdi.dibadge.util.fullName
import com.fabxdi.dibadge.util.getGreetingName
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabxdi.dibadge.ui.auth.AuthScreen
import com.fabxdi.dibadge.ui.auth.BrandSplash
import com.fabxdi.dibadge.ui.auth.WelcomeScreen
import com.fabxdi.dibadge.ui.calendar.CalendarScreen
import com.fabxdi.dibadge.ui.home.HomeTab
import com.fabxdi.dibadge.ui.home.MainDashboard
import com.fabxdi.dibadge.ui.settings.SettingsDialog
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.AlarmSoundManager
import com.fabxdi.dibadge.util.LiveProfilePhoto
import com.fabxdi.dibadge.util.PushNotifier
import com.fabxdi.dibadge.util.PushTokens
import com.fabxdi.dibadge.util.fixDownloadUrl
import com.fabxdi.dibadge.viewmodel.AuthViewModel
import com.fabxdi.dibadge.viewmodel.LeaveOvertimeViewModel
import com.fabxdi.dibadge.viewmodel.ReminderViewModel
import com.fabxdi.dibadge.viewmodel.ThemeViewModel
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    companion object {
        /** True once the launch screen has been shown, so turning the phone does not show it again. */
        private var splashShown = false
    }
    private var reminderIdToOpenState by mutableIntStateOf(-1)
    private var openSnoozeTimePickerState by mutableStateOf(false)
    // Chat to open because a push notification was tapped
    private var chatGroupIdToOpenState by mutableStateOf<String?>(null)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Handle permission result
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(PushNotifier.EXTRA_OPEN_CHAT)?.let { chatGroupIdToOpenState = it }
        val newId = intent.getIntExtra("OPEN_REMINDER_ID", -1)
        if (newId != -1) {
            reminderIdToOpenState = newId
            openSnoozeTimePickerState = intent.getBooleanExtra("OPEN_SNOOZE_TIME_PICKER", false)
        }
    }

    // TEMPORARY: launch timing, so we can see where the startup time goes
    private fun launchLog(what: String) {
        val ms = android.os.SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime()
        android.util.Log.d("DiBadgeLaunch", "$what at ${ms}ms since process start")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        launchLog("onCreate start")
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false

        checkNotificationPermission()
        PushNotifier.ensureChannel(this)
        AlarmSoundManager.stopAlarmSound()
        intent.getStringExtra(PushNotifier.EXTRA_OPEN_CHAT)?.let { chatGroupIdToOpenState = it }

        val initialId = intent.getIntExtra("OPEN_REMINDER_ID", -1)
        if (initialId != -1) {
            reminderIdToOpenState = initialId
            openSnoozeTimePickerState = intent.getBooleanExtra("OPEN_SNOOZE_TIME_PICKER", false)
        }

        launchLog("before setContent")
        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            val themeMode by themeViewModel.themeMode.collectAsState()

            DiBadgeTheme(themeMode = themeMode) {
                // Launch screen: shown once when the app starts, over the app while it gets ready
                var showSplash by remember { mutableStateOf(!splashShown) }
                // The app itself is built only after the launch screen has been drawn, so the
                // launch screen appears at once and is not held up by the app getting ready
                var appReady by remember { mutableStateOf(splashShown) }
                LaunchedEffect(Unit) {
                    if (showSplash) {
                        launchLog("launch screen composed")
                        withFrameNanos { }      // wait until the launch screen is on screen
                        launchLog("launch screen drawn")
                        delay(500)              // let its text finish easing in
                        appReady = true         // app builds underneath the launch screen
                        launchLog("app building")
                        // Building the app can hold up the screen for a moment, so the time the
                        // launch screen stays is counted from after that, not before
                        withFrameNanos { }
                        withFrameNanos { }
                        launchLog("app built")
                        delay(1200)
                        splashShown = true
                        showSplash = false
                        launchLog("launch screen removed")
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    if (appReady) {
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
                        } else {
                            val user = currentUser
                            // Save this phone's push address once signed in
                            LaunchedEffect(user?.uid) {
                                if (user?.uid != null) PushTokens.register()
                            }
                            val displayName = user?.displayName?.ifBlank { null }
                                ?: user?.email?.substringBefore("@")
                                ?: "User"

                            var userNickname by remember { mutableStateOf("") }
                            var userFirstName by remember { mutableStateOf("") }
                            var userMiddleName by remember { mutableStateOf("") }
                            var userLastName by remember { mutableStateOf("") }
                            var userPhotoUrl by remember { mutableStateOf("") }

                            // The photo as the Profile screen has it right now (null = use the saved one)
                            val livePhoto by LiveProfilePhoto.url.collectAsState()

                            DisposableEffect(user?.uid) {
                                val uid = user?.uid
                                LiveProfilePhoto.url.value = null
                                val listener = if (uid != null) {
                                    FirebaseFirestore.getInstance()
                                        .collection("users")
                                        .document(uid)
                                        .addSnapshotListener { snapshot, _ ->
                                            if (snapshot != null && snapshot.exists()) {
                                                userNickname = snapshot.getString("nickname") ?: ""
                                                userFirstName = snapshot.getString("firstName") ?: ""
                                                userMiddleName = snapshot.getString("middleName") ?: ""
                                                userLastName = snapshot.getString("lastName") ?: ""
                                                userPhotoUrl = fixDownloadUrl(snapshot.getString("photoUrl") ?: "")
                                            }
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

                            val reminderViewModel: ReminderViewModel = viewModel()
                            val leaveOvertimeViewModel: LeaveOvertimeViewModel = viewModel()
                            val todayRemindersCount by reminderViewModel.todayRemindersCount.collectAsState()
                            val hasMyTasksDot by reminderViewModel.hasOverdueOrTodayTasks.collectAsState()

                            // State to manage the currently selected tab
                            var selectedTab by remember { mutableStateOf(HomeTab.Home) }
                            var showCalendarScreen by remember { mutableStateOf(false) }
                            var initialDateForCalendar by remember { mutableStateOf<LocalDate?>(null) }

                            LaunchedEffect(reminderIdToOpenState) {
                                if (reminderIdToOpenState != -1) {
                                    showCalendarScreen = false
                                    selectedTab = HomeTab.MyTasks
                                }
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
                                        initialDateForCalendar = null
                                    },
                                    onCalendarTabClick = {
                                        showCalendarScreen = true
                                        initialDateForCalendar = null
                                    },
                                    onReminderClick = {
                                        initialDateForCalendar = LocalDate.now()
                                        showCalendarScreen = true
                                    },
                                    reminderCount = todayRemindersCount,
                                    hasMyTasksDot = hasMyTasksDot,
                                    notificationCount = 0,
                                    // Banner name: nickname if set, otherwise the first name only (never the full name)
                                    firstName = if (userFirstName.isNotBlank()) userFirstName.trim() else displayName.trim().substringBefore(" "),
                                    nickname = userNickname,
                                    fullName = computedFullName,
                                    email = user?.email ?: "",
                                    photoUrl = livePhoto ?: userPhotoUrl,
                                    onSettingsClick = { showSettingsDialog = true },
                                    openChatGroupId = chatGroupIdToOpenState,
                                    onChatOpenHandled = {
                                        chatGroupIdToOpenState = null
                                        intent.removeExtra(PushNotifier.EXTRA_OPEN_CHAT)
                                    },
                                    onSignOut = {
                                        PushTokens.unregister()
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
                    if (showSplash) BrandSplash()
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