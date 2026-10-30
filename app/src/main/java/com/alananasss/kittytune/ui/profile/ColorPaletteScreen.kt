package com.alananasss.kittytune.ui.profile

import android.graphics.Color as AndroidColor
import android.os.Build
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.Slider as Material3Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.common.rememberAppHaptics
import com.alananasss.kittytune.ui.theme.MaterialKolorColorSpecOptions
import com.alananasss.kittytune.ui.theme.ThemeState
import com.alananasss.kittytune.ui.theme.normalizedMaterialKolorColorSpecName
import com.alananasss.kittytune.ui.theme.parseMaterialKolorPaletteStyle
import com.materialkolor.PaletteStyle
import com.alananasss.kittytune.ui.design.Card
import com.alananasss.kittytune.ui.design.FilterChip
import com.alananasss.kittytune.ui.design.IconButton
import com.alananasss.kittytune.ui.design.OutlinedTextField
import com.alananasss.kittytune.ui.design.Surface

private data class PresetAccent(
    @StringRes val nameRes: Int,
    val color: Int,
    val isDynamic: Boolean = false,
)

private val curatedPresets = listOf(
    PresetAccent(R.string.theme_preset_ocean, 0xFF1565C0.toInt()),
    PresetAccent(R.string.theme_preset_lavender, 0xFF7E57C2.toInt()),
    PresetAccent(R.string.color_seed_electric_blue, 0xFF2962FF.toInt()),
    PresetAccent(R.string.theme_preset_mint, 0xFF00A884.toInt()),
    PresetAccent(R.string.theme_preset_forest, 0xFF2E7D32.toInt()),
    PresetAccent(R.string.color_seed_gold, 0xFFFFB300.toInt()),
    PresetAccent(R.string.color_seed_kitty_orange, 0xFFFF7A1A.toInt()),
    PresetAccent(R.string.theme_preset_sunset, 0xFFE64A19.toInt()),
    PresetAccent(R.string.theme_preset_rose, 0xFFD81B60.toInt()),
    PresetAccent(R.string.color_seed_plum, 0xFF8E24AA.toInt()),
    PresetAccent(R.string.color_seed_cyan, 0xFF00ACC1.toInt()),
    PresetAccent(R.string.color_seed_slate, 0xFF455A64.toInt()),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ColorPaletteScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    val haptic = rememberAppHaptics()
    val clipboardManager = LocalClipboardManager.current

    var currentKeyColor by remember { mutableIntStateOf(prefs.getKeyColor()) }
    var colorStyle by remember { mutableStateOf(parseMaterialKolorPaletteStyle(prefs.getColorStyle()).name) }
    var colorSpec by remember { mutableStateOf(normalizedMaterialKolorColorSpecName(prefs.getColorSpec())) }
    var dynamicTheme by remember { mutableStateOf(prefs.getDynamicTheme()) }

    val fallbackArgb = MaterialTheme.colorScheme.primary.toArgb()
    val initialSeedColor = remember {
        if (currentKeyColor != 0) currentKeyColor else fallbackArgb
    }
    val initialHsv = remember { initialSeedColor.toHsv() }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember { mutableFloatStateOf(initialHsv[2]) }

    var workingArgb by remember { mutableIntStateOf(initialSeedColor) }
    var hexText by remember { mutableStateOf(initialSeedColor.toSeedHex()) }

    fun syncSlidersFromColor(seed: Int) {
        val hsvArr = seed.toHsv()
        if (hsvArr[1] >= 0.02f && hsvArr[2] >= 0.02f) {
            hue = hsvArr[0]
        }
        saturation = hsvArr[1]
        brightness = hsvArr[2]
        workingArgb = seed
        hexText = seed.toSeedHex()
    }

    fun commitColor(seed: Int, updateDynamic: Boolean = false) {
        currentKeyColor = seed
        ThemeState.previewKeyColor = null
        prefs.setKeyColor(seed)
        if (updateDynamic) {
            val isDyn = (seed == 0)
            dynamicTheme = isDyn
            prefs.setDynamicTheme(isDyn)
            if (isDyn) {
                prefs.setColorStyle("System")
                colorStyle = "System"
                syncSlidersFromColor(fallbackArgb)
            }
        } else {
            dynamicTheme = false
            prefs.setDynamicTheme(false)
        }
    }

    fun previewColorRealtime(seed: Int) {
        currentKeyColor = seed
        dynamicTheme = false
        ThemeState.previewKeyColor = seed
    }

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

            // 1. Curated Preset Swatches (Riff's theme-palette-picker)
            PresetSwatchesCard(
                currentKeyColor = currentKeyColor,
                dynamicTheme = dynamicTheme,
                onPresetSelected = { seed, isDyn ->
                    haptic.perform(HapticKind.UI)
                    if (isDyn || seed == 0) {
                        commitColor(0, updateDynamic = true)
                    } else {
                        syncSlidersFromColor(seed)
                        commitColor(seed, updateDynamic = false)
                    }
                }
            )

            // 3. Custom Seed Fine-Tuning (Hue, Saturation, Brightness, Hex)
            CustomSeedPickerCard(
                workingArgb = workingArgb,
                hexText = hexText,
                hue = hue,
                saturation = saturation,
                brightness = brightness,
                onHexChange = { newHex ->
                    hexText = newHex
                    parseHexSeedColor(newHex)?.let { newArgb ->
                        syncSlidersFromColor(newArgb)
                        previewColorRealtime(newArgb)
                        commitColor(newArgb, updateDynamic = false)
                    }
                },
                onHueChange = { newHue ->
                    hue = newHue.coerceIn(0f, 360f)
                    val newArgb = hsvToSeedArgb(hue, saturation, brightness)
                    workingArgb = newArgb
                    hexText = newArgb.toSeedHex()
                    previewColorRealtime(newArgb)
                },
                onSaturationChange = { newSat ->
                    saturation = newSat.coerceIn(0f, 1f)
                    val newArgb = hsvToSeedArgb(hue, saturation, brightness)
                    workingArgb = newArgb
                    hexText = newArgb.toSeedHex()
                    previewColorRealtime(newArgb)
                },
                onBrightnessChange = { newBright ->
                    brightness = newBright.coerceIn(0f, 1f)
                    val newArgb = hsvToSeedArgb(hue, saturation, brightness)
                    workingArgb = newArgb
                    hexText = newArgb.toSeedHex()
                    previewColorRealtime(newArgb)
                },
                onHsvChangeFinished = {
                    commitColor(workingArgb, updateDynamic = false)
                },
                onCopyHex = {
                    clipboardManager.setText(AnnotatedString(hexText))
                    haptic.perform(HapticKind.CONFIRM)
                    Toast.makeText(context, context.getString(R.string.color_hex_copied), Toast.LENGTH_SHORT).show()
                },
                onPasteHex = {
                    val clipText = clipboardManager.getText()?.text?.trim().orEmpty()
                    parseHexSeedColor(clipText)?.let { pastedArgb ->
                        haptic.perform(HapticKind.CONFIRM)
                        syncSlidersFromColor(pastedArgb)
                        previewColorRealtime(pastedArgb)
                        commitColor(pastedArgb, updateDynamic = false)
                    } ?: run {
                        haptic.perform(HapticKind.REJECT)
                    }
                }
            )

            // 4. Color Engine (Palette Style & Color Spec)
            ColorEngineCard(
                colorStyle = colorStyle,
                colorSpec = colorSpec,
                onStyleSelected = {
                    haptic.perform(HapticKind.UI)
                    colorStyle = it
                    prefs.setColorStyle(it)
                },
                onSpecSelected = {
                    haptic.perform(HapticKind.UI)
                    colorSpec = it
                    prefs.setColorSpec(it)
                }
            )

            Spacer(Modifier.height(140.dp))
        }
    }
}

