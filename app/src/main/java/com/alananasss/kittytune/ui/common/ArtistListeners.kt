package com.alananasss.kittytune.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.artist.ArtistReach

/**
 * An artist's monthly listeners, as the page's one-line measure of reach (ported from desktop, PR #77):
 * hidden until known, so the layout never jumps for artists Spotify does not know.
 */
@Composable
fun rememberArtistProfile(artistName: String?): ArtistReach.Profile? =
    produceState<ArtistReach.Profile?>(initialValue = null, artistName) {
        value = ArtistReach.profileOf(artistName)
    }.value

/** A banner for [artistName] from their streaming profile, or a member's for a duo; null before it is found and when there is none. */
@Composable
fun rememberArtistBanner(artistName: String?): String? =
    produceState<String?>(initialValue = null, artistName) {
        value = ArtistReach.bannerOf(artistName)
    }.value

@Composable
fun MonthlyListenersText(
    artistName: String?,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val profile = rememberArtistProfile(artistName)
    val listeners = profile?.monthlyListeners
    AnimatedVisibility(visible = listeners != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        if (listeners != null) {
            Text(
                stringResource(R.string.artist_monthly_listeners, listeners),
                style = style,
                fontWeight = FontWeight.Medium,
                color = color,
            )
        }
    }
}
