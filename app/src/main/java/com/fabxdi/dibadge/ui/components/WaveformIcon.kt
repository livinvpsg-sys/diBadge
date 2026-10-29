package com.fabxdi.dibadge.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

@Composable
fun WaveformIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val barWidth = size.width / 9f
        val maxH = size.height
        val bars = listOf(0.4f, 0.8f, 1f, 0.6f, 0.4f)
        var x = 0f
        for (h in bars) {
            val barH = maxH * h
            drawRoundRect(
                color = color,
                topLeft = Offset(x, (maxH - barH) / 2f),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
            x += barWidth * 2f
        }
    }
}
