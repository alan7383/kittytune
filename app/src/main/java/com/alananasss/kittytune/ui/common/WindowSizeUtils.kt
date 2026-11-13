package com.alananasss.kittytune.ui.common

import android.content.res.Configuration
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class WindowWidthSizeClass {
    COMPACT,
    MEDIUM,
    EXPANDED
}

enum class WindowHeightSizeClass {
    COMPACT,
    MEDIUM,
    EXPANDED
}

data class WindowSizeInfo(
    val widthSizeClass: WindowWidthSizeClass,
    val heightSizeClass: WindowHeightSizeClass,
    val screenWidthDp: Dp,
    val screenHeightDp: Dp,
    val isLandscape: Boolean
) {
    /**
     * True strictly when in landscape orientation or on an ultra-wide / desktop window.
     * A phone or device in portrait is NEVER a wide screen.
     */
    val isWideScreen: Boolean
        get() = isLandscape || widthSizeClass == WindowWidthSizeClass.EXPANDED

    /**
     * A tablet has a smallest width of at least 600dp (sw600dp standard).
     * On any phone in landscape or portrait, minOf(width, height) is always < 600dp (typically 360-440dp).
     */
    val isTablet: Boolean
        get() = minOf(screenWidthDp, screenHeightDp) >= 600.dp

    /**
     * Show side navigation rail: on any phone in landscape mode.
     */
    val showNavRail: Boolean
        get() = isLandscape && !isTablet

    // Width is at least 840dp (Material EXPANDED): large tablets in landscape,
    // foldables opened, desktop windows. Used to unlock two-pane layouts.
    val isExpandedWidth: Boolean
        get() = widthSizeClass == WindowWidthSizeClass.EXPANDED

    // Max width for list-style content on tablets. Phones stay full-bleed (null),
    // tablets center their list content so rows don't stretch across the whole screen.
    val listContentMaxWidth: Dp?
        get() = if (isTablet) 840.dp else null

    /**
     * Show tablet bottom dock: ONLY on tablets.
     */
    val showTabletDock: Boolean
        get() = isTablet

    /**
     * Show standard phone bottom bar: on standard portrait mode.
     */
    val showPhoneBottomBar: Boolean
        get() = !isLandscape && !isTablet
}

@Composable
fun rememberWindowSizeInfo(
    maxWidth: Dp? = null,
    maxHeight: Dp? = null
): WindowSizeInfo {
    val configuration = LocalConfiguration.current
    val screenWidthDp = maxWidth ?: configuration.screenWidthDp.dp
    val screenHeightDp = maxHeight ?: configuration.screenHeightDp.dp

    // An orientation is landscape IF AND ONLY IF the width is strictly greater than the height.
    val isLandscape = screenWidthDp > screenHeightDp

    val widthSizeClass = when {
        screenWidthDp < 600.dp -> WindowWidthSizeClass.COMPACT
        screenWidthDp < 840.dp -> WindowWidthSizeClass.MEDIUM
        else -> WindowWidthSizeClass.EXPANDED
    }

    val heightSizeClass = when {
        screenHeightDp < 480.dp -> WindowHeightSizeClass.COMPACT
        screenHeightDp < 900.dp -> WindowHeightSizeClass.MEDIUM
        else -> WindowHeightSizeClass.EXPANDED
    }

    return WindowSizeInfo(
        widthSizeClass = widthSizeClass,
        heightSizeClass = heightSizeClass,
        screenWidthDp = screenWidthDp,
        screenHeightDp = screenHeightDp,
        isLandscape = isLandscape
    )
}

@Composable
fun rememberGridColumnCount(
    compactCount: Int = 2,
    mediumCount: Int = 3,
    expandedCount: Int = 5
): Int {
    val windowInfo = rememberWindowSizeInfo()
    return when {
        windowInfo.widthSizeClass == WindowWidthSizeClass.EXPANDED -> expandedCount
        windowInfo.widthSizeClass == WindowWidthSizeClass.MEDIUM -> mediumCount
        windowInfo.isLandscape -> mediumCount
        else -> compactCount
    }
}

// Grid cells that scale with the screen: phones keep the familiar fixed column count,
// tablets switch to adaptive tiles so artwork grids grow columns with the window width
// instead of blowing tiles up to phone size. This is what makes grids look native on
// 600dp-1280dp+ screens.
//
// @param compactCount columns used on phones (unchanged behavior)
// @param tabletTileSize minimum tile width on tablets; higher = fewer, larger tiles
@Composable
fun rememberAdaptiveGridCells(
    compactCount: Int,
    tabletTileSize: Dp = 160.dp
): GridCells {
    val windowInfo = rememberWindowSizeInfo()
    return remember(windowInfo.isTablet, compactCount, tabletTileSize) {
        if (windowInfo.isTablet) GridCells.Adaptive(tabletTileSize) else GridCells.Fixed(compactCount)
    }
}

// Modifier helper: on tablets center-caps the element at [maxWidth], phones stay full width.
// Usage: Modifier.tabletContentWidth(windowInfo) - must be composed inside a centering parent
// (e.g. Box(contentAlignment = Alignment.TopCenter) or Column(horizontalAlignment = ...)).
fun Modifier.tabletContentWidth(
    windowInfo: WindowSizeInfo,
    maxWidth: Dp = 840.dp
): Modifier =
    if (windowInfo.isTablet) widthIn(max = maxWidth) else this
