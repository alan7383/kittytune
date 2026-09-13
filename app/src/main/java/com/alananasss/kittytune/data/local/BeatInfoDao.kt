package com.alananasss.kittytune.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BeatInfoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(beatInfo: BeatInfoEntity)

    @Query("SELECT * FROM beat_info WHERE songId = :songId LIMIT 1")
    suspend fun getBeatInfo(songId: String): BeatInfoEntity?

    @Query("DELETE FROM beat_info WHERE songId = :songId")
    suspend fun deleteBeatInfo(songId: String)

    @Query("DELETE FROM beat_info")
    suspend fun clearAll()

    @Query("DELETE FROM beat_info WHERE bpm <= 0")
    suspend fun clearFailedBeatInfo()
}
