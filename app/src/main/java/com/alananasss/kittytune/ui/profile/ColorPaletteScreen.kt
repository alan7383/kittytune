package com.alananasss.kittytune.ui.profile

import android.graphics.Color as AndroidColor
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.AppThemeMode
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.theme.MaterialKolorColorSpecOptions
import com.alananasss.kittytune.ui.theme.normalizedMaterialKolorColorSpecName
import com.alananasss.kittytune.ui.theme.parseMaterialKolorPaletteStyle
import com.alananasss.kittytune.ui.theme.rememberSoundTuneColorScheme
import com.materialkolor.PaletteStyle

import androidx.annotation.StringRes

private data class SeedColorOption(
    @param:StringRes val nameRes: Int,
    val color: Int,
)

private data class SeedColorGroup(
    @param:StringRes val titleRes: Int,
    val options: List<SeedColorOption>,
)

private val groupedKeyColorOptions = listOf(
    SeedColorGroup(
        titleRes = R.string.color_group_deep_blues,
        options = listOf(
            SeedColorOption(R.string.color_seed_deep_blue, Color(0xFF141A4C).toArgb()),
            SeedColorOption(R.string.color_seed_abyss, Color(0xFF0A0E29).toArgb()),
            SeedColorOption(R.string.color_seed_space_blue, Color(0xFF1A225D).toArgb()),
            SeedColorOption(R.string.color_seed_ink_blue, Color(0xFF0E143A).toArgb()),
            SeedColorOption(R.string.color_seed_midnight_indigo, Color(0xFF1A1B4B).toArgb()),
            SeedColorOption(R.string.color_seed_cosmic_blue, Color(0xFF1D2663).toArgb()),
            SeedColorOption(R.string.color_seed_twilight, Color(0xFF182055).toArgb()),
            SeedColorOption(R.string.color_seed_royal_navy, Color(0xFF101740).toArgb()),
            SeedColorOption(R.string.color_seed_midnight, Color(0xFF071A3D).toArgb()),
            SeedColorOption(R.string.color_seed_navy, Color(0xFF0D47A1).toArgb()),
        ),
    ),
    SeedColorGroup(
        titleRes = R.string.color_group_blues_cyans,
        options = listOf(
            SeedColorOption(R.string.color_seed_sapphire, Color(0xFF1565C0).toArgb()),
            SeedColorOption(R.string.color_seed_electric_blue, Color(0xFF2962FF).toArgb()),
            SeedColorOption(R.string.color_seed_azure, Color(0xFF0288D1).toArgb()),
            SeedColorOption(R.string.color_seed_cyan, Color(0xFF00ACC1).toArgb()),
            SeedColorOption(R.string.color_seed_teal, Color(0xFF00897B).toArgb())
        )
    ),
    SeedColorGroup(
        titleRes = R.string.color_group_greens_teals,
        options = listOf(
            SeedColorOption(R.string.color_seed_deep_teal, Color(0xFF00695C).toArgb()),
            SeedColorOption(R.string.color_seed_emerald, Color(0xFF00A86B).toArgb()),
            SeedColorOption(R.string.color_seed_forest, Color(0xFF2E7D32).toArgb()),
            SeedColorOption(R.string.color_seed_moss, Color(0xFF558B2F).toArgb()),
            SeedColorOption(R.string.color_seed_lime, Color(0xFF8BC34A).toArgb()),
            SeedColorOption(R.string.color_seed_olive, Color(0xFF6B7D2A).toArgb())
        )
    ),
    SeedColorGroup(
        titleRes = R.string.color_group_sunset_warm,
        options = listOf(
            SeedColorOption(R.string.color_seed_gold, Color(0xFFFFB300).toArgb()),
            SeedColorOption(R.string.color_seed_amber, Color(0xFFFF8F00).toArgb()),
            SeedColorOption(R.string.color_seed_kitty_orange, Color(0xFFFF7A1A).toArgb()),
            SeedColorOption(R.string.color_seed_deep_orange, Color(0xFFE64A19).toArgb()),
            SeedColorOption(R.string.color_seed_coral, Color(0xFFFF7043).toArgb()),
            SeedColorOption(R.string.color_seed_sand, Color(0xFFD1A054).toArgb())
        )
    ),
    SeedColorGroup(
        titleRes = R.string.color_group_pinks_purples,
        options = listOf(
            SeedColorOption(R.string.color_seed_crimson, Color(0xFFC62828).toArgb()),
            SeedColorOption(R.string.color_seed_rose, Color(0xFFD81B60).toArgb()),
            SeedColorOption(R.string.color_seed_hot_pink, Color(0xFFFF4081).toArgb()),
            SeedColorOption(R.string.color_seed_magenta, Color(0xFFC2185B).toArgb()),
            SeedColorOption(R.string.color_seed_plum, Color(0xFF8E24AA).toArgb()),
            SeedColorOption(R.string.color_seed_deep_purple, Color(0xFF512DA8).toArgb()),
            SeedColorOption(R.string.color_seed_indigo, Color(0xFF303F9F).toArgb()),
            SeedColorOption(R.string.color_seed_periwinkle, Color(0xFF5E6AD2).toArgb())
        )
    ),
    SeedColorGroup(
        titleRes = R.string.color_group_neutrals,
        options = listOf(
            SeedColorOption(R.string.color_seed_blue_gray, Color(0xFF546E7A).toArgb()),
            SeedColorOption(R.string.color_seed_slate, Color(0xFF37474F).toArgb()),
            SeedColorOption(R.string.color_seed_graphite, Color(0xFF424242).toArgb()),
            SeedColorOption(R.string.color_seed_warm_gray, Color(0xFF6D5F5B).toArgb()),
            SeedColorOption(R.string.color_seed_mocha, Color(0xFF795548).toArgb())
        )
    )
)

