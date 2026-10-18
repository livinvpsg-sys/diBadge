package com.fabxdi.dibadge.ui.my_tasks.to_do

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RepeatSummaryTest {

    @Test
    fun testFormatRepeatValue_None() {
        assertEquals("None", formatRepeatValue(null))
        assertEquals("None", formatRepeatValue("once"))
        assertEquals("None", formatRepeatValue("Does not repeat"))
    }

    @Test
    fun testFormatRepeatValue_Daily() {
        assertEquals("Every day", formatRepeatValue("daily"))
    }

    @Test
    fun testFormatRepeatValue_Weekly_OneDay() {
        assertEquals("Every Sunday", formatRepeatValue("weekly:SUNDAY"))
        assertEquals("Every Monday", formatRepeatValue("weekly:MONDAY"))
    }

    @Test
    fun testFormatRepeatValue_Weekly_TwoOrThreeDays() {
        assertEquals("Mon, Wed, Fri", formatRepeatValue("weekly:MONDAY,WEDNESDAY,FRIDAY"))
        assertEquals("Tue, Thu", formatRepeatValue("weekly:TUESDAY,THURSDAY"))
    }

    @Test
    fun testFormatRepeatValue_Weekly_Weekdays() {
        assertEquals("Weekdays", formatRepeatValue("weekly:MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY"))
    }

    @Test
    fun testFormatRepeatValue_Weekly_Weekends() {
        assertEquals("Weekends", formatRepeatValue("weekly:SATURDAY,SUNDAY"))
    }

    @Test
    fun testFormatRepeatValue_Weekly_FourOrMoreDays() {
        assertEquals("4 days a week", formatRepeatValue("weekly:MONDAY,TUESDAY,WEDNESDAY,SATURDAY"))
        assertEquals("6 days a week", formatRepeatValue("weekly:MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY"))
    }

    @Test
    fun testFormatRepeatValue_Weekly_AllSevenDays() {
        assertEquals("Every day", formatRepeatValue("weekly:MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY"))
    }

    @Test
    fun testFormatRepeatValue_Monthly_OneDate() {
        assertEquals("Monthly on 5th", formatRepeatValue("monthly:5"))
        assertEquals("Monthly on 1st", formatRepeatValue("monthly:1"))
        assertEquals("Monthly on 22nd", formatRepeatValue("monthly:22"))
    }

    @Test
    fun testFormatRepeatValue_Monthly_TwoOrThreeDates() {
        assertEquals("5th, 20th monthly", formatRepeatValue("monthly:5,20"))
        assertEquals("1st, 2nd, 3rd monthly", formatRepeatValue("monthly:1,2,3"))
    }

    @Test
    fun testFormatRepeatValue_Monthly_FourOrMoreDates() {
        assertEquals("4 dates monthly", formatRepeatValue("monthly:5,10,15,20"))
    }

    @Test
    fun testFormatRepeatValue_Yearly() {
        assertEquals("Every 15 Oct", formatRepeatValue("yearly:october-15"))
    }

    @Test
    fun testFormatRepeatValue_Custom() {
        assertEquals("16 Oct – 22 Oct", formatRepeatValue("custom:16 Oct – 22 Oct"))
        assertEquals("28 Dec 2026 – 3 Jan 2027", formatRepeatValue("custom:28 Dec 2026 – 3 Jan 2027"))
    }

    @Test
    fun testGetRepeatEndLine() {
        // None
        assertEquals(null, getRepeatEndLine("once", null))

        val futureDueDate = LocalDate.now().plusDays(10)
        
        // Due Date Set
        assertEquals("Until due date", getRepeatEndLine("daily", futureDueDate))
        assertEquals("Until due date", getRepeatEndLine("weekly:MONDAY", futureDueDate))

        // No Due Date
        assertEquals("No end date", getRepeatEndLine("daily", null))
        assertEquals("No end date", getRepeatEndLine("weekly:MONDAY", null))

        // Custom
        assertEquals("Until 22 Oct", getRepeatEndLine("custom:16 Oct – 22 Oct", null))
        assertEquals("Until 3 Jan 2027", getRepeatEndLine("custom:28 Dec 2026 – 3 Jan 2027", null))

        // Due date is today
        val today = LocalDate.now()
        assertEquals("Not available when due today", getRepeatEndLine("daily", today))
    }
}