package com.fabxdi.dibadge.util

data class PollVoteRecord(
    val voterUid: String,
    val optionIds: Set<String>
)

object PollVoteCalculator {

    fun calculateVoteCounts(
        options: List<String>,
        votes: List<PollVoteRecord>,
        isMultiSelect: Boolean,
        isClosed: Boolean
    ): Map<String, Int> {
        val counts = options.associateWith { 0 }.toMutableMap()
        votes.forEach { vote ->
            val validOptions = if (!isMultiSelect && vote.optionIds.size > 1) {
                setOf(vote.optionIds.first())
            } else {
                vote.optionIds
            }
            validOptions.forEach { optId ->
                if (counts.containsKey(optId)) {
                    counts[optId] = (counts[optId] ?: 0) + 1
                }
            }
        }
        return counts
    }

    fun applyVoteToggle(
        currentVotedOptions: Set<String>,
        toggledOptionId: String,
        isMultiSelect: Boolean,
        isClosed: Boolean
    ): Set<String> {
        if (isClosed) return currentVotedOptions
        return if (isMultiSelect) {
            if (currentVotedOptions.contains(toggledOptionId)) {
                currentVotedOptions - toggledOptionId
            } else {
                currentVotedOptions + toggledOptionId
            }
        } else {
            if (currentVotedOptions.contains(toggledOptionId)) {
                emptySet()
            } else {
                setOf(toggledOptionId)
            }
        }
    }
}
