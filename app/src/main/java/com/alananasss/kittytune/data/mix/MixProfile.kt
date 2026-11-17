package com.alananasss.kittytune.data.mix

import com.alananasss.kittytune.domain.Track

/**
 * The scoring behind "start mixing" — what makes a track worth putting in front of somebody (issue #33).
 *
 * ## What was asked for, and the one clause that shapes everything
 *
 * "Add your own mix, on the main screen, above your time, just click the start mixing button, which,
 * according to your interests, gives out songs that you like **(these are not your favorite songs)**, you can
 * also customize your mix, select a genre or artist's songs, for example, in the Yeat style."
 *
 * That parenthesis is the whole specification. A mix built from what somebody has already liked is a playlist
 * of their likes, which they already have and did not ask for. What he wants is the *inference*: from what you
 * play, work out what you would play, and hand you things you have not heard. So the profile is built from
 * listening rather than from likes, and anything already liked or recently played is penalised rather than
 * rewarded.
 *
 * ## Why the profile comes from plays and not from likes
 *
 * A like is a single bit recorded once, often out of politeness to an artist, and it never decays. A play is a
 * repeated, involuntary vote: nobody plays a track forty times by accident. The stats table already records
 * every play with a timestamp and a duration, which is a far richer signal than the likes list — and it is the
 * signal that answers "according to your interests" honestly.
 *
 * Everything here is pure. The network half lives in [MixEngine]; this is the part worth testing, because a
 * recommendation engine's bugs are all in its arithmetic.
 */
object MixProfile {

    /**
     * What the listener's plays say about them: a weight per artist and per genre.
     *
     * @param artists lower-cased artist name to weight.
     * @param genres lower-cased genre or tag to weight.
     * @param knownTrackIds everything they have played or liked, which is what the mix must avoid.
     * @param knownArtists artists they already listen to, kept separately from [artists] because a *seed* and
     *   a *thing to avoid* are different roles: the mix leans on these to find candidates and then prefers the
     *   candidates that are not by them.
     */
    data class Taste(
        val artists: Map<String, Float>,
        val genres: Map<String, Float>,
        val knownTrackIds: Set<Long>,
        val knownArtists: Set<String>,
    ) {
        val isEmpty: Boolean get() = artists.isEmpty() && genres.isEmpty()

        /** Artists in weight order, which is the order seeds are drawn in. */
        val rankedArtists: List<String> get() = artists.entries.sortedByDescending { it.value }.map { it.key }

        val rankedGenres: List<String> get() = genres.entries.sortedByDescending { it.value }.map { it.key }
    }

    /**
     * One play, reduced to what the profile cares about.
     *
     * @param atMs when it happened, for the decay in [weightOf].
     * @param listenedMs how much of it was actually heard, which is what separates a play from a skip.
     */
    data class Play(
        val trackId: Long,
        val artist: String,
        val genre: String?,
        val atMs: Long,
        val listenedMs: Long,
        val durationMs: Long,
    )

    /**
     * How much one play counts.
     *
     * Two multipliers on top of the play itself.
     *
     * **Completion.** A track skipped after eight seconds is evidence *against* it, and the stats table records
     * those the same way it records a track played to the end. Below [SKIP_FRACTION] of its length a play
     * contributes nothing at all; above it, it contributes in proportion. Without this the profile is dominated
     * by whatever somebody skipped through while looking for something else.
     *
     * **Recency.** Halved every [HALF_LIFE_DAYS], so a phase somebody went through last spring informs the mix
     * without dictating it. A profile with no decay converges on whatever you listened to most in your life and
     * then never changes, which is the failure mode of every recommender that felt stale.
     */
    fun weightOf(play: Play, nowMs: Long): Float {
        val fraction =
            if (play.durationMs <= 0L) 1f
            else (play.listenedMs.toFloat() / play.durationMs).coerceIn(0f, 1f)
        if (fraction < SKIP_FRACTION) return 0f

        val ageDays = ((nowMs - play.atMs).coerceAtLeast(0L)).toFloat() / DAY_MS
        val recency = Math.pow(0.5, (ageDays / HALF_LIFE_DAYS).toDouble()).toFloat()
        return fraction * recency
    }

