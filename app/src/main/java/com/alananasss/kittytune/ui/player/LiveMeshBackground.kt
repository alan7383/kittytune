package com.alananasss.kittytune.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.runtime.remember

// The "Live Mesh" background (mini style): three overlapping, saturated, heavily blurred copies
// of the artwork rotating at different speeds and directions, scaled past the viewport so no
// edge ever shows. Ported in spirit from the reference mini-style LIVE_MESH implementation.
//
// @param thumbnailUrl low-res artwork works best here (128 px): everything is blurred anyway
//   and the small request loads long before the full player is interactive.
@Composable
fun LiveMeshBackground(
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
    contentScrim: Boolean = true,
) {
    val context = LocalContext.current

    val infiniteTransition = rememberInfiniteTransition(label = "liveMeshRotation")
    val anchorRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 80_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "anchorRotation"
    )
    val fastRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 40_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fastRotation"
    )
    val slowRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 60_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "slowRotation"
    )

    Box(modifier = modifier.fillMaxSize()) {
        if (thumbnailUrl != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.7f
                        scaleY = 1.7f
                    }
            ) {
                val saturationMatrix = remember {
                    ColorMatrix().apply { setToSaturation(1.8f) }
                }
                val colorFilter = remember(saturationMatrix) { ColorFilter.colorMatrix(saturationMatrix) }
                val model = remember(thumbnailUrl) {
                    ImageRequest.Builder(context)
                        .data(thumbnailUrl)
                        .size(128, 128)
                        .build()
                }

                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = colorFilter,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(100.dp)
                        .graphicsLayer { rotationZ = anchorRotation }
                )
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = colorFilter,
                    alignment = Alignment.TopStart,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(120.dp)
                        .graphicsLayer {
                            rotationZ = fastRotation
                            alpha = 0.6f
                        }
                )
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = colorFilter,
                    alignment = Alignment.BottomEnd,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(120.dp)
                        .graphicsLayer {
                            rotationZ = slowRotation
                            alpha = 0.5f
                        }
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
            }
        }
        if (contentScrim) {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f))
                    )
                )
            )
        }
    }
}
