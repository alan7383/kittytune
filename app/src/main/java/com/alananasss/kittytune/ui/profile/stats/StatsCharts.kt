package com.alananasss.kittytune.ui.profile.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.stats.ListeningReport
import com.alananasss.kittytune.data.stats.ReportPeriod

/**
 * Listening per day — per month for a year or all time — over a labelled scale, with the average
 * as a dashed line and today marked.
 *
 * The scale and the average are the point: a bare set of bars reads as a shape, and this reads as
 * "about forty minutes on Friday, which is typical".
 *
 * Tapping a bar selects it, where the desktop hovers. The readout stays on until another bar is
 * tapped, because a finger cannot hover and a readout that vanishes on lift is no use on a phone.
 */
@Composable
internal fun ActivityCard(
    report: ListeningReport,
    period: ReportPeriod,
    modifier: Modifier = Modifier
) {
    val buckets = report.activity
    var selected by remember(buckets) { mutableStateOf<Int?>(null) }
    val shown = selected?.let { buckets.getOrNull(it) }
    val isMonthly = buckets.firstOrNull()?.isMonth == true
    val subtitle = when {
        shown != null -> "${bucketLabel(shown)} · ${formatDuration(shown.listenMs)}"
        isMonthly -> stringResource(
            R.string.listening_stats_activity_avg_month,
            formatDurationShort(if (buckets.isEmpty()) 0L else buckets.sumOf { it.listenMs } / buckets.size)
        )
        else -> buildString {
            append(stringResource(R.string.listening_stats_activity_avg_day, formatDurationShort(report.averagePerDayMs)))
            report.busiestWeekday
                ?.takeIf { period != ReportPeriod.WEEK || report.activeDays > 1 }
                ?.let {
                    append(" · ")
                    append(stringResource(R.string.listening_stats_busiest_day_named, weekdayName(it)))
                }
        }
    }
    StatsCard(
        title = stringResource(if (isMonthly) R.string.listening_stats_activity_months else R.string.listening_stats_activity_days),
        subtitle = subtitle,
        modifier = modifier
    ) {
        BarChart(
            values = buckets.map { it.listenMs },
            labels = buckets.mapIndexed { index, bucket -> axisLabel(bucket, index, buckets.size) },
            current = buckets.indexOfFirst { isCurrent(it) }.takeIf { it >= 0 },
            average = if (isMonthly || buckets.isEmpty()) null else report.averagePerDayMs,
            selected = selected,
            onSelect = { selected = it },
            modifier = Modifier.fillMaxWidth().height(180.dp)
        )
    }
}

/** Listening by hour of the day, and the same split into night, morning, afternoon and evening. */
@Composable
internal fun HoursCard(report: ListeningReport, modifier: Modifier = Modifier) {
    var selected by remember(report) { mutableStateOf<Int?>(null) }
    val subtitle = selected?.let { "${hourLabel(it)}–${hourLabel((it + 1) % 24)} · ${formatDuration(report.hours[it])}" }
        ?: report.peakHour?.let { stringResource(R.string.listening_stats_peak_hour_label, hourLabel(it)) }
    StatsCard(title = stringResource(R.string.listening_stats_hours), subtitle = subtitle, modifier = modifier) {
        BarChart(
            values = report.hours,
            labels = List(24) { hour -> if (hour % 6 == 0) hourLabel(hour) else "" },
            current = null,
            average = null,
            selected = selected,
            onSelect = { selected = it },
            modifier = Modifier.fillMaxWidth().height(120.dp)
        )
        Spacer(Modifier.height(12.dp))
        PartsOfDay(report.partsOfDay)
    }
}

/**
 * The four parts of the day as equal tiles, the biggest one picked out.
 *
 * Equal widths on purpose: the share is already written as a percentage, so a bar that also
 * encoded it would be saying the same thing twice and taking the width away from the label.
 */
