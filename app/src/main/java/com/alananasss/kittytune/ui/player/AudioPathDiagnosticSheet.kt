package com.alananasss.kittytune.ui.player

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.audio.usb.UsbAudioManager
import com.alananasss.kittytune.audio.usb.UsbAudioStatus
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.ui.common.KittyModalBottomSheet
import com.alananasss.kittytune.ui.player.audio.EqualizerAudioProcessor
import java.util.Locale

enum class AudioPathStatus { BIT_PERFECT, RESAMPLED, DIRECT }

data class AudioPathSnapshot(
    val hasTrack: Boolean,
    val codec: String,
    val trackRateHz: Int,
    val depthLabel: String?,
    val bitrateKbps: Int,
    val channelsLabel: String,
    val eqEnabled: Boolean,
    val eqBands: Int,
    val eqPreset: String,
    val normalizeEnabled: Boolean,
    val normalizeTargetLufs: String?,
    val bitPerfectBypassesDsp: Boolean,
    val outputKind: OutputKind,
    val outputName: String?,
    val bitPerfect: Boolean,
    val containerUpgrade: Boolean,
    val busRateHz: Int,
    val busDepthLabel: String?,
    val status: AudioPathStatus
)

enum class OutputKind { USB_DAC, WIRED, BLUETOOTH, BUILTIN }

object AudioPathColors {
    val good: Color = Color(0xFF43A047)
    val warn: Color = Color(0xFFF9A825)

    fun statusColor(status: AudioPathStatus): Color = when (status) {
        AudioPathStatus.BIT_PERFECT -> good
        AudioPathStatus.RESAMPLED -> warn
        AudioPathStatus.DIRECT -> Color(0xFF8E9AA6)
    }
}

@Composable
fun rememberAudioPathSnapshot(playerViewModel: PlayerViewModel): AudioPathSnapshot {
    val context = LocalContext.current
    val format by MusicManager.currentAudioFormatFlow.collectAsState()
    val usbStatus by UsbAudioManager.status.collectAsState()
    val eqState = playerViewModel.equalizerState
    val effectsState = playerViewModel.effectsState

    var deviceTick by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                deviceTick++
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                deviceTick++
            }
        }
        am?.registerAudioDeviceCallback(callback, null)
        onDispose { am?.unregisterAudioDeviceCallback(callback) }
    }

    return remember(format, usbStatus, eqState, effectsState, deviceTick) {
        buildAudioPathSnapshot(context, format, usbStatus, eqState, effectsState)
    }
}

private fun buildAudioPathSnapshot(
    context: Context,
    format: androidx.media3.common.Format?,
    usb: UsbAudioStatus,
    eqState: EqualizerState,
    effectsState: AudioEffectsState
): AudioPathSnapshot {
    val hasTrack = format != null
    val codec = codecLabel(format?.sampleMimeType)
    val trackRate = format?.sampleRate ?: 0
    val depth = media3DepthLabel(format?.pcmEncoding ?: -1)
    val bitrate = (format?.bitrate ?: 0) / 1000
    val channels = channelsLabel(format?.channelCount ?: 0)

    val output = resolveOutputInfo(context, usb)
    val busRate = output.busRateHz

    val status = when {
        output.bitPerfect -> AudioPathStatus.BIT_PERFECT
        trackRate > 0 && busRate > 0 && trackRate != busRate -> AudioPathStatus.RESAMPLED
        else -> AudioPathStatus.DIRECT
    }

    val targetLufs = if (effectsState.isNormalizationEnabled) {
        when (effectsState.normalizationLevel) {
            NormalizationLevel.QUIET -> "−19 LUFS"
            NormalizationLevel.LOUD -> "−11 LUFS"
            else -> "−14 LUFS"
        }
    } else null

    return AudioPathSnapshot(
        hasTrack = hasTrack,
        codec = codec,
        trackRateHz = trackRate,
        depthLabel = depth,
        bitrateKbps = bitrate,
        channelsLabel = channels,
        eqEnabled = eqState.isEnabled,
        eqBands = EqualizerAudioProcessor.NUM_BANDS,
        eqPreset = eqState.selectedPreset,
        normalizeEnabled = effectsState.isNormalizationEnabled,
        normalizeTargetLufs = targetLufs,
        bitPerfectBypassesDsp = com.alananasss.kittytune.audio.AudioRoutingMatrix.isBitPerfectActive(),
        outputKind = output.kind,
        outputName = output.name,
        bitPerfect = output.bitPerfect,
        containerUpgrade = output.containerUpgrade,
        busRateHz = busRate,
        busDepthLabel = output.busDepthLabel,
        status = status
    )
}

