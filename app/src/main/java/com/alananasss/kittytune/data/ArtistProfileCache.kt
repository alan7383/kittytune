package com.alananasss.kittytune.data

import android.content.Context
import android.util.Log
import com.alananasss.kittytune.KittyTuneApp
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.toTrack
import com.alananasss.kittytune.data.spotify.SpotifyArtist
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.SafePlaylistListAdapter
import com.alananasss.kittytune.domain.SafeTrackListAdapter
import com.alananasss.kittytune.domain.SafeUserListAdapter
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.utils.AppUtils
import com.google.gson.Gson
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

data class CachedArtistProfile(
    @SerializedName("user") val user: User? = null,
    @SerializedName("isSpotify", alternate = ["is_spotify"]) val isSpotify: Boolean = false,
    @SerializedName("isVk", alternate = ["is_vk"]) val isVk: Boolean = false,
    @SerializedName("vkPageUrl", alternate = ["vk_page_url"]) val vkPageUrl: String? = null,
    @SerializedName("spotifyArtist", alternate = ["spotify_artist"]) val spotifyArtist: SpotifyArtist? = null,
    @SerializedName("popularTracks", alternate = ["popular_tracks"]) @JsonAdapter(SafeTrackListAdapter::class) val popularTracks: List<Track> = emptyList(),
    @SerializedName("allTracks", alternate = ["all_tracks"]) @JsonAdapter(SafeTrackListAdapter::class) val allTracks: List<Track> = emptyList(),
    @SerializedName("popularReleases", alternate = ["popular_releases"]) @JsonAdapter(SafePlaylistListAdapter::class) val popularReleases: List<Playlist> = emptyList(),
    @SerializedName("albums") @JsonAdapter(SafePlaylistListAdapter::class) val albums: List<Playlist> = emptyList(),
    @SerializedName("singles") @JsonAdapter(SafePlaylistListAdapter::class) val singles: List<Playlist> = emptyList(),
    @SerializedName("compilations") @JsonAdapter(SafePlaylistListAdapter::class) val compilations: List<Playlist> = emptyList(),
    @SerializedName("appearsOn", alternate = ["appears_on"]) @JsonAdapter(SafePlaylistListAdapter::class) val appearsOn: List<Playlist> = emptyList(),
    @SerializedName("discoveredOn", alternate = ["discovered_on"]) @JsonAdapter(SafePlaylistListAdapter::class) val discoveredOn: List<Playlist> = emptyList(),
    @SerializedName("similarArtists", alternate = ["similar_artists"]) @JsonAdapter(SafeUserListAdapter::class) val similarArtists: List<User> = emptyList(),
    @SerializedName("playlists") @JsonAdapter(SafePlaylistListAdapter::class) val playlists: List<Playlist> = emptyList(),
    @SerializedName("likedTracks", alternate = ["liked_tracks"]) @JsonAdapter(SafeTrackListAdapter::class) val likedTracks: List<Track> = emptyList(),
    @SerializedName("repostedTracks", alternate = ["reposted_tracks"]) @JsonAdapter(SafeTrackListAdapter::class) val repostedTracks: List<Track> = emptyList(),
    @SerializedName("savedAtMs", alternate = ["saved_at_ms"]) val savedAtMs: Long = System.currentTimeMillis()
)

fun CachedArtistProfile.sanitized(): CachedArtistProfile = copy(
    popularTracks = (popularTracks as? List<*>)?.filterIsInstance<Track>() ?: emptyList(),
    allTracks = (allTracks as? List<*>)?.filterIsInstance<Track>() ?: emptyList(),
    popularReleases = (popularReleases as? List<*>)?.filterIsInstance<Playlist>() ?: emptyList(),
    albums = (albums as? List<*>)?.filterIsInstance<Playlist>() ?: emptyList(),
    singles = (singles as? List<*>)?.filterIsInstance<Playlist>() ?: emptyList(),
    compilations = (compilations as? List<*>)?.filterIsInstance<Playlist>() ?: emptyList(),
    appearsOn = (appearsOn as? List<*>)?.filterIsInstance<Playlist>() ?: emptyList(),
    discoveredOn = (discoveredOn as? List<*>)?.filterIsInstance<Playlist>() ?: emptyList(),
    similarArtists = (similarArtists as? List<*>)?.filterIsInstance<User>() ?: emptyList(),
    playlists = (playlists as? List<*>)?.filterIsInstance<Playlist>() ?: emptyList(),
    likedTracks = (likedTracks as? List<*>)?.filterIsInstance<Track>() ?: emptyList(),
    repostedTracks = (repostedTracks as? List<*>)?.filterIsInstance<Track>() ?: emptyList(),
)

