package com.alananasss.kittytune.ui.profile

import android.content.Context
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
        var verticalVolume = prefs.getVerticalVolumeSlider()
        var crossfade = prefs.getCrossfadeEnabled()
        var automix = prefs.getAutomixEnabled()
        var autoplay = prefs.getAutoplayEnabled()
        var stopOnTaskClear = prefs.getStopOnTaskClear()
        var persistentQueue = prefs.getPersistentQueueEnabled()
        var savePosition = prefs.getSavePositionEnabled()
        var youtubeFallback = prefs.getYouTubeFallbackEnabled()
        var discordRpc = prefs.getDiscordRpcEnabled()
        var achievementPopups = prefs.getAchievementPopupsEnabled()
        var autoUpdate = prefs.getAutoUpdateEnabled()
        var rememberSearchFilter = prefs.getRememberSearchFilter()
        var customFontEnabled = prefs.getCustomFontEnabled()
        var explorerGridLayout = prefs.getExplorerGridLayout()

        val staticItems = listOf(
            // INTERFACE
            SearchSettingEntry(
                title = context.getString(R.string.settings_page_themes),
                subtitle = context.getString(R.string.settings_page_themes_sub),
                categoryName = catInterface,
                icon = Icons.Rounded.ColorLens,
                route = "appearance_settings",
                keywords = listOf("theme", "couleur", "sombre", "clair", "amoled", "oled", "palette", "ocean", "forest", "sunset", "rose", "lavande", "menthe", "dark", "light", "colors", "apparence")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.settings_page_player),
                subtitle = context.getString(R.string.settings_page_player_sub),
                categoryName = catInterface,
                icon = Icons.Rounded.PlayCircle,
                route = "player_design_settings",
                keywords = listOf("lecteur", "player", "silhouette", "curseur", "slider", "wavy", "slim", "squiggly", "bar", "volume", "boutons", "menu", "morceau", "playlist", "sheet", "trois petits points", "dock", "flottant", "dj flow", "dj")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_bottom_menu_title),
                subtitle = context.getString(R.string.pref_bottom_menu_subtitle),
                categoryName = catInterface,
                icon = Icons.AutoMirrored.Rounded.ViewSidebar,
                route = "bottom_bar_settings",
                keywords = listOf("barre", "navigation", "onglets", "fab", "menu du bas", "bottom bar", "tabs", "personnaliser barre")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_bottom_menu_fab),
                subtitle = context.getString(R.string.pref_bottom_menu_subtitle),
                categoryName = catInterface,
                icon = Icons.Rounded.Add,
                route = "fab_settings",
                keywords = listOf("fab", "bouton flottant", "floating action button", "raccourci", "action flottante", "bouton bas")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_lyrics_title),
                subtitle = context.getString(R.string.settings_page_lyrics_sub),
                categoryName = catInterface,
                icon = Icons.Rounded.Lyrics,
                route = "lyrics_settings",
                keywords = listOf("paroles", "lyrics", "karaoke", "synchro", "texte", "chanson", "fournisseur", "traduction", "police paroles")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_color_palette_title),
                subtitle = context.getString(R.string.pref_color_palette_subtitle),
                categoryName = catInterface,
                icon = Icons.Rounded.Palette,
                route = "color_palette",
                keywords = listOf("palette", "couleur personnalisee", "accent", "custom color", "teinte")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_app_icon_title),
                subtitle = context.getString(R.string.pref_app_icon_subtitle),
                categoryName = catInterface,
                icon = Icons.Rounded.Apps,
                route = "app_icon_settings",
                keywords = listOf("icone", "app icon", "logo", "visuel", "icone application")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_theme_dynamic),
                subtitle = context.getString(R.string.pref_theme_dynamic_sub),
                categoryName = catInterface,
                icon = Icons.Rounded.AutoAwesome,
                route = "appearance_settings",
                highlightKey = "pref_theme_dynamic",
                keywords = listOf("dynamic", "couleurs dynamiques", "papier peint", "wallpaper", "monet", "material you"),
                hasSwitch = true,
                switchState = dynamicTheme,
                onSwitchChange = {
                    dynamicTheme = it
                    prefs.setDynamicTheme(it)
                    onPreferenceChange()
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_theme_track_dynamic),
                subtitle = context.getString(R.string.pref_theme_track_dynamic_sub),
                categoryName = catInterface,
                icon = Icons.Rounded.Album,
                route = "appearance_settings",
                highlightKey = "pref_theme_track_dynamic",
                keywords = listOf("pochette", "album art", "cover color", "track dynamic", "couleur morceau"),
                hasSwitch = true,
                switchState = trackDynamicTheme,
                onSwitchChange = {
                    trackDynamicTheme = it
                    prefs.setTrackDynamicTheme(it)
                    onPreferenceChange()
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_theme_pure_black),
                subtitle = context.getString(R.string.pref_theme_pure_black_sub),
                categoryName = catInterface,
                icon = Icons.Rounded.Contrast,
                route = "appearance_settings",
                highlightKey = "settings_page_themes",
                keywords = listOf("noir pur", "pure black", "amoled", "oled", "true black"),
                hasSwitch = true,
                switchState = pureBlack,
                onSwitchChange = {
                    pureBlack = it
                    prefs.setPureBlack(it)
                    onPreferenceChange()
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_show_remaining_time),
                subtitle = context.getString(R.string.pref_show_remaining_time_desc),
                categoryName = catInterface,
                icon = Icons.Rounded.Timer,
                route = "player_design_settings",
                highlightKey = "pref_show_remaining_time",
                keywords = listOf("temps restant", "remaining time", "countdown", "-00:14", "duree", "decompte"),
                hasSwitch = true,
                switchState = showRemainingTime,
                onSwitchChange = {
                    showRemainingTime = it
                    prefs.setShowRemainingTime(it)
                    onPreferenceChange()
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_volume_slider_title),
                subtitle = context.getString(R.string.volume_vertical),
                categoryName = catInterface,
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                keywords = listOf("volume", "curseur volume", "vertical", "horizontal", "slider", "curseur"),
                hasSwitch = true,
                switchState = verticalVolume,
                onSwitchChange = {
                    verticalVolume = it
                    prefs.setVerticalVolumeSlider(it)
                    onPreferenceChange()
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_animated_covers),
                subtitle = context.getString(R.string.pref_animated_covers_desc),
                categoryName = catInterface,
                icon = Icons.Rounded.PlayCircle,
                route = "appearance_settings",
                highlightKey = "pref_animated_covers",
                keywords = listOf("pochettes animees", "animated covers", "video cover", "pochette video"),
                hasSwitch = true,
                switchState = animatedCovers,
                onSwitchChange = {
                    animatedCovers = it
                    prefs.setAnimatedCoversEnabled(it)
                    onPreferenceChange()
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_animated_covers_fade_ui),
                subtitle = context.getString(R.string.pref_animated_covers_fade_ui_desc),
                categoryName = catInterface,
                icon = Icons.Rounded.Opacity,
                route = "appearance_settings",
                highlightKey = "pref_animated_covers_fade_ui",
                keywords = listOf("fondu", "fade ui", "masquer controles", "interface fondu"),
                hasSwitch = true,
                switchState = animatedCoversFadeUi,
                onSwitchChange = {
                    animatedCoversFadeUi = it
                    prefs.setAnimatedCoversFadeUiEnabled(it)
                    onPreferenceChange()
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_animated_artist_profiles),
                subtitle = context.getString(R.string.pref_animated_artist_profiles_desc),
                categoryName = catInterface,
                icon = Icons.Rounded.AccountCircle,
                route = "appearance_settings",
                highlightKey = "pref_animated_artist_profiles",
                keywords = listOf("profils artistes", "artiste anime", "artist video", "banniere animee"),
                hasSwitch = true,
                switchState = animatedArtistProfiles,
                onSwitchChange = {
                    animatedArtistProfiles = it
                    prefs.setAnimatedArtistProfilesEnabled(it)
                    onPreferenceChange()
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_lyrics_under_cover),
                subtitle = context.getString(R.string.pref_lyrics_under_cover_sub),
                categoryName = catInterface,
                icon = Icons.Rounded.Lyrics,
                route = "appearance_settings",
                highlightKey = "pref_lyrics_under_cover",
                keywords = listOf("paroles sous la pochette", "lyrics under cover", "paroles lecteur"),
                hasSwitch = true,
                switchState = lyricsUnderCover,
                onSwitchChange = {
                    lyricsUnderCover = it
                    prefs.setLyricsUnderCoverEnabled(it)
                    onPreferenceChange()
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_font_custom_title),
                subtitle = context.getString(R.string.pref_font_custom_subtitle),
                categoryName = catInterface,
                icon = Icons.Rounded.TextFields,
                route = "appearance_settings",
                highlightKey = "pref_font_custom",
                keywords = listOf("police", "font", "typographie", "custom font", "texte", "police personnalisee"),
                hasSwitch = true,
                switchState = customFontEnabled,
                onSwitchChange = {
                    customFontEnabled = it
                    prefs.setCustomFontEnabled(it)
                    onPreferenceChange()
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_font_variations_title),
                subtitle = context.getString(R.string.pref_font_variations_subtitle),
                categoryName = catInterface,
                icon = Icons.Rounded.Tune,
                route = "appearance_settings",
                highlightKey = "pref_font_variations",
                keywords = listOf("variations de police", "font variations", "epaisseur", "weight", "slant", "round", "graisse")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_explorer_grid_title),
                subtitle = context.getString(R.string.pref_explorer_grid_subtitle),
                categoryName = catInterface,
                icon = Icons.Rounded.GridView,
                route = "appearance_settings",
                highlightKey = "pref_explorer_grid",
                keywords = listOf("explorer grille", "grille exploration", "explorer grid", "affichage grille"),
                hasSwitch = true,
                switchState = explorerGridLayout,
                onSwitchChange = {
                    explorerGridLayout = it
                    prefs.setExplorerGridLayout(it)
                    onPreferenceChange()
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_library_category_layout_title),
                subtitle = context.getString(R.string.pref_library_category_layout_subtitle),
                categoryName = catInterface,
                icon = Icons.Rounded.FilterList,
                route = "appearance_settings",
                highlightKey = "pref_library_category_layout",
                keywords = listOf("bibliotheque", "library", "filtres", "filtre", "categories", "layout", "disposition", "desktop", "bureau", "bouton", "playlists", "albums", "artistes", "stations", "all", "tout")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_achievement_popups),
                subtitle = context.getString(R.string.pref_achievement_popups_sub),
                categoryName = catInterface,
                icon = Icons.Rounded.EmojiEvents,
                route = "appearance_settings",
                highlightKey = "pref_achievement_popups",
                keywords = listOf("succes", "achievement", "popups", "trophees", "notifications de succes"),
                hasSwitch = true,
                switchState = achievementPopups,
                onSwitchChange = {
                    achievementPopups = it
                    prefs.setAchievementPopupsEnabled(it)
                    onPreferenceChange()
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.achievements_title),
                subtitle = null,
                categoryName = catInterface,
                icon = Icons.Rounded.EmojiEvents,
                route = "achievements",
                keywords = listOf("succes", "achievements", "trophees", "recompenses", "badges")
            ),

            // AUDIO
            SearchSettingEntry(
                title = context.getString(R.string.pref_audio_title),
                subtitle = context.getString(R.string.pref_audio_subtitle),
                categoryName = catAudio,
                icon = Icons.Rounded.GraphicEq,
                route = "audio_settings",
                keywords = listOf("audio", "qualite", "egaliseur", "equalizer", "normalisation", "gain", "bitrate", "parametres audio")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_quality),
                subtitle = context.getString(R.string.quality_high_sub),
                categoryName = catAudio,
                icon = Icons.Rounded.GraphicEq,
                route = "audio_settings",
                highlightKey = "pref_quality",
                keywords = listOf("qualite", "qualite audio", "stream quality", "audio quality", "bitrate", "high", "low", "haute qualite", "debit")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.equalizer_title),
                subtitle = if (playerViewModel.equalizerState.isEnabled) {
                    playerViewModel.equalizerState.selectedPreset
                } else {
                    context.getString(R.string.equalizer_subtitle)
                },
                categoryName = catAudio,
                icon = Icons.Rounded.Equalizer,
                route = "audio_settings",
                highlightKey = "equalizer",
                keywords = listOf("equalizer", "egaliseur", "eq", "preamp", "preset", "bass", "treble", "frequence", "son", "16-band")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_norm_title),
                subtitle = context.getString(R.string.pref_norm_sub),
                categoryName = catAudio,
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                route = "audio_settings",
                highlightKey = "pref_norm",
                keywords = listOf("normalisation", "volume", "replaygain", "lufs", "gain", "loudness", "egalisation volume")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_audio_mono),
                subtitle = context.getString(R.string.pref_audio_mono_sub),
                categoryName = catAudio,
                icon = Icons.AutoMirrored.Rounded.VolumeDown,
                route = "audio_settings",
                highlightKey = "pref_audio_mono",
                keywords = listOf("mono", "audio mono", "stereo", "canaux"),
                hasSwitch = true,
                switchState = playerViewModel.effectsState.isMonoEnabled,
                onSwitchChange = { playerViewModel.toggleMono() }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_haptics_title),
                subtitle = context.getString(R.string.pref_haptics_subtitle),
                categoryName = catAudio,
                icon = Icons.Rounded.Vibration,
                route = "haptic_settings",
                keywords = listOf("vibration", "haptic", "retour haptique", "touch", "retours haptiques")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_haptics_enable),
                subtitle = context.getString(R.string.pref_haptics_enable_sub),
                categoryName = catAudio,
                icon = Icons.Rounded.Vibration,
                route = "audio_settings",
                highlightKey = "pref_haptics",
                keywords = listOf("haptique", "vibrations", "music haptics", "retour haptique", "activer haptique"),
                hasSwitch = true,
                switchState = playerViewModel.isHapticsEnabled,
                onSwitchChange = { playerViewModel.toggleHaptics(it) }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_crossfade_title),
                subtitle = context.getString(R.string.pref_crossfade_sub),
                categoryName = catAudio,
                icon = Icons.Rounded.LinearScale,
                route = "audio_settings",
                highlightKey = "pref_crossfade",
                keywords = listOf("crossfade", "fondu enchaine", "transition", "fondu"),
                hasSwitch = true,
                switchState = crossfade,
                onSwitchChange = {
                    crossfade = it
                    prefs.setCrossfadeEnabled(it)
                    onPreferenceChange()
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_autoplay),
                subtitle = context.getString(R.string.pref_autoplay_sub),
                categoryName = catAudio,
                icon = Icons.Rounded.PlayArrow,
                route = "audio_settings",
                highlightKey = "pref_autoplay",
                keywords = listOf("autoplay", "lecture automatique", "suite", "recommandation"),
                hasSwitch = true,
                switchState = autoplay,
                onSwitchChange = {
                    autoplay = it
                    prefs.setAutoplayEnabled(it)
                    onPreferenceChange()
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.automix),
                subtitle = context.getString(R.string.automix_desc),
                categoryName = catAudio,
                icon = Icons.Rounded.AutoMode,
                route = "audio_settings",
                highlightKey = "pref_automix",
                keywords = listOf("automix", "enchainement", "dj", "dj flow", "flow", "mix", "transition", "tempo", "smart mix"),
                hasSwitch = true,
                switchState = automix,
                onSwitchChange = {
                    automix = it
                    prefs.setAutomixEnabled(it)
                    onPreferenceChange()
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_stop_on_task_clear),
                subtitle = null,
                categoryName = catAudio,
                icon = Icons.Rounded.Cancel,
                route = "audio_settings",
                highlightKey = "pref_stop_on_task_clear",
                keywords = listOf("arreter", "fermeture", "stop on task clear", "quitter", "tache", "kill", "arreter musique a la fermeture", "app close"),
                hasSwitch = true,
                switchState = stopOnTaskClear,
                onSwitchChange = {
                    stopOnTaskClear = it
                    prefs.setStopOnTaskClear(it)
                    onPreferenceChange()
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_persist_queue),
                subtitle = context.getString(R.string.pref_persist_queue_sub),
                categoryName = catAudio,
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                route = "audio_settings",
                highlightKey = "pref_persist_queue",
                keywords = listOf("file d'attente", "queue", "memoriser file", "persist queue"),
                hasSwitch = true,
                switchState = persistentQueue,
                onSwitchChange = {
                    persistentQueue = it
                    prefs.setPersistentQueueEnabled(it)
                    onPreferenceChange()
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_save_position),
                subtitle = context.getString(R.string.pref_save_position_sub),
                categoryName = catAudio,
                icon = Icons.Rounded.Restore,
                route = "audio_settings",
                highlightKey = "pref_save_position",
                keywords = listOf("reprendre lecture", "position de lecture", "save position", "memoriser position", "resume", "reprise"),
                hasSwitch = true,
                switchState = savePosition,
                onSwitchChange = {
                    savePosition = it
                    prefs.setSavePositionEnabled(it)
                    onPreferenceChange()
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.sleep_timer_fade_title),
                subtitle = null,
                categoryName = catAudio,
                icon = Icons.Rounded.Bedtime,
                route = "audio_settings",
                highlightKey = "sleep_timer_fade",
                keywords = listOf("minuteur de sommeil", "sleep timer", "fondu sommeil", "minuterie", "fade")
            ),

            // SOURCES
            SearchSettingEntry(
                title = context.getString(R.string.pref_accounts_title),
                subtitle = context.getString(R.string.pref_accounts_subtitle),
                categoryName = catSources,
                icon = Icons.Rounded.ImportExport,
                route = "accounts_settings",
                keywords = listOf("comptes", "sources", "spotify", "soundcloud", "vk", "tidal", "deezer", "qobuz", "connexions")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_account_soundcloud_title),
                subtitle = null,
                categoryName = catSources,
                iconRes = R.drawable.ic_soundcloud,
                route = "accounts_settings",
                highlightKey = "pref_account_soundcloud",
                keywords = listOf("soundcloud", "sc", "compte soundcloud", "stream", "login")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_account_vk_title),
                subtitle = null,
                categoryName = catSources,
                iconRes = R.drawable.ic_vk,
                route = "accounts_settings",
                highlightKey = "pref_account_vk",
                keywords = listOf("vk", "vkontakte", "vk music", "compte vk", "login")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_discord_title),
                subtitle = null,
                categoryName = catSources,
                iconRes = R.drawable.ic_discord,
                route = "accounts_settings",
                highlightKey = "pref_discord",
                keywords = listOf("discord", "rpc", "presence", "rich presence", "statut", "compte discord")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.qobuz_integration),
                subtitle = null,
                categoryName = catSources,
                iconRes = R.drawable.ic_logo_qobuz,
                route = "accounts_settings",
                highlightKey = "pref_qobuz",
                keywords = listOf("qobuz", "flac", "hi-res", "source qobuz", "haute resolution")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.tidal_integration),
                subtitle = null,
                categoryName = catSources,
                iconRes = R.drawable.ic_logo_tidal,
                route = "accounts_settings",
                highlightKey = "pref_tidal",
                keywords = listOf("tidal", "hifi", "lossless", "master", "source tidal")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.deezer_integration),
                subtitle = null,
                categoryName = catSources,
                iconRes = R.drawable.ic_logo_deezer,
                route = "accounts_settings",
                highlightKey = "pref_deezer",
                keywords = listOf("deezer", "mp3", "flac", "source deezer")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.provider_order),
                subtitle = context.getString(R.string.pref_accounts_subtitle),
                categoryName = catSources,
                icon = Icons.Rounded.Tune,
                route = "accounts_settings",
                highlightKey = "pref_provider_order",
                keywords = listOf("ordre sources", "fournisseurs", "priorite", "stream", "provider order")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_youtube_fallback),
                subtitle = context.getString(R.string.pref_youtube_fallback_sub),
                categoryName = catSources,
                icon = Icons.Rounded.SmartDisplay,
                route = "audio_settings",
                highlightKey = "pref_youtube_fallback",
                keywords = listOf("youtube fallback", "repli youtube", "secours", "youtube"),
                hasSwitch = true,
                switchState = youtubeFallback,
                onSwitchChange = {
                    youtubeFallback = it
                    prefs.setYouTubeFallbackEnabled(it)
                    onPreferenceChange()
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.discord_rpc_title),
                subtitle = context.getString(R.string.discord_enable_rpc_desc),
                categoryName = catSources,
                icon = Icons.AutoMirrored.Rounded.Chat,
                route = "accounts_settings",
                highlightKey = "pref_discord",
                keywords = listOf("discord", "presence", "rpc", "statut", "rich presence"),
                hasSwitch = true,
                switchState = discordRpc,
                onSwitchChange = {
                    discordRpc = it
                    prefs.setDiscordRpcEnabled(it)
                    onPreferenceChange()
                }
            ),

            // STORAGE
            SearchSettingEntry(
                title = context.getString(R.string.pref_storage_title),
                subtitle = context.getString(R.string.pref_storage_subtitle),
                categoryName = catStorage,
                icon = Icons.Rounded.Storage,
                route = "storage",
                keywords = listOf("stockage", "cache", "vider", "memoire", "disque", "nettoyer", "storage")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_local_title),
                subtitle = context.getString(R.string.pref_local_subtitle),
                categoryName = catStorage,
                icon = Icons.Rounded.SdStorage,
                route = "local_media_settings",
                keywords = listOf("fichiers locaux", "dossiers", "sd card", "musique locale", "mp3", "scan")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_backup_title),
                subtitle = context.getString(R.string.pref_backup_subtitle),
                categoryName = catStorage,
                icon = Icons.Rounded.Backup,
                route = "backup_restore",
                keywords = listOf("sauvegarde", "restauration", "backup", "restore", "exporter", "importer")
            ),

            // SYNC
            SearchSettingEntry(
                title = context.getString(R.string.sync_title),
                subtitle = context.getString(R.string.sync_intro),
                categoryName = catSync,
                icon = Icons.Rounded.Devices,
                route = "sync_settings",
                keywords = listOf("sync", "synchronisation", "appareils", "appairage", "qr code", "connexion", "devices")
            ),

            // NETWORK
            SearchSettingEntry(
                title = context.getString(R.string.pref_proxy_title),
                subtitle = context.getString(R.string.pref_proxy_subtitle),
                categoryName = catNetwork,
                icon = Icons.Rounded.Dns,
                route = "proxy_settings",
                keywords = listOf("proxy", "reseau", "ip", "port", "socks", "http", "dns", "vpn", "network")
            ),

            // MISC
            SearchSettingEntry(
                title = context.getString(R.string.settings_cat_general),
                subtitle = context.getString(R.string.settings_cat_general_sub),
                categoryName = catMisc,
                icon = Icons.Rounded.Tune,
                route = "misc_settings",
                keywords = listOf("general", "langue", "language", "anglais", "francais", "traduction", "demarrage", "start", "maj", "update", "mise a jour")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_language),
                subtitle = null,
                categoryName = catMisc,
                icon = Icons.Rounded.Translate,
                route = "misc_settings",
                highlightKey = "pref_language",
                keywords = listOf("langue", "language", "francais", "english", "anglais", "deutsch", "allemand", "russe", "traduction", "systeme")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_start_screen),
                subtitle = null,
                categoryName = catMisc,
                icon = Icons.Rounded.Home,
                route = "misc_settings",
                highlightKey = "pref_start_screen",
                keywords = listOf("ecran de demarrage", "start screen", "accueil", "bibliotheque", "home", "library", "demarrage")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_auto_update),
                subtitle = context.getString(R.string.pref_auto_update_sub),
                categoryName = catMisc,
                icon = Icons.Rounded.SystemUpdate,
                route = "misc_settings",
                highlightKey = "pref_auto_update",
                keywords = listOf(
                    "auto check update", "auto check", "check update", "auto update",
                    "mise a jour auto", "maj auto", "update", "mise a jour", "maj",
                    "startup", "demarrage", "nouvelle version", "version check",
                    "verifier au demarrage", "check", "versions", "rechercher"
                ),
                hasSwitch = true,
                switchState = autoUpdate,
                onSwitchChange = {
                    autoUpdate = it
                    prefs.setAutoUpdateEnabled(it)
                    onPreferenceChange()
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_remember_search_filter),
                subtitle = context.getString(R.string.pref_remember_search_filter_sub),
                categoryName = catMisc,
                icon = Icons.Rounded.FilterList,
                route = "misc_settings",
                highlightKey = "pref_remember_search_filter",
                keywords = listOf(
                    "filter", "filtre", "recherche", "search", "search filter", "filtre recherche",
                    "artistes", "artists", "playlists", "tracks", "titres", "memoriser", "remember"
                ),
                hasSwitch = true,
                switchState = rememberSearchFilter,
                onSwitchChange = {
                    rememberSearchFilter = it
                    prefs.setRememberSearchFilter(it)
                    onPreferenceChange()
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_about_title),
                subtitle = context.getString(R.string.pref_about_subtitle),
                categoryName = catMisc,
                icon = Icons.Rounded.Info,
                route = "about",
                keywords = listOf("a propos", "about", "version", "developpeur", "credits", "licences", "github", "check update", "mise a jour", "maj", "update", "rechercher mise a jour")
            )
        )

        val dynamicItems = SettingsRegistry.allDefinitions.map { def ->
            def.toSearchSettingEntry(context, prefs, navController) {
                onPreferenceChange()
            }
        }
        staticItems + dynamicItems
    }
}
