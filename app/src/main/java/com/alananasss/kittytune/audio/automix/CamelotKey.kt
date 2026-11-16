/**
 * Developed by Jason-Marshall Fastner, Germany <jasonfastner@protonmail.com>
 * Questions, feedback, or beat-matching debates? Feel free to reach out via email!
 *
 * Note: Cats always land on their feet, and with this engine, your transitions will too.
 */
package com.alananasss.kittytune.audio.automix

import kotlin.math.abs

/**
 * The Camelot wheel, typed — the harmonic-mixing notation the whole Mix feature speaks.
 *
 * Every key is a number 1-12 on the wheel plus a mode letter: `A` for minor, `B` for major
 * (`8A` = A minor, `11B` = F-sharp major). Neighbours on the wheel share enough notes to mix,
 * which is what [harmonicDistanceTo] measures and what the badges, the smart sort and the
 * engine's Auto decision all consume via [KeyHarmony].
 *
 * Two distance measures exist on purpose:
 * - [harmonicDistanceTo]: wheel steps only (mode ignored) — used by the existing Mix code.
 * - [distanceTo]: DJ-style distance that also counts the mode switch (see its doc).
 */
data class CamelotKey(val number: Int, val mode: CamelotMode) {

    init {
        require(number in 1..12) { "Camelot number must be in 1..12, got: $number" }
    }

    /** The canonical two-character code, e.g. `8A`, `12B`. */
    val code: String get() = "$number${mode.letter}"

    /** Same text as [code] — the label the Mix screens print inside the key badge. */
    val formatted: String get() = code

    val letter: Char get() = mode.letter
    val isMinor: Boolean get() = mode == CamelotMode.MINOR
    val isMajor: Boolean get() = !isMinor

    /** Pitch class of the tonic: 0=C, 1=C#, 2=D, 3=D#, 4=E, 5=F, 6=F#, 7=G, 8=G#, 9=A, 10=A#, 11=B. */
    val pitchClass: Int
        get() = ((if (isMinor) 8 else 11) + (number - 1) * 7) % 12 // one wheel step = a fifth (+7)

    /** Standard musical name, e.g. `Abm`, `C`. */
    val standardName: String
        get() = (if (isMinor) MINOR_NAMES else MAJOR_NAMES)[number - 1]

    /** Enharmonic alternative name, e.g. `G#m`, `C`. */
    val altName: String
        get() = (if (isMinor) MINOR_ALT_NAMES else MAJOR_ALT_NAMES)[number - 1]

    /**
     * The wheel colour the playlist rows and track cards paint the key badge with — the
     * Spotify-Mix palette, anchored so musically-adjacent cells (11 = deep blue,
     * 9 = purple, 8 = dark red) read as neighbours at a glance.
     */
    val badgeColor: Long get() = when (number) {
        1 -> 0xFF4A90E2 // B/Abm - Aqua Blue
        2 -> 0xFF357ABD // F#/Ebm - Sky Blue
        3 -> 0xFF2ECC71 // C#/Bbm - Green
        4 -> 0xFF27AE60 // Ab/Fm - Forest Green
        5 -> 0xFFF1C40F // Eb/Cm - Yellow
        6 -> 0xFFE67E22 // Bb/Gm - Orange
        7 -> 0xFFE74C3C // F/Dm - Red
        8 -> 0xFFC0392B // C/Am - Dark Red
        9 -> 0xFF9B59B6 // G/Em - Purple
        10 -> 0xFF8E44AD // D/Bm - Dark Purple
        11 -> 0xFF2980B9 // A/F#m - Deep Blue
        12 -> 0xFF16A085 // E/Dbm - Teal
        else -> 0xFF7F8C8D
    }

    /**
     * Steps around the wheel to [other]: `0` for the same segment (relative major/minor hold
     * the same notes), `1` for the adjacent segment, and so on — always the short way round
     * (12B → 1B is one step, not eleven). Mode is ignored.
     */
    fun harmonicDistanceTo(other: CamelotKey): Int {
        val d = abs(number - other.number)
        return minOf(d, 12 - d)
    }

    /** Circular difference on the 12-hour wheel (0..6). Same as [harmonicDistanceTo]. */
    fun wheelDiff(other: CamelotKey): Int = harmonicDistanceTo(other)

    /**
     * Whether the two keys mix, the DJ rulebook: the same segment, ±1 step (with or without a
     * mode change), or the classic same-letter +2 "energy boost".
     */
    fun isCompatibleWith(other: CamelotKey): Boolean {
        val step = harmonicDistanceTo(other)
        val sameMode = mode == other.mode
        return step == 0 || step == 1 || (sameMode && step == 2)
    }

