@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.alananasss.kittytune.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.ui.glass.GlassComponent
import com.alananasss.kittytune.ui.glass.components.GlassButton
import com.alananasss.kittytune.ui.glass.components.GlassButtonStyle
import com.alananasss.kittytune.ui.glass.components.GlassIconButton
import com.alananasss.kittytune.ui.glass.glassEnabled
import com.alananasss.kittytune.ui.glass.rememberGlassColors
import com.alananasss.kittytune.ui.glass.rememberGlassShape
import androidx.compose.material3.Button as M3Button
import androidx.compose.material3.ElevatedButton as M3ElevatedButton
import androidx.compose.material3.FilledIconButton as M3FilledIconButton
import androidx.compose.material3.FilledTonalButton as M3FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton as M3FilledTonalIconButton
import androidx.compose.material3.IconButton as M3IconButton
import androidx.compose.material3.OutlinedButton as M3OutlinedButton
import androidx.compose.material3.OutlinedIconButton as M3OutlinedIconButton
import androidx.compose.material3.TextButton as M3TextButton

/*
 * Drop-in replacements for the Material 3 buttons.
 *
 * Each one has the Material signature (the `shape` and the expressive `shapes` overloads merged into
 * one function) and renders the Material component untouched unless Liquid Glass is on for its
 * kind. Screens switch to them with a single import; nothing else in the call site changes.
 */

/** The role of a button inside a glass dialog's action row. */
enum class GlassDialogAction { None, Confirm, Dismiss }

/** Set by the glass dialog around its button slots. */
val LocalGlassDialogAction = compositionLocalOf { GlassDialogAction.None }

private enum class ButtonKind { Filled, Tonal, Elevated, Outlined, Text }

