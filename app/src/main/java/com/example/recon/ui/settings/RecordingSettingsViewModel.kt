package com.example.recon.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recon.config.RecordingConfig
import com.example.recon.config.RecordingSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecordingSettingsUiState(
    val segmentDurationMinutes: Int = RecordingConfig.DEFAULT_SEGMENT_MINUTES,
    val bufferDurationMinutes: Int = RecordingConfig.DEFAULT_BUFFER_MINUTES,
    val segmentMenuExpanded: Boolean = false,
    val bufferMenuExpanded: Boolean = false,
)

class RecordingSettingsViewModel(
    private val repository: RecordingSettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(
        RecordingSettingsUiState(
            segmentDurationMinutes = repository.config.value.segmentDurationMinutes,
            bufferDurationMinutes = repository.config.value.bufferDurationMinutes,
        ),
    )
    val state: StateFlow<RecordingSettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.config.collect { config ->
                _state.update {
                    it.copy(
                        segmentDurationMinutes = config.segmentDurationMinutes,
                        bufferDurationMinutes = config.bufferDurationMinutes,
                    )
                }
            }
        }
    }

    fun setSegmentMenuExpanded(expanded: Boolean) {
        _state.update { it.copy(segmentMenuExpanded = expanded) }
    }

    fun setBufferMenuExpanded(expanded: Boolean) {
        _state.update { it.copy(bufferMenuExpanded = expanded) }
    }

    fun selectSegmentDuration(minutes: Int) {
        repository.setSegmentDuration(minutes)
        _state.update { it.copy(segmentMenuExpanded = false) }
    }

    fun selectBufferDuration(minutes: Int) {
        repository.setBufferDuration(minutes)
        _state.update { it.copy(bufferMenuExpanded = false) }
    }
}
