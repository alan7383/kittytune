package com.alananasss.kittytune.ui.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.common.rememberWindowSizeInfo
import com.alananasss.kittytune.ui.glass.components.GlassButton
import com.alananasss.kittytune.ui.glass.components.GlassButtonStyle
import com.alananasss.kittytune.ui.glass.components.GlassIconButton
import com.alananasss.kittytune.ui.glass.components.GlassNavItem
import com.alananasss.kittytune.ui.glass.components.GlassNavigationTabs
import com.alananasss.kittytune.ui.glass.components.GlassSlider
import com.alananasss.kittytune.ui.glass.components.GlassToggle
import com.alananasss.kittytune.ui.design.FilterChip
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.RoundedRectangle
import kotlin.math.cos
import kotlin.math.sin

/**
 * Settings → Appearance → Liquid Glass.
 *
 * A live preview on top (glass over a moving, theme-coloured scene, so refraction is visible even
 * when the rest of the app is calm), the master switch, the material options, and one switch per
 * component so each part of the interface can be glass or Material.
 */
@Composable
fun LiquidGlassSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val settings = LocalGlassSettings.current
    fun update(transform: (GlassSettings) -> GlassSettings) = GlassPreferences.update(context, transform)
    val windowSizeInfo = rememberWindowSizeInfo()

    SettingsScaffold(title = stringResource(R.string.glass_settings_title), onBackClick = onBackClick) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 220.dp)) {
            item {
                GlassPreview(
                    settings = settings,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .height(if (windowSizeInfo.isTablet) 300.dp else 260.dp)
                )
            }

            item {
                SettingsGroup(
                    items = buildList {
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.glass_enable_title),
                                subtitle = stringResource(R.string.glass_enable_desc),
                                icon = Icons.Rounded.WaterDrop,
                                hasSwitch = true,
                                switchState = settings.enabled,
                                onSwitchChange = { on -> com.alananasss.kittytune.ui.design.setDesignStyle(context, if (on) com.alananasss.kittytune.ui.design.DesignStyle.LIQUID_GLASS else com.alananasss.kittytune.ui.design.DesignStyle.MATERIAL3) },
                                highlightKey = "pref_liquid_glass"
                            )
                        }
                        if (!isGlassLensSupported) {
                            add { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.glass_limited_title),
                                    subtitle = stringResource(
                                        if (isGlassBlurSupported) R.string.glass_limited_no_lens else R.string.glass_limited_no_blur
                                    ),
                                    icon = Icons.Rounded.Info,
                                )
                            }
                        }
                    }
                )
            }

            item {
                SettingsGroup(
                    title = stringResource(R.string.glass_group_look),
                    items = listOf(
                        { shape ->
                            OptionRow(
                                shape = shape,
                                title = stringResource(R.string.glass_material_title),
                                options = GlassMaterial.entries,
                                selected = settings.material,
                                label = { stringResource(it.title) },
                                onSelect = { m -> update { it.copy(material = m) } }
                            )
                        },
                        { shape ->
                            OptionRow(
                                shape = shape,
                                title = stringResource(R.string.glass_refraction_title),
                                options = GlassRefraction.entries,
                                selected = settings.refraction,
                                label = { stringResource(it.title) },
                                onSelect = { r -> update { it.copy(refraction = r) } }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.glass_chromatic_title),
                                subtitle = stringResource(R.string.glass_chromatic_desc),
                                icon = Icons.Rounded.AutoAwesome,
                                hasSwitch = true,
                                switchState = settings.chromaticAberration,
                                onSwitchChange = { on -> update { it.copy(chromaticAberration = on) } }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.glass_specular_title),
                                subtitle = stringResource(R.string.glass_specular_desc),
                                icon = Icons.Rounded.BlurOn,
                                hasSwitch = true,
                                switchState = settings.specularHighlights,
                                onSwitchChange = { on -> update { it.copy(specularHighlights = on) } }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.glass_motion_title),
                                subtitle = stringResource(R.string.glass_motion_desc),
                                icon = Icons.Rounded.Gesture,
                                hasSwitch = true,
                                switchState = settings.interactiveMotion,
                                onSwitchChange = { on -> update { it.copy(interactiveMotion = on) } }
                            )
                        },
                    )
                )
            }

            GlassComponentGroup.entries.forEach { group ->
                item(key = group.name) {
                    val components = GlassComponent.entries.filter { it.group == group }
                    SettingsGroup(
                        title = stringResource(group.title),
                        items = components.map { component ->
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(component.title),
                                    subtitle = stringResource(component.summary),
                                    hasSwitch = true,
                                    switchState = component in settings.components,
                                    onSwitchChange = { on -> GlassPreferences.setComponent(context, component, on) },
                                    highlightKey = "glass_${component.key}"
                                )
                            }
                        }
                    )
                }
            }

            item {
                SettingsGroup(
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.glass_reset_title),
                                subtitle = stringResource(R.string.glass_reset_desc),
                                icon = Icons.Rounded.RestartAlt,
                                onClick = { GlassPreferences.reset(context) }
                            )
                        }
                    )
                )
            }
        }
    }
}

