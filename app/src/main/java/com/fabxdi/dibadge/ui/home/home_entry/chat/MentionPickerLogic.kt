package com.fabxdi.dibadge.ui.home.home_entry.chat

import com.fabxdi.dibadge.viewmodel.GroupConnectionItem
import com.fabxdi.dibadge.viewmodel.GroupMember

/**
 * Decides whether the "@" list opens and which tab it starts on.
 *  - no other members and no connected groups: no list
 *  - only members: members list, no tabs
 *  - only groups: groups list, no tabs
 *  - both: both tabs ("@" starts on Members, "@ " on Groups)
 * Returns Triple(mode, filtered items, index of the '@'), or null when no list should show.
 */
fun detectMentionTrigger(
    text: String,
    members: List<GroupMember>,
    groups: List<GroupConnectionItem>,
    currentUserId: String
): Triple<MentionPickerMode, List<Any>, Int>? {
    val otherMembers = members.filter { !it.isYou && it.uid != currentUserId }
    val hasMembers = otherMembers.isNotEmpty()
    val hasGroups = groups.isNotEmpty()
    if (!hasMembers && !hasGroups) return null

    val lastAt = text.lastIndexOf('@')
    if (lastAt == -1 || !(lastAt == 0 || text[lastAt - 1].isWhitespace())) return null
    val afterAt = text.substring(lastAt + 1)
    if (afterAt.contains("\n")) return null

    val startsWithSpace = afterAt.startsWith(" ")
    val mode = when {
        hasMembers && hasGroups -> if (startsWithSpace) MentionPickerMode.LINKED_GROUPS else MentionPickerMode.MEMBERS
        hasMembers -> MentionPickerMode.MEMBERS
        else -> MentionPickerMode.LINKED_GROUPS
    }
    // A typed name ends at the first space; "@ " (space right after @) is the groups search.
    if (!startsWithSpace && afterAt.contains(" ")) return null

    val query = afterAt.trim().lowercase()
    return if (mode == MentionPickerMode.MEMBERS) {
        val list = if (query.isEmpty()) otherMembers else otherMembers.filter { m ->
            m.displayName.lowercase().split(" ").any { it.startsWith(query) } || m.displayName.lowercase().contains(query)
        }
        Triple(mode, list, lastAt)
    } else {
        val list = if (query.isEmpty()) groups else groups.filter { it.otherEntityName.lowercase().contains(query) }
        Triple(mode, list, lastAt)
    }
}
