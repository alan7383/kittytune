package com.alananasss.kittytune.ui.musicimport

import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.musicimport.MusicApi

data class MusicPlatformVisual(
    val icon: ImageVector,
    @DrawableRes val logoRes: Int,
    @DrawableRes val likedArtworkRes: Int,
    val color: Color
)

fun MusicApi.visual(): MusicPlatformVisual = when (this) {
    MusicApi.SPOTIFY -> MusicPlatformVisual(
        icon = Icons.Rounded.MusicNote,
        logoRes = R.drawable.ic_logo_spotify,
        likedArtworkRes = R.drawable.ic_likes_spotify,
        color = Color(0xFF1DB954)
    )
    MusicApi.APPLE_MUSIC -> MusicPlatformVisual(
        icon = Icons.AutoMirrored.Rounded.QueueMusic,
        logoRes = R.drawable.ic_logo_apple_music,
        likedArtworkRes = R.drawable.ic_likes_apple_music,
        color = Color(0xFFFA243C)
    )
    MusicApi.YOUTUBE_MUSIC -> MusicPlatformVisual(
        icon = Icons.Rounded.PlayCircle,
        logoRes = R.drawable.ic_logo_youtube_music,
        likedArtworkRes = R.drawable.ic_logo_youtube_music,
        color = Color(0xFFFF0000)
    )
    MusicApi.DEEZER -> MusicPlatformVisual(
        icon = Icons.Rounded.Equalizer,
        logoRes = R.drawable.ic_logo_deezer,
        likedArtworkRes = R.drawable.ic_likes_deezer,
        color = Color(0xFFA238FF)
    )
    MusicApi.TIDAL -> MusicPlatformVisual(
        icon = Icons.Rounded.GraphicEq,
        logoRes = R.drawable.ic_logo_tidal,
        likedArtworkRes = R.drawable.ic_likes_tidal,
        color = Color(0xFF4E4E4E)
    )
    MusicApi.AMAZON_MUSIC -> MusicPlatformVisual(
        icon = Icons.Rounded.Cloud,
        logoRes = R.drawable.ic_logo_amazon_music,
        likedArtworkRes = R.drawable.ic_likes_amazon_music,
        color = Color(0xFF25D1DA)
    )
    MusicApi.BOOMPLAY -> MusicPlatformVisual(
        icon = Icons.Rounded.Album,
        logoRes = R.drawable.ic_logo_boomplay,
        likedArtworkRes = R.drawable.ic_logo_boomplay,
        color = Color(0xFF00B14F)
    )
}

fun MusicApi?.likedTracksArtwork(): Int =
    this?.visual()?.likedArtworkRes ?: R.drawable.ic_likes_spotify
