package com.alananasss.kittytune.ui.player.audio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.common.KittyModalBottomSheet
import com.alananasss.kittytune.ui.common.Slider
import com.alananasss.kittytune.ui.player.EqualizerPresets
import com.alananasss.kittytune.ui.player.PlayerViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

private val FREQUENCY_LABELS = listOf(
    "32", "64", "125", "200", "315", "500", "800", "1.2k",
    "2k", "3.1k", "5k", "8k", "10k", "12.5k", "16k", "20k"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val eqState = viewModel.equalizerState
    val haptic = LocalHapticFeedback.current

    KittyModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header Row: Icon + Title + Switch + Reset
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Equalizer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = stringResource(R.string.equalizer_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.equalizer_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            viewModel.resetEqualizer()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = stringResource(R.string.equalizer_reset),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = eqState.isEnabled,
                        onCheckedChange = {
                            viewModel.toggleEqualizer()
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    )
                }
            }

            // Frequency Curve Canvas
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.equalizer_response_curve),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (eqState.isEnabled) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            }
                        ) {
                            Text(
                                text = if (eqState.isEnabled) eqState.selectedPreset else stringResource(R.string.equalizer_off),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = if (eqState.isEnabled) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    EqualizerCurveVisualizer(
                        bandGains = eqState.bandGainsDb,
                        isEnabled = eqState.isEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                    )
                }
            }

            // Presets Horizontal Row
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.equalizer_presets),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(EqualizerPresets.all) { preset ->
                        val isSelected = eqState.selectedPreset == preset.name && eqState.isEnabled
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                viewModel.applyEqualizerPreset(preset)
                                if (!eqState.isEnabled) viewModel.toggleEqualizer()
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            label = { Text(preset.name) },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // Preamp Slider Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.equalizer_preamp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = String.format("%+.1f dB", eqState.preampDb),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = eqState.preampDb,
                        onValueChange = { newPreamp ->
                            viewModel.setEqualizerPreamp(newPreamp)
                        },
                        valueRange = -12f..12f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 16-Band Sliders (Horizontally Scrollable)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.equalizer_bands_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.equalizer_double_tap_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (i in 0 until 16) {
                            val gain = eqState.bandGainsDb.getOrElse(i) { 0f }
                            val label = FREQUENCY_LABELS.getOrElse(i) { "" }

                            EqualizerBandColumn(
                                frequencyLabel = label,
                                gainDb = gain,
                                isEnabled = eqState.isEnabled,
                                onGainChange = { newGain ->
                                    viewModel.setEqualizerBand(i, newGain)
                                },
                                onReset = {
                                    viewModel.setEqualizerBand(i, 0f)
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * An expressive vertical slider for a single frequency band.
 */
@Composable
private fun EqualizerBandColumn(
    frequencyLabel: String,
    gainDb: Float,
    isEnabled: Boolean,
    onGainChange: (Float) -> Unit,
    onReset: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var lastHapticCrossZero by remember { mutableStateOf(false) }

    val trackHeight = 130.dp
    val trackWidth = 5.dp
    val thumbSize = 22.dp

    val primaryColor = MaterialTheme.colorScheme.primary
    val trackBgColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val centerLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)

    Column(
        modifier = Modifier
            .width(46.dp)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Gain value badge
        Text(
            text = if (abs(gainDb) < 0.2f) "0" else String.format("%+.0f", gainDb),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            fontWeight = if (abs(gainDb) >= 0.5f) FontWeight.Bold else FontWeight.Normal,
            color = if (isEnabled && abs(gainDb) >= 0.5f) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        // Custom Vertical Slider
        Box(
            modifier = Modifier
                .height(trackHeight)
                .width(42.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { onReset() },
                        onTap = { offset ->
                            val heightPx = size.height.toFloat()
                            val frac = 1f - (offset.y / heightPx).coerceIn(0f, 1f)
                            val newGain = (frac * 24f - 12f).coerceIn(-12f, 12f)
                            onGainChange(newGain)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, _ ->
                        change.consume()
                        val heightPx = size.height.toFloat()
                        val frac = 1f - (change.position.y / heightPx).coerceIn(0f, 1f)
                        val newGain = (frac * 24f - 12f).coerceIn(-12f, 12f)

                        // Trigger haptic when crossing 0 dB
                        val nearZero = abs(newGain) < 0.5f
                        if (nearZero && !lastHapticCrossZero) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastHapticCrossZero = true
                        } else if (!nearZero) {
                            lastHapticCrossZero = false
                        }

                        onGainChange(newGain)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Track & Center Line Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cW = size.width
                val cH = size.height
                val midX = cW / 2f
                val midY = cH / 2f

                val tW = trackWidth.toPx()

                // Background track
                drawRoundRect(
                    color = trackBgColor,
                    topLeft = Offset(midX - tW / 2f, 0f),
                    size = Size(tW, cH),
                    cornerRadius = CornerRadius(tW / 2f)
                )

                // Active gain fill from center (0 dB)
                val gainFrac = (gainDb / 12f).coerceIn(-1f, 1f)
                val activeH = abs(gainFrac) * (cH / 2f)

                if (isEnabled && activeH > 1f) {
                    val topY = if (gainFrac > 0) midY - activeH else midY
                    drawRoundRect(
                        color = primaryColor,
                        topLeft = Offset(midX - tW / 2f, topY),
                        size = Size(tW, activeH),
                        cornerRadius = CornerRadius(tW / 2f)
                    )
                }

                // Center 0 dB notch tick
                drawLine(
                    color = centerLineColor,
                    start = Offset(midX - 8.dp.toPx(), midY),
                    end = Offset(midX + 8.dp.toPx(), midY),
                    strokeWidth = 1.5.dp.toPx()
                )
            }

            // Thumb
            val normalizedPos = 1f - ((gainDb + 12f) / 24f).coerceIn(0f, 1f)
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val maxHPx = with(density) { maxHeight.toPx() }
                val thumbSizePx = with(density) { thumbSize.toPx() }
                val thumbY = (normalizedPos * (maxHPx - thumbSizePx))

                Surface(
                    modifier = Modifier
                        .offset(y = with(density) { thumbY.toDp() })
                        .size(thumbSize),
                    shape = CircleShape,
                    color = if (isEnabled) primaryColor else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        2.dp,
                        if (isEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline
                    ),
                    shadowElevation = 3.dp
                ) {}
            }
        }

        // Frequency Label
        Text(
            text = frequencyLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/**
 * Visual curve showing the continuous spline frequency response across the 16 bands.
 */
@Composable
private fun EqualizerCurveVisualizer(
    bandGains: List<Float>,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val curveColor = if (isEnabled) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val zeroLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    Canvas(modifier = modifier) {
        val cW = size.width
        val cH = size.height
        val midY = cH / 2f
        val numBands = bandGains.size.coerceAtLeast(1)

        // Draw 0 dB baseline
        drawLine(
            color = zeroLineColor,
            start = Offset(0f, midY),
            end = Offset(cW, midY),
            strokeWidth = 1.dp.toPx()
        )

        // Draw +6 dB and -6 dB faint dashed/subtle lines
        val quarterH = cH / 4f
        drawLine(
            color = zeroLineColor.copy(alpha = 0.2f),
            start = Offset(0f, midY - quarterH),
            end = Offset(cW, midY - quarterH),
            strokeWidth = 0.8.dp.toPx()
        )
        drawLine(
            color = zeroLineColor.copy(alpha = 0.2f),
            start = Offset(0f, midY + quarterH),
            end = Offset(cW, midY + quarterH),
            strokeWidth = 0.8.dp.toPx()
        )

        if (numBands < 2) return@Canvas

        // Calculate control points
        val points = mutableListOf<Offset>()
        for (i in 0 until numBands) {
            val gain = bandGains.getOrElse(i) { 0f }
            val x = (i.toFloat() / (numBands - 1).toFloat()) * cW
            val y = midY - (gain / 12f) * (midY - 6.dp.toPx())
            points.add(Offset(x, y.coerceIn(4f, cH - 4f)))
        }

        // Build smooth cubic bezier curve
        val strokePath = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 0 until points.size - 1) {
                val p0 = points[i]
                val p1 = points[i + 1]
                val midX = (p0.x + p1.x) / 2f
                cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
            }
        }

        // Build filled gradient path underneath
        val fillPath = Path().apply {
            addPath(strokePath)
            lineTo(cW, midY)
            lineTo(0f, midY)
            close()
        }

        // Draw soft gradient area towards 0 dB
        if (isEnabled) {
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.25f),
                        primaryColor.copy(alpha = 0.05f),
                        Color.Transparent
                    )
                )
            )
        }

        // Draw continuous stroke curve
        drawPath(
            path = strokePath,
            color = curveColor,
            style = Stroke(width = 2.5.dp.toPx())
        )

        // Draw small dot on each band node
        points.forEach { pt ->
            drawCircle(
                color = curveColor,
                radius = 2.5.dp.toPx(),
                center = pt
            )
        }
    }
}
