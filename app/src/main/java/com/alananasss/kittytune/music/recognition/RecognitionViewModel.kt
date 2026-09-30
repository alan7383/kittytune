package com.alananasss.kittytune.music.recognition

import android.content.Context
import android.media.projection.MediaProjection
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.data.RecognitionHistoryRepository
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.domain.Track
import com.metrolist.shazamkit.Shazam
import com.metrolist.shazamkit.models.RecognitionResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.util.Log

enum class RecognitionAudioSource {
    MIC,
    DEVICE
}

/**
 * State machine matching Google Pixel Now Playing architecture:
 * - Idle: listening button is circular at rest
 * - Searching: listening button squashes, blooms into scalloped shape and pulses its halo continuously
 * - Success: displays identified song details
 * - Error: displays "Couldn't identify song" with retry button
 * Note: There is NO separate "Processing" / "Analyzing" screen, matching the official app.
 */
sealed class RecognitionState {
    object Idle : RecognitionState()
    object Searching : RecognitionState()
    data class Success(val result: RecognitionResult, val soundcloudTrack: Track?) : RecognitionState()
    data class Error(val message: String) : RecognitionState()
}

class RecognitionViewModel(private val context: Context) : ViewModel() {
    private val prefs = PlayerPreferences(context)

    private val _audioSource = MutableStateFlow(
        if (prefs.getRecognitionAudioSource() == "DEVICE") RecognitionAudioSource.DEVICE else RecognitionAudioSource.MIC
    )
    val audioSource: StateFlow<RecognitionAudioSource> = _audioSource.asStateFlow()

    private val _state = MutableStateFlow<RecognitionState>(RecognitionState.Idle)
    val state: StateFlow<RecognitionState> = _state.asStateFlow()

    private val audioRecorder = AudioRecorder()
    private val api by lazy { RetrofitClient.create(context) }
    private var activeControl: RecordControl? = null
    private var recognitionJob: Job? = null

    fun setAudioSource(source: RecognitionAudioSource) {
        if (_state.value is RecognitionState.Searching) {
            return
        }
        _audioSource.value = source
        prefs.setRecognitionAudioSource(source.name)
    }

    private suspend fun checkRecognition(pcmData: ByteArray, durationMs: Long): RecognitionState.Success? {
        if (pcmData.size < 1000) return null
        val signature = ShazamSignatureGenerator.fromI16(pcmData)
        val recognitionResult = Shazam.recognize(signature, durationMs)
        var successResult: RecognitionState.Success? = null
        recognitionResult.onSuccess { shazamResult ->
            val searchQuery = "${shazamResult.title} ${shazamResult.artist}"
            val track = try {
                val searchResponse = api.searchTracks(query = searchQuery, limit = 5)
                searchResponse.collection.firstOrNull()
            } catch (e: Exception) {
                Log.e("RecognitionViewModel", "Failed to search track on SoundCloud", e)
                null
            }
            successResult = RecognitionState.Success(shazamResult, track)
        }.onFailure { error ->
            Log.d("RecognitionViewModel", "Step check failed: ${error.message}")
        }
        return successResult
    }

    private fun updateToSuccess(successState: RecognitionState.Success, isDeviceCapture: Boolean = false) {
        if (_state.value is RecognitionState.Success) return
        _state.value = successState

        val shazamResult = successState.result
        val soundcloudTrack = successState.soundcloudTrack
        val imageUrl = soundcloudTrack?.fullResArtwork ?: shazamResult.coverArtHqUrl ?: shazamResult.coverArtUrl
        val title = soundcloudTrack?.title ?: shazamResult.title
        val artist = soundcloudTrack?.user?.username ?: shazamResult.artist

        RecognitionHistoryRepository.addToHistory(
            trackId = soundcloudTrack?.id,
            title = title,
            artist = artist,
            artworkUrl = imageUrl
        )

        if (isDeviceCapture) {
            AudioCaptureManager.notifySuccess(context, title, artist)
        }
    }

    fun startRecognition(mediaProjection: MediaProjection? = null) {
        if (_state.value is RecognitionState.Searching) {
            return
        }

        recognitionJob?.cancel()
        recognitionJob = viewModelScope.launch {
            val isDeviceCapture = mediaProjection != null
            try {
                // Official Pixel Now Playing transitions straight to the searching state (gnh)
                _state.value = RecognitionState.Searching

                val control = RecordControl()
                activeControl = control
                var intermediateJob: Job? = null
                var earlySuccess: RecognitionState.Success? = null

                // 5.5s is the standard length of a music recognition audio sample (matching Now Playing)
                val totalDurationMs = 5500L
                val pcmData = audioRecorder.recordAudio(
                    durationMs = totalDurationMs,
                    mediaProjection = mediaProjection,
                    control = control,
                    onProgress = { currentPcm ->
                        // Try an early check around 3.2s of audio (approx 100k bytes at 32k bytes/sec)
                        if (!control.shouldStop && intermediateJob == null && currentPcm.size >= 100_000) {
                            intermediateJob = launch {
                                val chunkDurationMs = (currentPcm.size.toLong() / 32)
                                val result = checkRecognition(currentPcm, chunkDurationMs)
                                if (result != null && !control.shouldStop) {
                                    earlySuccess = result
                                    control.shouldStop = true
                                    updateToSuccess(result, isDeviceCapture)
                                }
                            }
                        }
                    }
                )

                // If early check was successful, we're done
                if (earlySuccess != null || _state.value is RecognitionState.Success) {
                    return@launch
                }

                // Audio capture finished; state remains Searching while we analyze and query
                intermediateJob?.cancel()

                if (pcmData.size < 32000) { // Less than 1s of audio recorded
                    _state.value = RecognitionState.Error(context.getString(com.alananasss.kittytune.R.string.recognition_track_not_found))
                    return@launch
                }

                val durationOfChunk = (pcmData.size.toLong() / 32)
                val result = checkRecognition(pcmData, durationOfChunk)
                if (result != null) {
                    updateToSuccess(result, isDeviceCapture)
                } else {
                    if (_state.value !is RecognitionState.Success) {
                        _state.value = RecognitionState.Error(context.getString(com.alananasss.kittytune.R.string.recognition_track_not_found))
                    }
                }

            } catch (e: kotlinx.coroutines.CancellationException) {
                // Recognition canceled
                throw e
            } catch (e: Exception) {
                Log.e("RecognitionViewModel", "Error during audio recognition", e)
                if (_state.value !is RecognitionState.Success) {
                    _state.value = RecognitionState.Error(context.getString(com.alananasss.kittytune.R.string.recognition_track_not_found))
                }
            } finally {
                activeControl = null
                if (isDeviceCapture) {
                    AudioCaptureManager.stopCapture(context)
                }
            }
        }
    }

    fun cancelRecognition() {
        activeControl?.shouldStop = true
        activeControl = null
        recognitionJob?.cancel()
        recognitionJob = null
        AudioCaptureManager.stopCapture(context)
        _state.value = RecognitionState.Idle
    }

    fun reset() {
        cancelRecognition()
    }
}
