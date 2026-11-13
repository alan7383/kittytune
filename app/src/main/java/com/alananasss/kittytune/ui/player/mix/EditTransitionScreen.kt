package com.alananasss.kittytune.ui.player.mix

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.audio.automix.EffectTransitionMode
import com.alananasss.kittytune.audio.automix.EqTransitionMode
import com.alananasss.kittytune.audio.automix.CurveOwner
import com.alananasss.kittytune.audio.automix.FilterTransitionMode
import com.alananasss.kittytune.audio.automix.FullTrackWaveformDecoder
import com.alananasss.kittytune.audio.automix.LoopTransitionMode
import com.alananasss.kittytune.audio.automix.MixPreset
import com.alananasss.kittytune.audio.automix.TempoMatchMode
import com.alananasss.kittytune.audio.automix.TransitionBars
import com.alananasss.kittytune.audio.automix.TransitionConfig
import com.alananasss.kittytune.audio.automix.TransitionPlan
import com.alananasss.kittytune.audio.automix.TransitionPreset
import com.alananasss.kittytune.audio.automix.VolumeCurveMode
import com.alananasss.kittytune.audio.automix.toConfig
import com.alananasss.kittytune.audio.automix.toPlan
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.BeatInfoEntity
import com.alananasss.kittytune.data.WaveformRepository
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.data.mix.MixPlaylistAnalyzer
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.KittyModalBottomSheet
import com.alananasss.kittytune.ui.common.hapticClickable
import com.alananasss.kittytune.ui.common.rememberAppHaptics
import com.alananasss.kittytune.utils.makeTimeString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import android.util.Log

private const val TAG = "EditTransition"

enum class EditSection { NONE, VOLUME, EQ, FILTER, EFFECTS, LOOPING, BEATMATCH }

