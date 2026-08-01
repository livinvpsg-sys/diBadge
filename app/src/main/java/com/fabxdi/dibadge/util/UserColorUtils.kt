package com.fabxdi.dibadge.util

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

object UserColorUtils {
    private val colors = listOf(
        Color(0xFFEF5350), // Red
        Color(0xFFEC407A), // Pink
        Color(0xFFAB47BC), // Purple
        Color(0xFF7E57C2), // Deep Purple
        Color(0xFF5C6BC0), // Indigo
        Color(0xFF42A5F5), // Blue
        Color(0xFF26A69A), // Teal
        Color(0xFF66BB6A), // Green
        Color(0xFFFFA726), // Orange
        Color(0xFF8D6E63)  // Brown
    )

    fun getColorForName(name: String): Color {
        val index = abs(name.hashCode()) % colors.size
        return colors[index]
    }
}
