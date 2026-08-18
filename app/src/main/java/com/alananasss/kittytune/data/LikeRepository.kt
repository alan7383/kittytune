package com.alananasss.kittytune.data

import android.content.Context
import android.content.SharedPreferences
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.data.network.TrackLikeItem
import com.alananasss.kittytune.data.network.TrackLikeRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.FileReader
import java.io.BufferedWriter
import java.io.BufferedReader
import java.lang.reflect.Type

object LikeRepository {
    private const val PREF_NAME = "soundtune_likes_v3"
    private const val KEY_LIKED_TRACKS = "liked_tracks_full"
    private const val KEY_LIKED_PLAYLISTS = "liked_playlists_ids"
    private const val KEY_LOCALLY_UNLIKED_IDS = "locally_unliked_ids"
    private const val LIKED_TRACKS_FILE = "liked_tracks_v1.json"
    private val fileLock = Any()

    private lateinit var prefs: SharedPreferences
    private lateinit var api: com.alananasss.kittytune.data.network.SoundCloudApi
    private lateinit var appContext: Context
    private val gson = com.alananasss.kittytune.utils.AppUtils.gson
    private var cachedUserId: Long? = null

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _likedTracks = MutableStateFlow<List<Track>>(emptyList())
    val likedTracks = _likedTracks.asStateFlow()

    private val _likedPlaylists = MutableStateFlow<Set<Long>>(emptySet())
    val likedPlaylists = _likedPlaylists.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private fun addToBlacklist(trackId: Long) {
        val current = getBlacklist().toMutableSet()
        current.add(trackId)
        prefs.edit().putStringSet(KEY_LOCALLY_UNLIKED_IDS, current.map { it.toString() }.toSet()).apply()
    }

    private fun removeFromBlacklist(trackId: Long) {
        val current = getBlacklist().toMutableSet()
        current.remove(trackId)
        prefs.edit().putStringSet(KEY_LOCALLY_UNLIKED_IDS, current.map { it.toString() }.toSet()).apply()
    }

    private fun getBlacklist(): Set<Long> {
        return prefs.getStringSet(KEY_LOCALLY_UNLIKED_IDS, emptySet())?.mapNotNull { it.toLongOrNull() }?.toSet()
            ?: emptySet()
    }

