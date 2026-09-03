package com.example.recon.ui.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettingsUiState(
    val message: String = "Impostazioni applicazione, attualmente in sviluppo",
)

class AppSettingsViewModel : ViewModel() {
    private val _state = MutableStateFlow(AppSettingsUiState())
    val state: StateFlow<AppSettingsUiState> = _state.asStateFlow()
}
