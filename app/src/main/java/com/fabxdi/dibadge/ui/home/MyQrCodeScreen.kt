package com.fabxdi.dibadge.ui.home

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.fabxdi.dibadge.ui.settings.SettingsCard
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.util.QrCodeGenerator
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyQrCodeScreen(
    userFullName: String,
    userCode: String = "7K2M9QX4B",
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showShareSheet by remember { mutableStateOf(false) }

    // Screen brightness raise & restore
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
    val diBadgeColors = DiBadgeTheme.colors

    val pageBgColor = colors.background
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val tealAccent = diBadgeColors.accent

    val qrPayload = remember(userCode) { "dibadge:$userCode" }
    val qrBitmap = remember(qrPayload) {
        QrCodeGenerator.generateQrBitmap(qrPayload, 512)
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(pageBgColor)
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
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
                    text = "My QR code",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Normal,
                    color = primaryTextColor
                )
            }
        },
        bottomBar = {
            // Pinned Bottom Share Text Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 36.dp)
                    .height(52.dp)
                    .clickable { showShareSheet = true },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = null,
                        tint = tealAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Share",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tealAccent
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(pageBgColor)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Card (20dp side margins, 16dp corners, card fill, 1dp outline)
            SettingsCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 240dp Square QR Image on White Background with 14dp corners
                    Surface(
                        modifier = Modifier.size(240.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "Your QR code, $userCode",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    // Full Name (20sp Medium, NO badge number text)
                    Text(
                        text = userFullName.ifBlank { "User" },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = primaryTextColor,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Others can scan this code to connect with you on diBadge",
                fontSize = 13.sp,
                color = secondaryTextColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp)
            )
        }
    }

    if (showShareSheet) {
        ModalBottomSheet(
            onDismissRequest = { showShareSheet = false },
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
                    text = "Share my QR code",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryTextColor,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Option 1: Share with another app
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clickable {
                            showShareSheet = false
                            val pngFile = generateShareableQrPng(context, qrBitmap, userFullName, userCode)
                            if (pngFile != null) {
                                try {
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pngFile)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share my QR code"))
                                } catch (e: Exception) {}
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, tint = secondaryTextColor, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Share with another app", fontSize = 16.sp, color = primaryTextColor)
                        Text("Send the QR image with any app", fontSize = 13.sp, color = secondaryTextColor)
                    }
                }

                HorizontalDivider(thickness = 1.dp, color = outlineColor)

                // Option 2: Save to gallery
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clickable {
                            showShareSheet = false
                            val pngBitmap = generateShareableBitmap(qrBitmap, userFullName, userCode)
                            val saved = saveImageToGallery(context, pngBitmap, "diBadge_QR_${userCode}.png")
                            if (saved) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Saved to gallery")
                                }
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = secondaryTextColor, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Save to gallery", fontSize = 16.sp, color = primaryTextColor)
                        Text("Keep the QR image on this phone", fontSize = 13.sp, color = secondaryTextColor)
                    }
                }
            }
        }
    }
}

private fun generateShareableBitmap(qrBitmap: Bitmap, name: String, code: String): Bitmap {
    val size = 1024
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(AndroidColor.WHITE)

    val paint = Paint().apply {
        isAntiAlias = true
        color = AndroidColor.BLACK
        textAlign = Paint.Align.CENTER
    }

    // Draw QR Bitmap (600x600 centered)
    val qrSize = 640
    val qrLeft = (size - qrSize) / 2
    val qrTop = 140
    val destRect = Rect(qrLeft, qrTop, qrLeft + qrSize, qrTop + qrSize)
    canvas.drawBitmap(qrBitmap, null, destRect, null)

    // Name
    paint.textSize = 42f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    canvas.drawText(name.ifBlank { "User" }, size / 2f, qrTop + qrSize + 80f, paint)

    // Code
    paint.textSize = 32f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    paint.color = AndroidColor.GRAY
    canvas.drawText("diBadge · $code", size / 2f, qrTop + qrSize + 140f, paint)

    return bitmap
}

private fun generateShareableQrPng(context: Context, qrBitmap: Bitmap, name: String, code: String): File? {
    return try {
        val bitmap = generateShareableBitmap(qrBitmap, name, code)
        val file = File(context.cacheDir, "diBadge_QR.png")
        val out = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.close()
        file
    } catch (e: Exception) {
        null
    }
}

private fun saveImageToGallery(context: Context, bitmap: Bitmap, fileName: String): Boolean {
    val resolver = context.contentResolver
    val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/diBadge")
        }
    }

    return try {
        val imageUri: Uri? = resolver.insert(imageCollection, contentValues)
        if (imageUri != null) {
            val outputStream: OutputStream? = resolver.openOutputStream(imageUri)
            if (outputStream != null) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.close()
                true
            } else false
        } else false
    } catch (e: Exception) {
        false
    }
}
