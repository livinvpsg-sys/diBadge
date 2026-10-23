package com.fabxdi.dibadge.ui.my_docs

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.fabxdi.dibadge.data.DocEntity
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareDocSheet(
    doc: DocEntity,
    context: Context,
    onShareWithOrg: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showOrgChoiceSheet by remember { mutableStateOf(false) }
    var selectedOrg by remember { mutableStateOf("diBadge Inc.") }

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val tealAccent = diBadgeColors.accent
    val tabInactiveColor = diBadgeColors.tabInactive

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
                    text = "A copy of the details and files is sent. Your original stays in My Docs.",
                    fontSize = 13.sp,
                    color = secondaryTextColor
                )

                Spacer(modifier = Modifier.height(16.dp))

                val orgs = listOf("diBadge Inc.", "Tuas Roof Works")
                orgs.forEach { org ->
                    val isSelected = selectedOrg == org
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clickable { selectedOrg = org },
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
                    Text(
                        text = "Cancel",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = secondaryTextColor,
                        modifier = Modifier
                            .clickable { showOrgChoiceSheet = false }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )

                    Spacer(modifier = Modifier.width(28.dp))

                    Text(
                        text = "Share",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tealAccent,
                        modifier = Modifier
                            .clickable {
                                showOrgChoiceSheet = false
                                onDismiss()
                                onShareWithOrg(selectedOrg)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
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
                text = "Share document",
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
                    .clickable {
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
                        text = "Send a copy to your organisation account",
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
                    .clickable {
                        onDismiss()
                        val expiryStr = if (!doc.noExpiry && doc.expiryDate != null) ", expires ${doc.expiryDate.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))}" else ""
                        val shareText = "${doc.name}$expiryStr"

                        try {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "*/*"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Document"))
                        } catch (e: Exception) {}
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
                        text = "Send the files with any app",
                        fontSize = 13.sp,
                        color = secondaryTextColor
                    )
                }
            }
        }
    }
}
