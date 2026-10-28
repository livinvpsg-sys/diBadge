package com.fabxdi.dibadge.ui.home.home_entry.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareLocationSheet(
    placeName: String = "Jurong West, Singapore",
    accuracyMeters: Int = 12,
    isDark: Boolean,
    onSendCurrentLocation: (lat: Double, lng: Double, place: String, accuracy: Int) -> Unit,
    onSendLiveLocation: (lat: Double, lng: Double, place: String, durationMinutes: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var showLiveDurationSheet by remember { mutableStateOf(false) }
    var selectedDurationMinutes by remember { mutableIntStateOf(60) } // Default 1 hour

    val cardColor = if (isDark) Color(0xFF181C21) else Color(0xFFF5F7F9)
    val cardFillColor = if (isDark) Color(0xFF222831) else Color(0xFFDDE2E7)
    val outlineColor = if (isDark) Color(0xFF2A3037) else Color(0xFFD3D9DF)
    val primaryTextColor = if (isDark) Color(0xFFF0F3F5) else Color(0xFF11161A)
    val secondaryTextColor = if (isDark) Color(0xFF8A9199) else Color(0xFF65707A)
    val tealAccent = if (isDark) Color(0xFF58B5AC) else Color(0xFF1B6F69)
    val errorColor = if (isDark) Color(0xFFE8837A) else Color(0xFFC4433A)

    val currentLat = 1.34042
    val currentLng = 103.70901

    if (showLiveDurationSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLiveDurationSheet = false },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = cardColor,
            tonalElevation = 0.dp,
            scrimColor = Color.Black.copy(alpha = 0.45f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Share live location",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                val durations = listOf(
                    15 to "15 minutes",
                    60 to "1 hour",
                    480 to "8 hours"
                )

                durations.forEach { (mins, label) ->
                    val isSelected = selectedDurationMinutes == mins
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable { selectedDurationMinutes = mins }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedDurationMinutes = mins },
                            colors = RadioButtonDefaults.colors(selectedColor = tealAccent)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = label,
                            fontSize = 15.sp,
                            color = primaryTextColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = 16.sp,
                        color = secondaryTextColor,
                        modifier = Modifier
                            .clickable { showLiveDurationSheet = false }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    Text(
                        text = "Share",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tealAccent,
                        modifier = Modifier
                            .clickable {
                                onSendLiveLocation(currentLat, currentLng, placeName, selectedDurationMinutes)
                                showLiveDurationSheet = false
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = cardColor,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(width = 36.dp, height = 4.dp),
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
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            Text(
                text = "Share location",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = primaryTextColor,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Map Preview Tile (190dp, 14dp corners)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp),
                shape = RoundedCornerShape(14.dp),
                color = cardFillColor,
                border = BorderStroke(1.dp, outlineColor)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = "Map Pin",
                        tint = errorColor,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Row 1: Send current location (60dp row)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clickable {
                        onSendCurrentLocation(currentLat, currentLng, placeName, accuracyMeters)
                        onDismiss()
                    }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = cardFillColor,
                    border = BorderStroke(1.dp, outlineColor)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            tint = primaryTextColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Send current location",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = primaryTextColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$placeName · accurate to ${accuracyMeters} m",
                        fontSize = 12.sp,
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Row 2: Share live location (60dp row)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clickable { showLiveDurationSheet = true }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = cardFillColor,
                    border = BorderStroke(1.dp, outlineColor)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.NearMe,
                            contentDescription = null,
                            tint = primaryTextColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Share live location",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = primaryTextColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Members see you move on the map",
                        fontSize = 12.sp,
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
