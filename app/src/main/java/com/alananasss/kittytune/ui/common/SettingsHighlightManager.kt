package com.alananasss.kittytune.ui.common

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

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
