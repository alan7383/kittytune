package com.alananasss.kittytune.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the [BlockedContent] table (issue #41).
 *
 * Queries are split between track blocks and artist blocks to keep them cheap:
 * we never load the full list of blocked IDs in a hot path — only when the user
 * opens the settings page. For the hot path (playback / list filtering) we use
 * [BlockManager]'s in-memory cache instead.
 */
@Dao
interface BlockedContentDao {

    // ─── Inserts ─────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBlock(block: BlockedContent)

    // ─── Track queries ────────────────────────────────────────────────────────

    @Query("SELECT * FROM blocked_content WHERE trackId IS NOT NULL ORDER BY blockedAt DESC")
    fun observeBlockedTracks(): Flow<List<BlockedContent>>

    @Query("SELECT * FROM blocked_content WHERE trackId IS NOT NULL ORDER BY blockedAt DESC")
    suspend fun getBlockedTracksList(): List<BlockedContent>

    @Query("SELECT trackId FROM blocked_content WHERE trackId IS NOT NULL")
    suspend fun getAllBlockedTrackIds(): List<Long>

    @Query("SELECT COUNT(*) FROM blocked_content WHERE trackId = :trackId")
    suspend fun isTrackBlocked(trackId: Long): Int

    @Query("DELETE FROM blocked_content WHERE trackId = :trackId")
    suspend fun unblockTrack(trackId: Long)

    // ─── Artist queries ───────────────────────────────────────────────────────

    @Query("SELECT * FROM blocked_content WHERE artistId IS NOT NULL ORDER BY blockedAt DESC")
    fun observeBlockedArtists(): Flow<List<BlockedContent>>

    @Query("SELECT * FROM blocked_content WHERE artistId IS NOT NULL ORDER BY blockedAt DESC")
    suspend fun getBlockedArtistsList(): List<BlockedContent>

    @Query("SELECT artistId FROM blocked_content WHERE artistId IS NOT NULL")
    suspend fun getAllBlockedArtistIds(): List<Long>

    @Query("SELECT COUNT(*) FROM blocked_content WHERE artistId = :artistId")
    suspend fun isArtistBlocked(artistId: Long): Int

    @Query("DELETE FROM blocked_content WHERE artistId = :artistId")
    suspend fun unblockArtist(artistId: Long)

    // ─── Combined ─────────────────────────────────────────────────────────────

    @Query("SELECT * FROM blocked_content ORDER BY blockedAt DESC")
    fun observeAll(): Flow<List<BlockedContent>>
}
