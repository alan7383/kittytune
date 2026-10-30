package com.alananasss.kittytune.ui.liquidglass

import androidx.compose.runtime.compositionLocalOf
import com.alananasss.kittytune.data.local.LiquidGlassConfig
import com.alananasss.kittytune.data.local.UiDesignStyle
import com.kyant.backdrop.Backdrop

val LocalUiDesignStyle = compositionLocalOf { UiDesignStyle.MATERIAL3 }
val LocalBackdrop = compositionLocalOf<Backdrop?> { null }

/**
 * Ambient backdrop used by the design-system controls (buttons, switches, sliders, fields...).
 *
 * It is deliberately separate from [LocalBackdrop]: [LocalBackdrop] is the recorded content of the
 * host screen (used by the navigation bars and the preview cards), while this one is a cheap,
 * coordinates-independent canvas. Because it is a canvas, it also works inside Dialogs and Popups,
 * where a render-node based backdrop from the main window cannot be sampled.
 */
val LocalGlassBackdrop = compositionLocalOf<Backdrop?> { null }

val LocalLiquidGlassConfig = compositionLocalOf { LiquidGlassConfig() }
