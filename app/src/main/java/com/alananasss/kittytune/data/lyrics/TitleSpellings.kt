package com.alananasss.kittytune.data.lyrics

import com.alananasss.kittytune.core.BoundedCache
import com.alananasss.kittytune.data.LyricsMatcher
import com.alananasss.kittytune.data.network.GeniusClient

/**
 * Spellings of a lyrics query that other sources use, for a title written as one word on SoundCloud ("NEWYORK")
 * that the lyrics sources only find spelled out ("NEW YORK"); see [LyricsMatcher.respellWith]. Genius is asked
 * because its search forgives the run-together spelling and its titles keep the real one. Remembered per query.
 */
object TitleSpellings {

    private val cache = BoundedCache<String, Spelling>(128)

    private data class Spelling(val query: String?)

    suspend fun respelled(query: String): String? {
        val key = query.trim().lowercase()
        if (key.isEmpty()) return null
        cache[key]?.let { return it.query }
        val titles = runCatching { GeniusClient.search(query).mapNotNull { it.title } }.getOrDefault(emptyList())
        val respelled = LyricsMatcher.respellWith(query, titles)?.takeIf { !it.equals(query, ignoreCase = true) }
        cache[key] = Spelling(respelled)
        return respelled
    }
}
