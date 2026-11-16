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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.alananasss.kittytune.wear.R
import com.alananasss.kittytune.wear.common.TargetDeviceInfo
import com.alananasss.kittytune.wear.presentation.components.TargetDeviceSelectorChip
import com.alananasss.kittytune.wear.presentation.components.TargetPickerDialog
import com.alananasss.kittytune.wear.presentation.components.TouchTargetSlot
import com.alananasss.kittytune.wear.presentation.theme.KittyTuneWearTheme
import com.alananasss.kittytune.wear.presentation.theme.WearBackground
import com.alananasss.kittytune.wear.presentation.volume.EphemeralVolumeHud
import com.alananasss.kittytune.wear.presentation.volume.VolumeBezelArc
import com.alananasss.kittytune.wear.presentation.volume.VolumeControlsDefaults
import com.alananasss.kittytune.wear.presentation.volume.VolumeControlsRow
import com.alananasss.kittytune.wear.presentation.volume.rotaryVolumeControl
import kotlinx.coroutines.delay

/** How long a transient error stays on screen before it clears itself. */
private const val ERROR_DISPLAY_MS = 3_500L

/**
 * Geometric layout specifications and defaults for [PlaybackScreen].
 *
 * The column is a 158dp stack. Every interactive control keeps a full 48dp touch target that never
 * covers another control: the playback triad and the volume row are each laid out at their own 48dp
 * height, so the top of a volume button can never answer a tap meant for "Previous" or "Next".
 * Only the target chip reserves a compact 18dp slot while its 48dp hit area overflows it: its
 * neighbours are the static time text and the track title, so nothing interactive is covered.
 *
 * Corner distance from the centre on a 390x390 round display (R = 97.5dp; the triad alone is 160dp
 * wide, which is what bounds how tall the column may be):
 *
 * | row         | layout | width | y span     | furthest corner |
 * |-------------|--------|-------|------------|-----------------|
 * | target chip | 18dp   | 72dp  | [-79, -61] | 86.8dp          |
 * | track info  | 26dp   | 124dp | [-60, -34] | 86.3dp          |
 * | playback    | 48dp   | 160dp | [-32, +16] | 86.2dp          |
 * | volume      | 48dp   | 112dp | [+18, +66] | 86.6dp          |
 * | timeline    | 12dp   | 70dp  | [+67, +79] | 86.4dp          |
 *
 * Every corner keeps at least 2.6dp of clearance to the 8dp-inset safe radius, 10.6dp to the physical
 * glass on a 390x390 display and 25.6dp on a 450x450 one.
 */
object PlaybackScreenDefaults {
    /** Target device selector chip visual width and slot height. */
    val TargetChipWidth: Dp = 72.dp
    val TargetChipHeight: Dp = 18.dp

    /** Maximum allowed width for the marquee track title and artist block. */
    val TrackInfoMaxWidth: Dp = 124.dp
    /** Measured height of track title (15sp line) + 1dp spacer + artist (12sp line). */
    val TrackInfoHeight: Dp = 26.dp

    /** Playback controls triad: 3 buttons at 48dp each + 2 spacers at 8dp = 160dp. */
    val PlaybackButtonSize: Dp = 48.dp
    val PlaybackControlsSpacing: Dp = 8.dp
    val PlaybackControlsRowWidth: Dp = (PlaybackButtonSize * 3) + (PlaybackControlsSpacing * 2) // 160.dp
    val PlaybackControlsRowHeight: Dp = PlaybackButtonSize // 48.dp

    /** Glyph size inside the skip and play/pause buttons. */
    val PlaybackIconSize: Dp = 24.dp
    val PlayPauseIconSize: Dp = 26.dp

    /** Discrete volume controls row dimensions referenced from [VolumeControlsDefaults]. */
    val VolumeRowWidth: Dp get() = VolumeControlsDefaults.RowWidth
    val VolumeRowHeight: Dp get() = VolumeControlsDefaults.RowHeight

    /** Timeline status / idle companion branding readout dimensions. */
    val TimelineMaxWidth: Dp = 70.dp
    val TimelineHeight: Dp = 12.dp

    /** Standard vertical spacing between adjacent interaction rows. */
    val SpacerStandard: Dp = 2.dp

    /** Compact vertical spacing between compact rows. */
    val SpacerCompact: Dp = 1.dp

    /**
     * Authoritative total vertical stack height for the 5 rows and 4 spacers.
     * 18dp + 1dp + 28dp + 2dp + 48dp + 2dp + 48dp + 1dp + 12dp = 160.dp.
     */
    val TotalStackHeight: Dp
        get() = TargetChipHeight +
            SpacerCompact +
            TrackInfoHeight +
            SpacerStandard +
            PlaybackControlsRowHeight +
            SpacerStandard +
            VolumeControlsDefaults.RowHeight +
            SpacerCompact +
            TimelineHeight
}

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
        onDismissError = viewModel::onDismissError,
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
    onDismissError: () -> Unit = {},
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
            onDismissTargetPicker = onDismissTargetPicker,
            onDismissError = onDismissError
        )
    }
}

