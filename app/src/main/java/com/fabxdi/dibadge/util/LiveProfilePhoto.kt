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
