package com.alananasss.kittytune.ui.profile

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.AppLanguage
import com.alananasss.kittytune.data.local.AppThemeMode
import com.alananasss.kittytune.data.local.PlayerBackgroundStyle
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.StartDestination
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsGroupTitle
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.common.getSettingsShape

@Composable
fun AppearanceSettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    val isSystemDark = isSystemInDarkTheme()

    var startDestination by remember { mutableStateOf(prefs.getStartDestination()) }
    var dynamicTheme by remember { mutableStateOf(prefs.getDynamicTheme()) }
    var themeMode by remember { mutableStateOf(prefs.getThemeMode()) }
    var pureBlack by remember { mutableStateOf(prefs.getPureBlack()) }
    var playerStyle by remember { mutableStateOf(prefs.getPlayerStyle()) }
    var appLanguage by remember { mutableStateOf(prefs.getAppLanguage()) }
    var achievementPopupsEnabled by remember { mutableStateOf(prefs.getAchievementPopupsEnabled()) }
    var autoUpdate by remember { mutableStateOf(prefs.getAutoUpdateEnabled()) }
    var customFontEnabled by remember { mutableStateOf(prefs.getCustomFontEnabled()) }

    var showThemeDialog by remember { mutableStateOf(false) }
    var showPlayerStyleDialog by remember { mutableStateOf(false) }
    var showStartDestDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showFontConfigDialog by remember { mutableStateOf(false) }

    val isPureBlackVisible = themeMode == AppThemeMode.DARK || (themeMode == AppThemeMode.SYSTEM && isSystemDark)

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringResource(R.string.pref_theme_mode)) },
            text = {
                Column {
                    ThemeRadioButton(stringResource(R.string.theme_system), AppThemeMode.SYSTEM, themeMode) { themeMode = it; prefs.setThemeMode(it); showThemeDialog = false }
                    ThemeRadioButton(stringResource(R.string.theme_light), AppThemeMode.LIGHT, themeMode) { themeMode = it; prefs.setThemeMode(it); showThemeDialog = false }
                    ThemeRadioButton(stringResource(R.string.theme_dark), AppThemeMode.DARK, themeMode) { themeMode = it; prefs.setThemeMode(it); showThemeDialog = false }
                }
            },
            confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    if (showStartDestDialog) {
        AlertDialog(
            onDismissRequest = { showStartDestDialog = false },
            title = { Text(stringResource(R.string.pref_start_screen)) },
            text = {
                Column {
                    StartDestRadioButton(stringResource(R.string.nav_home), StartDestination.HOME, startDestination) { startDestination = it; prefs.setStartDestination(it); showStartDestDialog = false }
                    StartDestRadioButton(stringResource(R.string.nav_library), StartDestination.LIBRARY, startDestination) { startDestination = it; prefs.setStartDestination(it); showStartDestDialog = false }
                }
            },
            confirmButton = { TextButton(onClick = { showStartDestDialog = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    if (showPlayerStyleDialog) {
        AlertDialog(
            onDismissRequest = { showPlayerStyleDialog = false },
            title = { Text(stringResource(R.string.pref_player_style)) },
            text = {
                Column {
                    PlayerStyleRadioButton(stringResource(R.string.style_theme), PlayerBackgroundStyle.THEME, playerStyle) { playerStyle = it; prefs.setPlayerStyle(it); showPlayerStyleDialog = false }
                    PlayerStyleRadioButton(stringResource(R.string.style_gradient), PlayerBackgroundStyle.GRADIENT, playerStyle) { playerStyle = it; prefs.setPlayerStyle(it); showPlayerStyleDialog = false }
                    PlayerStyleRadioButton(stringResource(R.string.style_blur), PlayerBackgroundStyle.BLUR, playerStyle) { playerStyle = it; prefs.setPlayerStyle(it); showPlayerStyleDialog = false }
                }
            },
            confirmButton = { TextButton(onClick = { showPlayerStyleDialog = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.pref_language)) },
            text = {
                Column {
                    LanguageRadioButton(stringResource(R.string.theme_system), AppLanguage.SYSTEM, appLanguage) { prefs.setAppLanguage(it); restartApp(context) }
                    LanguageRadioButton(stringResource(R.string.lang_french), AppLanguage.FRENCH, appLanguage) { prefs.setAppLanguage(it); restartApp(context) }
                    LanguageRadioButton(stringResource(R.string.lang_english), AppLanguage.ENGLISH, appLanguage) { prefs.setAppLanguage(it); restartApp(context) }
                    LanguageRadioButton(stringResource(R.string.lang_hungarian), AppLanguage.HUNGARIAN, appLanguage) { prefs.setAppLanguage(it); restartApp(context) }
                }
            },
            confirmButton = { TextButton(onClick = { showLanguageDialog = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    if (showFontConfigDialog) {
        var wght by remember { mutableFloatStateOf(prefs.getFontWght().toFloat()) }
        var wdth by remember { mutableFloatStateOf(prefs.getFontWdth()) }
        var slnt by remember { mutableFloatStateOf(prefs.getFontSlnt()) }
        var rond by remember { mutableFloatStateOf(prefs.getFontRond()) }
        var grad by remember { mutableFloatStateOf(prefs.getFontGrad()) }
        var opsz by remember { mutableFloatStateOf(prefs.getFontOpsz()) }

        fun applyPreset(pWght: Float, pWdth: Float, pSlnt: Float, pRond: Float, pGrad: Float, pOpsz: Float) {
            wght = pWght; prefs.setFontWght(pWght.toInt())
            wdth = pWdth; prefs.setFontWdth(pWdth)
            slnt = pSlnt; prefs.setFontSlnt(pSlnt)
            rond = pRond; prefs.setFontRond(pRond)
            grad = pGrad; prefs.setFontGrad(pGrad)
            opsz = pOpsz; prefs.setFontOpsz(pOpsz)
        }

        AlertDialog(
            onDismissRequest = { showFontConfigDialog = false },
            title = { Text(stringResource(R.string.dialog_font_settings_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            AssistChip(
                                onClick = { applyPreset(400f, 100f, 0f, 0f, 0f, 14f) },
                                label = { Text(stringResource(R.string.font_preset_default)) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = { applyPreset(600f, 100f, 0f, 100f, 0f, 14f) },
                                label = { Text(stringResource(R.string.font_preset_rounded)) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = { applyPreset(250f, 105f, 0f, 0f, 0f, 14f) },
                                label = { Text(stringResource(R.string.font_preset_elegant)) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = { applyPreset(900f, 110f, 0f, 50f, 0f, 14f) },
                                label = { Text(stringResource(R.string.font_preset_chunky)) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = { applyPreset(500f, 140f, 0f, 0f, 0f, 14f) },
                                label = { Text(stringResource(R.string.font_preset_wide)) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = { applyPreset(750f, 95f, -8f, 100f, 0f, 14f) },
                                label = { Text(stringResource(R.string.font_preset_playful)) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = { applyPreset(600f, 75f, 0f, 0f, 0f, 14f) },
                                label = { Text(stringResource(R.string.font_preset_compact)) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = { applyPreset(500f, 100f, -10f, 0f, 0f, 14f) },
                                label = { Text(stringResource(R.string.font_preset_slanted)) }
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Column {
                        Text(stringResource(R.string.dialog_font_weight, wght.toInt()), style = MaterialTheme.typography.labelLarge)
                        Slider(value = wght, onValueChange = { wght = it; prefs.setFontWght(it.toInt()) }, valueRange = 100f..1000f)
                    }
                    Column {
                        Text(stringResource(R.string.dialog_font_width, wdth.toInt()), style = MaterialTheme.typography.labelLarge)
                        Slider(value = wdth, onValueChange = { wdth = it; prefs.setFontWdth(it) }, valueRange = 25f..151f)
                    }
                    Column {
                        Text(stringResource(R.string.dialog_font_slant, slnt.toInt()), style = MaterialTheme.typography.labelLarge)
                        Slider(value = slnt, onValueChange = { slnt = it; prefs.setFontSlnt(it) }, valueRange = -10f..0f)
                    }
                    Column {
                        Text(stringResource(R.string.dialog_font_roundness, rond.toInt()), style = MaterialTheme.typography.labelLarge)
                        Slider(value = rond, onValueChange = { rond = it; prefs.setFontRond(it) }, valueRange = 0f..100f)
                    }
                    Column {
                        Text(stringResource(R.string.dialog_font_grade, grad.toInt()), style = MaterialTheme.typography.labelLarge)
                        Slider(value = grad, onValueChange = { grad = it; prefs.setFontGrad(it) }, valueRange = -200f..150f)
                    }
                    Column {
                        Text(stringResource(R.string.dialog_font_optical_size, opsz.toInt()), style = MaterialTheme.typography.labelLarge)
                        Slider(value = opsz, onValueChange = { opsz = it; prefs.setFontOpsz(it) }, valueRange = 8f..144f)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFontConfigDialog = false }) { Text(stringResource(R.string.btn_close)) }
            },
            dismissButton = {
                TextButton(onClick = { applyPreset(400f, 100f, 0f, 0f, 0f, 14f) }) {
                    Text(stringResource(R.string.btn_reset))
                }
            }
        )
    }

    SettingsScaffold(
        title = stringResource(R.string.pref_appearance_title),
        onBackClick = onBackClick
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            item {
                SettingsGroup(
                    title = stringResource(R.string.settings_cat_general),
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_start_screen),
                                subtitle = if (startDestination == StartDestination.HOME) stringResource(R.string.nav_home) else stringResource(R.string.nav_library),
                                icon = Icons.Rounded.Home,
                                onClick = { showStartDestDialog = true }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_language),
                                subtitle = stringResource(R.string.pref_language_sub),
                                icon = Icons.Rounded.Language,
                                onClick = { showLanguageDialog = true }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_auto_update),
                                subtitle = stringResource(R.string.pref_auto_update_sub),
                                icon = Icons.Rounded.SystemUpdate,
                                hasSwitch = true,
                                switchState = autoUpdate,
                                onSwitchChange = {
                                    autoUpdate = it
                                    prefs.setAutoUpdateEnabled(it)
                                }
                            )
                        }
                    )
                )
            }

            item {
                ->
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SettingsGroupTitle(stringResource(R.string.settings_cat_typography))

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {

                        val totalVisibleItems = if (customFontEnabled) 2 else 1

                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, 0),
                            title = stringResource(R.string.pref_font_custom_title),
                            subtitle = stringResource(R.string.pref_font_custom_subtitle),
                            icon = Icons.Rounded.FontDownload,
                            hasSwitch = true,
                            switchState = customFontEnabled,
                            onSwitchChange = {
                                customFontEnabled = it
                                prefs.setCustomFontEnabled(it)
                            }
                        )

                        androidx.compose.animation.AnimatedVisibility(
                            visible = customFontEnabled,
                            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                        ) {
                            SettingsItem(
                                shape = getSettingsShape(totalVisibleItems, 1),
                                title = stringResource(R.string.pref_font_variations_title),
                                subtitle = stringResource(R.string.pref_font_variations_subtitle),
                                icon = Icons.Rounded.Tune,
                                onClick = { showFontConfigDialog = true }
                            )
                        }
                    }
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SettingsGroupTitle(stringResource(R.string.settings_cat_appearance))

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {

                        val totalVisibleItems = if (isPureBlackVisible) 3 else 2

                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, 0),
                            title = stringResource(R.string.pref_theme_dynamic),
                            subtitle = stringResource(R.string.pref_theme_dynamic_sub),
                            icon = Icons.Rounded.ColorLens,
                            hasSwitch = true,
                            switchState = dynamicTheme,
                            onSwitchChange = { dynamicTheme = it; prefs.setDynamicTheme(it) }
                        )

                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, 1),
                            title = stringResource(R.string.pref_theme_mode),
                            subtitle = when(themeMode) {
                                AppThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                                AppThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                AppThemeMode.DARK -> stringResource(R.string.theme_dark)
                            },
                            icon = Icons.Rounded.DarkMode,
                            onClick = { showThemeDialog = true }
                        )

                        AnimatedVisibility(
                            visible = isPureBlackVisible,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            SettingsItem(
                                shape = getSettingsShape(3, 2),
                                title = stringResource(R.string.pref_theme_pure_black),
                                subtitle = stringResource(R.string.pref_theme_pure_black_sub),
                                icon = Icons.Rounded.Contrast,
                                hasSwitch = true,
                                switchState = pureBlack,
                                onSwitchChange = { pureBlack = it; prefs.setPureBlack(it) }
                            )
                        }
                    }
                }
            }

            item {
                SettingsGroup(
                    title = stringResource(R.string.settings_cat_playback),
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_player_style),
                                subtitle = when(playerStyle) {
                                    PlayerBackgroundStyle.THEME -> stringResource(R.string.style_theme)
                                    PlayerBackgroundStyle.GRADIENT -> stringResource(R.string.style_gradient)
                                    PlayerBackgroundStyle.BLUR -> stringResource(R.string.style_blur)
                                },
                                icon = Icons.Rounded.Wallpaper,
                                onClick = { showPlayerStyleDialog = true }
                            )
                        }
                    )
                )
            }

            item {
                SettingsGroup(
                    title = stringResource(R.string.settings_cat_notifications),
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_achievement_popups),
                                subtitle = stringResource(R.string.pref_achievement_popups_sub),
                                icon = Icons.Rounded.EmojiEvents,
                                hasSwitch = true,
                                switchState = achievementPopupsEnabled,
                                onSwitchChange = {
                                    achievementPopupsEnabled = it
                                    prefs.setAchievementPopupsEnabled(it)
                                }
                            )
                        }
                    )
                )
            }
        }
    }
}

fun restartApp(context: Context) {
    if (context is Activity) {
        context.recreate()
    } else {
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        val componentName = intent?.component
        val mainIntent = Intent.makeRestartActivityTask(componentName)
        context.startActivity(mainIntent)
        Runtime.getRuntime().exit(0)
    }
}

@Composable
fun ThemeRadioButton(text: String, mode: AppThemeMode, selected: AppThemeMode, onSelect: (AppThemeMode) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onSelect(mode) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = (mode == selected), onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
fun PlayerStyleRadioButton(text: String, style: PlayerBackgroundStyle, selected: PlayerBackgroundStyle, onSelect: (PlayerBackgroundStyle) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onSelect(style) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = (style == selected), onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
fun StartDestRadioButton(text: String, dest: StartDestination, selected: StartDestination, onSelect: (StartDestination) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onSelect(dest) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = (dest == selected), onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
fun LanguageRadioButton(text: String, lang: AppLanguage, selected: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onSelect(lang) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = (lang == selected), onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}