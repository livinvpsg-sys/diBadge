package com.fabxdi.dibadge.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
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
import androidx.core.content.IntentCompat

/**
 * Saved keys: sounds_enabled, vibration_enabled, notification_tone_name / _uri, alarm_tone_name / _uri.
 * An empty *_uri means "Silent". A missing *_uri means the phone's default.
 * Note: on Android 8+ the notification sound belongs to the notification channel. A new tone only
 * takes effect if the app creates the channel with that URI (a new channel id is needed to change it).
 */
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

    // Saves the picked tone. "Silent" comes back as RESULT_OK with no URI.
    fun saveTone(result: androidx.activity.result.ActivityResult, nameKey: String, uriKey: String, setName: (String) -> Unit) {
        if (result.resultCode != Activity.RESULT_OK) return
        val uri = result.data?.let {
            IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        }
        if (uri == null) {
            setName("Silent")
            prefs.edit().putString(nameKey, "Silent").putString(uriKey, "").apply()
        } else {
            val name = try {
                RingtoneManager.getRingtone(context, uri)?.getTitle(context)
            } catch (e: Exception) {
                null
            } ?: "Default"
            setName(name)
            prefs.edit().putString(nameKey, name).putString(uriKey, uri.toString()).apply()
        }
    }

    fun pickerIntent(type: Int, title: String, uriKey: String): Intent =
        Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, type)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, title)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            // Preselect the tone that is saved now
            val saved = prefs.getString(uriKey, null)
            if (saved != null) {
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, if (saved.isEmpty()) null else Uri.parse(saved))
            }
        }

    val notificationRingtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result -> saveTone(result, "notification_tone_name", "notification_tone_uri") { notificationToneName = it } }

    val alarmRingtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result -> saveTone(result, "alarm_tone_name", "alarm_tone_uri") { alarmToneName = it } }

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

            SettingsCard {
                SettingsRow(
                    title = "Sounds",
                    subtitle = "Play sounds inside the app",
                    showChevron = false,
                    onClick = {
                        isSoundsEnabled = !isSoundsEnabled
                        prefs.edit().putBoolean("sounds_enabled", isSoundsEnabled).apply()
                    },
                    trailingContent = {
                        SettingsSwitch(
                            checked = isSoundsEnabled,
                            onCheckedChange = {
                                isSoundsEnabled = it
                                prefs.edit().putBoolean("sounds_enabled", it).apply()
                            },
                            label = "Sounds"
                        )
                    }
                )

                HorizontalDivider(thickness = 1.dp, color = colors.outline)

                SettingsRow(
                    title = "Vibration",
                    subtitle = "Vibrate for alerts and actions",
                    showChevron = false,
                    onClick = {
                        isVibrationEnabled = !isVibrationEnabled
                        prefs.edit().putBoolean("vibration_enabled", isVibrationEnabled).apply()
                    },
                    trailingContent = {
                        SettingsSwitch(
                            checked = isVibrationEnabled,
                            onCheckedChange = {
                                isVibrationEnabled = it
                                prefs.edit().putBoolean("vibration_enabled", it).apply()
                            },
                            label = "Vibration"
                        )
                    }
                )
            }

            SettingsSectionLabel("Tones")

            SettingsCard {
                SettingsRow(
                    title = "Notification tone",
                    iconVector = Icons.Outlined.Notifications,
                    valueText = notificationToneName,
                    onClick = {
                        notificationRingtoneLauncher.launch(
                            pickerIntent(RingtoneManager.TYPE_NOTIFICATION, "Notification Tone", "notification_tone_uri")
                        )
                    }
                )

                HorizontalDivider(thickness = 1.dp, color = colors.outline)

                SettingsRow(
                    title = "Alarm sound",
                    iconVector = Icons.Outlined.Alarm,
                    valueText = alarmToneName,
                    onClick = {
                        alarmRingtoneLauncher.launch(
                            pickerIntent(RingtoneManager.TYPE_ALARM, "Alarm Sound", "alarm_tone_uri")
                        )
                    }
                )
            }
        }
    }
}
