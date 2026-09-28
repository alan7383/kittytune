package com.alananasss.kittytune.data.filter

import android.content.Context
import com.alananasss.kittytune.data.WaveformRepository
import com.alananasss.kittytune.data.network.SoundCloudApi
import com.alananasss.kittytune.domain.Comment
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/** Where a piece of evidence comes from. Each source is capped so that none can carry a verdict on its own. */
enum class AiSignalFamily(val cap: Int) {
    METADATA(80),
    UPLOADER(80),
    CATALOG(70),
    COMMENTS(70),
    AUDIO(60),
    ENGAGEMENT(25),
    WAVEFORM(20)
}

/**
 * One piece of evidence. [declared] marks what the uploader said themselves (a label, their bio, their own
 * comment); that is not discounted for verified accounts, because a verified artist who calls a song AI means it.
 */
data class AiSignal(
    val code: String,
    val points: Int,
    val family: AiSignalFamily,
    val detail: String = "",
    val declared: Boolean = false
)

data class AiScoringResult(
    val isAi: Boolean,
    val score: Int,
    val reason: String,
    val signals: List<AiSignal> = emptyList(),
    val definitive: Boolean = false
)

/**
 * Decides whether a track was made with a music generator (Suno, Udio, …) or a voice-cloning tool.
 *
 * 1. A declaration settles it with a score of 100: the metadata says so in one of 16 languages — also when
 *    disguised with look-alike letters, invisible characters or "Al" for "AI" — or the artist is a documented AI act.
 * 2. Otherwise evidence from seven independent sources is added up:
 *    - METADATA: prompts pasted from the generator, Suno meta tags, comma-separated style prompts, AI lyric credits,
 *      mentions of AI, LLM-cliché titles and lyrics
 *    - UPLOADER: the bio, uploads per day since the account was created, many tracks but no followers, "…AI" names
 *    - CATALOG: the uploader's latest 50 tracks — other uploads labelled AI, prompt-styled descriptions, upload
 *      bursts, the same title uploaded twice (generators return two takes per prompt), genre hopping
 *    - COMMENTS: listeners calling it AI, or the uploader admitting it
 *    - AUDIO: the generator's fingerprint in the sound itself ([AiFakeprint]), known once the first 20 seconds
 *      have played
 *    - ENGAGEMENT: many plays with hardly any likes or reposts
 *    - WAVEFORM: a flat, brick-walled envelope, smeared transients, no sections, a hard cut at the end
 *    Each source is capped, three agreeing sources earn a bonus, and the total is discounted for uploads from
 *    before song generators existed and for verified artists. For DJ mixes, sets and podcasts (over 15
 *    minutes) what is known about the uploader counts a third: an account that posts generated songs can
 *    still post a mix of other people's records.
 * 3. Heuristic scores stop at 99, so 100 always means "declared"; [THRESHOLD_AI] and above counts as AI.
 */
object SoundCloudAiScorer {

    private const val TAG = "SoundCloudAiScorer"

    const val THRESHOLD_AI = 60

    private const val CORROBORATION_BONUS = 10.0
    private const val CORROBORATING_FAMILY_POINTS = 15.0
    private const val VERIFIED_FACTOR = 0.6
    private const val LONG_FORM_MS = 15 * 60_000L
    private const val LONG_FORM_FACTOR = 1.0 / 3
    private const val DEEP_FETCH_TIMEOUT_MS = 6_000L
    private const val HOUR_MS = 3_600_000L

    private val profileCache = TtlCache<Long, User>(maxEntries = 64, ttlMs = 6 * HOUR_MS)
    private val catalogCache = TtlCache<Long, List<Track>>(maxEntries = 64, ttlMs = 6 * HOUR_MS)
    private val commentsCache = TtlCache<Long, List<Comment>>(maxEntries = 128, ttlMs = HOUR_MS)

    /** Metadata-only check without network access, cheap enough to screen autoplay candidates in bulk. */
    fun isExplicitlyLabeledAi(track: Track): Boolean = AiTextSignals.explicitEvidence(track) != null

