package com.fabxdi.dibadge.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.R
import com.fabxdi.dibadge.util.capitalizeWords

val DrawerBackgroundColor = Color(0xFF1C1C1E)
val DrawerDividerColor = Color(0xFF2E2E30)
val DrawerEmailColor = Color(0xFFA8A8AD)
val DrawerRowTextColor = Color(0xFFF2F2F2)
val DrawerSignOutColor = Color(0xFFFF8A80)
val DrawerVersionTextColor = Color(0xFF77777C)

data class MenuRowData(
    val id: String,
    val label: String,
    val iconVector: ImageVector? = null,
    val iconRes: Int? = null
)

@Composable
fun HomeMenu(
    firstName: String,
    fullName: String = firstName,
    email: String = "",
    onCloseDrawer: () -> Unit,
    onProfileClick: () -> Unit = {},
    onMyActivityClick: () -> Unit = {},
    onCalendarClick: () -> Unit = {},
    onMyDocsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    val context = LocalContext.current
    var showSignOutConfirmation by remember { mutableStateOf(false) }

    val versionName = remember(context) {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    val displayFullName = remember(fullName, firstName) {
        val raw = if (fullName.isNotBlank()) fullName else if (firstName.isNotBlank()) firstName else "User"
        raw.capitalizeWords()
    }
    val avatarInitial = displayFullName.trim().take(1).uppercase()
    val avatarGradient = remember {
        Brush.linearGradient(
            colors = listOf(Color(0xFF5CC9C0), Color(0xFF17645E))
        )
    }

    val topNavGroup = listOf(
        MenuRowData("Profile", "Profile", iconVector = Icons.Outlined.Person),
        MenuRowData("My Activity", "My Activity", iconRes = R.drawable.ic_my_activity),
        MenuRowData("Calendar", "Calendar", iconVector = Icons.Outlined.CalendarMonth),
        MenuRowData("My Docs", "My Docs", iconVector = Icons.Outlined.Description)
    )

    val bottomNavGroup = listOf(
        MenuRowData("Settings", "Settings", iconVector = Icons.Outlined.Settings)
    )

    val drawerBgColor = MaterialTheme.colorScheme.surface
    val drawerDividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val drawerEmailColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val drawerRowTextColor = MaterialTheme.colorScheme.onSurface
    val drawerSignOutColor = MaterialTheme.colorScheme.error
    val drawerVersionTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

    ModalDrawerSheet(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.72f)
            .widthIn(max = 320.dp),
        drawerContainerColor = drawerBgColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(drawerBgColor)
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // 1. Identity Header (display only, not clickable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 20.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = if (email.isNotBlank()) "$displayFullName, $email" else displayFullName
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Teal Gradient Avatar Circle 60dp
                Surface(
                    modifier = Modifier.size(60.dp),
                    shape = CircleShape,
                    color = Color.Transparent
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(brush = avatarGradient, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = avatarInitial,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFEDEDED)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Name & Email Column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = displayFullName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (email.isNotBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = email,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = drawerEmailColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 2. Divider
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                thickness = 1.dp,
                color = drawerDividerColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Top Group Menu Rows (Profile, My Activity, Calendar, My Docs)
            topNavGroup.forEach { item ->
                DrawerRowItem(
                    label = item.label,
                    iconVector = item.iconVector,
                    iconRes = item.iconRes,
                    textColor = drawerRowTextColor,
                    iconColor = drawerRowTextColor,
                    onClick = {
                        onCloseDrawer()
                        when (item.id) {
                            "Profile" -> onProfileClick()
                            "My Activity" -> onMyActivityClick()
                            "Calendar" -> onCalendarClick()
                            "My Docs" -> onMyDocsClick()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Middle Divider
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                thickness = 1.dp,
                color = drawerDividerColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Settings Row
            bottomNavGroup.forEach { item ->
                DrawerRowItem(
                    label = item.label,
                    iconVector = item.iconVector,
                    iconRes = item.iconRes,
                    textColor = drawerRowTextColor,
                    iconColor = drawerRowTextColor,
                    onClick = {
                        onCloseDrawer()
                        if (item.id == "Settings") onSettingsClick()
                    }
                )
            }

            // 4. Flexible Space
            Spacer(modifier = Modifier.weight(1f))

            // 5. Bottom Block: Divider aligned with bottom bar top edge, then Sign Out, then Version Text
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                thickness = 1.dp,
                color = drawerDividerColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            DrawerRowItem(
                label = "Sign out",
                iconVector = Icons.AutoMirrored.Outlined.Logout,
                textColor = drawerSignOutColor,
                iconColor = drawerSignOutColor,
                height = 44.dp,
                onClick = {
                    showSignOutConfirmation = true
                }
            )

            // 6. Version Text
            Text(
                text = "diBadge v$versionName",
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = drawerVersionTextColor,
                modifier = Modifier
                    .padding(start = 24.dp, top = 4.dp, bottom = 36.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            )
        }
    }

    // Sign Out Confirmation Dialog
    if (showSignOutConfirmation) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirmation = false },
            title = { Text("Sign out of diBadge?") },
            text = { Text("Are you sure you want to sign out of your account?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutConfirmation = false
                        onCloseDrawer()
                        onSignOut()
                    }
                ) {
                    Text("Sign out", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DrawerRowItem(
    label: String,
    iconVector: ImageVector? = null,
    iconRes: Int? = null,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    iconColor: Color = MaterialTheme.colorScheme.onSurface,
    height: Dp = 52.dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = label
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            } else if (iconVector != null) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(18.dp))
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        }
    }
}
