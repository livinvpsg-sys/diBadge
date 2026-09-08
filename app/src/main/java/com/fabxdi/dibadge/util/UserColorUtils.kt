package com.fabxdi.dibadge.util

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

object UserColorUtils {
    private val colors = listOf(
        Color(0xFFE53935), // Red
        Color(0xFFD81B60), // Pink
        Color(0xFF8E24AA), // Purple
        Color(0xFF5E35B1), // Deep Purple
        Color(0xFF3949AB), // Indigo
        Color(0xFF1E88E5), // Blue
        Color(0xFF039BE5), // Light Blue
        Color(0xFF00ACC1), // Cyan
        Color(0xFF00897B), // Teal
        Color(0xFF43A047), // Green
        Color(0xFF7CB342), // Light Green
        Color(0xFFFB8C00), // Orange
        Color(0xFFF4511E), // Deep Orange
        Color(0xFF8D6E63), // Brown
        Color(0xFF546E7A)  // Blue Grey
    )

    fun getColorForName(name: String): Color {
        if (name.isBlank()) return colors[0]
        val cleanName = name.trim().lowercase()
        var hash = 0
        cleanName.forEachIndexed { i, char ->
            hash = 31 * hash + char.code + i
        }
        val index = abs(hash) % colors.size
        return colors[index]
    }
}
