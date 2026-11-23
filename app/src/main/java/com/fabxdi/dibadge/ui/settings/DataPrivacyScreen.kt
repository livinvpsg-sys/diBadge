package com.fabxdi.dibadge.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.FilePickerUtils
import com.fabxdi.dibadge.util.SubscriptionManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DataPrivacyScreen(
    userEmail: String,
    onBack: () -> Unit,
    onOpenManageMedia: () -> Unit = {},
    onDeleteAccountConfirmed: () -> Unit = {}
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var cacheBytes by remember { mutableLongStateOf(0L) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<String?>(null) }

    // The person picks where to save the file (Downloads is the default), so no storage permission is needed
    val saveExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val text = pendingExport
        pendingExport = null
        if (uri == null || text == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val saved = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
                } catch (e: Exception) {
                    false
                }
            }
            snackbarHostState.showSnackbar(if (saved) "Your data was saved" else "Could not save the file. Try again")
        }
    }

    LaunchedEffect(Unit) {
        cacheBytes = withContext(Dispatchers.IO) {
            context.cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        }
    }

    fun exportData() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            coroutineScope.launch { snackbarHostState.showSnackbar("Sign in to download your data") }
            return
        }
        isExporting = true
        AccountDataExport.build(user.uid, user.email.orEmpty().ifBlank { userEmail }) { json ->
            isExporting = false
            if (json == null) {
                coroutineScope.launch { snackbarHostState.showSnackbar("Could not read your data. Check your connection") }
            } else {
                pendingExport = json
                val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                saveExportLauncher.launch("dibadge-data-$day.json")
            }
        }
    }

    fun deleteAccount() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        isDeleting = true
        // A fresh token, then the Worker removes the data and the sign-in on the server
        user.getIdToken(true)
            .addOnSuccessListener { tokenResult ->
                val token = tokenResult.token
                coroutineScope.launch {
                    // Photo, signature and other files first: after the account is gone Storage refuses the request
                    AccountDeletion.deleteStorageFiles(user.uid)
                    if (token == null) {
                        isDeleting = false
                        snackbarHostState.showSnackbar("Could not delete your account. Try again")
                        return@launch
                    }
                    val (code, body) = AccountDeletion.request(token)
                    isDeleting = false
                    when {
                        code in 200..299 -> {
                            withContext(Dispatchers.IO) {
                                // Files kept only on this phone: chat media, My Credentials files, the profile photo copy
                                File(context.filesDir, "media").deleteRecursively()
                                File(context.filesDir, "documents").deleteRecursively()
                                File(context.filesDir, "profile_photo.jpg").delete()
                            }
                            FirebaseAuth.getInstance().signOut()
                            onDeleteAccountConfirmed()
                        }
                        code == 403 && body.contains("recent_login_required") ->
                            snackbarHostState.showSnackbar("For your security, sign out and sign in again, then delete your account")
                        code == -1 ->
                            snackbarHostState.showSnackbar("Could not reach the server. Check your connection")
                        else ->
                            snackbarHostState.showSnackbar("Could not delete your account. Try again")
                    }
                }
            }
            .addOnFailureListener {
                isDeleting = false
                coroutineScope.launch { snackbarHostState.showSnackbar("Could not delete your account. Check your connection") }
            }
    }

    LaunchedEffect(Unit) {
        SubscriptionManager.listenToSubscription(context)
    }

    val subscription by SubscriptionManager.subscriptionState.collectAsState()

    BackHandler(enabled = true, onBack = onBack)

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val errorColor = colors.error

    Scaffold(
        topBar = {
            SettingsHeader(
                title = "Data and privacy",
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

            // Card 0: Media on this phone
            SettingsCard {
                val localMediaSize by produceState(0L, context) {
                    value = withContext(Dispatchers.IO) {
                        val mediaFolder = File(context.filesDir, "media")
                        if (mediaFolder.exists()) {
                            mediaFolder.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
                        } else 0L
                    }
                }

                SettingsRow(
                    title = "Media on this phone",
                    valueText = FilePickerUtils.formatFileSize(localMediaSize),
                    onClick = onOpenManageMedia
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Storage Card for Free Users (1 GB limit thin progress bar)
            if (!subscription.isPro) {
                val usedStr = FilePickerUtils.formatFileSize(subscription.storageUsedBytes)
                val progress = (subscription.storageUsedBytes.toFloat() / subscription.storageLimitBytes.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)

                SettingsCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Storage",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal,
                                color = primaryTextColor
                            )
                            Text(
                                text = "$usedStr of 1 GB",
                                fontSize = 14.sp,
                                color = secondaryTextColor
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = if (progress >= 0.8f) errorColor else diBadgeColors.accent,
                            trackColor = colors.outline.copy(alpha = 0.3f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Card 1: Data Export & Cache
            SettingsCard {
                SettingsRow(
                    title = "Download my data",
                    subtitle = if (isExporting) "Preparing…" else "Save a copy of your profile and activity to your phone",
                    showChevron = false,
                    onClick = { if (!isExporting) exportData() }
                )

                HorizontalDivider(thickness = 1.dp, color = colors.outline)

                SettingsRow(
                    title = "Clear cache",
                    valueText = FilePickerUtils.formatFileSize(cacheBytes),
                    showChevron = false,
                    onClick = {
                        coroutineScope.launch {
                            val left = withContext(Dispatchers.IO) {
                                context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                                context.cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
                            }
                            cacheBytes = left
                            snackbarHostState.showSnackbar("Cache cleared")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Card 2: Legal Links
            SettingsCard {
                SettingsRow(
                    title = "Privacy policy",
                    showChevron = false,
                    trailingContent = {
                        Icon(
                            imageVector = Icons.Outlined.OpenInNew,
                            contentDescription = "External link",
                            tint = secondaryTextColor,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://dibadge.com/privacy"))
                            context.startActivity(intent)
                        } catch (e: Exception) {}
                    }
                )

                HorizontalDivider(thickness = 1.dp, color = colors.outline)

                SettingsRow(
                    title = "Terms of service",
                    showChevron = false,
                    trailingContent = {
                        Icon(
                            imageVector = Icons.Outlined.OpenInNew,
                            contentDescription = "External link",
                            tint = secondaryTextColor,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://dibadge.com/terms"))
                            context.startActivity(intent)
                        } catch (e: Exception) {}
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Card 3: Delete Account
            SettingsCard {
                SettingsRow(
                    title = "Delete account",
                    subtitle = if (isDeleting) "Deleting…" else "Permanently delete your account and data",
                    iconVector = Icons.Outlined.Delete,
                    titleColor = errorColor,
                    iconTint = errorColor,
                    showChevron = false,
                    onClick = { if (!isDeleting) showDeleteAccountDialog = true }
                )
            }
        }
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = {
                Text(
                    text = "Delete account?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryTextColor
                )
            },
            text = {
                Text(
                    text = "This permanently deletes your profile, photo, signature, tasks, leaves, claims, credentials, connections and blocked list, and frees your username.\n\n" +
                            "Files saved on this phone (media and credential files) are removed too.\n\n" +
                            "Messages you already sent stay with the people and groups who received them.\n\n" +
                            "This cannot be undone. You can download your data first.",
                    fontSize = 14.sp,
                    color = secondaryTextColor
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAccountDialog = false
                        deleteAccount()
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
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text(
                        text = "Cancel",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = secondaryTextColor
                    )
                }
            },
            containerColor = cardBgColor,
            shape = RoundedCornerShape(20.dp)
        )
    }
}