object ArtistProfileCache {
    private const val TAG = "ArtistProfileCache"
    private val gson = AppUtils.gson

    private val dir: File by lazy {
        File(KittyTuneApp.instance.filesDir, "artist_profiles").apply { mkdirs() }
    }

    private val memory = ConcurrentHashMap<String, CachedArtistProfile>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        profile.user?.username?.let { name ->
            if (name.isNotBlank()) {
                val nameKey = "name:${name.trim().lowercase()}"
                memory[nameKey] = profile
            }
        }

        // Write to disk
        scope.launch {
            try {
                val file = File(dir, "${hashKey(primaryKey)}.json")
                val json = gson.toJson(profile)
                file.writeText(json)

                // Also save auxiliary file for name lookup if primary key was not already name
                profile.user?.username?.let { name ->
                    if (name.isNotBlank()) {
                        val nameFile = File(dir, "${hashKey("name:${name.trim().lowercase()}")}.json")
                        if (nameFile.absolutePath != file.absolutePath) {
                            nameFile.writeText(json)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist cached artist profile for $primaryKey: ${e.message}")
            }
        }
    }

    fun get(rawKey: String): CachedArtistProfile? {
        val key = normalizeKey(rawKey)
        memory[key]?.let {
            if (it.user != null) return it.sanitized() else memory.remove(key)
        }

        return try {
            val file = File(dir, "${hashKey(key)}.json")
            if (file.exists() && file.length() > 0) {
                val json = file.readText()
                val profile: CachedArtistProfile = gson.fromJson(json, CachedArtistProfile::class.java)
                if (profile.user != null) {
                    val safe = profile.sanitized()
                    memory[key] = safe
                    safe
                } else {
                    file.delete()
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read cached artist profile for $key: ${e.message}")
            runCatching { File(dir, "${hashKey(key)}.json").delete() }
            null
        }
    }

    fun getByName(artistName: String): CachedArtistProfile? {
        if (artistName.isBlank()) return null
        val direct = get("name:${artistName.trim().lowercase()}")
        if (direct != null) return direct

        // Fallback: search memory & disk for matching username
        val clean = artistName.trim().lowercase()
        memory.values.firstOrNull { it.user?.username?.trim()?.lowercase() == clean }?.let { return it.sanitized() }

        for (cached in getAllCachedProfiles()) {
            if (cached.user?.username?.trim()?.lowercase() == clean) {
                return cached.sanitized()
            }
        }
        return null
    }

    fun getAllCachedArtists(): List<User> {
        val artists = mutableListOf<User>()
        // From memory
        artists.addAll(memory.values.mapNotNull { it.user })

        // From disk
        try {
            val files = dir.listFiles { f -> f.extension == "json" } ?: emptyArray()
            for (file in files) {
                try {
                    val json = file.readText()
                    val profile: CachedArtistProfile = gson.fromJson(json, CachedArtistProfile::class.java)
                    profile.user?.let { artists.add(it) }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        return artists.distinctBy { it.username?.trim()?.lowercase() }
    }

    fun getAllCachedProfiles(): List<CachedArtistProfile> {
        val profiles = mutableListOf<CachedArtistProfile>()
        profiles.addAll(memory.values.filter { it.user != null }.map { it.sanitized() })
        try {
            val files = dir.listFiles { f -> f.extension == "json" } ?: emptyArray()
            for (file in files) {
                try {
                    val json = file.readText()
                    val profile: CachedArtistProfile = gson.fromJson(json, CachedArtistProfile::class.java)
                    if (profile.user != null) {
                        profiles.add(profile.sanitized())
                    } else {
                        file.delete()
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        return profiles.distinctBy { it.user?.username?.trim()?.lowercase() }
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
