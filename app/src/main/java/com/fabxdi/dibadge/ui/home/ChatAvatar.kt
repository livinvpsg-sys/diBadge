package com.fabxdi.dibadge.ui.home

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fabxdi.dibadge.util.UserColorUtils

/**
 * The one avatar used for every chat and group, on the home list, the chat header, the
 * chat details screen and the forward sheet. Same shape, colour and letter everywhere.
 *
 * [idForColor] must be the chat's id (`entry.id.toString()`) so the colour matches on every screen.
 */
@Composable
fun ChatAvatar(
    title: String,
    idForColor: String,
    size: Dp,
    modifier: Modifier = Modifier,
    photoUrl: String = "",
    fontSize: TextUnit = (size.value * 0.41f).sp
) {
    val background = remember(idForColor) { UserColorUtils.getColorForName(idForColor) }

    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = background
    ) {
        if (photoUrl.isNotBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title.trim().take(1).uppercase(),
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Remembers the latest photo for each chat, so a photo seen on one screen (for example a group
 * photo on the details screen) also shows on the home list and in the chat header.
 */
object AvatarPhotoCache {
    private val photos = mutableStateMapOf<String, String>()
    private var prefs: SharedPreferences? = null

    private fun ensureLoaded(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("avatar_photos", Context.MODE_PRIVATE)
        prefs = p
        p.all.forEach { (key, value) ->
            if (value is String && value.isNotBlank()) photos[key] = value
        }
    }

    /** Reads inside a composable, so the screen updates when a photo changes. */
    @Composable
    fun photoFor(entryId: String): String {
        val context = LocalContext.current
        remember { ensureLoaded(context) }
        return photos[entryId].orEmpty()
    }

    /** An empty [url] removes the photo. */
    fun put(context: Context, entryId: String, url: String) {
        ensureLoaded(context)
        if (photos[entryId].orEmpty() == url) return
        if (url.isBlank()) {
            photos.remove(entryId)
            prefs?.edit()?.remove(entryId)?.apply()
        } else {
            photos[entryId] = url
            prefs?.edit()?.putString(entryId, url)?.apply()
        }
    }
}
