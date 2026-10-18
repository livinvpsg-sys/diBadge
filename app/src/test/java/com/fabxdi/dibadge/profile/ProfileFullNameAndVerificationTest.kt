package com.fabxdi.dibadge.profile

import com.fabxdi.dibadge.util.fullName
import com.fabxdi.dibadge.util.isValidName
import com.fabxdi.dibadge.util.maskEmail
import com.fabxdi.dibadge.viewmodel.UserProfileData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileFullNameAndVerificationTest {

    @Test
    fun testFullNameWithAllThreeNames() {
        val name = fullName("Livin", "Philip", "Varghese")
        assertEquals("Livin Philip Varghese", name)
    }

    @Test
    fun testFullNameWithoutMiddleName() {
        val name = fullName("Livin", "", "Varghese")
        assertEquals("Livin Varghese", name)
    }

    @Test
    fun testFullNameWithExtraSpaces() {
        val name = fullName("  Livin  ", "  Philip  ", "  Varghese  ")
        assertEquals("Livin Philip Varghese", name)
    }

    @Test
    fun testFullNameUserProfileData() {
        val user = UserProfileData(firstName = "Anne", middleName = "Marie", lastName = "Smith")
        assertEquals("Anne Marie Smith", fullName(user.firstName, user.middleName, user.lastName))
    }

    @Test
    fun testValidNameCharacters() {
        assertTrue(isValidName("O'Neil"))
        assertTrue(isValidName("Anne-Marie"))
        assertTrue(isValidName("K."))
        assertTrue(isValidName("Livin Philip Varghese"))
    }

    @Test
    fun testInvalidNameCharacters() {
        assertFalse(isValidName("Livin123"))
        assertFalse(isValidName("Philip@Varghese"))
        assertFalse(isValidName("VeryLongNameThatExceedsFortyCharactersLimitForProfileNameFieldInDiBadgeApp"))
    }

    @Test
    fun testMaskEmail() {
        val masked = maskEmail("livin.varghese@gmail.com")
        assertEquals("l••••••••••@gmail.com", masked)
    }
}
