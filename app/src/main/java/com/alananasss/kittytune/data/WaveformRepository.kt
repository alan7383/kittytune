package com.alananasss.kittytune.data

import android.content.Context
import android.util.Log
import android.util.LruCache
import com.alananasss.kittytune.data.network.SoundCloudApi
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object WaveformRepository {
    private const val TAG = "WaveformRepository"

    // In-memory LRU cache holding normalized FloatArray (up to 150 tracks)
    private val memoryCache = LruCache<Long, FloatArray>(150)

    // In-flight job flags to avoid duplicate parallel downloads
    private val inFlightRequests = ConcurrentHashMap<Long, Boolean>()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    /** Returns cached waveform samples immediately (0ms synchronous lookup). */
    fun getCachedWaveform(trackId: Long): FloatArray? {
        return memoryCache.get(trackId)
    }

    /** Gets waveform from memory cache, then disk cache, then network. */
    suspend fun getWaveform(context: Context, track: Track, api: SoundCloudApi? = null): FloatArray? {
        val trackId = track.id
        if (trackId <= 0L) return null

        // 1. Check memory cache (0ms)
        memoryCache.get(trackId)?.let { return it }

        // 2. Check disk cache (fast binary read)
        val diskFile = getDiskCacheFile(context, trackId)
        if (diskFile.exists() && diskFile.length() > 0) {
            try {
                val samples = withContext(Dispatchers.IO) { readSamplesFromDisk(diskFile) }
                if (samples != null && samples.isNotEmpty()) {
                    memoryCache.put(trackId, samples)
                    return samples
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading disk cache for track $trackId", e)
            }
        }

        // 3. Fetch from remote & cache
        return fetchAndCache(context, track, api)
    }

    /** Prefetches waveform in the background without blocking. */
    suspend fun prefetchWaveform(context: Context, track: Track, api: SoundCloudApi? = null) {
        if (track.id <= 0L || memoryCache.get(track.id) != null) return
        if (inFlightRequests.putIfAbsent(track.id, true) != null) return

        try {
            getWaveform(context, track, api)
        } finally {
            inFlightRequests.remove(track.id)
        }
    }

    private suspend fun fetchAndCache(context: Context, track: Track, api: SoundCloudApi?): FloatArray? = withContext(Dispatchers.IO) {
        try {
            var url = track.waveformUrl
            if (url.isNullOrBlank() && api != null && track.source == "soundcloud") {
                try {
                    val fullList = api.getTracksByIds(track.id.toString())
                    url = fullList.firstOrNull()?.waveformUrl
                } catch (e: Exception) {
                    Log.w(TAG, "Failed fetching waveformUrl for ${track.id}: ${e.message}")
                }
            }

            if (url.isNullOrBlank()) return@withContext null

            val request = Request.Builder().url(url).build()
            val responseBody = httpClient.newCall(request).execute().use { it.body?.string() } ?: return@withContext null

            val json = JSONObject(responseBody)
            val height = json.optDouble("height", 140.0)
            val arr = json.optJSONArray("samples") ?: return@withContext null
            val count = arr.length()
            if (count == 0) return@withContext null

            // SoundCloud normalization: (raw / height)^1.5, in [0.02, 1.0]
            val samples = FloatArray(count) { i ->
                Math.pow((arr.getDouble(i) / height).coerceIn(0.0, 1.0), 1.5).toFloat().coerceIn(0.02f, 1f)
            }

            // Store in memory cache
            memoryCache.put(track.id, samples)

            // Store in disk cache
            val diskFile = getDiskCacheFile(context, track.id)
            saveSamplesToDisk(diskFile, samples)

            samples
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching waveform for ${track.id}: ${e.message}")
            null
        }
    }

    private fun getDiskCacheFile(context: Context, trackId: Long): File {
        val dir = File(context.cacheDir, "waveforms").apply { if (!exists()) mkdirs() }
        return File(dir, "wf_$trackId.bin")
    }

    private fun saveSamplesToDisk(file: File, samples: FloatArray) {
        try {
            DataOutputStream(FileOutputStream(file).buffered()).use { out ->
                out.writeInt(samples.size)
                for (s in samples) {
                    out.writeFloat(s)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed saving waveform to disk", e)
        }
    }

    private fun readSamplesFromDisk(file: File): FloatArray? {
        return try {
            DataInputStream(FileInputStream(file).buffered()).use { input ->
                val size = input.readInt()
                if (size in 10..5000) {
                    val array = FloatArray(size)
                    for (i in 0 until size) {
                        array[i] = input.readFloat()
                    }
                    array
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
