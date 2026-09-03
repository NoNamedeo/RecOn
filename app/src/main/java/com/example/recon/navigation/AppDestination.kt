package com.example.recon.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object HomeDestination : NavKey

@Serializable
data object RecordingSettingsDestination : NavKey

@Serializable
data object AppSettingsDestination : NavKey

@Serializable
data object RecordingsDestination : NavKey
