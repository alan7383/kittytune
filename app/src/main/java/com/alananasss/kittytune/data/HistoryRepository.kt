package com.alananasss.kittytune.data

import android.content.Context
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.HistoryItem
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object HistoryRepository {
    private lateinit var database: AppDatabase
    private val scope = CoroutineScope(Dispatchers.IO)

    fun init(context: Context) {
        database = AppDatabase.getDatabase(context)
    }

    fun addToHistory(track: Track) {
        scope.launch {
            val item = HistoryItem(
                id = "track:${track.id}",
                numericId = track.id,
                title = track.title ?: "Sans titre",
                subtitle = track.user?.username ?: "Inconnu",
                imageUrl = track.fullResArtwork,
                type = "TRACK"
            )
            database.downloadDao().insertHistory(item)
        }
    }

    fun addToHistory(playlist: Playlist, isStation: Boolean = false, isProfile: Boolean = false) {
        scope.launch {
            val (stringId, type) = when {
                isProfile -> "profile:${playlist.id}" to "PROFILE"
                isStation -> "station:${playlist.id}" to "STATION"
                playlist.id == -1L -> "likes" to "PLAYLIST"
                playlist.id == -2L -> "downloads" to "PLAYLIST"
                // Pour les playlists locales (ID < 0), on utilise le préfixe standard ou spécifique selon besoin.
                // Ici on garde "playlist:" pour matcher la logique de navigation
                playlist.id < 0 -> "playlist:${playlist.id}" to "PLAYLIST"
                else -> "playlist:${playlist.id}" to "PLAYLIST"
            }

            val finalSubtitle = when {
                isProfile -> "Artiste"
                isStation -> playlist.user?.username ?: "Station"
                playlist.id < 0 -> "Playlist Locale"
                else -> playlist.user?.username ?: "SoundCloud"
            }

            val item = HistoryItem(
                id = stringId,
                numericId = playlist.id,
                title = playlist.title ?: "Playlist",
                subtitle = finalSubtitle,
                imageUrl = playlist.fullResArtwork,
                type = type
            )
            database.downloadDao().insertHistory(item)
        }
    }

    // NOUVEAU : Supprimer un élément de l'historique
    fun removeFromHistory(playlistId: Long) {
        scope.launch {
            // On tente de supprimer les variantes possibles
            database.downloadDao().deleteHistoryItem("playlist:$playlistId")
            database.downloadDao().deleteHistoryItem("station:$playlistId")
            // Note: pour les playlists locales (ex: -123), l'ID est "playlist:-123"
        }
    }

    fun getHistory() = database.downloadDao().getHistory()
}