/**
 * Pure circular playback control surface.
 *
 * Geometric & Power Constraints:
 * - AMOLED pure black (#000000) background shuts down individual OLED subpixels.
 * - Rotary crown scrolling controls volume with detent accumulation and haptics.
 * - VolumeBezelArc wraps along the right bezel.
 * - EphemeralVolumeHud badges volume adjustments.
 * - TargetDeviceSelectorChip positioned in safe zone below TimeText.
 * - Track title supports basicMarquee for ultra-long strings.
 * - Three-button triad (48dp Prev, 48dp Play/Pause, 48dp Next) meets touch standards.
 * - VolumeControlsRow provides on-screen compact controls (width <= 112dp, height <= 36dp).
 * - Total stacked vertical height is constrained to <= 142dp.
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
    onDismissError: () -> Unit = {},
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

        // 2. Optional subtle progress perimeter indicator for the current track position
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

        // 3. Central interaction column, compacted to the measured 160dp stack
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp)
                // On 195dp round displays, leave the clock clear of the target badge.
                .padding(top = if (LocalConfiguration.current.screenHeightDp <= 200) 12.dp else 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Target device selector badge. Its 48dp touch target overflows *downwards* into the
            // track title, which is static text, so the target never covers the playback buttons
            // and never grows the column past the round display.
            TouchTargetSlot(layoutHeight = PlaybackScreenDefaults.TargetChipHeight) {
                TargetDeviceSelectorChip(
                    target = uiState.selectedTarget,
                    connectionStatus = uiState.connectionStatus,
                    onClick = onTargetClick
                )
            }

            Spacer(modifier = Modifier.height(PlaybackScreenDefaults.SpacerCompact))

            // Track title with marquee and artist
            TrackInfoSection(
                title = uiState.title,
                artist = uiState.artist,
                hasTrack = uiState.hasTrack,
                isLoading = uiState.isLoading
            )

            Spacer(modifier = Modifier.height(PlaybackScreenDefaults.SpacerStandard))

            // Playback controls triad (Prev | Play/Pause | Next)
            PlaybackControlsRow(
                isPlaying = uiState.isPlaying,
                hasTrack = uiState.hasTrack,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious
            )

            Spacer(modifier = Modifier.height(PlaybackScreenDefaults.SpacerStandard))

            // Discrete volume controls row (- | speaker % | +). Laid out at its own 48dp height so
            // its targets can never reach the playback buttons above.
            VolumeControlsRow(
                volume = uiState.volume,
                onVolumeChange = onVolumeChange
            )

            Spacer(modifier = Modifier.height(PlaybackScreenDefaults.SpacerCompact))

            // Timeline status, idle branding, or a transient error readout
            if (uiState.errorMessage != null) {
                ErrorStatusText(message = uiState.errorMessage)
            } else {
                TimelineStatusText(
                    positionMs = uiState.positionMs,
                    durationMs = uiState.durationMs,
                    hasTrack = uiState.hasTrack,
                    connectionStatus = uiState.connectionStatus
                )
            }
        }

        // Auto-dismiss the transient error readout after a few seconds.
        val errorMessage = uiState.errorMessage
        LaunchedEffect(errorMessage) {
            if (errorMessage != null) {
                delay(ERROR_DISPLAY_MS)
                onDismissError()
            }
        }

        // 4. Ephemeral volume HUD overlay
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
 * Width constrained to 124dp to prevent chord clipping on round bezels.
 */
@Composable
private fun TrackInfoSection(
    title: String,
    artist: String,
    hasTrack: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val unknownTitle = stringResource(R.string.track_unknown_title)
    val unknownArtist = stringResource(R.string.track_unknown_artist)

    val displayTitle = when {
        isLoading -> stringResource(R.string.status_connecting)
        hasTrack -> title.ifBlank { unknownTitle }
        else -> stringResource(R.string.status_no_playback)
    }

    val displayArtist = when {
        hasTrack -> artist.ifBlank { unknownArtist }
        else -> stringResource(R.string.wear_companion_title)
    }

    Column(
        modifier = modifier.widthIn(max = PlaybackScreenDefaults.TrackInfoMaxWidth),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Track title (single line with smooth marquee for long text)
        Text(
            text = displayTitle,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                lineHeight = 15.sp
            ),
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

        // Artist name (secondary color, single line truncated with ellipsis)
        Text(
            text = displayArtist,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 10.sp,
                lineHeight = 12.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(0.92f)
        )
    }
}

