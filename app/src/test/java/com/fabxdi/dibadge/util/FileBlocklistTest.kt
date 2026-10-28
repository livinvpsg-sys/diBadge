package com.fabxdi.dibadge.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileBlocklistTest {

    @Test
    fun isFileBlocked_allowedFiles_returnsFalse() {
        assertFalse(FileBlocklistValidator.isFileBlocked("document.pdf"))
        assertFalse(FileBlocklistValidator.isFileBlocked("report.docx"))
        assertFalse(FileBlocklistValidator.isFileBlocked("data.csv"))
        assertFalse(FileBlocklistValidator.isFileBlocked("archive.zip"))
        assertFalse(FileBlocklistValidator.isFileBlocked("model.dwg"))
    }

    @Test
    fun isFileBlocked_blockedFiles_returnsTrue() {
        assertTrue(FileBlocklistValidator.isFileBlocked("app.apk"))
        assertTrue(FileBlocklistValidator.isFileBlocked("setup.exe"))
        assertTrue(FileBlocklistValidator.isFileBlocked("script.bat"))
        assertTrue(FileBlocklistValidator.isFileBlocked("installer.msi"))
        assertTrue(FileBlocklistValidator.isFileBlocked("run.sh"))
        assertTrue(FileBlocklistValidator.isFileBlocked("macro.vbs"))
        assertTrue(FileBlocklistValidator.isFileBlocked("script.ps1"))
    }

    @Test
    fun isFileBlocked_doubleExtension_checksLastExtension() {
        assertTrue(FileBlocklistValidator.isFileBlocked("document.pdf.exe"))
        assertTrue(FileBlocklistValidator.isFileBlocked("report.docx.apk"))
        assertFalse(FileBlocklistValidator.isFileBlocked("setup.exe.pdf"))
    }
}
