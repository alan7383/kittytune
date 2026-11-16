package com.alananasss.kittytune.ui.design

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.glass.LocalGlassBackdrop
import com.alananasss.kittytune.ui.glass.LocalGlassSettings
import com.alananasss.kittytune.ui.glass.components.GlassButton
import com.alananasss.kittytune.ui.glass.components.GlassButtonStyle
import com.alananasss.kittytune.ui.glass.components.GlassToggle
import com.alananasss.kittytune.ui.glass.rememberGlassColors
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import androidx.compose.material3.Button as M3Button
import androidx.compose.material3.Icon as M3Icon
import androidx.compose.material3.Switch as M3Switch
import top.yukonga.miuix.kmp.basic.Button as MxButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MxButtonDefaults
import top.yukonga.miuix.kmp.basic.Card as MxCard
import top.yukonga.miuix.kmp.basic.Switch as MxSwitch
import top.yukonga.miuix.kmp.basic.Text as MxText

/**
 * Settings → Appearance → Interface style: Material 3, Liquid Glass or HyperOS (MIUIX), each shown
 * as a live miniature, plus the way into each style's own customisation screen.
 */
@Composable
fun DesignStyleScreen(
    onBackClick: () -> Unit,
    onOpenLiquidGlass: () -> Unit,
    onOpenMiuix: () -> Unit,
) {
    val context = LocalContext.current
    val current = currentDesignStyle()

    SettingsScaffold(title = stringResource(R.string.design_style_title), onBackClick = onBackClick) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 220.dp)) {
            DesignStyle.entries.forEach { style ->
                item(key = style.name) {
                    StyleCard(
                        style = style,
                        selected = style == current,
                        onSelect = { setDesignStyle(context, style) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            item {
                SettingsGroup(
                    title = stringResource(R.string.design_customize),
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.glass_settings_title),
                                subtitle = stringResource(R.string.glass_settings_entry_desc),
                                icon = Icons.Rounded.WaterDrop,
                                onClick = onOpenLiquidGlass,
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.miuix_settings_title),
                                subtitle = stringResource(R.string.miuix_settings_entry_desc),
                                icon = Icons.Rounded.Tune,
                                onClick = onOpenMiuix,
                            )
                        },
                    )
                )
            }
        }
    }
}

@Composable
private fun StyleCard(style: DesignStyle, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceContainerHigh)
            .border(BorderStroke(if (selected) 2.dp else 0.dp, if (selected) scheme.primary else Color.Transparent), shape)
            .clickable(onClick = onSelect)
            .padding(14.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(20.dp))
        ) {
            when (style) {
                DesignStyle.MATERIAL3 -> MaterialMiniature()
                DesignStyle.LIQUID_GLASS -> GlassMiniature()
                DesignStyle.MIUIX -> MiuixMiniature()
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(style.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(style.summary), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            if (selected) {
                M3Icon(Icons.Rounded.CheckCircle, null, tint = scheme.primary, modifier = Modifier.size(28.dp))
            } else {
                Box(Modifier.size(26.dp).border(2.dp, scheme.outline, CircleShape))
            }
        }
    }
}

@Composable
private fun MaterialMiniature() {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(scheme.surfaceContainerLow).padding(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surfaceContainerHighest).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Material You", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                var on by remember { mutableStateOf(true) }
                M3Switch(checked = on, onCheckedChange = { on = it })
            }
            M3Button(onClick = {}) { Text(stringResource(R.string.action_play)) }
        }
    }
}

@Composable
private fun GlassMiniature() {
    val scheme = MaterialTheme.colorScheme
    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary, scheme.secondaryContainer)))
        )
        CompositionLocalProvider(
            LocalGlassSettings provides LocalGlassSettings.current.copy(enabled = true),
            LocalGlassBackdrop provides backdrop,
        ) {
            val colors = rememberGlassColors()
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                var on by remember { mutableStateOf(true) }
                GlassToggle(checked = on, onCheckedChange = { on = it })
                GlassButton(onClick = {}, style = GlassButtonStyle.Prominent, tint = colors.accent, contentColor = colors.onAccent) {
                    Text(stringResource(R.string.action_play))
                }
            }
        }
    }
}

@Composable
private fun MiuixMiniature() {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val controller = remember(dark) { ThemeController(if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light) }
    MiuixTheme(controller = controller) {
        val mx = MiuixTheme.colorScheme
        Box(Modifier.fillMaxSize().background(mx.surface).padding(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MxCard {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        M3Icon(Icons.Rounded.PhoneAndroid, null, tint = mx.primary)
                        Spacer(Modifier.size(12.dp))
                        MxText("HyperOS", Modifier.weight(1f))
                        var on by remember { mutableStateOf(true) }
                        MxSwitch(checked = on, onCheckedChange = { on = it })
                    }
                }
                MxButton(onClick = {}, colors = MxButtonDefaults.buttonColorsPrimary(), modifier = Modifier.fillMaxWidth()) {
                    MxText(stringResource(R.string.action_play), color = mx.onPrimary)
                }
            }
        }
    }
}
