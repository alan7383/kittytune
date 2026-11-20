package com.alananasss.kittytune.ui.feed

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.mix.MixRanking
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.data.wave.DiscoverFeed
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import kotlinx.coroutines.launch

/** One post of the vertical feed: a track, who shared it and why it is here. */
data class FeedPost(
    val track: Track,
    val author: User?,
    val caption: String? = null,
    val isRepost: Boolean = false,
)

/**
 * The SoundCloud-style feed. Discover is the app's own endless smart feed — fresh
 * finds picked from likes, history and subscriptions through [DiscoverFeed] (which learns
 * from skips and never runs dry: taste mixer → paginated charts → related frontier);
 * Following is the chronological stream of uploads and captioned reposts from the artists
 * the user follows.
 */
class FeedViewModel(application: Application) : AndroidViewModel(application) {

    private val api = RetrofitClient.create(application)
    private val tokenManager = TokenManager(application)
    private val prefs = PlayerPreferences(application)

    val isLoggedIn: Boolean
        get() = !tokenManager.isGuestMode() && !tokenManager.getAccessToken().isNullOrEmpty()

    enum class FeedTab { DISCOVER, FOLLOWING }

    val discover = mutableStateListOf<FeedPost>()
    val following = mutableStateListOf<FeedPost>()

    /**
     * "Official artists only": the feed keeps just the tracks the Official mix station means by
     * that — a verified or Pro account, or a release carrying an ISRC. Lives in the view model so
     * paging stops wasting batches on rows the filter would hide, and re-reads the preference on
     * [applyOfficialOnly] so changing it in settings applies the moment the user comes back.
     */
    var officialOnly by mutableStateOf(prefs.getFeedOfficialOnlyEnabled())
        private set

    /** Renamed away from `setOfficialOnly` so it does not clash with the property's JVM setter. */
    fun applyOfficialOnly(enabled: Boolean) {
        if (enabled == officialOnly) return
        officialOnly = enabled
        prefs.setFeedOfficialOnlyEnabled(enabled)
        // Rebuild the stream the filter applies to, so nothing hidden stays visible. The
        // source starts from scratch too: its taste tier is re-primed and the chart walk
        // restarts, so tracks the old setting filtered out can finally surface.
        DiscoverFeed.reset()
        discover.clear()
        discoverExhausted = false
        seenDiscoverIds.clear()
        loadDiscoverMore()
        if (following.isNotEmpty() || followingNextHref != null) loadFollowingPage(reset = true)
    }

    /** Whether a track survives the "official artists only" feed filter. */
    private fun Track.isOfficialForFeed(): Boolean = !officialOnly || MixRanking.isOfficialArtist(this)

    /** When the official filter hides whole batches, keep pulling up to this many before waiting for a scroll. */
    private companion object {
        const val MAX_FILTERED_BATCHES = 5
        const val MAX_FILTERED_PAGES = 3
    }

    var discoverLoading by mutableStateOf(false)
        private set
    var followingLoading by mutableStateOf(false)
        private set
    var followingError by mutableStateOf(false)
        private set

    private var discoverExhausted = false
    private var followingNextHref: String? = null
    private val seenDiscoverIds = mutableSetOf<Long>()

    /** Per-author follow state; absent = not checked yet. */
    var followState by mutableStateOf<Map<Long, Boolean>>(emptyMap())
        private set
    private val followChecksInFlight = mutableSetOf<Long>()

    init {
        loadDiscoverMore()
    }

    /**
     * A fresh page of the smart feed: fresh finds only, nothing the user already saw.
     * An empty batch is no longer the end — [DiscoverFeed] only reports [DiscoverFeed.exhausted]
     * when every source tier is drained for good; otherwise the next scroll simply retries.
     * With the official filter on, a batch can surface nothing visible, so up to
     * [MAX_FILTERED_BATCHES] are pulled before the page gives up until the next scroll.
     */
    fun loadDiscoverMore() {
        if (discoverLoading || discoverExhausted) return
        discoverLoading = true
        viewModelScope.launch {
            try {
                repeat(MAX_FILTERED_BATCHES) {
                    if (discoverExhausted) return@repeat
                    val fresh = DiscoverFeed.nextBatch(exclude = seenDiscoverIds)
                    if (fresh.isEmpty()) {
                        // Transient "nothing right now" waits for the next scroll; only the
                        // source's own verdict ends the feed for good.
                        if (DiscoverFeed.exhausted) discoverExhausted = true
                        return@repeat
                    }
                    fresh.forEach { seenDiscoverIds += it.id }
                    val posts = fresh.filter { it.isOfficialForFeed() }
                        .map { FeedPost(track = it, author = it.user) }
                    discover.addAll(posts)
                    if (posts.isNotEmpty()) return@repeat
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                discoverLoading = false
            }
        }
    }

    private fun loadFollowingPage(reset: Boolean) {
        if (followingLoading) return
        followingLoading = true
        followingError = false
        viewModelScope.launch {
            try {
                if (reset) {
                    following.clear()
                    followingNextHref = null
                }
                // With the official filter on, a page can surface nothing visible; pull the next
                // one right away, but only while the server has more and nothing is showing yet.
                repeat(MAX_FILTERED_PAGES) {
                    val response = if (followingNextHref == null) {
                        api.getActivities(limit = 40)
                    } else {
                        api.getActivitiesNextPage(followingNextHref!!)
                    }
                    followingNextHref = response.next_href
                    val posts = response.collection.mapNotNull { item ->
                        val track = item.track ?: return@mapNotNull null
                        FeedPost(
                            track = track,
                            author = item.user ?: track.user,
                            caption = item.caption?.takeIf { it.isNotBlank() },
                            isRepost = item.type == "track-repost",
                        )
                    }.filter { it.track.isOfficialForFeed() }
                    following.addAll(posts)
                    if (posts.isNotEmpty() || followingNextHref == null || !officialOnly) return@repeat
                }
            } catch (e: Exception) {
                e.printStackTrace()
                followingError = true
            } finally {
                followingLoading = false
            }
        }
    }

    /** The Following tab hits the network only when the user actually opens it. */
    fun ensureFollowingLoaded() {
        if (!isLoggedIn) return
        if (following.isEmpty() && !followingLoading) loadFollowingPage(reset = true)
    }

    fun loadMoreFollowing() {
        if (!isLoggedIn) return
        if (followingNextHref != null && !followingLoading) loadFollowingPage(reset = false)
    }

    fun resetFollowing() {
        if (!isLoggedIn) return
        loadFollowingPage(reset = true)
    }

    /** Follow state is checked lazily, one call per author, only for cards actually shown. */
    fun checkFollowState(userId: Long) {
        if (!isLoggedIn || userId <= 0L) return
        if (followState.containsKey(userId) || userId in followChecksInFlight) return
        followChecksInFlight += userId
        viewModelScope.launch {
            val isFollowing = try {
                api.checkFollowState(userId).isSuccessful
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
            followChecksInFlight -= userId
            followState = followState + (userId to isFollowing)
        }
    }

    /** Optimistic toggle; rolls back if the server rejects it. */
    fun toggleFollow(userId: Long) {
        val isFollowing = followState[userId] ?: return
        followState = followState + (userId to !isFollowing)
        viewModelScope.launch {
            val ok = try {
                val response = if (isFollowing) api.unfollowUser(userId) else api.followUser(userId)
                response.isSuccessful
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
            if (!ok) followState = followState + (userId to isFollowing)
        }
    }
}
