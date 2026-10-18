package com.alananasss.kittytune.ui.player.lyrics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.data.local.FullPlayerBgStyle
import com.alananasss.kittytune.data.local.FullPlayerLayout

/**
 * Choices drawn as cards, two to a row: a sketch of what each does with its name under it.
 */
@Composable
internal fun <T> SketchChoices(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    sketch: @Composable (T) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { option ->
                    PreviewCard(
                        title = label(option),
                        isSelected = option == selected,
                        onClick = { onSelect(option) },
                        modifier = Modifier.weight(1f),
                    ) { sketch(option) }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * A card with a picture on top and a name under it.
 */
@Composable
internal fun PreviewCard(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    picture: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) scheme.primaryContainer.copy(alpha = 0.45f) else scheme.surfaceContainerHighest.copy(alpha = 0.5f),
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) scheme.primary else scheme.outlineVariant.copy(alpha = 0.35f),
        ),
        modifier = modifier,
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(PREVIEW_PICTURE_HEIGHT).clip(RoundedCornerShape(10.dp))) { picture() }
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) scheme.primary else scheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private val PREVIEW_PICTURE_HEIGHT = 76.dp

/** What a background style looks like, in a few shapes. */
@Composable
internal fun BackgroundSketch(style: FullPlayerBgStyle) {
    val scheme = MaterialTheme.colorScheme
    Canvas(Modifier.fillMaxSize()) {
        when (style) {
            FullPlayerBgStyle.APPLE_MUSIC -> {
                drawRect(Color(0xFF15131A))
                drawSoftBlob(Offset(size.width * 0.25f, size.height * 0.3f), size.minDimension * 0.6f, scheme.primary)
                drawSoftBlob(Offset(size.width * 0.8f, size.height * 0.7f), size.minDimension * 0.6f, scheme.tertiary)
            }
            FullPlayerBgStyle.BLUR -> {
                drawRect(Color(0xFF26262C))
                drawRect(Brush.radialGradient(listOf(scheme.primary.copy(alpha = 0.45f), Color.Transparent), center = center, radius = size.minDimension * 0.7f))
            }
            FullPlayerBgStyle.GRADIENT -> {
                drawRect(Brush.verticalGradient(listOf(scheme.primary, Color(0xFF0B0B0F))))
            }
            FullPlayerBgStyle.PURE_BLACK -> {
                drawRect(Color.Black)
                drawLine(Color.White.copy(alpha = 0.35f), Offset(size.width * 0.2f, size.height * 0.5f), Offset(size.width * 0.8f, size.height * 0.5f), strokeWidth = 3f)
            }
        }
    }
}

/** Where the words sit against the cover, in a few shapes: a block for the cover, bars for the lines. */
@Composable
internal fun LayoutSketch(layout: FullPlayerLayout) {
    val scheme = MaterialTheme.colorScheme
    Canvas(Modifier.fillMaxSize().background(scheme.surfaceContainerLowest)) {
        val cover = scheme.primary.copy(alpha = 0.8f)
        val words = scheme.onSurfaceVariant.copy(alpha = 0.7f)
        val w = size.width
        val h = size.height
        when (layout) {
            FullPlayerLayout.LYRICS_RIGHT -> {
                drawRect(cover, Offset(w * 0.08f, h * 0.18f), Size(h * 0.64f, h * 0.64f))
                bars(words, w * 0.55f, w * 0.35f, h)
            }
            FullPlayerLayout.LYRICS_LEFT -> {
                bars(words, w * 0.1f, w * 0.35f, h)
                drawRect(cover, Offset(w - w * 0.08f - h * 0.64f, h * 0.18f), Size(h * 0.64f, h * 0.64f))
            }
            FullPlayerLayout.LYRICS_CENTRED -> {
                bars(words, w * 0.3f, w * 0.4f, h)
                drawRect(cover, Offset(w * 0.5f - h * 0.14f, h * 0.7f), Size(h * 0.28f, h * 0.28f))
            }
            FullPlayerLayout.COVER_AND_LINE -> {
                drawRect(cover, Offset(w * 0.5f - h * 0.3f, h * 0.1f), Size(h * 0.6f, h * 0.6f))
                drawRect(words, Offset(w * 0.3f, h * 0.8f), Size(w * 0.4f, h * 0.08f))
            }
        }
    }
}

private fun DrawScope.drawSoftBlob(center: Offset, radius: Float, color: Color) {
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.7f), Color.Transparent), center = center, radius = radius), radius = radius, center = center)
}

/** Three lines of words, the first one longest. */
private fun DrawScope.bars(color: Color, left: Float, width: Float, h: Float) {
    val lengths = listOf(1f, 0.8f, 0.6f)
    lengths.forEachIndexed { index, fraction ->
        drawRect(color, Offset(left, h * (0.2f + index * 0.22f)), Size(width * fraction, h * 0.1f))
    }
}