/**
 * Curated Preset Swatches Card (Riff-inspired .theme-palette-picker).
 * Provides one-tap access to harmonious ready-to-use color seeds.
 */
@Composable
private fun PresetSwatchesCard(
    currentKeyColor: Int,
    dynamicTheme: Boolean,
    onPresetSelected: (Int, Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.theme_preset_accents_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.theme_preset_accents_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
            ) {
                // System / Dynamic swatch on Android 12+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    item {
                        val isSelected = currentKeyColor == 0 && dynamicTheme
                        val dynamicSweep = remember {
                            Brush.sweepGradient(
                                listOf(
                                    Color(0xFF2962FF),
                                    Color(0xFF00ACC1),
                                    Color(0xFF00A884),
                                    Color(0xFFFFB300),
                                    Color(0xFFFF4081),
                                    Color(0xFF2962FF)
                                )
                            )
                        }
                        Box(
                            modifier = Modifier.size(54.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                )
                            }
                            Surface(
                                onClick = { onPresetSelected(0, true) },
                                shape = CircleShape,
                                modifier = Modifier.size(if (isSelected) 42.dp else 48.dp),
                                color = Color.Transparent
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(dynamicSweep),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = stringResource(R.string.color_palette_system_dynamic),
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .align(Alignment.BottomEnd)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Curated palette presets
                items(curatedPresets) { preset ->
                    val isSelected = currentKeyColor == preset.color && !dynamicTheme
                    Box(
                        modifier = Modifier.size(54.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            )
                        }
                        Surface(
                            onClick = { onPresetSelected(preset.color, false) },
                            shape = CircleShape,
                            modifier = Modifier.size(if (isSelected) 42.dp else 48.dp),
                            color = Color(preset.color),
                            shadowElevation = 1.dp
                        ) {}

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                    contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Custom Seed Fine-Tuning Card with color swatch, hex input (with copy/paste),
 * and thick Material 3 Expressive sliders for Hue, Saturation, and Brightness.
 */
@Composable
private fun CustomSeedPickerCard(
    workingArgb: Int,
    hexText: String,
    hue: Float,
    saturation: Float,
    brightness: Float,
    onHexChange: (String) -> Unit,
    onHueChange: (Float) -> Unit,
    onSaturationChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onHsvChangeFinished: () -> Unit,
    onCopyHex: () -> Unit,
    onPasteHex: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.pref_custom_seed_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Swatch + Hex Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(62.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(workingArgb),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    shadowElevation = 2.dp
                ) {}

                OutlinedTextField(
                    value = hexText,
                    onValueChange = { input ->
                        val normalized = input.trim().take(9)
                        onHexChange(normalized)
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    label = { Text(stringResource(R.string.color_hex_label)) },
                    textStyle = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Palette,
                            contentDescription = null,
                            tint = Color(workingArgb),
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            IconButton(onClick = onCopyHex, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = onPasteHex, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentPaste,
                                    contentDescription = "Paste",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii
                    )
                )
            }

            // 1. Hue Slider (Riff segmented design)
            RiffHueSlider(
                value = hue,
                valueText = "${hue.roundToInt()}°",
                onValueChange = onHueChange,
                onValueChangeFinished = onHsvChangeFinished
            )

            // 2. Saturation Slider (Riff active/inactive split track)
            RiffTrackSlider(
                label = stringResource(R.string.color_saturation_label),
                value = saturation,
                valueText = "${(saturation * 100).roundToInt()}%",
                activeColor = MaterialTheme.colorScheme.primary,
                onValueChange = onSaturationChange,
                onValueChangeFinished = onHsvChangeFinished,
                hints = listOf(
                    stringResource(R.string.color_hint_muted),
                    stringResource(R.string.color_hint_balanced),
                    stringResource(R.string.color_hint_vivid)
                )
            )

            // 3. Brightness Slider (Riff active/inactive split track)
            RiffTrackSlider(
                label = stringResource(R.string.color_brightness_label),
                value = brightness,
                valueText = "${(brightness * 100).roundToInt()}%",
                activeColor = MaterialTheme.colorScheme.primary,
                onValueChange = onBrightnessChange,
                onValueChangeFinished = onHsvChangeFinished,
                hints = listOf(
                    stringResource(R.string.color_hint_dark),
                    stringResource(R.string.color_hint_default),
                    stringResource(R.string.color_hint_bright)
                )
            )
        }
    }
}

/**
 * Riff-style Segmented Hue Slider.
 * 12 color segments across the 360° spectrum with a clean negative-space cutout
 * around a floating vertical pill thumb.
 */
@Composable
private fun RiffHueSlider(
    value: Float,
    valueText: String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    val haptic = rememberAppHaptics()
    var widthPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    val thumbWidth = 5.dp
    val thumbHeight = 26.dp
    val trackHeight = 16.dp

    val thumbWidthPx = with(density) { thumbWidth.toPx() }
    val cutoutHalfWidthPx = with(density) { 10.dp.toPx() }

    val fraction = (value / 360f).coerceIn(0f, 1f)

    var lastBucket by remember { mutableIntStateOf(-1) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.color_hue_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(thumbHeight)
                .onSizeChanged { widthPx = it.width.toFloat() }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val usableWidth = (widthPx - thumbWidthPx).coerceAtLeast(1f)
                        val updateVal = { x: Float ->
                            val frac = ((x - thumbWidthPx / 2f) / usableWidth).coerceIn(0f, 1f)
                            val newHue = frac * 360f
                            currentOnValueChange(newHue)
                            val bucket = (frac * 24).toInt()
                            if (bucket != lastBucket) {
                                lastBucket = bucket
                                haptic.perform(HapticKind.SLIDER)
                            }
                        }
                        updateVal(down.position.x)
                        val pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (change.pressed) {
                                change.consume()
                                updateVal(change.position.x)
                            } else {
                                break
                            }
                        }
                        currentOnValueChangeFinished()
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            val usableWidth = (widthPx - thumbWidthPx).coerceAtLeast(1f)
            val thumbXPx = (thumbWidthPx / 2f) + (fraction * usableWidth)

            // 12-segment track with dynamic thumb cutout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .drawWithContent {
                        clipRect(
                            left = thumbXPx - cutoutHalfWidthPx,
                            top = 0f,
                            right = thumbXPx + cutoutHalfWidthPx,
                            bottom = size.height,
                            clipOp = ClipOp.Difference
                        ) {
                            this@drawWithContent.drawContent()
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (i in 0 until 12) {
                    val startAngle = i * 30f
                    val endAngle = (i + 1) * 30f
                    val segBrush = remember(i) {
                        Brush.horizontalGradient(
                            listOf(
                                Color(AndroidColor.HSVToColor(floatArrayOf(startAngle, 0.76f, 0.90f))),
                                Color(AndroidColor.HSVToColor(floatArrayOf(endAngle, 0.76f, 0.90f)))
                            )
                        )
                    }
                    val shape = when (i) {
                        0 -> RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 3.dp, bottomEnd = 3.dp)
                        11 -> RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 8.dp, bottomEnd = 8.dp)
                        else -> RoundedCornerShape(3.dp)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(shape)
                            .background(segBrush)
                    )
                }
            }

            // Vertical pill thumb (Riff style: onSurface)
            if (widthPx > 0f) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset((thumbXPx - thumbWidthPx / 2f).roundToInt(), 0) }
                        .size(width = thumbWidth, height = thumbHeight)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.onSurface)
                )
            }
        }
    }
}

