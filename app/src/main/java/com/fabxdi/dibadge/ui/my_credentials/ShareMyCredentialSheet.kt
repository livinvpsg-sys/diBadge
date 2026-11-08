package com.fabxdi.dibadge.ui.my_credentials

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.data.DocEntity
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * [organisations] are the organisations this account really belongs to. When it is empty the
 * "Share with organisation" row is shown disabled.
 * [onShareWithOrg] is called before [onDismiss], so the caller can still read what was being shared.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareMyCredentialSheet(
    docs: List<DocEntity>,
    context: Context,
    organisations: List<String>,
    onShareWithOrg: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showOrgChoiceSheet by remember { mutableStateOf(false) }
    var selectedOrg by remember(organisations) { mutableStateOf(organisations.firstOrNull() ?: "") }

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val tealAccent = diBadgeColors.accent
    val tabInactiveColor = diBadgeColors.tabInactive

    val hasOrg = organisations.isNotEmpty()
    val countLabel = if (docs.size == 1) "credential" else "${docs.size} credentials"

    if (showOrgChoiceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOrgChoiceSheet = false },
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
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = "Share with organisation",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryTextColor,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "A copy of the details and files is sent. Your original stays in My Credentials.",
                    fontSize = 13.sp,
                    color = secondaryTextColor
                )

                Spacer(modifier = Modifier.height(16.dp))

                organisations.forEach { org ->
                    val isSelected = selectedOrg == org
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clickable(role = Role.RadioButton) { selectedOrg = org },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(22.dp),
                            shape = CircleShape,
                            color = Color.Transparent,
                            border = BorderStroke(1.5.dp, if (isSelected) tealAccent else tabInactiveColor)
                        ) {
                            if (isSelected) {
                                Box(contentAlignment = Alignment.Center) {
                                    Surface(
                                        modifier = Modifier.size(10.dp),
                                        shape = CircleShape,
                                        color = tealAccent
                                    ) {}
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Text(
                            text = org,
                            fontSize = 16.sp,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                            color = primaryTextColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.Button) { showOrgChoiceSheet = false }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = secondaryTextColor
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Box(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.Button, enabled = selectedOrg.isNotBlank()) {
                                showOrgChoiceSheet = false
                                onShareWithOrg(selectedOrg)
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Share",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selectedOrg.isNotBlank()) tealAccent else tabInactiveColor
                        )
                    }
                }
            }
        }
        return
    }

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
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = if (docs.size == 1) "Share credential" else "Share ${docs.size} credentials",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = primaryTextColor,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Row 1: Share with organisation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .alpha(if (hasOrg) 1f else 0.45f)
                    .clickable(enabled = hasOrg, role = Role.Button) {
                        showOrgChoiceSheet = true
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Business,
                    contentDescription = null,
                    tint = secondaryTextColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Share with organisation",
                        fontSize = 16.sp,
                        color = primaryTextColor
                    )
                    Text(
                        text = if (hasOrg) "Send a copy to your organisation account" else "No organisation linked to your account",
                        fontSize = 13.sp,
                        color = secondaryTextColor
                    )
                }
            }

            HorizontalDivider(thickness = 1.dp, color = outlineColor)

            // Row 2: Share with another app
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clickable(role = Role.Button) {
                        onDismiss()
                        shareWithOtherApp(context, docs)
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = null,
                    tint = secondaryTextColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Share with another app",
                        fontSize = 16.sp,
                        color = primaryTextColor
                    )
                    Text(
                        text = "Send the $countLabel with any app",
                        fontSize = 13.sp,
                        color = secondaryTextColor
                    )
                }
            }
        }
    }
}

/** Sends the files (and a short text with name and expiry) to the app the user picks. */
private fun shareWithOtherApp(context: Context, docs: List<DocEntity>) {
    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
    val text = docs.joinToString("\n") { doc ->
        val expiry = doc.expiryDate
        if (!doc.noExpiry && expiry != null) "${doc.name}, expires ${expiry.format(formatter)}" else doc.name
    }
    val uris = docs.flatMap { it.files }.mapNotNull { MyCredentialFileUtils.toShareUri(context, it) }

    val intent = if (uris.isEmpty()) {
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    try {
        context.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
