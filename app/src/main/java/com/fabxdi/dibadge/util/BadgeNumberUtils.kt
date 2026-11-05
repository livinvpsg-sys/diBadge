package com.fabxdi.dibadge.util

import java.security.SecureRandom

const val BADGE_NUMBER_LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ"
const val BADGE_NUMBER_DIGITS = "123456789"
const val BADGE_NUMBER_ALPHABET = BADGE_NUMBER_LETTERS + BADGE_NUMBER_DIGITS

private val secureRandom = SecureRandom()

/** Upper case, without spaces or dashes: "abc-123 xyz" -> "ABC123XYZ". */
private fun cleanBadgeNumber(code: String): String =
    code.uppercase().filter { !it.isWhitespace() && it != '-' }

fun formatBadgeNumber(code: String): String {
    val clean = cleanBadgeNumber(code)
    if (clean.length != 9) return clean
    return "${clean.substring(0, 3)} ${clean.substring(3, 6)} ${clean.substring(6, 9)}"
}

fun normalizeBadgeNumber(code: String): String = cleanBadgeNumber(code)

/** The caller still has to check that the number is not already taken. */
fun generateBadgeNumber(): String {
    var code: String
    do {
        val sb = StringBuilder(9)
        for (i in 0 until 9) {
            sb.append(BADGE_NUMBER_ALPHABET[secureRandom.nextInt(BADGE_NUMBER_ALPHABET.length)])
        }
        code = sb.toString()
    } while (!code.any { it in BADGE_NUMBER_LETTERS } || !code.any { it in BADGE_NUMBER_DIGITS })
    return code
}

fun isValidBadgeNumberFormat(code: String): Boolean {
    val clean = cleanBadgeNumber(code)
    if (clean.length != 9) return false
    val hasLetter = clean.any { it in BADGE_NUMBER_LETTERS }
    val hasDigit = clean.any { it in BADGE_NUMBER_DIGITS }
    val validChars = clean.all { it in BADGE_NUMBER_ALPHABET }
    return hasLetter && hasDigit && validChars
}
