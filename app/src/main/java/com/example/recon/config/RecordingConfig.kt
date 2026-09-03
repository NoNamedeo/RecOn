package com.example.recon.config

import android.media.MediaRecorder

data class RecordingConfig(
    val segmentDurationMinutes: Int = DEFAULT_SEGMENT_MINUTES,
    val bufferDurationMinutes: Int = DEFAULT_BUFFER_MINUTES,
    val audioBitrateBitsPerSecond: Int = DEFAULT_AUDIO_BITRATE,
    val sampleRateHz: Int = DEFAULT_SAMPLE_RATE,
    val channelCount: Int = DEFAULT_CHANNEL_COUNT,
    val outputFormat: Int = MediaRecorder.OutputFormat.MPEG_4,
    val audioEncoder: Int = MediaRecorder.AudioEncoder.AAC,
) {
    init {
        require(segmentDurationMinutes in SEGMENT_MINUTE_OPTIONS)
        require(bufferDurationMinutes in BUFFER_MINUTE_OPTIONS)
        require(segmentDurationMinutes <= bufferDurationMinutes)
        require(audioBitrateBitsPerSecond > 0)
    }

    val segmentDurationMillis: Long = segmentDurationMinutes * 60_000L

    val bufferDurationMillis: Long = bufferDurationMinutes * 60_000L

    val segmentSizeBytes: Long =
        audioBitrateBitsPerSecond.toLong() * segmentDurationMinutes * 60L / 8L

    companion object {
        const val DEFAULT_SEGMENT_MINUTES = 15
        const val DEFAULT_BUFFER_MINUTES = 60
        const val DEFAULT_AUDIO_BITRATE = 128_000
        const val DEFAULT_SAMPLE_RATE = 44_100
        const val DEFAULT_CHANNEL_COUNT = 1

        val SEGMENT_MINUTE_OPTIONS = listOf(5, 10, 15)
        val BUFFER_MINUTE_OPTIONS = listOf(30, 60, 120, 240)
    }
}
