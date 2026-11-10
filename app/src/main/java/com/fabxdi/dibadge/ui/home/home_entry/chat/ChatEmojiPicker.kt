package com.fabxdi.dibadge.ui.home.home_entry.chat

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private class EmojiSection(val title: String, val tabIcon: String, val emojis: List<String>)

/**
 * The full Unicode emoji set by category. A "Recent" section shows first only after the person
 * has used an emoji; until then the picker opens on Smileys & Emotion.
 */
@Composable
fun FullEmojiPicker(
    containerColor: Color,
    secondaryTextColor: Color,
    accentColor: Color,
    onEmojiSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("emoji_picker", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    // Hide emojis this phone cannot draw yet (newer than its Android version)
    val sections = remember {
        val paint = android.graphics.Paint()
        val drawable: (String) -> Boolean = { e -> try { paint.hasGlyph(e) } catch (_: Exception) { true } }
        val recent = (prefs.getString("recent", "") ?: "").split(" ").filter { it.isNotEmpty() && drawable(it) }
        val list = mutableListOf<EmojiSection>()
        if (recent.isNotEmpty()) list.add(EmojiSection("Recent", "🕒", recent))
        EmojiData.groups.forEach { (title, icon, all) ->
            val ok = all.split(" ").filter { drawable(it) }
            if (ok.isNotEmpty()) list.add(EmojiSection(title, icon, ok))
        }
        list
    }
    // Position of each heading in the grid (a heading is one full-width item)
    val headerIndexes = remember(sections) {
        var idx = 0
        sections.map { s -> val at = idx; idx += 1 + s.emojis.size; at }
    }
    val gridState = rememberLazyGridState()
    val currentSection by remember(headerIndexes) {
        derivedStateOf {
            val first = gridState.firstVisibleItemIndex
            headerIndexes.indexOfLast { it <= first }.coerceAtLeast(0)
        }
    }

    fun saveRecent(emoji: String) {
        val old = (prefs.getString("recent", "") ?: "").split(" ").filter { it.isNotEmpty() && it != emoji }
        prefs.edit().putString("recent", (listOf(emoji) + old).take(30).joinToString(" ")).apply()
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        color = containerColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Category tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                sections.forEachIndexed { i, s ->
                    val selected = i == currentSection
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { scope.launch { gridState.scrollToItem(headerIndexes[i]) } },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = s.tabIcon,
                            fontSize = 18.sp,
                            modifier = Modifier.alpha(if (selected) 1f else 0.45f)
                        )
                        if (selected) {
                            Surface(
                                color = accentColor,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 2.dp)
                                    .fillMaxWidth(0.5f)
                                    .height(2.dp)
                            ) {}
                        }
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                state = gridState,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                sections.forEach { section ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = section.title,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = secondaryTextColor,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 2.dp)
                        )
                    }
                    items(section.emojis) { emoji ->
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    saveRecent(emoji)
                                    onEmojiSelected(emoji)
                                },
                            contentAlignment = Alignment.Center
                        ) { Text(text = emoji, fontSize = 24.sp) }
                    }
                }
            }
        }
    }
}
