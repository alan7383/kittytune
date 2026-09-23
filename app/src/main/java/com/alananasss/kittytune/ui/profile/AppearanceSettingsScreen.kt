package com.alananasss.kittytune.ui.profile

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Color as AndroidColor
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.WaveformRepository
import com.alananasss.kittytune.ui.theme.ThemeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
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
import com.alananasss.kittytune.data.local.WaveformColorMode
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
                    val onLanguageSelected: (AppLanguage) -> Unit = { selectedLang ->
                        appLanguage = selectedLang
                        prefs.setAppLanguage(selectedLang)
                        showLanguageDialog = false
                        restartApp(context)
                    }
                    LanguageRadioButton(
                        stringResource(R.string.theme_system),
                        AppLanguage.SYSTEM,
                        appLanguage,
                        onLanguageSelected
                    )
                    LanguageRadioButton(
                        stringResource(R.string.lang_french),
                        AppLanguage.FRENCH,
                        appLanguage,
                        onLanguageSelected
                    )
                    LanguageRadioButton(
                        stringResource(R.string.lang_english),
                        AppLanguage.ENGLISH,
                        appLanguage,
                        onLanguageSelected
                    )
                    LanguageRadioButton(
                        stringResource(R.string.lang_german),
                        AppLanguage.GERMAN,
                        appLanguage,
                        onLanguageSelected
                    )
                    LanguageRadioButton(
                        stringResource(R.string.lang_hungarian),
                        AppLanguage.HUNGARIAN,
                        appLanguage,
                        onLanguageSelected
                    )
                    LanguageRadioButton(
                        stringResource(R.string.lang_russian),
                        AppLanguage.RUSSIAN,
                        appLanguage,
                        onLanguageSelected
                    )
                    LanguageRadioButton(
                        stringResource(R.string.lang_vietnamese),
                        AppLanguage.VIETNAMESE,
                        appLanguage,
                        onLanguageSelected
                    )
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
                                AppLanguage.GERMAN -> stringResource(R.string.lang_german)
                                AppLanguage.HUNGARIAN -> stringResource(R.string.lang_hungarian)
                                AppLanguage.RUSSIAN -> stringResource(R.string.lang_russian)
                                AppLanguage.VIETNAMESE -> stringResource(R.string.lang_vietnamese)
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun restartApp(context: Context) {
    com.alananasss.kittytune.utils.LocaleUtils.applyAppLanguage(context)
    com.alananasss.kittytune.data.network.RetrofitClient.resetClient()
    val activity = context.findActivity()
    if (activity != null) {
        val intent = Intent(activity, activity.javaClass)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(intent)
        activity.finish()
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
        PlayerActionButtonSlot.FULLSCREEN_LYRICS -> Icons.Rounded.OpenInFull
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

    var waveformColorMode by remember { mutableStateOf(prefs.getWaveformColorMode()) }
    var waveformCustomColor by remember { mutableIntStateOf(prefs.getWaveformCustomColor()) }
    var showWaveformColorDialog by remember { mutableStateOf(false) }

    var showSliderStyleDialog by remember { mutableStateOf(false) }

    val slotCount = if (currentDesign == PlayerDesign.SOUNDCLOUD) 5 else 4

    var slots by remember(currentDesign) {
        mutableStateOf(List(slotCount) { i -> prefs.getSlotForDesign(currentDesign, i) })
    }

    var selectedSlotToEdit by remember { mutableStateOf<Int?>(null) }

    var shareCardCodeMode by remember { mutableIntStateOf(prefs.getShareCardCodeMode()) }
    var showShareCardCodeDialog by remember { mutableStateOf(false) }

    if (showShareCardCodeDialog) {
        val labels = listOf(
            stringResource(R.string.share_card_code_auto) to stringResource(R.string.share_card_code_auto_desc),
            stringResource(R.string.share_card_code_solid) to stringResource(R.string.share_card_code_solid_desc),
            stringResource(R.string.share_card_code_halftone) to stringResource(R.string.share_card_code_halftone_desc),
        )
        AlertDialog(
            onDismissRequest = { showShareCardCodeDialog = false },
            title = { Text(stringResource(R.string.share_card_code_title)) },
            text = {
                Column {
                    labels.forEachIndexed { index, (label, desc) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    shareCardCodeMode = index
                                    prefs.setShareCardCodeMode(index)
                                    showShareCardCodeDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = shareCardCodeMode == index,
                                onClick = {
                                    shareCardCodeMode = index
                                    prefs.setShareCardCodeMode(index)
                                    showShareCardCodeDialog = false
                                },
                            )
                            Column(modifier = Modifier.padding(start = 4.dp)) {
                                Text(label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showShareCardCodeDialog = false },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
        )
    }

    if (showWaveformColorDialog) {
        WaveformColorDialog(
            currentMode = waveformColorMode,
            currentColor = waveformCustomColor,
            onModeSelected = { mode ->
                waveformColorMode = mode
                prefs.setWaveformColorMode(mode)
                onUpdated()
            },
            onColorSelected = { color ->
                waveformCustomColor = color
                prefs.setWaveformCustomColor(color)
                waveformColorMode = WaveformColorMode.CUSTOM
                prefs.setWaveformColorMode(WaveformColorMode.CUSTOM)
                onUpdated()
            },
            onDismiss = { showWaveformColorDialog = false }
        )
    }

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
                            shape = getSettingsShape(4, 0),
                            title = stringResource(R.string.pref_waveform_color_title),
                            subtitle = when (waveformColorMode) {
                                WaveformColorMode.SOUNDCLOUD -> stringResource(R.string.waveform_color_soundcloud)
                                WaveformColorMode.COVER_ART -> stringResource(R.string.waveform_color_cover_art)
                                WaveformColorMode.APP_THEME -> stringResource(R.string.waveform_color_app_theme)
                                WaveformColorMode.CUSTOM -> stringResource(R.string.waveform_color_custom)
                            },
                            icon = Icons.Rounded.Palette,
                            onClick = { showWaveformColorDialog = true }
                        )

                        SettingsItem(
                            shape = getSettingsShape(4, 1),
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
                            shape = getSettingsShape(4, 2),
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
                            shape = getSettingsShape(4, 3),
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
                                shape = getSettingsShape(2, 0),
                                title = stringResource(R.string.pref_waveform_color_title),
                                subtitle = when (waveformColorMode) {
                                    WaveformColorMode.SOUNDCLOUD -> stringResource(R.string.waveform_color_soundcloud)
                                    WaveformColorMode.COVER_ART -> stringResource(R.string.waveform_color_cover_art)
                                    WaveformColorMode.APP_THEME -> stringResource(R.string.waveform_color_app_theme)
                                    WaveformColorMode.CUSTOM -> stringResource(R.string.waveform_color_custom)
                                },
                                icon = Icons.Rounded.Palette,
                                onClick = { showWaveformColorDialog = true }
                            )

                            SettingsItem(
                                shape = getSettingsShape(2, 1),
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

            SettingsGroupTitle(stringResource(R.string.share_card_settings_group))
            val codeLabels = listOf(
                stringResource(R.string.share_card_code_auto),
                stringResource(R.string.share_card_code_solid),
                stringResource(R.string.share_card_code_halftone),
            )
            SettingsItem(
                shape = getSettingsShape(1, 0),
                title = stringResource(R.string.share_card_code_title),
                subtitle = stringResource(R.string.share_card_code_subtitle),
                trailingText = codeLabels[shareCardCodeMode.coerceIn(0, 2)],
                onClick = { showShareCardCodeDialog = true },
            )
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

@Composable
fun WaveformColorDialog(
    currentMode: WaveformColorMode,
    currentColor: Int,
    onModeSelected: (WaveformColorMode) -> Unit,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var selectedMode by remember { mutableStateOf(currentMode) }
    var selectedColor by remember { mutableIntStateOf(currentColor) }

    val initialHsv = remember(currentColor) {
        FloatArray(3).also { AndroidColor.colorToHSV(currentColor, it) }
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember { mutableFloatStateOf(initialHsv[2]) }

    var hexInput by remember {
        mutableStateOf(String.format("%06X", currentColor and 0xFFFFFF))
    }
    var hexError by remember { mutableStateOf(false) }

    fun updateColorFromHsv(newH: Float, newS: Float, newV: Float) {
        hue = newH
        saturation = newS
        brightness = newV
        val argb = AndroidColor.HSVToColor(floatArrayOf(newH, newS, newV))
        selectedColor = argb
        hexInput = String.format("%06X", argb and 0xFFFFFF)
        hexError = false
        onColorSelected(argb)
    }

    fun selectPreset(presetInt: Int) {
        selectedColor = presetInt
        val hsv = FloatArray(3).also { AndroidColor.colorToHSV(presetInt, it) }
        hue = hsv[0]
        saturation = hsv[1]
        brightness = hsv[2]
        hexInput = String.format("%06X", presetInt and 0xFFFFFF)
        hexError = false
        onColorSelected(presetInt)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    val presetColors = remember {
        listOf(
            0xFFFF5500.toInt(), // SoundCloud Orange
            0xFFFF3366.toInt(), // Neon Coral Pink
            0xFFE53935.toInt(), // Crimson Red
            0xFF8E24AA.toInt(), // Purple
            0xFF3F51B5.toInt(), // Indigo
            0xFF1E88E5.toInt(), // Sky Blue
            0xFF00ACC1.toInt(), // Cyan
            0xFF00E676.toInt(), // Mint Green
            0xFF43A047.toInt(), // Emerald Green
            0xFFFFB300.toInt(), // Amber Gold
            0xFFFFFFFF.toInt()  // Pure White
        )
    }

    val hueGradient = remember {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFFFF0000), // Red
                Color(0xFFFFFF00), // Yellow
                Color(0xFF00FF00), // Green
                Color(0xFF00FFFF), // Cyan
                Color(0xFF0000FF), // Blue
                Color(0xFFFF00FF), // Magenta
                Color(0xFFFF0000)  // Red
            )
        )
    }

    val satGradient = remember(hue, brightness) {
        Brush.horizontalGradient(
            colors = listOf(
                Color(AndroidColor.HSVToColor(floatArrayOf(hue, 0.0f, brightness))),
                Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1.0f, brightness)))
            )
        )
    }

    val brightGradient = remember(hue, saturation) {
        Brush.horizontalGradient(
            colors = listOf(
                Color.Black,
                Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, 1.0f)))
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.pref_waveform_color_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.pref_waveform_color_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Live Waveform Preview Card
                MiniWaveformPreview(
                    mode = selectedMode,
                    customColor = selectedColor
                )

                // 2. Mode Cards Grid (2 rows x 2 columns)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WaveformModeCard(
                            title = stringResource(R.string.waveform_mode_soundcloud_title),
                            subtitle = stringResource(R.string.waveform_mode_soundcloud_sub),
                            isSelected = selectedMode == WaveformColorMode.SOUNDCLOUD,
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF5500))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), CircleShape)
                                )
                            },
                            onClick = {
                                selectedMode = WaveformColorMode.SOUNDCLOUD
                                onModeSelected(WaveformColorMode.SOUNDCLOUD)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            modifier = Modifier.weight(1f)
                        )

                        WaveformModeCard(
                            title = stringResource(R.string.waveform_mode_cover_title),
                            subtitle = stringResource(R.string.waveform_mode_cover_sub),
                            isSelected = selectedMode == WaveformColorMode.COVER_ART,
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.sweepGradient(
                                                listOf(
                                                    Color(0xFFE53935),
                                                    Color(0xFFFFB300),
                                                    Color(0xFF43A047),
                                                    Color(0xFF1E88E5),
                                                    Color(0xFF8E24AA),
                                                    Color(0xFFE53935)
                                                )
                                            )
                                        )
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), CircleShape)
                                )
                            },
                            onClick = {
                                selectedMode = WaveformColorMode.COVER_ART
                                onModeSelected(WaveformColorMode.COVER_ART)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WaveformModeCard(
                            title = stringResource(R.string.waveform_mode_theme_title),
                            subtitle = stringResource(R.string.waveform_mode_theme_sub),
                            isSelected = selectedMode == WaveformColorMode.APP_THEME,
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), CircleShape)
                                )
                            },
                            onClick = {
                                selectedMode = WaveformColorMode.APP_THEME
                                onModeSelected(WaveformColorMode.APP_THEME)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            modifier = Modifier.weight(1f)
                        )

                        WaveformModeCard(
                            title = stringResource(R.string.waveform_mode_custom_title),
                            subtitle = stringResource(R.string.waveform_mode_custom_sub),
                            isSelected = selectedMode == WaveformColorMode.CUSTOM,
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Color(selectedColor))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape)
                                )
                            },
                            onClick = {
                                selectedMode = WaveformColorMode.CUSTOM
                                onModeSelected(WaveformColorMode.CUSTOM)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 3. Custom Color Section (animated)
                AnimatedVisibility(
                    visible = selectedMode == WaveformColorMode.CUSTOM,
                    enter = fadeIn(tween(200)) + expandVertically(tween(250)),
                    exit = fadeOut(tween(150)) + shrinkVertically(tween(200))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 2.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )

                        // Expressive Color Sliders Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                WaveformColorSlider(
                                    label = stringResource(R.string.color_picker_hue),
                                    value = hue,
                                    valueText = "${hue.toInt()}°",
                                    valueRange = 0f..360f,
                                    gradientBrush = hueGradient,
                                    onValueChange = { newHue ->
                                        updateColorFromHsv(newHue, saturation, brightness)
                                    }
                                )

                                WaveformColorSlider(
                                    label = stringResource(R.string.color_picker_saturation),
                                    value = saturation,
                                    valueText = "${(saturation * 100).toInt()}%",
                                    valueRange = 0f..1f,
                                    gradientBrush = satGradient,
                                    onValueChange = { newSat ->
                                        updateColorFromHsv(hue, newSat, brightness)
                                    }
                                )

                                WaveformColorSlider(
                                    label = stringResource(R.string.color_picker_brightness),
                                    value = brightness,
                                    valueText = "${(brightness * 100).toInt()}%",
                                    valueRange = 0f..1f,
                                    gradientBrush = brightGradient,
                                    onValueChange = { newBright ->
                                        updateColorFromHsv(hue, saturation, newBright)
                                    }
                                )
                            }
                        }

                        // Presets Swatches
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.color_picker_presets),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(presetColors.size) { idx ->
                                    val colorInt = presetColors[idx]
                                    val isColorActive = (selectedColor and 0xFFFFFF) == (colorInt and 0xFFFFFF)
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(colorInt))
                                            .border(
                                                width = if (isColorActive) 2.5.dp else 1.dp,
                                                color = if (isColorActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                                shape = CircleShape
                                            )
                                            .clickable { selectPreset(colorInt) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isColorActive) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = if ((colorInt and 0xFFFFFF) == 0xFFFFFF || (colorInt and 0xFFFFFF) == 0xFFFFB300.toInt()) Color.Black else Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // HEX input row + preview swatch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = hexInput,
                                onValueChange = { input ->
                                    val filtered = input.take(6).filter { c ->
                                        c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
                                    }
                                    hexInput = filtered.uppercase()
                                    if (filtered.length == 6) {
                                        try {
                                            val parsed = (0xFF000000L or filtered.toLong(16)).toInt()
                                            selectedColor = parsed
                                            val hsv = FloatArray(3).also { AndroidColor.colorToHSV(parsed, it) }
                                            hue = hsv[0]
                                            saturation = hsv[1]
                                            brightness = hsv[2]
                                            hexError = false
                                            onColorSelected(parsed)
                                        } catch (e: Exception) {
                                            hexError = true
                                        }
                                    } else {
                                        hexError = false
                                    }
                                },
                                label = { Text("HEX") },
                                prefix = { Text("#") },
                                singleLine = true,
                                isError = hexError,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Characters,
                                    keyboardType = KeyboardType.Ascii
                                ),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            )

                            Surface(
                                modifier = Modifier.size(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                color = Color(selectedColor),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {}
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text(
                    text = stringResource(R.string.btn_ok),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

@Composable
private fun MiniWaveformPreview(
    mode: WaveformColorMode,
    customColor: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTrack = MusicManager.currentTrack
    val trackId = currentTrack?.id

    var waveformSamples by remember(trackId) {
        mutableStateOf(trackId?.let { WaveformRepository.getCachedWaveform(it) })
    }

    LaunchedEffect(trackId) {
        if (currentTrack != null && waveformSamples == null) {
            val samples = withContext(Dispatchers.IO) {
                WaveformRepository.getWaveform(context, currentTrack)
            }
            if (samples != null) {
                waveformSamples = samples
            }
        }
    }

    val fallbackBars = remember {
        val rng = java.util.Random(13L)
        FloatArray(200) { i ->
            val base = (Math.sin(i * 0.08) * 0.3 + 0.55).toFloat()
            val noise = (rng.nextFloat() - 0.5f) * 0.25f
            (base + noise).coerceIn(0.08f, 0.95f)
        }
    }

    val themePrimary = MaterialTheme.colorScheme.primary
    val targetAccentColor = remember(mode, customColor, themePrimary, ThemeState.coverSeedColor) {
        when (mode) {
            WaveformColorMode.SOUNDCLOUD -> Color(0xFFFF5500)
            WaveformColorMode.COVER_ART -> ThemeState.coverSeedColor?.let { Color(it) } ?: Color(0xFFE53935)
            WaveformColorMode.APP_THEME -> themePrimary
            WaveformColorMode.CUSTOM -> Color(customColor)
        }
    }
    val accentColor by animateColorAsState(
        targetValue = targetAccentColor,
        animationSpec = tween(durationMillis = 300),
        label = "miniWaveformAccentColor"
    )
    val inactiveBarColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)

    var progressFrac by remember { mutableFloatStateOf(0.55f) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = accentColor
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.waveform_preview_title),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (currentTrack != null && !currentTrack.title.isNullOrBlank()) {
                            Text(
                                text = currentTrack.title,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = when (mode) {
                            WaveformColorMode.SOUNDCLOUD -> "SoundCloud"
                            WaveformColorMode.COVER_ART -> "Auto Match"
                            WaveformColorMode.APP_THEME -> "App Theme"
                            WaveformColorMode.CUSTOM -> String.format("#%06X", customColor and 0xFFFFFF)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
            ) {
                val density = LocalDensity.current
                val canvasWidthPx = with(density) { maxWidth.toPx() }
                val barWidthPx = with(density) { 2.2.dp.toPx() }
                val gapPx = with(density) { 1.2.dp.toPx() }
                val stepPx = barWidthPx + gapPx
                val cornerRadius = CornerRadius(barWidthPx / 2f)

                val targetBarCount = (canvasWidthPx / stepPx).toInt().coerceAtLeast(20)

                val resampledBars = remember(waveformSamples, targetBarCount) {
                    val raw = waveformSamples ?: fallbackBars
                    val rawSize = raw.size
                    val result = FloatArray(targetBarCount)
                    for (j in 0 until targetBarCount) {
                        val startIdx = (j.toLong() * rawSize / targetBarCount).toInt()
                        val endIdx = (((j + 1).toLong() * rawSize / targetBarCount).toInt())
                            .coerceAtMost(rawSize)
                            .coerceAtLeast(startIdx + 1)
                        var sum = 0f
                        for (k in startIdx until endIdx) {
                            sum += raw[k]
                        }
                        result[j] = (sum / (endIdx - startIdx)).coerceIn(0.04f, 1f)
                    }
                    result
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(canvasWidthPx) {
                            detectTapGestures { offset ->
                                progressFrac = (offset.x / size.width).coerceIn(0.05f, 0.95f)
                            }
                        }
                        .pointerInput(canvasWidthPx) {
                            detectHorizontalDragGestures { change, _ ->
                                change.consume()
                                progressFrac = (change.position.x / size.width).coerceIn(0.05f, 0.95f)
                            }
                        }
                ) {
                    val cH = size.height
                    val baselineY = cH * 0.60f
                    val reflectGap = 1.5.dp.toPx()

                    val barsToDraw = resampledBars
                    val barCount = barsToDraw.size
                    val cutoffX = size.width * progressFrac

                    for (i in 0 until barCount) {
                        val x = i * stepPx
                        if (x + barWidthPx < 0f || x > size.width) continue

                        val h = barsToDraw[i]
                        val isPlayed = (x + barWidthPx / 2f) <= cutoffX

                        val topH = (baselineY * h * 0.92f).coerceAtLeast(3f)
                        val botH = ((cH - baselineY - reflectGap) * h * 0.65f).coerceAtLeast(2f)

                        val topColor = if (isPlayed) accentColor else inactiveBarColor
                        val botColor = if (isPlayed) accentColor.copy(alpha = 0.50f) else inactiveBarColor.copy(alpha = 0.30f)

                        drawRoundRect(
                            color = topColor,
                            topLeft = Offset(x, baselineY - topH),
                            size = Size(barWidthPx, topH),
                            cornerRadius = cornerRadius
                        )
                        drawRoundRect(
                            color = botColor,
                            topLeft = Offset(x, baselineY + reflectGap),
                            size = Size(barWidthPx, botH),
                            cornerRadius = cornerRadius
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WaveformModeCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    leadingContent: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            leadingContent()

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WaveformColorSlider(
    label: String,
    value: Float,
    valueText: String,
    valueRange: ClosedFloatingPointRange<Float>,
    gradientBrush: Brush,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(gradientBrush)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        RoundedCornerShape(6.dp)
                    )
            )

            Slider(
                value = value,
                onValueChange = onValueChange,
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


