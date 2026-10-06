package com.alananasss.kittytune.ui.navigation

import android.os.Build
import android.view.RoundedCorner
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/**
 * The device's real rounded-corner radius in pixels, or `0f` when there is none.
 *
 * `ActionBarLayout.onApplyWindowInsets` caches the four window radii exactly this way (`0f` when
 * `getRoundedCorner()` returns null, and untouched below API 31), and
 * `getPredictiveBackStartCornerRadius()` feeds the max of them to the predictive-back animation.
 */
@Composable
fun getRealScreenCornerRadiusPx(): Float {
    val context = LocalContext.current

    return remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val windowManager = context.getSystemService(android.content.Context.WINDOW_SERVICE)
                    as android.view.WindowManager
            val insets = windowManager.currentWindowMetrics.windowInsets
            var max = 0f
            for (position in intArrayOf(
                RoundedCorner.POSITION_TOP_LEFT,
                RoundedCorner.POSITION_TOP_RIGHT,
                RoundedCorner.POSITION_BOTTOM_RIGHT,
                RoundedCorner.POSITION_BOTTOM_LEFT
            )) {
                val radius = (insets.getRoundedCorner(position)?.radius ?: 0).toFloat()
                if (radius > max) max = radius
            }
            max
        } else {
            0f
        }
    }
}

/**
 * Retrieves the device's physical screen corner radius (Android 12+).
 * Falls back to 28dp for older devices.
 */
@Composable
fun getScreenCornerRadius(): Dp {
    val context = LocalContext.current
    val density = LocalDensity.current

    return remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val windowManager = context.getSystemService(android.content.Context.WINDOW_SERVICE)
                    as android.view.WindowManager
            val windowInsets = windowManager.currentWindowMetrics.windowInsets
            val roundedCorner = windowInsets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
            if (roundedCorner != null) {
                with(density) { roundedCorner.radius.toDp() }
            } else {
                28.dp
            }
        } else {
            28.dp
        }
    }
}

/**
 * Wraps a destination with the AyuGram predictive-back animation.
 *
 * This is the Compose counterpart of `ActionBarLayout.drawChild()`: the current screen
 * (`containerView`, see [AyuScreenRole.Closing]) and the screen underneath it
 * (`containerViewBack`, see [AyuScreenRole.Entering]) are both translated/scaled/clipped to the
 * rects computed by [AyuPredictiveBackGeometry], and a black scrim is drawn between them.
 *
 * The composable structure never changes: the scrim and the content are always the same two
 * children of the same box, and only the layer's values differ between the back animation and a
 * plain forward navigation. Branching on the animation state would move `content` to a different
 * parent, which makes Compose dispose and recreate the whole destination subtree (every `remember`
 * inside it, every scroll state, every in-flight animation) at the start and at the end of every
 * back gesture.
 */
