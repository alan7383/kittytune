@file:OptIn(ExperimentalMaterial3Api::class)

package com.alananasss.kittytune.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingActionButtonElevation
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import com.alananasss.kittytune.ui.glass.GlassComponent
import com.alananasss.kittytune.ui.glass.GlassLevel
import com.alananasss.kittytune.ui.glass.GlassShadow
import com.alananasss.kittytune.ui.glass.LocalGlassBackdrop
import com.alananasss.kittytune.ui.glass.glass
import com.alananasss.kittytune.ui.glass.glassEnabled
import com.alananasss.kittytune.ui.glass.rememberGlassColors
import com.alananasss.kittytune.ui.glass.rememberGlassHighlight
import com.alananasss.kittytune.ui.glass.rememberGlassShape
import com.alananasss.kittytune.ui.glass.rememberWindowSnapshotBackdrop
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import androidx.compose.material3.AlertDialog as M3AlertDialog
import androidx.compose.material3.Card as M3Card
import androidx.compose.material3.DropdownMenu as M3DropdownMenu
import androidx.compose.material3.ExtendedFloatingActionButton as M3ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton as M3FloatingActionButton
import androidx.compose.material3.ModalBottomSheet as M3ModalBottomSheet
import androidx.compose.material3.SmallFloatingActionButton as M3SmallFloatingActionButton

/**
 * Inside a settings group drawn as one glass panel: cards render as rows of that panel instead of
 * separate glass slabs.
 */
val LocalGlassCardGroup = compositionLocalOf { false }

// --- Cards -----------------------------------------------------------------------------------

@Composable
private fun cardSurface(colors: CardColors, enabled: Boolean): Color {
    val container = if (enabled) colors.containerColor else colors.disabledContainerColor
    return container.copy(alpha = container.alpha * if (LocalGlassCardGroup.current) 0.30f else 0.55f)
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (com.alananasss.kittytune.ui.common.LocalMiuixSettingsGroup.current || miuixEnabled(MiuixComponent.CARDS)) {
        MiuixMaterialCard(modifier, colors.containerColor, colors.contentColor, null, true, content)
        return
    }
    if (!glassEnabled(GlassComponent.CARDS)) {
        M3Card(modifier, shape, colors, elevation, border, content)
        return
    }
    val glassShape = rememberGlassShape(shape)
    val inGroup = LocalGlassCardGroup.current
    Column(
        modifier
            .then(
                if (inGroup) Modifier.clip(glassShape).background(cardSurface(colors, true))
                else Modifier.glass(glassShape, GlassLevel.Regular, surfaceColor = cardSurface(colors, true), shadow = GlassShadow.Soft)
            )
            .clip(glassShape)
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.contentColor) { content() }
    }
}

@Composable
fun Card(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (com.alananasss.kittytune.ui.common.LocalMiuixSettingsGroup.current || miuixEnabled(MiuixComponent.CARDS)) {
        MiuixMaterialCard(modifier, if (enabled) colors.containerColor else colors.disabledContainerColor, if (enabled) colors.contentColor else colors.disabledContentColor, onClick, enabled, content)
        return
    }
    if (!glassEnabled(GlassComponent.CARDS)) {
        M3Card(onClick, modifier, enabled, shape, colors, elevation, border, interactionSource, content)
        return
    }
    val glassShape = rememberGlassShape(shape)
    val inGroup = LocalGlassCardGroup.current
    val highlight = rememberGlassHighlight(enabled && !inGroup)
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    Column(
        modifier
            .then(
                if (inGroup) {
                    Modifier.clip(glassShape).background(cardSurface(colors, enabled))
                } else {
                    Modifier.glass(
                        glassShape,
                        GlassLevel.Regular,
                        surfaceColor = cardSurface(colors, enabled),
                        highlight = highlight,
                        pressExpansion = 2.dp,
                        shadow = GlassShadow.Soft
                    )
                }
            )
            .clip(glassShape)
            .clickable(
                interactionSource = interaction,
                indication = if (inGroup) androidx.compose.material3.ripple() else null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .then(highlight?.gestureModifier ?: Modifier)
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (enabled) colors.contentColor else colors.disabledContentColor
        ) { content() }
    }
}

// --- Dialogs ---------------------------------------------------------------------------------

/**
 * The Liquid Glass dialog panel: the app window refracted through a thick, blurred lens with a
 * plain rim light, like the Backdrop catalog's dialog.
 */
@Composable
fun GlassDialogSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedRectangle(36.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val backdrop = rememberWindowSnapshotBackdrop()
    val glassShape = rememberGlassShape(shape)
    Column(
        modifier
            .sizeIn(minWidth = 280.dp, maxWidth = 560.dp)
            .glass(glassShape, GlassLevel.Thick, backdrop = backdrop, shadow = GlassShadow.Floating)
            .clip(glassShape)
    ) {
        CompositionLocalProvider(LocalGlassBackdrop provides null) { content() }
    }
}

