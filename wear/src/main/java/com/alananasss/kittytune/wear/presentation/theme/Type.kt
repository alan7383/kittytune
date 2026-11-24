package com.alananasss.kittytune.wear.presentation.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.wear.compose.material3.Typography
import com.alananasss.kittytune.wear.common.R

/**
 * Material 3 Typography for KittyTune Wear OS Companion.
 * Uses default Wear OS typography scale optimized for circular watch screens.
 */
@OptIn(ExperimentalTextApi::class)
val KittyTuneWearTypography = Typography(
    defaultFontFamily = FontFamily(
        listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
            Font(
                resId = R.font.kittytune_flex,
                weight = weight,
                variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
            )
        }
    ),
)
