package com.alananasss.kittytune.audio.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.media3.common.C
import com.alananasss.kittytune.data.local.PlayerPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class UsbAudioStatus(
    val usbDacConnected: Boolean = false,
    val usbAttached: Boolean = usbDacConnected,
    val deviceName: String? = null,
    val bitPerfectActive: Boolean = false,
    val activeSampleRate: Int = 0,
    val sampleRateHz: Int = activeSampleRate,
    val activeEncoding: Int = 0,
    val encodingLabel: String? = null,
    val volumeForcedToMax: Boolean = false,
    val reason: String = "idle",
    val failureReason: String = reason,
    val trackVerified: Boolean = false
)

object UsbAudioManager {

    private const val TAG = "UsbAudioManager"

    private var appContext: Context? = null
    private var audioManager: AudioManager? = null
    private var prefs: PlayerPreferences? = null

    private var lastSampleRate: Int = 0
    private var lastChannelCount: Int = 2
    private var lastEncoding: Int = AudioFormat.ENCODING_PCM_16BIT

    @Volatile
    var containerUpgradeTarget: Int = 0
        private set

    private var reconfigureAttempts: Int = 0

    // Identity of the track the counters above belong to. onTracksChanged fires again after
    // every stop()/prepare() of our own sink rebuild, so the counters must only be reset when
    // the TRACK changes - never on a mere re-announcement of the same track (that reset used
    // to make the reconfigure loop unbounded).
    private var currentTrackKey: String? = null

    // True once rate/channels were corrected from the real AudioTrack for [currentTrackKey].
    private var trackFormatAdopted: Boolean = false

    @Volatile
    private var mismatchLatched: Boolean = false

    var onReconfigureRequested: (() -> Unit)? = null
    var onPreferredDeviceChanged: ((AudioDeviceInfo?) -> Unit)? = null
    var onUsbDacDisconnectedWhileActive: (() -> Unit)? = null

    private var mixerController: UsbBitPerfectController? = null

    private var volumeForcedToMax: Boolean = false
    private var savedVolumeIndex: Int = -1

    val mediaAudioAttributes: AudioAttributes by lazy {
        try {
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        } catch (_: Throwable) {
            AudioAttributes.Builder().build()
        }
    }

    private val _status = MutableStateFlow(UsbAudioStatus())
    val status: StateFlow<UsbAudioStatus> = _status.asStateFlow()

    @Volatile
    private var bypassEffectsCache: Boolean = false

    private fun publishStatus(s: UsbAudioStatus) {
        bypassEffectsCache = s.bitPerfectActive && prefs?.getUsbBitPerfect() == true
        _status.value = s
    }

    private val derivedScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val isEffectsSuspendedByBitPerfect: StateFlow<Boolean> =
        status
            .map { it.bitPerfectActive && prefs?.getUsbBitPerfect() == true }
            .distinctUntilChanged()
            .stateIn(derivedScope, SharingStarted.Eagerly, false)

    val automixConflictsWithBitPerfect: StateFlow<Boolean> =
        status
            .map { s -> s.bitPerfectActive && prefs?.getAutomixEnabled() == true }
            .distinctUntilChanged()
            .stateIn(derivedScope, SharingStarted.Eagerly, false)

    fun badgeText(): String {
        val s = _status.value
        if (!s.bitPerfectActive) return ""
        val rate = if (s.activeSampleRate > 0) {
            val khz = s.activeSampleRate / 1000.0
            khz.toString().removeSuffix(".0") + " kHz"
        } else null
        return listOfNotNull("Bit-Perfect", rate, s.encodingLabel?.takeIf { it.isNotBlank() })
            .joinToString(" • ")
    }

    val shouldBypassPcmEffects: Boolean
        get() = bypassEffectsCache

