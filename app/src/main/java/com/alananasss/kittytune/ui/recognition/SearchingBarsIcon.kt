package com.alananasss.kittytune.ui.recognition

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

/**
 * Pixel-perfect reproduction of `avd_nowplaying_searching` (+ `vd_nowplaying_search`).
 *
 * The icon has 4 elements drawn in a 24×24 viewport:
 *
 *   ● circle  (musicNote_main)  – filled circle, cx=6.5   cy=17.44  r=5.5
 *   | bar1    (post1_main)      – tallest stroke,  cx=10.5  cy=10.64  h=12.375
 *   | bar2    (post2_main)      – medium stroke,   cx=15.5  cy=9.5    h=9.0
 *   · bar3    (post3_main)      – short stroke,    cx=20.5  cy=13.0   h=3.0
 *
 * All strokes have strokeWidth=3 (= 12 × scale 0.25), strokeLineCap=round.
 * All 4 elements share the same bar width (w=3 units in the 24-unit viewport).
 *
 * Animation timings (from $avd_nowplaying_searching__0/1/2/3.xml):
 *   translateY oscillates between 9.95 and 11.95 (amplitude ±1 viewport unit).
 *   – bar1 + circle  half-cycle = 375 ms  (750 ms full), intro=367 ms
 *   – bar2           half-cycle = 500 ms  (1000ms full), intro=500 ms
 *   – bar3           half-cycle = 750 ms  (1500ms full), intro=750 ms
 *   Interpolator: ease = cubic-bezier(0.5, 0, 0.5, 1)
 *
 * The circle and bar1 share the same translateY animation (same xml timings),
 * so they move in perfect sync.
 */

/** ease interpolator from the original AVD: cubic-bezier(0.5, 0, 0.5, 1). */
private val EaseInterp = CubicBezierEasing(0.5f, 0f, 0.5f, 1f)

// Positions in the 24-unit viewport (as fractions of canvas size)
private const val VPW = 24f

// bar3 (rightmost, shortest)
private const val B3_CX = 20.5f / VPW
private const val B3_CY = 13.0f / VPW
private const val B3_H  = 3.0f  / VPW

// bar2 (middle)
private const val B2_CX = 15.5f / VPW
private const val B2_CY = 9.5f  / VPW
private const val B2_H  = 9.0f  / VPW

// bar1 (tallest, trimPathEnd=0.9 → effective height 12.375)
private const val B1_CX = 10.5f  / VPW
private const val B1_CY = 10.637f / VPW
private const val B1_H  = 12.375f / VPW

// circle (musicNote)
private const val CIRCLE_CX = 6.5f   / VPW
private const val CIRCLE_CY = 17.436f / VPW
private const val CIRCLE_R  = 5.5f   / VPW

// Shared bar / circle width (strokeWidth=12 × scale 0.25 = 3, then /24)
private const val BAR_W = 3f / VPW

// Bounce: translateY oscillates between 9.95 and 11.95 (±1 unit in viewport)
private const val BOUNCE_AMPLITUDE = 1f / VPW

@Composable
fun SearchingBarsIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    // Phase for each bouncing group (in viewport units, centred at 0).
    // Positive = moved down, negative = moved up.
    val dY1 = remember { Animatable(0f) } // bar1 + circle (same phase, from AVD file)
    val dY2 = remember { Animatable(0f) } // bar2
    val dY3 = remember { Animatable(0f) } // bar3

    LaunchedEffect(Unit) {
        // bar1 & circle: intro 367ms up, then 750ms full cycles
        launch {
            dY1.animateTo(-BOUNCE_AMPLITUDE, tween(367, easing = EaseInterp))
            while (true) {
                dY1.animateTo(+BOUNCE_AMPLITUDE, tween(375, easing = EaseInterp))
                dY1.animateTo(-BOUNCE_AMPLITUDE, tween(375, easing = EaseInterp))
            }
        }

        // bar2: intro 500ms down, then 1000ms full cycles (opposite phase to bar1)
        launch {
            dY2.animateTo(+BOUNCE_AMPLITUDE, tween(500, easing = EaseInterp))
            while (true) {
                dY2.animateTo(-BOUNCE_AMPLITUDE, tween(500, easing = EaseInterp))
                dY2.animateTo(+BOUNCE_AMPLITUDE, tween(500, easing = EaseInterp))
            }
        }

        // bar3: intro 750ms up, then 1500ms full cycles
        launch {
            dY3.animateTo(-BOUNCE_AMPLITUDE, tween(750, easing = EaseInterp))
            while (true) {
                dY3.animateTo(+BOUNCE_AMPLITUDE, tween(750, easing = EaseInterp))
                dY3.animateTo(-BOUNCE_AMPLITUDE, tween(750, easing = EaseInterp))
            }
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val barW = w * BAR_W
        val cornerR = CornerRadius(barW / 2f, barW / 2f)

        // dy values scaled to canvas pixels
        val dy1 = dY1.value * h
        val dy2 = dY2.value * h
        val dy3 = dY3.value * h

        // --- Circle (musicNote_main) – bounces with bar1 ---
        val circleCx = w * CIRCLE_CX
        val circleCy = h * CIRCLE_CY + dy1
        val circleR  = w * CIRCLE_R
        drawCircle(color = color, radius = circleR, center = Offset(circleCx, circleCy))

        // --- Bar 1 (post1, tallest) ---
        val b1CxPx = w * B1_CX
        val b1HalfH = h * B1_H / 2f
        val b1CyPx  = h * B1_CY + dy1
        drawRoundRect(
            color = color,
            topLeft = Offset(b1CxPx - barW / 2f, b1CyPx - b1HalfH),
            size = Size(barW, b1HalfH * 2f),
            cornerRadius = cornerR,
        )

        // --- Bar 2 (post2, medium) ---
        val b2CxPx = w * B2_CX
        val b2HalfH = h * B2_H / 2f
        val b2CyPx  = h * B2_CY + dy2
        drawRoundRect(
            color = color,
            topLeft = Offset(b2CxPx - barW / 2f, b2CyPx - b2HalfH),
            size = Size(barW, b2HalfH * 2f),
            cornerRadius = cornerR,
        )

        // --- Bar 3 (post3, shortest) ---
        val b3CxPx = w * B3_CX
        val b3HalfH = h * B3_H / 2f
        val b3CyPx  = h * B3_CY + dy3
        drawRoundRect(
            color = color,
            topLeft = Offset(b3CxPx - barW / 2f, b3CyPx - b3HalfH),
            size = Size(barW, b3HalfH * 2f),
            cornerRadius = cornerR,
        )
    }
}
