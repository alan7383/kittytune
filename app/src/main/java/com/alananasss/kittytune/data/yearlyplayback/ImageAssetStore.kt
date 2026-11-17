package com.alananasss.kittytune.data.yearlyplayback

import android.os.Handler
import android.os.Looper
import android.util.Log
import app.rive.runtime.kotlin.core.FileAsset
import app.rive.runtime.kotlin.core.FileAssetLoader
import app.rive.runtime.kotlin.core.ImageAsset
import app.rive.runtime.kotlin.core.RiveRenderImage
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory store holding references to Rive ImageAssets discovered during file parsing,
 * and caching downloaded artwork byte arrays for injection into Rive artboards.
 * Parity with SoundCloud decompiled:
 * com.soundcloud.android.yearlyplayback.ImageAssetStore
 */
class ImageAssetStore {
    val allowedNames: MutableSet<String> = ConcurrentHashMap.newKeySet<String>()
    val assets = ConcurrentHashMap<String, ImageAsset>()
    private val pendingImages = ConcurrentHashMap<String, ByteArray>()

    fun storeImage(name: String, bytes: ByteArray) {
        pendingImages[name] = bytes
        val asset = assets[name]
        if (asset != null) {
            runOnMainThread {
                runCatching {
                    val renderImage = RiveRenderImage.fromEncoded(bytes)
                    asset.image = renderImage
                    Log.d(TAG, "YearlyPlayback: Injected live image into Rive asset: '$name' (${bytes.size} bytes)")
                }.onFailure { err ->
                    Log.w(TAG, "YearlyPlayback: Failed to set RiveRenderImage for '$name'", err)
                }
            }
        } else {
            Log.d(TAG, "YearlyPlayback: Asset '$name' not yet registered in Rive, stored in pending (${bytes.size} bytes)")
        }
    }

    fun onAssetRegistered(name: String, asset: ImageAsset) {
        assets[name] = asset
        Log.d(TAG, "YearlyPlayback: Registered Rive ImageAsset '$name'")
        val bytes = pendingImages[name]
        if (bytes != null) {
            runOnMainThread {
                runCatching {
                    val renderImage = RiveRenderImage.fromEncoded(bytes)
                    asset.image = renderImage
                    Log.d(TAG, "YearlyPlayback: Injected pending image into Rive asset: '$name' (${bytes.size} bytes)")
                }.onFailure { err ->
                    Log.w(TAG, "YearlyPlayback: Failed to set pending RiveRenderImage for '$name'", err)
                }
            }
        }
    }

    fun clear() {
        allowedNames.clear()
        assets.clear()
        pendingImages.clear()
    }

    private fun runOnMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            Handler(Looper.getMainLooper()).post(block)
        }
    }

    companion object {
        private const val TAG = "ImageAssetStore"
    }
}

/**
 * Intercepts Rive file parsing to hook ImageAssets matching our yearly playback assets.
 * Parity with SoundCloud decompiled:
 * com.soundcloud.android.yearlyplayback.DefaultFileAssetLoader
 */
class DefaultFileAssetLoader(
    private val imageAssetStore: ImageAssetStore
) : FileAssetLoader() {

    override fun loadContents(asset: FileAsset, inBandBytes: ByteArray): Boolean {
        if (asset is ImageAsset) {
            val name = asset.name
            Log.d(TAG, "YearlyPlayback: attempt image addition '$name'")
            if (imageAssetStore.allowedNames.isEmpty() || imageAssetStore.allowedNames.contains(name)) {
                Log.d(TAG, "YearlyPlayback: image added '$name'")
                imageAssetStore.onAssetRegistered(name, asset)
                return true
            }
        }
        return false
    }

    companion object {
        private const val TAG = "DefaultFileAssetLoader"
    }
}
