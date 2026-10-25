package com.fabxdi.dibadge.ui.my_forms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fabxdi.dibadge.data.FormLogosData
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme

@Composable
fun FormHeaderLogosStrip(
    logos: FormLogosData,
    onClick: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val secondaryTextColor = colors.onSurfaceVariant
    val outlineColor = colors.outline
    val dimColor = diBadgeColors.tabInactive

    val modifier = Modifier
        .fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)

    if (!logos.hasLogos()) {
        if (onClick != null) {
            // Placeholder style: image icon and "Add logos", both dim
            Column(modifier = modifier) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        tint = dimColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Add logos",
                        fontSize = 15.sp,
                        color = dimColor
                    )
                }
                HorizontalDivider(thickness = 1.dp, color = outlineColor)
            }
        }
    } else {
        // Header strip displaying Left, Center, Right logos on one line with thin line underneath
        val logoHeight = logos.getLogoHeightDp().dp

        Column(modifier = modifier) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Logo Slot
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (logos.leftLogoUrl.isNotBlank()) {
                        AsyncImage(
                            model = logos.leftLogoUrl,
                            contentDescription = "Left Logo",
                            modifier = Modifier.height(logoHeight),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                // Center Logo Slot
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (logos.centerLogoUrl.isNotBlank()) {
                        AsyncImage(
                            model = logos.centerLogoUrl,
                            contentDescription = "Center Logo",
                            modifier = Modifier.height(logoHeight),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                // Right Logo Slot
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    if (logos.rightLogoUrl.isNotBlank()) {
                        AsyncImage(
                            model = logos.rightLogoUrl,
                            contentDescription = "Right Logo",
                            modifier = Modifier.height(logoHeight),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = outlineColor)
        }
    }
}
