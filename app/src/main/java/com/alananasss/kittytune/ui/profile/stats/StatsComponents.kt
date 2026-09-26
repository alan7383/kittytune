package com.alananasss.kittytune.ui.profile.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.stats.ActivityBucket
import com.alananasss.kittytune.data.stats.ListeningReport
import com.alananasss.kittytune.data.stats.ReportPeriod
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A cover or an avatar, with an icon on the container colour while it loads or when there is none.
 */
@Composable
internal fun StatsCover(
    url: String?,
    modifier: Modifier = Modifier,
    placeholder: ImageVector = Icons.Rounded.MusicNote
) {
    Box(
        modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            placeholder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

/**
 * A titled card on the surface-container colour, with an optional "show all" action.
 */
@Composable
internal fun StatsCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (action != null) {
                    TextButton(onClick = action) { Text(stringResource(R.string.listening_stats_show_all)) }
                }
            }
            Spacer(Modifier.size(12.dp))
            content()
        }
    }
}

/**
 * The change against the previous span, as a chip with a direction icon.
 *
 * A chip rather than a line of text because it is read next to the total, where the eye wants a
 * weight and a sign, not a sentence.
 */
@Composable
internal fun ChangeChip(change: Float, period: ReportPeriod, modifier: Modifier = Modifier) {
    val percent = (change * 100).roundToInt()
    val isUp = percent >= 0
    val amount = (if (isUp) "+" else "−") + "${abs(percent)} %"
    val key = when (period) {
        ReportPeriod.WEEK -> R.string.listening_stats_change_week
        ReportPeriod.MONTH -> R.string.listening_stats_change_month
        ReportPeriod.YEAR -> R.string.listening_stats_change_year
        ReportPeriod.ALL_TIME -> R.string.listening_stats_change_year
    }
    val tint = if (isUp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        modifier = modifier
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isUp) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = tint
            )
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(key, amount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** "2 s", "3 min 12 s", "1 h 5 min", "2 j 3 h". */
@Composable
internal fun formatDuration(ms: Long): String {
    if (ms <= 0L) return stringResource(R.string.listening_stats_duration_sec, 0)
    val totalSeconds = ms / 1000
    val days = totalSeconds / 86400
    val hours = (totalSeconds % 86400) / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        days > 0 -> stringResource(R.string.listening_stats_duration_days_hrs, days, hours)
        hours > 0 -> stringResource(R.string.listening_stats_duration_hr_min, hours, minutes)
        minutes > 0 -> stringResource(R.string.listening_stats_duration_min_sec, minutes, seconds)
        else -> stringResource(R.string.listening_stats_duration_sec, seconds)
    }
}

/** Coarser, for axes and chart subtitles: "12 min", "1 h 5 min", "40 s". */
@Composable
internal fun formatDurationShort(ms: Long): String {
    val totalMinutes = ms / 60_000
    return when {
        totalMinutes >= 60 -> stringResource(R.string.listening_stats_duration_hr_min, totalMinutes / 60, totalMinutes % 60)
        totalMinutes > 0 -> stringResource(R.string.listening_stats_duration_min, totalMinutes)
        else -> stringResource(R.string.listening_stats_duration_sec, ms / 1000)
    }
}

internal fun hourLabel(hour: Int): String = "%02d:00".format(hour)

internal fun weekdayName(day: DayOfWeek, locale: Locale = Locale.getDefault()): String =
    day.getDisplayName(TextStyle.FULL_STANDALONE, locale)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

/**
 * The span the numbers cover, in words: "22 – 28 Sept", "September 2026", "2026", "March 2024 – today".
 *
 * Shown under the title because a bare total does not say whether it is three days or three years.
 */
@Composable
internal fun spanLabel(period: ReportPeriod, report: ListeningReport): String {
    val zone = ZoneId.systemDefault()
    val loc = Locale.getDefault()
    val start = Instant.ofEpochMilli(report.window.startMs).atZone(zone).toLocalDate()
    val end = Instant.ofEpochMilli(report.window.endMs).atZone(zone).toLocalDate().minusDays(1)
    return when (period) {
        ReportPeriod.WEEK -> "${start.dayOfMonth} ${start.month.getDisplayName(TextStyle.SHORT, loc)} – " +
                "${end.dayOfMonth} ${end.month.getDisplayName(TextStyle.SHORT, loc)}"
        ReportPeriod.MONTH -> monthName(start, loc) + " ${start.year}"
        ReportPeriod.YEAR -> start.year.toString()
        ReportPeriod.ALL_TIME -> stringResource(
            R.string.listening_stats_since,
            monthName(start, loc) + " ${start.year}"
        )
    }
}

private fun monthName(date: LocalDate, loc: Locale): String =
    date.month.getDisplayName(TextStyle.FULL_STANDALONE, loc)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(loc) else it.toString() }

/** What one bar means in words, for the tapped-bar readout. */
internal fun bucketLabel(bucket: ActivityBucket, loc: Locale = Locale.getDefault()): String {
    val date = Instant.ofEpochMilli(bucket.startMs).atZone(ZoneId.systemDefault()).toLocalDate()
    return if (bucket.isMonth) {
        monthName(date, loc) + " ${date.year}"
    } else {
        "${date.dayOfWeek.getDisplayName(TextStyle.SHORT, loc)}, ${date.dayOfMonth} " +
                date.month.getDisplayName(TextStyle.SHORT, loc)
    }
}

/**
 * Every bar's label, thinned out on long charts so they never overlap.
 *
 * A week has seven bars and wants all seven; a year has twelve months and wants a third of them;
 * all time can have thirty and wants a handful.
 */
internal fun axisLabel(bucket: ActivityBucket, index: Int, count: Int, loc: Locale = Locale.getDefault()): String {
    val date = Instant.ofEpochMilli(bucket.startMs).atZone(ZoneId.systemDefault()).toLocalDate()
    return when {
        bucket.isMonth -> if (count <= 12 || index % 3 == 0) date.month.getDisplayName(TextStyle.NARROW_STANDALONE, loc) else ""
        count <= 7 -> date.dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, loc)
        else -> if (date.dayOfMonth == 1 || date.dayOfMonth % 5 == 0) date.dayOfMonth.toString() else ""
    }
}

/** Whether [bucket] is today — or, for month bars, the current month. */
internal fun isCurrent(bucket: ActivityBucket): Boolean {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(bucket.startMs).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return if (bucket.isMonth) date.year == today.year && date.month == today.month else date == today
}

internal fun Modifier.roundedClip(radius: Int): Modifier = clip(RoundedCornerShape(radius.dp))
