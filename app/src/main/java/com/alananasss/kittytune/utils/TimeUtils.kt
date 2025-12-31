package com.alananasss.kittytune.utils

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