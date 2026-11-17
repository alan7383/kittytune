package com.alananasss.kittytune.audio.haptics

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType as ComposeHapticFeedbackType
import com.alananasss.kittytune.data.local.PlayerPreferences

enum class AppHapticMode {
    DISABLED,
    ENABLED,
    STRONGER
}

fun VibrationEffect.Composition.composeBoosted(vibrator: Vibrator): VibrationEffect {
    if (HapticUtil.hapticMode.value == AppHapticMode.STRONGER &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)
    ) {
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.6f, 0)
    }
    return compose()
}

/**
 * Centralized haptic feedback utility for KittyTune inspired by Essentials.
 * Provides system-level haptic primitives, custom amplitude curves, and
 * gated Compose haptic interception across the entire app.
 */
object HapticUtil {
    val hapticMode = mutableStateOf(AppHapticMode.ENABLED)
    val isAppHapticsEnabled = mutableStateOf(true)

    /** Gates discrete interactions: taps, buttons, switches, cards, confirmations. */
    val isButtonsHapticsEnabled = mutableStateOf(true)

    /** Gates continuous interactions: sliders, seek bars, equalizer bands. */
    val isSlidersHapticsEnabled = mutableStateOf(true)

    fun boost(scale: Float): Float =
        if (hapticMode.value == AppHapticMode.STRONGER) (scale * 1.6f + 0.2f).coerceAtMost(1f) else scale

    fun initialize(context: Context) {
        val prefs = PlayerPreferences(context)
        applyMode(prefs.getAppHapticMode())
        isButtonsHapticsEnabled.value = prefs.getHapticsButtons()
        isSlidersHapticsEnabled.value = prefs.getHapticsSliders()
    }

    fun applyMode(mode: AppHapticMode) {
        hapticMode.value = mode
        isAppHapticsEnabled.value = mode != AppHapticMode.DISABLED
    }

    fun saveHapticMode(context: Context, mode: AppHapticMode) {
        PlayerPreferences(context).setAppHapticMode(mode)
        if (mode == AppHapticMode.DISABLED) {
            // Acknowledge the tap while the old mode still allows feedback.
            performLightHaptic(context)
            applyMode(mode)
        } else {
            applyMode(mode)
            performUIHaptic(context)
        }
    }

    fun applyButtonsHaptics(enabled: Boolean) {
        isButtonsHapticsEnabled.value = enabled
    }

    fun applySlidersHaptics(enabled: Boolean) {
        isSlidersHapticsEnabled.value = enabled
    }

    fun saveButtonsHaptics(context: Context, enabled: Boolean) {
        PlayerPreferences(context).setHapticsButtons(enabled)
        applyButtonsHaptics(enabled)
    }

    fun saveSlidersHaptics(context: Context, enabled: Boolean) {
        PlayerPreferences(context).setHapticsSliders(enabled)
        applySlidersHaptics(enabled)
    }

    private fun buttonsEnabled(): Boolean =
        isAppHapticsEnabled.value && isButtonsHapticsEnabled.value

    private fun slidersEnabled(): Boolean =
        isAppHapticsEnabled.value && isSlidersHapticsEnabled.value

