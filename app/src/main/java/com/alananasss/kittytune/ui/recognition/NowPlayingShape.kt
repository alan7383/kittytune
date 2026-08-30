package com.alananasss.kittytune.ui.recognition

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

/** `button_pulse_scale_interpolator`: cubic(0, 0, 0, 1), an almost instant departure. */
private val PulseEasing: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)

private const val SQUASH_SCALE = 0.95f
private const val SQUASH_MS = 200
private const val BLOOM_DELAY_MS = 83L
private const val BLOOM_SCALE = 1.3111111f
private const val BLOOM_MS = 300

private const val PULSE_START_DELAY_MS = 100L
private const val PULSE_SCALE_TO = 1.818f
private const val PULSE_SCALE_MS = 1150
private const val PULSE_FADE_IN_MS = 500
private const val PULSE_FADE_OUT_MS = 450
private const val PULSE_PEAK_ALPHA = 0.15f

/** The 12 ms no-op the app plays after each pulse before the listener restarts it. */
private const val PULSE_TAIL_MS = 12L

/**
 * The listen button's shape and its tap animation.
 *
 * @param active true while the app is listening: the button wears the scalloped shape, sits at its
 *   bloomed size, and the halo behind it pulses.
 * @param color the fill for both the button and its halo.
 * @param content centred on the button. It scales with the shape, because in the app the scale is
 *   set on the `ImageButton` itself and a View's scale carries its `src` along with its background.
 */
@Composable
fun NowPlayingListenButton(
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val buttonScale = remember { Animatable(1f) }
    val pulseScale = remember { Animatable(1f) }
    val pulseAlpha = remember { Animatable(0f) }
    // The swap happens on the squash's first frame rather than at the end, so the shape has already
    // changed while the button is still shrinking.
    val wearingBlob = remember { Animatable(0f) }

    LaunchedEffect(active) {
        if (!active) {
            buttonScale.snapTo(1f)
            pulseScale.snapTo(1f)
            pulseAlpha.snapTo(0f)
            wearingBlob.snapTo(0f)
            return@LaunchedEffect
        }

        launch {
            while (true) {
                delay(PULSE_START_DELAY_MS)
                pulseScale.snapTo(1f)
                pulseAlpha.snapTo(0f)
                launch {
                    pulseScale.animateTo(
                        PULSE_SCALE_TO,
                        tween(PULSE_SCALE_MS, easing = PulseEasing),
                    )
                }
                // Fades all the way in, then all the way back out, while the scale keeps going.
                pulseAlpha.animateTo(PULSE_PEAK_ALPHA, tween(PULSE_FADE_IN_MS, easing = LinearEasing))
                pulseAlpha.animateTo(0f, tween(PULSE_FADE_OUT_MS, easing = LinearEasing))
                delay(PULSE_TAIL_MS)
            }
        }

        wearingBlob.snapTo(1f)
        buttonScale.animateTo(SQUASH_SCALE, tween(SQUASH_MS, easing = EmphasizedDecelerate))
        delay(BLOOM_DELAY_MS)
        buttonScale.animateTo(BLOOM_SCALE, tween(BLOOM_MS, easing = StandardAccelerate))
    }

    val blobPath = remember { PathParser().parsePathString(ACTIVE_BLOB_PATH_DATA).toPath() }
    val haloPath = remember { PathParser().parsePathString(PULSE_HALO_PATH_DATA).toPath() }
    val scratch = remember { Path() }
    val matrix = remember { Matrix() }

    Box(
        // The halo is a sibling of the button in the app, constrained to its bounds, so it pulses on
        // its own scale rather than inheriting the button's.
        modifier = modifier.drawBehind {
            val alpha = pulseAlpha.value
            if (alpha <= 0f) return@drawBehind
            scale(pulseScale.value) {
                drawFitted(haloPath, PULSE_HALO_VIEWPORT, scratch, matrix, color, alpha)
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
                    if (wearingBlob.value > 0f) {
                        drawFitted(blobPath, ACTIVE_BLOB_VIEWPORT, scratch, matrix, color, 1f)
                    } else {
                        val diameter = min(size.width, size.height)
                        drawCircle(color = color, radius = diameter / 2f, center = size.center)
                    }
                },
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
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
