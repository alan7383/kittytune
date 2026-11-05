package com.alananasss.kittytune.wear.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.compose.ui.tooling.preview.Preview
import com.alananasss.kittytune.wear.R
import com.alananasss.kittytune.wear.common.TargetDeviceInfo
import com.alananasss.kittytune.wear.common.WearConstants
import com.alananasss.kittytune.wear.presentation.components.TargetDeviceSelectorChip
import com.alananasss.kittytune.wear.presentation.components.TargetPickerDialog
import com.alananasss.kittytune.wear.presentation.theme.KittyTuneWearTheme
import com.alananasss.kittytune.wear.presentation.theme.WearBackground
import com.alananasss.kittytune.wear.presentation.volume.EphemeralVolumeHud
import com.alananasss.kittytune.wear.presentation.volume.VolumeBezelArc
import com.alananasss.kittytune.wear.presentation.volume.VolumeControlsRow
import com.alananasss.kittytune.wear.presentation.volume.rotaryVolumeControl

/**
 * Stateful entry point for PlaybackScreen, binding with [PlaybackViewModel].
 */
@Composable
fun PlaybackScreen(
    modifier: Modifier = Modifier,
    viewModel: PlaybackViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    PlaybackScreen(
        uiState = uiState,
        onPlayPause = viewModel::onPlayPause,
        onNext = viewModel::onNext,
        onPrevious = viewModel::onPrevious,
        onVolumeChange = viewModel::onSetVolume,
        onTargetClick = viewModel::onToggleTarget,
        onSelectTarget = viewModel::onSelectTarget,
        onDismissTargetPicker = { viewModel.onShowTargetPicker(false) },
        modifier = modifier
    )
}

/**
 * Top-level stateless circular playback screen container.
 * Strictly designed for round Wear OS displays (390x390 and 450x450).
 */
@Composable
fun PlaybackScreen(
    uiState: WearPlaybackUiState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onVolumeChange: (Float) -> Unit = {},
    onTargetClick: () -> Unit = {},
    onSelectTarget: (TargetDeviceInfo) -> Unit = {},
    onDismissTargetPicker: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    ScreenScaffold(
        timeText = { TimeText() },
        modifier = modifier
    ) {
        PlaybackScreenContent(
            uiState = uiState,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onPrevious = onPrevious,
            onVolumeChange = onVolumeChange,
            onTargetClick = onTargetClick,
            onSelectTarget = onSelectTarget,
            onDismissTargetPicker = onDismissTargetPicker
        )
    }
}

/**
 * Pure circular playback control surface.
 *
 * Geometric & Power Constraints:
 * - AMOLED Pure Black (#000000) background shuts down individual OLED subpixels.
 * - Rotary crown scrolling controls volume with detent accumulation and haptics.
 * - VolumeBezelArc wraps along the right bezel.
 * - EphemeralVolumeHud badges volume adjustments.
 * - TargetDeviceSelectorChip positioned in safe zone below TimeText.
 * - Track title supports basicMarquee for ultra-long strings.
 * - Three-button triad (48dp Prev, 56dp Play/Pause, 48dp Next) meets touch standards.
 * - VolumeControlsRow provides on-screen fallback buttons.
 */
