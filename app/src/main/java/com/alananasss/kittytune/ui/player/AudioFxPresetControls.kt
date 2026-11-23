@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.alananasss.kittytune.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.hapticAction
import java.util.Locale

/**
 * The saved presets, as a row of cards under the speed: one tap brings a sound back, another tap
 * puts back what played before. Holding a card offers to rename it, overwrite it with the current
 * sound, or delete it. The first card saves the current sound.
 *
 * [onNeedsEarrapeWarning] runs when the preset would turn on the loud-volume effect before its
 * warning was ever accepted; nothing has changed then.
 */
@Composable
internal fun AudioFxPresetsSection(
    viewModel: PlayerViewModel,
    onNeedsEarrapeWarning: (String) -> Unit,
    showSaveDialog: Boolean,
    onDismissSaveDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var presetToRename by rememberSaveable { mutableStateOf<String?>(null) }
    var presetToDelete by rememberSaveable { mutableStateOf<String?>(null) }
    val presets = viewModel.audioFxPresets
    val activeName = viewModel.activeAudioFxPresetName
    // Nothing saved: no header and no hint taking room. The dialogs below still work, for the Save button.
    if (presets.isNotEmpty()) Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Bookmark, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            // The title takes what the button leaves; the count stays right after it.
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.audio_fx_saved_presets),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (presets.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Text(
                            "${presets.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }

        run {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().testTag("audio-fx-presets")) {
                presets.keys.reversed().chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { name ->
                            androidx.compose.runtime.key(name) {
                                AudioFxPresetCard(
                                    name = name,
                                    preset = presets.getValue(name),
                                    isActive = name == activeName || viewModel.isAudioFxPresetActive(name),
                                    onToggle = { if (!viewModel.toggleAudioFxPreset(name)) onNeedsEarrapeWarning(name) },
                                    onRename = { presetToRename = name },
                                    onUpdate = { viewModel.updateAudioFxPreset(name) },
                                    onDelete = { presetToDelete = name },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        AudioFxPresetNameDialog(
            title = stringResource(R.string.audio_fx_save_preset),
            initialName = "",
            confirmLabel = stringResource(R.string.btn_save),
            nameError = { viewModel.audioFxPresetNameError(it) },
            onConfirm = { name ->
                if (viewModel.saveAudioFxPreset(name)) {
                    onDismissSaveDialog()
                    true
                } else false
            },
            onDismiss = onDismissSaveDialog,
        )
    }
    presetToRename?.let { name ->
        AudioFxPresetNameDialog(
            title = stringResource(R.string.audio_fx_rename_preset),
            initialName = name,
            confirmLabel = stringResource(R.string.audio_fx_rename),
            nameError = { viewModel.audioFxPresetNameError(it, except = name) },
            onConfirm = { newName ->
                if (viewModel.renameAudioFxPreset(name, newName)) {
                    presetToRename = null
                    true
                } else false
            },
            onDismiss = { presetToRename = null },
        )
    }
    presetToDelete?.let { name ->
        AudioFxPresetDeleteDialog(name, viewModel) { presetToDelete = null }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AudioFxPresetCard(
    name: String,
    preset: AudioFxPreset,
    isActive: Boolean,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(if (isActive) scheme.secondaryContainer else scheme.surfaceContainerHigh, label = "presetContainer")
    val content by animateColorAsState(if (isActive) scheme.onSecondaryContainer else scheme.onSurface, label = "presetContent")
    val corner by animateDpAsState(
        if (isActive) 32.dp else 24.dp,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "presetCorner",
    )
    val spin by animateFloatAsState(if (isActive) 360f else 0f, spring(stiffness = Spring.StiffnessVeryLow), label = "presetSpin")
    val toggle = hapticAction(HapticKind.MEDIUM, onToggle)
    val openMenu = hapticAction(HapticKind.HEAVY) { menuOpen = true }

    Box(modifier) {
        Surface(
            shape = RoundedCornerShape(corner),
            color = container,
            contentColor = content,
            modifier = Modifier
                .fillMaxWidth()
                .height(PRESET_CARD_HEIGHT)
                .clip(RoundedCornerShape(corner))
                .combinedClickable(
                    role = Role.Button,
                    onClick = toggle,
                    onLongClick = openMenu,
                    onLongClickLabel = stringResource(R.string.audio_fx_preset_options),
                )
                .semantics { selected = isActive }
                .testTag("audio-fx-preset-$name"),
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .graphicsLayer { rotationZ = spin }
                            .clip(if (isActive) MaterialShapes.Cookie9Sided.toShape() else CircleShape)
                            .background(if (isActive) scheme.primary else scheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (isActive) Icons.Rounded.Check else Icons.Rounded.BookmarkBorder,
                            contentDescription = null,
                            tint = if (isActive) scheme.onPrimary else scheme.primary,
                            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = -spin },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    AnimatedVisibility(isActive, enter = scaleIn(), exit = scaleOut()) {
                        Surface(shape = CircleShape, color = scheme.primary, contentColor = scheme.onPrimary) {
                            Text(
                                stringResource(R.string.audio_fx_preset_on),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    audioFxPresetSummary(preset),
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            shape = RoundedCornerShape(20.dp),
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(if (isActive) R.string.audio_fx_preset_turn_off else R.string.audio_fx_preset_apply)) },
                leadingIcon = { Icon(if (isActive) Icons.Rounded.BookmarkBorder else Icons.Rounded.Bookmark, null) },
                onClick = { menuOpen = false; onToggle() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.audio_fx_update_preset)) },
                leadingIcon = { Icon(Icons.Rounded.Save, null) },
                enabled = !isActive,
                onClick = { menuOpen = false; onUpdate() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.audio_fx_rename)) },
                leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) },
                onClick = { menuOpen = false; onRename() },
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = { Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { menuOpen = false; onDelete() },
            )
        }
    }
}

/** "3 effects · 1.25×": what a preset changes, at a glance. */
@Composable
internal fun audioFxPresetSummary(preset: AudioFxPreset): String {
    val count = preset.activeEffectCount()
    val effects = when (count) {
        0 -> stringResource(R.string.audio_fx_preset_no_effects)
        1 -> stringResource(R.string.audio_fx_preset_one_effect)
        else -> stringResource(R.string.audio_fx_preset_effects, count)
    }
    val speed = preset.effects.speed
    return if (speed == 1f) effects else "$effects · ${String.format(Locale.getDefault(), "%.2f", speed).trimEnd('0').trimEnd('.', ',')}×"
}

/** Asks for a preset name, saying as it is typed why one cannot be used. */
@Composable
internal fun AudioFxPresetNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    nameError: (String) -> AudioFxPresetNameError?,
    onConfirm: (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    var field by remember { mutableStateOf(TextFieldValue(initialName, TextRange(0, initialName.length))) }
    val focusRequester = remember { FocusRequester() }
    val error = nameError(field.text)
    // An empty field is simply not ready yet; it is not worth a red message.
    val shownError = error.takeIf { it != AudioFxPresetNameError.EMPTY }
    val canSave = error == null && field.text.trim() != initialName
    val save: () -> Unit = { if (canSave) onConfirm(field.text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.BookmarkAdd, contentDescription = null) },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = field,
                onValueChange = { if (it.text.length <= AudioFxPreset.MAX_NAME_LENGTH + 8) field = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                label = { Text(stringResource(R.string.audio_fx_preset_name)) },
                singleLine = true,
                isError = shownError != null,
                supportingText = {
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            when (shownError) {
                                AudioFxPresetNameError.TAKEN -> stringResource(R.string.audio_fx_preset_name_used)
                                AudioFxPresetNameError.TOO_LONG -> stringResource(R.string.audio_fx_preset_name_too_long)
                                else -> ""
                            },
                            modifier = Modifier.weight(1f),
                        )
                        Text("${field.text.trim().length}/${AudioFxPreset.MAX_NAME_LENGTH}")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                shape = RoundedCornerShape(16.dp),
            )
            LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
        },
        confirmButton = {
            Button(
                onClick = hapticAction(action = save),
                enabled = canSave,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.testTag("audio-fx-name-confirm"),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = hapticAction(action = onDismiss), shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.btn_cancel))
            }
        },
    )
}

@Composable
internal fun AudioFxPresetDeleteDialog(name: String, viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
        title = { Text(stringResource(R.string.audio_fx_delete_preset)) },
        text = { Text(stringResource(R.string.audio_fx_delete_preset_body, name)) },
        confirmButton = {
            Button(
                onClick = hapticAction {
                    viewModel.deleteAudioFxPreset(name)
                    onDismiss()
                },
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
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

private val PRESET_CARD_HEIGHT = 112.dp
