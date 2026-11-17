package com.alananasss.kittytune.ui.yearlyplayback

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.yearlyplayback.YearlyPlaybackTarget

/**
 * Top Banner for the Yearly Playback Story screen.
 *
 * Displays SoundCloud ✕ KittyTune collaboration branding alongside
 * Ghost action buttons (Share and Close).
 */
@Composable
fun TopBanner(
    target: YearlyPlaybackTarget,
    onShare: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    showBranding: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (showBranding) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                // Official SoundCloud Wordmark Logo
                Image(
                    painter = painterResource(R.drawable.ic_logo_soundcloud_light),
                    contentDescription = "SoundCloud",
                    modifier = Modifier.height(13.5.dp)
                )

                // Collaboration separator "×"
                Text(
                    text = "×",
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 1.dp)
                )

                // KittyTune Brand: Pixel Cat Logo (tight bounding box) + KittyTune Name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_kittytune_logo_tight),
                        contentDescription = "KittyTune",
                        modifier = Modifier.height(14.dp)
                    )
                    Text(
                        text = "KittyTune",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 13.sp,
                        letterSpacing = 0.2.sp
                    )
                }

                if (target == YearlyPlaybackTarget.CREATOR) {
                    Spacer(Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Creator",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.width(1.dp))
        }

        // Actions: Share & Close (matching SoundCloud official TopBanner Ghost style buttons)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = onShare,
                modifier = Modifier.size(40.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_actions_share_light),
                    contentDescription = stringResource(R.string.share_card_send),
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier.size(40.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_actions_close_light),
                    contentDescription = stringResource(R.string.btn_close),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
