package com.alananasss.kittytune.wear.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/**
 * Reserves a compact height in the parent column while letting its content keep a larger touch target.
 *
 * Wear OS asks for 48dp interactive targets, but a control that *lays out* at 48dp makes the column
 * taller than a small round display can show. This slot measures the content with no height bound,
 * reports only [layoutHeight], and centres the content, so the extra hit area is drawn outside the
 * slot instead of squeezing the content. Compose hit-tests children outside their parent bounds, so
 * the target stays full size.
 *
 * Only use this for a row whose neighbours are static text, with the content being a fixed-size box
 * whose *visual* is centred inside it (for example a 48dp clickable box holding a compact pill). The
 * overflow then lands equally on static elements, and no control ever answers a tap meant for
 * another. A row with an interactive neighbour must lay out at its full target height instead.
 */
@Composable
fun TouchTargetSlot(
    layoutHeight: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val slotHeight = layoutHeight.roundToPx().coerceAtLeast(0)
        val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
        val placeables = measurables.map { measurable ->
            measurable.measure(
                Constraints(
                    minWidth = 0,
                    maxWidth = maxWidth,
                    minHeight = 0,
                    maxHeight = Constraints.Infinity
                )
            )
        }
        val width = placeables.maxOfOrNull { it.width }
            ?.coerceIn(constraints.minWidth, maxWidth)
            ?: constraints.minWidth
        layout(width = width, height = slotHeight) {
            placeables.forEach { placeable ->
                placeable.place(
                    x = (width - placeable.width) / 2,
                    y = (slotHeight - placeable.height) / 2
                )
            }
        }
    }
}
