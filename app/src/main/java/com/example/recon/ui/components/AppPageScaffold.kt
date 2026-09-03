package com.example.recon.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation3.runtime.NavKey
import com.example.recon.navigation.AppSettingsDestination
import com.example.recon.navigation.HomeDestination
import com.example.recon.navigation.RecordingsDestination
import com.example.recon.navigation.RecordingSettingsDestination
import kotlinx.coroutines.launch

data class DrawerNavigation(
    val openHome: () -> Unit,
    val openRecordingSettings: () -> Unit,
    val openAppSettings: () -> Unit,
    val openRecordings: () -> Unit,
)

private data class DrawerItem(
    val destination: NavKey,
    val label: String,
    val icon: ImageVector,
    val navigate: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPageScaffold(
    title: String,
    currentDestination: NavKey,
    navigation: DrawerNavigation,
    content: @Composable (PaddingValues) -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val items = listOf(
        DrawerItem(HomeDestination, "Home", Icons.Default.Home, navigation.openHome),
        DrawerItem(
            RecordingSettingsDestination,
            "Impostazioni registrazione",
            Icons.Default.Tune,
            navigation.openRecordingSettings,
        ),
        DrawerItem(
            AppSettingsDestination,
            "Impostazioni app",
            Icons.Default.Settings,
            navigation.openAppSettings,
        ),
        DrawerItem(
            RecordingsDestination,
            "Visualizza registrazioni",
            Icons.Default.Mic,
            navigation.openRecordings,
        ),
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(304.dp).fillMaxHeight()) {
                Column(modifier = Modifier.fillMaxSize().padding(vertical = 20.dp)) {
                    Text(
                        text = "RecOn",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(horizontal = 28.dp),
                    )
                    Spacer(Modifier.height(24.dp))
                    items.forEach { item ->
                        NavigationDrawerItem(
                            label = { Text(item.label) },
                            selected = item.destination == currentDestination,
                            icon = { Icon(item.icon, contentDescription = null) },
                            onClick = {
                                scope.launch { drawerState.close() }
                                item.navigate()
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                        )
                    }
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Apri menu")
                        }
                    },
                )
            },
            content = content,
        )
    }
}
