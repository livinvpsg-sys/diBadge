package com.fabxdi.dibadge.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ScanNameValidatorTest {

    @Test
    fun generateDefaultScanName_formatsCorrectly() {
        val testTime = LocalDateTime.of(2026, 10, 28, 19, 40)
        val defaultName = ScanNameValidator.generateDefaultScanName(testTime)
        assertEquals("Scan 28 Oct 2026 7:40 PM.pdf", defaultName)
    }

    @Test
    fun isValidScanName_validNames_returnsTrue() {
        assertTrue(ScanNameValidator.isValidScanName("Invoice 2026"))
        assertTrue(ScanNameValidator.isValidScanName("My_Contract_v2"))
        assertTrue(ScanNameValidator.isValidScanName("Scan 28 Oct"))
    }

    @Test
    fun isValidScanName_invalidNames_returnsFalse() {
        assertFalse(ScanNameValidator.isValidScanName(""))
        assertFalse(ScanNameValidator.isValidScanName("   "))
        assertFalse(ScanNameValidator.isValidScanName("File/Name"))
        assertFalse(ScanNameValidator.isValidScanName("File:Name"))
        assertFalse(ScanNameValidator.isValidScanName("File?Name"))
        assertFalse(ScanNameValidator.isValidScanName("File*Name"))
        assertFalse(ScanNameValidator.isValidScanName("File\"Name"))
        assertFalse(ScanNameValidator.isValidScanName("File<Name>"))
        assertFalse(ScanNameValidator.isValidScanName("File|Name"))
    }

    @Test
    fun sanitizeScanName_appendsPdfExtension() {
        assertEquals("My Document.pdf", ScanNameValidator.sanitizeScanName("My Document"))
        assertEquals("My Document.pdf", ScanNameValidator.sanitizeScanName("My Document.pdf"))
    }
}
