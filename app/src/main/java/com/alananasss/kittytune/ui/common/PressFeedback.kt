package com.alananasss.kittytune.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Presses in slightly while held and springs back on release — the physical response Material 3
 * Expressive gives its buttons, for surfaces that are not buttons: cards, tiles, chips.
 *
 * Reads the press from [interactionSource], so it must be the same source the element's
 * `clickable` uses. Only the draw layer is scaled: layout, and therefore the neighbours, do not move.
 */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource, pressedScale: Float = PRESSED_SCALE): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Small enough to read as give, not as the element shrinking. */
private const val PRESSED_SCALE = 0.96f
