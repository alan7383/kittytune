package com.alananasss.kittytune.data

import android.content.Context
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.RecognitionHistoryItem
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

object RecognitionHistoryRepository {
    private lateinit var database: AppDatabase
    private val scope = CoroutineScope(Dispatchers.IO)

    fun init(context: Context) {
        database = AppDatabase.getDatabase(context)
    }

    fun addToHistory(trackId: Long?, title: String, artist: String, artworkUrl: String?) {
        scope.launch {
            val item = RecognitionHistoryItem(
                trackId = trackId,
                title = title,
                artist = artist,
                artworkUrl = artworkUrl
            )
            database.recognitionHistoryDao().insertItem(item)
        }
    }

    fun getHistory(): Flow<List<RecognitionHistoryItem>> {
        return database.recognitionHistoryDao().getAllItems()
    }

    fun clearHistory() {
        scope.launch {
            database.recognitionHistoryDao().clearHistory()
        }
    }

    fun deleteItem(itemId: Long) {
        scope.launch {
            database.recognitionHistoryDao().deleteItem(itemId)
        }
    }
}
