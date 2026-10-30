package com.alananasss.kittytune.ui.design

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox as MaterialCheckbox
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton as MaterialRadioButton
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch as MaterialSwitch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.data.local.UiDesignStyle
import com.alananasss.kittytune.ui.liquidglass.LocalLiquidGlassConfig
import com.alananasss.kittytune.ui.liquidglass.LocalUiDesignStyle
import com.alananasss.kittytune.ui.liquidglass.components.KittyLiquidToggle
import com.alananasss.kittytune.ui.liquidglass.rememberGlassBackdrop

/**
 * Glass switches, checkboxes and radio buttons.
 *
 * The switch maps onto the same liquid toggle the settings rows use, so there is one switch
 * language in the app. Checkboxes and radios keep Material's drawing (and therefore their
 * semantics) and receive a subtle glass halo behind the touch target.
 */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    if (LocalUiDesignStyle.current == UiDesignStyle.LIQUID_GLASS && LocalLiquidGlassConfig.current.applyToControls) {
        val backdrop = rememberGlassBackdrop()
        KittyLiquidToggle(
            selected = { checked },
            onSelect = { next ->
                if (enabled) onCheckedChange?.invoke(next)
            },
            backdrop = backdrop,
            modifier = modifier.then(if (enabled) Modifier else Modifier.alpha(0.45f)),
            accentColor = if (enabled) colors.checkedTrackColor else colors.disabledCheckedTrackColor
        )
    } else {
        MaterialSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            thumbContent = thumbContent,
            enabled = enabled,
            colors = colors,
            interactionSource = interactionSource
        )
    }
}

@Composable
fun Checkbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: CheckboxColors = CheckboxDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    if (LocalUiDesignStyle.current == UiDesignStyle.LIQUID_GLASS && LocalLiquidGlassConfig.current.applyToControls) {
        val tint = if (checked) colors.checkedBoxColor else MaterialTheme.colorScheme.surface
        MaterialCheckbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier.then(
                Modifier.glassSurface(
                    shape = RoundedCornerShape(8.dp),
                    tint = tint,
                    emphasis = GlassEmphasis.Subtle,
                    shadowElevation = 0.dp
                )
            ),
            enabled = enabled,
            colors = colors,
            interactionSource = interactionSource
        )
    } else {
        MaterialCheckbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            colors = colors,
            interactionSource = interactionSource
        )
    }
}

@Composable
fun RadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: RadioButtonColors = RadioButtonDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    if (LocalUiDesignStyle.current == UiDesignStyle.LIQUID_GLASS && LocalLiquidGlassConfig.current.applyToControls) {
        val tint = if (selected) colors.selectedColor else MaterialTheme.colorScheme.surface
        MaterialRadioButton(
            selected = selected,
            onClick = onClick,
            modifier = modifier.then(
                Modifier.glassSurface(
                    shape = CircleShape,
                    tint = tint,
                    emphasis = GlassEmphasis.Subtle,
                    shadowElevation = 0.dp
                )
            ),
            enabled = enabled,
            colors = colors,
            interactionSource = interactionSource
        )
    } else {
        MaterialRadioButton(
            selected = selected,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = colors,
            interactionSource = interactionSource
        )
    }
}
