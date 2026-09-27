package com.alananasss.kittytune.audio.haptics

/**
 * Decides how much of the continuous bass rumble to keep.
 *
 * There are two haptic paths. Transients fire on kicks and snares; the continuous path lays the
 * bass envelope onto the motor as a running vibration, re-issued every few tens of milliseconds.
 * The second one exists because without knowing where the beats are, a drone is the only way to
 * convey anything at all.
 *
 * Once the beat grid is known that reasoning inverts. Discrete pulses on actual beats carry more
 * information than a continuous level, and a phone that has been buzzing steadily for thirty
 * seconds has nothing left with which to mark a drop — the constant vibration is not just
 * uninformative, it is what makes the impact impossible.
 *
 * So the same control that asks for contrast over loudness also decides this: with a trusted
 * grid, more contrast means less drone and more event. Without a grid the drone stays, because
 * then it is all there is.
 */
object ContinuousHapticPolicy {

    /**
     * @param hasTrustedGrid the track has an analysed beat grid worth pulsing against.
     * @param contrast the user's "impact" setting, 0..1.
     * @return factor to scale the continuous amplitude by; 0 means skip it entirely.
     */
    fun rumbleScale(hasTrustedGrid: Boolean, contrast: Float): Float {
        if (!hasTrustedGrid) return 1f
        val amount = contrast.coerceIn(0f, 1f)
        val scale = 1f - amount
        // Below this the rumble is too weak to feel but still wakes the motor every few tens of
        // milliseconds, which costs current for nothing.
        return if (scale < MIN_USEFUL_SCALE) 0f else scale
    }

    /** True when the continuous path should not run at all. */
    fun isSuppressed(hasTrustedGrid: Boolean, contrast: Float): Boolean =
        rumbleScale(hasTrustedGrid, contrast) <= 0f

    const val MIN_USEFUL_SCALE = 0.15f
}
