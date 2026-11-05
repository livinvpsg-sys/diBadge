package com.fabxdi.dibadge.util

object FileBlocklistValidator {

    private val BLOCKED_EXTENSIONS = setOf(
        // programs and installers
        "apk", "xapk", "apks", "exe", "msi", "msix", "appx", "dmg", "jar", "scr", "com", "pif", "cpl", "dll",
        // scripts
        "bat", "cmd", "sh", "vbs", "vbe", "js", "jse", "wsf", "wsh", "hta", "ps1", "reg", "lnk"
    )

    fun isFileBlocked(fileName: String): Boolean {
        if (fileName.isBlank()) return false
        // "report.exe." and "report.exe " must not slip through
        val clean = fileName.trim().trimEnd('.', ' ')
        val ext = clean.substringAfterLast('.', "").lowercase().trim()
        return ext in BLOCKED_EXTENSIONS
    }
}
