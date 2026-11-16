package com.alananasss.kittytune.ui.design

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.basic.Button as MxButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MxButtonDefaults
import top.yukonga.miuix.kmp.basic.Card as MxCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MxCardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox as MxCheckbox
import top.yukonga.miuix.kmp.basic.FloatingActionButton as MxFloatingActionButton
import top.yukonga.miuix.kmp.basic.IconButton as MxIconButton
import top.yukonga.miuix.kmp.basic.RadioButton as MxRadioButton
import top.yukonga.miuix.kmp.basic.Slider as MxSlider
import top.yukonga.miuix.kmp.basic.Switch as MxSwitch
import top.yukonga.miuix.kmp.window.WindowBottomSheet as MxWindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog as MxWindowDialog

/*
 * MIUIX renderings behind the Material drop-ins. Each takes the values a Material call site already
 * passes (colours, enabled, content lambdas) and draws the HyperOS component; the Material call
 * sites never change.
 */

/** The HyperOS button label: 16 sp medium, coloured by the button. */
private val MiuixButtonText = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium)

internal enum class MiuixButtonRole { Primary, Secondary, Text }

@Composable
internal fun MiuixMaterialButton(
    role: MiuixButtonRole,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    containerColor: Color?,
    contentColor: Color?,
    interactionSource: MutableInteractionSource?,
    contentPadding: PaddingValues? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val mx = MiuixTheme.colorScheme
    var effectiveRole = role
    var fill = false
    when (LocalGlassDialogAction.current) {
        GlassDialogAction.Confirm -> { effectiveRole = MiuixButtonRole.Primary; fill = true }
        GlassDialogAction.Dismiss -> { effectiveRole = MiuixButtonRole.Secondary; fill = true }
        GlassDialogAction.None -> Unit
    }
    val colors = when (effectiveRole) {
        MiuixButtonRole.Primary -> MxButtonDefaults.buttonColorsPrimary(
            color = if (fill || containerColor == null) mx.primary else containerColor,
            contentColor = if (fill || contentColor == null) mx.onPrimary else contentColor,
        )
        MiuixButtonRole.Secondary -> MxButtonDefaults.buttonColors(
            contentColor = if (fill || contentColor == null) mx.onSecondaryVariant else contentColor,
        )
        MiuixButtonRole.Text -> MxButtonDefaults.buttonColors(
            color = Color.Transparent,
            disabledColor = Color.Transparent,
            contentColor = contentColor ?: mx.primary,
        )
    }
    val textColor = if (enabled) colors.contentColor else colors.disabledContentColor
    MxButton(
        onClick = onClick,
        modifier = if (fill) modifier.fillMaxWidth() else modifier,
        enabled = enabled,
        minHeight = if (effectiveRole == MiuixButtonRole.Text) 36.dp else MxButtonDefaults.MinHeight,
        minWidth = if (effectiveRole == MiuixButtonRole.Text) 0.dp else MxButtonDefaults.MinWidth,
        colors = colors,
        // The caller's padding is kept: many buttons have a fixed height sized for it.
        insideMargin = when {
            fill -> MxButtonDefaults.InsideMargin
            contentPadding != null -> contentPadding
            effectiveRole == MiuixButtonRole.Text -> PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            else -> MxButtonDefaults.InsideMargin
        },
        interactionSource = interactionSource,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides textColor,
            LocalTextStyle provides MiuixButtonText,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

internal enum class MiuixIconRole { Standard, Filled, Tonal }

@Composable
internal fun MiuixMaterialIconButton(
    role: MiuixIconRole,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    containerColor: Color,
    contentColor: Color,
    content: @Composable () -> Unit,
) {
    val mx = MiuixTheme.colorScheme
    val background = when (role) {
        MiuixIconRole.Filled -> if (containerColor.alpha > 0.05f) containerColor else mx.primary
        MiuixIconRole.Tonal -> mx.secondaryVariant
        MiuixIconRole.Standard -> if (containerColor.alpha > 0.05f) containerColor else Color.Unspecified
    }
    MxIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        backgroundColor = background,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (enabled) contentColor else contentColor.copy(alpha = 0.38f)) {
            content()
        }
    }
}

@Composable
internal fun MiuixMaterialSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier, enabled: Boolean) {
    MxSwitch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled)
}

@Composable
internal fun MiuixMaterialSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChangeFinished: (() -> Unit)?,
) {
    MxSlider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
    )
}

@Composable
internal fun MiuixMaterialCheckbox(state: ToggleableState, onClick: (() -> Unit)?, modifier: Modifier, enabled: Boolean) {
    MxCheckbox(state = state, onClick = onClick, modifier = modifier, enabled = enabled)
}

@Composable
internal fun MiuixMaterialRadio(selected: Boolean, onClick: (() -> Unit)?, modifier: Modifier, enabled: Boolean) {
    MxRadioButton(selected = selected, onClick = onClick, modifier = modifier, enabled = enabled)
}

