package com.alananasss.kittytune.ui.profile

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.ImportExport
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.ui.graphics.vector.ImageVector
import com.alananasss.kittytune.R

import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SwapVert

/**
 * The settings categories, in the order they are worth opening.
 *
 * The same list, order and icons the desktop app uses. Each category opens into a clean
 * folder view of sub-pages, matching the Interface category design.
 */
internal enum class SettingsCategory(
    @StringRes val titleRes: Int,
    val icon: ImageVector
) {
    INTERFACE(R.string.settings_cat_interface, Icons.Rounded.Palette),
    AUDIO(R.string.settings_cat_audio, Icons.Rounded.GraphicEq),
    SOURCES(R.string.settings_cat_accounts, Icons.Rounded.ImportExport),
    STORAGE(R.string.pref_storage_title, Icons.Rounded.Storage),
    SYNC(R.string.sync_title, Icons.Rounded.Devices),
    NETWORK(R.string.pref_proxy_title, Icons.Rounded.Dns),
    MISC(R.string.settings_cat_misc, Icons.Rounded.Tune),
}

/** Pages opened inside a category. */
internal enum class SettingsSubPage(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int?,
    val icon: ImageVector?
) {
    // Interface
    THEMES(R.string.settings_page_themes, R.string.settings_page_themes_sub, Icons.Rounded.ColorLens),
    PLAYER(R.string.settings_page_player, R.string.settings_page_player_sub, Icons.Rounded.PlayCircle),
    BOTTOM_BAR(R.string.pref_bottom_menu_title, R.string.pref_bottom_menu_subtitle, Icons.Rounded.Home),
    LYRICS(R.string.pref_lyrics_title, R.string.settings_page_lyrics_sub, Icons.Rounded.Lyrics),

    // Audio
    AUDIO_PLAYBACK(R.string.settings_cat_playback, R.string.settings_audio_playback_sub, Icons.Rounded.PlayCircle),
    AUDIO_QUALITY(R.string.settings_audio_quality_title, R.string.settings_audio_quality_sub, Icons.Rounded.GraphicEq),
    AUDIO_TRANSITIONS(R.string.settings_audio_transitions_title, R.string.settings_audio_transitions_sub, Icons.Rounded.Tune),
    AUDIO_SLEEP_TIMER(R.string.sleep_timer_title, R.string.settings_audio_sleep_sub, Icons.Rounded.Schedule),
    AUDIO_HAPTICS(R.string.pref_haptics_title, R.string.pref_haptics_subtitle, Icons.Rounded.Vibration),

    // Sources
    SOURCES_SERVICES(R.string.pref_accounts_title, R.string.sources_services_sub, Icons.Rounded.Cloud),
    SOURCES_ORDER(R.string.provider_order, R.string.sources_order_sub, Icons.Rounded.SwapVert),
    SOURCES_IMPORT(R.string.music_import_title, R.string.music_import_settings_subtitle, Icons.Rounded.ImportExport),
    SOURCES_LYRICS(R.string.lyrics_sources_title, R.string.lyrics_sources_sub, Icons.Rounded.Lyrics),

    // Storage
    STORAGE_CACHE(R.string.pref_storage_title, R.string.storage_cache_sub, Icons.Rounded.Storage),
    STORAGE_LOCAL_FILES(R.string.pref_local_title, R.string.storage_local_files_sub, Icons.Filled.SdStorage),
    STORAGE_BACKUP(R.string.pref_backup_title, R.string.storage_backup_sub, Icons.Rounded.Backup),

    // Sync
    SYNC_DEVICES(R.string.sync_paired_devices_title, R.string.sync_paired_devices_sub, Icons.Rounded.Devices),
    SYNC_OPTIONS(R.string.sync_options_title, R.string.sync_options_sub, Icons.Rounded.Check),
    SYNC_ADVANCED(R.string.sync_advanced_title, R.string.sync_advanced_sub, Icons.Rounded.Tune),

    // Misc
    MISC_GENERAL(R.string.settings_cat_general, R.string.misc_general_sub, Icons.Rounded.Tune),
    MISC_CONTENT_FILTER(R.string.pref_content_filter_title, R.string.pref_content_filter_subtitle, Icons.Rounded.Block),
    MISC_DISCORD(R.string.pref_discord_title, R.string.misc_discord_sub, Icons.Rounded.Cloud),
    MISC_ABOUT(R.string.pref_about_title, R.string.pref_about_subtitle, Icons.Rounded.Info),
    ;

    companion object {
        val interfacePages: List<SettingsSubPage> = listOf(THEMES, PLAYER, BOTTOM_BAR, LYRICS)
        val audioPages: List<SettingsSubPage> = listOf(AUDIO_PLAYBACK, AUDIO_QUALITY, AUDIO_TRANSITIONS, AUDIO_SLEEP_TIMER, AUDIO_HAPTICS)
        val sourcesPages: List<SettingsSubPage> = listOf(SOURCES_SERVICES, SOURCES_ORDER, SOURCES_IMPORT, SOURCES_LYRICS)
        val storagePages: List<SettingsSubPage> = listOf(STORAGE_CACHE, STORAGE_LOCAL_FILES, STORAGE_BACKUP)
        val syncPages: List<SettingsSubPage> = listOf(SYNC_DEVICES, SYNC_OPTIONS, SYNC_ADVANCED)
        val miscPages: List<SettingsSubPage> = listOf(MISC_GENERAL, MISC_CONTENT_FILTER, MISC_DISCORD, MISC_ABOUT)
    }
}

