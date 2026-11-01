package com.alananasss.kittytune.data.stats

/**
 * A "Music Passport": the listener as a traveller through music, built from their whole listening
 * history. It reads like a real passport — a holder, a number, citizenships (the genres they belong
 * to), visas (the platforms their finds came from) and stamps (achievements earned) — and is made
 * to be rendered as one shareable image.
 *
 * Everything here is already resolved to display values by the builder, so the renderer draws it
 * without reaching back into the data layer. Computed from the local history, so it works signed out
 * exactly as the recaps do.
 */
data class MusicPassport(
    val holderName: String,
    val passportNo: String,
    /** The first listen on record, the passport's date of issue. */
    val issuedAtMs: Long,
    val avatarUrl: String?,
    /** Favourite genres, the strongest first — the listener's musical "nationalities". */
    val citizenships: List<Citizenship>,
    /** Where the listener's finds came from, by platform — their "visas". */
    val visas: List<Visa>,
    /** Achievements earned, drawn as inked entry stamps. */
    val stamps: List<Stamp>,
    val totalMinutes: Long,
    val tracks: Int,
    val artists: Int,
    val longestStreakDays: Int,
    /** A short traveller archetype, e.g. "Night Voyager". */
    val travellerClass: String,
    /** The accent colour the card is themed on, as an ARGB int. */
    val accentArgb: Int,
) {
    val hasData: Boolean get() = totalMinutes > 0 || tracks > 0

    data class Citizenship(val genre: String, val sharePercent: Int)

    /** [since] is the first listen on that platform, "12 MAR 2025". */
    data class Visa(val platform: String, val discoveries: Int, val since: String)

    /** [date] is when the achievement was earned, "31 OCT 2026", or blank when unknown. */
    data class Stamp(val label: String, val date: String)
}
