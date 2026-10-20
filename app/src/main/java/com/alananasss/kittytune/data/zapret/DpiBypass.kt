package com.alananasss.kittytune.data.zapret

import javax.net.SocketFactory
import javax.net.ssl.SSLSocketFactory

/**
 * Strategy for ByeDPI in-app circumvention.
 */
enum class ZapretStrategy(val id: String) {
    /** Split the first flight in two, zapret `split2`-style with TLS record split. */
    SPLIT2("split2"),

    /** Multi-split + disorder + TLS record split, GoodbyeDPI-style. */
    MULTI("multi");

    companion object {
        fun fromId(id: String?): ZapretStrategy =
            entries.firstOrNull { it.id == id } ?: SPLIT2
    }
}

/** Kept for compatibility with tests. */
data class FragmentSpec(
    val chunkBytes: Int,
    val delayMs: Long,
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
    @Volatile
    var policy: (host: String?) -> FragmentSpec? = { null }

    val socketFactory: SocketFactory = SocketFactory.getDefault()
    val sslSocketFactory: SSLSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory

    fun installDefaultHttpsFactory() {
        // Handled transparently by ProxySelector without touching SSLSocketFactory
    }
}