@Composable
private fun <T> OptionRow(
    shape: androidx.compose.ui.graphics.Shape,
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    androidx.compose.material3.Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(
            alpha = if (glassEnabled(GlassComponent.CARDS)) 0.3f else 1f
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    FilterChip(
                        selected = option == selected,
                        onClick = { onSelect(option) },
                        label = { Text(label(option)) }
                    )
                }
            }
        }
    }
}

/**
 * A small stage showing the glass as configured: a navigation bar, a prominent button, a round
 * button, a switch and a slider over a slowly drifting scene in the theme colours.
 */
@Composable
private fun GlassPreview(settings: GlassSettings, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val stageShape = RoundedRectangle(32.dp)
    val backdrop = rememberLayerBackdrop()
    val transition = rememberInfiniteTransition(label = "glassPreview")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )

    var tab by remember { mutableStateOf("home") }
    var toggle by remember { mutableStateOf(true) }
    var slider by remember { mutableFloatStateOf(0.6f) }

    val previewSettings = settings.copy(enabled = true)

    Box(modifier.clip(stageShape)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            drawRect(
                Brush.linearGradient(
                    listOf(scheme.primaryContainer, scheme.tertiaryContainer, scheme.secondaryContainer),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
            )
            val blobs = listOf(scheme.primary, scheme.tertiary, scheme.secondary, scheme.error.copy(alpha = 0.7f))
            blobs.forEachIndexed { i, color ->
                val a = phase + i * 1.7f
                val center = Offset(
                    size.width * (0.5f + 0.38f * cos(a)),
                    size.height * (0.5f + 0.34f * sin(a * 1.3f))
                )
                drawCircle(color, radius = size.minDimension * (0.22f + 0.05f * i), center = center)
            }
            // Fine stripes: refraction bends them at the rim, which is how lens strength reads.
            val stripe = 18.dp.toPx()
            var y = 0f
            while (y < size.height) {
                drawRect(Color.White.copy(alpha = 0.10f), topLeft = Offset(0f, y), size = androidx.compose.ui.geometry.Size(size.width, stripe / 3f))
                y += stripe
            }
        }

        CompositionLocalProvider(
            LocalGlassSettings provides previewSettings,
            LocalGlassBackdrop provides backdrop,
        ) {
            val colors = rememberGlassColors()
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassButton(
                        onClick = {},
                        style = GlassButtonStyle.Prominent,
                        tint = colors.accent,
                        contentColor = colors.onAccent,
                        minHeight = 44.dp,
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
                        Text(stringResource(R.string.action_play))
                    }
                    GlassIconButton(onClick = {}, style = GlassButtonStyle.Regular, contentColor = colors.content, containerSize = 44.dp) {
                        Icon(Icons.Rounded.Search, null)
                    }
                    Spacer(Modifier.weight(1f))
                    GlassToggle(checked = toggle, onCheckedChange = { toggle = it })
                }
                GlassSlider(value = slider, onValueChange = { slider = it }, modifier = Modifier.padding(horizontal = 4.dp))
                GlassNavigationTabs(
                    items = listOf(
                        GlassNavItem("home", stringResource(R.string.glass_preview_home), Icons.Rounded.Home),
                        GlassNavItem("search", stringResource(R.string.glass_preview_search), Icons.Rounded.Search),
                        GlassNavItem("library", stringResource(R.string.glass_preview_library), Icons.Rounded.LibraryMusic),
                    ),
                    selectedKey = tab,
                    onSelect = { tab = it.key },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
