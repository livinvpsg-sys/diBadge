package com.fabxdi.dibadge.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fabxdi.dibadge.R
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.avatarColorFor
import com.fabxdi.dibadge.util.capitalizeWords

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
    photoUrl: String = "",
    onCloseDrawer: () -> Unit,
    onProfileClick: () -> Unit = {},
    onMyActivityClick: () -> Unit = {},
    onCalendarClick: () -> Unit = {},
    onMyDocsClick: () -> Unit = {},
    onFormsClick: () -> Unit = {},
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
    val avatarBgColor = remember(firstName, fullName) {
        avatarColorFor(firstName.ifBlank { fullName.ifBlank { "User" } })
    }

    val topNavGroup = listOf(
        MenuRowData("Profile", "Profile", iconVector = Icons.Outlined.Person),
        MenuRowData("My Activity", "My Activity", iconRes = R.drawable.ic_my_activity),
        MenuRowData("Calendar", "Calendar", iconVector = Icons.Outlined.CalendarMonth),
        MenuRowData("My Docs", "My Docs", iconVector = Icons.Outlined.Description),
        MenuRowData("My Forms", "My Forms", iconRes = R.drawable.ic_forms_outline)
    )

    val bottomNavGroup = listOf(
        MenuRowData("Settings", "Settings", iconVector = Icons.Outlined.Settings)
    )

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val drawerBgColor = colors.surface
    val drawerOutlineColor = colors.outline
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val tabInactiveColor = diBadgeColors.tabInactive
    val errorColor = colors.error

    ModalDrawerSheet(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.72f)
            .widthIn(max = 320.dp),
        drawerContainerColor = drawerBgColor
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = drawerBgColor,
            border = BorderStroke(1.dp, drawerOutlineColor)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Scrollable Content Column
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(drawerBgColor)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 100.dp) // Leave space for pinned bottom block
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
                        // Avatar Circle 60dp
                        Surface(
                            modifier = Modifier.size(60.dp),
                            shape = CircleShape,
                            color = avatarBgColor
                        ) {
                            if (photoUrl.isNotBlank()) {
                                AsyncImage(
                                    model = photoUrl,
                                    contentDescription = "Profile photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = avatarInitial,
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
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
                                fontWeight = FontWeight.Normal,
                                color = primaryTextColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (email.isNotBlank()) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = email,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = secondaryTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // 2. Divider (1dp outline, 24dp side margins, 8dp below)
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        thickness = 1.dp,
                        color = drawerOutlineColor
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 3. Top Group Menu Rows (Profile, My Activity, Calendar, My Docs)
                    topNavGroup.forEach { item ->
                        DrawerRowItem(
                            label = item.label,
                            iconVector = item.iconVector,
                            iconRes = item.iconRes,
                            textColor = primaryTextColor,
                            iconColor = secondaryTextColor,
                            onClick = {
                                onCloseDrawer()
                                when (item.id) {
                                    "Profile" -> onProfileClick()
                                    "My Activity" -> onMyActivityClick()
                                    "Calendar" -> onCalendarClick()
                                    "My Docs" -> onMyDocsClick()
                                    "My Forms", "Forms" -> onFormsClick()
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Middle Divider (1dp outline, 24dp side margins, 8dp above and below)
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        thickness = 1.dp,
                        color = drawerOutlineColor
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Settings Row
                    bottomNavGroup.forEach { item ->
                        DrawerRowItem(
                            label = item.label,
                            iconVector = item.iconVector,
                            iconRes = item.iconRes,
                            textColor = primaryTextColor,
                            iconColor = secondaryTextColor,
                            onClick = {
                                onCloseDrawer()
                                if (item.id == "Settings") onSettingsClick()
                            }
                        )
                    }
                }

                // 5. Pinned Bottom Block (Sign out divider aligned with bottom nav bar top line)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(drawerBgColor)
                        .navigationBarsPadding()
                ) {
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        thickness = 1.dp,
                        color = drawerOutlineColor
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    DrawerRowItem(
                        label = "Sign out",
                        iconVector = Icons.AutoMirrored.Outlined.Logout,
                        textColor = errorColor,
                        iconColor = errorColor,
                        height = 44.dp,
                        onClick = {
                            showSignOutConfirmation = true
                        }
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "diBadge v$versionName",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = tabInactiveColor,
                        modifier = Modifier.padding(start = 24.dp, bottom = 12.dp)
                    )
                }
            }
        }
    }

    // Sign Out Confirmation Dialog
    if (showSignOutConfirmation) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirmation = false },
            title = {
                Text(
                    text = "Sign out of diBadge?",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Normal,
                    color = primaryTextColor
                )
            },
            text = {
                Text(
                    text = "You will need to log in again to use the app.",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal,
                    color = secondaryTextColor
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutConfirmation = false
                        onCloseDrawer()
                        onSignOut()
                    }
                ) {
                    Text(
                        text = "Sign out",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                        color = errorColor
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirmation = false }) {
                    Text(
                        text = "Cancel",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                        color = primaryTextColor
                    )
                }
            },
            containerColor = drawerBgColor,
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
fun DrawerRowItem(
    label: String,
    iconVector: ImageVector? = null,
    iconRes: Int? = null,
    textColor: Color = MaterialTheme.colorScheme.onBackground,
    iconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                fontWeight = FontWeight.Normal,
                color = textColor
            )
        }
    }
}
