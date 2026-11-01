package com.fabxdi.dibadge.profile

import com.fabxdi.dibadge.util.fullName
import com.fabxdi.dibadge.util.isValidName
import com.fabxdi.dibadge.util.maskEmail
import com.fabxdi.dibadge.viewmodel.UserProfileData
import com.fabxdi.dibadge.viewmodel.VerifiedOrganizationItem
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

    @Test
    fun testOrganizationRowWhenNotVerified() {
        val freeTextOrg = "Self Employed"
        val verifiedOrg: VerifiedOrganizationItem? = null

        val displayedOrg = verifiedOrg?.name ?: freeTextOrg
        assertEquals("Self Employed", displayedOrg)
    }

    @Test
    fun testOrganizationRowWhenVerifiedWithRole() {
        val freeTextOrg = "Old Org"
        val freeTextRole = "Developer"
        val verifiedOrg = VerifiedOrganizationItem(name = "Acme Corp", role = "Senior Engineer")

        val displayedOrg = verifiedOrg.name
        val displayedRole = verifiedOrg.role ?: freeTextRole

        assertEquals("Acme Corp", displayedOrg)
        assertEquals("Senior Engineer", displayedRole)
    }

    @Test
    fun testOrganizationRowWhenVerifiedWithoutRole() {
        val freeTextOrg = "Old Org"
        val freeTextRole = "Developer"
        val verifiedOrg = VerifiedOrganizationItem(name = "Acme Corp", role = null)

        val displayedOrg = verifiedOrg.name
        val displayedRole = verifiedOrg.role ?: freeTextRole

        assertEquals("Acme Corp", displayedOrg)
        assertEquals("Developer", displayedRole)
    }

    @Test
    fun testOrganizationAndRoleWhenVerificationRemoved() {
        val freeTextOrg = "Acme Corp"
        val freeTextRole = "Senior Engineer"
        var verifiedOrg: VerifiedOrganizationItem? = VerifiedOrganizationItem(name = "Acme Corp", role = "Senior Engineer")

        // Initial: Verified
        assertEquals("Acme Corp", verifiedOrg?.name)
        assertEquals("Senior Engineer", verifiedOrg?.role)

        // Verification removed on web app
        verifiedOrg = null
        val displayedOrg = verifiedOrg?.name ?: freeTextOrg
        val displayedRole = verifiedOrg?.role ?: freeTextRole

        assertEquals("Acme Corp", displayedOrg)
        assertEquals("Senior Engineer", displayedRole)
    }

    @Test
    fun testOrganizationAndRoleWhenVerificationReplacedByNewOrg() {
        var verifiedOrg: VerifiedOrganizationItem? = VerifiedOrganizationItem(name = "Acme Corp", role = "Senior Engineer")
        assertEquals("Acme Corp", verifiedOrg?.name)
        assertEquals("Senior Engineer", verifiedOrg?.role)

        // Second organisation verifies user -> Replaces single document
        verifiedOrg = VerifiedOrganizationItem(name = "Global Tech", role = "Lead Architect")
        assertEquals("Global Tech", verifiedOrg?.name)
        assertEquals("Lead Architect", verifiedOrg?.role)
    }
}
