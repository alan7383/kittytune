package com.alananasss.kittytune.audio.usb

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi

class UsbBitPerfectController(context: Context) {

    data class MixerSelection(
        val mixer: Any, // android.media.AudioMixerAttributes on API 34+
        val containerUpgrade: Boolean
    )

    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val mediaAudioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private var appliedDevice: AudioDeviceInfo? = null

    fun findUsbDac(): AudioDeviceInfo? {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return outputs.filter {
            it.isSink && (
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
                )
        }.minByOrNull { it.id } // same pick as UsbAudioManager.findUsbDac()
    }

    fun selectBitPerfectMixer(
        dac: AudioDeviceInfo,
        sampleRate: Int,
        channelMask: Int,
        sourceEncoding: Int
    ): MixerSelection? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return Api34Impl.selectBitPerfectMixer(audioManager, dac, sampleRate, channelMask, sourceEncoding)
    }

    fun applyMixer(dac: AudioDeviceInfo, mixer: Any): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        val success = Api34Impl.applyMixer(audioManager, mediaAudioAttributes, dac, mixer)
        if (success) {
            appliedDevice = dac
        }
        return success
    }

    fun setupBitPerfect(sampleRate: Int, channelMask: Int, encoding: Int): MixerSelection? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        val dac = findUsbDac() ?: return null
        val selection = selectBitPerfectMixer(dac, sampleRate, channelMask, encoding) ?: return null
        return if (applyMixer(dac, selection.mixer)) selection else null
    }

    fun resetMixer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            appliedDevice?.let { dev ->
                Api34Impl.clearMixer(audioManager, mediaAudioAttributes, dev)
            }
        }
        appliedDevice = null
    }

    fun appliedMixerFormat(): AudioFormat? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return Api34Impl.getAppliedFormat()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private object Api34Impl {
        @Volatile
        private var lastAppliedMixer: android.media.AudioMixerAttributes? = null

        fun selectBitPerfectMixer(
            am: AudioManager,
            dac: AudioDeviceInfo,
            sampleRate: Int,
            channelMask: Int,
            sourceEncoding: Int
        ): MixerSelection? {
            val supportedMixers = try {
                am.getSupportedMixerAttributes(dac).filter {
                    it.mixerBehavior == android.media.AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT
                }
            } catch (_: Exception) {
                emptyList()
            }
            if (supportedMixers.isEmpty()) return null

            // 1. Exact match
            supportedMixers.firstOrNull { attr ->
                val fmt = attr.format
                fmt.sampleRate == sampleRate &&
                    fmt.channelMask == channelMask &&
                    fmt.encoding == sourceEncoding
            }?.let {
                return MixerSelection(it, containerUpgrade = false)
            }

            // 2. Lossless container upgrade (16 -> 24/32, 24 -> 32)
            supportedMixers.firstOrNull { attr ->
                val fmt = attr.format
                fmt.sampleRate == sampleRate &&
                    fmt.channelMask == channelMask &&
                    PcmContainerConverter.isLosslessUpgrade(sourceEncoding, fmt.encoding)
            }?.let {
                return MixerSelection(it, containerUpgrade = true)
            }

            return null
        }

        fun applyMixer(
            am: AudioManager,
            attr: AudioAttributes,
            dac: AudioDeviceInfo,
            mixer: Any
        ): Boolean {
            val mixerAttr = mixer as? android.media.AudioMixerAttributes ?: return false
            val success = try {
                am.setPreferredMixerAttributes(attr, dac, mixerAttr)
            } catch (_: Exception) {
                false
            }
            if (!success) return false

            val verified = try {
                am.getPreferredMixerAttributes(attr, dac) == mixerAttr
            } catch (_: Exception) {
                false
            }
            if (verified) {
                lastAppliedMixer = mixerAttr
            }
            return verified
        }

        fun clearMixer(am: AudioManager, attr: AudioAttributes, dac: AudioDeviceInfo) {
            try {
                am.clearPreferredMixerAttributes(attr, dac)
            } catch (_: Exception) {}
            lastAppliedMixer = null
        }

        fun getAppliedFormat(): AudioFormat? = lastAppliedMixer?.format
    }
}
