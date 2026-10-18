package com.alananasss.kittytune.ui.track

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The page of a track: who liked and reposted it, which playlists it is in, and what is like it.
 *
 * Likers and reposters open by followers, the most followed first, and playlists by popularity:
 * the popular playlists come from the GraphQL `featuredPlaylists` endpoint, which returns playlist URNs
 * already ordered server-side in a single call. The full playlists are then hydrated in parallel and shown
 * progressively as they arrive. The chronological order (toggle off) still pages through
 * GET tracks/{id}/playlists on scroll. Everything loaded is kept in [TrackDetailsCache] and
 * comes back when the page is opened again.
 */
class TrackDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val api = RetrofitClient.create(application)

    var track by mutableStateOf<Track?>(null)
    var isLoading by mutableStateOf(true)

    // What the lists show, in the order they are sorted to.
    val likers = mutableStateListOf<User>()
    val reposters = mutableStateListOf<User>()
    val inPlaylists = mutableStateListOf<Playlist>()
    val relatedTracks = mutableStateListOf<Track>()

    // The same, as the service gave them, for going back to that order.
    private val likersArrived = mutableListOf<User>()
    private val repostersArrived = mutableListOf<User>()
    // Chronological order of "appears in" (GET tracks/{id}/playlists pages, as arrived).
    private val playlistsArrived = mutableListOf<Playlist>()
    // Popular order of "appears in" (GraphQL featuredPlaylists, server order — never re-sorted).
    private val playlistsPopularArrived = mutableListOf<Playlist>()

    // pagination cursors (next_href)
    private var likersNextUrl: String? = null
    private var repostersNextUrl: String? = null
    private var playlistsNextUrl: String? = null
    private var relatedNextUrl: String? = null

    // individual loading states for infinite scroll
    var isLikersLoadingMore by mutableStateOf(false)
    var isRepostersLoadingMore by mutableStateOf(false)
    var isPlaylistsLoadingMore by mutableStateOf(false)
    var isRelatedLoadingMore by mutableStateOf(false)

    // loading state for sort toggle (full re-fetch)
    var isPlaylistsSortLoading by mutableStateOf(false)
    var isUsersSortLoading by mutableStateOf(false)

    var isUsersSortedByFollowers by mutableStateOf(true)
        private set
    var isPlaylistsSortedByLikes by mutableStateOf(true)
        private set

    private var loadedTrackId: Long = 0L
    private var prefetchJob: Job? = null
    private var playlistsFetchJob: Job? = null

    fun loadTrackDetails(trackId: Long) {
        if (trackId == 0L) {
            isLoading = false
            return
        }
        if (this.track?.id == trackId) {
            isLoading = false
            return
        }
        loadedTrackId = trackId
        TrackDetailsCache.get(trackId)?.let { restore(it); return }

        viewModelScope.launch {
            isLoading = true
            // clean slate
            likers.clear(); likersArrived.clear(); likersNextUrl = null
            reposters.clear(); repostersArrived.clear(); repostersNextUrl = null
            inPlaylists.clear(); playlistsArrived.clear(); playlistsPopularArrived.clear(); playlistsNextUrl = null
            relatedTracks.clear(); relatedNextUrl = null

            try {
                coroutineScope {
                    val trackDef = async { api.getTracksByIds(trackId.toString()).firstOrNull() }
                    val likersResponseDef = async { try { api.getTrackLikers(trackId) } catch (e: Exception) { null } }
                    val repostersResponseDef = async { try { api.getTrackReposters(trackId) } catch (e: Exception) { null } }
                    val playlistsResponseDef = async { try { api.getTrackInPlaylists(trackId, limit = PLAYLISTS_PAGE) } catch (e: Exception) { null } }
                    val relatedResponseDef = async { try { api.getRelatedTracks(trackId) } catch (e: Exception) { null } }

                    track = trackDef.await()

                    likersResponseDef.await()?.let {
                        likersArrived.addAll(it.collection)
                        likersNextUrl = it.next_href
                    }
                    repostersResponseDef.await()?.let {
                        repostersArrived.addAll(it.collection)
                        repostersNextUrl = it.next_href
                    }
                    playlistsResponseDef.await()?.let {
                        playlistsArrived.addAll(it.collection)
                        playlistsNextUrl = it.next_href
                    }
                    relatedResponseDef.await()?.let {
                        relatedTracks.addAll(it.collection)
                        relatedNextUrl = it.next_href
                    }
                }
                showUsers()
                showPlaylists()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
                save()
            }
            prefetchUsers()
            fetchPopularPlaylists()
        }
    }

    // --- the order of the lists ---

    private fun showUsers() {
        val uniqueLikers = likersArrived.distinctBy { it.id }
        val uniqueReposters = repostersArrived.distinctBy { it.id }
        likers.clear(); likers.addAll(TrackDetailOrder.users(uniqueLikers, isUsersSortedByFollowers))
        reposters.clear(); reposters.addAll(TrackDetailOrder.users(uniqueReposters, isUsersSortedByFollowers))
    }

    private fun showPlaylists() {
        inPlaylists.clear()
        if (isPlaylistsSortedByLikes) {
            inPlaylists.addAll(playlistsPopularArrived.distinctBy { it.id })
        } else {
            inPlaylists.addAll(playlistsArrived.distinctBy { it.id })
        }
    }

    fun toggleUsersSort() {
        isUsersSortedByFollowers = !isUsersSortedByFollowers
        showUsers()
        save()
        if (isUsersSortedByFollowers) {
            prefetchUsers()
        }
    }

    fun toggleSortPlaylists() {
        isPlaylistsSortedByLikes = !isPlaylistsSortedByLikes
        showPlaylists()
        save()
        if (isPlaylistsSortedByLikes && playlistsPopularArrived.isEmpty() && playlistsFetchJob?.isActive != true) {
            fetchPopularPlaylists()
        }
    }

    /**
     * More pages of those who liked and reposted, updating as they arrive, so "the most followed first"
     * is chosen from a large sample and the list updates live.
     */
    private fun prefetchUsers() {
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            val id = loadedTrackId
            isUsersSortLoading = true
            try {
                var pagesFetched = 0
                while (pagesFetched < PREFETCH_PAGES) {
                    val nextLikers = likersNextUrl
                    val nextReposters = repostersNextUrl
                    if (nextLikers == null && nextReposters == null) break

                    if (nextLikers != null) {
                        try {
                            val page = api.getLikersNextPage(nextLikers)
                            likersArrived.addAll(page.collection)
                            likersNextUrl = page.next_href
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            likersNextUrl = null
                        }
                    }
                    if (nextReposters != null) {
                        try {
                            val page = api.getRepostersNextPage(nextReposters)
                            repostersArrived.addAll(page.collection)
                            repostersNextUrl = page.next_href
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            repostersNextUrl = null
                        }
                    }
                    pagesFetched++
                    if (id == loadedTrackId) {
                        showUsers()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isUsersSortLoading = false
                if (id == loadedTrackId) {
                    showUsers()
                    save()
                }
            }
        }
    }

    /**
     * The most popular playlists the track appears in:
     * one GraphQL `featuredPlaylists` call returns playlist URNs already ordered by
     * popularity, then the playlists are hydrated in parallel and shown progressively.
     */
    private fun fetchPopularPlaylists() {
        if (!isPlaylistsSortedByLikes) return
        playlistsFetchJob?.cancel()
        playlistsFetchJob = viewModelScope.launch {
            val id = loadedTrackId
            isPlaylistsSortLoading = true
            isPlaylistsLoadingMore = playlistsPopularArrived.isEmpty()
            try {
                val req = FeaturedPlaylistsGraphQlRequest(
                    variables = FeaturedPlaylistsVariables(
                        input = FeaturedPlaylistsInput(
                            trackUrn = "soundcloud:tracks:$id",
                            filter = "PLAYLIST",
                            limit = POPULAR_LIMIT,
                        )
                    )
                )
                val urns = api.getFeaturedPlaylistsGraphQL(req)
                    .data?.featuredPlaylists.orEmpty()
                    .mapNotNull { it.urn }
                    .distinct()
                if (urns.isEmpty()) {
                    fetchPopularFallbackLocked(id)
                    return@launch
                }
                val ids = urns.mapNotNull { it.substringAfterLast(":").toLongOrNull() }
                if (id == loadedTrackId && playlistsPopularArrived.isNotEmpty()) {
                    // keep what we have
                } else {
                    playlistsPopularArrived.clear()
                }
                val knownIds = playlistsPopularArrived.map { it.id }.toHashSet()
                val missing = ids.filter { it !in knownIds }
                for (chunk in missing.chunked(HYDRATE_CONCURRENCY)) {
                    ensureActive()
                    val hydrated = withContext(Dispatchers.IO) {
                        chunk.map { pid ->
                            async {
                                try {
                                    api.getPlaylist(pid)
                                } catch (_: Exception) {
                                    null
                                }
                            }
                        }.awaitAll().filterNotNull()
                    }
                    if (id != loadedTrackId) return@launch
                    playlistsPopularArrived.addAll(hydrated)
                    showPlaylists()
                    isPlaylistsLoadingMore = false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                if (playlistsPopularArrived.isEmpty()) {
                    fetchPopularFallbackLocked(id)
                    return@launch
                }
            } finally {
                isPlaylistsSortLoading = false
                isPlaylistsLoadingMore = false
                if (id == loadedTrackId) {
                    showPlaylists()
                    save()
                }
            }
        }
    }

    private suspend fun fetchPopularFallbackLocked(id: Long) {
        for (p in playlistsArrived) {
            if (playlistsPopularArrived.none { it.id == p.id }) playlistsPopularArrived.add(p)
        }
        playlistsPopularArrived.sortByDescending { it.likesCount ?: 0 }
        if (id == loadedTrackId) {
            showPlaylists()
            isPlaylistsLoadingMore = false
        }
        var next = playlistsNextUrl
        var pageCount = 0
        while (next != null && pageCount < FALLBACK_PAGES) {
            try {
                val res = withContext(Dispatchers.IO) { api.getInPlaylistsNextPage(next) }
                if (id != loadedTrackId) return
                playlistsPopularArrived.addAll(res.collection)
                playlistsPopularArrived.sortByDescending { it.likesCount ?: 0 }
                playlistsArrived.addAll(res.collection)
                next = res.next_href
                playlistsNextUrl = next
                pageCount++
                showPlaylists()
                isPlaylistsLoadingMore = false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                break
            }
        }
    }

    // --- load more functions ---

    fun loadMoreLikers() {
        if (isLikersLoadingMore || likersNextUrl == null) return
        viewModelScope.launch {
            isLikersLoadingMore = true
            try {
                val res = api.getLikersNextPage(likersNextUrl!!)
                likersArrived.addAll(res.collection)
                likersNextUrl = res.next_href
                showUsers()
            } catch (e: Exception) { e.printStackTrace() }
            finally { isLikersLoadingMore = false; save() }
        }
    }

    fun loadMoreReposters() {
        if (isRepostersLoadingMore || repostersNextUrl == null) return
        viewModelScope.launch {
            isRepostersLoadingMore = true
            try {
                val res = api.getRepostersNextPage(repostersNextUrl!!)
                repostersArrived.addAll(res.collection)
                repostersNextUrl = res.next_href
                showUsers()
            } catch (e: Exception) { e.printStackTrace() }
            finally { isRepostersLoadingMore = false; save() }
        }
    }

    fun loadMorePlaylists() {
        if (isPlaylistsSortedByLikes) return
        if (isPlaylistsLoadingMore || playlistsNextUrl == null) return
        viewModelScope.launch {
            isPlaylistsLoadingMore = true
            try {
                val res = api.getInPlaylistsNextPage(playlistsNextUrl!!)
                playlistsArrived.addAll(res.collection)
                playlistsNextUrl = res.next_href
                showPlaylists()
            } catch (e: Exception) { e.printStackTrace() }
            finally { isPlaylistsLoadingMore = false; save() }
        }
    }

    fun loadMoreRelated() {
        if (isRelatedLoadingMore || relatedNextUrl == null) return
        viewModelScope.launch {
            isRelatedLoadingMore = true
            try {
                val res = api.getRelatedTracksNextPage(relatedNextUrl!!)
                relatedTracks.addAll(res.collection)
                relatedNextUrl = res.next_href
            } catch (e: Exception) { e.printStackTrace() }
            finally { isRelatedLoadingMore = false; save() }
        }
    }

    // --- what is kept ---

    private fun save() {
        val current = track ?: return
        TrackDetailsCache.put(
            current.id,
            TrackDetailsCache.Snapshot(
                track = current,
                likers = likersArrived.toList(),
                reposters = repostersArrived.toList(),
                playlists = playlistsArrived.toList(),
                playlistsPopular = playlistsPopularArrived.toList(),
                related = relatedTracks.toList(),
                likersNext = likersNextUrl,
                repostersNext = repostersNextUrl,
                playlistsNext = playlistsNextUrl,
                relatedNext = relatedNextUrl,
                usersByFollowers = isUsersSortedByFollowers,
                playlistsByLikes = isPlaylistsSortedByLikes,
                takenAtMs = System.currentTimeMillis(),
            )
        )
    }

    private fun restore(snapshot: TrackDetailsCache.Snapshot) {
        track = snapshot.track
        likersArrived.clear(); likersArrived.addAll(snapshot.likers)
        repostersArrived.clear(); repostersArrived.addAll(snapshot.reposters)
        playlistsArrived.clear(); playlistsArrived.addAll(snapshot.playlists)
        playlistsPopularArrived.clear(); playlistsPopularArrived.addAll(snapshot.playlistsPopular)
        relatedTracks.clear(); relatedTracks.addAll(snapshot.related)
        likersNextUrl = snapshot.likersNext
        repostersNextUrl = snapshot.repostersNext
        playlistsNextUrl = snapshot.playlistsNext
        relatedNextUrl = snapshot.relatedNext
        isUsersSortedByFollowers = snapshot.usersByFollowers
        isPlaylistsSortedByLikes = snapshot.playlistsByLikes
        showUsers()
        showPlaylists()
        isLoading = false
        if (isPlaylistsSortedByLikes && playlistsPopularArrived.isEmpty()) {
            fetchPopularPlaylists()
        }
    }

    private companion object {
        const val PLAYLISTS_PAGE = 50
        const val PREFETCH_PAGES = 10
        const val POPULAR_LIMIT = 100
        const val HYDRATE_CONCURRENCY = 10
        const val FALLBACK_PAGES = 6
    }
}
