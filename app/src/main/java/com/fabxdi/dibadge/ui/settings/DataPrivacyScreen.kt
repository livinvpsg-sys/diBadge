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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun DataPrivacyScreen(
    userEmail: String,
    onBack: () -> Unit,
    onDeleteAccountConfirmed: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var cacheSizeMb by remember { mutableIntStateOf(24) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = true, onBack = onBack)

    val colors = MaterialTheme.colorScheme
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

            // Card 1: Data Export & Cache
            SettingsCard {
                SettingsRow(
                    title = "Download my data",
                    subtitle = "Get a copy of your profile and activity",
                    showChevron = false,
                    onClick = {
                        coroutineScope.launch {
                            val targetEmail = userEmail.ifBlank { "your email" }
                            snackbarHostState.showSnackbar("We will email your data to $targetEmail when it is ready")
                        }
                    }
                )

                HorizontalDivider(thickness = 1.dp, color = colors.outline)

                SettingsRow(
                    title = "Clear cache",
                    valueText = "$cacheSizeMb MB",
                    showChevron = false,
                    onClick = {
                        cacheSizeMb = 0
                        coroutineScope.launch {
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
                    subtitle = "Permanently delete your account and data",
                    iconVector = Icons.Outlined.Delete,
                    titleColor = errorColor,
                    iconTint = errorColor,
                    showChevron = false,
                    onClick = { showDeleteAccountDialog = true }
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
                        onDeleteAccountConfirmed()
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
