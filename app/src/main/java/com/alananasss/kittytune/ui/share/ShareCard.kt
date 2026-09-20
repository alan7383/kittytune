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
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(style.onBackground.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                if (artwork != null) {
                    Image(
                        bitmap = artwork.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
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
