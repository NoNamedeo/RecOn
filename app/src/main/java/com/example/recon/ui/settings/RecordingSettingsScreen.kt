package com.example.recon.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.recon.config.RecordingConfig

@Composable
fun RecordingSettingsScreen(
    state: RecordingSettingsUiState,
    paddingValues: PaddingValues,
    onSegmentExpandedChange: (Boolean) -> Unit,
    onBufferExpandedChange: (Boolean) -> Unit,
    onSegmentSelected: (Int) -> Unit,
    onBufferSelected: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(paddingValues).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SettingsDropdown(
            label = "Durata del singolo segmento",
            selectedLabel = "${state.segmentDurationMinutes} minuti",
            expanded = state.segmentMenuExpanded,
            options = RecordingConfig.SEGMENT_MINUTE_OPTIONS,
            optionLabel = { "$it minuti" },
            onExpandedChange = onSegmentExpandedChange,
            onSelected = onSegmentSelected,
        )
        Spacer(Modifier.height(28.dp))
        SettingsDropdown(
            label = "Durata massima del buffer",
            selectedLabel = formatBufferMinutes(state.bufferDurationMinutes),
            expanded = state.bufferMenuExpanded,
            options = RecordingConfig.BUFFER_MINUTE_OPTIONS,
            optionLabel = ::formatBufferMinutes,
            onExpandedChange = onBufferExpandedChange,
            onSelected = onBufferSelected,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDropdown(
    label: String,
    selectedLabel: String,
    expanded: Boolean,
    options: List<Int>,
    optionLabel: (Int) -> String,
    onExpandedChange: (Boolean) -> Unit,
    onSelected: (Int) -> Unit,
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = { onSelected(option) },
                )
            }
        }
    }
}

private fun formatBufferMinutes(minutes: Int): String = when (minutes) {
    30 -> "30 minuti"
    60 -> "1 ora"
    else -> "${minutes / 60} ore"
}
