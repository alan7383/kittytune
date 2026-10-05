package com.alananasss.kittytune.data.zapret

import android.util.Log
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException
import java.nio.channels.SocketChannel
import javax.net.SocketFactory
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.math.min

/**
 * In-app DPI circumvention, the Android equivalent of what zapret's `winws`/`nfqws` do on desktop.
 *
 * No root, no VPN, no proxy, no external folder: the TLS ClientHello — the packet DPI boxes read
 * the SNI (host name) from to decide what to throttle or reset — is split across several small TCP
 * segments with a short pause between them. A box that only inspects the first packet per connection
 * never sees a complete ClientHello and lets the connection through, while the server's TCP stack
 * reassembles the segments transparently.
 *
 * This is the same principle as zapret `--dpi-desync=fake,split2` (minus the fake packet, which
 * needs raw sockets and therefore root) and GoodbyeDPI `-f` fragmentation. It only applies to the
 * app's own traffic and only to hosts in the bypass domain list; everything else goes through
 * untouched. Connections are pooled by OkHttp/ExoPlayer, so the one-time per-connection pause
 * (a few milliseconds) does not affect playback.
 */
enum class ZapretStrategy(val id: String) {
    /** Split the first flight in two, zapret `split2`-style. Fastest, defeats basic SNI DPI. */
    SPLIT2("split2"),

    /** Many tiny fragments, GoodbyeDPI-style. Slightly slower handshake, stronger against DPI. */
    MULTI("multi");

    companion object {
        fun fromId(id: String?): ZapretStrategy =
            entries.firstOrNull { it.id == id } ?: SPLIT2
    }
}

/** How to fragment one connection's first bytes. */
data class FragmentSpec(
    /** Max bytes per TCP segment while fragmenting. */
    val chunkBytes: Int,
    /** Pause between segments, defeats Nagle coalescing so each chunk is its own segment. */
    val delayMs: Long,
    /** Only the first [budgetBytes] of a connection are fragmented (covers the ClientHello). */
    val budgetBytes: Long,
) {
    companion object {
        fun forStrategy(strategy: ZapretStrategy): FragmentSpec = when (strategy) {
            ZapretStrategy.SPLIT2 -> FragmentSpec(chunkBytes = 512, delayMs = 8, budgetBytes = 4096)
            ZapretStrategy.MULTI -> FragmentSpec(chunkBytes = 32, delayMs = 1, budgetBytes = 8192)
        }
    }
}

object DpiBypass {
    private const val TAG = "DpiBypass"

    /**
     * Decides per connection whether to fragment. `null` means pass through.
     * Set by [ZapretManager]; read on the connection thread, so it must be cheap and thread-safe.
     */
    @Volatile
    var policy: (host: String?) -> FragmentSpec? = { null }

    private val plainDelegate: SocketFactory = SocketFactory.getDefault()

    /** Drop-in [SocketFactory] for OkHttp builders. Delegates everything when the policy says no. */
    val socketFactory: SocketFactory = object : SocketFactory() {
        override fun createSocket(): Socket = BypassSocket(plainDelegate.createSocket(), null)
        override fun createSocket(host: String?, port: Int): Socket =
            connectedSocket(plainDelegate.createSocket(), host, port) { plainDelegate.createSocket(host, port) }

        override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
            connectedSocket(plainDelegate.createSocket(), host, port) {
                plainDelegate.createSocket(host, port, localHost, localPort)
            }

        override fun createSocket(host: InetAddress?, port: Int): Socket =
            connectedSocket(plainDelegate.createSocket(), host?.hostName, port) {
                plainDelegate.createSocket(host, port)
            }

        override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
            connectedSocket(plainDelegate.createSocket(), address?.hostName, port) {
                plainDelegate.createSocket(address, port, localAddress, localPort)
            }

        /** Connects [fresh] to [host]:[port] and wraps it; falls back to [direct] when that fails. */
        private fun connectedSocket(fresh: Socket, host: String?, port: Int, direct: () -> Socket): Socket {
            return try {
                fresh.connect(InetSocketAddress(host, port))
                BypassSocket(fresh, host)
            } catch (_: Exception) {
                runCatching { fresh.close() }
                // [direct] lets the delegate resolve/connect itself (e.g. unresolved hosts).
                BypassSocket(direct(), host)
            }
        }
    }

