package com.fabxdi.dibadge.ui.my_forms

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.data.FormStamp
import com.fabxdi.dibadge.ui.profile.parseSignatureImageBitmap
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplicantYourSignatureSheet(
    formName: String,
    fieldLabel: String,
    profileFullName: String,
    profileBadgeNumber: String,
    profileSignatureUrl: String,
    onSignConfirmed: (FormStamp) -> Unit,
    onNotNow: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val tealAccent = diBadgeColors.accent

    val nowStr = remember {
        val dt = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(ZoneId.systemDefault()).toLocalDateTime()
        val date = dt.format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.getDefault()))
        val tz = ZoneId.systemDefault().rules.getOffset(Instant.now()).id.let { if (it == "Z") "GMT+0" else "GMT$it" }
        "$date $tz"
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
            // Title
            Text(
                text = "Your signature",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = primaryTextColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Subtitle
            val subtitleStr = listOfNotNull(formName.ifBlank { null }, fieldLabel.ifBlank { null }).joinToString(" · ")
            Text(
                text = subtitleStr.ifBlank { formName },
                fontSize = 13.sp,
                color = secondaryTextColor
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Stamp Preview Card (Identical stamp layout: signature at top, full name, badge number directly below, date/time/timezone in 11sp secondary text)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = colors.background,
                border = BorderStroke(1.dp, outlineColor)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxWidth()
                ) {
                    if (profileSignatureUrl.isNotBlank()) {
                        val sigBitmap = remember(profileSignatureUrl) { parseSignatureImageBitmap(profileSignatureUrl) }
                        if (sigBitmap != null) {
                            Image(
                                bitmap = sigBitmap,
                                contentDescription = "Signature stamp",
                                modifier = Modifier
                                    .widthIn(min = 96.dp, max = 120.dp)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Fit
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    Text(
                        text = profileFullName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryTextColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = profileBadgeNumber,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryTextColor
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = nowStr,
                        fontSize = 11.sp,
                        color = secondaryTextColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Footer actions: Not now (left) and Sign (right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Not now",
                    fontSize = 15.sp,
                    color = secondaryTextColor,
                    modifier = Modifier
                        .clickable { onNotNow() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )

                Spacer(modifier = Modifier.width(24.dp))

                Text(
                    text = "Sign",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = tealAccent,
                    modifier = Modifier
                        .clickable {
                            val tzOffsetStr = ZoneId.systemDefault().rules.getOffset(Instant.now()).id.let {
                                if (it == "Z") "GMT+0" else "GMT$it"
                            }
                            val stamp = FormStamp(
                                signatureUrl = profileSignatureUrl,
                                fullName = profileFullName,
                                badgeNumber = profileBadgeNumber,
                                signedAtMs = System.currentTimeMillis(),
                                tzOffset = tzOffsetStr
                            )
                            onSignConfirmed(stamp)
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}