@Composable
private fun GlassMaterialButton(
    kind: ButtonKind,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shape: Shape,
    colors: ButtonColors,
    contentPadding: PaddingValues,
    interactionSource: MutableInteractionSource?,
    content: @Composable RowScope.() -> Unit,
) {
    val glassColors = rememberGlassColors()
    val dialogAction = LocalGlassDialogAction.current
    val container = if (enabled) colors.containerColor else colors.disabledContainerColor
    var contentColor = if (enabled) colors.contentColor else colors.disabledContentColor
    val hasContainer = container.alpha > 0.05f

    var style = when (kind) {
        ButtonKind.Filled -> if (hasContainer) GlassButtonStyle.Prominent else GlassButtonStyle.Regular
        ButtonKind.Tonal, ButtonKind.Elevated -> GlassButtonStyle.Regular
        ButtonKind.Outlined -> GlassButtonStyle.Clear
        ButtonKind.Text -> GlassButtonStyle.Borderless
    }
    var tint = container
    var surface = when (kind) {
        ButtonKind.Tonal -> if (hasContainer) container.copy(alpha = minOf(container.alpha, 0.42f)) else Color.Unspecified
        else -> Color.Unspecified
    }
    var fill = false

    when (dialogAction) {
        GlassDialogAction.Confirm -> {
            style = GlassButtonStyle.Prominent
            tint = glassColors.accent
            surface = Color.Unspecified
            if (enabled) contentColor = glassColors.onAccent
            fill = true
        }
        GlassDialogAction.Dismiss -> {
            style = GlassButtonStyle.Regular
            surface = Color.Unspecified
            fill = true
        }
        GlassDialogAction.None -> Unit
    }

    GlassButton(
        onClick = onClick,
        modifier = if (fill) modifier.fillMaxWidth() else modifier,
        enabled = enabled,
        style = style,
        shape = rememberGlassShape(shape),
        tint = tint,
        surfaceColor = surface,
        contentColor = contentColor,
        contentPadding = if (fill) PaddingValues(horizontal = 16.dp, vertical = 12.dp) else contentPadding,
        minHeight = if (fill) 48.dp else ButtonDefaults.MinHeight,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
private fun useMiuixButton(): Boolean = LocalMiuixSettings.current.let { mx ->
    mx.isOn(MiuixComponent.BUTTONS) || (mx.isOn(MiuixComponent.DIALOGS) && LocalGlassDialogAction.current != GlassDialogAction.None)
}

@Composable
private fun useGlassButton(): Boolean =
    LocalGlassDialogAction.current != GlassDialogAction.None || glassEnabled(GlassComponent.BUTTONS)

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
    content: @Composable RowScope.() -> Unit,
) {
    when {
        useMiuixButton() -> MiuixMaterialButton(MiuixButtonRole.Primary, onClick, modifier, enabled, colors.containerColor, colors.contentColor, interactionSource, contentPadding, content)
        useGlassButton() -> GlassMaterialButton(ButtonKind.Filled, onClick, modifier, enabled, shapes?.shape ?: shape, colors, contentPadding, interactionSource, content)
        shapes != null -> M3Button(onClick, shapes, modifier, enabled, colors, elevation, border, contentPadding, interactionSource, content)
        else -> M3Button(onClick, modifier, enabled, shape, colors, elevation, border, contentPadding, interactionSource, content)
    }
}

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
    content: @Composable RowScope.() -> Unit,
) {
    when {
        useMiuixButton() -> MiuixMaterialButton(MiuixButtonRole.Secondary, onClick, modifier, enabled, null, null, interactionSource, contentPadding, content)
        useGlassButton() -> GlassMaterialButton(ButtonKind.Tonal, onClick, modifier, enabled, shapes?.shape ?: shape, colors, contentPadding, interactionSource, content)
        shapes != null -> M3FilledTonalButton(onClick, shapes, modifier, enabled, colors, elevation, border, contentPadding, interactionSource, content)
        else -> M3FilledTonalButton(onClick, modifier, enabled, shape, colors, elevation, border, contentPadding, interactionSource, content)
    }
}

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
    when {
        useMiuixButton() -> MiuixMaterialButton(MiuixButtonRole.Secondary, onClick, modifier, enabled, null, null, interactionSource, contentPadding, content)
        useGlassButton() -> GlassMaterialButton(ButtonKind.Elevated, onClick, modifier, enabled, shapes?.shape ?: shape, colors, contentPadding, interactionSource, content)
        shapes != null -> M3ElevatedButton(onClick, shapes, modifier, enabled, colors, elevation, border, contentPadding, interactionSource, content)
        else -> M3ElevatedButton(onClick, modifier, enabled, shape, colors, elevation, border, contentPadding, interactionSource, content)
    }
}

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
    when {
        useMiuixButton() -> MiuixMaterialButton(MiuixButtonRole.Secondary, onClick, modifier, enabled, null, null, interactionSource, contentPadding, content)
        useGlassButton() -> GlassMaterialButton(ButtonKind.Outlined, onClick, modifier, enabled, shapes?.shape ?: shape, colors, contentPadding, interactionSource, content)
        shapes != null -> M3OutlinedButton(onClick, shapes, modifier, enabled, colors, elevation, border, contentPadding, interactionSource, content)
        else -> M3OutlinedButton(onClick, modifier, enabled, shape, colors, elevation, border, contentPadding, interactionSource, content)
    }
}

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
    when {
        useMiuixButton() -> MiuixMaterialButton(MiuixButtonRole.Text, onClick, modifier, enabled, null, colors.contentColor, interactionSource, contentPadding, content)
        useGlassButton() -> GlassMaterialButton(ButtonKind.Text, onClick, modifier, enabled, shapes?.shape ?: shape, colors, contentPadding, interactionSource, content)
        shapes != null -> M3TextButton(onClick, shapes, modifier, enabled, colors, elevation, border, contentPadding, interactionSource, content)
        else -> M3TextButton(onClick, modifier, enabled, shape, colors, elevation, border, contentPadding, interactionSource, content)
    }
}

// --- Icon buttons ----------------------------------------------------------------------------

private enum class IconKind { Standard, Filled, Tonal, Outlined }

