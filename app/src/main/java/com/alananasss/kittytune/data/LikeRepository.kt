package com.alananasss.kittytune.data

import android.content.Context
import android.content.SharedPreferences
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.Track
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.lang.reflect.Type

object LikeRepository {
    private const val PREF_NAME = "soundtune_likes_v3"
    private const val KEY_LIKED_TRACKS = "liked_tracks_full"
    private const val KEY_LOCALLY_UNLIKED_IDS = "locally_unliked_ids"

    private lateinit var prefs: SharedPreferences
    private lateinit var api: com.alananasss.kittytune.data.network.SoundCloudApi
    private lateinit var appContext: Context
    private val gson = Gson()
    private var cachedUserId: Long? = null

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _likedTracks = MutableStateFlow<List<Track>>(emptyList())
    val likedTracks = _likedTracks.asStateFlow()

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
        return prefs.getStringSet(KEY_LOCALLY_UNLIKED_IDS, emptySet())?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
    }

    private lateinit var playerPrefs: com.alananasss.kittytune.data.local.PlayerPreferences

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        api = RetrofitClient.create(context)
        playerPrefs = com.alananasss.kittytune.data.local.PlayerPreferences(context)
        loadFromPrefs()
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
            val safeSource = (track.source as? String) ?: "soundcloud"
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

        scope.launch {
            saveToPrefs()

            if (!playerPrefs.getSyncLikesEnabled()) return@launch
            val tokenManager = TokenManager(appContext)
            val token = tokenManager.getAccessToken()
            if (!token.isNullOrEmpty() && !tokenManager.isGuestMode()) {
                val uid = getUserId()
                if (uid != null) {
                    com.alananasss.kittytune.data.SessionManager.syncLikeState(track.id, true, token, uid)
                }
            }
        }
    }

    fun removeLike(trackId: Long) {
        addToBlacklist(trackId)

        _likedTracks.update { it.filterNot { t -> t.id == trackId } }

        scope.launch {
            saveToPrefs()
            if (!playerPrefs.getSyncLikesEnabled()) return@launch
            val tokenManager = TokenManager(appContext)
            val token = tokenManager.getAccessToken()
            if (!token.isNullOrEmpty() && !tokenManager.isGuestMode()) {
                val uid = getUserId()
                if (uid != null) {
                    com.alananasss.kittytune.data.SessionManager.syncLikeState(trackId, false, token, uid)
                }
            }
        }
    }

    fun isTrackLiked(trackId: Long): Boolean {
        return _likedTracks.value.any { it.id == trackId }
    }

    fun replaceAllLikes(serverTracks: List<Track>) {
        _likedTracks.update { currentLocalList ->
            val blacklist = getBlacklist()

            val serverList = serverTracks
                .filter { !blacklist.contains(it.id) }
                .map { it.copy(isLiked = true) }

            val combined = currentLocalList + serverList

            val mergedAndDeduplicated = combined
                .groupBy { it.id }
                .map { (_, tracks) -> tracks.maxByOrNull { it.likedAt ?: 0L }!! }

            mergedAndDeduplicated.sortedByDescending { it.likedAt ?: System.currentTimeMillis() }
        }

        scope.launch {
            saveToPrefs()
        }
        _isSyncing.value = false
    }

    fun setSyncing(isSync: Boolean) {
        _isSyncing.value = isSync
    }

    private fun saveToPrefs() {
        val json = gson.toJson(_likedTracks.value)
        prefs.edit().putString(KEY_LIKED_TRACKS, json).apply()
    }

    private fun loadFromPrefs() {
        val json = prefs.getString(KEY_LIKED_TRACKS, null)
        if (json != null) {
            try {
                val type: Type = object : TypeToken<List<Track>>() {}.type
                val loadedList: List<Track> = gson.fromJson(json, type) ?: emptyList()

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

            } catch (e: Exception) {
                _likedTracks.value = emptyList()
            }
        }
    }
}