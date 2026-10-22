package com.fabxdi.dibadge.ui.settings

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun SoundsVibrationScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("sounds_settings", Context.MODE_PRIVATE) }

    var isSoundsEnabled by remember { mutableStateOf(prefs.getBoolean("sounds_enabled", true)) }
    var isVibrationEnabled by remember { mutableStateOf(prefs.getBoolean("vibration_enabled", true)) }
    var notificationToneName by remember { mutableStateOf(prefs.getString("notification_tone_name", "Default") ?: "Default") }
    var alarmToneName by remember { mutableStateOf(prefs.getString("alarm_tone_name", "Default") ?: "Default") }

    BackHandler(enabled = true, onBack = onBack)

    val notificationRingtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (uri != null) {
            val ringtone = RingtoneManager.getRingtone(context, uri)
            val name = ringtone.getTitle(context) ?: "Default"
            notificationToneName = name
            prefs.edit().putString("notification_tone_name", name).putString("notification_tone_uri", uri.toString()).apply()
        }
    }

    val alarmRingtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (uri != null) {
            val ringtone = RingtoneManager.getRingtone(context, uri)
            val name = ringtone.getTitle(context) ?: "Default"
            alarmToneName = name
            prefs.edit().putString("alarm_tone_name", name).putString("alarm_tone_uri", uri.toString()).apply()
        }
    }

    val colors = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            SettingsHeader(
                title = "Sounds and vibration",
                onBackClick = onBack
            )
        }
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

            // Card 1: In-App Sounds & Vibration Switches
            SettingsCard {
                SettingsRow(
                    title = "Sounds",
                    subtitle = "Play sounds inside the app",
                    showChevron = false,
                    trailingContent = {
                        SettingsSwitch(
                            checked = isSoundsEnabled,
                            onCheckedChange = {
                                isSoundsEnabled = it
                                prefs.edit().putBoolean("sounds_enabled", it).apply()
                            }
                        )
                    }
                )

                HorizontalDivider(thickness = 1.dp, color = colors.outline)

                SettingsRow(
                    title = "Vibration",
                    subtitle = "Vibrate for alerts and actions",
                    showChevron = false,
                    trailingContent = {
                        SettingsSwitch(
                            checked = isVibrationEnabled,
                            onCheckedChange = {
                                isVibrationEnabled = it
                                prefs.edit().putBoolean("vibration_enabled", it).apply()
                            }
                        )
                    }
                )
            }

            // Tones Section
            SettingsSectionLabel("Tones")

            SettingsCard {
                SettingsRow(
                    title = "Notification tone",
                    iconVector = Icons.Outlined.Notifications,
                    valueText = notificationToneName,
                    onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Notification Tone")
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                        }
                        notificationRingtoneLauncher.launch(intent)
                    }
                )

                HorizontalDivider(thickness = 1.dp, color = colors.outline)

                SettingsRow(
                    title = "Alarm sound",
                    iconVector = Icons.Outlined.Alarm,
                    valueText = alarmToneName,
                    onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm Sound")
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                        }
                        alarmRingtoneLauncher.launch(intent)
                    }
                )
            }
        }
    }
}
