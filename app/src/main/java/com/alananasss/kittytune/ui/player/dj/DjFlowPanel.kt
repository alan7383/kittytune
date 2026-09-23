/**
 * Developed by Jason-Marshall Fastner, Germany <jasonfastner@protonmail.com>
 * Questions, feedback, or beat-matching debates? Feel free to reach out via email!
 *
 * Note: Cats always land on their feet, and with this engine, your transitions will too.
 */
package com.alananasss.kittytune.ui.player.dj

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.player.DjFlowState
import com.alananasss.kittytune.ui.player.EnergyMode
import kotlin.math.roundToInt

/**
 * Minimal Material 3 surface for the DJ Flow engine.
 *
 * Deliberately small. It exists to show the whole feature working through nothing but the public
 * API — it reads only [DjFlowState] and calls only the four callbacks below. If something cannot
 * be built here, the engine's public surface is missing it, and that is a bug in the engine
 * rather than a reason to reach into internals.
 *
 * The full developer console lives in `DjDevDebugSheet` and is not meant to ship.
 */
@Composable
fun DjFlowPanel(
    state: DjFlowState,
    onToggleDj: (Boolean) -> Unit,
    onEnergyMode: (EnergyMode) -> Unit,
    onMixNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.dj_flow_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (state.isActive) stringResource(R.string.dj_flow_subtitle_on) else stringResource(R.string.dj_flow_subtitle_off),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.isActive, onCheckedChange = onToggleDj)
            }

            AnimatedVisibility(visible = state.isActive) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    NowPlayingRow(state)
                    BeatPulse(state)
                    EnergyRow(state.energyMode, onEnergyMode)
                    NextUp(state, onMixNow)
                }
            }
        }
    }
}

/** Tempo and key of the playing track. Analysis is async, so both can legitimately be unknown. */
@Composable
private fun NowPlayingRow(state: DjFlowState) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Stat(
            label = stringResource(R.string.dj_flow_tempo),
            value = if (state.currentBpm > 0f) stringResource(R.string.dj_flow_bpm_value, state.currentBpm.roundToInt())
            else stringResource(R.string.dj_flow_analysing),
            modifier = Modifier.weight(1f),
        )
        Stat(
            label = stringResource(R.string.dj_flow_key),
            value = state.currentKey?.code ?: stringResource(R.string.dj_flow_unknown),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
    }
}

/**
 * Four dots, one per beat of the bar, with the current one pulsing.
 *
 * Driven by `beatProgress` rather than `isDownbeat`: the former is continuous, so the animation
 * stays smooth even though position updates arrive in discrete ticks.
 */
@Composable
private fun BeatPulse(state: DjFlowState) {
    val pulse by animateFloatAsState(
        targetValue = if (state.beatProgress < 0.25f) 1f else 0.55f,
        label = "beatPulse",
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(4) { i ->
            val isCurrent = (i + 1) == state.beatInBar
            val isOne = i == 0
            Box(
                modifier = Modifier
                    .size(if (isOne) 12.dp else 9.dp)
                    .scale(if (isCurrent) pulse else 1f)
                    .clip(CircleShape)
                    .background(
                        when {
                            isCurrent && isOne -> MaterialTheme.colorScheme.primary
                            isCurrent -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.dj_flow_bar_beat, state.barInPhrase, state.beatInPhrase),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EnergyRow(current: EnergyMode, onSelect: (EnergyMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EnergyMode.entries.forEach { mode ->
            FilterChip(
                selected = mode == current,
                onClick = { onSelect(mode) },
                label = { Text(mode.displayName, style = MaterialTheme.typography.labelMedium) },
            )
        }
    }
}

/** The upcoming match plus the countdown to the planned mix-out. */
@Composable
private fun NextUp(state: DjFlowState, onMixNow: () -> Unit) {
    val match = state.nextTrackMatch
    if (match == null) {
        Text(
            stringResource(R.string.dj_flow_empty_queue),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.dj_flow_next_up), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = match.track.title ?: stringResource(R.string.dj_flow_unknown_track),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        Text(
            text = buildString {
                append(stringResource(R.string.dj_flow_match_summary, match.score.roundToInt()))
                if (match.bpm > 0f) append(" · ${match.bpm.roundToInt()} BPM")
                match.key?.let { append(" · ${it.code}") }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Bars read better than seconds here: the transition is scheduled musically, not on a clock.
        state.barsUntilTransition?.let { bars ->
            Text(
                text = when {
                    bars > 1 -> stringResource(R.string.dj_flow_mixing_in_bars, bars)
                    bars == 1 -> stringResource(R.string.dj_flow_mixing_in_bar)
                    else -> stringResource(R.string.dj_flow_mixing_now)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(2.dp))
        FilledTonalButton(onClick = onMixNow, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.dj_flow_mix_now))
        }
    }
}
