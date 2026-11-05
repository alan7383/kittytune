package com.alananasss.kittytune.wear.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme

// AMOLED True Black for watch displays (battery-efficient and bezelless)
val WearBackground = Color(0xFF000000)
val WearOnBackground = Color(0xFFE2E2E6)

// KittyTune Signature Primary Accents (High-contrast blue for circular watch screens)
val WearPrimary = Color(0xFF90CAF9)
val WearPrimaryDim = Color(0xFF64B5F6)
val WearPrimaryContainer = Color(0xFF0D47A1)
val WearOnPrimary = Color(0xFF003258)
val WearOnPrimaryContainer = Color(0xFFD1E4FF)

// Secondary Accents (Controls & Sliders)
val WearSecondary = Color(0xFFB0BEC5)
val WearSecondaryDim = Color(0xFF90A4AE)
val WearSecondaryContainer = Color(0xFF37474F)
val WearOnSecondary = Color(0xFF1C313A)
val WearOnSecondaryContainer = Color(0xFFCFD8DC)

// Tertiary Accents (Desktop Target / Active Status)
val WearTertiary = Color(0xFFFFB74D)
val WearTertiaryDim = Color(0xFFFFA726)
val WearTertiaryContainer = Color(0xFF633F00)
val WearOnTertiary = Color(0xFF452B00)
val WearOnTertiaryContainer = Color(0xFFFFDDB3)

// Surface & Container Tones
val WearSurfaceContainerLow = Color(0xFF121214)
val WearSurfaceContainer = Color(0xFF1E1F22)
val WearSurfaceContainerHigh = Color(0xFF2B2D31)
val WearOnSurface = Color(0xFFE2E2E6)
val WearOnSurfaceVariant = Color(0xFFC4C6D0)
val WearOutline = Color(0xFF8E9099)
val WearOutlineVariant = Color(0xFF44474E)

// Error States
val WearError = Color(0xFFFFB4AB)
val WearOnError = Color(0xFF690005)
val WearErrorContainer = Color(0xFF93000A)
val WearOnErrorContainer = Color(0xFFFFDAD6)

/**
 * Material 3 ColorScheme for KittyTune Wear OS Companion.
 * Uses AMOLED pure black background and vibrant accents tuned for watch visibility.
 */
val KittyTuneWearColorScheme: ColorScheme = ColorScheme(
    primary = WearPrimary,
    primaryDim = WearPrimaryDim,
    primaryContainer = WearPrimaryContainer,
    onPrimary = WearOnPrimary,
    onPrimaryContainer = WearOnPrimaryContainer,
    secondary = WearSecondary,
    secondaryDim = WearSecondaryDim,
    secondaryContainer = WearSecondaryContainer,
    onSecondary = WearOnSecondary,
    onSecondaryContainer = WearOnSecondaryContainer,
    tertiary = WearTertiary,
    tertiaryDim = WearTertiaryDim,
    tertiaryContainer = WearTertiaryContainer,
    onTertiary = WearOnTertiary,
    onTertiaryContainer = WearOnTertiaryContainer,
    surfaceContainerLow = WearSurfaceContainerLow,
    surfaceContainer = WearSurfaceContainer,
    surfaceContainerHigh = WearSurfaceContainerHigh,
    onSurface = WearOnSurface,
    onSurfaceVariant = WearOnSurfaceVariant,
    outline = WearOutline,
    outlineVariant = WearOutlineVariant,
    background = WearBackground,
    onBackground = WearOnBackground,
    error = WearError,
    onError = WearOnError,
    errorContainer = WearErrorContainer,
    onErrorContainer = WearOnErrorContainer
)
