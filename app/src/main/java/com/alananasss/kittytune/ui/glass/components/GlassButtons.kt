package com.alananasss.kittytune.ui.glass.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.ui.glass.GlassLevel
import com.alananasss.kittytune.ui.glass.GlassShadow
import com.alananasss.kittytune.ui.glass.LocalGlassFlattened
import com.alananasss.kittytune.ui.glass.glass
import com.alananasss.kittytune.ui.glass.rememberGlassHighlight
import com.kyant.shapes.Capsule

/**
 * How a glass button presents itself at rest.
 */
enum class GlassButtonStyle {
    /** Tinted with the accent colour: the primary action. */
    Prominent,

    /** Regular glass material. */
    Regular,

    /** Only the rim is visible; the material appears under the finger. */
    Clear,

    /** Nothing at rest. Glass materialises while pressed, like an iOS toolbar item. */
    Borderless,
}

/**
 * The Liquid Glass button: a capsule of glass that swells and leans under the finger, with a light
 * that tracks the touch. Mirrors the reference LiquidButton of the Backdrop catalog.
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: GlassButtonStyle = GlassButtonStyle.Regular,
    shape: Shape = Capsule(),
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    contentColor: Color = LocalContentColor.current,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
    minWidth: Dp = 56.dp,
    minHeight: Dp = 40.dp,
    interactionSource: MutableInteractionSource? = null,
    role: Role = Role.Button,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val flattened = LocalGlassFlattened.current
    val highlight = rememberGlassHighlight(enabled && !flattened)

    val pressed by interaction.collectIsPressedAsState()
    val pressFallback by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(0.5f, 300f, 0.001f),
        label = "glassPress"
    )
    val visibility: (() -> Float)? = when {
        style == GlassButtonStyle.Borderless -> { { highlight?.pressProgress ?: pressFallback } }
        !enabled -> { { 0.6f } }
        else -> null
    }

    val level = when (style) {
        GlassButtonStyle.Clear, GlassButtonStyle.Borderless -> GlassLevel.Clear
        else -> GlassLevel.Regular
    }

    Row(
        modifier
            .defaultMinSize(minWidth = minWidth, minHeight = minHeight)
            .then(
                if (flattened) {
                    Modifier
                } else {
                    Modifier.glass(
                        shape = shape,
                        level = level,
                        tint = if (style == GlassButtonStyle.Prominent && enabled) tint else Color.Unspecified,
                        surfaceColor = surfaceColor,
                        highlight = highlight,
                        shadow = if (style == GlassButtonStyle.Prominent || style == GlassButtonStyle.Regular) GlassShadow.Soft else GlassShadow.None,
                        visibility = visibility,
                    )
                }
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = role,
                onClick = onClick
            )
            .then(highlight?.gestureModifier ?: Modifier)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides contentColor,
            LocalTextStyle provides MaterialTheme.typography.labelLarge,
        ) {
            content()
        }
    }
}

/**
 * A round glass icon button. [style] picks the material the same way as [GlassButton]; the default
 * [GlassButtonStyle.Borderless] is the toolbar look: just the icon, glass blooms when pressed.
 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: GlassButtonStyle = GlassButtonStyle.Borderless,
    shape: Shape = CircleShape,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    contentColor: Color = LocalContentColor.current,
    containerSize: Dp = 40.dp,
    interactionSource: MutableInteractionSource? = null,
    role: Role = Role.Button,
    content: @Composable () -> Unit,
) {
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val flattened = LocalGlassFlattened.current
    val highlight = rememberGlassHighlight(enabled && !flattened)

    val pressed by interaction.collectIsPressedAsState()
    val pressFallback by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(0.5f, 300f, 0.001f),
        label = "glassIconPress"
    )
    val visibility: (() -> Float)? = when {
        style == GlassButtonStyle.Borderless || flattened -> { { highlight?.pressProgress ?: pressFallback } }
        !enabled -> { { 0.6f } }
        else -> null
    }

    Box(
        modifier
            .minimumInteractiveComponentSize()
            .size(containerSize)
            .glass(
                shape = shape,
                level = if (style == GlassButtonStyle.Borderless || style == GlassButtonStyle.Clear) GlassLevel.Clear else GlassLevel.Regular,
                tint = if (style == GlassButtonStyle.Prominent && enabled) tint else Color.Unspecified,
                surfaceColor = surfaceColor,
                highlight = highlight,
                pressExpansion = 6.dp,
                shadow = if (style == GlassButtonStyle.Prominent || style == GlassButtonStyle.Regular) GlassShadow.Soft else GlassShadow.None,
                visibility = visibility,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = role,
                onClick = onClick
            )
            .then(highlight?.gestureModifier ?: Modifier),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor, content = content)
    }
}
