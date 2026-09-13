package com.alananasss.kittytune.data.cover

/**
 * Single source of truth for ISRC parsing, normalization and validation.
 *
 * ISRC format: CC-XXX-YY-NNNNN
 *   CC    = 2-letter ISO country code
 *   XXX   = 3-character alphanumeric registrant code
 *   YY    = 2-digit reference year
 *   NNNNN = 5-digit designation code
 * Total: 12 alphanumeric characters once dashes/spaces are stripped.
 */
object ProviderIsrc {

    private val ISRC_REGEX = Regex("^[A-Z]{2}[A-Z0-9]{3}\\d{2}\\d{5}$")

    /**
     * Cleans a raw ISRC string:
     *  - uppercases
     *  - strips whitespace, dashes, dots and any other non-alphanumeric characters
     *  - validates the resulting 12-character shape
     */
    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
        return cleaned.takeIf { ISRC_REGEX.matches(it) }
    }

    /** True if [raw] normalizes to a structurally valid ISRC. */
    fun isValid(raw: String?): Boolean = normalize(raw) != null

    /**
     * Extracts and normalizes the first valid ISRC found across candidates.
     */
    fun firstOf(vararg candidates: String?): String? {
        for (candidate in candidates) {
            normalize(candidate)?.let { return it }
        }
        return null
    }
}
