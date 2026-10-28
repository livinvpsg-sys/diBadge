package com.fabxdi.dibadge.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object ScanNameValidator {

    fun generateDefaultScanName(now: LocalDateTime = LocalDateTime.now()): String {
        val formatter = DateTimeFormatter.ofPattern("d MMM yyyy h:mm a", Locale.ENGLISH)
        return "Scan ${now.format(formatter)}.pdf"
    }

    fun isValidScanName(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        val invalidChars = listOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        return !trimmed.any { it in invalidChars }
    }

    fun sanitizeScanName(name: String): String {
        val trimmed = name.trim().removeSuffix(".pdf").trim()
        return if (isValidScanName(trimmed)) "$trimmed.pdf" else generateDefaultScanName()
    }
}
