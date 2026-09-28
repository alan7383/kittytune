package com.alananasss.kittytune.ui.profile

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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

    BackHandler(enabled = searchQuery.isNotEmpty()) {
        searchQuery = ""
        focusManager.clearFocus()
    }

    SettingsScaffold(
        title = if (searchQuery.isNotBlank()) stringResource(R.string.settings_search_results) else stringResource(R.string.settings_title),
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
                        navController = navController,
                        playerViewModel = playerViewModel
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

/**
 * One searchable item representation with direct action or route.
 */
private data class SearchSettingEntry(
    val title: String,
    val subtitle: String? = null,
    val categoryName: String,
    val icon: ImageVector? = null,
    val route: String? = null,
    val keywords: List<String> = emptyList(),
    val hasSwitch: Boolean = false,
    val switchState: Boolean = false,
    val onSwitchChange: ((Boolean) -> Unit)? = null,
    val onClick: (() -> Unit)? = null,
)

@Composable
private fun SettingsSearchResults(
    query: String,
    navController: NavController,
    playerViewModel: PlayerViewModel
) {
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
    var persistentQueue by remember { mutableStateOf(prefs.getPersistentQueueEnabled()) }
    var savePosition by remember { mutableStateOf(prefs.getSavePositionEnabled()) }
    var youtubeFallback by remember { mutableStateOf(prefs.getYouTubeFallbackEnabled()) }
    var discordRpc by remember { mutableStateOf(prefs.getDiscordRpcEnabled()) }
    var achievementPopups by remember { mutableStateOf(prefs.getAchievementPopupsEnabled()) }

    val catInterface = stringResource(R.string.settings_cat_interface)
    val catAudio = stringResource(R.string.settings_cat_audio)
    val catSources = stringResource(R.string.settings_cat_accounts)
    val catStorage = stringResource(R.string.pref_storage_title)
    val catSync = stringResource(R.string.sync_title)
    val catNetwork = stringResource(R.string.pref_proxy_title)
    val catMisc = stringResource(R.string.settings_cat_misc)

    val allItems = remember(
        dynamicTheme, trackDynamicTheme, pureBlack, animatedCovers, animatedCoversFadeUi,
        animatedArtistProfiles, lyricsUnderCover, showRemainingTime, verticalVolume,
        crossfade, automix, autoplay, persistentQueue, savePosition,
        youtubeFallback, discordRpc, achievementPopups
    ) {
        listOf(
            // INTERFACE
            SearchSettingEntry(
                title = context.getString(R.string.settings_page_themes),
                subtitle = context.getString(R.string.settings_page_themes_sub),
                categoryName = catInterface,
                icon = Icons.Rounded.ColorLens,
                route = "appearance_settings",
                keywords = listOf("theme", "couleur", "sombre", "clair", "amoled", "oled", "palette", "ocean", "forest", "sunset", "rose", "lavande", "menthe", "dark", "light", "colors")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.settings_page_player),
                subtitle = context.getString(R.string.settings_page_player_sub),
                categoryName = catInterface,
                icon = Icons.Rounded.PlayCircle,
                route = "player_design_settings",
                keywords = listOf("lecteur", "player", "silhouette", "curseur", "slider", "wavy", "slim", "squiggly", "bar", "volume", "boutons", "menu", "morceau", "playlist", "sheet", "trois petits points", "dock", "flottant")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_bottom_menu_title),
                subtitle = context.getString(R.string.pref_bottom_menu_subtitle),
                categoryName = catInterface,
                icon = Icons.Rounded.ViewSidebar,
                route = "bottom_bar_settings",
                keywords = listOf("barre", "navigation", "onglets", "fab", "menu du bas", "bottom bar", "tabs")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_lyrics_title),
                subtitle = context.getString(R.string.settings_page_lyrics_sub),
                categoryName = catInterface,
                icon = Icons.Rounded.Lyrics,
                route = "lyrics_settings",
                keywords = listOf("paroles", "lyrics", "karaoke", "synchro", "texte", "chanson")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_color_palette_title),
                subtitle = context.getString(R.string.pref_color_palette_subtitle),
                categoryName = catInterface,
                icon = Icons.Rounded.Palette,
                route = "color_palette",
                keywords = listOf("palette", "couleur personnalisee", "accent", "custom color")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_app_icon_title),
                subtitle = context.getString(R.string.pref_app_icon_subtitle),
                categoryName = catInterface,
                icon = Icons.Rounded.Apps,
                route = "app_icon_settings",
                keywords = listOf("icone", "app icon", "logo", "visuel")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_theme_dynamic),
                subtitle = context.getString(R.string.pref_theme_dynamic_sub),
                categoryName = catInterface,
                icon = Icons.Rounded.AutoAwesome,
                keywords = listOf("dynamic", "couleurs dynamiques", "papier peint", "wallpaper"),
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
                keywords = listOf("pochette", "album art", "cover color", "track dynamic"),
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
                keywords = listOf("temps restant", "remaining time", "countdown", "-00:14", "durée"),
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
                icon = Icons.Rounded.VolumeUp,
                keywords = listOf("volume", "curseur volume", "vertical", "horizontal", "slider"),
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
                keywords = listOf("pochettes animees", "animated covers", "video cover"),
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
                keywords = listOf("fondu", "fade ui", "masquer controles"),
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
                keywords = listOf("profils artistes", "artiste anime", "artist video"),
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
                keywords = listOf("paroles sous la pochette", "lyrics under cover"),
                hasSwitch = true,
                switchState = lyricsUnderCover,
                onSwitchChange = {
                    lyricsUnderCover = it
                    prefs.setLyricsUnderCoverEnabled(it)
                }
            ),

            // AUDIO
            SearchSettingEntry(
                title = context.getString(R.string.pref_audio_title),
                subtitle = context.getString(R.string.pref_audio_subtitle),
                categoryName = catAudio,
                icon = Icons.Rounded.GraphicEq,
                route = "audio_settings",
                keywords = listOf("audio", "qualite", "egaliseur", "equalizer", "normalisation", "gain", "bitrate")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_haptics_title),
                subtitle = context.getString(R.string.pref_haptics_subtitle),
                categoryName = catAudio,
                icon = Icons.Rounded.Vibration,
                route = "haptic_settings",
                keywords = listOf("vibration", "haptic", "retour haptique", "touch")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_crossfade_title),
                subtitle = context.getString(R.string.pref_crossfade_sub),
                categoryName = catAudio,
                icon = Icons.Rounded.LinearScale,
                keywords = listOf("crossfade", "fondu enchaine", "transition"),
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
                keywords = listOf("autoplay", "lecture automatique", "suite"),
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
                keywords = listOf("automix", "enchainement", "dj"),
                hasSwitch = true,
                switchState = automix,
                onSwitchChange = {
                    automix = it
                    prefs.setAutomixEnabled(it)
                }
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_persist_queue),
                subtitle = context.getString(R.string.pref_persist_queue_sub),
                categoryName = catAudio,
                icon = Icons.Rounded.QueueMusic,
                keywords = listOf("file d'attente", "queue", "memoriser file"),
                hasSwitch = true,
                switchState = persistentQueue,
                onSwitchChange = {
                    persistentQueue = it
                    prefs.setPersistentQueueEnabled(it)
                }
            ),

            // SOURCES
            SearchSettingEntry(
                title = context.getString(R.string.pref_accounts_title),
                subtitle = context.getString(R.string.pref_accounts_subtitle),
                categoryName = catSources,
                icon = Icons.Rounded.ImportExport,
                route = "accounts_settings",
                keywords = listOf("comptes", "sources", "spotify", "soundcloud", "vk", "tidal", "deezer", "qobuz")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.provider_order),
                subtitle = context.getString(R.string.pref_accounts_subtitle),
                categoryName = catSources,
                icon = Icons.Rounded.Tune,
                route = "provider_order_settings",
                keywords = listOf("ordre sources", "fournisseurs", "priorite", "stream")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_youtube_fallback),
                subtitle = context.getString(R.string.pref_youtube_fallback_sub),
                categoryName = catSources,
                icon = Icons.Rounded.SmartDisplay,
                keywords = listOf("youtube fallback", "repli youtube", "secours"),
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
                icon = Icons.Rounded.Chat,
                keywords = listOf("discord", "presence", "rpc", "statut"),
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
                keywords = listOf("stockage", "cache", "vider", "memoire", "disque")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_local_title),
                subtitle = context.getString(R.string.pref_local_subtitle),
                categoryName = catStorage,
                icon = Icons.Rounded.SdStorage,
                route = "local_media_settings",
                keywords = listOf("fichiers locaux", "dossiers", "sd card", "musique locale", "mp3")
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
                keywords = listOf("sync", "synchronisation", "appareils", "appairage", "qr code", "connexion")
            ),

            // NETWORK
            SearchSettingEntry(
                title = context.getString(R.string.pref_proxy_title),
                subtitle = context.getString(R.string.pref_proxy_subtitle),
                categoryName = catNetwork,
                icon = Icons.Rounded.Dns,
                route = "proxy_settings",
                keywords = listOf("proxy", "reseau", "ip", "port", "socks", "http", "dns", "vpn")
            ),

            // MISC
            SearchSettingEntry(
                title = context.getString(R.string.pref_language),
                subtitle = context.getString(R.string.pref_language_sub),
                categoryName = catMisc,
                icon = Icons.Rounded.Language,
                route = "misc_settings",
                keywords = listOf("langue", "language", "anglais", "francais", "traduction")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.music_import_title),
                subtitle = context.getString(R.string.music_import_settings_subtitle),
                categoryName = catMisc,
                icon = Icons.Rounded.ImportExport,
                route = "music_import",
                keywords = listOf("import", "importer", "playlist import", "spotify import")
            ),
            SearchSettingEntry(
                title = context.getString(R.string.pref_about_title),
                subtitle = context.getString(R.string.pref_about_subtitle),
                categoryName = catMisc,
                icon = Icons.Rounded.Info,
                route = "about",
                keywords = listOf("a propos", "version", "developpeur", "credits", "licences", "github")
            )
        )
    }

    val q = query.trim().lowercase()
    val matches = remember(q, allItems) {
        allItems.filter { item ->
            item.title.lowercase().contains(q) ||
            (item.subtitle?.lowercase()?.contains(q) == true) ||
            item.categoryName.lowercase().contains(q) ||
            item.keywords.any { it.lowercase().contains(q) }
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
        val grouped = matches.groupBy { it.categoryName }
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
