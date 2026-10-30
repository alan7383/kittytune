package com.alananasss.kittytune.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import com.alananasss.kittytune.data.local.UiDesignStyle
import com.alananasss.kittytune.ui.liquidglass.LocalBackdrop
import com.alananasss.kittytune.ui.design.GlassEmphasis
import com.alananasss.kittytune.ui.design.interactiveGlassButton
import com.alananasss.kittytune.ui.liquidglass.LocalLiquidGlassConfig
import com.alananasss.kittytune.ui.liquidglass.LocalUiDesignStyle
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.RoundedRectangle

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ExpressiveConnectedButtonGroup(
    options: List<T>,
    selectedOption: T?,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    fillMaxWidth: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    iconSpacing: Dp = 4.dp,
    checkedContainerColor: Color = MaterialTheme.colorScheme.primary,
    uncheckedContainerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    checkedContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    uncheckedContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    border: BorderStroke? = null,
    /** Set false when the caller plays its own, more meaningful preview on selection. */
    hapticOnSelect: Boolean = true,
    labelProvider: @Composable (T) -> Unit,
    iconProvider: (@Composable (T) -> Unit)? = null
) {
    val haptics = rememberAppHaptics()
    val designStyle = LocalUiDesignStyle.current
    val liquidConfig = LocalLiquidGlassConfig.current

    val rowModifier = if (fillMaxWidth) {
        modifier.fillMaxWidth().padding(vertical = 4.dp)
    } else {
        modifier.padding(vertical = 4.dp)
    }

    if (designStyle == UiDesignStyle.LIQUID_GLASS && liquidConfig.applyToButtons) {
        val fallbackBackdrop = rememberCanvasBackdrop {
            drawRect(Color.DarkGray.copy(alpha = 0.2f))
        }
        val backdrop = LocalBackdrop.current ?: fallbackBackdrop
        val groupCornerRadius = (24f * liquidConfig.cornerRadiusFactor).dp
        Row(
            modifier = rowModifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(groupCornerRadius) },
                    effects = {
                        if (liquidConfig.vibrancyEnabled) {
                            vibrancy()
                        }
                        blur(liquidConfig.blurRadiusDp.coerceAtLeast(4f).dp.toPx())
                        lens(
                            refractionHeight = (liquidConfig.refractionHeightDp * 0.4f).dp.toPx(),
                            refractionAmount = (liquidConfig.refractionAmountDp * 0.4f).dp.toPx(),
                            depthEffect = liquidConfig.depthEffect,
                            chromaticAberration = liquidConfig.chromaticAberration > 0f
                        )
                    },
                    highlight = { Highlight.Default.copy(alpha = (0.4f * liquidConfig.highlightIntensity).coerceIn(0f, 1f)) },
                    onDrawSurface = {
                        drawRect(uncheckedContainerColor.copy(alpha = liquidConfig.surfaceAlpha))
                    }
                )
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEach { option ->
                val isChecked = selectedOption != null && selectedOption == option
                val btnModifier = if (fillMaxWidth) Modifier.weight(1f) else Modifier
                val itemCornerRadius = (20f * liquidConfig.cornerRadiusFactor).dp
                val itemShape = RoundedCornerShape(itemCornerRadius)
                val itemContentColor by animateColorAsState(
                    targetValue = if (isChecked) checkedContentColor else uncheckedContentColor,
                    label = "liquid_btn_content"
                )
                Box(
                    modifier = btnModifier
                        .defaultMinSize(minHeight = 40.dp)
                        .clip(itemShape)
                        .clickable {
                            if (hapticOnSelect) haptics.perform(HapticKind.LIGHT)
                            onOptionSelected(option)
                        }
                        .then(
                            if (isChecked) {
                                // The selected segment is a real liquid glass control, not a
                                // flat colour pill: same press/lens animation as the buttons.
                                Modifier.interactiveGlassButton(
                                    shape = itemShape,
                                    tint = checkedContainerColor,
                                    emphasis = GlassEmphasis.Prominent,
                                    shadowElevation = 0.dp
                                )
                            } else {
                                Modifier
                            }
                        )
                        .padding(contentPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CompositionLocalProvider(
                        androidx.compose.material3.LocalContentColor provides itemContentColor
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (iconProvider != null) {
                                iconProvider(option)
                                Spacer(Modifier.width(iconSpacing))
                            }
                            labelProvider(option)
                        }
                    }
                }
            }
        }
    } else {
        Row(
            modifier = rowModifier,
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            options.forEachIndexed { index, option ->
                val buttonModifier = if (fillMaxWidth) Modifier.weight(1f) else Modifier
                val isChecked = selectedOption != null && selectedOption == option
                val containerColor by animateColorAsState(
                    targetValue = if (isChecked) checkedContainerColor else uncheckedContainerColor,
                    animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                    label = "toggle_container_color"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isChecked) checkedContentColor else uncheckedContentColor,
                    animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                    label = "toggle_content_color"
                )

                ToggleButton(
                    checked = isChecked,
                    onCheckedChange = {
                        if (hapticOnSelect) haptics.perform(HapticKind.LIGHT)
                        onOptionSelected(option)
                    },
                    modifier = buttonModifier,
                    contentPadding = contentPadding,
                    border = border,
                    colors = ToggleButtonDefaults.colors(
                        containerColor = containerColor,
                        contentColor = contentColor,
                        checkedContainerColor = containerColor,
                        checkedContentColor = contentColor
                    ),
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (iconProvider != null) {
                            iconProvider(option)
                            Spacer(Modifier.width(iconSpacing))
                        }
                        labelProvider(option)
                    }
                }
            }
        }
    }
}
