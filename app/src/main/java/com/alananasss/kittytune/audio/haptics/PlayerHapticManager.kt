@file:Suppress("DEPRECATION")

package com.alananasss.kittytune.audio.haptics

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import com.alananasss.kittytune.data.local.PlayerPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * PlayerHapticManager: High-precision music haptics engine for KittyTune.
 * Analyzes audio frequency bands (sub-bass, bass, mids, highs) and generates
 * synchronized haptic vibrations matching kicks, snares, and bass rumbles.
 */
class PlayerHapticManager private constructor(context: Context) {

    private val context: Context = context.applicationContext
    private var attachedViewRef: WeakReference<View>? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile var isHapticsEnabled: Boolean = false
        private set
    @Volatile private var vibrationStrengthMultiplier: Float = 0.8f

    @Volatile var isHapticPlayPauseEnabled: Boolean = true
        private set
    @Volatile var isHapticSeekEnabled: Boolean = true
        private set
    @Volatile var isHapticLikeEnabled: Boolean = true
        private set
    @Volatile var isHapticQueueEnabled: Boolean = true
        private set

    @Volatile var bassBoostMultiplier: Float = 1.0f
    @Volatile var playbackSpeedFactor: Float = 1.0f

    private var prefs: SharedPreferences? = null
    private var cachedVibrator: Vibrator? = null

    private val isOreoOrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    private val isSOrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    private var hasAmplitudeControl = false
    @SuppressLint("ObsoleteSdkInt")
    private var hasViewHaptics = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

    private var deviceMode: HapticMode = HapticMode.VIBRATION_ONLY

    private enum class HapticMode {
        VIBRATION_ONLY, HAPTIC_ONLY, DUAL_MODE
    }

    // Frequency band history
    private val subBassHistory = FloatArray(4)
    private val bassHistory = FloatArray(5)
    private val midHistory = FloatArray(6)
    private var historyIndex = 0

    // Energy tracking
    private var lastMidEnergy = 0f
    private var peakBassLevel = 0.5f
    private var peakMidLevel = 0.3f

    // Timing & beat detection
    private var lastKickTime: Long = 0
    private var lastSnareTime: Long = 0

    // Continuous haptic state
    private var currentBassIntensity = 0f
    private var targetBassIntensity = 0f
    private var globalEnergyAvg = 0.15f
    private var currentMotorState = MotorState.IDLE

    private enum class MotorState {
        IDLE, CONTINUOUS, TRANSIENT, DECAY
    }

    private var lastContinuousAmplitude = 0

    // Cross-OEM profile and controllers
    val deviceProfile: DeviceHapticProfile
    private val binderController = BinderLoadController()
    private val normalizer = AdaptiveAmplitudeNormalizer()
    private val oemMapper = OEMHapticMapper()
    private val modeSelector = ContinuousModeSelector()

    /**
     * Output latency measurement. The processor analyses audio that is still queued, so a
     * vibration fired at analysis time lands ahead of the sound it belongs to.
     */
    private val latencyEstimator = HapticLatencyEstimator()

    /** Contrast shaping, so a drop lands instead of disappearing into constant buzzing. */
    private val dynamics = HapticDynamics()

    /** Vendor-tuned primitives where the motor supports them. Null until a vibrator exists. */
    private var primitivesCache: HapticPrimitivePalette? = null

    private fun primitivePalette(): HapticPrimitivePalette? {
        primitivesCache?.let { return it }
        val v = getVibrator() ?: return null
        return HapticPrimitivePalette(v).also { primitivesCache = it }
    }

    private var lastShapedBeatAt: Long = 0

    /**
     * Analysed beat grid of the playing track, when one exists and was trusted.
     *
     * Null on unanalysed material, which is not a failure state: the energy detector alone is
     * what shipped before, and it still runs. The grid only makes it stricter and better timed.
     */
    @Volatile
    private var beatGrid: BeatHapticGrid? = null

    /** Audible position, kept for asking the grid where we are. */
    @Volatile
    private var audiblePositionMs: Long = 0

    @Volatile
    private var audiblePositionStampMs: Long = 0

    /**
     * Media position of the audio currently being analysed.
     *
     * This, not the audible position, is where a detected transient actually lives: the
     * processor reads audio roughly half a second before anyone hears it. Asking the beat grid
     * about the audible position would check a point ~1.5 beats behind the transient, so the
     * gate would pass and reject essentially at random.
     */
    @Volatile
    private var writtenSinceFlushMs: Long = 0

