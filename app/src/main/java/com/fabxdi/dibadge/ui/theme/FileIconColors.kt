package com.fabxdi.dibadge.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

@Immutable
data class FileIconTheme(
    val color: Color,
    val mark: String,
    val fontSize: TextUnit,
    val hasZipper: Boolean = false
)

object FileIconColors {
    fun getTheme(extension: String, isDark: Boolean): FileIconTheme {
        val ext = extension.lowercase()
        return when (ext) {
            "doc", "docx" -> FileIconTheme(if (isDark) Color(0xFF5B8DEF) else Color(0xFF2F5FC0), "W", 13.sp)
            "pdf" -> FileIconTheme(if (isDark) Color(0xFFE5534B) else Color(0xFFC4433A), "PDF", 7.5.sp)
            "txt" -> FileIconTheme(if (isDark) Color(0xFF8A9199) else Color(0xFF65707A), "TXT", 7.5.sp)
            "rtf" -> FileIconTheme(if (isDark) Color(0xFF8C8FE6) else Color(0xFF5558B8), "RTF", 7.5.sp)
            "odt" -> FileIconTheme(if (isDark) Color(0xFF4FB0E6) else Color(0xFF1E7DB5), "ODT", 7.5.sp)
            "xls", "xlsx" -> FileIconTheme(if (isDark) Color(0xFF3FB27F) else Color(0xFF1F7A4F), "X", 13.sp)
            "csv" -> FileIconTheme(if (isDark) Color(0xFF9BCB4F) else Color(0xFF5E8F1E), "CSV", 7.5.sp)
            "ppt", "pptx" -> FileIconTheme(if (isDark) Color(0xFFF0883E) else Color(0xFFC25A12), "P", 13.sp)
            "html", "htm" -> FileIconTheme(if (isDark) Color(0xFFB07CE8) else Color(0xFF7A47B8), "</>", 9.sp)
            "zip" -> FileIconTheme(if (isDark) Color(0xFFE8B03A) else Color(0xFFA8780E), "ZIP", 7.5.sp, hasZipper = true)
            "rar" -> FileIconTheme(if (isDark) Color(0xFFD9835F) else Color(0xFFA84F2E), "RAR", 7.5.sp, hasZipper = true)
            "pages" -> FileIconTheme(if (isDark) Color(0xFF82A8FF) else Color(0xFF3C6FD9), "Pg", 12.sp)
            "ods" -> FileIconTheme(if (isDark) Color(0xFF5FD39A) else Color(0xFF23915E), "ODS", 7.5.sp)
            "numbers" -> FileIconTheme(if (isDark) Color(0xFF58C26B) else Color(0xFF2E8B3F), "Nm", 12.sp)
            "odp" -> FileIconTheme(if (isDark) Color(0xFFF6A25D) else Color(0xFFC76B1E), "ODP", 7.5.sp)
            "key" -> FileIconTheme(if (isDark) Color(0xFFF7B267) else Color(0xFFC98035), "Key", 9.sp)
            "xml" -> FileIconTheme(if (isDark) Color(0xFF8F9BC9) else Color(0xFF505C8F), "XML", 7.5.sp)
            "json" -> FileIconTheme(if (isDark) Color(0xFFCDAA5A) else Color(0xFF8C6B15), "{ }", 10.sp)
            "7z" -> FileIconTheme(if (isDark) Color(0xFFC9C14E) else Color(0xFF8A8410), "7Z", 8.5.sp, hasZipper = true)
            "tar" -> FileIconTheme(if (isDark) Color(0xFFC9A27A) else Color(0xFF8A6A3E), "TAR", 7.5.sp, hasZipper = true)
            "gz" -> FileIconTheme(if (isDark) Color(0xFFB58B6A) else Color(0xFF7A5436), "GZ", 8.5.sp, hasZipper = true)
            "dwg" -> FileIconTheme(if (isDark) Color(0xFF7FA6D1) else Color(0xFF386C9F), "DWG", 7.5.sp)
            "dxf" -> FileIconTheme(if (isDark) Color(0xFFA3B8CF) else Color(0xFF5A728C), "DXF", 7.5.sp)
            "ifc" -> FileIconTheme(if (isDark) Color(0xFFC58BD1) else Color(0xFF8A3E99), "IFC", 7.5.sp)
            "rvt" -> FileIconTheme(if (isDark) Color(0xFF5E86E0) else Color(0xFF2F55B0), "RVT", 7.5.sp)
            "skp" -> FileIconTheme(if (isDark) Color(0xFFE0788F) else Color(0xFFB03F5A), "SKP", 7.5.sp)
            else -> {
                val mark = ext.uppercase().take(3).ifBlank { "DOC" }
                val fSize = if (mark.length > 2) 7.5.sp else 12.sp
                FileIconTheme(if (isDark) Color(0xFF8A9199) else Color(0xFF65707A), mark, fSize)
            }
        }
    }
}
