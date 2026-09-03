package com.example.recon

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.example.recon.navigation.AppNavigator
import com.example.recon.navigation.AppSettingsDestination
import com.example.recon.navigation.HomeDestination
import com.example.recon.navigation.RecordingsDestination
import com.example.recon.navigation.RecordingSettingsDestination
import com.example.recon.ui.AppSettingsRoute
import com.example.recon.ui.HomeRoute
import com.example.recon.ui.RecordingsRoute
import com.example.recon.ui.RecordingSettingsRoute
import com.example.recon.ui.components.DrawerNavigation
import com.example.recon.ui.theme.RecOnTheme

@Composable
fun RecOnApp() {
    val application = LocalContext.current.applicationContext as RecOnApplication
    val backStack = rememberNavBackStack(HomeDestination)
    val navigator = remember(backStack) { AppNavigator(backStack) }
    val drawerNavigation = remember(navigator) {
        DrawerNavigation(
            openHome = navigator::openHome,
            openRecordingSettings = navigator::openRecordingSettings,
            openAppSettings = navigator::openAppSettings,
            openRecordings = navigator::openRecordings,
        )
    }

    RecOnTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            NavDisplay(
                backStack = backStack,
                onBack = navigator::back,
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = entryProvider {
                    entry<HomeDestination> {
                        HomeRoute(application.container, drawerNavigation)
                    }
                    entry<RecordingSettingsDestination> {
                        RecordingSettingsRoute(application.container, drawerNavigation)
                    }
                    entry<AppSettingsDestination> {
                        AppSettingsRoute(drawerNavigation)
                    }
                    entry<RecordingsDestination> {
                        RecordingsRoute(application.container, drawerNavigation)
                    }
                },
            )
        }
    }
}
