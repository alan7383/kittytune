package com.alananasss.kittytune.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.delay

/**
 * Scrolls the container so that the requested item is positioned near the center of the viewport,
 * rather than stopped at the top or bottom edge.
 */
suspend fun BringIntoViewRequester.bringIntoViewCentered(coordinates: LayoutCoordinates?) {
    if (coordinates != null && coordinates.isAttached) {
        val rootCoords = try { coordinates.findRootCoordinates() } catch (_: Exception) { null }
        val viewportHeight = rootCoords?.size?.height?.toFloat() ?: 800f
        val itemHeight = coordinates.size.height.toFloat()
        val itemWidth = coordinates.size.width.toFloat()
        val verticalPadding = ((viewportHeight - itemHeight) / 2f).coerceAtLeast(0f)
        val centeredRect = Rect(
            left = 0f,
            top = -verticalPadding,
            right = itemWidth,
            bottom = itemHeight + verticalPadding
        )
        bringIntoView(centeredRect)
    } else {
        bringIntoView()
    }
}

/**
 * Manages target item highlighting when navigating from settings search,
 * exactly replicating Android Settings (AOSP) search highlight behavior.
 */
object SettingsHighlightManager {
    private var _highlightKey by mutableStateOf<String?>(null)
    val highlightKey: String?
        get() = _highlightKey

    var isScrollingToTarget by mutableStateOf(false)

    fun setHighlightKey(key: String?) {
        _highlightKey = key
        isScrollingToTarget = (key != null)
    }

    fun isHighlighted(key: String?): Boolean {
        if (key == null || _highlightKey == null) return false
        return _highlightKey == key
    }

    fun clearHighlight(key: String?) {
        if (key != null && _highlightKey == key) {
            _highlightKey = null
            isScrollingToTarget = false
        }
    }
}

/**
 * Automatically and smoothly scrolls down to the highlighted setting item
 * when navigating from search results, replicating AOSP smoothScrollToPosition behavior.
 */
@Composable
fun AutoScrollToHighlightedItem(
    listState: LazyListState,
    keyToIndex: Map<String, Int>
) {
    val highlightKey = SettingsHighlightManager.highlightKey
    LaunchedEffect(highlightKey) {
        if (highlightKey != null) {
            val targetIndex = keyToIndex[highlightKey]
            if (targetIndex != null) {
                SettingsHighlightManager.isScrollingToTarget = true
                delay(250) // Wait for screen transition to settle (AOSP DELAY_HIGHLIGHT_DURATION_MILLIS)
                try {
                    listState.animateScrollToItem(
                        index = targetIndex,
                        scrollOffset = -80
                    )
                } catch (_: Exception) {
                } finally {
                    delay(150) // Stabilization pause after arrival before pulsing starts
                    SettingsHighlightManager.isScrollingToTarget = false
                }
            } else {
                SettingsHighlightManager.isScrollingToTarget = false
            }
        } else {
            SettingsHighlightManager.isScrollingToTarget = false
        }
    }
}

/**
 * Modifier that can be attached to any Composable setting card, row or container.
 * When highlighted via [SettingsHighlightManager], it automatically scrolls into view (centered)
 * and pulses 3 times before smoothly fading out.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.settingHighlight(
    highlightKey: String?,
    shape: Shape? = null,
    overlayAlpha: Float = 0.26f,
): Modifier = composed {
    if (highlightKey == null) return@composed this

    val isHighlighted = SettingsHighlightManager.isHighlighted(highlightKey)
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val highlightAlpha = remember { Animatable(0f) }
    val primaryColor = MaterialTheme.colorScheme.primary
    var itemCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            // Short 50ms delay for initial layout pass to settle coordinates
            delay(50)
            try {
                bringIntoViewRequester.bringIntoViewCentered(itemCoordinates)
            } catch (_: Exception) {}

            highlightAlpha.animateTo(1f, tween(200, easing = LinearEasing))
            highlightAlpha.animateTo(0f, tween(200, easing = LinearEasing))
            highlightAlpha.animateTo(1f, tween(200, easing = LinearEasing))
            highlightAlpha.animateTo(0f, tween(200, easing = LinearEasing))
            highlightAlpha.animateTo(1f, tween(200, easing = LinearEasing))
            delay(1200)
            highlightAlpha.animateTo(0f, tween(500, easing = FastOutSlowInEasing))
            SettingsHighlightManager.clearHighlight(highlightKey)
        }
    }

    this
        .bringIntoViewRequester(bringIntoViewRequester)
        .onGloballyPositioned { itemCoordinates = it }
        .drawWithContent {
            drawContent()
            if (highlightAlpha.value > 0f) {
                val overlayColor = primaryColor.copy(alpha = overlayAlpha * highlightAlpha.value)
                if (shape != null) {
                    when (val outline = shape.createOutline(size, layoutDirection, this)) {
                        is Outline.Rectangle -> drawRect(color = overlayColor)
                        is Outline.Rounded -> drawRoundRect(
                            color = overlayColor,
                            topLeft = Offset(outline.roundRect.left, outline.roundRect.top),
                            size = Size(outline.roundRect.width, outline.roundRect.height),
                            cornerRadius = CornerRadius(
                                outline.roundRect.topLeftCornerRadius.x,
                                outline.roundRect.topLeftCornerRadius.y
                            )
                        )
                        is Outline.Generic -> drawPath(path = outline.path, color = overlayColor)
                    }
                } else {
                    drawRect(color = overlayColor)
                }
            }
        }
}
