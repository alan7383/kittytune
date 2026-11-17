package com.alananasss.kittytune.ui.home

import com.alananasss.kittytune.domain.Track
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * New songs grouped the way releases come out: on Fridays. "This Friday" is everything since the most recent
 * Friday, "last Friday" the week before it, and the rest is earlier (ported from the desktop PR #77).
 *
 * A Friday is when the industry puts a record out, so a shelf of new music read best as "what came out this
 * Friday", not as a calendar week the reader has to place themselves in.
 */
internal object ReleaseWeeks {

    enum class Week { THIS_FRIDAY, LAST_FRIDAY, EARLIER }

    data class Group(val week: Week, val since: LocalDate?, val tracks: List<Track>)

    /** [tracks] in their release weeks, newest week first and newest song first in each; empty weeks left out. */
    fun group(tracks: List<Track>, today: LocalDate = LocalDate.now()): List<Group> {
        val thisFriday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY))
        val lastFriday = thisFriday.minusWeeks(1)
        val dated = tracks.map { it to releaseDateOf(it) }
        fun weekOf(date: LocalDate?): Week = when {
            date == null -> Week.EARLIER
            !date.isBefore(thisFriday) -> Week.THIS_FRIDAY
            !date.isBefore(lastFriday) -> Week.LAST_FRIDAY
            else -> Week.EARLIER
        }
        return Week.entries.mapNotNull { week ->
            val inWeek = dated.filter { (_, date) -> weekOf(date) == week }
                .sortedByDescending { (_, date) -> date ?: LocalDate.MIN }
                .map { (track, _) -> track }
            if (inWeek.isEmpty()) return@mapNotNull null
            Group(
                week = week,
                since = when (week) {
                    Week.THIS_FRIDAY -> thisFriday
                    Week.LAST_FRIDAY -> lastFriday
                    Week.EARLIER -> null
                },
                tracks = inWeek,
            )
        }
    }

    /** The day [track] came out: its release date when it has one, else the day it was uploaded. */
    fun releaseDateOf(track: Track): LocalDate? =
        parseDate(track.releaseDate) ?: parseDate(track.createdAt)

    /** "3 Oct", in [language]. */
    fun shortDate(date: LocalDate, language: String): String =
        date.format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag(language))).trimEnd('.')

    private fun parseDate(raw: String?): LocalDate? {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { OffsetDateTime.parse(text).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate() }.getOrNull()
            ?: runCatching { java.time.Instant.parse(text).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull()
            ?: runCatching { LocalDate.parse(text.take(10)) }.getOrNull()
            ?: runCatching { LocalDate.parse(text.take(10).replace('/', '-')) }.getOrNull()
    }
}
