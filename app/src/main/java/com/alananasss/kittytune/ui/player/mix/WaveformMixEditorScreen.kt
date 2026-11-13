package com.alananasss.kittytune.ui.player.mix

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.audio.automix.CurveOwner
import com.alananasss.kittytune.audio.automix.CurvePoint
import com.alananasss.kittytune.audio.automix.EffectTransitionMode
import com.alananasss.kittytune.audio.automix.EqTransitionMode
import com.alananasss.kittytune.audio.automix.FullTrackWaveformDecoder
import com.alananasss.kittytune.audio.automix.TransitionConfig
import com.alananasss.kittytune.audio.automix.bassLevelAt
import com.alananasss.kittytune.audio.automix.curveLevelAt
import com.alananasss.kittytune.audio.automix.effectWetAt
import com.alananasss.kittytune.audio.automix.filterInFractionAt
import com.alananasss.kittytune.audio.automix.filterOutFractionAt
import com.alananasss.kittytune.audio.automix.volumeInAt
import com.alananasss.kittytune.audio.automix.volumeOutAt
import com.alananasss.kittytune.data.local.BeatInfoEntity
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.utils.makeTimeString
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

// ---------------------------------------------------------------------------
// The "Edit transition" waveform panel:
//
//   · the transition window is STATIC — its left edge is pinned near the left
//     third of the panel and it never moves; the TRACKS move under it instead:
//     grab the top wave and drag left/right to slide the outgoing track, grab
//     the bottom wave to slide the incoming one (the wave follows the finger,
//     the window stays put);
//   · both tracks are drawn as REAL full-length decoded waveforms, stacked —
//     the outgoing on top, the incoming below, each mirrored around its
//     centre; the part inside the window is painted by height (blue tips,
//     brown mids, a cream core), the parts around it stay clearly visible in
//     cool gray so the whole song is always on screen;
//   · between the two waves runs the beat strip: green ticks on bar starts,
//     gray ticks on the beats between them (16ths appear when zoomed in);
//   · the window wears a rounded blue frame with white edge lines, and the
//     long green anchor line at its left edge is where the mix starts;
//   · every control paints its own line in its card's accent colour — the blue
//     volume curves, the fuchsia filter legs, the amber bass legs and the white
//     effects tail — and the orange box wraps the looped piece of the outgoing
//     wave; every line walks its wave's FULL height now (level 1 touches the
//     very top of the wave, level 0 the very bottom — no more "only to the
//     middle"), and the EQ no longer hides in a lane under the bottom wave: it
//     rides both waves like the volume pair, one amber leg per deck. The
//     beatmatch draws no line of its own;
//   · the customization button puts hand-placed points on a control's curve:
//     touch an empty spot to drop a point, drag points to reshape, long-press a
//     point to remove it — the drawn shape is what the decks will perform;
//   · pinch zooms around the window's pinned edge, double-tap fits the
//     default view back.
// ---------------------------------------------------------------------------

/** Where the window's left edge sits, as a share of the panel width — it never moves. */
private const val WINDOW_LEFT_FRACTION = 0.30f

/** The window's width at the default zoom, as a share of the panel width. */
private const val WINDOW_WIDTH_FRACTION = 0.42f

/** The wave itself, painted by height: blue tips, brown mids, cream core. */
private val WaveBlueTip = Color(0xFF2B4BC9)
private val WaveBrownMid = Color(0xFFB5713A)
private val WaveCreamCore = Color(0xFFF5EFE0)
private val WaveIncomingCore = Color(0xFFCB8F5C)

/**
 * The same wave outside the transition window: cool gray but clearly readable, so the
 * full track is always visible around the mix — never a "cut off" stub.
 */
private val WaveDimEdge = Color(0xFF646E7A)
private val WaveDimMid = Color(0xFF838D9A)
private val WaveDimCore = Color(0xFFA2ACB8)

/** Automation and frame accents, as in the reference editor. The per-control
 *  overlay colours are the shared Transition*Color accents (TransitionWaveformCanvas.kt). */
private val WaveFrameBlue = Color(0xFF4EA8DE)
private val WaveGreenTick = Color(0xFF1DB954)
private val WaveBeatTick = Color(0xFF9A9A9A)
private val WaveSubTick = Color(0xFF555555)
private val WavePanelBg = Color(0xFF1A1A1A)

/** A hand curve carries at most this many points — enough to draw any shape, few enough
 *  to stay editable on a phone screen. */
private const val MAX_CURVE_POINTS = 8

/**
 * The full-waveform area of the "Edit transition" screen. [config] is the live
 * transition being edited (already auto-resolved by the caller); the anchor it
 * shows comes straight from [TransitionConfig.zoneStartMs], and manual edits are
 * reported back through [onAnchorChange] / [onBOffsetChange] once a drag settles.
 */
