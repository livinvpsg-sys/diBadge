package com.fabxdi.dibadge.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrButtonSheet(
    onScanQrClick: () -> Unit,
    onMyQrClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline

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
                .padding(bottom = 28.dp)
        ) {
            // Row 1: Scan QR code
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable {
                        onDismiss()
                        onScanQrClick()
                    }
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.QrCodeScanner,
                    contentDescription = "Scan QR code",
                    tint = secondaryTextColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Scan QR code",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = primaryTextColor
                )
            }

            HorizontalDivider(thickness = 1.dp, color = outlineColor)

            // Row 2: My QR code
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable {
                        onDismiss()
                        onMyQrClick()
                    }
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.QrCode,
                    contentDescription = "My QR code",
                    tint = secondaryTextColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "My QR code",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = primaryTextColor
                )
            }
        }
    }
}
