package com.alananasss.kittytune.ui.profile

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.AppLanguage
import com.alananasss.kittytune.data.local.AppThemeMode
import com.alananasss.kittytune.data.local.LibraryCategoryLayout
import com.alananasss.kittytune.data.local.PlayerActionButtonSlot
import com.alananasss.kittytune.data.local.PlayerBackgroundStyle
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.StartDestination
import com.alananasss.kittytune.data.local.TrackRemovalMethod
import com.alananasss.kittytune.ui.common.AutoScrollToHighlightedItem
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsGroupTitle
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.common.Slider
import com.alananasss.kittytune.ui.common.getSettingsShape
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme

private enum class ThemeLook(
    val mode: AppThemeMode,
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    SYSTEM(AppThemeMode.SYSTEM, R.string.theme_system, Icons.Rounded.BrightnessAuto),
    LIGHT(AppThemeMode.LIGHT, R.string.theme_light, Icons.Rounded.LightMode),
    DARK(AppThemeMode.DARK, R.string.theme_dark, Icons.Rounded.DarkMode),
    AMOLED(AppThemeMode.DARK, R.string.theme_amoled, Icons.Rounded.Contrast),
}

private class ThemePreset(
    @StringRes val labelRes: Int,
    val seed: Int,
    val style: PaletteStyle
) {
    val styleName: String get() = if (seed == 0) "System" else style.name
}

private val themePresets = listOf(
    ThemePreset(R.string.theme_preset_ocean, 0xFF1565C0.toInt(), PaletteStyle.Vibrant),
    ThemePreset(R.string.theme_preset_forest, 0xFF2E7D32.toInt(), PaletteStyle.Vibrant),
    ThemePreset(R.string.theme_preset_sunset, 0xFFE64A19.toInt(), PaletteStyle.Vibrant),
    ThemePreset(R.string.theme_preset_rose, 0xFFD81B60.toInt(), PaletteStyle.Vibrant),
    ThemePreset(R.string.theme_preset_lavender, 0xFF7E57C2.toInt(), PaletteStyle.Vibrant),
    ThemePreset(R.string.theme_preset_mint, 0xFF00A884.toInt(), PaletteStyle.Vibrant),
)

