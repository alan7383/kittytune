package com.alananasss.kittytune.ui.yearlyplayback

import com.alananasss.kittytune.R
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Star
import androidx.compose.foundation.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import android.util.Log
import androidx.compose.ui.viewinterop.AndroidView
import app.rive.runtime.kotlin.RiveAnimationView
import com.alananasss.kittytune.data.yearlyplayback.Block

/**
 * Slide renderer for ArtBoard blocks powered by SoundCloud's authentic Rive vector engine.
 * Direct parity with SoundCloud's AnimationBlockKt.
 */
@Composable
fun AnimationBlock(
    riveAnimationView: RiveAnimationView,
    artBoard: Block.ArtBoard,
    isPaused: Boolean,
    modifier: Modifier = Modifier
) {
    // Track the last artboard name applied so we only switch artboards when truly navigating
    // to a new slide — NOT when toggling isPaused. Re-assigning artboardName resets the Rive
    // animation to frame 0, which was causing the story to restart every time the user released
    // a long-press hold.
    val lastArtboardRef = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }

    LaunchedEffect(artBoard.artboardId) {
        // New slide: apply the artboard and text runs, then play
        runCatching {
            riveAnimationView.fit = app.rive.runtime.kotlin.core.Fit.CONTAIN
            riveAnimationView.alignment = app.rive.runtime.kotlin.core.Alignment.CENTER
            riveAnimationView.artboardName = artBoard.artboardId
            lastArtboardRef.value = artBoard.artboardId
            artBoard.textRuns.forEach { run ->
                try {
                    riveAnimationView.setTextRunValue(run.key, run.value)
                } catch (e: Throwable) {
                    // Text run key may not exist in every artboard variant
                }
            }
            if (!isPaused) riveAnimationView.play()
        }.onFailure { err ->
            Log.w("AnimationBlock", "Error switching artboard to ${artBoard.artboardId}", err)
        }
    }

    LaunchedEffect(isPaused) {
        // Only pause/resume — never restart the animation
        runCatching {
            if (isPaused) riveAnimationView.pause() else riveAnimationView.play()
        }
    }

    AndroidView(
        factory = {
            (riveAnimationView.parent as? android.view.ViewGroup)?.removeView(riveAnimationView)
            riveAnimationView
        },
        onReset = { view ->
            (view.parent as? android.view.ViewGroup)?.removeView(view)
        },
        onRelease = { view ->
            (view.parent as? android.view.ViewGroup)?.removeView(view)
        },
        // update() is intentionally minimal — artboard switching and pause/resume are handled
        // in LaunchedEffects above to avoid restarting the animation on every recomposition.
        update = { _ -> },
        modifier = modifier.fillMaxSize()
    )
}

/**
 * Slide renderer for ArtBoard blocks fallback (used when offline without Rive assets).
 * Employs Material 3 tokens, dynamic typography, and fluid micro-animations.
 */
@Composable
fun ArtBoardSlide(
    block: Block.ArtBoard,
    modifier: Modifier = Modifier
) {
    val runMap = remember(block.textRuns) {
        block.textRuns.associate { it.key to it.value }
    }

    var visible by remember(block.artboardId) { mutableStateOf(false) }
    LaunchedEffect(block.artboardId) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400, easing = FastOutSlowInEasing)) +
                slideInVertically(tween(400, easing = FastOutSlowInEasing)) { it / 6 },
        modifier = modifier.fillMaxSize()
    ) {
        when {
            block.artboardId.contains("Intro", ignoreCase = true) -> {
                IntroArtboardContent(runMap)
            }
            block.artboardId.contains("Minutes", ignoreCase = true) -> {
                MinutesArtboardContent(runMap)
            }
            block.artboardId.contains("TopTrack", ignoreCase = true) -> {
                TopTrackArtboardContent(runMap, block)
            }
            block.artboardId.contains("TopArtist", ignoreCase = true) -> {
                TopArtistsArtboardContent(runMap)
            }
            block.artboardId.contains("Habit", ignoreCase = true) -> {
                HabitsArtboardContent(runMap)
            }
            else -> {
                GenericArtboardContent(runMap, block)
            }
        }
    }
}