private class OutputInfo(
    val kind: OutputKind,
    val name: String?,
    val bitPerfect: Boolean,
    val containerUpgrade: Boolean,
    val busRateHz: Int,
    val busDepthLabel: String?
)

private fun resolveOutputInfo(context: Context, usb: UsbAudioStatus): OutputInfo {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    val propertyRate = try {
        am?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 0
    } catch (_: Exception) {
        0
    }

    if (usb.usbDacConnected) {
        val dac = UsbAudioManager.findUsbDac()
        if (usb.bitPerfectActive) {
            return OutputInfo(
                kind = OutputKind.USB_DAC,
                name = usb.deviceName
                    ?: dac?.productName?.toString()?.takeIf { it.isNotBlank() },
                bitPerfect = true,
                containerUpgrade = usb.reason == "active_container_upgrade",
                busRateHz = usb.activeSampleRate,
                busDepthLabel = usb.encodingLabel?.takeIf { it.isNotBlank() }
            )
        }
        var rate = 0
        var depth: String? = null

        // Crash guard: getSupportedMixerAttributes is only available on API 34+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && dac != null && am != null) {
            val mixerFmt = Api34DiagnosticHelper.getDefaultMixerFormat(am, dac)
            if (mixerFmt != null) {
                rate = mixerFmt.first
                depth = mixerFmt.second
            }
        }

        if (rate <= 0) rate = propertyRate
        return OutputInfo(
            kind = OutputKind.USB_DAC,
            name = usb.deviceName
                ?: dac?.productName?.toString()?.takeIf { it.isNotBlank() },
            bitPerfect = false,
            containerUpgrade = false,
            busRateHz = rate,
            busDepthLabel = depth
        )
    }

    val device = am?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.firstOrNull {
        it.isSink && when (it.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> true
            else -> false
        }
    }
    val kind = when (device?.type) {
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> OutputKind.WIRED
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET -> OutputKind.BLUETOOTH
        else -> OutputKind.BUILTIN
    }
    return OutputInfo(
        kind = kind,
        name = device?.productName?.toString()?.takeIf { it.isNotBlank() },
        bitPerfect = false,
        containerUpgrade = false,
        busRateHz = propertyRate,
        busDepthLabel = null
    )
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private object Api34DiagnosticHelper {
    fun getDefaultMixerFormat(am: AudioManager, dac: AudioDeviceInfo): Pair<Int, String?>? {
        return try {
            val fmt = am.getSupportedMixerAttributes(dac).firstOrNull()?.format ?: return null
            Pair(fmt.sampleRate, audioFormatDepthLabel(fmt.encoding))
        } catch (_: Throwable) {
            null
        }
    }
}

private fun media3DepthLabel(pcmEncoding: Int): String? = when (pcmEncoding) {
    androidx.media3.common.C.ENCODING_PCM_16BIT -> "16-bit"
    androidx.media3.common.C.ENCODING_PCM_24BIT,
    androidx.media3.common.C.ENCODING_PCM_24BIT_BIG_ENDIAN -> "24-bit"
    androidx.media3.common.C.ENCODING_PCM_32BIT,
    androidx.media3.common.C.ENCODING_PCM_32BIT_BIG_ENDIAN -> "32-bit"
    androidx.media3.common.C.ENCODING_PCM_FLOAT -> "32-bit float"
    else -> null
}

private fun audioFormatDepthLabel(encoding: Int): String? = when (encoding) {
    AudioFormat.ENCODING_PCM_16BIT -> "16-bit"
    AudioFormat.ENCODING_PCM_24BIT_PACKED -> "24-bit"
    AudioFormat.ENCODING_PCM_32BIT -> "32-bit"
    AudioFormat.ENCODING_PCM_FLOAT -> "Float"
    else -> null
}

private fun codecLabel(mime: String?): String {
    if (mime.isNullOrEmpty()) return "PCM"
    val sub = mime.substringAfterLast('/').removePrefix("x-").uppercase(Locale.US)
    return when (sub) {
        "MPEG" -> "MP3"
        "MP4A-LATM", "AAC" -> "AAC"
        "AC3" -> "AC-3"
        "EAC3" -> "E-AC-3"
        "AC4" -> "AC-4"
        "RAW" -> "PCM"
        else -> sub
    }
}

private fun channelsLabel(count: Int): String = when (count) {
    1 -> "Mono 1.0"
    2 -> "Stereo 2.0"
    6 -> "Surround 5.1"
    8 -> "Surround 7.1"
    else -> if (count > 0) "$count ch" else "—"
}

