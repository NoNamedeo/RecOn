package com.example.recon.recording

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RecordingStatus {
    IDLE,
    STARTING,
    RECORDING,
    FINALIZING,
    STOPPING,
    ERROR,
}

data class RecordingSessionState(
    val status: RecordingStatus = RecordingStatus.IDLE,
    val hasBufferedAudio: Boolean = false,
    val errorMessage: String? = null,
) {
    val isRecording: Boolean get() = status == RecordingStatus.RECORDING
    val isBusy: Boolean get() = status in setOf(
        RecordingStatus.STARTING,
        RecordingStatus.FINALIZING,
        RecordingStatus.STOPPING,
    )
}

class RecordingSessionStore {
    private val _state = MutableStateFlow(RecordingSessionState())
    val state: StateFlow<RecordingSessionState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun update(status: RecordingStatus, hasBufferedAudio: Boolean, errorMessage: String? = null) {
        _state.value = RecordingSessionState(status, hasBufferedAudio, errorMessage)
    }

    fun message(value: String) {
        _messages.tryEmit(value)
    }
}
