package com.alananasss.kittytune.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.alananasss.kittytune.domain.Track
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object LikeRepository {
    private const val PREF_NAME = "soundtune_likes_v3"
    private const val KEY_LIKED_TRACKS = "liked_tracks_full"
    private const val KEY_PRIORITY_IDS = "saved_priority_ids"
    private const val KEY_DELETED_IDS = "saved_deleted_ids"

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    private val _likedTracks = MutableStateFlow<List<Track>>(emptyList())
    val likedTracks: StateFlow<List<Track>> = _likedTracks.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private val priorityIds = mutableSetOf<Long>()
    private val deletedIds = mutableSetOf<Long>()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        loadFromPrefs()
    }

    fun addLike(track: Track) {
        deletedIds.remove(track.id)
        priorityIds.add(track.id)
        // Added immediately to the top of the list for the UI
        _likedTracks.update { current ->
            listOf(track.copy(isLiked = true)) + current.filterNot { it.id == track.id }
        }
        saveToPrefs()
    }

    fun removeLike(trackId: Long) {
        priorityIds.remove(trackId)
        deletedIds.add(trackId)
        // Immediate removal for the UI
        _likedTracks.update { current ->
            current.filterNot { it.id == trackId }
        }
        saveToPrefs()
    }

    fun isTrackLiked(trackId: Long): Boolean {
        return _likedTracks.value.any { it.id == trackId }
    }

    // This function no longer updates the Flow on every page.
    // It replaces EVERYTHING once it's finished.
    fun replaceAllLikes(serverTracks: List<Track>) {
        val serverIds = serverTracks.map { it.id }.toSet()

        // 1. We clean up our local priorities if the server knows them
        val syncedVips = priorityIds.filter { serverIds.contains(it) }
        if (syncedVips.isNotEmpty()) {
            priorityIds.removeAll(syncedVips.toSet())
        }

        // 2. We rebuild the final list
        // First, recent local likes (Priority)
        val localVips = _likedTracks.value.filter { priorityIds.contains(it.id) }

        // Then the server tracks (removing those that were deleted locally)
        val validServerTracks = serverTracks.filterNot { deletedIds.contains(it.id) }
            .map { it.copy(isLiked = true) }

        // 3. Atomic Update
        val finalList = localVips + validServerTracks

        _likedTracks.value = finalList
        saveToPrefs()
        _isSyncing.value = false
    }

    fun setSyncing(isSync: Boolean) {
        _isSyncing.value = isSync
    }

    private fun saveToPrefs() {
        val listToSave = _likedTracks.value
        val prioritiesToSave = priorityIds.map { it.toString() }.toSet()
        val deletedsToSave = deletedIds.map { it.toString() }.toSet()

        Thread {
            try {
                val editor = prefs.edit()
                val json = gson.toJson(listToSave)
                editor.putString(KEY_LIKED_TRACKS, json)
                editor.putStringSet(KEY_PRIORITY_IDS, prioritiesToSave)
                editor.putStringSet(KEY_DELETED_IDS, deletedsToSave)
                editor.apply()
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }

    private fun loadFromPrefs() {
        try {
            val json = prefs.getString(KEY_LIKED_TRACKS, null)
            if (json != null) {
                val type = object : TypeToken<List<Track>>() {}.type
                _likedTracks.value = gson.fromJson(json, type)
            }
            val savedPriorities = prefs.getStringSet(KEY_PRIORITY_IDS, emptySet()) ?: emptySet()
            val savedDeleteds = prefs.getStringSet(KEY_DELETED_IDS, emptySet()) ?: emptySet()

            priorityIds.clear()
            priorityIds.addAll(savedPriorities.mapNotNull { it.toLongOrNull() })

            deletedIds.clear()
            deletedIds.addAll(savedDeleteds.mapNotNull { it.toLongOrNull() })
        } catch (e: Exception) { e.printStackTrace() }
    }
}