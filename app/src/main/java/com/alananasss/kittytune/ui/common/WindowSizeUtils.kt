package com.alananasss.kittytune.ui.common

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
