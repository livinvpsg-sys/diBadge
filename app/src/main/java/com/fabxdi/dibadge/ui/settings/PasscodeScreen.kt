package com.fabxdi.dibadge.ui.settings

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasscodeScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("passcode_prefs", Context.MODE_PRIVATE) }

    var lockAfterOption by remember { mutableStateOf(prefs.getString("lock_after", "Immediately") ?: "Immediately") }
    var useFingerprint by remember { mutableStateOf(prefs.getBoolean("use_fingerprint", false)) }
    var showLockAfterSheet by remember { mutableStateOf(false) }

    // Changing the passcode: 1 = enter the current one, 2 = enter the new one
    var changeStep by remember { mutableIntStateOf(0) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    BackHandler(enabled = true, onBack = onBack)

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface

    if (changeStep == 1) {
        PinVerifyScreen(
            title = "Enter current passcode",
            subtitle = "Enter your current 6-digit passcode",
            onVerified = { changeStep = 2 },
            onCancel = { changeStep = 0 }
        )
        return
    }
    if (changeStep == 2) {
        PinPadScreen(
            title = "Change passcode",
            subtitle = "Enter new 6-digit passcode",
            onPasscodeEntered = { newPin ->
                PasscodeStore.set(context, newPin)
                changeStep = 0
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Passcode changed")
                }
            },
            onCancel = { changeStep = 0 }
        )
        return
    }

    Scaffold(
        topBar = {
            SettingsHeader(
                title = "Passcode",
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

            SettingsCard {
                SettingsRow(
                    title = "Change passcode",
                    onClick = { changeStep = 1 }
                )

                HorizontalDivider(thickness = 1.dp, color = colors.outline)

                SettingsRow(
                    title = "Lock after",
                    valueText = lockAfterOption,
                    onClick = { showLockAfterSheet = true }
                )

                HorizontalDivider(thickness = 1.dp, color = colors.outline)

                SettingsRow(
                    title = "Use fingerprint",
                    showChevron = false,
                    onClick = {
                        useFingerprint = !useFingerprint
                        prefs.edit().putBoolean("use_fingerprint", useFingerprint).apply()
                    },
                    trailingContent = {
                        SettingsSwitch(
                            checked = useFingerprint,
                            onCheckedChange = {
                                useFingerprint = it
                                prefs.edit().putBoolean("use_fingerprint", it).apply()
                            },
                            label = "Use fingerprint"
                        )
                    }
                )
            }
        }
    }

    if (showLockAfterSheet) {
        val options = listOf("Immediately", "1 minute", "5 minutes", "15 minutes")
        ModalBottomSheet(
            onDismissRequest = { showLockAfterSheet = false },
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
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = "Lock after",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryTextColor,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                options.forEach { option ->
                    val isSelected = lockAfterOption == option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clickable(role = Role.RadioButton) {
                                lockAfterOption = option
                                prefs.edit().putString("lock_after", option).apply()
                                showLockAfterSheet = false
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(22.dp),
                            shape = CircleShape,
                            color = Color.Transparent,
                            border = BorderStroke(1.5.dp, if (isSelected) diBadgeColors.accent else diBadgeColors.tabInactive)
                        ) {
                            if (isSelected) {
                                Box(contentAlignment = Alignment.Center) {
                                    Surface(
                                        modifier = Modifier.size(10.dp),
                                        shape = CircleShape,
                                        color = diBadgeColors.accent
                                    ) {}
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Text(
                            text = option,
                            fontSize = 16.sp,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                            color = primaryTextColor
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// PIN pad pieces shared by "create" and "verify"
// ---------------------------------------------------------------------------------------------

@Composable
private fun PinDots(filled: Int) {
    val tabInactiveColor = DiBadgeTheme.colors.tabInactive
    val primaryTextColor = MaterialTheme.colorScheme.onBackground
    Row(
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until 6) {
            val isFilled = i < filled
            Surface(
                modifier = Modifier.size(14.dp),
                shape = CircleShape,
                color = if (isFilled) primaryTextColor else Color.Transparent,
                border = if (!isFilled) BorderStroke(1.5.dp, tabInactiveColor) else null
            ) {}
        }
    }
}

@Composable
private fun PinKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit
) {
    val primaryTextColor = MaterialTheme.colorScheme.onBackground
    val padNumbers = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "back")
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp)
            .padding(bottom = 32.dp)
    ) {
        padNumbers.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(enabled = key.isNotEmpty(), role = Role.Button) {
                                if (key == "back") onBackspace() else onDigit(key)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (key == "back") {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Backspace",
                                tint = primaryTextColor,
                                modifier = Modifier.size(24.dp)
                            )
                        } else if (key.isNotEmpty()) {
                            Text(
                                text = key,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Normal,
                                color = primaryTextColor
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Create or change a passcode: enter it twice. */
@Composable
fun PinPadScreen(
    title: String = "Create passcode",
    subtitle: String = "Enter a 6-digit passcode",
    onPasscodeEntered: (String) -> Unit,
    onCancel: () -> Unit
) {
    var step by remember { mutableIntStateOf(1) } // 1 = First Entry, 2 = Confirm
    var firstPin by remember { mutableStateOf("") }
    var currentPin by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val colors = MaterialTheme.colorScheme
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant

    val displayTitle = if (step == 1) title else "Confirm passcode"
    val displaySubtitle = if (step == 1) subtitle else "Enter passcode again to confirm"

    BackHandler(enabled = true, onBack = onCancel)

    Scaffold(
        topBar = { SettingsHeader(title = "Passcode", onBackClick = onCancel) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.background),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            Text(text = displayTitle, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = primaryTextColor)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = displaySubtitle, fontSize = 14.sp, color = secondaryTextColor)
            Spacer(modifier = Modifier.height(40.dp))

            PinDots(filled = currentPin.length)

            Spacer(modifier = Modifier.weight(1f))

            PinKeypad(
                onBackspace = { if (currentPin.isNotEmpty()) currentPin = currentPin.dropLast(1) },
                onDigit = { key ->
                    if (currentPin.length < 6) {
                        currentPin += key
                        if (currentPin.length == 6) {
                            if (step == 1) {
                                firstPin = currentPin
                                currentPin = ""
                                step = 2
                            } else if (currentPin == firstPin) {
                                onPasscodeEntered(currentPin)
                            } else {
                                coroutineScope.launch { snackbarHostState.showSnackbar("Passcodes do not match") }
                                currentPin = ""
                                step = 1
                                firstPin = ""
                            }
                        }
                    }
                }
            )
        }
    }
}

/** Asks for the current passcode. Wrong guesses are counted and locked out for a while. */
@Composable
fun PinVerifyScreen(
    title: String = "Enter passcode",
    subtitle: String = "Enter your 6-digit passcode",
    onVerified: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var currentPin by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val colors = MaterialTheme.colorScheme
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant

    BackHandler(enabled = true, onBack = onCancel)

    Scaffold(
        topBar = { SettingsHeader(title = "Passcode", onBackClick = onCancel) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.background),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = primaryTextColor)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = subtitle, fontSize = 14.sp, color = secondaryTextColor)
            Spacer(modifier = Modifier.height(40.dp))

            PinDots(filled = currentPin.length)

            Spacer(modifier = Modifier.weight(1f))

            PinKeypad(
                onBackspace = { if (currentPin.isNotEmpty()) currentPin = currentPin.dropLast(1) },
                onDigit = { key ->
                    if (currentPin.length < 6) {
                        currentPin += key
                        if (currentPin.length == 6) {
                            val entered = currentPin
                            currentPin = ""
                            val waitMs = PasscodeStore.lockRemainingMs(context)
                            if (waitMs > 0L) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Too many attempts. Try again in ${(waitMs + 999) / 1000} s")
                                }
                            } else if (PasscodeStore.verify(context, entered)) {
                                onVerified()
                            } else {
                                val after = PasscodeStore.lockRemainingMs(context)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (after > 0L) "Too many attempts. Try again in ${(after + 999) / 1000} s"
                                        else "Wrong passcode"
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }
    }
}