    /** Under a fifth of a track, somebody was looking for something else. */
    const val SKIP_FRACTION = 0.2f

    /** Three weeks, which is about how long a phase lasts before it stops being what you are listening to. */
    const val HALF_LIFE_DAYS = 21f

    private const val DAY_MS = 24 * 60 * 60 * 1000f

    /**
     * Builds the profile.
     *
     * Likes are folded in at [LIKE_WEIGHT] — present, because a like *is* a signal, and small, because it is a
     * much weaker one than a play and it does not decay. Their real job here is the other one: everything liked
     * goes into [Taste.knownTrackIds], so the mix knows not to hand it back.
     */
    fun build(plays: List<Play>, likes: List<Track>, nowMs: Long, dislikedTrackIds: Set<Long> = emptySet()): Taste {
        val artists = HashMap<String, Float>()
        val genres = HashMap<String, Float>()
        val known = HashSet<Long>(dislikedTrackIds)
        val knownArtists = HashSet<String>()

        for (play in plays) {
            known += play.trackId
            val weight = weightOf(play, nowMs)
            val artist = play.artist.normalised()
            if (artist.isNotEmpty()) {
                knownArtists += artist
                if (weight > 0f) artists.merge(artist, weight, Float::plus)
            }
            if (weight > 0f) play.genre?.normalised()?.takeIf { it.isNotEmpty() }?.let {
                genres.merge(it, weight, Float::plus)
            }
        }

        for (like in likes) {
            known += like.id
            val artist = (like.displayArtist.ifBlank { like.user?.username.orEmpty() }).normalised()
            if (artist.isNotEmpty()) {
                knownArtists += artist
                artists.merge(artist, LIKE_WEIGHT, Float::plus)
            }
            like.genre?.normalised()?.takeIf { it.isNotEmpty() }?.let {
                genres.merge(it, LIKE_WEIGHT, Float::plus)
            }
        }

        return Taste(artists, genres, known, knownArtists)
    }

    /** A like is worth about a third of a completed play, and unlike a play it never fades. */
    const val LIKE_WEIGHT = 0.35f

    private fun String.normalised(): String = trim().lowercase()
}

/**
 * Ranking and shaping the candidates a mix was built from (issue #33).
 *
 * Separate from [MixProfile] because it answers a different question. The profile is *who is this listener*;
 * this is *given a heap of candidate tracks, which twenty go in the mix and in what order*. Both are pure, and
 * both are here rather than in [MixEngine] because arithmetic is where a recommender goes wrong and arithmetic
 * is what can be tested.
 */
object MixRanking {

    /**
     * A candidate and why it might belong.
     *
     * @param seedAffinity how strongly the thing that produced this candidate matched the profile, in `0f..1f`.
     *   A track found through the listener's top artist scores higher than one found through their twelfth.
     */
    data class Candidate(val track: Track, val seedAffinity: Float)

