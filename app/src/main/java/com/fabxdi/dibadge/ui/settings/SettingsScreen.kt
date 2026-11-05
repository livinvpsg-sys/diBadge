package com.fabxdi.dibadge.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabxdi.dibadge.ui.theme.AppThemeMode
import com.fabxdi.dibadge.util.SubscriptionManager
import com.fabxdi.dibadge.viewmodel.ThemeViewModel
import com.fabxdi.dibadge.viewmodel.ToDoViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch

enum class SettingsSubPage {
    MAIN,
    SUBSCRIPTION,
    LINKED_DEVICES,
    SOUNDS_VIBRATION,
    PASSCODE,
    CREATE_PASSCODE,
    BLOCKED_USERS,
    DATA_PRIVACY,
    DRIVE_BACKUP,
    MANAGE_MEDIA,
    SET_PASSWORD,
    VERIFY_PASSCODE_OFF
}

@Composable
fun SettingsScreen(
    userEmail: String = "",
    isOrganisationAccount: Boolean = false,
    onBack: () -> Unit,
    onSignOut: () -> Unit = {},
    themeViewModel: ThemeViewModel = viewModel()
) {
    val context = LocalContext.current
    val currentThemeMode by themeViewModel.themeMode.collectAsState()

    LaunchedEffect(Unit) { SubscriptionManager.listenToSubscription(context) }
    val subscription by SubscriptionManager.subscriptionState.collectAsState()

    var activeSubPage by rememberSaveable { mutableStateOf(SettingsSubPage.MAIN) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var showSetPasswordSheet by remember { mutableStateOf(false) }
    var showLinkedDevicesScanner by remember { mutableStateOf(false) }

    // Read every time so a different signed-in account never sees the previous one's data
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    var passwordVersion by remember { mutableIntStateOf(0) }
    var linkedDevicesCount by remember { mutableIntStateOf(0) }
    var isHideFromSearch by remember { mutableStateOf(false) }

    DisposableEffect(currentUid) {
        if (currentUid.isBlank()) return@DisposableEffect onDispose { }
        val db = FirebaseFirestore.getInstance()

        val devicesReg = db.collection("users").document(currentUid)
            .collection("linkedDevices")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                linkedDevicesCount = snapshot.documents.size
            }

        val userReg = db.collection("users").document(currentUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                isHideFromSearch = snapshot.getBoolean("hideFromSearch") ?: false
            }

        onDispose {
            devicesReg.remove()
            userReg.remove()
        }
    }

    var isPasscodeOn by remember { mutableStateOf(PasscodeStore.isSet(context)) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    BackHandler(enabled = true) {
        if (showLinkedDevicesScanner) {
            showLinkedDevicesScanner = false
        } else if (activeSubPage != SettingsSubPage.MAIN) {
            activeSubPage = SettingsSubPage.MAIN
        } else {
            onBack()
        }
    }

    when (activeSubPage) {
        SettingsSubPage.SUBSCRIPTION -> {
            SubscriptionScreen(
                onBack = { activeSubPage = SettingsSubPage.MAIN }
            )
            return
        }
        SettingsSubPage.LINKED_DEVICES -> {
            if (showLinkedDevicesScanner) {
                LinkedDevicesScannerScreen(
                    userUid = currentUid,
                    onBack = { showLinkedDevicesScanner = false },
                    onDeviceLinkedSuccessfully = {
                        showLinkedDevicesScanner = false
                    }
                )
            } else {
                LinkedDevicesScreen(
                    userUid = currentUid,
                    isOrganisationAccount = isOrganisationAccount,
                    onBack = { activeSubPage = SettingsSubPage.MAIN },
                    onOpenScanner = { showLinkedDevicesScanner = true }
                )
            }
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
                    PasscodeStore.set(context, newPin)
                    isPasscodeOn = true
                    activeSubPage = SettingsSubPage.PASSCODE
                },
                onCancel = { activeSubPage = SettingsSubPage.MAIN }
            )
            return
        }
        SettingsSubPage.VERIFY_PASSCODE_OFF -> {
            PinVerifyScreen(
                title = "Turn off passcode",
                subtitle = "Enter your passcode to turn it off",
                onVerified = {
                    PasscodeStore.clear(context)
                    isPasscodeOn = false
                    activeSubPage = SettingsSubPage.MAIN
                    coroutineScope.launch { snackbarHostState.showSnackbar("Passcode turned off") }
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
                onOpenManageMedia = { activeSubPage = SettingsSubPage.MANAGE_MEDIA },
                onDeleteAccountConfirmed = {
                    onSignOut()
                }
            )
            return
        }
        SettingsSubPage.DRIVE_BACKUP -> {
            DriveBackupScreen(
                onBack = { activeSubPage = SettingsSubPage.MAIN }
            )
            return
        }
        SettingsSubPage.MANAGE_MEDIA -> {
            val toDoViewModel: ToDoViewModel = viewModel()
            val homeEntries by toDoViewModel.allHomeEntries.collectAsState(initial = emptyList())
            ManageMediaStorageScreen(
                homeEntries = homeEntries,
                onBack = { activeSubPage = SettingsSubPage.DATA_PRIVACY }
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
                    passwordVersion++
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

    val currentThemeLabel = currentThemeMode.title

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

            // Card 1: Subscription, Linked devices, Back up
            SettingsCard {
                SettingsRow(
                    title = "Subscription",
                    valueText = if (subscription.isPro) "Pro" else "Free",
                    onClick = { activeSubPage = SettingsSubPage.SUBSCRIPTION }
                )

                if (!isOrganisationAccount) {
                    HorizontalDivider(thickness = 1.dp, color = colors.outline)

                    SettingsRow(
                        title = "Linked devices",
                        valueText = if (linkedDevicesCount > 0) linkedDevicesCount.toString() else null,
                        onClick = { activeSubPage = SettingsSubPage.LINKED_DEVICES }
                    )
                }

                HorizontalDivider(thickness = 1.dp, color = colors.outline)

                SettingsRow(
                    title = "Back up",
                    valueText = "Coming soon",
                    onClick = { activeSubPage = SettingsSubPage.DRIVE_BACKUP }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Card 2: Theme, Sounds and vibration, Notifications
            SettingsCard {
                SettingsRow(
                    title = "Theme",
                    valueText = currentThemeLabel,
                    onClick = { showThemeSheet = true }
                )

                HorizontalDivider(thickness = 1.dp, color = colors.outline)

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
                // 1. Passcode Row
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
                                    // Needs the current passcode first
                                    activeSubPage = SettingsSubPage.VERIFY_PASSCODE_OFF
                                }
                            },
                            label = "Passcode"
                        )
                    }
                )

                HorizontalDivider(thickness = 1.dp, color = colors.outline)

                // 2. Hide from Search Row
                SettingsRow(
                    title = "Hide from search",
                    subtitle = "Others can find you only by badge number",
                    showChevron = false,
                    onClick = {
                        val toggle = !isHideFromSearch
                        isHideFromSearch = toggle
                        if (currentUid.isNotBlank()) {
                            FirebaseFirestore.getInstance().collection("users").document(currentUid)
                                .set(mapOf("hideFromSearch" to toggle), SetOptions.merge())
                        }
                    },
                    trailingContent = {
                        SettingsSwitch(
                            checked = isHideFromSearch,
                            onCheckedChange = { toggle ->
                                isHideFromSearch = toggle
                                if (currentUid.isNotBlank()) {
                                    FirebaseFirestore.getInstance().collection("users").document(currentUid)
                                        .set(mapOf("hideFromSearch" to toggle), SetOptions.merge())
                                }
                            },
                            label = "Hide from search"
                        )
                    }
                )

                HorizontalDivider(thickness = 1.dp, color = colors.outline)

                val hasPassword = remember(passwordVersion, currentUid) {
                    FirebaseAuth.getInstance().currentUser?.providerData?.any { it.providerId == "password" } == true
                }

                // 3. Set password / Change password
                SettingsRow(
                    title = if (hasPassword) "Change password" else "Set password",
                    onClick = {
                        if (hasPassword) {
                            activeSubPage = SettingsSubPage.SET_PASSWORD
                        } else {
                            showSetPasswordSheet = true
                        }
                    }
                )

                HorizontalDivider(thickness = 1.dp, color = colors.outline)

                // 4. Blocked users
                SettingsRow(
                    title = "Blocked users",
                    onClick = { activeSubPage = SettingsSubPage.BLOCKED_USERS }
                )

                HorizontalDivider(thickness = 1.dp, color = colors.outline)

                // 5. Data and privacy
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

    if (showSetPasswordSheet) {
        SetPasswordSheet(
            userEmail = userEmail,
            onSaveSuccess = {
                passwordVersion++
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Password set")
                }
            },
            onDismiss = { showSetPasswordSheet = false }
        )
    }
}
