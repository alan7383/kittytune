package com.alananasss.kittytune.ui.login

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

// The welcome screen's expressive waves.
//
// The old treatment was one thick flat sine and one thin one running at the same phase - two
// parallel lines that only differed in colour. This version earns the "expressive" name it
// sits under:
//
//  - each line is a **compound wave**: a fundamental plus a 2.7× overtone at ~40 % amplitude,
//    so the crest leans forward and the trough rounds off the way a real audio wave drawn by
//    hand does, instead of the metronome look of a pure sine;
//  - the lines run at **different phase speeds** (a 1 : 1.6 : 2.4 ratio), so they slowly overtake
//    each other and the composition never repeats exactly;
//  - amplitude breathes on a slow envelope, ±25 % around the base, offset per line;
//  - the fill under the front line grounds the composition against the artwork above it.
//
// Kept as a drop-in for the old pair of [SineWaveLine]s: same modifier contract, colors passed
// in, animation internal.
@Composable
fun ExpressiveWaves(
    modifier: Modifier = Modifier,
    // Colour of the filled area and the thick front line - usually `surface`.
    baseColor: Color,
    // Colour of the accent line - usually `primary`.
    accentColor: Color,
    // Colour of the second accent line - usually `tertiary`.
    secondaryAccentColor: Color = accentColor,
    animate: Boolean = true,
    strokeWidth: Dp = 4.dp,
    amplitude: Dp = 4.dp,
    waves: Float = 7.6f,
    samples: Int = 320,
) {
    val density = LocalDensity.current

    // Three phase clocks, deliberately incommensurate so the group takes a long time to
    // return to its starting relationship - the "never the same twice" property.
    val transition = rememberInfiniteTransition(label = "ExpressiveWaves")
    val phaseFront by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart),
        label = "wave_phase_front",
    )
    val phaseAccent by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(3300, easing = LinearEasing), RepeatMode.Restart),
        label = "wave_phase_accent",
    )
    val phaseSecond by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(7900, easing = LinearEasing), RepeatMode.Restart),
        label = "wave_phase_second",
    )
    val breath by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "wave_breath",
    )

    val strokePx = with(density) { strokeWidth.toPx() }
    val ampPx = with(density) { amplitude.toPx() }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val centerY = h * 0.42f
        if (w <= 0f || samples < 2) return@Canvas
        val step = w / (samples - 1)

        fun compoundWave(phase: Float, ampScale: Float, freqScale: Float): (Float) -> Float {
            return { x ->
                val base = (x / w) * (2f * PI.toFloat() * waves * freqScale) + phase
                // Fundamental + overtone: the overtone's frequency ratio (2.7) and amplitude
                // ratio (0.38) are what bend the crest off the pure-sine look.
                val y = sin(base) + 0.38f * sin(base * 2.7f + 0.6f)
                centerY + ampPx * ampScale * y * if (animate) breath else 1f
            }
        }

        // 1. Fill under the front line, so the waves read as a landscape, not wires.
        val frontWave = compoundWave(phaseFront, 1.35f, 1f)
        val fill = Path().apply {
            moveTo(0f, frontWave(0f))
            for (i in 1 until samples) lineTo(i * step, frontWave(i * step))
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(fill, baseColor)

        // 2. The thick front line riding exactly on the fill's edge.
        drawCompoundLine(frontWave, step, samples, baseColor, strokePx * 3.6f)

        // 3. Two accent lines behind it, thinner, offset in phase and vertical position.
        drawCompoundLine(
            compoundWave(phaseAccent + 1.1f, 1.0f, 1.18f), step, samples,
            accentColor, strokePx, yOffset = -strokePx * 2.2f,
        )
        drawCompoundLine(
            compoundWave(phaseSecond + 2.3f, 0.8f, 1.43f), step, samples,
            secondaryAccentColor, strokePx * 0.8f, yOffset = strokePx * 2.6f,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCompoundLine(
    wave: (Float) -> Float,
    step: Float,
    samples: Int,
    color: Color,
    strokeWidth: Float,
    yOffset: Float = 0f,
) {
    val path = Path().apply {
        moveTo(0f, wave(0f) + yOffset)
        for (i in 1 until samples) lineTo(i * step, wave(i * step) + yOffset)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}
