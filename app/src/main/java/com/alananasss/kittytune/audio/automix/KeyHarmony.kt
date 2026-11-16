package com.alananasss.kittytune.audio.automix

// Which keys mix, by the Camelot wheel (ported from desktop, PR #77).
//
// The notation and the compatibility rule live in [CamelotKey]; this object is the thin
// decision layer the engine and the analyser call with raw `"8A"`-style codes.
//
// The moves a DJ can make, all verified against the wheel:
//  - **SAME** - same number, either letter (relative major/minor holds the same notes);
//  - **NEIGHBOUR** - ±1 number in the same mode (a fourth/fifth, the classic harmonic move);
//  - **DIAGONAL** - ±1 number with a letter change (six of seven notes shared);
//  - **LIFT** - +2 numbers in the same mode (the "+2 semitones" energy boost);
//  - everything else **CLASH**.
//
// Five wheel steps (same mode) is a ±1 *semitone* transposition - only two of seven scale
// degrees shared - and used to be classified as a "+7 semitones super boost". That was a
// wheel-steps/semitones mix-up: a fifth up (+7 st) is one wheel step, i.e. a NEIGHBOUR.
// The bogus rule made genuinely clashing pairs mix as if they fitted.
internal object KeyHarmony {

    enum class Fit { SAME, NEIGHBOUR, DIAGONAL, LIFT, CLASH, UNKNOWN }

    fun between(outCode: String?, inCode: String?): Fit {
        val out = CamelotKey.parse(outCode) ?: return Fit.UNKNOWN
        val into = CamelotKey.parse(inCode) ?: return Fit.UNKNOWN
        val step = out.harmonicDistanceTo(into)
        val sameLetter = out.mode == into.mode
        return when {
            step == 0 -> Fit.SAME // relative major and minor hold the same notes
            step == 1 -> if (sameLetter) Fit.NEIGHBOUR else Fit.DIAGONAL
            sameLetter && step == 2 -> Fit.LIFT // the classic +2 semitone "energy boost"
            else -> Fit.CLASH
        }
    }
}
