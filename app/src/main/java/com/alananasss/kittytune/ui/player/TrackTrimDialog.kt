package com.alananasss.kittytune.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.audio.TrackTrim
import com.alananasss.kittytune.audio.TrimMode
import com.alananasss.kittytune.audio.TrimSegment
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.res.stringResource
import com.alananasss.kittytune.R

/**
 * Trimming the track that is playing, by ear (issue #33).
 *
 * There is no waveform here and that is deliberate. The request came from people who re-upload edited versions
 * of songs to drop a guest verse or a long intro, and the way anyone actually finds those boundaries is by
 * listening for them. So the editor's one gesture is *mark it here*: you let the track play, and when the part
 * you want gone starts, you press the button. A waveform would be a nicer picture and a worse tool — a guest
 * verse does not look like anything.
 *
 * The two modes are not two ways of saying the same thing:
 *
 * - **Cut** removes the spans and plays the rest. Right for a verse in the middle.
 * - **Keep** plays only the spans and *ends the track* after them. Right for trimming an intro and an outro at
 *   once, which cutting cannot express without knowing where the song really ends.
 *
 * Nothing is written to the audio. Clearing the trim gives the original back.
 */
@Composable
fun TrackTrimDialog(viewModel: PlayerViewModel) {
    if (!viewModel.showTrimDialog) return
    val track = viewModel.currentTrack ?: return

    // Edited as a draft and committed on save, so half-built spans never reach playback — a start with no end
    // yet would otherwise be a cut running to the end of the track, applied the moment it was typed.
    var mode by remember(track.id) { mutableStateOf(viewModel.currentTrim.mode) }
    var segments by remember(track.id) { mutableStateOf(viewModel.currentTrim.segments) }

    val duration = viewModel.duration.coerceAtLeast(0L)
    val position = viewModel.currentPosition.coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
    val draft = TrackTrim.of(mode, segments)

    AlertDialog(
        onDismissRequest = { viewModel.showTrimDialog = false },
        icon = { Icon(Icons.Rounded.ContentCut, null) },
        title = { Text(stringResource(R.string.trim_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.trim_sub),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Two buttons rather than the desktop's connected group, which does not exist on this side.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        TrimMode.CUT to stringResource(R.string.trim_mode_cut),
                        TrimMode.KEEP to stringResource(R.string.trim_mode_keep),
                    ).forEach { (picked, label) ->
                        if (mode == picked) {
                            Button(
                                onClick = { mode = picked },
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.weight(1f),
                            ) { Text(label, maxLines = 1) }
                        } else {
                            FilledTonalButton(
                                onClick = { mode = picked },
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.weight(1f),
                            ) { Text(label, maxLines = 1) }
                        }
                    }
                }

                Text(
                    stringResource(if (mode == TrimMode.CUT) R.string.trim_mode_cut_sub else R.string.trim_mode_keep_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                if (segments.isEmpty()) {
                    Text(
                        stringResource(R.string.trim_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                segments.forEachIndexed { index, segment ->
                    TrimSegmentRow(
                        segment = segment,
                        position = position,
                        onStartHere = {
                            segments = segments.toMutableList().also {
                                it[index] = segment.copy(startMs = position)
                            }
                        },
                        onEndHere = {
                            segments = segments.toMutableList().also {
                                it[index] = segment.copy(endMs = position)
                            }
                        },
                        onRemove = {
                            segments = segments.toMutableList().also { it.removeAt(index) }
                        },
                    )
                }

                FilledTonalButton(
                    onClick = {
                        // Opens at the playhead and runs to the end, which is the shape you want when you
                        // have just heard the part start: press once, keep listening, then mark the end.
                        val end = if (duration > position) duration else position + 1_000L
                        segments = segments + TrimSegment(position, end)
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(String.format(stringResource(R.string.trim_add_here), formatMs(position)))
                }

                if (!draft.isEmpty && duration > 0) {
                    Text(
                        String.format(stringResource(R.string.trim_plays_for), formatMs(draft.playedDurationMs(duration)), formatMs(duration)),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.saveCurrentTrim(draft)
                viewModel.showTrimDialog = false
            }) { Text(stringResource(R.string.btn_save)) }
        },
        dismissButton = {
            Row {
                if (!viewModel.currentTrim.isEmpty) {
                    TextButton(onClick = {
                        viewModel.clearCurrentTrim()
                        segments = emptyList()
                        viewModel.showTrimDialog = false
                    }) { Text(stringResource(R.string.trim_clear)) }
                }
                TextButton(onClick = { viewModel.showTrimDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        },
    )
}

@Composable
private fun TrimSegmentRow(
    segment: TrimSegment,
    position: Long,
    onStartHere: () -> Unit,
    onEndHere: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${formatMs(segment.startMs)} → ${formatMs(segment.endMs)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                // Stated rather than left to be worked out from the two timestamps: the length is the thing
                // you are judging when you decide whether the marks are right.
                Text(
                    formatMs(segment.durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = onRemove, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        Icons.Rounded.Delete,
                        stringResource(R.string.trim_remove_segment),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onStartHere) { Text(String.format(stringResource(R.string.trim_set_start), formatMs(position))) }
                TextButton(onClick = onEndHere) { Text(String.format(stringResource(R.string.trim_set_end), formatMs(position))) }
            }
        }
    }
}

/** "1:07" — the form a player shows everywhere else, so the marks read against the seek bar. */
private fun formatMs(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
