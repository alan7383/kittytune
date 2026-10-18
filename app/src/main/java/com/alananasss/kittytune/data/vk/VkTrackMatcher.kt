package com.alananasss.kittytune.data.vk

import com.alananasss.kittytune.data.LyricsMatcher
import kotlin.math.abs
import kotlin.math.max

/**
 * Decides whether a search result is the VK track being imported.
 */
internal object VkTrackMatcher {

    data class Candidate(val title: String, val artist: String, val durationSec: Int)

    /** Below this a candidate is not taken, however it ranks. */
    const val ACCEPT = 0.72f

    /** Words that make a recording a different version of a song, in the languages VK titles use. */
    private val VERSION_MARKERS = mapOf(
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

    fun score(track: VkTrack, candidate: Candidate): Float {
        val titleSim = max(
            LyricsMatcher.similarity(track.title, candidate.title),
            LyricsMatcher.similarity(transliterate(track.title), transliterate(candidate.title)),
        )
        if (titleSim < 0.6f) return 0f

        val artistSim = artistsOf(track).maxOfOrNull { wanted ->
            max(
                LyricsMatcher.similarity(wanted, candidate.artist),
                LyricsMatcher.similarity(transliterate(wanted), transliterate(candidate.artist)),
            )
        } ?: 0f

        val known = track.durationSec > 0 && candidate.durationSec > 0
        val deltaSec = if (known) abs(track.durationSec - candidate.durationSec) else -1
        val durationScore = when {
            !known -> 0.5f
            deltaSec <= 2 -> 1f
            else -> (1f - (deltaSec - 2) / 18f).coerceIn(0f, 1f)
        }

        if (artistSim < 0.4f && (!known || deltaSec > 3)) return 0f

        val sameRecording = titleSim >= 0.99f && known && deltaSec <= 1 &&
            versionsOf("${track.title} ${track.subtitle}") == versionsOf(candidate.title)
        if (sameRecording) return maxOf(ACCEPT, titleSim * 0.5f + artistSim * 0.3f + 0.2f)

        var score = titleSim * 0.5f + artistSim * 0.3f + durationScore * 0.2f
        if (versionsOf("${track.title} ${track.subtitle}") != versionsOf(candidate.title)) score -= 0.3f
        return score
    }

    /** What to search for: the main artists, the title, and the version when it names one. */
    fun query(track: VkTrack): String {
        val artist = track.mainArtists.joinToString(" ").ifBlank { track.artist }
        val version = track.subtitle.takeIf { versionsOf(it).isNotEmpty() }.orEmpty()
        return "$artist ${track.title} $version".replace(Regex("\\s+"), " ").trim()
    }

    private fun artistsOf(track: VkTrack): List<String> =
        (track.mainArtists + track.artist + track.artist.split(',', '&', '/').map { it.trim() })
            .filter { it.isNotBlank() }
            .distinct()

    internal fun versionsOf(text: String): Set<String> {
        val words = text.lowercase().split(Regex("[^\\p{L}\\p{Nd}]+")).filter { it.isNotBlank() }
        val found = words.mapNotNull { VERSION_MARKERS[it] }.toMutableSet()
        if ("spedup" in found && "up" !in words && "spedup" !in words && "спид" !in words) found.remove("spedup")
        return found
    }

    private val CYRILLIC = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "e", 'ж' to "zh",
        'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o",
        'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts",
        'ч' to "ch", 'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu",
        'я' to "ya", 'і' to "i", 'ї' to "yi", 'є' to "ye", 'ґ' to "g",
    )

    internal fun transliterate(text: String): String = buildString {
        for (char in text.lowercase()) append(CYRILLIC[char] ?: char.toString())
    }
}