private data class TransitionOption<T>(val value: T, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun EditTransitionScreen(
    trackA: Track,
    trackB: Track,
    initialPlan: TransitionPlan,
    playlistKey: String?,
    onSave: (TransitionPlan) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = rememberAppHaptics()

    var config by remember(trackA.id, trackB.id) { mutableStateOf(initialPlan.toConfig()) }
    val initialConfig = remember(trackA.id, trackB.id) { initialPlan.toConfig() }
    val hasChanges = config != initialConfig

    var activeSection by remember { mutableStateOf(EditSection.NONE) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    // The customization button's mode: which control's curve is being hand-placed on the
    // canvas right now. NONE = regular editing (waves drag, named curves show).
    var curveEditing by remember { mutableStateOf(EditSection.NONE) }

    var previewing by remember { mutableStateOf(false) }
    var previewProgress by remember { mutableFloatStateOf(0f) }

    val profileA by remember(trackA.id) { MixPlaylistAnalyzer.profileFlow(context, trackA) }
        .collectAsState(initial = null)
    val profileB by remember(trackB.id) { MixPlaylistAnalyzer.profileFlow(context, trackB) }
        .collectAsState(initial = null)
    val bpmA = profileA?.bpm?.toFloat() ?: 0f
    val bpmB = profileB?.bpm?.toFloat() ?: 0f
    val keyA = profileA?.camelot
    val keyB = profileB?.camelot

    val resolvedConfig = remember(config, bpmA, bpmB, keyA, keyB) {
        if (config.isAuto) config.toPlan().resolveAuto(bpmA, bpmB, keyA, keyB).toConfig()
        else config
    }

    // The real, full-length waveforms for the edit canvas - decoded once and
    // cached in WaveformRepository - plus the beat grids of both tracks.
    var decodedA by remember(trackA.id) { mutableStateOf<FullTrackWaveformDecoder.DecodedWaveform?>(null) }
    var decodedB by remember(trackB.id) { mutableStateOf<FullTrackWaveformDecoder.DecodedWaveform?>(null) }
    var decodedProgressA by remember(trackA.id) { mutableFloatStateOf(0f) }
    var decodedProgressB by remember(trackB.id) { mutableFloatStateOf(0f) }
    var decodedFailedA by remember(trackA.id) { mutableStateOf(false) }
    var decodedFailedB by remember(trackB.id) { mutableStateOf(false) }
    // Bumping this nonce re-runs both loads - tapping "Waveform unavailable" retries them.
    var waveRetryNonce by remember(trackA.id, trackB.id) { mutableStateOf(0) }

    LaunchedEffect(trackA.id, waveRetryNonce) {
        decodedFailedA = false
        decodedProgressA = 0f
        WaveformRepository.getDecodedWaveform(context, trackA) { p -> decodedProgressA = p }
            ?.let { decodedA = it }
            ?: run {
                // Reset the stale progress, or "Loading 67%" would sit next to the
                // failure badge forever.
                decodedProgressA = 0f
                decodedFailedA = true
            }
    }
    LaunchedEffect(trackB.id, waveRetryNonce) {
        decodedFailedB = false
        decodedProgressB = 0f
        WaveformRepository.getDecodedWaveform(context, trackB) { p -> decodedProgressB = p }
            ?.let { decodedB = it }
            ?: run {
                decodedProgressB = 0f
                decodedFailedB = true
            }
    }

    var beatInfoA by remember(trackA.id) { mutableStateOf<BeatInfoEntity?>(null) }
    var beatInfoB by remember(trackB.id) { mutableStateOf<BeatInfoEntity?>(null) }
    LaunchedEffect(trackA.id) {
        val id = trackA.id.toString()
        beatInfoA = runCatching {
            AppDatabase.getDatabase(context).beatInfoDao().getBeatInfo(id)
        }.getOrNull()
        if (beatInfoA?.bpm?.let { it > 0f } != true) {
            runCatching {
                com.alananasss.kittytune.audio.automix.AutomixManager.maybeAnalyzeBeat(
                    trackA,
                    com.alananasss.kittytune.audio.automix.BeatAnalysisPriority.IMMEDIATE,
                )
            }
            com.alananasss.kittytune.audio.automix.AutomixManager.onBeatAnalysisCompleted.collect { done ->
                if (done == id) {
                    beatInfoA = runCatching {
                        AppDatabase.getDatabase(context).beatInfoDao().getBeatInfo(id)
                    }.getOrNull()
                }
            }
        }
    }
    LaunchedEffect(trackB.id) {
        val id = trackB.id.toString()
        beatInfoB = runCatching {
            AppDatabase.getDatabase(context).beatInfoDao().getBeatInfo(id)
        }.getOrNull()
        if (beatInfoB?.bpm?.let { it > 0f } != true) {
            runCatching {
                com.alananasss.kittytune.audio.automix.AutomixManager.maybeAnalyzeBeat(
                    trackB,
                    com.alananasss.kittytune.audio.automix.BeatAnalysisPriority.IMMEDIATE,
                )
            }
            com.alananasss.kittytune.audio.automix.AutomixManager.onBeatAnalysisCompleted.collect { done ->
                if (done == id) {
                    beatInfoB = runCatching {
                        AppDatabase.getDatabase(context).beatInfoDao().getBeatInfo(id)
                    }.getOrNull()
                }
            }
        }
    }

    val previewPlayer = remember(trackA.id, trackB.id) {
        TransitionPreviewPlayer(context, trackA, trackB)
    }
    val previewState by previewPlayer.prepareState.collectAsState()
    DisposableEffect(trackA.id, trackB.id) {
        onDispose { previewPlayer.release() }
    }
    LaunchedEffect(trackA.id, trackB.id) {
        try {
            previewPlayer.prepare()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Preview prepare failed", e)
        }
    }

    val previewBpm = if (bpmA > 0f) bpmA else if (bpmB > 0f) bpmB else 120f
    val beatMs = 60_000f / previewBpm.coerceIn(60f, 200f)
    val overlapMs = resolvedConfig.duration.beats * beatMs

    LaunchedEffect(previewPlayer, bpmA, bpmB, resolvedConfig.tempoMatch) {
        previewPlayer.setTempoPair(bpmA, bpmB, resolvedConfig.tempoMatch)
    }

    // The rehearsal loop. One press of Play now performs the whole bracket:
    // a 3-second run-in on the outgoing (upper) track, the transition itself,
    // then a 3-second run-out on the incoming (lower) track - then it wraps
    // and plays again, exactly like a DJ cueing the seam over and over.
    LaunchedEffect(
        previewing,
        resolvedConfig.preset, resolvedConfig.volumeCurve, resolvedConfig.eqMode, resolvedConfig.filterMode,
        resolvedConfig.effectMode, resolvedConfig.loopMode, resolvedConfig.tempoMatch,
        resolvedConfig.duration, previewBpm, resolvedConfig.zoneStartMs,
    ) {
        if (!previewing) return@LaunchedEffect
        previewPlayer.arm(resolvedConfig, overlapMs.toLong())
        previewPlayer.start()
        // The lead-in the decks can actually honor (usually the full 3 s), read back from
        // the player so this clock and the decks' seeks always share one number.
        val leadInMs = previewPlayer.effectiveLeadInMs().coerceAtLeast(0L).toDouble()
        val runOutMs = TransitionPreviewPlayer.PREVIEW_TAIL_OUT_MS.toDouble()
        val totalMs = leadInMs + overlapMs + runOutMs
        var t = 0.0
        val frameMs = 16.0
        while (isActive && previewing) {
            t += frameMs
            val zoneT = t - leadInMs
            if (t >= totalMs) {
                // Wrap: back to the run-in for another pass.
                previewProgress = 0f
                t = 0.0
                previewPlayer.restartRun()
                previewPlayer.updateFrame(-1f)
            } else {
                val progress = (zoneT / overlapMs).toFloat()
                // The playhead parks at the zone's edges while the run-in/run-out plays.
                previewProgress = progress.coerceIn(0f, 1f)
                previewPlayer.updateFrame(progress)
            }
            // The encore - keep the outgoing deck inside its loop region, same as the engine.
            previewPlayer.applyLoop()
            // No beat haptics in this window: pressing play must stay vibration-free.
            delay(frameMs.toLong())
        }
    }

    // Hand curves (and plain envelope switches) ride into the running decks without the
    // re-arm above - dragging a point must never restart the audio mid-run.
    LaunchedEffect(
        resolvedConfig.volumeCurve, resolvedConfig.eqMode, resolvedConfig.filterMode,
        resolvedConfig.effectMode,
        resolvedConfig.customVolumeOut, resolvedConfig.customVolumeIn,
        resolvedConfig.customEqOut, resolvedConfig.customEqIn,
        resolvedConfig.customFilterOut, resolvedConfig.customFilterIn, resolvedConfig.customEffect,
    ) {
        previewPlayer.updateCurves(resolvedConfig)
    }

    LaunchedEffect(resolvedConfig.userNudgeOffsetMs) {
        previewPlayer.updateIncomingOffset(resolvedConfig.userNudgeOffsetMs)
    }

    val handleBack = {
        if (hasChanges) {
            showDiscardDialog = true
        } else {
            onDismiss()
        }
    }
    BackHandler { handleBack() }

    val volumeOptions = listOf(
        TransitionOption(VolumeCurveMode.OVERLAP, stringResource(R.string.volume_option_overlap)),
        TransitionOption(VolumeCurveMode.SMOOTH_CROSSFADE, stringResource(R.string.volume_option_smooth_crossfade)),
        TransitionOption(VolumeCurveMode.FADE_IN_FADE_OUT, stringResource(R.string.volume_option_fade_in_fade_out)),
        TransitionOption(VolumeCurveMode.CUT_IN_FADE_OUT, stringResource(R.string.volume_option_cut_in_fade_out)),
        TransitionOption(VolumeCurveMode.FADE_IN_CUT_OUT, stringResource(R.string.volume_option_fade_in_cut_out)),
        TransitionOption(VolumeCurveMode.CENTER_CUT, stringResource(R.string.volume_option_center_cut)),
        TransitionOption(VolumeCurveMode.CUT_IN_FADE_OUT, stringResource(R.string.volume_option_crossfade)),
        TransitionOption(VolumeCurveMode.FADE_IN_FAST_OUT, stringResource(R.string.volume_option_fade_in_fast_out)),
    )
    val eqOptions = listOf(
        TransitionOption(EqTransitionMode.CENTER_BASS_SWAP, stringResource(R.string.eq_option_centre_bass_swap)),
        TransitionOption(EqTransitionMode.END_BASS_SWAP, stringResource(R.string.eq_option_end_bass_swap)),
        TransitionOption(EqTransitionMode.START_BASS_SWAP, stringResource(R.string.eq_option_start_bass_swap)),
        TransitionOption(EqTransitionMode.THREE_BAND_FADE, stringResource(R.string.eq_option_three_band_fade)),
        TransitionOption(EqTransitionMode.QUICK_BASS_CUT, stringResource(R.string.eq_option_quick_bass_cut)),
        TransitionOption(EqTransitionMode.LONG_BASS_CUT, stringResource(R.string.eq_option_long_bass_cut)),
        TransitionOption(EqTransitionMode.NONE, stringResource(R.string.eq_option_none)),
    )
    val filterOptions = listOf(
        TransitionOption(FilterTransitionMode.LOW_PASS_OUT, stringResource(R.string.filter_option_low_pass_out)),
        TransitionOption(FilterTransitionMode.HIGH_PASS_OUT, stringResource(R.string.filter_option_high_pass_out)),
        TransitionOption(FilterTransitionMode.LOW_PASS_IN, stringResource(R.string.filter_option_low_pass_in)),
        TransitionOption(FilterTransitionMode.HIGH_PASS_IN, stringResource(R.string.filter_option_high_pass_in)),
        TransitionOption(FilterTransitionMode.HPF_OUT_LPF_IN, stringResource(R.string.filter_option_hpf_out_lpf_in)),
        TransitionOption(FilterTransitionMode.RISE_SWEEP, stringResource(R.string.filter_option_rise_sweep)),
        TransitionOption(FilterTransitionMode.NONE, stringResource(R.string.filter_option_none)),
    )
    val effectOptions = listOf(
        TransitionOption(EffectTransitionMode.NONE, stringResource(R.string.effect_option_none)),
        TransitionOption(EffectTransitionMode.REVERB_OUT_CENTER, stringResource(R.string.effect_option_reverb_out_center)),
        TransitionOption(EffectTransitionMode.REVERB_CUT_END, stringResource(R.string.effect_option_reverb_cut_end)),
        TransitionOption(EffectTransitionMode.REVERB_OUT_END, stringResource(R.string.effect_option_reverb_out_end)),
        TransitionOption(EffectTransitionMode.DELAY_HALF_CUT_END, stringResource(R.string.effect_option_delay_half_cut_end)),
        TransitionOption(EffectTransitionMode.DELAY_THREE_QUARTER_CUT_END, stringResource(R.string.effect_option_delay_three_quarter_cut_end)),
        TransitionOption(EffectTransitionMode.ECHO_HALF_CUT_END, stringResource(R.string.effect_option_echo_half_cut_end)),
        TransitionOption(EffectTransitionMode.ECHO_HALF_OUT_END, stringResource(R.string.effect_option_echo_half_out_end)),
    )
    val loopOptions = listOf(
        TransitionOption(LoopTransitionMode.NONE, stringResource(R.string.loop_option_none)),
        TransitionOption(LoopTransitionMode.BEAT_1, stringResource(R.string.loop_option_beat_1)),
        TransitionOption(LoopTransitionMode.BEAT_2, stringResource(R.string.loop_option_beat_2)),
        TransitionOption(LoopTransitionMode.BEAT_4, stringResource(R.string.loop_option_beat_4)),
        TransitionOption(LoopTransitionMode.BEAT_8, stringResource(R.string.loop_option_beat_8)),
        TransitionOption(LoopTransitionMode.BEAT_16, stringResource(R.string.loop_option_beat_16)),
    )
    val tempoMatchOptions = listOf(
        TransitionOption(TempoMatchMode.AUTO, stringResource(R.string.beatmatch_option_auto)),
        TransitionOption(TempoMatchMode.MATCH, stringResource(R.string.beatmatch_option_match)),
        TransitionOption(TempoMatchMode.ORIGINAL, stringResource(R.string.beatmatch_option_original)),
    )

    val bpmDiff = kotlin.math.abs(bpmA - bpmB)
    val isBpmDivergent = (bpmA > 0f && bpmB > 0f) && !TransitionPreviewPlayer.calculateSpotifyBpmMatch(bpmA, bpmB).first

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TransitionEditorBackground)
            .padding(top = 16.dp, bottom = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // Top Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(R.string.btn_cancel),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    modifier = Modifier.hapticClickable(kind = HapticKind.LIGHT) { handleBack() }
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.edit_transition_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(2.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = TransitionPlayheadColor,
                        contentColor = Color.Black,
                    ) {
                        Text(
                            text = stringResource(R.string.edit_transition_beta),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
                        )
                    }
                }
                Button(
                    onClick = {
                        haptics.perform(HapticKind.MEDIUM)
                        onSave(config.toPlan())
                        onDismiss()
                    },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TransitionPlayheadColor,
                        contentColor = Color.Black,
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.edit_transition_save),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Outgoing Track Card
            TransitionTrackCardSpotify(
                track = trackA,
                bpm = bpmA.toInt(),
                camelot = keyA,
                showOrangeDot = isBpmDivergent,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )

            // Waveform Editor
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(300.dp)
            ) {
                SpotifyFullWaveCanvas(
                    trackA = trackA,
                    trackB = trackB,
                    config = resolvedConfig,
                    bpmA = bpmA,
                    bpmB = bpmB,
                    waveA = decodedA,
                    waveB = decodedB,
                    waveProgressA = decodedProgressA,
                    waveProgressB = decodedProgressB,
                    waveFailedA = decodedFailedA,
                    waveFailedB = decodedFailedB,
                    beatInfoA = beatInfoA,
                    beatInfoB = beatInfoB,
                    isPreviewing = previewing,
                    previewProgress = previewProgress,
                    onAnchorChange = { ms -> config = config.copy(zoneStartMs = ms) },
                    onBOffsetChange = { ms -> config = config.copy(userNudgeOffsetMs = ms) },
                    onTogglePreview = {
                        // No haptic here on purpose: the user asked for a silent play button -
                        // the sound of the preview itself is the feedback.
                        previewing = !previewing
                        if (!previewing) {
                            previewPlayer.pauseAndReset()
                            previewProgress = 0f
                        }
                    },
                    onRetryWaveA = { waveRetryNonce++ },
                    onRetryWaveB = { waveRetryNonce++ },
                    editSection = curveEditing,
                    onCurvePoints = { owner, points ->
                        config = config.withCurvePoints(owner, points)
                    },
                    onDoneEditing = { curveEditing = EditSection.NONE },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Duration pills: 2 / 4 / 8 / 16 bars
            // A direct row of pills, same look as the Auto/Custom/Fade/Rise ribbon.
            // The old "4 bars ⌄" toggle hid the choices behind a tap and had no 16-bar
            // option at all - now every length is one tap away.
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(
                    TransitionBars.BARS_2,
                    TransitionBars.BARS_4,
                    TransitionBars.BARS_8,
                    TransitionBars.BARS_16,
                ).forEach { barCandidate ->
                    val selected = config.duration == barCandidate
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) Color.White else Color(0xFF2A2A2A),
                        contentColor = if (selected) Color.Black else Color.White,
                        modifier = Modifier
                            .weight(1f)
                            .hapticClickable(kind = HapticKind.LIGHT) {
                                config = config.copy(duration = barCandidate)
                            }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.transition_bars_value, barCandidate.bars),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Incoming Track Card
            Spacer(Modifier.height(8.dp))
            TransitionTrackCardSpotify(
                track = trackB,
                bpm = bpmB.toInt(),
                camelot = keyB,
                showOrangeDot = isBpmDivergent,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )

            Spacer(Modifier.height(16.dp))

            // Bottom Controls Block
            // The six control cards stay visible at all times; tapping one opens
            // its options as a bottom-sheet popup - the same sheet for every
            // control, titled and accented like the reference editor's Looping
            // panel (drag handle, accent bar, option rows, green check).
            // Row 1: 3 Cards (Volume, EQ, Filter)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SpotifySettingCard(
                    title = stringResource(R.string.transition_section_volume),
                    subtitle = if (resolvedConfig.hasCustomVolume) {
                        stringResource(R.string.edit_transition_preset_custom)
                    } else {
                        volumeOptions.firstOrNull { it.value == resolvedConfig.volumeCurve }?.label.orEmpty()
                    },
                    accentColor = TransitionVolumeColor,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        // A card whose curve is being hand-placed exits the hand edit;
                        // otherwise it opens its option sheet as usual.
                        if (curveEditing == EditSection.VOLUME) curveEditing = EditSection.NONE
                        else activeSection = EditSection.VOLUME
                    }
                )
                SpotifySettingCard(
                    title = stringResource(R.string.transition_section_eq),
                    subtitle = if (resolvedConfig.hasCustomEq) {
                        stringResource(R.string.edit_transition_preset_custom)
                    } else {
                        eqOptions.firstOrNull { it.value == resolvedConfig.eqMode }?.label.orEmpty()
                    },
                    accentColor = TransitionEqColor,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (curveEditing == EditSection.EQ) curveEditing = EditSection.NONE
                        else activeSection = EditSection.EQ
                    }
                )
                SpotifySettingCard(
                    title = stringResource(R.string.transition_section_filter),
                    subtitle = if (resolvedConfig.hasCustomFilter) {
                        stringResource(R.string.edit_transition_preset_custom)
                    } else {
                        filterOptions.firstOrNull { it.value == resolvedConfig.filterMode }?.label.orEmpty()
                    },
                    accentColor = TransitionFilterColor,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (curveEditing == EditSection.FILTER) curveEditing = EditSection.NONE
                        else activeSection = EditSection.FILTER
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            // Row 2: 3 Cards (Effects, Looping, Beatmatch)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SpotifySettingCard(
                    title = stringResource(R.string.transition_section_effects),
                    subtitle = if (resolvedConfig.hasCustomCurve(CurveOwner.EFFECT)) {
                        stringResource(R.string.edit_transition_preset_custom)
                    } else {
                        effectOptions.firstOrNull { it.value == resolvedConfig.effectMode }?.label.orEmpty()
                    },
                    accentColor = TransitionEffectsColor,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (curveEditing == EditSection.EFFECTS) curveEditing = EditSection.NONE
                        else activeSection = EditSection.EFFECTS
                    }
                )
                SpotifySettingCard(
                    title = stringResource(R.string.transition_section_looping),
                    subtitle = loopOptions.firstOrNull { it.value == resolvedConfig.loopMode }?.label.orEmpty(),
                    accentColor = TransitionLoopColor,
                    modifier = Modifier.weight(1f),
                    onClick = { activeSection = EditSection.LOOPING }
                )
                SpotifySettingCard(
                    title = stringResource(R.string.transition_section_beatmatch),
                    subtitle = tempoMatchOptions.firstOrNull { it.value == resolvedConfig.tempoMatch }?.label.orEmpty(),
                    accentColor = TransitionPlayheadColor,
                    modifier = Modifier.weight(1f),
                    onClick = { activeSection = EditSection.BEATMATCH }
                )
            }

            Spacer(Modifier.height(14.dp))

            // Row 3: Preset pills - the full ribbon: Auto keeps its sparkle icon,
            // and Blend / Wave / Melt / Slam / No transition join Fade and Rise.
            val generalPresets = listOf(
                MixPreset.AUTO,
                MixPreset.BLEND,
                MixPreset.RISE,
                MixPreset.FADE,
                MixPreset.WAVE,
                MixPreset.MELT,
                MixPreset.SLAM,
                MixPreset.NO_TRANSITION,
                MixPreset.CUSTOM
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(generalPresets) { preset ->
                    val isSelected = config.preset == preset
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (isSelected) Color.White else Color(0xFF2A2A2A),
                        contentColor = if (isSelected) Color.Black else Color.White,
                        modifier = Modifier.hapticClickable(kind = HapticKind.LIGHT) {
                            config = config.withPresetSelected(preset)
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            if (preset == MixPreset.AUTO) {
                                Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(14.dp), tint = if (isSelected) Color.Black else Color.White)
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                text = transitionMixPresetTitle(preset),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Option sheets, one per open section
            // Volume / EQ / Filter / Effects carry the customization button: its press -
            // or the Custom row - seeds hand points from the current curve and switches
            // the canvas into point editing. Looping and Beatmatch have no button: their
            // shapes are regions and switches, not curves.
            when (activeSection) {
                EditSection.VOLUME -> OptionPickerSheet(
                    title = stringResource(R.string.transition_section_volume),
                    accentColor = TransitionVolumeColor,
                    options = volumeOptions,
                    selected = resolvedConfig.volumeCurve,
                    hint = stringResource(R.string.volume_spotify_hint),
                    onDismiss = { activeSection = EditSection.NONE },
                    onSelect = {
                        config = config.withCurves(volume = it)
                        curveEditing = EditSection.NONE
                    },
                    showCustomize = true,
                    customizeSelected = resolvedConfig.hasCustomVolume,
                    onCustomize = {
                        config = config
                            .withCustomEditStarted(CurveOwner.VOLUME_OUT)
                            .withCustomEditStarted(CurveOwner.VOLUME_IN)
                        curveEditing = EditSection.VOLUME
                        activeSection = EditSection.NONE
                    },
                )

                EditSection.EQ -> OptionPickerSheet(
                    title = stringResource(R.string.transition_section_eq),
                    accentColor = TransitionEqColor,
                    options = eqOptions,
                    selected = resolvedConfig.eqMode,
                    hint = stringResource(R.string.eq_spotify_hint),
                    onDismiss = { activeSection = EditSection.NONE },
                    onSelect = {
                        config = config.withCurves(eq = it)
                        curveEditing = EditSection.NONE
                    },
                    showCustomize = true,
                    customizeSelected = resolvedConfig.hasCustomEq,
                    onCustomize = {
                        config = config
                            .withCustomEditStarted(CurveOwner.EQ_OUT)
                            .withCustomEditStarted(CurveOwner.EQ_IN)
                        curveEditing = EditSection.EQ
                        activeSection = EditSection.NONE
                    },
                )

                EditSection.FILTER -> OptionPickerSheet(
                    title = stringResource(R.string.transition_section_filter),
                    accentColor = TransitionFilterColor,
                    options = filterOptions,
                    selected = resolvedConfig.filterMode,
                    hint = stringResource(R.string.filter_spotify_hint),
                    onDismiss = { activeSection = EditSection.NONE },
                    onSelect = {
                        config = config.withCurves(filter = it)
                        curveEditing = EditSection.NONE
                    },
                    showCustomize = true,
                    customizeSelected = resolvedConfig.hasCustomFilter,
                    onCustomize = {
                        config = config
                            .withCustomEditStarted(CurveOwner.FILTER_OUT)
                            .withCustomEditStarted(CurveOwner.FILTER_IN)
                        curveEditing = EditSection.FILTER
                        activeSection = EditSection.NONE
                    },
                )

                EditSection.EFFECTS -> OptionPickerSheet(
                    title = stringResource(R.string.transition_section_effects),
                    accentColor = TransitionEffectsColor,
                    options = effectOptions,
                    selected = resolvedConfig.effectMode,
                    hint = stringResource(R.string.effect_spotify_hint),
                    onDismiss = { activeSection = EditSection.NONE },
                    onSelect = {
                        // A hand-picked tail is a custom shape; turning it back off
                        // keeps whatever preset the curves imply.
                        config = config.withEffect(it)
                        curveEditing = EditSection.NONE
                    },
                    showCustomize = true,
                    customizeSelected = resolvedConfig.hasCustomCurve(CurveOwner.EFFECT),
                    onCustomize = {
                        config = config.withCustomEditStarted(CurveOwner.EFFECT)
                        curveEditing = EditSection.EFFECTS
                        activeSection = EditSection.NONE
                    },
                )

                EditSection.LOOPING -> OptionPickerSheet(
                    title = stringResource(R.string.transition_section_looping),
                    accentColor = TransitionLoopColor,
                    options = loopOptions,
                    selected = resolvedConfig.loopMode,
                    hint = stringResource(R.string.looping_spotify_hint),
                    onDismiss = { activeSection = EditSection.NONE },
                    onSelect = {
                        config = config.copy(
                            loopMode = it,
                            preset = if (it != LoopTransitionMode.NONE && config.preset != MixPreset.CUSTOM) {
                                MixPreset.CUSTOM
                            } else {
                                config.preset
                            },
                        )
                    }
                )

                EditSection.BEATMATCH -> OptionPickerSheet(
                    title = stringResource(R.string.transition_section_beatmatch),
                    accentColor = TransitionPlayheadColor,
                    options = tempoMatchOptions,
                    selected = resolvedConfig.tempoMatch,
                    hint = stringResource(R.string.beatmatch_spotify_hint),
                    onDismiss = { activeSection = EditSection.NONE },
                    onSelect = {
                        // The beatmatch is orthogonal to the curve ribbon - it never
                        // flips the preset, exactly like Spotify's separate screen.
                        config = config.copy(tempoMatch = it)
                    }
                )

                EditSection.NONE -> {}
            }

            Spacer(Modifier.height(32.dp))
        }

        // Modal "Discard changes?" dialog
        if (showDiscardDialog) {
            BasicAlertDialog(
                onDismissRequest = { showDiscardDialog = false },
                properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    contentColor = Color.Black,
                    modifier = Modifier.padding(16.dp).fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.discard_changes_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.discard_changes_text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Black.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = { showDiscardDialog = false },
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TransitionPlayheadColor,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.keep_editing),
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                showDiscardDialog = false
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.discard),
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black
                            )
                        }
                    }
                }
            }
        }

    }
}

