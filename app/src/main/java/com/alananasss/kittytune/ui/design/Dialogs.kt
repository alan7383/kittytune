package com.alananasss.kittytune.ui.design

import androidx.compose.material3.AlertDialog as MaterialAlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog as MaterialBasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogProperties
import com.alananasss.kittytune.data.local.UiDesignStyle
import com.alananasss.kittytune.ui.liquidglass.LocalLiquidGlassConfig
import com.alananasss.kittytune.ui.liquidglass.LocalUiDesignStyle
import com.alananasss.kittytune.ui.liquidglass.components.KittyLiquidAlertDialog

/**
 * Alert dialogs.
 *
 * The Liquid Glass variant keeps Material's slots (title, text, buttons, icon) and dialog
 * properties, but draws the container with the Backdrop optics. Material's own dialog is used
 * untouched for every other design style.
 */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    if (LocalUiDesignStyle.current == UiDesignStyle.LIQUID_GLASS && LocalLiquidGlassConfig.current.applyToDialogs) {
        KittyLiquidAlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text,
            properties = properties
        )
    } else {
        MaterialAlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text,
            shape = shape,
            containerColor = containerColor,
            iconContentColor = iconContentColor,
            titleContentColor = titleContentColor,
            textContentColor = textContentColor,
            tonalElevation = tonalElevation,
            properties = properties
        )
    }
}

/**
 * The content-only dialog overload. It stays Material's for now: the caller owns the surface, and
 * most of these already draw their own card or sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    MaterialBasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        properties = properties,
        content = content
    )
}
