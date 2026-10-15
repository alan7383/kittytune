package com.alananasss.kittytune.data.artist

import com.alananasss.kittytune.data.LyricsMatcher
import com.alananasss.kittytune.data.spotify.SpotifyArtist
import com.alananasss.kittytune.data.spotify.SpotifyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * An artist's reach: how many people listen to them monthly, follow them, and what they look like, from
 * Spotify's public pages (ported from desktop, PR #77) — the one number every artist page is judged by.
 */
object ArtistReach {

    data class Profile(
        val spotifyId: String,
        val name: String,
        val monthlyListeners: Long?,
        val followers: Long?,
        val avatarUrl: String?,
        val headerImageUrl: String?,
        val biography: String?,
        val isVerified: Boolean,
        val details: SpotifyArtist,
    )

    private const val CACHE_SIZE = 256
    private const val MAX_PARALLEL = 4

    /** Recent lookups, most recent last; nobody's monthly listeners move fast enough to matter. */
    private val cache = object : LinkedHashMap<String, Profile?>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Profile?>) = size > CACHE_SIZE
    }

    /** Four searches at a time is plenty; the calls are cheap and the result cached either way. */
    private val gate = Semaphore(MAX_PARALLEL)

    suspend fun profileOf(name: String): Profile? {
        val key = LyricsMatcher.normalize(name).replace(" ", "")
        if (key.isEmpty()) return null
        synchronized(cache) { cache[key] }?.let { return it }
        val profile = withContext(Dispatchers.IO) { gate.withPermit { fetchProfile(name) } }
        synchronized(cache) { cache[key] = profile }
        return profile
    }

    /** The artist's streaming header image, or a duo member's when they share the page. */
    suspend fun bannerOf(name: String): String? {
        profileOf(name)?.headerImageUrl?.let { return it }
        val members = ArtistClips.splitNames(name.replace('/', '&'))
        for (member in members) {
            profileOf(member)?.headerImageUrl?.let { return it }
        }
        return null
    }

    private suspend fun fetchProfile(name: String): Profile? {
        val found = runCatching { SpotifyRepository.search(name, limit = 8).artists }.getOrDefault(emptyList())
        val keyOf = { candidate: SpotifyArtist -> LyricsMatcher.normalize(candidate.name).replace(" ", "") }
        val wanted = LyricsMatcher.normalize(name).replace(" ", "")
        val match = found.filter { keyOf(it) == wanted }.maxByOrNull { if (it.verified) 1 else 0 }
            ?: return null
        val details = runCatching { SpotifyRepository.getArtist(match.id) }.getOrNull() ?: match
        return Profile(
            spotifyId = details.id,
            name = details.name,
            monthlyListeners = details.monthlyListeners?.takeIf { it > 0 },
            followers = details.followers?.takeIf { it > 0 },
            avatarUrl = details.avatarUrl,
            headerImageUrl = details.headerImageUrl,
            biography = details.biography,
            isVerified = details.verified,
            details = details,
        )
    }
}
