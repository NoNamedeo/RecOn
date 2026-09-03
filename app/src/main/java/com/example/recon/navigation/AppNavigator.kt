package com.example.recon.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

class AppNavigator(private val backStack: NavBackStack<NavKey>) {
    fun openHome() = open(HomeDestination)

    fun openRecordingSettings() = open(RecordingSettingsDestination)

    fun openAppSettings() = open(AppSettingsDestination)

    fun openRecordings() = open(RecordingsDestination)

    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    private fun open(destination: NavKey) {
        if (backStack.lastOrNull() != destination) backStack.add(destination)
    }
}
