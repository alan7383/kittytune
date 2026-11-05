package com.alananasss.kittytune.wear.presentation.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.MaterialTheme

/**
 * Baseline Wear Material 3 Theme for KittyTune Wear OS Companion.
 * Wraps content in Wear Compose MaterialTheme with custom ColorScheme and Typography.
 */
@Composable
fun KittyTuneWearTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = KittyTuneWearColorScheme,
        typography = KittyTuneWearTypography,
        content = content
    )
}
