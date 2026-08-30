package com.alananasss.kittytune.ui.profile

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.AppLanguage
import com.alananasss.kittytune.data.local.AppThemeMode
import com.alananasss.kittytune.data.local.PlayerActionButtonSlot
import com.alananasss.kittytune.data.local.PlayerBackgroundStyle
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.PlayerProgressMode
import com.alananasss.kittytune.data.local.StartDestination
import com.alananasss.kittytune.data.local.TrackRemovalMethod
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsGroupTitle
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.common.getSettingsShape

@Composable
fun AppearanceSettingsScreen(
    onNavigateToColors: () -> Unit,
    onNavigateToBottomBarSettings: () -> Unit,
    onNavigateToAppIconSettings: () -> Unit,
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
    var newPlayerDesign by remember { mutableStateOf(prefs.getNewPlayerDesignEnabled()) }
    var waveformComments by remember { mutableStateOf(prefs.getWaveformCommentsEnabled()) }
    var appLanguage by remember { mutableStateOf(prefs.getAppLanguage()) }
    var achievementPopupsEnabled by remember { mutableStateOf(prefs.getAchievementPopupsEnabled()) }
    var autoUpdate by remember { mutableStateOf(prefs.getAutoUpdateEnabled()) }
    var customFontEnabled by remember { mutableStateOf(prefs.getCustomFontEnabled()) }
    var appIcon by remember { mutableStateOf(prefs.getAppIconId()) }
    var playerProgressMode by remember { mutableStateOf(prefs.getPlayerProgressMode()) }
    var trackRemovalMethod by remember { mutableStateOf(prefs.getTrackRemovalMethod()) }

    var showPlayerStyleDialog by remember { mutableStateOf(false) }
    var showStartDestDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showFontConfigDialog by remember { mutableStateOf(false) }
    var showPlayerCustomizationBottomSheet by remember { mutableStateOf(false) }
    var showTrackRemovalDialog by remember { mutableStateOf(false) }

    val isPureBlackVisible = themeMode == AppThemeMode.DARK || (themeMode == AppThemeMode.SYSTEM && isSystemDark)

    if (showPlayerCustomizationBottomSheet) {
        PlayerCustomizationBottomSheet(
            prefs = prefs,
            onDismiss = { showPlayerCustomizationBottomSheet = false },
            onUpdated = {
                playerProgressMode = prefs.getPlayerProgressMode()
                waveformComments = prefs.getWaveformCommentsEnabled()
            }
        )
    }

    if (showStartDestDialog) {
        AlertDialog(
            onDismissRequest = { showStartDestDialog = false },
            title = { Text(stringResource(R.string.pref_start_screen)) },
            text = {
                Column {
                    StartDestRadioButton(
                        stringResource(R.string.nav_home),
                        StartDestination.HOME,
                        startDestination
                    ) { startDestination = it; prefs.setStartDestination(it); showStartDestDialog = false }
                    StartDestRadioButton(
                        stringResource(R.string.nav_library),
                        StartDestination.LIBRARY,
                        startDestination
                    ) { startDestination = it; prefs.setStartDestination(it); showStartDestDialog = false }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showStartDestDialog = false
                }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    if (showPlayerStyleDialog) {
        AlertDialog(
            onDismissRequest = { showPlayerStyleDialog = false },
            title = { Text(stringResource(R.string.pref_player_style)) },
            text = {
                Column {
                    PlayerStyleRadioButton(
                        stringResource(R.string.style_theme),
                        PlayerBackgroundStyle.THEME,
                        playerStyle
                    ) { playerStyle = it; prefs.setPlayerStyle(it); showPlayerStyleDialog = false }
                    PlayerStyleRadioButton(
                        stringResource(R.string.style_gradient),
                        PlayerBackgroundStyle.GRADIENT,
                        playerStyle
                    ) { playerStyle = it; prefs.setPlayerStyle(it); showPlayerStyleDialog = false }
                    PlayerStyleRadioButton(
                        stringResource(R.string.style_blur),
                        PlayerBackgroundStyle.BLUR,
                        playerStyle
                    ) { playerStyle = it; prefs.setPlayerStyle(it); showPlayerStyleDialog = false }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showPlayerStyleDialog = false
                }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.pref_language)) },
            text = {
                Column {
                    LanguageRadioButton(
                        stringResource(R.string.theme_system),
                        AppLanguage.SYSTEM,
                        appLanguage
                    ) { prefs.setAppLanguage(it); restartApp(context) }
                    LanguageRadioButton(
                        stringResource(R.string.lang_french),
                        AppLanguage.FRENCH,
                        appLanguage
                    ) { prefs.setAppLanguage(it); restartApp(context) }
                    LanguageRadioButton(
                        stringResource(R.string.lang_english),
                        AppLanguage.ENGLISH,
                        appLanguage
                    ) { prefs.setAppLanguage(it); restartApp(context) }
                    LanguageRadioButton(
                        stringResource(R.string.lang_hungarian),
                        AppLanguage.HUNGARIAN,
                        appLanguage
                    ) { prefs.setAppLanguage(it); restartApp(context) }
                    LanguageRadioButton(
                        stringResource(R.string.lang_russian),
                        AppLanguage.RUSSIAN,
                        appLanguage
                    ) { prefs.setAppLanguage(it); restartApp(context) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showLanguageDialog = false
                }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    if (showTrackRemovalDialog) {
        AlertDialog(
            onDismissRequest = { showTrackRemovalDialog = false },
            title = { Text(stringResource(R.string.pref_track_removal_title)) },
            text = {
                Column {
                    TrackRemovalRadioButton(
                        stringResource(R.string.track_removal_swipe_and_menu),
                        TrackRemovalMethod.SWIPE_AND_MENU,
                        trackRemovalMethod
                    ) {
                        trackRemovalMethod = it
                        prefs.setTrackRemovalMethod(it)
                        showTrackRemovalDialog = false
                    }
                    TrackRemovalRadioButton(
                        stringResource(R.string.track_removal_menu_only),
                        TrackRemovalMethod.MENU_ONLY,
                        trackRemovalMethod
                    ) {
                        trackRemovalMethod = it
                        prefs.setTrackRemovalMethod(it)
                        showTrackRemovalDialog = false
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTrackRemovalDialog = false
                    },
                    shapes = ButtonDefaults.shapes()
                ) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    if (showFontConfigDialog) {
        // Font logic remains unchanged
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
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        item {
                            AssistChip(
                                onClick = { applyPreset(400f, 100f, 0f, 0f, 0f, 14f) },
                                label = { Text(stringResource(R.string.font_preset_default)) })
                        }
                        item {
                            AssistChip(
                                onClick = { applyPreset(600f, 100f, 0f, 100f, 0f, 14f) },
                                label = { Text(stringResource(R.string.font_preset_rounded)) })
                        }
                        item {
                            AssistChip(
                                onClick = { applyPreset(250f, 105f, 0f, 0f, 0f, 14f) },
                                label = { Text(stringResource(R.string.font_preset_elegant)) })
                        }
                        item {
                            AssistChip(
                                onClick = { applyPreset(900f, 110f, 0f, 50f, 0f, 14f) },
                                label = { Text(stringResource(R.string.font_preset_chunky)) })
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Column {
                        Text(
                            stringResource(R.string.dialog_font_weight, wght.toInt()),
                            style = MaterialTheme.typography.labelLarge
                        ); Slider(
                        value = wght,
                        onValueChange = { wght = it; prefs.setFontWght(it.toInt()) },
                        valueRange = 100f..1000f
                    )
                    }
                    Column {
                        Text(
                            stringResource(R.string.dialog_font_width, wdth.toInt()),
                            style = MaterialTheme.typography.labelLarge
                        ); Slider(
                        value = wdth,
                        onValueChange = { wdth = it; prefs.setFontWdth(it) },
                        valueRange = 25f..151f
                    )
                    }
                    Column {
                        Text(
                            stringResource(R.string.dialog_font_slant, slnt.toInt()),
                            style = MaterialTheme.typography.labelLarge
                        ); Slider(
                        value = slnt,
                        onValueChange = { slnt = it; prefs.setFontSlnt(it) },
                        valueRange = -10f..0f
                    )
                    }
                    Column {
                        Text(
                            stringResource(R.string.dialog_font_roundness, rond.toInt()),
                            style = MaterialTheme.typography.labelLarge
                        ); Slider(
                        value = rond,
                        onValueChange = { rond = it; prefs.setFontRond(it) },
                        valueRange = 0f..100f
                    )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showFontConfigDialog = false
                }) { Text(stringResource(R.string.btn_close)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    applyPreset(
                        400f,
                        100f,
                        0f,
                        0f,
                        0f,
                        14f
                    )
                }) { Text(stringResource(R.string.btn_reset)) }
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
            contentPadding = PaddingValues(bottom = 180.dp)
        ) {

            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SettingsGroupTitle(stringResource(R.string.settings_cat_appearance)) // "Apparence"
                    ThemeSelector(
                        currentTheme = themeMode,
                        onThemeSelected = {
                            themeMode = it
                            prefs.setThemeMode(it)
                        },
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val totalVisibleItems = if (isPureBlackVisible) 4 else 3
                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, 0),
                            title = stringResource(R.string.pref_language),
                            subtitle = stringResource(R.string.pref_language_sub),
                            trailingText = when (appLanguage) {
                                AppLanguage.SYSTEM -> stringResource(R.string.theme_system)
                                AppLanguage.FRENCH -> stringResource(R.string.lang_french)
                                AppLanguage.ENGLISH -> stringResource(R.string.lang_english)
                                AppLanguage.HUNGARIAN -> stringResource(R.string.lang_hungarian)
                                AppLanguage.RUSSIAN -> stringResource(R.string.lang_russian)
                            },
                            onClick = { showLanguageDialog = true }
                        )

                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, 1),
                            title = stringResource(R.string.pref_theme_dynamic),
                            subtitle = stringResource(R.string.pref_theme_dynamic_sub),
                            hasSwitch = true,
                            switchState = dynamicTheme,
                            onSwitchChange = { dynamicTheme = it; prefs.setDynamicTheme(it) }
                        )

                        AnimatedVisibility(
                            visible = isPureBlackVisible,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            SettingsItem(
                                shape = getSettingsShape(totalVisibleItems, 2),
                                title = stringResource(R.string.pref_theme_pure_black),
                                subtitle = stringResource(R.string.pref_theme_pure_black_sub),
                                hasSwitch = true,
                                switchState = pureBlack,
                                onSwitchChange = { pureBlack = it; prefs.setPureBlack(it) }
                            )
                        }

                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, if (isPureBlackVisible) 3 else 2),
                            title = stringResource(R.string.pref_color_palette_title),
                            subtitle = stringResource(R.string.pref_color_palette_subtitle),
                            onClick = onNavigateToColors
                        )
                    }
                }
            }

            item {
                SettingsGroup(
                    title = stringResource(R.string.settings_cat_app_icon),
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_app_icon_title),
                                subtitle = stringResource(R.string.pref_app_icon_subtitle),
                                trailingText = getAppIconDisplayName(context, appIcon),
                                onClick = onNavigateToAppIconSettings
                            )
                        }
                    )
                )
            }

            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SettingsGroupTitle(stringResource(R.string.settings_cat_typography))

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val customFontBottomRadius by animateDpAsState(
                            targetValue = if (customFontEnabled) 4.dp else 24.dp,
                            label = "CustomFontCornerAnimation"
                        )

                        SettingsItem(
                            shape = RoundedCornerShape(
                                topStart = 24.dp,
                                topEnd = 24.dp,
                                bottomStart = customFontBottomRadius,
                                bottomEnd = customFontBottomRadius
                            ),
                            title = stringResource(R.string.pref_font_custom_title),
                            subtitle = stringResource(R.string.pref_font_custom_subtitle),
                            hasSwitch = true,
                            switchState = customFontEnabled,
                            onSwitchChange = {
                                customFontEnabled = it
                                prefs.setCustomFontEnabled(it)
                            }
                        )

                        AnimatedVisibility(
                            visible = customFontEnabled,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            SettingsItem(
                                shape = RoundedCornerShape(
                                    topStart = 4.dp,
                                    topEnd = 4.dp,
                                    bottomStart = 24.dp,
                                    bottomEnd = 24.dp
                                ),
                                title = stringResource(R.string.pref_font_variations_title),
                                subtitle = stringResource(R.string.pref_font_variations_subtitle),
                                onClick = { showFontConfigDialog = true }
                            )
                        }
                    }
                }
            }

            item {
                SettingsGroup(
                    title = stringResource(R.string.settings_cat_general),
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_bottom_menu_title),
                                subtitle = stringResource(R.string.pref_bottom_menu_subtitle),
                                onClick = onNavigateToBottomBarSettings
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_start_screen),
                                subtitle = if (startDestination == StartDestination.HOME) stringResource(R.string.nav_home) else stringResource(
                                    R.string.nav_library
                                ),
                                onClick = { showStartDestDialog = true }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_auto_update),
                                subtitle = stringResource(R.string.pref_auto_update_sub),
                                hasSwitch = true,
                                switchState = autoUpdate,
                                onSwitchChange = {
                                    autoUpdate = it
                                    prefs.setAutoUpdateEnabled(it)
                                }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_achievement_popups),
                                subtitle = stringResource(R.string.pref_achievement_popups_sub),
                                hasSwitch = true,
                                switchState = achievementPopupsEnabled,
                                onSwitchChange = {
                                    achievementPopupsEnabled = it
                                    prefs.setAchievementPopupsEnabled(it)
                                }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_track_removal_title),
                                subtitle = when (trackRemovalMethod) {
                                    TrackRemovalMethod.SWIPE_AND_MENU -> stringResource(R.string.track_removal_swipe_and_menu)
                                    TrackRemovalMethod.MENU_ONLY -> stringResource(R.string.track_removal_menu_only)
                                },
                                onClick = { showTrackRemovalDialog = true }
                            )
                        }
                    )
                )
            }

            item {
                SettingsGroup(
                    title = stringResource(R.string.settings_cat_player),
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.player_style_customization_title),
                                subtitle = when (playerProgressMode) {
                                    PlayerProgressMode.SOUNDCLOUD -> stringResource(R.string.player_style_soundcloud_desc)
                                    PlayerProgressMode.HYBRID_WAVEFORM -> stringResource(R.string.player_style_hybrid_desc)
                                    PlayerProgressMode.CLASSIC_BAR -> stringResource(R.string.player_style_classic_desc)
                                },
                                icon = Icons.Rounded.Tune,
                                trailingText = stringResource(R.string.player_slot_edit),
                                onClick = { showPlayerCustomizationBottomSheet = true }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_new_player_design),
                                hasSwitch = true,
                                switchState = newPlayerDesign,
                                onSwitchChange = {
                                    newPlayerDesign = it
                                    prefs.setNewPlayerDesignEnabled(it)
                                }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_player_style),
                                subtitle = when (playerStyle) {
                                    PlayerBackgroundStyle.THEME -> stringResource(R.string.style_theme)
                                    PlayerBackgroundStyle.GRADIENT -> stringResource(R.string.style_gradient)
                                    PlayerBackgroundStyle.BLUR -> stringResource(R.string.style_blur)
                                },
                                onClick = { showPlayerStyleDialog = true }
                            )
                        }
                    )
                )
            }
        }
    }
}

