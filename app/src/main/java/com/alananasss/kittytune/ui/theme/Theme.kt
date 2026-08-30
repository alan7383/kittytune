package com.alananasss.kittytune.ui.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.alananasss.kittytune.data.local.AppThemeMode
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.scheme.DynamicScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

object ThemeState {
    var previewKeyColor by mutableStateOf<Int?>(null)

    /**
     * The dominant colour of the cover that is playing, or null when there is none (issue #33).
     *
     * Written by the player, read by the theme. The desktop app has seeded its palette from this for a while;
     * this side had every piece and no wire between them.
     */
    var coverSeedColor by mutableStateOf<Int?>(null)
}

/**
 * How long the palette takes to travel to a new cover's colour. Long enough to read as a transition rather
 * than a flash, short enough that the app has finished changing before you have finished looking at the new
 * track.
 */
private const val SEED_TRANSITION_MS = 450

internal val KittyTuneDefaultSeedColor = Color(0xFFFF7A1A)
internal val MaterialKolorColorSpecOptions = listOf("SPEC_2025", "SPEC_2021")

internal fun parseMaterialKolorPaletteStyle(colorStyle: String): PaletteStyle =
    PaletteStyle.entries.firstOrNull { it.name.equals(colorStyle.trim(), ignoreCase = true) }
        ?: PaletteStyle.Expressive

internal fun parseMaterialKolorColorSpec(colorSpec: String): ColorSpec.SpecVersion =
    when (colorSpec.trim().uppercase()) {
        "SPEC_2021", "2021", "MATERIAL_2021" -> ColorSpec.SpecVersion.SPEC_2021
        "SPEC_2025", "2025", "MATERIAL_2025", "DEFAULT" -> ColorSpec.SpecVersion.SPEC_2025
        else -> ColorSpec.SpecVersion.SPEC_2025
    }

internal fun normalizedMaterialKolorColorSpecName(colorSpec: String): String =
    when (parseMaterialKolorColorSpec(colorSpec)) {
        ColorSpec.SpecVersion.SPEC_2021 -> "SPEC_2021"
        ColorSpec.SpecVersion.SPEC_2025 -> "SPEC_2025"
    }

@Composable
internal fun rememberSoundTuneColorScheme(
    useDarkTheme: Boolean,
    dynamicColor: Boolean,
    pureBlack: Boolean,
    keyColor: Int,
    colorStyle: String,
    colorSpec: String,
): ColorScheme {
    val context = LocalContext.current

    // Read before anything can return, which is the whole trick. The "System" branch below hands back the
    // platform's own wallpaper scheme and never looks at the cover — so with that style picked, and it is the
    // default, following the artwork would have silently done nothing. The desktop had the identical bug with
    // its own accent styles (issue #33).
    val coverSeed = if (dynamicColor) ThemeState.coverSeedColor else null

    // Use the native system generated scheme if "System" style is selected with Auto color — but only while
    // there is no cover colour to prefer.
    if (coverSeed == null && colorStyle.equals("System", ignoreCase = true) && dynamicColor && keyColor == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val platformScheme = if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        return if (pureBlack && useDarkTheme) platformScheme.withAmoledSurfaces() else platformScheme
    }

    val style = remember(colorStyle) { parseMaterialKolorPaletteStyle(colorStyle) }
    val specVersion = remember(colorSpec) { parseMaterialKolorColorSpec(colorSpec) }

    val effectiveKeyColor = ThemeState.previewKeyColor ?: keyColor
    val seedColor = remember(context, dynamicColor, effectiveKeyColor, coverSeed, useDarkTheme) {
        when {
            coverSeed != null -> Color(coverSeed)
            else -> resolveSeedColor(
                context = context,
                useDarkTheme = useDarkTheme,
                dynamicColor = dynamicColor,
                keyColor = effectiveKeyColor,
            )
        }
    }

    val target = rememberDynamicColorScheme(
        seedColor = seedColor,
        isDark = useDarkTheme,
        isAmoled = pureBlack,
        style = style,
        specVersion = specVersion,
        platform = DynamicScheme.Platform.PHONE,
        modifyColorScheme = { scheme ->
            if (pureBlack && useDarkTheme) scheme.withAmoledSurfaces() else scheme
        }
    )

    // Snapped rather than eased while the colour picker is being dragged: there the whole point is that the
    // app follows your finger, and a tween would only lag behind it.
    return target.easedInto(instant = ThemeState.previewKeyColor != null)
}

private fun resolveSeedColor(
    context: Context,
    useDarkTheme: Boolean,
    dynamicColor: Boolean,
    keyColor: Int,
): Color {
    if (keyColor != 0) return Color(keyColor)

    return if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val platformScheme = if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        platformScheme.primary
    } else {
        KittyTuneDefaultSeedColor
    }
}

/**
 * Eases the palette towards this scheme instead of repainting the app in a single frame.
 *
 * The desktop's copy of this function, ported unchanged: it is pure Compose and the two apps should cross-fade
 * their palettes the same way.
 *
 * The interpolation is over the resolved colours, not over the seed. Building a scheme from a seed
 * costs about 5 ms here, a third of a 60 Hz frame, so animating the seed would mean paying that on
 * every frame of the transition. Both ends of the interpolation are schemes materialkolor built, so
 * lerping between them stays coherent while costing a lerp per slot.
 */
