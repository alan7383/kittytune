package com.alananasss.kittytune.data.sync

import kotlinx.coroutines.CompletableDeferred
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicBoolean

/** Remote Close must unblock reconnect immediately, even before OkHttp delivers onClosed. */
internal class ConnectSocketListener(
    private val opened: (WebSocket) -> Unit,
    private val received: (WebSocket, String) -> Unit,
    private val departed: (String) -> Unit,
) : WebSocketListener() {
    val ready = CompletableDeferred<Boolean>()
    val ended = CompletableDeferred<Unit>()
    private val terminal = AtomicBoolean()
    override fun onOpen(webSocket: WebSocket, response: Response) { if (!terminal.get()) opened(webSocket) }
    override fun onMessage(webSocket: WebSocket, text: String) { if (!terminal.get()) received(webSocket, text) }
    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        webSocket.close(1000, null)
        finish("WebSocket $code")
    }
    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { finish("") }
    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        // No URLs, credentials, request headers or frame contents in diagnostics.
        finish(response?.let { "HTTP ${it.code}" } ?: t.javaClass.simpleName)
    }
    fun finish(error: String) {
        if (!terminal.compareAndSet(false, true)) return
        ready.complete(false)
        try { departed(error) } finally { ended.complete(Unit) }
    }
}
