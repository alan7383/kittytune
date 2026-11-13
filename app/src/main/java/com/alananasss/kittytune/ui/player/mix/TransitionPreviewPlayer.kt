/**
 * Developed by Jason-Marshall Fastner, Germany <jasonfastner@protonmail.com>
 * Questions, feedback, or beat-matching debates? Feel free to reach out via email!
 *
 * Note: Cats always land on their feet, and with this engine, your transitions will too.
 */
package com.alananasss.kittytune.ui.player.mix

import android.content.Context
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.alananasss.kittytune.audio.automix.AutomixDuckAudioProcessor
import com.alananasss.kittytune.audio.automix.EffectTransitionMode
import com.alananasss.kittytune.audio.automix.EqTransitionMode
import com.alananasss.kittytune.audio.automix.LoopTransitionMode
import com.alananasss.kittytune.audio.automix.MixTransitionPresets
import com.alananasss.kittytune.audio.automix.TempoMatchMode
import com.alananasss.kittytune.audio.automix.TransitionConfig
import com.alananasss.kittytune.audio.automix.TransitionEffectsAudioProcessor
import com.alananasss.kittytune.audio.automix.TransitionSweepAudioProcessor
import com.alananasss.kittytune.audio.automix.bassLevelAt
import com.alananasss.kittytune.audio.automix.curveLevelAt
import com.alananasss.kittytune.audio.automix.filterInFractionAt
import com.alananasss.kittytune.audio.automix.filterLegIsHighPass
import com.alananasss.kittytune.audio.automix.filterOutFractionAt
import com.alananasss.kittytune.audio.automix.volumeInAt
import com.alananasss.kittytune.audio.automix.volumeOutAt
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.ResolvedStream
import com.alananasss.kittytune.data.SoundCloudDrmCallback
import com.alananasss.kittytune.data.StreamResolver
import com.alananasss.kittytune.data.local.ExoCacheManager
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.utils.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