// The options popup, one sheet for every control - modeled on the reference
// editor's Looping panel: a dark sheet with a drag handle, the control's name
// next to its accent bar, one row per option with the green check on the chosen
// one. The controls whose shape is a curve (Volume, EQ, Filter, Effects) also
// carry the customization pill on the title's right and a Custom row: pressing
// either seeds hand points from the current curve and switches the canvas into
// point editing. Looping and Beatmatch have no customize affordance - their
// options are regions and switches, not curves.
@Composable
private fun <T> OptionPickerSheet(
    title: String,
    accentColor: Color,
    options: List<TransitionOption<T>>,
    selected: T,
    hint: String,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
    showCustomize: Boolean = false,
    customizeSelected: Boolean = false,
    onCustomize: () -> Unit = {},
) {
    KittyModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
    ) {
        Spacer(Modifier.height(2.dp))

        // Title row: the accent bar, the control's name, and - for curve controls -
        // the customization pill on the right, same placement as the reference
        // editor's bottom bar.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            if (showCustomize) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (customizeSelected) TransitionPlayheadColor else Color(0xFF2A2A2A),
                    contentColor = if (customizeSelected) Color.Black else Color.White,
                    modifier = Modifier
                        .size(40.dp)
                        .hapticClickable(kind = HapticKind.LIGHT) { onCustomize() }
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Rounded.Tune,
                            contentDescription = stringResource(R.string.customize_curve),
                            tint = if (customizeSelected) Color.Black else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // The Custom row, above every named option - the hand-placed curve. Present on
        // the curve controls only; the green check marks it while points are placed.
        if (showCustomize) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .hapticClickable(kind = HapticKind.LIGHT) { onCustomize() }
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Text(
                    text = stringResource(R.string.edit_transition_preset_custom),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (customizeSelected) FontWeight.Bold else FontWeight.Normal,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                if (customizeSelected) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = TransitionPlayheadColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // One row per option; the chosen one wears the green check.
        options.forEach { opt ->
            val isOptSelected = opt.value == selected && !(showCustomize && customizeSelected)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .hapticClickable(kind = HapticKind.LIGHT) { onSelect(opt.value) }
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Text(
                    text = opt.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isOptSelected) FontWeight.Bold else FontWeight.Normal,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                if (isOptSelected) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = TransitionPlayheadColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )
        if (showCustomize) {
            Text(
                text = stringResource(R.string.curve_editor_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SpotifySettingCard(
    title: String,
    subtitle: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF242424),
        contentColor = Color.White,
        modifier = modifier
            .height(58.dp)
            .hapticClickable(kind = HapticKind.LIGHT, onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 6.dp, end = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f),
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TransitionTrackCardSpotify(
    track: Track,
    bpm: Int,
    camelot: String?,
    showOrangeDot: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        val art = track.thumbnailUrl
        AsyncImage(
            model = remember(art) { if (art.isNullOrEmpty()) null else if (art.startsWith("/")) java.io.File(art) else art },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF2A2A2A)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title ?: stringResource(R.string.untitled_track),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.displayArtist.ifBlank { stringResource(R.string.unknown_artist) },
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showOrangeDot) {
                    Text(
                        text = "● ",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFFF9800),
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = if (bpm > 0) stringResource(R.string.mix_badge_bpm_only, bpm)
                    else stringResource(R.string.mix_bpm_unknown),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                camelot?.let { CamelotKeyBadge(code = it) }
                track.durationMs?.takeIf { it > 0 }?.let {
                    Text(
                        text = makeTimeString(it),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                }
            }
        }
    }
}

@Composable
fun CamelotKeyBadge(code: String, modifier: Modifier = Modifier) {
    val wheel = camelotWheelColor(code)
    val container = wheel ?: Color(0xFF333333)
    val content = when {
        wheel == null -> Color.White
        wheel.needsLightContent() -> Color.White
        else -> Color.Black
    }
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = container,
        contentColor = content,
        modifier = modifier,
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}

fun transitionMixPresetIcon(preset: MixPreset): ImageVector = when (preset) {
    MixPreset.AUTO -> Icons.Rounded.AutoAwesome
    MixPreset.BLEND -> Icons.Rounded.CompareArrows
    MixPreset.RISE -> Icons.Rounded.TrendingUp
    MixPreset.FADE -> Icons.Rounded.Waves
    MixPreset.WAVE -> Icons.Rounded.GraphicEq
    MixPreset.MELT -> Icons.Rounded.Album
    MixPreset.SLAM -> Icons.Rounded.FlashOn
    MixPreset.DISSOLVE -> Icons.Rounded.BlurOn
    MixPreset.NO_TRANSITION -> Icons.Rounded.Album
    MixPreset.CUSTOM -> Icons.Rounded.Tune
}

@Composable
fun transitionMixPresetTitle(preset: MixPreset): String = when (preset) {
    MixPreset.AUTO -> stringResource(R.string.edit_transition_preset_auto)
    else -> stringResource(transitionPresetTitleRes(preset))
}

private fun transitionPresetTitleRes(preset: MixPreset): Int = when (preset) {
    MixPreset.CUSTOM -> R.string.edit_transition_preset_custom
    MixPreset.AUTO -> R.string.edit_transition_preset_auto
    MixPreset.FADE -> R.string.edit_transition_preset_fade
    MixPreset.RISE -> R.string.edit_transition_preset_rise
    MixPreset.BLEND -> R.string.edit_transition_preset_blend
    MixPreset.WAVE -> R.string.edit_transition_preset_wave
    MixPreset.MELT -> R.string.edit_transition_preset_melt
    MixPreset.SLAM -> R.string.edit_transition_preset_slam
    MixPreset.DISSOLVE -> R.string.edit_transition_preset_dissolve
    MixPreset.NO_TRANSITION -> R.string.edit_transition_preset_none
}

fun transitionPresetIcon(preset: TransitionPreset): ImageVector =
    transitionMixPresetIcon(preset.toMixPresetForConnector())

@Composable
fun transitionPresetTitle(preset: TransitionPreset): String =
    transitionMixPresetTitle(preset.toMixPresetForConnector())

private fun TransitionPreset.toMixPresetForConnector(): MixPreset = when (this) {
    TransitionPreset.AUTO -> MixPreset.AUTO
    TransitionPreset.BLEND -> MixPreset.BLEND
    TransitionPreset.RISE -> MixPreset.RISE
    TransitionPreset.FADE -> MixPreset.FADE
    TransitionPreset.WAVE -> MixPreset.WAVE
    TransitionPreset.MELT -> MixPreset.MELT
    TransitionPreset.SLAM -> MixPreset.SLAM
    TransitionPreset.DISSOLVE -> MixPreset.DISSOLVE
    TransitionPreset.NONE -> MixPreset.NO_TRANSITION
    TransitionPreset.CUSTOM -> MixPreset.CUSTOM
}
