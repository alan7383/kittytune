package com.alananasss.kittytune.ui.player

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import com.alananasss.kittytune.R
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.common.hapticClickable
import com.alananasss.kittytune.ui.common.rememberAppHaptics

@Composable
internal fun TrackArtistText(
    track: Track,
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    text: String = track.displayArtist.ifBlank { stringResource(R.string.unknown_artist) },
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
    marquee: Boolean = false,
    onNavigate: () -> Unit = {}
) {
    val artists = track.artists.orEmpty()
        .filter { it.name.isNotBlank() }
        .distinctBy { it.id.ifBlank { it.uri.orEmpty().ifBlank { it.name.trim() } } }

    val haptics = rememberAppHaptics()
    val hasMultipleArtists = artists.size > 1
    val artistText = if (hasMultipleArtists) {
        val linkStyles = TextLinkStyles(
            focusedStyle = SpanStyle(textDecoration = TextDecoration.Underline),
            pressedStyle = SpanStyle(textDecoration = TextDecoration.Underline)
        )
        buildAnnotatedString {
            artists.forEachIndexed { index, artist ->
                if (index > 0) append(", ")
                withLink(LinkAnnotation.Clickable("artist_$index", linkStyles) {
                    haptics.click()
                    onNavigate()
                    viewModel.navigateToTrackArtist(track, artist)
                }) {
                    append(artist.name.trim())
                }
            }
        }
    } else {
        AnnotatedString(text)
    }
    val artistModifier = if (hasMultipleArtists) modifier else modifier.hapticClickable {
        onNavigate()
        viewModel.navigateToTrackArtist(track)
    }
    if (marquee) {
        PremiumMarqueeText(artistText, modifier = artistModifier, style = style, color = color, textAlign = textAlign)
    } else {
        Text(
            text = artistText,
            modifier = artistModifier,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = overflow,
            textAlign = textAlign
        )
    }
}
