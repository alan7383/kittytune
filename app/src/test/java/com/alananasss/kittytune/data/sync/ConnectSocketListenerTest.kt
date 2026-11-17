package com.alananasss.kittytune.data.sync

import kotlin.test.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Request
import okhttp3.Response
import okhttp3.Protocol
import okhttp3.WebSocket
import okio.ByteString

class ConnectSocketListenerTest {
    private class Socket : WebSocket {
        var closeCode: Int? = null
        override fun request() = Request.Builder().url("https://example.invalid").build()
        override fun queueSize() = 0L
        override fun send(text: String) = true
        override fun send(bytes: ByteString) = true
        override fun close(code: Int, reason: String?): Boolean { closeCode = code; return true }
        override fun cancel() {}
    }
    @Test fun remoteCloseUnblocksReconnectWithoutWaitingForOnClosed() = runBlocking {
        val socket = Socket()
        var departures = 0
        val listener = ConnectSocketListener({}, { _, _ -> }, { departures++ })
        listener.ready.complete(true)
        listener.onClosing(socket, 1001, "Going away")
        withTimeout(100) { listener.ended.await() }
        assertEquals(1000, socket.closeCode)
        listener.onClosed(socket, 1001, "Going away")
        assertEquals(1, departures)
    }
    @Test fun failedHandshakeDoesNotLeaveReconnectOrOldCallbacksRunning() = runBlocking {
        val socket = Socket()
        var opened = false
        var error = ""
        val listener = ConnectSocketListener({ opened = true }, { _, _ -> }, { error = it })
        val response = Response.Builder().request(socket.request()).protocol(Protocol.HTTP_1_1)
            .code(502).message("Bad gateway").build()
        listener.onFailure(socket, java.io.IOException("private data must not be shown"), response)
        assertFalse(withTimeout(100) { listener.ready.await() })
        withTimeout(100) { listener.ended.await() }
        listener.onOpen(socket, response)
        assertFalse(opened)
        assertEquals("HTTP 502", error)
    }
}