    /**
     * Media position the processor's frame counter starts from.
     *
     * The counter resets on every flush - a seek, a track change - so on its own it says "how
     * much audio since the flush", not "where in the track". Resuming at 2:00 would leave the
     * counter at zero while the player reports 120000, and every comparison between them would
     * be nonsense. Anchoring it to the position at flush time makes it a media position again.
     */
    @Volatile
    private var writtenOriginMs: Long = 0

    /**
     * True on a 16-beat phrase line, which is where a build-up resolves. Counted from the grid's
     * own anchor so it agrees with the engine's phrase boundaries.
     */
    private fun isPhraseLine(grid: BeatHapticGrid, positionMs: Long): Boolean {
        if (!grid.isUsable) return false
        val elapsed = (positionMs - grid.anchorMs).toDouble()
        val beatIndex = Math.round(elapsed / grid.periodMs)
        return Math.floorMod(beatIndex, BEATS_PER_PHRASE.toLong()) == 0L
    }

    /** Media position of the audio currently being analysed. */
    private fun writtenMediaMs(): Long = writtenOriginMs + writtenSinceFlushMs

    /** Delivers the delayed vibrations. One thread, so the order of transients is preserved. */
    private val hapticScheduler by lazy {
        android.os.HandlerThread("haptic-sched").apply { start() }
    }
    private val hapticHandler by lazy { android.os.Handler(hapticScheduler.looper) }

    private val BEATS_PER_PHRASE = 16

    private var lastLoggedLead: Long = -1

    private var lastTransientTime: Long = 0
    private var lastTriggeredKickTime: Long = 0

    enum class HapticType {
        KICK, SNARE
    }

