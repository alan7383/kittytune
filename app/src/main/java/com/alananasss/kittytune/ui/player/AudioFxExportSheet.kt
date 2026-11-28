@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.alananasss.kittytune.ui.player

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.audio.AudioFxExporter
import java.util.Locale

/** One thing the export applies, as the sheet lists it: "Earrape · 10 %". */
internal data class AppliedFx(val label: String, val value: String?, val icon: ImageVector)

/** The main setting of the effects that have one, shown as a percentage beside their name. */
private val primaryIntensity: Map<String, (AudioEffectsState) -> Float> = mapOf(
    "bass_boost" to { it.bassBoostIntensity },
    "earrape" to { it.earrapeIntensity },
    "reverb" to { it.reverbIntensity },
    "muffled" to { it.muffledIntensity },
    "eight_d" to { it.eightDSpeed },
    "vocal_remover" to { it.vocalRemoverLevel },
    "vocal_boost" to { it.vocalBoostIntensity },
    "flanger" to { it.flangerIntensity },
    "vintage_mp3" to { it.vintageMp3Compression },
    "sub_octaver" to { it.subOctaverLevel },
    "tape_saturation" to { it.tapeWarmth },
    "super_wide" to { it.superWideWidth },
    "chorus" to { it.chorusDepth },
)

/**
 * Not in the file: the ambient soundscape plays from a player of its own, loudness levelling is the
 * player's, and the equalizer gets its own chip from its own state.
 */
private val notRendered = setOf("rain", "normalization", "equalizer")

internal fun speedText(speed: Float): String =
    String.format(Locale.getDefault(), "%.2f", speed).trimEnd('0').trimEnd('.', ',') + "×"

internal fun appliedFx(context: Context, state: AudioEffectsState, equalizer: EqualizerState, effects: List<AudioFxDefinition>): List<AppliedFx> {
    val list = mutableListOf<AppliedFx>()
    if (state.speed != 1f) {
        list += AppliedFx(
            context.getString(R.string.fx_export_speed),
            speedText(state.speed) + " · " + context.getString(if (state.isPitchEnabled) R.string.fx_export_with_pitch else R.string.fx_export_pitch_kept),
            Icons.Rounded.Speed,
        )
    }
    effects.filter { it.id !in notRendered && it.isActive(state) }.forEach { fx ->
        val value = primaryIntensity[fx.id]?.invoke(state)?.let { "${(it * 100).toInt()} %" }
        list += AppliedFx(context.getString(fx.titleRes), value, fx.icon)
    }
    if (equalizer.isEnabled) {
        list += AppliedFx(context.getString(R.string.fx_export_equalizer), equalizer.selectedPreset.takeIf { it.isNotBlank() }, Icons.Rounded.GraphicEq)
    }
    return list
}

internal fun appliedFxSummary(applied: List<AppliedFx>): String =
    applied.joinToString(" · ") { fx -> listOfNotNull(fx.label, fx.value).joinToString(" ") }

