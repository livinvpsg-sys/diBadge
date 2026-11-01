package com.fabxdi.dibadge.ui.settings

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.FilePickerUtils
import com.fabxdi.dibadge.util.SubscriptionManager

@Composable
fun SubscriptionScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    BackHandler(enabled = true, onBack = onBack)

    LaunchedEffect(Unit) {
        SubscriptionManager.listenToSubscription(context)
    }

    val subscription by SubscriptionManager.subscriptionState.collectAsState()

    var selectedPlanType by remember { mutableStateOf("Yearly") } // "Yearly" or "Monthly"
    var isPurchasing by remember { mutableStateOf(false) }

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors
    val isDark = colors.onBackground.red > 0.5f

    val pageBgColor = colors.background
    val cardBgColor = colors.surface
    val cardFillColor = if (isDark) Color(0xFF222831) else Color(0xFFDDE2E7)
    val outlineColor = colors.outline
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val tealAccent = diBadgeColors.accent

    // Solid Neutral Subscribe Button Colors (not teal, not a pill)
    val subscribeBtnFill = if (isDark) Color(0xFFF0F3F5) else Color(0xFF11161A)
    val subscribeBtnText = if (isDark) Color(0xFF11161A) else Color(0xFFFFFFFF)

    Scaffold(
        topBar = {
            SettingsHeader(
                title = "Subscription",
                onBackClick = onBack
            )
        },
        bottomBar = {
            if (!subscription.isPro) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = pageBgColor,
                    border = BorderStroke(1.dp, outlineColor)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Full width solid neutral button
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clickable(enabled = !isPurchasing) {
                                    isPurchasing = true
                                    SubscriptionManager.subscribeUserToPro(context, selectedPlanType) {
                                        isPurchasing = false
                                    }
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = subscribeBtnFill
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isPurchasing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = subscribeBtnText,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = "Subscribe",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = subscribeBtnText
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Billed through Google Play. Cancel anytime.",
                            fontSize = 12.sp,
                            color = secondaryTextColor,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Restore purchases",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tealAccent,
                            modifier = Modifier.clickable {
                                SubscriptionManager.restorePurchases(context)
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(pageBgColor)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 32.dp)
        ) {
            if (subscription.isPro) {
                // ==================== PRO USER VIEW (Frame 2) ====================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        color = cardFillColor
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Pro Check",
                                tint = primaryTextColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = "You are on Pro",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryTextColor
                        )
                        val statusLine = if (subscription.isCancelled) {
                            "${subscription.planType} · Ends on ${subscription.renewalDateText}"
                        } else {
                            "${subscription.planType} · Renews on ${subscription.renewalDateText}"
                        }
                        Text(
                            text = statusLine,
                            fontSize = 14.sp,
                            color = secondaryTextColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Included",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = secondaryTextColor,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                BenefitRowsList(
                    cardBgColor = cardBgColor,
                    cardFillColor = cardFillColor,
                    primaryTextColor = primaryTextColor,
                    secondaryTextColor = secondaryTextColor
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Storage Card for Pro
                val usedStr = FilePickerUtils.formatFileSize(subscription.storageUsedBytes)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = cardBgColor
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Storage",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = primaryTextColor
                        )
                        Text(
                            text = "$usedStr used · Unlimited",
                            fontSize = 15.sp,
                            color = secondaryTextColor
                        )
                    }
                }
            } else {
                // ==================== FREE USER VIEW (Frame 1) ====================
                Text(
                    text = "diBadge Pro",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Work with your organisation on diBadge.",
                    fontSize = 15.sp,
                    color = secondaryTextColor
                )

                Spacer(modifier = Modifier.height(20.dp))

                BenefitRowsList(
                    cardBgColor = cardBgColor,
                    cardFillColor = cardFillColor,
                    primaryTextColor = primaryTextColor,
                    secondaryTextColor = secondaryTextColor
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Selectable Plan Cards (Yearly & Monthly)
                PlanCard(
                    title = "Yearly",
                    subtitle = "$5.00 a month, billed yearly",
                    priceText = "$59.99",
                    periodText = "per year",
                    saveTagText = "Save 16%",
                    isSelected = selectedPlanType == "Yearly",
                    primaryTextColor = primaryTextColor,
                    secondaryTextColor = secondaryTextColor,
                    cardBgColor = cardBgColor,
                    outlineColor = outlineColor,
                    tealAccent = tealAccent,
                    onClick = { selectedPlanType = "Yearly" }
                )

                Spacer(modifier = Modifier.height(10.dp))

                PlanCard(
                    title = "Monthly",
                    subtitle = "Billed every month",
                    priceText = "$5.99",
                    periodText = "per month",
                    saveTagText = null,
                    isSelected = selectedPlanType == "Monthly",
                    primaryTextColor = primaryTextColor,
                    secondaryTextColor = secondaryTextColor,
                    cardBgColor = cardBgColor,
                    outlineColor = outlineColor,
                    tealAccent = tealAccent,
                    onClick = { selectedPlanType = "Monthly" }
                )
            }
        }
    }
}

@Composable
private fun BenefitRowsList(
    cardBgColor: Color,
    cardFillColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = cardBgColor
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            BenefitItemRow(
                icon = Icons.Outlined.VerifiedUser,
                title = "Get verified by an organisation",
                subtitle = "Join your organisation as a verified member",
                cardFillColor = cardFillColor,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor
            )

            BenefitItemRow(
                icon = Icons.Outlined.People,
                title = "Organisation groups",
                subtitle = "Open groups created by your organisation",
                cardFillColor = cardFillColor,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor
            )

            BenefitItemRow(
                icon = Icons.Outlined.Description,
                title = "Create forms",
                subtitle = "Build your own forms for your groups",
                cardFillColor = cardFillColor,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor
            )

            BenefitItemRow(
                icon = Icons.Outlined.Cloud,
                title = "Unlimited storage",
                subtitle = "Keep all your photos, videos and documents",
                cardFillColor = cardFillColor,
                primaryTextColor = primaryTextColor,
                secondaryTextColor = secondaryTextColor
            )
        }
    }
}

@Composable
private fun BenefitItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    cardFillColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(12.dp),
            color = cardFillColor
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = primaryTextColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = primaryTextColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = secondaryTextColor
            )
        }
    }
}

@Composable
private fun PlanCard(
    title: String,
    subtitle: String,
    priceText: String,
    periodText: String,
    saveTagText: String? = null,
    isSelected: Boolean,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    cardBgColor: Color,
    outlineColor: Color,
    tealAccent: Color,
    onClick: () -> Unit
) {
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val borderColor = if (isSelected) primaryTextColor else outlineColor

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = cardBgColor,
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Radio Circle (22dp)
            Surface(
                modifier = Modifier.size(22.dp),
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(1.5.dp, if (isSelected) primaryTextColor else secondaryTextColor)
            ) {
                if (isSelected) {
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            modifier = Modifier.size(11.dp),
                            shape = CircleShape,
                            color = primaryTextColor
                        ) {}
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Plan Title & Subtitle
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryTextColor
                    )
                    if (saveTagText != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = saveTagText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tealAccent
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = secondaryTextColor
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Price Column
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = priceText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = periodText,
                    fontSize = 12.sp,
                    color = secondaryTextColor
                )
            }
        }
    }
}
