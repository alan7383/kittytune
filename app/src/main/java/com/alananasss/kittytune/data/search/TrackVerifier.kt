package com.alananasss.kittytune.data.search

import android.content.Context
import com.alananasss.kittytune.data.deezer.DeezerSearchRepository
import com.alananasss.kittytune.data.qobuz.QobuzSearchRepository
import com.alananasss.kittytune.data.spotify.SpotifyRepository
import com.alananasss.kittytune.data.tidal.TidalSearchRepository
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Checks search results against the official catalogues before the Verified filter shows them.
 *
 * The platforms queried here are the ones that publish real release metadata. SoundCloud, YouTube
 * and VK are deliberately not used as references: those are the sources that carry re-uploads in
 * the first place, so a copy cannot be allowed to verify itself. Whichever platform the search
 * itself is using is skipped as well, for the same reason.
 *
 * References are cached per query because flipping the filter or loading another page asks the
 * same catalogues the same question. Cover fingerprints have their own URL cache in
 * [CoverFingerprint].
 */
object TrackVerifier {

    data class Outcome(
        val tracks: List<Track>,
        val verifiedKeys: Set<String>,
        val status: TrackVerification.Status,
        val referencesAvailable: Boolean,
    )

    private data class CacheEntry(
        val expiresAt: Long,
        val references: List<TrackVerification.Reference>,
    )

    private const val QUERY_LIMIT = 12
    private const val PROVIDER_TIMEOUT_MS = 5_000L
    private const val COVER_BUDGET_MS = 8_000L
    private const val CACHE_TTL_MS = 10 * 60_000L
    private const val MAX_CACHE_ENTRIES = 80
    private const val MAX_REFERENCE_COVERS = 48
    private const val MAX_CANDIDATE_COVERS = 32
    private const val COVER_PARALLELISM = 6

    private val referenceCache = ConcurrentHashMap<String, CacheEntry>()

    /**
     * Returns the visible tracks, the identities of the ones that could be fully verified and a
     * status the UI can use to explain an empty or unfiltered result.
     *
     * When no official catalogue answers, [tracks] is returned untouched and the status is
     * [TrackVerification.Status.UNAVAILABLE]. The filter never silently hides everything because a
     * platform is down.
     */
    suspend fun verify(
        context: Context,
        query: String,
        tracks: List<Track>,
        excludedPlatform: String? = null,
    ): Outcome = withContext(Dispatchers.IO) {
        if (query.isBlank() || tracks.isEmpty()) {
            return@withContext Outcome(
                tracks = tracks,
                verifiedKeys = emptySet(),
                status = TrackVerification.Status.IDLE,
                referencesAvailable = false,
            )
        }

        val references = references(context.applicationContext, query, excludedPlatform)
        if (references.isEmpty()) {
            return@withContext Outcome(
                tracks = tracks,
                verifiedKeys = emptySet(),
                status = TrackVerification.Status.UNAVAILABLE,
                referencesAvailable = false,
            )
        }

        var clusters = TrackVerification.buildClusters(query, references)
        if (clusters.isEmpty()) {
            return@withContext Outcome(
                tracks = tracks,
                verifiedKeys = emptySet(),
                status = TrackVerification.Status.INCONCLUSIVE,
                referencesAvailable = true,
            )
        }

        var candidates = tracks.mapIndexed { index, track ->
            TrackVerification.candidateOf(track, index)
        }

        // First pass without covers tells us which rows are worth downloading artwork for. A row
        // already rejected on artist or duration cannot be rescued by its cover.
        val preliminary = TrackVerification.rank(query, candidates, clusters)
        val worthChecking = preliminary.evaluations.filter {
            it.cluster != null && it.verdict != TrackVerification.Verdict.REJECTED
        }
        val referenceCoverUrls = worthChecking
            .flatMap { it.cluster?.coverUrls.orEmpty() }
            .distinct()
            .take(MAX_REFERENCE_COVERS)
        val candidateIndexes = worthChecking.map { it.originalIndex }.toSet()
        val candidateCoverUrls = candidateIndexes
            .mapNotNull { candidates.getOrNull(it)?.coverUrl }
            .distinct()
            .take(MAX_CANDIDATE_COVERS)

        val coverHashes = fetchCoverHashes((referenceCoverUrls + candidateCoverUrls).distinct())
        if (coverHashes.isNotEmpty()) {
            clusters = TrackVerification.withCoverFingerprints(clusters, coverHashes)
            candidates = candidates.map { candidate ->
                candidate.copy(coverHash = candidate.coverUrl?.let(coverHashes::get))
            }
        }

        val ranking = TrackVerification.rank(query, candidates, clusters)
        val tracksByIndex = tracks.withIndex().associate { it.index to it.value }
        val ordered = ranking.visible.mapNotNull { tracksByIndex[it.originalIndex] }

        Outcome(
            tracks = ordered,
            verifiedKeys = ranking.verifiedKeys,
            status = ranking.status,
            referencesAvailable = true,
        )
    }

