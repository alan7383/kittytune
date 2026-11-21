package com.alananasss.kittytune.data.cover

import com.alananasss.kittytune.data.StreamResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

internal object YouTubeSampleProvider {
    private val videoSuffix = Regex(
        """\s*[\[(]\s*(?:official\s+)?(?:music\s+)?video(?:\s+\d+k)?\s*[\])]\s*$""",
        RegexOption.IGNORE_CASE,
    )

    suspend fun resolve(title: String, artist: String, durationSeconds: Int?): FeedVisual? =
        runInterruptible(Dispatchers.IO) {
            StreamResolver.ensureExtractorReady()
            val youtube = ServiceList.YouTube
            val query = youtube.searchQHFactory.fromQuery("$artist $title official music video", listOf("videos"), "")
            val match = SearchInfo.getInfo(youtube, query).relatedItems.filterIsInstance<StreamInfoItem>()
                .firstOrNull {
                    it.duration in 30..900 &&
                        (durationSeconds == null || abs(it.duration - durationSeconds) <= 45) &&
                        matches(title, artist, it.name, it.uploaderName.orEmpty())
                } ?: return@runInterruptible null

            val extractor = youtube.getStreamExtractor(match.url)
            extractor.fetchPage()
            val streams = extractor.videoOnlyStreams + extractor.videoStreams
            val best = streams.asSequence().filter {
                it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
                    it.content.startsWith("https://") && it.format?.mimeType == "video/mp4" &&
                    it.codec?.startsWith("av01") != true
            }.mapNotNull { stream ->
                val height = stream.resolution.substringBefore('p').toIntOrNull() ?: return@mapNotNull null
                height.takeIf { it in 144..720 }?.let { stream.content to it }
            }.maxByOrNull { it.second } ?: return@runInterruptible null

            val length = extractor.length.takeIf { it >= 30 } ?: return@runInterruptible null
            val start = (length / 4).coerceAtMost(length - 25) * 1000
            FeedVisual(best.first, FeedVisualSource.YOUTUBE, start, start + 25_000L)
        }

    internal fun matches(title: String, artist: String, candidate: String, uploader: String): Boolean {
        val parts = candidate.replace(videoSuffix, "").split(Regex("""\s+[-–—]\s+"""), limit = 2)
        val wantedArtist = normalize(artist)
        val channel = normalize(uploader)
        if (wantedArtist.isEmpty() || channel.endsWith("topic")) return false
        val wantedTitle = normalize(title)
        if (wantedTitle.isEmpty()) return false
        return if (parts.size == 2) {
            normalize(parts[0]) == wantedArtist && normalize(parts[1]) == wantedTitle
        } else {
            channel in setOf(wantedArtist, wantedArtist + "vevo", wantedArtist + "official") &&
                normalize(parts[0]) == wantedTitle
        }
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD).lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)
}
