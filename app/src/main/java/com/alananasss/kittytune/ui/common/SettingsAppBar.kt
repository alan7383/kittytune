package com.alananasss.kittytune.ui.common

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateTo
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Settings' top app bar, ported from AOSP.
 *
 * AOSP does not use Material's [androidx.compose.material3.LargeTopAppBar]: it ships its own
 * two-row bar in `settingslib` so the collapsed row (back button + small title) stays pinned while
 * the large title row is what collapses. Source of truth:
 *
 *   frameworks/base/packages/SettingsLib/Spa/spa/src/com/android/settingslib/spa/widget/
 *       scaffold/CustomizedAppBar.kt
 *       scaffold/SettingsTopAppBar.kt
 *       scaffold/SettingsScaffold.kt
 *
 * Two behaviours matter for how a settings page opens, and both come from that file:
 *
 *  - `TwoRowsTopAppBar` assigns `heightOffsetLimit` during composition and then calls
 *    `collapse()` from a `LaunchedEffect` keyed on it, so a page opens with the large title already
 *    folded away and only reveals it when the user scrolls back up. Material's own
 *    `LargeTopAppBar` starts expanded instead, which is why the header used to look dead.
 *  - The two title rows cross-fade on `TopTitleAlphaEasing` rather than cross-fading linearly, so
 *    the small title is already readable while the large one is still fading.
 */

private val safeDrawingWindowInsets: WindowInsets
    @Composable
    @NonRestartableComposable
    get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)

/**
 * The customized LargeTopAppBar for Settings. Mirrors AOSP's `CustomizedLargeTopAppBar`, with
 * [subtitle] rendered inside the large row so the existing two settings pages that pass one keep
 * their second line. It rides along AOSP's own multi-line title measurement, so a subtitle simply
 * makes the bar taller rather than squeezing the title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsTopAppBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TwoRowsTopAppBar(
        title = { LargeTitle(title = title, subtitle = subtitle) },
        titleTextStyle = MaterialTheme.typography.displaySmallEmphasized,
        smallTitleTextStyle = MaterialTheme.typography.titleLargeEmphasized,
        titleBottomPadding = LargeTitleBottomPadding,
        smallTitle = { SmallTitle(title = title) },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = topAppBarColors(),
        windowInsets = safeDrawingWindowInsets,
        pinnedHeight = ContainerHeight,
        scrollBehavior = scrollBehavior,
    )
}

/** The large title row, plus Settings' optional second line under the name. */
@Composable
private fun LargeTitle(title: String, subtitle: String?) {
    Column {
        Title(text = title, maxLines = 3, startPadding = LargeTitleStartPadding)
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
                modifier = Modifier.padding(start = LargeTitleStartPadding, end = ItemPaddingEnd),
            )
        }
    }
}

@Composable
private fun SmallTitle(title: String) {
    Title(text = title, maxLines = 1, startPadding = SmallTitleStartPadding)
}

@Composable
private fun Title(text: String, maxLines: Int = Int.MAX_VALUE, startPadding: Dp) {
    Text(
        text = text,
        modifier =
            Modifier.padding(start = startPadding, end = ItemPaddingEnd)
                .semantics { heading() },
        overflow = TextOverflow.Ellipsis,
        maxLines = maxLines,
    )
}