    private lateinit var playerPrefs: com.alananasss.kittytune.data.local.PlayerPreferences

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        api = RetrofitClient.create(context)
        playerPrefs = com.alananasss.kittytune.data.local.PlayerPreferences(context)
        loadFromStorage()
    }

    private suspend fun getUserId(): Long? {
        if (cachedUserId != null) return cachedUserId
        return try {
            val me = api.getMe()
            cachedUserId = me.id
            me.id
        } catch (e: Exception) {
            null
        }
    }

    fun addLike(track: Track) {
        removeFromBlacklist(track.id)

        _likedTracks.update { current ->
            val safeSource = track.source ?: "soundcloud"
            if (current.any { it.id == track.id }) {
                current
            } else {
                val newTrack = track.copy(
                    isLiked = true,
                    source = safeSource,
                    likedAt = System.currentTimeMillis()
                )
                (listOf(newTrack) + current).sortedByDescending { it.likedAt ?: 0L }
            }
        }

        saveLikedTracks()

        scope.launch {
            if (!playerPrefs.getSyncLikesEnabled()) return@launch
            val tokenManager = TokenManager(appContext)
            if (tokenManager.isGuestMode()) return@launch
            val token = tokenManager.getAccessToken()
            if (!token.isNullOrEmpty()) {
                try {
                    val payload = TrackLikeRequest(
                        likes = listOf(TrackLikeItem("soundcloud:tracks:${track.id}"))
                    )
                    val response = api.likeTrack(payload)
                    if (response.code() == 401) {
                        com.alananasss.kittytune.data.SessionManager.requestSessionRefresh(appContext, force = true)
                    }
                    android.util.Log.d("LikeRepository", "Direct track like success status: ${response.code()}")
                } catch (e: Exception) {
                    android.util.Log.e("LikeRepository", "Direct track like failed", e)
                }
            }
        }
    }

    fun removeLike(trackId: Long) {
        addToBlacklist(trackId)

        _likedTracks.update { it.filterNot { t -> t.id == trackId } }

        saveLikedTracks()

        scope.launch {
            if (!playerPrefs.getSyncLikesEnabled()) return@launch
            val tokenManager = TokenManager(appContext)
            if (tokenManager.isGuestMode()) return@launch
            val token = tokenManager.getAccessToken()
            if (!token.isNullOrEmpty()) {
                try {
                    val payload = TrackLikeRequest(
                        likes = listOf(TrackLikeItem("soundcloud:tracks:$trackId"))
                    )
                    val response = api.unlikeTrack(payload)
                    if (response.code() == 401) {
                        com.alananasss.kittytune.data.SessionManager.requestSessionRefresh(appContext, force = true)
                    }
                    android.util.Log.d("LikeRepository", "Direct track unlike success status: ${response.code()}")
                } catch (e: Exception) {
                    android.util.Log.e("LikeRepository", "Direct track unlike failed", e)
                }
            }
        }
    }

    fun isTrackLiked(trackId: Long): Boolean {
        return _likedTracks.value.any { it.id == trackId }
    }

    fun isPlaylistLiked(playlistId: Long): Boolean {
        return _likedPlaylists.value.contains(playlistId)
    }

    fun setLikedPlaylists(ids: Set<Long>) {
        _likedPlaylists.value = ids
        saveLikedPlaylists()
    }

    fun togglePlaylistLike(playlistId: Long, isLiked: Boolean, permalink: String? = null, urn: String? = null) {
        val current = _likedPlaylists.value.toMutableSet()
        if (isLiked) {
            current.add(playlistId)
            DownloadManager.clearDeletedPlaylistId(playlistId)
        } else {
            current.remove(playlistId)
            DownloadManager.clearDeletedPlaylistId(playlistId)
        }
        _likedPlaylists.value = current
        DownloadManager.notifyLibraryUpdated()
        saveLikedPlaylists()

        scope.launch {
            val tokenManager = com.alananasss.kittytune.data.TokenManager(appContext)
            if (tokenManager.isGuestMode()) return@launch
            val token = tokenManager.getAccessToken()
            if (!token.isNullOrEmpty()) {
                try {
                    val safePermalink = permalink ?: ""
                    val targetUrn = urn ?: when {
                        safePermalink.contains("artist-stations") -> "soundcloud:system-playlists:artist-stations:$playlistId"
                        safePermalink.contains("track-stations") -> "soundcloud:system-playlists:track-stations:$playlistId"
                        else -> "soundcloud:playlists:$playlistId"
                    }
                    val payload = com.alananasss.kittytune.data.network.PlaylistLikeRequest(
                        likes = listOf(com.alananasss.kittytune.data.network.PlaylistLikeItem(targetUrn))
                    )

                    val response = if (isLiked) api.likePlaylist(payload) else api.unlikePlaylist(payload)

                    if (response.code() == 401) {
                        com.alananasss.kittytune.data.SessionManager.requestSessionRefresh(appContext, force = true)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun replaceAllLikes(serverTracks: List<Track>, currentUserId: Long? = null) {
        if (currentUserId != null) {
            val lastUserId = prefs.getLong("last_synced_user_id", -1L)
            if (lastUserId != -1L && lastUserId != currentUserId) {
                prefs.edit().remove(KEY_LOCALLY_UNLIKED_IDS).apply()
            }
            prefs.edit().putLong("last_synced_user_id", currentUserId).apply()
            cachedUserId = currentUserId
        }

        _likedTracks.update { currentLocalList ->
            val blacklist = getBlacklist()

            val serverList = serverTracks
                .filter { !blacklist.contains(it.id) }
                .map { it.copy(isLiked = true) }

            val localNonSoundcloud = currentLocalList.filter {
                (it.source != "soundcloud" || it.id <= 0L) && !blacklist.contains(it.id)
            }

            val combined = localNonSoundcloud + serverList

            val mergedAndDeduplicated = combined
                .groupBy { it.id }
                .map { (_, tracks) -> tracks.maxByOrNull { it.likedAt ?: 0L }!! }

            mergedAndDeduplicated.sortedByDescending { it.likedAt ?: System.currentTimeMillis() }
        }

        saveLikedTracks()
        _isSyncing.value = false
    }

    fun clear() {
        cachedUserId = null
        _likedTracks.value = emptyList()
        _likedPlaylists.value = emptySet()
        _isSyncing.value = false
        prefs.edit()
            .remove(KEY_LIKED_TRACKS)
            .remove(KEY_LIKED_PLAYLISTS)
            .remove(KEY_LOCALLY_UNLIKED_IDS)
            .remove("last_synced_user_id")
            .apply()
        scope.launch(Dispatchers.IO) {
            synchronized(fileLock) {
                try {
                    val file = File(appContext.filesDir, LIKED_TRACKS_FILE)
                    if (file.exists()) file.delete()
                    val tmpFile = File(appContext.filesDir, "$LIKED_TRACKS_FILE.tmp")
                    if (tmpFile.exists()) tmpFile.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun setSyncing(isSync: Boolean) {
        _isSyncing.value = isSync
    }

    private fun saveLikedTracks() {
        val tracks = _likedTracks.value
        scope.launch(Dispatchers.IO) {
            synchronized(fileLock) {
                try {
                    val file = File(appContext.filesDir, LIKED_TRACKS_FILE)
                    val tmpFile = File(appContext.filesDir, "$LIKED_TRACKS_FILE.tmp")
                    FileWriter(tmpFile).use { fileWriter ->
                        BufferedWriter(fileWriter).use { bufferedWriter ->
                            val type: Type = object : TypeToken<List<Track>>() {}.type
                            gson.toJson(tracks, type, bufferedWriter)
                        }
                    }
                    if (tmpFile.exists()) {
                        if (file.exists()) {
                            file.delete()
                        }
                        tmpFile.renameTo(file)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun saveLikedPlaylists() {
        prefs.edit()
            .putStringSet(KEY_LIKED_PLAYLISTS, _likedPlaylists.value.map { it.toString() }.toSet())
            .apply()
    }

    private fun loadFromStorage() {
        var loadedList: List<Track>? = null
        val file = File(appContext.filesDir, LIKED_TRACKS_FILE)
        if (file.exists()) {
            try {
                FileReader(file).use { fileReader ->
                    BufferedReader(fileReader).use { bufferedReader ->
                        val type: Type = object : TypeToken<List<Track>>() {}.type
                        loadedList = gson.fromJson(bufferedReader, type)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (loadedList == null) {
            val json = prefs.getString(KEY_LIKED_TRACKS, null)
            if (json != null) {
                try {
                    val type: Type = object : TypeToken<List<Track>>() {}.type
                    loadedList = gson.fromJson(json, type)
                    prefs.edit().remove(KEY_LIKED_TRACKS).apply()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        if (loadedList != null) {
            val now = System.currentTimeMillis()
            val migratedList = loadedList.mapIndexed { index, track ->
                if (track.likedAt == null || track.likedAt == 0L) {
                    track.copy(likedAt = now - (index * 1000))
                } else {
                    track
                }
            }
            val blacklist = getBlacklist()
            _likedTracks.value = migratedList.filter { !blacklist.contains(it.id) }
            saveLikedTracks()
        }

        val savedPlaylistIds =
            prefs.getStringSet(KEY_LIKED_PLAYLISTS, emptySet())?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
        _likedPlaylists.value = savedPlaylistIds
    }
}