private fun khz(rate: Int): String =
    if (rate <= 0) "—" else String.format(Locale.US, "%.1f", rate / 1000.0) + " kHz"

private fun khzCompact(rate: Int): String {
    if (rate <= 0) return ""
    val v = rate / 1000.0
    val num = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.1f", v)
    return "${num}kHz"
}

@Composable
private fun outputDisplayName(snapshot: AudioPathSnapshot): String {
    snapshot.outputName?.let { return it }
    return stringResource(
        when (snapshot.outputKind) {
            OutputKind.USB_DAC -> R.string.diag_out_usb_dac
            OutputKind.WIRED -> R.string.diag_out_wired
            OutputKind.BLUETOOTH -> R.string.diag_out_bluetooth
            OutputKind.BUILTIN -> R.string.diag_out_builtin
        }
    )
}

@Composable
fun audioPathHeadline(snapshot: AudioPathSnapshot): String {
    if (!snapshot.hasTrack) return stringResource(R.string.diag_hero_sub_waiting)
    val input = listOfNotNull(
        snapshot.codec,
        snapshot.depthLabel,
        khzCompact(snapshot.trackRateHz).ifEmpty { null }
    ).joinToString(" ")
    val mode = when (snapshot.status) {
        AudioPathStatus.BIT_PERFECT -> "Bit-Perfect"
        AudioPathStatus.RESAMPLED -> stringResource(R.string.diag_badge_resampled)
        AudioPathStatus.DIRECT -> stringResource(R.string.diag_badge_direct)
    }
    return "$input → ${outputDisplayName(snapshot)} [$mode]"
}

private enum class ChipTone { NEUTRAL, GOOD, WARN }

private data class DiagChipData(
    val label: String,
    val value: String,
    val tone: ChipTone
)

@Composable
fun AudioPathHeroCard(
    playerViewModel: PlayerViewModel,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snapshot = rememberAudioPathSnapshot(playerViewModel)
    val headline = audioPathHeadline(snapshot)

    ElevatedCard(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.diag_hero_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = headline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(10.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = stringResource(R.string.diag_hero_open),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AudioPathDiagnosticSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    playerViewModel: PlayerViewModel
) {
    if (!visible) return

    val snapshot = rememberAudioPathSnapshot(playerViewModel)

    KittyModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.diag_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                StatusBadge(snapshot.status)
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = audioPathHeadline(snapshot),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(18.dp))

            PathSection(
                icon = Icons.Rounded.MusicNote,
                title = stringResource(R.string.diag_section_input),
                subtitle = snapshot.codec,
                chips = inputChips(snapshot)
            )

            PathConnector()

            PathSection(
                icon = Icons.Rounded.Tune,
                title = stringResource(R.string.diag_section_dsp),
                subtitle = null,
                chips = dspChips(snapshot)
            )

            PathConnector()

            PathSection(
                icon = Icons.Rounded.Speaker,
                title = stringResource(R.string.diag_section_output),
                subtitle = null,
                chips = outputChips(snapshot)
            )

            PathConnector()

            RoutingMatrixSection()
        }
    }
}

@Composable
private fun inputChips(snapshot: AudioPathSnapshot): List<DiagChipData> {
    return listOf(
        DiagChipData(
            label = stringResource(R.string.diag_chip_rate),
            value = khz(snapshot.trackRateHz),
            tone = ChipTone.NEUTRAL
        ),
        DiagChipData(
            label = stringResource(R.string.diag_chip_depth),
            value = snapshot.depthLabel ?: "—",
            tone = ChipTone.NEUTRAL
        ),
        DiagChipData(
            label = stringResource(R.string.diag_chip_bitrate),
            value = if (snapshot.bitrateKbps > 0) "${snapshot.bitrateKbps} kbps" else "—",
            tone = ChipTone.NEUTRAL
        ),
        DiagChipData(
            label = stringResource(R.string.diag_chip_channels),
            value = snapshot.channelsLabel,
            tone = ChipTone.NEUTRAL
        )
    )
}

