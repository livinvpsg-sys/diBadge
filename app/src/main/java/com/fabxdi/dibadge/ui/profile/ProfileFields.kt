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

                if (value.isNotEmpty() || prefixText != null || leadingContent != null) {
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
    width: Int,
    height: Int,
    strokes: List<List<Offset>>,
    typedText: String,
    mode: String,
    strokeColorHex: String
): String {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.parseColor(strokeColorHex)
        strokeWidth = 12f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    if (mode == "draw") {
        for (stroke in strokes) {
            val path = Path()
            for (i in stroke.indices) {
                val pt = stroke[i]
                if (i == 0) path.moveTo(pt.x * 3f, pt.y * 3f)
                else path.lineTo(pt.x * 3f, pt.y * 3f)
            }
            canvas.drawPath(path, paint)
        }
    } else if (typedText.isNotBlank()) {
        paint.style = Paint.Style.FILL
        paint.textSize = 160f
        paint.typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
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

@Composable
fun SignatureDialog(
    initialMode: String = "draw",
    initialTypedName: String = "",
    initialStrokes: List<List<Offset>> = emptyList(),
    onDismiss: () -> Unit,
    onSignatureSaved: (sigDataUrl: String, mode: String, typedName: String, strokes: List<List<Offset>>) -> Unit
) {
    var selectedMode by remember { mutableStateOf(initialMode.ifBlank { "draw" }) }
    var typedName by remember { mutableStateOf(initialTypedName) }

    val currentStroke = remember { mutableStateListOf<Offset>() }
    val allStrokes = remember { mutableStateListOf<List<Offset>>().apply { addAll(initialStrokes) } }

    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val strokeColorHex = if (isDark) "#FAF9F5" else "#1C1B1F"
    val strokeColor = if (isDark) Color(0xFFFAF9F5) else Color(0xFF1C1B1F)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = selectedMode == "draw",
                        onClick = { selectedMode = "draw" },
                        label = { Text("Draw", fontWeight = if (selectedMode == "draw") FontWeight.Bold else FontWeight.Normal) },
                        shape = CircleShape
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    FilterChip(
                        selected = selectedMode == "type",
                        onClick = { selectedMode = "type" },
                        label = { Text("Type", fontWeight = if (selectedMode == "type") FontWeight.Bold else FontWeight.Normal) },
                        shape = CircleShape
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedMode == "draw") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Draw your signature below:",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            currentStroke.clear()
                                            currentStroke.add(offset)
                                        },
                                        onDrag = { change, _ ->
                                            currentStroke.add(change.position)
                                        },
                                        onDragEnd = {
                                            if (currentStroke.isNotEmpty()) {
                                                allStrokes.add(currentStroke.toList())
                                                currentStroke.clear()
                                            }
                                        }
                                    )
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                for (stroke in allStrokes) {
                                    for (i in 0 until stroke.size - 1) {
                                        drawLine(
                                            color = strokeColor,
                                            start = stroke[i],
                                            end = stroke[i + 1],
                                            strokeWidth = 3.dp.toPx(),
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }
                                for (i in 0 until currentStroke.size - 1) {
                                    drawLine(
                                        color = strokeColor,
                                        start = currentStroke[i],
                                        end = currentStroke[i + 1],
                                        strokeWidth = 3.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Type your signature:",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = typedName,
                            onValueChange = { typedName = it },
                            placeholder = { Text("Your Name", fontFamily = FontFamily.Cursive) },
                            textStyle = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 26.sp,
                                fontFamily = FontFamily.Cursive,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalStrokes = if (selectedMode == "draw") {
                        if (currentStroke.isNotEmpty()) allStrokes + listOf(currentStroke.toList()) else allStrokes.toList()
                    } else {
                        emptyList()
                    }

                    val finalTypedName = if (selectedMode == "type") typedName.trim() else ""

                    val sigDataUrl = createSignatureDataUrl(
                        width = 1200,
                        height = 800,
                        strokes = finalStrokes,
                        typedText = finalTypedName,
                        mode = selectedMode,
                        strokeColorHex = strokeColorHex
                    )

                    onSignatureSaved(sigDataUrl, selectedMode, finalTypedName, finalStrokes)
                }
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (selectedMode == "draw") {
                        currentStroke.clear()
                        allStrokes.clear()
                    } else {
                        typedName = ""
                    }
                }
            ) {
                Text(
                    text = "Clear",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
