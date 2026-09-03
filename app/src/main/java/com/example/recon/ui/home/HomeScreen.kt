package com.example.recon.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.recon.recording.RecordingStatus

@Composable
fun HomeScreen(
    state: HomeUiState,
    paddingValues: PaddingValues,
    snackbarHostState: SnackbarHostState,
    onStart: () -> Unit,
    onSave: () -> Unit,
    onStop: () -> Unit,
    onPermissionConfirm: () -> Unit,
    onPermissionDismiss: () -> Unit,
    onStopAndSave: () -> Unit,
    onStopAndDiscard: () -> Unit,
    onTitleChange: (String) -> Unit,
    onSaveConfirm: () -> Unit,
    onDismissDialog: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = when (state.session.status) {
                    RecordingStatus.RECORDING -> "Registrazione attiva"
                    RecordingStatus.STARTING -> "Avvio in corso…"
                    RecordingStatus.FINALIZING -> "Salvataggio in corso…"
                    RecordingStatus.STOPPING -> "Interruzione in corso…"
                    RecordingStatus.ERROR -> "Registrazione non attiva"
                    RecordingStatus.IDLE -> "Pronto per registrare"
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            state.session.errorMessage?.let { message ->
                Spacer(Modifier.height(16.dp))
                Card(modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp)) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            Spacer(Modifier.height(36.dp))
            ActionButton(
                text = "Avvia registrazione",
                icon = { Icon(Icons.Default.Mic, contentDescription = null) },
                enabled = state.canStart,
                onClick = onStart,
            )
            Spacer(Modifier.height(14.dp))
            ActionButton(
                text = "Salva registrazione",
                icon = { Icon(Icons.Default.Save, contentDescription = null) },
                enabled = state.canSaveOrStop,
                onClick = onSave,
            )
            Spacer(Modifier.height(14.dp))
            ActionButton(
                text = "Interrompi registrazione",
                icon = { Icon(Icons.Default.DeleteForever, contentDescription = null) },
                enabled = state.canSaveOrStop,
                onClick = onStop,
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }

    if (state.showPermissionRationale) {
        AlertDialog(
            onDismissRequest = onPermissionDismiss,
            title = { Text("Autorizzazioni necessarie") },
            text = {
                Text(
                    "Usiamo queste autorizzazioni per eseguire correttamente la " +
                        "registrazione 24/7 e mostrare la notifica persistente.",
                )
            },
            confirmButton = { TextButton(onClick = onPermissionConfirm) { Text("Continua") } },
            dismissButton = { TextButton(onClick = onPermissionDismiss) { Text("Annulla") } },
        )
    }

    if (state.showStopConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissDialog,
            title = { Text("Interrompere la registrazione?") },
            text = { Text("Puoi salvare il buffer corrente oppure scartarlo definitivamente.") },
            confirmButton = { TextButton(onClick = onStopAndSave) { Text("Salva") } },
            dismissButton = { TextButton(onClick = onStopAndDiscard) { Text("Scarta") } },
        )
    }

    if (state.saveDialogReason != null) {
        AlertDialog(
            onDismissRequest = onDismissDialog,
            title = { Text("Titolo della registrazione") },
            text = {
                OutlinedTextField(
                    value = state.recordingTitle,
                    onValueChange = onTitleChange,
                    singleLine = true,
                    label = { Text("Titolo") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onSaveConfirm,
                    enabled = state.recordingTitle.isNotBlank(),
                ) { Text("Salva") }
            },
            dismissButton = { TextButton(onClick = onDismissDialog) { Text("Annulla") } },
        )
    }
}

@Composable
private fun ActionButton(
    text: String,
    icon: @Composable () -> Unit,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp).height(56.dp),
    ) {
        icon()
        Spacer(Modifier.padding(horizontal = 6.dp))
        Text(text)
    }
}
