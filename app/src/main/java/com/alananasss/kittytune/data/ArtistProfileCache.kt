package com.alananasss.kittytune.data

import android.content.Context
import android.util.Log
import com.alananasss.kittytune.KittyTuneApp
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.LocalArtist
import com.alananasss.kittytune.data.local.toTrack
import com.alananasss.kittytune.data.spotify.SpotifyArtist
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

data class CachedArtistProfile(
    val user: User,
    val isSpotify: Boolean = false,
    val isVk: Boolean = false,
    val vkPageUrl: String? = null,
    val spotifyArtist: SpotifyArtist? = null,
    val popularTracks: List<Track> = emptyList(),
    val allTracks: List<Track> = emptyList(),
    val popularReleases: List<Playlist> = emptyList(),
    val albums: List<Playlist> = emptyList(),
    val singles: List<Playlist> = emptyList(),
    val compilations: List<Playlist> = emptyList(),
    val appearsOn: List<Playlist> = emptyList(),
    val discoveredOn: List<Playlist> = emptyList(),
    val similarArtists: List<User> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val likedTracks: List<Track> = emptyList(),
    val repostedTracks: List<Track> = emptyList(),
    val savedAtMs: Long = System.currentTimeMillis()
)

object ArtistProfileCache {
    private const val TAG = "ArtistProfileCache"
    private val gson = Gson()
    private val cacheType = object : TypeToken<CachedArtistProfile>() {}.type

    private val dir: File by lazy {
        File(KittyTuneApp.instance.filesDir, "artist_profiles").apply { mkdirs() }
    }

    private val memory = ConcurrentHashMap<String, CachedArtistProfile>()
    private val scope = CoroutineScope(Dispatchers.IO)

    fun normalizeKey(rawKey: String): String {
        val trimmed = rawKey.trim()
        val clean = com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(trimmed)
        return when {
            trimmed.startsWith("spotify") -> "spotify:$clean"
            clean.length == 22 && clean.all { it.isLetterOrDigit() } -> "spotify:$clean"
            trimmed.startsWith("sc:") || trimmed.startsWith("profile:") -> "sc:${trimmed.substringAfter(':')}"
            trimmed.toLongOrNull() != null -> "sc:$trimmed"
            trimmed.startsWith("vk") -> "vk:$trimmed"
            else -> "name:${trimmed.lowercase()}"
        }
    }

    private fun hashKey(key: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(key.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            key.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        }
    }

    fun save(rawKey: String, profile: CachedArtistProfile) {
        val primaryKey = normalizeKey(rawKey)
        memory[primaryKey] = profile

        // Also index by artist username if available
        profile.user.username?.let { name ->
            if (name.isNotBlank()) {
                val nameKey = "name:${name.trim().lowercase()}"
                memory[nameKey] = profile
            }
        }

        // Write to disk & sync to saved_artists in database
        scope.launch {
            try {
                val file = File(dir, "${hashKey(primaryKey)}.json")
                val json = gson.toJson(profile)
                file.writeText(json)

                // Also save auxiliary file for name lookup if primary key was not already name
                profile.user.username?.let { name ->
                    if (name.isNotBlank()) {
                        val nameFile = File(dir, "${hashKey("name:${name.trim().lowercase()}")}.json")
                        if (nameFile.absolutePath != file.absolutePath) {
                            nameFile.writeText(json)
                        }
                    }
                }

                // Auto-sync into Room's saved_artists so offline Library and Search know about this artist
                val db = AppDatabase.getDatabase(KittyTuneApp.instance).downloadDao()
                val artistId = profile.user.numericId
                if (artistId != 0L) {
                    val existing = db.getArtist(artistId)
                    val effectiveCount = profile.user.trackCount.coerceAtLeast(
                        profile.allTracks.size.coerceAtLeast(profile.popularTracks.size)
                    )
                    if (existing == null) {
                        db.insertArtist(
                            LocalArtist(
                                id = artistId,
                                username = profile.user.username ?: "",
                                avatarUrl = profile.user.avatarUrl ?: "",
                                trackCount = effectiveCount
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist cached artist profile for $primaryKey: ${e.message}")
            }
        }
    }

    fun get(rawKey: String): CachedArtistProfile? {
        val key = normalizeKey(rawKey)
        memory[key]?.let { return it }

        return try {
            val file = File(dir, "${hashKey(key)}.json")
            if (file.exists() && file.length() > 0) {
                val json = file.readText()
                val profile: CachedArtistProfile = gson.fromJson(json, cacheType)
                memory[key] = profile
                profile
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read cached artist profile for $key: ${e.message}")
            null
        }
    }

    fun getByName(artistName: String): CachedArtistProfile? {
        if (artistName.isBlank()) return null
        val direct = get("name:${artistName.trim().lowercase()}")
        if (direct != null) return direct

        // Fallback: search memory & disk for matching username
        val clean = artistName.trim().lowercase()
        memory.values.firstOrNull { it.user.username?.trim()?.lowercase() == clean }?.let { return it }

        for (cached in getAllCachedProfiles()) {
            if (cached.user.username?.trim()?.lowercase() == clean) {
                return cached
            }
        }
        return null
    }

    fun getAllCachedArtists(): List<User> {
        val artists = mutableListOf<User>()
        // From memory
        artists.addAll(memory.values.map { it.user })

        // From disk
        try {
            val files = dir.listFiles { f -> f.extension == "json" } ?: emptyArray()
            for (file in files) {
                try {
                    val json = file.readText()
                    val profile: CachedArtistProfile = gson.fromJson(json, cacheType)
                    artists.add(profile.user)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        return artists.distinctBy { it.username?.trim()?.lowercase() }
    }

    fun getAllCachedProfiles(): List<CachedArtistProfile> {
        val profiles = mutableListOf<CachedArtistProfile>()
        profiles.addAll(memory.values)
        try {
            val files = dir.listFiles { f -> f.extension == "json" } ?: emptyArray()
            for (file in files) {
                try {
                    val json = file.readText()
                    val profile: CachedArtistProfile = gson.fromJson(json, cacheType)
                    profiles.add(profile)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        return profiles.distinctBy { it.user.username?.trim()?.lowercase() }
    }

    suspend fun synthesizeFromDownloads(rawQuery: String, context: Context): CachedArtistProfile? = withContext(Dispatchers.IO) {
        val clean = rawQuery.trim().lowercase()
        if (clean.isBlank()) return@withContext null

        try {
            val db = AppDatabase.getDatabase(context).downloadDao()
            val allLocal = db.getAllTracksList().filter { it.localAudioPath.isNotEmpty() }
            val matching = allLocal.filter {
                val aName = it.artist.lowercase()
                aName == clean || aName.contains(clean) || clean.contains(aName)
            }

            if (matching.isNotEmpty()) {
                val primaryName = matching.first().artist
                val artwork = matching.firstOrNull { it.localArtworkPath.isNotEmpty() || it.artworkUrl.isNotEmpty() }
                    ?.let { it.localArtworkPath.ifEmpty { it.artworkUrl } }

                val tracks = matching.map { local ->
                    local.toTrack(
                        artworkOverride = local.localArtworkPath.ifEmpty { local.artworkUrl },
                        isLiked = true
                    )
                }

                val stableId = kotlin.math.abs(primaryName.hashCode().toLong())
                val synthesizedUser = User(
                    id = stableId,
                    username = primaryName,
                    avatarUrl = artwork,
                    trackCount = tracks.size
                )

                val profile = CachedArtistProfile(
                    user = synthesizedUser,
                    popularTracks = tracks,
                    allTracks = tracks
                )
                save(rawQuery, profile)
                profile
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