@Composable
private fun dspChips(snapshot: AudioPathSnapshot): List<DiagChipData> {
    val resamplerValue = when {
        snapshot.status == AudioPathStatus.RESAMPLED &&
            snapshot.trackRateHz > 0 && snapshot.busRateHz > 0 ->
            stringResource(
                R.string.diag_resampler_src,
                khz(snapshot.trackRateHz),
                khz(snapshot.busRateHz)
            )
        else -> stringResource(R.string.diag_resampler_native)
    }
    val eqValue = when {
        !snapshot.eqEnabled -> stringResource(R.string.diag_eq_off)
        snapshot.bitPerfectBypassesDsp -> stringResource(R.string.diag_eq_active_bp)
        else -> stringResource(R.string.diag_eq_active, snapshot.eqBands)
    }
    val normValue = if (snapshot.normalizeEnabled) {
        stringResource(R.string.diag_norm_on, snapshot.normalizeTargetLufs ?: "−14 LUFS")
    } else {
        stringResource(R.string.diag_norm_off)
    }
    return listOf(
        DiagChipData(
            label = stringResource(R.string.diag_resampler),
            value = resamplerValue,
            tone = if (snapshot.status == AudioPathStatus.RESAMPLED) ChipTone.WARN else ChipTone.GOOD
        ),
        DiagChipData(
            label = stringResource(R.string.diag_equalizer),
            value = eqValue,
            tone = when {
                !snapshot.eqEnabled -> ChipTone.NEUTRAL
                snapshot.bitPerfectBypassesDsp -> ChipTone.WARN
                else -> ChipTone.GOOD
            }
        ),
        DiagChipData(
            label = stringResource(R.string.diag_normalizer),
            value = normValue,
            tone = if (snapshot.normalizeEnabled) ChipTone.GOOD else ChipTone.NEUTRAL
        )
    )
}

@Composable
private fun outputChips(snapshot: AudioPathSnapshot): List<DiagChipData> {
    val modeValue = if (snapshot.bitPerfect) {
        stringResource(R.string.diag_output_mode_bp)
    } else {
        stringResource(R.string.diag_output_mode_mixer)
    }
    val busValue = listOfNotNull(
        khz(snapshot.busRateHz).takeIf { snapshot.busRateHz > 0 },
        snapshot.busDepthLabel
    ).joinToString(" · ").ifEmpty { "—" }

    val chips = mutableListOf(
        DiagChipData(
            label = stringResource(R.string.diag_output_device),
            value = outputDisplayName(snapshot),
            tone = ChipTone.NEUTRAL
        ),
        DiagChipData(
            label = stringResource(R.string.diag_output_mode),
            value = modeValue,
            tone = if (snapshot.bitPerfect) ChipTone.GOOD else ChipTone.NEUTRAL
        ),
        DiagChipData(
            label = stringResource(R.string.diag_bus_format),
            value = busValue,
            tone = ChipTone.NEUTRAL
        )
    )
    if (snapshot.containerUpgrade) {
        chips.add(
            DiagChipData(
                label = stringResource(R.string.diag_container_upgrade),
                value = snapshot.busDepthLabel ?: khz(snapshot.busRateHz),
                tone = ChipTone.GOOD
            )
        )
    }
    return chips
}

@Composable
private fun PathSection(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    chips: List<DiagChipData>
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!subtitle.isNullOrEmpty()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                chips.forEach { chip ->
                    DiagChip(chip)
                }
            }
        }
    }
}

@Composable
private fun DiagChip(chip: DiagChipData) {
    val toneColor = when (chip.tone) {
        ChipTone.GOOD -> AudioPathColors.good
        ChipTone.WARN -> AudioPathColors.warn
        ChipTone.NEUTRAL -> null
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = toneColor?.copy(alpha = 0.14f) ?: MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (toneColor != null) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(toneColor, CircleShape)
                )
            }
            Column {
                Text(
                    text = chip.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = chip.value,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = toneColor ?: MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PathConnector() {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(8.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(22.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.ArrowDownward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(8.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    }
}

@Composable
private fun StatusBadge(status: AudioPathStatus) {
    val color = AudioPathColors.statusColor(status)
    val text = stringResource(
        when (status) {
            AudioPathStatus.BIT_PERFECT -> R.string.diag_badge_bitperfect
            AudioPathStatus.RESAMPLED -> R.string.diag_badge_resampled
            AudioPathStatus.DIRECT -> R.string.diag_badge_direct
        }
    )
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.14f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(color, CircleShape)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun RoutingMatrixSection() {
    PathSection(
        icon = Icons.Rounded.GraphicEq,
        title = stringResource(R.string.diag_section_routing),
        subtitle = if (com.alananasss.kittytune.audio.AudioRoutingMatrix.isBitPerfectActive())
            stringResource(R.string.diag_routing_isolated)
        else null,
        chips = routingMatrixChips()
    )
}

@Composable
private fun routingMatrixChips(): List<DiagChipData> {
    return com.alananasss.kittytune.audio.AudioRoutingMatrix.snapshot().map { (stage, routed) ->
        DiagChipData(
            label = stage.label,
            value = if (routed) "\u2713" else "\u2014",
            tone = if (routed) ChipTone.GOOD else ChipTone.NEUTRAL
        )
    }
}