    private suspend fun references(
        context: Context,
        query: String,
        excludedPlatform: String?,
    ): List<TrackVerification.Reference> {
        val cacheKey = "${excludedPlatform.orEmpty().lowercase()}|${normalizeQuery(query)}"
        val now = System.currentTimeMillis()
        referenceCache[cacheKey]?.takeIf { it.expiresAt > now }?.let { return it.references }

        val fetched = fetchReferences(context, query, excludedPlatform)
        if (fetched.isNotEmpty()) {
            if (referenceCache.size >= MAX_CACHE_ENTRIES) referenceCache.clear()
            referenceCache[cacheKey] = CacheEntry(now + CACHE_TTL_MS, fetched)
        }
        return fetched
    }

    private suspend fun fetchReferences(
        context: Context,
        query: String,
        excludedPlatform: String?,
    ): List<TrackVerification.Reference> = coroutineScope {
        val excluded = excludedPlatform?.lowercase()
        val providers = mutableListOf<Pair<String, suspend () -> List<Track>>>()

        if (excluded != "deezer") {
            providers += "deezer" to {
                DeezerSearchRepository.searchTracks(query, limit = QUERY_LIMIT)
            }
        }
        if (excluded != "tidal") {
            providers += "tidal" to {
                TidalSearchRepository.search(context, query, limit = QUERY_LIMIT).tracks
            }
        }
        if (excluded != "qobuz") {
            providers += "qobuz" to {
                QobuzSearchRepository.search(context, query, limit = QUERY_LIMIT).tracks
            }
        }
        if (excluded != "spotify") {
            providers += "spotify" to {
                SpotifyRepository.search(query, limit = QUERY_LIMIT).tracks.map { it.toTrack() }
            }
        }

        providers.map { (platform, load) ->
            async {
                val tracks = try {
                    withTimeoutOrNull(PROVIDER_TIMEOUT_MS) { load() }.orEmpty()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }
                platform to tracks
            }
        }.awaitAll()
            .flatMap { (platform, tracks) ->
                tracks.mapNotNull { track -> TrackVerification.referenceOf(track, platform) }
            }
            .distinctBy { it.id }
    }

    private suspend fun fetchCoverHashes(urls: List<String>): Map<String, Long> {
        if (urls.isEmpty()) return emptyMap()

        val results = ConcurrentHashMap<String, Long>()
        val semaphore = Semaphore(COVER_PARALLELISM)
        withTimeoutOrNull(COVER_BUDGET_MS) {
            coroutineScope {
                urls.forEach { url ->
                    launch {
                        semaphore.withPermit {
                            CoverFingerprint.fingerprint(url)?.let { results[url] = it }
                        }
                    }
                }
            }
        }
        return results.toMap()
    }

    private fun normalizeQuery(query: String): String =
        query.trim().lowercase().replace(Regex("\\s+"), " ")
}
