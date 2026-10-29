package com.fabxdi.dibadge.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fabxdi.dibadge.ui.theme.FileIconColors

@Composable
fun DocumentTypeTile(
    extension: String,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    widthDp: Int = 28,
    heightDp: Int = 34
) {
    val theme = FileIconColors.getTheme(extension, isDark)

    Box(
        modifier = modifier.size(widthDp.dp, heightDp.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scaleX = size.width / 28f
            val scaleY = size.height / 34f

            scale(scaleX, scaleY, pivot = Offset.Zero) {
                // Base page shape
                val basePath = Path().apply {
                    moveTo(3f, 0f)
                    lineTo(17f, 0f)
                    lineTo(26f, 9f)
                    lineTo(26f, 31f)
                    arcTo(Rect(20f, 28f, 26f, 34f), 0f, 90f, false)
                    lineTo(3f, 34f)
                    arcTo(Rect(0f, 28f, 6f, 34f), 90f, 90f, false)
                    lineTo(0f, 3f)
                    arcTo(Rect(0f, 0f, 6f, 6f), 180f, 90f, false)
                    close()
                }
                drawPath(path = basePath, color = theme.color)

                // Folded corner
                val foldPath = Path().apply {
                    moveTo(17f, 0f)
                    lineTo(26f, 9f)
                    lineTo(20f, 9f)
                    arcTo(Rect(17f, 3f, 23f, 9f), 90f, 90f, false)
                    close()
                }
                drawPath(path = foldPath, color = Color.White.copy(alpha = 0.35f))

                // Zipper
                if (theme.hasZipper) {
                    val zipperPath = Path().apply {
                        moveTo(13f, 4f)
                        lineTo(13f, 18f)
                    }
                    drawPath(
                        path = zipperPath,
                        color = Color.White.copy(alpha = 0.6f),
                        style = Stroke(
                            width = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 2f), 0f)
                        )
                    )
                }
            }
        }

        // Text Mark
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (theme.hasZipper) 2.dp else 5.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = theme.mark,
                fontSize = theme.fontSize,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
