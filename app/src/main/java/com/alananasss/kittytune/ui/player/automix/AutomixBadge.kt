package com.alananasss.kittytune.ui.player.automix

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.audio.automix.AutomixManager

@Composable
fun AutomixBadge(
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val isAutomixing by AutomixManager.isAutomixing.collectAsState()
    val automixDebug by AutomixManager.automixDebugInfo.collectAsState()
    val mixBeatsLeft by AutomixManager.mixBeatsLeft.collectAsState()
    val isCrossfading = com.alananasss.kittytune.data.MusicManager.isCrossfadingOut
    val beats = mixBeatsLeft

    // Dynamic tempo-synced beat period (ms) based on outgoing track BPM
    val mixBeatMs = automixDebug?.outBpm?.takeIf { it > 0f }?.let { 60_000f / it } ?: 500f

    // Only show badge when countdown is active, automixing, or crossfading
    val visible = isAutomixing || isCrossfading || (beats != null && beats > 0)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(400)),
        exit = fadeOut(animationSpec = tween(400)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(textColor.copy(alpha = 0.12f))
                .border(
                    width = 1.dp,
                    color = textColor.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            if (isAutomixing || isCrossfading) {
                val infiniteTransition = rememberInfiniteTransition(label = "CrossfadePulse")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.35f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "CrossfadeAlpha"
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = if (isAutomixing) Icons.Rounded.GraphicEq else Icons.Rounded.Repeat,
                        contentDescription = if (isAutomixing) "Automixing" else "Crossfading",
                        tint = textColor.copy(alpha = alpha),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = stringResource(if (isAutomixing) R.string.automixing else R.string.crossfading),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        ),
                        color = textColor.copy(alpha = alpha),
                        maxLines = 1,
                    )
                }
            } else if (beats != null) {
                val beatTransition = rememberInfiniteTransition(label = "MixCountdownBeat")
                val beatAlpha by beatTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 0.35f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(mixBeatMs.toInt().coerceIn(200, 1000), easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "MixCountdownAlpha"
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = textColor.copy(alpha = beatAlpha),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = stringResource(R.string.automix_mix_in, beats),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp
                        ),
                        color = textColor.copy(alpha = beatAlpha),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
