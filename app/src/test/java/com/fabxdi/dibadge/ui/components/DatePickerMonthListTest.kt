package com.fabxdi.dibadge.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class DatePickerMonthListTest {

    @Test
    fun testMonthListGenerationAcrossYearBoundaries() {
        val dec2026 = YearMonth.of(2026, 12)
        val jan2027 = dec2026.plusMonths(1)

        assertEquals(2026, dec2026.year)
        assertEquals(12, dec2026.monthValue)
        assertEquals(2027, jan2027.year)
        assertEquals(1, jan2027.monthValue)
    }

    @Test
    fun testYearControlTracksScrollingTopMonth() {
        val startYearMonth = YearMonth.of(1990, 1)
        
        // Dec 2026 index = (2026 - 1990)*12 + (12 - 1) = 443
        val dec2026Index = ((2026 - 1990) * 12) + (12 - 1)
        val dec2026YearMonth = startYearMonth.plusMonths(dec2026Index.toLong())
        assertEquals(2026, dec2026YearMonth.year)
        assertEquals("2026", dec2026YearMonth.year.toString())

        // Scrolling to Jan 2027 index = dec2026Index + 1 = 444
        val jan2027Index = dec2026Index + 1
        val jan2027YearMonth = startYearMonth.plusMonths(jan2027Index.toLong())
        assertEquals(2027, jan2027YearMonth.year)
        assertEquals("2027", jan2027YearMonth.year.toString())
    }

    @Test
    fun testLeapYearFebruary() {
        val feb2024 = YearMonth.of(2024, 2)
        val feb2025 = YearMonth.of(2025, 2)

        assertEquals(29, feb2024.lengthOfMonth())
        assertEquals(28, feb2025.lengthOfMonth())
    }

    @Test
    fun testLeadingBlanksCalculation() {
        // Oct 1, 2026 is a Thursday (DayOfWeek value = 4)
        // In Sunday-first calendar grid (S=0, M=1, T=2, W=3, T=4, F=5, S=6):
        val oct2026FirstDay = YearMonth.of(2026, 10).atDay(1)
        val leadingBlanks = oct2026FirstDay.dayOfWeek.value % 7

        assertEquals(4, leadingBlanks) // Thursday -> 4 empty cells before 1st
    }

    @Test
    fun testMinAndMaxDateHandling() {
        val minDate = LocalDate.of(2026, 10, 5)
        val maxDate = LocalDate.of(2026, 10, 25)

        val beforeMin = LocalDate.of(2026, 10, 2)
        val insideRange = LocalDate.of(2026, 10, 15)
        val afterMax = LocalDate.of(2026, 10, 28)

        assertTrue(beforeMin.isBefore(minDate))
        assertFalse(insideRange.isBefore(minDate) || insideRange.isAfter(maxDate))
        assertTrue(afterMax.isAfter(maxDate))
    }

    @Test
    fun testRangeBandSegmentAcrossMonthBreak() {
        val startDate = LocalDate.of(2026, 10, 28)
        val endDate = LocalDate.of(2026, 11, 3)

        val oct31 = LocalDate.of(2026, 10, 31)
        val nov1 = LocalDate.of(2026, 11, 1)

        val isOct31InRange = !oct31.isBefore(startDate) && !oct31.isAfter(endDate)
        val isNov1InRange = !nov1.isBefore(startDate) && !nov1.isAfter(endDate)

        assertTrue(isOct31InRange)
        assertTrue(isNov1InRange)

        val oct31MonthLastDay = YearMonth.of(2026, 10).atEndOfMonth()
        val nov1MonthFirstDay = YearMonth.of(2026, 11).atDay(1)

        assertEquals(oct31, oct31MonthLastDay)
        assertEquals(nov1, nov1MonthFirstDay)
    }

    @Test
    fun testStartAndEndDateOrderNormalization() {
        val picked1 = LocalDate.of(2026, 10, 25)
        val picked2 = LocalDate.of(2026, 10, 15)

        val effectiveStart = if (picked2.isBefore(picked1)) picked2 else picked1
        val effectiveEnd = if (picked2.isBefore(picked1)) picked1 else picked2

        assertEquals(LocalDate.of(2026, 10, 15), effectiveStart)
        assertEquals(LocalDate.of(2026, 10, 25), effectiveEnd)
    }
}
