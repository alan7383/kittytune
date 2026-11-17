package com.alananasss.kittytune.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.audio.haptics.HapticUtil
import com.alananasss.kittytune.audio.usb.UsbAudioManager
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.local.PlayerPreferences

// The "show codec on player" chip (mini style): a small rounded badge between the title and the
// progress bar that names what is actually playing, e.g. "OPUS • 160 kbps".
//
// The format comes from the live ExoPlayer tracks selection (mime + bitrate from the
// Media3 Format). When USB bit-perfect is active the DAC badge takes its place, since that
// is the more interesting fact about the signal at that moment.
//
// Tapping the badge opens the live audio path diagnostics ([AudioPathDiagnosticSheet]);
// pass [onClick] to override that behaviour.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CodecBadge(
    viewModel: PlayerViewModel,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var showDiagnostics by remember { mutableStateOf(false) }

    // The badge can disappear (buffering / hidden by a setting), and an open
    // diagnostics window must live independently - so we host it before the early returns.
    if (showDiagnostics) {
        AudioPathDiagnosticSheet(
            visible = true,
            onDismiss = { showDiagnostics = false },
            playerViewModel = viewModel
        )
    }

    val prefs = remember { PlayerPreferences(context) }
    val showCodec by remember(prefs) { prefs.showCodecOnPlayerFlow() }
        .collectAsState(initial = prefs.getShowCodecOnPlayer())
    if (!showCodec || viewModel.playbackDeviceLabel != null) return

    val format by MusicManager.currentAudioFormatFlow.collectAsState()
    val usbStatus by UsbAudioManager.status.collectAsState()

    val isBuffering = viewModel.isLoading

    val formatText = remember(format) {
        val f = format ?: return@remember ""
        val codecStr = f.sampleMimeType?.substringAfter("audio/")?.uppercase() ?: ""
        val bitrateStr = if (f.bitrate > 0) "${f.bitrate / 1000} kbps" else ""
        val rateStr = if (f.sampleRate > 0) "${f.sampleRate / 1000.0}".removeSuffix(".0") + " kHz" else ""
        listOf(codecStr, bitrateStr, rateStr).filter { it.isNotEmpty() }.joinToString(" • ")
    }

    val usbBadge = if (usbStatus.bitPerfectActive && prefs.getUsbShowStatus()) {
        UsbAudioManager.badgeText()
    } else null

    val badgeText = usbBadge ?: formatText
    if (badgeText.isBlank() && !isBuffering) return

    FilledTonalButton(
        onClick = {
            HapticUtil.performUIHaptic(context)
            if (onClick != null) onClick() else showDiagnostics = true
        },
        modifier = modifier.height(22.dp),
        shapes = ButtonDefaults.shapes(shape = CircleShape),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = textColor.copy(alpha = 0.08f),
            contentColor = textColor.copy(alpha = 0.8f),
        ),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        AnimatedContent(
            targetState = isBuffering,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "QualityBufferSwitcher"
        ) { buffering ->
            if (buffering) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        strokeWidth = 1.5.dp,
                        modifier = Modifier.size(10.dp),
                        color = textColor.copy(alpha = 0.8f)
                    )
                }
            } else {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        fontSize = 10.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
