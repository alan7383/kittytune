/**
 * Developed by Jason-Marshall Fastner, Germany <jasonfastner@protonmail.com>
 * Questions, feedback, or beat-matching debates? Feel free to reach out via email!
 * 
 * Note: Cats always land on their feet, and with this engine, your transitions will too.
 */
package com.alananasss.kittytune.ui.player

import kotlin.math.abs

/**
 * Represents the 24 musical keys in the Camelot Wheel system used in professional DJ harmonic mixing.
 *
 * The system maps musical tonalities to positions from 1 to 12 with:
 * - 'A' denoting Minor scales
 * - 'B' denoting Major scales
 *
 * Harmonic transitions:
 * - Distance 0: Identical key (seamless energy match)
 * - Distance 1: Adjacent key (+1 / -1 on wheel) or Relative Major/Minor (e.g. 8A <-> 8B)
 * - Distance 2: Diagonal shift or two-step energy lift (+2 semitones / +2 on wheel)
 * - Distance > 2: Disharmonic / tonal dissonance
 */
enum class CamelotKey(
    val code: String,
    val number: Int,
    val letter: Char,
    val standardName: String,
    val altName: String,
    val pitchClass: Int, // 0=C, 1=C#, 2=D, 3=D#, 4=E, 5=F, 6=F#, 7=G, 8=G#, 9=A, 10=A#, 11=B
    val isMinor: Boolean
) {
    KEY_1A("1A", 1, 'A', "Abm", "G#m", 8, true),
    KEY_2A("2A", 2, 'A', "Ebm", "D#m", 3, true),
    KEY_3A("3A", 3, 'A', "Bbm", "A#m", 10, true),
    KEY_4A("4A", 4, 'A', "Fm", "Fm", 5, true),
    KEY_5A("5A", 5, 'A', "Cm", "Cm", 0, true),
    KEY_6A("6A", 6, 'A', "Gm", "Gm", 7, true),
    KEY_7A("7A", 7, 'A', "Dm", "Dm", 2, true),
    KEY_8A("8A", 8, 'A', "Am", "Am", 9, true),
    KEY_9A("9A", 9, 'A', "Em", "Em", 4, true),
    KEY_10A("10A", 10, 'A', "Bm", "Bm", 11, true),
    KEY_11A("11A", 11, 'A', "F#m", "Gbm", 6, true),
    KEY_12A("12A", 12, 'A', "Dbm", "C#m", 1, true),

    KEY_1B("1B", 1, 'B', "B", "B", 11, false),
    KEY_2B("2B", 2, 'B', "F#", "Gb", 6, false),
    KEY_3B("3B", 3, 'B', "Db", "C#", 1, false),
    KEY_4B("4B", 4, 'B', "Ab", "G#", 8, false),
    KEY_5B("5B", 5, 'B', "Eb", "D#", 3, false),
    KEY_6B("6B", 6, 'B', "Bb", "A#", 10, false),
    KEY_7B("7B", 7, 'B', "F", "F", 5, false),
    KEY_8B("8B", 8, 'B', "C", "C", 0, false),
    KEY_9B("9B", 9, 'B', "G", "G", 7, false),
    KEY_10B("10B", 10, 'B', "D", "D", 2, false),
    KEY_11B("11B", 11, 'B', "A", "A", 9, false),
    KEY_12B("12B", 12, 'B', "E", "E", 4, false);

    val isMajor: Boolean get() = !isMinor

    /**
     * Circular difference on the 12-hour Camelot wheel (0..6).
     * Wrapping around 12 to 1 correctly produces a distance of 1.
     */
    fun wheelDiff(other: CamelotKey): Int {
        val diff = abs(this.number - other.number)
        return minOf(diff, 12 - diff)
    }

    /**
     * Calculates the harmonic distance to a subsequent track.
     *
     * 0 = Identical key (e.g. 8A -> 8A)
     * 1 = Adjacent on wheel (e.g. 8A -> 7A or 9A, 12A -> 1A) OR relative major/minor (e.g. 8A -> 8B)
     * 2 = Diagonal shift (e.g. 8A -> 7B) or 2-step energy boost (e.g. 8A -> 10A)
     * >= 3 = Tonal dissonance
     */
    fun distanceTo(other: CamelotKey): Int {
        if (this == other) return 0
        val numDiff = wheelDiff(other)
        val letterDiff = if (this.letter == other.letter) 0 else 1

        return when {
            numDiff == 0 && letterDiff == 1 -> 1 // Relative Major / Minor
            numDiff == 1 && letterDiff == 0 -> 1 // Direct adjacent neighbor
            numDiff == 1 && letterDiff == 1 -> 2 // Diagonal neighbor
            numDiff == 2 && letterDiff == 0 -> 2 // Energy modulation (+2 steps)
            else -> numDiff + letterDiff
        }
    }

    /**
     * Returns true if two keys are considered harmonically compatible for seamless mixing (distance <= 1).
     */
    fun isHarmonicWith(other: CamelotKey): Boolean = distanceTo(other) <= 1

    fun isExactMatch(other: CamelotKey): Boolean = this == other

    fun isRelativeMajorMinor(other: CamelotKey): Boolean =
        this.number == other.number && this.letter != other.letter

    fun isAdjacentOnWheel(other: CamelotKey): Boolean =
        this.letter == other.letter && wheelDiff(other) == 1

    /**
     * Returns a normalized compatibility score from 0.0f to 1.0f.
     */
    fun compatibilityScore(other: CamelotKey): Float {
        return when (distanceTo(other)) {
            0 -> 1.0f
            1 -> if (isRelativeMajorMinor(other)) 0.95f else 0.90f
            2 -> 0.65f
            3 -> 0.30f
            else -> 0.0f
        }
    }

    companion object {
        private val CODE_MAP by lazy { entries.associateBy { it.code } }

        /**
         * Resolves a [CamelotKey] from pitch class (0..11) and scale type.
         */
        fun fromPitchClass(pitchClass: Int, isMinor: Boolean): CamelotKey? {
            val normPc = ((pitchClass % 12) + 12) % 12
            return entries.find { it.pitchClass == normPc && it.isMinor == isMinor }
        }

        /**
         * Parses a raw key string into a [CamelotKey].
         *
         * Handles:
         * - Camelot codes: "8A", "8a", "12B", "1b"
         * - Musical keys: "Am", "A minor", "Amin", "C Major", "C", "Cmaj", "F#m", "Db", "B♭m", "G# minor"
         */
        fun fromKeyString(raw: String?): CamelotKey? {
            if (raw.isNullOrBlank()) return null
            val clean = raw.trim().uppercase()
                .replace("♭", "B")
                .replace("♯", "#")

            // 1. Exact Camelot code
            CODE_MAP[clean]?.let { return it }

            val camelotRegex = Regex("""^([1-9]|1[0-2])([AB])$""")
            camelotRegex.matchEntire(clean)?.let { match ->
                val num = match.groupValues[1]
                val letter = match.groupValues[2]
                return CODE_MAP["$num$letter"]
            }

            // 2. Musical notation
            var s = clean
                .replace("MAJOR", "MAJ")
                .replace("MINOR", "M")
                .replace("MIN", "M")
                .replace("DUR", "MAJ")
                .replace("MOLL", "M")
                .replace(" ", "")

            val isMinor = s.endsWith("M") && !s.endsWith("MAJ")
            if (isMinor) {
                s = s.removeSuffix("M")
            } else {
                s = s.removeSuffix("MAJ")
            }

            val pitchClass = when (s) {
                "C", "B#" -> 0
                "C#", "DB" -> 1
                "D" -> 2
                "D#", "EB" -> 3
                "E", "FB" -> 4
                "F", "E#" -> 5
                "F#", "GB" -> 6
                "G" -> 7
                "G#", "AB" -> 8
                "A" -> 9
                "A#", "BB" -> 10
                "B", "CB" -> 11
                else -> return null
            }

            return fromPitchClass(pitchClass, isMinor)
        }
    }
}
