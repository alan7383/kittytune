package com.alananasss.kittytune.ui.profile

import com.alananasss.kittytune.R
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * When something came out, written as a date: "9 September", and "9 September 2025" once it is from another year
 * (desktop PR #77).
 *
 * "Out 6 days ago" is fine for last week and hopeless for an artist who has been quiet for years: "out 3 years
 * ago" says nothing about when. The year is left off while it is this year, since then it only adds length.
 */
internal object ReleaseDate {

    /**
     * The release date for display in the app's language, or "" when [raw] is not a date.
     */
    fun text(raw: String?, today: LocalDate = LocalDate.now(), locale: Locale = Locale.getDefault()): String {
        val date = parse(raw) ?: return ""
        return format(date, today, locale)
    }

    /** The date with its month shortened and its year always: "9 сент. 2025". For a pill, where the long one is too wide. */
    fun shortText(raw: String?, locale: Locale = Locale.getDefault()): String {
        val date = parse(raw) ?: return ""
        val pattern = SHORT_PATTERNS[locale.language] ?: DEFAULT_SHORT_PATTERN
        return DateTimeFormatter.ofPattern(pattern, locale).format(date)
    }

    internal fun format(date: LocalDate, today: LocalDate, locale: Locale): String {
        val patterns = PATTERNS[locale.language] ?: DEFAULT_PATTERNS
        val pattern = if (date.year == today.year) patterns.first else patterns.second
        return DateTimeFormatter.ofPattern(pattern, locale).format(date)
    }

    internal fun parse(raw: String?): LocalDate? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        // A bare date, which is what catalogues give.
        runCatching { return LocalDate.parse(text.take(10)) }
        val date = TIMESTAMP_FORMATS.firstNotNullOfOrNull { pattern ->
            runCatching { SimpleDateFormat(pattern, Locale.US).parse(text) }.getOrNull()
        } ?: return null
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
    }

    /** Month and day, and with the year: the order and the punctuation each language writes them in. */
    private val PATTERNS = mapOf(
        "ru" to ("d MMMM" to "d MMMM yyyy"),
        "en" to ("MMMM d" to "MMMM d, yyyy"),
        "de" to ("d. MMMM" to "d. MMMM yyyy"),
        "fr" to ("d MMMM" to "d MMMM yyyy"),
        "hu" to ("MMMM d." to "yyyy. MMMM d."),
        "vi" to ("d MMMM" to "d MMMM, yyyy"),
    )
    private val DEFAULT_PATTERNS = "d MMMM" to "d MMMM yyyy"

    private val SHORT_PATTERNS = mapOf(
        "ru" to "d MMM yyyy",
        "en" to "MMM d, yyyy",
        "de" to "d. MMM yyyy",
        "fr" to "d MMM yyyy",
        "hu" to "yyyy. MMM d.",
        "vi" to "d MMM, yyyy",
    )
    private const val DEFAULT_SHORT_PATTERN = "d MMM yyyy"

    private val TIMESTAMP_FORMATS = listOf(
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy/MM/dd HH:mm:ss Z",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
    )
}
