package com.alananasss.kittytune.ui.recognition

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * The Pixel "Now Playing" listen button, rebuilt from the app's own resources and animator values.
 *
 * The shape is not one of Material's: it is a hand-authored vector,
 * `music_recognition_active_background_new`, and the button only wears it while listening. Idle it
 * is a plain circle (`music_recognition_button_background`, an `<shape android:shape="oval">`).
 *
 * The tap is a two-beat squash and bloom, read straight out of `dej.java`:
 *
 *  1. scale to 0.95 over 200 ms on emphasized-decelerate, with the background swapped to the
 *     scalloped vector on the *first frame* of it (that swap is what `def`'s `onAnimationStart`
 *     does, which is why the shape changes before the button has finished shrinking);
 *  2. 83 ms later, scale to 1.3111111 over 300 ms on standard-accelerate.
 *
 * That final 1.3111 is not arbitrary: 180 dp × 1.3111 = 236 dp, exactly the viewport the scalloped
 * vector is authored at, so it ends up drawn at its intended size.
 *
 * Behind it, a second copy of a different scalloped vector
 * (`music_recognition_button_active_background`, 12 lobes rather than the button's) pulses on a
 * loop for as long as the app is listening.
 */

/** `motionEasingEmphasizedDecelerate`: cubic(0.1, 0.7, 0.1, 1.0). */
private val EmphasizedDecelerate: Easing = CubicBezierEasing(0.1f, 0.7f, 0.1f, 1f)

/** `motionEasingStandardAccelerate`: cubic(0.3, 0.0, 1.0, 1.0). */
private val StandardAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

private const val SQUASH_SCALE = 0.95f
private const val SQUASH_MS = 200
private const val BLOOM_DELAY_MS = 83L
/** 180dp × 1.3111111 = 236dp — the scalloped vector's authored viewport size (gam.java:121). */
private const val BLOOM_SCALE = 1.3111111f
private const val BLOOM_MS = 300

/**
 * The listen button's shape and its tap animation.
 *
 * Reconstructed directly from Pixel Now Playing's Compose implementation (hac.java lines 18576-19060
 * and gow.java):
 *  - Idle: 180 dp circular button (colorSecondary) with onSecondary note icon.
 *  - Searching: Bloomed to 236 dp (scale 1.3111x) with 10-lobed scalloped active background
 *    (colorOnPrimaryContainer) and primaryContainer searching bars icon.
 *  - Behind it, the aura / halo performs a continuous breathing "battement" (heartbeat) animation:
 *    oscillating between 220 dp (scale 0.70x, tucked behind the button) and 314 dp (scale 1.00x,
 *    expanding outward) with RepeatMode.Reverse over a 1500 ms cycle, while alpha peaks at 0.20
 *    mid-expansion and fades to 0.
 *
 * @param active true while the app is listening: the button wears the scalloped shape, sits at its
 *   bloomed size, and the halo behind it pulses with the heartbeat animation.
 * @param color the fill for the button.
 * @param haloColor the fill for the pulsing halo (defaults to color).
 * @param content centred on the button. It scales with the shape.
 */
@Composable
fun NowPlayingListenButton(
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    haloColor: Color = color,
    content: @Composable BoxScope.() -> Unit,
) {
    val buttonScale = remember { Animatable(if (active) BLOOM_SCALE else 1f) }
    val morphProgress = remember { Animatable(if (active) 1f else 0f) }
    val breathingOffset = remember { Animatable(0f) }
    var isFirstComposition by remember { mutableStateOf(true) }

    LaunchedEffect(active) {
        if (!active) {
            if (isFirstComposition) {
                isFirstComposition = false
                morphProgress.snapTo(0f)
                buttonScale.snapTo(1f)
                breathingOffset.snapTo(0f)
                return@LaunchedEffect
            }
            // Exit: smoothly return breathing to 0 (250ms), morph scalloped shape -> circle (350ms), squish & return to 1.0
            launch {
                breathingOffset.animateTo(0f, tween(250, easing = FastOutSlowInEasing))
            }
            launch {
                morphProgress.animateTo(0f, tween(350, easing = FastOutSlowInEasing))
            }
            buttonScale.animateTo(SQUASH_SCALE, tween(150, easing = EmphasizedDecelerate))
            delay(50L)
            buttonScale.animateTo(1.0f, tween(250, easing = StandardAccelerate))
            return@LaunchedEffect
        }

        // Active state (listening / searching)
        if (isFirstComposition) {
            isFirstComposition = false
            morphProgress.snapTo(1f)
            buttonScale.snapTo(BLOOM_SCALE)
        } else {
            // Tap squish & bloom entry: morph circle -> scalloped shape while squishing and springing back
            launch {
                morphProgress.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
            }
            buttonScale.animateTo(SQUASH_SCALE, tween(SQUASH_MS, easing = EmphasizedDecelerate))
            delay(BLOOM_DELAY_MS)
            buttonScale.animateTo(BLOOM_SCALE, tween(BLOOM_MS, easing = StandardAccelerate))
        }

        // Official Now Playing breathing pulse (fid.smali / hac.java lines 18803-18870):
        // 1000ms delay after listening begins (0x3e8 in fid.smali)
        delay(1000L)
        // Initial rise to +8dp in 750ms with LinearOutSlowInEasing (vl.b / 0x2ee in fid.smali)
        breathingOffset.animateTo(
            targetValue = 8f,
            animationSpec = tween(750, easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f))
        )
        // Continuous breathing loop: oscillates between -8dp and +8dp over 1500ms with FastOutSlowInEasing (vl.a / 0x5dc in fid.smali)
        while (true) {
            breathingOffset.animateTo(
                targetValue = -8f,
                animationSpec = tween(1500, easing = FastOutSlowInEasing)
            )
            breathingOffset.animateTo(
                targetValue = 8f,
                animationSpec = tween(1500, easing = FastOutSlowInEasing)
            )
        }
    }

    // Heartbeat / breathing aura animation from hac.java bv() lines 18803-18870:
    // Scale radiates continuously outward from 0.70f (220dp, tucked behind button)
    // to 1.0f (314dp, expanding past the 236dp button) with RepeatMode.Restart (always expanding outward, never reversing)
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val pulseScaleRaw by infiniteTransition.animateFloat(
        initialValue = 0.70f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale"
    )

    // Alpha peaks at 0.25f at mid-cycle (750ms) and fades to 0f at 0 and 1500ms (ggp.java case 12)
    val pulseAlphaRaw by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1500
                0.0f at 0 using CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)
                0.25f at 750 using CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
                0.0f at 1500
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    val haloFade by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(300),
        label = "halo_fade"
    )
    val haloAlpha = pulseAlphaRaw * haloFade

    val haloPath = remember { Path().also { ButtonMorph.toComposePath(1f, it) } }
    val buttonScratch = remember { Path() }

    Box(
        modifier = modifier.drawBehind {
            if (haloAlpha <= 0f) return@drawBehind
            // Official Now Playing halo viewport: 314dp (hac.java:18839 / amn.m598g(..., 314.0f))
            // At scale 0.70x: 220dp (tucked behind the 236dp bloomed button).
            // At scale 1.00x: 314dp (emerging generously 39dp past each edge of the 236dp bloomed button).
            val radius = (314.dp.toPx() / 2f) * pulseScaleRaw
            translate(left = size.center.x, top = size.center.y) {
                scale(scaleX = radius, scaleY = radius, pivot = Offset.Zero) {
                    rotate(degrees = -90f, pivot = Offset.Zero) {
                        drawPath(path = haloPath, color = haloColor, alpha = haloAlpha, style = Fill)
                    }
                }
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = buttonScale.value
                    scaleY = buttonScale.value
                }
                .drawBehind {
                    val baseRadius = min(size.width, size.height) / 2f
                    val radius = baseRadius + (breathingOffset.value.dp.toPx() / 2f)
                    ButtonMorph.toComposePath(morphProgress.value, buttonScratch)
                    translate(left = size.center.x, top = size.center.y) {
                        scale(scaleX = radius, scaleY = radius, pivot = Offset.Zero) {
                            rotate(degrees = -90f, pivot = Offset.Zero) {
                                drawPath(path = buttonScratch, color = color, style = Fill)
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

private val CircleShape = RoundedPolygon.circle(numVertices = 10, radius = 1f)
private val BlobShape = RoundedPolygon.star(
    numVerticesPerRadius = 10,
    radius = 1f,
    innerRadius = 0.8f,
    rounding = CornerRounding(0.33333334f)
)
private val ButtonMorph = Morph(CircleShape, BlobShape)

private fun Morph.toComposePath(progress: Float, path: Path): Path {
    path.reset()
    var isFirst = true
    forEachCubic(progress) { cubic ->
        if (isFirst) {
            path.moveTo(cubic.anchor0X, cubic.anchor0Y)
            isFirst = false
        }
        path.cubicTo(
            cubic.control0X, cubic.control0Y,
            cubic.control1X, cubic.control1Y,
            cubic.anchor1X, cubic.anchor1Y
        )
    }
    path.close()
    return path
}

/**
 * Draws [source], authored in [viewport] units, scaled to fit this box and centred in it.
 *
 * The path is copied into [scratch] before being transformed, because transforming the cached one
 * would compound the scaling on every frame.
 */
private fun DrawScope.drawFitted(
    source: Path,
    viewport: Size,
    scratch: Path,
    matrix: Matrix,
    color: Color,
    alpha: Float,
) {
    val factor = min(size.width / viewport.width, size.height / viewport.height)
    scratch.rewind()
    scratch.addPath(source)
    matrix.reset()
    matrix.scale(x = factor, y = factor)
    scratch.transform(matrix)
    scratch.translate(size.center - scratch.getBounds().center)
    drawPath(path = scratch, color = color, alpha = alpha, style = Fill)
}

private val ACTIVE_BLOB_VIEWPORT = Size(236f, 236f)
private val PULSE_HALO_VIEWPORT = Size(176f, 182f)

/** `music_recognition_active_background_new`, the shape the button wears while listening. */
private const val ACTIVE_BLOB_PATH_DATA =
    "M95.85,8.64C108.2,-2.88 127.8,-2.88 140.15,8.64C147.22,15.22 157.04,18.3 166.77,16.97C183.79," +
        "14.65 199.65,25.76 202.62,42.08C204.31,51.41 210.38,59.46 219.07,63.9C234.25,71.66 240.3," +
        "89.63 232.76,104.51C228.44,113.02 228.44,122.98 232.76,131.49C240.3,146.37 234.25,164.34 " +
        "219.07,172.1C210.38,176.54 204.31,184.6 202.62,193.93C199.65,210.24 183.79,221.35 166.77," +
        "219.03C157.04,217.7 147.22,220.78 140.15,227.36C127.8,238.88 108.2,238.88 95.85,227.36C88" +
        ".78,220.78 78.96,217.7 69.23,219.03C52.21,221.35 36.35,210.24 33.38,193.93C31.69,184.6 25" +
        ".62,176.54 16.93,172.1C1.75,164.34 -4.3,146.37 3.24,131.49C7.56,122.98 7.56,113.02 3.24,1" +
        "04.51C-4.3,89.63 1.75,71.66 16.93,63.9C25.62,59.46 31.69,51.41 33.38,42.08C36.35,25.76 52" +
        ".21,14.65 69.23,16.97C78.96,18.3 88.78,15.22 95.85,8.64Z"

/** `music_recognition_button_active_background`, the halo that pulses behind it. */
private const val PULSE_HALO_PATH_DATA =
    "M76.96,2.72C83.88,-0.87 92.12,-0.87 99.04,2.72L110.62,8.72C112.95,9.93 115.47,10.74 118.06,11" +
        ".14L130.96,13.09C138.67,14.26 145.33,19.1 148.82,26.07L154.67,37.73C155.84,40.08 157.4,42" +
        ".21 159.27,44.06L168.55,53.22C174.1,58.7 176.64,66.53 175.37,74.22L173.25,87.09C172.82,89" +
        ".68 172.82,92.32 173.25,94.91L175.37,107.78C176.64,115.47 174.1,123.3 168.55,128.78L159.2" +
        "7,137.94C157.4,139.79 155.84,141.92 154.67,144.27L148.82,155.93C145.33,162.9 138.67,167.7" +
        "4 130.96,168.91L118.06,170.87C115.47,171.26 112.95,172.08 110.62,173.28L99.04,179.28C92.1" +
        "2,182.87 83.88,182.87 76.96,179.28L65.38,173.28C63.05,172.08 60.53,171.26 57.94,170.87L45" +
        ".04,168.91C37.33,167.74 30.67,162.9 27.18,155.93L21.33,144.27C20.16,141.92 18.6,139.79 16" +
        ".73,137.94L7.45,128.78C1.9,123.3 -0.64,115.47 0.63,107.78L2.75,94.91C3.18,92.32 3.18,89.6" +
        "8 2.75,87.09L0.63,74.22C-0.64,66.53 1.9,58.7 7.45,53.22L16.73,44.06C18.6,42.21 20.16,40.0" +
        "8 21.33,37.73L27.18,26.07C30.67,19.1 37.33,14.26 45.04,13.09L57.94,11.14C60.53,10.74 63.0" +
        "5,9.93 65.38,8.72L76.96,2.72Z"

/**
 * The Now Playing glyph: a note with two level bars beside it.
 *
 * Traced from `gs_pixel_now_playing_vd_theme_32` in the app's own resources, at its original 960
 * unit viewport, so it is the same outline rather than a lookalike.
 */
val NowPlayingNote: ImageVector by lazy {
    ImageVector.Builder(
        name = "NowPlayingNote",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
    ).addPath(
        pathData = PathParser().parsePathString(NOW_PLAYING_PATH_DATA).toNodes(),
        fill = SolidColor(Color.Black),
    ).build()
}

private const val NOW_PLAYING_PATH_DATA =
    "M581.33,661.71q-16.67-16.63-16.67-40.38V258q0-23.75 16.61-40.38T621.61,201t40.23," +
        "16.63T678.33,258V621.33q0,23.75-16.54,40.38t-40.17,16.63t-40.29-16.63ZM823.29,557q-23.63," +
        "0-40.29-16.53t-16.67-40.14V379q0-23.61 16.61-40.14t40.33-16.53t40.23,16.53T880,379V500.33" +
        "q0,23.61-16.54,40.14T823.29,557ZM258.33,880q-91.16,0-154.58-63.52T40.33,661.65t63.42-154." +
        "81t154.58-63.5q28.67,0 54.83,7.17T363,470.33V136.67q0-23.61 16.54-40.14T419.71,80T460,96." +
        "53t16.67,40.14v525q0,91.3-63.52,154.82T258.33,880Z"
