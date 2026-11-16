package com.alananasss.kittytune.data.auto

import android.content.Context
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

/**
 * Decides when the lyrics may take over the artist line of the media session.
 *
 * A connected Android Auto controller alone does not mean a car is attached: the Android Auto
 * phone app keeps its media browser bound to our service after the car is unplugged (and can
 * bind on its own after a reboot). Gating on the controller only left the lyrics in the artist
 * line, and in every other media surface such as quick settings, long after the drive ended.
 * The car connection state reported by Android Auto is the source of truth for "in a car".
 */
object AndroidAutoLyricsGate {

    fun isInCar(carConnectionType: Int): Boolean =
        carConnectionType == CarConnection.CONNECTION_TYPE_PROJECTION ||
            carConnectionType == CarConnection.CONNECTION_TYPE_NATIVE

    fun shouldShowLyrics(
        carConnectionType: Int,
        automotiveControllerConnected: Boolean,
        lyricsEnabled: Boolean,
    ): Boolean = lyricsEnabled && automotiveControllerConnected && isInCar(carConnectionType)

    /** Emits the [CarConnection] type (NOT_CONNECTED / NATIVE / PROJECTION) as it changes. */
    fun carConnectionTypeFlow(context: Context): Flow<Int> = callbackFlow {
        val type = CarConnection(context.applicationContext).type
        val observer = Observer<Int> { trySend(it) }
        type.observeForever(observer)
        awaitClose { type.removeObserver(observer) }
    }.flowOn(Dispatchers.Main.immediate).distinctUntilChanged()
}
