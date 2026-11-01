package com.alananasss.kittytune.ui.profile.pet

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.ui.widget.pet.PetActivity
import com.alananasss.kittytune.ui.widget.pet.PetBrain
import com.alananasss.kittytune.ui.widget.pet.PetFrames
import com.alananasss.kittytune.ui.widget.pet.PetLayers
import com.alananasss.kittytune.ui.widget.pet.PetSettings
import kotlinx.coroutines.delay

/**
 * The same pixel cat as the widget, drawn in Compose: wall, room, seasonal decor and the eight
 * stacked frame layers, tinted with the given look. [animate] = false shows the first frame only
 * (cheap enough for long lists of option previews).
 */
@Composable
fun PetSprite(
    activity: PetActivity,
    settings: PetSettings,
    modifier: Modifier = Modifier,
    room: String? = null,
    boopable: Boolean = false,
    label: String? = null,
    corner: Int = 28,
    animate: Boolean = true,
    onBoop: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var boopUntil by remember { mutableLongStateOf(0L) }
    var booping by remember { mutableStateOf(false) }
    // booped while dancing: a quick squee with the headphones kept on, like on the widget
    val shown = if (booping) (if (activity.isDance) PetActivity.BOOP_DANCE else PetActivity.BOOP) else activity
    val roomName = room ?: when (shown) {
        PetActivity.SLEEP -> "sleep"
        PetActivity.SAD -> "rain"
        PetActivity.DOZE, PetActivity.STARGAZE, PetActivity.NOD, PetActivity.SHOOTING_STAR -> "night"
        PetActivity.IVY_SPACE -> "space"
        else -> PetBrain.currentSlot().room
    }
    val wall = PetLayers.wallColor(roomName, settings)
    val wallColor = wall?.let { Color(it) } ?: MaterialTheme.colorScheme.secondaryContainer
    val dark = wall?.let { PetLayers.isDark(it) } ?: false
    val decor = remember(settings.seasonal, settings.roomDecor) {
        if (settings.roomDecor) PetBrain.decorFor(context, settings)?.let { PetFrames.decor[it] } else null
    }

    val frameList = remember(shown) { PetBrain.framesFor(shown) }
    var frame by remember(shown) { mutableIntStateOf(0) }
    if (animate) {
        LaunchedEffect(shown, settings.speed) {
            while (true) {
                // dancing while music plays: step exactly on each beat the listener hears
                val onBeat = if (shown.isDance) nextHeardBeatMs() else null
                delay(onBeat ?: (shown.frameMs * if (shown.isDance || shown == PetActivity.NY_COUNTDOWN) 1f else settings.speed.factor).toLong())
                frame = (frame + 1) % frameList.size
                if (booping && System.currentTimeMillis() > boopUntil) booping = false
            }
        }
    }
    val frameName = frameList[frame % frameList.size]
    val layers = remember(frameName, settings) { PetLayers.of(context, frameName, settings) }

    Box(
        modifier = modifier
            .aspectRatio(46f / 32f)
            .clip(RoundedCornerShape(corner.dp))
            .background(wallColor)
            .then(
                if (boopable) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    boopUntil = System.currentTimeMillis() + 2_000
                    booping = true
                    onBoop?.invoke()
                } else Modifier
            )
    ) {
        if (settings.roomDecor) {
            Image(painterResource(PetFrames.rooms.getValue(roomName)), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            if (roomName != "space") decor?.let { Image(painterResource(it), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        }
        layers.forEach { layer ->
            val tint = layer.tint?.let { ColorFilter.tint(Color(it), BlendMode.SrcAtop) }
            if (layer.bitmap != null) {
                Image(
                    bitmap = layer.bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    colorFilter = tint,
                    filterQuality = FilterQuality.None
                )
            } else if (layer.res != 0) {
                Image(
                    painter = painterResource(layer.res),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    colorFilter = layer.tint?.let { ColorFilter.tint(Color(it), BlendMode.SrcAtop) }
                )
            }
        }
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (dark) Color(0xFFF1ECFF) else Color(0xFF4A3448),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .background(if (dark) Color(0x26FFFFFF) else Color(0xB3FFFFFF), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}


/** Ms until the next beat heard (live beat tracker), or null when no music / no confident grid. */
private fun nextHeardBeatMs(): Long? = runCatching {
    val player = com.alananasss.kittytune.data.MusicManager.player
    if (!player.isPlaying) return null
    com.alananasss.kittytune.data.MusicManager.liveBeat.nextBeat(player.currentPosition, minAheadMs = 30)?.first
}.getOrNull()
