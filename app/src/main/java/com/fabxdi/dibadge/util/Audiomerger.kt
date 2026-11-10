package com.fabxdi.dibadge.util

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Joins recorded AAC / MP4 audio files end to end into one file, without re-encoding, so the
 * quality stays the same. Used when a voice note is paused and then resumed.
 */
object AudioMerger {

    // One AAC frame at 44.1 kHz is 1024 samples, about 23 ms
    private const val AAC_FRAME_US = 23_220L

    /** Returns false if anything went wrong. The output file is deleted in that case. */
    fun merge(inputs: List<File>, output: File): Boolean {
        if (inputs.isEmpty()) return false
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        try {
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var outTrack = -1
            var offsetUs = 0L
            val buffer = ByteBuffer.allocate(256 * 1024)
            val info = MediaCodec.BufferInfo()

            for (file in inputs) {
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(file.absolutePath)
                    var audioTrack = -1
                    for (i in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(i)
                        if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                            audioTrack = i
                            if (outTrack == -1) {
                                outTrack = muxer.addTrack(format)
                                muxer.start()
                                muxerStarted = true
                            }
                            break
                        }
                    }
                    if (audioTrack == -1) {
                        output.delete()
                        return false
                    }
                    extractor.selectTrack(audioTrack)

                    var lastUs = 0L
                    while (true) {
                        buffer.clear()
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) break
                        val timeUs = extractor.sampleTime
                        val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                            MediaCodec.BUFFER_FLAG_KEY_FRAME
                        } else {
                            0
                        }
                        info.set(0, size, timeUs + offsetUs, flags)
                        muxer.writeSampleData(outTrack, buffer, info)
                        if (timeUs > lastUs) lastUs = timeUs
                        extractor.advance()
                    }
                    // The next file starts right after the last frame of this one
                    offsetUs += lastUs + AAC_FRAME_US
                } finally {
                    try { extractor.release() } catch (_: Exception) {}
                }
            }

            muxer.stop()
            muxerStarted = false
            return true
        } catch (e: Exception) {
            output.delete()
            return false
        } finally {
            try {
                if (muxerStarted) muxer?.stop()
            } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }
}