    /**
     * How good a candidate is, before diversity gets a say.
     *
     * Five terms, and the two negative ones are the point of the feature.
     *
     * - **Seed affinity**, weighted highest: this is the "according to your interests" part.
     * - **Already known** — liked, or played before — is disqualifying, not merely penalised. "These are not
     *   your favorite songs": handing somebody their own likes back is the one outcome that makes the button
     *   pointless.
     * - **An artist they already listen to** is penalised but allowed. A mix with none of your artists in it
     *   feels like somebody else's; a mix that is only your artists is not discovery. The penalty is what
     *   produces a few familiar names among mostly new ones.
     * - **Popularity**, mildly and logarithmically. A track nobody has played is usually unplayed for a reason,
     *   and a straight play-count sort would return the same global hits to everybody — the log flattens that
     *   into "not obscure" rather than "famous".
     * - **A short-track penalty**, because SoundCloud is full of thirty-second snippets and interludes that
     *   nothing else distinguishes from songs.
     *
     * @return the score, or null when the candidate is disqualified.
     */
    fun score(
        candidate: Candidate,
        taste: MixProfile.Taste,
        /** Passed in rather than read here, so the scoring stays pure and testable at both settings. */
        youtubeFallback: Boolean = true,
        prioritizeTrusted: Boolean = true,
    ): Float? {
        val track = candidate.track
        if (track.id in taste.knownTrackIds) return null
        if (!isPlayable(track, youtubeFallback)) return null
        val duration = track.durationMs ?: 0L
        if (duration in 1 until MIN_DURATION_MS) return null

        val artist = (track.displayArtist.ifBlank { track.user?.username.orEmpty() }).trim().lowercase()
        val familiar = artist.isNotEmpty() && artist in taste.knownArtists

        val popularity = (track.playbackCount.coerceAtLeast(0) + 1).toDouble()
        val popularityTerm = (Math.log10(popularity) / 7.0).coerceIn(0.0, 1.0).toFloat()

        val trustedTerm = if (prioritizeTrusted) {
            val followers = (track.user?.followersCount ?: 0).coerceAtLeast(0)
            val followersTerm = if (followers > 0) {
                (Math.log10(followers.toDouble() + 1.0) / 6.0).coerceIn(0.0, 1.0).toFloat()
            } else 0f

            val isTrusted = track.user?.verified == true || track.user?.isPro == true
            val verifiedBonus = if (isTrusted) TRUSTED_SOURCE_BONUS else 0f
            val obscurePenalty = if (!isTrusted && followers < 30) OBSCURE_UPLOADER_PENALTY else 0f

            followersTerm * FOLLOWERS_WEIGHT + verifiedBonus + obscurePenalty
        } else 0f

        return candidate.seedAffinity * AFFINITY_WEIGHT +
            popularityTerm * POPULARITY_WEIGHT +
            trustedTerm +
            (if (familiar) FAMILIAR_ARTIST_PENALTY else 0f)
    }

    /**
     * Whether *anything* can play this — which is not the same question as whether SoundCloud can.
     *
     * ## Why the first version of this was wrong
     *
     * "Les titres injouables pourquoi ils sont injouables, car c'est des titres Go+ ? Si c'est le cas pourquoi ne
     * pas faire un fallback YouTube ?"
     *
     * Because I had not looked, and the answer is that the app already does. `SNIP` and a `SUB_HIGH_TIER`
     * monetisation are Go+ — a real SoundCloud track whose full audio needs a subscription — and `BLOCK` is one
     * that is geo-restricted or taken down. [com.alananasss.kittytune.data.StreamResolver] has resolved exactly
     * those through YouTube for as long as the setting has existed. My filter was throwing away tracks the player
     * would have played (issue #33).
     *
     * It was also checking `SNIPPET`, which is not a value their API returns — theirs is `SNIP` — so it was a
     * second, subtly wrong copy of a predicate that already existed. It defers to the real one now: one place
     * decides what "restricted" means, and this only asks whether there is a way to hear it.
     *
     * What is still excluded is the case where there is no route at all: restricted *and* the YouTube fallback
     * turned off. Then nothing can play it, and a hundred-track queue nobody vetted is the one list in the app
     * that cannot afford a row that silently fails.
     *
     * The candidates themselves remain SoundCloud's alone. Only where the audio comes from may differ, which is
     * exactly the instruction: "assure-toi que tous les titres du mix viennent uniquement de SoundCloud, mais
     * l'audio tu peux mettre YouTube uniquement si c'est Go+ ou injouable."
     *
     * @param youtubeFallback whether the setting that lets a restricted track be heard from YouTube is on.
     */
    fun isPlayable(track: Track, youtubeFallback: Boolean): Boolean {
        val restricted = com.alananasss.kittytune.data.StreamResolver.isRestricted(track) ||
            track.streamable == false
        return !restricted || youtubeFallback
    }