@Composable
private fun IntroArtboardContent(runs: Map<String, String>) {
    val year = runs["year"] ?: "2025"
    val title = runs["title"] ?: "Your Year in Sound"
    val subtitle = runs["subtitle"] ?: "Here is everything that defined your musical year."

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Decorative Hero Icon
        Box(
            modifier = Modifier
                .size(110.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFF5500),
                            Color(0xFFFF2255).copy(alpha = 0.6f),
                            Color.Transparent
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Headphones,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(54.dp)
            )
        }

        Spacer(Modifier.height(32.dp))

        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.15f),
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Text(
                text = year,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }

        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun MinutesArtboardContent(runs: Map<String, String>) {
    val minutes = runs["minutes"] ?: "0"
    val plays = runs["plays"] ?: "0"
    val uniqueTracks = runs["unique_tracks"] ?: "0"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Total Listening Time",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.75f)
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = minutes,
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp, lineHeight = 78.sp),
            fontWeight = FontWeight.Black,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Text(
            text = "MINUTES",
            style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 4.sp),
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFFFF5500)
        )

        Spacer(Modifier.height(36.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "Tracks Played",
                value = plays,
                icon = Icons.Rounded.MusicNote,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Unique Tracks",
                value = uniqueTracks,
                icon = Icons.Rounded.GraphicEq,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TopTrackArtboardContent(runs: Map<String, String>, block: Block.ArtBoard) {
    val title = runs["title"] ?: block.backgroundTrack?.title ?: "Top Song"
    val artist = runs["artist"] ?: block.backgroundTrack?.user?.name ?: "Artist"
    val plays = runs["plays"] ?: ""
    val artwork = runs["artwork"] ?: block.backgroundTrack?.artworkUrlTemplate.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFFFF5500),
            modifier = Modifier.padding(bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Star, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "YOUR #1 SONG",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
        }

        // Album Artwork
        Surface(
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 16.dp,
            modifier = Modifier
                .size(240.dp)
                .border(2.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
        ) {
            if (artwork.isNotEmpty()) {
                AsyncImage(
                    model = artwork.replace("-large", "-t500x500"),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF2B2B2B)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(80.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = artist,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (plays.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.15f)
            ) {
                Text(
                    text = plays,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun TopArtistsArtboardContent(runs: Map<String, String>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "YOUR TOP ARTISTS",
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF5500)
        )
        Text(
            text = "The Voices You Loved",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            for (i in 1..5) {
                val name = runs["artist_$i"] ?: continue
                val plays = runs["plays_$i"].orEmpty()
                val art = runs["artwork_$i"].orEmpty()

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = if (i == 1) 0.2f else 0.1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#$i",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = if (i == 1) Color(0xFFFF5500) else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.width(32.dp)
                        )

                        if (art.isNotEmpty()) {
                            AsyncImage(
                                model = art,
                                contentDescription = name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(Modifier.width(10.dp))
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Person, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                        }

                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (i == 1) FontWeight.Bold else FontWeight.Medium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        if (plays.isNotEmpty()) {
                            Text(
                                text = plays,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitsArtboardContent(runs: Map<String, String>) {
    val completion = runs["completion_rate"] ?: "85% completion rate"
    val tracks = runs["unique_tracks"] ?: "Songs heard"
    val vibe = runs["vibe"] ?: "The Passionate Explorer"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.15f),
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Text(
                text = "LISTENING PERSONALITY",
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        Text(
            text = vibe,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            color = Color(0xFFFF8833),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White.copy(alpha = 0.1f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Check, null, tint = Color(0xFF00E676), modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = completion,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.MusicNote, null, tint = Color(0xFFFF5500), modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = tracks,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun GenericArtboardContent(runs: Map<String, String>, block: Block.ArtBoard) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = block.artboardId,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(20.dp))

        runs.forEach { (k, v) ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = k, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
                    Text(text = v, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.12f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text(text = value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
        }
    }
}

/**
 * Slide renderer for Block.SavePlaylist matching SoundCloud parity.
 */
@Composable
fun SavePlaylistSlide(
    block: Block.SavePlaylist,
    onSaveToggle: (Block.SavePlaylist) -> Unit,
    onNavigateToPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val extractedYear = remember(block.text, block.activeTitle) {
        (block.text + " " + block.activeTitle).filter { it.isDigit() }.take(4).ifEmpty { "2025" }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Full bleed background art extending edge-to-edge
        val bgUrl = block.backgroundImageUrl.replace("{size}", "t500x500")
        if (bgUrl.isNotEmpty()) {
            AsyncImage(
                model = bgUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Subtle dark scrim gradient to ensure rich contrast for controls and text
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.5f),
                                Color.Black.copy(alpha = 0.25f),
                                Color.Black.copy(alpha = 0.65f)
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(top = 64.dp, bottom = 28.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Title text ("Here's your 2025 Playback") placed ABOVE artwork, matching decompiled SavePlaylistScreenKt
            Text(
                text = block.text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .width(236.dp)
                    .padding(bottom = 20.dp)
            )

            // Playlist Artwork (236dp with 16dp rounded corners, clickable to navigate to playlist)
            Surface(
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .size(236.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onNavigateToPlaylist(block.playlistUrn) }
            ) {
                val artworkUrl = block.playlistArtwork
                    .replace("{size}", "t500x500")
                    .replace("-large", "-t500x500")

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Fallback cover if image is missing or loading
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFFF5500), Color(0xFF8E24AA), Color(0xFF1E88E5))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Headphones,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = extractedYear,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }

                    if (artworkUrl.isNotBlank() && artworkUrl != "null") {
                        AsyncImage(
                            model = artworkUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // Save / Liked Outlined Button (matching decompiled SoundCloud ButtonStyle.Outlined with ic_actions_heart)
            AnimatedContent(
                targetState = block.isLiked,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(200, delayMillis = 60)) +
                            scaleIn(initialScale = 0.94f, animationSpec = tween(200, delayMillis = 60)))
                        .togetherWith(fadeOut(animationSpec = tween(90)))
                },
                label = "playlistSaveToggle"
            ) { isLiked ->
                val buttonText = if (isLiked) {
                    if (block.activeTitle.isNotBlank()) block.activeTitle else "Saved to Library"
                } else {
                    if (block.inactiveTitle.isNotBlank()) block.inactiveTitle else "Save to Library"
                }

                OutlinedButton(
                    onClick = { onSaveToggle(block) },
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isLiked) Color.White.copy(alpha = 0.15f) else Color.Transparent,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.height(48.dp)
                ) {
                    Image(
                        painter = painterResource(
                            if (isLiked) R.drawable.ic_actions_heart_active else R.drawable.ic_actions_heart_light
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = buttonText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Slide renderer for Block.OpenInsights matching SoundCloud parity.
 */
@Composable
fun OpenInsightsSlide(
    block: Block.OpenInsights,
    onOpenStats: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val bgUrl = block.backgroundImageUrl.replace("{size}", "t500x500")
        if (bgUrl.isNotEmpty()) {
            AsyncImage(
                model = bgUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(top = 64.dp, bottom = 28.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFFFF5500), Color.Transparent)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = block.title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(14.dp))

            Text(
                text = block.text,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(36.dp))

            Button(
                onClick = onOpenStats,
                shape = ButtonDefaults.shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF5500),
                    contentColor = Color.White
                ),
                modifier = Modifier.height(52.dp)
            ) {
                Text(text = stringResource(R.string.yearly_playback_explore_detailed_stats), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}
