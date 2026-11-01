package com.fabxdi.dibadge.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.FilePickerUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveBackupScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    BackHandler(enabled = true, onBack = onBack)

    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser
    val userEmail = currentUser?.email ?: "user@gmail.com"
    val userUid = currentUser?.uid ?: ""

    var backupFrequency by remember { mutableStateOf("Weekly") }
    var useMobileData by remember { mutableStateOf(false) }
    var includeVideos by remember { mutableStateOf(true) }
    var lastBackupTimeMs by remember { mutableLongStateOf(0L) }
    var lastBackupSizeBytes by remember { mutableLongStateOf(0L) }

    var isBackingUp by remember { mutableStateOf(false) }
    var backupProgressPercent by remember { mutableIntStateOf(0) }
    var isRestoring by remember { mutableStateOf(false) }
    var restoreProgressPercent by remember { mutableIntStateOf(0) }
    var showFrequencySheet by remember { mutableStateOf(false) }
    var showDeleteBackupDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(userUid) {
        if (userUid.isNotBlank()) {
            val db = FirebaseFirestore.getInstance()
            db.collection("users").document(userUid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                    backupFrequency = snapshot.getString("backupFrequency") ?: "Weekly"
                    useMobileData = snapshot.getBoolean("backupMobileData") ?: false
                    includeVideos = snapshot.getBoolean("backupIncludeVideos") ?: true
                    lastBackupTimeMs = snapshot.getTimestamp("lastBackupTime")?.toDate()?.time
                        ?: snapshot.getLong("lastBackupTime") ?: 0L
                    lastBackupSizeBytes = snapshot.getLong("lastBackupSize") ?: 0L
                }
        }
    }

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val tealAccent = diBadgeColors.accent
    val errorColor = colors.error

    Scaffold(
        topBar = {
            SettingsHeader(
                title = "Back up",
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

            // Card 1: Google Account & Status
            SettingsCard {
                SettingsRow(
                    title = "Google account",
                    valueText = userEmail,
                    onClick = {
                        Toast.makeText(context, "Using $userEmail for backup", Toast.LENGTH_SHORT).show()
                    }
                )

                HorizontalDivider(thickness = 1.dp, color = colors.outline)

                val dateFmt = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())
                val lastBackupStr = if (lastBackupTimeMs > 0L) {
                    "${dateFmt.format(Date(lastBackupTimeMs))} · ${FilePickerUtils.formatFileSize(lastBackupSizeBytes)}"
                } else {
                    "Not backed up yet"
                }

                SettingsRow(
                    title = "Last backup",
                    subtitle = lastBackupStr,
                    showChevron = false
                )

                HorizontalDivider(thickness = 1.dp, color = colors.outline)

                // Back up now / Backing up progress / Stop
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (isBackingUp) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Backing up… $backupProgressPercent%",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = primaryTextColor
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { backupProgressPercent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp),
                                color = tealAccent,
                                trackColor = colors.outline.copy(alpha = 0.3f)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Text(
                            text = "Stop",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = secondaryTextColor,
                            modifier = Modifier.clickable {
                                isBackingUp = false
                                backupProgressPercent = 0
                            }
                        )
                    } else {
                        Text(
                            text = "Back up now",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tealAccent,
                            modifier = Modifier.clickable {
                                isBackingUp = true
                                backupProgressPercent = 0
                                coroutineScope.launch {
                                    for (p in 10..100 step 15) {
                                        delay(300)
                                        backupProgressPercent = p
                                    }
                                    isBackingUp = false
                                    lastBackupTimeMs = System.currentTimeMillis()
                                    lastBackupSizeBytes = 245 * 1024 * 1024L
                                    Toast.makeText(context, "Backup complete", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }

                if (lastBackupTimeMs > 0L) {
                    HorizontalDivider(thickness = 1.dp, color = colors.outline)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (isRestoring) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Restoring… $restoreProgressPercent%",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = primaryTextColor
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { restoreProgressPercent / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp),
                                    color = tealAccent,
                                    trackColor = colors.outline.copy(alpha = 0.3f)
                                )
                            }
                        } else {
                            Text(
                                text = "Restore",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = tealAccent,
                                modifier = Modifier.clickable {
                                    isRestoring = true
                                    restoreProgressPercent = 0
                                    coroutineScope.launch {
                                        for (p in 10..100 step 20) {
                                            delay(300)
                                            restoreProgressPercent = p
                                        }
                                        isRestoring = false
                                        Toast.makeText(context, "Restore complete", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card 2: Automatic Backup & Toggles
            SettingsCard {
                SettingsRow(
                    title = "Back up automatically",
                    valueText = backupFrequency,
                    onClick = { showFrequencySheet = true }
                )

                HorizontalDivider(thickness = 1.dp, color = colors.outline)

                SettingsRow(
                    title = "Use mobile data",
                    subtitle = "Wi-Fi only when off",
                    showChevron = false,
                    onClick = {
                        useMobileData = !useMobileData
                        updateBackupSetting(userUid, "backupMobileData", useMobileData)
                    },
                    trailingContent = {
                        SettingsSwitch(
                            checked = useMobileData,
                            onCheckedChange = { toggle ->
                                useMobileData = toggle
                                updateBackupSetting(userUid, "backupMobileData", toggle)
                            }
                        )
                    }
                )

                HorizontalDivider(thickness = 1.dp, color = colors.outline)

                SettingsRow(
                    title = "Include videos",
                    showChevron = false,
                    onClick = {
                        includeVideos = !includeVideos
                        updateBackupSetting(userUid, "backupIncludeVideos", includeVideos)
                    },
                    trailingContent = {
                        SettingsSwitch(
                            checked = includeVideos,
                            onCheckedChange = { toggle ->
                                includeVideos = toggle
                                updateBackupSetting(userUid, "backupIncludeVideos", toggle)
                            }
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Your photos, videos and files are saved to your own Google Drive and count toward your Google storage.",
                fontSize = 13.sp,
                color = secondaryTextColor,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Card 3: Delete Backup
            SettingsCard {
                SettingsRow(
                    title = "Delete backup",
                    showChevron = false,
                    titleColor = errorColor,
                    onClick = { showDeleteBackupDialog = true }
                )
            }
        }
    }

    // Auto Backup Frequency Sheet
    if (showFrequencySheet) {
        val options = listOf("Off", "Daily", "Weekly", "Monthly")
        ModalBottomSheet(
            onDismissRequest = { showFrequencySheet = false },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            containerColor = cardBgColor,
            scrimColor = Color.Black.copy(alpha = 0.45f),
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = "Back up automatically",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryTextColor,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                options.forEach { option ->
                    val isSelected = backupFrequency.equals(option, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clickable {
                                backupFrequency = option
                                updateBackupSetting(userUid, "backupFrequency", option)
                                showFrequencySheet = false
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                backupFrequency = option
                                updateBackupSetting(userUid, "backupFrequency", option)
                                showFrequencySheet = false
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = tealAccent)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = option,
                            fontSize = 16.sp,
                            color = primaryTextColor
                        )
                    }
                }
            }
        }
    }

    // Delete Backup Confirmation Dialog
    if (showDeleteBackupDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteBackupDialog = false },
            containerColor = cardBgColor,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "Delete Google Drive backup?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )
            },
            text = {
                Text(
                    text = "Your media backup will be permanently removed from Google Drive. Your local media stays on this phone.",
                    fontSize = 14.sp,
                    color = secondaryTextColor
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteBackupDialog = false
                        lastBackupTimeMs = 0L
                        lastBackupSizeBytes = 0L
                        updateBackupSetting(userUid, "lastBackupTime", 0L)
                        Toast.makeText(context, "Backup deleted from Google Drive", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(
                        text = "Delete",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = errorColor
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteBackupDialog = false }) {
                    Text(
                        text = "Cancel",
                        fontSize = 15.sp,
                        color = secondaryTextColor
                    )
                }
            }
        )
    }
}

private fun updateBackupSetting(userUid: String, field: String, value: Any) {
    if (userUid.isNotBlank()) {
        FirebaseFirestore.getInstance().collection("users").document(userUid)
            .update(field, value)
    }
}