    private const val AFFINITY_WEIGHT = 1.0f
    private const val POPULARITY_WEIGHT = 0.25f
    const val FOLLOWERS_WEIGHT = 0.20f
    const val TRUSTED_SOURCE_BONUS = 0.12f
    const val OBSCURE_UPLOADER_PENALTY = -0.10f

    /** Enough to push a familiar artist below an equally-good stranger, not enough to exclude them. */
    private const val FAMILIAR_ARTIST_PENALTY = -0.18f

    /** Ninety seconds. Below that it is a snippet, an intro or a voice memo. */
    private const val MIN_DURATION_MS = 90_000L

    /**
     * The final running order: ranked, then spread out, then shuffled at the top.
     *
     * ## Why the best track is not first
     *
     * Ranking alone gives a mix that front-loads everything and then decays, and — worse — gives the *same*
     * order every time it is pressed, because the profile barely moves between two presses on the same evening.
     * A mix you cannot re-roll is a playlist.
     *
     * So the top [SHUFFLE_HEAD] are shuffled among themselves. They are all strong, the difference between the
     * first and the fourth is inside the noise of the scoring, and shuffling them means pressing the button
     * twice gives two mixes.
     *
     * ## Why no artist gets three tracks in a row
     *
     * A station seeded on one artist returns that artist's whole catalogue, so the raw ranking clumps: four by
     * one act, then four by another. The pass below takes the best remaining track whose artist has not been
     * used [ARTIST_GAP] slots ago, which interleaves without reordering by anything other than score.
     *
     * Two rules, and only one of them bends. [MAX_PER_ARTIST] is hard — a mix of ten tracks by one artist is the
     * opposite of a mix, so a mix that can only continue by breaking it ends short instead. [ARTIST_GAP] is
     * soft, because a mix of twenty with one artist twice in a row beats a mix of eleven.
     */
    fun order(
        candidates: List<Candidate>,
        taste: MixProfile.Taste,
        size: Int,
        seed: Long,
        youtubeFallback: Boolean = true,
        prioritizeTrusted: Boolean = true,
    ): List<Track> {
        val scored = candidates
            .mapNotNull { candidate -> score(candidate, taste, youtubeFallback, prioritizeTrusted)?.let { candidate.track to it } }
            .distinctBy { it.first.id }
            .sortedByDescending { it.second }
            .toMutableList()

        val chosen = ArrayList<Track>(size)
        val recentArtists = ArrayDeque<String>()
        val perArtist = HashMap<String, Int>()

        while (chosen.size < size && scored.isNotEmpty()) {
            fun artistOf(track: Track) = (track.displayArtist.ifBlank { track.user?.username.orEmpty() }).trim().lowercase()

            // Two rules, and only one of them bends.
            //
            // The cap is hard: a mix of ten tracks by one artist is the opposite of a mix, so if nothing left
            // is under the cap the mix ends here, short. The spacing is soft: a mix of twenty with one artist
            // twice in a row is better than a mix of eleven, so when nothing satisfies the spacing the best
            // candidate that is still under the cap is taken anyway.
            val underCap = { track: Track -> (perArtist[artistOf(track)] ?: 0) < MAX_PER_ARTIST }
            val spacedOut = { track: Track -> artistOf(track) !in recentArtists }

            val index = scored.indexOfFirst { underCap(it.first) && spacedOut(it.first) }
                .takeIf { it >= 0 }
                ?: scored.indexOfFirst { underCap(it.first) }
            if (index == null || index < 0) break

            val (track, _) = scored.removeAt(index)
            chosen += track

            val artist = artistOf(track)
            perArtist[artist] = (perArtist[artist] ?: 0) + 1
            recentArtists.addLast(artist)
            if (recentArtists.size > ARTIST_GAP) recentArtists.removeFirst()
        }

        if (chosen.size <= 1) return chosen
        val head = chosen.take(minOf(SHUFFLE_HEAD, chosen.size)).shuffled(java.util.Random(seed))
        return head + chosen.drop(head.size)
    }

