package com.example.recon.recording

import kotlinx.coroutines.flow.Flow

interface StreamRecording {
    val events: Flow<RecordingEvent>

    suspend fun start()

    suspend fun stop(): RecordingSegment?
}
