package com.fabxdi.dibadge.ui.home.home_entry.chat_details

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.R
import com.fabxdi.dibadge.ui.theme.AppThemeMode
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme

const val LIBRARY_TITLE = "Library"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailsMenuSheet(
    isMuted: Boolean = false,
    canUserManage: Boolean = true,
    waitingInvitesCount: Int = 0,
    isConnectedToOrg: Boolean = false,
    onSelectMembers: () -> Unit,
    onSelectGroups: () -> Unit,
    onSelectForms: () -> Unit,
    onSelectTasks: () -> Unit,
    onSelectCredentials: () -> Unit,
    onSelectLibrary: () -> Unit,
    onSelectMute: () -> Unit,
    onSelectUnmute: () -> Unit,
    onSelectClearChat: () -> Unit,
    onSelectGroupCode: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val tealAccent = diBadgeColors.accent

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = cardBgColor,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(width = 32.dp, height = 4.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = secondaryTextColor.copy(alpha = 0.40f)
                ) {}
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 1. Members
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clickable {
                        onDismiss()
                        onSelectMembers()
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_members),
                    contentDescription = null,
                    tint = secondaryTextColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Members",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = primaryTextColor
                )
            }

            // 2. Groups (Shows waiting invites count at right if owner/admin and > 0)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clickable {
                        onDismiss()
                        onSelectGroups()
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_groups),
                        contentDescription = null,
                        tint = secondaryTextColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Groups",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = primaryTextColor
                    )
                }

                if (canUserManage && waitingInvitesCount > 0) {
                    Text(
                        text = "$waitingInvitesCount",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tealAccent
                    )
                }
            }

            // 3. Forms (Only with an organisation)
            if (isConnectedToOrg) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clickable {
                            onDismiss()
                            onSelectForms()
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_forms_outline),
                        contentDescription = null,
                        tint = secondaryTextColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Forms",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = primaryTextColor
                    )
                }
            }

            // 4. Tasks
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clickable {
                        onDismiss()
                        onSelectTasks()
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_my_tasks),
                    contentDescription = null,
                    tint = secondaryTextColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Tasks",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = primaryTextColor
                )
            }

            // 5. Credentials
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clickable {
                        onDismiss()
                        onSelectCredentials()
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.WorkspacePremium,
                    contentDescription = null,
                    tint = secondaryTextColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Credentials",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = primaryTextColor
                )
            }

            // 6. Library
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clickable {
                        onDismiss()
                        onSelectLibrary()
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.FolderCopy,
                    contentDescription = null,
                    tint = secondaryTextColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = LIBRARY_TITLE,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = primaryTextColor
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                thickness = 1.dp,
                color = outlineColor
            )

            // Group Code (Only shown to owner and admins)
            if (canUserManage) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clickable {
                            onDismiss()
                            onSelectGroupCode()
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_key_outline),
                        contentDescription = null,
                        tint = secondaryTextColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Group code",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = primaryTextColor
                    )
                }
            }

            // Mute / Unmute Notifications
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clickable {
                        onDismiss()
                        if (isMuted) onSelectUnmute() else onSelectMute()
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff,
                    contentDescription = null,
                    tint = secondaryTextColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = if (isMuted) "Unmute notifications" else "Mute notifications",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = primaryTextColor
                )
            }

            // Clear Chat
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clickable {
                        onDismiss()
                        onSelectClearChat()
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = colors.error,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Clear chat",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = colors.error
                )
            }
        }
    }
}

// Previews

@Preview(name = "Three-dot Sheet Owner with Invitation - Light", widthDp = 412, showBackground = true)
@Composable
fun ChatDetailsMenuSheetOwnerPreview() {
    DiBadgeTheme(themeMode = AppThemeMode.LIGHT) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatDetailsMenuSheet(
                isMuted = false,
                canUserManage = true,
                waitingInvitesCount = 2,
                isConnectedToOrg = false,
                onSelectMembers = {},
                onSelectGroups = {},
                onSelectForms = {},
                onSelectTasks = {},
                onSelectCredentials = {},
                onSelectLibrary = {},
                onSelectMute = {},
                onSelectUnmute = {},
                onSelectClearChat = {},
                onSelectGroupCode = {},
                onDismiss = {}
            )
        }
    }
}

@Preview(name = "Three-dot Sheet Member - Light", widthDp = 412, showBackground = true)
@Composable
fun ChatDetailsMenuSheetMemberPreview() {
    DiBadgeTheme(themeMode = AppThemeMode.LIGHT) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatDetailsMenuSheet(
                isMuted = false,
                canUserManage = false,
                waitingInvitesCount = 0,
                isConnectedToOrg = false,
                onSelectMembers = {},
                onSelectGroups = {},
                onSelectForms = {},
                onSelectTasks = {},
                onSelectCredentials = {},
                onSelectLibrary = {},
                onSelectMute = {},
                onSelectUnmute = {},
                onSelectClearChat = {},
                onSelectGroupCode = {},
                onDismiss = {}
            )
        }
    }
}

@Preview(
    name = "Three-dot Sheet Owner - Dark",
    widthDp = 412,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun ChatDetailsMenuSheetDarkPreview() {
    DiBadgeTheme(themeMode = AppThemeMode.DARK) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ChatDetailsMenuSheet(
                isMuted = false,
                canUserManage = true,
                waitingInvitesCount = 0,
                isConnectedToOrg = false,
                onSelectMembers = {},
                onSelectGroups = {},
                onSelectForms = {},
                onSelectTasks = {},
                onSelectCredentials = {},
                onSelectLibrary = {},
                onSelectMute = {},
                onSelectUnmute = {},
                onSelectClearChat = {},
                onSelectGroupCode = {},
                onDismiss = {}
            )
        }
    }
}