    /**
     * Harmonic distance that also counts the mode switch:
     *
     * - 0 = identical key (8A -> 8A)
     * - 1 = adjacent on the wheel (8A -> 7A / 9A, 12A -> 1A) or relative major/minor (8A -> 8B)
     * - 2 = diagonal (8A -> 7B) or two-step energy boost (8A -> 10A)
     * - >= 3 = tonal dissonance
     */
    fun distanceTo(other: CamelotKey): Int {
        if (this == other) return 0
        val numDiff = wheelDiff(other)
        val letterDiff = if (mode == other.mode) 0 else 1

        return when {
            numDiff == 0 && letterDiff == 1 -> 1 // relative major / minor
            numDiff == 1 && letterDiff == 0 -> 1 // direct neighbour
            numDiff == 1 && letterDiff == 1 -> 2 // diagonal neighbour
            numDiff == 2 && letterDiff == 0 -> 2 // energy modulation (+2 steps)
            else -> numDiff + letterDiff
        }
    }

    /** True if seamless mixing is possible ([distanceTo] <= 1). */
    fun isHarmonicWith(other: CamelotKey): Boolean = distanceTo(other) <= 1

    fun isExactMatch(other: CamelotKey): Boolean = this == other

    fun isRelativeMajorMinor(other: CamelotKey): Boolean =
        number == other.number && mode != other.mode

    fun isAdjacentOnWheel(other: CamelotKey): Boolean =
        mode == other.mode && wheelDiff(other) == 1

    /** Normalized compatibility score from 0.0f to 1.0f. */
    fun compatibilityScore(other: CamelotKey): Float = when (distanceTo(other)) {
        0 -> 1.0f
        1 -> if (isRelativeMajorMinor(other)) 0.95f else 0.90f
        2 -> 0.65f
        3 -> 0.30f
        else -> 0.0f
    }

    companion object {
        private val MINOR_NAMES = arrayOf("Abm", "Ebm", "Bbm", "Fm", "Cm", "Gm", "Dm", "Am", "Em", "Bm", "F#m", "Dbm")
        private val MINOR_ALT_NAMES = arrayOf("G#m", "D#m", "A#m", "Fm", "Cm", "Gm", "Dm", "Am", "Em", "Bm", "Gbm", "C#m")
        private val MAJOR_NAMES = arrayOf("B", "F#", "Db", "Ab", "Eb", "Bb", "F", "C", "G", "D", "A", "E")
        private val MAJOR_ALT_NAMES = arrayOf("B", "Gb", "C#", "G#", "D#", "A#", "F", "C", "G", "D", "A", "E")

        /** All 24 keys: 1A..12A, then 1B..12B. */
        val ALL: List<CamelotKey> by lazy {
            CamelotMode.entries.flatMap { m -> (1..12).map { CamelotKey(it, m) } }
        }

        /**
         * Parses `8A` / `12b` / `11B` — the plain Camelot notation only; sharps, flats and
         * full key names are not accepted here (use [fromKeyString] for those).
         */
        fun parse(code: String?): CamelotKey? {
            if (code == null) return null
            val text = code.trim().uppercase()
            if (text.length !in 2..3) return null
            val number = text.dropLast(1).toIntOrNull() ?: return null
            if (number !in 1..12) return null
            return when (text.last()) {
                'A' -> CamelotKey(number, CamelotMode.MINOR)
                'B' -> CamelotKey(number, CamelotMode.MAJOR)
                else -> null
            }
        }

        /** Resolves a key from tonic pitch class (0..11) and scale type. */
        fun fromPitchClass(pitchClass: Int, isMinor: Boolean): CamelotKey? {
            val pc = ((pitchClass % 12) + 12) % 12
            return ALL.find { it.pitchClass == pc && it.isMinor == isMinor }
        }

        /**
         * Parses any raw key string: Camelot codes ("8A", "12b") or musical keys
         * ("Am", "A minor", "Amin", "C Major", "C", "Cmaj", "F#m", "Db", "B♭m", "G# minor").
         */
        fun fromKeyString(raw: String?): CamelotKey? {
            if (raw.isNullOrBlank()) return null
            val clean = raw.trim().uppercase()
                .replace("♭", "B")
                .replace("♯", "#")

            // 1. Plain Camelot code
            parse(clean)?.let { return it }

            // 2. Musical notation
            var s = clean
                .replace("MAJOR", "MAJ")
                .replace("MINOR", "M")
                .replace("MIN", "M")
                .replace("DUR", "MAJ")
                .replace("MOLL", "M")
                .replace(" ", "")

            val isMinor = s.endsWith("M") && !s.endsWith("MAJ")
            s = if (isMinor) s.removeSuffix("M") else s.removeSuffix("MAJ")

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

/** The wheel's mode letter: minor (`A`) or major (`B`). */
enum class CamelotMode(val letter: Char) {
    MINOR('A'),
    MAJOR('B'),
}