    /**
     * Everything that needs no network: the track's own metadata, its play counts and what the track carries
     * about its uploader. Never higher than [scoreTrack] for the same track, so it can screen candidates.
     */
    fun scoreOffline(
        track: Track,
        audioAiProbability: Float? = null,
        nowMs: Long = System.currentTimeMillis()
    ): AiScoringResult {
        AiTextSignals.explicitEvidence(track)?.let { evidence ->
            return AiScoringResult(isAi = true, score = 100, reason = evidence.code, signals = listOf(evidence), definitive = true)
        }
        val signals = AiTextSignals.softSignals(track) + AiBehaviorSignals.engagement(track) +
            guarded { AiBehaviorSignals.uploader(track.user, nowMs) } + listOfNotNull(audioSignal(audioAiProbability))
        return combine(track, track.user, signals)
    }

    /**
     * Points for the audio fingerprint's probability that a generator rendered the track. Calibrated on what
     * the phone hears from SoundCloud: its re-encoding pulls Suno songs from near 1 down to 0.3-0.9, while
     * every human track measured stayed at 0.000, so even a middling probability is a strong sign.
     */
    internal fun audioSignal(probability: Float?): AiSignal? {
        val p = probability ?: return null
        val points = when {
            p >= 0.80f -> 60
            p >= 0.50f -> 45
            p >= 0.25f -> 25
            else -> return null
        }
        return AiSignal("AUDIO_FINGERPRINT", points, AiSignalFamily.AUDIO, String.format(java.util.Locale.US, "p=%.3f", p))
    }

    /**
     * Scores [track]. With an [api], SoundCloud tracks are also checked against the uploader's full profile, their
     * latest uploads and the comment section; [uploaderProfile], [uploaderTracks] and [comments] take precedence
     * over fetching when they are already at hand. [audioAiProbability] is the audio fingerprint's verdict once
     * [AiAudioProbe] has heard the track.
     */
    suspend fun scoreTrack(
        track: Track,
        waveformSamples: FloatArray? = null,
        context: Context? = null,
        api: SoundCloudApi? = null,
        uploaderProfile: User? = null,
        uploaderTracks: List<Track>? = null,
        comments: List<Comment>? = null,
        audioAiProbability: Float? = null,
        nowMs: Long = System.currentTimeMillis()
    ): AiScoringResult = withContext(Dispatchers.Default) {
        AiTextSignals.explicitEvidence(track)?.let { evidence ->
            android.util.Log.d(TAG, "'${track.title}': ${evidence.code} (${evidence.detail})")
            return@withContext AiScoringResult(
                isAi = true,
                score = 100,
                reason = evidence.code,
                signals = listOf(evidence),
                definitive = true
            )
        }

        val uploaderId = track.user?.numericId ?: 0L
        // Tracks parsed from SoundCloud's JSON carry no "source" (Gson skips Kotlin defaults); other sources set it.
        val isSoundCloud = (track.source ?: "soundcloud") == "soundcloud"
        val soundCloud = api?.takeIf { isSoundCloud && track.id > 0 && uploaderId > 0 }

        coroutineScope {
            val profileJob = async { uploaderProfile ?: soundCloud?.let { fetchProfile(it, uploaderId, nowMs) } }
            val catalogJob = async { uploaderTracks ?: soundCloud?.let { fetchCatalog(it, uploaderId, nowMs) } }
            val commentsJob = async { comments ?: soundCloud?.let { fetchComments(it, track.id, nowMs) } }
            val waveformJob = async { waveformSamples ?: loadWaveform(context, track, api) }

            val profile = profileJob.await()
            val catalog = catalogJob.await()
            val commentThread = commentsJob.await()
            val samples = waveformJob.await()?.takeIf { it.isNotEmpty() }

            val uploader = profile ?: track.user
            val signals = buildList {
                addAll(AiTextSignals.softSignals(track))
                addAll(AiBehaviorSignals.engagement(track))
                addAll(guarded { AiBehaviorSignals.uploader(uploader, nowMs) })
                catalog?.let { addAll(guarded { AiBehaviorSignals.catalog(track, it, nowMs) }) }
                commentThread?.let { addAll(guarded { AiBehaviorSignals.comments(track, it) }) }
                samples?.let { addAll(AiWaveformSignals.analyze(it)) }
                audioSignal(audioAiProbability)?.let { add(it) }
            }
            combine(track, uploader, signals).also {
                android.util.Log.d(
                    TAG,
                    "'${track.title}': score=${it.score} reason=${it.reason} (profile=${profile != null}, " +
                        "catalog=${catalog?.size}, comments=${commentThread?.size}, waveform=${samples?.size}, " +
                        "audio=${audioAiProbability})"
                )
            }
        }
    }

