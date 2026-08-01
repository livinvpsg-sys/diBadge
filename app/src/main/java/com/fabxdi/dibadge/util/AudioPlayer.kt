package com.fabxdi.dibadge.util

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri

class AudioPlayer(private val context: Context) {

    private var player: MediaPlayer? = null

    fun playFile(fileUri: Uri, onComplete: () -> Unit) {
        stop()
        MediaPlayer.create(context, fileUri).apply {
            player = this
            start()
            setOnCompletionListener {
                stop()
                onComplete()
            }
        }
    }

    fun stop() {
        player?.stop()
        player?.release()
        player = null
    }

    fun pause() {
        player?.pause()
    }

    fun resume() {
        player?.start()
    }

    fun isPlaying(): Boolean {
        return player?.isPlaying ?: false
    }

    fun getCurrentPosition(): Int {
        return player?.currentPosition ?: 0
    }

    fun getDuration(): Int {
        return player?.duration ?: 0
    }
}