/**
 * Riff-style Split Track Slider (used for Intensity/Saturation and Brightness).
 * Features an active colored track on the left, an inactive translucent track on the right,
 * and a vertical pill thumb centered in a clean 16dp gap between the two tracks.
 */
@Composable
private fun RiffTrackSlider(
    label: String,
    value: Float, // 0f..1f
    valueText: String,
    activeColor: Color,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    hints: List<String>? = null,
) {
    val haptic = rememberAppHaptics()
    var widthPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    val thumbWidth = 5.dp
    val thumbHeight = 26.dp
    val trackHeight = 16.dp
    val gapHalfWidth = 8.dp

    val thumbWidthPx = with(density) { thumbWidth.toPx() }
    val gapHalfWidthPx = with(density) { gapHalfWidth.toPx() }

    val fraction = value.coerceIn(0f, 1f)
    var lastBucket by remember { mutableIntStateOf(-1) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(thumbHeight)
                .onSizeChanged { widthPx = it.width.toFloat() }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val usableWidth = (widthPx - thumbWidthPx).coerceAtLeast(1f)
                        val updateVal = { x: Float ->
                            val frac = ((x - thumbWidthPx / 2f) / usableWidth).coerceIn(0f, 1f)
                            currentOnValueChange(frac)
                            val bucket = (frac * 20).toInt()
                            if (bucket != lastBucket) {
                                lastBucket = bucket
                                haptic.perform(HapticKind.SLIDER)
                            }
                        }
                        updateVal(down.position.x)
                        val pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (change.pressed) {
                                change.consume()
                                updateVal(change.position.x)
                            } else {
                                break
                            }
                        }
                        currentOnValueChangeFinished()
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            val usableWidth = (widthPx - thumbWidthPx).coerceAtLeast(1f)
            val thumbXPx = (thumbWidthPx / 2f) + (fraction * usableWidth)

            if (widthPx > 0f) {
                // Left active bar: 0 .. (thumbXPx - gapHalfWidthPx)
                val leftBarWidth = (thumbXPx - gapHalfWidthPx).coerceIn(0f, widthPx)
                if (leftBarWidth > 0f) {
                    val leftBarWidthDp = with(density) { leftBarWidth.toDp() }
                    Box(
                        modifier = Modifier
                            .width(leftBarWidthDp)
                            .height(trackHeight)
                            .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 3.dp, bottomEnd = 3.dp))
                            .background(activeColor)
                    )
                }

                // Right inactive bar: (thumbXPx + gapHalfWidthPx) .. widthPx
                val rightBarStart = (thumbXPx + gapHalfWidthPx).coerceIn(0f, widthPx)
                val rightBarWidth = (widthPx - rightBarStart).coerceIn(0f, widthPx)
                if (rightBarWidth > 0f) {
                    val rightBarWidthDp = with(density) { rightBarWidth.toDp() }
                    val rightBarOffsetDp = with(density) { rightBarStart.toDp() }
                    Box(
                        modifier = Modifier
                            .offset(x = rightBarOffsetDp)
                            .width(rightBarWidthDp)
                            .height(trackHeight)
                            .clip(RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 8.dp, bottomEnd = 8.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f))
                    )
                }

                // Center vertical pill thumb
                Box(
                    modifier = Modifier
                        .offset { IntOffset((thumbXPx - thumbWidthPx / 2f).roundToInt(), 0) }
                        .size(width = thumbWidth, height = thumbHeight)
                        .clip(RoundedCornerShape(3.dp))
                        .background(activeColor)
                )
            }
        }

        if (!hints.isNullOrEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                hints.forEach { hint ->
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )
                }
            }
        }
    }
}