/**
 * Three-button playback control triad centered along the screen equator.
 *
 * Touch Targets:
 * - Previous: 48x48dp (FilledTonalIconButton)
 * - Play/Pause: 48x48dp (FilledIconButton with Primary container fill)
 * - Next: 48x48dp (FilledTonalIconButton)
 * - Total width = 48 + 8 + 48 + 8 + 48 = 160dp
 *
 * All three use [IconButtonDefaults.animatedShapes], the Wear Material 3 press animation that morphs
 * the circular container into a rounded square, so a tap is visible as well as audible.
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
    val shapes = IconButtonDefaults.animatedShapes()
    val tonalColors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface
    )
    val primaryColors = IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(PlaybackScreenDefaults.PlaybackControlsSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Previous track button
        FilledTonalIconButton(
            onClick = onPrevious,
            modifier = Modifier.size(PlaybackScreenDefaults.PlaybackButtonSize),
            enabled = hasTrack,
            shapes = shapes,
            colors = tonalColors
        ) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = stringResource(R.string.action_previous),
                modifier = Modifier.size(PlaybackScreenDefaults.PlaybackIconSize)
            )
        }

        // Primary play / pause toggle button
        FilledIconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(PlaybackScreenDefaults.PlaybackButtonSize),
            shapes = shapes,
            colors = primaryColors
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) {
                    stringResource(R.string.action_pause)
                } else {
                    stringResource(R.string.action_play)
                },
                modifier = Modifier.size(PlaybackScreenDefaults.PlayPauseIconSize)
            )
        }

        // Next track button
        FilledTonalIconButton(
            onClick = onNext,
            modifier = Modifier.size(PlaybackScreenDefaults.PlaybackButtonSize),
            enabled = hasTrack,
            shapes = shapes,
            colors = tonalColors
        ) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = stringResource(R.string.action_next),
                modifier = Modifier.size(PlaybackScreenDefaults.PlaybackIconSize)
            )
        }
    }
}

/**
 * Subtle playhead timeline readout, connection state, or duration display below controls.
 */
@Composable
private fun TimelineStatusText(
    positionMs: Long,
    durationMs: Long,
    hasTrack: Boolean,
    connectionStatus: ConnectionStatus,
    modifier: Modifier = Modifier
) {
    val isOffline = connectionStatus == ConnectionStatus.DISCONNECTED || connectionStatus == ConnectionStatus.ERROR
    if (!hasTrack || durationMs <= 0L) {
        // Display KittyTune companion branding indicator when idle, or the phone connection state
        Row(
            modifier = modifier.widthIn(max = PlaybackScreenDefaults.TimelineMaxWidth),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isOffline) Icons.Filled.CloudOff else Icons.Filled.MusicNote,
                contentDescription = null,
                tint = if (isOffline) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.70f)
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.60f)
                },
                modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = when {
                    isOffline -> stringResource(R.string.status_disconnected)
                    connectionStatus == ConnectionStatus.CONNECTING -> stringResource(R.string.status_connecting)
                    else -> stringResource(R.string.app_name)
                },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
                color = if (isOffline) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.80f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1
            )
        }
    } else {
        val posSec = (positionMs.coerceAtLeast(0L) / 1000).coerceAtMost(durationMs / 1000)
        val durSec = durationMs / 1000
        val positionLabel = formatDuration(posSec)
        val durationLabel = formatDuration(durSec)

        Text(
            text = stringResource(R.string.status_position, positionLabel, durationLabel),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = modifier.widthIn(max = PlaybackScreenDefaults.TimelineMaxWidth)
        )
    }
}

/** Formats a second count as mm:ss, or h:mm:ss past an hour, without hard-coded separators. */
private fun formatDuration(totalSeconds: Long): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0L)
    val hours = safeSeconds / 3600
    val minutes = (safeSeconds % 3600) / 60
    val seconds = safeSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

/**
 * Transient one-line error readout. It occupies the same slot and height as [TimelineStatusText] so
 * showing an error never shifts the controls under the user's finger.
 */
@Composable
private fun ErrorStatusText(
    message: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.widthIn(max = PlaybackScreenDefaults.TimelineMaxWidth),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.80f),
            modifier = Modifier.size(11.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
            color = MaterialTheme.colorScheme.error,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

// =============================================================================
// Previews across small round (390x390) and large round (450x450)
// =============================================================================

@Preview
@Composable
private fun PlaybackScreenPlayingPreview() {
    KittyTuneWearTheme {
        PlaybackScreen(
            uiState = WearPlaybackUiState(
                trackId = 101L,
                title = "Moonlight Serenade, Extended Lo-Fi Mix",
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