    /** How many slots must pass before an artist may come round again. */
    private const val ARTIST_GAP = 3

    /** And how many tracks by one artist a mix may hold at all. */
    private const val MAX_PER_ARTIST = 2

    /** The band at the top that is shuffled, so two presses give two mixes. */
    private const val SHUFFLE_HEAD = 6
}


/**
 * Choosing which SoundCloud account somebody meant (issue #33).
 *
 * ## The bug
 *
 * "J'écris snorunt, un vrai artiste qui existe sur SoundCloud, et il me sort absolument rien."
 *
 * `search/users?q=snorunt&limit=1` answers with **Snorunt, 7 followers**. The artist anybody means is
 * `snorunt★`, 10,011 followers, third in the same response. SoundCloud's user search is not ordered by
 * prominence, so taking the first row seeded the mix on a stranger with no catalogue and no station — which is
 * exactly the empty result he got.
 *
 * Pure and separate from the request so the choice can be tested, because the choice is what was wrong.
 */
object MixArtistMatch {

    /** What the chooser needs to know about a candidate account. */
    data class Account(val id: Long, val username: String?, val followers: Long)

    /**
     * The account [wanted] most likely means, or null when the list is empty.
     *
     * Follower count decides, but only among names that plausibly *are* the name typed — and that filter matters
     * as much as the count. Without it, searching a short word finds whichever unrelated large account happens to
     * contain those letters, which would be the same bug pointing the other way.
     *
     * Decoration is not a difference: `snorunt★` is snorunt, `Snorunt.` is snorunt, and `ian` is not `Brian`
     * only because containment is checked both ways rather than by prefix.
     */
    fun best(candidates: List<Account>, wanted: String): Long? {
        if (candidates.isEmpty()) return null
        val target = wanted.letters()
        if (target.isEmpty()) return candidates.maxByOrNull { it.followers }?.id

        // Two tiers, and the tier beats the follower count. Containment alone was not enough: searching "ian"
        // matches "brianenoofficialfanuploads", and on followers alone a fan page with four million wins over the
        // artist with forty thousand. An exact match on the letters is a different kind of answer from a substring
        // and has to be treated as one.
        val scored = candidates.mapNotNull { account ->
            val name = account.username?.letters()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val tier = when {
                name == target -> EXACT
                name.contains(target) || target.contains(name) -> CONTAINS
                else -> return@mapNotNull null
            }
            account to tier
        }

        // Nothing resembled it, so trust their search over our own filter rather than answering nothing: a mix is
        // better seeded on their best guess than not built at all.
        if (scored.isEmpty()) return candidates.maxByOrNull { it.followers }?.id

        return scored
            .sortedWith(
                compareByDescending<Pair<Account, Int>> { it.second }.thenByDescending { it.first.followers }
            )
            .first().first.id
    }

    private const val EXACT = 2
    private const val CONTAINS = 1

    /**
     * Letters and digits only, lower-cased, after compatibility normalisation.
     *
     * The normalisation is the part that is not obvious and is not optional on SoundCloud, where decorated names
     * are the norm. `𝐘𝐄𝐀𝐓 ✪` is made of mathematical bold capitals, which *are* letters and which `lowercase()`
     * leaves exactly as they are — so without NFKC the real account with half a million followers does not match
     * the word "yeat" and loses to an impostor with ten. NFKC maps them onto the plain letters they are drawn to
     * look like, which is precisely what it is for.
     */
    private fun String.letters(): String =
        java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFKC)
            .lowercase()
            .filter { it.isLetterOrDigit() }
}
