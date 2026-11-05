package com.fabxdi.dibadge.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme

/**
 * Backing up to Google Drive is not built yet. The earlier version only played a fake progress bar
 * and a made-up "245 MB", so it is replaced by an honest placeholder. When Drive upload exists
 * (Drive API + WorkManager), put the real controls back here.
 */
@Composable
fun DriveBackupScreen(
    onBack: () -> Unit
) {
    BackHandler(enabled = true, onBack = onBack)

    val colors = MaterialTheme.colorScheme
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant

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
                .padding(horizontal = 40.dp)
                .padding(top = 100.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.Cloud,
                contentDescription = null,
                tint = DiBadgeTheme.colors.tabInactive,
                modifier = Modifier.size(44.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Backup is coming soon",
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = primaryTextColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Your media is kept only on this phone until backup is available.",
                fontSize = 13.sp,
                color = secondaryTextColor,
                textAlign = TextAlign.Center
            )
        }
    }
}
