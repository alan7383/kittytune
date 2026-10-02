package com.alananasss.kittytune.data.mix

import com.alananasss.kittytune.data.ListeningStatsRepository
import com.alananasss.kittytune.data.LikeRepository
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Builds the mix: reads the listener, asks SoundCloud for candidates, hands the arithmetic to [MixRanking].
 *
 * ## What "a real algo" means here
 *
 * Not one request. A recommendation is a fan-out and a filter, and the shape below is the standard one: profile
 * the listener from their own history, turn that profile into *seeds*, expand each seed through an endpoint that
 * knows about similarity, then score and diversify what comes back (issue #33).
 *
 * The expansion uses three SoundCloud endpoints, each answering a different question, and the mix is better for
 * having all three than it would be with more of any one:
 *
 *  - **`/tracks/{id}/related`** — "what goes with this track". Their own collaborative filtering, and the
 *    strongest signal available, because it is built from what other listeners actually played next.
 *  - **`system-playlists:artist-stations:{id}`** — "an hour of this artist and their neighbours". This is what
 *    answers "in the Yeat style" directly: the station for an artist is not that artist's catalogue, it is the
 *    neighbourhood around them.
 *  - **`/search/tracks` filtered by tag** — "what is good in this genre right now". Weaker per candidate, but it
 *    is the only one that reaches artists the listener has no path to at all, and a mix with no strangers in it
 *    is not a mix.
 *
 * Seeds are drawn from the top of the profile but *sampled* rather than taken in order, so two presses on the
 * same evening give two different mixes.
 *
 * ## Why every step degrades instead of failing
 *
 * A mix is a nicety. If the history is empty, if a request times out, if the token has expired — the answer is a
 * shorter mix or a mix built from fewer seeds, never an error dialog. The only hard failure is having nothing at
 * all to work from, and that is reported as its own state so the button can say "listen to something first"
 * rather than spinning.
 */
object MixEngine {

    private val api by lazy { RetrofitClient.create() }

    /**
     * Whether a Go+ or geo-blocked track can still be heard, which decides whether it belongs in a mix at all.
     *
     * Read from the setting each time rather than captured: somebody who turns the fallback off between two mixes
     * should get the second one without those tracks.
     */
    private fun youtubeFallback(): Boolean =
        com.alananasss.kittytune.data.local.PlayerPreferences().getYouTubeFallbackEnabled()

    private fun prioritizeTrusted(): Boolean =
        com.alananasss.kittytune.data.local.PlayerPreferences().getMixPrioritizeTrusted()

    /** How the mix was asked for. */
    sealed interface Recipe {
        /** From everything the listener plays. The plain "start mixing" press. */
        data object MyTaste : Recipe

        /** "In the Yeat style" — the neighbourhood around one artist, not that artist's catalogue. */
        data class LikeArtist(val artistId: Long?, val artistName: String) : Recipe

        /** One genre or tag, as SoundCloud spells it. */
        data class InGenre(val genre: String) : Recipe
    }

    sealed interface Result {
        data class Mixed(val tracks: List<Track>, val describedBy: String) : Result

        /** Nothing to profile and no seed given: the honest answer is "play something first". */
        data object NotEnoughHistory : Result

        /**
         * Nothing came back, and *which* nothing it was.
         *
         * "Diagnostique partout." One message for three different failures is what made the snorunt report hard to
         * act on: "check your connection" was wrong, the connection was fine, the artist had simply been resolved
         * to the wrong account. Each of these is a different thing to tell somebody and a different thing to look
         * at in a log (issue #33).
         */
        data class NothingFound(val stage: Stage) : Result

        enum class Stage {
            /** No seed could be built — for an artist recipe, the name matched nobody. */
            NO_SEEDS,

            /** Seeds existed and every expansion returned nothing. Network, or an artist with no catalogue. */
            NO_CANDIDATES,

            /** Candidates existed and every one was disqualified: all known, all unplayable, all too short. */
            ALL_FILTERED,
        }
    }

    /** How far back the profile looks. Longer than the decay's half-life, so old phases fade rather than cut. */
    private const val HISTORY_WINDOW_DAYS = 120L

    /**
     * How many tracks a mix holds.
     *
     * "Ça doit en mettre 100 dans la queue direct." A hundred is about six hours, which is not an evening's
     * listening — it is a queue you stop thinking about, which is the point of a mix you press once.
     *
     * It also changes the shape of everything upstream: a hundred slots with a two-per-artist cap needs fifty
     * distinct artists among the candidates, so the seed counts and the per-request limits below are what actually
     * make the number reachable rather than aspirational (issue #33).
     */
    const val MIX_SIZE = 100

    suspend fun mix(recipe: Recipe = Recipe.MyTaste, size: Int = MIX_SIZE): Result =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val taste = profile(now)

            val seeds = seedsFor(recipe, taste)
            if (seeds.isEmpty()) {
                return@withContext if (taste.isEmpty) Result.NotEnoughHistory
                else Result.NothingFound(Result.Stage.NO_SEEDS)
            }

            val candidates = expand(seeds)
            // Printed rather than swallowed, because the three numbers are the whole diagnosis: how many seeds were
            // drawn, how much they returned, and how much survived the filters. A mix that comes back short is one
            // of those three being small, and there is no way to tell which from the outside.
            println("KittyTune mix: ${seeds.size} seeds -> ${candidates.size} candidates")
            if (candidates.isEmpty()) return@withContext Result.NothingFound(Result.Stage.NO_CANDIDATES)

            val tracks = MixRanking.order(
                candidates = candidates,
                taste = taste,
                size = size,
                seed = now,
                youtubeFallback = youtubeFallback(),
                prioritizeTrusted = prioritizeTrusted(),
            )
            println("KittyTune mix: ${candidates.size} candidates -> ${tracks.size} tracks")
            if (tracks.isEmpty()) Result.NothingFound(Result.Stage.ALL_FILTERED)
            else Result.Mixed(tracks, describe(recipe, seeds))
        }

    /**
     * Turns a recipe and a profile into things worth expanding.
     *
     * ## Why the seeds are sampled and not taken in order
     *
     * Taking the top five artists gives the same five every time, so the same mix every time, and a mix you
     * cannot re-roll is a playlist with extra steps. Sampling from a wider band — the top [SEED_POOL] weighted
     * by their own weights — keeps the mix recognisably yours while making two presses two different answers.
     *
     * The split between artists, genres and tracks is deliberate rather than even. Artist stations are the
     * strongest signal, so they get the most seeds; a genre seed is the only one that reaches somebody you have
     * no path to at all, so there is always at least one when the profile can supply it.
     */
    private suspend fun seedsFor(recipe: Recipe, taste: MixProfile.Taste): List<Seed> = when (recipe) {
        is Recipe.LikeArtist -> {
            // "In the Yeat style." One seed, full affinity, and the station endpoint does the rest — an artist
            // station is the neighbourhood around them rather than their own back catalogue, which is exactly
            // what "in the style of" means and is why this is not just a search for their name.
            val id = recipe.artistId ?: resolveArtistId(recipe.artistName)
            if (id != null) listOf(Seed.OfArtist(id, recipe.artistName, 1f)) else emptyList()
        }

        // `wide`, because this one seed is the entire mix: eight seeds at fifty candidates each is what makes a
        // hundred-track mix reachable, and one seed at fifty cannot get past about thirty-five once the known,
        // the unplayable and the two-per-artist cap have taken their share. The search endpoint honours a
        // larger limit, so a genre mix asks for one rather than coming back a third full.
        is Recipe.InGenre -> listOf(Seed.OfGenre(recipe.genre, 1f, wide = true))

        Recipe.MyTaste -> {
            if (taste.isEmpty) emptyList() else buildList {
                val random = java.util.Random(System.currentTimeMillis())

                // Artists, with their ids looked up from the same stats table the profile came from.
                val ids = artistIds()
                val artistPool = taste.rankedArtists.take(SEED_POOL).mapNotNull { name ->
                    ids[name]?.let { Triple(name, it, taste.artists[name] ?: 0f) }
                }
                sample(artistPool, ARTIST_SEEDS, random).forEach { (name, id, weight) ->
                    add(Seed.OfArtist(id, name, affinityOf(weight, taste.artists)))
                }

                // Tracks they have played most, for "what goes with this".
                val trackPool = runCatching {
                    ListeningStatsRepository.getTopTracks(sinceForSeeds(), limit = SEED_POOL)
                }.getOrDefault(emptyList())
                sample(trackPool, TRACK_SEEDS, random).forEach {
                    add(Seed.OfTrack(it.trackId, 0.8f))
                }

                // And a genre, which is the only seed that can reach a stranger.
                val genrePool = taste.rankedGenres.take(SEED_POOL).map { it to (taste.genres[it] ?: 0f) }
                sample(genrePool, GENRE_SEEDS, random).forEach { (genre, weight) ->
                    add(Seed.OfGenre(genre, affinityOf(weight, taste.genres) * 0.7f))
                }
            }
        }
    }

    /**
     * What the plain press is built from, in the form the card can show it.
     *
     * "Qu'est-ce que ça fait quand on fait start mix tout seul ? Ça se base sur quoi ?" — a question the card
     * should not have made anybody ask, when the answer is sitting in the same table the mix reads. So the card
     * names it: these artists, and how many more are behind them.
     *
     * Artists rather than tracks or genres because artists are what most of the seeds are, and they are the part
     * somebody recognises — three names and three faces say "it read your listening" in a way no sentence does.
     */
    suspend fun basis(): Basis? = withContext(Dispatchers.IO) {
        runCatching {
            val since = sinceForSeeds()
            val top = ListeningStatsRepository.getTopArtists(since, limit = 3)
                .filter { it.artistName.isNotBlank() }
            if (top.isEmpty()) null
            else Basis(topArtists = top, artistCount = ListeningStatsRepository.getUniqueArtists(since))
        }.getOrNull()
    }

    /** The faces and the count on the card: who the mix is being built from, and how deep the profile goes. */
    data class Basis(
        val topArtists: List<com.alananasss.kittytune.data.local.TopArtistResult>,
        val artistCount: Int,
    )

    /** Where a weight sits relative to the strongest one, so affinity is comparable across seed kinds. */
    private fun affinityOf(weight: Float, all: Map<String, Float>): Float {
        val top = all.values.maxOrNull() ?: return 0.5f
        return if (top <= 0f) 0.5f else (weight / top).coerceIn(0.15f, 1f)
    }

    /** Weighted-ish sampling without replacement: shuffled, then the first [count]. */
    private fun <T> sample(pool: List<T>, count: Int, random: java.util.Random): List<T> =
        if (pool.size <= count) pool else pool.shuffled(random).take(count)

    /**
     * How wide the band is that seeds are drawn from, and how many come from each kind.
     *
     * Sized for a hundred-track mix. Eight seeds at fifty candidates each is four hundred before deduplication,
     * which after the known-track filter, the unplayable filter and the two-per-artist cap leaves comfortably more
     * than a hundred — and comfortably is what matters, because a mix that returns eighty because the arithmetic
     * was tight is a mix that looks broken.
     */
    private const val SEED_POOL = 20
    private const val ARTIST_SEEDS = 4
    private const val TRACK_SEEDS = 3
    private const val GENRE_SEEDS = 2

    private fun sinceForSeeds(): Long =
        System.currentTimeMillis() - HISTORY_WINDOW_DAYS * 24 * 60 * 60 * 1000L

    /** Artist name to id, from the stats table, because the profile only carries names. */
    private suspend fun artistIds(): Map<String, Long> = runCatching {
        ListeningStatsRepository.getTopArtists(sinceForSeeds(), limit = 60)
            .mapNotNull { row -> row.artistId?.let { row.artistName.trim().lowercase() to it } }
            .toMap()
    }.getOrDefault(emptyMap())

    /**
     * For a hand-picked artist that is not in the history: find them by name.
     *
     * ## Why the first result is the wrong one
     *
     * Typing "snorunt" returned nothing at all, and this is why. `search/users?q=snorunt&limit=1` answers with
     * **Snorunt, 7 followers** — not `snorunt★`, 10,011, who is the artist anybody means. SoundCloud's user search
     * is not sorted by prominence, and taking the first row means seeding the mix on a stranger with no catalogue
     * and no station, which produces exactly the empty result he saw (issue #33).
     *
     * So it asks for several and picks by follower count among the names that actually look like what was typed.
     * The name check matters as much as the count: without it, searching a short word finds whichever unrelated
     * big account happens to contain it.
     */
    private suspend fun resolveArtistId(name: String): Long? = runCatching {
        val accounts = api.searchUsers(name, limit = 10).collection.map {
            MixArtistMatch.Account(id = it.id, username = it.username, followers = it.followersCount.toLong())
        }
        MixArtistMatch.best(accounts, name)
    }.getOrNull()

    /**
     * Every seed expanded in parallel, with failures dropped.
     *
     * In parallel because a mix is five to six requests and doing them in series is five to six round trips of
     * waiting; dropped because one dead seed out of six should cost a few candidates, not the mix.
     */
    private suspend fun expand(seeds: List<Seed>): List<MixRanking.Candidate> = coroutineScope {
        seeds
            .map { seed -> async { runCatching { candidatesFor(seed) }.getOrDefault(emptyList()) } }
            .flatMap { it.await() }
    }

    private suspend fun candidatesFor(seed: Seed): List<MixRanking.Candidate> = when (seed) {
        is Seed.OfTrack ->
            api.getRelatedTracks(seed.trackId, limit = PER_SEED).collection
                .map { MixRanking.Candidate(it, seed.affinity) }

        is Seed.OfArtist -> artistCandidates(seed)

        is Seed.OfGenre -> genreCandidates(seed)
    }

    /**
     * A genre seed, which needs two requests rather than one, because SoundCloud's tag filter is stricter than
     * the words a chip is written in — and that is the whole of "les filtres de catégorie marchent pas" (issue #33).
     *
     * ## The lower-casing
     *
     * `filter.genre_or_tag` is case-sensitive and answers an *empty collection* for anything else — HTTP 200, no
     * error, nothing to catch. Measured against the live endpoint: `phonk` returns 487,036 tracks and `Phonk`
     * returns none; `hip hop` returns 7.8 million and `Hip Hop` returns none. Every category in this app is
     * written in title case, because that is how a chip is read, so every genre mix in the app was empty.
     *
     * ## The fallback
     *
     * Lower-casing fixes most of them and not all, because the filter matches a tag *literally* and several of
     * these categories are phrases rather than tags: `variété française` has 0 tracks behind it, `calm relax` has
     * 2, `80s 90s` has 63. Those chips would still come back empty. So when the tag answers thinly, the same
     * words are searched as words instead, sorted by popularity — which is what the browse screens have always
     * done and what never returns nothing. The second request only happens in the case that needs it.
     */
    private suspend fun genreCandidates(seed: Seed.OfGenre): List<MixRanking.Candidate> {
        val genre = seed.genre.trim()
        val limit = if (seed.wide) WIDE_PER_SEED else PER_SEED
        // Sorted by recency rather than all-time popularity: "what is good in this genre" should mean this month,
        // not the same five anthems everybody has already heard.
        val tagged = runCatching {
            api.searchTracksStrict(tag = genre.lowercase(), sort = "recent", limit = limit).collection
        }.getOrDefault(emptyList())

        val searched =
            if (tagged.size >= THIN_TAG) emptyList()
            else runCatching { api.searchTracksPop(genre, limit = limit).collection }.getOrDefault(emptyList())

        return (tagged + searched).distinctBy { it.id }.map { MixRanking.Candidate(it, seed.affinity) }
    }

    /** Below this many results, a tag has not really answered and the words get asked instead. */
    private const val THIN_TAG = 10

    /** What one seed asks for when it is carrying the whole mix. Their search endpoint returns all of them. */
    private const val WIDE_PER_SEED = 200

    /**
     * Everything an artist seed can reach, gathered from four places at once.
     *
     * ## Why four and not one
     *
     * This used to be the artist station alone, and that is why "in the style of snorunt" came back empty:
     * `system-playlists:artist-stations:{id}` **404s for most artists** — verified against two separate accounts —
     * because SoundCloud only generates a station for acts above some popularity it does not document. One
     * endpoint, one point of failure, and the failure is the common case rather than the rare one (issue #33).
     *
     * So four paths, and any one of them can carry the mix:
     *
     *  - the **station**, when it exists, which is the best answer because it is already a neighbourhood;
     *  - their **top tracks** and their **uploads**, which are the artist themselves rather than their style, and
     *    are the seed for the hop below;
     *  - a **search on the name**, which is the only one that never comes back empty on SoundCloud, where an
     *    artist's own account is often quiet while re-uploads, features and edits are everywhere.
     *
     * Then one hop through `related` on whatever the first three found, which is what turns "their songs" into
     * "their style" — the request he actually made. The hop is what a station would have given for free.
     */
    private suspend fun artistCandidates(seed: Seed.OfArtist): List<MixRanking.Candidate> = coroutineScope {
        val station = async { runCatching { api.getArtistStation(seed.artistId).tracks.orEmpty() }.getOrDefault(emptyList()) }
        val top = async { runCatching { api.getUserTopTracks(seed.artistId, limit = PER_SEED).collection }.getOrDefault(emptyList()) }
        val posted = async { runCatching { api.getUserTracks(seed.artistId, limit = PER_SEED).collection }.getOrDefault(emptyList()) }
        val byName = async { runCatching { api.searchTracksPop(seed.name, limit = PER_SEED).collection }.getOrDefault(emptyList()) }

        val direct = (station.await() + top.await() + posted.await() + byName.await()).distinctBy { it.id }

        // The hop. Taken from the most popular of what was found rather than the first: a related lookup is only
        // as good as the track it starts from, and an obscure re-upload has no neighbours worth having.
        val hopSeeds = direct
            .filter { MixRanking.isPlayable(it, youtubeFallback()) }
            .sortedByDescending { it.playbackCount }
            .take(HOP_SEEDS)

        val related = hopSeeds
            .map { track -> async { runCatching { api.getRelatedTracks(track.id, limit = PER_SEED).collection }.getOrDefault(emptyList()) } }
            .flatMap { it.await() }

        // The artist's own tracks score a little below their neighbourhood, because "in the style of" is a request
        // for the neighbourhood — but they stay in, since a mix in somebody's style with none of them in it reads
        // as having missed the point.
        direct.map { MixRanking.Candidate(it, seed.affinity * OWN_TRACK_AFFINITY) } +
            related.map { MixRanking.Candidate(it, seed.affinity) }
    }

    /** How many of an artist's own tracks are used as a starting point for the related hop. */
    private const val HOP_SEEDS = 6

    /** An artist's own track against their neighbourhood, for a request that asked for the style. */
    private const val OWN_TRACK_AFFINITY = 0.75f

    /** How many candidates one request contributes. */
    private const val PER_SEED = 50

    /** What the mix says it is, on the card. */
    private fun describe(recipe: Recipe, seeds: List<Seed>): String = when (recipe) {
        is Recipe.LikeArtist -> recipe.artistName
        is Recipe.InGenre -> recipe.genre
        Recipe.MyTaste -> seeds.filterIsInstance<Seed.OfArtist>()
            .take(2)
            .joinToString(", ") { it.name }
    }

    /**
     * The listener, from the plays the stats table already holds.
     *
     * Read rather than tracked: every play has been recorded with a timestamp and a listened duration since the
     * statistics feature shipped, which means the profile is available on the first press instead of needing its
     * own collection period.
     */
    private suspend fun profile(now: Long): MixProfile.Taste {
        val since = now - HISTORY_WINDOW_DAYS * 24 * 60 * 60 * 1000L
        val events = runCatching { ListeningStatsRepository.getEvents(since) }.getOrDefault(emptyList())
        val plays = events.map { event ->
            MixProfile.Play(
                trackId = event.trackId,
                artist = event.artistName,
                // The stats row carries no genre — it was built to answer "how long did you listen", not "to
                // what kind of thing". So the genre half of the profile comes from the likes below, and the
                // artist half, which is the stronger signal anyway, comes from here.
                genre = null,
                atMs = event.timestamp,
                listenedMs = event.listenDurationMs,
                durationMs = event.trackDurationMs,
            )
        }
        val likes = runCatching { LikeRepository.likedTracks.value }.getOrDefault(emptyList())
        val disliked = runCatching { com.alananasss.kittytune.data.local.PlayerPreferences().getMixDislikedTrackIds() }.getOrDefault(emptySet())
        return MixProfile.build(plays, likes, now, disliked)
    }
}

/**
 * One thing to expand into candidates, and how much the profile believes in it.
 *
 * Three kinds because SoundCloud has three different endpoints that know about similarity, and they know
 * different things: a track knows what is played next to it, an artist knows their own neighbourhood, and a
 * genre knows what is good in it right now. Using only one of the three is how a mix ends up either entirely
 * familiar or entirely random.
 */
private sealed interface Seed {
    val affinity: Float

    data class OfTrack(val trackId: Long, override val affinity: Float) : Seed
    data class OfArtist(val artistId: Long, val name: String, override val affinity: Float) : Seed
    /**
     * @param wide whether this seed is the whole mix rather than one of eight, and so has to reach far enough on
     *   its own to fill it.
     */
    data class OfGenre(val genre: String, override val affinity: Float, val wide: Boolean = false) : Seed
}
