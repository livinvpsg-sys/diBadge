package com.fabxdi.dibadge.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme

@Composable
fun SubscriptionScreen(
    currentPlanName: String = "Free",
    onBack: () -> Unit
) {
    BackHandler(enabled = true, onBack = onBack)

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val tealAccent = diBadgeColors.accent

    Scaffold(
        topBar = {
            SettingsHeader(
                title = "Subscription",
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
                    title = "Current Plan",
                    iconVector = Icons.Outlined.Star,
                    valueText = currentPlanName,
                    showChevron = false
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "diBadge $currentPlanName Plan",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = primaryTextColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "You are currently using the $currentPlanName plan.",
                        fontSize = 14.sp,
                        color = secondaryTextColor
                    )
                }
            }
        }
    }
}
