package com.alananasss.kittytune.ui.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * A drop-in replacement helper for [OutlinedTextField] that uses Compose Foundation Text 2
 * ([TextFieldState]) under the hood.
 *
 * This provides:
 * 1. Automatic horizontal scrolling following the cursor ([bringIntoView]) on long titles.
 * 2. Touch dragging and viewport movement for overflowing single-line texts.
 * 3. Arrow key / D-pad caret movement without focus escape via [moveCaretWithArrowKeys].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KittyOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    interactionSource: MutableInteractionSource? = null
) {
    val state = remember { TextFieldState(value) }
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val isFocused by interactions.collectIsFocusedAsState()

    // Sync from state to parent callback
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }
            .distinctUntilChanged()
            .collect { newText ->
                if (newText != value) {
                    onValueChange(newText)
                }
            }
    }

    // Sync external updates from parent into state
    LaunchedEffect(value) {
        if (state.text.toString() != value) {
            state.edit {
                replace(0, length, value)
                selection = TextRange(length)
            }
        }
    }

    val lineLimits = if (singleLine) {
        TextFieldLineLimits.SingleLine
    } else {
        TextFieldLineLimits.MultiLine(minLines, maxLines)
    }

    val actionHandler = onKeyboardAction ?: KeyboardActionHandler { performDefaultAction ->
        val scope = object : androidx.compose.foundation.text.KeyboardActionScope {
            override fun defaultKeyboardAction(imeAction: ImeAction) {
                performDefaultAction()
            }
        }
        val block = when (keyboardOptions.imeAction) {
            ImeAction.Search -> keyboardActions.onSearch
            ImeAction.Done -> keyboardActions.onDone
            ImeAction.Go -> keyboardActions.onGo
            ImeAction.Next -> keyboardActions.onNext
            ImeAction.Previous -> keyboardActions.onPrevious
            ImeAction.Send -> keyboardActions.onSend
            else -> null
        }
        if (block != null) {
            block(scope)
        } else {
            performDefaultAction()
        }
    }

    OutlinedTextField(
        state = state,
        modifier = modifier.moveCaretWithArrowKeys(state, isFocused),
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label?.let { { it() } },
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = actionHandler,
        lineLimits = lineLimits,
        shape = shape,
        colors = colors,
        interactionSource = interactions
    )
}
