package com.alananasss.kittytune.ui.liquidglass.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.alananasss.kittytune.ui.liquidglass.LocalLiquidGlassConfig
import com.alananasss.kittytune.ui.liquidglass.utils.InteractiveHighlight
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

@Composable
fun KittyLiquidButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    isInteractive: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    content: @Composable RowScope.() -> Unit
) {
    val animationScope = rememberCoroutineScope()
    val liquidConfig = LocalLiquidGlassConfig.current

    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope
        )
    }

    val buttonShape = { RoundedRectangle((24f * liquidConfig.cornerRadiusFactor).dp) }

    Row(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = buttonShape,
                effects = {
                    if (liquidConfig.vibrancyEnabled) {
                        vibrancy()
                    }
                    blur((liquidConfig.blurRadiusDp * 0.25f).coerceAtLeast(0f).dp.toPx())
                    lens(
                        refractionHeight = (liquidConfig.refractionHeightDp * 0.5f).dp.toPx(),
                        refractionAmount = liquidConfig.refractionAmountDp.dp.toPx(),
                        depthEffect = liquidConfig.depthEffect,
                        chromaticAberration = liquidConfig.chromaticAberration > 0f
                    )
                },
                highlight = {
                    val progress = interactiveHighlight.pressProgress
                    Highlight.Default.copy(alpha = (0.7f * progress * liquidConfig.highlightIntensity).coerceIn(0f, 1f))
                },
                shadow = if (liquidConfig.shadowsEnabled) {
                    {
                        val progress = interactiveHighlight.pressProgress
                        Shadow(radius = 8.dp, alpha = progress)
                    }
                } else null,
                layerBlock = if (isInteractive && liquidConfig.interactiveBounce) {
                    {
                        val width = size.width
                        val height = size.height

                        val progress = interactiveHighlight.pressProgress
                        val scale = lerp(1f, 1f + 4f.dp.toPx() / size.height, progress)

                        val maxOffset = size.minDimension
                        val initialDerivative = 0.05f
                        val offset = interactiveHighlight.offset
                        translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                        translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

                        val maxDragScale = 4f.dp.toPx() / size.height
                        val offsetAngle = atan2(offset.y, offset.x)
                        scaleX =
                            scale +
                                    maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                                    (width / height).fastCoerceAtMost(1f)
                        scaleY =
                            scale +
                                    maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                                    (height / width).fastCoerceAtMost(1f)
                    }
                } else {
                    null
                },
                onDrawSurface = {
                    if (tint.isSpecified) {
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = 0.75f))
                    }
                    if (surfaceColor.isSpecified) {
                        drawRect(surfaceColor)
                    }
                }
            )
            .clickable(
                interactionSource = null,
                indication = if (isInteractive) null else LocalIndication.current,
                role = Role.Button,
                onClick = onClick
            )
            .then(
                if (isInteractive) {
                    Modifier
                        .then(interactiveHighlight.modifier)
                        .then(interactiveHighlight.gestureModifier)
                } else {
                    Modifier
                }
            )
            .defaultMinSize(minHeight = 48.dp)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