    private val tlsDelegate: SSLSocketFactory by lazy {
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, null, null)
        ctx.socketFactory
    }

    /**
     * Drop-in [SSLSocketFactory] installed as the process default so `HttpURLConnection`-based
     * clients (ExoPlayer's `DefaultHttpDataSource`, downloads) fragment too. TLS is layered over a
     * fragmenting plain socket, so the handshake flights are split at the TCP level.
     */
    val sslSocketFactory: SSLSocketFactory = object : SSLSocketFactory() {
        override fun getDefaultCipherSuites(): Array<String> = tlsDelegate.defaultCipherSuites
        override fun getSupportedCipherSuites(): Array<String> = tlsDelegate.supportedCipherSuites

        override fun createSocket(): Socket = tlsDelegate.createSocket()

        override fun createSocket(host: String?, port: Int): Socket =
            layeredSocket(host, port)

        override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
            layeredSocket(host, port, localHost, localPort)

        override fun createSocket(host: InetAddress?, port: Int): Socket =
            layeredSocket(host?.hostName, port)

        override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
            layeredSocket(address?.hostName, port, localAddress, localPort)

        override fun createSocket(s: Socket?, host: String?, port: Int, autoClose: Boolean): Socket {
            // The layered variant both OkHttp (RealConnection.connectTls) and HttpURLConnection use:
            // the TCP socket is already connected but no handshake byte has flown yet, so wrapping
            // its streams still splits the ClientHello. Skip re-wrapping our own sockets — the inner
            // BypassSocket already fragments.
            if (s == null) return tlsDelegate.createSocket(null, host, port, autoClose)
            val transport: Socket =
                if (s !is BypassSocket && runCatching { policy(host) }.getOrNull() != null) {
                    BypassSocket(s, host)
                } else {
                    s
                }
            return tlsDelegate.createSocket(transport, host, port, autoClose)
        }

        private fun layeredSocket(host: String?, port: Int, localHost: InetAddress? = null, localPort: Int = 0): Socket {
            val spec = runCatching { policy(host) }.getOrNull()
            if (spec == null) {
                return if (localHost != null) tlsDelegate.createSocket(host, port, localHost, localPort)
                else tlsDelegate.createSocket(host, port)
            }
            val plain = (plainDelegate.createSocket() as? BypassSocket)?.apply { setFragmentHost(host) }
                ?: BypassSocket(plainDelegate.createSocket(), host)
            try {
                if (localHost != null) plain.bind(InetSocketAddress(localHost, localPort))
                plain.connect(InetSocketAddress(host, port))
                plain.tcpNoDelay = true
            } catch (e: IOException) {
                runCatching { plain.close() }
                throw e
            }
            return tlsDelegate.createSocket(plain, host, port, true)
        }
    }

    /** Installs [sslSocketFactory] as the process default once; the policy gates per connection. */
    fun installDefaultHttpsFactory() {
        try {
            HttpsURLConnection.setDefaultSSLSocketFactory(sslSocketFactory)
        } catch (e: Exception) {
            Log.w(TAG, "Could not install default SSLSocketFactory", e)
        }
    }

    /**
     * A [Socket] that splits the first bytes written into small flushed chunks when the policy
     * provides a [FragmentSpec] for the remote host. Everything else is a straight delegate.
     */
    private class BypassSocket(
        private val delegate: Socket,
        initialHost: String?,
    ) : Socket() {
        @Volatile
        private var remoteHost: String? = initialHost?.takeIf { it.isNotBlank() }
        @Volatile
        private var spec: FragmentSpec? = null
        @Volatile
        private var specResolved = false

        fun setFragmentHost(host: String?) {
            if (!specResolved) remoteHost = host?.takeIf { it.isNotBlank() }
        }

        private fun resolveSpec(): FragmentSpec? {
            if (!specResolved) {
                specResolved = true
                spec = runCatching { policy(remoteHost ?: delegate.inetAddress?.hostName) }.getOrNull()
                if (spec != null) runCatching { delegate.tcpNoDelay = true }
            }
            return spec
        }

        override fun connect(endpoint: SocketAddress?, timeout: Int) {
            (endpoint as? InetSocketAddress)?.let { if (!it.isUnresolved) remoteHost = it.hostString }
            delegate.connect(endpoint, timeout)
        }

        override fun connect(endpoint: SocketAddress?) {
            (endpoint as? InetSocketAddress)?.let { if (!it.isUnresolved) remoteHost = it.hostString }
            delegate.connect(endpoint)
        }

        override fun bind(bindpoint: SocketAddress?) = delegate.bind(bindpoint)
        override fun getInputStream(): InputStream = delegate.inputStream
        override fun getOutputStream(): OutputStream {
            val active = resolveSpec()
            val raw = delegate.outputStream
            return if (active == null) raw else FragmentingOutputStream(raw, active)
        }

        override fun close() = delegate.close()
        override fun shutdownInput() = delegate.shutdownInput()
        override fun shutdownOutput() = delegate.shutdownOutput()
        override fun isConnected(): Boolean = delegate.isConnected
        override fun isBound(): Boolean = delegate.isBound
        override fun isClosed(): Boolean = delegate.isClosed
        override fun isInputShutdown(): Boolean = delegate.isInputShutdown
        override fun isOutputShutdown(): Boolean = delegate.isOutputShutdown
        override fun getInetAddress(): InetAddress? = delegate.inetAddress
        override fun getPort(): Int = delegate.port
        override fun getRemoteSocketAddress(): SocketAddress? = delegate.remoteSocketAddress
        override fun getLocalSocketAddress(): SocketAddress? = delegate.localSocketAddress
        override fun getLocalAddress(): InetAddress? = delegate.localAddress
        override fun getLocalPort(): Int = delegate.localPort
        override fun getChannel(): SocketChannel? = delegate.channel
        override fun getSoTimeout(): Int = delegate.soTimeout
        override fun setSoTimeout(timeout: Int) { delegate.soTimeout = timeout }
        override fun getTcpNoDelay(): Boolean = delegate.tcpNoDelay
        override fun setTcpNoDelay(on: Boolean) { delegate.tcpNoDelay = on }
        override fun getKeepAlive(): Boolean = delegate.keepAlive
        override fun setKeepAlive(on: Boolean) { delegate.keepAlive = on }
        override fun getReceiveBufferSize(): Int = delegate.receiveBufferSize
        override fun setReceiveBufferSize(size: Int) { delegate.receiveBufferSize = size }
        override fun getSendBufferSize(): Int = delegate.sendBufferSize
        override fun setSendBufferSize(size: Int) { delegate.sendBufferSize = size }
        override fun getSoLinger(): Int = delegate.soLinger
        override fun setSoLinger(on: Boolean, linger: Int) { delegate.setSoLinger(on, linger) }
        override fun getTrafficClass(): Int = delegate.trafficClass
        override fun setTrafficClass(tc: Int) { delegate.trafficClass = tc }
        override fun getReuseAddress(): Boolean = delegate.reuseAddress
        override fun setReuseAddress(on: Boolean) { delegate.reuseAddress = on }
        override fun getOOBInline(): Boolean = delegate.oobInline
        override fun setOOBInline(on: Boolean) { delegate.oobInline = on }
        override fun sendUrgentData(data: Int) = delegate.sendUrgentData(data)
        override fun setPerformancePreferences(connectionTime: Int, latency: Int, bandwidth: Int) =
            delegate.setPerformancePreferences(connectionTime, latency, bandwidth)

        override fun toString(): String = "BypassSocket($delegate)"
    }

    private class FragmentingOutputStream(
        out: OutputStream,
        private val spec: FragmentSpec,
    ) : FilterOutputStream(out) {
        @Volatile
        private var remaining = spec.budgetBytes

        @Throws(IOException::class)
        override fun write(b: Int) {
            write(byteArrayOf(b.toByte()), 0, 1)
        }

        @Throws(IOException::class)
        override fun write(buf: ByteArray, off: Int, len: Int) {
            val budget = remaining
            if (budget <= 0L || len <= 0) {
                out.write(buf, off, len)
                return
            }
            var offset = off
            var fragmentable = min(len.toLong(), budget).toInt()
            try {
                while (fragmentable > 0) {
                    val n = min(fragmentable, spec.chunkBytes)
                    out.write(buf, offset, n)
                    out.flush()
                    offset += n
                    fragmentable -= n
                    remaining -= n
                    if (fragmentable > 0 || offset - off < len) {
                        try {
                            Thread.sleep(spec.delayMs)
                        } catch (_: InterruptedException) {
                            Thread.currentThread().interrupt()
                            break
                        }
                    }
                }
            } catch (e: SocketException) {
                throw e
            }
            val rest = len - (offset - off)
            if (rest > 0) out.write(buf, offset, rest)
        }
    }
}
