package com.alananasss.kittytune.data

import android.content.Context
import android.util.Log
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.BlockedContent
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton for content filtering (issue #41).
 *
 * Manages blocked tracks and artists with an in-memory cache for O(1) hot-path checks
 * during playback and list rendering. The underlying Room database is the source of
 * truth; the cache is rebuilt on init and kept in sync via coroutines.
 *
 * Block reasons:
 * - [REASON_MANUAL]       — user explicitly blocked via the 3-dot menu
 * - [REASON_AI_GENERATED] — ArtifactNet detected AI-generated audio (auto-skip enabled)
 */
object BlockManager {

    private const val TAG = "BlockManager"
    const val REASON_MANUAL = "MANUAL"
    const val REASON_AI_GENERATED = "AI_GENERATED"

    private val scope = CoroutineScope(Dispatchers.IO)
    private var db: AppDatabase? = null

    // ─── In-memory caches (thread-safe) ──────────────────────────────────────

    /** Blocked track IDs — checked on every list render and before playback. */
    private val blockedTrackIds = ConcurrentHashMap.newKeySet<Long>()

    /** Blocked artist IDs — checked when filtering lists. */
    private val blockedArtistIds = ConcurrentHashMap.newKeySet<Long>()

    /** Observable StateFlows for reactive Compose UI and ViewModels. */
    private val _blockedTrackIdsFlow = MutableStateFlow<Set<Long>>(emptySet())
    val blockedTrackIdsFlow: StateFlow<Set<Long>> = _blockedTrackIdsFlow.asStateFlow()

    private val _blockedArtistIdsFlow = MutableStateFlow<Set<Long>>(emptySet())
    val blockedArtistIdsFlow: StateFlow<Set<Long>> = _blockedArtistIdsFlow.asStateFlow()

    /** Callback invoked on the main thread when the current playing track is blocked. */
    var onCurrentTrackBlocked: (() -> Unit)? = null

    /** Callbacks invoked when content is blocked or unblocked. */
    var onTrackBlocked: ((Long) -> Unit)? = null
    var onTrackUnblocked: ((Long) -> Unit)? = null
    var onArtistBlocked: ((Long) -> Unit)? = null
    var onArtistUnblocked: ((Long) -> Unit)? = null

    // ─── Init ─────────────────────────────────────────────────────────────────

    fun init(context: Context) {
        if (db != null) return
        db = AppDatabase.getDatabase(context)
        scope.launch {
            // Warm the in-memory caches from DB
            val trackIds = db!!.blockedContentDao().getAllBlockedTrackIds()
            val artistIds = db!!.blockedContentDao().getAllBlockedArtistIds()
            blockedTrackIds.addAll(trackIds)
            blockedArtistIds.addAll(artistIds)
            _blockedTrackIdsFlow.value = blockedTrackIds.toSet()
            _blockedArtistIdsFlow.value = blockedArtistIds.toSet()
            Log.i(TAG, "Loaded ${trackIds.size} blocked tracks, ${artistIds.size} blocked artists")
        }
    }

    // ─── Hot-path checks (O(1), no DB) ────────────────────────────────────────

    /** Returns true if this track or its artist is blocked. */
    fun isBlocked(track: Track): Boolean {
        if (blockedTrackIds.contains(track.id)) return true
        val artistId = track.user?.id ?: return false
        return blockedArtistIds.contains(artistId)
    }

    fun isTrackBlocked(trackId: Long): Boolean = blockedTrackIds.contains(trackId)

    fun isArtistBlocked(artistId: Long): Boolean = blockedArtistIds.contains(artistId)

    // ─── Block actions ────────────────────────────────────────────────────────

    /**
     * Block a single track.
     * @param skipIfCurrent  If true and this is the currently playing track,
     *                       [onCurrentTrackBlocked] is invoked so the caller can skip.
     */
    fun blockTrack(
        track: Track,
        reason: String = REASON_MANUAL,
        skipIfCurrent: Boolean = true,
        currentlyPlayingId: Long? = null
    ) {
        val trackId = track.id
        if (blockedTrackIds.add(trackId)) {
            _blockedTrackIdsFlow.value = blockedTrackIds.toSet()
            scope.launch {
                db?.blockedContentDao()?.insertBlock(
                    BlockedContent(
                        trackId = trackId,
                        trackTitle = track.title,
                        trackArtworkUrl = track.artworkUrl,
                        source = track.source ?: "soundcloud",
                        reason = reason
                    )
                )
                Log.i(TAG, "Blocked track $trackId (\"${track.title}\") reason=$reason")
            }
            onTrackBlocked?.invoke(trackId)
        }
        if (skipIfCurrent && currentlyPlayingId == trackId) {
            onCurrentTrackBlocked?.invoke()
        }
    }

    /**
     * Block an artist by ID.
     * @param skipIfCurrent  If true and the currently playing track belongs to this artist,
     *                       [onCurrentTrackBlocked] is invoked.
     */
    fun blockArtist(
        artistId: Long,
        artistName: String,
        avatarUrl: String? = null,
        source: String = "soundcloud",
        reason: String = REASON_MANUAL,
        currentlyPlayingTrack: Track? = null
    ) {
        if (blockedArtistIds.add(artistId)) {
            _blockedArtistIdsFlow.value = blockedArtistIds.toSet()
            scope.launch {
                db?.blockedContentDao()?.insertBlock(
                    BlockedContent(
                        artistId = artistId,
                        artistName = artistName,
                        artistAvatarUrl = avatarUrl,
                        source = source,
                        reason = reason
                    )
                )
                Log.i(TAG, "Blocked artist $artistId (\"$artistName\") reason=$reason")
            }
            onArtistBlocked?.invoke(artistId)
        }
        if (currentlyPlayingTrack?.user?.id == artistId) {
            onCurrentTrackBlocked?.invoke()
        }
    }

    // ─── Unblock ─────────────────────────────────────────────────────────────

    fun unblockTrack(trackId: Long) {
        if (blockedTrackIds.remove(trackId)) {
            _blockedTrackIdsFlow.value = blockedTrackIds.toSet()
            scope.launch { db?.blockedContentDao()?.unblockTrack(trackId) }
            onTrackUnblocked?.invoke(trackId)
            Log.i(TAG, "Unblocked track $trackId")
        }
    }

    fun unblockArtist(artistId: Long) {
        if (blockedArtistIds.remove(artistId)) {
            _blockedArtistIdsFlow.value = blockedArtistIds.toSet()
            scope.launch { db?.blockedContentDao()?.unblockArtist(artistId) }
            onArtistUnblocked?.invoke(artistId)
            Log.i(TAG, "Unblocked artist $artistId")
        }
    }

    // ─── Observable streams (for Settings screen) ─────────────────────────────

    fun observeBlockedTracks(): Flow<List<BlockedContent>>? =
        db?.blockedContentDao()?.observeBlockedTracks()

    fun observeBlockedArtists(): Flow<List<BlockedContent>>? =
        db?.blockedContentDao()?.observeBlockedArtists()

    fun observeAll(): Flow<List<BlockedContent>>? =
        db?.blockedContentDao()?.observeAll()

    // ─── Filter utility ───────────────────────────────────────────────────────

    /** Filter a list of tracks, removing any that are blocked. */
    fun filterBlocked(tracks: List<Track>): List<Track> =
        tracks.filter { !isBlocked(it) }
}
