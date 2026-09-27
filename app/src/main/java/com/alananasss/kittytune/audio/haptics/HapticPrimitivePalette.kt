package com.alananasss.kittytune.audio.haptics

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Picks haptic primitives for a beat, on devices whose motor can play them.
 *
 * A linear resonant actuator driven by `createOneShot` gets a rectangular amplitude envelope and
 * rings out however it likes. Primitives are tuned per device by the vendor, so `THUD` on a
 * Pixel is a modelled low thump rather than a buzz of the same length — which is the difference
 * between feeling a kick drum and feeling a notification.
 *
 * The choice depends on tempo, not just on the drum. `THUD` runs 300 ms; at 175 BPM a beat is
 * 343 ms, so a thud on every kick would still be playing when the next one is due, and the bar
 * turns into one continuous smear. Fast material therefore gets the short primitives, and the
 * weight has to come from amplitude instead of duration.
 */
class HapticPrimitivePalette(private val vibrator: Vibrator) {

    /** Whether this device can compose primitives at all. Checked once; it cannot change. */
    val isSupported: Boolean by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@lazy false
        try {
            vibrator.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_TICK,
            )
        } catch (_: Throwable) {
            false
        }
    }

    private val hasThud: Boolean by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@lazy false
        try {
            vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * @param amplitude 0..1, already shaped by [HapticDynamics].
     * @param beatPeriodMs beat length, or 0 when the tempo is unknown — in which case the short
     *   primitives are used, because guessing wrong towards "long" is the audible mistake.
     * @return an effect, or null when primitives are unavailable and the caller should fall back.
     */
    /** Whether the rise/fall primitives are available for build-up gestures. */
    private val hasShapes: Boolean by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@lazy false
        try {
            vibrator.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
            )
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Builds the effect for a gesture.
     *
     * @param amplitude 0..1, already shaped by [HapticDynamics].
     * @param beatPeriodMs beat length, or 0 when tempo is unknown - then the short primitives
     *   are used, because guessing wrong towards "long" is the audible mistake.
     * @return null when primitives are unavailable and the caller should fall back.
     */
    fun build(gesture: HapticGesture, amplitude: Float, beatPeriodMs: Double): VibrationEffect? {
        if (!isSupported || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val scale = amplitude.coerceIn(0.05f, 1f)

        return try {
            val c = VibrationEffect.startComposition()
            when (gesture) {
                HapticGesture.IMPACT, HapticGesture.DOWNBEAT -> {
                    // A thud runs 300 ms - at 160 BPM that is 80 % of a beat, so one per kick
                    // would smear the bar into a drone. It fits comfortably inside a *bar*
                    // though, and beat 1 is where a listener expects the weight.
                    val availableMs = beatPeriodMs * BEATS_PER_BAR
                    if (hasThud && availableMs > THUD_DURATION_MS / THUD_BEAT_FRACTION) {
                        c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, scale)
                    } else {
                        c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, scale)
                        c.addPrimitive(
                            VibrationEffect.Composition.PRIMITIVE_TICK,
                            scale * TICK_TAIL_SCALE,
                            TICK_TAIL_DELAY_MS,
                        )
                    }
                }
                HapticGesture.BEAT -> {
                    c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, scale)
                    if (scale > TICK_TAIL_THRESHOLD) {
                        c.addPrimitive(
                            VibrationEffect.Composition.PRIMITIVE_TICK,
                            scale * TICK_TAIL_SCALE,
                            TICK_TAIL_DELAY_MS,
                        )
                    }
                }
                // Texture, not an event. A low tick is 12 ms and stays out of the way.
                HapticGesture.GHOST ->
                    c.addPrimitive(
                        if (hasShapes) VibrationEffect.Composition.PRIMITIVE_LOW_TICK
                        else VibrationEffect.Composition.PRIMITIVE_TICK,
                        scale * GHOST_SCALE,
                    )
                // A crack, not a weight.
                HapticGesture.BACKBEAT ->
                    c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, scale)
                // Tension: a rising sensation is the one thing a flat pulse cannot express.
                HapticGesture.BUILD_UP -> {
                    if (hasShapes) {
                        c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, scale)
                    } else {
                        c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, scale * 0.5f)
                        c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, scale, RISE_FALLBACK_DELAY_MS)
                    }
                }
            }
            c.compose()
        } catch (_: Throwable) {
            // A vendor can advertise a primitive and still reject a composition; falling back is
            // always possible, so this must never take playback down with it.
            null
        }
    }

    companion object {
        /** Documented nominal length of PRIMITIVE_THUD. */
        const val THUD_DURATION_MS = 300.0

        /** A thud may occupy at most this much of a beat before it smears into the next. */
        const val THUD_BEAT_FRACTION = 0.55

        /** Below this amplitude the extra tick is not felt and only costs current. */
        const val TICK_TAIL_THRESHOLD = 0.5f

        const val TICK_TAIL_SCALE = 0.6f
        const val TICK_TAIL_DELAY_MS = 18

        /** A thud has a whole bar to breathe in when it only plays on beat 1. */
        const val BEATS_PER_BAR = 4

        /** Ghost beats are texture; at full scale they would flatten the bar. */
        const val GHOST_SCALE = 0.45f

        /** Without a rise primitive, a tick then a click approximates the shape. */
        const val RISE_FALLBACK_DELAY_MS = 60
    }
}
