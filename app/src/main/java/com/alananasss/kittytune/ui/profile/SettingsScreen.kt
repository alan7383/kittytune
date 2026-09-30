package com.alananasss.kittytune.ui.profile

import java.text.Normalizer
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.player.PlayerViewModel



/**
 * The settings screen, structured exactly like KittyTune Desktop:
 * - A top search bar with live filtering across all settings, keywords, and direct toggle switches.
 * - The 7 clean desktop categories: Interface, Audio, Sources, Storage, Sync, Network, Misc.
 */
@Composable
fun SettingsScreen(
    navController: NavController,
    onBackClick: () -> Unit,
    playerViewModel: PlayerViewModel
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }

    var dynamicTheme by remember { mutableStateOf(prefs.getDynamicTheme()) }
    var trackDynamicTheme by remember { mutableStateOf(prefs.getTrackDynamicTheme()) }
    var pureBlack by remember { mutableStateOf(prefs.getPureBlack()) }
    var animatedCovers by remember { mutableStateOf(prefs.getAnimatedCoversEnabled()) }
    var animatedCoversFadeUi by remember { mutableStateOf(prefs.getAnimatedCoversFadeUiEnabled()) }
    var animatedArtistProfiles by remember { mutableStateOf(prefs.getAnimatedArtistProfilesEnabled()) }
    var lyricsUnderCover by remember { mutableStateOf(prefs.getLyricsUnderCoverEnabled()) }
    var showRemainingTime by remember { mutableStateOf(prefs.getShowRemainingTime()) }
    var verticalVolume by remember { mutableStateOf(prefs.getVerticalVolumeSlider()) }
    var crossfade by remember { mutableStateOf(prefs.getCrossfadeEnabled()) }
    var automix by remember { mutableStateOf(prefs.getAutomixEnabled()) }
    var autoplay by remember { mutableStateOf(prefs.getAutoplayEnabled()) }
    var stopOnTaskClear by remember { mutableStateOf(prefs.getStopOnTaskClear()) }
    var persistentQueue by remember { mutableStateOf(prefs.getPersistentQueueEnabled()) }
    var savePosition by remember { mutableStateOf(prefs.getSavePositionEnabled()) }
    var youtubeFallback by remember { mutableStateOf(prefs.getYouTubeFallbackEnabled()) }
    var discordRpc by remember { mutableStateOf(prefs.getDiscordRpcEnabled()) }
    var achievementPopups by remember { mutableStateOf(prefs.getAchievementPopupsEnabled()) }
    var autoUpdate by remember { mutableStateOf(prefs.getAutoUpdateEnabled()) }
    var customFontEnabled by remember { mutableStateOf(prefs.getCustomFontEnabled()) }
    var explorerGridLayout by remember { mutableStateOf(prefs.getExplorerGridLayout()) }
    var preferenceVersion by remember { mutableStateOf(0) }

    val catInterface = stringResource(R.string.settings_cat_interface)
    val catAudio = stringResource(R.string.settings_cat_audio)
    val catSources = stringResource(R.string.settings_cat_accounts)
    val catStorage = stringResource(R.string.pref_storage_title)
    val catSync = stringResource(R.string.sync_title)
    val catNetwork = stringResource(R.string.pref_proxy_title)
    val catMisc = stringResource(R.string.settings_cat_misc)

    // Pre-warmed search items catalogue: constructed during screen opening so the first typed
    // character has 0 disk I/O, 0 string allocations, and zero lag.
    val allSearchItems = remember(
        dynamicTheme, trackDynamicTheme, pureBlack, animatedCovers, animatedCoversFadeUi,
        animatedArtistProfiles, lyricsUnderCover, showRemainingTime, verticalVolume,
        crossfade, automix, autoplay, stopOnTaskClear, persistentQueue, savePosition,
        youtubeFallback, discordRpc, achievementPopups, autoUpdate, customFontEnabled,
        explorerGridLayout,
        playerViewModel.isHapticsEnabled, playerViewModel.equalizerState.isEnabled,
        playerViewModel.effectsState.isNormalizationEnabled, playerViewModel.effectsState.isMonoEnabled,
        preferenceVersion, searchQuery
    ) {
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
                keywords = listOf("dynamic", "couleurs dynamiques", "papier peint", "wallpaper", "monet", "material you"),
                hasSwitch = true,
                switchState = dynamicTheme,
                onSwitchChange = {
                    dynamicTheme = it
                    prefs.setDynamicTheme(it)
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_theme_track_dynamic),
                subtitle = context.getString(R.string.pref_theme_track_dynamic_sub),
                categoryName = catInterface,
                icon = Icons.Rounded.Album,
                keywords = listOf("pochette", "album art", "cover color", "track dynamic", "couleur morceau"),
                hasSwitch = true,
                switchState = trackDynamicTheme,
                onSwitchChange = {
                    trackDynamicTheme = it
                    prefs.setTrackDynamicTheme(it)
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_theme_pure_black),
                subtitle = context.getString(R.string.pref_theme_pure_black_sub),
                categoryName = catInterface,
                icon = Icons.Rounded.Contrast,
                keywords = listOf("noir pur", "pure black", "amoled", "oled", "true black"),
                hasSwitch = true,
                switchState = pureBlack,
                onSwitchChange = {
                    pureBlack = it
                    prefs.setPureBlack(it)
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_show_remaining_time),
                subtitle = context.getString(R.string.pref_show_remaining_time_desc),
                categoryName = catInterface,
                icon = Icons.Rounded.Timer,
                keywords = listOf("temps restant", "remaining time", "countdown", "-00:14", "duree", "decompte"),
                hasSwitch = true,
                switchState = showRemainingTime,
                onSwitchChange = {
                    showRemainingTime = it
                    prefs.setShowRemainingTime(it)
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
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_animated_covers),
                subtitle = context.getString(R.string.pref_animated_covers_desc),
                categoryName = catInterface,
                icon = Icons.Rounded.PlayCircle,
                keywords = listOf("pochettes animees", "animated covers", "video cover", "pochette video"),
                hasSwitch = true,
                switchState = animatedCovers,
                onSwitchChange = {
                    animatedCovers = it
                    prefs.setAnimatedCoversEnabled(it)
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_animated_covers_fade_ui),
                subtitle = context.getString(R.string.pref_animated_covers_fade_ui_desc),
                categoryName = catInterface,
                icon = Icons.Rounded.Opacity,
                keywords = listOf("fondu", "fade ui", "masquer controles", "interface fondu"),
                hasSwitch = true,
                switchState = animatedCoversFadeUi,
                onSwitchChange = {
                    animatedCoversFadeUi = it
                    prefs.setAnimatedCoversFadeUiEnabled(it)
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_animated_artist_profiles),
                subtitle = context.getString(R.string.pref_animated_artist_profiles_desc),
                categoryName = catInterface,
                icon = Icons.Rounded.AccountCircle,
                keywords = listOf("profils artistes", "artiste anime", "artist video", "banniere animee"),
                hasSwitch = true,
                switchState = animatedArtistProfiles,
                onSwitchChange = {
                    animatedArtistProfiles = it
                    prefs.setAnimatedArtistProfilesEnabled(it)
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_lyrics_under_cover),
                subtitle = context.getString(R.string.pref_lyrics_under_cover_sub),
                categoryName = catInterface,
                icon = Icons.Rounded.Lyrics,
                keywords = listOf("paroles sous la pochette", "lyrics under cover", "paroles lecteur"),
                hasSwitch = true,
                switchState = lyricsUnderCover,
                onSwitchChange = {
                    lyricsUnderCover = it
                    prefs.setLyricsUnderCoverEnabled(it)
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_font_custom_title),
                subtitle = context.getString(R.string.pref_font_custom_subtitle),
                categoryName = catInterface,
                icon = Icons.Rounded.TextFields,
                keywords = listOf("police", "font", "typographie", "custom font", "texte", "police personnalisee"),
                hasSwitch = true,
                switchState = customFontEnabled,
                onSwitchChange = {
                    customFontEnabled = it
                    prefs.setCustomFontEnabled(it)
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_font_variations_title),
                subtitle = context.getString(R.string.pref_font_variations_subtitle),
                categoryName = catInterface,
                icon = Icons.Rounded.Tune,
                route = "appearance_settings",
                keywords = listOf("variations de police", "font variations", "epaisseur", "weight", "slant", "round", "graisse")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_explorer_grid_title),
                subtitle = context.getString(R.string.pref_explorer_grid_subtitle),
                categoryName = catInterface,
                icon = Icons.Rounded.GridView,
                keywords = listOf("explorer grille", "grille exploration", "explorer grid", "affichage grille"),
                hasSwitch = true,
                switchState = explorerGridLayout,
                onSwitchChange = {
                    explorerGridLayout = it
                    prefs.setExplorerGridLayout(it)
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_library_category_layout_title),
                subtitle = context.getString(R.string.pref_library_category_layout_subtitle),
                categoryName = catInterface,
                icon = Icons.Rounded.FilterList,
                route = "appearance_settings",
                keywords = listOf("bibliotheque", "library", "filtres", "filtre", "categories", "layout", "disposition", "desktop", "bureau", "bouton", "playlists", "albums", "artistes", "stations", "all", "tout")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_achievement_popups),
                subtitle = context.getString(R.string.pref_achievement_popups_sub),
                categoryName = catInterface,
                icon = Icons.Rounded.EmojiEvents,
                keywords = listOf("succes", "achievement", "popups", "trophees", "notifications de succes"),
                hasSwitch = true,
                switchState = achievementPopups,
                onSwitchChange = {
                    achievementPopups = it
                    prefs.setAchievementPopupsEnabled(it)
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
                keywords = listOf("equalizer", "egaliseur", "eq", "preamp", "preset", "bass", "treble", "frequence", "son", "16-band")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_norm_title),
                subtitle = context.getString(R.string.pref_norm_sub),
                categoryName = catAudio,
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                route = "audio_settings",
                keywords = listOf("normalisation", "volume", "replaygain", "lufs", "gain", "loudness", "egalisation volume")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_audio_mono),
                subtitle = context.getString(R.string.pref_audio_mono_sub),
                categoryName = catAudio,
                icon = Icons.AutoMirrored.Rounded.VolumeDown,
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
                keywords = listOf("crossfade", "fondu enchaine", "transition", "fondu"),
                hasSwitch = true,
                switchState = crossfade,
                onSwitchChange = {
                    crossfade = it
                    prefs.setCrossfadeEnabled(it)
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_autoplay),
                subtitle = context.getString(R.string.pref_autoplay_sub),
                categoryName = catAudio,
                icon = Icons.Rounded.PlayArrow,
                keywords = listOf("autoplay", "lecture automatique", "suite", "recommandation"),
                hasSwitch = true,
                switchState = autoplay,
                onSwitchChange = {
                    autoplay = it
                    prefs.setAutoplayEnabled(it)
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.automix),
                subtitle = context.getString(R.string.automix_desc),
                categoryName = catAudio,
                icon = Icons.Rounded.AutoMode,
                keywords = listOf("automix", "enchainement", "dj", "dj flow", "flow", "mix", "transition", "tempo", "smart mix"),
                hasSwitch = true,
                switchState = automix,
                onSwitchChange = {
                    automix = it
                    prefs.setAutomixEnabled(it)
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_stop_on_task_clear),
                subtitle = null,
                categoryName = catAudio,
                icon = Icons.Rounded.Cancel,
                keywords = listOf("arreter", "fermeture", "stop on task clear", "quitter", "tache", "kill", "arreter musique a la fermeture", "app close"),
                hasSwitch = true,
                switchState = stopOnTaskClear,
                onSwitchChange = {
                    stopOnTaskClear = it
                    prefs.setStopOnTaskClear(it)
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_persist_queue),
                subtitle = context.getString(R.string.pref_persist_queue_sub),
                categoryName = catAudio,
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                keywords = listOf("file d'attente", "queue", "memoriser file", "persist queue"),
                hasSwitch = true,
                switchState = persistentQueue,
                onSwitchChange = {
                    persistentQueue = it
                    prefs.setPersistentQueueEnabled(it)
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_save_position),
                subtitle = context.getString(R.string.pref_save_position_sub),
                categoryName = catAudio,
                icon = Icons.Rounded.Restore,
                keywords = listOf("reprendre lecture", "position de lecture", "save position", "memoriser position", "resume", "reprise"),
                hasSwitch = true,
                switchState = savePosition,
                onSwitchChange = {
                    savePosition = it
                    prefs.setSavePositionEnabled(it)
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.sleep_timer_fade_title),
                subtitle = null,
                categoryName = catAudio,
                icon = Icons.Rounded.Bedtime,
                route = "audio_settings",
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
                route = "soundcloud_account_settings",
                keywords = listOf("soundcloud", "sc", "compte soundcloud", "stream", "login")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_account_vk_title),
                subtitle = null,
                categoryName = catSources,
                iconRes = R.drawable.ic_vk,
                route = "vk_account_settings",
                keywords = listOf("vk", "vkontakte", "vk music", "compte vk", "login")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_discord_title),
                subtitle = null,
                categoryName = catSources,
                iconRes = R.drawable.ic_discord,
                route = "discord_settings",
                keywords = listOf("discord", "rpc", "presence", "rich presence", "statut", "compte discord")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.qobuz_integration),
                subtitle = null,
                categoryName = catSources,
                iconRes = R.drawable.ic_logo_qobuz,
                route = "qobuz_settings",
                keywords = listOf("qobuz", "flac", "hi-res", "source qobuz", "haute resolution")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.tidal_integration),
                subtitle = null,
                categoryName = catSources,
                iconRes = R.drawable.ic_logo_tidal,
                route = "tidal_settings",
                keywords = listOf("tidal", "hifi", "lossless", "master", "source tidal")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.deezer_integration),
                subtitle = null,
                categoryName = catSources,
                iconRes = R.drawable.ic_logo_deezer,
                route = "deezer_settings",
                keywords = listOf("deezer", "mp3", "flac", "source deezer")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.provider_order),
                subtitle = context.getString(R.string.pref_accounts_subtitle),
                categoryName = catSources,
                icon = Icons.Rounded.Tune,
                route = "provider_order_settings",
                keywords = listOf("ordre sources", "fournisseurs", "priorite", "stream", "provider order")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_youtube_fallback),
                subtitle = context.getString(R.string.pref_youtube_fallback_sub),
                categoryName = catSources,
                icon = Icons.Rounded.SmartDisplay,
                keywords = listOf("youtube fallback", "repli youtube", "secours", "youtube"),
                hasSwitch = true,
                switchState = youtubeFallback,
                onSwitchChange = {
                    youtubeFallback = it
                    prefs.setYouTubeFallbackEnabled(it)
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.discord_rpc_title),
                subtitle = context.getString(R.string.discord_enable_rpc_desc),
                categoryName = catSources,
                icon = Icons.AutoMirrored.Rounded.Chat,
                keywords = listOf("discord", "presence", "rpc", "statut", "rich presence"),
                hasSwitch = true,
                switchState = discordRpc,
                onSwitchChange = {
                    discordRpc = it
                    prefs.setDiscordRpcEnabled(it)
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
                keywords = listOf("langue", "language", "francais", "english", "anglais", "deutsch", "allemand", "russe", "traduction", "systeme")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_start_screen),
                subtitle = null,
                categoryName = catMisc,
                icon = Icons.Rounded.Home,
                route = "misc_settings",
                keywords = listOf("ecran de demarrage", "start screen", "accueil", "bibliotheque", "home", "library", "demarrage")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_auto_update),
                subtitle = context.getString(R.string.pref_auto_update_sub),
                categoryName = catMisc,
                icon = Icons.Rounded.SystemUpdate,
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
                preferenceVersion++
            }
        }
        staticItems + dynamicItems
    }

    BackHandler(enabled = searchQuery.isNotEmpty()) {
        searchQuery = ""
        focusManager.clearFocus()
    }

    SettingsScaffold(
        title = stringResource(R.string.settings_title),
        onBackClick = {
            if (searchQuery.isNotEmpty()) {
                searchQuery = ""
                focusManager.clearFocus()
            } else {
                onBackClick()
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize()
        ) {
            // Desktop-styled Search Bar
            SettingsSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onClear = {
                    searchQuery = ""
                    focusManager.clearFocus()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (searchQuery.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    SettingsSearchResults(
                        query = searchQuery,
                        allItems = allSearchItems,
                        navController = navController
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 180.dp, top = 8.dp)
                ) {
                    SettingsCategory.entries.forEach { category ->
                        item(key = "cat-${category.name}") {
                            SettingsGroup(
                                title = stringResource(category.titleRes),
                                items = category.entriesFor().map { entry ->
                                    { shape ->
                                        SettingsItem(
                                            shape = shape,
                                            title = stringResource(entry.titleRes),
                                            subtitle = entry.subtitleRes?.let { stringResource(it) },
                                            icon = entry.icon,
                                            onClick = { navController.navigate(entry.route) }
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Desktop-styled search field with rounded pill shape, search icon and clear button.
 */
@Composable
private fun SettingsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                stringResource(R.string.search_settings_hint),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.btn_clear),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = modifier
    )
}

@Composable
private fun SettingsSearchResults(
    query: String,
    allItems: List<SearchSettingEntry>,
    navController: NavController
) {
    val normalizedQuery = remember(query) { normalizeSearchText(query) }
    val queryTokens = remember(normalizedQuery) {
        normalizedQuery.split(WHITESPACE_REGEX).filter { it.isNotBlank() }
    }

    val matches = remember(normalizedQuery, queryTokens, allItems) {
        if (queryTokens.isEmpty()) {
            emptyList()
        } else {
            allItems
                .mapNotNull { item ->
                    if (queryTokens.all { token -> item.searchCorpus.contains(token) }) {
                        val score = when {
                            item.normTitle == normalizedQuery -> 100
                            item.normTitle.startsWith(normalizedQuery) -> 80
                            item.normTitle.contains(normalizedQuery) -> 60
                            queryTokens.all { item.normTitle.contains(it) } -> 50
                            item.normKeywords.any { it == normalizedQuery } -> 40
                            item.normKeywords.any { it.startsWith(normalizedQuery) } -> 30
                            item.normKeywords.any { it.contains(normalizedQuery) } -> 20
                            else -> 10
                        }
                        item to score
                    } else {
                        null
                    }
                }
                .sortedWith(
                    compareByDescending<Pair<SearchSettingEntry, Int>> { it.second }
                        .thenBy { it.first.title }
                )
                .map { it.first }
        }
    }

    if (matches.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 56.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = stringResource(R.string.settings_search_no_results, query),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    } else {
        val grouped = remember(matches) { matches.groupBy { it.categoryName } }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp, top = 8.dp)
        ) {
            grouped.forEach { (catName, itemsInCat) ->
                item(key = "search-cat-$catName") {
                    SettingsGroup(
                        title = catName,
                        items = itemsInCat.map { searchItem ->
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = searchItem.title,
                                    subtitle = searchItem.subtitle,
                                    icon = searchItem.icon,
                                    iconRes = searchItem.iconRes,
                                    hasSwitch = searchItem.hasSwitch,
                                    switchState = searchItem.switchState,
                                    onSwitchChange = searchItem.onSwitchChange,
                                    onClick = {
                                        if (searchItem.onClick != null) {
                                            searchItem.onClick.invoke()
                                        } else if (searchItem.route != null) {
                                            navController.navigate(searchItem.route)
                                        }
                                    }
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}


/**
 * The Interface category's sub-pages, rendered identically to Desktop Screenshot 5:
 * Thèmes, Design du lecteur, Barre de navigation, Paroles.
 */
@Composable
fun InterfaceSettingsScreen(
    navController: NavController,
    onBackClick: () -> Unit
) {
    SettingsScaffold(
        title = stringResource(R.string.settings_cat_interface),
        onBackClick = onBackClick
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp, top = 16.dp)
        ) {
            item {
                SettingsGroup(
                    items = SettingsSubPage.interfacePages.map { page ->
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(page.titleRes),
                                subtitle = page.subtitleRes?.let { stringResource(it) },
                                icon = page.icon,
                                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                                onClick = { navController.navigate(page.route) }
                            )
                        }
                    }
                )
            }
        }
    }
}
