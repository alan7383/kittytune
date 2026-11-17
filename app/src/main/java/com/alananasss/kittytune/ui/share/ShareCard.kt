package com.alananasss.kittytune.ui.share

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

// The wide share-card proportion: wide enough for a full lyric line.
private val CARD_ASPECT = 4f / 5f

// The card itself, laid out at a fixed aspect so it renders the same whatever the screen.
//
// The 4:5 wide proportion replaced the old 9:16 story cut: the card is wide and the lyric
// lines fit whole (the type even shrinks itself until they do), which reads far better in a
// feed than a tall strip with ellipsized words. Everything here is drawn from values the
// player already holds, so the card can be produced without a network round trip of its own.
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
            .aspectRatio(CARD_ASPECT)
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
                .padding(horizontal = 28.dp),
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
                    .fillMaxWidth(0.82f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(18.dp))
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
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 22.dp),
            )
            Text(
                text = artist,
                color = style.onBackground.copy(alpha = 0.72f),
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        // The KittyTune mark sits in the card corner on each face:
        // bottom-left on the lyrics card, centred under the track card.
        Row(
            modifier = Modifier
                .align(if (!lyrics.isNullOrEmpty()) Alignment.BottomStart else Alignment.BottomCenter)
                .padding(horizontal = 28.dp, vertical = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_kittytune_logo),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "KittyTune",
                color = style.onBackground.copy(alpha = 0.78f),
                fontSize = 13.sp,
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

// The lyrics face of the card: the track in a compact header with its artwork,
// its words given the rest of the card at a size meant to be read across a feed.
//
// The lines are guaranteed to fit: the type starts at the tiered size for the number of lines
// and then shrinks itself (via [androidx.compose.ui.text.TextLayoutResult.didOverflow]) until
// nothing is ellipsized - a whole verse shares the card instead of being cut into "…".
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
            .padding(horizontal = 28.dp, vertical = 30.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(style.onBackground.copy(alpha = 0.08f))
            ) {
                if (artwork != null) {
                    Image(
                        bitmap = artwork.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(52.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = style.onBackground,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = artist,
                    color = style.onBackground.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // More selected lines -> smaller starting type; the auto-fit below then takes care
            // of whatever still overflows, so the words are always shown in full.
            val baseSize = when {
                lines.size <= 2 -> 27.sp
                lines.size <= 3 -> 24.sp
                lines.size <= 5 -> 21.sp
                else -> 17.sp
            }
            var fitScale by remember(lines) { mutableFloatStateOf(1f) }
            val lineH = baseSize * 1.28f * fitScale
            lines.forEach { line ->
                Text(
                    text = line,
                    color = style.onBackground,
                    fontSize = baseSize * fitScale,
                    fontWeight = FontWeight.Bold,
                    lineHeight = lineH,
                    // Two visual lines per lyric line at most; anything longer shrinks the
                    // type instead of being ellipsized - "the text fits in full" is the point
                    // of this card.
                    maxLines = 2,
                    overflow = TextOverflow.Clip,
                    onTextLayout = { result ->
                        if (result.hasVisualOverflow && fitScale > 0.5f) {
                            fitScale = (fitScale * 0.92f).coerceAtLeast(0.5f)
                        }
                    },
                )
            }
        }
    }
}
