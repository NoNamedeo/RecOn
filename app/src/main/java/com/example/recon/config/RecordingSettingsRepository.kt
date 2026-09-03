package com.example.recon.config

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class RecordingSettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(
        RecordingConfig(
            segmentDurationMinutes = preferences.getInt(
                KEY_SEGMENT_MINUTES,
                RecordingConfig.DEFAULT_SEGMENT_MINUTES,
            ).takeIf { it in RecordingConfig.SEGMENT_MINUTE_OPTIONS }
                ?: RecordingConfig.DEFAULT_SEGMENT_MINUTES,
            bufferDurationMinutes = preferences.getInt(
                KEY_BUFFER_MINUTES,
                RecordingConfig.DEFAULT_BUFFER_MINUTES,
            ).takeIf { it in RecordingConfig.BUFFER_MINUTE_OPTIONS }
                ?: RecordingConfig.DEFAULT_BUFFER_MINUTES,
        ),
    )

    val config: StateFlow<RecordingConfig> = _config.asStateFlow()

    fun setSegmentDuration(minutes: Int) {
        require(minutes in RecordingConfig.SEGMENT_MINUTE_OPTIONS)
        preferences.edit { putInt(KEY_SEGMENT_MINUTES, minutes) }
        _config.update { it.copy(segmentDurationMinutes = minutes) }
    }

    fun setBufferDuration(minutes: Int) {
        require(minutes in RecordingConfig.BUFFER_MINUTE_OPTIONS)
        preferences.edit { putInt(KEY_BUFFER_MINUTES, minutes) }
        _config.update { it.copy(bufferDurationMinutes = minutes) }
    }

    private companion object {
        const val PREFERENCES_NAME = "recording_settings"
        const val KEY_SEGMENT_MINUTES = "segment_minutes"
        const val KEY_BUFFER_MINUTES = "buffer_minutes"
    }
}
