package com.alananasss.kittytune.ui.profile

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.rounded.Backup
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
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.ui.graphics.vector.ImageVector
import com.alananasss.kittytune.R

/**
 * The settings categories, in the order they are worth opening.
 *
 * The same list, order and icons the desktop app uses. Language, start screen and auto-update used
 * to sit under Appearance on Android, which is where they do not belong: none of them changes how
 * anything looks, and burying them is why that screen had grown to well over a thousand lines.
 * They are in [SettingsCategory.MISC] now, where the desktop keeps them.
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
    THEMES(R.string.settings_page_themes, R.string.settings_page_themes_sub, Icons.Rounded.ColorLens),
    PLAYER(R.string.settings_page_player, R.string.settings_page_player_sub, Icons.Rounded.PlayCircle),
    BOTTOM_BAR(R.string.pref_bottom_menu_title, R.string.pref_bottom_menu_subtitle, Icons.Rounded.Home),
    LYRICS(R.string.pref_lyrics_title, R.string.settings_page_lyrics_sub, Icons.Rounded.Lyrics),
    ;

    companion object {
        /**
         * The Interface category's pages, in the desktop's order.
         */
        val interfacePages: List<SettingsSubPage> = listOf(THEMES, PLAYER, BOTTOM_BAR, LYRICS)
    }
}

/** One tappable row in a category: a title, an icon, and where it leads. */
internal data class SettingsEntry(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int?,
    val icon: ImageVector,
    val route: String
)

/** The rows of each category, in the desktop's order. */
internal fun SettingsCategory.entriesFor(): List<SettingsEntry> = when (this) {
    // Opened as a page of sub-pages rather than as its own screen, so the category list stays put.
    // One row that opens the sub-page list, the way the desktop's Interface pane lists its pages.
    SettingsCategory.INTERFACE -> listOf(
        SettingsEntry(
            R.string.settings_cat_interface,
            R.string.settings_cat_interface_sub,
            Icons.Rounded.Palette,
            "interface_settings"
        )
    )
    SettingsCategory.AUDIO -> listOf(
        SettingsEntry(R.string.pref_audio_title, R.string.pref_audio_subtitle, Icons.Rounded.GraphicEq, "audio_settings"),
        SettingsEntry(R.string.pref_haptics_title, R.string.pref_haptics_subtitle, Icons.Rounded.Vibration, "haptic_settings")
    )
    SettingsCategory.SOURCES -> listOf(
        SettingsEntry(R.string.pref_accounts_title, R.string.pref_accounts_subtitle, Icons.Rounded.ImportExport, "accounts_settings"),
        SettingsEntry(R.string.provider_order, R.string.pref_accounts_subtitle, Icons.Rounded.Tune, "provider_order_settings")
    )
    SettingsCategory.STORAGE -> listOf(
        SettingsEntry(R.string.pref_storage_title, R.string.pref_storage_subtitle, Icons.Rounded.Storage, "storage"),
        SettingsEntry(R.string.pref_local_title, R.string.pref_local_subtitle, Icons.Filled.SdStorage, "local_media_settings"),
        SettingsEntry(R.string.pref_backup_title, R.string.pref_backup_subtitle, Icons.Rounded.Backup, "backup_restore")
    )
    SettingsCategory.SYNC -> listOf(
        SettingsEntry(R.string.sync_title, R.string.sync_intro, Icons.Rounded.Devices, "sync_settings")
    )
    SettingsCategory.NETWORK -> listOf(
        SettingsEntry(R.string.pref_proxy_title, R.string.pref_proxy_subtitle, Icons.Rounded.Dns, "proxy_settings")
    )
    SettingsCategory.MISC -> listOf(
        SettingsEntry(R.string.settings_cat_general, R.string.settings_cat_general_sub, Icons.Rounded.Tune, "misc_settings"),
        SettingsEntry(R.string.music_import_title, R.string.music_import_settings_subtitle, Icons.Rounded.ImportExport, "music_import"),
        SettingsEntry(R.string.pref_about_title, R.string.pref_about_subtitle, Icons.Rounded.Info, "about")
    )
}

/** Where a sub-page lives in the navigation graph. */
internal val SettingsSubPage.route: String
    get() = when (this) {
        SettingsSubPage.THEMES -> "appearance_settings"
        SettingsSubPage.PLAYER -> "player_design_settings"
        SettingsSubPage.BOTTOM_BAR -> "bottom_bar_settings"
        SettingsSubPage.LYRICS -> "lyrics_settings"
    }
