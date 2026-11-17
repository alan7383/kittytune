package com.alananasss.kittytune.domain

import kotlin.math.ln

/**
 * Intelligent ranking algorithm for selecting the Top Match ("Meilleur résultat") artist.
 *
 * Rather than naively taking the first result returned by the search API, this calculates
 * a multi-criteria composite score evaluating:
 * 1. Text match relevance (exact name match, normalized match, word boundaries, prefix)
 * 2. Official verification badge (Certified / Pro status)
 * 3. Audience popularity & follower count (logarithmic scale)
 * 4. Catalog volume (track count)
 * 5. Search engine prior index bias (tie breaker)
 * 6. The listener's own picks for this query
 *
 * Between two accounts with the same name, the bigger audience used to lose to the one the search engine listed
 * first or the one with more uploads, which a reupload account always has (issue #66). Followers weigh more now,
 * uploads and position less, and an artist the listener keeps opening for this query comes out on top.
 */
object TopResultRanker {

    /**
     * Calculates the composite relevance score for an artist given the search query.
     */
    fun scoreArtist(user: User, query: String, index: Int = 0, timesPicked: Int = 0): Double {
        val name = user.username?.trim() ?: ""
        val q = query.trim()

        if (name.isEmpty() || q.isEmpty()) return 0.0

        var score = 0.0

        // 1. Textual match precision
        val cleanName = name.replace(Regex("[^\\p{L}\\p{Nd}]"), "").lowercase()
        val cleanQuery = q.replace(Regex("[^\\p{L}\\p{Nd}]"), "").lowercase()

        when {
            name.equals(q, ignoreCase = true) -> score += 1200.0 // Exact string match
            cleanName.isNotEmpty() && cleanName == cleanQuery -> score += 1100.0 // Exact normalized match (without punctuation/symbols)
            name.startsWith(q, ignoreCase = true) -> score += 750.0 // Starts with query
            name.split(" ", "-", "_").any { it.equals(q, ignoreCase = true) } -> score += 650.0 // Whole word match
            name.contains(q, ignoreCase = true) -> score += 350.0 // Substring match
            else -> score += 100.0
        }

        // 2. Official verified certification badge (Strong authenticity signal)
        if (user.verified) {
            score += 900.0
        }

        // 3. Audience popularity (Logarithmic follower count curve)
        val followers = user.followersCount
        if (followers > 0) {
            // Examples: 1k -> ~970 pts, 10k -> ~1290 pts, 40k -> ~1480 pts, 100k -> ~1610 pts, 1M -> ~1930 pts
            val followerScore = ln(followers.toDouble() + 1.0) * 140.0
            score += followerScore.coerceAtMost(2400.0)
        }

        // 4. Catalog volume, lightly: a reupload account has more tracks than the artist.
        val tracks = user.trackCount
        if (tracks > 0) {
            score += (ln(tracks.toDouble() + 1.0) * 20.0).coerceAtMost(100.0)
        }

        // 5. Original search engine index rank bias (slight priority to top search positions)
        score += (60.0 - (index * 15.0)).coerceAtLeast(0.0)

        // 6. Picked before for this very query.
        score += PICK_BONUS * timesPicked.coerceAtMost(MAX_COUNTED_PICKS)

        return score
    }

    /**
     * Finds the most relevant and popular top match artist from the search results.
     */
    fun findTopArtist(artists: List<User>, query: String, timesPicked: (User) -> Int = { 0 }): User? {
        if (artists.isEmpty()) return null
        return artists.mapIndexed { index, user -> user to scoreArtist(user, query, index, timesPicked(user)) }
            .maxByOrNull { it.second }
            ?.first
    }

    /** Enough for two picks to beat a tenfold difference in followers. */
    private const val PICK_BONUS = 400.0
    private const val MAX_COUNTED_PICKS = 3
}