@Composable
internal fun PartsOfDay(parts: List<Long>, modifier: Modifier = Modifier) {
    val total = parts.sum().coerceAtLeast(1L)
    val labels = listOf(
        R.string.listening_stats_night,
        R.string.listening_stats_morning,
        R.string.listening_stats_afternoon,
        R.string.listening_stats_evening
    )
    val icons = listOf(Icons.Rounded.Bedtime, Icons.Rounded.WbTwilight, Icons.Rounded.WbSunny, Icons.Rounded.NightsStay)
    val top = parts.indexOf(parts.max())
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        parts.forEachIndexed { index, value ->
            val isTop = index == top && value > 0
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isTop) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        icons[index],
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${(value * 100 / total)} %",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(labels[index]),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** A round step for the scale: 1, 2, 5, 10, 15, 30 minutes, then whole hours. */
private fun scaleStepMs(maxMs: Long): Long {
    val minute = 60_000L
    val steps = listOf(1, 2, 5, 10, 15, 30, 60, 120, 180, 240, 360, 600, 1200, 2400, 6000).map { it * minute }
    return steps.firstOrNull { maxMs / it <= 3 } ?: steps.last()
}

/**
 * Bars with rounded tops over a labelled scale. The selected bar — or today's — is in the primary
 * colour, the rest softer, and the dashed line is the average. The bars grow in when the data changes.
 */
@Composable
internal fun BarChart(
    values: List<Long>,
    labels: List<String>,
    current: Int?,
    average: Long?,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val grow = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { grow.animateTo(1f, tween(500)) }
    val step = scaleStepMs(values.maxOrNull() ?: 0L)
    val lines = (((values.maxOrNull() ?: 0L) + step - 1) / step).coerceIn(1, 4).toInt()
    val top = step * lines
    val strong = MaterialTheme.colorScheme.primary
    val soft = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val averageColor = MaterialTheme.colorScheme.tertiary
    val labelStyle = MaterialTheme.typography.labelSmall
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val scaleLabels = (lines downTo 1).map { formatDurationShort(step * it) }

    Column(modifier) {
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(values.size) {
                        detectTapGestures { offset ->
                            if (values.isEmpty() || size.width == 0) return@detectTapGestures
                            val slot = size.width.toFloat() / values.size
                            val index = (offset.x / slot).toInt().coerceIn(0, values.size - 1)
                            onSelect(if (index == selected) null else index)
                        }
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    if (values.isEmpty()) return@Canvas
                    for (line in 0..lines) {
                        val y = size.height - size.height * line / lines
                        drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    }
                    val slot = size.width / values.size
                    val barWidth = (slot * 0.62f).coerceAtMost(28.dp.toPx())
                    val radius = CornerRadius(barWidth / 2, barWidth / 2)
                    val highlight = selected ?: current
                    values.forEachIndexed { index, value ->
                        if (value <= 0) return@forEachIndexed
                        val left = slot * index + (slot - barWidth) / 2
                        val height = (size.height * value / top * grow.value)
                            .coerceAtLeast(barWidth.coerceAtMost(6.dp.toPx()))
                        drawRoundRect(
                            color = if (index == highlight) strong else soft,
                            topLeft = Offset(left, size.height - height),
                            size = Size(barWidth, height),
                            cornerRadius = radius
                        )
                    }
                    if (average != null && average > 0) {
                        val y = size.height - size.height * average / top
                        drawLine(
                            color = averageColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                        )
                    }
                }
            }
            // The scale, at the top of each grid line.
            Column(
                Modifier
                    .width(56.dp)
                    .fillMaxHeight()
                    .padding(start = 6.dp)
            ) {
                scaleLabels.forEachIndexed { index, label ->
                    Text(label, style = labelStyle, color = labelColor, maxLines = 1, softWrap = false)
                    if (index < scaleLabels.lastIndex) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().padding(end = 56.dp)) {
            labels.forEachIndexed { index, label ->
                Text(
                    label,
                    style = labelStyle,
                    color = if (index == current) MaterialTheme.colorScheme.primary else labelColor,
                    fontWeight = if (index == current) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
