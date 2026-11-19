package com.alananasss.kittytune.data.wave

import com.alananasss.kittytune.KittyTuneApp
import com.alananasss.kittytune.data.mix.MixEngine
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.Track

/**
 * The Discover feed's endless source. Before this existed the feed drew one static chart
 * (40 tracks, no pagination) through [MyWave], so after a few swipes every batch came back
 * fully de-duplicated and the pager silently died — no "load more", no end marker.
 *
 * Three tiers, each taking over only when the one above it runs dry:
 *
 *  1. **Taste mixer** — [MixEngine] with the listener's own history as seeds, for logged-in
 *     listeners whose taste can still supply fresh tracks.
 *  2. **Charts, page by page** — SoundCloud charts are now followed through `next_href`
 *     instead of re-reading page one forever; when a chart ends the next genre takes over,
 *     and after every genre has been walked with one kind the other kind (trending → top)
 *     restarts the walk. This alone turns ~40 cards into hundreds.
 *  3. **Related frontier** — every track the feed has already served becomes a seed for
 *     `tracks/{id}/related`, and every new find joins the frontier in turn. Breadth-first
 *     over the whole graph, which is what makes the feed genuinely endless: the pool
 *     regrows from whatever it just served.
 *
 * [WaveFeedback] still has the last word on every candidate, so artists the listener
 * disliked never sneak back in through a new tier.
 */
object DiscoverFeed {

    private const val BATCH_SIZE = 20
    private const val CHART_LIMIT = 50
    private const val RELATED_LIMIT = 20
    private const val FRONTIER_CAP = 500

    /** Runaway guard for the chart walk: 16 genres x 2 kinds is ~32 first pages, the rest is margin. */
    private const val CHART_WALK_CAP = 64

    /** Genres walked in order once the current chart runs dry; the broad one leads. */
    private val CHART_GENRES = listOf(
        "soundcloud:genres:all-music",
        "soundcloud:genres:electronic",
        "soundcloud:genres:house",
        "soundcloud:genres:hiphop",
        "soundcloud:genres:deephouse",
        "soundcloud:genres:techno",
        "soundcloud:genres:rock",
        "soundcloud:genres:pop",
        "soundcloud:genres:drumandbass",
        "soundcloud:genres:ambient",
        "soundcloud:genres:indie",
        "soundcloud:genres:jazz",
        "soundcloud:genres:metal",
        "soundcloud:genres:rnb",
        "soundcloud:genres:latin",
        "soundcloud:genres:classical",
    )

    /** Chart kinds cycled after every genre has been walked with the previous kind. */
    private val CHART_KINDS = listOf("trending", "top")

    // Tier 1 — taste. False only after the mixer has stopped producing anything new.
    private var mixDry = false

    // Tier 2 — charts. The cursor is the current chart's next_href; when it runs out the
    // genre (then kind) advances. chartsDone means the whole walk has finished once.
    private var kindIndex = 0
    private var genreIndex = 0
    private var chartCursor: String? = null
    private var chartsDone = false

    // Tier 3 — the related frontier: ids of tracks worth expanding, plus everything seeded
    // so far so a track never seeds twice.
    private val frontier = ArrayDeque<Long>()
    private val seeded = mutableSetOf<Long>()

    /** True only when every tier is drained for good; the feed may then show its end. */
    @Volatile
    var exhausted = false
        private set

    /**
     * The next page of Discover. Empty does not mean over: it means nothing new right now
     * (check [exhausted] for the real end). Callers pass what they have already shown so the
     * tiers skip it; unlike the old [MyWave] path a dry batch here never freezes the pager.
     */
    suspend fun nextBatch(exclude: Set<Long>, size: Int = BATCH_SIZE): List<Track> {
        if (exhausted) return emptyList()
        val fresh = tasteBatch(exclude, size)
            ?: chartBatch(exclude)
            ?: relatedBatch(exclude)
        return fresh?.take(size) ?: emptyList()
    }

