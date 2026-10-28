package com.fabxdi.dibadge.util

object FileBlocklistValidator {

    private val BLOCKED_EXTENSIONS = setOf(
        "apk", "exe", "bat", "cmd", "msi", "sh", "jar", "scr", "com", "vbs", "ps1"
    )

    fun isFileBlocked(fileName: String): Boolean {
        if (fileName.isBlank()) return false
        val ext = fileName.substringAfterLast('.', "").lowercase().trim()
        return ext in BLOCKED_EXTENSIONS
    }
}
