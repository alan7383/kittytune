/**
 * Developed by Jason-Marshall Fastner, Germany <jasonfastner@protonmail.com>
 * Questions, feedback, or beat-matching debates? Feel free to reach out via email!
 * 
 * Note: Cats always land on their feet, and with this engine, your transitions will too.
 */
package com.alananasss.kittytune.ui.player

import android.content.Context
import android.util.Log
import com.alananasss.kittytune.audio.automix.AutomixManager
import com.alananasss.kittytune.audio.automix.BeatAnalysisPriority
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.BeatInfoEntity
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.phraseAnchorMs
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Main coordinator for the "DJ Flow / Non-Stop Mode" feature in Kittytune.
 *
 * Responsibilities:
 * - Reactive state container exposing [flowState] for UI components, animations, and overlays.
 * - Asynchronous queue analysis, caching, and harmonic scoring via [HarmonicMixer].
 * - Orchestration of seamless, beat-matched crossfades with dynamic progress updates.
 */
class DjFlowController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val prefs: PlayerPreferences? = null,
    var onSeekTo: ((Long) -> Unit)? = null,
    var onExecuteTransition: ((toTrack: Track?, crossfadeDurationMs: Long) -> Unit)? = null,
    var onRequestAutonomousTransition: ((
        targetTrack: Track,
        startPositionMs: Long,
        crossfadeDurationMs: Long,
        tempoRatio: Float,
        phaseOffsetMs: Long
    ) -> Unit)? = null,
    var onRequestPromoteTrack: ((fromIndex: Int, toIndex: Int) -> Unit)? = null,
    var onRequestPrebuffer: ((targetTrack: Track, startPositionMs: Long) -> Unit)? = null,
    var onRequestAppendToQueue: ((List<Track>) -> Unit)? = null,
    var onRequestPlayTracks: ((List<Track>) -> Unit)? = null
) {
    companion object {
        private const val TAG = "DjFlowController"
        const val MAX_CANDIDATES_LOOKAHEAD = 15
        private const val REPLENISH_CHECK_INTERVAL_MS = 3_000L
    }

    private val _flowState = MutableStateFlow(DjFlowState())
    val flowState: StateFlow<DjFlowState> = _flowState.asStateFlow()

    private val beatCache = ConcurrentHashMap<String, BeatInfoEntity>()
    private var evaluationJob: Job? = null
    private var transitionJob: Job? = null
    private var infiniteFetchJob: Job? = null

    private var currentTrack: Track? = null
    private var currentQueue: List<Track> = emptyList()
    private var currentQueueIndex: Int = -1

    private var currentPositionMs: Long = 0L
    private var currentDurationMs: Long = 0L
    private var hasTriggeredForCurrentTrack: Boolean = false
    private var hasPrebufferedForCurrentTrack: Boolean = false

    /**
     * Set once the playhead has been seen *before* the planned trigger point.
     *
     * A transition drops the incoming track in mid-song, at its mix-in point, and the freshly
     * planned trigger for that track can already lie behind that position. Without this the
     * trigger fires again the instant the new track starts, and each firing starts another one:
     * three transitions inside 0.7 s were observed, skipping tracks and stacking crossfades.
     */
    private var isTriggerArmed: Boolean = false

    /** Throttles the queue top-up check, which used to run on every position tick. */
    private var lastReplenishCheckAt: Long = 0L

    init {
        // Initialize from preferences if present
        prefs?.let { p ->
            val isActive = p.getDjFlowEnabled()
            val energyMode = EnergyMode.fromString(p.getDjFlowEnergyMode())
            val isAutonomous = p.getDjFlowAutonomousEnabled()
            val isConstantEnergy = p.getDjFlowConstantEnergy()
            val isAutoReorder = p.getDjFlowAutoReorder()
            val isLoopExt = p.getDjFlowLoopExtension()
            val loopBeats = p.getDjFlowLoopBeats()
            val isInfiniteStream = p.getDjFlowInfiniteStream()
            val category = p.getDjFlowCategory()
            val isAutoStemCut = p.getDjFlowAutoStemCut()

            _flowState.update {
                it.copy(
                    isActive = isActive,
                    energyMode = energyMode,
                    isAutonomousEnabled = isAutonomous,
                    isConstantEnergyEnabled = isConstantEnergy,
                    isAutoReorderEnabled = isAutoReorder,
                    isLoopExtensionEnabled = isLoopExt,
                    loopBeats = loopBeats,
                    isInfiniteStreamEnabled = isInfiniteStream,
                    selectedCategory = category,
                    isAutoStemDropCutEnabled = isAutoStemCut
                )
            }
        }

        // React immediately when any background beat analysis completes
        scope.launch {
            AutomixManager.onBeatAnalysisCompleted.collect { songId: String ->
                val isCurrent = currentTrack?.id?.toString() == songId
                val isInCandidates = currentQueue.drop(kotlin.math.max(0, currentQueueIndex + 1)).take(MAX_CANDIDATES_LOOKAHEAD).any { it.id.toString() == songId }
                if (isCurrent || isInCandidates) {
                    beatCache.remove(songId)
                    if (isCurrent && currentTrack != null) {
                        val updatedInfo = getOrFetchBeatInfo(currentTrack!!)
                        if (updatedInfo != null && updatedInfo.bpm > 0f) {
                            val bpm = updatedInfo.bpm
                            val key = if (updatedInfo.keyPitchClass != null && updatedInfo.keyIsMinor != null) {
                                CamelotKey.fromPitchClass(updatedInfo.keyPitchClass, updatedInfo.keyIsMinor)
                            } else null
                            withContext(Dispatchers.Main) {
                                _flowState.update {
                                    it.copy(
                                        currentBpm = bpm,
                                        currentKey = key
                                    )
                                }
                            }
                        }
                    }
                    evaluateQueueAsync()
                }
            }
        }
    }

    /**
     * Enables or disables DJ Flow mode.
     */
    fun enableDjMode(enabled: Boolean) {
        _flowState.update { it.copy(isActive = enabled) }
        prefs?.setDjFlowEnabled(enabled)
        if (enabled) {
            currentTrack?.let { curr ->
                AutomixManager.maybeAnalyzeBeat(curr, BeatAnalysisPriority.IMMEDIATE)
            }
            evaluateQueueAsync()
        }
    }

    fun toggleDjMode() {
        enableDjMode(!_flowState.value.isActive)
    }

    fun setAutonomousEnabled(enabled: Boolean) {
        _flowState.update { it.copy(isAutonomousEnabled = enabled) }
        prefs?.setDjFlowAutonomousEnabled(enabled)
    }

    fun setConstantEnergyEnabled(enabled: Boolean) {
        _flowState.update { it.copy(isConstantEnergyEnabled = enabled) }
        prefs?.setDjFlowConstantEnergy(enabled)
    }

    fun setAutoReorderEnabled(enabled: Boolean) {
        _flowState.update { it.copy(isAutoReorderEnabled = enabled) }
        prefs?.setDjFlowAutoReorder(enabled)
    }

    fun setEnergyMode(mode: EnergyMode) {
        _flowState.update { it.copy(energyMode = mode) }
        prefs?.setDjFlowEnergyMode(mode.name)
        evaluateQueueAsync()
    }

    fun setLoopExtensionEnabled(enabled: Boolean) {
        _flowState.update { it.copy(isLoopExtensionEnabled = enabled) }
        prefs?.setDjFlowLoopExtension(enabled)
    }

    fun setLoopBeats(beats: Int) {
        val clamped = beats.coerceIn(2, 32)
        _flowState.update { it.copy(loopBeats = clamped) }
        prefs?.setDjFlowLoopBeats(clamped)
        if (_flowState.value.isLoopActive) {
            activatePhraseLoopAt(currentPositionMs)
        }
    }

    fun toggleCurrentPhraseLoop() {
        if (_flowState.value.isLoopActive) {
            clearPhraseLoop()
        } else {
            activatePhraseLoopAt(currentPositionMs)
        }
    }

    fun activatePhraseLoopAt(positionMs: Long) {
        val bpm = if (_flowState.value.currentBpm > 0f) _flowState.value.currentBpm else 120f
        val beatMs = (60_000f / bpm).toDouble()
        val phraseLengthMs = (beatMs * _flowState.value.loopBeats).toLong().coerceAtLeast(1000L)

        val loopStart = ((positionMs / phraseLengthMs) * phraseLengthMs).coerceAtLeast(0L)
        val loopEnd = (loopStart + phraseLengthMs).coerceAtMost(if (currentDurationMs > 0) currentDurationMs else Long.MAX_VALUE)

        _flowState.update {
            it.copy(
                isLoopActive = true,
                loopRangeMs = Pair(loopStart, loopEnd)
            )
        }
    }

    fun clearPhraseLoop() {
        _flowState.update {
            it.copy(
                isLoopActive = false,
                loopRangeMs = null
            )
        }
    }

    // --- Real-Time DJ Stem Isolator Controls ---

    fun setStemLevels(vocal: Float, drum: Float, bass: Float) {
        _flowState.update {
            it.copy(
                vocalStemLevel = vocal,
                drumStemLevel = drum,
                bassStemLevel = bass
            )
        }
        com.alananasss.kittytune.data.MusicManager.setGlobalStemLevels(vocal, drum, bass)
    }

    fun setVocalStem(level: Float) {
        setStemLevels(level, _flowState.value.drumStemLevel, _flowState.value.bassStemLevel)
    }

    fun setDrumStem(level: Float) {
        setStemLevels(_flowState.value.vocalStemLevel, level, _flowState.value.bassStemLevel)
    }

    fun setBassStem(level: Float) {
        setStemLevels(_flowState.value.vocalStemLevel, _flowState.value.drumStemLevel, level)
    }

    fun resetStems() {
        setStemLevels(1.0f, 1.0f, 1.0f)
    }

    fun setAutoStemDropCutEnabled(enabled: Boolean) {
        _flowState.update { it.copy(isAutoStemDropCutEnabled = enabled) }
        prefs?.setDjFlowAutoStemCut(enabled)
    }

    // --- Infinite DJ Stream & Category Search ---

    fun setInfiniteStreamEnabled(enabled: Boolean) {
        _flowState.update { it.copy(isInfiniteStreamEnabled = enabled) }
        prefs?.setDjFlowInfiniteStream(enabled)
        if (enabled) {
            checkInfiniteReplenishment()
        }
    }

    fun setSelectedCategory(category: String) {
        _flowState.update { it.copy(selectedCategory = category) }
        prefs?.setDjFlowCategory(category)
    }

    fun searchAndPopulateCategory(query: String, startFreshIfIdle: Boolean = true) {
        infiniteFetchJob?.cancel()
        val cat = query.trim().ifEmpty { "Meine Likes" }
        _flowState.update {
            it.copy(
                selectedCategory = cat,
                isFetchingInfiniteCandidates = true
            )
        }
        prefs?.setDjFlowCategory(cat)

        infiniteFetchJob = scope.launch(Dispatchers.IO) {
            try {
                val candidateTracks = mutableListOf<Track>()
                val isLikes = cat.contains("Likes", ignoreCase = true) || cat.equals("Meine Likes", ignoreCase = true)

                if (isLikes) {
                    val likes = com.alananasss.kittytune.data.LikeRepository.likedTracks.value
                    candidateTracks.addAll(likes)
                } else {
                    val cleanTag = cat.replace(Regex("[^\\p{L}\\p{Nd}\\s]"), "").trim().ifEmpty { cat.trim() }
                    val api = com.alananasss.kittytune.data.network.RetrofitClient.create(context)
                    val resp = api.searchTracks(query = cleanTag, limit = 50)
                    val valid = resp.collection.filter {
                        val dur = it.durationMs ?: it.fullDuration ?: 0L
                        dur > 40_000L && it.media?.transcodings?.isNotEmpty() == true
                    }
                    candidateTracks.addAll(valid)
                }

                // Filter out songs already in current queue
                val existingIds = currentQueue.map { it.id }.toSet()
                val freshCandidates = candidateTracks.filter { !existingIds.contains(it.id) }
                val pool = if (freshCandidates.isNotEmpty()) freshCandidates else candidateTracks.shuffled()

                if (pool.isNotEmpty()) {
                    val currBpm = _flowState.value.currentBpm
                    val currKey = _flowState.value.currentKey

                    val scored = pool.map { track ->
                        val info = getOrFetchBeatInfo(track)
                        val candBpm = info?.bpm ?: 0f
                        val candKey = if (info?.keyPitchClass != null && info.keyIsMinor != null) {
                            CamelotKey.fromPitchClass(info.keyPitchClass, info.keyIsMinor)
                        } else null

                        HarmonicMixer.scoreCandidate(
                            currentBpm = currBpm,
                            currentKey = currKey,
                            candidateTrack = track,
                            candidateBpm = candBpm,
                            candidateKey = candKey,
                            mode = _flowState.value.energyMode,
                            mixInPointMs = info?.mixInPointMs,
                            mixOutPointMs = info?.mixOutPointMs
                        )
                    }.sortedByDescending { it.score }

                    val tracksToAdd = scored.take(15).map { it.track }
                    if (tracksToAdd.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            if (currentTrack == null && startFreshIfIdle) {
                                onRequestPlayTracks?.invoke(tracksToAdd)
                            } else {
                                onRequestAppendToQueue?.invoke(tracksToAdd)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch songs for category '$cat': ${e.message}", e)
            } finally {
                withContext(Dispatchers.Main) {
                    _flowState.update { it.copy(isFetchingInfiniteCandidates = false) }
                    evaluateQueueAsync()
                }
            }
        }
    }

    private fun checkInfiniteReplenishment() {
        val state = _flowState.value
        if (!state.isActive || !state.isInfiniteStreamEnabled || state.isFetchingInfiniteCandidates) return
        val unplayedCount = currentQueue.size - (currentQueueIndex + 1)
        if (unplayedCount < 5) {
            searchAndPopulateCategory(state.selectedCategory, startFreshIfIdle = false)
        }
    }

    /**
     * Called by [PlayerViewModel] whenever the playing track changes.
     */
    fun onTrackChanged(track: Track?) {
        currentTrack = track
        hasTriggeredForCurrentTrack = false
        hasPrebufferedForCurrentTrack = false
        isTriggerArmed = false

        if (track == null) {
            _flowState.update {
                it.copy(
                    currentBpm = 0f,
                    currentKey = null,
                    transitionPhase = TransitionPhase.IDLE,
                    transitionProgress = 0f,
                    plannedTriggerTimeMs = null,
                    incomingDropPointMs = null,
                    beatsUntilTransition = null
                )
            }
            return
        }

        scope.launch(Dispatchers.IO) {
            val info = getOrFetchBeatInfo(track)
            if (info == null || info.bpm <= 0f) {
                AutomixManager.maybeAnalyzeBeat(track, BeatAnalysisPriority.IMMEDIATE)
            }
            val bpm = info?.bpm ?: AutomixManager.automixDebugInfo.value?.outBpm ?: 0f
            val key = if (info?.keyPitchClass != null && info.keyIsMinor != null) {
                CamelotKey.fromPitchClass(info.keyPitchClass, info.keyIsMinor)
            } else null

            withContext(Dispatchers.Main) {
                _flowState.update {
                    it.copy(
                        currentBpm = bpm,
                        currentKey = key,
                        transitionPhase = TransitionPhase.IDLE,
                        transitionProgress = 0f
                    )
                }
                evaluateQueueAsync()
            }
        }
    }

    /**
     * Called by [PlayerViewModel] whenever the playback queue is modified or reordered.
     */
    fun onQueueUpdated(queue: List<Track>, currentIndex: Int) {
        currentQueue = queue
        currentQueueIndex = currentIndex
        evaluateQueueAsync()
    }

    /**
     * Called periodically during playback to track position, animate transition progress,
     * calculate countdown, and autonomously trigger transitions at the exact beat.
     */
    fun onPositionUpdated(positionMs: Long, durationMs: Long) {
        currentPositionMs = positionMs
        currentDurationMs = durationMs

        val state = _flowState.value

        // 1. Seamless phrase repeat check
        if (state.isLoopActive && state.loopRangeMs != null) {
            val (startMs, endMs) = state.loopRangeMs
            if (positionMs >= endMs - 60L) {
                onSeekTo?.invoke(startMs)
            }
        }

        // 2. Real-time Takt & Beat calculations
        var barNumber = 1
        var beatInBar = 1
        var beatInPhrase = 1
        var barInPhrase = 1
        var isDownbeat = false
        var beatProgress = 0f
        var phaseOffsetMs = 0L

        val normBpm = HarmonicMixer.normalizeBpm(state.currentBpm)
        if (normBpm > 0f) {
            val periodMs = 60_000.0 / normBpm
            val currInfo = currentTrack?.let { beatCache[it.id.toString()] }
            // Counting from the classified phrase start, so beat 1 of the display is beat 1 of
            // the music. Counting from the raw beat grid put "the One" on a random beat.
            val phraseAnchor = currInfo?.phraseAnchorMs ?: 0L
            val elapsedSinceAnchor = positionMs - phraseAnchor
            val exactBeats = elapsedSinceAnchor / periodMs
            val totalBeats = kotlin.math.floor(exactBeats).toLong()

            beatInBar = Math.floorMod(totalBeats, 4L).toInt() + 1
            barNumber = Math.floorDiv(totalBeats, 4L).toInt() + 1
            beatInPhrase = Math.floorMod(totalBeats, 16L).toInt() + 1
            barInPhrase = Math.floorMod(Math.floorDiv(totalBeats, 4L), 4L).toInt() + 1
            isDownbeat = (beatInBar == 1)
            beatProgress = (exactBeats - totalBeats).toFloat().coerceIn(0f, 1f)
            // Distance already travelled into the current beat: the incoming deck has to be
            // nudged by exactly this much to land in phase instead of galloping.
            phaseOffsetMs = ((exactBeats - totalBeats) * periodMs).toLong().coerceIn(0L, periodMs.toLong())
        }

        // 3. Transition countdown and autonomous trigger
        val triggerTime = state.plannedTriggerTimeMs
        var beatsLeft: Int? = null
        var barsLeft: Int? = null
        var timeUntilMs: Long? = null

        if (triggerTime != null && triggerTime > 0L && normBpm > 0f) {
            val remainingMs = triggerTime - positionMs
            timeUntilMs = if (remainingMs > 0) remainingMs else 0L
            val periodMs = 60_000.0 / normBpm
            beatsLeft = if (remainingMs > 0) kotlin.math.ceil(remainingMs / periodMs).toInt() else 0
            barsLeft = if (beatsLeft > 0) (beatsLeft + 3) / 4 else 0

            if (state.isActive && beatsLeft in 1..64) {
                AutomixManager.setMixBeatsLeft(beatsLeft)
            } else if (beatsLeft == 0) {
                AutomixManager.setMixBeatsLeft(null)
            }

            // Arming requires having seen the playhead approach the trigger from before it.
            // A trigger point already behind the playhead is a leftover from the previous
            // track's plan, not something to act on.
            if (positionMs < triggerTime) isTriggerArmed = true

            if (state.isActive && state.isAutonomousEnabled) {
                val topMatch = state.nextTrackMatch
                if (topMatch != null && !hasTriggeredForCurrentTrack && isTriggerArmed) {
                    // Prebuffer 8 seconds before trigger time
                    if (remainingMs in 1000L..8500L && !hasPrebufferedForCurrentTrack) {
                        hasPrebufferedForCurrentTrack = true
                        val incomingDrop = state.incomingDropPointMs ?: topMatch.mixInPointMs ?: 0L
                        onRequestPrebuffer?.invoke(topMatch.track, incomingDrop)
                        Log.d(TAG, "Autonomous DJ: Prebuffering top match ${topMatch.track.title} at ${incomingDrop}ms")
                    }

                    // Trigger transition at exact phrase trigger time.
                    // transitionPhase alone is not a sufficient guard here: the autonomous path
                    // goes through onRequestAutonomousTransition, which never moves the phase off
                    // IDLE, so a crossfade already in flight is only visible via MusicManager.
                    val crossfadeInFlight = com.alananasss.kittytune.data.MusicManager.isCrossfadingOut
                    if (positionMs >= triggerTime &&
                        state.transitionPhase == TransitionPhase.IDLE &&
                        !crossfadeInFlight
                    ) {
                        hasTriggeredForCurrentTrack = true
                        isTriggerArmed = false
                        Log.d(TAG, "Autonomous DJ: Auto-triggering transition to ${topMatch.track.title}")

                        // Reorder if necessary to bring top match forward
                        if (state.isAutoReorderEnabled) {
                            val targetIndex = currentQueue.indexOfFirst { it.id == topMatch.track.id }
                            val nextSlot = currentQueueIndex + 1
                            if (targetIndex > nextSlot && targetIndex in currentQueue.indices) {
                                onRequestPromoteTrack?.invoke(targetIndex, nextSlot)
                            }
                        }

                        val incomingStart = state.incomingDropPointMs ?: topMatch.mixInPointMs ?: 0L
                        val transitionDuration = state.plannedTransitionDurationMs
                        val tempoRatio = state.sync.tempoRatio

                        if (state.isAutoStemDropCutEnabled) {
                            com.alananasss.kittytune.data.MusicManager.triggerStemDropCut()
                        }

                        if (onRequestAutonomousTransition != null) {
                            // The autonomous path used to leave transitionPhase at IDLE for the
                            // whole transition, so any UI asking "is a mix running" was told no,
                            // and the IDLE guard above guarded nothing. Drive it here too, and
                            // let the crossfade's own progress clear it.
                            _flowState.update {
                                it.copy(transitionPhase = TransitionPhase.CROSSFADING, transitionProgress = 0f)
                            }
                            trackAutonomousTransition(transitionDuration)
                            onRequestAutonomousTransition?.invoke(
                                topMatch.track,
                                incomingStart,
                                transitionDuration,
                                tempoRatio,
                                phaseOffsetMs
                            )
                        } else {
                            triggerTransition(topMatch.track, transitionDuration)
                        }
                    }
                }
            }
        }

        // Queue top-up is a network call behind a threshold check; it does not need to run
        // 25 times a second.
        val nowMs = android.os.SystemClock.elapsedRealtime()
        if (nowMs - lastReplenishCheckAt > REPLENISH_CHECK_INTERVAL_MS) {
            lastReplenishCheckAt = nowMs
            checkInfiniteReplenishment()
        }

        _flowState.update {
            it.copy(
                currentBar = barNumber,
                beatInBar = beatInBar,
                beatInPhrase = beatInPhrase,
                barInPhrase = barInPhrase,
                isDownbeat = isDownbeat,
                beatProgress = beatProgress,
                sync = it.sync.copy(phaseOffsetMs = phaseOffsetMs),
                beatsUntilTransition = beatsLeft,
                barsUntilTransition = barsLeft,
                timeUntilTransitionMs = timeUntilMs,
                transitionStyle = state.nextTrackMatch?.transitionStyle ?: TransitionStyle.HARMONIC_CLUB_BLEND
            )
        }
    }

    /**
     * Initiates a manual or automated transition to the next or specified track.
     */
    fun triggerTransition(targetTrack: Track? = null, crossfadeDurationMs: Long = 4000L) {
        transitionJob?.cancel()
        val chosenTarget = targetTrack ?: _flowState.value.nextTrackMatch?.track
            ?: currentQueue.getOrNull(currentQueueIndex + 1)

        val state = _flowState.value
        if (state.isLoopExtensionEnabled && currentDurationMs > 0 && currentPositionMs + crossfadeDurationMs >= currentDurationMs) {
            activatePhraseLoopAt(currentPositionMs)
        }

        if (state.isAutoStemDropCutEnabled) {
            com.alananasss.kittytune.data.MusicManager.triggerStemDropCut()
        }

        _flowState.update { it.copy(transitionPhase = TransitionPhase.PREPARING, transitionProgress = 0f) }

        // Every crossfade should be traceable to what asked for it. Without this an extra
        // transition in the log is unattributable, and "where did that come from" is exactly
        // the question that matters when they start stacking.
        Log.d(TAG, "Transition requested (manual/skip) -> ${chosenTarget?.title}")
        onExecuteTransition?.invoke(chosenTarget, crossfadeDurationMs)

        transitionJob = scope.launch {
            _flowState.update { it.copy(transitionPhase = TransitionPhase.CROSSFADING) }
            val startTime = System.currentTimeMillis()
            val totalSteps = 40
            val stepDelay = crossfadeDurationMs / totalSteps

            while (isActive) {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = (elapsed.toFloat() / crossfadeDurationMs).coerceIn(0f, 1f)
                _flowState.update { it.copy(transitionProgress = progress) }

                if (progress >= 1f) break
                delay(stepDelay)
            }

            clearPhraseLoop()
            if (_flowState.value.isAutoStemDropCutEnabled) {
                com.alananasss.kittytune.data.MusicManager.setGlobalStemLevels(
                    _flowState.value.vocalStemLevel,
                    _flowState.value.drumStemLevel,
                    _flowState.value.bassStemLevel
                )
            }
            _flowState.update {
                it.copy(
                    transitionPhase = TransitionPhase.IDLE,
                    transitionProgress = 0f
                )
            }
        }
    }

    /**
     * Holds [TransitionPhase.CROSSFADING] for the length of an autonomous fade and animates
     * [DjFlowState.transitionProgress] alongside it.
     *
     * The audio engine owns the actual crossfade; this only mirrors it for the UI, which is why
     * it is time-based rather than reading back from the player.
     */
    private fun trackAutonomousTransition(durationMs: Long) {
        transitionJob?.cancel()
        transitionJob = scope.launch {
            val startedAt = System.currentTimeMillis()
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startedAt
                val progress = (elapsed.toFloat() / durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
                _flowState.update { it.copy(transitionProgress = progress) }
                if (progress >= 1f) break
                delay(80)
            }
            _flowState.update {
                it.copy(transitionPhase = TransitionPhase.IDLE, transitionProgress = 0f)
            }
        }
    }

    fun cancelTransition() {
        transitionJob?.cancel()
        _flowState.update {
            it.copy(
                transitionPhase = TransitionPhase.IDLE,
                transitionProgress = 0f
            )
        }
    }

    /**
     * Evaluates up to 15 upcoming queue tracks in the background, matching tonality and tempo,
     * and calculating exact cue-points.
     */
    fun evaluateQueueAsync() {
        evaluationJob?.cancel()
        evaluationJob = scope.launch(Dispatchers.IO) {
            val curr = currentTrack ?: return@launch
            val queue = currentQueue
            val currIdx = currentQueueIndex

            if (queue.isEmpty() || currIdx !in queue.indices) return@launch

            // Load current track info
            val currInfo = getOrFetchBeatInfo(curr)
            val currBpm = currInfo?.bpm ?: _flowState.value.currentBpm
            val currKey = if (currInfo?.keyPitchClass != null && currInfo.keyIsMinor != null) {
                CamelotKey.fromPitchClass(currInfo.keyPitchClass, currInfo.keyIsMinor)
            } else _flowState.value.currentKey

            // Candidate tracks: up to 15 upcoming songs in the queue
            val candidates = queue.subList(minOf(currIdx + 1, queue.size), queue.size)
                .take(MAX_CANDIDATES_LOOKAHEAD)

            // Batch-fetch any uncached candidate beat information from Room DB
            val uncachedIds = candidates.map { it.id.toString() }.filter { !beatCache.containsKey(it) }
            if (uncachedIds.isNotEmpty()) {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val batchResults = db.beatInfoDao().getBeatInfoForSongs(uncachedIds)
                    batchResults.forEach { entity ->
                        beatCache[entity.songId] = entity
                    }
                } catch (_: Exception) {}
            }

            var analyzedCount = 0

            val evaluatedCandidates = candidates.map { track ->
                val info = getOrFetchBeatInfo(track)
                if (info != null && info.bpm > 0f) {
                    analyzedCount++
                } else {
                    // Queue for background analysis
                    AutomixManager.maybeAnalyzeBeat(track, BeatAnalysisPriority.LOOKAHEAD)
                }

                val candBpm = info?.bpm ?: 0f
                val candKey = if (info?.keyPitchClass != null && info.keyIsMinor != null) {
                    CamelotKey.fromPitchClass(info.keyPitchClass, info.keyIsMinor)
                } else null

                HarmonicMixer.scoreCandidate(
                    currentBpm = currBpm,
                    currentKey = currKey,
                    candidateTrack = track,
                    candidateBpm = candBpm,
                    candidateKey = candKey,
                    mode = _flowState.value.energyMode,
                    mixInPointMs = info?.mixInPointMs?.takeIf { it > 0L },
                    mixOutPointMs = info?.mixOutPointMs?.takeIf { it > 0L }
                )
            }.sortedByDescending { it.score }

            val topMatch = evaluatedCandidates.firstOrNull()

            // Calculate exact cue-points: Mix-Out & Mix-In drop point
            val dur = if (currentDurationMs > 0) currentDurationMs else (curr.durationMs ?: curr.fullDuration ?: 0L)
            var plannedTrigger: Long? = null
            var incomingDrop: Long? = null
            var plannedDuration: Long = 8000L
            var plannedTempoRatio: Float = 1.0f
            var outgoingGrid: com.alananasss.kittytune.audio.automix.BeatPhaseLock.Grid? = null
            var incomingGrid: com.alananasss.kittytune.audio.automix.BeatPhaseLock.Grid? = null

            val normCurrBpm = HarmonicMixer.normalizeBpm(currBpm)
            if (normCurrBpm > 0f && dur > 10_000L) {
                val periodMs = 60_000.0 / normCurrBpm
                val stylePhraseBeats = topMatch?.transitionStyle?.defaultPhraseBeats ?: 16
                val phraseBeats = 16 // 4 bars per phrase
                val phraseMs = periodMs * phraseBeats
                val transitionDuration = (periodMs * stylePhraseBeats).toLong().coerceIn(4_000L, 20_000L)
                plannedDuration = transitionDuration

                val rawMixOut = currInfo?.mixOutPointMs?.takeIf { it > 0L } ?: (dur - 16_000L)
                val outAnchor = currInfo?.phraseAnchorMs ?: 0L

                // Quantize Mix-Out to an exact 16-beat phrase boundary off the classified downbeat
                val phraseCount = kotlin.math.max(1L, ((rawMixOut - outAnchor) / phraseMs).toLong())
                val mixOutPhraseEndMs = (outAnchor + phraseCount * phraseMs).toLong()

                // Trigger starts exactly transitionDuration before the phrase end, on Beat 1 of a bar
                var triggerTime = mixOutPhraseEndMs - transitionDuration
                if (triggerTime < 2000L) {
                    triggerTime = 2000L
                }
                plannedTrigger = triggerTime.coerceIn(2000L, kotlin.math.max(2000L, dur - 2000L))

                // Incoming track drop & start point calculation
                val targetInfo = topMatch?.let { getOrFetchBeatInfo(it.track) }
                if (targetInfo != null && targetInfo.bpm > 0f) {
                    val normInBpm = HarmonicMixer.normalizeBpm(targetInfo.bpm)
                    if (normInBpm > 0f) {
                        plannedTempoRatio = (normCurrBpm / normInBpm).coerceIn(0.92f, 1.08f)
                    }
                    val inPeriodMs = 60_000.0 / normInBpm
                    val inPhraseMs = inPeriodMs * phraseBeats
                    val inAnchor = targetInfo.phraseAnchorMs
                    val rawMixIn = targetInfo.mixInPointMs?.takeIf { it > 0L } ?: inAnchor
                    val inK = kotlin.math.max(0L, kotlin.math.ceil((rawMixIn - inAnchor) / inPhraseMs).toLong())
                    val phraseDropMs = (inAnchor + inK * inPhraseMs).toLong().coerceAtLeast(0L)

                    // Track B has to reach its drop exactly when the fade ends, so it starts the
                    // fade's length earlier. Backing off by whole beats of B's own grid - not by
                    // the wall-clock duration, which is measured in A's tempo - is what keeps B
                    // on its phrase boundary. After tempo matching both spans are the same time.
                    val backoffMs = (stylePhraseBeats * inPeriodMs).toLong()
                    val startPointMs = if (phraseDropMs >= backoffMs) {
                        phraseDropMs - backoffMs
                    } else {
                        inAnchor
                    }
                    incomingDrop = startPointMs.coerceAtLeast(0L)
                    incomingGrid = com.alananasss.kittytune.audio.automix.BeatPhaseLock.Grid.of(
                        anchorMs = inAnchor,
                        bpm = normInBpm,
                    )
                }
                outgoingGrid = com.alananasss.kittytune.audio.automix.BeatPhaseLock.Grid.of(
                    anchorMs = outAnchor,
                    bpm = normCurrBpm,
                )
            }

            withContext(Dispatchers.Main) {
                _flowState.update {
                    it.copy(
                        currentBpm = currBpm,
                        currentKey = currKey,
                        targetBpm = topMatch?.bpm ?: currBpm,
                        targetKey = topMatch?.key ?: currKey,
                        suggestedMatches = evaluatedCandidates,
                        suggestedNextTracks = evaluatedCandidates.map { m -> m.track },
                        nextTrackMatch = topMatch,
                        transitionStyle = topMatch?.transitionStyle ?: TransitionStyle.HARMONIC_CLUB_BLEND,
                        analyzedCandidateCount = analyzedCount,
                        totalCandidateCount = candidates.size,
                        plannedTriggerTimeMs = plannedTrigger,
                        incomingDropPointMs = incomingDrop,
                        plannedTransitionDurationMs = plannedDuration,
                        sync = it.sync.copy(
                            tempoRatio = plannedTempoRatio,
                            outgoingGrid = outgoingGrid,
                            incomingGrid = incomingGrid,
                        )
                    )
                }
            }
        }
    }

    private suspend fun getOrFetchBeatInfo(track: Track): BeatInfoEntity? {
        val songId = track.id.toString()
        beatCache[songId]?.let { return it }

        return try {
            val db = AppDatabase.getDatabase(context)
            val entity = db.beatInfoDao().getBeatInfo(songId)
            if (entity != null) {
                beatCache[songId] = entity
            }
            entity
        } catch (_: Exception) {
            null
        }
    }
}
