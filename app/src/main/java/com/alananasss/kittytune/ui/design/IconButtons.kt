package com.alananasss.kittytune.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton as MaterialFilledIconButton
import androidx.compose.material3.FilledTonalIconButton as MaterialFilledTonalIconButton
import androidx.compose.material3.IconButton as MaterialIconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.OutlinedIconButton as MaterialOutlinedIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.data.local.UiDesignStyle
import com.alananasss.kittytune.ui.liquidglass.LocalLiquidGlassConfig
import com.alananasss.kittytune.ui.liquidglass.LocalUiDesignStyle

/**
 * Liquid Glass icon buttons.
 *
 * Plain icon buttons only grow a glass bubble while pressed (otherwise every toolbar icon would be
 * wrapped in a permanent pill); filled/tonal/outlined variants keep their container and get the
 * full glass treatment, tinted with their own colour.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = IconButtonDefaults.standardShape,
    shapes: IconButtonShapes? = null,
    content: @Composable () -> Unit,
) {
    val liquid = LocalUiDesignStyle.current == UiDesignStyle.LIQUID_GLASS && LocalLiquidGlassConfig.current.applyToIconButtons
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val glass = Modifier.glassSurface(
        shape = shape,
        tint = MaterialTheme.colorScheme.primary,
        emphasis = GlassEmphasis.Subtle,
        enabled = pressed,
        shadowElevation = 0.dp
    )

    if (liquid) {
        // Material's icon button body is 40 dp inside a 48 dp touch target. Draw the pressed
        // glass on a 40 dp body so it cannot look larger than the icon button itself.
        Box(
            modifier = modifier.minimumInteractiveComponentSize(),
            contentAlignment = Alignment.Center
        ) {
            if (pressed) {
                Box(
                    Modifier
                        .defaultMinSize(40.dp, 40.dp)
                        .then(glass)
                ) {}
            }
            MaterialIconButton(
                onClick = onClick,
                modifier = Modifier,
                enabled = enabled,
                colors = colors,
                interactionSource = interaction,
                shape = shape,
                content = content
            )
        }
    } else if (shapes != null) {
        MaterialIconButton(
            onClick = onClick,
            shapes = shapes,
            modifier = modifier,
            enabled = enabled,
            colors = colors,
            interactionSource = interaction,
            content = content
        )
    } else {
        MaterialIconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = colors,
            interactionSource = interaction,
            shape = shape,
            content = content
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FilledIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = IconButtonDefaults.filledShape,
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    shapes: IconButtonShapes? = null,
    content: @Composable () -> Unit,
) {
    val liquid = LocalUiDesignStyle.current == UiDesignStyle.LIQUID_GLASS && LocalLiquidGlassConfig.current.applyToIconButtons
    if (liquid) {
        LiquidIconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            tint = if (enabled) colors.containerColor else colors.disabledContainerColor,
            contentColor = if (enabled) colors.contentColor else colors.disabledContentColor,
            interactionSource = interactionSource,
            content = content
        )
    } else if (shapes != null) {
        MaterialFilledIconButton(
            onClick = onClick,
            shapes = shapes,
            modifier = modifier,
            enabled = enabled,
            colors = colors,
            interactionSource = interactionSource,
            content = content
        )
    } else {
        MaterialFilledIconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = colors,
            shape = shape,
            interactionSource = interactionSource,
            content = content
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FilledTonalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = IconButtonDefaults.filledShape,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    shapes: IconButtonShapes? = null,
    content: @Composable () -> Unit,
) {
    val liquid = LocalUiDesignStyle.current == UiDesignStyle.LIQUID_GLASS && LocalLiquidGlassConfig.current.applyToIconButtons
    if (liquid) {
        LiquidIconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            tint = if (enabled) colors.containerColor else colors.disabledContainerColor,
            contentColor = if (enabled) colors.contentColor else colors.disabledContentColor,
            interactionSource = interactionSource,
            content = content
        )
    } else if (shapes != null) {
        MaterialFilledTonalIconButton(
            onClick = onClick,
            shapes = shapes,
            modifier = modifier,
            enabled = enabled,
            colors = colors,
            interactionSource = interactionSource,
            content = content
        )
    } else {
        MaterialFilledTonalIconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = colors,
            shape = shape,
            interactionSource = interactionSource,
            content = content
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OutlinedIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = IconButtonDefaults.outlinedShape,
    colors: IconButtonColors = IconButtonDefaults.outlinedIconButtonColors(),
    border: BorderStroke? = IconButtonDefaults.outlinedIconButtonBorder(enabled),
    interactionSource: MutableInteractionSource? = null,
    shapes: IconButtonShapes? = null,
    content: @Composable () -> Unit,
) {
    val liquid = LocalUiDesignStyle.current == UiDesignStyle.LIQUID_GLASS && LocalLiquidGlassConfig.current.applyToIconButtons
    if (liquid) {
        val containerColor = if (enabled) colors.containerColor else colors.disabledContainerColor
        LiquidIconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            tint = if (containerColor.alpha > 0.01f) containerColor else MaterialTheme.colorScheme.surface,
            contentColor = if (enabled) colors.contentColor else colors.disabledContentColor,
            interactionSource = interactionSource,
            border = border,
            content = content
        )
    } else if (shapes != null) {
        MaterialOutlinedIconButton(
            onClick = onClick,
            shapes = shapes,
            modifier = modifier,
            enabled = enabled,
            colors = colors,
            border = border,
            interactionSource = interactionSource,
            content = content
        )
    } else {
        MaterialOutlinedIconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = colors,
            border = border,
            shape = shape,
            interactionSource = interactionSource,
            content = content
        )
    }
}
