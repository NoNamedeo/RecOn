package com.example.recon.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.recon.AppContainer
import com.example.recon.data.RecordEntity
import com.example.recon.navigation.AppSettingsDestination
import com.example.recon.navigation.HomeDestination
import com.example.recon.navigation.RecordingsDestination
import com.example.recon.navigation.RecordingSettingsDestination
import com.example.recon.recording.RecordingServiceController
import com.example.recon.sharing.RecordingSharer
import com.example.recon.ui.components.AppPageScaffold
import com.example.recon.ui.components.DrawerNavigation
import com.example.recon.ui.home.HomeScreen
import com.example.recon.ui.home.HomeViewModel
import com.example.recon.ui.recordings.RecordingsScreen
import com.example.recon.ui.recordings.RecordingsViewModel
import com.example.recon.ui.settings.AppSettingsScreen
import com.example.recon.ui.settings.AppSettingsViewModel
import com.example.recon.ui.settings.RecordingSettingsScreen
import com.example.recon.ui.settings.RecordingSettingsViewModel

@Composable
fun HomeRoute(container: AppContainer, navigation: DrawerNavigation) {
    val context = LocalContext.current
    val viewModel: HomeViewModel = viewModel(
        factory = SimpleViewModelFactory { HomeViewModel(container.recordingSessionStore) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message -> snackbarHostState.showSnackbar(message) }
    }
    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        viewModel.dismissPermissionRationale()
        if (result[Manifest.permission.RECORD_AUDIO] == true || hasMicrophonePermission(context)) {
            viewModel.beginStart { RecordingServiceController.start(context) }
        } else {
            viewModel.permissionDenied()
        }
    }

    AppPageScaffold("RecOn", HomeDestination, navigation) { padding ->
        HomeScreen(
            state = state,
            paddingValues = padding,
            snackbarHostState = snackbarHostState,
            onStart = {
                if (hasRequestedPermissions(context)) {
                    viewModel.beginStart { RecordingServiceController.start(context) }
                } else {
                    viewModel.showPermissionRationale()
                }
            },
            onSave = viewModel::requestSave,
            onStop = viewModel::requestStop,
            onPermissionConfirm = { permissionsLauncher.launch(requiredRuntimePermissions()) },
            onPermissionDismiss = viewModel::dismissPermissionRationale,
            onStopAndSave = viewModel::saveFromStopConfirmation,
            onStopAndDiscard = {
                viewModel.discard { RecordingServiceController.discard(context) }
            },
            onTitleChange = viewModel::updateTitle,
            onSaveConfirm = {
                viewModel.confirmSave { title -> RecordingServiceController.save(context, title) }
            },
            onDismissDialog = viewModel::dismissDialogs,
        )
    }
}

@Composable
fun RecordingSettingsRoute(container: AppContainer, navigation: DrawerNavigation) {
    val viewModel: RecordingSettingsViewModel = viewModel(
        factory = SimpleViewModelFactory {
            RecordingSettingsViewModel(container.recordingSettingsRepository)
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    AppPageScaffold(
        "Impostazioni registrazione",
        RecordingSettingsDestination,
        navigation,
    ) { padding ->
        RecordingSettingsScreen(
            state = state,
            paddingValues = padding,
            onSegmentExpandedChange = viewModel::setSegmentMenuExpanded,
            onBufferExpandedChange = viewModel::setBufferMenuExpanded,
            onSegmentSelected = viewModel::selectSegmentDuration,
            onBufferSelected = viewModel::selectBufferDuration,
        )
    }
}

@Composable
fun AppSettingsRoute(navigation: DrawerNavigation) {
    val viewModel: AppSettingsViewModel = viewModel(
        factory = SimpleViewModelFactory(::AppSettingsViewModel),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    AppPageScaffold("Impostazioni app", AppSettingsDestination, navigation) { padding ->
        AppSettingsScreen(state, padding)
    }
}

@Composable
fun RecordingsRoute(container: AppContainer, navigation: DrawerNavigation) {
    val context = LocalContext.current
    val viewModel: RecordingsViewModel = viewModel(
        factory = SimpleViewModelFactory {
            RecordingsViewModel(context, container.recordRepository)
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    fun share(record: RecordEntity) {
        runCatching { RecordingSharer.share(context, record) }
            .onFailure {
                viewModel.reportError(it.message ?: "Impossibile condividere la registrazione")
            }
        viewModel.dismissMenu()
    }

    AppPageScaffold("Registrazioni", RecordingsDestination, navigation) { padding ->
        RecordingsScreen(
            state = state,
            paddingValues = padding,
            onPlayPause = viewModel::playPause,
            onSeek = viewModel::seek,
            onLongPress = viewModel::showMenu,
            onDismissMenu = viewModel::dismissMenu,
            onRename = viewModel::requestRename,
            onDelete = viewModel::requestDelete,
            onShare = ::share,
            onRenameTitleChange = viewModel::updateRenameTitle,
            onRenameConfirm = viewModel::confirmRename,
            onDeleteConfirm = viewModel::confirmDelete,
            onDismissDialog = {
                viewModel.dismissDialogs()
                viewModel.clearError()
            },
        )
    }
}

private fun hasMicrophonePermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

private fun hasRequestedPermissions(context: android.content.Context): Boolean =
    hasMicrophonePermission(context) &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED)

private fun requiredRuntimePermissions(): Array<String> = buildList {
    add(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()
