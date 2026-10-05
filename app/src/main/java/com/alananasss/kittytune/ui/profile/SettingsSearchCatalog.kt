package com.alananasss.kittytune.ui.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.player.PlayerViewModel

@Composable
internal fun rememberSettingsSearchCatalog(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    preferenceVersion: Int,
    onPreferenceChange: () -> Unit
): List<SearchSettingEntry> {
    val context = LocalContext.current
    val englishContext = remember(context) { getEnglishContext(context) }
    val prefs = remember { PlayerPreferences(context) }

    val catInterface = stringResource(R.string.settings_cat_interface)
    val catAudio = stringResource(R.string.settings_cat_audio)
    val catSources = stringResource(R.string.settings_cat_accounts)
    val catStorage = stringResource(R.string.pref_storage_title)
    val catSync = stringResource(R.string.sync_title)
    val catNetwork = stringResource(R.string.pref_proxy_title)
    val catMisc = stringResource(R.string.settings_cat_misc)

    return remember(
        preferenceVersion,
        playerViewModel.isHapticsEnabled,
        playerViewModel.equalizerState.isEnabled,
        playerViewModel.effectsState.isNormalizationEnabled,
        playerViewModel.effectsState.isMonoEnabled
    ) {
        var dynamicTheme = prefs.getDynamicTheme()
        var trackDynamicTheme = prefs.getTrackDynamicTheme()
        var pureBlack = prefs.getPureBlack()
        var animatedCovers = prefs.getAnimatedCoversEnabled()
        var animatedCoversFadeUi = prefs.getAnimatedCoversFadeUiEnabled()
        var animatedArtistProfiles = prefs.getAnimatedArtistProfilesEnabled()
        var lyricsUnderCover = prefs.getLyricsUnderCoverEnabled()
        var showRemainingTime = prefs.getShowRemainingTime()
        var fullPlayerSource = prefs.getFullPlayerSourceIndicatorEnabled()
        var verticalVolume = prefs.getVerticalVolumeSlider()
        var crossfade = prefs.getCrossfadeEnabled()
        var automix = prefs.getAutomixEnabled()
        var autoplay = prefs.getAutoplayEnabled()
        var stopOnTaskClear = prefs.getStopOnTaskClear()
        var persistentQueue = prefs.getPersistentQueueEnabled()
        var savePosition = prefs.getSavePositionEnabled()
        var youtubeFallback = prefs.getYouTubeFallbackEnabled()
        var hideYoutubeVideos = prefs.getHideYoutubeVideos()
        var discordRpc = prefs.getDiscordRpcEnabled()
        var dataSaver = prefs.getDataSaverEnabled()
        var achievementPopups = prefs.getAchievementPopupsEnabled()
        var autoUpdate = prefs.getAutoUpdateEnabled()
        var betaUpdates = prefs.getBetaUpdatesEnabled()
        var rememberSearchFilter = prefs.getRememberSearchFilter()
        var showPlaylistTotalDuration = prefs.getShowPlaylistTotalDuration()
        var customFontEnabled = prefs.getCustomFontEnabled()
        var explorerGridLayout = prefs.getExplorerGridLayout()
        var showHomeListeningStats = prefs.getShowHomeListeningStats()
        var showHomeYourMix = prefs.getShowHomeYourMix()

        val staticItems = listOf(
            // INTERFACE
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.settings_page_themes,
                subtitleRes = R.string.settings_page_themes_sub,
                categoryName = catInterface,
                icon = Icons.Rounded.ColorLens,
                route = "appearance_settings",
                keywordsRes = R.string.keywords_themes,
                keywords = listOf("amoled", "oled", "palette")
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.settings_page_player,
                subtitleRes = R.string.settings_page_player_sub,
                categoryName = catInterface,
                icon = Icons.Rounded.PlayCircle,
                route = "player_design_settings",
                keywordsRes = R.string.keywords_player,
                keywords = listOf("wavy", "slim", "squiggly", "dj flow")
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_bottom_menu_title,
                subtitleRes = R.string.pref_bottom_menu_subtitle,
                categoryName = catInterface,
                icon = Icons.AutoMirrored.Rounded.ViewSidebar,
                route = "bottom_bar_settings",
                keywordsRes = R.string.keywords_navigation_bar,
                keywords = listOf("fab", "tabs")
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_bottom_menu_fab,
                subtitleRes = R.string.pref_bottom_menu_subtitle,
                categoryName = catInterface,
                icon = Icons.Rounded.Add,
                route = "fab_settings",
                keywordsRes = R.string.keywords_fab,
                keywords = listOf("fab")
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_lyrics_title,
                subtitleRes = R.string.settings_page_lyrics_sub,
                categoryName = catInterface,
                icon = Icons.Rounded.Lyrics,
                route = "lyrics_settings",
                keywordsRes = R.string.keywords_lyrics,
                keywords = listOf("karaoke", "synchro")
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_color_palette_title,
                subtitleRes = R.string.pref_color_palette_subtitle,
                categoryName = catInterface,
                icon = Icons.Rounded.Palette,
                route = "color_palette",
                keywordsRes = R.string.keywords_color_palette
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_app_icon_title,
                subtitleRes = R.string.pref_app_icon_subtitle,
                categoryName = catInterface,
                icon = Icons.Rounded.Apps,
                route = "app_icon_settings",
                keywordsRes = R.string.keywords_app_icon
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_theme_dynamic,
                subtitleRes = R.string.pref_theme_dynamic_sub,
                categoryName = catInterface,
                icon = Icons.Rounded.AutoAwesome,
                route = "appearance_settings",
                highlightKey = "pref_theme_dynamic",
                keywordsRes = R.string.keywords_dynamic_colors,
                keywords = listOf("monet", "material you"),
                hasSwitch = true,
                switchState = dynamicTheme,
                onSwitchChange = {
                    dynamicTheme = it
                    prefs.setDynamicTheme(it)
                    onPreferenceChange()
                }
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_theme_track_dynamic,
                subtitleRes = R.string.pref_theme_track_dynamic_sub,
                categoryName = catInterface,
                icon = Icons.Rounded.Album,
                route = "appearance_settings",
                highlightKey = "pref_theme_track_dynamic",
                keywordsRes = R.string.keywords_track_dynamic,
                hasSwitch = true,
                switchState = trackDynamicTheme,
                onSwitchChange = {
                    trackDynamicTheme = it
                    prefs.setTrackDynamicTheme(it)
                    onPreferenceChange()
                }
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_theme_pure_black,
                subtitleRes = R.string.pref_theme_pure_black_sub,
                categoryName = catInterface,
                icon = Icons.Rounded.Contrast,
                route = "appearance_settings",
                highlightKey = "settings_page_themes",
                keywordsRes = R.string.keywords_pure_black,
                keywords = listOf("amoled", "oled"),
                hasSwitch = true,
                switchState = pureBlack,
                onSwitchChange = {
                    pureBlack = it
                    prefs.setPureBlack(it)
                    onPreferenceChange()
                }
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_show_remaining_time,
                subtitleRes = R.string.pref_show_remaining_time_desc,
                categoryName = catInterface,
                icon = Icons.Rounded.Timer,
                route = "player_design_settings",
                highlightKey = "pref_show_remaining_time",
                keywordsRes = R.string.keywords_remaining_time,
                hasSwitch = true,
                switchState = showRemainingTime,
                onSwitchChange = {
                    showRemainingTime = it
                    prefs.setShowRemainingTime(it)
                    onPreferenceChange()
                }
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_full_player_source_title,
                subtitleRes = R.string.pref_full_player_source_desc,
                categoryName = catInterface,
                icon = Icons.Rounded.GraphicEq,
                route = "player_design_settings",
                highlightKey = "pref_full_player_source",
                keywords = listOf("source", "youtube", "soundcloud", "badge", "provider"),
                hasSwitch = true,
                switchState = fullPlayerSource,
                onSwitchChange = {
                    fullPlayerSource = it
                    prefs.setFullPlayerSourceIndicatorEnabled(it)
                    onPreferenceChange()
                }
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_volume_slider_title,
                subtitleRes = R.string.volume_vertical,
                categoryName = catInterface,
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                keywordsRes = R.string.keywords_volume_slider,
                hasSwitch = true,
                switchState = verticalVolume,
                onSwitchChange = {
                    verticalVolume = it
                    prefs.setVerticalVolumeSlider(it)
                    onPreferenceChange()
                }
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_animated_covers,
                subtitleRes = R.string.pref_animated_covers_desc,
                categoryName = catInterface,
                icon = Icons.Rounded.PlayCircle,
                route = "appearance_settings",
                highlightKey = "pref_animated_covers",
                keywordsRes = R.string.keywords_animated_covers,
                hasSwitch = true,
                switchState = animatedCovers,
                onSwitchChange = {
                    animatedCovers = it
                    prefs.setAnimatedCoversEnabled(it)
                    onPreferenceChange()
                }
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_animated_covers_fade_ui,
                subtitleRes = R.string.pref_animated_covers_fade_ui_desc,
                categoryName = catInterface,
                icon = Icons.Rounded.Opacity,
                route = "appearance_settings",
                highlightKey = "pref_animated_covers_fade_ui",
                keywords = listOf("fade ui", "fondu"),
                hasSwitch = true,
                switchState = animatedCoversFadeUi,
                onSwitchChange = {
                    animatedCoversFadeUi = it
                    prefs.setAnimatedCoversFadeUiEnabled(it)
                    onPreferenceChange()
                }
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_animated_artist_profiles,
                subtitleRes = R.string.pref_animated_artist_profiles_desc,
                categoryName = catInterface,
                icon = Icons.Rounded.AccountCircle,
                route = "appearance_settings",
                highlightKey = "pref_animated_artist_profiles",
                keywordsRes = R.string.keywords_animated_artist_profiles,
                hasSwitch = true,
                switchState = animatedArtistProfiles,
                onSwitchChange = {
                    animatedArtistProfiles = it
                    prefs.setAnimatedArtistProfilesEnabled(it)
                    onPreferenceChange()
                }
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_lyrics_under_cover,
                subtitleRes = R.string.pref_lyrics_under_cover_sub,
                categoryName = catInterface,
                icon = Icons.Rounded.Lyrics,
                route = "appearance_settings",
                highlightKey = "pref_lyrics_under_cover",
                keywordsRes = R.string.keywords_lyrics,
                hasSwitch = true,
                switchState = lyricsUnderCover,
                onSwitchChange = {
                    lyricsUnderCover = it
                    prefs.setLyricsUnderCoverEnabled(it)
                    onPreferenceChange()
                }
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_font_custom_title,
                subtitleRes = R.string.pref_font_custom_subtitle,
                categoryName = catInterface,
                icon = Icons.Rounded.TextFields,
                route = "appearance_settings",
                highlightKey = "pref_font_custom",
                keywordsRes = R.string.keywords_font,
                hasSwitch = true,
                switchState = customFontEnabled,
                onSwitchChange = {
                    customFontEnabled = it
                    prefs.setCustomFontEnabled(it)
                    onPreferenceChange()
                }
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_font_variations_title,
                subtitleRes = R.string.pref_font_variations_subtitle,
                categoryName = catInterface,
                icon = Icons.Rounded.Tune,
                route = "appearance_settings",
                highlightKey = "pref_font_variations",
                keywordsRes = R.string.keywords_font,
                keywords = listOf("weight", "slant", "round")
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_explorer_grid_title,
                subtitleRes = R.string.pref_explorer_grid_subtitle,
                categoryName = catInterface,
                icon = Icons.Rounded.GridView,
                route = "appearance_settings",
                highlightKey = "pref_explorer_grid",
                keywordsRes = R.string.keywords_explorer_grid,
                hasSwitch = true,
                switchState = explorerGridLayout,
                onSwitchChange = {
                    explorerGridLayout = it
                    prefs.setExplorerGridLayout(it)
                    onPreferenceChange()
                }
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_library_category_layout_title,
                subtitleRes = R.string.pref_library_category_layout_subtitle,
                categoryName = catInterface,
                icon = Icons.Rounded.FilterList,
                route = "appearance_settings",
                highlightKey = "pref_library_category_layout",
                keywordsRes = R.string.keywords_library_layout
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_achievement_popups,
                subtitleRes = R.string.pref_achievement_popups_sub,
                categoryName = catInterface,
                icon = Icons.Rounded.EmojiEvents,
                route = "appearance_settings",
                highlightKey = "pref_achievement_popups",
                keywordsRes = R.string.keywords_achievements,
                hasSwitch = true,
                switchState = achievementPopups,
                onSwitchChange = {
                    achievementPopups = it
                    prefs.setAchievementPopupsEnabled(it)
                    onPreferenceChange()
                }
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.achievements_title,
                subtitleRes = R.string.achievements_subtitle,
                categoryName = catInterface,
                icon = Icons.Rounded.EmojiEvents,
                route = "achievements",
                keywordsRes = R.string.keywords_achievements
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.listening_stats_title,
                subtitleRes = R.string.listening_stats_subtitle,
                categoryName = catInterface,
                icon = Icons.Rounded.Insights,
                route = "listening_stats",
                keywordsRes = R.string.keywords_stats,
                keywords = listOf("wrapped", "recap")
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.listening_stats_title,
                subtitleRes = R.string.pref_home_listening_stats_desc,
                categoryName = catInterface,
                icon = Icons.Rounded.Analytics,
                route = "appearance_settings",
                highlightKey = "pref_home_listening_stats",
                keywordsRes = R.string.keywords_stats,
                hasSwitch = true,
                switchState = showHomeListeningStats,
                onSwitchChange = {
                    showHomeListeningStats = it
                    prefs.setShowHomeListeningStats(it)
                    onPreferenceChange()
                }
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.mix_title,
                subtitleRes = R.string.pref_home_your_mix_desc,
                categoryName = catInterface,
                icon = Icons.Rounded.AutoMode,
                route = "appearance_settings",
                highlightKey = "pref_home_your_mix",
                keywordsRes = R.string.keywords_your_mix,
                keywords = listOf("vibe", "mix"),
                hasSwitch = true,
                switchState = showHomeYourMix,
                onSwitchChange = {
                    showHomeYourMix = it
                    prefs.setShowHomeYourMix(it)
                    onPreferenceChange()
                }
            ),

            // AUDIO
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_audio_title,
                subtitleRes = R.string.pref_audio_subtitle,
                categoryName = catAudio,
                icon = Icons.Rounded.GraphicEq,
                route = "audio_settings",
                keywordsRes = R.string.keywords_audio,
                keywords = listOf("bitrate", "gain")
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.equalizer_title,
                subtitleRes = R.string.equalizer_subtitle,
                categoryName = catAudio,
                icon = Icons.Rounded.Tune,
                route = "audio_quality_settings",
                highlightKey = "equalizer",
                keywordsRes = R.string.keywords_equalizer,
                keywords = listOf("eq", "bass", "treble")
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_norm_title,
                subtitleRes = R.string.pref_norm_sub,
                categoryName = catAudio,
                icon = Icons.Rounded.VolumeUp,
                route = "audio_quality_settings",
                highlightKey = "pref_norm",
                keywordsRes = R.string.keywords_volume_normalization,
                keywords = listOf("replaygain", "lufs")
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_audio_mono,
                subtitleRes = R.string.pref_audio_mono_sub,
                categoryName = catAudio,
                icon = Icons.Rounded.Headphones,
                route = "audio_quality_settings",
                highlightKey = "pref_audio_mono",
                keywordsRes = R.string.keywords_audio_mono
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_haptics_title,
                subtitleRes = R.string.pref_haptics_subtitle,
                categoryName = catAudio,
                icon = Icons.Rounded.Vibration,
                route = "haptic_settings",
                keywordsRes = R.string.keywords_haptics
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_haptics_enable,
                subtitleRes = R.string.pref_haptics_enable_sub,
                categoryName = catAudio,
                icon = Icons.Rounded.Vibration,
                route = "haptic_settings",
                highlightKey = "pref_haptics",
                keywordsRes = R.string.keywords_haptics,
                hasSwitch = true,
                switchState = playerViewModel.isHapticsEnabled,
                onSwitchChange = { playerViewModel.toggleHaptics(it) }
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_crossfade_title,
                subtitleRes = R.string.pref_crossfade_sub,
                categoryName = catAudio,
                icon = Icons.Rounded.LinearScale,
                route = "audio_transitions_settings",
                highlightKey = "pref_crossfade",
                keywordsRes = R.string.keywords_crossfade,
                hasSwitch = true,
                switchState = crossfade,
                onSwitchChange = {
                    crossfade = it
                    prefs.setCrossfadeEnabled(it)
                    onPreferenceChange()
                }
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_autoplay,
                subtitleRes = R.string.pref_autoplay_sub,
                categoryName = catAudio,
                icon = Icons.Rounded.PlayArrow,
                route = "audio_playback_settings",
                highlightKey = "pref_autoplay",
                keywordsRes = R.string.keywords_autoplay,
                hasSwitch = true,
                switchState = autoplay,
                onSwitchChange = {
                    autoplay = it
                    prefs.setAutoplayEnabled(it)
                    onPreferenceChange()
                }
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.automix,
                subtitleRes = R.string.automix_desc,
                categoryName = catAudio,
                icon = Icons.Rounded.AutoMode,
                route = "audio_transitions_settings",
                highlightKey = "pref_automix",
                keywordsRes = R.string.keywords_automix,
                keywords = listOf("dj", "dj flow"),
                hasSwitch = true,
                switchState = automix,
                onSwitchChange = {
                    automix = it
                    prefs.setAutomixEnabled(it)
                    onPreferenceChange()
                }
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_stop_on_task_clear,
                subtitleRes = null,
                categoryName = catAudio,
                icon = Icons.Rounded.Cancel,
                route = "audio_playback_settings",
                highlightKey = "pref_stop_on_task_clear",
                keywordsRes = R.string.keywords_stop_on_task_clear,
                hasSwitch = true,
                switchState = stopOnTaskClear,
                onSwitchChange = {
                    stopOnTaskClear = it
                    prefs.setStopOnTaskClear(it)
                    onPreferenceChange()
                }
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_persist_queue,
                subtitleRes = R.string.pref_persist_queue_sub,
                categoryName = catAudio,
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                route = "audio_playback_settings",
                highlightKey = "pref_persist_queue",
                keywordsRes = R.string.keywords_persist_queue,
                hasSwitch = true,
                switchState = persistentQueue,
                onSwitchChange = {
                    persistentQueue = it
                    prefs.setPersistentQueueEnabled(it)
                    onPreferenceChange()
                }
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_save_position,
                subtitleRes = R.string.pref_save_position_sub,
                categoryName = catAudio,
                icon = Icons.Rounded.Restore,
                route = "audio_playback_settings",
                highlightKey = "pref_save_position",
                keywordsRes = R.string.keywords_save_position,
                hasSwitch = true,
                switchState = savePosition,
                onSwitchChange = {
                    savePosition = it
                    prefs.setSavePositionEnabled(it)
                    onPreferenceChange()
                }
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.sleep_timer_fade_title,
                subtitleRes = null,
                categoryName = catAudio,
                icon = Icons.Rounded.Bedtime,
                route = "audio_sleep_settings",
                highlightKey = "sleep_timer_fade",
                keywordsRes = R.string.keywords_sleep_timer
            ),

            // SOURCES
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_accounts_title,
                subtitleRes = R.string.pref_accounts_subtitle,
                categoryName = catSources,
                icon = Icons.Rounded.ImportExport,
                route = "accounts_settings",
                keywordsRes = R.string.keywords_accounts,
                keywords = listOf("spotify", "soundcloud", "vk", "tidal", "deezer", "qobuz")
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_account_soundcloud_title,
                subtitleRes = null,
                categoryName = catSources,
                iconRes = R.drawable.ic_soundcloud,
                route = "accounts_settings",
                highlightKey = "pref_account_soundcloud",
                keywords = listOf("soundcloud", "sc", "login")
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_account_vk_title,
                subtitleRes = null,
                categoryName = catSources,
                iconRes = R.drawable.ic_vk,
                route = "accounts_settings",
                highlightKey = "pref_account_vk",
                keywords = listOf("vk", "vkontakte", "vk music", "login")
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_discord_title,
                subtitleRes = null,
                categoryName = catSources,
                iconRes = R.drawable.ic_discord,
                route = "accounts_settings",
                highlightKey = "pref_discord",
                keywords = listOf("discord", "rpc", "presence")
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.qobuz_integration,
                subtitleRes = null,
                categoryName = catSources,
                iconRes = R.drawable.ic_logo_qobuz,
                route = "accounts_settings",
                highlightKey = "pref_qobuz",
                keywords = listOf("qobuz", "flac", "hi-res")
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.tidal_integration,
                subtitleRes = null,
                categoryName = catSources,
                iconRes = R.drawable.ic_logo_tidal,
                route = "accounts_settings",
                highlightKey = "pref_tidal",
                keywords = listOf("tidal", "hifi", "lossless", "master")
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.deezer_integration,
                subtitleRes = null,
                categoryName = catSources,
                iconRes = R.drawable.ic_logo_deezer,
                route = "accounts_settings",
                highlightKey = "pref_deezer",
                keywords = listOf("deezer", "mp3", "flac")
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.provider_order,
                subtitleRes = R.string.pref_accounts_subtitle,
                categoryName = catSources,
                icon = Icons.Rounded.Tune,
                route = "accounts_settings",
                highlightKey = "pref_provider_order",
                keywordsRes = R.string.keywords_accounts
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_youtube_fallback,
                subtitleRes = R.string.pref_youtube_fallback_sub,
                categoryName = catSources,
                icon = Icons.Rounded.SmartDisplay,
                route = "audio_playback_settings",
                highlightKey = "pref_youtube_fallback",
                keywords = listOf("youtube", "youtube music", "fallback"),
                hasSwitch = true,
                switchState = youtubeFallback,
                onSwitchChange = {
                    youtubeFallback = it
                    prefs.setYouTubeFallbackEnabled(it)
                    onPreferenceChange()
                }
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_hide_youtube_videos,
                subtitleRes = R.string.pref_hide_youtube_videos_sub,
                categoryName = catSources,
                icon = Icons.Rounded.SmartDisplay,
                route = "audio_playback_settings",
                highlightKey = "pref_hide_youtube_videos",
                keywords = listOf("youtube", "videos", "shorts"),
                hasSwitch = true,
                switchState = hideYoutubeVideos,
                onSwitchChange = {
                    hideYoutubeVideos = it
                    prefs.setHideYoutubeVideos(it)
                    onPreferenceChange()
                }
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.discord_rpc_title,
                subtitleRes = R.string.discord_enable_rpc_desc,
                categoryName = catSources,
                icon = Icons.AutoMirrored.Rounded.Chat,
                route = "accounts_settings",
                highlightKey = "pref_discord",
                keywords = listOf("discord", "rpc", "presence"),
                hasSwitch = true,
                switchState = discordRpc,
                onSwitchChange = {
                    discordRpc = it
                    prefs.setDiscordRpcEnabled(it)
                    onPreferenceChange()
                }
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.data_saver_title,
                subtitleRes = R.string.data_saver_sub,
                categoryName = catSources,
                icon = Icons.Rounded.DataSaverOn,
                route = "accounts_settings",
                highlightKey = "pref_data_saver",
                keywordsRes = R.string.keywords_data_saver,
                keywords = listOf("eco", "data", "saver", "traffic", "metered"),
                hasSwitch = true,
                switchState = dataSaver,
                onSwitchChange = {
                    dataSaver = it
                    prefs.setDataSaverEnabled(it)
                    onPreferenceChange()
                }
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_quality,
                subtitleRes = R.string.quality_high_sub,
                categoryName = catSources,
                icon = Icons.Rounded.HighQuality,
                route = "accounts_settings",
                highlightKey = "pref_quality",
                keywordsRes = R.string.keywords_audio,
                keywords = listOf("bitrate", "flac", "stream", "quality")
            ),

            // STORAGE
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_storage_title,
                subtitleRes = R.string.pref_storage_subtitle,
                categoryName = catStorage,
                icon = Icons.Rounded.Storage,
                route = "storage",
                keywordsRes = R.string.keywords_storage
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_local_title,
                subtitleRes = R.string.pref_local_subtitle,
                categoryName = catStorage,
                icon = Icons.Rounded.SdStorage,
                route = "local_media_settings",
                keywordsRes = R.string.keywords_local_media,
                keywords = listOf("mp3", "sd card")
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_backup_title,
                subtitleRes = R.string.pref_backup_subtitle,
                categoryName = catStorage,
                icon = Icons.Rounded.Backup,
                route = "backup_restore",
                keywordsRes = R.string.keywords_backup
            ),

            // SYNC
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.sync_title,
                subtitleRes = R.string.sync_intro,
                categoryName = catSync,
                icon = Icons.Rounded.Devices,
                route = "sync_settings",
                keywordsRes = R.string.keywords_sync,
                keywords = listOf("qr code")
            ),

            // NETWORK
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_proxy_title,
                subtitleRes = R.string.pref_proxy_subtitle,
                categoryName = catNetwork,
                icon = Icons.Rounded.Dns,
                route = "proxy_settings",
                keywordsRes = R.string.keywords_proxy,
                keywords = listOf("ip", "port", "socks", "http", "dns", "vpn")
            ),

            // MISC
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.settings_cat_general,
                subtitleRes = R.string.settings_cat_general_sub,
                categoryName = catMisc,
                icon = Icons.Rounded.Tune,
                route = "misc_general_settings",
                keywordsRes = R.string.keywords_language
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_language,
                subtitleRes = null,
                categoryName = catMisc,
                icon = Icons.Rounded.Translate,
                route = "misc_general_settings",
                highlightKey = "pref_language",
                keywordsRes = R.string.keywords_language
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_start_screen,
                subtitleRes = null,
                categoryName = catMisc,
                icon = Icons.Rounded.Home,
                route = "misc_general_settings",
                highlightKey = "pref_start_screen",
                keywordsRes = R.string.keywords_start_screen
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_auto_update,
                subtitleRes = R.string.pref_auto_update_sub,
                categoryName = catMisc,
                icon = Icons.Rounded.SystemUpdate,
                route = "misc_general_settings",
                highlightKey = "pref_auto_update",
                keywordsRes = R.string.keywords_auto_update,
                hasSwitch = true,
                switchState = autoUpdate,
                onSwitchChange = {
                    autoUpdate = it
                    prefs.setAutoUpdateEnabled(it)
                    onPreferenceChange()
                }
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.beta_updates_title,
                subtitleRes = R.string.beta_updates_subtitle,
                categoryName = catMisc,
                icon = Icons.Rounded.Science,
                route = "misc_general_settings",
                highlightKey = "pref_beta_updates",
                keywords = listOf("beta", "bêta", "pre-release", "prerelease", "test", "preview"),
                hasSwitch = true,
                switchState = betaUpdates,
                onSwitchChange = {
                    betaUpdates = it
                    prefs.setBetaUpdatesEnabled(it)
                    onPreferenceChange()
                }
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_remember_search_filter,
                subtitleRes = R.string.pref_remember_search_filter_sub,
                categoryName = catMisc,
                icon = Icons.Rounded.FilterList,
                route = "misc_general_settings",
                highlightKey = "pref_remember_search_filter",
                keywords = listOf("filter", "filtre"),
                hasSwitch = true,
                switchState = rememberSearchFilter,
                onSwitchChange = {
                    rememberSearchFilter = it
                    prefs.setRememberSearchFilter(it)
                    onPreferenceChange()
                }
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_show_playlist_total_duration,
                subtitleRes = R.string.pref_show_playlist_total_duration_sub,
                categoryName = catMisc,
                icon = Icons.Rounded.Schedule,
                route = "misc_general_settings",
                highlightKey = "pref_show_playlist_total_duration",
                keywords = listOf("duration", "playtime", "playlist", "duree", "temps"),
                hasSwitch = true,
                switchState = showPlaylistTotalDuration,
                onSwitchChange = {
                    showPlaylistTotalDuration = it
                    prefs.setShowPlaylistTotalDuration(it)
                    onPreferenceChange()
                }
            ),
            createSearchEntry(
                context = context,
                englishContext = englishContext,
                titleRes = R.string.pref_about_title,
                subtitleRes = R.string.pref_about_subtitle,
                categoryName = catMisc,
                icon = Icons.Rounded.Info,
                route = "about",
                keywords = listOf("github", "about", "version")
            )
        )

        val dynamicItems = SettingsRegistry.allDefinitions.map { def ->
            def.toSearchSettingEntry(context, prefs, navController, englishContext) {
                onPreferenceChange()
            }
        }
        staticItems + dynamicItems
    }
}