@Composable
private fun topAppBarColors() =
    TopAppBarColors(
        containerColor = MaterialTheme.colorScheme.background,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

/**
 * Represents the colors used by a top app bar in different states.
 *
 * This implementation animates the container color according to the top app bar scroll state. It
 * does not animate the leading, headline, or trailing colors.
 */
@Stable
private class TopAppBarColors(
    val containerColor: Color,
    val scrolledContainerColor: Color,
    val navigationIconContentColor: Color,
    val titleContentColor: Color,
    val actionIconContentColor: Color,
) {
    /**
     * Represents the container color used for the top app bar.
     *
     * A [colorTransitionFraction] provides a percentage value that can be used to generate a color.
     * Usually, an app bar implementation will pass in a [colorTransitionFraction] read from the
     * [TopAppBarState.collapsedFraction] or the [TopAppBarState.overlappedFraction].
     */
    @Stable
    fun containerColor(colorTransitionFraction: Float): Color =
        lerp(
            containerColor,
            scrolledContainerColor,
            FastOutLinearInEasing.transform(colorTransitionFraction),
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is TopAppBarColors) return false

        if (containerColor != other.containerColor) return false
        if (scrolledContainerColor != other.scrolledContainerColor) return false
        if (navigationIconContentColor != other.navigationIconContentColor) return false
        if (titleContentColor != other.titleContentColor) return false
        if (actionIconContentColor != other.actionIconContentColor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = containerColor.hashCode()
        result = 31 * result + scrolledContainerColor.hashCode()
        result = 31 * result + navigationIconContentColor.hashCode()
        result = 31 * result + titleContentColor.hashCode()
        result = 31 * result + actionIconContentColor.hashCode()

        return result
    }
}

/**
 * A two-rows top app bar. This is the piece that collapses: the first row is pinned at
 * [pinnedHeight] and carries the navigation icon plus the small title, the second row holds the
 * large title and is what gets scrolled out of view.
 *
 * @throws [IllegalArgumentException] if the given [MaxHeightWithoutTitle] is equal or smaller than
 *   the [pinnedHeight]
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TwoRowsTopAppBar(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    titleTextStyle: TextStyle,
    titleBottomPadding: Dp,
    smallTitle: @Composable () -> Unit,
    smallTitleTextStyle: TextStyle,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    windowInsets: WindowInsets,
    colors: TopAppBarColors,
    pinnedHeight: Dp,
    scrollBehavior: TopAppBarScrollBehavior?,
) {
    require(MaxHeightWithoutTitle > pinnedHeight) {
        "A TwoRowsTopAppBar max height should be greater than its pinned height"
    }
    val pinnedHeightPx: Float
    val titleBottomPaddingPx: Int
    val defaultMaxHeightPx: Float
    val density = LocalDensity.current
    density.run {
        pinnedHeightPx = pinnedHeight.toPx()
        titleBottomPaddingPx = titleBottomPadding.roundToPx()
        defaultMaxHeightPx = (MaxHeightWithoutTitle + DefaultTitleHeight).toPx()
    }

    val maxHeightPx = remember(density) { mutableFloatStateOf(defaultMaxHeightPx) }

    // Sets the app bar's height offset limit to hide just the bottom title area and keep top title
    // visible when collapsed.
    scrollBehavior?.state?.heightOffsetLimit = pinnedHeightPx - maxHeightPx.floatValue
    // Settings opens a page with the large title already folded away; it comes back when the user
    // scrolls up. AOSP does this here, re-running whenever the limit moves (a multi-line title
    // makes the bar taller, so the key changes and the bar folds again).
    LaunchedEffect(scrollBehavior?.state?.heightOffsetLimit) { scrollBehavior?.collapse() }

    // Obtain the container Color from the TopAppBarColors using the `collapsedFraction`, as the
    // bottom part of this TwoRowsTopAppBar changes color at the same rate the app bar expands or
    // collapse.
    val colorTransitionFraction = { scrollBehavior?.state?.collapsedFraction ?: 0f }
    val appBarContainerColor = { colors.containerColor(colorTransitionFraction()) }

    // Wrap the given actions in a Row.
    val actionsRow =
        @Composable {
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    val topTitleAlpha = { TopTitleAlphaEasing.transform(colorTransitionFraction()) }
    val bottomTitleAlpha = { 1f - colorTransitionFraction() }
    // Hide the top row title semantics when its alpha value goes below 0.5 threshold.
    // Hide the bottom row title semantics when the top title semantics are active.
    val hideTopRowSemantics by
        remember(colorTransitionFraction) { derivedStateOf { colorTransitionFraction() < 0.5f } }
    val hideBottomRowSemantics = !hideTopRowSemantics

    // Set up support for resizing the top app bar when vertically dragging the bar itself.
    val appBarDragModifier =
        if (scrollBehavior != null && !scrollBehavior.isPinned) {
            Modifier.draggable(
                orientation = Orientation.Vertical,
                state =
                    rememberDraggableState { delta -> scrollBehavior.state.heightOffset += delta },
                onDragStopped = { velocity ->
                    settleAppBar(
                        scrollBehavior.state,
                        velocity,
                        scrollBehavior.flingAnimationSpec,
                        scrollBehavior.snapAnimationSpec,
                    )
                },
            )
        } else {
            Modifier
        }

    Box(
        modifier =
            modifier
                .then(appBarDragModifier)
                .drawBehind { drawRect(color = appBarContainerColor()) }
                .semantics { isTraversalGroup = true }
                .pointerInput(Unit) {}
    ) {
        Column {
            TopAppBarLayout(
                modifier =
                    Modifier.windowInsetsPadding(windowInsets)
                        // clip after padding so we don't show the title over the inset area
                        .clipToBounds(),
                heightPx = pinnedHeightPx,
                navigationIconContentColor = colors.navigationIconContentColor,
                titleContentColor = colors.titleContentColor,
                actionIconContentColor = colors.actionIconContentColor,
                title = smallTitle,
                titleTextStyle = smallTitleTextStyle,
                titleAlpha = topTitleAlpha,
                titleVerticalArrangement = Arrangement.Center,
                titleBottomPadding = 0,
                hideTitleSemantics = hideTopRowSemantics,
                navigationIcon = navigationIcon,
                actions = actionsRow,
            )
            TopAppBarLayout(
                modifier =
                    Modifier
                        // only apply the horizontal sides of the window insets padding, since the
                        // top padding will always be applied by the layout above
                        .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Horizontal))
                        .clipToBounds(),
                heightPx =
                    maxHeightPx.floatValue - pinnedHeightPx +
                        (scrollBehavior?.state?.heightOffset ?: 0f),
                navigationIconContentColor = colors.navigationIconContentColor,
                titleContentColor = colors.titleContentColor,
                actionIconContentColor = colors.actionIconContentColor,
                title = {
                    Box(
                        modifier =
                            Modifier.onGloballyPositioned { coordinates ->
                                val measuredMaxHeightPx =
                                    density.run {
                                        MaxHeightWithoutTitle.toPx() +
                                            coordinates.size.height.toFloat() +
                                            TitleBaselineHeight.toPx()
                                    }
                                // Allow larger max height for multi-line title, but do not reduce
                                // max height to prevent flaky.
                                if (measuredMaxHeightPx > defaultMaxHeightPx) {
                                    maxHeightPx.floatValue = measuredMaxHeightPx
                                }
                            }
                    ) {
                        title()
                    }
                },
                titleTextStyle = titleTextStyle,
                titleAlpha = bottomTitleAlpha,
                titleVerticalArrangement = Arrangement.Bottom,
                titleBottomPadding = titleBottomPaddingPx,
                hideTitleSemantics = hideBottomRowSemantics,
                navigationIcon = {},
                actions = {},
            )
        }
    }
}

/**
 * The base [Layout] for all top app bars. This function lays out a top app bar navigation icon
 * (leading icon), a title (header), and action icons (trailing icon). Note that the navigation and
 * the actions are optional.
 *
 * @param heightPx the total height this layout is capped to
 * @param titleAlpha the title's alpha
 * @param titleVerticalArrangement the title's vertical arrangement
 * @param titleBottomPadding the title's bottom padding
 * @param hideTitleSemantics hides the title node from the semantic tree. Apply this boolean when
 *   this layout is part of a [TwoRowsTopAppBar] to hide the title's semantics from accessibility
 *   services. This is needed to avoid having multiple titles visible to accessibility services at
 *   the same time, when animating between collapsed / expanded states.
 */
@Composable
private fun TopAppBarLayout(
    modifier: Modifier,
    heightPx: Float,
    navigationIconContentColor: Color,
    titleContentColor: Color,
    actionIconContentColor: Color,
    title: @Composable () -> Unit,
    titleTextStyle: TextStyle,
    titleAlpha: () -> Float,
    titleVerticalArrangement: Arrangement.Vertical,
    titleBottomPadding: Int,
    hideTitleSemantics: Boolean,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable () -> Unit,
) {
    Layout(
        {
            Box(Modifier.layoutId("navigationIcon").padding(start = TopAppBarHorizontalPadding)) {
                CompositionLocalProvider(
                    LocalContentColor provides navigationIconContentColor,
                    content = navigationIcon,
                )
            }
            Box(
                Modifier.layoutId("title")
                    .padding(horizontal = TopAppBarHorizontalPadding)
                    .then(if (hideTitleSemantics) Modifier.clearAndSetSemantics {} else Modifier)
                    .graphicsLayer { alpha = titleAlpha() }
            ) {
                ProvideTextStyle(value = titleTextStyle) {
                    CompositionLocalProvider(
                        LocalContentColor provides titleContentColor,
                        LocalDensity provides
                            with(LocalDensity.current) {
                                // Settings does not let the large title scale with the font size
                                // preference: a two-line name would otherwise push the collapsed
                                // row around.
                                Density(density = density, fontScale = 1f)
                            },
                        content = title,
                    )
                }
            }
            Box(Modifier.layoutId("actionIcons").padding(end = TopAppBarHorizontalPadding)) {
                CompositionLocalProvider(
                    LocalContentColor provides actionIconContentColor,
                    content = actions,
                )
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val navigationIconPlaceable =
            measurables
                .first { it.layoutId == "navigationIcon" }
                .measure(constraints.copy(minWidth = 0))
        val actionIconsPlaceable =
            measurables
                .first { it.layoutId == "actionIcons" }
                .measure(constraints.copy(minWidth = 0))

        val maxTitleWidth =
            if (constraints.maxWidth == Constraints.Infinity) {
                constraints.maxWidth
            } else {
                (constraints.maxWidth - navigationIconPlaceable.width - actionIconsPlaceable.width)
                    .coerceAtLeast(0)
            }
        val titlePlaceable =
            measurables
                .first { it.layoutId == "title" }
                .measure(constraints.copy(minWidth = 0, maxWidth = maxTitleWidth))

        // Locate the title's baseline.
        val titleBaseline =
            if (titlePlaceable[LastBaseline] != AlignmentLine.Unspecified) {
                titlePlaceable[LastBaseline]
            } else {
                0
            }

        val layoutHeight = if (heightPx > 0) heightPx.roundToInt() else 0

        layout(constraints.maxWidth, layoutHeight) {
            // Navigation icon
            navigationIconPlaceable.placeRelative(
                x = 0,
                y = (layoutHeight - navigationIconPlaceable.height) / 2,
            )

            // Title
            titlePlaceable.placeRelative(
                x = max(TopAppBarTitleInset.roundToPx(), navigationIconPlaceable.width),
                y =
                    when (titleVerticalArrangement) {
                        Arrangement.Center -> (layoutHeight - titlePlaceable.height) / 2
                        // Apply bottom padding from the title's baseline only when the Arrangement
                        // is "Bottom".
                        Arrangement.Bottom ->
                            if (titleBottomPadding == 0) layoutHeight - titlePlaceable.height
                            else
                                layoutHeight -
                                    titlePlaceable.height -
                                    max(
                                        0,
                                        titleBottomPadding - titlePlaceable.height + titleBaseline,
                                    )
                        // Arrangement.Top
                        else -> 0
                    },
            )

            // Action icons
            actionIconsPlaceable.placeRelative(
                x = constraints.maxWidth - actionIconsPlaceable.width,
                y = (layoutHeight - actionIconsPlaceable.height) / 2,
            )
        }
    }
}

/**
 * Settles the app bar by flinging, in case the given velocity is greater than zero, and snapping
 * after the fling settles.
 */
@OptIn(ExperimentalMaterial3Api::class)
private suspend fun settleAppBar(
    state: TopAppBarState,
    velocity: Float,
    flingAnimationSpec: DecayAnimationSpec<Float>?,
    snapAnimationSpec: AnimationSpec<Float>?,
): Velocity {
    // Check if the app bar is completely collapsed/expanded. If so, no need to settle the app bar,
    // and just return Zero Velocity.
    // Note that we don't check for 0f due to float precision with the collapsedFraction
    // calculation.
    if (state.collapsedFraction < 0.01f || state.collapsedFraction == 1f) {
        return Velocity.Zero
    }
    var remainingVelocity = velocity
    // In case there is an initial velocity that was left after a previous user fling, animate to
    // continue the motion to expand or collapse the app bar.
    if (flingAnimationSpec != null && abs(velocity) > 1f) {
        var lastValue = 0f
        AnimationState(initialValue = 0f, initialVelocity = velocity).animateDecay(
            flingAnimationSpec
        ) {
            val delta = value - lastValue
            val initialHeightOffset = state.heightOffset
            state.heightOffset = initialHeightOffset + delta
            val consumed = abs(initialHeightOffset - state.heightOffset)
            lastValue = value
            remainingVelocity = this.velocity
            // avoid rounding errors and stop if anything is unconsumed
            if (abs(delta - consumed) > 0.5f) this.cancelAnimation()
        }
    }
    // Snap if animation specs were provided.
    if (snapAnimationSpec != null) {
        if (state.heightOffset < 0 && state.heightOffset > state.heightOffsetLimit) {
            AnimationState(initialValue = state.heightOffset).animateTo(
                if (state.collapsedFraction < 0.5f) {
                    0f
                } else {
                    state.heightOffsetLimit
                },
                animationSpec = snapAnimationSpec,
            ) {
                state.heightOffset = value
            }
        }
    }

    return Velocity(0f, remainingVelocity)
}

/** Folds the app bar all the way down. AOSP's `SettingsTopAppBarKt.collapse`. */
@OptIn(ExperimentalMaterial3Api::class)
internal fun TopAppBarScrollBehavior.collapse() {
    with(state) { heightOffset = heightOffsetLimit }
}

// An easing function used to compute the alpha value that is applied to the top title part of a
// Medium or Large app bar.
private val TopTitleAlphaEasing = CubicBezierEasing(.8f, 0f, .8f, .15f)

// Every value below is read out of com.android.settings_17.apk - the static fields of
// CustomizedAppBarKt and the call sites of CustomizedLargeTopAppBar - and not off AOSP main,
// because the two disagree: main has MaxHeightWithoutTitle at 84.dp and drives the title weight
// through toSemiBoldWeight(), while the shipped build resolves to the numbers below.
private val MaxHeightWithoutTitle = 124.dp
private val DefaultTitleHeight = 52.dp
private val ContainerHeight = 56.dp
private val TitleBaselineHeight = 0.dp
private val LargeTitleBottomPadding = 28.dp
private val TopAppBarHorizontalPadding = 4.dp

// A title inset when the App-Bar is a Medium or Large one. Also used to size a spacer when the
// navigation icon is missing.
private val TopAppBarTitleInset = 16.dp - TopAppBarHorizontalPadding

// The two title rows are inset differently: the large one sits flush against the navigation icon,
// the small one is pushed clear of it (SettingsSpace.extraSmall6).
private val LargeTitleStartPadding = 0.dp
private val SmallTitleStartPadding = 12.dp

// SettingsDimension.itemPaddingEnd, shared by both title rows.
private val ItemPaddingEnd = 16.dp
