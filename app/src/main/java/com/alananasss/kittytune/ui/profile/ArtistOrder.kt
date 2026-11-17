package com.alananasss.kittytune.ui.profile

import com.alananasss.kittytune.R
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max

/**
 * The orders an artist's lists can be put in (desktop PR #77).
 *
 * "Popular" used to be every song by plays of all time, so the top of it was the song that had had the longest to
 * collect them, not the one people play now. The default is how the page's own popular songs are ordered, which
 * is what the card showed, and a song counts for what it collects per day of its life.
 */
object ArtistOrder {

    enum class TrackSort(val labelRes: Int) {
        /** What is listened to now: the page's popular songs as the card shows them, then the rest by plays a day. */
        NOW(R.string.sort_now),
        ALL_TIME(R.string.sort_all_time),
        NEWEST(R.string.sort_newest),
        OLDEST(R.string.sort_oldest),
        LIKES(R.string.sort_likes),
    }

    enum class ReleaseSort(val labelRes: Int) {
        NEWEST(R.string.sort_newest),
        OLDEST(R.string.sort_oldest),
        TITLE(R.string.sort_title_az),
        LIKES(R.string.sort_likes),
    }

    /**
     * [tracks] in the order [sort] asks for.
     *
     * @param cardOrder the songs the page shows as popular, in its order; [TrackSort.NOW] puts them first, since that
     *   order is the service's own idea of what is played now.
     */
    fun sortTracks(
        sort: TrackSort,
        tracks: List<Track>,
        cardOrder: List<Track> = emptyList(),
        today: LocalDate = LocalDate.now(),
    ): List<Track> = when (sort) {
        TrackSort.NOW -> {
            val first = cardOrder.mapNotNull { top -> tracks.firstOrNull { it.id == top.id } }
            val firstIds = first.mapTo(HashSet()) { it.id }
            first + tracks.filter { it.id !in firstIds }.sortedByDescending { playsPerDay(it, today) }
        }
        TrackSort.ALL_TIME -> tracks.sortedByDescending { it.playbackCount }
        TrackSort.NEWEST -> tracks.sortedByDescending { dateOf(it.releaseDate ?: it.createdAt) ?: LocalDate.MIN }
        TrackSort.OLDEST -> tracks.sortedBy { dateOf(it.releaseDate ?: it.createdAt) ?: LocalDate.MAX }
        TrackSort.LIKES -> tracks.sortedByDescending { it.likesCount }
    }

    fun sortReleases(sort: ReleaseSort, releases: List<Playlist>): List<Playlist> = when (sort) {
        ReleaseSort.NEWEST -> releases.sortedByDescending { dateOf(it.releaseDate ?: it.createdAt) ?: LocalDate.MIN }
        ReleaseSort.OLDEST -> releases.sortedBy { dateOf(it.releaseDate ?: it.createdAt) ?: LocalDate.MAX }
        ReleaseSort.TITLE -> releases.sortedBy { it.title.orEmpty().lowercase() }
        ReleaseSort.LIKES -> releases.sortedByDescending { it.likesCount ?: 0 }
    }

    /** Plays a day over the song's life; a song under a month old counts as a month old, so a day's plays are not a trend. */
    internal fun playsPerDay(track: Track, today: LocalDate): Double {
        val released = dateOf(track.releaseDate ?: track.createdAt)
        val ageDays = if (released == null) UNKNOWN_AGE_DAYS else max(ChronoUnit.DAYS.between(released, today), MIN_AGE_DAYS)
        return track.playbackCount.toDouble() / ageDays
    }

    private fun dateOf(raw: String?): LocalDate? = ReleaseDate.parse(raw)

    private const val MIN_AGE_DAYS = 30L

    /** A song with no date is taken to be about three years old: neither new nor ancient. */
    private const val UNKNOWN_AGE_DAYS = 3 * 365L
}
