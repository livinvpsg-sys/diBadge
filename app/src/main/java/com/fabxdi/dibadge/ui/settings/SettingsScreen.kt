package com.fabxdi.dibadge.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabxdi.dibadge.ui.theme.AppThemeMode
import com.fabxdi.dibadge.viewmodel.ThemeViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

enum class SettingsSubPage {
    MAIN,
    SUBSCRIPTION,
    SOUNDS_VIBRATION,
    PASSCODE,
    CREATE_PASSCODE,
    BLOCKED_USERS,
    DATA_PRIVACY,
    SET_PASSWORD
}

@Composable
fun SettingsScreen(
    userEmail: String = "",
    onBack: () -> Unit,
    onSignOut: () -> Unit = {},
    themeViewModel: ThemeViewModel = viewModel()
) {
    val context = LocalContext.current
    val currentThemeMode by themeViewModel.themeMode.collectAsState()

    var activeSubPage by remember { mutableStateOf(SettingsSubPage.MAIN) }
    var showThemeSheet by remember { mutableStateOf(false) }

    val passcodePrefs = remember(context) { context.getSharedPreferences("passcode_prefs", Context.MODE_PRIVATE) }
    var isPasscodeOn by remember { mutableStateOf(passcodePrefs.getString("passcode_hash", null) != null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    BackHandler(enabled = true) {
        if (activeSubPage != SettingsSubPage.MAIN) {
            activeSubPage = SettingsSubPage.MAIN
        } else {
            onBack()
        }
    }

    when (activeSubPage) {
        SettingsSubPage.SUBSCRIPTION -> {
            SubscriptionScreen(
                currentPlanName = "Free",
                onBack = { activeSubPage = SettingsSubPage.MAIN }
            )
            return
        }
        SettingsSubPage.SOUNDS_VIBRATION -> {
            SoundsVibrationScreen(
                onBack = { activeSubPage = SettingsSubPage.MAIN }
            )
            return
        }
        SettingsSubPage.PASSCODE -> {
            PasscodeScreen(
                onBack = { activeSubPage = SettingsSubPage.MAIN }
            )
            return
        }
        SettingsSubPage.CREATE_PASSCODE -> {
            PinPadScreen(
                title = "Create passcode",
                subtitle = "Enter a 6-digit passcode",
                onPasscodeEntered = { newPin ->
                    passcodePrefs.edit().putString("passcode_hash", newPin).apply()
                    isPasscodeOn = true
                    activeSubPage = SettingsSubPage.PASSCODE
                },
                onCancel = { activeSubPage = SettingsSubPage.MAIN }
            )
            return
        }
        SettingsSubPage.BLOCKED_USERS -> {
            BlockedUsersScreen(
                onBack = { activeSubPage = SettingsSubPage.MAIN }
            )
            return
        }
        SettingsSubPage.DATA_PRIVACY -> {
            DataPrivacyScreen(
                userEmail = userEmail,
                onBack = { activeSubPage = SettingsSubPage.MAIN },
                onDeleteAccountConfirmed = {
                    onSignOut()
                }
            )
            return
        }
        SettingsSubPage.SET_PASSWORD -> {
            val currentUser = FirebaseAuth.getInstance().currentUser
            val hasPassword = currentUser?.providerData?.any { it.providerId == "password" } == true

            SetPasswordScreen(
                userEmail = userEmail,
                hasExistingPassword = hasPassword,
                onBack = { activeSubPage = SettingsSubPage.MAIN },
                onPasswordSavedSuccessfully = { isNew ->
                    activeSubPage = SettingsSubPage.MAIN
                    coroutineScope.launch {
                        val msg = if (isNew) "Password set" else "Password changed"
                        snackbarHostState.showSnackbar(msg)
                    }
                }
            )
            return
        }
        else -> {}
    }

    val colors = MaterialTheme.colorScheme

    val currentThemeLabel = when (currentThemeMode) {
        AppThemeMode.SYSTEM -> "System"
        AppThemeMode.LIGHT -> "Light"
        AppThemeMode.DARK -> "Dark"
    }

    Scaffold(
        topBar = {
            SettingsHeader(
                title = "Settings",
                onBackClick = onBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.background)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Card 0: Subscription
            SettingsCard {
                SettingsRow(
                    title = "Subscription",
                    iconVector = Icons.Outlined.Star,
                    valueText = "Free",
                    onClick = { activeSubPage = SettingsSubPage.SUBSCRIPTION }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Card 1: Theme
            SettingsCard {
                SettingsRow(
                    title = "Theme",
                    valueText = currentThemeLabel,
                    onClick = { showThemeSheet = true }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Card 2: Sounds and vibration & Notifications
            SettingsCard {
                SettingsRow(
                    title = "Sounds and vibration",
                    onClick = { activeSubPage = SettingsSubPage.SOUNDS_VIBRATION }
                )

                HorizontalDivider(thickness = 1.dp, color = colors.outline)

                SettingsRow(
                    title = "Notifications",
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {}
                    }
                )
            }

            // Section Label: Privacy and security
            SettingsSectionLabel("Privacy and security")

            // Card 3: Privacy & Security
            SettingsCard {
                // Passcode Row
                SettingsRow(
                    title = "Passcode",
                    showChevron = false,
                    onClick = {
                        if (isPasscodeOn) {
                            activeSubPage = SettingsSubPage.PASSCODE
                        } else {
                            activeSubPage = SettingsSubPage.CREATE_PASSCODE
                        }
                    },
                    trailingContent = {
                        SettingsSwitch(
                            checked = isPasscodeOn,
                            onCheckedChange = { toggle ->
                                if (toggle) {
                                    activeSubPage = SettingsSubPage.CREATE_PASSCODE
                                } else {
                                    passcodePrefs.edit().remove("passcode_hash").apply()
                                    isPasscodeOn = false
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Passcode turned off")
                                    }
                                }
                            }
                        )
                    }
                )

                HorizontalDivider(thickness = 1.dp, color = colors.outline)

                val currentUser = FirebaseAuth.getInstance().currentUser
                val hasPassword = currentUser?.providerData?.any { it.providerId == "password" } == true

                // Set password / Change password
                SettingsRow(
                    title = if (hasPassword) "Change password" else "Set password",
                    onClick = { activeSubPage = SettingsSubPage.SET_PASSWORD }
                )

                HorizontalDivider(thickness = 1.dp, color = colors.outline)

                // Blocked users
                SettingsRow(
                    title = "Blocked users",
                    onClick = { activeSubPage = SettingsSubPage.BLOCKED_USERS }
                )

                HorizontalDivider(thickness = 1.dp, color = colors.outline)

                // Data and privacy
                SettingsRow(
                    title = "Data and privacy",
                    onClick = { activeSubPage = SettingsSubPage.DATA_PRIVACY }
                )
            }
        }
    }

    if (showThemeSheet) {
        ThemePickerSheet(
            currentMode = currentThemeMode,
            onModeSelected = { mode ->
                themeViewModel.setThemeMode(mode)
            },
            onDismiss = { showThemeSheet = false }
        )
    }
}