@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    if (miuixEnabled(MiuixComponent.DIALOGS)) {
        MiuixMaterialDialog(onDismissRequest, confirmButton, dismissButton, icon, title, text, properties.dismissOnClickOutside || properties.dismissOnBackPress)
        return
    }
    if (!glassEnabled(GlassComponent.DIALOGS)) {
        M3AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text,
            shape = shape,
            containerColor = containerColor,
            iconContentColor = iconContentColor,
            titleContentColor = titleContentColor,
            textContentColor = textContentColor,
            tonalElevation = tonalElevation,
            properties = properties,
        )
        return
    }
    val glass = rememberGlassColors()
    BasicAlertDialog(onDismissRequest = onDismissRequest, modifier = modifier, properties = properties) {
        GlassDialogSurface {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 20.dp)) {
                if (icon != null) {
                    CompositionLocalProvider(LocalContentColor provides glass.accent) {
                        Box(Modifier.padding(bottom = 16.dp).align(Alignment.CenterHorizontally)) { icon() }
                    }
                }
                if (title != null) {
                    CompositionLocalProvider(
                        LocalContentColor provides glass.content,
                        LocalTextStyle provides MaterialTheme.typography.headlineSmall
                    ) {
                        Box(
                            Modifier
                                .padding(bottom = 12.dp)
                                .align(if (icon == null) Alignment.Start else Alignment.CenterHorizontally)
                        ) { title() }
                    }
                }
                if (text != null) {
                    CompositionLocalProvider(
                        LocalContentColor provides glass.content.copy(alpha = 0.78f),
                        LocalTextStyle provides MaterialTheme.typography.bodyMedium
                    ) {
                        Box(
                            Modifier
                                .weight(1f, fill = false)
                                .padding(bottom = 20.dp)
                                .align(Alignment.Start)
                        ) { text() }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (dismissButton != null) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            CompositionLocalProvider(LocalGlassDialogAction provides GlassDialogAction.Dismiss) {
                                dismissButton()
                            }
                        }
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        CompositionLocalProvider(LocalGlassDialogAction provides GlassDialogAction.Confirm) {
                            confirmButton()
                        }
                    }
                }
            }
        }
    }
}

/** Free-form dialog: the caller draws its own surface, so this stays Material. */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismissRequest, modifier = modifier, properties = properties, content = content)
}

// --- Menus -----------------------------------------------------------------------------------

@Composable
fun DropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    shape: Shape = MenuDefaults.shape,
    containerColor: Color = MenuDefaults.containerColor,
    tonalElevation: Dp = MenuDefaults.TonalElevation,
    shadowElevation: Dp = MenuDefaults.ShadowElevation,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (miuixEnabled(MiuixComponent.MENUS)) {
        M3DropdownMenu(expanded, onDismissRequest, modifier, offset, scrollState, properties, RoundedCornerShape(18.dp), MaterialTheme.colorScheme.surfaceContainer, 0.dp, 12.dp, null, content)
        return
    }
    if (!glassEnabled(GlassComponent.MENUS)) {
        M3DropdownMenu(expanded, onDismissRequest, modifier, offset, scrollState, properties, shape, containerColor, tonalElevation, shadowElevation, border, content)
        return
    }
    val menuShape = remember { RoundedCornerShape(22.dp) }
    M3DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.then(GlassMenuBackground(menuShape)),
        offset = offset,
        scrollState = scrollState,
        properties = properties,
        shape = menuShape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = null,
        content = content,
    )
}

@Composable
private fun GlassMenuBackground(shape: Shape): Modifier {
    val backdrop = rememberWindowSnapshotBackdrop()
    return Modifier.glass(shape, GlassLevel.Thick, backdrop = backdrop)
}

// --- Bottom sheets ---------------------------------------------------------------------------

/** The glass panel of a bottom sheet, with its own drag handle. */
@Composable
fun GlassSheetSurface(
    shape: Shape,
    showHandle: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backdrop = rememberWindowSnapshotBackdrop()
    val glass = rememberGlassColors()
    val glassShape = rememberGlassShape(shape)
    Column(
        modifier
            .fillMaxWidth()
            .glass(glassShape, GlassLevel.Thick, backdrop = backdrop)
            .clip(glassShape)
    ) {
        if (showHandle) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .width(36.dp)
                        .height(5.dp)
                        .clip(Capsule())
                        .background(glass.content.copy(alpha = 0.25f))
                )
            }
        }
        CompositionLocalProvider(LocalGlassBackdrop provides null) { content() }
    }
}

