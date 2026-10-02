package com.alananasss.kittytune

import com.alananasss.kittytune.utils.formatPlaylistTotalDuration
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistTotalDurationTest {

    private val hourMs = 60 * 60 * 1000L
    private val dayMs = 24 * hourMs

    private fun mockResolveEnglish(resId: Int, args: Array<out Any>): String {
        return when (resId) {
            R.string.listening_stats_duration_sec -> "${args[0]}s"
            R.string.listening_stats_duration_min -> "${args[0]} min"
            R.string.playlist_duration_hours -> "${args[0]}h"
            R.string.listening_stats_duration_hr_min -> "${args[0]}h ${args[1]}m"
            R.string.playlist_duration_days -> "${args[0]}d"
            R.string.listening_stats_duration_days_hrs -> "${args[0]}d ${args[1]}h"
            R.string.playlist_duration_months -> "${args[0]}mo"
            R.string.playlist_duration_months_days -> "${args[0]}mo ${args[1]}d"
            R.string.playlist_duration_year -> "${args[0]}y"
            R.string.playlist_duration_years -> "${args[0]}y"
            R.string.playlist_duration_year_months -> "${args[0]}y ${args[1]}mo"
            R.string.playlist_duration_years_months -> "${args[0]}y ${args[1]}mo"
            R.string.playlist_duration_year_days -> "${args[0]}y ${args[1]}d"
            R.string.playlist_duration_years_days -> "${args[0]}y ${args[1]}d"
            else -> ""
        }
    }

    private fun mockResolveFrench(resId: Int, args: Array<out Any>): String {
        return when (resId) {
            R.string.listening_stats_duration_sec -> "${args[0]}s"
            R.string.listening_stats_duration_min -> "${args[0]} min"
            R.string.playlist_duration_hours -> "${args[0]}h"
            R.string.listening_stats_duration_hr_min -> "${args[0]}h ${args[1]}m"
            R.string.playlist_duration_days -> "${args[0]}j"
            R.string.listening_stats_duration_days_hrs -> "${args[0]}j ${args[1]}h"
            R.string.playlist_duration_months -> "${args[0]} mois"
            R.string.playlist_duration_months_days -> "${args[0]} mois ${args[1]}j"
            R.string.playlist_duration_year -> "${args[0]} an"
            R.string.playlist_duration_years -> "${args[0]} ans"
            R.string.playlist_duration_year_months -> "${args[0]} an ${args[1]} mois"
            R.string.playlist_duration_years_months -> "${args[0]} ans ${args[1]} mois"
            R.string.playlist_duration_year_days -> "${args[0]} an ${args[1]}j"
            R.string.playlist_duration_years_days -> "${args[0]} ans ${args[1]}j"
            else -> ""
        }
    }

    private fun mockResolveGerman(resId: Int, args: Array<out Any>): String {
        return when (resId) {
            R.string.listening_stats_duration_sec -> "${args[0]} Sek."
            R.string.listening_stats_duration_min -> "${args[0]} Min."
            R.string.playlist_duration_hours -> "${args[0]} Std."
            R.string.listening_stats_duration_hr_min -> "${args[0]} Std. ${args[1]} Min."
            R.string.playlist_duration_days -> "${args[0]} T."
            R.string.listening_stats_duration_days_hrs -> "${args[0]} T. ${args[1]} Std."
            R.string.playlist_duration_months -> "${args[0]} Mon."
            R.string.playlist_duration_months_days -> "${args[0]} Mon. ${args[1]} T."
            R.string.playlist_duration_year -> "${args[0]} J."
            R.string.playlist_duration_years -> "${args[0]} J."
            R.string.playlist_duration_year_months -> "${args[0]} J. ${args[1]} Mon."
            R.string.playlist_duration_years_months -> "${args[0]} J. ${args[1]} Mon."
            R.string.playlist_duration_year_days -> "${args[0]} J. ${args[1]} T."
            R.string.playlist_duration_years_days -> "${args[0]} J. ${args[1]} T."
            else -> ""
        }
    }

    private fun mockResolveRussian(resId: Int, args: Array<out Any>): String {
        return when (resId) {
            R.string.listening_stats_duration_sec -> "${args[0]} с"
            R.string.listening_stats_duration_min -> "${args[0]} мин."
            R.string.playlist_duration_hours -> "${args[0]} ч."
            R.string.listening_stats_duration_hr_min -> "${args[0]} ч. ${args[1]} мин."
            R.string.playlist_duration_days -> "${args[0]} дн."
            R.string.listening_stats_duration_days_hrs -> "${args[0]} дн. ${args[1]} ч."
            R.string.playlist_duration_months -> "${args[0]} мес."
            R.string.playlist_duration_months_days -> "${args[0]} мес. ${args[1]} дн."
            R.string.playlist_duration_year -> "${args[0]} г."
            R.string.playlist_duration_years -> "${args[0]} г."
            R.string.playlist_duration_year_months -> "${args[0]} г. ${args[1]} мес."
            R.string.playlist_duration_years_months -> "${args[0]} г. ${args[1]} мес."
            R.string.playlist_duration_year_days -> "${args[0]} г. ${args[1]} дн."
            R.string.playlist_duration_years_days -> "${args[0]} г. ${args[1]} дн."
            else -> ""
        }
    }

    @Test
    fun testDurationFormattingEnglish() {
        assertEquals("", formatPlaylistTotalDuration(0L, ::mockResolveEnglish))
        assertEquals("", formatPlaylistTotalDuration(-1000L, ::mockResolveEnglish))
        assertEquals("45s", formatPlaylistTotalDuration(45_000L, ::mockResolveEnglish))
        assertEquals("4 min", formatPlaylistTotalDuration(4 * 60 * 1000L, ::mockResolveEnglish))
        assertEquals("1h", formatPlaylistTotalDuration(hourMs, ::mockResolveEnglish))
        assertEquals("7h 23m", formatPlaylistTotalDuration(7 * hourMs + 23 * 60 * 1000L, ::mockResolveEnglish))

        // Days + Hours
        assertEquals("1d", formatPlaylistTotalDuration(1 * dayMs, ::mockResolveEnglish))
        assertEquals("1d 4h", formatPlaylistTotalDuration(28 * hourMs + 5 * 60 * 1000L, ::mockResolveEnglish))
        assertEquals("11d 16h", formatPlaylistTotalDuration(280 * hourMs + 17 * 60 * 1000L, ::mockResolveEnglish))

        // Months + Days
        assertEquals("1mo", formatPlaylistTotalDuration(30 * dayMs, ::mockResolveEnglish))
        assertEquals("1mo 15d", formatPlaylistTotalDuration(45 * dayMs, ::mockResolveEnglish))
        assertEquals("2mo", formatPlaylistTotalDuration(60 * dayMs, ::mockResolveEnglish))

        // Years + Months / Days
        assertEquals("1y", formatPlaylistTotalDuration(365 * dayMs, ::mockResolveEnglish))
        assertEquals("1y 15d", formatPlaylistTotalDuration(380 * dayMs, ::mockResolveEnglish))
        assertEquals("1y 2mo", formatPlaylistTotalDuration((365 + 60) * dayMs, ::mockResolveEnglish))
        assertEquals("2y 3mo", formatPlaylistTotalDuration((2 * 365 + 90) * dayMs, ::mockResolveEnglish))
        assertEquals("2y", formatPlaylistTotalDuration(2 * 365 * dayMs, ::mockResolveEnglish))
    }

    @Test
    fun testDurationFormattingFrench() {
        assertEquals("", formatPlaylistTotalDuration(0L, ::mockResolveFrench))
        assertEquals("45s", formatPlaylistTotalDuration(45_000L, ::mockResolveFrench))
        assertEquals("4 min", formatPlaylistTotalDuration(4 * 60 * 1000L, ::mockResolveFrench))
        assertEquals("1h", formatPlaylistTotalDuration(hourMs, ::mockResolveFrench))
        assertEquals("7h 23m", formatPlaylistTotalDuration(7 * hourMs + 23 * 60 * 1000L, ::mockResolveFrench))

        // Days + Hours (User's screenshot: 4332 titres = 280h 17m -> 11j 16h)
        assertEquals("1j", formatPlaylistTotalDuration(1 * dayMs, ::mockResolveFrench))
        assertEquals("1j 4h", formatPlaylistTotalDuration(28 * hourMs + 5 * 60 * 1000L, ::mockResolveFrench))
        assertEquals("11j 16h", formatPlaylistTotalDuration(280 * hourMs + 17 * 60 * 1000L, ::mockResolveFrench))

        // Months + Days
        assertEquals("1 mois", formatPlaylistTotalDuration(30 * dayMs, ::mockResolveFrench))
        assertEquals("1 mois 15j", formatPlaylistTotalDuration(45 * dayMs, ::mockResolveFrench))
        assertEquals("2 mois", formatPlaylistTotalDuration(60 * dayMs, ::mockResolveFrench))

        // Years + Months / Days
        assertEquals("1 an", formatPlaylistTotalDuration(365 * dayMs, ::mockResolveFrench))
        assertEquals("1 an 15j", formatPlaylistTotalDuration(380 * dayMs, ::mockResolveFrench))
        assertEquals("1 an 2 mois", formatPlaylistTotalDuration((365 + 60) * dayMs, ::mockResolveFrench))
        assertEquals("2 ans 3 mois", formatPlaylistTotalDuration((2 * 365 + 90) * dayMs, ::mockResolveFrench))
        assertEquals("2 ans", formatPlaylistTotalDuration(2 * 365 * dayMs, ::mockResolveFrench))
    }

    @Test
    fun testDurationFormattingGerman() {
        assertEquals("1 Std.", formatPlaylistTotalDuration(hourMs, ::mockResolveGerman))
        assertEquals("7 Std. 23 Min.", formatPlaylistTotalDuration(7 * hourMs + 23 * 60 * 1000L, ::mockResolveGerman))
        assertEquals("11 T. 16 Std.", formatPlaylistTotalDuration(280 * hourMs + 17 * 60 * 1000L, ::mockResolveGerman))
        assertEquals("1 Mon. 15 T.", formatPlaylistTotalDuration(45 * dayMs, ::mockResolveGerman))
        assertEquals("1 J.", formatPlaylistTotalDuration(365 * dayMs, ::mockResolveGerman))
        assertEquals("2 J. 3 Mon.", formatPlaylistTotalDuration((2 * 365 + 90) * dayMs, ::mockResolveGerman))
    }

    @Test
    fun testDurationFormattingRussian() {
        assertEquals("1 ч.", formatPlaylistTotalDuration(hourMs, ::mockResolveRussian))
        assertEquals("7 ч. 23 мин.", formatPlaylistTotalDuration(7 * hourMs + 23 * 60 * 1000L, ::mockResolveRussian))
        assertEquals("11 дн. 16 ч.", formatPlaylistTotalDuration(280 * hourMs + 17 * 60 * 1000L, ::mockResolveRussian))
        assertEquals("1 мес. 15 дн.", formatPlaylistTotalDuration(45 * dayMs, ::mockResolveRussian))
        assertEquals("1 г.", formatPlaylistTotalDuration(365 * dayMs, ::mockResolveRussian))
        assertEquals("2 г. 3 мес.", formatPlaylistTotalDuration((2 * 365 + 90) * dayMs, ::mockResolveRussian))
    }

    @Test
    fun testPlaylistDurationDeserialization() {
        val json = """
            {
                "id": 123456,
                "title": "My SoundCloud Playlist",
                "track_count": 104,
                "duration": 22440000
            }
        """.trimIndent()
        val playlist = com.google.gson.Gson().fromJson(json, com.alananasss.kittytune.domain.Playlist::class.java)
        assertEquals(22440000L, playlist.durationMs)
        assertEquals(104, playlist.trackCount)
    }
}
