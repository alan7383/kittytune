package com.alananasss.kittytune

import com.alananasss.kittytune.data.zapret.DpiBypass
import com.alananasss.kittytune.data.zapret.FragmentSpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The load-bearing property of the in-app zapret: [DpiBypass.socketFactory] must deliver
 * every byte intact and in order while fragmenting, and pass through untouched when the
 * policy says no. A broken Socket override here would silently kill all app networking.
 */
class DpiBypassSocketTest {

    private fun withPolicy(policy: (String?) -> FragmentSpec?, block: () -> Unit) {
        val previous = DpiBypass.policy
        DpiBypass.policy = policy
        try {
            block()
        } finally {
            DpiBypass.policy = previous
        }
    }

    @Test
    fun fragmentedWritesArriveIntactAndInOrder() {
        val payload = ByteArray(4096) { (it * 31 % 251).toByte() }
        // Deterministic pass: reader thread started first, then the client connects.
        ServerSocket(0).use { server ->
            val pool = Executors.newSingleThreadExecutor()
            try {
                val future = pool.submit<ByteArray> {
                    server.accept().use { socket ->
                        socket.soTimeout = 10_000
                        val out = ByteArrayOutputStream()
                        val buf = ByteArray(1024)
                        var total = 0
                        while (total < payload.size) {
                            val n = socket.getInputStream().read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            total += n
                        }
                        out.toByteArray()
                    }
                }
                withPolicy({ FragmentSpec(chunkBytes = 32, delayMs = 1, budgetBytes = 8192) }) {
                    // The exact path OkHttp takes: unconnected socket, then connect(address).
                    DpiBypass.socketFactory.createSocket().use { socket ->
                        socket.connect(InetSocketAddress("127.0.0.1", server.localPort), 5_000)
                        socket.getOutputStream().write(payload)
                    }
                }
                assertArrayEquals(payload, future.get(15, TimeUnit.SECONDS))
            } finally {
                pool.shutdownNow()
            }
        }
    }

    @Test
    fun policySeesTheRemoteHostAndCanPassThrough() {
        val seen = mutableListOf<String?>()
        ServerSocket(0).use { server ->
            val pool = Executors.newSingleThreadExecutor()
            try {
                val future = pool.submit<Int> {
                    server.accept().use { socket ->
                        socket.soTimeout = 10_000
                        socket.getInputStream().read()
                    }
                }
                withPolicy({ host ->
                    seen += host
                    null // pass through
                }) {
                    DpiBypass.socketFactory.createSocket("127.0.0.1", server.localPort).use { socket ->
                        socket.getOutputStream().write(42)
                    }
                }
                assertEquals(42, future.get(15, TimeUnit.SECONDS))
            } finally {
                pool.shutdownNow()
            }
        }
        assertTrue("policy should observe the target host, saw: $seen", seen.any { it == "127.0.0.1" })
    }

    @Test
    fun fragmentationActuallySplitsWithDelays() {
        val payload = ByteArray(100) { it.toByte() }
        ServerSocket(0).use { server ->
            val pool = Executors.newSingleThreadExecutor()
            try {
                val future = pool.submit<ByteArray> {
                    server.accept().use { socket ->
                        socket.soTimeout = 10_000
                        val out = ByteArrayOutputStream()
                        val buf = ByteArray(64)
                        var total = 0
                        while (total < payload.size) {
                            val n = socket.getInputStream().read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            total += n
                        }
                        out.toByteArray()
                    }
                }
                // 1-byte chunks with 5 ms between: 100 chunks must take ~0.5 s when fragmenting,
                // and ~0 ms when passing through.
                val start = System.nanoTime()
                withPolicy({ FragmentSpec(chunkBytes = 1, delayMs = 5, budgetBytes = 8192) }) {
                    DpiBypass.socketFactory.createSocket("127.0.0.1", server.localPort).use { socket ->
                        socket.getOutputStream().write(payload)
                    }
                }
                val elapsedMs = (System.nanoTime() - start) / 1_000_000
                assertArrayEquals(payload, future.get(15, TimeUnit.SECONDS))
                assertTrue("expected ~500 ms of inter-chunk delays, took ${elapsedMs} ms", elapsedMs >= 300)
            } finally {
                pool.shutdownNow()
            }
        }
    }

