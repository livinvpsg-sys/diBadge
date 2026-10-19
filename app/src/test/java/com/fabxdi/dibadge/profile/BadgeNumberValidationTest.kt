package com.fabxdi.dibadge.profile

import com.fabxdi.dibadge.util.BADGE_NUMBER_ALPHABET
import com.fabxdi.dibadge.util.BADGE_NUMBER_DIGITS
import com.fabxdi.dibadge.util.BADGE_NUMBER_LETTERS
import com.fabxdi.dibadge.util.formatBadgeNumber
import com.fabxdi.dibadge.util.generateBadgeNumber
import com.fabxdi.dibadge.util.isValidBadgeNumberFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeNumberValidationTest {

    @Test
    fun testGenerateBadgeNumberLengthAndAlphabetAndMix() {
        for (i in 0 until 100) {
            val code = generateBadgeNumber()
            assertEquals(9, code.length)
            assertTrue(code.all { it in BADGE_NUMBER_ALPHABET })
            assertTrue("Must contain at least one letter", code.any { it in BADGE_NUMBER_LETTERS })
            assertTrue("Must contain at least one digit", code.any { it in BADGE_NUMBER_DIGITS })
            assertFalse(code.contains('I'))
            assertFalse(code.contains('O'))
            assertFalse(code.contains('0'))
        }
    }

    @Test
    fun testFormatBadgeNumber() {
        assertEquals("K7M 2X9 QD4", formatBadgeNumber("K7M2X9QD4"))
        assertEquals("ABC DEF GHK", formatBadgeNumber("abcdefghk"))
        assertEquals("K7M 2X9 QD4", formatBadgeNumber("K7M 2X9 QD4"))
    }

    @Test
    fun testIsValidBadgeNumberFormat() {
        assertTrue(isValidBadgeNumberFormat("K7M2X9QD4"))
        assertTrue(isValidBadgeNumberFormat("K7M 2X9 QD4"))
        assertFalse(isValidBadgeNumberFormat("AAAAAAAAA")) // Missing digits
        assertFalse(isValidBadgeNumberFormat("123456789")) // Missing letters
        assertFalse(isValidBadgeNumberFormat("K7M2I9QD4")) // Contains 'I'
        assertFalse(isValidBadgeNumberFormat("K7M2O9QD4")) // Contains 'O'
        assertFalse(isValidBadgeNumberFormat("K7M209QD4")) // Contains '0'
        assertFalse(isValidBadgeNumberFormat("SHORT"))
    }
}
