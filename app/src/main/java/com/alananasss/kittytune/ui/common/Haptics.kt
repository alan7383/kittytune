package com.alananasss.kittytune.ui.common

import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import com.alananasss.kittytune.audio.haptics.HapticUtil

/**
 * The feel of an interaction, named by intent rather than by vibration constant.
 *
 * Everything funnels into [HapticUtil], so the app-wide mode (disabled / enabled / stronger),
 * the "buttons" and "sliders" switches, and per-action player settings all keep working no
 * matter which component asks for feedback.
 */
enum class HapticKind {
    /** Barely there, for chips, tiny toggles and repeating ticks. */
    MICRO,

    /** A soft tick for secondary actions and segmented controls. */
    LIGHT,

    /** The default tap for ordinary buttons, rows and cards. */
    UI,

    /** A firmer tap for selection and state changes. */
    MEDIUM,

    /** A full key-press for primary actions. */
    HEAVY,

    /** A crisp impact for hero actions such as play/pause. */
    STRONG,

    /** Two-part confirmation for like, save, follow and download. */
    CONFIRM,

    /** Refusal for delete, dismiss and error paths. */
    REJECT,

    /** One step of a slider or seek bar. */
    SLIDER,

    /** Crossing a drag threshold. */
    THRESHOLD,

    /** Two quick clicks for an unmistakable confirmation. */
    DOUBLE,
}

/**
 * A haptic dispatcher bound to the current window's [View].
 *
 * Holding the view once is what lets the JVM-level helper stay non-composable: call sites can
 * write `haptics.perform(HapticKind.MEDIUM)` inside any existing `onClick` without threading a
 * `Context` or a `LocalView` through the layout.
 */
@Stable
class AppHaptics internal constructor(private val view: View) {
    fun perform(kind: HapticKind) {
        when (kind) {
            HapticKind.MICRO -> HapticUtil.performMicroHaptic(view)
            HapticKind.LIGHT -> HapticUtil.performLightHaptic(view)
            HapticKind.UI -> HapticUtil.performUIHaptic(view)
            HapticKind.MEDIUM -> HapticUtil.performMediumHaptic(view)
            HapticKind.HEAVY -> HapticUtil.performHeavyHaptic(view)
            HapticKind.STRONG -> HapticUtil.performStrongTickHaptic(view)
            HapticKind.CONFIRM -> HapticUtil.performConfirmHaptic(view)
            HapticKind.REJECT -> HapticUtil.performRejectHaptic(view)
            HapticKind.SLIDER -> HapticUtil.performSliderHaptic(view)
            HapticKind.THRESHOLD -> HapticUtil.performGestureThresholdHaptic(view)
            HapticKind.DOUBLE -> HapticUtil.performDoubleHaptic(view)
        }
    }

    /** Shorthand for the overwhelmingly common case. */
    fun click() = perform(HapticKind.UI)
}

/**
 * Wraps [action] so it fires [kind] first. Handy for Material components that own their own
 * gesture (`Button`, `IconButton`, `Card(onClick)`, `Surface(onClick)`).
 */
@Composable
fun hapticAction(kind: HapticKind = HapticKind.UI, action: () -> Unit): () -> Unit {
    val haptics = rememberAppHaptics()
    return {
        haptics.perform(kind)
        action()
    }
}

@Composable
fun rememberAppHaptics(): AppHaptics {
    val view = LocalView.current
    return remember(view) { AppHaptics(view) }
}

/**
 * `Modifier.clickable` that also fires [kind] before running [onClick].
 *
 * Use it for custom surfaces (cards, tiles, rows, text links) that Material does not wrap for
 * us. Material's own `Button`, `IconButton` and `Card(onClick)` need the haptic inside their
 * `onClick` instead, because they own the gesture.
 */
fun Modifier.hapticClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    kind: HapticKind = HapticKind.UI,
    onClick: () -> Unit,
): Modifier = composed {
    val haptics = rememberAppHaptics()
    clickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
    ) {
        haptics.perform(kind)
        onClick()
    }
}
