package com.alananasss.kittytune

import androidx.car.app.connection.CarConnection
import com.alananasss.kittytune.data.auto.AndroidAutoLyricsGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAutoLyricsGateTest {

    @Test
    fun showsLyricsWhileProjectingToTheCar() {
        assertTrue(
            AndroidAutoLyricsGate.shouldShowLyrics(
                carConnectionType = CarConnection.CONNECTION_TYPE_PROJECTION,
                automotiveControllerConnected = true,
                lyricsEnabled = true,
            )
        )
    }

    @Test
    fun showsLyricsOnAndroidAutomotiveOs() {
        assertTrue(
            AndroidAutoLyricsGate.shouldShowLyrics(
                carConnectionType = CarConnection.CONNECTION_TYPE_NATIVE,
                automotiveControllerConnected = true,
                lyricsEnabled = true,
            )
        )
    }

    // Reported bug: after unplugging the USB cable the Android Auto app stays bound to the
    // session, so the lyrics kept replacing the artist line in quick settings.
    @Test
    fun restoresArtistOnceTheCarIsGoneEvenIfAndroidAutoStaysBound() {
        assertFalse(
            AndroidAutoLyricsGate.shouldShowLyrics(
                carConnectionType = CarConnection.CONNECTION_TYPE_NOT_CONNECTED,
                automotiveControllerConnected = true,
                lyricsEnabled = true,
            )
        )
    }

    @Test
    fun respectsTheSetting() {
        assertFalse(
            AndroidAutoLyricsGate.shouldShowLyrics(
                carConnectionType = CarConnection.CONNECTION_TYPE_PROJECTION,
                automotiveControllerConnected = true,
                lyricsEnabled = false,
            )
        )
    }

    @Test
    fun needsKittyTuneToBeBrowsedByTheCar() {
        assertFalse(
            AndroidAutoLyricsGate.shouldShowLyrics(
                carConnectionType = CarConnection.CONNECTION_TYPE_PROJECTION,
                automotiveControllerConnected = false,
                lyricsEnabled = true,
            )
        )
    }
}
