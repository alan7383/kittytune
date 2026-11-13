package com.alananasss.kittytune.ui.player.lyrics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

// Visual shown over a long instrumental gap in the line-by-line lyrics view.
//
// Two looks, both ported from apps the user liked:
//  - [IntervalIndicatorCircle]: The classic wavy circular progress ring (Material3 Expressive
//    `CircularWavyProgressIndicator`), fading/revealing in 200 ms.
//  - [InterludeNoteWave]: A breathing music note next to a wavy linear
//    progress bar.
//
// Both take the playback position as a provider lambda and derive their animated values in
// `derivedStateOf`, so only the indicator whose gap is actually playing recomposes on each
// position tick - the rest of the list stays still, exactly like the lyric lines around them.
object InstrumentalIndicators {

    // The last 650 ms of the gap are trimmed so the indicator is gone before the vocal.
    const val EARLY_CUT_MS = 650L
}

// The classic interval indicator: a wavy circular ring whose sweep follows the gap's progress.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IntervalIndicatorCircle(
    gapStartMs: Long,
    gapEndMs: Long,
    positionProvider: () -> Long,
    color: Color,
    modifier: Modifier = Modifier
) {
    val visibleState = remember(gapStartMs, gapEndMs) {
        derivedStateOf { positionProvider() in gapStartMs..(gapEndMs - InstrumentalIndicators.EARLY_CUT_MS) }
    }
    val progressState = remember(gapStartMs, gapEndMs) {
        derivedStateOf {
            val span = (gapEndMs - InstrumentalIndicators.EARLY_CUT_MS - gapStartMs).coerceAtLeast(1L)
            ((positionProvider() - gapStartMs).toFloat() / span).coerceIn(0f, 1f)
        }
    }

    val alpha = remember { Animatable(0f) }
    val rowHeight = remember { Animatable(0f) }

    LaunchedEffect(visibleState.value) {
        if (visibleState.value) {
            rowHeight.animateTo(1f, tween(200))
            alpha.animateTo(1f, tween(200))
        } else {
            alpha.animateTo(0f, tween(200))
            rowHeight.animateTo(0f, tween(200))
        }
    }

    val targetHeight = 72.dp

    val animatedProgress by animateFloatAsState(
        targetValue = progressState.value,
        animationSpec = tween(durationMillis = 100, easing = LinearEasing),
        label = "intervalProgress"
    )

    Box(
        modifier = modifier
            .height(targetHeight * rowHeight.value)
            .padding(top = 16.dp * rowHeight.value)
            .graphicsLayer {
                this.alpha = alpha.value
                clip = true
            },
        contentAlignment = Alignment.Center
    ) {
        CircularWavyProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .size(36.dp)
                .alpha(alpha.value),
            color = color,
            trackColor = color.copy(alpha = 0.2f),
        )
    }
}

// The note+wave interlude item: a breathing music note icon plus a wavy linear progress bar.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InterludeNoteWave(
    startMs: Long,
    endMs: Long,
    positionProvider: () -> Long,
    accentColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val effectiveEnd = endMs - InstrumentalIndicators.EARLY_CUT_MS
    val duration = (effectiveEnd - startMs).coerceAtLeast(1L)

    val isActiveState = remember(startMs, endMs) {
        derivedStateOf { positionProvider() in startMs..effectiveEnd }
    }
    val isActive = isActiveState.value

    val rawProgressState = remember(startMs, endMs) {
        derivedStateOf {
            if (positionProvider() in startMs..effectiveEnd) {
                ((positionProvider() - startMs).toFloat() / duration).coerceIn(0f, 1f)
            } else {
                0f
            }
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = rawProgressState.value,
        animationSpec = WavyProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "InterludeProgress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "InterludeBreathing")
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BreathScale"
    )

    val iconAlpha by animateFloatAsState(
        targetValue = if (isActive) 0.85f else 0.25f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "InterludeAlpha"
    )

    val density = LocalDensity.current
    val thickStroke = remember(density) {
        Stroke(
            width = with(density) { 4.dp.toPx() },
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 24.dp, top = 24.dp, bottom = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = "Interlude",
            tint = accentColor.copy(alpha = iconAlpha),
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer {
                    scaleX = if (isActive) breatheScale else 1f
                    scaleY = if (isActive) breatheScale else 1f
                }
        )

        LinearWavyProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .weight(1f)
                .height(11.dp),
            color = accentColor.copy(alpha = if (isActive) 0.75f else 0.18f),
            trackColor = contentColor.copy(alpha = 0.06f),
            stroke = thickStroke,
            trackStroke = thickStroke,
            wavelength = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
            amplitude = { p -> WavyProgressIndicatorDefaults.indicatorAmplitude(p) * 2.5f }
        )
    }
}

// Convenience wrapper: picks the configured indicator look for a gap.
@Composable
fun InstrumentalGapIndicator(
    kind: InstrumentalIndicatorStyleKind,
    startMs: Long,
    endMs: Long,
    positionProvider: () -> Long,
    accentColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    when (kind) {
        InstrumentalIndicatorStyleKind.DOTS -> Unit
        InstrumentalIndicatorStyleKind.CIRCLE -> IntervalIndicatorCircle(
            gapStartMs = startMs,
            gapEndMs = endMs,
            positionProvider = positionProvider,
            color = accentColor,
            modifier = modifier
        )
        InstrumentalIndicatorStyleKind.NOTE_WAVE -> InterludeNoteWave(
            startMs = startMs,
            endMs = endMs,
            positionProvider = positionProvider,
            accentColor = accentColor,
            contentColor = contentColor,
            modifier = modifier
        )
        InstrumentalIndicatorStyleKind.OFF -> Unit
    }
}

// Local enum so the indicator composables do not depend on the preferences layer.
enum class InstrumentalIndicatorStyleKind { DOTS, CIRCLE, NOTE_WAVE, OFF }
