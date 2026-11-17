package com.alananasss.kittytune.ui.player.lyrics.accompanist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine

// Mini-style top-to-bottom sweep fill for a plain (line-timed) karaoke line: a soft
// gradient band travels down the text at the pace of playback. Passed to
// [SyncedLineText] only for the CURRENT line while line-synced lyrics are shown.
data class KaraokeSweepSpec(
    val brightColor: Color,
    val dimColor: Color,
    val progress: () -> Float,
)

@Composable
fun SyncedLineText(
    line: SyncedLine,
    isLineRtl: Boolean,
    isRightAligned: Boolean = false,
    isCenterAligned: Boolean = false,
    textStyle: TextStyle,
    textColor: Color,
    modifier: Modifier = Modifier,
    showTranslation: Boolean = true,
    activeScale: Float = 1.0f,
    sweep: KaraokeSweepSpec? = null
) {
    val scaleFactor = activeScale.coerceAtLeast(1.0f)
    val widthFraction = (1f / scaleFactor).coerceIn(0.5f, 1f)
    val hAlign = when {
        isRightAligned -> Alignment.End
        isCenterAligned -> Alignment.CenterHorizontally
        isLineRtl -> Alignment.End
        else -> Alignment.Start
    }
    val tAlign = when {
        isRightAligned -> TextAlign.End
        isCenterAligned -> TextAlign.Center
        isLineRtl -> TextAlign.End
        else -> TextAlign.Start
    }
    val boxAlignment = when {
        isRightAligned || isLineRtl -> Alignment.CenterEnd
        isCenterAligned -> Alignment.Center
        else -> Alignment.CenterStart
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalAlignment = hAlign
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(widthFraction),
            contentAlignment = boxAlignment
        ) {
            if (sweep != null) {
                // Current line with the Sweep animation on: same mini-style fill as the classic
                // line-by-line view - a vertical gradient band that travels down the text.
                var textHeightPx by remember { mutableFloatStateOf(1f) }
                val sweepProgress by remember(sweep) {
                    derivedStateOf { sweep.progress().coerceIn(0f, 1f) }
                }
                val bandOrigin = sweepProgress * textHeightPx
                val sweepDone = sweepProgress >= 1f
                val brush = if (sweepDone) null else Brush.verticalGradient(
                    colors = listOf(sweep.brightColor, sweep.dimColor),
                    startY = bandOrigin - 10f,
                    endY = bandOrigin + 10f
                )
                Text(
                    text = line.content,
                    style = textStyle.copy(
                        brush = brush,
                        shadow = Shadow(color = sweep.brightColor.copy(alpha = 0.5f), blurRadius = sweepProgress * 20f)
                    ),
                    color = if (sweepDone) sweep.brightColor else textColor,
                    textAlign = tAlign,
                    onTextLayout = { textHeightPx = it.size.height.toFloat().coerceAtLeast(1f) }
                )
            } else {
                Text(
                    text = line.content,
                    style = textStyle,
                    color = textColor,
                    textAlign = tAlign
                )
            }
        }
        if (showTranslation) {
            line.translation?.takeUnless { com.alananasss.kittytune.ui.player.lyrics.LyricsUtils.isDuplicateTranslation(line.content, it) }?.let {
                Box(
                    modifier = Modifier.fillMaxWidth(widthFraction),
                    contentAlignment = boxAlignment
                ) {
                    Text(
                        text = it,
                        color = textColor.copy(alpha = 0.6f),
                        textAlign = tAlign
                    )
                }
            }
        }
    }
}
