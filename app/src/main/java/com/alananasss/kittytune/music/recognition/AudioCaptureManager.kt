package com.alananasss.kittytune.music.recognition

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AudioCaptureManager {
    private var onReadyCallback: ((MediaProjection) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    private var onCancelCallback: (() -> Unit)? = null

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    fun startCapture(
        context: Context,
        resultCode: Int,
        data: Intent,
        onReady: (MediaProjection) -> Unit,
        onError: ((String) -> Unit)? = null,
        onCancel: (() -> Unit)? = null
    ) {
        onReadyCallback = onReady
        onErrorCallback = onError
        onCancelCallback = onCancel
        _isCapturing.value = true
        AudioCaptureService.start(context, resultCode, data)
    }

    fun notifyProjectionReady(projection: MediaProjection) {
        onReadyCallback?.invoke(projection)
        onReadyCallback = null
    }

    fun notifyError(error: String) {
        _isCapturing.value = false
        onErrorCallback?.invoke(error)
        clearCallbacks()
    }

    fun notifyProjectionStopped() {
        _isCapturing.value = false
        onCancelCallback?.invoke()
        clearCallbacks()
    }

    fun stopCapture(context: Context) {
        _isCapturing.value = false
        clearCallbacks()
        AudioCaptureService.stop(context)
    }

    fun notifySuccess(context: Context, title: String, artist: String) {
        AudioCaptureService.showResultNotification(context, title, artist)
        stopCapture(context)
    }

    private fun clearCallbacks() {
        onReadyCallback = null
        onErrorCallback = null
        onCancelCallback = null
    }
}
