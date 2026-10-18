package com.alananasss.kittytune.ui.library

import com.alananasss.kittytune.domain.Track

/**
 * Who a record is by, from its tracks rather than from the account that posted it (desktop PR #77).
 *
 * Artists who share a SoundCloud account post every record under its name, so an album sung by 9mice alone
 * read "by Kai Angel & 9mice". The tracks themselves carry the real credit; when most of them agree on one,
 * that is the record's artist.
 *
 * @return the credit most tracks share, or null when there is none or no majority.
 */
internal fun albumCreditFor(tracks: List<Track>): String? {
    val credits = tracks.map { it.displayArtist.trim() }.filter { it.isNotEmpty() }
    if (credits.isEmpty()) return null
    val (key, count) = credits.groupingBy { it.lowercase() }.eachCount().maxBy { it.value }
    if (count * 2 <= credits.size) return null
    return credits.first { it.lowercase() == key }
}
