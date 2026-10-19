package com.fabxdi.dibadge.util

import java.security.SecureRandom

val BADGE_NUMBER_LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ"
val BADGE_NUMBER_DIGITS = "123456789"
val BADGE_NUMBER_ALPHABET = BADGE_NUMBER_LETTERS + BADGE_NUMBER_DIGITS

fun formatBadgeNumber(code: String): String {
    val clean = code.replace(" ", "").trim().uppercase()
    if (clean.length != 9) return clean
    return "${clean.substring(0, 3)} ${clean.substring(3, 6)} ${clean.substring(6, 9)}"
}

fun generateBadgeNumber(): String {
    val random = SecureRandom()
    var code: String
    do {
        val sb = StringBuilder(9)
        for (i in 0 until 9) {
            val index = random.nextInt(BADGE_NUMBER_ALPHABET.length)
            sb.append(BADGE_NUMBER_ALPHABET[index])
        }
        code = sb.toString()
    } while (!code.any { it in BADGE_NUMBER_LETTERS } || !code.any { it in BADGE_NUMBER_DIGITS })
    return code
}

fun isValidBadgeNumberFormat(code: String): Boolean {
    val clean = code.replace(" ", "").trim().uppercase()
    if (clean.length != 9) return false
    val hasLetter = clean.any { it in BADGE_NUMBER_LETTERS }
    val hasDigit = clean.any { it in BADGE_NUMBER_DIGITS }
    val validChars = clean.all { it in BADGE_NUMBER_ALPHABET }
    return hasLetter && hasDigit && validChars
}
