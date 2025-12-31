package com.alananasss.kittytune.ui.home

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.launch

class TagViewModel(application: Application) : AndroidViewModel(application) {
    private val api = RetrofitClient.create(application)

    val tracks = mutableStateListOf<Track>()
    var uiState by mutableStateOf("LOADING")

    private var nextHref: String? = null
    private var isLoadingMore = false
    private var currentTagName: String = ""

    fun loadTag(tagName: String) {
        currentTagName = tagName
        refreshData()
    }

    private fun refreshData() {
        if (currentTagName.isBlank()) return

        uiState = "LOADING"
        tracks.clear()
        nextHref = null

        viewModelScope.launch {
            try {
                val cleanTag = currentTagName.replace("#", "").trim()

                // simple call: force "recent" sort to get new stuff
                val result = api.searchTracksStrict(
                    query = cleanTag,
                    tag = cleanTag,
                    sort = "recent"
                )

                nextHref = result.next_href

                val verifiedTracks = filterTracksByTag(result.collection, cleanTag)
                tracks.addAll(verifiedTracks)

                uiState = if (tracks.isEmpty()) "EMPTY" else "SUCCESS"
            } catch (e: Exception) {
                e.printStackTrace()
                uiState = "ERROR"
            }
        }
    }

    fun loadMore() {
        if (isLoadingMore || nextHref == null) return

        isLoadingMore = true
        val cleanTag = currentTagName.replace("#", "").trim()

        viewModelScope.launch {
            try {
                val result = api.getSearchTracksNextPage(nextHref!!)
                nextHref = result.next_href

                val verifiedTracks = filterTracksByTag(result.collection, cleanTag)
                tracks.addAll(verifiedTracks)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingMore = false
            }
        }
    }

    private fun filterTracksByTag(rawTracks: List<Track>, tag: String): List<Track> {
        return rawTracks.filter { track ->
            val rawTags = track.tagList ?: ""
            val genre = track.genre ?: ""
            // double check that the track actually matches the tag
            rawTags.contains(tag, ignoreCase = true) ||
                    genre.contains(tag, ignoreCase = true)
        }
    }
}