    /** Full reset — a new feed session (filter toggle, manual refresh). Keeps nothing. */
    fun reset() {
        mixDry = false
        kindIndex = 0
        genreIndex = 0
        chartCursor = null
        chartsDone = false
        frontier.clear()
        seeded.clear()
        exhausted = false
    }

    private suspend fun tasteBatch(exclude: Set<Long>, size: Int): List<Track>? {
        if (mixDry) return null
        val mixed = MixEngine.mix(MixEngine.Recipe.MyTaste, size = size * 3)
        if (mixed is MixEngine.Result.Mixed) {
            val fresh = mixed.tracks
                .filter { it.id !in exclude && WaveFeedback.isWelcome(it) }
            if (fresh.isNotEmpty()) return rememberSeeds(fresh)
        }
        // Nothing new from taste any more — from this batch on, charts take over.
        mixDry = true
        return null
    }

    /** Follows the chart cursor through pages, genres and kinds until something fresh. */
    private suspend fun chartBatch(exclude: Set<Long>): List<Track>? {
        if (chartsDone) return null
        val api = RetrofitClient.create(KittyTuneApp.instance)
        // A genre's chart is a handful of pages, and the walk itself is 16 genres x 2 kinds;
        // the cap is purely a runaway guard in case a server loop ever repeats a cursor.
        var pulled = 0
        while (!chartsDone && pulled < CHART_WALK_CAP) {
            pulled++
            val page = try {
                if (chartCursor != null) {
                    api.getChartsNextPage(chartCursor!!)
                } else {
                    api.getCharts(
                        kind = CHART_KINDS[kindIndex],
                        genre = CHART_GENRES[genreIndex],
                        limit = CHART_LIMIT,
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // A network blip is not the end of the charts; let the related tier answer
                // this call and the chart retry on the next batch.
                return null
            }
            chartCursor = page.next_href
            if (chartCursor == null) advanceChart()
            val fresh = page.collection
                .mapNotNull { it.track }
                .filter { it.id !in exclude && WaveFeedback.isWelcome(it) }
            if (fresh.isNotEmpty()) return rememberSeeds(fresh)
            // Whole page already seen or filtered: keep walking without waiting for a scroll.
        }
        return null
    }

    /** Breadth-first over the served tracks: every one of them is a related-tracks seed. */
    private suspend fun relatedBatch(exclude: Set<Long>): List<Track>? {
        val api = RetrofitClient.create(KittyTuneApp.instance)
        while (frontier.isNotEmpty()) {
            val seed = frontier.removeFirst()
            val related = try {
                api.getRelatedTracks(seed, limit = RELATED_LIMIT).collection.filterNotNull()
            } catch (e: Exception) {
                e.printStackTrace()
                // The seed stays first in line: a network blip must not burn it, or a bad
                // connection could drain the whole frontier and fake a permanent end.
                frontier.addFirst(seed)
                return null
            }
            val fresh = related.filter { it.id !in exclude && WaveFeedback.isWelcome(it) }
            if (fresh.isNotEmpty()) return rememberSeeds(fresh)
            // A seed that led nowhere is simply dropped; the frontier stays breadth-first.
        }
        // The frontier is fed by every served batch, so empty means the whole feed really
        // is over — the one honest "exhausted" among all the "nothing right now" cases.
        exhausted = true
        return null
    }

    private fun rememberSeeds(tracks: List<Track>): List<Track> {
        tracks.forEach {
            if (seeded.add(it.id) && frontier.size < FRONTIER_CAP) frontier.addLast(it.id)
        }
        return tracks
    }

    private fun advanceChart() {
        chartCursor = null
        genreIndex++
        if (genreIndex >= CHART_GENRES.size) {
            genreIndex = 0
            kindIndex++
            if (kindIndex >= CHART_KINDS.size) {
                kindIndex = 0
                chartsDone = true
            }
        }
    }
}
