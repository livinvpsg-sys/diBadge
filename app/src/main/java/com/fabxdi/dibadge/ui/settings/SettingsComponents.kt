package com.fabxdi.dibadge.ui.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.AppThemeMode
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme

@Composable
fun SettingsHeader(
    title: String,
    onBackClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val primaryTextColor = colors.onBackground

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(64.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = primaryTextColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Normal,
            color = primaryTextColor
        )
    }
}

@Composable
fun SettingsSectionLabel(
    text: String
) {
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = secondaryTextColor,
        modifier = Modifier
            .padding(start = 24.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsCard(
    content: @Composable ColumnScope.() -> Unit
) {
    val cardBgColor = MaterialTheme.colorScheme.surface
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        color = cardBgColor
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    iconVector: ImageVector? = null,
    iconRes: Int? = null,
    valueText: String? = null,
    showChevron: Boolean = true,
    titleColor: Color = MaterialTheme.colorScheme.onBackground,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outline

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
        } else if (iconVector != null) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = titleColor
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = secondaryTextColor
                )
            }
        }

        if (trailingContent != null) {
            trailingContent()
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!valueText.isNullOrBlank()) {
                    Text(
                        text = valueText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = secondaryTextColor
                    )
                    if (showChevron) Spacer(modifier = Modifier.width(6.dp))
                }
                if (showChevron) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = secondaryTextColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val diBadgeColors = DiBadgeTheme.colors
    val colors = MaterialTheme.colorScheme

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 18.dp else 2.dp,
        animationSpec = tween(durationMillis = 200),
        label = "switchThumbOffset"
    )

    val trackColor = if (checked) diBadgeColors.accent else colors.surface
    val thumbColor = if (checked) colors.background else diBadgeColors.tabInactive
    val borderColor = if (checked) diBadgeColors.accent else diBadgeColors.tabInactive

    Surface(
        modifier = Modifier
            .size(width = 44.dp, height = 26.dp)
            .clickable { onCheckedChange(!checked) }
            .semantics { contentDescription = if (checked) "On" else "Off" },
        shape = CircleShape,
        color = trackColor,
        border = BorderStroke(1.5.dp, borderColor)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterStart
        ) {
            Surface(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(if (checked) 20.dp else 16.dp),
                shape = CircleShape,
                color = thumbColor
            ) {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemePickerSheet(
    currentMode: AppThemeMode,
    onModeSelected: (AppThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val tealAccent = diBadgeColors.accent
    val tabInactiveColor = diBadgeColors.tabInactive

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
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Theme",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = primaryTextColor,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            val modes = listOf(
                AppThemeMode.SYSTEM to "System default",
                AppThemeMode.LIGHT to "Light",
                AppThemeMode.DARK to "Dark"
            )

            modes.forEach { (mode, label) ->
                val isSelected = currentMode == mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clickable {
                            onModeSelected(mode)
                            onDismiss()
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Custom 22dp Radio Button
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
                        text = label,
                        fontSize = 16.sp,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        color = primaryTextColor
                    )
                }
            }
        }
    }
}
