package com.fabxdi.dibadge.ui.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import java.io.ByteArrayOutputStream

fun parseSignatureImageBitmap(sigUrl: String): ImageBitmap? {
    if (sigUrl.isBlank()) return null
    return try {
        if (sigUrl.startsWith("data:image")) {
            val base64Str = sigUrl.substringAfter("base64,")
            val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            bitmap?.asImageBitmap()
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
fun ProfileCardRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    prefixText: String? = null,
    errorMessage: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    onFieldClick: (() -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val labelColor = if (errorMessage != null) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val valueColor = if (enabled) {
        MaterialTheme.colorScheme.onBackground
    } else {
        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(enabled = onFieldClick != null && enabled) { onFieldClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = labelColor
                )

                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (leadingContent != null) {
                        leadingContent()
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (prefixText != null) {
                        val prefixColor = if (value.isEmpty()) {
                            DiBadgeTheme.colors.tabInactive
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            text = prefixText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            color = prefixColor
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled && !readOnly,
                        readOnly = readOnly,
                        singleLine = singleLine,
                        maxLines = maxLines,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            color = valueColor
                        ),
                        cursorBrush = SolidColor(DiBadgeTheme.colors.accent),
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isFocused = it.isFocused }
                    )
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = errorMessage,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(8.dp))
                trailingIcon()
            }
        }
    }
}

@Composable
fun CountryChip(
    code: String = "+65",
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .height(30.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "🇸🇬 $code",
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onBackground
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

fun createSignatureDataUrl(
    width: Int = 1200,
    height: Int = 800,
    strokes: List<List<Offset>>,
    typedText: String,
    mode: String,
    strokeColorHex: String,
    fontIndex: Int = 0
): String {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val colorInt = try { android.graphics.Color.parseColor(strokeColorHex) } catch (e: Exception) { android.graphics.Color.BLACK }

    val paint = Paint().apply {
        isAntiAlias = true
        color = colorInt
        strokeWidth = 14f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    if (mode == "draw" && strokes.isNotEmpty()) {
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE

        for (stroke in strokes) {
            for (pt in stroke) {
                if (pt.x < minX) minX = pt.x
                if (pt.x > maxX) maxX = pt.x
                if (pt.y < minY) minY = pt.y
                if (pt.y > maxY) maxY = pt.y
            }
        }

        val strokeWidthRange = (maxX - minX).coerceAtLeast(1f)
        val strokeHeightRange = (maxY - minY).coerceAtLeast(1f)

        val paddingX = width * 0.08f
        val paddingY = height * 0.08f
        val availW = width - (paddingX * 2f)
        val availH = height - (paddingY * 2f)

        val scale = (availW / strokeWidthRange).coerceAtMost(availH / strokeHeightRange)

        val offsetX = paddingX + (availW - strokeWidthRange * scale) / 2f - minX * scale
        val offsetY = paddingY + (availH - strokeHeightRange * scale) / 2f - minY * scale

        for (stroke in strokes) {
            if (stroke.isEmpty()) continue
            val path = Path()
            for (i in stroke.indices) {
                val pt = stroke[i]
                val scaledX = pt.x * scale + offsetX
                val scaledY = pt.y * scale + offsetY
                if (i == 0) path.moveTo(scaledX, scaledY)
                else path.lineTo(scaledX, scaledY)
            }
            canvas.drawPath(path, paint)
        }
    } else if (typedText.isNotBlank()) {
        val selectedTypeface = when (fontIndex) {
            0 -> Typeface.create("cursive", Typeface.ITALIC)
            1 -> Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            else -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD_ITALIC)
        }

        paint.style = Paint.Style.FILL
        paint.textSize = 150f
        paint.typeface = selectedTypeface
        val bounds = Rect()
        paint.getTextBounds(typedText, 0, typedText.length, bounds)
        val x = (width - bounds.width()) / 2f
        val y = (height + bounds.height()) / 2f
        canvas.drawText(typedText, x, y, paint)
    }

    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
    val bytes = stream.toByteArray()
    return "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
}
