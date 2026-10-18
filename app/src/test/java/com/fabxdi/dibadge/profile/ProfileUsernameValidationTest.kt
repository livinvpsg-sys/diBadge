package com.fabxdi.dibadge.profile

import com.fabxdi.dibadge.util.UsernameFormatResult
import com.fabxdi.dibadge.util.cleanAndFormatUsernameInput
import com.fabxdi.dibadge.util.generateUsernameSuggestions
import com.fabxdi.dibadge.util.validateUsernameFormat
import com.fabxdi.dibadge.viewmodel.UsernameAvailabilityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileUsernameValidationTest {

    @Test
    fun testValidUsernameFormat() {
        assertEquals(UsernameFormatResult.VALID, validateUsernameFormat("livin.v"))
        assertEquals(UsernameFormatResult.VALID, validateUsernameFormat("livin_v"))
        assertEquals(UsernameFormatResult.VALID, validateUsernameFormat("livin88"))
        assertEquals(UsernameFormatResult.VALID, validateUsernameFormat("abc"))
    }

    @Test
    fun testReservedUsernames() {
        assertEquals(UsernameFormatResult.RESERVED, validateUsernameFormat("admin"))
        assertEquals(UsernameFormatResult.RESERVED, validateUsernameFormat("support"))
        assertEquals(UsernameFormatResult.RESERVED, validateUsernameFormat("dibadge"))
        assertEquals(UsernameFormatResult.RESERVED, validateUsernameFormat("official"))
        assertEquals(UsernameFormatResult.RESERVED, validateUsernameFormat("system"))
    }

    @Test
    fun testInvalidUsernameFormatLength() {
        assertEquals(UsernameFormatResult.INVALID_FORMAT, validateUsernameFormat("ab"))
        assertEquals(UsernameFormatResult.INVALID_FORMAT, validateUsernameFormat("verylongusernamethatexceedstwentycharacters"))
    }

    @Test
    fun testInvalidUsernameFormatEdgeSeparators() {
        assertEquals(UsernameFormatResult.INVALID_FORMAT, validateUsernameFormat(".livin"))
        assertEquals(UsernameFormatResult.INVALID_FORMAT, validateUsernameFormat("livin."))
        assertEquals(UsernameFormatResult.INVALID_FORMAT, validateUsernameFormat("_livin"))
        assertEquals(UsernameFormatResult.INVALID_FORMAT, validateUsernameFormat("livin_"))
    }

    @Test
    fun testInvalidUsernameFormatDoubleSeparators() {
        assertEquals(UsernameFormatResult.INVALID_FORMAT, validateUsernameFormat("livin..v"))
        assertEquals(UsernameFormatResult.INVALID_FORMAT, validateUsernameFormat("livin__v"))
        assertEquals(UsernameFormatResult.INVALID_FORMAT, validateUsernameFormat("livin._v"))
        assertEquals(UsernameFormatResult.INVALID_FORMAT, validateUsernameFormat("livin_.v"))
    }

    @Test
    fun testCleanAndFormatInput() {
        assertEquals("livin.v", cleanAndFormatUsernameInput("Livin.V"))
        assertEquals("livinv", cleanAndFormatUsernameInput("Livin V"))
        assertEquals("livinv", cleanAndFormatUsernameInput("Livin!@#V"))
    }

    @Test
    fun testGenerateUsernameSuggestions() {
        val suggestions = generateUsernameSuggestions("livin", "Livin", "Varghese")
        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.all { validateUsernameFormat(it) == UsernameFormatResult.VALID })
    }

    @Test
    fun testUsernameStateAllowsSaveOnlyWhenAvailable() {
        fun isSaveAllowed(state: UsernameAvailabilityState, typedName: String): Boolean {
            if (typedName.isBlank()) return true
            return state is UsernameAvailabilityState.Available && state.name == typedName.trim().lowercase()
        }

        val name = "livin.v"
        assertTrue(isSaveAllowed(UsernameAvailabilityState.Available("livin.v"), name))
        assertFalse(isSaveAllowed(UsernameAvailabilityState.CantCheck, name))
        assertFalse(isSaveAllowed(UsernameAvailabilityState.Checking, name))
        assertFalse(isSaveAllowed(UsernameAvailabilityState.InvalidFormat, name))
        assertFalse(isSaveAllowed(UsernameAvailabilityState.Reserved, name))
        assertFalse(isSaveAllowed(UsernameAvailabilityState.Taken(emptyList()), name))
        assertFalse(isSaveAllowed(UsernameAvailabilityState.Idle, name))
        assertFalse(isSaveAllowed(UsernameAvailabilityState.Available("other.name"), name))
    }
}
