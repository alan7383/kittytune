@file:OptIn(ExperimentalMaterial3Api::class)

package com.alananasss.kittytune.ui.design

import androidx.annotation.IntRange
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ChipColors
import androidx.compose.material3.ChipElevation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SelectableChipElevation
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.ui.glass.GlassComponent
import com.alananasss.kittytune.ui.glass.components.GlassButton
import com.alananasss.kittytune.ui.glass.components.GlassButtonStyle
import com.alananasss.kittytune.ui.glass.components.GlassSlider
import com.alananasss.kittytune.ui.glass.components.GlassToggle
import com.alananasss.kittytune.ui.glass.glassEnabled
import com.alananasss.kittytune.ui.glass.rememberGlassColors
import com.kyant.shapes.Capsule
import androidx.compose.material3.AssistChip as M3AssistChip
import androidx.compose.material3.FilterChip as M3FilterChip
import androidx.compose.material3.InputChip as M3InputChip
import androidx.compose.material3.Slider as M3Slider
import androidx.compose.material3.SuggestionChip as M3SuggestionChip
import androidx.compose.material3.Switch as M3Switch

// --- Switch ----------------------------------------------------------------------------------

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
    if (miuixEnabled(MiuixComponent.SWITCHES)) {
        MiuixMaterialSwitch(checked, onCheckedChange, modifier, enabled)
        return
    }
    if (!glassEnabled(GlassComponent.SWITCHES)) {
        M3Switch(checked, onCheckedChange, modifier, thumbContent, enabled, colors, interactionSource)
        return
    }
    Box(modifier.minimumInteractiveComponentSize(), contentAlignment = Alignment.Center) {
        GlassToggle(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            accentColor = colors.checkedTrackColor,
        )
    }
}

// --- Slider ----------------------------------------------------------------------------------

@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    @IntRange(from = 0) steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    if (miuixEnabled(MiuixComponent.SLIDERS)) {
        MiuixMaterialSlider(value, onValueChange, modifier, enabled, valueRange, steps, onValueChangeFinished)
        return
    }
    if (!glassEnabled(GlassComponent.SLIDERS)) {
        M3Slider(value, onValueChange, modifier, enabled, valueRange, steps, onValueChangeFinished, colors, interactionSource)
        return
    }
    GlassSlider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        accentColor = colors.activeTrackColor,
    )
}

/** Custom-slot sliders keep their own thumb and track: they are drawn by the caller. */
@Composable
fun Slider(
    state: SliderState,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    thumb: @Composable (SliderState) -> Unit = { _ ->
        SliderDefaults.Thumb(interactionSource = interactionSource, colors = colors, enabled = enabled)
    },
    track: @Composable (SliderState) -> Unit = { sliderState ->
        SliderDefaults.Track(colors = colors, enabled = enabled, sliderState = sliderState)
    },
) {
    M3Slider(state, onValueChange, modifier, enabled, onValueChangeFinished, colors, interactionSource, thumb, track)
}

@Composable
fun Slider(
    state: SliderState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChange: ((Float) -> Unit)? = null,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    thumb: @Composable (SliderState) -> Unit = { _ ->
        SliderDefaults.Thumb(interactionSource = interactionSource, colors = colors, enabled = enabled)
    },
    track: @Composable (SliderState) -> Unit = { sliderState ->
        SliderDefaults.Track(colors = colors, enabled = enabled, sliderState = sliderState)
    },
) {
    if (onValueChange != null) {
        M3Slider(state, onValueChange, modifier, enabled, onValueChangeFinished, colors, interactionSource, thumb, track)
    } else {
        M3Slider(state, { state.value = it }, modifier, enabled, onValueChangeFinished, colors, interactionSource, thumb, track)
    }
}

// --- Chips -----------------------------------------------------------------------------------

@Composable
private fun GlassChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    leadingIcon: (@Composable () -> Unit)?,
    trailingIcon: (@Composable () -> Unit)?,
    containerColor: Color,
    labelColor: Color,
    iconColor: Color,
    role: Role,
    interactionSource: MutableInteractionSource?,
) {
    val prominent = selected && containerColor.alpha > 0.05f
    GlassButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        style = if (prominent) GlassButtonStyle.Prominent else GlassButtonStyle.Regular,
        shape = Capsule(),
        tint = containerColor,
        contentColor = labelColor,
        contentPadding = PaddingValues(
            start = if (leadingIcon != null) 10.dp else 14.dp,
            end = if (trailingIcon != null) 10.dp else 14.dp,
        ),
        minWidth = 0.dp,
        minHeight = 32.dp,
        interactionSource = interactionSource,
        role = role,
    ) {
        if (leadingIcon != null) {
            CompositionLocalProvider(LocalContentColor provides iconColor) {
                Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) { leadingIcon() }
            }
        }
        CompositionLocalProvider(androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.labelLarge) {
            label()
        }
        if (trailingIcon != null) {
            CompositionLocalProvider(LocalContentColor provides iconColor) {
                Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) { trailingIcon() }
            }
        }
    }
}

