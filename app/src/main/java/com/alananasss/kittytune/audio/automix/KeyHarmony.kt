package com.alananasss.kittytune.audio.automix

import kotlin.math.abs

/**
 * How well two keys sit together, on the Camelot wheel DJs mix by.
 *
 * Two tracks blend when their keys are the same, a step apart on the wheel, or the relative major or minor of each
 * other: the notes of one are then nearly the notes of the other. Anything else fights while both are heard, which is
 * what a long overlap of two unrelated keys sounds like.
 */
internal object KeyHarmony {

    enum class Fit {
        /** The same key, or the same notes: relative major and minor. */
        SAME,

        /** A step round the wheel: a few notes differ, and they pass unnoticed in a blend. */
        NEIGHBOUR,

        /** Two steps up, the "energy boost" mix: it works, but with less margin. */
        LIFT,

        /** Unrelated keys. */
        CLASH,

        /** A key of one of them could not be told. */
        UNKNOWN,
    }

    fun between(outCode: String?, inCode: String?): Fit {
        val out = parse(outCode) ?: return Fit.UNKNOWN
        val into = parse(inCode) ?: return Fit.UNKNOWN
        val step = wheelDistance(out.number, into.number)
        val sameLetter = out.isMinor == into.isMinor
        return when {
            sameLetter && step == 0 -> Fit.SAME
            !sameLetter && step == 0 -> Fit.SAME
            sameLetter && step == 1 -> Fit.NEIGHBOUR
            sameLetter && step == 2 -> Fit.LIFT
            else -> Fit.CLASH
        }
    }

    private data class Code(val number: Int, val isMinor: Boolean)

    private fun parse(code: String?): Code? {
        if (code == null || code.length < 2) return null
        val number = code.dropLast(1).toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        return when (code.last()) {
            'A' -> Code(number, isMinor = true)
            'B' -> Code(number, isMinor = false)
            else -> null
        }
    }

    /** Steps between two positions on the twelve-hour wheel, the short way round. */
    private fun wheelDistance(a: Int, b: Int): Int {
        val d = abs(a - b)
        return minOf(d, 12 - d)
    }
}
