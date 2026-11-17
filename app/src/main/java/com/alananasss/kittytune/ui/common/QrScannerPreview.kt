package com.alananasss.kittytune.ui.common

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A camera preview that reports the first QR code it sees (issue #33).
 *
 * The desktop shows a pairing QR; without this it was a picture of nothing. zxing was already in the
 * project for drawing codes, so only the frames were missing — CameraX supplies those and the decoding
 * is the same library in reverse.
 *
 * Decoding runs on its own single thread, never on the main one: a frame is a few hundred kilobytes of
 * luminance data and scanning it takes long enough to drop the preview's frame rate if done inline.
 *
 * @param onDetected called once, on the main thread, with the decoded text. Further frames are ignored
 *   after that — a scanner that fires repeatedly while the phone is still pointed at the code would
 *   start the same pairing several times.
 */
@Composable
fun QrScannerPreview(
    onDetected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            // COMPATIBLE, not the default PERFORMANCE. Performance mode backs the preview with a
            // SurfaceView, which Android composites in its own layer *below* the Compose surface — so
            // the camera was running and drawing into a layer nothing could see, which is why the
            // scanner came up as an empty frame on the app's background (issue #33). Compatible mode
            // uses a TextureView, which lives in the view hierarchy and composites normally.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    /** Set the moment something is decoded, so the callback can only ever fire once. */
    val consumed = remember { AtomicBoolean(false) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null

        providerFuture.addListener({
            provider = providerFuture.get()

            val preview = Preview.Builder().build().apply {
                surfaceProvider = previewView.surfaceProvider
            }

            // A modest resolution on purpose: a QR fills a good part of the frame at scanning
            // distance, and a 4K frame costs far more to binarize for no extra reliability.
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER)
                        )
                        .build()
                )
                // Only the newest frame matters; queuing them up would scan a backlog of stale views.
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val reader = QRCodeReader()
            analysis.setAnalyzer(analysisExecutor) { image ->
                if (!consumed.get()) {
                    decodeQr(image, reader)?.let { text ->
                        if (consumed.compareAndSet(false, true)) {
                            ContextCompat.getMainExecutor(context).execute { onDetected(text) }
                        }
                    }
                }
                image.close()
            }

            runCatching {
                provider?.unbindAll()
                provider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { provider?.unbindAll() }
            analysisExecutor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

/**
 * Decodes one camera frame, or returns null when it holds no readable code.
 *
 * Reads the Y plane alone. Luminance is all a QR needs and it is the first plane of every format
 * CameraX hands over, which avoids converting to RGB for every frame.
 *
 * The row stride is usually the width but is allowed to be larger, and ignoring that turns the image
 * into diagonal mush — so the data is repacked when they differ.
 */
private fun decodeQr(image: ImageProxy, reader: QRCodeReader): String? {
    val plane = image.planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)

    val width = image.width
    val height = image.height
    val rowStride = plane.rowStride
    val packed = if (rowStride == width) {
        bytes
    } else {
        ByteArray(width * height).also { out ->
            for (row in 0 until height) {
                val from = row * rowStride
                if (from + width > bytes.size) break
                bytes.copyInto(out, row * width, from, from + width)
            }
        }
    }

    val source = PlanarYUVLuminanceSource(
        packed, width, height, 0, 0, width, height, false,
    )
    return runCatching {
        reader.decode(
            BinaryBitmap(HybridBinarizer(source)),
            // A pairing code is a dense QR held still; trying harder is worth the cycles here and
            // costs nothing on the frames that hold no code at all.
            mapOf(DecodeHintType.TRY_HARDER to true),
        ).text
    }.getOrNull().also {
        // zxing throws NotFoundException on most frames, which is normal and not worth logging.
        reader.reset()
    }
}
