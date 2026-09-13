    package com.alananasss.kittytune.ui.utils

    import androidx.compose.ui.Modifier
    import androidx.compose.ui.draw.drawWithContent
    import androidx.compose.ui.graphics.BlendMode
    import androidx.compose.ui.graphics.Brush
    import androidx.compose.ui.graphics.Color
    import androidx.compose.ui.graphics.CompositingStrategy
    import androidx.compose.ui.graphics.graphicsLayer

    fun Modifier.fadingEdge(brush: Brush) = this
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            drawRect(brush = brush, blendMode = BlendMode.DstIn)
        }

    fun Modifier.smoothFadingEdge(
        top: androidx.compose.ui.unit.Dp? = null,
        bottom: androidx.compose.ui.unit.Dp? = null,
    ) = graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            if (top != null) {
                val topPx = top.toPx()
                drawRect(
                    brush =
                        Brush.verticalGradient(
                            colorStops =
                                arrayOf(
                                    0.0f to Color.Transparent,
                                    0.3f to Color.Black.copy(alpha = 0.15f),
                                    0.5f to Color.Black.copy(alpha = 0.4f),
                                    0.7f to Color.Black.copy(alpha = 0.7f),
                                    0.85f to Color.Black.copy(alpha = 0.9f),
                                    1.0f to Color.Black,
                                ),
                            startY = 0f,
                            endY = topPx,
                        ),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (bottom != null) {
                val bottomPx = bottom.toPx()
                drawRect(
                    brush =
                        Brush.verticalGradient(
                            colorStops =
                                arrayOf(
                                    0.0f to Color.Black,
                                    0.15f to Color.Black.copy(alpha = 0.9f),
                                    0.3f to Color.Black.copy(alpha = 0.7f),
                                    0.5f to Color.Black.copy(alpha = 0.4f),
                                    0.7f to Color.Black.copy(alpha = 0.15f),
                                    1.0f to Color.Transparent,
                                ),
                            startY = size.height - bottomPx,
                            endY = size.height,
                        ),
                    blendMode = BlendMode.DstIn,
                )
            }
        }

    fun Modifier.smoothFadingEdge(vertical: androidx.compose.ui.unit.Dp) =
        smoothFadingEdge(
            top = vertical,
            bottom = vertical,
        )

