package com.alananasss.kittytune.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ElevatedButton as MaterialElevatedButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton as MaterialFilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton as MaterialOutlinedButton
import androidx.compose.material3.TextButton as MaterialTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.data.local.UiDesignStyle
import com.alananasss.kittytune.ui.liquidglass.LocalLiquidGlassConfig
import com.alananasss.kittytune.ui.liquidglass.LocalUiDesignStyle

/**
 * The design system's buttons.
 *
 * They are drop-in replacements for Material 3's: every parameter of the standard/expressive
 * overloads is accepted and, when the design style is not Liquid Glass, the exact Material
 * component is used. In Liquid Glass mode the Material container is made transparent and a glass
 * surface is drawn behind it with the component's own shape and colour, so the layout, ripple,
 * semantics and content padding stay the ones Material computed.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    shapes: ButtonShapes? = null,
    glassHighlightAlpha: Float = 0.75f,
    glassPressExpansion: Dp = 4.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val liquid = LocalUiDesignStyle.current == UiDesignStyle.LIQUID_GLASS && LocalLiquidGlassConfig.current.applyToButtons
    val containerColor = if (enabled) colors.containerColor else colors.disabledContainerColor
    val glass = Modifier.interactiveGlassButton(
        shape = shape,
        tint = containerColor,
        emphasis = GlassEmphasis.Prominent,
        enabled = enabled,
        shadowElevation = 8.dp,
        highlightAlpha = glassHighlightAlpha,
        pressExpansion = glassPressExpansion
    )
    val materialColors = if (liquid) colors.copy(
        containerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent
    ) else colors

    LiquidButtonIndication(liquid) {
    if (!liquid && shapes != null) {
        MaterialButton(
            onClick = onClick,
            shapes = shapes,
            modifier = modifier,
            enabled = enabled,
            colors = materialColors,
            elevation = elevation,
            border = border,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = content
        )
    } else {
        MaterialButton(
            onClick = onClick,
            modifier = if (liquid) modifier.then(glass) else modifier,
            enabled = enabled,
            shape = shape,
            colors = materialColors,
            elevation = if (liquid) null else elevation,
            border = border,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = content
        )
    }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.filledTonalShape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    shapes: ButtonShapes? = null,
    glassHighlightAlpha: Float = 0.75f,
    glassPressExpansion: Dp = 4.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val liquid = LocalUiDesignStyle.current == UiDesignStyle.LIQUID_GLASS && LocalLiquidGlassConfig.current.applyToButtons
    val containerColor = if (enabled) colors.containerColor else colors.disabledContainerColor
    val glass = Modifier.interactiveGlassButton(
        shape = shape,
        tint = containerColor,
        emphasis = GlassEmphasis.Prominent,
        enabled = enabled,
        shadowElevation = 8.dp,
        highlightAlpha = glassHighlightAlpha,
        pressExpansion = glassPressExpansion
    )
    val materialColors = if (liquid) colors.copy(
        containerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent
    ) else colors

    LiquidButtonIndication(liquid) {
    if (!liquid && shapes != null) {
        MaterialFilledTonalButton(
            onClick = onClick,
            shapes = shapes,
            modifier = modifier,
            enabled = enabled,
            colors = materialColors,
            elevation = elevation,
            border = border,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = content
        )
    } else {
        MaterialFilledTonalButton(
            onClick = onClick,
            modifier = if (liquid) modifier.then(glass) else modifier,
            enabled = enabled,
            shape = shape,
            colors = materialColors,
            elevation = if (liquid) null else elevation,
            border = border,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = content
        )
    }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    shapes: ButtonShapes? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val liquid = LocalUiDesignStyle.current == UiDesignStyle.LIQUID_GLASS && LocalLiquidGlassConfig.current.applyToButtons
    val containerColor = if (enabled) colors.containerColor else colors.disabledContainerColor
    val glassTint = if (containerColor.alpha > 0.01f) containerColor else MaterialTheme.colorScheme.surface
    val glass = Modifier.interactiveGlassButton(
        shape = shape,
        tint = glassTint,
        emphasis = GlassEmphasis.Surface,
        enabled = enabled,
        shadowElevation = 4.dp
    )
    val materialColors = if (liquid) colors.copy(
        containerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent
    ) else colors

    LiquidButtonIndication(liquid) {
    if (!liquid && shapes != null) {
        MaterialOutlinedButton(
            onClick = onClick,
            shapes = shapes,
            modifier = modifier,
            enabled = enabled,
            colors = materialColors,
            elevation = elevation,
            border = border,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = content
        )
    } else {
        MaterialOutlinedButton(
            onClick = onClick,
            modifier = if (liquid) modifier.then(glass) else modifier,
            enabled = enabled,
            shape = shape,
            colors = materialColors,
            elevation = if (liquid) null else elevation,
            border = border,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = content
        )
    }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ElevatedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.elevatedShape,
    colors: ButtonColors = ButtonDefaults.elevatedButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.elevatedButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    shapes: ButtonShapes? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val liquid = LocalUiDesignStyle.current == UiDesignStyle.LIQUID_GLASS && LocalLiquidGlassConfig.current.applyToButtons
    val containerColor = if (enabled) colors.containerColor else colors.disabledContainerColor
    val glass = Modifier.interactiveGlassButton(
        shape = shape,
        tint = containerColor,
        emphasis = GlassEmphasis.Prominent,
        enabled = enabled,
        shadowElevation = 10.dp
    )
    val materialColors = if (liquid) colors.copy(
        containerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent
    ) else colors

    LiquidButtonIndication(liquid) {
    if (!liquid && shapes != null) {
        MaterialElevatedButton(
            onClick = onClick,
            shapes = shapes,
            modifier = modifier,
            enabled = enabled,
            colors = materialColors,
            elevation = elevation,
            border = border,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = content
        )
    } else {
        MaterialElevatedButton(
            onClick = onClick,
            modifier = if (liquid) modifier.then(glass) else modifier,
            enabled = enabled,
            shape = shape,
            colors = materialColors,
            elevation = if (liquid) null else elevation,
            border = border,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            content = content
        )
    }
    }
}

/**
 * Text buttons stay text-only at rest; glass only appears under the finger while pressed.
 *
 * That keeps inline actions ("See all", "Cancel"...) from turning into a wall of pills while still
 * giving them the liquid press feedback everywhere else.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    shapes: ButtonShapes? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val liquid = LocalUiDesignStyle.current == UiDesignStyle.LIQUID_GLASS && LocalLiquidGlassConfig.current.applyToButtons
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val glass = Modifier.glassSurface(
        shape = shape,
        tint = MaterialTheme.colorScheme.primary,
        emphasis = GlassEmphasis.Subtle,
        enabled = pressed,
        shadowElevation = 0.dp
    )
    val materialColors = if (liquid) colors.copy(
        containerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent
    ) else colors

    if (!liquid && shapes != null) {
        MaterialTextButton(
            onClick = onClick,
            shapes = shapes,
            modifier = modifier,
            enabled = enabled,
            colors = materialColors,
            elevation = elevation,
            border = border,
            contentPadding = contentPadding,
            interactionSource = interaction,
            content = content
        )
    } else {
        MaterialTextButton(
            onClick = onClick,
            modifier = if (liquid) modifier.then(glass) else modifier,
            enabled = enabled,
            shape = shape,
            colors = materialColors,
            elevation = if (liquid) null else elevation,
            border = border,
            contentPadding = contentPadding,
            interactionSource = interaction,
            content = content
        )
    }
}