@Composable
fun AppearanceSettingsScreen(
    onNavigateToColors: () -> Unit,
    onNavigateToBottomBarSettings: () -> Unit,
    onNavigateToAppIconSettings: () -> Unit,
    onNavigateToPlayerCustomization: () -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    val isSystemDark = isSystemInDarkTheme()

    var dynamicTheme by remember { mutableStateOf(prefs.getDynamicTheme()) }
    var trackDynamicTheme by remember { mutableStateOf(prefs.getTrackDynamicTheme()) }
    var themeMode by remember { mutableStateOf(prefs.getThemeMode()) }
    var pureBlack by remember { mutableStateOf(prefs.getPureBlack()) }
    var keyColor by remember { mutableIntStateOf(prefs.getKeyColor()) }

    var customFontEnabled by remember { mutableStateOf(prefs.getCustomFontEnabled()) }
    var appIcon by remember { mutableStateOf(prefs.getAppIconId()) }
    var lyricsUnderCover by remember { mutableStateOf(prefs.getLyricsUnderCoverEnabled()) }
    var animatedCovers by remember { mutableStateOf(prefs.getAnimatedCoversEnabled()) }
    var animatedCoversFadeUi by remember { mutableStateOf(prefs.getAnimatedCoversFadeUiEnabled()) }
    var animatedArtistProfiles by remember { mutableStateOf(prefs.getAnimatedArtistProfilesEnabled()) }
    var explorerGridLayout by remember { mutableStateOf(prefs.getExplorerGridLayout()) }
    var libraryCategoryLayout by remember { mutableStateOf(prefs.getLibraryCategoryLayout()) }
    var achievementPopupsEnabled by remember { mutableStateOf(prefs.getAchievementPopupsEnabled()) }
    var showHomeListeningStats by remember { mutableStateOf(prefs.getShowHomeListeningStats()) }
    var showHomeYourMix by remember { mutableStateOf(prefs.getShowHomeYourMix()) }

    var showFontConfigDialog by remember { mutableStateOf(false) }
    var showCategoryLayoutDialog by remember { mutableStateOf(false) }
    var trackRemovalMethod by remember { mutableStateOf(prefs.getTrackRemovalMethod()) }
    var showTrackRemovalDialog by remember { mutableStateOf(false) }

    val look = remember(themeMode, pureBlack) {
        when {
            themeMode == AppThemeMode.DARK && pureBlack -> ThemeLook.AMOLED
            themeMode == AppThemeMode.DARK -> ThemeLook.DARK
            themeMode == AppThemeMode.LIGHT -> ThemeLook.LIGHT
            else -> ThemeLook.SYSTEM
        }
    }

    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemDark
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
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
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
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
                                label = { Text(stringResource(R.string.font_preset_chunky)) }
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.dialog_font_weight, wght.toInt()), style = MaterialTheme.typography.bodyMedium)
                        }
                        Slider(
                            value = wght,
                            onValueChange = { wght = it; prefs.setFontWght(it.toInt()) },
                            valueRange = 100f..1000f,
                            steps = 18
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.dialog_font_roundness, rond.toInt()), style = MaterialTheme.typography.bodyMedium)
                        }
                        Slider(
                            value = rond,
                            onValueChange = { rond = it; prefs.setFontRond(it) },
                            valueRange = 0f..100f
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFontConfigDialog = false }) {
                    Text(stringResource(R.string.btn_close))
                }
            },
            dismissButton = {
                TextButton(onClick = { applyPreset(400f, 100f, 0f, 0f, 0f, 14f) }) {
                    Text(stringResource(R.string.btn_reset))
                }
            }
        )
    }

    if (showCategoryLayoutDialog) {
        AlertDialog(
            onDismissRequest = { showCategoryLayoutDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.pref_library_category_layout_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LibraryCategoryLayout.entries.forEach { layoutOption ->
                        val isSelected = libraryCategoryLayout == layoutOption
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    libraryCategoryLayout = layoutOption
                                    prefs.setLibraryCategoryLayout(layoutOption)
                                    showCategoryLayoutDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(layoutOption.titleRes),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = stringResource(layoutOption.descRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCategoryLayoutDialog = false }) {
                    Text(stringResource(R.string.btn_close))
                }
            }
        )
    }

    if (showTrackRemovalDialog) {
        AlertDialog(
            onDismissRequest = { showTrackRemovalDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.pref_track_removal_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrackRemovalMethod.entries.forEach { method ->
                        val isSelected = trackRemovalMethod == method
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    trackRemovalMethod = method
                                    prefs.setTrackRemovalMethod(method)
                                    showTrackRemovalDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(
                                    when (method) {
                                        TrackRemovalMethod.SWIPE_AND_MENU -> R.string.track_removal_swipe_and_menu
                                        TrackRemovalMethod.MENU_ONLY -> R.string.track_removal_menu_only
                                    }
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTrackRemovalDialog = false }) {
                    Text(stringResource(R.string.btn_close))
                }
            }
        )
    }

    val listState = rememberLazyListState()

    AutoScrollToHighlightedItem(
        listState = listState,
        keyToIndex = mapOf(
            "pref_theme_dynamic" to 0,
            "pref_theme_track_dynamic" to 0,
            "settings_page_themes" to 1,
            "pref_font_custom" to 2,
            "pref_font_variations" to 2,
            "pref_app_icon" to 2,
            "pref_animated_covers" to 3,
            "pref_animated_covers_fade_ui" to 3,
            "pref_animated_artist_profiles" to 3,
            "pref_lyrics_under_cover" to 3,
            "pref_explorer_grid" to 4,
            "pref_library_category_layout" to 4,
            "pref_track_removal" to 4,
            "pref_achievement_popups" to 4,
            "pref_home_listening_stats" to 5,
            "pref_home_your_mix" to 5
        )
    )

    SettingsScaffold(
        title = stringResource(R.string.pref_appearance_title),
        onBackClick = onBackClick
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp)
        ) {
            // Section: Dynamic Colors
            item {
                SettingsGroup(
                    title = stringResource(R.string.settings_cat_appearance),
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_theme_dynamic),
                                subtitle = stringResource(R.string.pref_theme_dynamic_sub),
                                icon = Icons.Rounded.AutoAwesome,
                                hasSwitch = true,
                                switchState = dynamicTheme,
                                onSwitchChange = { on ->
                                    dynamicTheme = on
                                    prefs.setDynamicTheme(on)
                                    if (on && trackDynamicTheme) {
                                        trackDynamicTheme = false
                                        prefs.setTrackDynamicTheme(false)
                                    }
                                },
                                highlightKey = "pref_theme_dynamic"
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_theme_track_dynamic),
                                subtitle = stringResource(R.string.pref_theme_track_dynamic_sub),
                                icon = Icons.Rounded.Album,
                                hasSwitch = true,
                                switchState = trackDynamicTheme,
                                onSwitchChange = { on ->
                                    trackDynamicTheme = on
                                    prefs.setTrackDynamicTheme(on)
                                    if (on && dynamicTheme) {
                                        dynamicTheme = false
                                        prefs.setDynamicTheme(false)
                                    }
                                },
                                highlightKey = "pref_theme_track_dynamic"
                            )
                        }
                    )
                )
            }

            // Section: Thèmes prêts (Ready-made themes) - Desktop screenshot 1
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    SettingsGroupTitle(stringResource(R.string.theme_presets_title))

                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 4.dp)
                        ) {
                            // Compact row for mode selection + AMOLED
                            ExpressiveConnectedButtonGroup(
                                options = ThemeLook.entries,
                                selectedOption = look,
                                onOptionSelected = { chosen ->
                                    themeMode = chosen.mode
                                    pureBlack = chosen == ThemeLook.AMOLED
                                    prefs.setThemeMode(chosen.mode)
                                    prefs.setPureBlack(pureBlack)
                                },
                                modifier = Modifier.padding(horizontal = 8.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                                iconSpacing = 2.dp,
                                iconProvider = {
                                    Icon(it.icon, contentDescription = null, modifier = Modifier.size(15.dp))
                                },
                                labelProvider = {
                                    Text(
                                        text = stringResource(it.labelRes),
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.5.sp,
                                            letterSpacing = (-0.3).sp
                                        ),
                                        autoSize = TextAutoSize.StepBased(
                                            minFontSize = 7.5.sp,
                                            maxFontSize = 11.5.sp,
                                            stepSize = 0.5.sp
                                        ),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )

                            // Preset Cards Carousel
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                themePresets.forEach { preset ->
                                    ThemePresetCard(
                                        label = stringResource(preset.labelRes),
                                        seed = preset.seed,
                                        style = preset.style,
                                        isSelected = !dynamicTheme && !trackDynamicTheme && (keyColor == preset.seed),
                                        isDark = isDark,
                                        pureBlack = pureBlack && isDark,
                                        onClick = {
                                            keyColor = preset.seed
                                            prefs.setKeyColor(preset.seed)
                                            prefs.setColorStyle(preset.styleName)
                                            dynamicTheme = false
                                            prefs.setDynamicTheme(false)
                                            trackDynamicTheme = false
                                            prefs.setTrackDynamicTheme(false)
                                        }
                                    )
                                }
                                CustomThemeCard(onClick = onNavigateToColors)
                            }
                        }
                    }
                }
            }

            // Section: Fenêtre et texte (Window & Text / Typography)
            item {
                SettingsGroup(
                    title = stringResource(R.string.settings_group_window),
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_font_custom_title),
                                subtitle = stringResource(R.string.pref_font_custom_subtitle),
                                icon = Icons.Rounded.TextFields,
                                hasSwitch = true,
                                switchState = customFontEnabled,
                                onSwitchChange = {
                                    customFontEnabled = it
                                    prefs.setCustomFontEnabled(it)
                                },
                                highlightKey = "pref_font_custom"
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_font_variations_title),
                                subtitle = stringResource(R.string.pref_font_variations_subtitle),
                                icon = Icons.Rounded.Tune,
                                onClick = { showFontConfigDialog = true },
                                highlightKey = "pref_font_variations"
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_app_icon_title),
                                subtitle = stringResource(R.string.pref_app_icon_subtitle),
                                trailingText = getAppIconDisplayName(context, appIcon),
                                icon = Icons.Rounded.Apps,
                                onClick = onNavigateToAppIconSettings,
                                highlightKey = "pref_app_icon"
                            )
                        }
                    )
                )
            }

            // Section: Pochettes (Covers & Artwork)
            item {
                SettingsGroup(
                    title = stringResource(R.string.settings_group_covers),
                    items = buildList {
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_animated_covers),
                                subtitle = stringResource(R.string.pref_animated_covers_desc),
                                icon = Icons.Rounded.PlayCircle,
                                hasSwitch = true,
                                switchState = animatedCovers,
                                onSwitchChange = {
                                    animatedCovers = it
                                    prefs.setAnimatedCoversEnabled(it)
                                },
                                highlightKey = "pref_animated_covers"
                            )
                        }
                        if (animatedCovers) {
                            add { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.pref_animated_covers_fade_ui),
                                    subtitle = stringResource(R.string.pref_animated_covers_fade_ui_desc),
                                    icon = Icons.Rounded.Opacity,
                                    hasSwitch = true,
                                    switchState = animatedCoversFadeUi,
                                    onSwitchChange = {
                                        animatedCoversFadeUi = it
                                        prefs.setAnimatedCoversFadeUiEnabled(it)
                                    },
                                    highlightKey = "pref_animated_covers_fade_ui"
                                )
                            }
                        }
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_animated_artist_profiles),
                                subtitle = stringResource(R.string.pref_animated_artist_profiles_desc),
                                icon = Icons.Rounded.AccountCircle,
                                hasSwitch = true,
                                switchState = animatedArtistProfiles,
                                onSwitchChange = {
                                    animatedArtistProfiles = it
                                    prefs.setAnimatedArtistProfilesEnabled(it)
                                },
                                highlightKey = "pref_animated_artist_profiles"
                            )
                        }
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_lyrics_under_cover),
                                subtitle = stringResource(R.string.pref_lyrics_under_cover_sub),
                                icon = Icons.Rounded.Lyrics,
                                hasSwitch = true,
                                switchState = lyricsUnderCover,
                                onSwitchChange = {
                                    lyricsUnderCover = it
                                    prefs.setLyricsUnderCoverEnabled(it)
                                },
                                highlightKey = "pref_lyrics_under_cover"
                            )
                        }
                    }
                )
            }

            // Section: Layouts & UI Display
            item {
                SettingsGroup(
                    title = stringResource(R.string.settings_cat_general),
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_explorer_grid_title),
                                subtitle = stringResource(R.string.pref_explorer_grid_subtitle),
                                icon = Icons.Rounded.GridView,
                                hasSwitch = true,
                                switchState = explorerGridLayout,
                                onSwitchChange = {
                                    explorerGridLayout = it
                                    prefs.setExplorerGridLayout(it)
                                },
                                highlightKey = "pref_explorer_grid"
                            )
                        },

                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_library_category_layout_title),
                                subtitle = stringResource(libraryCategoryLayout.titleRes),
                                icon = Icons.Rounded.FilterList,
                                onClick = { showCategoryLayoutDialog = true },
                                highlightKey = "pref_library_category_layout"
                            )
                        },

                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_track_removal_title),
                                subtitle = stringResource(
                                    when (trackRemovalMethod) {
                                        TrackRemovalMethod.SWIPE_AND_MENU -> R.string.track_removal_swipe_and_menu
                                        TrackRemovalMethod.MENU_ONLY -> R.string.track_removal_menu_only
                                    }
                                ),
                                icon = Icons.Rounded.DeleteSweep,
                                onClick = { showTrackRemovalDialog = true },
                                highlightKey = "pref_track_removal"
                            )
                        },

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
                                },
                                highlightKey = "pref_achievement_popups"
                            )
                        }
                    )
                )
            }

            // Section: Home Screen Cards
            item {
                SettingsGroup(
                    title = stringResource(R.string.pref_home_cards_group_title),
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.listening_stats_title),
                                subtitle = stringResource(R.string.pref_home_listening_stats_desc),
                                icon = Icons.Rounded.BarChart,
                                hasSwitch = true,
                                switchState = showHomeListeningStats,
                                onSwitchChange = {
                                    showHomeListeningStats = it
                                    prefs.setShowHomeListeningStats(it)
                                },
                                highlightKey = "pref_home_listening_stats"
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.mix_title),
                                subtitle = stringResource(R.string.pref_home_your_mix_desc),
                                icon = Icons.Rounded.AutoAwesome,
                                hasSwitch = true,
                                switchState = showHomeYourMix,
                                onSwitchChange = {
                                    showHomeYourMix = it
                                    prefs.setShowHomeYourMix(it)
                                },
                                highlightKey = "pref_home_your_mix"
                            )
                        }
                    )
                )
            }
        }
    }
}

