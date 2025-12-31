package com.alananasss.kittytune.data

import android.content.Context
import android.net.Uri
import com.alananasss.kittytune.data.local.*
import com.alananasss.kittytune.domain.Track
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Global backup model
data class FullBackupData(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val localTracks: List<LocalTrack>,
    val localPlaylists: List<LocalPlaylist>,
    val playlistRefs: List<PlaylistTrackCrossRef>,
    val savedArtists: List<LocalArtist>,
    val history: List<HistoryItem>,
    val likedTracks: List<Track>,
    val achievements: Map<String, Any?>,
    val playerPrefs: Map<String, Any?>
)

object BackupManager {
    private val gson = Gson()

    suspend fun createBackup(context: Context, uri: Uri) {
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context).downloadDao()
            val tracks = db.getAllTracks().first()
            val playlists = db.getAllPlaylists().first()
            val allRefs = mutableListOf<PlaylistTrackCrossRef>()
            playlists.forEach { playlist ->
                val tracksInPlaylist = db.getTracksForPlaylistSync(playlist.id)
                tracksInPlaylist.forEach { track ->
                    val ref = db.getRef(playlist.id, track.id)
                    if (ref != null) allRefs.add(ref)
                }
            }

            val artists = db.getAllSavedArtists().first()
            val history = db.getHistory().first()
            val likes = LikeRepository.likedTracks.value
            val achievementPrefs = context.getSharedPreferences("achievements_prefs", Context.MODE_PRIVATE).all
            val playerPrefsMap = context.getSharedPreferences("player_state", Context.MODE_PRIVATE).all

            val backupData = FullBackupData(
                localTracks = tracks,
                localPlaylists = playlists,
                playlistRefs = allRefs,
                savedArtists = artists,
                history = history,
                likedTracks = likes,
                achievements = achievementPrefs,
                playerPrefs = playerPrefsMap
            )

            val jsonString = gson.toJson(backupData)
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(jsonString.toByteArray())
            }
        }
    }

    suspend fun restoreBackup(context: Context, uri: Uri) {
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context).downloadDao()
            val contentResolver = context.contentResolver

            val jsonString = contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).readText()
            } ?: throw Exception("Impossible de lire le fichier")

            val data: FullBackupData = gson.fromJson(jsonString, FullBackupData::class.java)


            // Tracks
            data.localTracks.forEach { db.insertTrack(it) }

            // Playlists
            data.localPlaylists.forEach { db.insertPlaylist(it) }

            // Refs
            data.playlistRefs.forEach { db.insertPlaylistTrackRef(it) }

            // Artists
            data.savedArtists.forEach { db.insertArtist(it) }

            // History
            data.history.forEach { db.insertHistory(it) }

            LikeRepository.replaceAllLikes(data.likedTracks)

            // 4. Restauration Preferences
            restorePrefs(context, "achievements_prefs", data.achievements)
            restorePrefs(context, "player_state", data.playerPrefs)

            // 5. Re-initializing managers to take changes into account
            withContext(Dispatchers.Main) {
                AchievementManager.init(context)
            }
        }
    }

    private fun restorePrefs(context: Context, prefName: String, map: Map<String, Any?>) {
        val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.clear()

        map.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                is Int -> editor.putInt(key, value.toInt())
                is Double -> {
                    if (key.contains("time") || key.contains("date") || key.contains("position")) {
                        editor.putLong(key, value.toLong())
                    } else {
                        editor.putInt(key, value.toInt())
                    }
                }
                is String -> editor.putString(key, value)
                is Long -> editor.putLong(key, value)
            }
        }
        editor.commit()
    }

    fun getBackupFileName(): String {
        val date = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        return "SoundTune_Backup_$date.backup"
    }
}