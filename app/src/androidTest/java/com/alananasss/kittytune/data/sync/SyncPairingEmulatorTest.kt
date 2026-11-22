package com.alananasss.kittytune.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okio.buffer
import okio.source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

@RunWith(AndroidJUnit4::class)
class SyncPairingEmulatorTest {
    @Test
    fun firstPairingOffersAReachableCallbackAndClosesItWhenRejected() = runBlocking {
        val previousPort = SyncService.port
        val previousEnabled = SyncService.isListenerEnabled
        val wasRunning = SyncService.isRunning
        val loopback = InetAddress.getByName("127.0.0.1")
        SyncService.isListenerEnabled = false

        try {
            ServerSocket(0, 1, loopback).use { peer ->
                peer.soTimeout = 5_000
                SyncService.port = ServerSocket(0).use { it.localPort }
                val observedCallback = async(Dispatchers.IO) {
                    peer.accept().use { incoming ->
                        incoming.soTimeout = 5_000
                        val input = incoming.source().buffer()
                        assertEquals("POST /sync HTTP/1.1", input.readUtf8LineStrict())
                        val headers = generateSequence { input.readUtf8LineStrict().takeIf(String::isNotEmpty) }
                            .associate { it.substringBefore(':').lowercase() to it.substringAfter(':').trim() }
                        val request = Gson().fromJson(
                            input.readUtf8(headers.getValue("content-length").toLong()),
                            SyncExchange::class.java,
                        )
                        val callback = checkNotNull(request.callback) { "Pairing request has no callback" }
                        Socket().use { connection ->
                            connection.connect(InetSocketAddress(loopback, callback.port), 2_000)
                        }
                        incoming.getOutputStream().write(
                            "HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()
                        )
                        callback.port
                    }
                }

                val result = SyncClient.pair(
                    PairingPayload("127.0.0.1", peer.localPort, "test-secret", "test-peer", "Test peer")
                )

                assertEquals(SyncClient.Result.Unauthorized, result)
                assertEquals(SyncService.port, observedCallback.await())
                assertFalse(SyncService.isRunning)
                assertFalse(SyncService.isListenerEnabled)
            }
        } finally {
            SyncService.port = previousPort
            SyncService.isListenerEnabled = previousEnabled
            if (!wasRunning) SyncService.stop()
        }
    }
}
