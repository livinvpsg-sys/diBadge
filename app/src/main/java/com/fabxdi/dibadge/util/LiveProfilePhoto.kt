package com.fabxdi.dibadge.util

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The profile photo as it is right now. The Profile screen sets it the moment a photo is chosen,
 * uploaded or removed, so the side menu updates at once instead of waiting for the cloud copy.
 * null means "nothing set in this session, use the saved photo".
 */
object LiveProfilePhoto {
    val url = MutableStateFlow<String?>(null)
}

/**
 * Firebase Storage links already contain "?alt=media&token=...". An older version added "?v=123"
 * on the end, which breaks the token so the picture never loads. This removes that bad ending.
 */
fun fixDownloadUrl(url: String): String {
    if (!url.startsWith("http") || !url.contains("alt=media")) return url
    return url.replace(Regex("\\?v=\\d+$"), "")
}
