package com.alananasss.kittytune.audio.haptics

/**
 * What a single haptic event is supposed to say.
 *
 * Mapping every beat to the same thump wastes a motor that can do much more, but variety for its
 * own sake is just noise on the wrist. Each gesture here exists because the music does something
 * different, and the engine already knows which: the beat grid gives bar position, the dynamics
 * give trend, and the detector gives which drum fired.
 */
enum class HapticGesture {
    /** Beat 1. The weight a listener counts from. */
    DOWNBEAT,

    /** A kick that is not the downbeat: present and articulate, without the mass. */
    BEAT,

    /** A beat barely above the floor - texture rather than an event. */
    GHOST,

    /** Snare or clap. A crack, not a weight. */
    BACKBEAT,

    /** Energy has been climbing for a while. A rising sensation, felt as tension. */
    BUILD_UP,

    /** The moment a build-up resolves. Everything the motor has. */
    IMPACT,
}

/**
 * Chooses the gesture for an event from what the engine already knows.
 *
 * Pure so it can be reasoned about and tested; the mapping to actual device primitives is
 * [HapticPrimitivePalette]'s job and depends on what the hardware supports.
 */
object HapticGestureSelector {

    /**
     * @param isKick false for snare/clap.
     * @param isDownbeat null when the bar position was not trusted - then no gesture claims it.
     * @param intensity 0..1 after contrast shaping.
     * @param trend what the energy has been doing over the last several seconds.
     * @param atPhraseBoundary true on a 16-beat line, where a build-up resolves.
     */
    fun select(
        isKick: Boolean,
        isDownbeat: Boolean?,
        intensity: Float,
        trend: HapticDynamics.Trend,
        atPhraseBoundary: Boolean,
    ): HapticGesture {
        if (!isKick) return HapticGesture.BACKBEAT

        // A drop is not merely a loud beat - it is a loud beat that a climb was leading to. Both
        // halves are required, otherwise every heavy track would be one long impact.
        if (atPhraseBoundary && intensity >= IMPACT_INTENSITY && trend != HapticDynamics.Trend.FALLING) {
            return HapticGesture.IMPACT
        }

        // Tension belongs on the way up, not at the top. At near-full intensity a rise would
        // feel weaker than a thud despite the passage being louder, so weight takes over before
        // the ceiling. Bar lines only: a rise on every kick is continuous motion, which is the
        // drone this design exists to avoid.
        if (trend == HapticDynamics.Trend.RISING &&
            isDownbeat == true &&
            intensity >= BUILD_UP_INTENSITY &&
            intensity < IMPACT_INTENSITY
        ) {
            return HapticGesture.BUILD_UP
        }

        if (intensity < GHOST_INTENSITY) return HapticGesture.GHOST
        return if (isDownbeat == true) HapticGesture.DOWNBEAT else HapticGesture.BEAT
    }

    /** A drop has to be near the top of the range to be one. */
    const val IMPACT_INTENSITY = 0.85f

    /** Below this a rise would be felt as a stutter rather than tension. */
    const val BUILD_UP_INTENSITY = 0.35f

    /** Below this the beat is texture; giving it weight would flatten the bar. */
    const val GHOST_INTENSITY = 0.3f
}
