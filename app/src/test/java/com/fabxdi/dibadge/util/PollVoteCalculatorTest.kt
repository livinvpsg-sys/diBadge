package com.fabxdi.dibadge.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PollVoteCalculatorTest {

    @Test
    fun calculateVoteCounts_singleOptionVote_countsCorrectly() {
        val options = listOf("0", "1", "2")
        val votes = listOf(
            PollVoteRecord("user1", setOf("0")),
            PollVoteRecord("user2", setOf("0")),
            PollVoteRecord("user3", setOf("1"))
        )
        val result = PollVoteCalculator.calculateVoteCounts(options, votes, isMultiSelect = false, isClosed = false)
        assertEquals(2, result["0"])
        assertEquals(1, result["1"])
        assertEquals(0, result["2"])
    }

    @Test
    fun applyVoteToggle_singleAnswer_movesVote() {
        val initial = setOf("0")
        val updated = PollVoteCalculator.applyVoteToggle(initial, "1", isMultiSelect = false, isClosed = false)
        assertEquals(setOf("1"), updated)
    }

    @Test
    fun applyVoteToggle_singleAnswer_unselectsVote() {
        val initial = setOf("0")
        val updated = PollVoteCalculator.applyVoteToggle(initial, "0", isMultiSelect = false, isClosed = false)
        assertTrue(updated.isEmpty())
    }

    @Test
    fun applyVoteToggle_multiAnswer_togglesOption() {
        val initial = setOf("0")
        val step1 = PollVoteCalculator.applyVoteToggle(initial, "1", isMultiSelect = true, isClosed = false)
        assertEquals(setOf("0", "1"), step1)

        val step2 = PollVoteCalculator.applyVoteToggle(step1, "0", isMultiSelect = true, isClosed = false)
        assertEquals(setOf("1"), step2)
    }

    @Test
    fun applyVoteToggle_whenClosed_doesNotChangeVote() {
        val initial = setOf("0")
        val updated = PollVoteCalculator.applyVoteToggle(initial, "1", isMultiSelect = false, isClosed = true)
        assertEquals(setOf("0"), updated)
    }
}
