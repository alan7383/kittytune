package com.alananasss.kittytune.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Manages target item highlighting when navigating from settings search,
 * exactly replicating Android Settings (AOSP) search highlight behavior.
 */
object SettingsHighlightManager {
    private var _highlightKey by mutableStateOf<String?>(null)
    val highlightKey: String?
        get() = _highlightKey

    fun setHighlightKey(key: String?) {
        _highlightKey = key
    }

    fun isHighlighted(key: String?): Boolean {
        if (key == null || _highlightKey == null) return false
        return _highlightKey == key
    }

    fun clearHighlight(key: String?) {
        if (key != null && _highlightKey == key) {
            _highlightKey = null
        }
    }
}