@Composable
fun SpotifyFullWaveCanvas(
    trackA: Track,
    trackB: Track,
    config: TransitionConfig,
    bpmA: Float,
    bpmB: Float,
    waveA: FullTrackWaveformDecoder.DecodedWaveform?,
    waveB: FullTrackWaveformDecoder.DecodedWaveform?,
    waveProgressA: Float,
    waveProgressB: Float,
    waveFailedA: Boolean,
    waveFailedB: Boolean,
    beatInfoA: BeatInfoEntity?,
    beatInfoB: BeatInfoEntity?,
    isPreviewing: Boolean,
    previewProgress: Float,
    onAnchorChange: (Long) -> Unit,
    onBOffsetChange: (Long) -> Unit,
    onTogglePreview: () -> Unit,
    onRetryWaveA: () -> Unit = {},
    onRetryWaveB: () -> Unit = {},
    /** The control whose hand curve is being edited (the customization button's mode). */
    editSection: EditSection = EditSection.NONE,
    /** Writes one hand curve back — [CurveOwner] plus the new point list. */
    onCurvePoints: (CurveOwner, List<CurvePoint>) -> Unit = { _, _ -> },
    /** The Done chip on the panel — leaves the hand-edit mode. */
    onDoneEditing: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    val durationA = waveA?.durationMs
        ?: trackA.actualDurationMs.takeIf { it > 0L }
        ?: trackA.durationMs?.takeIf { (it ?: 0L) > 0L } ?: 0L
    val durationB = waveB?.durationMs
        ?: trackB.actualDurationMs.takeIf { it > 0L }
        ?: trackB.durationMs?.takeIf { (it ?: 0L) > 0L } ?: 0L

    val beatMsA = if (bpmA > 0f) 60_000f / bpmA else 500f
    val beatMsB = if (bpmB > 0f) 60_000f / bpmB else 500f
    val windowMs = (config.duration.beats * beatMsA).toLong()

    // The incoming wave is drawn beat-locked: stretched or squeezed to A's tempo,
    // so its ticks fall on the window's beats — the way the deck will sound.
    val tempoScale = if (bpmA > 0f && bpmB > 0f) (bpmB / bpmA).coerceIn(0.5f, 2f) else 1f

    // ------------------------------------------------------------------
    // Working state: the anchor (where the window sits in track A) and the
    // incoming entry (where it sits in track B). The window itself is pinned;
    // dragging a wave moves its track under the window. On release the raw
    // value is reported back — the wave stays exactly where the finger left
    // it (free movement, no grid snapping).
    // ------------------------------------------------------------------
    var rawAnchorMs by remember(trackA.id, trackB.id) {
        mutableLongStateOf(
            config.zoneStartMs.takeIf { it >= 0L }
                ?: (durationA - windowMs).coerceAtLeast(0L)
        )
    }
    var anchorCustom by remember(trackA.id, trackB.id) { mutableStateOf(config.zoneStartMs >= 0L) }
    var rawBOffsetMs by remember(trackA.id, trackB.id) {
        mutableLongStateOf(config.userNudgeOffsetMs.coerceAtLeast(0L))
    }

    val anchorMs = if (anchorCustom) {
        rawAnchorMs.coerceIn(0L, durationA)
    } else {
        (durationA - windowMs).coerceAtLeast(0L)
    }
    val bStartMs = if (rawBOffsetMs > 0L) {
        rawBOffsetMs.coerceIn(0L, durationB)
    } else {
        beatInfoB?.mixInPointMs ?: 0L
    }

    // Zoom, in px per second of track A's timeline. The default fits the window
    // into [WINDOW_WIDTH_FRACTION] of the panel; the pinch scales around the
    // window's pinned left edge; a double tap returns to the default.
    var ppsOverride by remember(trackA.id, trackB.id) { mutableFloatStateOf(-1f) }

    BoxWithConstraints(modifier = modifier) {
        val panelW = maxWidth
        val panelWpx = with(density) { panelW.toPx() }
        val panelHpx = with(density) { maxHeight.toPx() }

        val windowLeftX = panelWpx * WINDOW_LEFT_FRACTION
        val defaultPps = if (windowMs > 0L) {
            (panelWpx * WINDOW_WIDTH_FRACTION) / (windowMs / 1000f)
        } else {
            panelWpx / 30f
        }
        val minPps = defaultPps * 0.35f
        val maxPps = defaultPps * 6f
        val pxPerSec = if (ppsOverride > 0f) ppsOverride.coerceIn(minPps, maxPps) else defaultPps

        // Panel geometry: pads, the two wave bands and the beat strip between them.
        // The EQ's old private lane under the bottom wave is gone — the amber bass
        // curves ride the two waves themselves now (one leg per deck, exactly like
        // the volume pair), and the lane's height went back to the waves.
        val padPx = panelHpx * 0.045f
        val stripHpx = panelHpx * 0.085f
        val bandHpx = (panelHpx - padPx * 2f - stripHpx) / 2f
        val aTopPx = padPx
        val aCenterPx = padPx + bandHpx / 2f
        val aBottomPx = padPx + bandHpx
        val stripTopPx = aBottomPx
        val stripMidPx = stripTopPx + stripHpx / 2f
        val bTopPx = stripTopPx + stripHpx
        val bCenterPx = bTopPx + bandHpx / 2f
        val bBottomPx = bTopPx + bandHpx

        // The mapping every drawn element shares: the window's left edge is the
        // anchor of the whole coordinate system, at a fixed x.
        fun timeToXA(ms: Float): Float = windowLeftX + (ms - anchorMs) / 1000f * pxPerSec
        val windowRightX = timeToXA((anchorMs + windowMs).toFloat())
        val windowPx = (windowRightX - windowLeftX).coerceAtLeast(2f)
        val xOfBZero = windowLeftX - bStartMs * tempoScale / 1000f * pxPerSec

        // Gradients, one per band: cream core on top, brown/blue on the bottom.
        val brushAColor = remember(aTopPx, aBottomPx) {
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to WaveBlueTip,
                    0.34f to WaveBrownMid,
                    0.5f to WaveCreamCore,
                    0.66f to WaveBrownMid,
                    1.0f to WaveBlueTip,
                ),
                startY = aTopPx,
                endY = aBottomPx,
            )
        }
        val brushADim = remember(aTopPx, aBottomPx) {
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to WaveDimEdge,
                    0.34f to WaveDimMid,
                    0.5f to WaveDimCore,
                    0.66f to WaveDimMid,
                    1.0f to WaveDimEdge,
                ),
                startY = aTopPx,
                endY = aBottomPx,
            )
        }
        val brushBColor = remember(bTopPx, bBottomPx) {
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to WaveBlueTip,
                    0.34f to WaveBrownMid,
                    0.5f to WaveIncomingCore,
                    0.66f to WaveBrownMid,
                    1.0f to WaveBlueTip,
                ),
                startY = bTopPx,
                endY = bBottomPx,
            )
        }
        val brushBDim = remember(bTopPx, bBottomPx) {
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to WaveDimEdge,
                    0.34f to WaveDimMid,
                    0.5f to WaveDimCore,
                    0.66f to WaveDimMid,
                    1.0f to WaveDimEdge,
                ),
                startY = bTopPx,
                endY = bBottomPx,
            )
        }
        val scratchAmps = remember { FloatArray(8192) }
        val wavePath = remember { Path() }

        // ------------------------------------------------------------------
        // Gestures. One pointer: the top band drags the outgoing track under
        // the window (the anchor), the bottom band drags the incoming one (its
        // entry point) — the wave follows the finger, the window never moves.
        // Two pointers: pinch zoom. Double tap: back to the default zoom.
        // ------------------------------------------------------------------
        val currentPps by rememberUpdatedState(pxPerSec)
        val currentDefaultPps by rememberUpdatedState(defaultPps)
        val currentMinPps by rememberUpdatedState(minPps)
        val currentMaxPps by rememberUpdatedState(maxPps)
        val currentAnchorMs by rememberUpdatedState(anchorMs)
        val currentBStart by rememberUpdatedState(bStartMs)
        val currentDurA by rememberUpdatedState(durationA)
        val currentDurB by rememberUpdatedState(durationB)
        val currentStripTopPx by rememberUpdatedState(stripTopPx)
        val currentStripBottomPx by rememberUpdatedState(bTopPx)
        val currentTempoScale by rememberUpdatedState(tempoScale)
        val currentOnAnchor by rememberUpdatedState(onAnchorChange)
        val currentOnBOffset by rememberUpdatedState(onBOffsetChange)

        // --- Hand-curve editing (the customization button). The values the point
        // gestures need ride rememberUpdatedState, so a drag always reads fresh geometry.
        val currentEditSection by rememberUpdatedState(editSection)
        val currentConfig by rememberUpdatedState(config)
        val currentOnCurvePoints by rememberUpdatedState(onCurvePoints)
        val currentWindowLeftX by rememberUpdatedState(windowLeftX)
        val currentWindowPx by rememberUpdatedState(windowPx)
        val currentABottomPx by rememberUpdatedState(aBottomPx)
        val currentBBottomPx by rememberUpdatedState(bBottomPx)
        val currentBandHeightPx by rememberUpdatedState(bandHpx)

        val gestureModifier = Modifier.pointerInput(trackA.id, trackB.id) {
            val slop = viewConfiguration.touchSlop
            var lastTapUpMs = 0L
            var lastTapX = 0f
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // A touch a child (a hand-curve point, the Done chip, the play capsule,
                // a retry label) already claimed is not the wave's business.
                if (down.isConsumed) return@awaitEachGesture
                val mode = when {
                    down.position.y < currentStripTopPx -> 1
                    down.position.y > currentStripBottomPx -> 2
                    else -> 1
                }
                var active = false
                var pinch = false
                var lastSpan = 1f
                val firstId = down.id
                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.isEmpty()) break
                    if (pressed.size >= 2) {
                        val span = (pressed[0].position - pressed[1].position)
                            .getDistance().coerceAtLeast(1f)
                        if (!pinch) {
                            pinch = true
                            active = true
                            lastSpan = span
                        }
                        val basePps = if (ppsOverride > 0f) ppsOverride else currentDefaultPps
                        ppsOverride = (basePps * span / lastSpan)
                            .coerceIn(currentMinPps, currentMaxPps)
                        lastSpan = span
                        event.changes.forEach { it.consume() }
                        continue
                    }
                    if (pinch) break // a finger lifted after a pinch — end the gesture
                    val change = event.changes.firstOrNull { it.id == firstId } ?: break
                    if (!change.pressed) break
                    val dx = change.positionChange().x
                    if (!active && abs(dx) > slop) {
                        active = true
                        if (mode == 1 && !anchorCustom) rawAnchorMs = currentAnchorMs
                        if (mode == 1) anchorCustom = true
                        if (mode == 2 && rawBOffsetMs <= 0L) rawBOffsetMs = currentBStart
                    }
                    if (active && dx != 0f) {
                        when (mode) {
                            1 -> rawAnchorMs = (rawAnchorMs - dx / currentPps * 1000f)
                                .toLong().coerceIn(0L, currentDurA)
                            2 -> rawBOffsetMs = (rawBOffsetMs -
                                dx * 1000f / (currentPps * currentTempoScale))
                                .toLong().coerceIn(0L, currentDurB)
                        }
                        change.consume()
                    }
                }
                if (!active && !pinch) {
                    val now = System.currentTimeMillis()
                    if (now - lastTapUpMs < 320L && abs(down.position.x - lastTapX) < 48f) {
                        ppsOverride = -1f // double tap: back to the default zoom
                        lastTapUpMs = 0L
                    } else {
                        lastTapUpMs = now
                        lastTapX = down.position.x
                    }
                }
                // The drag settled: report the raw value back as-is. Free movement —
                // the wave stays where the finger left it instead of jumping to the
                // beat grid, so a green downbeat never snaps under the green anchor.
                if (!pinch && active && mode == 1) {
                    currentOnAnchor(rawAnchorMs)
                }
                if (!pinch && active && mode == 2) {
                    currentOnBOffset(rawBOffsetMs)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(WavePanelBg)
                .then(gestureModifier),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f || windowPx <= 0f) return@Canvas

                // ---------------- 1. The two waves, gray overall, colored in the window
                val pxPerMsA = pxPerSec / 1000f
                val pxPerMsB = pxPerSec * tempoScale / 1000f

                val wa = waveA
                if (wa != null && wa.peaks.isNotEmpty() && wa.peaksPerSecond > 0 && durationA > 0L) {
                    if (buildMirroredWavePath(
                            path = wavePath,
                            scratch = scratchAmps,
                            peaks = wa.peaks,
                            samplesPerSecond = wa.peaksPerSecond,
                            durationMs = durationA,
                            xOfZero = timeToXA(0f),
                            pxPerMs = pxPerMsA,
                            cy = aCenterPx,
                            half = bandHpx * 0.46f,
                            width = w,
                        )
                    ) {
                        drawPath(wavePath, brushADim)
                        clipRect(windowLeftX, 0f, windowRightX, h) {
                            drawPath(wavePath, brushAColor)
                        }
                    }
                }
                val wb = waveB
                if (wb != null && wb.peaks.isNotEmpty() && wb.peaksPerSecond > 0 && durationB > 0L) {
                    if (buildMirroredWavePath(
                            path = wavePath,
                            scratch = scratchAmps,
                            peaks = wb.peaks,
                            samplesPerSecond = wb.peaksPerSecond,
                            durationMs = durationB,
                            xOfZero = xOfBZero,
                            pxPerMs = pxPerMsB,
                            cy = bCenterPx,
                            half = bandHpx * 0.46f,
                            width = w,
                        )
                    ) {
                        drawPath(wavePath, brushBDim)
                        clipRect(windowLeftX, 0f, windowRightX, h) {
                            drawPath(wavePath, brushBColor)
                        }
                    }
                }

                // Fade the waves into the panel at the left and right edges.
                val fade = 26f
                if (w > fade * 2f) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(WavePanelBg.copy(alpha = 0.92f), Color.Transparent),
                            startX = 0f,
                            endX = fade,
                        ),
                        topLeft = Offset(0f, 0f),
                        size = Size(fade, h),
                    )
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, WavePanelBg.copy(alpha = 0.92f)),
                            startX = w - fade,
                            endX = w,
                        ),
                        topLeft = Offset(w - fade, 0f),
                        size = Size(fade, h),
                    )
                }

                // ---------------- 2. The beat strip between the waves
                drawLine(
                    color = Color.White.copy(alpha = 0.10f),
                    start = Offset(0f, stripTopPx),
                    end = Offset(w, stripTopPx),
                    strokeWidth = 1f,
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.10f),
                    start = Offset(0f, bTopPx),
                    end = Offset(w, bTopPx),
                    strokeWidth = 1f,
                )
                drawBeatStrip(
                    info = beatInfoA,
                    beatMs = beatMsA,
                    maxMs = durationA,
                    windowLeftX = windowLeftX,
                    windowRightX = windowRightX,
                    tickTopPx = stripTopPx,
                    tickBottomPx = stripMidPx,
                    xOfZero = timeToXA(0f),
                    pxPerMs = pxPerMsA,
                    width = w,
                )
                drawBeatStrip(
                    info = beatInfoB,
                    beatMs = beatMsB,
                    maxMs = durationB,
                    windowLeftX = windowLeftX,
                    windowRightX = windowRightX,
                    tickTopPx = stripMidPx,
                    tickBottomPx = bTopPx,
                    xOfZero = xOfBZero,
                    pxPerMs = pxPerMsB,
                    width = w,
                )

                // ---------------- 3. The (static) transition window frame
                val frameTop = 2f
                val frameBottom = h - 2f
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.04f),
                    topLeft = Offset(windowLeftX, frameTop),
                    size = Size(windowPx, frameBottom - frameTop),
                    cornerRadius = CornerRadius(10f, 10f),
                )
                drawRoundRect(
                    color = WaveFrameBlue,
                    topLeft = Offset(windowLeftX, frameTop),
                    size = Size(windowPx, frameBottom - frameTop),
                    cornerRadius = CornerRadius(10f, 10f),
                    style = Stroke(width = 2f),
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.75f),
                    start = Offset(windowLeftX, frameTop),
                    end = Offset(windowLeftX, frameBottom),
                    strokeWidth = 2f,
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.75f),
                    start = Offset(windowRightX, frameTop),
                    end = Offset(windowRightX, frameBottom),
                    strokeWidth = 2f,
                )

                // The long green anchor line: where the transition starts.
                drawLine(
                    color = WaveGreenTick,
                    start = Offset(windowLeftX, 0f),
                    end = Offset(windowLeftX, h),
                    strokeWidth = 2.5f,
                )

                // ---------------- 4. Automation overlays, one colour per control
                // Every control paints in the accent its card wears: blue volume
                // curves, the fuchsia filter legs, the amber bass legs and the
                // white effects tail, plus the orange loop rectangle. The beatmatch
                // deliberately draws no line — its screen is the beat strip itself.
                val segs = 48
                val pointR = (bandHpx * 0.46f).coerceIn(9f, 15f)

                // Where a curve level (0..1) sits vertically for each owner: EVERY control
                // walks its band's FULL height now — level 1 touches the very top of the
                // wave, level 0 the very bottom — so any line can be pulled edge to edge,
                // not just to the wave's middle. Full volume / full bass / open filter sit
                // at the top, silence / no bass / closed filter at the bottom.
                fun levelToY(owner: CurveOwner, level: Float): Float = when (owner) {
                    CurveOwner.VOLUME_OUT, CurveOwner.EFFECT, CurveOwner.EQ_OUT, CurveOwner.FILTER_OUT ->
                        aBottomPx - level.coerceIn(0f, 1f) * bandHpx
                    CurveOwner.VOLUME_IN, CurveOwner.EQ_IN, CurveOwner.FILTER_IN ->
                        bBottomPx - level.coerceIn(0f, 1f) * bandHpx
                }

                // A hand curve: the drawn shape walks the window (levels held flat past
                // the outermost points), and its points show while it is being edited.
                fun drawHandCurve(points: List<CurvePoint>, owner: CurveOwner, color: Color, showPoints: Boolean) {
                    if (points.isEmpty()) return
                    val sorted = points.sortedBy { it.x }
                    var prevX = windowLeftX
                    var prevY = levelToY(owner, curveLevelAt(sorted, 0f) ?: 0f)
                    for (i in 1..segs * 2) {
                        val c = i.toFloat() / (segs * 2)
                        val x = windowLeftX + c * windowPx
                        val y = levelToY(owner, curveLevelAt(sorted, c) ?: 0f)
                        drawLine(color, Offset(prevX, prevY), Offset(x, y), 3.4f)
                        prevX = x; prevY = y
                    }
                    if (showPoints) {
                        sorted.forEach { pt ->
                            val cx = windowLeftX + pt.x * windowPx
                            val cy = levelToY(owner, pt.y)
                            drawCircle(Color.White, radius = pointR, center = Offset(cx, cy))
                            drawCircle(color, radius = pointR, center = Offset(cx, cy), style = Stroke(width = 3f))
                        }
                    }
                }

                // --- Volume (blue): the outgoing yields, the incoming rises. A hand
                // shape replaces its leg's named curve.
                val curveMode = config.volumeCurve
                if (config.customVolumeOut.isEmpty()) {
                    var prevOutX = windowLeftX
                    var prevOutY = levelToY(CurveOwner.VOLUME_OUT, volumeOutAt(curveMode, 0f))
                    for (i in 1..segs) {
                        val p = i.toFloat() / segs
                        val x = windowLeftX + p * windowPx
                        val outY = levelToY(CurveOwner.VOLUME_OUT, volumeOutAt(curveMode, p))
                        drawLine(TransitionVolumeColor, Offset(prevOutX, prevOutY), Offset(x, outY), 3f)
                        prevOutX = x; prevOutY = outY
                    }
                } else {
                    drawHandCurve(
                        config.customVolumeOut, CurveOwner.VOLUME_OUT, TransitionVolumeColor,
                        showPoints = editSection == EditSection.VOLUME,
                    )
                }
                if (config.customVolumeIn.isEmpty()) {
                    var prevInX = windowLeftX
                    var prevInY = levelToY(CurveOwner.VOLUME_IN, volumeInAt(curveMode, 0f))
                    for (i in 1..segs) {
                        val p = i.toFloat() / segs
                        val x = windowLeftX + p * windowPx
                        val inY = levelToY(CurveOwner.VOLUME_IN, volumeInAt(curveMode, p))
                        drawLine(TransitionVolumeColor, Offset(prevInX, prevInY), Offset(x, inY), 3f)
                        prevInX = x; prevInY = inY
                    }
                } else {
                    drawHandCurve(
                        config.customVolumeIn, CurveOwner.VOLUME_IN, TransitionVolumeColor,
                        showPoints = editSection == EditSection.VOLUME,
                    )
                }
                // The incoming stays at full level once the window is over —
                // a faint blue continuation to the right edge, at the wave's top
                // where "full volume" now lives.
                drawLine(
                    color = TransitionVolumeColor.copy(alpha = 0.30f),
                    start = Offset(windowRightX, bTopPx),
                    end = Offset(w, bTopPx),
                    strokeWidth = 2f,
                )

                // --- EQ (amber): the bass handover, one leg per deck, riding the waves
                // exactly like the volume pair — deck A's lows drop where the swap says,
                // deck B's lows rise where A's leave. NONE keeps both decks' bass, so
                // with no hand curves there is no amber line at all.
                if (config.customEqOut.isEmpty()) {
                    if (config.eqMode != EqTransitionMode.NONE) {
                        var prevBassX = windowLeftX
                        var prevBassY = levelToY(CurveOwner.EQ_OUT, bassLevelAt(config.eqMode, 0f))
                        for (i in 1..segs) {
                            val p = i.toFloat() / segs
                            val x = windowLeftX + p * windowPx
                            val y = levelToY(CurveOwner.EQ_OUT, bassLevelAt(config.eqMode, p))
                            drawLine(TransitionEqColor, Offset(prevBassX, prevBassY), Offset(x, y), 2.8f)
                            prevBassX = x; prevBassY = y
                        }
                    }
                } else {
                    drawHandCurve(
                        config.customEqOut, CurveOwner.EQ_OUT, TransitionEqColor,
                        showPoints = editSection == EditSection.EQ,
                    )
                }
                if (config.customEqIn.isEmpty()) {
                    if (config.eqMode != EqTransitionMode.NONE) {
                        var prevBassX = windowLeftX
                        var prevBassY = levelToY(CurveOwner.EQ_IN, 1f - bassLevelAt(config.eqMode, 0f))
                        for (i in 1..segs) {
                            val p = i.toFloat() / segs
                            val x = windowLeftX + p * windowPx
                            val y = levelToY(CurveOwner.EQ_IN, 1f - bassLevelAt(config.eqMode, p))
                            drawLine(TransitionEqColor, Offset(prevBassX, prevBassY), Offset(x, y), 2.8f)
                            prevBassX = x; prevBassY = y
                        }
                    }
                } else {
                    drawHandCurve(
                        config.customEqIn, CurveOwner.EQ_IN, TransitionEqColor,
                        showPoints = editSection == EditSection.EQ,
                    )
                }

                // --- Filter (fuchsia): each deck's own leg, only when the mode
                // actually filters that deck — no placeholder diagonal for NONE.
                var sweepPrevX: Float? = null
                var sweepPrevY: Float? = null
                if (config.customFilterOut.isEmpty()) {
                    for (i in 0..segs) {
                        val p = i.toFloat() / segs
                        val frac = filterOutFractionAt(config.filterMode, p)
                        if (frac != null) {
                            val x = windowLeftX + p * windowPx
                            val y = levelToY(CurveOwner.FILTER_OUT, frac)
                            if (sweepPrevX != null && sweepPrevY != null) {
                                drawLine(
                                    color = TransitionFilterColor,
                                    start = Offset(sweepPrevX, sweepPrevY),
                                    end = Offset(x, y),
                                    strokeWidth = 2.6f,
                                )
                            }
                            sweepPrevX = x; sweepPrevY = y
                        } else {
                            sweepPrevX = null; sweepPrevY = null
                        }
                    }
                } else {
                    drawHandCurve(
                        config.customFilterOut, CurveOwner.FILTER_OUT, TransitionFilterColor,
                        showPoints = editSection == EditSection.FILTER,
                    )
                }
                sweepPrevX = null; sweepPrevY = null
                if (config.customFilterIn.isEmpty()) {
                    for (i in 0..segs) {
                        val p = i.toFloat() / segs
                        val frac = filterInFractionAt(config.filterMode, p)
                        if (frac != null) {
                            val x = windowLeftX + p * windowPx
                            val y = levelToY(CurveOwner.FILTER_IN, frac)
                            if (sweepPrevX != null && sweepPrevY != null) {
                                drawLine(
                                    color = TransitionFilterColor,
                                    start = Offset(sweepPrevX, sweepPrevY),
                                    end = Offset(x, y),
                                    strokeWidth = 2.6f,
                                )
                            }
                            sweepPrevX = x; sweepPrevY = y
                        } else {
                            sweepPrevX = null; sweepPrevY = null
                        }
                    }
                } else {
                    drawHandCurve(
                        config.customFilterIn, CurveOwner.FILTER_IN, TransitionFilterColor,
                        showPoints = editSection == EditSection.FILTER,
                    )
                }

                // --- Effects (white): the outgoing deck's wet tail.
                if (config.customEffect.isEmpty()) {
                    if (config.effectMode != EffectTransitionMode.NONE) {
                        var prevFxX = windowLeftX
                        var prevFxY = levelToY(CurveOwner.EFFECT, effectWetAt(config.effectMode, 0f))
                        for (i in 1..segs) {
                            val p = i.toFloat() / segs
                            val x = windowLeftX + p * windowPx
                            val y = levelToY(CurveOwner.EFFECT, effectWetAt(config.effectMode, p))
                            drawLine(TransitionEffectsColor, Offset(prevFxX, prevFxY), Offset(x, y), 2.2f)
                            prevFxX = x; prevFxY = y
                        }
                    }
                } else {
                    drawHandCurve(
                        config.customEffect, CurveOwner.EFFECT, TransitionEffectsColor,
                        showPoints = editSection == EditSection.EFFECTS,
                    )
                }

                // --- Looping (orange): the encore region — the first N beats of the
                // window repeat on the outgoing deck, so the rectangle wraps exactly
                // that piece of the upper wave, like the guide's orange box.
                val loopBeatsCount = config.loopMode.beats
                if (loopBeatsCount > 0) {
                    val loopW = (loopBeatsCount * beatMsA * pxPerMsA).coerceAtMost(windowPx)
                    val rectLeft = windowLeftX + 3f
                    val rectTop = aTopPx + 3f
                    val rectSize = Size(
                        (loopW - 6f).coerceAtLeast(8f),
                        (stripTopPx - aTopPx - 6f).coerceAtLeast(8f),
                    )
                    drawRoundRect(
                        color = TransitionLoopColor.copy(alpha = 0.12f),
                        topLeft = Offset(rectLeft, rectTop),
                        size = rectSize,
                        cornerRadius = CornerRadius(9f, 9f),
                    )
                    drawRoundRect(
                        color = TransitionLoopColor,
                        topLeft = Offset(rectLeft, rectTop),
                        size = rectSize,
                        cornerRadius = CornerRadius(9f, 9f),
                        style = Stroke(width = 3f),
                    )
                }

                // ---------------- 5. Preview playhead
                if (isPreviewing) {
                    val x = windowLeftX + previewProgress.coerceIn(0f, 1f) * windowPx
                    drawLine(
                        color = Color.White.copy(alpha = 0.28f),
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 7f,
                    )
                    drawLine(
                        color = WaveGreenTick,
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 2.5f,
                    )
                }
            }

            // ------------------------------------------------------------------
            // Hand-curve editing. A dedicated touch layer above the canvas, active
            // only while the customization button has a control in edit mode: touch
            // an empty spot in the control's zone to drop a point there (it follows
            // the finger), drag an existing point to reshape the curve, long-press
            // a point to remove it. Everything else keeps the wave gestures.
            // ------------------------------------------------------------------
            if (editSection != EditSection.NONE) {
                val curveEditModifier = Modifier.pointerInput(editSection, trackA.id, trackB.id) {
                    val slop = viewConfiguration.touchSlop
                    val longPressMs = viewConfiguration.longPressTimeoutMillis
                    val hitRadiusPx = with(density) { 22.dp.toPx() }

                    // Which curve a touch in [pos] edits, per the control being edited.
                    // The EQ edits like volume/filter now — the upper wave carries the
                    // outgoing deck's leg, the lower one the incoming deck's.
                    fun ownerFor(section: EditSection, pos: Offset): CurveOwner? = when (section) {
                        EditSection.VOLUME -> when {
                            pos.y < currentStripTopPx -> CurveOwner.VOLUME_OUT
                            pos.y > currentStripBottomPx -> CurveOwner.VOLUME_IN
                            else -> null
                        }
                        EditSection.FILTER -> when {
                            pos.y < currentStripTopPx -> CurveOwner.FILTER_OUT
                            pos.y > currentStripBottomPx -> CurveOwner.FILTER_IN
                            else -> null
                        }
                        EditSection.EFFECTS -> if (pos.y < currentStripTopPx) CurveOwner.EFFECT else null
                        EditSection.EQ -> when {
                            pos.y < currentStripTopPx -> CurveOwner.EQ_OUT
                            pos.y > currentStripBottomPx -> CurveOwner.EQ_IN
                            else -> null
                        }
                        else -> null
                    }

                    // Curve x (0..1 across the window) and level (0..1) ↔ panel pixels.
                    // The level spans each wave band's FULL height — a point can sit at
                    // the very top or the very bottom of its wave, matching the canvas.
                    fun pxToCurveX(px: Float): Float =
                        ((px - currentWindowLeftX) / currentWindowPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    fun curveXToPx(cx: Float): Float = currentWindowLeftX + cx * currentWindowPx
                    fun pyToLevel(owner: CurveOwner, py: Float): Float = when (owner) {
                        CurveOwner.VOLUME_OUT, CurveOwner.EFFECT, CurveOwner.EQ_OUT, CurveOwner.FILTER_OUT ->
                            ((currentABottomPx - py) / currentBandHeightPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
                        CurveOwner.VOLUME_IN, CurveOwner.EQ_IN, CurveOwner.FILTER_IN ->
                            ((currentBBottomPx - py) / currentBandHeightPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    }
                    fun levelToPy(owner: CurveOwner, level: Float): Float = when (owner) {
                        CurveOwner.VOLUME_OUT, CurveOwner.EFFECT, CurveOwner.EQ_OUT, CurveOwner.FILTER_OUT ->
                            currentABottomPx - level.coerceIn(0f, 1f) * currentBandHeightPx
                        CurveOwner.VOLUME_IN, CurveOwner.EQ_IN, CurveOwner.FILTER_IN ->
                            currentBBottomPx - level.coerceIn(0f, 1f) * currentBandHeightPx
                    }

                    // A drag clamps its point between the neighbours so the line never
                    // folds back on itself.
                    fun withMovedPoint(points: List<CurvePoint>, index: Int, x: Float, y: Float): List<CurvePoint> {
                        val lo = (points.getOrNull(index - 1)?.x?.plus(0.02f) ?: 0f)
                        val hi = (points.getOrNull(index + 1)?.x?.minus(0.02f) ?: 1f)
                        val cx = x.coerceIn(lo, hi).coerceIn(0f, 1f)
                        return points.toMutableList().apply { set(index, CurvePoint(cx, y)) }
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (down.isConsumed) return@awaitEachGesture
                        val owner = ownerFor(currentEditSection, down.position) ?: return@awaitEachGesture
                        down.consume() // this touch belongs to the curve now, not to the waves

                        val points = currentConfig.curvePoints(owner)
                        // Nearest point under the finger, if any.
                        var dragIndex = -1
                        var best = hitRadiusPx * hitRadiusPx
                        points.forEachIndexed { i, pt ->
                            val dx = curveXToPx(pt.x) - down.position.x
                            val dy = levelToPy(owner, pt.y) - down.position.y
                            val d2 = dx * dx + dy * dy
                            if (d2 < best) { best = d2; dragIndex = i }
                        }

                        if (dragIndex >= 0) {
                            // Drag moves the point; a long hold without moving removes it.
                            var moved = false
                            val downAt = System.currentTimeMillis()
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    if (!moved && System.currentTimeMillis() - downAt >= longPressMs) {
                                        currentOnCurvePoints(
                                            owner,
                                            points.filterIndexed { idx, _ -> idx != dragIndex },
                                        )
                                    }
                                    break
                                }
                                val delta = change.positionChange()
                                if (!moved && delta.getDistance() > slop) moved = true
                                if (moved) {
                                    currentOnCurvePoints(
                                        owner,
                                        withMovedPoint(
                                            points, dragIndex,
                                            pxToCurveX(change.position.x),
                                            pyToLevel(owner, change.position.y),
                                        ),
                                    )
                                    change.consume()
                                }
                            }
                        } else {
                            // Empty spot: a new point appears under the finger and follows it.
                            if (points.size >= MAX_CURVE_POINTS) {
                                while (awaitPointerEvent().changes.any { it.pressed }) {
                                    // hold: the lane is full — swallow the gesture
                                }
                                return@awaitEachGesture
                            }
                            val nx = pxToCurveX(down.position.x)
                            val ny = pyToLevel(owner, down.position.y)
                            val at = points.count { it.x < nx }
                            // List, not MutableList: withMovedPoint hands back plain lists and the
                            // follow-the-finger loop below re-assigns latest with them.
                            var latest: List<CurvePoint> =
                                points.toMutableList().apply { add(at, CurvePoint(nx, ny)) }
                            currentOnCurvePoints(owner, latest)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val delta = change.positionChange()
                                if (delta.getDistance() > slop) {
                                    latest = withMovedPoint(
                                        latest, at,
                                        pxToCurveX(change.position.x),
                                        pyToLevel(owner, change.position.y),
                                    )
                                    currentOnCurvePoints(owner, latest)
                                    change.consume()
                                }
                            }
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxSize().then(curveEditModifier))
            }

            // The Done chip — leaves the hand-edit mode, wherever the finger is.
            if (editSection != EditSection.NONE) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = TransitionPlayheadColor,
                    contentColor = Color.Black,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        // Plain clickable, no haptic: this window stays vibration-free.
                        .clickable { onDoneEditing() },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.curve_editor_done),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // ------------------------------------------------------------------
            // Compose overlays: timecodes, preview capsule, load states.
            // ------------------------------------------------------------------
            val windowLeftDp = with(density) { windowLeftX.toDp() }
            val windowRightDp = with(density) { windowRightX.toDp() }
            val anchorLabelX = (windowLeftDp + 6.dp)
                .coerceAtMost(panelW - 70.dp)
                .coerceAtLeast(0.dp)
            Text(
                text = makeTimeString(anchorMs.coerceAtLeast(0L)),
                color = WaveGreenTick,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = anchorLabelX, y = 3.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
            val endLabelX = (windowRightDp + 6.dp)
                .coerceAtMost(panelW - 70.dp)
                .coerceAtLeast(0.dp)
            Text(
                text = makeTimeString((anchorMs + windowMs).coerceAtLeast(0L)),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = endLabelX, y = (-4).dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )

            // Preview capsule, sitting on the beat strip.
            val stripMidDp = with(density) { stripMidPx.toDp() }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = panelW / 2 - 22.dp, y = stripMidDp - 12.dp)
                    .size(width = 44.dp, height = 24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xE6141414))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                    // Plain clickable, no haptic: play in this window stays vibration-free.
                    .clickable { onTogglePreview() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPreviewing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }

            // Wave load state, one label per deck. A failed load stays tappable —
            // plain clickable, no haptic: this window must stay vibration-free —
            // so a transient network slip can be retried with one tap.
            if (waveA == null) {
                Text(
                    text = if (waveFailedA) {
                        stringResource(R.string.wave_unavailable)
                    } else {
                        stringResource(R.string.wave_loading_pct, (waveProgressA * 100).toInt())
                    },
                    color = if (waveFailedA) Color(0xFFFF7A7A) else Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = 48.dp, y = with(density) { aTopPx.toDp() } + 2.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .then(
                            if (waveFailedA) Modifier.clickable(onClick = onRetryWaveA) else Modifier
                        ),
                )
            }
            if (waveB == null) {
                Text(
                    text = if (waveFailedB) {
                        stringResource(R.string.wave_unavailable)
                    } else {
                        stringResource(R.string.wave_loading_pct, (waveProgressB * 100).toInt())
                    },
                    color = if (waveFailedB) Color(0xFFFF7A7A) else Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = 48.dp, y = (-4).dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .then(
                            if (waveFailedB) Modifier.clickable(onClick = onRetryWaveB) else Modifier
                        ),
                )
            }
        }
    }
}

