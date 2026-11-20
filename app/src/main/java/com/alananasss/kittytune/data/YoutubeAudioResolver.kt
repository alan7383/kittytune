package com.alananasss.kittytune.data

import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.SongItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.net.URI
import java.util.concurrent.TimeUnit

internal object YoutubeAudioResolver {
    private val idPattern = Regex("[A-Za-z0-9_-]{11}")

    fun isYoutubeUrl(reference: String?): Boolean {
        val uri = runCatching { URI(reference?.trim().orEmpty()) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        return (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) &&
            (host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com"))
    }

    fun videoUrl(reference: String?): String? {
        val raw = reference?.trim().orEmpty()
        val value = raw.removePrefix("youtube:").removePrefix("ytm:")
        val id = if (idPattern.matches(value)) value else {
            if (!isYoutubeUrl(value)) return null
            runCatching { ServiceList.YouTube.streamLHFactory.fromUrl(value).id }.getOrNull()
        }
        return id?.takeIf(idPattern::matches)?.let { "https://www.youtube.com/watch?v=$it" }
    }

    suspend fun findSongs(
        recording: YoutubeFallbackMatcher.Recording,
        search: suspend (String) -> List<SongItem>,
    ): List<SongItem> {
        val songs = mutableListOf<SongItem>()
        for (query in YoutubeFallbackMatcher.queries(recording)) {
            try {
                songs += search(query)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // An unavailable search page does not rule out the other query.
            }
        }
        return rankSongs(recording, songs.distinctBy { it.id })
    }

    private suspend fun searchSongs(query: String): List<SongItem> =
        YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrThrow().items.filterIsInstance<SongItem>()

    fun rankSongs(recording: YoutubeFallbackMatcher.Recording, songs: List<SongItem>): List<SongItem> {
        val songsById = songs.associateBy { it.id }
        return YoutubeFallbackMatcher.rankedMatches(recording, songs.map {
            YoutubeFallbackMatcher.Candidate(it.id, it.title, it.artists.map { artist -> artist.name },
                it.duration?.toLong() ?: 0L, it.album?.name.orEmpty())
        }).mapNotNull { songsById[it.id] }
    }

    fun matchedVideos(recording: YoutubeFallbackMatcher.Recording, videos: List<StreamInfoItem>): List<String> =
        YoutubeFallbackMatcher.rankedMatches(recording, videos.map {
            YoutubeFallbackMatcher.Candidate(it.url, it.name, listOf(it.uploaderName.orEmpty()), it.duration)
        }).map { it.id }

    suspend fun resolveMatch(
        recording: YoutubeFallbackMatcher.Recording,
        resolve: suspend (String) -> String?,
    ): String? {
        val tried = mutableSetOf<String>()
        val songs = findSongs(recording, ::searchSongs).take(3).map { it.id }
        tryMatches(songs, tried, resolve)?.let { return it }

        val service = ServiceList.YouTube
        val videos = mutableListOf<StreamInfoItem>()
        for (query in YoutubeFallbackMatcher.queries(recording)) {
            currentCoroutineContext().ensureActive()
            try {
                videos += SearchInfo.getInfo(service, service.searchQHFactory.fromQuery(query, listOf("videos"), ""))
                    .relatedItems.filterIsInstance<StreamInfoItem>()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep matches from the other query when a page cannot be read.
            }
        }
        return tryMatches(matchedVideos(recording, videos.distinctBy { it.url }).take(4), tried, resolve)
    }

    private suspend fun tryMatches(
        references: List<String>,
        tried: MutableSet<String>,
        resolve: suspend (String) -> String?,
    ): String? {
        for (reference in references) {
            currentCoroutineContext().ensureActive()
            val url = videoUrl(reference) ?: continue
            if (!tried.add(url)) continue
            resolve(url)?.let { return it }
        }
        return null
    }

    /**
     * NewPipe can still list an expired or rejected rendition. Try verified audio URLs in order
     * before handing one to the player, and never replace the video with another search result here.
     */
    suspend fun extract(videoUrl: String, client: OkHttpClient, preferSmallest: Boolean = false): String? {
        currentCoroutineContext().ensureActive()
        val extractor = ServiceList.YouTube.getStreamExtractor(videoUrl)
        extractor.fetchPage()

        val bitrateOrder = if (preferSmallest) compareBy<AudioStream> { it.averageBitrate }
            else compareByDescending<AudioStream> { it.averageBitrate }
        val streams = runCatching { extractor.audioStreams }.getOrDefault(emptyList())
            .filter { it.isUrl && (it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL) }
            .sortedWith(
                compareByDescending<AudioStream> { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                    .thenByDescending { it.format == MediaFormat.M4A }
                    .then(bitrateOrder)
            )
        val probeClient = client.newBuilder().callTimeout(5, TimeUnit.SECONDS).build()

        firstPlayable(streams.asSequence().map { it.content }, 3, probeClient)?.let { return it }
        val muxed = runCatching { extractor.videoStreams }.getOrDefault(emptyList()).filter { it.isUrl }
            .sortedBy { it.getResolution().removeSuffix("p").toIntOrNull() ?: Int.MAX_VALUE }
        return firstPlayable(muxed.asSequence().map { it.content }, 2, probeClient)
    }

    private suspend fun firstPlayable(urls: Sequence<String>, limit: Int, client: OkHttpClient): String? {
        for (url in urls.distinct().take(limit)) {
            currentCoroutineContext().ensureActive()
            if (isPlayable(url, client)) return url
        }
        return null
    }

    /** The probe and both playback engines must use the same headers for the extracted URL. */
    fun requestHeaders(url: String): Map<String, String> {
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return emptyMap()
        if (host != "googlevideo.com" && !host.endsWith(".googlevideo.com") && !isYoutubeUrl(url)) return emptyMap()
        val userAgent = if (YoutubeParsingHelper.isVisionOsStreamingUrl(url)) {
            YoutubeParsingHelper.getVisionOsUserAgent(null)
        } else {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36"
        }
        return mapOf(
            "User-Agent" to userAgent,
            "Accept" to "*/*",
            "Origin" to "https://www.youtube.com",
            "Referer" to "https://www.youtube.com/",
        )
    }

    private fun isPlayable(url: String, client: OkHttpClient): Boolean = try {
        val request = Request.Builder().url(url).header("Range", "bytes=0-0")
        requestHeaders(url).forEach { (name, value) -> request.header(name, value) }
        client.newCall(request.build()).execute().use { response ->
            if (!response.isSuccessful) return@use false
            val type = response.body.contentType()
            type == null || type.type == "audio" || type.type == "video" || type.subtype == "octet-stream"
        }
    } catch (_: java.io.IOException) {
        false
    }
}
