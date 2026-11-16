@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.alananasss.kittytune.ui.design

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.ui.glass.LiquidGlassRoot
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.utils.MiuixIndication
import top.yukonga.miuix.kmp.utils.MiuixOverscrollFactory

/**
 * Root of the design system: provides the MIUIX settings and, when MIUIX is the active style,
 * re-themes the whole app as HyperOS. The MIUIX palette and type scale are mapped onto the
 * Material theme too, so every screen that only knows `MaterialTheme` (backgrounds, text, icons,
 * list rows) turns HyperOS along with the components that are swapped for MIUIX ones.
 */
@Composable
fun DesignRoot(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val miuix by remember(context) { MiuixPreferences.flow(context) }.collectAsState()
    CompositionLocalProvider(LocalMiuixSettings provides miuix) {
        if (miuix.enabled) {
            MiuixThemeBridge(miuix) { LiquidGlassRoot(content) }
        } else {
            LiquidGlassRoot(content)
        }
    }
}

@Composable
private fun MiuixThemeBridge(settings: MiuixSettings, content: @Composable () -> Unit) {
    val appScheme = MaterialTheme.colorScheme
    val appTypography = MaterialTheme.typography
    val appShapes = MaterialTheme.shapes
    val materialIndication = LocalIndication.current
    val materialOverscroll = LocalOverscrollFactory.current
    val isDark = appScheme.background.luminance() < 0.5f

    val controller = remember(settings.colorSource, isDark, appScheme.primary) {
        when (settings.colorSource) {
            MiuixColorSource.HYPEROS -> ThemeController(if (isDark) ColorSchemeMode.Dark else ColorSchemeMode.Light)
            MiuixColorSource.APP_THEME -> ThemeController(
                if (isDark) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight,
                keyColor = appScheme.primary,
            )
            MiuixColorSource.WALLPAPER -> ThemeController(
                if (isDark) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight,
            )
        }
    }

    MiuixTheme(controller = controller) {
        val miuixColors = MiuixTheme.colorScheme
        val textStyles = MiuixTheme.textStyles
        val scheme = remember(miuixColors.primary, miuixColors.surface, miuixColors.surfaceContainer, isDark, settings.trueBlack) {
            miuixColors.toMaterial(appScheme, isDark, settings.trueBlack)
        }
        val typography = remember(settings.hyperOsTypography, appTypography) {
            if (settings.hyperOsTypography) hyperOsTypography(appTypography, textStyles.main.fontSize) else appTypography
        }
        MaterialExpressiveTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = appShapes,
            motionScheme = MotionScheme.expressive(),
        ) {
            val indication = remember(scheme.onBackground, settings.pressFeedback) {
                if (settings.pressFeedback) MiuixIndication(color = scheme.onBackground) else null
            }
            CompositionLocalProvider(
                LocalIndication provides (indication ?: materialIndication),
                LocalOverscrollFactory provides if (settings.bounceOverscroll) MiuixOverscrollFactory else materialOverscroll,
            ) {
                content()
            }
        }
    }
}

/**
 * HyperOS surfaces: a grey page ([Colors.surface]) with white cards ([Colors.surfaceContainer]) in
 * light mode, black page and dark grey cards in dark mode.
 */
private fun Colors.toMaterial(base: ColorScheme, isDark: Boolean, trueBlack: Boolean): ColorScheme {
    val page = if (isDark && !trueBlack) Color(0xFF121212) else surface
    val card = surfaceContainer
    val subtle = onSurfaceVariantSummary.compositeOver(page)
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = tertiaryContainer,
        onPrimaryContainer = onTertiaryContainer,
        inversePrimary = primaryVariant,
        secondary = primary,
        onSecondary = onPrimary,
        secondaryContainer = tertiaryContainer,
        onSecondaryContainer = onTertiaryContainer,
        tertiary = primaryContainer,
        onTertiary = onPrimaryContainer,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = page,
        onBackground = onSurface,
        surface = page,
        onSurface = onSurface,
        surfaceVariant = secondaryContainer,
        onSurfaceVariant = subtle,
        surfaceTint = Color.Transparent,
        inverseSurface = onSurface,
        inverseOnSurface = page,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        outline = outline,
        outlineVariant = dividerLine,
        surfaceBright = card,
        surfaceDim = page,
        surfaceContainerLowest = card,
        surfaceContainerLow = card,
        surfaceContainer = card,
        surfaceContainerHigh = card,
        surfaceContainerHighest = secondaryContainer,
    )
}

/** The HyperOS type scale (17 sp body, bold titles) laid over the Material roles. */
private fun hyperOsTypography(base: Typography, main: TextUnit): Typography {
    fun TextStyle.hos(size: Int, weight: FontWeight, lineHeight: Float = 1.3f) =
        copy(fontSize = size.sp, fontWeight = weight, lineHeight = (size * lineHeight).sp, letterSpacing = 0.sp)
    val bodySize = if (main.isSp) main.value.toInt() else 17
    return base.copy(
        displayLarge = base.displayLarge.hos(48, FontWeight.Bold, 1.15f),
        displayMedium = base.displayMedium.hos(40, FontWeight.Bold, 1.15f),
        displaySmall = base.displaySmall.hos(34, FontWeight.Bold, 1.15f),
        headlineLarge = base.headlineLarge.hos(32, FontWeight.Bold, 1.2f),
        headlineMedium = base.headlineMedium.hos(28, FontWeight.Bold, 1.2f),
        headlineSmall = base.headlineSmall.hos(24, FontWeight.Bold, 1.25f),
        titleLarge = base.titleLarge.hos(20, FontWeight.Bold),
        titleMedium = base.titleMedium.hos(bodySize, FontWeight.Medium),
        titleSmall = base.titleSmall.hos(16, FontWeight.Medium),
        bodyLarge = base.bodyLarge.hos(16, FontWeight.Normal, 1.4f),
        bodyMedium = base.bodyMedium.hos(14, FontWeight.Normal, 1.4f),
        bodySmall = base.bodySmall.hos(13, FontWeight.Normal, 1.4f),
        labelLarge = base.labelLarge.hos(15, FontWeight.Medium),
        labelMedium = base.labelMedium.hos(13, FontWeight.Medium),
        labelSmall = base.labelSmall.hos(11, FontWeight.Medium),
    )
}
