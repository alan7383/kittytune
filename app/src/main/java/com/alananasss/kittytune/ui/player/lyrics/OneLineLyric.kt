package com.alananasss.kittytune.ui.player.lyrics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.alananasss.kittytune.ui.design.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.ui.player.LyricsMode
import com.alananasss.kittytune.ui.player.PlayerViewModel

/**
 * The line being sung, in a small card floating above the play button.
 * Keeps the bar height fixed and hovers just above it. Follows untimed text too and wraps long lines.
 */
@Composable
fun FloatingLyricChip(vm: PlayerViewModel, modifier: Modifier = Modifier) {
    val syncedLines = vm.lyricsLines
    val plainText = vm.rawPlainLyrics.orEmpty()
    val plainSpeed = vm.effectivePlainAutoScrollSpeed
    val followsPlain = plainText.isNotBlank() && (vm.lyricsMode == LyricsMode.PLAIN || syncedLines.isEmpty())
    val plainLines = remember(plainText, plainSpeed, followsPlain) {
        if (followsPlain) pacedPlainLines(plainText, plainSpeed) else emptyList()
    }
    val lines = if (followsPlain) plainLines else syncedLines
    val isTimed = (followsPlain || vm.lyricsMode == LyricsMode.SYNCED) && lines.isNotEmpty() && !vm.isLyricsLoading
    val gapPx = with(androidx.compose.ui.platform.LocalDensity.current) { CHIP_GAP.roundToPx() }
    val maxChipPx = with(androidx.compose.ui.platform.LocalDensity.current) { (MAX_LINE_WIDTH + 40.dp).roundToPx() }

    AnimatedVisibility(
        visible = isTimed && vm.oneLineLyricsEnabled,
        enter = fadeIn(tween(220)) + scaleIn(tween(260, easing = FastOutSlowInEasing), initialScale = 0.9f),
        exit = fadeOut(tween(160)) + scaleOut(tween(200), targetScale = 0.9f),
        modifier = modifier.layout { measurable, constraints ->
            val chip = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0, maxWidth = constraints.maxWidth.coerceAtMost(maxChipPx)))
            layout(chip.width, 0) { chip.place(0, -chip.height - gapPx) }
        },
    ) {
        val positionState = rememberSmoothPosition({ vm.uiCurrentPosition }, vm.uiIsPlaying, vm.effectsState.speed)
        val lyricsOffset = if (followsPlain) 0L else vm.lyricsOffset
        val activeIndex by remember(lines, lyricsOffset) {
            derivedStateOf { LyricsUtils.activeLineIndex(lines, (positionState.value + lyricsOffset).toLong()) }
        }
        val active = lines.getOrNull(activeIndex)
        val showsWords = active != null && !active.isInstrumental && active.text.isNotBlank()
        val scheme = MaterialTheme.colorScheme

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = scheme.surfaceContainer.copy(alpha = 0.94f),
            contentColor = scheme.onSurface,
            shadowElevation = 3.dp,
        ) {
            AnimatedContent(
                targetState = if (showsWords) activeIndex else -1,
                transitionSpec = {
                    (fadeIn(tween(200, delayMillis = 50)) + slideInVertically(tween(240)) { it / 2 }) togetherWith
                        (fadeOut(tween(120)) + slideOutVertically(tween(180)) { -it / 2 }) using
                        SizeTransform(clip = false) { _, _ -> spring(stiffness = Spring.StiffnessMediumLow) }
                },
                contentAlignment = Alignment.Center,
                label = "floatingLyric",
            ) { index ->
                val line = lines.getOrNull(index)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = if (line == null) 12.dp else 16.dp, vertical = 8.dp),
                ) {
                    if (line == null) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        SungLine(vm, line.withEvenWords(lines.getOrNull(index + 1)?.startTime), { positionState.value + lyricsOffset }, isEven = line.words.isEmpty())
                    }
                }
            }
        }
    }
}

/** Untimed [text] as lines laid on a timeline at the scroll pace. */
private fun pacedPlainLines(text: String, speed: Float): List<LyricLine> {
    val lineMs = (1000f / (LyricsScrolling.PLAIN_BASE_LINES_PER_SEC * speed.coerceAtLeast(0.1f))).toLong().coerceAtLeast(400L)
    return text.split("\n").mapIndexed { index, raw ->
        val line = raw.trim()
        LyricLine(
            text = line,
            startTime = index * lineMs,
            endTime = (index + 1) * lineMs,
            isInstrumental = line.isEmpty(),
        )
    }
}

@Composable
private fun SungLine(vm: PlayerViewModel, line: LyricLine, position: () -> Float, isEven: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val main = remember(line) { separateBackingVocals(line).first }
    val style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    LyricLineText(
        line = main,
        isActive = true,
        positionMs = position,
        wordSync = vm.isWordSyncEnabled || isEven,
        fillEffect = vm.isAppleMusicEffectEnabled || isEven,
        activeStyle = style,
        inactiveStyle = style,
        activeColor = scheme.onSurface,
        inactiveColor = scheme.onSurfaceVariant,
        unsungColor = scheme.onSurfaceVariant.copy(alpha = 0.55f),
        textAlign = TextAlign.Center,
        fillWidth = false,
        modifier = Modifier.widthIn(max = MAX_LINE_WIDTH),
    )
}

private val CHIP_GAP = 10.dp
private val MAX_LINE_WIDTH = 460.dp
