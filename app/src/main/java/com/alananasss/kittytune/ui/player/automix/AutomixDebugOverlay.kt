package com.alananasss.kittytune.ui.player.automix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.audio.automix.AutomixManager
import com.alananasss.kittytune.utils.makeTimeString

@Composable
fun AutomixDebugOverlay(
    currentPositionMs: Long,
    modifier: Modifier = Modifier,
) {
    val debugInfo by AutomixManager.automixDebugInfo.collectAsState()

    val mono = MaterialTheme.typography.labelSmall.copy(
        fontSize = 9.sp,
        fontFamily = FontFamily.Monospace,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(8.dp)
    ) {
        val dbg = debugInfo
        if (dbg != null) {
            Text(
                text = "AUTOMIX  ${dbg.status}",
                style = mono,
                color = Color.White
            )
            Text(
                text = "out: ${dbg.outBpm?.let { "%.1f bpm".format(it) } ?: "—"}" +
                    (dbg.outConfidence?.let { "  conf %.2f".format(it) } ?: "") +
                    (dbg.outMixOutMs?.takeIf { it > 0 }?.let { "  mixOut ${makeTimeString(it)}" } ?: ""),
                style = mono,
                color = Color.White.copy(alpha = 0.85f)
            )
            Text(
                text = "in:  ${dbg.inBpm?.let { "%.1f bpm".format(it) } ?: "—"}" +
                    (dbg.inConfidence?.let { "  conf %.2f".format(it) } ?: "") +
                    (dbg.inMixInMs?.takeIf { it > 0 }?.let { "  mixIn ${makeTimeString(it)}" } ?: ""),
                style = mono,
                color = Color.White.copy(alpha = 0.85f)
            )
            if (dbg.triggerTimeMs != null) {
                val remainingS = ((dbg.triggerTimeMs - currentPositionMs) / 1000).coerceAtLeast(0)
                Text(
                    text = "mix @ ${makeTimeString(dbg.triggerTimeMs)} (in ${remainingS}s)" +
                        (dbg.incomingStartMs?.let { "  from ${makeTimeString(it)}" } ?: "") +
                        (dbg.tempoRatio?.let { "  tempo ×%.3f".format(it) } ?: "") +
                        (dbg.pitchRatio?.takeIf { it != 1f }?.let { "  pitch ×%.3f".format(it) } ?: ""),
                    style = mono,
                    color = Color.White
                )
            }
        } else {
            Text(
                text = "AUTOMIX  standby",
                style = mono,
                color = Color.White
            )
            Text(
                text = "out: —",
                style = mono,
                color = Color.White.copy(alpha = 0.85f)
            )
            Text(
                text = "in:  —",
                style = mono,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}