@Composable
private fun ColorScheme.easedInto(instant: Boolean): ColorScheme {
    val spec: AnimationSpec<Color> = remember(instant) {
        if (instant) snap() else tween(durationMillis = SEED_TRANSITION_MS, easing = FastOutSlowInEasing)
    }

    @Composable
    fun ease(color: Color, label: String): Color =
        animateColorAsState(targetValue = color, animationSpec = spec, label = label).value

    return copy(
        primary = ease(primary, "primary"),
        onPrimary = ease(onPrimary, "onPrimary"),
        primaryContainer = ease(primaryContainer, "primaryContainer"),
        onPrimaryContainer = ease(onPrimaryContainer, "onPrimaryContainer"),
        inversePrimary = ease(inversePrimary, "inversePrimary"),
        secondary = ease(secondary, "secondary"),
        onSecondary = ease(onSecondary, "onSecondary"),
        secondaryContainer = ease(secondaryContainer, "secondaryContainer"),
        onSecondaryContainer = ease(onSecondaryContainer, "onSecondaryContainer"),
        tertiary = ease(tertiary, "tertiary"),
        onTertiary = ease(onTertiary, "onTertiary"),
        tertiaryContainer = ease(tertiaryContainer, "tertiaryContainer"),
        onTertiaryContainer = ease(onTertiaryContainer, "onTertiaryContainer"),
        background = ease(background, "background"),
        onBackground = ease(onBackground, "onBackground"),
        surface = ease(surface, "surface"),
        onSurface = ease(onSurface, "onSurface"),
        surfaceVariant = ease(surfaceVariant, "surfaceVariant"),
        onSurfaceVariant = ease(onSurfaceVariant, "onSurfaceVariant"),
        surfaceTint = ease(surfaceTint, "surfaceTint"),
        inverseSurface = ease(inverseSurface, "inverseSurface"),
        inverseOnSurface = ease(inverseOnSurface, "inverseOnSurface"),
        error = ease(error, "error"),
        onError = ease(onError, "onError"),
        errorContainer = ease(errorContainer, "errorContainer"),
        onErrorContainer = ease(onErrorContainer, "onErrorContainer"),
        outline = ease(outline, "outline"),
        outlineVariant = ease(outlineVariant, "outlineVariant"),
        scrim = ease(scrim, "scrim"),
        surfaceBright = ease(surfaceBright, "surfaceBright"),
        surfaceDim = ease(surfaceDim, "surfaceDim"),
        surfaceContainer = ease(surfaceContainer, "surfaceContainer"),
        surfaceContainerHigh = ease(surfaceContainerHigh, "surfaceContainerHigh"),
        surfaceContainerHighest = ease(surfaceContainerHighest, "surfaceContainerHighest"),
        surfaceContainerLow = ease(surfaceContainerLow, "surfaceContainerLow"),
        surfaceContainerLowest = ease(surfaceContainerLowest, "surfaceContainerLowest"),
        primaryFixed = ease(primaryFixed, "primaryFixed"),
        primaryFixedDim = ease(primaryFixedDim, "primaryFixedDim"),
        onPrimaryFixed = ease(onPrimaryFixed, "onPrimaryFixed"),
        onPrimaryFixedVariant = ease(onPrimaryFixedVariant, "onPrimaryFixedVariant"),
        secondaryFixed = ease(secondaryFixed, "secondaryFixed"),
        secondaryFixedDim = ease(secondaryFixedDim, "secondaryFixedDim"),
        onSecondaryFixed = ease(onSecondaryFixed, "onSecondaryFixed"),
        onSecondaryFixedVariant = ease(onSecondaryFixedVariant, "onSecondaryFixedVariant"),
        tertiaryFixed = ease(tertiaryFixed, "tertiaryFixed"),
        tertiaryFixedDim = ease(tertiaryFixedDim, "tertiaryFixedDim"),
        onTertiaryFixed = ease(onTertiaryFixed, "onTertiaryFixed"),
        onTertiaryFixedVariant = ease(onTertiaryFixedVariant, "onTertiaryFixedVariant"),
    )
}

private fun ColorScheme.withAmoledSurfaces(): ColorScheme =
    copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceContainerLow = Color.Black,
        surfaceContainer = Color.Black,
        surfaceContainerHigh = Color(0xFF121212),
        surfaceContainerHighest = Color(0xFF181818)
    )

@Composable
fun SoundTuneTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    pureBlack: Boolean = false,
    keyColor: Int = 0,
    colorStyle: String = "System",
    colorSpec: String = "SPEC_2025",
    typography: androidx.compose.material3.Typography = Typography,
    content: @Composable () -> Unit,
) {
    val systemInDark = isSystemInDarkTheme()

    val useDarkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> systemInDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val colorScheme = rememberSoundTuneColorScheme(
        useDarkTheme = useDarkTheme,
        dynamicColor = dynamicColor,
        pureBlack = pureBlack,
        keyColor = keyColor,
        colorStyle = colorStyle,
        colorSpec = colorSpec,
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.Transparent.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.Transparent.toArgb()

            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !useDarkTheme
            insetsController.isAppearanceLightNavigationBars = !useDarkTheme
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content,
        motionScheme = MotionScheme.expressive(),
    )
}
