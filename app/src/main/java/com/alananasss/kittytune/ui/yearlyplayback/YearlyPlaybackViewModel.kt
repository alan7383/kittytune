package com.alananasss.kittytune.ui.yearlyplayback

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.yearlyplayback.ApiYearlyPlaybackAsset
import com.alananasss.kittytune.data.yearlyplayback.Block
import com.alananasss.kittytune.data.yearlyplayback.ErrorType
import com.alananasss.kittytune.data.yearlyplayback.ImageAssetStore
import com.alananasss.kittytune.data.yearlyplayback.RiveFileManager
import com.alananasss.kittytune.data.yearlyplayback.YearlyPlaybackAudioPlayer
import com.alananasss.kittytune.data.yearlyplayback.YearlyPlaybackEvent
import com.alananasss.kittytune.data.yearlyplayback.YearlyPlaybackRepository
import com.alananasss.kittytune.data.yearlyplayback.YearlyPlaybackState
import com.alananasss.kittytune.data.yearlyplayback.YearlyPlaybackTarget
import com.alananasss.kittytune.data.yearlyplayback.YearlyPlaybackVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class YearlyPlaybackViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = YearlyPlaybackRepository(application)
    val imageAssetStore = ImageAssetStore()
    private val riveFileManager = RiveFileManager(application, imageAssetStore)
    private val audioPlayer = YearlyPlaybackAudioPlayer(application, viewModelScope)

    private val _state = MutableStateFlow<YearlyPlaybackState>(YearlyPlaybackState.Prepare.InitData)
    val state: StateFlow<YearlyPlaybackState> = _state.asStateFlow()

    private val _riveFile = MutableStateFlow<app.rive.runtime.kotlin.core.File?>(null)
    val riveFile: StateFlow<app.rive.runtime.kotlin.core.File?> = _riveFile.asStateFlow()

    private val _blocks = MutableStateFlow<List<Block>>(emptyList())
    val blocks: StateFlow<List<Block>> = _blocks.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _events = MutableSharedFlow<YearlyPlaybackEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<YearlyPlaybackEvent> = _events.asSharedFlow()

    private var autoAdvanceJob: Job? = null
    private var loadDataJob: Job? = null
    private var pausedRemainingMs: Long = 0L
    private var slideStartTimeMs: Long = 0L

    var target: YearlyPlaybackTarget = YearlyPlaybackTarget.FAN
        private set
    var variant: YearlyPlaybackVariant = YearlyPlaybackVariant.CURRENT_YEAR
        private set

    var currentYear: Int = -1
        private set

    fun loadData(
        target: YearlyPlaybackTarget = YearlyPlaybackTarget.FAN,
        variant: YearlyPlaybackVariant = YearlyPlaybackVariant.CURRENT_YEAR,
        year: Int = 2025
    ) {
        this.target = target
        this.variant = variant
        this.currentYear = year

        loadDataJob?.cancel()
        cancelAutoAdvance()
        audioPlayer.stop()

        _riveFile.value = null
        _blocks.value = emptyList()
        _currentIndex.value = 0
        imageAssetStore.clear()

        loadDataJob = viewModelScope.launch {
            _state.value = YearlyPlaybackState.Prepare.InitData

            val result = repository.fetchData(target, variant, year)
            result.onSuccess { data ->
                val loadedBlocks = data.blocks
                if (loadedBlocks.isEmpty()) {
                    _state.value = YearlyPlaybackState.Empty
                    return@launch
                }

                _blocks.value = loadedBlocks
                _currentIndex.value = 0

                // If authentic Rive animation URL is provided, load the Rive file and images
                if (!data.riveAssetUrl.isNullOrEmpty()) {
                    // Pre-populate allowedNames BEFORE loading Rive file (matches SoundCloud decompiled parity!)
                    imageAssetStore.clear()
                    data.imageAssets.forEach { imageAssetStore.allowedNames.add(it.assetId) }
                    Log.d("YearlyPlaybackVM", "Allowed ImageAsset names pre-populated: ${imageAssetStore.allowedNames}")

                    _state.value = YearlyPlaybackState.Prepare.InitRive(data)
                    val loadedRive = riveFileManager.loadRiveFile(target, data.riveAssetUrl)
                    if (loadedRive != null) {
                        _riveFile.value = loadedRive
                        _state.value = YearlyPlaybackState.Prepare.InitDownloadImages(data, loadedRive)

                        // Download all images in parallel and inject into Rive textures before starting slides
                        downloadAndInjectImages(data.imageAssets)
                    } else {
                        _riveFile.value = null
                    }
                } else {
                    _riveFile.value = null
                }

                val firstBlock = loadedBlocks[0]
                _state.value = YearlyPlaybackState.Display(
                    block = firstBlock,
                    totalBlocks = loadedBlocks.size,
                    durationMs = firstBlock.durationMs
                )
                _events.tryEmit(YearlyPlaybackEvent.Start)
                audioPlayer.playTrackSnippet(firstBlock.backgroundTrack)
                startAutoAdvance(firstBlock.durationMs.toLong())

            }.onFailure { err ->
                Log.e("YearlyPlaybackVM", "Failed to fetch yearly playback", err)
                _state.value = YearlyPlaybackState.Error(ErrorType.NETWORK)
            }
        }
    }

    private suspend fun downloadAndInjectImages(assets: List<ApiYearlyPlaybackAsset>) = withContext(Dispatchers.IO) {
        if (assets.isEmpty()) return@withContext
        Log.d("YearlyPlaybackVM", "Downloading ${assets.size} authentic Wrapped image assets in parallel...")
        val imageLoader = ImageLoader(getApplication())
        val fallbackBytes by lazy { getFallbackPlaceholderBytes() }

        coroutineScope {
            assets.map { asset ->
                async {
                    runCatching {
                        val rawUrl = asset.url.replace("{size}", "t500x500")
                        val effectiveUrl = if (rawUrl.contains("al.sndcdn")) {
                            if (rawUrl.contains("?")) "$rawUrl&theme=dark" else "$rawUrl?theme=dark"
                        } else rawUrl

                        val req = ImageRequest.Builder(getApplication())
                            .data(effectiveUrl)
                            .allowHardware(false)
                            .bitmapConfig(Bitmap.Config.ARGB_8888)
                            .build()
                        val result = imageLoader.execute(req)
                        if (result is SuccessResult) {
                            val bitmap = result.drawable.toBitmap()
                            val stream = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                            val bytes = stream.toByteArray()
                            imageAssetStore.storeImage(asset.assetId, bytes)
                            Log.d("YearlyPlaybackVM", "Loaded & injected image '${asset.assetId}' (${bytes.size} bytes)")
                        } else {
                            Log.w("YearlyPlaybackVM", "Coil load failed for '${asset.assetId}', injecting placeholder")
                            imageAssetStore.storeImage(asset.assetId, fallbackBytes)
                        }
                    }.onFailure { err ->
                        Log.w("YearlyPlaybackVM", "Exception downloading image '${asset.assetId}', injecting placeholder", err)
                        imageAssetStore.storeImage(asset.assetId, fallbackBytes)
                    }
                }
            }.awaitAll()
        }
        Log.d("YearlyPlaybackVM", "All ${assets.size} image assets processed and bound!")
    }

    private fun getFallbackPlaceholderBytes(): ByteArray {
        return runCatching {
            val drawable = ContextCompat.getDrawable(
                getApplication(),
                R.drawable.ic_default_user_artwork_placeholder_dark
            ) ?: ColorDrawable(android.graphics.Color.DKGRAY)
            val bitmap = drawable.toBitmap(width = 368, height = 368, config = Bitmap.Config.ARGB_8888)
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            stream.toByteArray()
        }.getOrElse {
            val bitmap = Bitmap.createBitmap(368, 368, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.DKGRAY)
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            stream.toByteArray()
        }
    }

    fun nextBlock() {
        val currentList = _blocks.value
        if (currentList.isEmpty()) return

        val cur = _currentIndex.value
        if (cur < currentList.size - 1) {
            val next = cur + 1
            _currentIndex.value = next
            val block = currentList[next]
            _state.value = YearlyPlaybackState.Display(
                block = block,
                totalBlocks = currentList.size,
                durationMs = block.durationMs
            )
            _events.tryEmit(YearlyPlaybackEvent.Next(cur, next))
            audioPlayer.playTrackSnippet(block.backgroundTrack)
            startAutoAdvance(block.durationMs.toLong())
        } else {
            // Reached final slide, keep on final slide without advancing
            cancelAutoAdvance()
        }
    }

    fun previousBlock() {
        val currentList = _blocks.value
        if (currentList.isEmpty()) return

        val cur = _currentIndex.value
        if (cur > 0) {
            val prev = cur - 1
            _currentIndex.value = prev
            val block = currentList[prev]
            _state.value = YearlyPlaybackState.Display(
                block = block,
                totalBlocks = currentList.size,
                durationMs = block.durationMs
            )
            _events.tryEmit(YearlyPlaybackEvent.Previous(cur, prev))
            audioPlayer.playTrackSnippet(block.backgroundTrack)
            startAutoAdvance(block.durationMs.toLong())
        } else {
            // Already on first slide, restart its timer
            val block = currentList[0]
            audioPlayer.playTrackSnippet(block.backgroundTrack)
            startAutoAdvance(block.durationMs.toLong())
        }
    }

    fun pausePlayback() {
        if (_isPaused.value) return
        _isPaused.value = true
        val elapsed = System.currentTimeMillis() - slideStartTimeMs
        val curBlock = _blocks.value.getOrNull(_currentIndex.value)
        val total = (curBlock?.durationMs ?: 5000).toLong()
        pausedRemainingMs = (total - elapsed).coerceAtLeast(500L)
        cancelAutoAdvance()
        audioPlayer.pause()
        _events.tryEmit(YearlyPlaybackEvent.Pause(_currentIndex.value))
    }

    fun resumePlayback() {
        if (!_isPaused.value) return
        _isPaused.value = false
        val remain = if (pausedRemainingMs > 100L) pausedRemainingMs else 2000L
        _events.tryEmit(YearlyPlaybackEvent.Resume(_currentIndex.value))
        audioPlayer.resume()
        startAutoAdvance(remain)
    }

    fun toggleSavePlaylist(savePlaylist: Block.SavePlaylist) {
        viewModelScope.launch {
            val updatedLike = !savePlaylist.isLiked
            val currentList = _blocks.value.toMutableList()
            val index = currentList.indexOfFirst { it.artboardId == savePlaylist.artboardId }
            if (index >= 0) {
                currentList[index] = savePlaylist.copy(
                    isLiked = updatedLike
                )
                _blocks.value = currentList

                val cur = _currentIndex.value
                if (cur == index) {
                    _state.value = YearlyPlaybackState.Display(
                        block = currentList[index],
                        totalBlocks = currentList.size,
                        durationMs = currentList[index].durationMs
                    )
                }
            }

            // Extract the real numeric ID from the URN (e.g. "soundcloud:playlists:1545548310" -> 1545548310L)
            // Using hashCode() as a fake ID caused 404s in the library when it tried to fetch
            // the playlist from /playlists/{hashId}. System playlists use a hash of their URN.
            val plId = extractPlaylistId(savePlaylist.playlistUrn)
            com.alananasss.kittytune.data.LikeRepository.togglePlaylistLike(plId, updatedLike, urn = savePlaylist.playlistUrn)
            _events.tryEmit(YearlyPlaybackEvent.LikePlaylist(savePlaylist.playlistUrn, updatedLike))
        }
    }

    fun onShareClicked() {
        val cur = _currentIndex.value
        val block = _blocks.value.getOrNull(cur) ?: return
        _events.tryEmit(YearlyPlaybackEvent.Share(cur, block.artboardId))
    }

    fun shareCurrentSlide(context: Context) {
        val cur = _currentIndex.value
        val block = _blocks.value.getOrNull(cur) ?: return
        _events.tryEmit(YearlyPlaybackEvent.Share(cur, block.artboardId))

        val shareText = buildString {
            append("🎶 My SoundCloud Wrapped on KittyTune!\n")
            when (block) {
                is Block.ArtBoard -> {
                    block.textRuns.forEach { run ->
                        if (run.key in listOf("headline", "minutes", "plays", "rank", "title", "artist", "trackName1", "artistName1")) {
                            append("${run.value}\n")
                        }
                    }
                }
                is Block.SavePlaylist -> append("Top Tracks of the Year: ${block.text}\n")
                is Block.OpenInsights -> append("${block.title}\n${block.text}\n")
            }
            append("\nRelive your musical year with KittyTune & SoundCloud!")
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(sendIntent, "Share Your Playback")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun startAutoAdvance(durationMs: Long) {
        cancelAutoAdvance()
        slideStartTimeMs = System.currentTimeMillis()
        autoAdvanceJob = viewModelScope.launch {
            delay(durationMs)
            if (isActive) {
                nextBlock()
            }
        }
    }

    private fun cancelAutoAdvance() {
        autoAdvanceJob?.cancel()
        autoAdvanceJob = null
    }

    fun stop() {
        loadDataJob?.cancel()
        cancelAutoAdvance()
        audioPlayer.stop()
    }

    override fun onCleared() {
        super.onCleared()
        cancelAutoAdvance()
        audioPlayer.release()
    }

    companion object {
        /**
         * Extracts the real numeric playlist ID from a SoundCloud URN.
         *
         * Examples:
         *   "soundcloud:playlists:1545548310"        -> 1545548310L  (regular playlist)
         *   "soundcloud:system-playlists:abc:12345"  -> hash-based negative ID (system playlist)
         *
         * Using hashCode() as a fake ID was causing 404s in the library because the SoundCloud
         * API endpoint /playlists/{id} only accepts the real numeric ID, not an arbitrary hash.
         */
        fun extractPlaylistId(urn: String): Long {
            // Regular playlist URN: "soundcloud:playlists:1234567"
            val simpleMatch = Regex("soundcloud:playlists:(\\d+)").find(urn)
            if (simpleMatch != null) {
                return simpleMatch.groupValues[1].toLongOrNull() ?: fallbackId(urn)
            }
            // System playlist or anything else: use a stable hash-based negative ID so it never
            // collides with a real SoundCloud playlist ID (which are always positive).
            return fallbackId(urn)
        }

        private fun fallbackId(urn: String): Long {
            return kotlin.math.abs(urn.hashCode().toLong())
        }
    }
}
