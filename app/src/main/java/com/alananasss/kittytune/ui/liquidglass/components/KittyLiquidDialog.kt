package com.alananasss.kittytune.ui.liquidglass.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alananasss.kittytune.ui.liquidglass.LocalBackdrop
import com.alananasss.kittytune.ui.liquidglass.LocalGlassBackdrop
import com.alananasss.kittytune.ui.liquidglass.LocalLiquidGlassConfig
import com.alananasss.kittytune.ui.liquidglass.rememberAmbientBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.RoundedRectangle

/**
 * Liquid Glass Alert Dialog following the Backdrop Catalog dialog:
 * a dimmed, saturated frost with a smooth lens rim and a plain highlight.
 *
 * A Dialog is a separate window: a RenderNode-owned backdrop from the main window (LocalBackdrop)
 * does not render there. Backdrop therefore uses a canvas backdrop for dialogs/popups; we use the
 * app's ambient canvas, which renders in the dialog and matches the rest of the glass controls.
 */
@Composable
fun KittyLiquidAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties()
) {
    // The app can be in dark mode while the system is light; derive the dialog's palette from
    // the app's colour scheme so the glass and its text stay in the same theme.
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val config = LocalLiquidGlassConfig.current

    // Canvas backdrop: it can be sampled from a Dialog (unlike a RenderNode from the main
    // window), so the glass no longer lets the page show through the transparent dialog window.
    val ambient = LocalGlassBackdrop.current ?: rememberAmbientBackdrop()

    // The catalog dialog colours. Keeping the same values is what makes the frost read as a
    // dialog instead of a coloured sheet.
    val containerColor = if (isLightTheme) {
        Color(0xFFFAFAFA).copy(alpha = 0.6f)
    } else {
        Color(0xFF121212).copy(alpha = 0.4f)
    }
    val cornerRadius = (48f * config.cornerRadiusFactor).dp

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        Column(
            modifier = modifier
                .widthIn(min = 280.dp, max = 560.dp)
                .drawBackdrop(
                    backdrop = ambient,
                    shape = { RoundedRectangle(cornerRadius) },
                    effects = {
                        colorControls(
                            brightness = if (isLightTheme) 0.2f else 0f,
                            saturation = 1.5f
                        )
                        blur(if (isLightTheme) 16f.dp.toPx() else 8f.dp.toPx())
                        lens(
                            refractionHeight = 24.dp.toPx(),
                            refractionAmount = 48.dp.toPx(),
                            depthEffect = true,
                            chromaticAberration = config.chromaticAberration > 0f
                        )
                    },
                    highlight = { Highlight.Plain },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .padding(24.dp)
        ) {
            // Give the dialog's glass children the same canvas. The page backdrop cannot render
            // in this window, so every nested control (groups, buttons, sliders) must sample the
            // ambient instead of falling back to a blank drawer.
            CompositionLocalProvider(
                LocalBackdrop provides ambient,
                LocalGlassBackdrop provides ambient
            ) {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .align(Alignment.CenterHorizontally)
                    ) {
                        icon()
                    }
                }
                if (title != null) {
                    Box(modifier = Modifier.padding(bottom = 12.dp)) {
                        CompositionLocalProvider(
                            LocalTextStyle provides MaterialTheme.typography.headlineSmall
                        ) {
                            title()
                        }
                    }
                }
                if (text != null) {
                    Box(modifier = Modifier.padding(bottom = 24.dp)) {
                        CompositionLocalProvider(
                            LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            text()
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}
