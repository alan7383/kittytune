package com.alananasss.kittytune.data.search

/**
 * The words that make a recording a different version of a song.
 *
 * Two uploads can share a title and an artist and still not be the same recording: a remix, a
 * live take, a sped-up edit or a cover each need to be told apart before search results are
 * called verified. The markers are the same ones the VK importer uses, in the languages its
 * titles are written in, so a remix is recognised no matter which platform spelled it out.
 */
object TrackVersionTags {

    private val MARKERS = mapOf(
        "remix" to "remix", "rmx" to "remix", "ремикс" to "remix",
        "slowed" to "slowed", "slow" to "slowed", "слоу" to "slowed", "замедленная" to "slowed",
        "sped" to "spedup", "speed" to "spedup", "spedup" to "spedup", "спид" to "spedup", "ускоренная" to "spedup",
        "nightcore" to "nightcore",
        "acoustic" to "acoustic", "акустика" to "acoustic", "акустическая" to "acoustic",
        "live" to "live", "концерт" to "live", "лайв" to "live",
        "instrumental" to "instrumental", "минус" to "instrumental", "минусовка" to "instrumental",
        "karaoke" to "instrumental", "караоке" to "instrumental",
        "cover" to "cover", "кавер" to "cover",
        "reverb" to "reverb", "8d" to "8d",
        "mashup" to "mashup", "мэшап" to "mashup",
        "extended" to "extended",
    )

    /**
     * The version markers in [text].
     *
     * "Sped Up" is two words, so "sped" alone would read as a sped-up version even in a title
     * such as "Speed of Sound". A bare "speed"/"sped" only counts when the title also spells out
     * "up" (or uses the run-together "spedup"/Cyrillic form).
     */
    fun of(text: String): Set<String> {
        val words = text.lowercase().split(Regex("[^\\p{L}\\p{Nd}]+")).filter { it.isNotBlank() }
        val found = words.mapNotNull { MARKERS[it] }.toMutableSet()
        if ("spedup" in found && "up" !in words && "spedup" !in words && "спид" !in words) {
            found.remove("spedup")
        }
        return found
    }

    /** True when the two titles name different kinds of recording. */
    fun conflict(first: Set<String>, second: Set<String>): Boolean = first != second
}
