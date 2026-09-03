package com.example.recon.ui.recordings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.recon.data.RecordEntity

@Composable
fun RecordingsScreen(
    state: RecordingsUiState,
    paddingValues: PaddingValues,
    onPlayPause: (RecordEntity) -> Unit,
    onSeek: (RecordEntity, Float) -> Unit,
    onLongPress: (Long) -> Unit,
    onDismissMenu: () -> Unit,
    onRename: (RecordEntity) -> Unit,
    onDelete: (RecordEntity) -> Unit,
    onShare: (RecordEntity) -> Unit,
    onRenameTitleChange: (String) -> Unit,
    onRenameConfirm: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onDismissDialog: () -> Unit,
) {
    if (state.records.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Non ci sono ancora registrazioni salvate")
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.records, key = { it.id }) { record ->
                RecordingRow(
                    record = record,
                    isActive = state.activeRecordId == record.id,
                    isPlaying = state.activeRecordId == record.id && state.isPlaying,
                    positionMillis = if (state.activeRecordId == record.id) {
                        state.positionMillis
                    } else {
                        0L
                    },
                    durationMillis = if (state.activeRecordId == record.id) {
                        state.durationMillis.takeIf { it > 0L } ?: record.durationMillis
                    } else {
                        record.durationMillis
                    },
                    menuExpanded = state.menuRecordId == record.id,
                    onPlayPause = { onPlayPause(record) },
                    onSeek = { onSeek(record, it) },
                    onLongPress = { onLongPress(record.id) },
                    onDismissMenu = onDismissMenu,
                    onRename = { onRename(record) },
                    onDelete = { onDelete(record) },
                    onShare = { onShare(record) },
                )
            }
        }
    }

    if (state.renameRecordId != null) {
        AlertDialog(
            onDismissRequest = onDismissDialog,
            title = { Text("Rinomina registrazione") },
            text = {
                OutlinedTextField(
                    value = state.renameTitle,
                    onValueChange = onRenameTitleChange,
                    label = { Text("Titolo") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = onRenameConfirm, enabled = state.renameTitle.isNotBlank()) {
                    Text("Rinomina")
                }
            },
            dismissButton = { TextButton(onClick = onDismissDialog) { Text("Annulla") } },
        )
    }

    if (state.deleteRecordId != null) {
        AlertDialog(
            onDismissRequest = onDismissDialog,
            title = { Text("Eliminare la registrazione?") },
            text = { Text("Il file audio verrà eliminato definitivamente.") },
            confirmButton = { TextButton(onClick = onDeleteConfirm) { Text("Elimina") } },
            dismissButton = { TextButton(onClick = onDismissDialog) { Text("Annulla") } },
        )
    }

    state.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissDialog,
            title = { Text("Operazione non riuscita") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = onDismissDialog) { Text("OK") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingRow(
    record: RecordEntity,
    isActive: Boolean,
    isPlaying: Boolean,
    positionMillis: Long,
    durationMillis: Long,
    menuExpanded: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onLongPress: () -> Unit,
    onDismissMenu: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = onLongPress),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isActive) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onPlayPause, modifier = Modifier.size(40.dp)) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pausa" else "Riproduci",
                            )
                        }
                        val safeDuration = durationMillis.coerceAtLeast(1L)
                        Slider(
                            value = (positionMillis.toFloat() / safeDuration).coerceIn(0f, 1f),
                            onValueChange = onSeek,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${formatDuration(positionMillis)} / ${formatDuration(durationMillis)}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = onDismissMenu) {
            DropdownMenuItem(
                text = { Text("Rinomina registrazione") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = onRename,
            )
            DropdownMenuItem(
                text = { Text("Elimina registrazione") },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = onDelete,
            )
            DropdownMenuItem(
                text = { Text("Condividi") },
                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                onClick = onShare,
            )
        }
    }
}

private fun formatDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis.coerceAtLeast(0L) / 1_000L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}
