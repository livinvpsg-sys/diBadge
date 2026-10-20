package com.fabxdi.dibadge.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGroupBottomSheet(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val accentColor = diBadgeColors.accent
    val tabInactiveColor = diBadgeColors.tabInactive

    val canCreate = title.trim().isNotBlank() && !isCreating

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
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
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Title Row: "New Group" on left, "Create" on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "New Group",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryTextColor,
                    modifier = Modifier.padding(start = 4.dp)
                )

                if (isCreating) {
                    CircularProgressIndicator(
                        color = accentColor,
                        strokeWidth = 2.dp,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(18.dp)
                    )
                } else {
                    Text(
                        text = "Create",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (canCreate) accentColor else tabInactiveColor,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .heightIn(min = 48.dp)
                            .clickable(enabled = canCreate) {
                                isCreating = true
                                onConfirm(title.trim(), subtitle.trim())
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Plain Row 1: Name
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (title.isEmpty()) {
                    Text(
                        text = "Name",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = tabInactiveColor
                    )
                }
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = primaryTextColor
                    ),
                    cursorBrush = SolidColor(accentColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }

            HorizontalDivider(thickness = 1.dp, color = outlineColor)

            // Plain Row 2: Details
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (subtitle.isEmpty()) {
                    Text(
                        text = "Details",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = tabInactiveColor
                    )
                }
                BasicTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = primaryTextColor
                    ),
                    cursorBrush = SolidColor(accentColor),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
