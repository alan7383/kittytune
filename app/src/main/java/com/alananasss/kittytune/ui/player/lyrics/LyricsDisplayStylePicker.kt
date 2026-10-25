package com.alananasss.kittytune.ui.player.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.hapticAction
import com.alananasss.kittytune.ui.common.hapticClickable
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.LyricsDisplayStyle

/**
 * The four ways to set the current line apart, each shown as it looks.
 */
@Composable
internal fun LyricsDisplayStylePicker(
    selected: LyricsDisplayStyle,
    onSelect: (LyricsDisplayStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LyricsDisplayStyle.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { style ->
                    StyleCard(style, isSelected = style == selected, onClick = hapticAction(HapticKind.UI) { onSelect(style) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StyleCard(style: LyricsDisplayStyle, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    PreviewCard(title = str(style.labelKey), isSelected = isSelected, onClick = onClick, modifier = modifier) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest)) { StylePreview(style) }
    }
}

/** Five short lines around a current one, drawn with the treatment [style] gives each distance. */
@Composable
private fun StylePreview(style: LyricsDisplayStyle) {
    Column(
        modifier = Modifier.fillMaxWidth().height(PREVIEW_HEIGHT),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SAMPLE_WIDTHS.forEachIndexed { i, width ->
            val distance = i - SAMPLE_WIDTHS.size / 2
            val treatment = LyricLineStyling.treatmentFor(style, distance, focusBlur = 1.dp)
            Box(
                Modifier
                    .scale(treatment.scale)
                    .alpha(treatment.alpha)
                    .then(if (treatment.blur > 0.dp) Modifier.blur(treatment.blur) else Modifier)
                    .width(width)
                    .height(if (distance == 0) 7.dp else 6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface)
            )
        }
    }
}

private val LyricsDisplayStyle.labelKey: String
    get() = when (this) {
        LyricsDisplayStyle.STANDARD -> "lyrics_style_standard"
        LyricsDisplayStyle.SCALE -> "lyrics_style_scale"
        LyricsDisplayStyle.FOCUS -> "lyrics_style_focus"
        LyricsDisplayStyle.SCALE_FOCUS -> "lyrics_style_scale_focus"
    }

private val SAMPLE_WIDTHS = listOf(52.dp, 78.dp, 70.dp, 60.dp, 44.dp)
private val PREVIEW_HEIGHT = 76.dp
