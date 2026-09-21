package com.alananasss.kittytune.ui.share

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import com.alananasss.kittytune.R

/**
 * A colour pairing for a share card, taken from the artwork.
 *
 * Offered as a small set of variants rather than one fixed look: the same cover reads very
 * differently against its own brightest and darkest shades, and which one carries the artwork
 * is a matter of taste, not something the palette can decide.
 */
data class ShareCardStyle(
    val background: Color,
    val onBackground: Color,
) {
    companion object {
        /** The look used when the artwork yields nothing usable. */
        val Neutral = ShareCardStyle(
            background = Color(0xFF121212),
            onBackground = Color.White,
        )
    }
}

/**
 * The card itself, laid out at a fixed aspect so it renders the same whatever the screen.
 *
 * 9:16 matches what a story wants; anything else is letterboxed by the target app, which crops
 * the artwork unpredictably. Everything here is drawn from values the player already holds, so
 * the card can be produced without a network round trip of its own.
 */
@Composable
fun ShareCard(
    artwork: Bitmap?,
    title: String,
    artist: String,
    style: ShareCardStyle,
    modifier: Modifier = Modifier,
    qrCover: Bitmap? = null,
    lyrics: List<String>? = null,
) {
    Box(
        modifier = modifier
            .aspectRatio(9f / 16f)
            .background(
                Brush.verticalGradient(
                    listOf(
                        style.background,
                        style.background.copy(alpha = 0.82f).compositeOverBlack(),
                    )
                )
            )
    ) {
        if (!lyrics.isNullOrEmpty()) {
            LyricsBody(
                artwork = artwork,
                title = title,
                artist = artist,
                style = style,
                lines = lyrics,
            )
        } else Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // The QR cover replaces the artwork rather than sitting beside it: the point is that
            // the cover is the code. It carries its own light ground, which is why it is not
            // tinted with the card's colours - a scanner needs that contrast more than the card
            // needs the match.
            val shown = qrCover ?: artwork
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (qrCover != null) Color.White else style.onBackground.copy(alpha = 0.06f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (shown != null) {
                    Image(
                        bitmap = shown.asImageBitmap(),
                        contentDescription = null,
                        contentScale = if (qrCover != null) ContentScale.Fit else ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    )
                }
            }

            Text(
                text = title,
                color = style.onBackground,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 28.dp),
            )
            Text(
                text = artist,
                color = style.onBackground.copy(alpha = 0.72f),
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_kittytune_logo),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "KittyTune",
                color = style.onBackground.copy(alpha = 0.78f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Flattens a translucent colour onto black.
 *
 * The gradient's lower stop has to be an opaque colour: the card is captured onto a bitmap with
 * no backdrop, so a translucent stop would carry its alpha into the exported file and show
 * whatever sits behind it in the app that opens it.
 */
private fun Color.compositeOverBlack(): Color =
    Color(
        red = red * alpha,
        green = green * alpha,
        blue = blue * alpha,
        alpha = 1f,
    )

/**
 * The lyrics face of the card: the song named small at the top, its words given the space.
 *
 * Inverted against the cover layout on purpose. A lyric is shared because of what it says, so
 * the artwork steps back to a thumbnail that identifies the track and the lines take the middle
 * at a size meant to be read across a feed.
 */
@Composable
private fun LyricsBody(
    artwork: Bitmap?,
    title: String,
    artist: String,
    style: ShareCardStyle,
    lines: List<String>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp, vertical = 56.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(style.onBackground.copy(alpha = 0.08f))
            ) {
                if (artwork != null) {
                    Image(
                        bitmap = artwork.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
            Column {
                Text(
                    text = title,
                    color = style.onBackground,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = artist,
                    color = style.onBackground.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            lines.forEach { line ->
                Text(
                    text = line,
                    color = style.onBackground,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 36.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