/** One tappable row in a category: a title, an icon, and where it leads. */
internal data class SettingsEntry(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int?,
    val icon: ImageVector,
    val route: String
)

/** A root-screen section: a header with the category's pages listed directly. */
internal data class SettingsRootGroup(
    @StringRes val titleRes: Int,
    val rows: List<SettingsEntry>
)

private fun SettingsSubPage.toEntry(): SettingsEntry =
    SettingsEntry(titleRes, subtitleRes, icon ?: Icons.Rounded.Tune, route)

/**
 * Root sections in the old (pre-desktop-rework) style: every category lists
 * its pages directly, several rows per card. Network holds proxy and zapret,
 * exactly like the desktop NETWORK category folder.
 */
internal fun rootGroups(): List<SettingsRootGroup> = listOf(
    SettingsRootGroup(
        SettingsCategory.INTERFACE.titleRes,
        SettingsSubPage.interfacePages.map { it.toEntry() }
    ),
    SettingsRootGroup(
        SettingsCategory.AUDIO.titleRes,
        SettingsSubPage.audioPages.map { it.toEntry() }
    ),
    SettingsRootGroup(
        SettingsCategory.SOURCES.titleRes,
        SettingsSubPage.sourcesPages.map { it.toEntry() }
    ),
    SettingsRootGroup(
        SettingsCategory.STORAGE.titleRes,
        SettingsSubPage.storagePages.map { it.toEntry() }
    ),
    SettingsRootGroup(
        SettingsCategory.SYNC.titleRes,
        SettingsSubPage.syncPages.map { it.toEntry() }
    ),
    SettingsRootGroup(
        R.string.settings_cat_network,
        listOf(
            SettingsEntry(
                R.string.pref_proxy_title,
                R.string.network_proxy_sub,
                Icons.Rounded.Dns,
                "proxy_settings"
            ),
            SettingsEntry(
                R.string.zapret_title,
                R.string.network_zapret_sub,
                Icons.Rounded.Security,
                "zapret_settings"
            )
        )
    ),
    SettingsRootGroup(
        SettingsCategory.MISC.titleRes,
        SettingsSubPage.miscPages.map { it.toEntry() }
    ),
)

