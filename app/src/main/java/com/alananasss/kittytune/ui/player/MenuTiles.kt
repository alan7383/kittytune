package com.alananasss.kittytune.ui.player

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.PlayerPreferences

/**
 * Catalogue and arrangement for track and playlist menu tiles (three dots options).
 */
object MenuTiles {

    data class Tile(val id: String, @StringRes val labelRes: Int)

    val TRACK = listOf(
        Tile("like", R.string.player_like_action),
        Tile("shuffle", R.string.menu_shuffle),
        Tile("repeat", R.string.menu_repeat),
        Tile("play_next", R.string.menu_play_next),
        Tile("add_queue", R.string.menu_add_queue),
        Tile("comments", R.string.menu_comments),
        Tile("repost", R.string.menu_repost),
        Tile("details", R.string.menu_details),
        Tile("lyrics", R.string.player_lyrics),
        Tile("duet_lyrics_blacklist", R.string.pref_lyrics_duet_title),
        Tile("add_playlist", R.string.menu_add_playlist),
        Tile("go_album", R.string.menu_go_album),
        Tile("go_artist", R.string.menu_go_artist),
        Tile("edit_track", R.string.menu_edit_track),
        Tile("track_radio", R.string.menu_track_radio),
        Tile("share", R.string.btn_share),
        Tile("share_card", R.string.share_card_title),
        Tile("remove_from_playlist", R.string.menu_remove),
        Tile("sleep_timer", R.string.sleep_timer_title),
        Tile("trim", R.string.trim_title),
        Tile("dj_flow", R.string.dj_flow_title),
        Tile("download", R.string.btn_download),
    )

    val PLAYLIST = listOf(
        Tile("play", R.string.btn_play),
        Tile("shuffle", R.string.btn_shuffle),
        Tile("play_next", R.string.menu_play_next),
        Tile("add_queue", R.string.menu_add_queue),
        Tile("add_playlist", R.string.menu_add_playlist),
        Tile("details", R.string.menu_playlist_details),
        Tile("go_artist", R.string.menu_go_artist),
        Tile("share", R.string.btn_share),
        Tile("download", R.string.btn_download),
    )

    fun defaultHidden(menu: String): Set<String> =
        PlayerPreferences.defaultHiddenMenuTiles(menu)

    fun catalogue(menu: String): List<Tile> =
        if (menu == PlayerPreferences.MENU_PLAYLIST) {
            PLAYLIST
        } else {
            TRACK.filter { it.id != "dj_flow" || com.alananasss.kittytune.BuildConfig.DEBUG }
        }

    fun <T> arrange(
        present: List<T>,
        order: List<String>,
        hidden: Set<String>,
        idOf: (T) -> String,
    ): List<T> {
        val visible = present.filterNot { idOf(it) in hidden }
        if (order.isEmpty()) return visible
        val rank = order.withIndex().associate { (i, id) -> id to i }
        return visible
            .withIndex()
            .sortedBy { (position, tile) -> rank[idOf(tile)] ?: (order.size + position) }
            .map { it.value }
    }

    fun tileIcon(id: String): ImageVector = when (id) {
        "like" -> Icons.Rounded.FavoriteBorder
        "shuffle" -> Icons.Rounded.Shuffle
        "repeat" -> Icons.Rounded.Repeat
        "play_next" -> Icons.AutoMirrored.Rounded.PlaylistPlay
        "add_queue" -> Icons.AutoMirrored.Rounded.QueueMusic
        "comments" -> Icons.Rounded.ChatBubbleOutline
        "repost" -> Icons.Rounded.Repeat
        "details" -> Icons.Rounded.Info
        "lyrics" -> Icons.Rounded.Lyrics
        "duet_lyrics_blacklist" -> Icons.Rounded.RecordVoiceOver
        "add_playlist" -> Icons.Rounded.PlaylistAdd
        "go_album" -> Icons.Rounded.Album
        "go_artist" -> Icons.Rounded.Person
        "edit_track" -> Icons.Rounded.Edit
        "track_radio" -> Icons.Rounded.Radio
        "share" -> Icons.Rounded.Share
        "remove_from_playlist" -> Icons.Rounded.DeleteOutline
        "sleep_timer" -> Icons.Rounded.Bedtime
        "trim" -> Icons.Rounded.ContentCut
        "download" -> Icons.Rounded.Download
        "share_card" -> Icons.Outlined.PhotoLibrary
        "dj_flow" -> Icons.Rounded.GraphicEq
        "play" -> Icons.Rounded.PlayArrow
        else -> Icons.Rounded.Apps
    }
}
