package com.alananasss.kittytune.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

// The previous Apple Music player background, kept alongside the newer fluid-shader face
// and offered as "Apple Music v2": a 150 dp blurred artwork that dims while paused, with
// the animated cover softened on top when the track has one, all under a dark
// vertical gradient that keeps controls readable.
@Composable
fun AppleMusicV2Background(
    artworkUrl: String?,
    animatedCoverUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = artworkUrl,
        transitionSpec = { fadeIn(tween(1200)) togetherWith fadeOut(tween(1200)) },
        label = "appleMusicV2BackgroundArt"
    ) { url ->
        Box(modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(150.dp)
                    .alpha(if (isPlaying) 0.78f else 0.55f)
            )

            // Animated (video) artwork overlay - softened so it reads as living ambience
            // behind the lyrics and controls rather than competing with them.
            if (!animatedCoverUrl.isNullOrBlank()) {
                com.alananasss.kittytune.ui.player.cover.CanvasVideo(
                    canvasUrl = animatedCoverUrl,
                    isPlaying = isPlaying,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(50.dp)
                        .alpha(0.85f)
                )
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.45f))
                        )
                    )
            )
        }
    }
}
