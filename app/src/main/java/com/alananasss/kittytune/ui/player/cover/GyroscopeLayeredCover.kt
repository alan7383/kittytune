package com.alananasss.kittytune.ui.player.cover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Normalized 2D tilt state driven by the smartphone's gyroscope / rotation sensors.
 * [tiltX] (roll: tilting left/right) and [tiltY] (pitch: tilting forward/backward)
 * are normalized to [-1f, 1f].
 */
@Immutable
data class GyroscopeTilt(
    val tiltX: Float = 0f,
    val tiltY: Float = 0f
) {
    val magnitude: Float
        get() = sqrt(tiltX * tiltX + tiltY * tiltY).coerceAtMost(1f)

    companion object {
        val Zero = GyroscopeTilt(0f, 0f)
    }
}

/**
 * Remembers and observes the phone's gyroscope / rotation vector sensors to produce a smooth,
 * auto-calibrated [GyroscopeTilt] relative to the user's current holding posture.
 * Automatically unregisters sensor listeners when [enabled] is false or lifecycle pauses.
 */
@Composable
fun rememberGyroscopeTilt(enabled: Boolean): State<GyroscopeTilt> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val tiltState = remember { mutableStateOf(GyroscopeTilt.Zero) }

    DisposableEffect(context, lifecycleOwner, enabled) {
        if (!enabled) {
            tiltState.value = GyroscopeTilt.Zero
            return@DisposableEffect onDispose { }
        }

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        if (sensorManager == null) {
            return@DisposableEffect onDispose { }
        }

        val rotationSensor: Sensor? =
            sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
                ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                ?: sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
                ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val gyroSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)

        var hasBaseline = false
        var refPitch = 0f
        var refRoll = 0f

        var smoothX = 0f
        var smoothY = 0f
        var gyroBoostX = 0f
        var gyroBoostY = 0f
        var lastGyroTimestampNs = 0L

        // ~20 degrees of tilt maps to full [-1, 1] parallax travel
        val maxTiltRad = 0.35f

        fun wrapRadians(angle: Float): Float {
            var a = angle
            val twoPi = (2.0 * PI).toFloat()
            val pi = PI.toFloat()
            while (a > pi) a -= twoPi
            while (a < -pi) a += twoPi
            return a
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_GYROSCOPE -> {
                        val now = event.timestamp
                        if (lastGyroTimestampNs != 0L) {
                            val dt = ((now - lastGyroTimestampNs) * 1e-9f).coerceIn(0f, 0.1f)
                            val wx = event.values[0] // pitch velocity (rad/s)
                            val wy = event.values[1] // roll velocity (rad/s)

                            if (rotationSensor == null) {
                                // Pure gyroscope integration mode with gentle spring recentering
                                val decay = exp(-dt / 3.5f)
                                smoothX = ((smoothX + (wy * dt) / maxTiltRad) * decay).coerceIn(-1f, 1f)
                                smoothY = ((smoothY + (wx * dt) / maxTiltRad) * decay).coerceIn(-1f, 1f)
                                tiltState.value = GyroscopeTilt(smoothX, smoothY)
                            } else {
                                // Instantaneous angular velocity impulse for ultra-snappy response
                                val decay = exp(-dt / 0.25f)
                                gyroBoostX = ((gyroBoostX + wy * dt * 0.45f / maxTiltRad) * decay).coerceIn(-0.35f, 0.35f)
                                gyroBoostY = ((gyroBoostY + wx * dt * 0.45f / maxTiltRad) * decay).coerceIn(-0.35f, 0.35f)
                            }
                        }
                        lastGyroTimestampNs = now
                    }

                    Sensor.TYPE_GAME_ROTATION_VECTOR,
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, orientationAngles)
                        val pitch = orientationAngles[1]
                        val roll = orientationAngles[2]
                        updateFromAngles(pitch, roll)
                    }

                    Sensor.TYPE_GRAVITY,
                    Sensor.TYPE_ACCELEROMETER -> {
                        val gx = event.values[0]
                        val gy = event.values[1]
                        val gz = event.values[2]
                        val norm = sqrt(gx * gx + gy * gy + gz * gz).coerceAtLeast(0.001f)
                        val nx = gx / norm
                        val ny = gy / norm
                        val nz = gz / norm
                        val pitch = atan2(-ny, sqrt(nx * nx + nz * nz))
                        val roll = atan2(-nx, nz)
                        updateFromAngles(pitch, roll)
                    }
                }
            }

            private fun updateFromAngles(pitch: Float, roll: Float) {
                if (!hasBaseline) {
                    refPitch = pitch
                    refRoll = roll
                    hasBaseline = true
                }

                var dPitch = wrapRadians(pitch - refPitch)
                var dRoll = wrapRadians(roll - refRoll)

                // If user shifts their holding posture beyond the comfortable range,
                // smoothly pull the reference baseline along so it never gets stuck at the edge.
                val maxComfortRad = maxTiltRad * 1.35f
                if (abs(dPitch) > maxComfortRad) {
                    val excess = dPitch - (if (dPitch > 0) maxComfortRad else -maxComfortRad)
                    refPitch = wrapRadians(refPitch + excess * 0.15f)
                    dPitch = wrapRadians(pitch - refPitch)
                }
                if (abs(dRoll) > maxComfortRad) {
                    val excess = dRoll - (if (dRoll > 0) maxComfortRad else -maxComfortRad)
                    refRoll = wrapRadians(refRoll + excess * 0.15f)
                    dRoll = wrapRadians(roll - refRoll)
                }

                // Gentle posture adaptation so holding a new angle for a while recenters smoothly
                refPitch = wrapRadians(refPitch + dPitch * 0.004f)
                refRoll = wrapRadians(refRoll + dRoll * 0.004f)

                val rawX = ((dRoll / maxTiltRad) + gyroBoostX).coerceIn(-1f, 1f)
                val rawY = ((-dPitch / maxTiltRad) + gyroBoostY).coerceIn(-1f, 1f)

                // Low-pass filter for silky 60fps interpolation
                val smoothing = 0.18f
                smoothX += (rawX - smoothX) * smoothing
                smoothY += (rawY - smoothY) * smoothing

                if (abs(smoothX - tiltState.value.tiltX) > 0.002f ||
                    abs(smoothY - tiltState.value.tiltY) > 0.002f
                ) {
                    tiltState.value = GyroscopeTilt(smoothX, smoothY)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        var isRegistered = false
        fun registerSensors() {
            if (isRegistered) return
            rotationSensor?.let {
                sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
            }
            gyroSensor?.let {
                sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
            }
            isRegistered = true
        }

        fun unregisterSensors() {
            if (!isRegistered) return
            sensorManager.unregisterListener(listener)
            isRegistered = false
            hasBaseline = false
        }

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> registerSensors()
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> unregisterSensors()
                else -> Unit
            }
        }

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            registerSensors()
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            unregisterSensors()
        }
    }

    return tiltState
}

