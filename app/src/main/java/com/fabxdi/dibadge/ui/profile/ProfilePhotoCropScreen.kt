package com.fabxdi.dibadge.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import java.io.InputStream
import kotlin.math.max

fun loadOrientedBitmap(context: Context, imageUri: Uri): Bitmap? {
    return try {
        val inputStream: InputStream = context.contentResolver.openInputStream(imageUri) ?: return null
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        if (bitmap == null) return null

        val rotationDegrees = try {
            context.contentResolver.openInputStream(imageUri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) {
            0
        }

        if (rotationDegrees != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotationDegrees.toFloat())
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
fun ProfilePhotoCropScreen(
    imageUri: Uri,
    onCancelClick: () -> Unit,
    onDoneCrop: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val originalBitmap = remember(imageUri) {
        loadOrientedBitmap(context, imageUri)
    }

    if (originalBitmap == null) {
        LaunchedEffect(Unit) { onCancelClick() }
        return
    }

    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        val screenWidthPx = with(density) { this@BoxWithConstraints.maxWidth.toPx() }
        val screenHeightPx = with(density) { this@BoxWithConstraints.maxHeight.toPx() }

        val circleRadiusPx = with(density) { (screenWidthPx - 90.dp.toPx()) / 2f }
        val circleCenter = Offset(screenWidthPx / 2f, (screenHeightPx - with(density) { 72.dp.toPx() }) / 2f)

        val minScale = remember(originalBitmap, circleRadiusPx) {
            val circleDiameter = circleRadiusPx * 2f
            max(circleDiameter / originalBitmap.width.toFloat(), circleDiameter / originalBitmap.height.toFloat())
        }

        LaunchedEffect(minScale) {
            if (scale < minScale) scale = minScale
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            scale = if (scale > minScale * 1.5f) minScale else minScale * 2.5f
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(minScale, minScale * 5f)
                        scale = newScale

                        val imgW = originalBitmap.width * scale
                        val imgH = originalBitmap.height * scale

                        val maxPanX = max(0f, (imgW / 2f) - circleRadiusPx)
                        val maxPanY = max(0f, (imgH / 2f) - circleRadiusPx)

                        offsetX = (offsetX + pan.x).coerceIn(-maxPanX, maxPanX)
                        offsetY = (offsetY + pan.y).coerceIn(-maxPanY, maxPanY)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val imgBitmap = originalBitmap.asImageBitmap()

                val scaledW = originalBitmap.width * scale
                val scaledH = originalBitmap.height * scale

                val imgLeft = circleCenter.x - (scaledW / 2f) + offsetX
                val imgTop = circleCenter.y - (scaledH / 2f) + offsetY

                // 1. Draw Image
                drawImage(
                    image = imgBitmap,
                    dstOffset = IntOffset(imgLeft.toInt(), imgTop.toInt()),
                    dstSize = IntSize(scaledW.toInt(), scaledH.toInt())
                )

                // 2. Dim outside circle (62% black overlay)
                val circlePath = Path().apply {
                    addOval(
                        Rect(
                            left = circleCenter.x - circleRadiusPx,
                            top = circleCenter.y - circleRadiusPx,
                            right = circleCenter.x + circleRadiusPx,
                            bottom = circleCenter.y + circleRadiusPx
                        )
                    )
                }

                clipPath(circlePath, clipOp = ClipOp.Difference) {
                    drawRect(color = Color.Black.copy(alpha = 0.62f))
                }

                // 3. Ring outline (1.5dp #F0F3F5)
                drawCircle(
                    color = Color(0xFFF0F3F5),
                    radius = circleRadiusPx,
                    center = circleCenter,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }

        // Bottom Bar (72dp high)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(72.dp)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Cancel",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFC5CBD1),
                modifier = Modifier.clickable { onCancelClick() }
            )

            Text(
                text = "Done",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = DiBadgeTheme.colors.accent,
                modifier = Modifier.clickable {
                    val cropped = cropBitmapToCircleSquare(
                        bitmap = originalBitmap,
                        scale = scale,
                        offsetX = offsetX,
                        offsetY = offsetY,
                        circleRadiusPx = circleRadiusPx,
                        circleCenter = circleCenter
                    )
                    onDoneCrop(cropped)
                }
            )
        }
    }
}

fun cropBitmapToCircleSquare(
    bitmap: Bitmap,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    circleRadiusPx: Float,
    circleCenter: Offset
): Bitmap {
    val scaledW = bitmap.width * scale
    val scaledH = bitmap.height * scale

    val imgLeft = circleCenter.x - (scaledW / 2f) + offsetX
    val imgTop = circleCenter.y - (scaledH / 2f) + offsetY

    val cropLeftScreen = circleCenter.x - circleRadiusPx
    val cropTopScreen = circleCenter.y - circleRadiusPx

    val cropLeftImg = max(0f, (cropLeftScreen - imgLeft) / scale)
    val cropTopImg = max(0f, (cropTopScreen - imgTop) / scale)
    val cropSideImg = (circleRadiusPx * 2f) / scale

    val finalCropX = cropLeftImg.toInt().coerceIn(0, (bitmap.width - 1).coerceAtLeast(0))
    val finalCropY = cropTopImg.toInt().coerceIn(0, (bitmap.height - 1).coerceAtLeast(0))
    val finalCropW = cropSideImg.toInt().coerceIn(1, bitmap.width - finalCropX)
    val finalCropH = cropSideImg.toInt().coerceIn(1, bitmap.height - finalCropY)

    return Bitmap.createBitmap(bitmap, finalCropX, finalCropY, finalCropW, finalCropH)
}
