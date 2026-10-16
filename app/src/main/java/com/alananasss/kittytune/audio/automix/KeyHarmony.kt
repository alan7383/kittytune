package com.alananasss.kittytune.audio.automix

/**
 * Which keys mix, by the Camelot wheel (ported from desktop, PR #77).
 *
 * Every key has a number 1-12 and a letter, A for minor and B for major. Two keys mix when they are near each
 * other on the wheel: the same number, or one step away, whether the letter matches or not (a relative
 * major/minor holds the same notes). Two steps on the same letter is the "energy boost" DJs use. Anything
 * further apart clashes.
 */
internal object KeyHarmony {

    enum class Fit { SAME, NEIGHBOUR, LIFT, CLASH, UNKNOWN }

    private data class ParsedKey(val number: Int, val isMinor: Boolean)

    /** "8A" (A minor), "12B" (E major); anything else — case, sharps, full names — is not the notation. */
    private fun parse(code: String?): ParsedKey? {
        if (code == null || code.length < 2) return null
        val text = code.trim().uppercase()
        if (text.length !in 2..3) return null
        val number = text.dropLast(1).toIntOrNull() ?: return null
        if (number !in 1..12) return null
        return when (text.last()) {
            'A' -> ParsedKey(number, isMinor = true)
            'B' -> ParsedKey(number, isMinor = false)
            else -> null
        }
    }

    fun between(outCode: String?, inCode: String?): Fit {
        val out = parse(outCode) ?: return Fit.UNKNOWN
        val into = parse(inCode) ?: return Fit.UNKNOWN
        val step = wheelDistance(out.number, into.number)
        val sameLetter = out.isMinor == into.isMinor
        return when {
            step == 0 -> Fit.SAME // relative major and minor hold the same notes
            sameLetter && step == 1 -> Fit.NEIGHBOUR
            sameLetter && step == 2 -> Fit.LIFT // the "energy boost" mix
            else -> Fit.CLASH
        }
    }

    /** Distance around the wheel: one step from 12B to 1B, not eleven. */
    private fun wheelDistance(a: Int, b: Int): Int {
        val d = kotlin.math.abs(a - b)
        return minOf(d, 12 - d)
    }
}
