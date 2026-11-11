package com.alananasss.kittytune.data

import android.content.Context
import android.util.Log
import android.util.LruCache
import com.alananasss.kittytune.audio.automix.FullTrackWaveformDecoder
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.data.network.SoundCloudApi
import com.alananasss.kittytune.domain.Track
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
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
import kotlin.coroutines.coroutineContext
import kotlin.math.floor

object WaveformRepository {
    private const val TAG = "WaveformRepository"

    private val memoryCache = LruCache<Long, FloatArray>(150)

    // Full-resolution REAL waveforms, decoded straight from the track's audio stream
    // (~50 peaks/second - enough to zoom into a single bar and still see every hit).
    // Kept separately from [memoryCache]: the SoundCloud arrays there are coarse
    // full-track overviews, these are the zoomable kind.
    private val decodedCache = LruCache<Long, FullTrackWaveformDecoder.DecodedWaveform>(12)

    private val inFlightRequests = ConcurrentHashMap<Long, Boolean>()

    // Guards the full-track decode: one pass per track id, others wait for the result.
    private val inFlightDecodes = ConcurrentHashMap<Long, Boolean>()

    // Disk format version of the decoded-waveform cache (`wfdec_*.bin`). Bumped to 2 to
    // drop any partial wave an interrupted decode may have persisted under v1.
    private const val DECODED_CACHE_VERSION = 2

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