// The transition editor's real two-deck preview engine - what turns the canvas' green
// playhead from a metronome into an actual DJ rehearsal.
//
// Two independent lightweight [ExoPlayer] instances carry the run:
//
//  - **Deck A** (outgoing) is seeked to `zoneStart − 3 s`, so the rehearsal opens with
//    [PREVIEW_LEAD_IN_MS] of the outgoing track playing on its own - the run-in;
//  - **Deck B** (incoming) waits paused at its entry point during the run-in, then joins
//    exactly when the zone opens (`0 ms + nudge`), stretched to deck A's tempo;
//  - after the zone closes (progress ≥ 1) deck A hands the speakers over and deck B
//    plays on alone for [PREVIEW_TAIL_OUT_MS] - the run-out.
//
// So one press of Play performs the whole bracket: 3 s of the upper track -> the
// transition itself -> 3 s of the lower track, then the run wraps and repeats.
//
// Both decks share the app's ExoCache (already-downloaded audio plays offline) and reuse
// [StreamResolver.resolveStreamWithDrm] - local files, SoundCloud progressive/CENC, YouTube, VK and
// provider streams all resolve through the same path the main player uses. A CENC stream's
// Widevine token is honoured with the same offline/streaming session split the beat decoder
// uses.
//
// The editor's 16 ms frame loop drives everything through [updateFrame]: deck gains come
// from the very same [volumeOutAt]/[volumeInAt] envelopes the canvas draws, the bass
// handover rides two [AutomixDuckAudioProcessor] low-shelves (one per deck, ownership from
// [bassLevelAt] - including the Quick/Long bass cuts), the filter sweep rides two
// [TransitionSweepAudioProcessor] SVFs fed by [filterOutFractionAt]/[filterInFractionAt],
// and the effect tail rides [TransitionEffectsAudioProcessor.setTail] - so what you see
// drawn is exactly what you hear. Outside the zone ([progress] < 0 or > 1) both decks run
// dry and solo: only the outgoing deck in the run-in, only the incoming deck in the run-out.
//
// [prepareState] tells the editor whether audio is still resolving, ready, or degraded to
// the silent playhead - the preview never blocks on a slow stream silently.
//
// Threading: create and call from the UI thread (the editor owns the lifecycle -
// [androidx.compose.runtime.DisposableEffect] releases it). [prepare] is suspend; the two
// stream resolutions run in parallel. The run-in only begins once both *resolved* decks
// are STATE_READY, so the decks land on the run together; a deck whose stream failed is
// excluded and the preview runs with whatever resolved.
@UnstableApi
class TransitionPreviewPlayer(
    private val context: Context,
    private val trackA: Track,
    private val trackB: Track,
) {

    // One preview deck: its player plus the processors the sync loop automates.
    private class Deck(
        val player: ExoPlayer,
        val shelf: AutomixDuckAudioProcessor,
        val sweep: TransitionSweepAudioProcessor,
        val effects: TransitionEffectsAudioProcessor,
        val stereoNormalizer: com.alananasss.kittytune.audio.automix.StereoNormalizerAudioProcessor,
    ) {
        // Set on a permanent player error; the deck then drops out of the preview.
        var failed = false
    }

    // What the editor can show beside the play button.
    enum class PrepareState { RESOLVING, READY, NO_AUDIO }

    private val _prepareState = kotlinx.coroutines.flow.MutableStateFlow(PrepareState.RESOLVING)
    val prepareState: kotlinx.coroutines.flow.StateFlow<PrepareState> = _prepareState

    private var deckA: Deck? = null
    private var deckB: Deck? = null
    private var prepared = false

    // The beat-lock ratio: deck A's tempo over deck B's. Deck B stretches to it (Sonic,
    // pitch-preserving) so both grids line up in the overlap; 1f leaves it untouched.
    @Volatile
    private var beatLockRatio: Float = 1f

    // The editor pressed Play and has not pressed Pause since.
    @Volatile
    private var startRequested = false

    // Whether the deck pair has actually started the current armed preview run.
    private var audioRunning = false

    // The currently armed shape (the editor's resolved config) and overlap length.
    private var config = TransitionConfig()
    private var overlapMs = 0L

    // The run-in this armed run uses, in ms - usually the full [PREVIEW_LEAD_IN_MS],
    // shortened when the outgoing track's zone sits closer to its head than that. The
    // editor reads it back through [effectiveLeadInMs] so its clock and the decks'
    // positions always agree.
    private var armedLeadInMs = 0L

    // Whether the incoming deck has already been released into the zone this run.
    private var zoneEntered = false

    // One beat on the preview's own grid, set at [arm] - the effects' and loop's clock.
    private var beatMs = 500f

    // Whether this preview is the one that paused the main PlaybackService.
    private var mainPlaybackPaused = false

    // Resolves both tracks' streams (in parallel) and builds the two decks, seeked to the
    // run's start and left paused. Cheap to re-enter; the decks are built once per instance.
    suspend fun prepare() {
        if (prepared) return
        _prepareState.value = PrepareState.RESOLVING
        // resolveStreamWithDrm (not the String-only resolveStream): the decks need the whole
        // ResolvedStream - the URL *and* the Widevine licenseAuthToken CENC streams carry.
        val resolved = coroutineScope {
            val a = async(Dispatchers.IO) {
                runCatching { StreamResolver.resolveStreamWithDrm(context, trackA) }.getOrNull()
            }
            val b = async(Dispatchers.IO) {
                runCatching { StreamResolver.resolveStreamWithDrm(context, trackB) }.getOrNull()
            }
            a.await() to b.await()
        }
        if (prepared) return // a release() slipped in while the streams were resolving
        withContext(Dispatchers.Main) {
            if (prepared) return@withContext
            deckA = resolved.first?.let { buildDeck(it) }
            deckB = resolved.second?.let { buildDeck(it) }
            prepared = true
            _prepareState.value = when {
                deckA != null && deckB != null -> PrepareState.READY
                deckA != null || deckB != null -> PrepareState.READY
                else -> PrepareState.NO_AUDIO
            }
            Log.d(
                TAG,
                "Preview decks ready (A=${deckA != null}, B=${deckB != null}); " +
                    "startRequested=$startRequested",
            )
            trySyncStart()
        }
    }

    // Arms the shape the editor currently draws; the next [start] obeys it.
    fun arm(newConfig: TransitionConfig, overlapDurationMs: Long) {
        config = newConfig
        overlapMs = overlapDurationMs.coerceAtLeast(1L)
        // toFloat() before the division: Long / Int would be *integer* division
        // (7500 / 16 = 468 instead of 468.75) and would not type as Float.
        beatMs = if (config.duration.beats > 0) overlapMs.toFloat() / config.duration.beats else 500f
    }

    // Live curve update while previewing: hand-placed points (and named-mode switches,
    // which change envelopes but not positions) reach the running decks on the very next
    // [updateFrame] - dragging a point must not re-seek or restart the audio the way a
    // full re-[arm]+[start] would.
    fun updateCurves(newConfig: TransitionConfig) {
        config = newConfig
    }

    // Beat-lock as in Spotify Mix, with the editor's per-pair toggle:
    //  - [TempoMatchMode.AUTO] - match only within ±10 BPM (octaves 2× / 0.5× folded in);
    //  - [TempoMatchMode.MATCH] - force the stretch inside the safe ±15 % window;
    //  - [TempoMatchMode.ORIGINAL] - both decks keep their own tempo.
    fun setTempoPair(outBpm: Float, inBpm: Float, mode: TempoMatchMode = TempoMatchMode.AUTO) {
        val (canMatch, ratio) = calculateSpotifyBpmMatch(outBpm, inBpm)
        beatLockRatio = when (mode) {
            TempoMatchMode.ORIGINAL -> 1f
            TempoMatchMode.MATCH -> {
                val folded = listOf(inBpm, inBpm * 2f, inBpm * 0.5f)
                    .minByOrNull { kotlin.math.abs(outBpm - it) } ?: inBpm
                if (folded > 0f && outBpm > 0f) (outBpm / folded).coerceIn(0.85f, 1.15f) else 1f
            }
            TempoMatchMode.AUTO -> if (canMatch) ratio else 1f
        }
        deckB?.takeIf { !it.failed }?.let { deck ->
            runCatching {
                // Pitch lock preserved via Sonic
                deck.player.playbackParameters = PlaybackParameters(beatLockRatio)
            }
        }
    }

    // Live nudge while previewing: dragging deck B's wave re-seeks the incoming deck to the
    // new entry point without touching the run. During the run-in the deck simply waits at
    // the new entry; it is released there when the zone opens.
    fun updateIncomingOffset(incomingStartMs: Long) {
        config = config.copy(userNudgeOffsetMs = incomingStartMs)
        if (!audioRunning) return
        deckB?.takeIf { !it.failed }?.let { deck ->
            runCatching { deck.player.seekTo(incomingEntryMs(deck.player.duration)) }
        }
    }

    // Begins (or restarts) the previewed run: the lead-in is (re)derived from what deck A
    // can honor, both decks re-seek to the run's start - deck A at the run-in position,
    // deck B paused at its entry - and playback begins the moment the decks are ready.
    fun start() {
        // The run-in is derived here, once per run, so the editor's clock (which reads it
        // back through [effectiveLeadInMs] right after this call) and the decks' seeks
        // always share the same number.
        armedLeadInMs = computeArmedLeadInMs()
        if (!prepared) {
            startRequested = true // audio joins the running playhead once prepare lands
            return
        }
        startRequested = true
        audioRunning = false
        seekPreviewStart()
        updateFrame(-1f)
        trySyncStart()
    }

    // The run-in this armed run uses - the editor's clock spans it before the zone.
    fun effectiveLeadInMs(): Long = armedLeadInMs.coerceIn(0L, PREVIEW_LEAD_IN_MS)

    // Stops the preview and rewinds both decks to the run's start, so the next Play opens
    // the rehearsal from the run-in again - and the editor's button flips back to Play.
    fun pauseAndReset() {
        startRequested = false
        audioRunning = false
        zoneEntered = false
        deckA?.takeIf { !it.failed }?.let { deck ->
            runCatching {
                deck.player.pause()
                deck.player.seekTo(previewStartMs(deck.player.duration))
            }
        }
        deckB?.takeIf { !it.failed }?.let { deck ->
            runCatching {
                deck.player.pause()
                deck.player.seekTo(incomingEntryMs(deck.player.duration))
            }
        }
    }

    // The end of the run (the editor leaves the screen or toggles Play off): stop
    // playback, park the decks and hand the speakers back to the main player.
    fun finishAndRelease() {
        pauseAndReset()
        if (mainPlaybackPaused) {
            mainPlaybackPaused = false
        }
    }

    // One 16 ms sync tick. [progress] is the zone's own coordinate: `0..1` inside the
    // transition, **negative** during the run-in (only the outgoing deck, solo and dry)
    // and **above 1** during the run-out (only the incoming deck, solo and dry). The very
    // first frame with `progress ≥ 0` releases the incoming deck into the zone.
    fun updateFrame(progress: Float) {
        if (progress < 0f) {
            // The run-in: the outgoing track alone, exactly as it will sound in the
            // mix right before the seam - no shelves, no sweeps, no tail.
            deckA?.takeIf { !it.failed }?.player?.volume = 1f
            deckB?.takeIf { !it.failed }?.player?.volume = 0f
            clearFrameProcessors()
            return
        }

        if (!zoneEntered) {
            // The zone just opened - release the incoming deck at its entry, stretched to
            // the outgoing deck's grid (the beat-lock set by setTempoPair).
            zoneEntered = true
            releaseIncomingDeck()
        }

        if (progress > 1f) {
            // The run-out: the transition is over, the incoming track plays on alone.
            // If the incoming deck never made it (stream failed), the outgoing deck simply
            // keeps the floor instead of fading the run into silence.
            val incomingAlive = deckB?.let { !it.failed } == true
            deckA?.takeIf { !it.failed }?.let { deck ->
                runCatching { if (incomingAlive) deck.player.pause() }
                deck.player.volume = if (incomingAlive) 0f else 1f
            }
            deckB?.takeIf { !it.failed }?.player?.volume = 1f
            clearFrameProcessors()
            return
        }

        val t = progress.coerceIn(0f, 1f)

        // Gains - the exact envelopes the canvas draws (computeVolA / computeVolB).
        // Hand-placed curves (the customization button) win over the named modes.
        val gainA = curveLevelAt(config.customVolumeOut, t) ?: volumeOutAt(config.volumeCurve, t)
        val gainB = curveLevelAt(config.customVolumeIn, t) ?: volumeInAt(config.volumeCurve, t)
        deckA?.takeIf { !it.failed }?.player?.volume = gainA
        deckB?.takeIf { !it.failed }?.player?.volume = gainB

        // Bass handover - each deck's low-shelf cuts deeper as it loses the lows. With no
        // swap the shelves stay dry and the bass simply rides the volume curves. Each deck
        // owns its own amber leg now: a hand curve on a leg replaces that deck's bass level
        // wholesale, the named swap modes give deck A [bassLevelAt] and deck B its complement.
        val bassActive = config.customEqOut.isNotEmpty() || config.customEqIn.isNotEmpty() ||
            config.eqMode != EqTransitionMode.NONE
        val bassLevelOut = curveLevelAt(config.customEqOut, t)
            ?: if (config.eqMode == EqTransitionMode.NONE) 1f else bassLevelAt(config.eqMode, t)
        val bassLevelIn = curveLevelAt(config.customEqIn, t)
            ?: if (config.eqMode == EqTransitionMode.NONE) 1f else 1f - bassLevelAt(config.eqMode, t)
        deckA?.shelf?.setMix(if (bassActive) 1f - bassLevelOut else 0f)
        deckB?.shelf?.setMix(if (bassActive) 1f - bassLevelIn else 0f)

        // The filter sweep, leg by leg: each deck gets its own tap and cutoff, and a mode
        // that sweeps both decks (e.g. high-pass out + low-pass in) runs them together.
        val sweepA = deckA?.sweep
        val sweepB = deckB?.sweep
        val legA = curveLevelAt(config.customFilterOut, t) ?: filterOutFractionAt(config.filterMode, t)
        val legB = curveLevelAt(config.customFilterIn, t) ?: filterInFractionAt(config.filterMode, t)
        if (legA != null) {
            val highPass = filterLegIsHighPass(config.filterMode, isOutgoingDeck = true)
            sweepA?.setSweep(
                if (highPass) TransitionSweepAudioProcessor.SweepMode.HIGH_PASS
                else TransitionSweepAudioProcessor.SweepMode.LOW_PASS,
                legA,
            )
        } else {
            sweepA?.clear()
        }
        if (legB != null) {
            val highPass = filterLegIsHighPass(config.filterMode, isOutgoingDeck = false)
            sweepB?.setSweep(
                if (highPass) TransitionSweepAudioProcessor.SweepMode.HIGH_PASS
                else TransitionSweepAudioProcessor.SweepMode.LOW_PASS,
                legB,
            )
        } else {
            sweepB?.clear()
        }

        // The dramatic exit - the white curve's tail on the outgoing deck: a damped feedback
        // delay whose wet level is the exact envelope the canvas draws. Beat-timed spacing
        // for the delay/echo modes, a short fixed tail for the reverb modes; a hand-drawn
        // curve drives the wet level by itself (fixed short tail spacing).
        val wetLevel = curveLevelAt(config.customEffect, t) ?: previewEffectWetAt(config.effectMode, t)
        val effectActive = config.customEffect.isNotEmpty() || config.effectMode != EffectTransitionMode.NONE
        if (effectActive) {
            val delayBeats = previewEffectDelayBeats(config.effectMode)
            val delayMs = if (delayBeats > 0f) delayBeats * beatMs else 95f
            deckA?.effects?.setTail(wetLevel, delayMs, previewEffectFeedback(config.effectMode))
        } else {
            deckA?.effects?.resetTail()
        }
        deckB?.effects?.resetTail()
    }

    // The encore - keeps deck A inside its loop region while the zone runs, exactly like
    // the engine does mid-transition: the first N beats of the overlap window repeat until
    // the volume curve carries the deck out. Outside the zone it is a no-op (the deck sits
    // before the region, or has already handed the floor over).
    fun applyLoop() {
        val beats = previewLoopBeats(config.loopMode)
        if (beats <= 0) return
        val deck = deckA?.takeIf { !it.failed } ?: return
        if (!deck.player.isPlaying) return
        val duration = deck.player.duration
        val zoneStart = zoneStartMs(duration, trackA)
        val zoneEnd = zoneStart + (beats * beatMs).toLong()
        if (zoneEnd <= zoneStart) return
        if (deck.player.currentPosition >= zoneEnd) {
            runCatching { deck.player.seekTo(zoneStart) }
        }
    }

    // Wraps the run back over its own start, matching the playhead's wrap: deck A jumps
    // back to the run-in position and keeps playing, deck B is parked paused at its entry
    // until the zone opens again.
    fun restartRun() {
        zoneEntered = false
        deckA?.takeIf { !it.failed }?.let {
            runCatching {
                it.player.seekTo(previewStartMs(it.player.duration))
                if (startRequested) it.player.play()
            }
        }
        deckB?.takeIf { !it.failed }?.let {
            runCatching {
                it.player.pause()
                it.player.seekTo(incomingEntryMs(it.player.duration))
            }
        }
    }

    // Parks both decks at the zone's own start (no run-in) - the classic seek used when
    // only the seam itself is wanted.
    fun seekZoneStart() {
        zoneEntered = false
        deckA?.takeIf { !it.failed }?.let { runCatching { it.player.seekTo(zoneStartMs(it.player.duration, trackA)) } }
        deckB?.takeIf { !it.failed }?.let { runCatching { it.player.seekTo(incomingEntryMs(it.player.duration)) } }
    }

    // Seeks both decks to the run's own start: deck A at the run-in position (the zone
    // start pulled back by the armed lead-in), deck B paused at its entry point.
    private fun seekPreviewStart() {
        zoneEntered = false
        deckA?.takeIf { !it.failed }?.let {
            runCatching { it.player.seekTo(previewStartMs(it.player.duration)) }
        }
        deckB?.takeIf { !it.failed }?.let {
            runCatching {
                it.player.pause()
                it.player.seekTo(incomingEntryMs(it.player.duration))
            }
        }
    }

    // Tears both decks down - the editor's `DisposableEffect` calls this on dispose.
    fun release() {
        startRequested = false
        audioRunning = false
        zoneEntered = false
        prepared = false
        _prepareState.value = PrepareState.NO_AUDIO
        listOfNotNull(deckA, deckB).forEach { deck ->
            runCatching {
                deck.effects.resetTail()
                deck.player.playbackParameters = PlaybackParameters(1f)
                deck.player.stop()
                deck.player.clearMediaItems()
                deck.player.release()
            }.onFailure { Log.w(TAG, "Preview deck release failed", it) }
        }
        deckA = null
        deckB = null
    }


    // Plays the armed run once every non-failed deck is STATE_READY - one shared trigger,
    // so the run-in opens with both decks loaded. Only deck A starts immediately; deck B
    // stays parked (paused at its entry) and is released into the zone by the first
    // in-zone [updateFrame] frame.
    private fun trySyncStart() {
        if (!startRequested || audioRunning) return
        val decks = listOfNotNull(deckA, deckB).filter { !it.failed }
        if (decks.isEmpty()) return
        if (decks.any { it.player.playbackState != Player.STATE_READY }) return
        if (!mainPlaybackPaused) {
            // The preview owns the speakers while it runs; the main PlaybackService yields
            // (the same hand-over the Wrapped story player performs).
            runCatching {
                if (MusicManager.player.isPlaying) MusicManager.player.pause()
            }
            mainPlaybackPaused = true
        }
        seekPreviewStart()
        decks.forEach { deck -> runCatching {
            if (deck === deckB) {
                // The beat-lock rides along for the moment the zone opens; the deck itself
                // waits paused at its entry until updateFrame releases it.
                deck.player.playbackParameters = PlaybackParameters(beatLockRatio)
            } else {
                deck.player.play()
            }
        } }
        audioRunning = true
    }

    // The run-in this pair can honor: three seconds of the outgoing track, unless its zone
    // starts closer to the head of the track than that (a zone at 1.2 s gets a 1.2 s
    // run-in, so the decks still land on the zone together), or the zone sits at the very
    // head (no run-in at all). Unknown durations assume the full run-in - the decks are
    // still resolving and the seek will clamp when they land.
    private fun computeArmedLeadInMs(): Long {
        val deck = deckA?.takeIf { !it.failed } ?: return PREVIEW_LEAD_IN_MS
        val duration = deck.player.duration
            .takeIf { it != C.TIME_UNSET && it > 0L }
            ?: trackA.actualDurationMs.takeIf { it > 0L }
            ?: return PREVIEW_LEAD_IN_MS
        val zoneStart = zoneStartMs(duration, trackA)
        return if (zoneStart <= 0L) 0L else PREVIEW_LEAD_IN_MS.coerceAtMost(zoneStart)
    }

    // Where the run begins on deck A: the zone start pulled back by the armed lead-in.
    private fun previewStartMs(deckDurationMs: Long): Long =
        (zoneStartMs(deckDurationMs, trackA) - armedLeadInMs).coerceAtLeast(0L)

    // Where the overlap begins on deck A: the hand-placed anchor when the editor drew one,
    // otherwise its duration minus the zone, plus the nudge, never before 0.
    private fun zoneStartMs(deckDurationMs: Long, outgoing: Track): Long {
        val duration = deckDurationMs
            .takeIf { it != C.TIME_UNSET && it > 0L }
            ?: outgoing.actualDurationMs.takeIf { it > 0L }
            ?: return 0L
        // The editor's drag pins the window in the outgoing track - the preview must
        // rehearse the seam from exactly there, not from the default end-of-track point.
        config.zoneStartMs.takeIf { it >= 0L }?.let { anchor ->
            val maxStart = (duration - overlapMs / 2).coerceAtLeast(0L)
            return anchor.coerceIn(0L, maxStart)
        }
        return (duration - overlapMs + config.userNudgeOffsetMs.coerceAtMost(overlapMs / 2))
            .coerceIn(0L, (duration - 1).coerceAtLeast(0L))
    }

    // Where deck B enters: the config's hand-drawn nudge (the editor's drag, beat-snapped),
    // clamped so the deck still has the whole zone ahead of it.
    private fun incomingEntryMs(deckDurationMs: Long): Long {
        val offset = config.userNudgeOffsetMs.coerceAtLeast(0L)
        if (offset <= 0L) return 0L
        val duration = deckDurationMs.takeIf { it != C.TIME_UNSET && it > 0L }
            ?: return offset
        return offset.coerceAtMost((duration - overlapMs).coerceAtLeast(0L))
    }

    // Releases the incoming deck into the zone: seeked to its entry, stretched, playing.
    private fun releaseIncomingDeck() {
        deckB?.takeIf { !it.failed }?.let { deck ->
            runCatching {
                deck.player.playbackParameters = PlaybackParameters(beatLockRatio)
                deck.player.seekTo(incomingEntryMs(deck.player.duration))
                deck.player.play()
            }
        }
    }

    // Both decks dry and clean - used by the run-in and run-out frames.
    private fun clearFrameProcessors() {
        deckA?.shelf?.setMix(0f)
        deckB?.shelf?.setMix(0f)
        deckA?.sweep?.clear()
        deckB?.sweep?.clear()
        deckA?.effects?.resetTail()
        deckB?.effects?.resetTail()
    }

    // Builds one deck: cache-backed data source, Widevine for CENC streams, and an audio
    // sink chain of the automatable processors - the stereo normalizer, the bass shelf,
    // the sweep SVF and the effect tail.
    private fun buildDeck(stream: ResolvedStream): Deck {
        val shelf = AutomixDuckAudioProcessor(
            shelfFrequencyHz = BASS_SHELF_HZ,
            maxDuckDb = BASS_SHELF_CUT_DB,
        )
        val sweep = TransitionSweepAudioProcessor()
        val effects = TransitionEffectsAudioProcessor()
        val stereoNormalizer = com.alananasss.kittytune.audio.automix.StereoNormalizerAudioProcessor()

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(Config.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)

        val cacheFactory = CacheDataSource.Factory()
            .setCache(ExoCacheManager.getCache(context))
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context, httpFactory))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val token = stream.licenseAuthToken
        // The same offline/streaming split the beat decoder uses: a pre-provisioned offline
        // key set id plays with no license server round-trip, everything else streams.
        var offlineKeySetId: ByteArray? = null
        if (!token.isNullOrEmpty() && !token.contains(".") && token.length < 120) {
            offlineKeySetId = runCatching {
                android.util.Base64.decode(token, android.util.Base64.NO_WRAP)
            }.getOrNull()
        }
        val drmProvider = DrmSessionManagerProvider {
            if (!token.isNullOrEmpty()) {
                val offline = offlineKeySetId
                if (offline != null && offline.isNotEmpty()) {
                    DefaultDrmSessionManager.Builder()
                        .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                        .build(LocalMediaDrmCallback(ByteArray(0)))
                        .apply { setMode(DefaultDrmSessionManager.MODE_PLAYBACK, offline) }
                } else {
                    DefaultDrmSessionManager.Builder()
                        .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                        .setMultiSession(true)
                        .build(SoundCloudDrmCallback(token))
                }
            } else {
                DrmSessionManager.DRM_UNSUPPORTED
            }
        }

        val mediaItem = MediaItem.Builder().setUri(stream.url).apply {
            val isHls = stream.url.contains(".m3u8")
            if (isHls) setMimeType(MimeTypes.APPLICATION_M3U8)
            if (!token.isNullOrEmpty()) {
                setMimeType(MimeTypes.APPLICATION_M3U8)
                setDrmConfiguration(MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID).build())
            }
        }.build()

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf(stereoNormalizer, shelf, sweep, effects))
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .build()
        }

        val player = ExoPlayer.Builder(context.applicationContext)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(cacheFactory)
                    .setDrmSessionManagerProvider(drmProvider),
            )
            .setRenderersFactory(renderersFactory)
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) trySyncStart()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.w(TAG, "Preview deck error, dropping it from the preview: ${error.message}")
                        markFailed(this@apply)
                        trySyncStart()
                    }
                })
                setMediaItem(mediaItem)
                prepare()
            }

        return Deck(player, shelf, sweep, effects, stereoNormalizer)
    }

    private fun markFailed(player: ExoPlayer) {
        listOfNotNull(deckA, deckB).firstOrNull { it.player === player }?.let { deck ->
            deck.failed = true
            runCatching { deck.player.pause() }
        }
    }

    companion object {
        private const val TAG = "TransitionPreview"

        // The preview's bass handover shelf: deep enough to hear the lows change hands.
        private const val BASS_SHELF_HZ = 120.0
        private const val BASS_SHELF_CUT_DB = -16.0

        // How much of the outgoing (upper) track plays on its own before the zone opens -
        // the run-in of every rehearsal.
        const val PREVIEW_LEAD_IN_MS = 3_000L

        // How much of the incoming (lower) track plays on its own after the zone closes -
        // the run-out of every rehearsal.
        const val PREVIEW_TAIL_OUT_MS = 3_000L

        // Calculates if tempo match should occur (within ±10 BPM) and the corresponding ratio.
        // Max pitch-preserving tempo stretch applied to the incoming deck (±8 %).
        const val MAX_TEMPO_STRETCH = 0.08f

        fun calculateSpotifyBpmMatch(outBpm: Float, inBpm: Float): Pair<Boolean, Float> {
            if (outBpm <= 0f || inBpm <= 0f) return false to 1f
            val candidates = listOf(inBpm, inBpm * 2f, inBpm * 0.5f)
            val bestCandidate = candidates.minByOrNull { kotlin.math.abs(outBpm - it) } ?: inBpm
            val diff = kotlin.math.abs(outBpm - bestCandidate)
            if (diff <= 10f) {
                val ratio = outBpm / bestCandidate
                // Sonic's pitch-preserving stretch turns audibly grainy (smeared kicks, metallic
                // ring) beyond ~8 %: 10 BPM at 70-80 BPM is already a 12-14 % stretch. The
                // ±10 BPM rule alone is not enough - the resulting ratio has to stay in range
                // too, otherwise the pair falls back to un-synced tempos + a style that does
                // not need beat-matching (Rise / Dissolve).
                if (ratio in (1f - MAX_TEMPO_STRETCH)..(1f + MAX_TEMPO_STRETCH)) {
                    return true to ratio
                }
            }
            return false to 1f
        }
    }
}

