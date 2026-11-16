@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.alananasss.kittytune.ui.design

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.ui.glass.GlassComponent
import com.alananasss.kittytune.ui.glass.GlassLevel
import com.alananasss.kittytune.ui.glass.GlassShadow
import com.alananasss.kittytune.ui.glass.LocalGlassFlattened
import com.alananasss.kittytune.ui.glass.glass
import com.alananasss.kittytune.ui.glass.glassEnabled
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import androidx.compose.material3.CenterAlignedTopAppBar as M3CenterAlignedTopAppBar
import androidx.compose.material3.LargeTopAppBar as M3LargeTopAppBar
import androidx.compose.material3.MediumTopAppBar as M3MediumTopAppBar
import androidx.compose.material3.OutlinedTextField as M3OutlinedTextField
import androidx.compose.material3.TopAppBar as M3TopAppBar

// --- Toolbar groups --------------------------------------------------------------------------

/**
 * A glass capsule around a group of toolbar items, the iOS 26 toolbar look. The items inside are
 * flattened: they show glass only under the finger. Draws nothing when the slot is empty, so an
 * unused navigation icon slot does not leave an orphan bubble.
 */
@Composable
fun GlassToolbarGroup(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Layout(
        contents = listOf(
            { Box(Modifier.glass(Capsule(), GlassLevel.Regular, shadow = GlassShadow.Soft)) },
            {
                CompositionLocalProvider(LocalGlassFlattened provides true) {
                    Row(Modifier.padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically, content = content)
                }
            },
        ),
        modifier = modifier,
    ) { (background, items), constraints ->
        val row = items.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val innerWidth = row.width - 4.dp.roundToPx()
        if (innerWidth <= 0 || row.height <= 0) {
            layout(0, 0) {}
        } else {
            val bg = background.first().measure(Constraints.fixed(row.width, row.height))
            layout(row.width, row.height) {
                bg.place(0, 0)
                row.place(0, 0)
            }
        }
    }
}

@Composable
private fun glassBarSlots(
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
): Pair<@Composable () -> Unit, @Composable RowScope.() -> Unit> {
    if (!glassEnabled(GlassComponent.TOP_BARS)) return navigationIcon to actions
    val nav: @Composable () -> Unit = { GlassToolbarGroup(Modifier.padding(start = 4.dp)) { navigationIcon() } }
    val acts: @Composable RowScope.() -> Unit = { GlassToolbarGroup(Modifier.padding(end = 8.dp)) { actions() } }
    return nav to acts
}

@Composable
fun TopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    expandedHeight: Dp = TopAppBarDefaults.TopAppBarExpandedHeight,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
    contentPadding: PaddingValues = TopAppBarDefaults.ContentPadding,
) {
    val (nav, acts) = glassBarSlots(navigationIcon, actions)
    M3TopAppBar(title, modifier, nav, acts, expandedHeight, windowInsets, colors, scrollBehavior, contentPadding)
}

@Composable
fun TopAppBar(
    title: @Composable () -> Unit,
    subtitle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    titleHorizontalAlignment: Alignment.Horizontal = Alignment.Start,
    expandedHeight: Dp = TopAppBarDefaults.TopAppBarExpandedHeight,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
    contentPadding: PaddingValues = TopAppBarDefaults.ContentPadding,
) {
    val (nav, acts) = glassBarSlots(navigationIcon, actions)
    M3TopAppBar(title, subtitle, modifier, nav, acts, titleHorizontalAlignment, expandedHeight, windowInsets, colors, scrollBehavior, contentPadding)
}

@Composable
fun CenterAlignedTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    expandedHeight: Dp = TopAppBarDefaults.TopAppBarExpandedHeight,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val (nav, acts) = glassBarSlots(navigationIcon, actions)
    M3CenterAlignedTopAppBar(title, modifier, nav, acts, expandedHeight, windowInsets, colors, scrollBehavior)
}

@Composable
fun LargeTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    collapsedHeight: Dp = TopAppBarDefaults.LargeAppBarCollapsedHeight,
    expandedHeight: Dp = TopAppBarDefaults.LargeAppBarExpandedHeight,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val (nav, acts) = glassBarSlots(navigationIcon, actions)
    M3LargeTopAppBar(title, modifier, nav, acts, collapsedHeight, expandedHeight, windowInsets, colors, scrollBehavior)
}

@Composable
fun MediumTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    collapsedHeight: Dp = TopAppBarDefaults.MediumAppBarCollapsedHeight,
    expandedHeight: Dp = TopAppBarDefaults.MediumAppBarExpandedHeight,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val (nav, acts) = glassBarSlots(navigationIcon, actions)
    M3MediumTopAppBar(title, modifier, nav, acts, collapsedHeight, expandedHeight, windowInsets, colors, scrollBehavior)
}

// --- Text fields -----------------------------------------------------------------------------

internal val GlassFieldShape = RoundedRectangle(18.dp)

@Composable
internal fun glassFieldColors(base: TextFieldColors): TextFieldColors {
    val scheme = MaterialTheme.colorScheme
    return base.copy(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        errorContainerColor = Color.Transparent,
        focusedIndicatorColor = scheme.primary.copy(alpha = 0.6f),
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
    )
}

@Composable
fun OutlinedTextField(
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
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
) {
    val glass = glassEnabled(GlassComponent.TEXT_FIELDS)
    val miuixField = miuixEnabled(MiuixComponent.TEXT_FIELDS)
    M3OutlinedTextField(
        value, onValueChange,
        if (glass) modifier.glass(GlassFieldShape, GlassLevel.Regular) else modifier,
        enabled, readOnly, textStyle, label, placeholder, leadingIcon, trailingIcon, prefix, suffix,
        supportingText, isError, visualTransformation, keyboardOptions, keyboardActions, singleLine,
        maxLines, minLines, interactionSource,
        if (glass) GlassFieldShape else if (miuixField) MiuixFieldShape else shape,
        if (glass) glassFieldColors(colors) else if (miuixField) miuixFieldColors(colors) else colors,
    )
}

@Composable
fun OutlinedTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
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
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
) {
    val glass = glassEnabled(GlassComponent.TEXT_FIELDS)
    val miuixField = miuixEnabled(MiuixComponent.TEXT_FIELDS)
    M3OutlinedTextField(
        value, onValueChange,
        if (glass) modifier.glass(GlassFieldShape, GlassLevel.Regular) else modifier,
        enabled, readOnly, textStyle, label, placeholder, leadingIcon, trailingIcon, prefix, suffix,
        supportingText, isError, visualTransformation, keyboardOptions, keyboardActions, singleLine,
        maxLines, minLines, interactionSource,
        if (glass) GlassFieldShape else if (miuixField) MiuixFieldShape else shape,
        if (glass) glassFieldColors(colors) else if (miuixField) miuixFieldColors(colors) else colors,
    )
}

/** HyperOS text field: a filled grey rounded box, blue outline only while focused. */
internal val MiuixFieldShape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)

@Composable
internal fun miuixFieldColors(base: TextFieldColors): TextFieldColors {
    val mx = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
    return base.copy(
        focusedContainerColor = mx.secondaryContainer,
        unfocusedContainerColor = mx.secondaryContainer,
        disabledContainerColor = mx.secondaryContainer.copy(alpha = 0.5f),
        errorContainerColor = mx.secondaryContainer,
        focusedIndicatorColor = mx.primary,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        cursorColor = mx.primary,
    )
}