@Composable
fun PlaybackScreenContent(
    uiState: WearPlaybackUiState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onTargetClick: () -> Unit = {},
    onSelectTarget: (TargetDeviceInfo) -> Unit = {},
    onDismissTargetPicker: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WearBackground)
            .rotaryVolumeControl(
                currentVolume = uiState.volume,
                onVolumeChange = onVolumeChange
            ),
        contentAlignment = Alignment.Center
    ) {
        // 1. Right bezel curved volume progress arc
        VolumeBezelArc(
            volume = uiState.volume,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Optional subtle progress perimeter indicator for current track position
        if (uiState.hasTrack && uiState.durationMs > 0L) {
            CircularProgressIndicator(
                progress = { uiState.progressFraction() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp),
                colors = ProgressIndicatorDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.50f),
                    trackColor = Color.Transparent
                ),
                strokeWidth = 2.dp
            )
        }

        // 3. Central interaction column
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Target Device Selector Badge
            TargetDeviceSelectorChip(
                target = uiState.selectedTarget,
                connectionStatus = uiState.connectionStatus,
                onClick = onTargetClick
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Track Title with Marquee & Artist Display
            TrackInfoSection(
                title = uiState.title,
                artist = uiState.artist,
                hasTrack = uiState.hasTrack,
                isLoading = uiState.isLoading
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Playback Controls Triad (Prev | Play/Pause | Next)
            PlaybackControlsRow(
                isPlaying = uiState.isPlaying,
                hasTrack = uiState.hasTrack,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Discrete Volume Controls Row (- | speaker % | +)
            VolumeControlsRow(
                volume = uiState.volume,
                onVolumeChange = onVolumeChange
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Timeline status or idle branding
            TimelineStatusText(
                positionMs = uiState.positionMs,
                durationMs = uiState.durationMs,
                hasTrack = uiState.hasTrack
            )
        }

        // 4. Ephemeral Volume HUD overlay
        EphemeralVolumeHud(
            volume = uiState.volume,
            triggerKey = uiState.volumeRevision,
            modifier = Modifier.align(Alignment.Center)
        )

        // 5. Target device picker modal dialog
        TargetPickerDialog(
            visible = uiState.isTargetPickerVisible,
            currentTargetId = uiState.selectedTarget.id,
            availableTargets = uiState.availableTargets,
            onSelectTarget = onSelectTarget,
            onDismissRequest = onDismissTargetPicker
        )
    }
}

/**
 * Track title with marquee scrolling and artist name with ellipsis.
 * Width constrained to 82% of screen to prevent chord clipping on round bezels.
 */
@Composable
private fun TrackInfoSection(
    title: String,
    artist: String,
    hasTrack: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val displayTitle = when {
        isLoading -> stringResource(R.string.status_connecting)
        hasTrack -> title.ifBlank { "Unknown Title" }
        else -> stringResource(R.string.status_no_playback)
    }

    val displayArtist = when {
        hasTrack -> artist.ifBlank { "Unknown Artist" }
        else -> stringResource(R.string.wear_companion_title)
    }

    Column(
        modifier = modifier.fillMaxWidth(0.82f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Track Title (single line with smooth marquee for long text)
        Text(
            text = displayTitle,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .basicMarquee(
                    iterations = Int.MAX_VALUE,
                    initialDelayMillis = 1500,
                    repeatDelayMillis = 1500,
                    velocity = 30.dp
                )
        )

        Spacer(modifier = Modifier.height(1.dp))

        // Artist Name (secondary color, single line truncated with ellipsis)
        Text(
            text = displayArtist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(0.90f)
        )
    }
}

/**
 * Three-button playback control triad centered along the screen equator.
 *
 * Touch Targets:
 * - Previous: 48x48dp (FilledTonalIconButton)
 * - Play/Pause: 56x56dp (FilledIconButton with Primary container fill)
 * - Next: 48x48dp (FilledTonalIconButton)
 * - Total width = 48 + 8 + 56 + 8 + 48 = 168dp
 */
@Composable
private fun PlaybackControlsRow(
    isPlaying: Boolean,
    hasTrack: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Previous Track Button
        FilledTonalIconButton(
            onClick = onPrevious,
            modifier = Modifier.size(IconButtonDefaults.SmallButtonSize), // 48.dp
            enabled = hasTrack,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = stringResource(R.string.action_previous),
                modifier = Modifier.size(IconButtonDefaults.SmallIconSize) // 24.dp
            )
        }

        // Primary Play / Pause Toggle Button
        FilledIconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(56.dp), // Prominent central touch target
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) {
                    stringResource(R.string.action_pause)
                } else {
                    stringResource(R.string.action_play)
                },
                modifier = Modifier.size(32.dp)
            )
        }

        // Next Track Button
        FilledTonalIconButton(
            onClick = onNext,
            modifier = Modifier.size(IconButtonDefaults.SmallButtonSize), // 48.dp
            enabled = hasTrack,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = stringResource(R.string.action_next),
                modifier = Modifier.size(IconButtonDefaults.SmallIconSize) // 24.dp
            )
        }
    }
}

/**
 * Subtle playhead timeline readout or duration display below controls.
 */
@Composable
private fun TimelineStatusText(
    positionMs: Long,
    durationMs: Long,
    hasTrack: Boolean,
    modifier: Modifier = Modifier
) {
    if (!hasTrack || durationMs <= 0L) {
        // Display KittyTune companion branding indicator when idle
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.60f),
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "KittyTune",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        val posSec = (positionMs.coerceAtLeast(0L) / 1000).coerceAtMost(durationMs / 1000)
        val durSec = durationMs / 1000
        val posStr = String.format("%02d:%02d", posSec / 60, posSec % 60)
        val durStr = String.format("%02d:%02d", durSec / 60, durSec % 60)

        Text(
            text = "$posStr / $durStr",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = modifier
        )
    }
}

// =============================================================================
// Previews across Small Round (390x390) and Large Round (450x450)
// =============================================================================

@Preview
@Composable
private fun PlaybackScreenPlayingPreview() {
    KittyTuneWearTheme {
        PlaybackScreen(
            uiState = WearPlaybackUiState(
                trackId = 101L,
                title = "Moonlight Serenade — Extended Lo-Fi Mix",
                artist = "Meow DJ feat. Cyber Cat",
                isPlaying = true,
                positionMs = 74_000L,
                durationMs = 210_000L,
                volume = 0.65f,
                targetDevice = "Pixel 8 Pro",
                targetType = "PHONE",
                isConnected = true
            ),
            onPlayPause = {},
            onNext = {},
            onPrevious = {},
            onVolumeChange = {},
            onTargetClick = {}
        )
    }
}

@Preview
@Composable
private fun PlaybackScreenIdlePreview() {
    KittyTuneWearTheme {
        PlaybackScreen(
            uiState = WearPlaybackUiState(
                isPlaying = false,
                volume = 0.40f,
                targetDevice = "MacBook Pro",
                targetType = "DESKTOP",
                isConnected = true
            ),
            onPlayPause = {},
            onNext = {},
            onPrevious = {},
            onVolumeChange = {},
            onTargetClick = {}
        )
    }
}
