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

    private val memoryCache = LruCache<Long, FloatArray>(150)

    private val inFlightRequests = ConcurrentHashMap<Long, Boolean>()

    private val baseHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    private val httpClient: OkHttpClient
        get() = com.alananasss.kittytune.data.network.ProxyManager.configureOkHttpClient(baseHttpClient.newBuilder())
            .build()

    fun getCachedWaveform(trackId: Long): FloatArray? {
        return memoryCache.get(trackId)
    }

    suspend fun getWaveform(context: Context, track: Track, api: SoundCloudApi? = null): FloatArray? {
        val trackId = track.id
        if (trackId <= 0L) return null

        memoryCache.get(trackId)?.let { return it }

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

        return fetchAndCache(context, track, api)
    }

    suspend fun prefetchWaveform(context: Context, track: Track, api: SoundCloudApi? = null) {
        if (track.id <= 0L || memoryCache.get(track.id) != null) return
        if (inFlightRequests.putIfAbsent(track.id, true) != null) return

        try {
            getWaveform(context, track, api)
        } finally {
            inFlightRequests.remove(track.id)
        }
    }

    private suspend fun fetchAndCache(context: Context, track: Track, api: SoundCloudApi?): FloatArray? =
        withContext(Dispatchers.IO) {
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
                val responseBody =
                    httpClient.newCall(request).execute().use { it.body?.string() } ?: return@withContext null

                val json = JSONObject(responseBody)
                val height = json.optDouble("height", 140.0)
                val arr = json.optJSONArray("samples") ?: return@withContext null
                val count = arr.length()
                if (count == 0) return@withContext null

                val samples = FloatArray(count) { i ->
                    Math.pow((arr.getDouble(i) / height).coerceIn(0.0, 1.0), 1.5).toFloat().coerceIn(0.02f, 1f)
                }

                memoryCache.put(track.id, samples)

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