@Composable
fun ColorPaletteScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    val haptic = LocalHapticFeedback.current

    var currentKeyColor by remember { mutableIntStateOf(prefs.getKeyColor()) }
    var colorStyle by remember { mutableStateOf(parseMaterialKolorPaletteStyle(prefs.getColorStyle()).name) }
    var colorSpec by remember { mutableStateOf(normalizedMaterialKolorColorSpecName(prefs.getColorSpec())) }
    val themeMode = prefs.getThemeMode()
    val pureBlack = prefs.getPureBlack()
    val dynamicTheme = prefs.getDynamicTheme()

    val isDark = ((themeMode == AppThemeMode.DARK) || (themeMode == AppThemeMode.SYSTEM && isSystemInDarkTheme()))

    SettingsScaffold(
        title = stringResource(R.string.color_palette_screen_title),
        onBackClick = onBackClick
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            ThemePreviewCard(
                keyColor = currentKeyColor,
                isDark = isDark,
                pureBlack = pureBlack,
                dynamicColor = dynamicTheme,
                colorStyle = colorStyle,
                colorSpec = colorSpec
            )

            SeedPaletteCard(
                selectedKeyColor = currentKeyColor,
                isDark = isDark,
                pureBlack = pureBlack,
                dynamicColor = dynamicTheme,
                colorStyle = colorStyle,
                colorSpec = colorSpec,
            ) { seed ->
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                currentKeyColor = seed
                prefs.setKeyColor(seed)
            }

                CustomSeedPickerCard(
                    selectedKeyColor = currentKeyColor,
                    isDark = isDark,
                    pureBlack = pureBlack,
                    dynamicColor = dynamicTheme,
                    colorStyle = colorStyle,
                    colorSpec = colorSpec,
                    onSeedChangedRealtime = { seed ->
                        currentKeyColor = seed
                        com.alananasss.kittytune.ui.theme.ThemeState.previewKeyColor = seed
                    },
                ) { seed ->
                    currentKeyColor = seed
                    com.alananasss.kittytune.ui.theme.ThemeState.previewKeyColor = null
                    prefs.setKeyColor(seed)
                }

            ColorGenerationCard(
                colorStyle = colorStyle,
                colorSpec = colorSpec,
                onStyleSelected = {
                    colorStyle = it
                    prefs.setColorStyle(it)
                },
                onSpecSelected = {
                    colorSpec = it
                    prefs.setColorSpec(it)
                }
            )

            Spacer(Modifier.height(140.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SeedPaletteCard(
    selectedKeyColor: Int,
    isDark: Boolean,
    pureBlack: Boolean,
    dynamicColor: Boolean,
    colorStyle: String,
    colorSpec: String,
    onSeedSelected: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.pref_seed_palette_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            var selectedCategoryIndex by remember {
                val index = groupedKeyColorOptions.indexOfFirst { group ->
                    group.options.any { it.color == selectedKeyColor }
                }
                mutableIntStateOf(if (index != -1) index else 0)
            }

            LaunchedEffect(selectedKeyColor) {
                val index = groupedKeyColorOptions.indexOfFirst { group ->
                    group.options.any { it.color == selectedKeyColor }
                }
                if (index != -1) {
                    selectedCategoryIndex = index
                }
            }

            val view = androidx.compose.ui.platform.LocalView.current
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(groupedKeyColorOptions) { index, group ->
                    val isSelected = selectedCategoryIndex == index
                    val containerColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                        label = "chipBg"
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "chipText"
                    )
                    Button(
                        onClick = {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                            selectedCategoryIndex = index
                        },
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = containerColor,
                            contentColor = contentColor
                        )
                    ) {
                        Text(stringResource(group.titleRes))
                    }
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ColorButtonMaterial(
                    seedColor = null,
                    isSelected = selectedKeyColor == 0,
                    isDark = isDark,
                    pureBlack = pureBlack,
                    dynamicColor = dynamicColor,
                    colorStyle = colorStyle,
                    colorSpec = colorSpec,
                    onClick = { onSeedSelected(0) },
                )

                val activeGroup = groupedKeyColorOptions[selectedCategoryIndex]
                activeGroup.options.forEach { option ->
                    ColorButtonMaterial(
                        seedColor = option.color,
                        isSelected = selectedKeyColor == option.color,
                        isDark = isDark,
                        pureBlack = pureBlack,
                        dynamicColor = dynamicColor,
                        colorStyle = colorStyle,
                        colorSpec = colorSpec,
                        onClick = { onSeedSelected(option.color) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomSeedPickerCard(
    selectedKeyColor: Int,
    isDark: Boolean,
    pureBlack: Boolean,
    dynamicColor: Boolean,
    colorStyle: String,
    colorSpec: String,
    onSeedChangedRealtime: (Int) -> Unit,
    onSeedChanged: (Int) -> Unit
) {
    val fallbackArgb = MaterialTheme.colorScheme.primary.toArgb()
    var workingArgb by remember(selectedKeyColor, fallbackArgb) {
        mutableIntStateOf(if (selectedKeyColor != 0) selectedKeyColor else fallbackArgb)
    }
    var hexText by remember { mutableStateOf(workingArgb.toSeedHex()) }
    val hsv = remember(workingArgb) { workingArgb.toHsv() }
    val pickerScheme = rememberSoundTuneColorScheme(
        useDarkTheme = isDark,
        dynamicColor = dynamicColor,
        pureBlack = pureBlack,
        keyColor = workingArgb,
        colorStyle = colorStyle,
        colorSpec = colorSpec
    )

    val animatedBorderColor by animateColorAsState(targetValue = pickerScheme.primary, label = "pickerBorder")

    LaunchedEffect(workingArgb) {
        hexText = workingArgb.toSeedHex()
    }

    fun updateWorkingColor(newArgb: Int) {
        workingArgb = newArgb
        onSeedChanged(newArgb)
    }

    val hueBrush = remember {
        Brush.horizontalGradient(
            colors = listOf(
                Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
            )
        )
    }
    val satBrush = remember(hsv[0], hsv[2]) {
        Brush.horizontalGradient(
            colors = listOf(
                Color(hsvToSeedArgb(hsv[0], 0.05f, hsv[2])),
                Color(hsvToSeedArgb(hsv[0], 1f, hsv[2]))
            )
        )
    }
    val valBrush = remember(hsv[0], hsv[1]) {
        Brush.horizontalGradient(
            colors = listOf(
                Color.Black,
                Color(hsvToSeedArgb(hsv[0], hsv[1], 1f))
            )
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.pref_custom_seed_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(72.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = Color(workingArgb),
                    border = BorderStroke(2.dp, animatedBorderColor)
                ) {}

                OutlinedTextField(
                    value = hexText,
                    onValueChange = { input ->
                        val normalized = input.trim().take(9)
                        hexText = normalized
                        parseHexSeedColor(normalized)?.let {
                            updateWorkingColor(it)
                            onSeedChangedRealtime(it)
                            onSeedChanged(it)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(stringResource(R.string.color_hex_label)) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii
                    )
                )
            }

            PickerSlider(
                label = stringResource(R.string.color_hue_label),
                value = hsv[0],
                valueText = hsv[0].toInt().toString(),
                valueRange = 0f..360f,
                gradientBrush = hueBrush,
                onValueChange = { hue ->
                    workingArgb = hsvToSeedArgb(hue, hsv[1], hsv[2])
                    onSeedChangedRealtime(workingArgb)
                },
                onValueChangeFinished = { onSeedChanged(workingArgb) }
            )
            PickerSlider(
                label = stringResource(R.string.color_saturation_label),
                value = hsv[1],
                valueText = "${(hsv[1] * 100).toInt()}%",
                valueRange = 0f..1f,
                gradientBrush = satBrush,
                onValueChange = { saturation ->
                    workingArgb = hsvToSeedArgb(hsv[0], saturation, hsv[2])
                    onSeedChangedRealtime(workingArgb)
                },
                onValueChangeFinished = { onSeedChanged(workingArgb) }
            )
            PickerSlider(
                label = stringResource(R.string.color_brightness_label),
                value = hsv[2],
                valueText = "${(hsv[2] * 100).toInt()}%",
                valueRange = 0f..1f,
                gradientBrush = valBrush,
                onValueChange = { brightness ->
                    workingArgb = hsvToSeedArgb(hsv[0], hsv[1], brightness)
                    onSeedChangedRealtime(workingArgb)
                },
                onValueChangeFinished = { onSeedChanged(workingArgb) }
            )
        }
    }
}
@Composable
private fun ColorGenerationCard(
    colorStyle: String,
    colorSpec: String,
    onStyleSelected: (String) -> Unit,
    onSpecSelected: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column {
            Text(
                text = stringResource(R.string.pref_color_generation_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 2.dp)
            )
            val styles = listOf("System") + PaletteStyle.entries.map { it.name }
            SettingsDropdownRow(
                title = stringResource(R.string.pref_color_style_title),
                items = styles,
                selectedItem = colorStyle,
                onItemSelected = onStyleSelected
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            SettingsDropdownRow(
                title = stringResource(R.string.pref_color_spec_title),
                items = MaterialKolorColorSpecOptions,
                selectedItem = colorSpec,
                onItemSelected = onSpecSelected
            )
        }
    }
}

@Composable
private fun PickerSlider(
    label: String,
    value: Float,
    valueText: String,
    valueRange: ClosedFloatingPointRange<Float>,
    gradientBrush: Brush,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(gradientBrush)
            )

            Slider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                colors = SliderDefaults.colors(
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun SettingsDropdownRow(
    title: String,
    items: List<String>,
    selectedItem: String,
    onItemSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(value = false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Box {
            Text(
                text = selectedItem,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                items.forEach { label ->
                    DropdownMenuItem(
                        text = { Text(label, color = if (label == selectedItem) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                        onClick = { onItemSelected(label); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemePreviewCard(
    keyColor: Int,
    isDark: Boolean,
    pureBlack: Boolean,
    dynamicColor: Boolean,
    colorStyle: String,
    colorSpec: String
) {
    val colorScheme = rememberSoundTuneColorScheme(
        useDarkTheme = isDark,
        dynamicColor = dynamicColor,
        pureBlack = pureBlack,
        keyColor = keyColor,
        colorStyle = colorStyle,
        colorSpec = colorSpec
    )

    val animatedBg by animateColorAsState(targetValue = colorScheme.background, label = "bg")
    val animatedOnSurface by animateColorAsState(targetValue = colorScheme.onSurface, label = "onSurface")
    val animatedPrimary by animateColorAsState(targetValue = colorScheme.primary, label = "primary")
    val animatedPrimaryContainer by animateColorAsState(targetValue = colorScheme.primaryContainer, label = "primaryContainer")
    val animatedSecondaryContainer by animateColorAsState(targetValue = colorScheme.secondaryContainer, label = "secondaryContainer")
    val animatedOnSecondaryContainer by animateColorAsState(targetValue = colorScheme.onSecondaryContainer, label = "onSecondaryContainer")
    val animatedSurfaceContainer by animateColorAsState(targetValue = colorScheme.surfaceContainer, label = "surfaceContainer")
    val animatedSurfaceContainerHigh by animateColorAsState(targetValue = colorScheme.surfaceContainerHigh, label = "surfaceContainerHigh")
    val animatedOnSurfaceVariant by animateColorAsState(targetValue = colorScheme.onSurfaceVariant, label = "onSurfaceVariant")

    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .width(200.dp)
                .aspectRatio(0.46f),
            color = animatedBg,
            shape = RoundedCornerShape(32.dp),
            border = BorderStroke(4.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(animatedPrimary))
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.height(14.dp).width(80.dp).clip(RoundedCornerShape(4.dp)).background(animatedOnSurface))
                }

                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    repeat(3) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(animatedSurfaceContainerHigh))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.height(12.dp).width(100.dp).clip(RoundedCornerShape(4.dp)).background(animatedOnSurface))
                                Box(modifier = Modifier.height(10.dp).width(60.dp).clip(RoundedCornerShape(4.dp)).background(animatedOnSurface.copy(alpha = 0.6f)))
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = animatedSecondaryContainer
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(animatedPrimaryContainer))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.height(10.dp).width(60.dp).clip(RoundedCornerShape(4.dp)).background(animatedOnSecondaryContainer))
                            Box(modifier = Modifier.height(8.dp).width(40.dp).clip(RoundedCornerShape(4.dp)).background(animatedOnSecondaryContainer.copy(alpha = 0.6f)))
                        }
                        Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(animatedOnSecondaryContainer))
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = animatedSurfaceContainer
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.width(48.dp).height(24.dp).clip(RoundedCornerShape(12.dp)).background(animatedSecondaryContainer))
                        Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(animatedOnSurfaceVariant))
                        Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(animatedOnSurfaceVariant))
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorButtonMaterial(
    seedColor: Int?,
    isSelected: Boolean,
    isDark: Boolean,
    pureBlack: Boolean,
    dynamicColor: Boolean,
    colorStyle: String,
    colorSpec: String,
    onClick: () -> Unit,
) {
    val colorScheme = if (seedColor == null) {
        rememberSoundTuneColorScheme(
            useDarkTheme = isDark,
            dynamicColor = dynamicColor,
            pureBlack = pureBlack,
            keyColor = 0,
            colorStyle = colorStyle,
            colorSpec = colorSpec,
        )
    } else null

    val rawHsv = remember(seedColor) { seedColor?.toHsv() }

    val color1 = seedColor?.let { Color(it) } ?: Color.Transparent
    val color2 = remember(rawHsv) {
        if (rawHsv != null) {
            Color(hsvToSeedArgb(
                rawHsv[0],
                (rawHsv[1] * 0.25f).coerceIn(0f, 1f),
                (rawHsv[2] + (1f - rawHsv[2]) * 0.75f).coerceIn(0f, 1f)
            ))
        } else Color.Transparent
    }
    val color3 = remember(rawHsv) {
        if (rawHsv != null) {
            Color(hsvToSeedArgb(
                rawHsv[0],
                (rawHsv[1] * 0.6f).coerceIn(0f, 1f),
                (rawHsv[2] + (1f - rawHsv[2]) * 0.35f).coerceIn(0f, 1f)
            ))
        } else Color.Transparent
    }

    val ringAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        label = "ringAlpha"
    )
    val accentColor = if (seedColor != null) color1 else (colorScheme?.primary ?: Color.White)

    Box(
        modifier = Modifier.size(56.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .border(2.dp, accentColor.copy(alpha = ringAlpha), CircleShape)
            )
        }

        Surface(
            onClick = onClick,
            shape = CircleShape,
            modifier = Modifier.size(if (isSelected) 42.dp else 48.dp),
            color = Color.Transparent
        ) {
            if (seedColor == null && colorScheme != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawArc(color = colorScheme.primary, startAngle = 180f, sweepAngle = 180f, useCenter = true)
                    drawArc(color = colorScheme.tertiary, startAngle = 0f, sweepAngle = 90f, useCenter = true)
                    drawArc(color = colorScheme.secondary, startAngle = 90f, sweepAngle = 90f, useCenter = true)
                }
            } else {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawArc(color = color1, startAngle = 180f, sweepAngle = 180f, useCenter = true)
                    drawArc(color = color3, startAngle = 0f, sweepAngle = 90f, useCenter = true)
                    drawArc(color = color2, startAngle = 90f, sweepAngle = 90f, useCenter = true)
                }
            }
        }

        // Check badge at bottom-right (Android 16 style)
        AnimatedVisibility(
            visible = isSelected,
            enter = fadeIn() + scaleIn(initialScale = 0.5f),
            exit = fadeOut() + scaleOut(targetScale = 0.5f),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(accentColor)
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

private fun Int.toHsv(): FloatArray =
    FloatArray(3).also { hsv -> AndroidColor.colorToHSV(this, hsv) }

private fun hsvToSeedArgb(hue: Float, saturation: Float, value: Float): Int =
    AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))

private fun Int.toSeedHex(): String =
    "#%06X".format(this and 0x00FFFFFF)

private fun parseHexSeedColor(input: String): Int? {
    val cleaned = input.trim().removePrefix("#")
    if (cleaned.length != 6 && cleaned.length != 8) return null
    return cleaned.toLongOrNull(radix = 16)?.let { parsed ->
        val rgb = if (cleaned.length == 8) parsed and 0x00FFFFFF else parsed
        (0xFF000000 or rgb).toInt()
    }
}

@Composable
fun surfaceColorAtElevation(elevation: androidx.compose.ui.unit.Dp): Color {
    if (elevation == 0.dp) return MaterialTheme.colorScheme.surface
    val alpha = ((4.5f * kotlin.math.ln(elevation.value + 1)) + 2f) / 100f
    return androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primary, alpha)
}
