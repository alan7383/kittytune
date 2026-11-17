package com.alananasss.kittytune.data.search

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.alananasss.kittytune.data.zapret.ZapretManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * A small difference hash for cover art, used by the verified-track filter.
 *
 * Two rows that claim to be the same song should show the same artwork. Comparing URLs does not
 * work — every platform re-hosts and resizes its own copy — so the image itself is reduced to a
 * 9x8 grayscale bitmap and every pixel is compared with the one to its right. That gives a 64-bit
 * fingerprint where the same cover at different sizes or compression levels stays within a few
 * bits, while a different cover lands much farther away.
 *
 * Decoded fingerprints are cached by URL: a search asks for the same handful of covers again
 * when it is repeated or when another result on the page shares the album art.
 */
object CoverFingerprint {

    private const val CACHE_SIZE = 192
    private const val MAX_BYTES = 6_000_000
    private const val FETCH_TIMEOUT_MS = 5_000L
    private const val HASH_WIDTH = 9
    private const val HASH_HEIGHT = 8

    private val cache = object : LruCache<String, Long>(CACHE_SIZE) {}

    /** URLs that will not be tried again during this process (404, not an image, local file…). */
    private val unavailable: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    /** One lock per URL so a page of rows sharing an album does not download it twice. */
    private val locks = ConcurrentHashMap<String, Mutex>()

    private val client by lazy {
        ZapretManager.newBuilder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    /** The fingerprint for [url], or null when the image cannot be fetched or decoded. */
    suspend fun fingerprint(url: String): Long? {
        if (url.isBlank() || !url.startsWith("http", ignoreCase = true)) return null
        cache.get(url)?.let { return it }
        if (url in unavailable) return null

        val lock = locks.getOrPut(url) { Mutex() }
        return lock.withLock {
            try {
                cache.get(url)?.let { return@withLock it }
                if (url in unavailable) return@withLock null

                val hash = try {
                    withTimeoutOrNull(FETCH_TIMEOUT_MS) { download(url) }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }

                if (hash == null) {
                    unavailable.add(url)
                } else {
                    cache.put(url, hash)
                }
                hash
            } finally {
                locks.remove(url)
            }
        }
    }

    /**
     * Hashes [bitmap] into the same 64-bit layout used by [fingerprint]. Exposed so other artwork
     * comparisons (and tests) can reuse the exact layout.
     */
    fun fromBitmap(bitmap: Bitmap): Long {
        val scaled = Bitmap.createScaledBitmap(bitmap, HASH_WIDTH, HASH_HEIGHT, true)
        val luminance = IntArray(HASH_WIDTH * HASH_HEIGHT)
        try {
            val pixels = IntArray(HASH_WIDTH * HASH_HEIGHT)
            scaled.getPixels(pixels, 0, HASH_WIDTH, 0, 0, HASH_WIDTH, HASH_HEIGHT)
            for (index in pixels.indices) {
                val pixel = pixels[index]
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF
                luminance[index] = (red * 299 + green * 587 + blue * 114) / 1000
            }
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }

        var hash = 0L
        var bit = 0
        for (y in 0 until HASH_HEIGHT) {
            val row = y * HASH_WIDTH
            for (x in 0 until HASH_WIDTH - 1) {
                val left = luminance[row + x]
                val right = luminance[row + x + 1]
                if (left > right) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    private suspend fun download(url: String): Long? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .header("Accept", "image/*")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val bytes = response.body?.bytes() ?: return@use null
            if (bytes.isEmpty() || bytes.size > MAX_BYTES) return@use null
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@use null
            try {
                fromBitmap(bitmap)
            } finally {
                bitmap.recycle()
            }
        }
    }
}