    @Test
    fun budgetExhaustionFallsBackToPassthrough() {
        val payload = ByteArray(100) { it.toByte() }
        ServerSocket(0).use { server ->
            val pool = Executors.newSingleThreadExecutor()
            try {
                val future = pool.submit<ByteArray> {
                    server.accept().use { socket ->
                        socket.soTimeout = 10_000
                        val out = ByteArrayOutputStream()
                        val buf = ByteArray(64)
                        var total = 0
                        while (total < payload.size) {
                            val n = socket.getInputStream().read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            total += n
                        }
                        out.toByteArray()
                    }
                }
                // Zero budget: everything must flow without any inter-chunk delay.
                val start = System.nanoTime()
                withPolicy({ FragmentSpec(chunkBytes = 1, delayMs = 5, budgetBytes = 0) }) {
                    DpiBypass.socketFactory.createSocket("127.0.0.1", server.localPort).use { socket ->
                        socket.getOutputStream().write(payload)
                    }
                }
                val elapsedMs = (System.nanoTime() - start) / 1_000_000
                assertArrayEquals(payload, future.get(15, TimeUnit.SECONDS))
                assertTrue("passthrough should be fast, took ${elapsedMs} ms", elapsedMs < 300)
            } finally {
                pool.shutdownNow()
            }
        }
    }

    /**
     * End to end through the exact path OkHttp takes for HTTPS: plain socket from
     * [DpiBypass.socketFactory], then TLS layered via
     * [DpiBypass.sslSocketFactory]`createSocket(socket, host, port, …)`. The ClientHello must
     * leave the device fragmented (inter-chunk delays on the sender side prove the chunks went
     * out as separate writes, i.e. separate TCP segments).
     */
    @Test
    fun layeredTlsClientHelloLeavesFragmented() {
        ServerSocket(0).use { server ->
            val pool = Executors.newSingleThreadExecutor()
            var receivedBytes = 0
            try {
                val future = pool.submit<Unit> {
                    server.accept().use { socket ->
                        socket.soTimeout = 10_000
                        val buf = ByteArray(4096)
                        try {
                            while (receivedBytes < 512) {
                                val n = socket.getInputStream().read(buf)
                                if (n < 0) break
                                receivedBytes += n
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
                val start = System.nanoTime()
                withPolicy({ FragmentSpec(chunkBytes = 32, delayMs = 5, budgetBytes = 8192) }) {
                    val plain = DpiBypass.socketFactory.createSocket()
                    plain.connect(InetSocketAddress("127.0.0.1", server.localPort), 5_000)
                    plain.soTimeout = 2_000
                    val tls = DpiBypass.sslSocketFactory.createSocket(plain, "example.com", 443, true)
                    // No TLS server behind: the handshake fails, but only after the
                    // (fragmented) ClientHello has been flushed out chunk by chunk.
                    runCatching { (tls as javax.net.ssl.SSLSocket).startHandshake() }
                    runCatching { tls.close() }
                }
                val elapsedMs = (System.nanoTime() - start) / 1_000_000
                future.get(15, TimeUnit.SECONDS)
                assertTrue("ClientHello should have been sent, got $receivedBytes bytes", receivedBytes > 100)
                // ~300 B ClientHello in 32 B chunks with 5 ms between ≈ 45 ms on the wire;
                // an unfragmented flight would take ~1 ms.
                assertTrue("ClientHello should leave fragmented, took ${elapsedMs} ms", elapsedMs >= 20)
            } finally {
                pool.shutdownNow()
            }
        }
    }
}
