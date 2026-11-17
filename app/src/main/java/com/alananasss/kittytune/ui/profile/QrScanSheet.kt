package com.alananasss.kittytune.ui.profile

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.common.QrScannerPreview

/**
 * Pointing the phone at the desktop's pairing code (issue #33).
 *
 * A full-screen dialog rather than a screen inside the app's scaffold. Rendered inline it sat under the
 * navigation bar and the mini player, which both stayed on top of the viewfinder — a scanner has to own
 * the whole screen, and a dialog is what draws above that chrome without touching the navigation graph.
 *
 * Nothing is decoded off-device and no image is kept. The camera is bound to this screen's lifecycle, so
 * it stops the moment the dialog closes.
 */
@Composable
fun QrScanSheet(
    onCode: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            // The default dialog is inset and width-limited; a viewfinder wants every pixel.
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
        ),
    ) {
        ScanSurface(onCode = onCode, onDismiss = onDismiss)
    }
}

@Composable
private fun ScanSurface(onCode: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var detected by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    // Asked for on arrival rather than behind another button: there is nothing else this screen does.
    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(Modifier.fillMaxSize()) {
        if (granted) {
            QrScannerPreview(
                onDetected = { text ->
                    detected = true
                    onCode(text)
                },
                modifier = Modifier.fillMaxSize(),
            )
            ScannerOverlay(detected = detected)
        } else {
            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
                CameraDenied(
                    onRetry = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onDismiss = onDismiss,
                )
            }
        }

        // Always reachable, camera or not, and above the viewfinder rather than behind it.
        FilledTonalIconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
        ) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.btn_close))
        }
    }
}

/**
 * The scrim, the window, and what happens when a code is found.
 *
 * Everything outside the window is dimmed, which is what makes it read as a window rather than a
 * decorative square: four rects around the hole, because a single layer with a cut-out needs its own
 * graphics layer and blend mode for no visible gain at this size.
 *
 * The motion is spring-based rather than tweened. A code appearing is a physical event — the frame
 * should arrive at the lock and settle, not glide there on a curve — and springs are what Material 3
 * Expressive uses for exactly this kind of confirmation.
 */
@Composable
private fun ScannerOverlay(detected: Boolean) {
    val scheme = MaterialTheme.colorScheme

    // Breathing while searching. Stops on a hit: the stillness reads as "locked on" more than the
    // colour change does.
    val searching by rememberInfiniteTransition(label = "scannerSearch").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1_600)),
        label = "scannerSearchSweep",
    )

    val windowScale by animateFloatAsState(
        targetValue = if (detected) 1f else 0.94f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "scannerWindowScale",
    )
    val bracketColor by animateColorAsState(
        targetValue = if (detected) scheme.primary else Color.White,
        animationSpec = tween(220),
        label = "scannerBracketColor",
    )
    val bracketWidth by animateDpAsState(
        targetValue = if (detected) 6.dp else 4.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "scannerBracketWidth",
    )

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val side = size.minDimension * 0.72f * windowScale
                    val left = (size.width - side) / 2f
                    val top = (size.height - side) / 2f
                    val corner = 32.dp.toPx()
                    val scrim = Color.Black.copy(alpha = 0.55f)
                    val strokePx = bracketWidth.toPx()
                    // Corner brackets rather than a full outline: the four corners are what a scanner
                    // reads as an aiming frame, and they leave the code itself unobscured.
                    val armLength = side * 0.18f
                    val brackets = Path().apply {
                        moveTo(left, top + corner + armLength)
                        lineTo(left, top + corner)
                        quadraticTo(left, top, left + corner, top)
                        lineTo(left + corner + armLength, top)

                        moveTo(left + side - corner - armLength, top)
                        lineTo(left + side - corner, top)
                        quadraticTo(left + side, top, left + side, top + corner)
                        lineTo(left + side, top + corner + armLength)

                        moveTo(left + side, top + side - corner - armLength)
                        lineTo(left + side, top + side - corner)
                        quadraticTo(left + side, top + side, left + side - corner, top + side)
                        lineTo(left + side - corner - armLength, top + side)

                        moveTo(left + corner + armLength, top + side)
                        lineTo(left + corner, top + side)
                        quadraticTo(left, top + side, left, top + side - corner)
                        lineTo(left, top + side - corner - armLength)
                    }

                    onDrawWithContent {
                        drawContent()
                        // Four rects around the window.
                        drawRect(scrim, size = Size(size.width, top))
                        drawRect(scrim, topLeft = Offset(0f, top + side), size = Size(size.width, size.height - top - side))
                        drawRect(scrim, topLeft = Offset(0f, top), size = Size(left, side))
                        drawRect(scrim, topLeft = Offset(left + side, top), size = Size(size.width - left - side, side))

                        drawPath(brackets, color = bracketColor, style = Stroke(width = strokePx))

                        // A line travelling down the window while searching: the one cue that says the
                        // camera is actually working when there is nothing in frame yet.
                        if (!detected) {
                            val y = top + side * searching
                            drawRoundRect(
                                color = bracketColor.copy(alpha = 0.75f * (1f - kotlin.math.abs(searching - 0.5f) * 1.4f).coerceAtLeast(0f)),
                                topLeft = Offset(left + side * 0.06f, y),
                                size = Size(side * 0.88f, 3.dp.toPx()),
                                cornerRadius = CornerRadius(3.dp.toPx()),
                            )
                        }
                    }
                }
        )

        AnimatedVisibility(
            visible = detected,
            enter = fadeIn(tween(140)) +
                scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy), initialScale = 0.5f),
            exit = fadeOut(),
        ) {
            Surface(shape = RoundedCornerShape(percent = 50), color = scheme.primary, modifier = Modifier.size(84.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = scheme.surfaceContainerHigh.copy(alpha = 0.94f),
            ) {
                Text(
                    stringResource(if (detected) R.string.sync_scan_found else R.string.sync_scan_aim),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
                )
            }
        }
    }
}

/** Shown when the camera is refused. The pairing code can still be pasted, so this is not a dead end. */
@Composable
private fun CameraDenied(onRetry: () -> Unit, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.NoPhotography,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.sync_scan_no_camera),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.sync_scan_allow_camera)) }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_close)) }
    }
}
