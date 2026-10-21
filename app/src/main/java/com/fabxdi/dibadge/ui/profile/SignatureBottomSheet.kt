package com.fabxdi.dibadge.ui.profile

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureBottomSheet(
    initialMode: String = "draw",
    initialTypedName: String = "",
    initialStrokes: List<List<Offset>> = emptyList(),
    existingSignatureUrl: String = "",
    onDismiss: () -> Unit,
    onSignatureSaved: (sigDataUrl: String, mode: String, typedName: String, strokes: List<List<Offset>>) -> Unit
) {
    var selectedMode by remember { mutableStateOf(initialMode.ifBlank { "draw" }) }
    var typedName by remember { mutableStateOf(initialTypedName) }
    var selectedFontIndex by remember { mutableIntStateOf(0) }

    val currentStroke = remember { mutableStateListOf<Offset>() }
    val allStrokes = remember { mutableStateListOf<List<Offset>>().apply { addAll(initialStrokes) } }

    var showExistingImage by remember(existingSignatureUrl, initialStrokes, initialMode) {
        mutableStateOf(initialStrokes.isEmpty() && existingSignatureUrl.isNotBlank() && initialMode == "draw")
    }

    val existingBitmap = remember(existingSignatureUrl) {
        parseSignatureImageBitmap(existingSignatureUrl)
    }

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val pageBgColor = colors.background
    val outlineColor = colors.outline
    val accentColor = diBadgeColors.accent
    val tabInactiveColor = diBadgeColors.tabInactive

    val isDark = colors.background.red < 0.5f
    val strokeColorHex = if (isDark) "#FAF9F5" else "#1C1B1F"

    BackHandler(enabled = true, onBack = onDismiss)

    // Validation
    val hasStrokes = allStrokes.isNotEmpty() || currentStroke.isNotEmpty()
    val canSaveDraw = showExistingImage || hasStrokes
    val canSaveType = typedName.trim().isNotBlank()
    val canSave = if (selectedMode == "draw") canSaveDraw else canSaveType

    // Font Options
    val fontOptions = remember {
        listOf(
            "Dancing Script" to FontFamily.Cursive,
            "Caveat" to FontFamily.Serif,
            "Homemade Apple" to FontFamily.SansSerif
        )
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
                    .padding(top = 12.dp, bottom = 18.dp),
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
                .padding(bottom = 20.dp)
        ) {
            // Centered Tabs: Draw / Type
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Draw Tab
                val isDrawSelected = selectedMode == "draw"
                Surface(
                    modifier = Modifier
                        .height(34.dp)
                        .clickable { selectedMode = "draw" }
                        .semantics {
                            contentDescription = "Draw tab, ${if (isDrawSelected) "selected" else "not selected"}"
                        },
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, if (isDrawSelected) primaryTextColor else outlineColor)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Draw",
                            fontSize = 14.sp,
                            fontWeight = if (isDrawSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isDrawSelected) primaryTextColor else tabInactiveColor
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Type Tab
                val isTypeSelected = selectedMode == "type"
                Surface(
                    modifier = Modifier
                        .height(34.dp)
                        .clickable { selectedMode = "type" }
                        .semantics {
                            contentDescription = "Type tab, ${if (isTypeSelected) "selected" else "not selected"}"
                        },
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, if (isTypeSelected) primaryTextColor else outlineColor)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Type",
                            fontSize = 14.sp,
                            fontWeight = if (isTypeSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isTypeSelected) primaryTextColor else tabInactiveColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (selectedMode == "draw") {
                // ==================== DRAW TAB ====================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        showExistingImage = false
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
                            }
                            .semantics {
                                contentDescription = "Signature drawing area, draw with your finger"
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = pageBgColor
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            if (showExistingImage && existingBitmap != null) {
                                drawImage(
                                    image = existingBitmap,
                                    dstSize = IntSize(size.width.toInt(), size.height.toInt())
                                )
                            }

                            for (stroke in allStrokes) {
                                for (i in 0 until stroke.size - 1) {
                                    drawLine(
                                        color = primaryTextColor,
                                        start = stroke[i],
                                        end = stroke[i + 1],
                                        strokeWidth = 2.5.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                            for (i in 0 until currentStroke.size - 1) {
                                drawLine(
                                    color = primaryTextColor,
                                    start = currentStroke[i],
                                    end = currentStroke[i + 1],
                                    strokeWidth = 2.5.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    }
                }
            } else {
                // ==================== TYPE TAB ====================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    // Your name field
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = pageBgColor
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Your name",
                                fontSize = 12.sp,
                                color = secondaryTextColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            BasicTextField(
                                value = typedName,
                                onValueChange = { if (it.length <= 40) typedName = it },
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
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Three Handwriting Style Options
                    fontOptions.forEachIndexed { index, (fontName, fontFam) ->
                        val isStyleSelected = selectedFontIndex == index
                        val displayText = typedName.ifBlank { "Your Name" }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clickable { selectedFontIndex = index }
                                .semantics {
                                    contentDescription = "Signature style ${index + 1} of 3, ${if (isStyleSelected) "selected" else "not selected"}"
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = pageBgColor,
                            border = BorderStroke(1.dp, if (isStyleSelected) primaryTextColor else outlineColor)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = displayText,
                                    fontSize = 26.sp,
                                    fontFamily = fontFam,
                                    fontStyle = if (index != 0) FontStyle.Italic else FontStyle.Normal,
                                    color = primaryTextColor,
                                    maxLines = 1
                                )
                            }
                        }

                        if (index < fontOptions.lastIndex) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bottom Action Buttons: Clear & Save
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 28.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Clear Button
                Text(
                    text = "Clear",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = secondaryTextColor,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable {
                            if (selectedMode == "draw") {
                                showExistingImage = false
                                currentStroke.clear()
                                allStrokes.clear()
                            } else {
                                typedName = ""
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                )

                Spacer(modifier = Modifier.width(28.dp))

                // Save Button
                Text(
                    text = "Save",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    modifier = Modifier
                        .alpha(if (canSave) 1.0f else 0.38f)
                        .heightIn(min = 48.dp)
                        .clickable(enabled = canSave) {
                            val finalStrokes = if (selectedMode == "draw") {
                                if (currentStroke.isNotEmpty()) allStrokes + listOf(currentStroke.toList()) else allStrokes.toList()
                            } else {
                                emptyList()
                            }

                            val finalTypedName = if (selectedMode == "type") typedName.trim() else ""

                            val sigDataUrl = if (selectedMode == "draw" && showExistingImage && finalStrokes.isEmpty()) {
                                existingSignatureUrl
                            } else {
                                createSignatureDataUrl(
                                    width = 1200,
                                    height = 800,
                                    strokes = finalStrokes,
                                    typedText = finalTypedName,
                                    mode = selectedMode,
                                    strokeColorHex = strokeColorHex,
                                    fontIndex = selectedFontIndex
                                )
                            }

                            onSignatureSaved(sigDataUrl, selectedMode, finalTypedName, finalStrokes)
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                )
            }
        }
    }
}
