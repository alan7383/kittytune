package com.alananasss.kittytune.ui.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Tune
import com.alananasss.kittytune.ui.design.AssistChip
import androidx.compose.material3.ButtonDefaults
import com.alananasss.kittytune.ui.design.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import com.alananasss.kittytune.ui.design.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import com.alananasss.kittytune.ui.design.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.UiDesignStyle
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsGroupTitle
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.common.rememberAppHaptics
import com.alananasss.kittytune.ui.common.rememberWindowSizeInfo
import com.alananasss.kittytune.ui.liquidglass.LocalBackdrop
import com.alananasss.kittytune.ui.liquidglass.LocalLiquidGlassConfig
import com.alananasss.kittytune.ui.liquidglass.LocalUiDesignStyle
import com.alananasss.kittytune.ui.liquidglass.components.KittyLiquidButton
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterfaceDesignScreen(
    onBackClick: () -> Unit,
    onNavigateToLiquidGlassSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    val haptics = rememberAppHaptics()
    val windowSizeInfo = rememberWindowSizeInfo()

    var uiDesignStyle by remember { mutableStateOf(prefs.getUiDesignStyle()) }
    val liquidConfig = remember { prefs.getLiquidGlassConfig() }
    val previewBackdrop = rememberLayerBackdrop()

    CompositionLocalProvider(
        LocalUiDesignStyle provides uiDesignStyle,
        LocalLiquidGlassConfig provides liquidConfig,
        LocalBackdrop provides previewBackdrop
    ) {
        SettingsScaffold(
            title = stringResource(R.string.pref_ui_design_style),
            onBackClick = onBackClick
        ) { innerPadding ->
            val isTablet = windowSizeInfo.isTablet

            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                val horizontalPadding = if (isTablet) 32.dp else 16.dp
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = if (isTablet) 780.dp else 600.dp),
                    contentPadding = PaddingValues(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = 16.dp,
                        bottom = 180.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item {
                        DesignStyleSelectorGroup(
                            currentStyle = uiDesignStyle,
                            onStyleSelected = { style ->
                                haptics.perform(HapticKind.UI)
                                uiDesignStyle = style
                                prefs.setUiDesignStyle(style)
                            },
                            onNavigateToLiquidGlassSettings = {
                                haptics.perform(HapticKind.UI)
                                onNavigateToLiquidGlassSettings()
                            }
                        )
                    }

                    item {
                        AnimatedContent(
                            targetState = uiDesignStyle,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "DesignStylePreview"
                        ) { targetStyle ->
                            when (targetStyle) {
                                UiDesignStyle.LIQUID_GLASS -> {
                                    LiquidGlassHeroCard(
                                        backdrop = previewBackdrop,
                                        onConfigureClick = onNavigateToLiquidGlassSettings
                                    )
                                }
                                UiDesignStyle.MATERIAL3 -> {
                                    Material3HeroCard()
                                }
                                UiDesignStyle.MIUIX -> {
                                    /* Currently inactive */
                                }
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(180.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DesignStyleSelectorGroup(
    currentStyle: UiDesignStyle,
    onStyleSelected: (UiDesignStyle) -> Unit,
    onNavigateToLiquidGlassSettings: () -> Unit
) {
    SettingsGroup(
        title = stringResource(R.string.pref_ui_design_style),
        items = listOf(
            { shape ->
                SettingsItem(
                    shape = shape,
                    title = stringResource(R.string.style_material3),
                    subtitle = stringResource(R.string.style_material3_desc),
                    icon = Icons.Rounded.Dashboard,
                    onClick = { onStyleSelected(UiDesignStyle.MATERIAL3) },
                    trailingContent = {
                        if (currentStyle == UiDesignStyle.MATERIAL3) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            },
            { shape ->
                SettingsItem(
                    shape = shape,
                    title = stringResource(R.string.style_liquid_glass),
                    subtitle = stringResource(R.string.style_liquid_glass_desc),
                    icon = Icons.Rounded.BlurOn,
                    onClick = {
                        if (currentStyle == UiDesignStyle.LIQUID_GLASS) {
                            onNavigateToLiquidGlassSettings()
                        } else {
                            onStyleSelected(UiDesignStyle.LIQUID_GLASS)
                        }
                    },
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (currentStyle == UiDesignStyle.LIQUID_GLASS) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            // Direct tune/configure button to Liquid Glass settings
                            FilledTonalIconButton(
                                onClick = onNavigateToLiquidGlassSettings,
                                modifier = Modifier.size(36.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = stringResource(R.string.pref_liquid_settings_title),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                )
            },
            { shape ->
                SettingsItem(
                    shape = shape,
                    title = stringResource(R.string.style_miuix),
                    subtitle = stringResource(R.string.style_miuix_desc),
                    icon = Icons.Rounded.Layers,
                    onClick = null,
                    trailingContent = {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("Soon", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                )
            }
        )
    )
}

/**
 * Clean hero card for Liquid Glass mode with official Backdrop wallpaper preview and configure shortcut.
 */
@Composable
private fun LiquidGlassHeroCard(
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    onConfigureClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingsGroupTitle(title = stringResource(R.string.style_liquid_glass))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Mini preview box with Backdrop wallpaper
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(18.dp)
                        )
                ) {
                    Image(
                        painter = painterResource(R.drawable.backdrop_wallpaper_sample),
                        contentDescription = null,
                        modifier = Modifier
                            .matchParentSize()
                            .layerBackdrop(backdrop),
                        contentScale = ContentScale.Crop
                    )

                    KittyLiquidButton(
                        onClick = onConfigureClick,
                        backdrop = backdrop,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.75f),
                        tint = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.pref_liquid_preview_button),
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.pref_liquid_settings_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                ElevatedButton(
                    onClick = onConfigureClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.pref_liquid_settings_title),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Hero card for Material 3.
 */
@Composable
private fun Material3HeroCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingsGroupTitle(title = stringResource(R.string.style_material3))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.style_material3_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Primary",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Secondary",
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Tertiary",
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
