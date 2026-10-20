package com.alananasss.kittytune

import com.alananasss.kittytune.data.zapret.DpiBypass
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DpiBypassSocketTest {

    @Test
    fun socketDeliversBytesIntactAndInOrder() {
        val payload = ByteArray(4096) { (it * 31 % 251).toByte() }
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
                DpiBypass.socketFactory.createSocket().use { socket ->
                    socket.connect(InetSocketAddress("127.0.0.1", server.localPort), 5_000)
                    socket.getOutputStream().write(payload)
                }
                assertArrayEquals(payload, future.get(15, TimeUnit.SECONDS))
            } finally {
                pool.shutdownNow()
            }
        }
    }
}
