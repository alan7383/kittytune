@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.alananasss.kittytune.ui.player

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.common.hapticAction

@Composable
internal fun AudioFxPresetControls(
    viewModel: PlayerViewModel,
    presetToDelete: String?,
    onDismissDelete: () -> Unit,
    onSaved: () -> Unit,
) {
    var presetToSave by remember { mutableStateOf<AudioFxPreset?>(null) }

    FilledTonalButton(
        onClick = hapticAction { presetToSave = viewModel.currentAudioFxPreset },
        modifier = Modifier.fillMaxWidth(),
        shapes = ButtonDefaults.shapes(),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Icon(
            Icons.Rounded.BookmarkAdd,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.audio_fx_save_preset))
    }

    presetToSave?.let { preset ->
        var name by remember { mutableStateOf("") }
        val focusRequester = remember { FocusRequester() }
        val duplicate = viewModel.audioFxPresets.keys.any { it.equals(name.trim(), ignoreCase = true) }
        val canSave = name.isNotBlank() && !duplicate
        val save: () -> Unit = {
            if (canSave && viewModel.saveAudioFxPreset(name, preset)) {
                presetToSave = null
                onSaved()
            }
        }
        AlertDialog(
            onDismissRequest = { presetToSave = null },
            icon = { Icon(Icons.Rounded.BookmarkAdd, contentDescription = null) },
            title = { Text(stringResource(R.string.audio_fx_save_preset)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    label = { Text(stringResource(R.string.audio_fx_preset_name)) },
                    singleLine = true,
                    isError = duplicate,
                    supportingText = if (duplicate) {
                        { Text(stringResource(R.string.audio_fx_preset_name_used)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    shape = RoundedCornerShape(16.dp),
                )
                LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
            },
            confirmButton = {
                Button(onClick = hapticAction(action = save), enabled = canSave, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.btn_save))
                }
            },
            dismissButton = {
                TextButton(onClick = hapticAction { presetToSave = null }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
        )
    }

    presetToDelete?.let { name ->
        AudioFxPresetDeleteDialog(name, viewModel, onDismissDelete)
    }
}

@Composable
internal fun AudioFxPresetDeleteDialog(name: String, viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.audio_fx_delete_preset)) },
        text = { Text(name) },
        confirmButton = {
            TextButton(
                onClick = hapticAction {
                    viewModel.deleteAudioFxPreset(name)
                    onDismiss()
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.btn_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = hapticAction(action = onDismiss), shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.btn_cancel))
            }
        },
    )
}
