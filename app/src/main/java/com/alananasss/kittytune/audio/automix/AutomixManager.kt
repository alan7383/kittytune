package com.alananasss.kittytune.audio.automix

import android.content.Context
import android.net.Uri
import android.util.Log
import com.alananasss.kittytune.data.StreamResolver
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.BeatInfoEntity
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow

object AutomixManager {

    private const val TAG = "AutomixManager"

    private data class BeatAnalysisHandle(val priority: BeatAnalysisPriority, val job: Job)
    private val beatAnalysisJobs = ConcurrentHashMap<String, BeatAnalysisHandle>()
    private val immediateAnalysisMutex = Mutex()
    private val lookaheadAnalysisMutex = Mutex()
    private val failedSongIds = ConcurrentHashMap<String, Long>()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var appContext: Context? = null

    private val _automixDebugInfo = MutableStateFlow<AutomixDebugInfo?>(null)
    val automixDebugInfo = _automixDebugInfo.asStateFlow()

    private val _isAutomixing = MutableStateFlow(false)
    val isAutomixing = _isAutomixing.asStateFlow()

    private val _mixBeatsLeft = MutableStateFlow<Int?>(null)
    val mixBeatsLeft = _mixBeatsLeft.asStateFlow()

    @Volatile
    var currentAutomixPlan: AutomixPlan? = null
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        failedSongIds.clear() // clear stale failures on fresh init
        scope.launch {
            try {
                AppDatabase.getDatabase(context).beatInfoDao().clearFailedBeatInfo()
            } catch (_: Exception) {}
        }
    }

    fun setIsAutomixing(active: Boolean) {
        _isAutomixing.value = active
    }

    fun setMixBeatsLeft(beats: Int?) {
        _mixBeatsLeft.value = beats
    }

    fun clearPlan() {
        currentAutomixPlan = null
        _mixBeatsLeft.value = null
        if (!_isAutomixing.value) {
            _automixDebugInfo.value = null
        }
    }

    fun maybeAnalyzeBeat(track: Track, priority: BeatAnalysisPriority = BeatAnalysisPriority.IMMEDIATE) {
        val context = appContext ?: return
        val songId = track.id.toString()

        val lastFailed = failedSongIds[songId]
        if (lastFailed != null && android.os.SystemClock.elapsedRealtime() - lastFailed < 45_000L) {
            return
        }

        synchronized(beatAnalysisJobs) {
            val existing = beatAnalysisJobs[songId]
            if (existing != null) {
                if (priority == BeatAnalysisPriority.IMMEDIATE && existing.priority == BeatAnalysisPriority.LOOKAHEAD) {
                    beatAnalysisJobs[songId] = existing.copy(priority = BeatAnalysisPriority.IMMEDIATE)
                }
                return
            }

            val job = scope.launch {
                val mutex = when (priority) {
                    BeatAnalysisPriority.IMMEDIATE -> immediateAnalysisMutex
                    BeatAnalysisPriority.LOOKAHEAD -> lookaheadAnalysisMutex
                }
                mutex.withLock {
                    try {
                        runBeatAnalysis(context, track, priority)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        Log.d(TAG, "Beat analysis cancelled for $songId ($priority)")
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Beat analysis failed for $songId: ${e.message}")
                    } finally {
                        synchronized(beatAnalysisJobs) {
                            beatAnalysisJobs.remove(songId)
                        }
                    }
                }
            }
            beatAnalysisJobs[songId] = BeatAnalysisHandle(priority, job)
        }
    }

    private suspend fun runBeatAnalysis(context: Context, track: Track, priority: BeatAnalysisPriority) {
        val songId = track.id.toString()
        val db = AppDatabase.getDatabase(context)
        val existing = db.beatInfoDao().getBeatInfo(songId)
        if (existing != null && existing.bpm > 0f) {
            val currentDbg = _automixDebugInfo.value
            val isCurrent = com.alananasss.kittytune.data.MusicManager.currentTrack?.id == track.id
            if (isCurrent && (currentDbg == null || currentDbg.outBpm == null)) {
                _automixDebugInfo.value = (currentDbg ?: AutomixDebugInfo("standby")).copy(
                    outBpm = existing.bpm,
                    outConfidence = existing.confidence,
                    outMixOutMs = existing.mixOutPointMs,
                )
            }
            return
        }

        Log.d(TAG, "Beat analysis starting for $songId - ${track.title} ($priority)")
        val startedAt = android.os.SystemClock.elapsedRealtime()
        // YouTube NewPipe fallback takes ~5-6s to search/resolve + stream fetch time.
        // IMMEDIATE gets a generous budget; LOOKAHEAD also needs enough for NewPipe.
        val timeoutMs = if (priority == BeatAnalysisPriority.IMMEDIATE) 45_000L else 30_000L
        fun timedOutOrCancelled(): Boolean =
            android.os.SystemClock.elapsedRealtime() - startedAt > timeoutMs

        var result: BeatAnalyzer.Result? = null
        var dataComplete = false

        // 1. Check if track is local / downloaded
        var localPath: String? = null
        try {
            val localTrack = db.downloadDao().getTrack(track.id)
            if (localTrack != null && localTrack.localAudioPath.isNotBlank()) {
                localPath = localTrack.localAudioPath
            }
        } catch (_: Exception) {}

        val trackDurationMs = track.durationMs ?: track.fullDuration ?: 0L
        if (localPath != null) {
            if (localPath.startsWith("exo_cache://")) {
                val parts = localPath.removePrefix("exo_cache://").split("::", limit = 3)
                val cachedStreamUrl = parts.getOrNull(1)
                val token = parts.getOrNull(2)
                if (!token.isNullOrEmpty()) {
                    try {
                        com.alananasss.kittytune.data.MusicManager.putDrmToken(track.id, token)
                    } catch (_: Exception) {}
                }
                if (!cachedStreamUrl.isNullOrEmpty()) {
                    Log.d(TAG, "Analyzing downloaded ExoCache stream for $songId (hasToken=${!token.isNullOrEmpty()})")
                    val fetched = BeatAnalyzer.analyzeDrmStream(
                        context = context,
                        streamUrl = cachedStreamUrl,
                        licenseAuthToken = token,
                        totalDurationMs = trackDurationMs,
                        shouldCancel = ::timedOutOrCancelled
                    )
                    result = fetched?.result
                    dataComplete = fetched?.complete ?: false
                }
            } else {
                val isContentUri = localPath.startsWith("content://")
                val fileExists = if (isContentUri) true else File(localPath).exists()
                if (fileExists) {
                    result = if (isContentUri) {
                        BeatAnalyzer.analyzeUri(context, Uri.parse(localPath), shouldCancel = ::timedOutOrCancelled)
                    } else {
                        BeatAnalyzer.analyzeFile(localPath, shouldCancel = ::timedOutOrCancelled)
                    }
                    dataComplete = true
                }
            }
        }

        // 2. If not local, resolve a stream URL for analysis.
        if (result == null && !timedOutOrCancelled()) {
            var stream: com.alananasss.kittytune.data.ResolvedStream? = null
            try {
                stream = StreamResolver.resolveStreamForBeatAnalysis(context, track)
            } catch (e: Exception) {
                Log.w(TAG, "Failed resolving stream for analysis of $songId: ${e.message}")
            }

            if (stream != null) {
                if (stream.isDrmProtected && !stream.licenseAuthToken.isNullOrEmpty()) {
                    try {
                        com.alananasss.kittytune.data.MusicManager.putDrmToken(track.id, stream.licenseAuthToken)
                    } catch (_: Exception) {}
                }
                Log.d(TAG, "Analyzing stream for $songId: ${stream.url} (drm=${stream.isDrmProtected})")
                val fetched = if (stream.isDrmProtected || stream.url.contains(".m3u8") || stream.url.contains("cenc")) {
                    BeatAnalyzer.analyzeDrmStream(
                        context = context,
                        streamUrl = stream.url,
                        licenseAuthToken = stream.licenseAuthToken,
                        totalDurationMs = trackDurationMs,
                        shouldCancel = ::timedOutOrCancelled
                    )
                } else {
                    val tempDir = File(context.cacheDir, "automix_beat_tmp").apply { mkdirs() }
                    BeatAnalyzer.analyzeStream(
                        url = stream.url,
                        tempDir = tempDir,
                        context = context,
                        licenseAuthToken = stream.licenseAuthToken,
                        totalDurationMs = trackDurationMs,
                        shouldCancel = ::timedOutOrCancelled
                    )
                }
                result = fetched?.result
                dataComplete = fetched?.complete ?: false
            } else {
                Log.d(TAG, "No stream available for $songId, marking as failed")
                dataComplete = true // treat as definitive failure so we don't keep retrying
            }
        }

        Log.d(
            TAG,
            "Beat analysis done for $songId: " +
                (result?.let { "bpm=%.1f conf=%.2f mixIn=%s mixOut=%s key=%d isMinor=%s".format(it.bpm, it.confidence, it.mixInPointMs, it.mixOutPointMs, it.keyPitchClass, it.keyIsMinor) }
                    ?: "failed (complete=$dataComplete)")
        )

        if (result == null) {
            failedSongIds[songId] = android.os.SystemClock.elapsedRealtime()
            return
        }

        val entity = BeatInfoEntity(
            songId = songId,
            bpm = result.bpm,
            firstBeatOffsetMs = result.firstBeatOffsetMs,
            confidence = result.confidence,
            mixInPointMs = result.mixInPointMs ?: -1L,
            mixOutPointMs = result.mixOutPointMs ?: -1L,
            keyPitchClass = result.keyPitchClass,
            keyIsMinor = result.keyIsMinor,
        )

        withContext(Dispatchers.IO) {
            db.beatInfoDao().upsert(entity)
        }

        val currentDbg = _automixDebugInfo.value
        val isCurrent = com.alananasss.kittytune.data.MusicManager.currentTrack?.id == track.id
        if (isCurrent && (currentDbg == null || currentDbg.outBpm == null)) {
            _automixDebugInfo.value = (currentDbg ?: AutomixDebugInfo("standby")).copy(
                outBpm = result.bpm,
                outConfidence = result.confidence,
                outMixOutMs = result.mixOutPointMs,
            )
        }
    }

    suspend fun computeAutomixPlan(
        currentTrack: Track,
        nextTrack: Track,
        currentPosition: Long,
        trackDuration: Long,
        prefs: PlayerPreferences,
    ): AutomixPlanResult {
        val context = appContext ?: return AutomixPlanResult(null, false)
        val currentId = currentTrack.id.toString()
        val nextId = nextTrack.id.toString()

        val db = AppDatabase.getDatabase(context)
        val (outBeat, inBeat) = withContext(Dispatchers.IO) {
            db.beatInfoDao().getBeatInfo(currentId) to db.beatInfoDao().getBeatInfo(nextId)
        }

        if (outBeat == null) maybeAnalyzeBeat(currentTrack, BeatAnalysisPriority.IMMEDIATE)
        if (inBeat == null && nextId != currentId) maybeAnalyzeBeat(nextTrack, BeatAnalysisPriority.IMMEDIATE)

        val partialDebug = AutomixDebugInfo(
            status = "",
            outBpm = outBeat?.bpm,
            outConfidence = outBeat?.confidence,
            outMixOutMs = outBeat?.mixOutPointMs,
            inBpm = inBeat?.bpm,
            inConfidence = inBeat?.confidence,
            inMixInMs = inBeat?.mixInPointMs,
        )

        val isOutFailed = failedSongIds.containsKey(currentId) || (outBeat != null && outBeat.bpm <= 0f)
        val isInFailed = failedSongIds.containsKey(nextId) || (inBeat != null && inBeat.bpm <= 0f)

        if (isOutFailed || isInFailed) {
            _automixDebugInfo.value = partialDebug.copy(
                status = "fallback: stream unsupported"
            )
            return AutomixPlanResult(plan = null, pairAnalyzed = true)
        }

        if (outBeat == null || inBeat == null) {
            _automixDebugInfo.value = partialDebug.copy(
                status = "fallback: analysis pending (" +
                    (if (outBeat == null) "current" else "") +
                    (if (outBeat == null && inBeat == null) "+" else "") +
                    (if (inBeat == null) "next" else "") + ")"
            )
            return AutomixPlanResult(plan = null, pairAnalyzed = false)
        }

        if (outBeat.confidence < 0.3f || inBeat.confidence < 0.3f || outBeat.bpm <= 0f || inBeat.bpm <= 0f) {
            _automixDebugInfo.value = partialDebug.copy(status = "fallback: low confidence")
            return AutomixPlanResult(plan = null, pairAnalyzed = true)
        }

        val periodMs = (60_000f / outBeat.bpm).toDouble()

        // Overlap duration: default 16 beats (4 bars), bounded 6-16s
        val overlapMode = prefs.getAutomixOverlapMode() // 0=Auto (4 bars), 1=2 bars, 2=4 bars, 3=8 bars, 4=Custom
        val baseOverlapMs = when (overlapMode) {
            1 -> (8 * periodMs).toLong().coerceIn(4_000L, 10_000L) // 2 bars
            2 -> (16 * periodMs).toLong().coerceIn(6_000L, 16_000L) // 4 bars
            3 -> (32 * periodMs).toLong().coerceIn(10_000L, 20_000L) // 8 bars
            4 -> prefs.getCrossfadeDuration() * 1000L
            else -> (16 * periodMs).toLong().coerceIn(6_000L, 16_000L) // Auto
        }
        val overlapMs = baseOverlapMs.coerceIn(4_000L, 20_000L)

        // Dynamic mix-out: start the transition where the song's body ends (outro begins)
        val latestTrigger = trackDuration - overlapMs
        val mixOut = if (prefs.getAutomixDynamicMixPointsEnabled()) {
            outBeat.mixOutPointMs?.takeIf { it > 0 }
        } else null
        val effectiveTrigger = mixOut?.coerceAtMost(latestTrigger) ?: latestTrigger

        // Snap the fade start onto an 8-beat phrase boundary of the outgoing track's grid
        val phraseMs = periodMs * 8
        val anchor = max(effectiveTrigger, currentPosition + 1000)
        val k = ((anchor - outBeat.firstBeatOffsetMs) / phraseMs).toLong()
        var triggerTime = (outBeat.firstBeatOffsetMs + k * phraseMs).toLong()
        if (triggerTime < anchor) triggerTime = (outBeat.firstBeatOffsetMs + (k + 1) * phraseMs).toLong()

        val roomMs = trackDuration - 500 - triggerTime
        val effectiveOverlapMs = overlapMs.coerceAtMost(roomMs)
        if (effectiveOverlapMs < 3000L || triggerTime >= trackDuration - 3000) {
            _automixDebugInfo.value = partialDebug.copy(status = "fallback: trigger out of range")
            return AutomixPlanResult(plan = null, pairAnalyzed = true)
        }

        // Fold octave errors, then cap pitch-preserving stretch at ±8%
        var tempoRatio = 1f
        if (prefs.getAutomixTempoMatchEnabled()) {
            tempoRatio = outBeat.bpm / inBeat.bpm
            while (tempoRatio > 1.5f) tempoRatio /= 2f
            while (tempoRatio < 0.667f) tempoRatio *= 2f
            if (tempoRatio !in 0.92f..1.08f) tempoRatio = 1f
        }

        // Harmonic correction: pitch-shift incoming track to align musical keys (up to ±3 semitones)
        var pitchRatio = 1f
        if (prefs.getAutomixHarmonicMixEnabled()) {
            val outKeyClass = outBeat.keyPitchClass
            val inKeyClass = inBeat.keyPitchClass
            if (outKeyClass != null && inKeyClass != null) {
                val outEffective = if (outBeat.keyIsMinor == true) (outKeyClass + 3) % 12 else outKeyClass
                val inEffective = if (inBeat.keyIsMinor == true) (inKeyClass + 3) % 12 else inKeyClass
                var semitoneShift = (outEffective - inEffective) % 12
                if (semitoneShift > 6) semitoneShift -= 12
                if (semitoneShift < -6) semitoneShift += 12
                if (semitoneShift != 0 && abs(semitoneShift) <= 3) {
                    pitchRatio = 2.0.pow(semitoneShift / 12.0).toFloat()
                }
            }
        }

        // Dynamic mix-in: skip incoming track's intro, snapped onto its 8-beat phrase grid
        val inPeriodMs = (60_000f / inBeat.bpm).toDouble()
        val rawStart = if (prefs.getAutomixDynamicMixPointsEnabled()) {
            inBeat.mixInPointMs?.takeIf { it > 0 } ?: inBeat.firstBeatOffsetMs
        } else inBeat.firstBeatOffsetMs
        val inPhraseMs = inPeriodMs * 8
        val inK = ceil((rawStart - inBeat.firstBeatOffsetMs) / inPhraseMs).toLong().coerceAtLeast(0)
        val incomingStart = (inBeat.firstBeatOffsetMs + inK * inPhraseMs).toLong()

        val plan = AutomixPlan(
            currentId = currentId,
            nextId = nextId,
            triggerTimeMs = triggerTime,
            incomingStartMs = incomingStart,
            tempoRatio = tempoRatio,
            pitchRatio = pitchRatio,
            overlapMs = effectiveOverlapMs,
        )

        currentAutomixPlan = plan
        _automixDebugInfo.value = partialDebug.copy(
            status = "plan ready",
            triggerTimeMs = plan.triggerTimeMs,
            incomingStartMs = plan.incomingStartMs,
            tempoRatio = plan.tempoRatio,
            pitchRatio = plan.pitchRatio,
            overlapMs = plan.overlapMs,
        )

        return AutomixPlanResult(plan = plan, pairAnalyzed = true)
    }
}