/** The effects panel's entry point: what an export would contain, or a running export's progress. */
@Composable
internal fun AudioFxExportCard(viewModel: PlayerViewModel, effects: List<AudioFxDefinition>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by AudioFxExporter.state.collectAsState()
    val applied = appliedFx(context, viewModel.effectsState, viewModel.equalizerState, effects)
    val hasTrack = viewModel.currentTrack != null
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onOpen,
        enabled = hasTrack || state !is AudioFxExporter.State.Idle,
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(scheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.SaveAlt, null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.fx_export_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                val running = state as? AudioFxExporter.State.Running
                Text(
                    when {
                        running != null -> stringResource(R.string.fx_export_running_short, (running.progress * 100).toInt())
                        state is AudioFxExporter.State.Done -> stringResource(R.string.fx_export_done_short)
                        !hasTrack -> stringResource(R.string.fx_export_no_track)
                        applied.isEmpty() -> stringResource(R.string.fx_export_none_short)
                        else -> appliedFxSummary(applied)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                AnimatedVisibility(running != null) {
                    LinearWavyProgressIndicator(progress = { running?.progress ?: 0f }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.ChevronRight, null, tint = scheme.onSurfaceVariant)
        }
    }
}

/**
 * The export, in a sheet styled like the settings around it: a hero card carrying the track and, once
 * started, the progress; grouped rows for what is applied and the file; a connected pair of actions.
 */
@Composable
internal fun AudioFxExportSheet(viewModel: PlayerViewModel, effects: List<AudioFxDefinition>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val state by AudioFxExporter.state.collectAsState()
    DisposableEffect(Unit) {
        AudioFxExporter.sheetOpen = true
        onDispose { AudioFxExporter.sheetOpen = false }
    }
    // What is exported is fixed when the sheet opens: touching an effect meanwhile does not change it.
    val track = remember { viewModel.currentTrack }
    val effectsState = remember { viewModel.effectsState }
    val equalizer = remember { viewModel.equalizerState }
    val applied = remember { appliedFx(context, effectsState, equalizer, effects) }
    var name by rememberSaveable { mutableStateOf(track?.let(AudioFxExporter::suggestedName).orEmpty()) }
    val close = {
        AudioFxExporter.dismiss()
        onDismiss()
    }
    val durationMs = track?.durationMs ?: 0L
    val outputMs = if (effectsState.speed > 0f) (durationMs / effectsState.speed).toLong() else durationMs
    // A tick when the export ends, either way.
    LaunchedEffect(state::class) {
        when (state) {
            is AudioFxExporter.State.Done -> view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            is AudioFxExporter.State.Failed -> view.performHapticFeedback(HapticFeedbackConstants.REJECT)
            else -> Unit
        }
    }

    com.alananasss.kittytune.ui.common.KittyModalBottomSheet(
        onDismissRequest = close,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
        ) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ExportHero(
                    state = state,
                    artworkUrl = track?.fullResArtwork,
                    title = track?.title.orEmpty(),
                    artist = track?.displayArtist.orEmpty(),
                    durationText = when {
                        durationMs <= 0 -> null
                        outputMs != durationMs -> "${clock(durationMs)} → ${clock(outputMs)}"
                        else -> clock(durationMs)
                    },
                )
                AnimatedContent(state::class, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "fxExportBody") { step ->
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        when (step) {
                            AudioFxExporter.State.Running::class -> InfoCard(Icons.Rounded.Info, stringResource(R.string.fx_export_background_hint))
                            AudioFxExporter.State.Done::class -> Section(null) {
                                GroupItem {
                                    ItemRow(
                                        Icons.Rounded.AudioFile,
                                        (state as? AudioFxExporter.State.Done)?.name.orEmpty(),
                                        stringResource(R.string.fx_export_saved_to_music),
                                    )
                                }
                            }
                            AudioFxExporter.State.Failed::class -> InfoCard(
                                Icons.Rounded.ErrorOutline,
                                stringResource(
                                    when ((state as? AudioFxExporter.State.Failed)?.failure) {
                                        AudioFxExporter.Failure.PROTECTED -> R.string.fx_export_failed_protected
                                        AudioFxExporter.Failure.UNAVAILABLE -> R.string.fx_export_failed_unavailable
                                        else -> R.string.fx_export_failed_other
                                    },
                                ),
                            )
                            else -> {
                                Section(stringResource(R.string.fx_export_applied)) {
                                    if (applied.isEmpty()) {
                                        GroupItem { ItemRow(Icons.Rounded.Tune, stringResource(R.string.fx_export_none), null) }
                                    } else {
                                        applied.forEach { fx ->
                                            // A short value reads best as a pill; a sentence ("0.9× · with pitch") as a subtitle.
                                            val short = fx.value?.takeIf { it.length <= 7 }
                                            GroupItem {
                                                ItemRow(fx.icon, fx.label, fx.value.takeIf { short == null }, trailing = short?.let { { ValuePill(it) } })
                                            }
                                        }
                                    }
                                }
                                if (effectsState.isRainEnabled) InfoCard(Icons.Rounded.Info, stringResource(R.string.fx_export_ambient_note))

                                Section(stringResource(R.string.fx_export_file)) {
                                    GroupItem {
                                        OutlinedTextField(
                                            value = name,
                                            onValueChange = { if (it.length <= 120) name = it },
                                            singleLine = true,
                                            label = { Text(stringResource(R.string.fx_export_file_name)) },
                                            suffix = { Text(".m4a") },
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        )
                                    }
                                    GroupItem {
                                        val sizeMb = outputMs / 1000.0 * AudioFxExporter.BITRATE / 8 / (1024 * 1024)
                                        ItemRow(
                                            icon = Icons.Rounded.Folder,
                                            title = stringResource(R.string.fx_export_saved_to_music),
                                            subtitle = stringResource(R.string.fx_export_format_m4a),
                                            trailing = if (outputMs > 0) {
                                                { ValuePill(stringResource(R.string.fx_export_size, String.format(Locale.getDefault(), "%.0f", sizeMb.coerceAtLeast(1.0)))) }
                                            } else null,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            when (val s = state) {
                is AudioFxExporter.State.Running -> ActionGroup(
                    secondary = stringResource(R.string.fx_export_stop) to { AudioFxExporter.cancel() },
                    primary = stringResource(R.string.fx_export_hide) to onDismiss,
                    primaryIcon = null,
                    secondaryIsDanger = true,
                )
                is AudioFxExporter.State.Done -> ActionGroup(
                    secondary = stringResource(R.string.fx_export_share) to { runCatching { context.startActivity(AudioFxExporter.shareIntent(s.uri)) } },
                    primary = stringResource(R.string.fx_export_open) to {
                        runCatching { context.startActivity(AudioFxExporter.openIntent(s.uri)) }
                        close()
                    },
                    primaryIcon = Icons.AutoMirrored.Rounded.OpenInNew,
                    secondaryIcon = Icons.Rounded.Share,
                )
                is AudioFxExporter.State.Failed -> if (s.failure == AudioFxExporter.Failure.PROTECTED) {
                    // Trying again cannot change a licence: only offer to close.
                    ActionGroup(
                        secondary = stringResource(R.string.fx_export_retry) to { AudioFxExporter.dismiss() },
                        primary = stringResource(R.string.btn_close) to close,
                        primaryIcon = null,
                    )
                } else {
                    ActionGroup(
                        secondary = stringResource(R.string.btn_cancel) to close,
                        primary = stringResource(R.string.fx_export_retry) to { AudioFxExporter.dismiss() },
                        primaryIcon = Icons.Rounded.Refresh,
                    )
                }
                else -> ActionGroup(
                    secondary = stringResource(R.string.btn_cancel) to close,
                    primary = stringResource(R.string.fx_export_start) to {
                        if (track != null && name.isNotBlank()) {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            AudioFxExporter.export(context, track, effectsState, equalizer, name)
                        }
                    },
                    primaryIcon = Icons.Rounded.SaveAlt,
                    primaryEnabled = track != null && name.isNotBlank(),
                )
            }
        }
    }
}

/**
 * The top of the sheet: the track's cover in an expressive shape, and what is happening to it. The
 * shape turns while the export runs, the progress lives inside the card, its colour says how it ended.
 */
@Composable
private fun ExportHero(state: AudioFxExporter.State, artworkUrl: String?, title: String, artist: String, durationText: String?) {
    val scheme = MaterialTheme.colorScheme
    val running = state as? AudioFxExporter.State.Running
    val failed = state is AudioFxExporter.State.Failed
    val done = state is AudioFxExporter.State.Done
    val container by animateColorAsState(if (failed) scheme.errorContainer else scheme.primaryContainer, label = "heroContainer")
    val content by animateColorAsState(if (failed) scheme.onErrorContainer else scheme.onPrimaryContainer, label = "heroContent")
    val badge by animateColorAsState(if (failed) scheme.error else scheme.onPrimaryContainer, label = "heroBadge")
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(running != null) {
        if (running != null) {
            while (true) rotation.animateTo(rotation.value + 360f, tween(6000, easing = LinearEasing))
        } else {
            rotation.animateTo(rotation.value + 45f, spring(dampingRatio = 0.5f, stiffness = 120f))
        }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(container).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier.size(84.dp).graphicsLayer { rotationZ = rotation.value }.clip(MaterialShapes.Cookie9Sided.toShape()).background(badge),
                contentAlignment = Alignment.Center,
            ) {
                // The icon sits under the cover, so the shape is never empty while the cover loads.
                Icon(
                    when {
                        done -> Icons.Rounded.Check
                        failed -> Icons.Rounded.ErrorOutline
                        else -> Icons.Rounded.SaveAlt
                    },
                    null,
                    tint = if (failed) scheme.onError else scheme.primaryContainer,
                    modifier = Modifier.size(36.dp).graphicsLayer { rotationZ = -rotation.value },
                )
                if (!artworkUrl.isNullOrBlank() && !done && !failed) {
                    // The cover turns with the shape while the export runs.
                    AsyncImage(model = artworkUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(84.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(
                        when (state) {
                            is AudioFxExporter.State.Running -> R.string.fx_export_running_title
                            is AudioFxExporter.State.Done -> R.string.fx_export_done_title
                            is AudioFxExporter.State.Failed -> R.string.fx_export_failed_title
                            else -> R.string.fx_export_title
                        },
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = content,
                )
                Text(
                    listOf(title, artist).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = content.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        AnimatedVisibility(
            visible = running != null,
            enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = 400f)) + fadeIn(),
            exit = shrinkVertically(spring(dampingRatio = 0.9f, stiffness = 400f)) + fadeOut(),
        ) {
            val progress by animateFloatAsState(running?.progress ?: 1f, label = "heroProgress")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${(progress * 100).toInt()}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, color = content)
                    Text(" %", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = content.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 4.dp))
                    Spacer(Modifier.weight(1f))
                    ValuePill("M4A", container = content.copy(alpha = 0.12f), content = content)
                }
                LinearWavyProgressIndicator(
                    progress = { progress },
                    color = content,
                    trackColor = content.copy(alpha = 0.18f),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (durationText != null && running == null) {
            Row { ValuePill(durationText, icon = Icons.Rounded.Schedule, container = content.copy(alpha = 0.12f), content = content) }
        }
    }
}

/** A titled group in the Android 16 style: large corners outside, small ones between rows. */
@Composable
private fun Section(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) {
            Text(
                title,
                modifier = Modifier.padding(start = 16.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
    }
}

/** A row of a group: its corners swell while pressed, the M3 Expressive shape morph. */
@Composable
private fun GroupItem(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(if (pressed) 22.dp else 6.dp, spring(dampingRatio = 0.6f, stiffness = 700f), label = "itemCorner")
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceBright)
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick) else Modifier),
    ) { content() }
}

@Composable
private fun ItemRow(icon: ImageVector, title: String, subtitle: String?, trailing: (@Composable () -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(scheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

@Composable
private fun ValuePill(
    text: String,
    icon: ImageVector? = null,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Row(Modifier.clip(CircleShape).background(container).padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, tint = content, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = content, maxLines = 1)
    }
}

@Composable
private fun InfoCard(icon: ImageVector, text: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(scheme.secondaryContainer).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = scheme.onSecondaryContainer)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSecondaryContainer)
    }
}

/** The sheet's two actions as one connected group, the main one wider. */
@Composable
private fun ActionGroup(
    secondary: Pair<String, () -> Unit>,
    primary: Pair<String, () -> Unit>,
    primaryIcon: ImageVector?,
    secondaryIcon: ImageVector? = null,
    primaryEnabled: Boolean = true,
    secondaryIsDanger: Boolean = false,
) {
    val leading = ButtonGroupDefaults.connectedLeadingButtonShapes()
    val trailing = ButtonGroupDefaults.connectedTrailingButtonShapes()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        FilledTonalButton(
            onClick = secondary.second,
            shapes = ButtonShapes(leading.shape, leading.pressedShape),
            colors = if (secondaryIsDanger) {
                ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
            } else ButtonDefaults.filledTonalButtonColors(),
            modifier = Modifier.weight(1f).height(60.dp),
        ) {
            if (secondaryIcon != null) {
                Icon(secondaryIcon, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(secondary.first, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
        Button(
            onClick = primary.second,
            enabled = primaryEnabled,
            shapes = ButtonShapes(trailing.shape, trailing.pressedShape),
            modifier = Modifier.weight(1.6f).height(60.dp),
        ) {
            if (primaryIcon != null) {
                Icon(primaryIcon, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(primary.first, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
    }
}

/** Tells the listener an export finished while its sheet was closed. Composed in the effects panel. */
@Composable
internal fun AudioFxExportNotifier() {
    val context = LocalContext.current
    val state by AudioFxExporter.state.collectAsState()
    LaunchedEffect(state) {
        if (AudioFxExporter.sheetOpen) return@LaunchedEffect
        when (val s = state) {
            is AudioFxExporter.State.Done -> Toast.makeText(context, context.getString(R.string.fx_export_done_toast, s.name), Toast.LENGTH_LONG).show()
            is AudioFxExporter.State.Failed -> Toast.makeText(context, context.getString(R.string.fx_export_failed_title), Toast.LENGTH_SHORT).show()
            else -> Unit
        }
    }
}

private fun clock(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}
