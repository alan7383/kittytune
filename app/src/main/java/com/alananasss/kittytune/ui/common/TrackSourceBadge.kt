package com.alananasss.kittytune.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.TrackSourceBadgeStyle
import com.alananasss.kittytune.domain.Track

enum class AudioSourceType(
    val id: String,
    val displayName: String,
    @DrawableRes val iconRes: Int?,
    val inlineWidth: Dp,
    val inlineHeight: Dp,
) {
    SOUNDCLOUD("soundcloud", "SoundCloud", R.drawable.ic_soundcloud, inlineWidth = 20.dp, inlineHeight = 9.dp),
    YOUTUBE("youtube", "YouTube", R.drawable.ic_logo_youtube, inlineWidth = 16.dp, inlineHeight = 11.dp),
    YOUTUBE_MUSIC("youtube_music", "YouTube Music", R.drawable.ic_logo_youtube_music_colored, inlineWidth = 12.dp, inlineHeight = 12.dp),
    SPOTIFY("spotify", "Spotify", R.drawable.ic_logo_spotify, inlineWidth = 12.dp, inlineHeight = 12.dp),
    DEEZER("deezer", "Deezer", R.drawable.ic_logo_deezer, inlineWidth = 13.dp, inlineHeight = 12.dp),
    TIDAL("tidal", "TIDAL", R.drawable.ic_logo_tidal, inlineWidth = 12.dp, inlineHeight = 12.dp),
    QOBUZ("qobuz", "Qobuz", R.drawable.ic_logo_qobuz, inlineWidth = 12.dp, inlineHeight = 12.dp),
    VK("vk", "VK", R.drawable.ic_vk, inlineWidth = 12.dp, inlineHeight = 12.dp),
    LOCAL("local", "Local", null, inlineWidth = 13.dp, inlineHeight = 12.dp);

    companion object {
        fun fromTrack(track: Track?, resolvedSource: String? = null): AudioSourceType {
            if (track == null) return SOUNDCLOUD
            if (track.id < 0 && track.source != "youtube" && track.source != "youtube_music") {
                return LOCAL
            }
            val key = resolvedSource?.lowercase()
                ?: track.source?.lowercase()
                ?: when {
                    track.permalink?.startsWith("deezer:") == true -> "deezer"
                    track.permalink?.startsWith("tidal:") == true -> "tidal"
                    track.permalink?.startsWith("qobuz:") == true -> "qobuz"
                    track.permalink?.startsWith("spotify:") == true -> "spotify"
                    track.permalinkUrl?.contains("youtube.com") == true || track.permalinkUrl?.contains("youtu.be") == true -> "youtube"
                    else -> "soundcloud"
                }

            return when {
                key == "youtube_music" || key.contains("youtube_music") || key == "ytm" -> YOUTUBE_MUSIC
                key == "youtube" || key.contains("youtube") || key == "yt" -> YOUTUBE
                key == "soundcloud" || key.contains("soundcloud") || key == "sc" -> SOUNDCLOUD
                key.contains("spotify") -> SPOTIFY
                key.contains("deezer") -> DEEZER
                key.contains("tidal") -> TIDAL
                key.contains("qobuz") -> QOBUZ
                key == "vk" || key.contains("vk") -> VK
                key == "local" || key.contains("local") -> LOCAL
                else -> SOUNDCLOUD
            }
        }
    }
}

/**
 * Modern, configurable badge displaying the source platform (YouTube, SoundCloud, Deezer, etc.)
 * of a given track or playing stream on Android.
 */
@Composable
fun TrackSourceBadge(
    track: Track?,
    resolvedSource: String? = null,
    modifier: Modifier = Modifier,
    overrideStyle: TrackSourceBadgeStyle? = null,
) {
    if (track == null) return
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    val enabled by prefs.getFullPlayerSourceIndicatorEnabledFlow().collectAsState(initial = prefs.getFullPlayerSourceIndicatorEnabled())
    if (!enabled) return
    val badgeStyle by prefs.getTrackSourceBadgeStyleFlow().collectAsState(initial = prefs.getTrackSourceBadgeStyle())
    val effectiveStyle = overrideStyle ?: badgeStyle

    if (effectiveStyle == TrackSourceBadgeStyle.HIDDEN) return

    val sourceType = remember(track.id, track.source, resolvedSource) {
        AudioSourceType.fromTrack(track, resolvedSource)
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.5.dp)
        ) {
            if (effectiveStyle == TrackSourceBadgeStyle.ICON_AND_TEXT || effectiveStyle == TrackSourceBadgeStyle.ICON_ONLY) {
                if (sourceType.iconRes != null) {
                    val effectiveTint = when (sourceType) {
                        AudioSourceType.SOUNDCLOUD -> Color(0xFFFF5500)
                        AudioSourceType.TIDAL, AudioSourceType.QOBUZ -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> Color.Unspecified
                    }
                    Icon(
                        painter = painterResource(sourceType.iconRes),
                        contentDescription = sourceType.displayName,
                        tint = effectiveTint,
                        modifier = Modifier.size(width = sourceType.inlineWidth, height = sourceType.inlineHeight)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = sourceType.displayName,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(width = sourceType.inlineWidth, height = sourceType.inlineHeight)
                    )
                }
            }

            if (effectiveStyle == TrackSourceBadgeStyle.ICON_AND_TEXT || effectiveStyle == TrackSourceBadgeStyle.TEXT_ONLY) {
                Text(
                    text = sourceType.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Clean, circular emblem badge overlaid directly on album artwork (e.g. MiniPlayer).
 */
@Composable
fun TrackSourceCoverBadge(
    track: Track?,
    resolvedSource: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    iconSize: Dp = 10.dp,
) {
    if (track == null) return
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    val enabled by prefs.getFullPlayerSourceIndicatorEnabledFlow().collectAsState(initial = prefs.getFullPlayerSourceIndicatorEnabled())
    if (!enabled) return
    val badgeStyle by prefs.getTrackSourceBadgeStyleFlow().collectAsState(initial = prefs.getTrackSourceBadgeStyle())
    if (badgeStyle == TrackSourceBadgeStyle.HIDDEN) return

    val sourceType = remember(track.id, track.source, resolvedSource) {
        AudioSourceType.fromTrack(track, resolvedSource)
    }

    Surface(
        shape = androidx.compose.foundation.shape.CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shadowElevation = 2.dp,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.size(size)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (sourceType.iconRes != null) {
                val effectiveTint = when (sourceType) {
                    AudioSourceType.SOUNDCLOUD -> Color(0xFFFF5500)
                    AudioSourceType.TIDAL, AudioSourceType.QOBUZ -> MaterialTheme.colorScheme.onSurface
                    else -> Color.Unspecified
                }
                val badgeWidth = when (sourceType) {
                    AudioSourceType.SOUNDCLOUD -> (iconSize.value * 1.3f).dp
                    AudioSourceType.YOUTUBE -> (iconSize.value * 1.2f).dp
                    else -> iconSize
                }
                val badgeHeight = when (sourceType) {
                    AudioSourceType.SOUNDCLOUD -> (iconSize.value * 0.6f).dp
                    AudioSourceType.YOUTUBE -> (iconSize.value * 0.85f).dp
                    else -> iconSize
                }
                Icon(
                    painter = painterResource(sourceType.iconRes),
                    contentDescription = sourceType.displayName,
                    tint = effectiveTint,
                    modifier = Modifier.size(width = badgeWidth, height = badgeHeight)
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = sourceType.displayName,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

