package com.fabxdi.dibadge.util

import androidx.compose.ui.graphics.Color
import java.util.Locale
import kotlin.math.abs

object UserColorUtils {
    // Non-teal identity palette (dark / light)
    private val darkIdentityColors = listOf(
        Color(0xFF7E9CE0), // Blue
        Color(0xFFB48AD6), // Violet
        Color(0xFFE0B25E), // Amber
        Color(0xFFE08AA6), // Rose
        Color(0xFFE39A6B), // Orange
        Color(0xFF9AA7B8)  // Slate
    )

    private val lightIdentityColors = listOf(
        Color(0xFF3E5EA8), // Blue
        Color(0xFF7A4AA0), // Violet
        Color(0xFF9A6B12), // Amber
        Color(0xFFA8466A), // Rose
        Color(0xFFA8541F), // Orange
        Color(0xFF5B6878)  // Slate
    )

    fun getIdentityColor(key: String, isDark: Boolean = true): Color {
        if (key.isBlank()) return if (isDark) darkIdentityColors[0] else lightIdentityColors[0]
        val cleanKey = key.trim().lowercase()
        var hash = 0
        cleanKey.forEachIndexed { i, char ->
            hash = 31 * hash + char.code + i
        }
        val index = abs(hash) % darkIdentityColors.size
        return if (isDark) darkIdentityColors[index] else lightIdentityColors[index]
    }

    fun getColorForName(name: String, isDark: Boolean = true): Color {
        return getIdentityColor(name, isDark)
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
