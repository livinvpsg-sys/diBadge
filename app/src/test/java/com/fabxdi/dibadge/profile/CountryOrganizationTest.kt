package com.fabxdi.dibadge.profile

import com.fabxdi.dibadge.util.CountryData
import com.fabxdi.dibadge.viewmodel.UserProfileData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CountryOrganizationTest {

    @Test
    fun testCountryDataCountAndSearch() {
        val countries = CountryData.allCountries
        assertTrue(countries.isNotEmpty())

        val singapore = countries.find { it.isoCode == "SG" }
        assertEquals("Singapore", singapore?.name)
        assertEquals("+65", singapore?.dialCode)

        val searchResult = countries.filter { it.name.contains("United", ignoreCase = true) }
        assertTrue(searchResult.any { it.name == "United States" })
        assertTrue(searchResult.any { it.name == "United Kingdom" })
        assertTrue(searchResult.any { it.name == "United Arab Emirates" })
    }

    @Test
    fun testGetCountryDisplayName() {
        assertEquals("Singapore", CountryData.getCountryDisplayName("SG"))
    }

    @Test
    fun testOrganizationFieldInUserProfileData() {
        val profile = UserProfileData(organization = "diBadge Inc.", country = "SG")
        assertEquals("diBadge Inc.", profile.organization)
        assertEquals("SG", profile.country)
    }
}
