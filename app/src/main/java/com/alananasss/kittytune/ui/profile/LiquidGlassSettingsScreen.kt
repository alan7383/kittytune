package com.alananasss.kittytune.ui.profile

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider as MaterialSlider
import androidx.compose.material3.Switch as MaterialSwitch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.LiquidGlassConfig
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.UiDesignStyle
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.SettingsGroupTitle
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.common.SettingsSwitch
import com.alananasss.kittytune.ui.common.Slider
import com.alananasss.kittytune.ui.common.rememberAppHaptics
import com.alananasss.kittytune.ui.common.rememberWindowSizeInfo
import com.alananasss.kittytune.ui.design.FilledTonalButton
import com.alananasss.kittytune.ui.design.FilledTonalIconButton
import com.alananasss.kittytune.ui.design.OutlinedButton
import com.alananasss.kittytune.ui.design.Surface
import com.alananasss.kittytune.ui.design.TextButton
import com.alananasss.kittytune.ui.liquidglass.LocalBackdrop
import com.alananasss.kittytune.ui.liquidglass.LocalLiquidGlassConfig
import com.alananasss.kittytune.ui.liquidglass.LocalUiDesignStyle
import com.alananasss.kittytune.ui.liquidglass.components.KittyLiquidBottomTab
import com.alananasss.kittytune.ui.liquidglass.components.KittyLiquidBottomTabs
import com.alananasss.kittytune.ui.liquidglass.components.KittyLiquidButton
import com.alananasss.kittytune.ui.liquidglass.components.KittyLiquidSlider
import com.alananasss.kittytune.ui.liquidglass.components.KittyLiquidToggle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidGlassSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    val haptics = rememberAppHaptics()
    val windowSizeInfo = rememberWindowSizeInfo()

    var liquidConfig by remember { mutableStateOf(prefs.getLiquidGlassConfig()) }

    fun updateConfig(block: LiquidGlassConfig.() -> LiquidGlassConfig) {
        val newConfig = liquidConfig.block()
        liquidConfig = newConfig
        prefs.setLiquidGlassConfig(newConfig)
    }

    val previewBackdrop = rememberLayerBackdrop()

    CompositionLocalProvider(
        LocalUiDesignStyle provides UiDesignStyle.LIQUID_GLASS,
        LocalLiquidGlassConfig provides liquidConfig,
        LocalBackdrop provides previewBackdrop
    ) {
        SettingsScaffold(
            title = stringResource(R.string.pref_liquid_settings_title),
            onBackClick = onBackClick,
            actions = {
                FilledTonalIconButton(
                    onClick = {
                        haptics.perform(HapticKind.MEDIUM)
                        prefs.resetLiquidGlassConfig()
                        liquidConfig = prefs.getLiquidGlassConfig()
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.pref_liquid_reset)
                    )
                }
            }
        ) { innerPadding ->
            val isTablet = windowSizeInfo.isTablet

            if (isTablet) {
                // Tablet layout: Side-by-side columns
                Row(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Left Column: Live Interactive Preview & UI Component selection
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        BackdropLivePreviewCard(
                            backdrop = previewBackdrop,
                            liquidConfig = liquidConfig
                        )

                        LiquidComponentsCard(
                            config = liquidConfig,
                            onConfigChange = { updateConfig { it } }
                        )

                        Spacer(Modifier.height(180.dp))
                    }

                    // Right Column: Optical Physics & 3D Effects
                    Column(
                        modifier = Modifier
                            .weight(1.15f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LiquidOpticalParametersCard(
                            config = liquidConfig,
                            onConfigChange = { updateConfig { it } },
                            onReset = {
                                haptics.perform(HapticKind.MEDIUM)
                                prefs.resetLiquidGlassConfig()
                                liquidConfig = prefs.getLiquidGlassConfig()
                            }
                        )

                        LiquidEffectsCard(
                            config = liquidConfig,
                            onConfigChange = { updateConfig { it } },
                            onReset = {
                                haptics.perform(HapticKind.MEDIUM)
                                prefs.resetLiquidGlassConfig()
                                liquidConfig = prefs.getLiquidGlassConfig()
                            }
                        )

                        Spacer(Modifier.height(180.dp))
                    }
                }
            } else {
                // Mobile layout: Single scrollable column
                LazyColumn(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        BackdropLivePreviewCard(
                            backdrop = previewBackdrop,
                            liquidConfig = liquidConfig
                        )
                    }

                    item {
                        LiquidComponentsCard(
                            config = liquidConfig,
                            onConfigChange = { updateConfig { it } }
                        )
                    }

                    item {
                        LiquidOpticalParametersCard(
                            config = liquidConfig,
                            onConfigChange = { updateConfig { it } },
                            onReset = {
                                haptics.perform(HapticKind.MEDIUM)
                                prefs.resetLiquidGlassConfig()
                                liquidConfig = prefs.getLiquidGlassConfig()
                            }
                        )
                    }

                    item {
                        LiquidEffectsCard(
                            config = liquidConfig,
                            onConfigChange = { updateConfig { it } },
                            onReset = {
                                haptics.perform(HapticKind.MEDIUM)
                                prefs.resetLiquidGlassConfig()
                                liquidConfig = prefs.getLiquidGlassConfig()
                            }
                        )
                    }

                    item {
                        Spacer(Modifier.height(180.dp))
                    }
                }
            }
        }
    }
}