// The effect tail's envelopes - the exact shapes the editor's white curve draws,
// mirrored here (prefixed) so the preview always performs what the canvas shows,
// independent of which model file revision the host app carries.

// The outgoing deck's effects wet level (0..1) at [x] - the white line the canvas draws and
// the tail the decks perform. Zero means a dry deck; the envelope mirrors the option names:
// "cut end" pulls the tail back before the seam lands, "out end" swells into it.
private fun previewEffectWetAt(mode: EffectTransitionMode, x: Float): Float {
    val p = x.coerceIn(0f, 1f)
    return when (mode) {
        EffectTransitionMode.REVERB_OUT_CENTER ->
            MixTransitionPresets.smoothstep(0.05f, 0.35f, p) // the room takes the track at the swap
        EffectTransitionMode.REVERB_CUT_END ->
            MixTransitionPresets.smoothstep(0.10f, 0.55f, p) *
                (1f - MixTransitionPresets.smoothstep(0.85f, 0.95f, p))
        EffectTransitionMode.REVERB_OUT_END ->
            MixTransitionPresets.smoothstep(0.35f, 0.90f, p) // the tail swells into the handover
        EffectTransitionMode.DELAY_HALF_CUT_END,
        EffectTransitionMode.DELAY_THREE_QUARTER_CUT_END ->
            MixTransitionPresets.smoothstep(0.15f, 0.50f, p) *
                (1f - MixTransitionPresets.smoothstep(0.80f, 0.92f, p))
        EffectTransitionMode.ECHO_HALF_CUT_END ->
            MixTransitionPresets.smoothstep(0.10f, 0.45f, p) *
                (1f - MixTransitionPresets.smoothstep(0.75f, 0.90f, p))
        EffectTransitionMode.ECHO_HALF_OUT_END ->
            MixTransitionPresets.smoothstep(0.30f, 0.95f, p) // repeat… repeat… into the new track
        EffectTransitionMode.NONE -> 0f
    }
}

