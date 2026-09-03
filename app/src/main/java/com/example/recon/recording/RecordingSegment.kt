package com.example.recon.recording

import java.io.File

data class RecordingSegment(
    val file: File,
    val startedAt: Long,
    val endedAt: Long,
) {
    val durationMillis: Long = (endedAt - startedAt).coerceAtLeast(0L)
}

sealed interface RecordingEvent {
    data class SegmentCompleted(val segment: RecordingSegment) : RecordingEvent

    data class Error(
        val description: String,
        val cause: Throwable? = null,
        val recoverableSegment: RecordingSegment? = null,
    ) : RecordingEvent

    data class Stopped(val finalSegment: RecordingSegment?) : RecordingEvent
}
