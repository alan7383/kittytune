package com.alananasss.kittytune.wear.common

import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest

/**
 * Mapping functions to serialize/deserialize between [WearPlaybackState]
 * and Google Play Services Wearable [DataMap].
 */
object WearDataMapMapper {

    /**
     * Populates a [DataMap] with values from a [WearPlaybackState].
     */
    fun writeToDataMap(state: WearPlaybackState, dataMap: DataMap) {
        dataMap.putLong(WearConstants.KEY_TRACK_ID, state.trackId)
        dataMap.putString(WearConstants.KEY_TITLE, state.title)
        dataMap.putString(WearConstants.KEY_ARTIST, state.artist)
        dataMap.putString(WearConstants.KEY_ALBUM, state.album)
        if (state.artworkUrl != null) {
            dataMap.putString(WearConstants.KEY_ARTWORK_URL, state.artworkUrl)
        } else {
            dataMap.remove(WearConstants.KEY_ARTWORK_URL)
        }
        dataMap.putBoolean(WearConstants.KEY_IS_PLAYING, state.isPlaying)
        dataMap.putBoolean(WearConstants.KEY_IS_LOADING, state.isLoading)
        dataMap.putLong(WearConstants.KEY_POSITION_MS, state.positionMs)
        dataMap.putLong(WearConstants.KEY_DURATION_MS, state.durationMs)
        val safeVolume = if (state.volume.isNaN()) 0.0f else state.volume.coerceIn(0.0f, 1.0f)
        dataMap.putFloat(WearConstants.KEY_VOLUME, safeVolume)
        dataMap.putString(WearConstants.KEY_TARGET_DEVICE, state.targetDevice)
        dataMap.putString(WearConstants.KEY_TARGET_TYPE, state.targetType)
        if (state.targetDeviceId != null) {
            dataMap.putString(WearConstants.KEY_TARGET_DEVICE_ID, state.targetDeviceId)
        } else {
            dataMap.remove(WearConstants.KEY_TARGET_DEVICE_ID)
        }
        dataMap.putBoolean(WearConstants.KEY_IS_LIKED, state.isLiked)
        dataMap.putBoolean(WearConstants.KEY_SHUFFLE_ENABLED, state.shuffleEnabled)
        dataMap.putString(WearConstants.KEY_REPEAT_MODE, state.repeatMode)
        dataMap.putString(WearConstants.KEY_AVAILABLE_TARGETS, WearCodecs.encodeTargetsList(state.availableTargets))
        dataMap.putLong(WearConstants.KEY_TIMESTAMP, if (state.timestamp > 0L) state.timestamp else System.currentTimeMillis())
    }

    /**
     * Reads a [WearPlaybackState] from a [DataMap].
     */
    fun fromDataMap(dataMap: DataMap?): WearPlaybackState {
        if (dataMap == null) return WearPlaybackState.EMPTY
        val rawTargets = dataMap.getString(WearConstants.KEY_AVAILABLE_TARGETS)
        val rawVolume = dataMap.getFloat(WearConstants.KEY_VOLUME, 0.0f)
        val safeVolume = if (rawVolume.isNaN()) 0.0f else rawVolume.coerceIn(0.0f, 1.0f)
        return WearPlaybackState(
            trackId = dataMap.getLong(WearConstants.KEY_TRACK_ID, 0L),
            title = dataMap.getString(WearConstants.KEY_TITLE, ""),
            artist = dataMap.getString(WearConstants.KEY_ARTIST, ""),
            album = dataMap.getString(WearConstants.KEY_ALBUM, ""),
            artworkUrl = if (dataMap.containsKey(WearConstants.KEY_ARTWORK_URL)) dataMap.getString(WearConstants.KEY_ARTWORK_URL) else null,
            isPlaying = dataMap.getBoolean(WearConstants.KEY_IS_PLAYING, false),
            isLoading = dataMap.getBoolean(WearConstants.KEY_IS_LOADING, false),
            positionMs = dataMap.getLong(WearConstants.KEY_POSITION_MS, 0L),
            durationMs = dataMap.getLong(WearConstants.KEY_DURATION_MS, 0L),
            volume = safeVolume,
            targetDevice = dataMap.getString(WearConstants.KEY_TARGET_DEVICE, WearConstants.TARGET_PHONE_LABEL),
            targetType = dataMap.getString(WearConstants.KEY_TARGET_TYPE, WearConstants.TARGET_TYPE_PHONE),
            targetDeviceId = if (dataMap.containsKey(WearConstants.KEY_TARGET_DEVICE_ID)) dataMap.getString(WearConstants.KEY_TARGET_DEVICE_ID) else null,
            isLiked = dataMap.getBoolean(WearConstants.KEY_IS_LIKED, false),
            shuffleEnabled = dataMap.getBoolean(WearConstants.KEY_SHUFFLE_ENABLED, false),
            repeatMode = dataMap.getString(WearConstants.KEY_REPEAT_MODE, WearConstants.REPEAT_MODE_NONE),
            availableTargets = WearCodecs.decodeTargetsList(rawTargets),
            timestamp = dataMap.getLong(WearConstants.KEY_TIMESTAMP, 0L),
        )
    }

    /**
     * Builds an urgent [PutDataRequest] containing the serialized [WearPlaybackState].
     */
    fun createPutDataRequest(state: WearPlaybackState, path: String = WearConstants.PATH_PLAYBACK_STATE): PutDataRequest {
        val putDataMapRequest = PutDataMapRequest.create(path)
        writeToDataMap(state, putDataMapRequest.dataMap)
        return putDataMapRequest.asPutDataRequest().setUrgent()
    }

    /**
     * Extracts a [WearPlaybackState] from a [DataMapItem].
     */
    fun fromDataMapItem(item: DataMapItem?): WearPlaybackState {
        return fromDataMap(item?.dataMap)
    }
}