/**
 * Build a mirrored envelope path for one wave: the top edge walks left to right
 * over the visible range, the bottom edge walks back, so the fill covers the
 * classic symmetric waveform. [xOfZero] is the x of the wave's own time zero;
 * [pxPerMs] is this wave's pixels per millisecond of its own timeline.
 */
private fun buildMirroredWavePath(
    path: Path,
    scratch: FloatArray,
    peaks: FloatArray,
    samplesPerSecond: Int,
    durationMs: Long,
    xOfZero: Float,
    pxPerMs: Float,
    cy: Float,
    half: Float,
    width: Float,
): Boolean {
    if (pxPerMs <= 0f || durationMs <= 0L) return false
    val step = 2f
    val xStart = xOfZero.coerceAtLeast(0f)
    val xEnd = (xOfZero + durationMs * pxPerMs).coerceAtMost(width)
    if (xEnd <= xStart) return false
    val n = ((xEnd - xStart) / step).toInt().coerceAtMost(scratch.size - 1)
    if (n <= 0) return false
    val windowSamples = (samplesPerSecond * step / pxPerMs / 1000f).toInt().coerceAtLeast(1)
    for (i in 0..n) {
        val x = xStart + i * step
        val tMs = (x - xOfZero) / pxPerMs
        val center = ((tMs * samplesPerSecond) / 1000f).toInt()
        var peak = 0f
        var j = center - windowSamples / 2
        val end = center + windowSamples / 2 + 1
        while (j < end) {
            if (j >= 0 && j < peaks.size) {
                val v = peaks[j]
                if (v > peak) peak = v
            }
            j++
        }
        scratch[i] = peak
    }
    path.reset()
    path.moveTo(xStart, cy - scratch[0] * half)
    for (i in 1..n) {
        path.lineTo(xStart + i * step, cy - scratch[i] * half)
    }
    for (i in n downTo 0) {
        path.lineTo(xStart + i * step, cy + scratch[i] * half)
    }
    path.close()
    return true
}