    private fun combine(track: Track, uploader: User?, signals: List<AiSignal>): AiScoringResult {
        val verified = uploader?.isVerifiedUser == true
        val longForm = track.actualDurationMs > LONG_FORM_MS
        val byFamily = signals.groupBy { it.family }.mapValues { (family, familySignals) ->
            val capped = familySignals
                .sumOf { if (verified && !it.declared) it.points * VERIFIED_FACTOR else it.points.toDouble() }
                .coerceAtMost(family.cap.toDouble())
            val aboutUploader = family == AiSignalFamily.UPLOADER || family == AiSignalFamily.CATALOG
            if (longForm && aboutUploader) capped * LONG_FORM_FACTOR else capped
        }

        val notes = mutableListOf<String>()
        if (longForm && byFamily.keys.any { it == AiSignalFamily.UPLOADER || it == AiSignalFamily.CATALOG }) {
            notes += "LONG_FORM"
        }
        var total = byFamily.values.sum()
        if (byFamily.values.count { it >= CORROBORATING_FAMILY_POINTS } >= 3) {
            total += CORROBORATION_BONUS
            notes += "CORROBORATED"
        }
        val era = eraFactor(track.createdAt)
        if (total > 0 && era < 1.0) {
            total *= era
            notes += "PRE_AI_ERA"
        }
        if (total > 0 && verified) notes += "VERIFIED_UPLOADER"

        val score = total.roundToInt().coerceIn(0, 99)
        val codes = signals.sortedByDescending { it.points }.map { it.code }.distinct() + notes
        return AiScoringResult(
            isAi = score >= THRESHOLD_AI,
            score = score,
            reason = codes.joinToString("+").ifEmpty { "NONE" },
            signals = signals
        )
    }

    /** Song generators reached the public in 2023; before 2019 only a few niche tools existed. */
    private fun eraFactor(createdAt: String?): Double {
        val uploaded = AiDates.parseEpochMs(createdAt) ?: return 1.0
        val year = AiDates.year(uploaded)
        return when {
            year < 2019 -> 0.2
            year < 2023 -> 0.6
            else -> 1.0
        }
    }

    private suspend fun fetchProfile(api: SoundCloudApi, userId: Long, nowMs: Long): User? =
        profileCache.get(userId, nowMs)
            ?: quietly { api.getUser(userId) }?.also { profileCache.put(userId, it, nowMs) }

    private suspend fun fetchCatalog(api: SoundCloudApi, userId: Long, nowMs: Long): List<Track>? =
        catalogCache.get(userId, nowMs)
            ?: quietly { api.getUserTracks(userId, limit = 50).collection }?.also { catalogCache.put(userId, it, nowMs) }

    private suspend fun fetchComments(api: SoundCloudApi, trackId: Long, nowMs: Long): List<Comment>? =
        commentsCache.get(trackId, nowMs)
            ?: quietly { api.getTrackComments(trackId, limit = 50).collection }?.also { commentsCache.put(trackId, it, nowMs) }

    /** Gson can put nulls where Kotlin promises none; one malformed payload must not sink the whole check. */
    private inline fun guarded(analysis: () -> List<AiSignal>): List<AiSignal> =
        try {
            analysis()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "AI check skipped a source: ${e.message}")
            emptyList()
        }

    private suspend fun <T> quietly(block: suspend () -> T): T? =
        try {
            withTimeoutOrNull(DEEP_FETCH_TIMEOUT_MS) { block() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w(TAG, "AI check lookup failed: ${e.message}")
            null
        }

    private suspend fun loadWaveform(context: Context?, track: Track, api: SoundCloudApi?): FloatArray? {
        if (context == null) return null
        return try {
            WaveformRepository.getWaveform(context, track, api)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}

/** A small LRU with expiry, so replays and albums do not fetch the same uploader again. */
private class TtlCache<K, V>(private val maxEntries: Int, private val ttlMs: Long) {
    private val entries = object : LinkedHashMap<K, Pair<Long, V>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Pair<Long, V>>?): Boolean =
            size > maxEntries
    }

    @Synchronized
    fun get(key: K, nowMs: Long): V? {
        val (storedAt, value) = entries[key] ?: return null
        if (nowMs - storedAt > ttlMs) {
            entries.remove(key)
            return null
        }
        return value
    }

    @Synchronized
    fun put(key: K, value: V, nowMs: Long) {
        entries[key] = nowMs to value
    }
}
