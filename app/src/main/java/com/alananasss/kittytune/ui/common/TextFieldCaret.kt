package com.alananasss.kittytune.ui.common

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange

/**
 * Moves the caret of a focused field with the left/right keys. Compose itself grabs those keys to
 * move focus to the neighbouring widget, so a D-pad can otherwise never move the caret. Moving it
 * through [TextFieldState] also makes the field scroll its own text to follow the caret. At the very
 * start/end of the text the key is left alone, so the D-pad can still walk out of the field.
 */
fun Modifier.moveCaretWithArrowKeys(state: TextFieldState, isFocused: Boolean): Modifier =
    onPreviewKeyEvent { event -> if (isFocused) moveCaret(state, event) else false }

private fun moveCaret(state: TextFieldState, event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown || event.isAltPressed) return false

    val forward = when (event.key) {
        Key.DirectionRight -> true
        Key.DirectionLeft -> false
        else -> return false
    }

    val current = state.selection
    if (!current.collapsed && !event.isShiftPressed) {
        state.edit { this.selection = TextRange(if (forward) current.max else current.min) }
        return true
    }

    val anchor = if (forward) current.max else current.min
    val text = state.text
    val target = if (event.isCtrlPressed || event.isMetaPressed) {
        wordBoundary(text, anchor, forward)
    } else {
        adjacentOffset(text, anchor, forward)
    }
    if (target == anchor) return false

    state.edit {
        this.selection = if (event.isShiftPressed) TextRange(anchor, target) else TextRange(target)
    }
    return true
}

private fun adjacentOffset(text: CharSequence, offset: Int, forward: Boolean): Int {
    if (forward) {
        if (offset >= text.length) return offset
        val step = if (text[offset].isHighSurrogate() && offset + 1 < text.length) 2 else 1
        return offset + step
    }
    if (offset <= 0) return offset
    val step = if (offset >= 2 && text[offset - 1].isLowSurrogate()) 2 else 1
    return offset - step
}

private fun wordBoundary(text: CharSequence, offset: Int, forward: Boolean): Int {
    var index = offset
    if (forward) {
        while (index < text.length && !text[index].isWhitespace()) index++
        while (index < text.length && text[index].isWhitespace()) index++
    } else {
        while (index > 0 && text[index - 1].isWhitespace()) index--
        while (index > 0 && !text[index - 1].isWhitespace()) index--
    }
    return index
}
