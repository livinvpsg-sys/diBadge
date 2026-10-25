package com.fabxdi.dibadge.ui.inbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme

@Composable
fun RejectRequestDialog(
    requesterName: String,
    onConfirmReject: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var reasonText by remember { mutableStateOf("") }
    var isFocused by remember { mutableStateOf(false) }

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val tealAccent = diBadgeColors.accent
    val errorColor = colors.error
    val outlineColor = colors.outline.copy(alpha = 0.60f)

    val underlineColor = if (isFocused) tealAccent else outlineColor

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Reject this request?",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = primaryTextColor
            )
        },
        text = {
            Column {
                Text(
                    text = "$requesterName will see that you rejected it.",
                    fontSize = 14.sp,
                    color = secondaryTextColor
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    if (reasonText.isEmpty()) {
                        Text(
                            text = "Reason (optional)",
                            fontSize = 15.sp,
                            color = secondaryTextColor.copy(alpha = 0.6f)
                        )
                    }
                    BasicTextField(
                        value = reasonText,
                        onValueChange = { if (it.length <= 200) reasonText = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            color = primaryTextColor
                        ),
                        cursorBrush = SolidColor(tealAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isFocused = it.isFocused }
                            .padding(bottom = 4.dp)
                    )
                }

                HorizontalDivider(
                    thickness = if (isFocused) 2.dp else 1.dp,
                    color = underlineColor,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Text(
                text = "Reject",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = errorColor,
                modifier = Modifier
                    .clickable {
                        onDismiss()
                        onConfirmReject(reasonText.trim())
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        },
        dismissButton = {
            Text(
                text = "Cancel",
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = secondaryTextColor,
                modifier = Modifier
                    .clickable { onDismiss() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        },
        containerColor = cardBgColor,
        shape = RoundedCornerShape(20.dp)
    )
}
