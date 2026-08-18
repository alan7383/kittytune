package com.alananasss.kittytune.ui.upload

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CropRotate
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.profile.BitmapUtils
import com.alananasss.kittytune.ui.profile.ImageState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TrackArtworkCropDialog(
    bitmap: Bitmap?,
    titleRes: Int = R.string.upload_crop_artwork_title,
    descRes: Int = R.string.upload_crop_artwork_desc,
    onDismiss: () -> Unit,
    onSave: (Bitmap) -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var allowGestureRotation by remember { mutableStateOf(false) }

    var containerSize by remember { mutableStateOf(Size.Zero) }

    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(titleRes),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(descRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(18.dp))

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .onGloballyPositioned { coordinates ->
                                containerSize = coordinates.size.toSize()
                            }
                            .pointerInput(allowGestureRotation, rotation, containerSize) {
                                detectTransformGestures { _, pan, zoom, rotationChange ->
                                    if (bitmap != null && containerSize.width > 0 && containerSize.height > 0) {
                                        val newScale = (scale * zoom).coerceIn(1f, 5f)
                                        scale = newScale

                                        if (allowGestureRotation) {
                                            rotation = (rotation + rotationChange) % 360f
                                        }

                                        val imageWidth = bitmap.width.toFloat()
                                        val imageHeight = bitmap.height.toFloat()
                                        val canvasWidth = containerSize.width
                                        val canvasHeight = containerSize.height

                                        val cropMarginPx = with(density) { 16.dp.toPx() }
                                        val cropSize = min(canvasWidth, canvasHeight) - cropMarginPx * 2f

                                        val baseScale = cropSize / min(imageWidth, imageHeight)
                                        val baseWidth = imageWidth * baseScale
                                        val baseHeight = imageHeight * baseScale

                                        val rad = Math.toRadians(rotation.toDouble())
                                        val cos = Math.abs(Math.cos(rad)).toFloat()
                                        val sin = Math.abs(Math.sin(rad)).toFloat()

                                        val effectiveWidth = (baseWidth * cos + baseHeight * sin) * scale
                                        val effectiveHeight = (baseWidth * sin + baseHeight * cos) * scale

                                        val maxOffsetX = (effectiveWidth - cropSize) / 2f
                                        val maxOffsetY = (effectiveHeight - cropSize) / 2f

                                        val limitX = max(0f, maxOffsetX)
                                        val limitY = max(0f, maxOffsetY)

                                        offsetX = (offsetX + pan.x).coerceIn(-limitX, limitX)
                                        offsetY = (offsetY + pan.y).coerceIn(-limitY, limitY)
                                    }
                                }
                            }
                    ) {
                        if (bitmap != null) {
                            val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val canvasWidth = size.width
                                val canvasHeight = size.height
                                val cropMargin = 16.dp.toPx()
                                val cropSize = min(canvasWidth, canvasHeight) - cropMargin * 2
                                val cornerRadius = 16.dp.toPx()

                                val imageWidth = imageBitmap.width.toFloat()
                                val imageHeight = imageBitmap.height.toFloat()

                                val baseScale = cropSize / min(imageWidth, imageHeight)
                                val drawWidth = imageWidth * baseScale
                                val drawHeight = imageHeight * baseScale

                                val left = (canvasWidth - drawWidth) / 2f
                                val top = (canvasHeight - drawHeight) / 2f

                                withTransform({
                                    val px = canvasWidth / 2f
                                    val py = canvasHeight / 2f

                                    translate(left = offsetX, top = offsetY)
                                    scale(scale, scale, pivot = Offset(px, py))
                                    rotate(rotation, pivot = Offset(px, py))
                                }) {
                                    drawImage(
                                        image = imageBitmap,
                                        dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
                                        dstSize = androidx.compose.ui.unit.IntSize(
                                            drawWidth.toInt(),
                                            drawHeight.toInt()
                                        )
                                    )
                                }

                                val overlayPath = Path().apply {
                                    addRect(Rect(0f, 0f, canvasWidth, canvasHeight))
                                }

                                val cropRect = Rect(
                                    left = (canvasWidth - cropSize) / 2f,
                                    top = (canvasHeight - cropSize) / 2f,
                                    right = (canvasWidth + cropSize) / 2f,
                                    bottom = (canvasHeight + cropSize) / 2f
                                )

                                val cropPath = Path().apply {
                                    addRoundRect(
                                        RoundRect(
                                            rect = cropRect,
                                            radiusX = cornerRadius,
                                            radiusY = cornerRadius
                                        )
                                    )
                                }

                                val finalScrimPath = Path.combine(
                                    operation = PathOperation.Difference,
                                    path1 = overlayPath,
                                    path2 = cropPath
                                )

                                drawPath(
                                    path = finalScrimPath,
                                    color = Color.Black.copy(alpha = 0.55f)
                                )

                                drawPath(
                                    path = cropPath,
                                    color = Color.White,
                                    style = Stroke(width = 2.5.dp.toPx())
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ToggleButton(
                            checked = false,
                            onCheckedChange = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                rotation = (rotation + 90f) % 360f
                                offsetX = 0f
                                offsetY = 0f
                            },
                            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                            colors = ToggleButtonDefaults.toggleButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                checkedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                checkedContentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.RotateRight,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.upload_crop_rotate_90),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        val freeRotateContainerColor by animateColorAsState(
                            targetValue = if (allowGestureRotation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                            animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                            label = "toggle_free_rotate_container"
                        )
                        val freeRotateContentColor by animateColorAsState(
                            targetValue = if (allowGestureRotation) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                            label = "toggle_free_rotate_content"
                        )

                        ToggleButton(
                            checked = allowGestureRotation,
                            onCheckedChange = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                allowGestureRotation = it
                            },
                            shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                            colors = ToggleButtonDefaults.toggleButtonColors(
                                containerColor = freeRotateContainerColor,
                                contentColor = freeRotateContentColor,
                                checkedContainerColor = freeRotateContainerColor,
                                checkedContentColor = freeRotateContentColor
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1.35f)
                                .height(46.dp)
                        ) {
                            Icon(
                                Icons.Rounded.CropRotate,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.upload_crop_free_rotate),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        ToggleButton(
                            checked = false,
                            onCheckedChange = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                                rotation = 0f
                            },
                            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                            colors = ToggleButtonDefaults.toggleButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                checkedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                checkedContentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(0.75f)
                                .height(46.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = stringResource(R.string.upload_crop_reset),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { scale = (scale - 0.15f).coerceAtLeast(1f) },
                                shapes = IconButtonDefaults.shapes()
                            ) {
                                Icon(
                                    Icons.Rounded.Remove,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Slider(
                                value = scale,
                                onValueChange = { scale = it },
                                valueRange = 1f..5f,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            )

                            IconButton(
                                onClick = { scale = (scale + 0.15f).coerceAtMost(5f) },
                                shapes = IconButtonDefaults.shapes()
                            ) {
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text(
                                stringResource(R.string.btn_cancel),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        Button(
                            onClick = {
                                if (!isSaving && bitmap != null) {
                                    isSaving = true
                                    scope.launch(Dispatchers.Default) {
                                        val size = containerSize
                                        val cropMarginPx = with(density) { 16.dp.toPx() }
                                        val cropSize = min(size.width, size.height) - cropMarginPx * 2f

                                        val cropRect = RectF(
                                            (size.width - cropSize) / 2f,
                                            (size.height - cropSize) / 2f,
                                            (size.width + cropSize) / 2f,
                                            (size.height + cropSize) / 2f
                                        )

                                        val state = ImageState(scale, offsetX, offsetY, rotation)

                                        val result = BitmapUtils.cropBitmap(
                                            source = bitmap,
                                            cropRect = cropRect,
                                            imageState = state,
                                            viewWidth = size.width,
                                            viewHeight = size.height,
                                            targetWidth = 1400,
                                            targetHeight = 1400
                                        )

                                        withContext(Dispatchers.Main) {
                                            onSave(result)
                                        }
                                    }
                                }
                            },
                            enabled = !isSaving,
                            shapes = ButtonDefaults.shapes(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.5.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(
                                    stringResource(R.string.btn_save),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