@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = FilterChipDefaults.shape,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(),
    elevation: SelectableChipElevation? = FilterChipDefaults.filterChipElevation(),
    border: BorderStroke? = FilterChipDefaults.filterChipBorder(enabled, selected),
    horizontalArrangement: Arrangement.Horizontal = FilterChipDefaults.horizontalArrangement(),
    contentPadding: PaddingValues = FilterChipDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
) {
    if (miuixEnabled(MiuixComponent.CHIPS)) {
        MiuixMaterialChip(selected, onClick, modifier, enabled, leadingIcon, trailingIcon, label)
        return
    }
    if (!glassEnabled(GlassComponent.CHIPS)) {
        M3FilterChip(selected, onClick, label, modifier, enabled, leadingIcon, trailingIcon, shape, colors, elevation, border, horizontalArrangement, contentPadding, interactionSource)
        return
    }
    val glass = rememberGlassColors()
    GlassChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        containerColor = if (selected) colors.selectedContainerColor else Color.Transparent,
        labelColor = when {
            !enabled -> colors.disabledLabelColor
            selected -> colors.selectedLabelColor
            else -> glass.content
        },
        iconColor = when {
            !enabled -> colors.disabledLeadingIconColor
            selected -> colors.selectedLeadingIconColor
            else -> colors.leadingIconColor
        },
        role = Role.Checkbox,
        interactionSource = interactionSource,
    )
}

@Composable
fun InputChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    avatar: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = InputChipDefaults.shape,
    colors: SelectableChipColors = InputChipDefaults.inputChipColors(),
    elevation: SelectableChipElevation? = InputChipDefaults.inputChipElevation(),
    border: BorderStroke? = InputChipDefaults.inputChipBorder(enabled, selected),
    horizontalArrangement: Arrangement.Horizontal = InputChipDefaults.horizontalArrangement(),
    contentPadding: PaddingValues = InputChipDefaults.contentPadding(avatar != null, leadingIcon != null, trailingIcon != null),
    interactionSource: MutableInteractionSource? = null,
) {
    if (miuixEnabled(MiuixComponent.CHIPS)) {
        MiuixMaterialChip(selected, onClick, modifier, enabled, avatar ?: leadingIcon, trailingIcon, label)
        return
    }
    if (!glassEnabled(GlassComponent.CHIPS)) {
        M3InputChip(selected, onClick, label, modifier, enabled, leadingIcon, avatar, trailingIcon, shape, colors, elevation, border, horizontalArrangement, contentPadding, interactionSource)
        return
    }
    val glass = rememberGlassColors()
    GlassChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = avatar ?: leadingIcon,
        trailingIcon = trailingIcon,
        containerColor = if (selected) colors.selectedContainerColor else Color.Transparent,
        labelColor = when {
            !enabled -> colors.disabledLabelColor
            selected -> colors.selectedLabelColor
            else -> glass.content
        },
        iconColor = if (selected) colors.selectedLeadingIconColor else colors.leadingIconColor,
        role = Role.Checkbox,
        interactionSource = interactionSource,
    )
}

@Composable
fun AssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = AssistChipDefaults.shape,
    colors: ChipColors = AssistChipDefaults.assistChipColors(),
    elevation: ChipElevation? = AssistChipDefaults.assistChipElevation(),
    border: BorderStroke? = AssistChipDefaults.assistChipBorder(enabled),
    horizontalArrangement: Arrangement.Horizontal = AssistChipDefaults.horizontalArrangement(),
    contentPadding: PaddingValues = AssistChipDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
) {
    if (miuixEnabled(MiuixComponent.CHIPS)) {
        MiuixMaterialChip(false, onClick, modifier, enabled, leadingIcon, trailingIcon, label)
        return
    }
    if (!glassEnabled(GlassComponent.CHIPS)) {
        M3AssistChip(onClick, label, modifier, enabled, leadingIcon, trailingIcon, shape, colors, elevation, border, horizontalArrangement, contentPadding, interactionSource)
        return
    }
    GlassChip(
        selected = false,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        containerColor = colors.containerColor,
        labelColor = if (enabled) colors.labelColor else colors.disabledLabelColor,
        iconColor = if (enabled) colors.leadingIconContentColor else colors.disabledLeadingIconContentColor,
        role = Role.Button,
        interactionSource = interactionSource,
    )
}

@Composable
fun SuggestionChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    shape: Shape = SuggestionChipDefaults.shape,
    colors: ChipColors = SuggestionChipDefaults.suggestionChipColors(),
    elevation: ChipElevation? = SuggestionChipDefaults.suggestionChipElevation(),
    border: BorderStroke? = SuggestionChipDefaults.suggestionChipBorder(enabled),
    horizontalArrangement: Arrangement.Horizontal = SuggestionChipDefaults.horizontalArrangement(),
    contentPadding: PaddingValues = SuggestionChipDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
) {
    if (miuixEnabled(MiuixComponent.CHIPS)) {
        MiuixMaterialChip(false, onClick, modifier, enabled, icon, null, label)
        return
    }
    if (!glassEnabled(GlassComponent.CHIPS)) {
        M3SuggestionChip(onClick, label, modifier, enabled, icon, shape, colors, elevation, border, horizontalArrangement, contentPadding, interactionSource)
        return
    }
    GlassChip(
        selected = false,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = icon,
        trailingIcon = null,
        containerColor = colors.containerColor,
        labelColor = if (enabled) colors.labelColor else colors.disabledLabelColor,
        iconColor = if (enabled) colors.leadingIconContentColor else colors.disabledLeadingIconContentColor,
        role = Role.Button,
        interactionSource = interactionSource,
    )
}
