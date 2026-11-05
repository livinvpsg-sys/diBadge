package com.fabxdi.dibadge.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null

    private fun createRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }

    /** Returns false if recording could not start (microphone busy, permission missing, ...). */
    fun start(outputFile: File): Boolean {
        stop()
        val r = createRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(44_100)
            r.setAudioEncodingBitRate(96_000)
            // A path (not a FileDescriptor) so nothing can close the file under the recorder
            r.setOutputFile(outputFile.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            true
        } catch (e: Exception) {
            try { r.reset() } catch (_: Exception) {}
            try { r.release() } catch (_: Exception) {}
            outputFile.delete()
            false
        }
    }

    /** Returns false when the recording was too short or failed, so the file may be unusable. */
    fun stop(): Boolean {
        val r = recorder ?: return true
        recorder = null
        var ok = true
        try {
            r.stop()
        } catch (e: Exception) {
            ok = false
        }
        try { r.reset() } catch (_: Exception) {}
        try { r.release() } catch (_: Exception) {}
        return ok
    }

    fun pause() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try { recorder?.pause() } catch (_: Exception) {}
        }
    }

    fun resume() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try { recorder?.resume() } catch (_: Exception) {}
        }
    }

    fun getMaxAmplitude(): Int {
        return try {
            recorder?.maxAmplitude ?: 0
        } catch (e: Exception) {
            0
        }
    }
}