/**
 * Live preview card inspired by the official Backdrop demo catalog.
 * Renders the rich authentic Backdrop wallpaper as the recorded backdrop layer,
 * with real lens refraction, fluid toggles, slider, buttons and tabs.
 */
@Composable
fun BackdropLivePreviewCard(
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    liquidConfig: LiquidGlassConfig,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = rememberAppHaptics()

    var customPainter: Painter? by remember { mutableStateOf(null) }
    var isCustomImage by remember { mutableStateOf(false) }

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val imageBitmap = BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
                    if (imageBitmap != null) {
                        customPainter = BitmapPainter(imageBitmap)
                        isCustomImage = true
                        haptics.perform(HapticKind.CONFIRM)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    var previewSliderValue by remember { mutableFloatStateOf(0.55f) }
    var previewToggleChecked by remember { mutableStateOf(true) }
    var previewSelectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f, fill = false)) {
                SettingsGroupTitle(title = stringResource(R.string.pref_liquid_preview_title))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isCustomImage) {
                    FilledTonalIconButton(
                        onClick = {
                            customPainter = null
                            isCustomImage = false
                            haptics.perform(HapticKind.UI)
                        },
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.HideImage,
                            contentDescription = stringResource(R.string.pref_liquid_reset_wallpaper),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                FilledTonalButton(
                    onClick = {
                        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.pref_liquid_pick_wallpaper),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(24.dp)
                )
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            // Sibling 1: Wallpaper layer recorded into backdrop
            Image(
                painter = customPainter ?: painterResource(R.drawable.backdrop_wallpaper_sample),
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .layerBackdrop(backdrop),
                contentScale = ContentScale.Crop
            )

            // Sibling 2: Components floating directly on top of the backdrop
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Interactive Button (reflects applyToButtons toggle)
                if (liquidConfig.applyToButtons) {
                    KittyLiquidButton(
                        onClick = {
                            haptics.perform(HapticKind.UI)
                        },
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth(0.9f),
                        tint = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color.White
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.pref_liquid_preview_button),
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    FilledTonalButton(
                        onClick = {
                            haptics.perform(HapticKind.UI)
                        },
                        modifier = Modifier.fillMaxWidth(0.9f),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.pref_liquid_preview_button),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }

                // Row with Slider and Toggle (reflecting applyToSliders and applyToControls)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (liquidConfig.applyToSliders) {
                        KittyLiquidSlider(
                            value = { previewSliderValue },
                            onValueChange = { previewSliderValue = it },
                            backdrop = backdrop,
                            modifier = Modifier.weight(1f),
                            accentColor = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        MaterialSlider(
                            value = previewSliderValue,
                            onValueChange = { previewSliderValue = it },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (liquidConfig.applyToControls) {
                        KittyLiquidToggle(
                            selected = { previewToggleChecked },
                            onSelect = {
                                previewToggleChecked = it
                                haptics.perform(HapticKind.UI)
                            },
                            backdrop = backdrop,
                            accentColor = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        MaterialSwitch(
                            checked = previewToggleChecked,
                            onCheckedChange = {
                                previewToggleChecked = it
                                haptics.perform(HapticKind.UI)
                            }
                        )
                    }
                }

                // Bottom Tabs simulation (reflects applyToBottomBar toggle)
                if (liquidConfig.applyToBottomBar) {
                    KittyLiquidBottomTabs(
                        selectedTabIndex = { previewSelectedTab },
                        onTabSelected = {
                            previewSelectedTab = it
                            haptics.perform(HapticKind.UI)
                        },
                        backdrop = backdrop,
                        tabsCount = 3,
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = MaterialTheme.colorScheme.primary
                    ) {
                        listOf(
                            Triple(stringResource(R.string.pref_liquid_preview_tab1), Icons.Rounded.MusicNote, 0),
                            Triple(stringResource(R.string.pref_liquid_preview_tab2), Icons.Rounded.TravelExplore, 1),
                            Triple(stringResource(R.string.pref_liquid_preview_tab3), Icons.Rounded.LibraryMusic, 2)
                        ).forEach { (title, icon, idx) ->
                            KittyLiquidBottomTab(
                                onClick = {
                                    previewSelectedTab = idx
                                    haptics.perform(HapticKind.UI)
                                }
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = title,
                                    tint = if (previewSelectedTab == idx) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = if (previewSelectedTab == idx) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(
                                Triple(stringResource(R.string.pref_liquid_preview_tab1), Icons.Rounded.MusicNote, 0),
                                Triple(stringResource(R.string.pref_liquid_preview_tab2), Icons.Rounded.TravelExplore, 1),
                                Triple(stringResource(R.string.pref_liquid_preview_tab3), Icons.Rounded.LibraryMusic, 2)
                            ).forEach { (title, icon, idx) ->
                                val selected = previewSelectedTab == idx
                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            previewSelectedTab = idx
                                            haptics.perform(HapticKind.UI)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = title,
                                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Granular UI components card allowing users to selectively enable/disable Liquid Glass styling
 * for specific elements (buttons, bottom bar, sliders, icon buttons, chips, etc.).
 */
@Composable
fun LiquidComponentsCard(
    config: LiquidGlassConfig,
    onConfigChange: (LiquidGlassConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.padding(horizontal = 4.dp)) {
            SettingsGroupTitle(title = stringResource(R.string.pref_liquid_components_title))
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 16.dp)
            ) {
                ComponentSwitchItem(
                    title = stringResource(R.string.pref_liquid_apply_buttons),
                    description = stringResource(R.string.pref_liquid_apply_buttons_desc),
                    checked = config.applyToButtons,
                    onCheckedChange = { onConfigChange(config.copy(applyToButtons = it)) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                ComponentSwitchItem(
                    title = stringResource(R.string.pref_liquid_apply_icon_buttons),
                    description = stringResource(R.string.pref_liquid_apply_icon_buttons_desc),
                    checked = config.applyToIconButtons,
                    onCheckedChange = { onConfigChange(config.copy(applyToIconButtons = it)) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                ComponentSwitchItem(
                    title = stringResource(R.string.pref_liquid_apply_bottom_bar),
                    description = stringResource(R.string.pref_liquid_apply_bottom_bar_desc),
                    checked = config.applyToBottomBar,
                    onCheckedChange = { onConfigChange(config.copy(applyToBottomBar = it)) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                ComponentSwitchItem(
                    title = stringResource(R.string.pref_liquid_apply_sliders),
                    description = stringResource(R.string.pref_liquid_apply_sliders_desc),
                    checked = config.applyToSliders,
                    onCheckedChange = { onConfigChange(config.copy(applyToSliders = it)) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                ComponentSwitchItem(
                    title = stringResource(R.string.pref_liquid_apply_controls),
                    description = stringResource(R.string.pref_liquid_apply_controls_desc),
                    checked = config.applyToControls,
                    onCheckedChange = { onConfigChange(config.copy(applyToControls = it)) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                ComponentSwitchItem(
                    title = stringResource(R.string.pref_liquid_apply_chips),
                    description = stringResource(R.string.pref_liquid_apply_chips_desc),
                    checked = config.applyToChips,
                    onCheckedChange = { onConfigChange(config.copy(applyToChips = it)) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                ComponentSwitchItem(
                    title = stringResource(R.string.pref_liquid_apply_text_fields),
                    description = stringResource(R.string.pref_liquid_apply_text_fields_desc),
                    checked = config.applyToTextFields,
                    onCheckedChange = { onConfigChange(config.copy(applyToTextFields = it)) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                ComponentSwitchItem(
                    title = stringResource(R.string.pref_liquid_apply_dialogs),
                    description = stringResource(R.string.pref_liquid_apply_dialogs_desc),
                    checked = config.applyToDialogs,
                    onCheckedChange = { onConfigChange(config.copy(applyToDialogs = it)) }
                )
            }
        }
    }
}

@Composable
private fun ComponentSwitchItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberAppHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                haptics.perform(HapticKind.UI)
                onCheckedChange(!checked)
            }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SettingsSwitch(
            checked = checked,
            onCheckedChange = {
                haptics.perform(HapticKind.UI)
                onCheckedChange(it)
            }
        )
    }
}

/**
 * Optical parameters fine-tuning card (corner radius, blur, refractions, highlight, etc.).
 */
@Composable
fun LiquidOpticalParametersCard(
    config: LiquidGlassConfig,
    onConfigChange: (LiquidGlassConfig) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsGroupTitle(title = stringResource(R.string.pref_liquid_experimental_title))
            TextButton(
                onClick = onReset,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.pref_liquid_reset),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Corner radius
                ParameterSliderItem(
                    title = stringResource(R.string.pref_liquid_corner_radius),
                    value = config.cornerRadiusFactor,
                    valueRange = 0.2f..1.8f,
                    valueLabel = String.format(Locale.US, "%.2fx", config.cornerRadiusFactor),
                    onValueChange = { onConfigChange(config.copy(cornerRadiusFactor = it)) }
                )

                // 2. Blur radius
                ParameterSliderItem(
                    title = stringResource(R.string.pref_liquid_blur_radius),
                    value = config.blurRadiusDp,
                    valueRange = 0f..32f,
                    valueLabel = String.format(Locale.US, "%.0f dp", config.blurRadiusDp),
                    onValueChange = { onConfigChange(config.copy(blurRadiusDp = it)) }
                )

                // 3. Refraction height
                ParameterSliderItem(
                    title = stringResource(R.string.pref_liquid_refraction_height),
                    value = config.refractionHeightDp,
                    valueRange = 0f..48f,
                    valueLabel = String.format(Locale.US, "%.0f dp", config.refractionHeightDp),
                    onValueChange = { onConfigChange(config.copy(refractionHeightDp = it)) }
                )

                // 4. Refraction amount
                ParameterSliderItem(
                    title = stringResource(R.string.pref_liquid_refraction_amount),
                    value = config.refractionAmountDp,
                    valueRange = 0f..48f,
                    valueLabel = String.format(Locale.US, "%.0f dp", config.refractionAmountDp),
                    onValueChange = { onConfigChange(config.copy(refractionAmountDp = it)) }
                )

                // 5. Chromatic aberration
                ParameterSliderItem(
                    title = stringResource(R.string.pref_liquid_chromatic_aberration),
                    value = config.chromaticAberration,
                    valueRange = 0f..1f,
                    valueLabel = String.format(Locale.US, "%.0f %%", config.chromaticAberration * 100),
                    onValueChange = { onConfigChange(config.copy(chromaticAberration = it)) }
                )

                // 6. Surface opacity
                ParameterSliderItem(
                    title = stringResource(R.string.pref_liquid_surface_alpha),
                    value = config.surfaceAlpha,
                    valueRange = 0.1f..0.9f,
                    valueLabel = String.format(Locale.US, "%.0f %%", config.surfaceAlpha * 100),
                    onValueChange = { onConfigChange(config.copy(surfaceAlpha = it)) }
                )

                // 7. Highlight & Shine intensity
                ParameterSliderItem(
                    title = stringResource(R.string.pref_liquid_highlight_intensity),
                    value = config.highlightIntensity,
                    valueRange = 0f..2f,
                    valueLabel = String.format(Locale.US, "%.0f %%", config.highlightIntensity * 100),
                    onValueChange = { onConfigChange(config.copy(highlightIntensity = it)) }
                )

                // 8. Navigation bubble width
                ParameterSliderItem(
                    title = stringResource(R.string.pref_liquid_bubble_width),
                    value = config.bubbleWidthFactor,
                    valueRange = 0.6f..1.4f,
                    valueLabel = String.format(Locale.US, "%.0f %%", config.bubbleWidthFactor * 100),
                    onValueChange = { onConfigChange(config.copy(bubbleWidthFactor = it)) }
                )
            }
        }
    }
}

/**
 * 3D, tactile dynamics and sensory effects card.
 */
@Composable
fun LiquidEffectsCard(
    config: LiquidGlassConfig,
    onConfigChange: (LiquidGlassConfig) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberAppHaptics()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.padding(horizontal = 4.dp)) {
            SettingsGroupTitle(title = stringResource(R.string.pref_liquid_effects_title))
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 16.dp)
            ) {
                // 1. 3D Depth effect
                ComponentSwitchItem(
                    title = stringResource(R.string.pref_liquid_depth_effect),
                    description = stringResource(R.string.pref_liquid_experimental_desc),
                    checked = config.depthEffect,
                    onCheckedChange = { onConfigChange(config.copy(depthEffect = it)) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // 2. Interactive touch bounce & squish
                ComponentSwitchItem(
                    title = stringResource(R.string.pref_liquid_interactive_bounce),
                    description = stringResource(R.string.pref_liquid_interactive_bounce_desc),
                    checked = config.interactiveBounce,
                    onCheckedChange = { onConfigChange(config.copy(interactiveBounce = it)) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // 3. Drop shadows
                ComponentSwitchItem(
                    title = stringResource(R.string.pref_liquid_shadows_enabled),
                    description = stringResource(R.string.pref_liquid_shadows_desc),
                    checked = config.shadowsEnabled,
                    onCheckedChange = { onConfigChange(config.copy(shadowsEnabled = it)) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // 4. Inner bevel depth shadow
                ComponentSwitchItem(
                    title = stringResource(R.string.pref_liquid_inner_shadow_enabled),
                    description = stringResource(R.string.pref_liquid_inner_shadow_desc),
                    checked = config.innerShadowEnabled,
                    onCheckedChange = { onConfigChange(config.copy(innerShadowEnabled = it)) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // 5. Color vibrancy boost
                ComponentSwitchItem(
                    title = stringResource(R.string.pref_liquid_vibrancy_enabled),
                    description = stringResource(R.string.pref_liquid_vibrancy_desc),
                    checked = config.vibrancyEnabled,
                    onCheckedChange = { onConfigChange(config.copy(vibrancyEnabled = it)) }
                )

                Spacer(Modifier.height(16.dp))

                // Reset Button
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.pref_liquid_reset),
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ParameterSliderItem(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