/**
 * Color Engine Card: Palette Style selection chips and Material 3 Color Spec toggle.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ColorEngineCard(
    colorStyle: String,
    colorSpec: String,
    onStyleSelected: (String) -> Unit,
    onSpecSelected: (String) -> Unit
) {
    val styles = listOf("System") + PaletteStyle.entries.map { it.name }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.pref_color_generation_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Palette Style
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.pref_color_style_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(styles) { styleName ->
                        val isSelected = styleName.equals(colorStyle, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { onStyleSelected(styleName) },
                            label = { Text(styleName, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            shape = CircleShape,
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            // Color Specification
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.pref_color_spec_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                ExpressiveConnectedButtonGroup(
                    options = MaterialKolorColorSpecOptions,
                    selectedOption = colorSpec,
                    onOptionSelected = onSpecSelected,
                    fillMaxWidth = true,
                    labelProvider = { spec ->
                        Text(
                            text = if (spec == "SPEC_2025") {
                                stringResource(R.string.color_spec_2025_label)
                            } else {
                                stringResource(R.string.color_spec_2021_label)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (spec == colorSpec) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                )
            }
        }
    }
}

private fun Int.toHsv(): FloatArray =
    FloatArray(3).also { hsv -> AndroidColor.colorToHSV(this, hsv) }

private fun hsvToSeedArgb(hue: Float, saturation: Float, value: Float): Int {
    val safeHue = if (hue >= 360f) 0f else hue.coerceIn(0f, 360f)
    return AndroidColor.HSVToColor(floatArrayOf(safeHue, saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f)))
}

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
