package com.alananasss.kittytune.ui.home

import com.alananasss.kittytune.data.LyricsMatcher

/**
 * A second try for a search that found nothing like what was typed.
 *
 * SoundCloud matches whole words, so "9mice NEWYORK" finds nothing of "9mice - New-York": the title's words
 * are "new" and "york". Spelled without spaces and dashes the two are the same, and a catalogue with a
 * forgiving search (Deezer) does find it; its spelling of the song is then searched for instead (issue #66).
 */
internal object SearchQueryRepair {

    /** Whether none of the first [results] (title to artist) contains everything typed in [query]. */
    fun needsRepair(query: String, results: List<Pair<String, String>>): Boolean {
        val tokens = tokens(query)
        if (tokens.isEmpty()) return false
        return results.take(RESULTS_CHECKED).none { (title, artist) -> covers(tokens, title, artist) }
    }

    /**
     * The first of [candidates] (title to artist) that contains everything typed in [query] once spaces and
     * punctuation are ignored, written out as "artist title", or null when none does or it is what was typed.
     */
    fun repairedQuery(query: String, candidates: List<Pair<String, String>>): String? {
        val tokens = tokens(query)
        if (tokens.isEmpty()) return null
        val (title, artist) = candidates.firstOrNull { (title, artist) -> covers(tokens, title, artist) } ?: return null
        val repaired = "${artist.trim()} ${LyricsMatcher.cleanNoiseAndBrackets(title)}".trim()
        return repaired.takeUnless { LyricsMatcher.normalize(it) == LyricsMatcher.normalize(query) }
    }

    private fun covers(tokens: List<String>, title: String, artist: String): Boolean {
        val text = compact("$artist $title")
        return tokens.all { it in text }
    }

    private fun tokens(query: String): List<String> =
        LyricsMatcher.normalize(query).split(' ').filter { it.isNotBlank() }

    private fun compact(text: String): String = LyricsMatcher.normalize(text).replace(" ", "")

    /** How many of the top results have to miss before the search is tried again. */
    private const val RESULTS_CHECKED = 8
}