/** The rows of each category on the root settings screen. */
internal fun SettingsCategory.entriesFor(): List<SettingsEntry> = when (this) {
    SettingsCategory.INTERFACE -> listOf(
        SettingsEntry(
            R.string.settings_cat_interface,
            R.string.settings_cat_interface_sub,
            Icons.Rounded.Palette,
            "interface_settings"
        )
    )
    SettingsCategory.AUDIO -> listOf(
        SettingsEntry(
            R.string.settings_cat_audio,
            R.string.pref_audio_subtitle,
            Icons.Rounded.GraphicEq,
            "audio_settings"
        )
    )
    SettingsCategory.SOURCES -> listOf(
        SettingsEntry(
            R.string.settings_cat_accounts,
            R.string.pref_accounts_subtitle,
            Icons.Rounded.ImportExport,
            "sources_settings"
        )
    )
    SettingsCategory.STORAGE -> listOf(
        SettingsEntry(
            R.string.pref_storage_title,
            R.string.pref_storage_subtitle,
            Icons.Rounded.Storage,
            "storage_settings"
        )
    )
    SettingsCategory.SYNC -> listOf(
        SettingsEntry(
            R.string.sync_title,
            R.string.sync_intro,
            Icons.Rounded.Devices,
            "sync_settings"
        )
    )
    SettingsCategory.NETWORK -> listOf(
        SettingsEntry(
            R.string.pref_proxy_title,
            R.string.network_proxy_sub,
            Icons.Rounded.Dns,
            "proxy_settings"
        ),
        SettingsEntry(
            R.string.zapret_title,
            R.string.network_zapret_sub,
            Icons.Rounded.Security,
            "zapret_settings"
        )
    )
    SettingsCategory.MISC -> listOf(
        SettingsEntry(
            R.string.settings_cat_misc,
            R.string.settings_cat_misc_sub,
            Icons.Rounded.Tune,
            "misc_settings"
        )
    )
}

/** Where a sub-page lives in the navigation graph. */
internal val SettingsSubPage.route: String
    get() = when (this) {
        SettingsSubPage.THEMES -> "appearance_settings"
        SettingsSubPage.PLAYER -> "player_design_settings"
        SettingsSubPage.BOTTOM_BAR -> "bottom_bar_settings"
        SettingsSubPage.LYRICS -> "lyrics_settings"

        SettingsSubPage.AUDIO_PLAYBACK -> "audio_playback_settings"
        SettingsSubPage.AUDIO_QUALITY -> "audio_quality_settings"
        SettingsSubPage.AUDIO_TRANSITIONS -> "audio_transitions_settings"
        SettingsSubPage.AUDIO_SLEEP_TIMER -> "audio_sleep_settings"
        SettingsSubPage.AUDIO_HAPTICS -> "haptic_settings"

        SettingsSubPage.SOURCES_SERVICES -> "accounts_settings"
        SettingsSubPage.SOURCES_ORDER -> "provider_order_settings"
        SettingsSubPage.SOURCES_IMPORT -> "music_import"
        SettingsSubPage.SOURCES_LYRICS -> "lyrics_settings"

        SettingsSubPage.STORAGE_CACHE -> "storage"
        SettingsSubPage.STORAGE_LOCAL_FILES -> "local_media_settings"
        SettingsSubPage.STORAGE_BACKUP -> "backup_restore"

        SettingsSubPage.SYNC_DEVICES -> "sync_devices_settings"
        SettingsSubPage.SYNC_OPTIONS -> "sync_options_settings"
        SettingsSubPage.SYNC_ADVANCED -> "sync_advanced_settings"

        SettingsSubPage.MISC_GENERAL -> "misc_general_settings"
        SettingsSubPage.MISC_CONTENT_FILTER -> "content_filter_settings"
        SettingsSubPage.MISC_DISCORD -> "discord_settings"
        SettingsSubPage.MISC_ABOUT -> "about"
    }
