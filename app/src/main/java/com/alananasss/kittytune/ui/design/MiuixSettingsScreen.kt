package com.alananasss.kittytune.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Interests
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Button as MxButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MxButtonDefaults
import top.yukonga.miuix.kmp.basic.Card as MxCard
import top.yukonga.miuix.kmp.basic.Slider as MxSlider
import top.yukonga.miuix.kmp.basic.TabRow as MxTabRow
import top.yukonga.miuix.kmp.basic.Text as MxText
import top.yukonga.miuix.kmp.preference.SwitchPreference as MxSwitchPreference

/**
 * Settings → Appearance → Interface style → HyperOS (MIUIX): palette source, type scale, motion,
 * icons, and one switch per part of the interface.
 */
@Composable
fun MiuixSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val settings = LocalMiuixSettings.current
    fun update(transform: (MiuixSettings) -> MiuixSettings) = MiuixPreferences.update(context, transform)

    SettingsScaffold(title = stringResource(R.string.miuix_settings_title), onBackClick = onBackClick) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 220.dp)) {
            if (settings.enabled) {
                item { MiuixPreview(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) }
            }
            item {
                SettingsGroup(
                    items = listOf { shape ->
                        SettingsItem(
                            shape = shape,
                            title = stringResource(R.string.miuix_enable_title),
                            subtitle = stringResource(R.string.miuix_enable_desc),
                            icon = Icons.Rounded.PhoneAndroid,
                            hasSwitch = true,
                            switchState = settings.enabled,
                            onSwitchChange = { on -> setDesignStyle(context, if (on) DesignStyle.MIUIX else DesignStyle.MATERIAL3) },
                            highlightKey = "pref_miuix",
                        )
                    }
                )
            }
            item {
                SettingsGroup(
                    title = stringResource(R.string.glass_group_look),
                    items = listOf(
                        { shape ->
                            ColorSourceRow(shape, settings.colorSource) { src -> update { it.copy(colorSource = src) } }
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.miuix_typography_title),
                                subtitle = stringResource(R.string.miuix_typography_desc),
                                icon = Icons.Rounded.TextFields,
                                hasSwitch = true,
                                switchState = settings.hyperOsTypography,
                                onSwitchChange = { on -> update { it.copy(hyperOsTypography = on) } },
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.miuix_true_black_title),
                                subtitle = stringResource(R.string.miuix_true_black_desc),
                                icon = Icons.Rounded.Contrast,
                                hasSwitch = true,
                                switchState = settings.trueBlack,
                                onSwitchChange = { on -> update { it.copy(trueBlack = on) } },
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.miuix_icons_title),
                                subtitle = stringResource(R.string.miuix_icons_desc),
                                icon = Icons.Rounded.Interests,
                                hasSwitch = true,
                                switchState = settings.miuixIcons,
                                onSwitchChange = { on -> update { it.copy(miuixIcons = on) } },
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.miuix_bounce_title),
                                subtitle = stringResource(R.string.miuix_bounce_desc),
                                icon = Icons.Rounded.Animation,
                                hasSwitch = true,
                                switchState = settings.bounceOverscroll,
                                onSwitchChange = { on -> update { it.copy(bounceOverscroll = on) } },
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.miuix_press_title),
                                subtitle = stringResource(R.string.miuix_press_desc),
                                icon = Icons.Rounded.TouchApp,
                                hasSwitch = true,
                                switchState = settings.pressFeedback,
                                onSwitchChange = { on -> update { it.copy(pressFeedback = on) } },
                            )
                        },
                    )
                )
            }
            MiuixComponentGroup.entries.forEach { group ->
                item(key = "miuix_" + group.name) {
                    SettingsGroup(
                        title = stringResource(group.title),
                        items = MiuixComponent.entries.filter { it.group == group }.map { component ->
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(component.title),
                                    subtitle = stringResource(component.summary),
                                    hasSwitch = true,
                                    switchState = component in settings.components,
                                    onSwitchChange = { on -> MiuixPreferences.setComponent(context, component, on) },
                                    highlightKey = "miuix_${component.key}",
                                )
                            }
                        }
                    )
                }
            }
            item {
                SettingsGroup(
                    items = listOf { shape ->
                        SettingsItem(
                            shape = shape,
                            title = stringResource(R.string.miuix_reset_title),
                            subtitle = stringResource(R.string.glass_reset_desc),
                            icon = Icons.Rounded.RestartAlt,
                            onClick = { MiuixPreferences.reset(context) },
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun ColorSourceRow(shape: androidx.compose.ui.graphics.Shape, selected: MiuixColorSource, onSelect: (MiuixColorSource) -> Unit) {
    val options = MiuixColorSource.entries
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(stringResource(R.string.miuix_colors_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        if (LocalMiuixSettings.current.enabled) {
            MxTabRow(
                tabs = options.map { stringResource(it.title) },
                selectedTabIndex = options.indexOf(selected),
                onTabSelected = { onSelect(options[it]) },
            )
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(stringResource(option.title)) })
                }
            }
        }
    }
}

/** A few HyperOS components rendered with the live palette, so changes are seen at once. */
@Composable
private fun MiuixPreview(modifier: Modifier = Modifier) {
    val mx = MiuixTheme.colorScheme
    var on by remember { mutableStateOf(true) }
    var value by remember { mutableFloatStateOf(0.6f) }
    MxCard(modifier.fillMaxWidth()) {
        MxSwitchPreference(
            checked = on,
            onCheckedChange = { on = it },
            title = stringResource(R.string.miuix_preview_title),
            summary = stringResource(R.string.miuix_preview_summary),
        )
        MxSlider(value = value, onValueChange = { value = it }, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(12.dp))
        androidx.compose.foundation.layout.Row(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MxButton(onClick = {}, modifier = Modifier.weight(1f)) { MxText(stringResource(R.string.btn_cancel)) }
            MxButton(onClick = {}, modifier = Modifier.weight(1f), colors = MxButtonDefaults.buttonColorsPrimary()) {
                MxText(stringResource(R.string.action_play), color = mx.onPrimary)
            }
        }
    }
}
