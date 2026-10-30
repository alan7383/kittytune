package com.alananasss.kittytune.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.data.local.UiDesignStyle
import com.alananasss.kittytune.ui.liquidglass.LocalLiquidGlassConfig
import com.alananasss.kittytune.ui.liquidglass.LocalUiDesignStyle

/**
 * A Backdrop-style liquid icon button whose glass body is the visible button, not Material's
 * 48 dp touch target. Material draws its icon button body at 40 dp inside a 48 dp touch target;
 * applying the glass to the outer node made the glass look larger than the button. Here the glass
 * lives on a 40 dp (or [size]) body centred in the touch target, with the same press spring,
 * finger offset, scale and moving highlight as the reference LiquidButton.
 */
@Composable
fun LiquidIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    tint: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    interactionSource: MutableInteractionSource? = null,
    size: Dp = 40.dp,
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val resolvedContentColor = if (contentColor.isSpecified) contentColor else LocalContentColor.current
    val liquid = LocalUiDesignStyle.current == UiDesignStyle.LIQUID_GLASS && LocalLiquidGlassConfig.current.applyToIconButtons
    val bodyGlass = if (liquid) {
        Modifier.interactiveGlassButton(
            shape = shape,
            tint = tint,
            emphasis = GlassEmphasis.Prominent,
            enabled = enabled,
            shadowElevation = 6.dp
        )
    } else {
        val bgTint = if (tint.isSpecified && tint.alpha > 0.01f) tint else MaterialTheme.colorScheme.surfaceVariant
        Modifier.background(bgTint, shape)
    }

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = size, minHeight = size)
                .then(bodyGlass)
                .then(if (border != null) Modifier.border(border, shape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(LocalContentColor provides resolvedContentColor) {
                content()
            }
        }
    }
}
