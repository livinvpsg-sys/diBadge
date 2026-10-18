package com.fabxdi.dibadge.util

import androidx.compose.ui.graphics.Color
import java.util.Locale
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

fun String.capitalizeWords(): String {
    if (this.isBlank()) return this
    return this.split(" ").joinToString(" ") { word ->
        if (word.isNotEmpty()) {
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        } else {
            word
        }
    }
}

fun getGreetingName(firstName: String, nickname: String = ""): String {
    return if (nickname.isNotBlank()) {
        nickname.trim()
    } else {
        firstName.capitalizeWords()
    }
}

fun fullName(firstName: String?, middleName: String?, lastName: String?): String {
    val fn = firstName?.trim() ?: ""
    val mn = middleName?.trim() ?: ""
    val ln = lastName?.trim() ?: ""
    return listOf(fn, mn, ln)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .replace(Regex("\\s+"), " ")
}

fun isValidName(name: String): Boolean {
    val trimmed = name.trim()
    if (trimmed.length > 40) return false
    return trimmed.all { it.isLetter() || it.isWhitespace() || it == '\'' || it == '-' || it == '.' }
}

fun maskEmail(email: String): String {
    if (email.isBlank() || !email.contains("@")) return email
    val parts = email.split("@")
    val local = parts[0]
    val domain = parts[1]
    val firstChar = local.take(1)
    val maskedLocal = firstChar + "•".repeat(10)
    return "$maskedLocal@$domain"
}

val avatarColors = listOf(
    Color(0xFF1F6F8B),
    Color(0xFF8B4F3C),
    Color(0xFF6B5B95),
    Color(0xFF4F7A5A),
    Color(0xFF8A6D3B),
    Color(0xFF7A4F6B),
    Color(0xFF3F5F8F),
    Color(0xFF5F6B73)
)

fun avatarColorFor(name: String): Color {
    val clean = name.trim().lowercase()
    if (clean.isEmpty()) return avatarColors[0]
    var hash = 0
    clean.forEachIndexed { i, char -> hash = 31 * hash + char.code + i }
    val index = abs(hash) % avatarColors.size
    return avatarColors[index]
}
