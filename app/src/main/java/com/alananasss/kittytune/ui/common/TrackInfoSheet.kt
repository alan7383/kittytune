package com.alananasss.kittytune.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.domain.Track
import java.util.Locale

/**
 * "Track info" bottom sheet — opened by tapping the song title on a feed card.
 *
 * The details a listener may want live here: where the audio comes from, how long the track is,
 * genre, upload date, label and the play/like/repost/comment counters. Everything optional
 * (genre, label, date) is simply omitted when the track has no value.
 */
@Composable
fun TrackInfoSheet(
    track: Track?,
    resolvedSource: String?,
    expanded: Boolean,
    onDismiss: () -> Unit,
) {
    if (!expanded || track == null) return

    KittyModalBottomSheet(onDismissRequest = onDismiss) {
        TrackInfoContent(track = track, resolvedSource = resolvedSource)
    }
}

@Composable
private fun TrackInfoContent(track: Track, resolvedSource: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.track_info_sheet_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = track.fullResArtwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title ?: stringResource(R.string.untitled_track),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.displayArtist.ifBlank { stringResource(R.string.unknown_artist) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Spacer(Modifier.height(6.dp))

        // The source of the audio — the indicator that used to sit on the mini player cover.
        InfoRow(
            label = stringResource(R.string.track_info_source),
            value = AudioSourceType.fromTrack(track, resolvedSource).displayName,
        )
        InfoRow(
            label = stringResource(R.string.track_info_duration),
            value = trackDurationText(track.actualDurationMs),
        )
        track.genre?.takeIf { it.isNotBlank() }?.let {
            InfoRow(label = stringResource(R.string.track_info_genre), value = it)
        }
        uploadedDateText(track.createdAt)?.let {
            InfoRow(label = stringResource(R.string.track_info_uploaded), value = it)
        }
        track.labelName?.takeIf { it.isNotBlank() }?.let {
            InfoRow(label = stringResource(R.string.track_info_label), value = it)
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatCell(value = track.playbackCount, label = stringResource(R.string.track_info_plays))
            StatCell(value = track.likesCount, label = stringResource(R.string.track_info_likes))
            StatCell(value = track.repostsCount, label = stringResource(R.string.track_info_reposts))
            StatCell(value = track.commentCount, label = stringResource(R.string.track_info_comments))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

@Composable
private fun StatCell(value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = formatCompactCount(value),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 3:07, 1:02:45 — the track length as the player slider shows it. */
private fun trackDurationText(ms: Long): String {
    if (ms <= 0L) return "—"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

/** SoundCloud's created_at ("2023-05-01T12:34:56Z") reduced to the date part. */
private fun uploadedDateText(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val date = Regex("""\d{4}-\d{2}-\d{2}""").find(raw)?.value ?: return null
    return date
}

/** 1 234 → 1.2K, 1 234 567 → 1.2M — the compact counters used across the app. */
private fun formatCompactCount(count: Int): String = when {
    count >= 1_000_000 -> formatCompactValue(count / 1_000_000f) + "M"
    count >= 1_000 -> formatCompactValue(count / 1_000f) + "K"
    else -> count.toString()
}

private fun formatCompactValue(value: Float): String {
    val rounded = String.format(Locale.US, "%.1f", value)
    return if (rounded.endsWith(".0")) rounded.dropLast(2) else rounded
}