@Composable
fun ThemeSelector(
    currentTheme: AppThemeMode,
    onThemeSelected: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ThemeOption(
                icon = Icons.Outlined.BrightnessAuto,
                selectedIcon = Icons.Filled.BrightnessAuto,
                label = stringResource(R.string.theme_system),
                isSelected = currentTheme == AppThemeMode.SYSTEM,
                onClick = { onThemeSelected(AppThemeMode.SYSTEM) },
                modifier = Modifier.weight(1f)
            )
            ThemeOption(
                icon = Icons.Outlined.LightMode,
                selectedIcon = Icons.Filled.LightMode,
                label = stringResource(R.string.theme_light),
                isSelected = currentTheme == AppThemeMode.LIGHT,
                onClick = { onThemeSelected(AppThemeMode.LIGHT) },
                modifier = Modifier.weight(1f)
            )
            ThemeOption(
                icon = Icons.Outlined.DarkMode,
                selectedIcon = Icons.Filled.DarkMode,
                label = stringResource(R.string.theme_dark),
                isSelected = currentTheme == AppThemeMode.DARK,
                onClick = { onThemeSelected(AppThemeMode.DARK) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThemeOption(
    icon: ImageVector,
    selectedIcon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clickable(
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            )
            .padding(vertical = 4.dp)
    ) {
        FilledTonalIconToggleButton(
            checked = isSelected,
            onCheckedChange = { onClick() },
            modifier = Modifier.size(56.dp),
            shapes = IconToggleButtonShapes(
                shape = CircleShape,
                pressedShape = RoundedCornerShape(16.dp),
                checkedShape = RoundedCornerShape(16.dp)
            ),
            colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Icon(
                imageVector = if (isSelected) selectedIcon else icon,
                contentDescription = label,
                modifier = Modifier.size(28.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PlayerStyleRadioButton(
    text: String,
    style: PlayerBackgroundStyle,
    selected: PlayerBackgroundStyle,
    onSelect: (PlayerBackgroundStyle) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable { onSelect(style) }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = (style == selected), onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
fun StartDestRadioButton(
    text: String,
    dest: StartDestination,
    selected: StartDestination,
    onSelect: (StartDestination) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable { onSelect(dest) }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = (dest == selected), onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
fun LanguageRadioButton(text: String, lang: AppLanguage, selected: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onSelect(lang) }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = (lang == selected), onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
fun TrackRemovalRadioButton(
    text: String,
    method: TrackRemovalMethod,
    selected: TrackRemovalMethod,
    onSelect: (TrackRemovalMethod) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable { onSelect(method) }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = (method == selected), onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

fun restartApp(context: Context) {
    com.alananasss.kittytune.utils.LocaleUtils.applyAppLanguage(context)
    com.alananasss.kittytune.data.network.RetrofitClient.resetClient()
    if (context is Activity) {
        val intent = Intent(context, context.javaClass)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        context.finish()
    } else {
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        val componentName = intent?.component
        val mainIntent = Intent.makeRestartActivityTask(componentName)
        context.startActivity(mainIntent)
        Runtime.getRuntime().exit(0)
    }
}

fun getSlotIcon(slot: PlayerActionButtonSlot): ImageVector {
    return when (slot) {
        PlayerActionButtonSlot.LIKE -> Icons.Rounded.Favorite
        PlayerActionButtonSlot.COMMENTS -> Icons.AutoMirrored.Rounded.Comment
        PlayerActionButtonSlot.SHARE -> Icons.Rounded.Share
        PlayerActionButtonSlot.QUEUE -> Icons.AutoMirrored.Rounded.QueueMusic
        PlayerActionButtonSlot.AUDIO_FX -> Icons.Rounded.GraphicEq
        PlayerActionButtonSlot.SHUFFLE -> Icons.Rounded.Shuffle
        PlayerActionButtonSlot.REPEAT -> Icons.Rounded.Repeat
        PlayerActionButtonSlot.LYRICS -> Icons.Rounded.Description
        PlayerActionButtonSlot.SLEEP_TIMER -> Icons.Rounded.Bedtime
        PlayerActionButtonSlot.MORE -> Icons.Rounded.MoreVert
        PlayerActionButtonSlot.NONE -> Icons.Rounded.Close
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerCustomizationBottomSheet(
    prefs: PlayerPreferences,
    onDismiss: () -> Unit,
    onUpdated: () -> Unit
) {
    var mode by remember { mutableStateOf(prefs.getPlayerProgressMode()) }
    var commentsPopup by remember { mutableStateOf(prefs.getWaveformCommentsPopupEnabled()) }
    var reactionsBar by remember { mutableStateOf(prefs.getSoundCloudReactionsBarEnabled()) }
    var parallax by remember { mutableStateOf(prefs.getSoundCloudParallaxEnabled()) }

    val isSoundCloudMode = (mode == PlayerProgressMode.SOUNDCLOUD)
    val slotCount = if (isSoundCloudMode) 5 else 4

    var slots by remember(mode) {
        mutableStateOf(List(slotCount) { i -> prefs.getSlot(mode, i) })
    }

    var selectedSlotToEdit by remember { mutableStateOf<Int?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 36.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.player_customization_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.player_customization_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = {
                        prefs.resetSoundCloudCustomization()
                        mode = PlayerProgressMode.CLASSIC_BAR
                        prefs.setPlayerProgressMode(PlayerProgressMode.CLASSIC_BAR)
                        commentsPopup = prefs.getWaveformCommentsPopupEnabled()
                        reactionsBar = prefs.getSoundCloudReactionsBarEnabled()
                        parallax = prefs.getSoundCloudParallaxEnabled()
                        slots = List(4) { i -> prefs.getSlot(PlayerProgressMode.CLASSIC_BAR, i) }
                        onUpdated()
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.btn_reset))
                }
            }

            SettingsGroupTitle(stringResource(R.string.player_style_group))

            ExpressiveConnectedButtonGroup(
                options = listOf(
                    PlayerProgressMode.SOUNDCLOUD,
                    PlayerProgressMode.HYBRID_WAVEFORM,
                    PlayerProgressMode.CLASSIC_BAR
                ),
                selectedOption = mode,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                onOptionSelected = {
                    mode = it
                    prefs.setPlayerProgressMode(it)
                    val newCount = if (it == PlayerProgressMode.SOUNDCLOUD) 5 else 4
                    slots = List(newCount) { i -> prefs.getSlot(it, i) }
                    onUpdated()
                },
                labelProvider = { option ->
                    Text(
                        text = when (option) {
                            PlayerProgressMode.SOUNDCLOUD -> stringResource(R.string.player_mode_soundcloud)
                            PlayerProgressMode.HYBRID_WAVEFORM -> stringResource(R.string.player_mode_hybrid)
                            PlayerProgressMode.CLASSIC_BAR -> stringResource(R.string.player_mode_classic)
                        },
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                },
                iconProvider = { option ->
                    Icon(
                        imageVector = when (option) {
                            PlayerProgressMode.SOUNDCLOUD -> Icons.Rounded.GraphicEq
                            PlayerProgressMode.HYBRID_WAVEFORM -> Icons.Rounded.Waves
                            PlayerProgressMode.CLASSIC_BAR -> Icons.Rounded.LinearScale
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )

            if (mode != PlayerProgressMode.CLASSIC_BAR) {
                SettingsGroupTitle(stringResource(R.string.player_visual_options_group))

                val isSoundCloud = mode == PlayerProgressMode.SOUNDCLOUD
                val itemsCount = if (isSoundCloud) 3 else 1

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    SettingsItem(
                        shape = getSettingsShape(itemsCount, 0),
                        title = stringResource(R.string.player_opt_comment_bubbles_title),
                        subtitle = stringResource(R.string.player_opt_comment_bubbles_subtitle),
                        icon = Icons.Rounded.ChatBubbleOutline,
                        hasSwitch = true,
                        switchState = commentsPopup,
                        onSwitchChange = {
                            commentsPopup = it
                            prefs.setWaveformCommentsPopupEnabled(it)
                            onUpdated()
                        }
                    )

                    if (isSoundCloud) {
                        SettingsItem(
                            shape = getSettingsShape(itemsCount, 1),
                            title = stringResource(R.string.player_opt_reactions_bar_title),
                            subtitle = stringResource(R.string.player_opt_reactions_bar_subtitle),
                            icon = Icons.Rounded.AddReaction,
                            hasSwitch = true,
                            switchState = reactionsBar,
                            onSwitchChange = {
                                reactionsBar = it
                                prefs.setSoundCloudReactionsBarEnabled(it)
                                onUpdated()
                            }
                        )

                        SettingsItem(
                            shape = getSettingsShape(itemsCount, 2),
                            title = stringResource(R.string.player_opt_parallax_title),
                            subtitle = stringResource(R.string.player_opt_parallax_subtitle),
                            icon = Icons.Rounded.AutoAwesome,
                            hasSwitch = true,
                            switchState = parallax,
                            onSwitchChange = {
                                parallax = it
                                prefs.setSoundCloudParallaxEnabled(it)
                                onUpdated()
                            }
                        )
                    }
                }
            }

            SettingsGroupTitle(
                if (isSoundCloudMode) stringResource(R.string.player_action_bar_5_title)
                else stringResource(R.string.player_action_bar_4_title)
            )

            Text(
                text = if (isSoundCloudMode) stringResource(R.string.player_action_bar_5_desc)
                else stringResource(R.string.player_action_bar_4_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                slots.forEachIndexed { index, slot ->
                    SettingsItem(
                        shape = getSettingsShape(slots.size, index),
                        title = stringResource(R.string.player_slot_n, index + 1),
                        subtitle = stringResource(slot.titleRes),
                        icon = getSlotIcon(slot),
                        trailingText = stringResource(R.string.player_slot_change),
                        onClick = { selectedSlotToEdit = index }
                    )
                }
            }
        }
    }

    selectedSlotToEdit?.let { slotIdx ->
        val allSlots = PlayerActionButtonSlot.values().toList()
        AlertDialog(
            onDismissRequest = { selectedSlotToEdit = null },
            title = {
                Text(
                    text = stringResource(R.string.player_slot_n, slotIdx + 1),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(allSlots.size) { idx ->
                        val slotOption = allSlots[idx]
                        val isSelected = slots[slotIdx] == slotOption
                        SettingsItem(
                            shape = getSettingsShape(allSlots.size, idx),
                            title = stringResource(slotOption.titleRes),
                            icon = getSlotIcon(slotOption),
                            trailingText = if (isSelected) stringResource(R.string.player_slot_active) else null,
                            onClick = {
                                prefs.setSlot(mode, slotIdx, slotOption)
                                slots = List(if (isSoundCloudMode) 5 else 4) { i -> prefs.getSlot(mode, i) }
                                selectedSlotToEdit = null
                                onUpdated()
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { selectedSlotToEdit = null },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

