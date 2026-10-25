package com.fabxdi.dibadge.ui.my_forms

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fabxdi.dibadge.data.FormLogosData
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormLogosSheet(
    initialLogos: FormLogosData,
    onDone: (FormLogosData) -> Unit,
    onDismiss: () -> Unit
) {
    var logosData by remember(initialLogos) { mutableStateOf(initialLogos) }
    var selectedSlot by remember { mutableStateOf("left") } // "left", "center", "right"

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val tealAccent = diBadgeColors.accent
    val errorColor = colors.error

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                val uriStr = uri.toString()
                logosData = when (selectedSlot) {
                    "left" -> logosData.copy(leftLogoUrl = uriStr)
                    "center" -> logosData.copy(centerLogoUrl = uriStr)
                    else -> logosData.copy(rightLogoUrl = uriStr)
                }
            }
        }
    )

    val currentSlotUrl = when (selectedSlot) {
        "left" -> logosData.leftLogoUrl
        "center" -> logosData.centerLogoUrl
        else -> logosData.rightLogoUrl
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = cardBgColor,
        scrimColor = Color.Black.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "Form logos",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = primaryTextColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Shown at the top of the form and in the PDF",
                fontSize = 13.sp,
                color = secondaryTextColor
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 3 Slots: Left, Center, Right (64dp high, rounded 12dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf(
                    "left" to "Left",
                    "center" to "Center",
                    "right" to "Right"
                ).forEach { (slotKey, slotLabel) ->
                    val isSelected = selectedSlot == slotKey
                    val logoUrl = when (slotKey) {
                        "left" -> logosData.leftLogoUrl
                        "center" -> logosData.centerLogoUrl
                        else -> logosData.rightLogoUrl
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clickable { selectedSlot = slotKey },
                        shape = RoundedCornerShape(12.dp),
                        color = colors.background,
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) primaryTextColor else outlineColor
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (logoUrl.isNotBlank()) {
                                AsyncImage(
                                    model = logoUrl,
                                    contentDescription = slotLabel,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(6.dp),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = secondaryTextColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = slotLabel,
                                        fontSize = 12.sp,
                                        color = secondaryTextColor
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Size Chips: Small, Medium, Large
            Text(
                text = "Size",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = primaryTextColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "small" to "Small",
                    "medium" to "Medium",
                    "large" to "Large"
                ).forEach { (sizeKey, sizeLabel) ->
                    val isSelected = logosData.size == sizeKey
                    Surface(
                        modifier = Modifier.clickable {
                            logosData = logosData.copy(size = sizeKey)
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) colors.background else Color.Transparent,
                        border = BorderStroke(1.dp, if (isSelected) tealAccent else outlineColor)
                    ) {
                        Text(
                            text = sizeLabel,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = primaryTextColor,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Menu Rows: Choose from gallery, Take photo, Remove logo
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable { galleryLauncher.launch("image/*") },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Image, contentDescription = null, tint = secondaryTextColor, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(14.dp))
                Text("Choose from gallery", fontSize = 15.sp, color = primaryTextColor)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable { galleryLauncher.launch("image/*") },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = secondaryTextColor, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(14.dp))
                Text("Take photo", fontSize = 15.sp, color = primaryTextColor)
            }

            if (currentSlotUrl.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable {
                            logosData = when (selectedSlot) {
                                "left" -> logosData.copy(leftLogoUrl = "")
                                "center" -> logosData.copy(centerLogoUrl = "")
                                else -> logosData.copy(rightLogoUrl = "")
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = errorColor, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(14.dp))
                    Text("Remove logo", fontSize = 15.sp, color = errorColor)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Footer: Cancel & Done
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cancel",
                    fontSize = 15.sp,
                    color = secondaryTextColor,
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )

                Spacer(modifier = Modifier.width(24.dp))

                Text(
                    text = "Done",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = tealAccent,
                    modifier = Modifier
                        .clickable {
                            onDone(logosData)
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}