@Composable
private fun GlassMaterialIconButton(
    kind: IconKind,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shape: Shape,
    colors: IconButtonColors,
    interactionSource: MutableInteractionSource?,
    content: @Composable () -> Unit,
) {
    val container = if (enabled) colors.containerColor else colors.disabledContainerColor
    val contentColor = if (enabled) colors.contentColor else colors.disabledContentColor
    val hasContainer = container.alpha > 0.05f
    val style = when (kind) {
        IconKind.Filled -> if (hasContainer) GlassButtonStyle.Prominent else GlassButtonStyle.Regular
        IconKind.Tonal -> GlassButtonStyle.Regular
        IconKind.Outlined -> GlassButtonStyle.Clear
        IconKind.Standard -> if (hasContainer) GlassButtonStyle.Regular else GlassButtonStyle.Borderless
    }
    val surface = when {
        kind == IconKind.Tonal && hasContainer -> container.copy(alpha = minOf(container.alpha, 0.42f))
        kind == IconKind.Standard && hasContainer -> container.copy(alpha = minOf(container.alpha, 0.42f))
        else -> Color.Unspecified
    }
    GlassIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        style = style,
        shape = rememberGlassShape(shape),
        tint = container,
        surfaceColor = surface,
        contentColor = contentColor,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
private fun useGlassIconButton(): Boolean = glassEnabled(GlassComponent.ICON_BUTTONS)

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
    when {
        miuixEnabled(MiuixComponent.ICON_BUTTONS) -> MiuixMaterialIconButton(MiuixIconRole.Standard, onClick, modifier, enabled, colors.containerColor, if (enabled) colors.contentColor else colors.disabledContentColor, content)
        useGlassIconButton() -> GlassMaterialIconButton(IconKind.Standard, onClick, modifier, enabled, shapes?.shape ?: shape, colors, interactionSource, content)
        shapes != null -> M3IconButton(onClick, shapes, modifier, enabled, colors, interactionSource, content)
        else -> M3IconButton(onClick, modifier, enabled, colors, interactionSource, shape, content)
    }
}

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
    when {
        miuixEnabled(MiuixComponent.ICON_BUTTONS) -> MiuixMaterialIconButton(MiuixIconRole.Filled, onClick, modifier, enabled, colors.containerColor, if (enabled) colors.contentColor else colors.disabledContentColor, content)
        useGlassIconButton() -> GlassMaterialIconButton(IconKind.Filled, onClick, modifier, enabled, shapes?.shape ?: shape, colors, interactionSource, content)
        shapes != null -> M3FilledIconButton(onClick, shapes, modifier, enabled, colors, interactionSource, content)
        else -> M3FilledIconButton(onClick, modifier, enabled, shape, colors, interactionSource, content)
    }
}

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
    when {
        miuixEnabled(MiuixComponent.ICON_BUTTONS) -> MiuixMaterialIconButton(MiuixIconRole.Tonal, onClick, modifier, enabled, colors.containerColor, if (enabled) colors.contentColor else colors.disabledContentColor, content)
        useGlassIconButton() -> GlassMaterialIconButton(IconKind.Tonal, onClick, modifier, enabled, shapes?.shape ?: shape, colors, interactionSource, content)
        shapes != null -> M3FilledTonalIconButton(onClick, shapes, modifier, enabled, colors, interactionSource, content)
        else -> M3FilledTonalIconButton(onClick, modifier, enabled, shape, colors, interactionSource, content)
    }
}

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
    when {
        miuixEnabled(MiuixComponent.ICON_BUTTONS) -> MiuixMaterialIconButton(MiuixIconRole.Tonal, onClick, modifier, enabled, colors.containerColor, if (enabled) colors.contentColor else colors.disabledContentColor, content)
        useGlassIconButton() -> GlassMaterialIconButton(IconKind.Outlined, onClick, modifier, enabled, shapes?.shape ?: shape, colors, interactionSource, content)
        shapes != null -> M3OutlinedIconButton(onClick, shapes, modifier, enabled, colors, border, interactionSource, content)
        else -> M3OutlinedIconButton(onClick, modifier, enabled, shape, colors, border, interactionSource, content)
    }
}