    private val emergencyDisconnectReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    Log.w(TAG, "USB device detached: $action")
                    handleDeviceDetached()
                }
                // BECOMING_NOISY fires for ANY wired-headphone unplug. Only treat it as a DAC
                // loss when the DAC is really gone; otherwise the user pulled some other
                // plug and we must not drop the bit-perfect mixer (ExoPlayer's own
                // setHandleAudioBecomingNoisy already pauses playback).
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    if (findUsbDac() == null) {
                        Log.w(TAG, "Becoming noisy and no USB DAC left: $action")
                        handleDeviceDetached()
                    } else {
                        Log.i(TAG, "Becoming noisy, USB DAC still present: ignored")
                    }
                }
            }
        }
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            if (addedDevices?.any { isUsbAudioDevice(it) } == true) {
                resetMismatchState()
                onPreferredDeviceChanged?.invoke(findUsbDac())
                refresh()
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            if (removedDevices?.any { isUsbAudioDevice(it) } == true) {
                handleDeviceDetached()
            }
        }
    }

    private fun resetMismatchState() {
        reconfigureAttempts = 0
        mismatchLatched = false
        trackFormatAdopted = false
    }

    private fun handleDeviceDetached() {
        val wasActive = _status.value.bitPerfectActive
        resetMismatchState()
        restoreMediaVolume()
        clearApplied()
        onPreferredDeviceChanged?.invoke(null)
        if (wasActive) {
            onUsbDacDisconnectedWhileActive?.invoke()
        }
        refresh()
    }

    fun init(context: Context, preferences: PlayerPreferences) {
        appContext = context.applicationContext
        audioManager = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        mixerController = UsbBitPerfectController(context)
        prefs = preferences
        recoverVolumeAfterAbnormalExit()

        audioManager?.registerAudioDeviceCallback(deviceCallback, null)

        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        context.registerReceiver(emergencyDisconnectReceiver, filter)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Api34Impl.registerMixerListener(appContext!!, audioManager) {
                clearApplied()
                refresh()
            }
        }
        refresh()
    }

    private fun recoverVolumeAfterAbnormalExit() {
        val saved = prefs?.getUsbSavedVolumeIndex() ?: return
        if (saved < 0) return
        val am = audioManager ?: return
        try {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, saved.coerceIn(0, max), 0)
        } catch (_: Exception) {}
        prefs?.clearUsbSavedVolumeIndex()
    }

    fun onSettingsChanged() {
        resetMismatchState()
        refresh()
    }

    private fun isUsbAudioDevice(device: AudioDeviceInfo): Boolean {
        if (!device.isSink) return false
        return device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            device.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
    }

    fun findUsbDac(): AudioDeviceInfo? {
        val am = audioManager ?: return null
        // sortedBy(id): with two USB sinks plugged in, pick the same one every time.
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { isUsbAudioDevice(it) }
            .minByOrNull { it.id }
    }

    fun mapMedia3EncodingToAudioFormat(media3Encoding: Int): Int {
        return when (media3Encoding) {
            C.ENCODING_PCM_16BIT -> AudioFormat.ENCODING_PCM_16BIT
            C.ENCODING_PCM_24BIT,
            C.ENCODING_PCM_24BIT_BIG_ENDIAN -> AudioFormat.ENCODING_PCM_24BIT_PACKED
            C.ENCODING_PCM_32BIT,
            C.ENCODING_PCM_32BIT_BIG_ENDIAN -> AudioFormat.ENCODING_PCM_32BIT
            C.ENCODING_PCM_FLOAT -> AudioFormat.ENCODING_PCM_FLOAT
            else -> AudioFormat.ENCODING_PCM_16BIT
        }
    }

    fun mapAudioFormatToMedia3(audioFormatEncoding: Int): Int {
        return when (audioFormatEncoding) {
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> C.ENCODING_PCM_24BIT
            AudioFormat.ENCODING_PCM_32BIT -> C.ENCODING_PCM_32BIT
            AudioFormat.ENCODING_PCM_FLOAT -> C.ENCODING_PCM_FLOAT
            else -> C.ENCODING_PCM_16BIT
        }
    }

    fun onPlaybackFormatChanged(sampleRate: Int, channelCount: Int) {
        onPlaybackFormatChanged(sampleRate, channelCount, C.ENCODING_PCM_16BIT)
    }

    // @param trackKey stable id of the track (mediaId). When the same key is announced again
    //   (our own stop()/prepare() re-fires onTracksChanged) the attempt counter and the
    //   values already corrected from the real AudioTrack are kept. null = legacy behaviour.
    fun onPlaybackFormatChanged(
        sampleRate: Int,
        channelCount: Int,
        media3Encoding: Int,
        trackKey: String? = null,
    ) {
        val sameTrack = trackKey != null && trackKey == currentTrackKey
        if (sameTrack && trackFormatAdopted) return

        if (!sameTrack) {
            currentTrackKey = trackKey
            trackFormatAdopted = false
            reconfigureAttempts = 0
            mismatchLatched = false
        }
        lastSampleRate = sampleRate
        lastChannelCount = channelCount
        lastEncoding = mapMedia3EncodingToAudioFormat(media3Encoding)
        refresh()
    }

    fun onAudioTrackInitialized(sampleRate: Int, channelMask: Int, media3Encoding: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val mixerFmt = Api34Impl.getAppliedFormat() ?: return

        // CRITICAL FIX: convert media3Encoding into the AudioFormat encoding before comparing
        val trackAudioFormatEncoding = mapMedia3EncodingToAudioFormat(media3Encoding)

        val matches = mixerFmt.sampleRate == sampleRate &&
            mixerFmt.channelMask == channelMask &&
            mixerFmt.encoding == trackAudioFormatEncoding

        Log.i(
            TAG,
            "AudioTrack ${sampleRate}Hz/ch=$channelMask/enc=$trackAudioFormatEncoding vs mixer " +
                "${mixerFmt.sampleRate}Hz/ch=${mixerFmt.channelMask}/enc=${mixerFmt.encoding} -> match=$matches"
        )

        if (matches) {
            mismatchLatched = false
            val s = _status.value
            if (s.bitPerfectActive && !s.trackVerified) publishStatus(s.copy(trackVerified = true))
            return
        }

        if (mismatchLatched) return // already settled for this track

        // The Tracks format (container) can differ from what the decoder really delivers
        // (HE-AAC/SBR doubles the rate). Trust the real AudioTrack: adopt its rate/channels,
        // re-select the mixer and rebuild the sink ONCE per track. The rebuild is also what
        // fixes the first-track race (containerUpgradeTarget is published after the sink was
        // configured). The encoding is NOT adopted: the track's encoding is already the
        // post-container-upgrade one.
        val realChannels = Integer.bitCount(channelMask).coerceAtLeast(1)
        if (reconfigureAttempts < 1) {
            reconfigureAttempts++
            lastSampleRate = sampleRate
            lastChannelCount = realChannels
            trackFormatAdopted = true
            refresh()
            onReconfigureRequested?.invoke()
            return
        }

        // Still no match after the one correction: give up for this track. Drop the preferred
        // mixer (honest fallback to the normal mixer) and stay latched until the track
        // changes, so nothing re-applies the mixer and restarts the loop.
        latchMismatch()
    }

    private fun latchMismatch() {
        mismatchLatched = true
        trackFormatAdopted = true
        clearApplied()
        restoreMediaVolume()
        val s = _status.value
        publishStatus(
            s.copy(
                bitPerfectActive = false,
                trackVerified = false,
                reason = "track_format_mismatch",
                failureReason = "track_format_mismatch"
            )
        )
    }

    fun refresh() {
        val dac = findUsbDac()
        val enabledInPrefs = prefs?.getUsbBitPerfect() == true

        if (dac == null) {
            clearApplied()
            restoreMediaVolume()
            publishStatus(UsbAudioStatus(
                usbDacConnected = false,
                usbAttached = false,
                deviceName = null,
                bitPerfectActive = false,
                reason = "no_usb_dac",
                failureReason = "no_usb_dac"
            ))
            return
        }

        if (!enabledInPrefs) {
            clearApplied()
            restoreMediaVolume()
            publishStatus(UsbAudioStatus(
                usbDacConnected = true,
                usbAttached = true,
                deviceName = dac.productName?.toString(),
                bitPerfectActive = false,
                reason = "disabled_by_user",
                failureReason = "disabled_by_user"
            ))
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            containerUpgradeTarget = 0
            publishStatus(UsbAudioStatus(
                usbDacConnected = true,
                usbAttached = true,
                deviceName = dac.productName?.toString(),
                bitPerfectActive = false,
                reason = "android_version",
                failureReason = "android_version"
            ))
            return
        }

        if (lastSampleRate <= 0) {
            containerUpgradeTarget = 0
            publishStatus(UsbAudioStatus(
                usbDacConnected = true,
                usbAttached = true,
                deviceName = dac.productName?.toString(),
                bitPerfectActive = false,
                reason = "waiting_for_track",
                failureReason = "waiting_for_track"
            ))
            return
        }

        tryApplyBitPerfect(dac, lastSampleRate, lastChannelCount, lastEncoding)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun tryApplyBitPerfect(
        dac: AudioDeviceInfo,
        wantedRate: Int,
        wantedChannels: Int,
        wantedEncoding: Int
    ) {
        val wantedChannelMask = if (wantedChannels == 1) {
            AudioFormat.CHANNEL_OUT_MONO
        } else {
            AudioFormat.CHANNEL_OUT_STEREO
        }

        if (mismatchLatched) {
            // Settled as "cannot be bit-perfect" for this track: do not re-apply the mixer.
            publishStatus(UsbAudioStatus(
                usbDacConnected = true,
                usbAttached = true,
                deviceName = dac.productName?.toString(),
                bitPerfectActive = false,
                reason = "track_format_mismatch",
                failureReason = "track_format_mismatch"
            ))
            return
        }

        val controller = mixerController ?: return
        val selection = controller.selectBitPerfectMixer(
            dac, wantedRate, wantedChannelMask, wantedEncoding
        )

        if (selection == null) {
            clearApplied()
            restoreMediaVolume()
            publishStatus(UsbAudioStatus(
                usbDacConnected = true,
                usbAttached = true,
                deviceName = dac.productName?.toString(),
                bitPerfectActive = false,
                reason = "format_unsupported_${wantedRate}Hz",
                failureReason = "format_unsupported_${wantedRate}Hz"
            ))
            return
        }

        val applied = controller.applyMixer(dac, selection.mixer)
        if (applied) {
            val mixerFmt = Api34Impl.getAppliedFormat()
            if (mixerFmt == null) {
                clearApplied()
                restoreMediaVolume()
                publishStatus(UsbAudioStatus(
                    usbDacConnected = true,
                    usbAttached = true,
                    deviceName = dac.productName?.toString(),
                    bitPerfectActive = false,
                    reason = "set_mixer_failed",
                    failureReason = "set_mixer_failed"
                ))
                return
            }
            containerUpgradeTarget = if (selection.containerUpgrade) {
                mixerFmt.encoding
            } else {
                0
            }
            forceMediaVolumeToMax()
            publishStatus(UsbAudioStatus(
                usbDacConnected = true,
                usbAttached = true,
                deviceName = dac.productName?.toString(),
                bitPerfectActive = true,
                activeSampleRate = mixerFmt.sampleRate,
                sampleRateHz = mixerFmt.sampleRate,
                activeEncoding = mixerFmt.encoding,
                // Honest label: the decoder hands the sink 16-bit PCM (float output is off), so
                // when the DAC runs a wider container say "16-bit -> 32-bit", not just "32-bit".
                encodingLabel = if (selection.containerUpgrade) {
                    "${encodingLabel(lastEncoding)} → ${encodingLabel(mixerFmt.encoding)}"
                } else {
                    encodingLabel(mixerFmt.encoding)
                },
                volumeForcedToMax = volumeForcedToMax,
                reason = if (selection.containerUpgrade) "active_container_upgrade" else "active",
                failureReason = "active"
            ))
        } else {
            clearApplied()
            restoreMediaVolume()
            publishStatus(UsbAudioStatus(
                usbDacConnected = true,
                usbAttached = true,
                deviceName = dac.productName?.toString(),
                bitPerfectActive = false,
                reason = "set_mixer_failed",
                failureReason = "set_mixer_failed"
            ))
        }
    }

    private fun encodingLabel(encoding: Int): String = when (encoding) {
        AudioFormat.ENCODING_PCM_16BIT -> "16-bit"
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> "24-bit"
        AudioFormat.ENCODING_PCM_32BIT -> "32-bit"
        AudioFormat.ENCODING_PCM_FLOAT -> "Float"
        else -> ""
    }

    private fun forceMediaVolumeToMax() {
        if (prefs?.getUsbForceMaxVolume() != true) return
        val am = audioManager ?: return
        try {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (!volumeForcedToMax) {
                savedVolumeIndex = current
                prefs?.setUsbSavedVolumeIndex(current)
                volumeForcedToMax = true
            }
            if (current != max) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
            }
        } catch (_: Exception) {}
    }

    private fun restoreMediaVolume() {
        if (!volumeForcedToMax) return
        val am = audioManager ?: return
        try {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val target = savedVolumeIndex.coerceIn(0, max)
            if (am.getStreamVolume(AudioManager.STREAM_MUSIC) != target) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            }
        } catch (_: Exception) {}
        volumeForcedToMax = false
        savedVolumeIndex = -1
        prefs?.clearUsbSavedVolumeIndex()
    }

    fun clearApplied() {
        containerUpgradeTarget = 0
        mixerController?.resetMixer()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private object Api34Impl {
        private var listener: AudioManager.OnPreferredMixerAttributesChangedListener? = null

        fun getAppliedFormat(): AudioFormat? {
            val controller = mixerController ?: return null
            return controller.appliedMixerFormat()
        }

        fun registerMixerListener(context: Context, am: AudioManager?, onRevoked: () -> Unit) {
            if (am == null) return
            try {
                listener = AudioManager.OnPreferredMixerAttributesChangedListener { _, _, _ ->
                    // Runs on the main executor: an escaping exception here kills the app.
                    try {
                        val controller = mixerController
                        val currentFmt = controller?.appliedMixerFormat()
                        val dac = findUsbDac()
                        if (currentFmt != null && dac != null) {
                            val currentPref = am.getPreferredMixerAttributes(mediaAudioAttributes, dac)
                            if (currentPref == null || currentPref.format != currentFmt) {
                                Log.w(TAG, "Bit-perfect mixer revoked by Android Audio HAL")
                                onRevoked()
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Mixer listener failed", e)
                    }
                }
                am.addOnPreferredMixerAttributesChangedListener(
                    androidx.core.content.ContextCompat.getMainExecutor(context),
                    listener!!
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed registering mixer listener", e)
            }
        }
    }
}