/**
 * Applies a 3D perspective card tilt driven by [tilt] to an album cover container.
 */
@Composable
fun Modifier.gyroscope3DCardTilt(
    tilt: GyroscopeTilt,
    enabled: Boolean,
    maxDegrees: Float = 8.5f
): Modifier {
    if (!enabled) return this
    val density = LocalDensity.current.density
    return this.graphicsLayer {
        rotationX = -tilt.tiltY * maxDegrees
        rotationY = tilt.tiltX * maxDegrees
        cameraDistance = 14f * density
    }
}

/**
 * Convenience modifier that observes [com.alananasss.kittytune.data.local.PlayerPreferences]
 * for the gyroscope 3D cover setting and applies [gyroscope3DCardTilt] when active.
 */
@Composable
fun Modifier.gyroscopeCoverContainer(maxDegrees: Float = 8.5f): Modifier {
    val context = LocalContext.current
    val prefs = remember { com.alananasss.kittytune.data.local.PlayerPreferences(context) }
    val enabled by prefs.getGyroscopeCoversFlow().collectAsState(initial = prefs.getGyroscopeCoversEnabled())
    val tilt by rememberGyroscopeTilt(enabled = enabled)
    return this.gyroscope3DCardTilt(tilt = tilt, enabled = enabled, maxDegrees = maxDegrees)
}

/**
 * Holds decomposed depth layers extracted from an album cover bitmap:
 * - [midground]: Intermediate scene layer with soft border feathering
 * - [foreground]: Salient foreground subject / typography / focal layer
 * - [shadow]: Soft drop shadow cast by the foreground layer onto the midground
 */
internal class DecomposedCoverLayers(
    val midground: ImageBitmap,
    val foreground: ImageBitmap,
    val shadow: ImageBitmap
)

private object LayeredCoverCache {
    private val cache = LruCache<String, DecomposedCoverLayers>(8)

    fun get(key: String): DecomposedCoverLayers? = synchronized(cache) { cache.get(key) }
    fun put(key: String, layers: DecomposedCoverLayers) = synchronized(cache) { cache.put(key, layers) }
}

