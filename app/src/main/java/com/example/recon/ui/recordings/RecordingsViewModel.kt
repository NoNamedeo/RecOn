package com.example.recon.ui.recordings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.recon.data.RecordEntity
import com.example.recon.data.RecordRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class RecordingsUiState(
    val records: List<RecordEntity> = emptyList(),
    val activeRecordId: Long? = null,
    val isPlaying: Boolean = false,
    val positionMillis: Long = 0L,
    val durationMillis: Long = 0L,
    val menuRecordId: Long? = null,
    val renameRecordId: Long? = null,
    val renameTitle: String = "",
    val deleteRecordId: Long? = null,
    val errorMessage: String? = null,
)

class RecordingsViewModel(
    context: Context,
    private val repository: RecordRepository,
) : ViewModel() {
    private val player = ExoPlayer.Builder(context.applicationContext).build()
    private val _state = MutableStateFlow(RecordingsUiState())
    val state: StateFlow<RecordingsUiState> = _state.asStateFlow()
    private var progressJob: Job? = null

    init {
        player.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.update { it.copy(isPlaying = isPlaying) }
                    if (isPlaying) startProgressUpdates() else stopProgressUpdates()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) updateProgress()
                    if (playbackState == Player.STATE_ENDED) {
                        player.seekTo(0L)
                        player.pause()
                        updateProgress()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    reportError(error.message ?: "Impossibile riprodurre la registrazione")
                }
            },
        )
        viewModelScope.launch {
            repository.records.collect { records ->
                val activeStillExists = records.any { it.id == state.value.activeRecordId }
                if (!activeStillExists) stopPlayback()
                _state.update { it.copy(records = records) }
            }
        }
    }

    fun playPause(record: RecordEntity) {
        if (!File(record.filePath).isFile) {
            reportError("Il file della registrazione non è più disponibile")
            return
        }
        if (state.value.activeRecordId == record.id) {
            if (player.isPlaying) player.pause() else player.play()
            return
        }
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(record.filePath))))
        player.prepare()
        player.playWhenReady = true
        _state.update {
            it.copy(
                activeRecordId = record.id,
                positionMillis = 0L,
                durationMillis = record.durationMillis,
            )
        }
    }

    fun seek(record: RecordEntity, fraction: Float) {
        if (state.value.activeRecordId != record.id) {
            playPause(record)
            player.pause()
        }
        val duration = effectiveDuration(record)
        player.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
        updateProgress()
    }

    fun showMenu(recordId: Long) {
        _state.update { it.copy(menuRecordId = recordId) }
    }

    fun dismissMenu() {
        _state.update { it.copy(menuRecordId = null) }
    }

    fun requestRename(record: RecordEntity) {
        _state.update {
            it.copy(menuRecordId = null, renameRecordId = record.id, renameTitle = record.title)
        }
    }

    fun updateRenameTitle(title: String) {
        _state.update { it.copy(renameTitle = title) }
    }

    fun confirmRename() {
        val id = state.value.renameRecordId ?: return
        val title = state.value.renameTitle.trim()
        if (title.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.rename(id, title) }
                .onSuccess { dismissDialogs() }
                .onFailure { reportError(it.message ?: "Impossibile rinominare la registrazione") }
        }
    }

    fun requestDelete(record: RecordEntity) {
        _state.update { it.copy(menuRecordId = null, deleteRecordId = record.id) }
    }

    fun confirmDelete() {
        val record = state.value.records.firstOrNull { it.id == state.value.deleteRecordId } ?: return
        viewModelScope.launch {
            if (state.value.activeRecordId == record.id) stopPlayback()
            runCatching { repository.delete(record) }
                .onSuccess { dismissDialogs() }
                .onFailure { reportError(it.message ?: "Impossibile eliminare la registrazione") }
        }
    }

    fun dismissDialogs() {
        _state.update {
            it.copy(
                menuRecordId = null,
                renameRecordId = null,
                renameTitle = "",
                deleteRecordId = null,
            )
        }
    }

    fun reportError(message: String) {
        _state.update { it.copy(errorMessage = message) }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive && player.isPlaying) {
                updateProgress()
                delay(250L)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
        updateProgress()
    }

    private fun updateProgress() {
        val playerDuration = player.duration.takeIf { it > 0L }
        _state.update {
            it.copy(
                positionMillis = player.currentPosition.coerceAtLeast(0L),
                durationMillis = playerDuration ?: it.durationMillis,
            )
        }
    }

    private fun effectiveDuration(record: RecordEntity): Long =
        state.value.durationMillis.takeIf { state.value.activeRecordId == record.id && it > 0L }
            ?: record.durationMillis.coerceAtLeast(1L)

    private fun stopPlayback() {
        player.stop()
        player.clearMediaItems()
        _state.update {
            it.copy(
                activeRecordId = null,
                isPlaying = false,
                positionMillis = 0L,
                durationMillis = 0L,
            )
        }
    }

    override fun onCleared() {
        progressJob?.cancel()
        player.release()
        super.onCleared()
    }
}
