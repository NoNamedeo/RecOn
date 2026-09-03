package com.example.recon.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recon.recording.RecordingSessionState
import com.example.recon.recording.RecordingSessionStore
import com.example.recon.recording.RecordingStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SaveDialogReason { SAVE_BUTTON, STOP_CONFIRMATION }

data class HomeUiState(
    val session: RecordingSessionState = RecordingSessionState(),
    val showPermissionRationale: Boolean = false,
    val showStopConfirmation: Boolean = false,
    val saveDialogReason: SaveDialogReason? = null,
    val recordingTitle: String = "",
) {
    val canStart: Boolean =
        !session.isBusy && !session.isRecording && !session.hasBufferedAudio
    val canSaveOrStop: Boolean = !session.isBusy &&
        (session.isRecording || session.hasBufferedAudio)
}

class HomeViewModel(private val sessionStore: RecordingSessionStore) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState(session = sessionStore.state.value))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
    val messages = sessionStore.messages

    init {
        viewModelScope.launch {
            sessionStore.state.collect { session -> _state.update { it.copy(session = session) } }
        }
    }

    fun beginStart(startService: () -> Unit) {
        if (!state.value.canStart) return
        sessionStore.update(RecordingStatus.STARTING, hasBufferedAudio = false)
        runCatching(startService).onFailure(::reportFrameworkFailure)
    }

    fun showPermissionRationale() {
        _state.update { it.copy(showPermissionRationale = true) }
    }

    fun dismissPermissionRationale() {
        _state.update { it.copy(showPermissionRationale = false) }
    }

    fun permissionDenied() {
        _state.update { it.copy(showPermissionRationale = false) }
        sessionStore.update(
            RecordingStatus.ERROR,
            hasBufferedAudio = false,
            errorMessage = "Il permesso microfono è necessario per registrare",
        )
    }

    fun requestSave() {
        if (state.value.canSaveOrStop) {
            _state.update { it.copy(saveDialogReason = SaveDialogReason.SAVE_BUTTON) }
        }
    }

    fun requestStop() {
        if (state.value.canSaveOrStop) {
            _state.update { it.copy(showStopConfirmation = true) }
        }
    }

    fun saveFromStopConfirmation() {
        _state.update {
            it.copy(
                showStopConfirmation = false,
                saveDialogReason = SaveDialogReason.STOP_CONFIRMATION,
            )
        }
    }

    fun updateTitle(title: String) {
        _state.update { it.copy(recordingTitle = title) }
    }

    fun confirmSave(saveService: (String) -> Unit) {
        val title = state.value.recordingTitle.trim()
        if (title.isEmpty()) return
        _state.update { it.copy(saveDialogReason = null, recordingTitle = "") }
        sessionStore.update(RecordingStatus.FINALIZING, hasBufferedAudio = true)
        runCatching { saveService(title) }.onFailure(::reportFrameworkFailure)
    }

    fun discard(discardService: () -> Unit) {
        _state.update { it.copy(showStopConfirmation = false) }
        sessionStore.update(RecordingStatus.STOPPING, hasBufferedAudio = true)
        runCatching(discardService).onFailure(::reportFrameworkFailure)
    }

    fun dismissDialogs() {
        _state.update {
            it.copy(
                showPermissionRationale = false,
                showStopConfirmation = false,
                saveDialogReason = null,
            )
        }
    }

    private fun reportFrameworkFailure(error: Throwable) {
        sessionStore.update(
            RecordingStatus.ERROR,
            state.value.session.hasBufferedAudio,
            error.message ?: "Impossibile comunicare con il servizio di registrazione",
        )
    }
}