@Composable
fun AnimatedVisibilityScope.ClippedScreen(
    entryId: String,
    content: @Composable () -> Unit
) {
    val deviceCornerRadius = getScreenCornerRadius()
    val deviceCornerRadiusPx = getRealScreenCornerRadiusPx()
    val density = LocalDensity.current
    // `Theme.isCurrentThemeDark()`: the *effective* theme, not the system one — KittyTune can force
    // light/dark through `AppThemeMode`, and `isSystemInDarkTheme()` would then pick the wrong
    // scrim strength (0.2 instead of 0.8).
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val backState = LocalAyuBackState.current
    // `ActionBarLayout.isRightLayout`: only relevant for the entering screen's left corners.
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    // `ActionBarLayout.getForegroundView()` / `getBackgroundView()` for the Compose world: the
    // destination being dismissed, and the one that sits underneath it.
    val role = backState.roleFor(entryId)
    // Deliberately not gated on the phase: the roles stay bound until the next navigation so the
    // dismissed screen cannot flash back while `AnimatedContent` finishes disposing it.
    val animated = role != AyuScreenRole.None
    val frame = if (animated) backState.frame(role, isDark) else AyuScreenFrame.Identity

    // Corner radius used when no back animation is running (forward navigation only).
    val restRadius by transition.animateDp(
        transitionSpec = {
            when {
                EnterExitState.PreEnter isTransitioningTo EnterExitState.Visible -> {
                    keyframes {
                        durationMillis = 400
                        deviceCornerRadius at 0
                        deviceCornerRadius at 280
                        0.dp at 400
                    }
                }

                EnterExitState.Visible isTransitioningTo EnterExitState.PostExit -> {
                    tween(durationMillis = 380)
                }

                else -> tween(durationMillis = 380)
            }
        },
        label = "screenCornerRadius"
    ) { state ->
        when (state) {
            // Only a *forward* navigation reveals the device corners. The screen revealed by a
            // back animation is already in place, so giving it rounded corners here made it flash
            // a rounded rectangle for one frame when the animation ended.
            EnterExitState.PreEnter ->
                if (backState.isPushTarget(entryId)) deviceCornerRadius else 0.dp

            EnterExitState.Visible -> 0.dp
            EnterExitState.PostExit -> deviceCornerRadius
        }
    }
    val restRadiusPx = with(density) { restRadius.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { backState.updateSize(it.width, it.height, deviceCornerRadiusPx) }
    ) {
        // Drawn *under* the closing screen and *over* the screen underneath it, exactly like the
        // `scrimPaint` rectangle in `ActionBarLayout.drawChild()`. Always present (alpha 0 when
        // idle) so the composition structure stays stable.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = frame.scrimAlpha))
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (animated) {
                        translationX = frame.translationX
                        translationY = frame.translationY
                        scaleX = frame.scale
                        scaleY = frame.scale
                        transformOrigin = if (frame.centerPivot) {
                            TransformOrigin.Center
                        } else {
                            TransformOrigin(0f, 0f)
                        }
                        alpha = frame.alpha
                        val radius = if (frame.clip) frame.cornerRadius else 0f
                        clip = radius > 0f
                        shape = if (role == AyuScreenRole.PushEntering && isRtl) {
                            pushEnteringShape(
                                radius,
                                AyuBack.pushRtlLeftFactor(density.density, frame.translationX)
                            )
                        } else {
                            roundedShape(radius)
                        }
                    } else {
                        clip = restRadiusPx > 0f
                        shape = roundedShape(restRadiusPx)
                    }
                }
        ) {
            content()
        }
    }
}

/**
 * Pixel-exact rounded clip shape. `Outline` is not a `Shape`, so build one; going through `Dp`
 * would quantise the radius, and AyuGram divides it by the scale for a reason.
 */
/**
 * `ActionBarLayout.drawChild` in RTL (`isRightLayout`): the entering screen keeps the full device
 * radius on its right corners while only its *left* corners fade with the slide.
 */
private fun pushEnteringShape(radiusPx: Float, leftFactor: Float): Shape =
    if (radiusPx <= 0f || leftFactor >= 1f) {
        roundedShape(radiusPx)
    } else {
        val left = radiusPx * leftFactor
        object : Shape {
            override fun createOutline(
                size: androidx.compose.ui.geometry.Size,
                layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                density: androidx.compose.ui.unit.Density
            ): Outline = Outline.Rounded(
                RoundRect(
                    androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height),
                    topLeft = CornerRadius(left),
                    topRight = CornerRadius(radiusPx),
                    bottomRight = CornerRadius(radiusPx),
                    bottomLeft = CornerRadius(left)
                )
            )
        }
    }

private fun roundedShape(radiusPx: Float): Shape =
    if (radiusPx <= 0f) {
        RectangleShape
    } else {
        object : Shape {
            override fun createOutline(
                size: androidx.compose.ui.geometry.Size,
                layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                density: androidx.compose.ui.unit.Density
            ): Outline = Outline.Rounded(
                RoundRect(0f, 0f, size.width, size.height, radiusX = radiusPx, radiusY = radiusPx)
            )
        }
    }

fun NavGraphBuilder.clippedComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit
) {
    composable(route, arguments) { entry ->
        ClippedScreen(entry.id) {
            content(entry)
        }
    }
}