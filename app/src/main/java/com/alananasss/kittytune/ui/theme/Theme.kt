package com.alananasss.kittytune.ui.theme

import android.app.Activity
import android.content.Context
import android.os.Build
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
    
    // Use the native system generated scheme if "System" style is selected with Auto color
    if (colorStyle.equals("System", ignoreCase = true) && dynamicColor && keyColor == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val platformScheme = if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        return if (pureBlack && useDarkTheme) platformScheme.withAmoledSurfaces() else platformScheme
    }

    val style = remember(colorStyle) { parseMaterialKolorPaletteStyle(colorStyle) }
    val specVersion = remember(colorSpec) { parseMaterialKolorColorSpec(colorSpec) }
    val seedColor = remember(context, dynamicColor, keyColor, useDarkTheme) {
        resolveSeedColor(
            context = context,
            useDarkTheme = useDarkTheme,
            dynamicColor = dynamicColor,
            keyColor = keyColor
        )
    }

    return rememberDynamicColorScheme(
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