/** A HyperOS card: 16 dp corners, white on a grey page, sinks when pressed. */
@Composable
internal fun MiuixMaterialCard(
    modifier: Modifier,
    containerColor: Color,
    contentColor: Color,
    onClick: (() -> Unit)?,
    enabled: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (com.alananasss.kittytune.ui.common.LocalMiuixSettingsGroup.current) {
        // A row of a HyperOS settings card: flat, pressed with the MIUIX wash.
        Column(
            modifier
                .then(if (containerColor.alpha > 0f && containerColor != MiuixTheme.colorScheme.surfaceContainer) Modifier.background(containerColor) else Modifier)
                .then(
                    if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier
                )
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
        }
        return
    }
    MxCard(
        modifier = modifier,
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(0.dp),
        colors = MxCardDefaults.defaultColors(color = containerColor, contentColor = contentColor),
        pressFeedbackType = if (onClick != null && enabled) PressFeedbackType.Sink else PressFeedbackType.None,
        showIndication = onClick != null && enabled,
        onClick = if (enabled) onClick else null,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
    }
}

/**
 * A HyperOS dialog: rounded sheet with a centred bold title, a summary and full-width actions.
 * Kept shown locally so it can play its exit animation before the caller removes it.
 */
@Composable
internal fun MiuixMaterialDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)?,
    icon: @Composable (() -> Unit)?,
    title: @Composable (() -> Unit)?,
    text: @Composable (() -> Unit)?,
    dismissible: Boolean,
) {
    val currentDismiss by rememberUpdatedState(onDismissRequest)
    var show by remember { mutableStateOf(true) }
    val mx = MiuixTheme.colorScheme
    MxWindowDialog(
        show = show,
        onDismissRequest = { if (dismissible) show = false },
        onDismissFinished = { if (!show) currentDismiss() },
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (icon != null) {
                CompositionLocalProvider(LocalContentColor provides mx.primary) {
                    Box(Modifier.padding(bottom = 12.dp)) { icon() }
                }
            }
            if (title != null) {
                CompositionLocalProvider(
                    LocalContentColor provides mx.onSurface,
                    LocalTextStyle provides MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
                ) {
                    Box(Modifier.padding(bottom = 12.dp)) { title() }
                }
            }
            if (text != null) {
                CompositionLocalProvider(
                    LocalContentColor provides mx.onSurfaceSecondary,
                    LocalTextStyle provides MaterialTheme.typography.bodyLarge,
                ) {
                    Box(Modifier.fillMaxWidth().padding(bottom = 20.dp)) { text() }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (dismissButton != null) {
                    Box(Modifier.weight(1f)) {
                        CompositionLocalProvider(LocalGlassDialogAction provides GlassDialogAction.Dismiss) { dismissButton() }
                    }
                }
                Box(Modifier.weight(1f)) {
                    CompositionLocalProvider(LocalGlassDialogAction provides GlassDialogAction.Confirm) { confirmButton() }
                }
            }
        }
    }
}

/** A HyperOS bottom sheet, with the same delayed-dismiss handling as the dialog. */
@Composable
internal fun MiuixMaterialSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    sheetMaxWidth: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val currentDismiss by rememberUpdatedState(onDismissRequest)
    var show by remember { mutableStateOf(true) }
    MxWindowBottomSheet(
        show = show,
        modifier = modifier,
        sheetMaxWidth = sheetMaxWidth,
        insideMargin = DpSize(0.dp, 0.dp),
        onDismissRequest = { show = false },
        onDismissFinished = { if (!show) currentDismiss() },
    ) {
        Column(Modifier.fillMaxWidth()) {
            content()
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun MiuixMaterialFab(
    onClick: () -> Unit,
    modifier: Modifier,
    containerColor: Color,
    contentColor: Color,
    content: @Composable () -> Unit,
) {
    MxFloatingActionButton(onClick = onClick, modifier = modifier, containerColor = containerColor) {
        CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
    }
}

/** HyperOS-style filter pill built on the MIUIX button. */
@Composable
internal fun MiuixMaterialChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    leadingIcon: (@Composable () -> Unit)?,
    trailingIcon: (@Composable () -> Unit)?,
    label: @Composable () -> Unit,
) {
    val mx = MiuixTheme.colorScheme
    val colors = if (selected) {
        MxButtonDefaults.buttonColors(color = mx.tertiaryContainer, contentColor = mx.onTertiaryContainer)
    } else {
        MxButtonDefaults.buttonColors()
    }
    val textColor = if (enabled) colors.contentColor else colors.disabledContentColor
    MxButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        cornerRadius = 18.dp,
        minHeight = 34.dp,
        minWidth = 0.dp,
        colors = colors,
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides textColor,
            LocalTextStyle provides TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) { leadingIcon() }
                label()
                if (trailingIcon != null) Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) { trailingIcon() }
            }
        }
    }
}

/** A HyperOS toast: white rounded card with a soft shadow and an optional blue action. */
@Composable
fun MiuixSnackbar(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val mx = MiuixTheme.colorScheme
    Row(
        modifier
            .shadow(12.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black.copy(0.12f), spotColor = Color.Black.copy(0.12f))
            .clip(RoundedCornerShape(16.dp))
            .background(mx.surfaceContainer)
            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Text(
            text = message,
            color = mx.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f, fill = false).padding(vertical = 10.dp),
        )
        if (actionLabel != null) {
            Spacer(Modifier.size(8.dp))
            MiuixMaterialButton(MiuixButtonRole.Text, onAction, Modifier, true, null, mx.primary, null) {
                androidx.compose.material3.Text(actionLabel)
            }
        } else {
            Spacer(Modifier.size(12.dp))
        }
    }
}
