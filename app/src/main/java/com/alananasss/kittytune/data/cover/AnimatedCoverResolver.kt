package com.alananasss.kittytune.data.cover

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

data class ResolvedAnimatedCover(
    val squareUrl: String? = null,
    val tallUrl: String? = null,
)

object AnimatedCoverResolver {

    suspend fun resolve(
        title: String,
        artist: String,
        album: String? = null,
        durationSeconds: Int? = null,
        isrc: String? = null,
    ): ResolvedAnimatedCover = withContext(Dispatchers.IO) {
        android.util.Log.d("AnimatedCoverResolver", "Resolving animated cover: title='$title', artist='$artist', album='$album', isrc='$isrc'")
        if (title.isBlank()) return@withContext ResolvedAnimatedCover()

        // 1. Check Apple Music in parallel (Square and Tall)
        val (appleSquare, appleTall) = coroutineScope {
            val squareDeferred = async {
                AppleMusicCanvasProvider.getBySongArtist(
                    song = title,
                    artist = artist,
                    album = album,
                    isrc = isrc,
                    durationSeconds = durationSeconds,
                    preferredAspect = AppleMusicCanvasProvider.CanvasAspectPreference.SQUARE
                )?.animated
            }
            val tallDeferred = async {
                AppleMusicCanvasProvider.getBySongArtist(
                    song = title,
                    artist = artist,
                    album = album,
                    isrc = isrc,
                    durationSeconds = durationSeconds,
                    preferredAspect = AppleMusicCanvasProvider.CanvasAspectPreference.TALL
                )?.animated
            }
            squareDeferred.await() to tallDeferred.await()
        }

        if (appleSquare != null) {
            android.util.Log.d("AnimatedCoverResolver", "Resolved via Apple Music: square=$appleSquare, tall=$appleTall")
            return@withContext ResolvedAnimatedCover(
                squareUrl = appleSquare,
                tallUrl = appleTall ?: appleSquare
            )
        }

        // 2. Fallback to TIDAL
        val tidalSquare = TidalAnimatedCoverProvider.resolveAnimatedArtwork(
            title = title,
            artist = artist,
            album = album,
            durationSeconds = durationSeconds
        )
        if (tidalSquare != null) {
            android.util.Log.d("AnimatedCoverResolver", "Resolved via TIDAL: square=$tidalSquare")
            return@withContext ResolvedAnimatedCover(
                squareUrl = tidalSquare,
                tallUrl = tidalSquare
            )
        }

        android.util.Log.d("AnimatedCoverResolver", "No animated cover found for '$title' by '$artist'")
        ResolvedAnimatedCover()
    }
}