    private var cachedKickEffect: VibrationEffect? = null
    private var cachedSnareEffect: VibrationEffect? = null

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        when (key) {
            PlayerPreferences.KEY_HAPTICS_CONTRAST -> {
                dynamics.contrast = sharedPreferences.getFloat(
                    PlayerPreferences.KEY_HAPTICS_CONTRAST, HapticDynamics.DEFAULT_CONTRAST
                )
            }
            PlayerPreferences.KEY_HAPTICS_ENABLED -> {
                val enabled = sharedPreferences.getBoolean(PlayerPreferences.KEY_HAPTICS_ENABLED, false)
                setHapticsEnabled(enabled)
            }
            PlayerPreferences.KEY_HAPTICS_STRENGTH -> {
                val strength = sharedPreferences.getFloat(PlayerPreferences.KEY_HAPTICS_STRENGTH, 80f)
                vibrationStrengthMultiplier = (strength / 100f).coerceIn(0f, 1f)
                updateCachedEffects()
            }
            PlayerPreferences.KEY_HAPTICS_PLAY_PAUSE -> {
                isHapticPlayPauseEnabled = sharedPreferences.getBoolean(PlayerPreferences.KEY_HAPTICS_PLAY_PAUSE, true)
            }
            PlayerPreferences.KEY_HAPTICS_SEEK -> {
                isHapticSeekEnabled = sharedPreferences.getBoolean(PlayerPreferences.KEY_HAPTICS_SEEK, true)
            }
            PlayerPreferences.KEY_HAPTICS_LIKE -> {
                isHapticLikeEnabled = sharedPreferences.getBoolean(PlayerPreferences.KEY_HAPTICS_LIKE, true)
            }
            PlayerPreferences.KEY_HAPTICS_QUEUE -> {
                isHapticQueueEnabled = sharedPreferences.getBoolean(PlayerPreferences.KEY_HAPTICS_QUEUE, true)
            }
        }
    }

    init {
        hasAmplitudeControl = detectAmplitudeControl()
        detectDeviceCapabilities()
        deviceProfile = DeviceHapticProfiler().buildProfile()
        updateCachedEffects()
        loadSettingsFromPrefs()
        registerPrefsListener()
    }

    private fun registerPrefsListener() {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs?.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    private fun unregisterPrefsListener() {
        prefs?.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    fun hasHardwareHaptics(): Boolean {
        return getVibrator()?.hasVibrator() == true
    }

    fun supportsAmplitudeControl(): Boolean = hasAmplitudeControl

    fun loadSettingsFromPrefs() {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        isHapticsEnabled = prefs?.getBoolean(PlayerPreferences.KEY_HAPTICS_ENABLED, false) ?: false
        dynamics.contrast =
            prefs?.getFloat(PlayerPreferences.KEY_HAPTICS_CONTRAST, HapticDynamics.DEFAULT_CONTRAST)
                ?: HapticDynamics.DEFAULT_CONTRAST
        vibrationStrengthMultiplier = ((prefs?.getFloat(PlayerPreferences.KEY_HAPTICS_STRENGTH, 80f) ?: 80f) / 100f).coerceIn(0f, 1f)
        isHapticPlayPauseEnabled = prefs?.getBoolean(PlayerPreferences.KEY_HAPTICS_PLAY_PAUSE, true) ?: true
        isHapticSeekEnabled = prefs?.getBoolean(PlayerPreferences.KEY_HAPTICS_SEEK, true) ?: true
        isHapticLikeEnabled = prefs?.getBoolean(PlayerPreferences.KEY_HAPTICS_LIKE, true) ?: true
        isHapticQueueEnabled = prefs?.getBoolean(PlayerPreferences.KEY_HAPTICS_QUEUE, true) ?: true
        updateCachedEffects()
        Log.d(TAG, "Haptics loaded: enabled=$isHapticsEnabled, strength=$vibrationStrengthMultiplier")
    }

    private fun updateCachedEffects() {
        if (!isOreoOrLater) return
        val multiplier = max(0f, min(1f, vibrationStrengthMultiplier))
        if (multiplier <= 0f) return

        try {
            // Kick effect
            val kickBaseAmp = 255
            val kickPeak = normalizer.normalize(kickBaseAmp, deviceProfile, multiplier)
            var kickTimings = longArrayOf(0, 40, 100, 50)
            kickTimings = normalizer.getAdaptedEnvelope(kickTimings, deviceProfile)
            val kickAmps = intArrayOf(0, (kickPeak * 0.9f).toInt(), kickPeak, 0)
            cachedKickEffect = if (deviceProfile.supportsWaveform) {
                VibrationEffect.createWaveform(kickTimings, kickAmps, -1)
            } else {
                VibrationEffect.createOneShot(kickTimings[1] + kickTimings[2], kickPeak)
            }

            // Snare effect
            val snareBaseAmp = 255
            val snareAmp = normalizer.normalize(snareBaseAmp, deviceProfile, multiplier)
            val snareDur = if (deviceProfile.isLikelyERM) 60L else 45L
            cachedSnareEffect = VibrationEffect.createOneShot(snareDur, snareAmp)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache haptic effects: ${e.message}")
        }
    }

    private fun detectDeviceCapabilities() {
        val vib = getVibrator()
        val hasVibrator = vib?.hasVibrator() == true

        deviceMode = when {
            hasVibrator && hasViewHaptics -> HapticMode.DUAL_MODE
            hasVibrator -> HapticMode.VIBRATION_ONLY
            hasViewHaptics -> HapticMode.HAPTIC_ONLY
            else -> HapticMode.VIBRATION_ONLY
        }
    }

    fun attachView(view: View?) {
        this.attachedViewRef = view?.let { WeakReference(it) }
        if (view != null && deviceMode != HapticMode.VIBRATION_ONLY) {
            view.isHapticFeedbackEnabled = true
        }
    }

    fun detachView() {
        attachedViewRef?.clear()
        attachedViewRef = null
    }

    fun setHapticsEnabled(enabled: Boolean) {
        val wasEnabled = this.isHapticsEnabled
        this.isHapticsEnabled = enabled

        if (enabled && !wasEnabled) {
            onPositionDiscontinuity(audiblePositionMs)
        } else if (!enabled && wasEnabled) {
            stopAllHaptics()
        }
    }

    fun setVibrationStrength(strength0to100: Float) {
        vibrationStrengthMultiplier = (strength0to100 / 100f).coerceIn(0f, 1f)
        updateCachedEffects()
    }

    private fun getVibrator(): Vibrator? {
        if (cachedVibrator != null) return cachedVibrator

        cachedVibrator = if (isSOrLater) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        return cachedVibrator
    }

    private fun detectAmplitudeControl(): Boolean {
        val vibrator = getVibrator() ?: return false
        return if (isOreoOrLater) vibrator.hasAmplitudeControl() else false
    }

    fun release() {
        stopAllHaptics()
        unregisterPrefsListener()
        scope.cancel()
    }

    // ============================================================
    // REAL-TIME PCM HAPTIC PROCESSING
    // ============================================================

    fun processPcmHaptics(subBass: Float, bass: Float, mid: Float, high: Float) {
        if (!isHapticsEnabled) return

        scope.launch {
            val frameEnergy = (subBass + bass + mid + high) / 4f
            if (frameEnergy > 0.01f) {
                globalEnergyAvg = globalEnergyAvg * (1 - GAIN_ADJUST_SPEED) + frameEnergy * GAIN_ADJUST_SPEED
            }

            val safeAvg = max(0.08f, globalEnergyAvg)
            var dynamicGain = 0.40f / safeAvg
            dynamicGain = dynamicGain.coerceIn(1.5f, 6.0f)

            val curSubBass = subBass * bassBoostMultiplier * dynamicGain
            val curBass = bass * (bassBoostMultiplier * 0.8f) * dynamicGain
            val curMid = mid * dynamicGain
            val vocalEnergy = (curBass * 0.4f + curMid * 0.6f)

            updateHistory(subBassHistory, curSubBass)
            updateHistory(bassHistory, curBass)
            updateHistory(midHistory, curMid)
            historyIndex++

            val avgSubBass = fastAverage(subBassHistory)
            val avgMid = fastAverage(midHistory)

            peakBassLevel = max(curSubBass, peakBassLevel * 0.985f)
            peakMidLevel = max(curMid, peakMidLevel * 0.985f)

            val now = System.currentTimeMillis()
            val adjustedKickInterval = (MIN_KICK_INTERVAL / playbackSpeedFactor.coerceAtLeast(0.25f)).toLong()
            val adjustedSnareInterval = (MIN_SNARE_INTERVAL / playbackSpeedFactor.coerceAtLeast(0.25f)).toLong()

            val dynamicPeak = max(peakBassLevel, 0.1f)
            val combinedHapticSignal = if (curSubBass > dynamicPeak * 0.4f) {
                curSubBass + (high * 0.15f)
            } else {
                curSubBass + (vocalEnergy * 0.45f) + (high * 0.10f)
            }

            var normalizedSignal = min(1f, combinedHapticSignal / dynamicPeak)
            if (normalizedSignal < 0.55f) {
                normalizedSignal = 0f
                if (currentMotorState == MotorState.CONTINUOUS) {
                    targetBassIntensity = 0f
                }
            }

            targetBassIntensity = normalizedSignal.pow(3.0f).coerceIn(0f, 1f)

            if (targetBassIntensity > currentBassIntensity) {
                currentBassIntensity = currentBassIntensity * (1 - ENVELOPE_ATTACK) + targetBassIntensity * ENVELOPE_ATTACK
            } else if (targetBassIntensity == 0f && currentBassIntensity > 0f) {
                currentBassIntensity *= 0.92f
                if (currentBassIntensity < 0.01f) currentBassIntensity = 0f
            } else {
                currentBassIntensity = currentBassIntensity * (1 - ENVELOPE_DECAY) + targetBassIntensity * ENVELOPE_DECAY
            }

            if (binderController.canUpdate(deviceProfile, now)) {
                updateContinuousBassHaptic(currentBassIntensity)
            }

            // Transient kick detection
            val subBassRise = curSubBass - avgSubBass
            val isKick = curSubBass > avgSubBass * 1.15f &&
                    subBassRise > 0.015f &&
                    (now - lastKickTime) > adjustedKickInterval

            if (isKick) {
                lastKickTime = now
                val kickIntensity = computeIntensity(curSubBass, avgSubBass, peakBassLevel)
                triggerTransientHaptic(max(0.9f, kickIntensity * 1.5f), HapticType.KICK)
            }

            // Transient snare detection
            val midRise = curMid - lastMidEnergy
            val isSnare = curMid > avgMid * 1.3f &&
                    midRise > (avgMid * 0.2f) &&
                    midRise > 0.03f &&
                    curSubBass < avgSubBass * 1.6f &&
                    (now - lastSnareTime) > adjustedSnareInterval

            if (isSnare) {
                lastSnareTime = now
                val snareIntensity = computeIntensity(curMid, avgMid, peakMidLevel)
                triggerTransientHaptic(min(1.0f, snareIntensity * 2.0f), HapticType.SNARE)
            }

            lastMidEnergy = curMid
        }
    }

    private fun updateHistory(history: FloatArray, value: Float) {
        history[historyIndex % history.size] = value
    }

    private fun fastAverage(array: FloatArray): Float {
        var sum = 0f
        for (i in array.indices) sum += array[i]
        return sum / array.size
    }

    private fun computeIntensity(energy: Float, avg: Float, peak: Float): Float {
        if (peak <= avg) return 0f
        val rawIntensity = (energy - avg) / (peak - avg + 0.01f)
        return min(1f, rawIntensity.toDouble().pow(0.75).toFloat() * 1.15f)
    }

    private fun updateContinuousBassHaptic(intensity: Float) {
        if (!isHapticsEnabled) return

        val now = System.currentTimeMillis()
        if (now - lastTransientTime < TRANSIENT_LOCKOUT_MS) return

        val vibrator = getVibrator() ?: return
        if (!vibrator.hasVibrator()) return

        val userMultiplier = max(0f, min(1f, vibrationStrengthMultiplier))
        if (userMultiplier <= 0f) return

        // With a known beat grid the drone is redundant and actively harmful: it is the constant
        // vibration the drop has to stand out from. The impact setting decides how much of it
        // survives - see ContinuousHapticPolicy.
        val rumble = ContinuousHapticPolicy.rumbleScale(
            hasTrustedGrid = beatGrid?.isUsable == true,
            contrast = dynamics.contrast,
        )
        if (rumble <= 0f) {
            if (currentMotorState == MotorState.CONTINUOUS) {
                currentMotorState = MotorState.DECAY
                getVibrator()?.cancel()
            }
            return
        }

        val clampedIntensity = (intensity * 3.0f * rumble).coerceIn(0f, 1f)

        if (isOreoOrLater && hasAmplitudeControl) {
            val targetAmplitudeBase = (clampedIntensity * CONTINUOUS_MAX_AMP).toInt()
            val targetAmplitude = normalizer.normalize(targetAmplitudeBase, deviceProfile, userMultiplier)

            if (targetAmplitude < deviceProfile.minEffectiveAmplitude) {
                if (currentMotorState == MotorState.CONTINUOUS) {
                    currentMotorState = MotorState.DECAY
                }
                currentMotorState = MotorState.IDLE
                lastContinuousAmplitude = 0
                return
            }

            if (currentMotorState == MotorState.CONTINUOUS &&
                abs(targetAmplitude - lastContinuousAmplitude) < DELTA_AMP_THRESHOLD) {
                return
            }

            modeSelector.executeContinuous(vibrator, targetAmplitude, deviceProfile)
            currentMotorState = MotorState.CONTINUOUS
            lastContinuousAmplitude = targetAmplitude
        } else {
            if (clampedIntensity > 0.2f && currentMotorState != MotorState.CONTINUOUS) {
                try {
                    if (isOreoOrLater) {
                        vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        vibrator.vibrate(80)
                    }
                    currentMotorState = MotorState.CONTINUOUS
                } catch (_: Exception) {}
            } else if (clampedIntensity == 0f) {
                currentMotorState = MotorState.IDLE
            }
        }
    }

    private fun stopContinuousHaptic() {
        if (currentMotorState == MotorState.IDLE) return
        val vibrator = getVibrator() ?: return
        try {
            vibrator.cancel()
            currentMotorState = MotorState.IDLE
            lastContinuousAmplitude = 0
        } catch (e: Exception) {
            Log.w(TAG, "Stop continuous haptic error: ${e.message}")
        }
    }

    /** Audio-thread hook: how much audio has been written into the sink. */
    fun onAudioWritten(positionMs: Long) {
        writtenSinceFlushMs = positionMs
        latencyEstimator.onAudioWritten(writtenMediaMs())
    }

    /** Main-thread hook: the player's audible position, which already includes sink latency. */
    fun onAudiblePosition(positionMs: Long) {
        audiblePositionMs = positionMs
        audiblePositionStampMs = System.currentTimeMillis()
        latencyEstimator.onAudiblePosition(positionMs, audiblePositionStampMs)
    }

    /**
     * Hands the analysed beat grid to the haptics.
     *
     * @param beatsPerBar 0 when the downbeat classification was not trusted, so bar position is
     *   never claimed on evidence that does not support it.
     */
    fun onTrackBeatGrid(bpm: Float, anchorMs: Long, beatsPerBar: Int) {
        beatGrid = if (bpm > 1f) {
            BeatHapticGrid(periodMs = 60_000.0 / bpm, anchorMs = anchorMs, beatsPerBar = beatsPerBar)
        } else null
    }

    fun clearTrackBeatGrid() {
        beatGrid = null
        // A new track's loudness says nothing about the last one's.
        dynamics.reset()
    }

    /** 0 = raw intensity as before, 1 = full contrast. Persisted by the settings screen. */
    fun setHapticContrast(value: Float) {
        dynamics.contrast = value.coerceIn(0f, 1f)
    }

    fun hapticContrast(): Float = dynamics.contrast

    /** True when the motor can play vendor-tuned primitives rather than raw amplitude. */
    fun supportsPrimitives(): Boolean = primitivePalette()?.isSupported == true


    /**
     * Resets the alignment between written PCM audio frames and audible player position
     * on a seek, track change, or stream discontinuity.
     */
    fun onPositionDiscontinuity(mediaPositionMs: Long) {
        val target = mediaPositionMs.coerceAtLeast(0L)
        writtenOriginMs = target
        writtenSinceFlushMs = 0
        audiblePositionMs = target
        audiblePositionStampMs = System.currentTimeMillis()
        latencyEstimator.onDiscontinuity(target)
        hapticHandler.removeCallbacksAndMessages(null)
    }

    /** A seek or track change flushes the ExoPlayer audio sink. */
    fun onAudioPipelineReset() {
        writtenSinceFlushMs = 0
        hapticHandler.removeCallbacksAndMessages(null)
    }

    /** Current latency compensation in ms, for the settings screen to show. */
    fun currentLatencyCompensationMs(): Long = latencyEstimator.currentLeadMs()

    /**
     * Fires a transient, delayed so it coincides with the sound that caused it.
     *
     * The guards below run immediately - they are rate limits on detection, which happens now -
     * while only the vibration itself is postponed.
     */
    fun triggerTransientHaptic(intensity: Float, type: HapticType) {
        // Timing from the analysis, dynamics from the signal. The energy detector says there is
        // something here; the grid says whether a beat is actually due. A transient that lands
        // between beats is a fill, a vocal or a sample edge - real in the audio, but not what a
        // listener is tapping their foot to.
        val leadForLog = latencyEstimator.currentLeadMs()
        if (leadForLog != lastLoggedLead) {
            lastLoggedLead = leadForLog
            android.util.Log.d(
                "HapticLatency",
                "lead=$leadForLog ms  grid=${if (beatGrid != null) "yes" else "no"}  written=${writtenMediaMs()}"
            )
        }

        var shaped = intensity
        val grid = beatGrid
        val at = writtenMediaMs()
        if (grid != null && grid.isUsable) {
            if (at > 0L) {
                if (type == HapticType.KICK && !grid.isOnBeat(at)) return
                shaped = (intensity * grid.emphasisAt(at)).coerceIn(0f, 1f)
            }
        }

        // Contrast last, so it shapes what actually survived gating and emphasis.
        val nowForDynamics = System.currentTimeMillis()
        val sinceLastBeat = if (lastShapedBeatAt == 0L) 0L else nowForDynamics - lastShapedBeatAt
        lastShapedBeatAt = nowForDynamics
        shaped = dynamics.shape(shaped, sinceLastBeat)

        // Select the gesture at detection time using the current 'at' and dynamics trend
        val gesture = HapticGestureSelector.select(
            isKick = type == HapticType.KICK,
            isDownbeat = grid?.isDownbeat(at),
            intensity = shaped,
            trend = dynamics.trend(),
            atPhraseBoundary = grid?.let { g ->
                g.isOnBeat(at) && g.beatInBar(at) == 1 && isPhraseLine(g, at)
            } ?: false,
        )

        val lead = latencyEstimator.leadMs(System.currentTimeMillis())
        if (lead <= 0L) {
            triggerTransientHapticNow(shaped, type, gesture)
            return
        }
        hapticHandler.postDelayed({ triggerTransientHapticNow(shaped, type, gesture) }, lead)
    }

    private fun triggerTransientHapticNow(intensity: Float, type: HapticType, gesture: HapticGesture) {
        val now = System.currentTimeMillis()
        if (type == HapticType.KICK && now - lastTriggeredKickTime < 110) return
        if (type == HapticType.KICK) lastTriggeredKickTime = now

        lastTransientTime = now
        currentMotorState = MotorState.TRANSIENT

        if (!isHapticsEnabled) return

        val userMultiplier = max(0f, min(1f, vibrationStrengthMultiplier))
        if (userMultiplier <= 0f) return

        when (deviceMode) {
            HapticMode.DUAL_MODE -> {
                performVibrationTransient(type, intensity, userMultiplier, gesture)
                attachedViewRef?.get()?.let { v ->
                    if (v.isAttachedToWindow) {
                        v.post { performAndroidHaptic(v, type) }
                    }
                }
            }
            HapticMode.HAPTIC_ONLY -> {
                attachedViewRef?.get()?.let { v ->
                    if (v.isAttachedToWindow) {
                        v.post { performAndroidHaptic(v, type) }
                    }
                }
            }
            HapticMode.VIBRATION_ONLY -> performVibrationTransient(type, intensity, userMultiplier, gesture)
        }
    }

    private fun performAndroidHaptic(view: View, type: HapticType) {
        if (!view.isHapticFeedbackEnabled) view.isHapticFeedbackEnabled = true
        try {
            val constant = when (type) {
                HapticType.KICK -> oemMapper.getKickConstant()
                HapticType.SNARE -> oemMapper.getSnareConstant()
            }
            view.performHapticFeedback(constant, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
        } catch (_: Exception) {}
    }

    private fun performVibrationTransient(type: HapticType, intensity: Float, multiplier: Float, gesture: HapticGesture) {
        binderController.markTransient(System.currentTimeMillis())
        val vibrator = getVibrator() ?: return
        if (!vibrator.hasVibrator()) return

        // Vendor-tuned primitives first. The motor's own model of a thump beats a rectangular
        // amplitude envelope, and the scale argument makes the strength actually follow the beat.
        val grid = beatGrid
        val primitive = primitivePalette()?.build(
            gesture = gesture,
            amplitude = intensity * multiplier,
            beatPeriodMs = grid?.periodMs ?: 0.0,
        )
        if (primitive != null) {
            try {
                vibrator.cancel()
                vibrator.vibrate(primitive)
                return
            } catch (_: Exception) {
                // Fall through to the amplitude path below.
            }
        }

        if (isOreoOrLater && hasAmplitudeControl) {
            try {
                val effect = when (type) {
                    // Deliberately not the cached effect: a cached waveform has one fixed
                    // amplitude, so every kick came out identical no matter how hard it hit.
                    // Building per beat is what makes the dynamics audible at all.
                    HapticType.KICK -> run {
                        val targetBaseAmp = min(255f, 180f + (75f * intensity)).toInt()
                        val peakAmp = normalizer.normalize(targetBaseAmp, deviceProfile, multiplier)
                        var timings = longArrayOf(0, 25, 60, 30)
                        timings = normalizer.getAdaptedEnvelope(timings, deviceProfile)
                        val amps = intArrayOf(0, (peakAmp * 0.6f).toInt(), peakAmp, 0)
                        if (deviceProfile.supportsWaveform) {
                            VibrationEffect.createWaveform(timings, amps, -1)
                        } else {
                            VibrationEffect.createOneShot(timings[1] + timings[2], peakAmp)
                        }
                    }
                    HapticType.SNARE -> run {
                        val targetBaseAmp = min(200f, 140f + (60f * intensity)).toInt()
                        val targetAmp = normalizer.normalize(targetBaseAmp, deviceProfile, multiplier)
                        val duration = if (deviceProfile.isLikelyERM) 40L else 25L
                        VibrationEffect.createOneShot(duration, targetAmp)
                    }
                }
                vibrator.vibrate(effect)
            } catch (e: Exception) {
                Log.w(TAG, "Vibration transient error: ${e.message}")
            }
        } else {
            vibrator.vibrate(if (type == HapticType.KICK) 45L else 20L)
        }
    }

    fun stopAllHaptics() {
        stopContinuousHaptic()
        currentBassIntensity = 0f
        targetBassIntensity = 0f
    }

    fun triggerTestVibration(strength0to100: Float) {
        val vibrator = getVibrator() ?: return
        if (!vibrator.hasVibrator()) return
        val mult = (strength0to100 / 100f).coerceIn(0.1f, 1f)
        try {
            if (isOreoOrLater && hasAmplitudeControl) {
                val amp = normalizer.normalize(230, deviceProfile, mult)
                vibrator.vibrate(VibrationEffect.createOneShot(50L, amp))
            } else if (isOreoOrLater) {
                vibrator.vibrate(VibrationEffect.createOneShot(50L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(50L)
            }
        } catch (_: Exception) {}
    }

    // ============================================================
    // CROSS-OEM HARDWARE PROFILES
    // ============================================================

    class DeviceHapticProfile {
        var supportsWaveform = true
        var amplitudeLinear = true
        var minEffectiveAmplitude = 35
        var maxStableAmplitude = 255
        var prefersOneShotContinuous = false
        var binderSafeUpdateInterval = 90L
        var isLikelyERM = false
    }

    private class DeviceHapticProfiler {
        fun buildProfile(): DeviceHapticProfile {
            val p = DeviceHapticProfile()
            val mfg = Build.MANUFACTURER.lowercase()
            val model = Build.MODEL.lowercase()

            when {
                mfg.contains("motorola") || mfg.contains("infinix") || mfg.contains("tecno") || mfg.contains("nokia") -> {
                    p.minEffectiveAmplitude = 60
                    p.isLikelyERM = true
                    p.amplitudeLinear = false
                    p.prefersOneShotContinuous = true
                    p.binderSafeUpdateInterval = 120L
                }
                mfg.contains("samsung") -> {
                    p.minEffectiveAmplitude = 40
                    p.binderSafeUpdateInterval = 100L
                    p.isLikelyERM = model.contains("sm-a") || model.contains("sm-m")
                    p.prefersOneShotContinuous = p.isLikelyERM
                }
                mfg.contains("xiaomi") || mfg.contains("redmi") || mfg.contains("poco") -> {
                    p.minEffectiveAmplitude = 70
                    p.binderSafeUpdateInterval = 110L
                    p.prefersOneShotContinuous = false
                }
                mfg.contains("oneplus") || mfg.contains("oppo") || mfg.contains("realme") || mfg.contains("vivo") -> {
                    p.minEffectiveAmplitude = 25
                    p.binderSafeUpdateInterval = 80L
                    p.isLikelyERM = false
                }
                mfg.contains("google") || mfg.contains("pixel") -> {
                    p.minEffectiveAmplitude = 30
                    p.binderSafeUpdateInterval = 85L
                    p.isLikelyERM = false
                }
                mfg.contains("sony") || mfg.contains("asus") -> {
                    p.minEffectiveAmplitude = 35
                    p.binderSafeUpdateInterval = 90L
                }
            }
            return p
        }
    }

    private class BinderLoadController {
        private var lastUpdate: Long = 0
        fun canUpdate(p: DeviceHapticProfile, now: Long): Boolean {
            if (now - lastUpdate > p.binderSafeUpdateInterval) {
                lastUpdate = now
                return true
            }
            return false
        }
        fun markTransient(now: Long) {
            lastUpdate = now
        }
    }

    private class AdaptiveAmplitudeNormalizer {
        fun normalize(baseAmp: Int, p: DeviceHapticProfile, mult: Float): Int {
            var amp = (baseAmp * mult).toInt()
            amp = max(p.minEffectiveAmplitude, min(p.maxStableAmplitude, amp))
            if (!p.amplitudeLinear) {
                amp = (amp * 1.15f).toInt().coerceAtMost(255)
            }
            return amp
        }
        fun getAdaptedEnvelope(timings: LongArray, p: DeviceHapticProfile): LongArray {
            if (p.isLikelyERM) {
                return longArrayOf(timings[0], timings[1] * 2, timings[2] * 2, timings[3] * 2)
            }
            return timings
        }
    }

    private class OEMHapticMapper {
        fun getKickConstant(): Int {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.REJECT
            } else {
                HapticFeedbackConstants.LONG_PRESS
            }
        }
        @SuppressLint("ObsoleteSdkInt")
        fun getSnareConstant(): Int {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                HapticFeedbackConstants.CONTEXT_CLICK
            } else {
                HapticFeedbackConstants.KEYBOARD_PRESS
            }
        }
    }

    private class ContinuousModeSelector {
        fun executeContinuous(vib: Vibrator, amp: Int, p: DeviceHapticProfile) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val duration = if (p.prefersOneShotContinuous) p.binderSafeUpdateInterval + 50 else 100L
                    vib.vibrate(VibrationEffect.createOneShot(duration, amp))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Continuous haptic execution error: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "PlayerHapticManager"
        private const val PREFS_NAME = "player_state"

        private const val MIN_KICK_INTERVAL = 200L
        private const val MIN_SNARE_INTERVAL = 150L
        private const val GAIN_ADJUST_SPEED = 0.005f
        private const val ENVELOPE_ATTACK = 0.35f
        private const val ENVELOPE_DECAY = 0.15f
        private const val CONTINUOUS_MAX_AMP = 130
        private const val DELTA_AMP_THRESHOLD = 15
        private const val TRANSIENT_LOCKOUT_MS = 120L

        @Volatile
        private var INSTANCE: PlayerHapticManager? = null

        fun getInstance(context: Context): PlayerHapticManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlayerHapticManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun triggerInteractionHaptic(context: Context, actionKey: String) {
            val instance = getInstance(context)
            if (!instance.isHapticsEnabled) return

            val isActionEnabled = when (actionKey) {
                PlayerPreferences.KEY_HAPTICS_PLAY_PAUSE -> instance.isHapticPlayPauseEnabled
                PlayerPreferences.KEY_HAPTICS_SEEK -> instance.isHapticSeekEnabled
                PlayerPreferences.KEY_HAPTICS_LIKE -> instance.isHapticLikeEnabled
                PlayerPreferences.KEY_HAPTICS_QUEUE -> instance.isHapticQueueEnabled
                else -> true
            }
            if (!isActionEnabled) return

            val strength = instance.vibrationStrengthMultiplier
            if (strength <= 0f) return

            val vibrator = instance.getVibrator() ?: return
            if (!vibrator.hasVibrator()) return

            try {
                val amp = (strength * 255).toInt().coerceIn(1, 255)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val duration = when (actionKey) {
                        PlayerPreferences.KEY_HAPTICS_PLAY_PAUSE -> 35L
                        PlayerPreferences.KEY_HAPTICS_SEEK -> 18L
                        PlayerPreferences.KEY_HAPTICS_LIKE -> 45L
                        PlayerPreferences.KEY_HAPTICS_QUEUE -> 25L
                        else -> 30L
                    }
                    vibrator.vibrate(VibrationEffect.createOneShot(duration, amp))
                } else {
                    vibrator.vibrate(30L)
                }
            } catch (_: Exception) {}
        }
    }
}
