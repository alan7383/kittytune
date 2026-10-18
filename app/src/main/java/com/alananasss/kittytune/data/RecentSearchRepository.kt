package com.alananasss.kittytune.data

import android.content.Context
import android.content.SharedPreferences
import com.alananasss.kittytune.KittyTuneApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * The queries this listener has searched for, newest first.
 *
 * One row per distinct term, the term itself as the key: searching for the same thing twice moves it
 * back to the top instead of leaving two rows that read identically.
 */
object RecentSearchRepository {

    /** How many queries are kept. Older ones are dropped as new ones arrive. */
    const val MAX_ENTRIES = 20

    private const val MIN_LENGTH = 3
    private const val PREFS_NAME = "recent_searches"
    private const val KEY_SEARCHES = "searches"

    private val prefs: SharedPreferences?
        get() = try { KittyTuneApp.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) } catch (e: Exception) { null }

    private val _searches = MutableStateFlow<List<String>>(load())

    fun recent(): Flow<List<String>> = _searches.asStateFlow()

    private fun load(): List<String> {
        val p = prefs ?: return emptyList()
        val raw = p.getString(KEY_SEARCHES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun persist(list: List<String>) {
        _searches.value = list
        prefs?.edit()?.putString(KEY_SEARCHES, JSONArray(list).toString())?.apply()
    }

    suspend fun record(query: String) {
        val term = query.trim()
        if (term.length < MIN_LENGTH) return
        val current = _searches.value.toMutableList()
        current.remove(term)
        current.add(0, term)
        persist(current.take(MAX_ENTRIES))
    }

    suspend fun forget(query: String) {
        val term = query.trim()
        val current = _searches.value.toMutableList()
        current.remove(term)
        persist(current)
    }

    suspend fun clear() {
        persist(emptyList())
    }
}
