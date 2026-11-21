package com.alananasss.kittytune.ui.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.alananasss.kittytune.data.DataSaver
import com.alananasss.kittytune.data.cover.FeedVisual
import com.alananasss.kittytune.data.cover.FeedVisualResolver
import com.alananasss.kittytune.data.cover.FeedVisualSource
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.playbackKey
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.player.cover.CanvasVideo

private val resolver = FeedVisualResolver()

@Composable
internal fun FeedVisualArtwork(track: Track, active: Boolean) {
    val context = LocalContext.current
    val prefs = remember(context) { PlayerPreferences(context) }
    val animated by prefs.getAnimatedCoversFlow().collectAsState(initial = prefs.getAnimatedCoversEnabled())
    val sources = buildList {
        if (animated) add(FeedVisualSource.ANIMATED_COVER)
        if (prefs.getFeedVideoSamplesEnabled()) add(FeedVisualSource.YOUTUBE)
    }

    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = track.fullResArtwork,
            contentDescription = track.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (active && sources.isNotEmpty() && !DataSaver.isActive(context)) {
            key(track.playbackKey(), sources) {
                var visual by remember { mutableStateOf<FeedVisual?>(null) }
                var excluded by remember { mutableStateOf(emptySet<FeedVisualSource>()) }

                LaunchedEffect(excluded) {
                    visual = resolver.resolve(track, sources, excluded)
                }
                visual?.let { art ->
                    key(art) {
                        CanvasVideo(
                            canvasUrl = art.url,
                            isPlaying = true,
                            modifier = Modifier.fillMaxSize(),
                            clipStartMs = art.startMs,
                            clipEndMs = art.endMs,
                            useYouTubeHeaders = art.source == FeedVisualSource.YOUTUBE,
                            onError = {
                                visual = null
                                excluded = excluded + art.source
                            },
                        )
                    }
                }
            }
        }
    }
}