// The repeats' spacing in beats. Zero means the reverb's fixed short tail - not beat-timed,
// the delay/echo modes are.
private fun previewEffectDelayBeats(mode: EffectTransitionMode): Float = when (mode) {
    EffectTransitionMode.DELAY_THREE_QUARTER_CUT_END -> 0.75f
    EffectTransitionMode.DELAY_HALF_CUT_END,
    EffectTransitionMode.ECHO_HALF_CUT_END,
    EffectTransitionMode.ECHO_HALF_OUT_END -> 0.5f
    else -> 0f
}

// The tail's regrowth: reverb trails stay modest, echo rings longer.
private fun previewEffectFeedback(mode: EffectTransitionMode): Float = when (mode) {
    EffectTransitionMode.REVERB_OUT_CENTER,
    EffectTransitionMode.REVERB_CUT_END,
    EffectTransitionMode.REVERB_OUT_END -> 0.30f
    EffectTransitionMode.DELAY_HALF_CUT_END,
    EffectTransitionMode.DELAY_THREE_QUARTER_CUT_END -> 0.38f
    EffectTransitionMode.ECHO_HALF_CUT_END,
    EffectTransitionMode.ECHO_HALF_OUT_END -> 0.55f
    EffectTransitionMode.NONE -> 0f
}

// The loop region's length in beats - the orange control's choice.
private fun previewLoopBeats(mode: LoopTransitionMode): Int = when (mode) {
    LoopTransitionMode.NONE -> 0
    LoopTransitionMode.BEAT_1 -> 1
    LoopTransitionMode.BEAT_2 -> 2
    LoopTransitionMode.BEAT_4 -> 4
    LoopTransitionMode.BEAT_8 -> 8
    LoopTransitionMode.BEAT_16 -> 16
}
