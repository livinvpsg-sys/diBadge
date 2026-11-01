package com.fabxdi.dibadge.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.QrCodeScanner
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
import androidx.core.content.ContextCompat
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LinkedDeviceItem(
    val id: String,
    val deviceName: String,
    val city: String,
    val linkedAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkedDevicesScreen(
    userUid: String,
    isOrganisationAccount: Boolean = false,
    onBack: () -> Unit,
    onOpenScanner: () -> Unit
) {
    val context = LocalContext.current
    BackHandler(enabled = true, onBack = onBack)

    var linkedDevices by remember { mutableStateOf<List<LinkedDeviceItem>>(emptyList()) }
    var selectedDeviceForDetails by remember { mutableStateOf<LinkedDeviceItem?>(null) }
    var showLogOutAllDialog by remember { mutableStateOf(false) }

    LaunchedEffect(userUid) {
        if (userUid.isNotBlank() && !isOrganisationAccount) {
            val db = FirebaseFirestore.getInstance()
            db.collection("users").document(userUid)
                .collection("linkedDevices")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    val items = snapshot.documents.mapNotNull { doc ->
                        val id = doc.id
                        val name = doc.getString("deviceName") ?: doc.getString("browser") ?: "Web Session"
                        val city = doc.getString("city") ?: doc.getString("location") ?: "Web"
                        val linkedAt = doc.getTimestamp("linkedAt")?.toDate()?.time ?: doc.getLong("linkedAt") ?: System.currentTimeMillis()
                        val lastActiveAt = doc.getTimestamp("lastActiveAt")?.toDate()?.time ?: doc.getLong("lastActiveAt") ?: System.currentTimeMillis()
                        LinkedDeviceItem(
                            id = id,
                            deviceName = name,
                            city = city,
                            linkedAt = linkedAt,
                            lastActiveAt = lastActiveAt
                        )
                    }.sortedByDescending { it.lastActiveAt }
                    linkedDevices = items
                }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                onOpenScanner()
            } else {
                Toast.makeText(context, "Camera permission is required to scan QR code", Toast.LENGTH_SHORT).show()
            }
        }
    )

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val errorColor = colors.error

    Scaffold(
        topBar = {
            SettingsHeader(
                title = "Linked devices",
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

            // Card 1: Link a Device
            SettingsCard {
                SettingsRow(
                    title = "Link a device",
                    subtitle = "Scan the QR code on diBadge web",
                    iconVector = Icons.Outlined.QrCodeScanner,
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            onOpenScanner()
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                )
            }

            if (linkedDevices.isEmpty()) {
                // Empty State
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp, bottom = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Link a device to use diBadge on a computer",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = secondaryTextColor,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Active On section
                SettingsSectionLabel("Active on")

                SettingsCard {
                    linkedDevices.forEachIndexed { index, device ->
                        val activeStr = formatActiveTime(device.lastActiveAt)
                        val subText = "${device.city.ifBlank { "Web" }} · $activeStr"

                        SettingsRow(
                            title = device.deviceName,
                            subtitle = subText,
                            iconVector = Icons.Outlined.DesktopWindows,
                            onClick = { selectedDeviceForDetails = device }
                        )

                        if (index < linkedDevices.size - 1) {
                            HorizontalDivider(thickness = 1.dp, color = colors.outline)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Card 3: Log out of all devices
                SettingsCard {
                    SettingsRow(
                        title = "Log out of all devices",
                        showChevron = false,
                        titleColor = errorColor,
                        onClick = { showLogOutAllDialog = true }
                    )
                }
            }
        }
    }

    // Sheet: Device Details & Log out
    if (selectedDeviceForDetails != null) {
        val dev = selectedDeviceForDetails!!
        val dateFmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        val linkedDateStr = dateFmt.format(Date(dev.linkedAt))
        val activeTimeStr = timeFmt.format(Date(dev.lastActiveAt))

        ModalBottomSheet(
            onDismissRequest = { selectedDeviceForDetails = null },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            containerColor = cardBgColor,
            scrimColor = Color.Black.copy(alpha = 0.45f),
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .padding(bottom = 20.dp)
            ) {
                Text(
                    text = dev.deviceName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Linked on $linkedDateStr",
                    fontSize = 13.sp,
                    color = secondaryTextColor
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Last active $activeTimeStr",
                    fontSize = 13.sp,
                    color = secondaryTextColor
                )

                Spacer(modifier = Modifier.height(20.dp))

                HorizontalDivider(thickness = 1.dp, color = colors.outline)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clickable {
                            val targetId = dev.id
                            selectedDeviceForDetails = null
                            FirebaseFirestore.getInstance().collection("users").document(userUid)
                                .collection("linkedDevices").document(targetId)
                                .delete()
                            Toast.makeText(context, "Device logged out", Toast.LENGTH_SHORT).show()
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Log out this device",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = errorColor
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clickable { selectedDeviceForDetails = null },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = 16.sp,
                        color = secondaryTextColor
                    )
                }
            }
        }
    }

    // Dialog: Log Out of All Devices
    if (showLogOutAllDialog) {
        AlertDialog(
            onDismissRequest = { showLogOutAllDialog = false },
            containerColor = cardBgColor,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "Log out of all devices?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )
            },
            text = {
                Text(
                    text = "The web sessions will end. You can link them again any time.",
                    fontSize = 14.sp,
                    color = secondaryTextColor
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogOutAllDialog = false
                        val db = FirebaseFirestore.getInstance()
                        db.collection("users").document(userUid)
                            .collection("linkedDevices")
                            .get()
                            .addOnSuccessListener { snap ->
                                for (doc in snap.documents) {
                                    doc.reference.delete()
                                }
                                Toast.makeText(context, "Logged out of all devices", Toast.LENGTH_SHORT).show()
                            }
                    }
                ) {
                    Text(
                        text = "Log out",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = errorColor
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogOutAllDialog = false }) {
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

private fun formatActiveTime(lastActiveMs: Long): String {
    val diffMs = System.currentTimeMillis() - lastActiveMs
    val minutes = diffMs / (60 * 1000)
    return when {
        minutes < 2 -> "Active now"
        minutes < 60 -> "Active $minutes min ago"
        else -> {
            val dateFmt = SimpleDateFormat("d MMM", Locale.getDefault())
            "Last active ${dateFmt.format(Date(lastActiveMs))}"
        }
    }
}
