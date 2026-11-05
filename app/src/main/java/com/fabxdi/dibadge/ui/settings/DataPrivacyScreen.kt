package com.fabxdi.dibadge.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

    LaunchedEffect(Unit) {
        cacheBytes = withContext(Dispatchers.IO) {
            context.cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        }
    }

    fun deleteAccount() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        isDeleting = true
        // The request lets a Cloud Function remove this user's Firestore and Storage data
        val ref = FirebaseFirestore.getInstance().collection("accountDeletionRequests").document(user.uid)
        ref.set(mapOf("requestedAt" to FieldValue.serverTimestamp()))
            .addOnSuccessListener {
                user.delete()
                    .addOnSuccessListener {
                        isDeleting = false
                        onDeleteAccountConfirmed()
                    }
                    .addOnFailureListener { e ->
                        ref.delete()
                        isDeleting = false
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                if (e is FirebaseAuthRecentLoginRequiredException)
                                    "For your security, sign out and sign in again, then delete your account"
                                else "Could not delete your account. Try again"
                            )
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
                    subtitle = "Get a copy of your profile and activity",
                    showChevron = false,
                    onClick = {
                        val uid = FirebaseAuth.getInstance().currentUser?.uid
                        val targetEmail = userEmail.ifBlank { "your email" }
                        if (uid == null) {
                            coroutineScope.launch { snackbarHostState.showSnackbar("Sign in to request your data") }
                        } else {
                            FirebaseFirestore.getInstance().collection("dataExportRequests").document(uid)
                                .set(mapOf("requestedAt" to FieldValue.serverTimestamp()))
                                .addOnSuccessListener {
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Request sent. We will email your data to $targetEmail") }
                                }
                                .addOnFailureListener {
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Could not send the request. Try again") }
                                }
                        }
                    }
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
                    text = "This permanently deletes your account and your data. This cannot be undone.",
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
