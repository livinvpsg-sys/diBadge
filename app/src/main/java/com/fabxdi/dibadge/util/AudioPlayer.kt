package com.fabxdi.dibadge.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build

/**
 * Plays a voice note or audio file from this phone or from a web link.
 * Web links are loaded in the background, so the screen never freezes while a file loads.
 */
class AudioPlayer(private val context: Context) {

    private var player: MediaPlayer? = null
    private var prepared = false
    private var speed = 1f
    private var source: String? = null

    fun playFile(fileUri: Uri, onComplete: () -> Unit) {
        stop()
        source = fileUri.toString()
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            mp.setDataSource(context, fileUri)
            mp.setOnPreparedListener {
                if (player === it) {
                    prepared = true
                    applySpeed()
                    it.start()
                }
            }
            mp.setOnCompletionListener {
                if (player === it) {
                    stop()
                    onComplete()
                }
            }
            mp.setOnErrorListener { m, _, _ ->
                if (player === m) {
                    stop()
                    onComplete()
                }
                true
            }
            player = mp
            prepared = false
            mp.prepareAsync()
        } catch (e: Exception) {
            e.printStackTrace()
            try { mp.release() } catch (_: Exception) {}
            player = null
            prepared = false
            source = null
            onComplete()
        }
    }

    fun stop() {
        val p = player
        player = null
        prepared = false
        source = null
        if (p != null) {
            try { p.stop() } catch (_: Exception) {}
            try { p.release() } catch (_: Exception) {}
        }
    }

    fun pause() {
        if (prepared) try { player?.pause() } catch (_: Exception) {}
    }

    fun resume() {
        if (prepared) try { player?.start() } catch (_: Exception) {}
    }

    /** The link or file that is loaded now (playing, paused or still loading), or null. */
    fun currentSource(): String? = source

    /** True once the loaded file is ready, so position and duration are real. */
    fun isPrepared(): Boolean = prepared

    fun seekTo(positionMs: Int) {
        if (prepared) try { player?.seekTo(positionMs) } catch (_: Exception) {}
    }

    fun isPlaying(): Boolean {
        return try { prepared && (player?.isPlaying ?: false) } catch (e: Exception) { false }
    }

    fun getCurrentPosition(): Int {
        return try { if (prepared) player?.currentPosition ?: 0 else 0 } catch (e: Exception) { 0 }
    }

    fun getDuration(): Int {
        return try { if (prepared) player?.duration ?: 0 else 0 } catch (e: Exception) { 0 }
    }

    fun setSpeed(newSpeed: Float) {
        speed = newSpeed
        applySpeed()
    }

    private fun applySpeed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && prepared) {
            try {
                val p = player ?: return
                val wasPlaying = p.isPlaying
                p.playbackParams = p.playbackParams.setSpeed(speed)
                // Setting the speed can start a paused player, so put it back as it was
                if (!wasPlaying) p.pause()
            } catch (e: Exception) {
            }
        }
    }
}