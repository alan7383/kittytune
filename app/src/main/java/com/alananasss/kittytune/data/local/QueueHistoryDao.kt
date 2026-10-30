package com.alananasss.kittytune.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QueueHistoryDao {
    @Query("SELECT id, title, trackCount, currentIndex, currentPositionMs, contextJson, firstTrackArtworkUrl, firstTrackTitle, firstTrackArtist, savedAt FROM queue_history ORDER BY savedAt DESC")
    fun getAllSummaries(): Flow<List<QueueHistorySummary>>

    @Query("SELECT * FROM queue_history WHERE id = :id")
    suspend fun getById(id: String): QueueHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: QueueHistoryEntity)

    @Query("DELETE FROM queue_history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM queue_history")
    suspend fun clearAll()

    @Query("SELECT id FROM queue_history ORDER BY savedAt DESC")
    suspend fun getAllIds(): List<String>

    @Query("DELETE FROM queue_history WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT COUNT(*) FROM queue_history")
    suspend fun getCount(): Int
}