/**
 * The tick strip between the two waves. Green ticks mark bar starts, gray the
 * beats in between; 16th-note ticks appear when the zoom is deep enough to
 * space them. Ticks outside the transition window stay faint. Each track's
 * strip is mapped through that track's own [xOfZero] / [pxPerMs].
 */
private fun DrawScope.drawBeatStrip(
    info: BeatInfoEntity?,
    beatMs: Float,
    maxMs: Long,
    windowLeftX: Float,
    windowRightX: Float,
    tickTopPx: Float,
    tickBottomPx: Float,
    xOfZero: Float,
    pxPerMs: Float,
    width: Float,
) {
    if (info == null || beatMs <= 0f || pxPerMs <= 0f) return
    val beatPx = beatMs * pxPerMs
    if (beatPx * 4f < 3f) return // even the bars are too dense — no grid here
    // Finest subdivision that still has room: 16ths deep in, beats mid, bars at overview.
    val subdiv = if (beatPx >= 26f) 4 else if (beatPx >= 7f) 1 else 0
    val stepMs = beatMs / maxOf(subdiv, 1)
    val origin = if (info.downbeatOffsetMs > 0L || info.downbeatConfidence > 0f) {
        info.downbeatOffsetMs
    } else {
        info.firstBeatOffsetMs
    }
    val tickH = tickBottomPx - tickTopPx
    if (tickH <= 0f) return
    val tickCenterY = tickTopPx + tickH / 2f

    val tVisibleStart = (0f - xOfZero) / pxPerMs
    val tVisibleEnd = (width - xOfZero) / pxPerMs
    val firstK = floor((tVisibleStart - origin) / stepMs).toInt()
    val lastK = ceil((tVisibleEnd - origin) / stepMs).toInt()
    var k = firstK
    while (k <= lastK) {
        val tMs = origin + k * stepMs
        val x = xOfZero + tMs * pxPerMs
        if (x >= -2f && x <= width + 2f && tMs >= 0f && tMs <= maxMs.toFloat()) {
            val inWindow = x >= windowLeftX && x <= windowRightX
            val alpha = if (inWindow) 1f else 0.42f
            val isDownbeat = (k % (4 * maxOf(subdiv, 1))) == 0
            val isBeat = subdiv >= 1 && (k % subdiv) == 0
            val h = when {
                isDownbeat -> tickH * 0.92f
                isBeat -> tickH * 0.58f
                else -> tickH * 0.34f
            }
            val color = when {
                isDownbeat -> WaveGreenTick
                isBeat -> WaveBeatTick
                else -> WaveSubTick
            }
            drawLine(
                color = color.copy(alpha = alpha),
                start = Offset(x, tickCenterY - h / 2f),
                end = Offset(x, tickCenterY + h / 2f),
                strokeWidth = if (isDownbeat) 2.2f else 1.2f,
            )
        }
        k++
    }
}