/**
 * Decomposes an album cover [Bitmap] into multiple depth planes (Midground, Salient Foreground Subject,
 * and Inter-Layer Cast Shadow) so that tilting the phone moves the layers relative to each other
 * with a deep 3D parallax effect.
 */
internal object LayeredCoverDecomposer {

    fun decompose(source: Bitmap): DecomposedCoverLayers {
        val targetSize = 360
        val sw = min(source.width, targetSize).coerceAtLeast(64)
        val sh = min(source.height, targetSize).coerceAtLeast(64)

        val workBitmap = if (source.width != sw || source.height != sh || source.config != Bitmap.Config.ARGB_8888) {
            Bitmap.createScaledBitmap(source, sw, sh, true).let { scaled ->
                if (scaled.config == Bitmap.Config.ARGB_8888) scaled
                else scaled.copy(Bitmap.Config.ARGB_8888, false)
            }
        } else {
            source
        }

        val pixels = IntArray(sw * sh)
        workBitmap.getPixels(pixels, 0, sw, 0, 0, sw, sh)

        // 1. Sample outer perimeter pixels to model the background palette (mean + variance in RGB)
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var borderCount = 0
        val borderMargin = (min(sw, sh) * 0.08f).toInt().coerceAtLeast(4)

        for (y in 0 until sh step 2) {
            for (x in 0 until sw step 2) {
                if (x < borderMargin || x >= sw - borderMargin || y < borderMargin || y >= sh - borderMargin) {
                    val c = pixels[y * sw + x]
                    sumR += (c shr 16) and 0xFF
                    sumG += (c shr 8) and 0xFF
                    sumB += c and 0xFF
                    borderCount++
                }
            }
        }
        val bgR = if (borderCount > 0) (sumR / borderCount).toFloat() else 128f
        val bgG = if (borderCount > 0) (sumG / borderCount).toFloat() else 128f
        val bgB = if (borderCount > 0) (sumB / borderCount).toFloat() else 128f

        // 2. Compute luminance for local edge/detail salience (Sobel gradient)
        val luma = FloatArray(sw * sh)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            luma[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        val fgAlpha = IntArray(sw * sh)
        val midAlpha = IntArray(sw * sh)

        val invHalfW = 2f / sw
        val invHalfH = 2f / sh

        for (y in 1 until sh - 1) {
            val ny = (y * invHalfH) - 1f // [-1, 1]
            val rowOffset = y * sw
            for (x in 1 until sw - 1) {
                val nx = (x * invHalfW) - 1f // [-1, 1]
                val idx = rowOffset + x
                val c = pixels[idx]
                val r = ((c shr 16) and 0xFF).toFloat()
                val g = ((c shr 8) and 0xFF).toFloat()
                val b = (c and 0xFF).toFloat()

                // Color distance from background border palette
                val dr = r - bgR
                val dg = g - bgG
                val db = b - bgB
                val colorDist = sqrt(dr * dr + dg * dg + db * db) / 220f

                // Local edge / high-frequency detail energy (typography, subject contours, logos)
                val gx = -luma[idx - sw - 1] + luma[idx - sw + 1] -
                    2f * luma[idx - 1] + 2f * luma[idx + 1] -
                    luma[idx + sw - 1] + luma[idx + sw + 1]
                val gy = -luma[idx - sw - 1] - 2f * luma[idx - sw] - luma[idx - sw + 1] +
                    luma[idx + sw - 1] + 2f * luma[idx + sw] + luma[idx + sw + 1]
                val edgeEnergy = (sqrt(gx * gx + gy * gy) / 340f).coerceIn(0f, 1f)

                // Radial spatial envelope: protects outer edges from hard clipping when shifting
                val distSq = nx * nx + ny * ny
                val radialDist = sqrt(distSq)
                // Smooth window that stays ~1.0 inside r < 0.65 and feathers smoothly to 0 at r = 0.96
                val edgeFeather = ((0.96f - max(abs(nx), abs(ny))) / 0.26f).coerceIn(0f, 1f)
                val smoothEdgeFeather = edgeFeather * edgeFeather * (3f - 2f * edgeFeather)

                // Central focal weight guarantees a 3D foreground layer even on minimal covers
                val centerFocus = (1f - (radialDist / 0.82f).coerceIn(0f, 1f)).let { it * it }

                val rawSalience = max(
                    colorDist * 0.85f + edgeEnergy * 0.65f,
                    centerFocus * 0.72f + edgeEnergy * 0.4f
                )
                // S-curve contrast on salience
                val contrasted = ((rawSalience - 0.22f) / 0.52f).coerceIn(0f, 1f)
                val smoothSalience = contrasted * contrasted * (3f - 2f * contrasted)

                val finalFg = (smoothSalience * smoothEdgeFeather * 255f).toInt().coerceIn(0, 255)
                fgAlpha[idx] = finalFg

                // Midground layer has a broader spatial envelope
                val midFeather = ((0.98f - max(abs(nx), abs(ny))) / 0.18f).coerceIn(0f, 1f)
                val midVal = ((0.35f + 0.65f * rawSalience.coerceIn(0f, 1f)) * midFeather * 255f).toInt().coerceIn(0, 255)
                midAlpha[idx] = midVal
            }
        }

        // 3. Smooth morphological feathering (fast separable box blur on alpha masks)
        val blurredFgAlpha = boxBlurAlpha(fgAlpha, sw, sh, radius = 6)
        val shadowAlpha = boxBlurAlpha(blurredFgAlpha, sw, sh, radius = 12)

        val fgPixels = IntArray(sw * sh)
        val midPixels = IntArray(sw * sh)
        val shadowPixels = IntArray(sw * sh)

        for (i in pixels.indices) {
            val rgb = pixels[i] and 0x00FFFFFF
            val aFg = blurredFgAlpha[i]
            val aMid = midAlpha[i]
            val aSh = (shadowAlpha[i] * 0.55f).toInt().coerceIn(0, 180)

            fgPixels[i] = (aFg shl 24) or rgb
            midPixels[i] = (aMid shl 24) or rgb
            shadowPixels[i] = (aSh shl 24) // Pure black with soft alpha
        }

        val fgBitmap = Bitmap.createBitmap(fgPixels, sw, sh, Bitmap.Config.ARGB_8888)
        val midBitmap = Bitmap.createBitmap(midPixels, sw, sh, Bitmap.Config.ARGB_8888)
        val shBitmap = Bitmap.createBitmap(shadowPixels, sw, sh, Bitmap.Config.ARGB_8888)

        return DecomposedCoverLayers(
            midground = midBitmap.asImageBitmap(),
            foreground = fgBitmap.asImageBitmap(),
            shadow = shBitmap.asImageBitmap()
        )
    }

    private fun boxBlurAlpha(src: IntArray, w: Int, h: Int, radius: Int): IntArray {
        val temp = IntArray(w * h)
        val dst = IntArray(w * h)
        val window = 2 * radius + 1

        // Horizontal pass
        for (y in 0 until h) {
            var sum = 0
            val row = y * w
            for (i in -radius..radius) {
                val x = i.coerceIn(0, w - 1)
                sum += src[row + x]
            }
            for (x in 0 until w) {
                temp[row + x] = sum / window
                val outX = (x - radius).coerceIn(0, w - 1)
                val inX = (x + radius + 1).coerceIn(0, w - 1)
                sum += src[row + inX] - src[row + outX]
            }
        }

        // Vertical pass
        for (x in 0 until w) {
            var sum = 0
            for (i in -radius..radius) {
                val y = i.coerceIn(0, h - 1)
                sum += temp[y * w + x]
            }
            for (y in 0 until h) {
                dst[y * w + x] = sum / window
                val outY = (y - radius).coerceIn(0, h - 1)
                val inY = (y + radius + 1).coerceIn(0, h - 1)
                sum += temp[inY * w + x] - temp[outY * w + x]
            }
        }
        return dst
    }
}

/**
 * Renders an album cover as a multi-layered 3D shadowbox driven by [tilt]:
 * - Layer 0 (Deep Background): Scaled up, slightly softened & dimmed, moves opposite to tilt.
 * - Layer 1 (Midground Scene): Intermediate parallax plane.
 * - Layer 2 (Dynamic Inter-Layer Drop Shadow): Casts a shifting 3D shadow behind the subject.
 * - Layer 3 (Salient Foreground Subject): Moves forward with the tilt + subtle 3D counter-rotation.
 * - Layer 4 (Specular Glint & 3D Depth Rim): Dynamic light sheen that glides across the cover.
 */
@Composable
fun GyroscopeLayeredCoverContent(
    artworkUrl: String?,
    tilt: GyroscopeTilt,
    contentScale: ContentScale,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    var layers by remember(artworkUrl) {
        mutableStateOf(artworkUrl?.let { LayeredCoverCache.get(it) })
    }
    var loadedBitmap by remember(artworkUrl) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(artworkUrl, loadedBitmap) {
        val url = artworkUrl ?: return@LaunchedEffect
        val cached = LayeredCoverCache.get(url)
        if (cached != null) {
            layers = cached
            return@LaunchedEffect
        }
        val bmp = loadedBitmap ?: return@LaunchedEffect
        val computed = withContext(Dispatchers.Default) {
            runCatching { LayeredCoverDecomposer.decompose(bmp) }.getOrNull()
        }
        if (computed != null) {
            LayeredCoverCache.put(url, computed)
            layers = computed
        }
    }

    val maxShiftPx = with(density) { 18.dp.toPx() }
    val shadowDownOffsetPx = with(density) { 5.dp.toPx() }

    val bgColorFilter = remember {
        val cm = ColorMatrix().apply {
            setToScale(0.76f, 0.76f, 0.80f, 1f)
        }
        ColorFilter.colorMatrix(cm)
    }

    val imageRequest = remember(context, artworkUrl) {
        ImageRequest.Builder(context)
            .data(artworkUrl)
            .allowHardware(false) // Needed for pixel salience decomposition
            .crossfade(true)
            .build()
    }

    Box(modifier = modifier.clipToBounds()) {
        // Layer 0: Deep Background Stage (moves furthest back, opposite to tilt)
        AsyncImage(
            model = imageRequest,
            contentDescription = contentDescription,
            contentScale = contentScale,
            colorFilter = if (layers != null) bgColorFilter else null,
            onSuccess = { state ->
                if (layers == null) {
                    val drawable = state.result.drawable
                    loadedBitmap = (drawable as? BitmapDrawable)?.bitmap
                        ?: runCatching { drawable.toBitmap() }.getOrNull()
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.22f
                    scaleY = 1.22f
                    translationX = -tilt.tiltX * maxShiftPx * 1.15f
                    translationY = -tilt.tiltY * maxShiftPx * 1.15f
                }
                .blur(if (layers != null) 4.dp else 0.dp)
        )

        val currentLayers = layers
        if (currentLayers != null) {
            // Layer 1: Midground Scene Plane (intermediate parallax depth)
            Image(
                bitmap = currentLayers.midground,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.12f
                        scaleY = 1.12f
                        translationX = -tilt.tiltX * maxShiftPx * 0.42f
                        translationY = -tilt.tiltY * maxShiftPx * 0.42f
                    }
            )

            // Layer 2: Inter-Layer Cast Shadow (detaches foreground subject from background)
            Image(
                bitmap = currentLayers.shadow,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.07f
                        scaleY = 1.07f
                        translationX = tilt.tiltX * maxShiftPx * 0.22f
                        translationY = tilt.tiltY * maxShiftPx * 0.22f + shadowDownOffsetPx
                    }
            )

            // Layer 3: Salient Foreground Subject Plane (pops forward in 3D)
            Image(
                bitmap = currentLayers.foreground,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.06f
                        scaleY = 1.06f
                        translationX = tilt.tiltX * maxShiftPx * 0.85f
                        translationY = tilt.tiltY * maxShiftPx * 0.85f
                        rotationX = -tilt.tiltY * 3.5f
                        rotationY = tilt.tiltX * 3.5f
                    }
            )
        }