@Composable
fun ModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    sheetGesturesEnabled: Boolean = true,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.modalWindowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (miuixEnabled(MiuixComponent.SHEETS)) {
        MiuixMaterialSheet(onDismissRequest, modifier, sheetMaxWidth, content)
        return
    }
    if (!glassEnabled(GlassComponent.SHEETS)) {
        M3ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            sheetMaxWidth = sheetMaxWidth,
            sheetGesturesEnabled = sheetGesturesEnabled,
            shape = shape,
            containerColor = containerColor,
            contentColor = contentColor,
            tonalElevation = tonalElevation,
            scrimColor = scrimColor,
            dragHandle = dragHandle,
            contentWindowInsets = contentWindowInsets,
            properties = properties,
            content = content,
        )
        return
    }
    val sheetShape = remember { RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp) }
    val glass = rememberGlassColors()
    M3ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        sheetMaxWidth = sheetMaxWidth,
        sheetGesturesEnabled = sheetGesturesEnabled,
        shape = sheetShape,
        containerColor = Color.Transparent,
        contentColor = glass.content,
        tonalElevation = 0.dp,
        scrimColor = scrimColor,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0) },
        properties = properties,
    ) {
        GlassSheetSurface(shape = sheetShape, showHandle = dragHandle != null) {
            val insets = contentWindowInsets()
            Column(Modifier.windowInsetsPadding(insets)) { content() }
        }
    }
}

// --- Floating action buttons -----------------------------------------------------------------

@Composable
private fun GlassFab(
    onClick: () -> Unit,
    modifier: Modifier,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    size: Dp?,
    interactionSource: MutableInteractionSource?,
    content: @Composable RowScope.() -> Unit,
) {
    com.alananasss.kittytune.ui.glass.components.GlassButton(
        onClick = onClick,
        modifier = if (size != null) modifier.size(size) else modifier.heightIn(min = 56.dp),
        style = com.alananasss.kittytune.ui.glass.components.GlassButtonStyle.Prominent,
        shape = rememberGlassShape(shape),
        tint = containerColor,
        contentColor = contentColor,
        contentPadding = if (size != null) PaddingValues(0.dp) else PaddingValues(horizontal = 20.dp),
        minWidth = size ?: 80.dp,
        minHeight = size ?: 56.dp,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun FloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = FloatingActionButtonDefaults.shape,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    if (miuixEnabled(MiuixComponent.FLOATING_BUTTONS)) {
        MiuixMaterialFab(onClick, modifier, containerColor, contentColor) { content() }
        return
    }
    if (!glassEnabled(GlassComponent.FLOATING_BUTTONS)) {
        M3FloatingActionButton(onClick, modifier, shape, containerColor, contentColor, elevation, interactionSource, content)
        return
    }
    GlassFab(onClick, modifier, RoundedRectangle(20.dp), containerColor, contentColor, 56.dp, interactionSource) { content() }
}

@Composable
fun SmallFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = FloatingActionButtonDefaults.smallShape,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    if (miuixEnabled(MiuixComponent.FLOATING_BUTTONS)) {
        MiuixMaterialFab(onClick, modifier, containerColor, contentColor) { content() }
        return
    }
    if (!glassEnabled(GlassComponent.FLOATING_BUTTONS)) {
        M3SmallFloatingActionButton(onClick, modifier, shape, containerColor, contentColor, elevation, interactionSource, content)
        return
    }
    GlassFab(onClick, modifier, RoundedRectangle(14.dp), containerColor, contentColor, 40.dp, interactionSource) { content() }
}

@Composable
fun ExtendedFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = FloatingActionButtonDefaults.extendedFabShape,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (miuixEnabled(MiuixComponent.FLOATING_BUTTONS)) {
        MiuixMaterialFab(onClick, modifier, containerColor, contentColor) { androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) { content() } }
        return
    }
    if (!glassEnabled(GlassComponent.FLOATING_BUTTONS)) {
        M3ExtendedFloatingActionButton(onClick, modifier, shape, containerColor, contentColor, elevation, interactionSource, content)
        return
    }
    GlassFab(onClick, modifier, Capsule(), containerColor, contentColor, null, interactionSource, content)
}

@Composable
fun ExtendedFloatingActionButton(
    text: @Composable () -> Unit,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    shape: Shape = FloatingActionButtonDefaults.extendedFabShape,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    interactionSource: MutableInteractionSource? = null,
) {
    if (!glassEnabled(GlassComponent.FLOATING_BUTTONS)) {
        M3ExtendedFloatingActionButton(text, icon, onClick, modifier, expanded, shape, containerColor, contentColor, elevation, interactionSource)
        return
    }
    GlassFab(onClick, modifier, Capsule(), containerColor, contentColor, if (expanded) null else 56.dp, interactionSource) {
        icon()
        if (expanded) {
            Spacer(Modifier.width(0.dp))
            text()
        }
    }
}
