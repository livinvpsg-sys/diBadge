package com.fabxdi.dibadge.ui.home.qr

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.QrCodeGenerator
import com.fabxdi.dibadge.util.currentUserAvatarColor
import kotlinx.coroutines.launch

@Composable
fun MyQrCodeScreen(
    userFullName: String,
    photoUrl: String = "",
    onScan: () -> Unit = {},
    onBack: () -> Unit
) {
    // The secret behind the QR code and the invite link; "Reset" replaces it
    val userCode = rememberMyQrCode()
    var showResetDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as? Activity

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Raise the screen brightness while the code is shown, and restore it after
    DisposableEffect(Unit) {
        val originalBrightness = activity?.window?.attributes?.screenBrightness
        activity?.window?.attributes = activity?.window?.attributes?.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        }
        onDispose {
            activity?.window?.attributes = activity?.window?.attributes?.apply {
                screenBrightness = originalBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }

    BackHandler(enabled = true, onBack = onBack)

    val colors = MaterialTheme.colorScheme
    val accent = DiBadgeTheme.colors.accent
    val pageBg = colors.background
    val primaryText = colors.onBackground
    val secondaryText = colors.onSurfaceVariant
    val cardBg = colors.surface
    val outline = colors.outline

    // Plain text, not a web link: a phone camera shows only text, and only the diBadge scanner opens it
    val qrPayload = remember(userCode) { if (userCode.isBlank()) "" else "dibadge:$userCode" }
    val qrBitmap = remember(qrPayload) {
        if (qrPayload.isBlank()) null else QrCodeGenerator.generateQrBitmap(qrPayload, 512)
    }
    val link = if (userCode.isBlank()) "" else MyQrCode.linkFor(userCode)
    val name = userFullName.ifBlank { "User" }

    Scaffold(
        containerColor = pageBg,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(pageBg)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp + innerPadding.calculateBottomPadding())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = primaryText, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text("My QR code", fontSize = 20.sp, fontWeight = FontWeight.Normal, color = primaryText)
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        if (link.isNotBlank()) {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Connect with me on diBadge: $link")
                            }
                            context.startActivity(Intent.createChooser(send, "Share link"))
                        }
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = "Share link", tint = primaryText, modifier = Modifier.size(22.dp))
                }
                IconButton(
                    onClick = {
                        if (link.isNotBlank()) {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("diBadge link", link))
                            Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy link", tint = primaryText, modifier = Modifier.size(22.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            // The card: photo or initial, name, QR code
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(28.dp),
                color = cardBg
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(currentUserAvatarColor()),
                        contentAlignment = Alignment.Center
                    ) {
                        if (photoUrl.isNotBlank()) {
                            AsyncImage(
                                model = photoUrl,
                                contentDescription = name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = name.first().uppercase(),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(name, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Normal, color = primaryText, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.height(28.dp))
                    Spacer(Modifier.height(16.dp))

                    // White tile with the QR code and an accent dot in the middle
                    Surface(
                        modifier = Modifier.size(224.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (qrBitmap != null) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "Your QR code",
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(accent)
                                )
                            } else {
                                CircularProgressIndicator(color = accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text("Scan to connect with me on diBadge", fontSize = 14.sp, lineHeight = 20.sp, color = secondaryText, textAlign = TextAlign.Center, modifier = Modifier.height(20.dp))
                }
            }

            Spacer(Modifier.height(18.dp))

            // Scan pill, same style as the pill on the scan screen
            val pillBorder = BorderStroke(1.dp, primaryText.copy(alpha = 0.35f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Surface(
                    onClick = onScan,
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = Color.Transparent,
                    border = pillBorder
                ) {
                    Row(modifier = Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.QrCodeScanner, contentDescription = null, tint = primaryText, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Scan a QR code", fontSize = 14.sp, color = primaryText)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Reset my QR code",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable { showResetDialog = true }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = cardBg,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Reset my QR code?", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = primaryText) },
            text = {
                Text(
                    "Your current QR code and invite link will stop working. Share the new ones with the people who need them. Your connections are not affected.",
                    fontSize = 14.sp,
                    color = secondaryText
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    MyQrCode.reset()
                    coroutineScope.launch { snackbarHostState.showSnackbar("New QR code ready") }
                }) { Text("Reset", fontWeight = FontWeight.Bold, color = accent) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel", color = secondaryText) }
            }
        )
    }
}