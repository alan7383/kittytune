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
import com.alananasss.kittytune.ui.common.Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import com.alananasss.kittytune.data.local.PlayerDesign
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.PlayerProgressMode
import com.alananasss.kittytune.data.local.PlayerSliderStyle
import com.alananasss.kittytune.data.local.StartDestination
import com.alananasss.kittytune.data.local.TrackRemovalMethod
import com.alananasss.kittytune.ui.player.slider.SliderStyleDialog
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
    var trackDynamicTheme by remember { mutableStateOf(prefs.getTrackDynamicTheme()) }
    var themeMode by remember { mutableStateOf(prefs.getThemeMode()) }
    var pureBlack by remember { mutableStateOf(prefs.getPureBlack()) }
    var playerStyle by remember { mutableStateOf(prefs.getPlayerStyle()) }
    var playerDesign by remember { mutableStateOf(prefs.getPlayerDesign()) }
    var showPlayerDesignDialog by remember { mutableStateOf(false) }
    var waveformComments by remember { mutableStateOf(prefs.getWaveformCommentsEnabled()) }
    var appLanguage by remember { mutableStateOf(prefs.getAppLanguage()) }
    var achievementPopupsEnabled by remember { mutableStateOf(prefs.getAchievementPopupsEnabled()) }
    var autoUpdate by remember { mutableStateOf(prefs.getAutoUpdateEnabled()) }
    var customFontEnabled by remember { mutableStateOf(prefs.getCustomFontEnabled()) }
    var appIcon by remember { mutableStateOf(prefs.getAppIconId()) }
    var playerProgressMode by remember { mutableStateOf(prefs.getPlayerProgressMode()) }
    var sliderStyle by remember { mutableStateOf(prefs.getPlayerSliderStyle()) }
    var trackRemovalMethod by remember { mutableStateOf(prefs.getTrackRemovalMethod()) }
    var lyricsUnderCover by remember { mutableStateOf(prefs.getLyricsUnderCoverEnabled()) }
    var animatedCovers by remember { mutableStateOf(prefs.getAnimatedCoversEnabled()) }
    var animatedCoversFadeUi by remember { mutableStateOf(prefs.getAnimatedCoversFadeUiEnabled()) }
    var animatedArtistProfiles by remember { mutableStateOf(prefs.getAnimatedArtistProfilesEnabled()) }

    var showPlayerStyleDialog by remember { mutableStateOf(false) }
    var showSliderStyleDialog by remember { mutableStateOf(false) }
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
                sliderStyle = prefs.getPlayerSliderStyle()
            }
        )
    }

    if (showSliderStyleDialog) {
        SliderStyleDialog(
            currentStyle = sliderStyle,
            onStyleSelected = {
                sliderStyle = it
                prefs.setPlayerSliderStyle(it)
            },
            onDismiss = { showSliderStyleDialog = false }
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
                    PlayerStyleRadioButton(
                        stringResource(R.string.style_apple_music),
                        PlayerBackgroundStyle.APPLE_MUSIC,
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

    if (showPlayerDesignDialog) {
        AlertDialog(
            onDismissRequest = { showPlayerDesignDialog = false },
            title = { Text(stringResource(R.string.pref_player_design)) },
            text = {
                Column {
                    PlayerDesignRadioButton(
                        title = stringResource(R.string.player_design_pixel),
                        description = stringResource(R.string.player_design_pixel_desc),
                        design = PlayerDesign.PIXEL_PLAYER,
                        selected = playerDesign
                    ) {
                        playerDesign = it
                        prefs.setPlayerDesign(it)
                        showPlayerDesignDialog = false
                    }
                    PlayerDesignRadioButton(
                        title = stringResource(R.string.player_design_soundcloud),
                        description = stringResource(R.string.player_design_soundcloud_desc),
                        design = PlayerDesign.SOUNDCLOUD,
                        selected = playerDesign
                    ) {
                        playerDesign = it
                        prefs.setPlayerDesign(it)
                        showPlayerDesignDialog = false
                    }
                    PlayerDesignRadioButton(
                        title = stringResource(R.string.player_design_modern),
                        description = stringResource(R.string.player_design_modern_desc),
                        design = PlayerDesign.MODERN,
                        selected = playerDesign
                    ) {
                        playerDesign = it
                        prefs.setPlayerDesign(it)
                        showPlayerDesignDialog = false
                    }
                    PlayerDesignRadioButton(
                        title = stringResource(R.string.player_design_classic),
                        description = stringResource(R.string.player_design_classic_desc),
                        design = PlayerDesign.CLASSIC,
                        selected = playerDesign
                    ) {
                        playerDesign = it
                        prefs.setPlayerDesign(it)
                        showPlayerDesignDialog = false
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlayerDesignDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
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
                        val totalVisibleItems = if (isPureBlackVisible) 5 else 4
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

                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, 2),
                            title = stringResource(R.string.pref_theme_track_dynamic),
                            subtitle = stringResource(R.string.pref_theme_track_dynamic_sub),
                            hasSwitch = true,
                            switchState = trackDynamicTheme,
                            onSwitchChange = { trackDynamicTheme = it; prefs.setTrackDynamicTheme(it) }
                        )

                        AnimatedVisibility(
                            visible = isPureBlackVisible,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            SettingsItem(
                                shape = getSettingsShape(totalVisibleItems, 3),
                                title = stringResource(R.string.pref_theme_pure_black),
                                subtitle = stringResource(R.string.pref_theme_pure_black_sub),
                                hasSwitch = true,
                                switchState = pureBlack,
                                onSwitchChange = { pureBlack = it; prefs.setPureBlack(it) }
                            )
                        }

                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, if (isPureBlackVisible) 4 else 3),
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
                    items = buildList {
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_player_design),
                                subtitle = when (playerDesign) {
                                    PlayerDesign.PIXEL_PLAYER -> stringResource(R.string.player_design_pixel)
                                    PlayerDesign.SOUNDCLOUD -> stringResource(R.string.player_design_soundcloud)
                                    PlayerDesign.MODERN -> stringResource(R.string.player_design_modern)
                                    PlayerDesign.CLASSIC -> stringResource(R.string.player_design_classic)
                                },
                                onClick = { showPlayerDesignDialog = true }
                            )
                        }
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.player_style_customization_title),
                                subtitle = when (playerDesign) {
                                    PlayerDesign.PIXEL_PLAYER -> stringResource(R.string.player_action_bar_pixel_desc)
                                    PlayerDesign.SOUNDCLOUD -> stringResource(R.string.player_design_soundcloud_desc)
                                    PlayerDesign.MODERN -> when (playerProgressMode) {
                                        PlayerProgressMode.HYBRID_WAVEFORM -> stringResource(R.string.player_style_hybrid_desc)
                                        else -> stringResource(R.string.player_style_classic_desc)
                                    }
                                    PlayerDesign.CLASSIC -> stringResource(R.string.player_style_classic_desc)
                                },
                                trailingText = stringResource(R.string.player_slot_edit),
                                onClick = { showPlayerCustomizationBottomSheet = true }
                            )
                        }
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_player_bg_style),
                                subtitle = when (playerStyle) {
                                    PlayerBackgroundStyle.THEME -> stringResource(R.string.style_theme)
                                    PlayerBackgroundStyle.GRADIENT -> stringResource(R.string.style_gradient)
                                    PlayerBackgroundStyle.BLUR -> stringResource(R.string.style_blur)
                                    PlayerBackgroundStyle.APPLE_MUSIC -> stringResource(R.string.style_apple_music)
                                },
                                onClick = { showPlayerStyleDialog = true }
                            )
                        }
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_lyrics_under_cover),
                                subtitle = stringResource(R.string.pref_lyrics_under_cover_sub),
                                hasSwitch = true,
                                switchState = lyricsUnderCover,
                                onSwitchChange = {
                                    lyricsUnderCover = it
                                    prefs.setLyricsUnderCoverEnabled(it)
                                }
                            )
                        }
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_animated_covers),
                                subtitle = stringResource(R.string.pref_animated_covers_desc),
                                hasSwitch = true,
                                switchState = animatedCovers,
                                onSwitchChange = {
                                    animatedCovers = it
                                    prefs.setAnimatedCoversEnabled(it)
                                }
                            )
                        }
                        if (animatedCovers) {
                            add { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.pref_animated_covers_fade_ui),
                                    subtitle = stringResource(R.string.pref_animated_covers_fade_ui_desc),
                                    hasSwitch = true,
                                    switchState = animatedCoversFadeUi,
                                    onSwitchChange = {
                                        animatedCoversFadeUi = it
                                        prefs.setAnimatedCoversFadeUiEnabled(it)
                                    }
                                )
                            }
                        }
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_animated_artist_profiles),
                                subtitle = stringResource(R.string.pref_animated_artist_profiles_desc),
                                hasSwitch = true,
                                switchState = animatedArtistProfiles,
                                onSwitchChange = {
                                    animatedArtistProfiles = it
                                    prefs.setAnimatedArtistProfilesEnabled(it)
                                }
                            )
                        }
                    }
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
fun PlayerDesignRadioButton(
    title: String,
    description: String,
    design: PlayerDesign,
    selected: PlayerDesign,
    onSelect: (PlayerDesign) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onSelect(design) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = (design == selected), onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
        PlayerActionButtonSlot.HAPTICS -> Icons.Rounded.Vibration
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
    var currentDesign by remember { mutableStateOf(prefs.getPlayerDesign()) }
    var modernProgressMode by remember {
        mutableStateOf(
            if (prefs.getPlayerProgressMode() == PlayerProgressMode.SOUNDCLOUD)
                PlayerProgressMode.CLASSIC_BAR
            else
                prefs.getPlayerProgressMode()
        )
    }
    var sliderStyle by remember { mutableStateOf(prefs.getPlayerSliderStyle()) }
    var commentsPopup by remember { mutableStateOf(prefs.getWaveformCommentsPopupEnabled()) }
    var reactionsBar by remember { mutableStateOf(prefs.getSoundCloudReactionsBarEnabled()) }
    var parallax by remember { mutableStateOf(prefs.getSoundCloudParallaxEnabled()) }

    var showSliderStyleDialog by remember { mutableStateOf(false) }

    val slotCount = if (currentDesign == PlayerDesign.SOUNDCLOUD) 5 else 4

    var slots by remember(currentDesign) {
        mutableStateOf(List(slotCount) { i -> prefs.getSlotForDesign(currentDesign, i) })
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
                        prefs.resetDesignCustomization(currentDesign)
                        val newCount = if (currentDesign == PlayerDesign.SOUNDCLOUD) 5 else 4
                        slots = List(newCount) { i -> prefs.getSlotForDesign(currentDesign, i) }
                        commentsPopup = prefs.getWaveformCommentsPopupEnabled()
                        reactionsBar = prefs.getSoundCloudReactionsBarEnabled()
                        parallax = prefs.getSoundCloudParallaxEnabled()
                        sliderStyle = prefs.getPlayerSliderStyle()
                        modernProgressMode = prefs.getPlayerProgressMode()
                        onUpdated()
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.btn_reset))
                }
            }

            SettingsGroupTitle(stringResource(R.string.pref_player_design))

            ExpressiveConnectedButtonGroup(
                options = listOf(
                    PlayerDesign.PIXEL_PLAYER,
                    PlayerDesign.SOUNDCLOUD,
                    PlayerDesign.MODERN,
                    PlayerDesign.CLASSIC
                ),
                selectedOption = currentDesign,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                onOptionSelected = {
                    currentDesign = it
                    prefs.setPlayerDesign(it)
                    val newCount = if (it == PlayerDesign.SOUNDCLOUD) 5 else 4
                    slots = List(newCount) { i -> prefs.getSlotForDesign(it, i) }
                    sliderStyle = prefs.getPlayerSliderStyle()
                    modernProgressMode = prefs.getPlayerProgressMode()
                    onUpdated()
                },
                labelProvider = { option ->
                    Text(
                        text = when (option) {
                            PlayerDesign.PIXEL_PLAYER -> stringResource(R.string.player_design_pixel)
                            PlayerDesign.SOUNDCLOUD -> stringResource(R.string.player_design_soundcloud)
                            PlayerDesign.MODERN -> stringResource(R.string.player_design_modern)
                            PlayerDesign.CLASSIC -> stringResource(R.string.player_design_classic)
                        },
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                },
                iconProvider = { option ->
                    if (option == PlayerDesign.MODERN) {
                        Icon(
                            painter = painterResource(R.drawable.ic_kittytune_logo),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = when (option) {
                                PlayerDesign.PIXEL_PLAYER -> Icons.Rounded.Smartphone
                                PlayerDesign.SOUNDCLOUD -> Icons.Rounded.GraphicEq
                                PlayerDesign.CLASSIC -> Icons.Rounded.LinearScale
                                else -> Icons.Rounded.Waves
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            )

            when (currentDesign) {
                PlayerDesign.PIXEL_PLAYER -> {
                    SettingsGroupTitle(stringResource(R.string.player_visual_options_group))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        SettingsItem(
                            shape = getSettingsShape(1, 0),
                            title = stringResource(R.string.pref_slider_style),
                            subtitle = when (sliderStyle) {
                                PlayerSliderStyle.BAR -> stringResource(R.string.slider_style_bar)
                                PlayerSliderStyle.WAVY -> stringResource(R.string.slider_style_wavy)
                                PlayerSliderStyle.SLIM -> stringResource(R.string.slider_style_slim)
                                PlayerSliderStyle.SQUIGGLY -> stringResource(R.string.slider_style_squiggly)
                            },
                            icon = Icons.Rounded.LinearScale,
                            onClick = { showSliderStyleDialog = true }
                        )
                    }

                    SettingsGroupTitle(stringResource(R.string.player_action_bar_pixel_title))

                    Text(
                        text = stringResource(R.string.player_action_bar_pixel_desc),
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

                PlayerDesign.SOUNDCLOUD -> {
                    SettingsGroupTitle(stringResource(R.string.player_visual_options_group))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        SettingsItem(
                            shape = getSettingsShape(3, 0),
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

                        SettingsItem(
                            shape = getSettingsShape(3, 1),
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
                            shape = getSettingsShape(3, 2),
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

                    SettingsGroupTitle(stringResource(R.string.player_action_bar_5_title))

                    Text(
                        text = stringResource(R.string.player_action_bar_5_desc),
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

                PlayerDesign.MODERN -> {
                    SettingsGroupTitle(stringResource(R.string.player_style_group))

                    ExpressiveConnectedButtonGroup(
                        options = listOf(
                            PlayerProgressMode.CLASSIC_BAR,
                            PlayerProgressMode.HYBRID_WAVEFORM
                        ),
                        selectedOption = modernProgressMode,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                        onOptionSelected = {
                            modernProgressMode = it
                            prefs.setPlayerProgressMode(it)
                            onUpdated()
                        },
                        labelProvider = { option ->
                            Text(
                                text = when (option) {
                                    PlayerProgressMode.CLASSIC_BAR -> stringResource(R.string.player_mode_classic)
                                    else -> stringResource(R.string.player_mode_hybrid)
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
                                    PlayerProgressMode.CLASSIC_BAR -> Icons.Rounded.LinearScale
                                    else -> Icons.Rounded.Waves
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )

                    SettingsGroupTitle(stringResource(R.string.player_visual_options_group))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (modernProgressMode == PlayerProgressMode.HYBRID_WAVEFORM) {
                            SettingsItem(
                                shape = getSettingsShape(1, 0),
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
                        } else {
                            SettingsItem(
                                shape = getSettingsShape(1, 0),
                                title = stringResource(R.string.pref_slider_style),
                                subtitle = when (sliderStyle) {
                                    PlayerSliderStyle.BAR -> stringResource(R.string.slider_style_bar)
                                    PlayerSliderStyle.WAVY -> stringResource(R.string.slider_style_wavy)
                                    PlayerSliderStyle.SLIM -> stringResource(R.string.slider_style_slim)
                                    PlayerSliderStyle.SQUIGGLY -> stringResource(R.string.slider_style_squiggly)
                                },
                                icon = Icons.Rounded.LinearScale,
                                onClick = { showSliderStyleDialog = true }
                            )
                        }
                    }

                    SettingsGroupTitle(stringResource(R.string.player_action_bar_4_title))

                    Text(
                        text = stringResource(R.string.player_action_bar_4_desc),
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

                PlayerDesign.CLASSIC -> {
                    SettingsGroupTitle(stringResource(R.string.player_visual_options_group))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        SettingsItem(
                            shape = getSettingsShape(1, 0),
                            title = stringResource(R.string.pref_slider_style),
                            subtitle = when (sliderStyle) {
                                PlayerSliderStyle.BAR -> stringResource(R.string.slider_style_bar)
                                PlayerSliderStyle.WAVY -> stringResource(R.string.slider_style_wavy)
                                PlayerSliderStyle.SLIM -> stringResource(R.string.slider_style_slim)
                                PlayerSliderStyle.SQUIGGLY -> stringResource(R.string.slider_style_squiggly)
                            },
                            icon = Icons.Rounded.LinearScale,
                            onClick = { showSliderStyleDialog = true }
                        )
                    }

                    SettingsGroupTitle(stringResource(R.string.player_action_bar_4_title))

                    Text(
                        text = stringResource(R.string.player_action_bar_4_desc),
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
        }
    }

    if (showSliderStyleDialog) {
        SliderStyleDialog(
            currentStyle = sliderStyle,
            onStyleSelected = {
                sliderStyle = it
                prefs.setPlayerSliderStyle(it)
                onUpdated()
            },
            onDismiss = { showSliderStyleDialog = false }
        )
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
                                prefs.setSlotForDesign(currentDesign, slotIdx, slotOption)
                                val count = if (currentDesign == PlayerDesign.SOUNDCLOUD) 5 else 4
                                slots = List(count) { i -> prefs.getSlotForDesign(currentDesign, i) }
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

