package com.alananasss.kittytune.utils

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.alananasss.kittytune.R
import java.util.Locale

fun makeTimeString(duration: Long): String {
    val totalSeconds = duration / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        // long stuff format (like 2h 14min)
        String.format(Locale.getDefault(), "%dh %02dmin", hours, minutes)
    } else {
        // standard song format (03:45)
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

fun makeRemainingTimeString(position: Long, duration: Long): String {
    val remaining = (duration - position).coerceAtLeast(0L)
    return "-" + makeTimeString(remaining)
}

/**
 * Formats a playlist's total playtime in milliseconds into a concise, localized string,
 * matching KittyTuneDesktop logic:
 * - < 1 min: "45s"
 * - < 1 hr: "4 min"
 * - < 1 day: "7h 23m" / "1h"
 * - < 30 days: "11d 16h" / "1d"
 * - < 365 days: "1mo 15d" / "2mo"
 * - >= 365 days: "1y 2mo" / "2y 3mo" / "1y"
 */
fun formatPlaylistTotalDuration(
    ms: Long,
    resolveString: (Int, Array<out Any>) -> String
): String {
    if (ms <= 0L) return ""
    val totalSeconds = (ms + 500) / 1000
    val totalMinutes = totalSeconds / 60
    val totalHours = totalMinutes / 60
    val totalDays = totalHours / 24

    return when {
        totalDays >= 365 -> {
            val years = totalDays / 365
            val remDays = totalDays % 365
            val months = remDays / 30
            val days = remDays % 30
            when {
                months > 0 -> if (years == 1L) {
                    resolveString(R.string.playlist_duration_year_months, arrayOf(years, months))
                } else {
                    resolveString(R.string.playlist_duration_years_months, arrayOf(years, months))
                }
                days > 0 -> if (years == 1L) {
                    resolveString(R.string.playlist_duration_year_days, arrayOf(years, days))
                } else {
                    resolveString(R.string.playlist_duration_years_days, arrayOf(years, days))
                }
                else -> if (years == 1L) {
                    resolveString(R.string.playlist_duration_year, arrayOf(years))
                } else {
                    resolveString(R.string.playlist_duration_years, arrayOf(years))
                }
            }
        }
        totalDays >= 30 -> {
            val months = totalDays / 30
            val days = totalDays % 30
            if (days > 0) {
                resolveString(R.string.playlist_duration_months_days, arrayOf(months, days))
            } else {
                resolveString(R.string.playlist_duration_months, arrayOf(months))
            }
        }
        totalDays >= 1 -> {
            val days = totalDays
            val hours = totalHours % 24
            if (hours > 0) {
                resolveString(R.string.listening_stats_duration_days_hrs, arrayOf(days, hours))
            } else {
                resolveString(R.string.playlist_duration_days, arrayOf(days))
            }
        }
        totalHours >= 1 -> {
            val hours = totalHours
            val minutes = totalMinutes % 60
            if (minutes > 0) {
                resolveString(R.string.listening_stats_duration_hr_min, arrayOf(hours, minutes))
            } else {
                resolveString(R.string.playlist_duration_hours, arrayOf(hours))
            }
        }
        totalMinutes >= 1 -> {
            resolveString(R.string.listening_stats_duration_min, arrayOf(totalMinutes))
        }
        else -> {
            resolveString(R.string.listening_stats_duration_sec, arrayOf(totalSeconds))
        }
    }
}

fun formatPlaylistTotalDuration(resources: Resources, ms: Long): String {
    return formatPlaylistTotalDuration(ms) { resId, args ->
        if (args.isEmpty()) resources.getString(resId)
        else resources.getString(resId, *args)
    }
}

fun formatPlaylistTotalDuration(context: Context, ms: Long): String =
    formatPlaylistTotalDuration(context.resources, ms)

@Composable
fun rememberPlaylistTotalDuration(ms: Long): String {
    val context = LocalContext.current
    return remember(context, ms) {
        formatPlaylistTotalDuration(context.resources, ms)
    }
}