@Composable
private fun ThemePresetCard(
    label: String,
    seed: Int,
    style: PaletteStyle,
    isSelected: Boolean,
    isDark: Boolean,
    pureBlack: Boolean,
    onClick: () -> Unit,
) {
    val scheme = rememberDynamicColorScheme(
        seedColor = if (seed == 0) Color(0xFFFF7A1A) else Color(seed),
        isDark = isDark,
        isAmoled = pureBlack,
        style = style,
    )
    val interaction = remember { MutableInteractionSource() }
    val ring by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        label = "presetRing",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            onClick = onClick,
            interactionSource = interaction,
            shape = RoundedCornerShape(18.dp),
            color = scheme.surfaceContainer,
            border = BorderStroke(if (isSelected) 2.dp else 1.dp, ring),
            modifier = Modifier.size(width = 96.dp, height = 72.dp)
        ) {
            Box(Modifier.fillMaxSize().padding(10.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val radius = size.height * 0.28f
                    drawCircle(scheme.primary, radius, Offset(radius, radius))
                    val barHeight = size.height * 0.16f
                    val barLeft = radius * 2 + 8.dp.toPx()
                    drawRoundRect(
                        scheme.secondaryContainer,
                        topLeft = Offset(barLeft, radius - barHeight),
                        size = Size(size.width - barLeft, barHeight),
                        cornerRadius = CornerRadius(barHeight / 2),
                    )
                    drawRoundRect(
                        scheme.tertiary,
                        topLeft = Offset(barLeft, radius + 2.dp.toPx()),
                        size = Size((size.width - barLeft) * 0.6f, barHeight),
                        cornerRadius = CornerRadius(barHeight / 2),
                    )
                    drawRoundRect(
                        scheme.primaryContainer,
                        topLeft = Offset(0f, size.height - barHeight * 1.4f),
                        size = Size(size.width, barHeight * 1.4f),
                        cornerRadius = CornerRadius(barHeight),
                    )
                }
                if (isSelected) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.align(Alignment.TopEnd).size(18.dp),
                    )
                }
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun CustomThemeCard(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            onClick = onClick,
            interactionSource = interaction,
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            modifier = Modifier.size(width = 96.dp, height = 72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
        Text(
            stringResource(R.string.theme_custom_short),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
