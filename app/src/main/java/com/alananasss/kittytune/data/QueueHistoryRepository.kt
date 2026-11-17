package com.alananasss.kittytune.data

import android.content.Context
import com.alananasss.kittytune.KittyTuneApp
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.QueueHistoryEntity
import com.alananasss.kittytune.data.local.QueueHistorySummary
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.player.PlaybackContext
import com.alananasss.kittytune.utils.AppUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

object QueueHistoryRepository {
    private var database: AppDatabase? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = AppUtils.gson
    private const val MAX_HISTORY_ITEMS = 30

    @Volatile
    private var lastSavedTrackIds: List<Long> = emptyList()

    fun init(context: Context) {
        if (database == null) {
            database = AppDatabase.getDatabase(context.applicationContext)
        }
    }

    private fun getDb(): AppDatabase {
        return database ?: synchronized(this) {
            database ?: AppDatabase.getDatabase(KittyTuneApp.instance).also { database = it }
        }
    }

    fun getSummaries(): Flow<List<QueueHistorySummary>> {
        return getDb().queueHistoryDao().getAllSummaries()
    }

    suspend fun getQueueById(id: String): QueueHistoryEntity? {
        return withContext(Dispatchers.IO) {
            getDb().queueHistoryDao().getById(id)
        }
    }

    /**
     * Preserves a queue snapshot into the history database.
     * Returns the generated history ID, or null if the queue was empty or identical to the last saved one.
     */
    fun saveQueue(
        queue: List<Track>,
        currentIndex: Int,
        currentPositionMs: Long,
        context: PlaybackContext?,
        customTitle: String? = null,
        onSaved: ((String) -> Unit)? = null
    ): String? {
        if (queue.isEmpty()) return null
        val currentIds = queue.map { it.id }
        if (currentIds == lastSavedTrackIds && customTitle == null) {
            return null
        }

        val id = UUID.randomUUID().toString()
        lastSavedTrackIds = currentIds

        val safeIndex = currentIndex.coerceIn(0, queue.lastIndex)
        val currentTrack = queue.getOrNull(safeIndex) ?: queue.firstOrNull()

        val title = customTitle?.takeIf { it.isNotBlank() }
            ?: context?.displayText?.takeIf { it.isNotBlank() }
            ?: currentTrack?.let {
                if (queue.size > 1) "${it.title} & ${queue.size - 1} more" else it.title
            }
            ?: "Queue (${queue.size} tracks)"

        val entity = QueueHistoryEntity(
            id = id,
            title = title,
            trackCount = queue.size,
            tracksJson = gson.toJson(queue),
            currentIndex = safeIndex,
            currentPositionMs = currentPositionMs.coerceAtLeast(0L),
            contextJson = context?.let { gson.toJson(it) },
            firstTrackArtworkUrl = currentTrack?.artworkUrl?.takeIf { it.isNotBlank() } ?: queue.firstOrNull()?.artworkUrl,
            firstTrackTitle = currentTrack?.title,
            firstTrackArtist = currentTrack?.displayArtist,
            savedAt = System.currentTimeMillis()
        )

        scope.launch {
            try {
                val db = getDb()
                db.queueHistoryDao().insert(entity)

                val ids = db.queueHistoryDao().getAllIds()
                if (ids.size > MAX_HISTORY_ITEMS) {
                    val toDelete = ids.drop(MAX_HISTORY_ITEMS)
                    db.queueHistoryDao().deleteByIds(toDelete)
                }

                withContext(Dispatchers.Main) {
                    onSaved?.invoke(id)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return id
    }

    fun deleteQueue(id: String) {
        scope.launch {
            runCatching { getDb().queueHistoryDao().deleteById(id) }
        }
    }

    fun clearAll() {
        scope.launch {
            runCatching {
                lastSavedTrackIds = emptyList()
                getDb().queueHistoryDao().clearAll()
            }
        }
    }

    fun deserializeTracks(tracksJson: String): List<Track> {
        return try {
            val arr = gson.fromJson<Array<Track>>(tracksJson, Array<Track>::class.java)
            arr?.filterNotNull() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun deserializeContext(contextJson: String?): PlaybackContext? {
        if (contextJson.isNullOrBlank()) return null
        return try {
            gson.fromJson(contextJson, PlaybackContext::class.java)
        } catch (_: Exception) {
            null
        }
    }
}
