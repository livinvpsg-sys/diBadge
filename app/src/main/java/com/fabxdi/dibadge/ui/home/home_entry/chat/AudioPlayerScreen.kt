package com.fabxdi.dibadge.ui.home.home_entry.chat

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabxdi.dibadge.ui.components.WaveformIcon
import com.fabxdi.dibadge.util.AudioPlayer
import com.fabxdi.dibadge.util.FilePickerUtils
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun AudioPlayerScreen(
    audioUris: List<Uri>,
    initialIndex: Int = 0,
    isDark: Boolean,
    senderName: String = "You",
    dateTimeString: String = "Today",
    isPreSend: Boolean = true,
    player: AudioPlayer,
    onClose: () -> Unit,
    onRemoveAudio: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val audioColor = if (isDark) Color(0xFFE87DA3) else Color(0xFFB0446A)
    val pageBgColor = if (isDark) Color(0xFF0E1114) else Color(0xFFE8ECEF)
    val cardColor = if (isDark) Color(0xFF181C21) else Color(0xFFF5F7F9)
    val primaryTextColor = if (isDark) Color(0xFFF0F3F5) else Color(0xFF11161A)
    val secondaryTextColor = if (isDark) Color(0xFF8A9199) else Color(0xFF65707A)
    val errorColor = if (isDark) Color(0xFFE8837A) else Color(0xFFC4433A)

    var currentIndex by remember { mutableStateOf(initialIndex) }
    val currentUri = audioUris.getOrNull(currentIndex) ?: return
    val fileName = FilePickerUtils.getFileName(context, currentUri)

    var isPlaying by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var currentPositionSec by remember { mutableLongStateOf(0L) }
    var totalDurationSec by remember { mutableLongStateOf(0L) }

    var playbackSpeed by remember { mutableStateOf(1f) }

    // Start playing when screen opens
    LaunchedEffect(currentUri) {
        player.playFile(currentUri) {
            isPlaying = false
            isPaused = false
            progress = 0f
            currentPositionSec = 0
        }
        isPlaying = true
        isPaused = false
    }

    LaunchedEffect(isPlaying, isPaused, playbackSpeed) {
        if (isPlaying && !isPaused) {
            player.setSpeed(playbackSpeed)
            while (isPlaying && !isPaused) {
                if (player.isPlaying()) {
                    val total = player.getDuration()
                    val current = player.getCurrentPosition()
                    if (total > 0) {
                        progress = current.toFloat() / total
                        currentPositionSec = (current / 1000).toLong()
                        totalDurationSec = (total / 1000).toLong()
                    }
                } else {
                    isPlaying = false
                    isPaused = false
                    progress = 0f
                    currentPositionSec = 0
                }
                delay(50)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player.stop()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBgColor)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(cardColor)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = primaryTextColor
                )
            }
            Text(
                text = "${currentIndex + 1} of ${audioUris.size}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = primaryTextColor,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            if (isPreSend) {
                IconButton(onClick = {
                    onRemoveAudio(currentIndex)
                    if (audioUris.size <= 1) {
                        onClose()
                    } else {
                        currentIndex = if (currentIndex == audioUris.size - 1) currentIndex - 1 else currentIndex
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = errorColor
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp)) // Placeholder
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Artwork Tile
        Surface(
            modifier = Modifier.size(200.dp),
            shape = RoundedCornerShape(28.dp),
            color = audioColor.copy(alpha = 0.14f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                WaveformIcon(color = audioColor, modifier = Modifier.size(84.dp))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = fileName,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = primaryTextColor,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Text(
            text = "$senderName · $dateTimeString",
            fontSize = 13.sp,
            color = secondaryTextColor,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Waveform Seekbar placeholder
        Box(
            modifier = Modifier
                .width(342.dp)
                .height(36.dp)
        ) {
            // Background bars
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                for (i in 0 until 25) {
                    val isPlayed = (i / 25f) <= progress
                    Box(
                        modifier = Modifier
                            .width(8.dp)
                            .height(if (i % 2 == 0) 36.dp else 24.dp)
                            .background(if (isPlayed) audioColor else secondaryTextColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .width(342.dp)
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = String.format(Locale.getDefault(), "%d:%02d", currentPositionSec / 60, currentPositionSec % 60),
                fontSize = 12.sp,
                color = secondaryTextColor
            )
            Text(
                text = String.format(Locale.getDefault(), "%d:%02d", totalDurationSec / 60, totalDurationSec % 60),
                fontSize = 12.sp,
                color = secondaryTextColor
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable {
                         // -10s not perfectly supported by simple MediaPlayer interface but we mock the UI
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("-10s", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = primaryTextColor)
            }

            Spacer(modifier = Modifier.width(32.dp))

            Surface(
                modifier = Modifier
                    .size(64.dp)
                    .clickable {
                        if (isPlaying) {
                            if (isPaused) {
                                player.resume()
                                isPaused = false
                            } else {
                                player.pause()
                                isPaused = true
                            }
                        } else {
                            player.playFile(currentUri) {
                                isPlaying = false
                                isPaused = false
                                progress = 0f
                                currentPositionSec = 0
                            }
                            isPlaying = true
                            isPaused = false
                        }
                    },
                shape = CircleShape,
                color = primaryTextColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying && !isPaused) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = pageBgColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(32.dp))

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable {
                         // +10s
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("+10s", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = primaryTextColor)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Surface(
            modifier = Modifier.clickable {
                playbackSpeed = when (playbackSpeed) {
                    1f -> 1.5f
                    1.5f -> 2f
                    else -> 1f
                }
            },
            shape = RoundedCornerShape(12.dp),
            color = cardColor
        ) {
            Text(
                text = "${playbackSpeed}x",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = primaryTextColor,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
    }
}