        // Layer 4: Dynamic Specular Glint & 3D Inner Depth Rim (closest to the glass)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            val glintCenter = Offset(
                x = w * (0.5f - tilt.tiltX * 0.42f),
                y = h * (0.5f - tilt.tiltY * 0.42f)
            )
            val glintAlpha = (0.06f + tilt.magnitude * 0.14f).coerceAtMost(0.20f)

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = glintAlpha),
                        Color.White.copy(alpha = glintAlpha * 0.35f),
                        Color.Transparent
                    ),
                    center = glintCenter,
                    radius = max(w, h) * 0.72f
                ),
                blendMode = BlendMode.Screen
            )

            // Subtle directional rim highlight along the tilted edge
            val rimAlpha = (0.10f + tilt.magnitude * 0.18f).coerceAtMost(0.28f)
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = rimAlpha),
                        Color.Transparent,
                        Color.Black.copy(alpha = rimAlpha * 0.7f)
                    ),
                    start = Offset(
                        x = w * (0.5f - tilt.tiltX * 0.5f),
                        y = h * (0.5f - tilt.tiltY * 0.5f)
                    ),
                    end = Offset(
                        x = w * (0.5f + tilt.tiltX * 0.5f),
                        y = h * (0.5f + tilt.tiltY * 0.5f)
                    )
                ),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}
