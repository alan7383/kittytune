package com.alananasss.kittytune.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.alananasss.kittytune.data.local.AppThemeMode
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.MaterialExpressiveTheme
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme

@Composable
fun SoundTuneTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    pureBlack: Boolean = false,
    keyColor: Int = 0,
    colorStyle: String = "TonalSpot",
    colorSpec: String = "Default",
    typography: androidx.compose.material3.Typography = Typography,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemInDark = isSystemInDarkTheme()
    
    val useDarkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> systemInDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val style = try { PaletteStyle.valueOf(colorStyle) } catch (e: Exception) { PaletteStyle.TonalSpot }

    val baseColorScheme = if (keyColor == 0) {
        val baseSystemScheme = if (useDarkTheme && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicDarkColorScheme(context) 
                               else if (!useDarkTheme && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicLightColorScheme(context)
                               else if (useDarkTheme) darkColorScheme() else lightColorScheme()
                               
        if (pureBlack && useDarkTheme) {
            baseSystemScheme.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceContainer = Color.Black,
                surfaceContainerLow = Color.Black,
                surfaceContainerHigh = Color(0xFF121212)
            )
        } else baseSystemScheme
    } else {
        rememberDynamicColorScheme(
            seedColor = Color(keyColor),
            isDark = useDarkTheme,
            isAmoled = pureBlack,
            style = style,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !useDarkTheme
            insetsController.isAppearanceLightNavigationBars = !useDarkTheme
        }
    }

    MaterialExpressiveTheme(
        colorScheme = baseColorScheme,
        typography = typography,
        content = content,
        motionScheme = MotionScheme.expressive()
    )
}
