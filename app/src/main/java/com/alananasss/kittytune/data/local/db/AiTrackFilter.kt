package com.alananasss.kittytune.data.local.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.TypeConverter
import kotlinx.coroutines.flow.Flow

enum class AiTrackDecision {
    BLOCKED,
    ALLOWED,
    PENDING
}

@Entity(tableName = "ai_track_filters")
data class AiTrackFilterEntity(
    @PrimaryKey val trackId: String,
    val title: String,
    val artist: String,
    val decision: AiTrackDecision,
    val detectionScore: Int,
    val detectionReason: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface AiTrackFilterDao {
    @Query("SELECT * FROM ai_track_filters WHERE trackId = :trackId")
    fun getFilterForTrack(trackId: String): Flow<AiTrackFilterEntity?>

    @Query("SELECT decision FROM ai_track_filters WHERE trackId = :trackId")
    suspend fun getDecisionSync(trackId: String): AiTrackDecision?

    @Query("SELECT COUNT(*) > 0 FROM ai_track_filters WHERE trackId = :trackId AND decision = 'BLOCKED'")
    suspend fun isTrackBlocked(trackId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDecision(filter: AiTrackFilterEntity)

    @Query("SELECT * FROM ai_track_filters WHERE decision = 'BLOCKED' ORDER BY timestamp DESC")
    fun getAllBlockedTracks(): Flow<List<AiTrackFilterEntity>>

    @Query("DELETE FROM ai_track_filters WHERE trackId = :trackId")
    suspend fun deleteFilter(trackId: String)
}

class AiTrackFilterConverters {
    @TypeConverter
    fun fromDecision(decision: AiTrackDecision?): String? = decision?.name

    @TypeConverter
    fun toDecision(value: String?): AiTrackDecision? = when (value) {
        "BLOCKED" -> AiTrackDecision.BLOCKED
        "ALLOWED" -> AiTrackDecision.ALLOWED
        "PENDING" -> AiTrackDecision.PENDING
        else -> null
    }
}
