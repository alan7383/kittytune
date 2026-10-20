package com.alananasss.kittytune.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
    labelProvider: @Composable (T) -> Unit,
    iconProvider: (@Composable (T) -> Unit)? = null
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val rowModifier = if (fillMaxWidth) {
        modifier.fillMaxWidth().padding(vertical = 4.dp)
    } else {
        modifier.padding(vertical = 4.dp)
    }
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
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
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
