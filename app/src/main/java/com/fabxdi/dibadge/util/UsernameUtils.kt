package com.fabxdi.dibadge.util

val RESERVED_USERNAMES = setOf(
    "admin", "administrator", "support", "help", "dibadge",
    "official", "system", "root", "moderator", "staff",
    "team", "security", "null", "undefined", "api", "www", "me", "you"
)

enum class UsernameFormatResult {
    EMPTY,
    INVALID_FORMAT,
    RESERVED,
    VALID
}

fun cleanAndFormatUsernameInput(raw: String): String {
    return raw.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '_' }.take(20)
}

fun validateUsernameFormat(username: String): UsernameFormatResult {
    val clean = username.lowercase().trim()
    if (clean.isEmpty()) return UsernameFormatResult.EMPTY
    if (clean in RESERVED_USERNAMES) return UsernameFormatResult.RESERVED

    if (clean.length !in 3..20) return UsernameFormatResult.INVALID_FORMAT
    if (!clean.all { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '_' }) {
        return UsernameFormatResult.INVALID_FORMAT
    }

    if (clean.startsWith('.') || clean.startsWith('_') || clean.endsWith('.') || clean.endsWith('_')) {
        return UsernameFormatResult.INVALID_FORMAT
    }

    if (clean.contains("..") || clean.contains("__") || clean.contains("._") || clean.contains("_.")) {
        return UsernameFormatResult.INVALID_FORMAT
    }

    return UsernameFormatResult.VALID
}

fun generateUsernameSuggestions(baseInput: String, firstName: String = "", lastName: String = ""): List<String> {
    val base = baseInput.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '_' }
    val fn = firstName.lowercase().filter { it in 'a'..'z' }
    val ln = lastName.lowercase().filter { it in 'a'..'z' }

    val candidates = mutableListOf<String>()

    if (base.isNotBlank()) {
        candidates.add("${base}_app")
        candidates.add("${base}.v")
        candidates.add("${base}88")
        candidates.add("${base}_99")
        candidates.add("${base}.official")
    }

    if (fn.isNotBlank() && ln.isNotBlank()) {
        candidates.add("${fn}.${ln}")
        candidates.add("${fn}_${ln}")
        candidates.add("${fn}.${ln.take(1)}")
    }

    return candidates
        .map { cleanAndFormatUsernameInput(it) }
        .filter { validateUsernameFormat(it) == UsernameFormatResult.VALID && it != base }
        .distinct()
}
