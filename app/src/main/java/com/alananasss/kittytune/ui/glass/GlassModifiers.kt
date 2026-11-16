package com.alananasss.kittytune.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * A custom button surface (a `Box` with a background and a click) that becomes Liquid Glass when
 * [component] is on: [prominent] buttons are tinted with [color], the others use it as their
 * translucent material. Otherwise it is exactly the original clip + background + ripple.
 */
@Composable
fun Modifier.glassButtonSurface(
    component: GlassComponent,
    shape: Shape,
    color: Color,
    interactionSource: MutableInteractionSource,
    prominent: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    if (!glassEnabled(component)) {
        return this
            .clip(shape)
            .background(color)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                enabled = enabled,
                onClick = onClick
            )
    }
    val glassShape = rememberGlassShape(shape)
    val highlight = rememberGlassHighlight(enabled)
    return this
        .glass(
            shape = glassShape,
            level = GlassLevel.Regular,
            tint = if (prominent) color else Color.Unspecified,
            surfaceColor = if (prominent) Color.Unspecified else color,
            highlight = highlight,
            shadow = GlassShadow.Soft,
        )
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick
        )
        .then(highlight?.gestureModifier ?: Modifier)
}

/**
 * Records [background] as the backdrop of [content] when glass is on for [component], so every
 * glass control in [content] refracts the real background (artwork, gradients) behind it.
 */
@Composable
fun GlassBackdropScope(
    component: GlassComponent,
    background: @Composable BoxScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    val on = glassEnabled(component)
    val backdrop = rememberLayerBackdrop()
    Box(
        Modifier
            .fillMaxSize()
            .then(if (on) Modifier.layerBackdrop(backdrop) else Modifier),
        content = background
    )
    CompositionLocalProvider(LocalGlassBackdrop provides if (on) backdrop else LocalGlassBackdrop.current) {
        content()
    }
}

/** A remembered interaction source, for call sites that did not keep one. */
@Composable
fun rememberInteraction(): MutableInteractionSource = remember { MutableInteractionSource() }
