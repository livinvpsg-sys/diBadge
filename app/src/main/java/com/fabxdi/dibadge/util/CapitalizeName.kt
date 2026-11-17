package com.fabxdi.dibadge.util


fun capitalizeName(value: String): String {
    if (value.isEmpty()) return value
    val out = StringBuilder(value.length)
    var startOfWord = true
    for (ch in value) {
        out.append(if (startOfWord && ch.isLetter()) ch.uppercaseChar() else ch)
        startOfWord = ch == ' ' || ch == '-' || ch == '\u2019' || ch == '\''
    }
    return out.toString()
}

/** Only the first letter of a title: "group for testing" -> "Group for testing". */
fun capitalizeFirst(value: String): String =
    value.replaceFirstChar { if (it.isLowerCase()) it.uppercaseChar() else it }