    fun gate(context: Context, delegate: HapticFeedback): HapticFeedback =
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: ComposeHapticFeedbackType) {
                if (!buttonsEnabled()) return
                if (hapticMode.value == AppHapticMode.STRONGER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    performCustomHaptic(context, composeStrength(hapticFeedbackType))
                } else {
                    delegate.performHapticFeedback(hapticFeedbackType)
                }
            }
        }

    private fun composeStrength(type: ComposeHapticFeedbackType): Float =
        when (type) {
            ComposeHapticFeedbackType.LongPress -> 0.7f
            ComposeHapticFeedbackType.TextHandleMove -> 0.3f
            ComposeHapticFeedbackType.SegmentFrequentTick -> 0.25f
            ComposeHapticFeedbackType.SegmentTick -> 0.35f
            ComposeHapticFeedbackType.GestureThresholdActivate -> 0.6f
            ComposeHapticFeedbackType.GestureEnd -> 0.5f
            ComposeHapticFeedbackType.Confirm -> 0.7f
            ComposeHapticFeedbackType.Reject -> 0.85f
            ComposeHapticFeedbackType.ToggleOn, ComposeHapticFeedbackType.ToggleOff -> 0.5f
            else -> 0.5f
        }

    fun getVibrator(context: Context): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun vibrateWithTouchAttributes(vibrator: Vibrator, effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attrs = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_TOUCH)
                .build()
            vibrator.vibrate(effect, attrs)
        } else {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .build()
            vibrator.vibrate(effect, attrs)
        }
    }

    private fun playStronger(context: Context, strength: Float): Boolean {
        if (hapticMode.value != AppHapticMode.STRONGER || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        performCustomHaptic(context, strength)
        return true
    }

    /** Standard UI interaction haptic (buttons, tab clicks, items) */
    fun performUIHaptic(context: Context) {
        if (!buttonsEnabled()) return
        if (playStronger(context, 0.5f)) return
        performCustomHaptic(context, 0.4f)
    }

    fun performUIHaptic(view: View) {
        if (!buttonsEnabled()) return
        if (playStronger(view.context, 0.5f)) return
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** Light tick (chips, small buttons, subtle feedback) */
    fun performLightHaptic(context: Context) {
        if (!buttonsEnabled()) return
        if (playStronger(context, 0.3f)) return
        performCustomHaptic(context, 0.2f)
    }

    fun performLightHaptic(view: View) {
        if (!buttonsEnabled()) return
        if (playStronger(view.context, 0.3f)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    /** Medium click (toggles, cards, selection) */
    fun performMediumHaptic(context: Context) {
        if (!buttonsEnabled()) return
        if (playStronger(context, 0.6f)) return
        performCustomHaptic(context, 0.5f)
    }

    fun performMediumHaptic(view: View) {
        if (!buttonsEnabled()) return
        if (playStronger(view.context, 0.6f)) return
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    /** Heavy virtual key punch (play/pause toggle, hero buttons) */
    fun performHeavyHaptic(context: Context) {
        if (!buttonsEnabled()) return
        if (playStronger(context, 0.85f)) return
        performCustomHaptic(context, 0.75f)
    }

    fun performHeavyHaptic(view: View) {
        if (!buttonsEnabled()) return
        if (playStronger(view.context, 0.85f)) return
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** Slider scrubbing / continuous tick */
    fun performSliderHaptic(context: Context) {
        if (!slidersEnabled()) return
        if (playStronger(context, 0.25f)) return
        performCustomHaptic(context, 0.15f)
    }

    fun performSliderHaptic(view: View) {
        if (!slidersEnabled()) return
        if (playStronger(view.context, 0.25f)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    /** Confirmation haptic (like, add to playlist, save, download) */
    fun performConfirmHaptic(context: Context) {
        if (!buttonsEnabled()) return
        if (playStronger(context, 0.7f)) return
        performDoubleHaptic(context)
    }

    fun performConfirmHaptic(view: View) {
        if (!buttonsEnabled()) return
        if (playStronger(view.context, 0.7f)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }

    /** Rejection haptic (delete, cancel, error) */
    fun performRejectHaptic(context: Context) {
        if (!buttonsEnabled()) return
        if (playStronger(context, 0.85f)) return
        performCustomHaptic(context, 0.85f)
    }

    fun performRejectHaptic(view: View) {
        if (!buttonsEnabled()) return
        if (playStronger(view.context, 0.85f)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    /** Micro tick for very light affordances such as chips or toggle rails. */
    fun performMicroHaptic(context: Context) {
        if (!buttonsEnabled()) return
        performCustomHaptic(context, 0.12f)
    }

    fun performMicroHaptic(view: View) {
        if (!buttonsEnabled()) return
        performCustomHaptic(view.context, 0.12f)
    }

    /** Crossing a drag threshold: dismissing a sheet, triggering a swipe action. */
    fun performGestureThresholdHaptic(context: Context) {
        if (!buttonsEnabled()) return
        if (playStronger(context, 0.6f)) return
        performCustomHaptic(context, 0.6f)
    }

    fun performGestureThresholdHaptic(view: View) {
        if (!buttonsEnabled()) return
        if (playStronger(view.context, 0.6f)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }

    /** A crisp, single key-press impact for primary actions. */
    fun performStrongTickHaptic(context: Context) {
        if (!buttonsEnabled()) return
        if (playStronger(context, 0.9f)) return
        val vibrator = getVibrator(context)
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)
        ) {
            val effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
                .composeBoosted(vibrator)
            vibrateWithTouchAttributes(vibrator, effect)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrateWithTouchAttributes(vibrator, VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(20, 255))
        }
    }

    fun performStrongTickHaptic(view: View) {
        performStrongTickHaptic(view.context)
    }

    /** Alias kept close to Essentials' naming for virtual-key style actions. */
    fun performVirtualKeyHaptic(context: Context) = performHeavyHaptic(context)

    fun performVirtualKeyHaptic(view: View) = performHeavyHaptic(view)

    fun performDoubleHaptic(view: View) {
        performDoubleHaptic(view.context)
    }

    fun performDoubleHaptic(context: Context) {
        if (!buttonsEnabled()) return
        val vibrator = getVibrator(context)
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)
        ) {
            val composition = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, boost(0.7f))
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, boost(1.0f), 60)
                .composeBoosted(vibrator)
            vibrateWithTouchAttributes(vibrator, composition)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrateWithTouchAttributes(vibrator, VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 20, 50, 25)
            val amplitudes = intArrayOf(0, 160, 0, 220)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        }
    }

    fun performCustomHaptic(context: Context, strength: Float) {
        if (!isAppHapticsEnabled.value) return
        val vibrator = getVibrator(context)
        if (!vibrator.hasVibrator()) return

        val clampedStrength = strength.coerceIn(0.01f, 1.0f)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val primitiveStrength = boost(clampedStrength)
                val primitive = if (clampedStrength < 0.35f &&
                    vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)
                ) {
                    VibrationEffect.Composition.PRIMITIVE_LOW_TICK
                } else if (clampedStrength < 0.75f &&
                    vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)
                ) {
                    VibrationEffect.Composition.PRIMITIVE_TICK
                } else if (vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
                    VibrationEffect.Composition.PRIMITIVE_CLICK
                } else {
                    null
                }

                if (primitive != null) {
                    val effect = VibrationEffect.startComposition()
                        .addPrimitive(primitive, primitiveStrength)
                        .composeBoosted(vibrator)
                    vibrateWithTouchAttributes(vibrator, effect)
                    return
                }
            } catch (_: Exception) {}
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val stronger = hapticMode.value == AppHapticMode.STRONGER
            val effect = if (stronger) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
            } else if (clampedStrength < 0.5f) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            } else {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            }
            vibrateWithTouchAttributes(vibrator, effect)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (vibrator.hasAmplitudeControl()) {
                val amplitude = (boost(clampedStrength) * boost(clampedStrength) * 255).toInt().coerceIn(1, 255)
                val effect = VibrationEffect.createOneShot(12, amplitude)
                vibrator.vibrate(effect)
            } else {
                val effect = VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(effect)
            }
        }
    }
}

/** Extension function on View to route haptics through HapticUtil */
fun View.performAppHaptic(constant: Int = HapticFeedbackConstants.KEYBOARD_TAP) {
    if (!HapticUtil.isAppHapticsEnabled.value) return
    when (constant) {
        HapticFeedbackConstants.CLOCK_TICK -> HapticUtil.performSliderHaptic(this)
        HapticFeedbackConstants.LONG_PRESS -> HapticUtil.performHeavyHaptic(this)
        HapticFeedbackConstants.CONTEXT_CLICK -> HapticUtil.performMediumHaptic(this)
        HapticFeedbackConstants.VIRTUAL_KEY -> HapticUtil.performHeavyHaptic(this)
        HapticFeedbackConstants.CONFIRM -> HapticUtil.performConfirmHaptic(this)
        HapticFeedbackConstants.REJECT -> HapticUtil.performRejectHaptic(this)
        else -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                (constant == HapticFeedbackConstants.SEGMENT_TICK || constant == HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
            ) {
                HapticUtil.performSliderHaptic(this)
            } else {
                HapticUtil.performUIHaptic(this)
            }
        }
    }
}
