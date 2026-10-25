package com.alananasss.kittytune.data.search

import android.content.Context
import com.alananasss.kittytune.KittyTuneApp
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.utils.AppUtils
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What was opened from search, so the search page can offer the artist or the track itself next time instead of
 * the words typed to find it (issue #66): typing "kai angel" and opening the artist leaves the artist in the
 * recent list, one click from their page.
 */
object RecentVisits {

    enum class Kind { ARTIST, TRACK, PLAYLIST }

    /**
     * @param destination where an artist or a playlist opens, as `navigateToPlaylistId` takes it.
     * @param track the track itself, for a track, played straight from the list.
     */
    data class Visit(
        @SerializedName("kind") val kind: Kind,
        @SerializedName("key") val key: String,
        @SerializedName("title") val title: String,
        @SerializedName("subtitle") val subtitle: String?,
        @SerializedName("imageUrl", alternate = ["image_url"]) val imageUrl: String?,
        @SerializedName("destination") val destination: String?,
        @SerializedName("track") val track: Track?,
        @SerializedName("isVerified", alternate = ["is_verified"]) val isVerified: Boolean,
        @SerializedName("at") val at: Long,
    )

    private const val PREFS_NAME = "search_memory"
    private const val KEY = "recent_search_visits"
    private const val MAX_VISITS = 12

    private val gson = AppUtils.gson

    private fun getPrefs() = runCatching {
        KittyTuneApp.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }.getOrNull()

    private val _visits = MutableStateFlow(load())
    val visits: StateFlow<List<Visit>> = _visits.asStateFlow()

    fun record(visit: Visit) {
        val updated = (listOf(visit) + _visits.value.filterNot { it.kind == visit.kind && it.key == visit.key })
            .take(MAX_VISITS)
        save(updated)
    }

    fun forget(visit: Visit) = save(_visits.value.filterNot { it.kind == visit.kind && it.key == visit.key })

    fun clear() = save(emptyList())

    private fun save(list: List<Visit>) {
        _visits.value = list
        getPrefs()?.edit()?.putString(KEY, gson.toJson(list))?.apply()
    }

    private fun load(): List<Visit> = runCatching {
        val raw = getPrefs()?.getString(KEY, null) ?: return emptyList()
        val visits = gson.fromJson(raw, Array<Visit>::class.java)?.filterNotNull() ?: emptyList()
        visits.map { v ->
            if (v.track != null && v.track !is Track) v.copy(track = null) else v
        }
    }.getOrDefault(emptyList())
}

/**
 * Which artist the listener picked for a query, to put them first next time (issue #66). When the best result
 * for "kai angel" is a copy and the listener keeps opening the real one, the real one becomes the best result.
 */
object SearchPicks {

    private const val PREFS_NAME = "search_memory"
    private const val KEY = "search_artist_picks"
    private const val MAX_QUERIES = 200

    private val gson = Gson()

    private fun getPrefs() = runCatching {
        KittyTuneApp.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }.getOrNull()

    private var picks: LinkedHashMap<String, MutableMap<String, Int>> = load()

    @Synchronized
    fun record(query: String, artistId: Long) {
        val key = normalize(query)
        if (key.isEmpty() || artistId == 0L) return
        val counts = picks.remove(key) ?: mutableMapOf()
        counts[artistId.toString()] = (counts[artistId.toString()] ?: 0) + 1
        picks[key] = counts
        while (picks.size > MAX_QUERIES) picks.remove(picks.keys.first())
        getPrefs()?.edit()?.putString(KEY, gson.toJson(picks))?.apply()
    }

    /** How many times [artistId] was picked for [query]. */
    @Synchronized
    fun timesPicked(query: String, artistId: Long): Int = picks[normalize(query)]?.get(artistId.toString()) ?: 0

    private fun normalize(query: String) = query.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun load(): LinkedHashMap<String, MutableMap<String, Int>> = runCatching {
        val raw = getPrefs()?.getString(KEY, null) ?: return LinkedHashMap()
        gson.fromJson<LinkedHashMap<String, MutableMap<String, Int>>>(
            raw,
            object : TypeToken<LinkedHashMap<String, MutableMap<String, Int>>>() {}.type,
        ) ?: LinkedHashMap()
    }.getOrDefault(LinkedHashMap())
}
