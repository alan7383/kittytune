package com.alananasss.kittytune.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip as MaterialAssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ChipColors
import androidx.compose.material3.ChipElevation
import androidx.compose.material3.FilterChip as MaterialFilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SelectableChipElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.data.local.UiDesignStyle
import com.alananasss.kittytune.ui.liquidglass.LocalLiquidGlassConfig
import com.alananasss.kittytune.ui.liquidglass.LocalUiDesignStyle

/**
 * Glass chips.
 *
 * Both variants keep Material's label/icon layout and selection semantics. The chip's own container
 * colours become the glass tint; selected chips therefore stay visually distinct while the
 * background behind them refracts.
 */
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
    val liquid = LocalUiDesignStyle.current == UiDesignStyle.LIQUID_GLASS && LocalLiquidGlassConfig.current.applyToChips
    val tint = when {
        !enabled -> colors.disabledContainerColor
        selected -> colors.selectedContainerColor
        else -> colors.containerColor
    }
    val materialColors = if (liquid) colors.copy(
        containerColor = Color.Transparent,
        selectedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        disabledSelectedContainerColor = Color.Transparent
    ) else colors

    val body: @Composable (Modifier) -> Unit = { bodyModifier ->
        MaterialFilterChip(
            selected = selected,
            onClick = onClick,
            label = label,
            modifier = bodyModifier,
            enabled = enabled,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            shape = shape,
            colors = materialColors,
            elevation = if (liquid) null else elevation,
            border = border,
            horizontalArrangement = horizontalArrangement,
            contentPadding = contentPadding,
            interactionSource = interactionSource
        )
    }

    if (liquid) {
        // Material's chip draws its 32 dp body inside a 48 dp touch target. Put the glass on the
        // body, not on the touch target, otherwise it reads as a larger pill behind the chip.
        Box(modifier = modifier) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(vertical = 8.dp)
                    .glassSurface(
                        shape = shape,
                        tint = tint,
                        emphasis = GlassEmphasis.Subtle,
                        shadowElevation = 0.dp
                    )
            ) {}
            body(Modifier)
        }
    } else {
        body(modifier)
    }
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
    val liquid = LocalUiDesignStyle.current == UiDesignStyle.LIQUID_GLASS && LocalLiquidGlassConfig.current.applyToChips
    val tint = if (enabled) colors.containerColor else colors.disabledContainerColor
    val materialColors = if (liquid) colors.copy(
        containerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent
    ) else colors

    val body: @Composable (Modifier) -> Unit = { bodyModifier ->
        MaterialAssistChip(
            onClick = onClick,
            label = label,
            modifier = bodyModifier,
            enabled = enabled,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            shape = shape,
            colors = materialColors,
            elevation = if (liquid) null else elevation,
            border = border,
            horizontalArrangement = horizontalArrangement,
            contentPadding = contentPadding,
            interactionSource = interactionSource
        )
    }

    if (liquid) {
        Box(modifier = modifier) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(vertical = 8.dp)
                    .glassSurface(
                        shape = shape,
                        tint = tint,
                        emphasis = GlassEmphasis.Subtle,
                        shadowElevation = 0.dp
                    )
            ) {}
            body(Modifier)
        }
    } else {
        body(modifier)
    }
}
