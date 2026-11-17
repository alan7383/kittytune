package com.alananasss.kittytune.ui.common

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.alananasss.kittytune.R
import com.google.accompanist.drawablepainter.rememberDrawablePainter

/**
 * Inflates an XML vector drawable that depends on Android theme attributes
 * using the real dark/light mode of the app with Material 3 dynamic colors.
 */
@Composable
fun MaterialYouVectorDrawable(
    modifier: Modifier = Modifier,
    @DrawableRes drawableResId: Int,
    isDarkTheme: Boolean = isSystemInDarkTheme()
) {
    val context = LocalContext.current
    val themedContext = remember(context, isDarkTheme) {
        context.createVectorThemedContext(isDarkTheme = isDarkTheme)
    }
    val drawable = remember(themedContext, drawableResId) {
        AppCompatResources.getDrawable(themedContext, drawableResId)?.mutate()
    }

    drawable?.let {
        Image(
            painter = rememberDrawablePainter(it),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = modifier
        )
    }
}

private fun Context.createVectorThemedContext(isDarkTheme: Boolean): Context {
    val themedConfiguration = Configuration(resources.configuration).apply {
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (isDarkTheme) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    }
    val modeContext = createConfigurationContext(themedConfiguration)
    return ContextThemeWrapper(modeContext, R.style.Theme_KittyTune_Vector)
}
