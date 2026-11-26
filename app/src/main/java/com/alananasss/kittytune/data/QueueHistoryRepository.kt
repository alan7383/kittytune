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
import kotlinx.coroutines.withContext
import java.util.UUID

object QueueHistoryRepository {
    private var database: AppDatabase? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = AppUtils.gson
    private val snapshotCodec = QueueSnapshotCodec(gson)
    private val writes = QueueHistoryWrites(scope) { it.printStackTrace() }
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
    @Synchronized
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

        // Freeze membership now: the caller may edit/clear its queue before the IO worker runs.
        // Only references are copied here; full graph traversal/JSON encoding belongs off Main.
        val snapshot = queue.toList()
        val artwork = currentTrack?.artworkUrl?.takeIf { it.isNotBlank() } ?: snapshot.firstOrNull()?.artworkUrl
        val savedAt = System.currentTimeMillis()

        // Serialize ALL writes, including clear/delete, in invocation order. A deferred save
        // must never insert its old snapshot after a later Clear All has completed.
        writes.submit {
            val entity = QueueHistoryEntity(
                id = id,
                title = title,
                trackCount = snapshot.size,
                tracksJson = snapshotCodec.encode(snapshot),
                currentIndex = safeIndex,
                currentPositionMs = currentPositionMs.coerceAtLeast(0L),
                contextJson = context?.let { gson.toJson(it) },
                firstTrackArtworkUrl = artwork,
                firstTrackTitle = currentTrack?.title,
                firstTrackArtist = currentTrack?.displayArtist,
                savedAt = savedAt,
            )
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
        }

        return id
    }

    @Synchronized
    fun deleteQueue(id: String) {
        writes.submit {
            getDb().queueHistoryDao().deleteById(id)
        }
    }

    @Synchronized
    fun clearAll() {
        lastSavedTrackIds = emptyList()
        writes.submit {
            getDb().queueHistoryDao().clearAll()
        }
    }

    fun deserializeTracks(tracksJson: String): List<Track> {
        return try {
            val arr = snapshotCodec.decode<Array<Track>>(tracksJson, Array<Track>::class.java)
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
