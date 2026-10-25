package com.alananasss.kittytune.ui.musicimport

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.DownloadManager
import com.alananasss.kittytune.data.LikeRepository
import com.alananasss.kittytune.data.musicimport.PortableImportFile
import com.alananasss.kittytune.data.musicimport.PortablePlaylistParser
import com.alananasss.kittytune.data.musicimport.PortableResolvedEntry
import com.alananasss.kittytune.data.musicimport.PortableTrackResolver
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.math.abs

class PortableImportViewModel(application: Application) : AndroidViewModel(application) {

    var fileName by mutableStateOf<String?>(null)
        private set
    var playlistName by mutableStateOf("")
    var entries by mutableStateOf<List<PortableResolvedEntry>>(emptyList())
        private set
    var selectedIndices by mutableStateOf<Set<Int>>(emptySet())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isResolving by mutableStateOf(false)
        private set
    var isImporting by mutableStateOf(false)
        private set
    var importToLikes by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var importedPlaylistId by mutableStateOf<Long?>(null)
        private set
    var importedCount by mutableStateOf(0)
        private set
    var skippedCount by mutableStateOf(0)
        private set
    var importFinished by mutableStateOf(false)
        private set

    private var resolveJob: kotlinx.coroutines.Job? = null

    val matchedCount: Int
        get() = entries.count { it.isMatched }

    val selectedTrackCount: Int
        get() = selectedIndices.sumOf { index ->
            entries.getOrNull(index)?.tracks?.size ?: 0
        }

    fun load(uri: Uri) {
        resolveJob?.cancel()
        viewModelScope.launch {
            isLoading = true
            error = null
            importFinished = false
            importedPlaylistId = null
            entries = emptyList()
            selectedIndices = emptySet()
            try {
                val name = displayName(uri) ?: "Import"
                val text = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver
                        .openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        ?: throw IllegalStateException("Could not read the selected file")
                }
                if (text.length > MAX_FILE_CHARS) {
                    throw IllegalStateException(getApplication<Application>().getString(R.string.portable_import_error_too_large))
                }
                val parsed = PortablePlaylistParser.parse(name, text)
                applyParsed(parsed)
                if (parsed.entries.isNotEmpty()) resolveAll()
            } catch (e: Exception) {
                error = e.message ?: getApplication<Application>().getString(R.string.portable_import_error_unreadable)
            } finally {
                isLoading = false
            }
        }
    }

    private fun applyParsed(parsed: PortableImportFile) {
        fileName = parsed.name
        playlistName = parsed.name
        entries = parsed.entries.map { PortableResolvedEntry(entry = it) }
        selectedIndices = emptySet()
    }

    /**
     * Resolves the rows in parallel (four at a time). Each row is independent,
     * so a provider hiccup on one line does not stop the rest of the file.
     */
    private fun resolveAll() {
        if (entries.isEmpty()) return
        resolveJob?.cancel()
        resolveJob = viewModelScope.launch {
            isResolving = true
            val resolver = PortableTrackResolver(getApplication())
            val snapshot = entries
            val semaphore = Semaphore(4)
            coroutineScope {
                snapshot.forEachIndexed { index, unresolved ->
                    launch {
                        semaphore.withPermit {
                            val resolved = withContext(Dispatchers.IO) {
                                resolver.resolve(unresolved.entry)
                            }
                            entries = entries.toMutableList().also { list ->
                                if (index in list.indices) list[index] = resolved
                            }
                        }
                    }
                }
            }
            isResolving = false
            selectedIndices = entries.indices.filter { entries[it].isMatched }.toSet()
        }
    }

    fun toggleSelected(index: Int) {
        val entry = entries.getOrNull(index) ?: return
        if (!entry.isMatched) return
        selectedIndices = if (index in selectedIndices) {
            selectedIndices - index
        } else {
            selectedIndices + index
        }
    }

    fun selectAll() {
        selectedIndices = entries.indices.filter { entries[it].isMatched }.toSet()
    }

    fun selectNone() {
        selectedIndices = emptySet()
    }

    fun updateImportToLikes(value: Boolean) {
        importToLikes = value
    }

    fun importSelected(onFinished: () -> Unit) {
        if (isImporting) return
        val chosen = selectedIndices
            .sorted()
            .mapNotNull { entries.getOrNull(it) }
            .filter { it.isMatched }
        val tracks = chosen.flatMap { it.tracks }.distinctBy { it.id }
        if (tracks.isEmpty()) {
            error = getApplication<Application>().getString(R.string.portable_import_error_empty_selection)
            return
        }
        val name = playlistName.trim().ifBlank { fileName ?: "Imported playlist" }
        isImporting = true
        error = null
        viewModelScope.launch {
            try {
                val playlist = Playlist(
                    id = -abs(System.currentTimeMillis()),
                    title = name,
                    artworkUrl = tracks.firstOrNull()?.artworkUrl,
                    calculatedArtworkUrl = tracks.firstOrNull()?.artworkUrl,
                    trackCount = tracks.size,
                    user = User(0L, "You", null),
                    tracks = tracks,
                )
                DownloadManager.importPlaylistToLibrarySync(
                    playlist = playlist,
                    tracks = tracks,
                    isUserCreated = true,
                )
                if (importToLikes) {
                    LikeRepository.addLikesBulk(tracks)
                }
                importedPlaylistId = playlist.id
                importedCount = tracks.size
                skippedCount = entries.count { !it.isMatched }
                importFinished = true
                onFinished()
            } catch (e: Exception) {
                error = e.message ?: getApplication<Application>().getString(R.string.portable_import_error_import)
            } finally {
                isImporting = false
            }
        }
    }

    fun dismissFinished() {
        importFinished = false
    }

    private fun displayName(uri: Uri): String? {
        val resolver = getApplication<Application>().contentResolver
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) cursor.getString(index) else null
                    } else null
                }
        }.getOrNull()?.let { return it }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private companion object {
        /** Roughly 8 MB of UTF-8 text; keeps a stray video file out of memory. */
        const val MAX_FILE_CHARS = 8_000_000
    }
}