    // The track's real waveform at full editor resolution.
    //
    // Sources, in order: the decoded memory/disk caches; SoundCloud's own waveform
    // (when the track has one - it is real audio data too); and, for everything else
    // (YouTube and friends), a one-off full-track decode via [FullTrackWaveformDecoder]
    // whose result lands in both caches. Never returns the synthesized placeholder -
    // null means "no waveform available", and the caller decides what to draw.
    suspend fun getDecodedWaveform(
        context: Context,
        track: Track,
        onProgress: ((Float) -> Unit)? = null,
    ): FullTrackWaveformDecoder.DecodedWaveform? {
        val trackId = track.id
        if (trackId <= 0L) return null

        decodedCache.get(trackId)?.let { return it }

        val diskFile = getDecodedCacheFile(context, trackId)
        if (diskFile.exists() && diskFile.length() > 0) {
            try {
                val decoded = withContext(Dispatchers.IO) { readDecodedFromDisk(diskFile) }
                if (decoded != null && decoded.peaks.isNotEmpty()) {
                    decodedCache.put(trackId, decoded)
                    return decoded
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading decoded waveform cache for track $trackId", e)
            }
        }

        // SoundCloud waveform JSON - real peaks, just coarse. Good enough for the editor.
        val cloud = runCatching { soundCloudSamples(context, track, null) }.getOrNull()
        if (cloud != null && cloud.isNotEmpty()) {
            val durationMs = track.actualDurationMs.takeIf { it > 0L } ?: track.durationMs ?: 0L
            if (durationMs > 0L) {
                val decoded = FullTrackWaveformDecoder.DecodedWaveform(
                    peaks = cloud,
                    durationMs = durationMs,
                    peaksPerSecond = ((cloud.size * 1000f) / durationMs).toInt().coerceAtLeast(1),
                    complete = true,
                )
                decodedCache.put(trackId, decoded)
                return decoded
            }
        }

        return decodeAndCache(context, track, onProgress)
    }

    // The overview waveform the transition editor's canvas draws. Same order as
    // [getDecodedWaveform]'s cheap paths, plus the full decode as the LAST resort -
    // until now YouTube tracks fell through to a synthesized placeholder, which is
    // why the editor's wave never looked like the song. The decode result is
    // downsampled to ≤ 2000 points so it fits the legacy cache format untouched.
    suspend fun getWaveform(context: Context, track: Track, api: SoundCloudApi? = null): FloatArray? {
        val trackId = track.id
        if (trackId <= 0L) return null

        soundCloudSamples(context, track, api)?.let { return it }

        // Real audio decode fallback (YouTube & other non-SoundCloud sources).
        val decoded = runCatching {
            getDecodedWaveform(
                context,
                track,
                onProgress = null,
            )
        }.getOrNull() ?: return null
        val overview = downsampleMax(decoded.peaks, 2000)
        if (overview.isEmpty()) return null
        memoryCache.put(trackId, overview)
        val diskFile = getDiskCacheFile(context, trackId)
        withContext(Dispatchers.IO) { saveSamplesToDisk(diskFile, overview) }
        return overview
    }

    // SoundCloud-only prefetch, as before: a queue scan must never spin up full-track
    // decoders for eight songs at once - the heavy path runs only when an editor is
    // actually open on the pair.
    suspend fun prefetchWaveform(context: Context, track: Track, api: SoundCloudApi? = null) {
        if (track.id <= 0L || memoryCache.get(track.id) != null) return
        if (inFlightRequests.putIfAbsent(track.id, true) != null) return

        try {
            soundCloudSamples(context, track, api)
        } finally {
            inFlightRequests.remove(track.id)
        }
    }

    // The original lookup chain, free of any decode fallback: memory -> disk ->
    // SoundCloud's waveform JSON. Shared by [getWaveform], [getDecodedWaveform] and
    // [prefetchWaveform] so nothing re-fetches twice.
    private suspend fun soundCloudSamples(context: Context, track: Track, api: SoundCloudApi?): FloatArray? {
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

    // Decodes the whole track once and files the result under both caches. A second caller
    // for the same track (the editor's mini canvas and the wave mode open together) waits
    // for the pass that is already running instead of starting its own.
    @UnstableApi
    private suspend fun decodeAndCache(
        context: Context,
        track: Track,
        onProgress: ((Float) -> Unit)?,
    ): FullTrackWaveformDecoder.DecodedWaveform? =
        withContext(Dispatchers.IO) {
            try {
                if (inFlightDecodes.putIfAbsent(track.id, true) != null) {
                    val startedAt = android.os.SystemClock.elapsedRealtime()
                    while (coroutineContext.isActive) {
                        decodedCache.get(track.id)?.let { return@withContext it }
                        if (android.os.SystemClock.elapsedRealtime() - startedAt > 100_000L) break
                        if (!inFlightDecodes.containsKey(track.id)) break
                        kotlinx.coroutines.delay(250L)
                    }
                    decodedCache.get(track.id)?.let { return@withContext it }
                }

                // Up to three passes, each with a freshly resolved stream: the stream URL
                // can expire between resolve and decode, and a mobile read can stall
                // mid-song - both used to surface as "waveform unavailable" in the editor.
                var best: FullTrackWaveformDecoder.DecodedWaveform? = null
                var attempts = 0
                while (attempts < 3 && coroutineContext.isActive) {
                    attempts++
                    if (attempts > 1) kotlinx.coroutines.delay(1_500L)

                    val stream = StreamResolver.resolveStreamWithDrm(context, track)
                    if (stream == null || stream.url.isBlank()) {
                        Log.w(TAG, "No stream to decode a waveform from for track ${track.id} (attempt $attempts)")
                        continue
                    }
                    val durationHintMs = track.actualDurationMs.takeIf { it > 0L }
                        ?: track.durationMs.takeIf { (it ?: 0L) > 0L }
                        ?: 0L
                    val decoded = FullTrackWaveformDecoder.decodePeaks(
                        context = context,
                        streamUrl = stream.url,
                        licenseAuthToken = stream.licenseAuthToken,
                        durationHintMs = durationHintMs,
                        shouldCancel = { !coroutineContext.isActive },
                        onProgress = onProgress,
                    ) ?: continue // hard failure - a fresh URL usually heals the next pass

                    if (decoded.complete) {
                        best = decoded
                        break
                    }
                    // A partial pass is still worth showing if nothing better arrives.
                    if (best == null || decoded.durationMs > best.durationMs) best = decoded
                }

                // Only a complete pass may become THE waveform of the track: caching a partial
                // one is how the editor ended up showing a 45-second wave for a 73-second song.
                if (best != null && best.complete) {
                    decodedCache.put(track.id, best)
                    val diskFile = getDecodedCacheFile(context, track.id)
                    saveDecodedToDisk(diskFile, best)
                } else if (best != null) {
                    Log.w(
                        TAG,
                        "Waveform decode still incomplete for track ${track.id}; " +
                            "showing partial wave without caching it",
                    )
                }
                best
            } catch (e: Exception) {
                Log.e(TAG, "Waveform decode failed for track ${track.id}: ${e.message}")
                null
            } finally {
                inFlightDecodes.remove(track.id)
            }
        }

    private suspend fun fetchAndCache(context: Context, track: Track, api: SoundCloudApi?): FloatArray? =
        withContext(Dispatchers.IO) {
            try {
                var url = track.waveformUrl
                if (url.isNullOrBlank() && track.source == "soundcloud") {
                    // The cheap real-data path: SoundCloud serves every track's own waveform
                    // JSON. Tracks that reached us without the url (search results, radio
                    // picks) looked like "no waveform at all" because no api instance was
                    // around to fetch it - look it up ourselves instead.
                    try {
                        val effectiveApi = api ?: RetrofitClient.create(context)
                        val fullList = effectiveApi.getTracksByIds(track.id.toString())
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

    private fun getDecodedCacheFile(context: Context, trackId: Long): File {
        val dir = File(context.cacheDir, "waveforms").apply { if (!exists()) mkdirs() }
        return File(dir, "wfdec_$trackId.bin")
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

    private fun saveDecodedToDisk(file: File, decoded: FullTrackWaveformDecoder.DecodedWaveform) {
        try {
            DataOutputStream(FileOutputStream(file).buffered()).use { out ->
                out.writeInt(DECODED_CACHE_VERSION)
                out.writeInt(decoded.peaks.size)
                out.writeLong(decoded.durationMs)
                out.writeInt(decoded.peaksPerSecond)
                out.writeBoolean(decoded.complete)
                for (v in decoded.peaks) {
                    out.writeFloat(v)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed saving decoded waveform to disk", e)
        }
    }

    private fun readDecodedFromDisk(file: File): FullTrackWaveformDecoder.DecodedWaveform? {
        return try {
            DataInputStream(FileInputStream(file).buffered()).use { input ->
                val version = input.readInt()
                if (version != DECODED_CACHE_VERSION) return null
                val size = input.readInt()
                if (size !in 10..100_000) return null
                val durationMs = input.readLong()
                val pps = input.readInt()
                val complete = input.readBoolean()
                if (durationMs <= 0L || pps <= 0) return null
                // A partial wave never becomes the track's waveform: pretend it is not there
                // so the caller falls through to a fresh decode.
                if (!complete) return null
                val peaks = FloatArray(size)
                for (i in 0 until size) {
                    peaks[i] = input.readFloat()
                }
                FullTrackWaveformDecoder.DecodedWaveform(
                    peaks = peaks,
                    durationMs = durationMs,
                    peaksPerSecond = pps,
                    complete = true,
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    // Max-pool downsample, matching the canvas's own [resampleWave] semantics.
    private fun downsampleMax(samples: FloatArray, buckets: Int): FloatArray {
        if (buckets <= 0 || samples.isEmpty()) return FloatArray(0)
        if (samples.size <= buckets) return samples
        val out = FloatArray(buckets)
        val perBucket = samples.size.toFloat() / buckets
        for (b in 0 until buckets) {
            val from = floor(b * perBucket).toInt()
            val to = floor((b + 1) * perBucket).toInt().coerceAtLeast(from + 1)
            var peak = 0f
            for (i in from until minOf(to, samples.size)) {
                if (samples[i] > peak) peak = samples[i]
            }
            out[b] = peak.coerceIn(0f, 1f)
        }
        return out
    }
}
