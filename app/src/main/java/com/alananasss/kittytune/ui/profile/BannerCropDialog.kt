    package com.alananasss.kittytune.ui.profile

    import android.graphics.Bitmap
    import android.graphics.RectF
    import androidx.compose.foundation.Canvas
    import androidx.compose.foundation.background
    import androidx.compose.foundation.gestures.detectTransformGestures
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.rounded.Add
    import androidx.compose.material.icons.rounded.Remove
    import androidx.compose.material3.*
    import androidx.compose.runtime.*
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.draw.clip
    import androidx.compose.ui.geometry.Offset
    import androidx.compose.ui.geometry.Rect
    import androidx.compose.ui.geometry.Size
    import androidx.compose.ui.graphics.Color
    import androidx.compose.ui.graphics.Path
    import androidx.compose.ui.graphics.PathOperation
    import androidx.compose.ui.graphics.asImageBitmap
    import androidx.compose.ui.graphics.drawscope.translate
    import androidx.compose.ui.graphics.drawscope.withTransform
    import androidx.compose.ui.input.pointer.pointerInput
    import androidx.compose.ui.layout.onGloballyPositioned
    import androidx.compose.ui.platform.LocalDensity
    import androidx.compose.ui.res.stringResource
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.unit.dp
    import androidx.compose.ui.unit.toSize
    import androidx.compose.ui.window.Dialog
    import androidx.compose.ui.window.DialogProperties
    import com.alananasss.kittytune.R
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.launch
    import kotlinx.coroutines.withContext
    import kotlin.math.max
    import kotlin.math.min

    @Composable
    fun BannerCropDialog(
        bitmap: Bitmap?,
        onDismiss: () -> Unit,
        onSave: (Bitmap) -> Unit
    ) {
        // states for zoom and pan
        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }

        // size of the display area
        var containerSize by remember { mutableStateOf(Size.Zero) }

        val scope = rememberCoroutineScope()
        var isSaving by remember { mutableStateOf(false) }
        val density = LocalDensity.current

        // soundcloud banner ratio: 2480 / 520 ≈ 4.77
        val targetRatio = 1240f / 260f

        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)) // Dimmed background
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer, // Monet Color
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = stringResource(R.string.profile_banner_upload_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.profile_banner_upload_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(24.dp))

                        // CROP AREA (CANVAS)
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest) // Monet background for empty space
                                .onGloballyPositioned { coordinates ->
                                    containerSize = coordinates.size.toSize()
                                }
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        if (bitmap != null && containerSize.width > 0 && containerSize.height > 0) {
                                            // Update scale
                                            scale = (scale * zoom).coerceIn(1f, 5f)

                                            // We duplicate the logic used in Canvas to know the strict limits
                                            val canvasW = containerSize.width
                                            val canvasH = containerSize.height
                                            val margin = 20f * density.density
                                            val maxRectWidth = canvasW - (margin * 2)
                                            val maxRectHeight = canvasH - (margin * 2)

                                            var cropW = maxRectWidth
                                            var cropH = maxRectWidth / targetRatio

                                            if (cropH > maxRectHeight) {
                                                cropH = maxRectHeight
                                                cropW = cropH * targetRatio
                                            }

                                            val imageWidth = bitmap.width.toFloat()
                                            val imageHeight = bitmap.height.toFloat()
                                            val srcRatio = imageWidth / imageHeight
                                            val dstRatio = canvasW / canvasH

                                            val baseWidth: Float
                                            val baseHeight: Float

                                            // Initial "fit" logic
                                            if (srcRatio > dstRatio) {
                                                baseWidth = canvasW
                                                baseHeight = canvasW / srcRatio
                                            } else {
                                                baseHeight = canvasH
                                                baseWidth = canvasH * srcRatio
                                            }

                                            val scaledWidth = baseWidth * scale
                                            val scaledHeight = baseHeight * scale

                                            // The image edge should not go inside the crop box edge
                                            // Limit = (ImageDimension - CropDimension) / 2
                                            // If image is smaller than crop box, limit is 0 (locked to center)
                                            val maxOffsetX = (scaledWidth - cropW) / 2f
                                            val maxOffsetY = (scaledHeight - cropH) / 2f

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

                                    // Calculate crop rectangle size
                                    val margin = 20.dp.toPx()
                                    val maxRectWidth = canvasWidth - (margin * 2)
                                    val maxRectHeight = canvasHeight - (margin * 2)

                                    var drawRectWidth = maxRectWidth
                                    var drawRectHeight = maxRectWidth / targetRatio

                                    if (drawRectHeight > maxRectHeight) {
                                        drawRectHeight = maxRectHeight
                                        drawRectWidth = drawRectHeight * targetRatio
                                    }

                                    val rectLeft = (canvasWidth - drawRectWidth) / 2f
                                    val rectTop = (canvasHeight - drawRectHeight) / 2f

                                    withTransform({
                                        val px = canvasWidth / 2f
                                        val py = canvasHeight / 2f
                                        translate(left = offsetX, top = offsetY)
                                        scale(scale, scale, pivot = Offset(px, py))
                                    }) {
                                        val imageWidth = imageBitmap.width.toFloat()
                                        val imageHeight = imageBitmap.height.toFloat()
                                        val srcRatio = imageWidth / imageHeight
                                        val dstRatio = canvasWidth / canvasHeight

                                        val imgDrawWidth: Float
                                        val imgDrawHeight: Float

                                        if (srcRatio > dstRatio) {
                                            imgDrawWidth = canvasWidth
                                            imgDrawHeight = canvasWidth / srcRatio
                                        } else {
                                            imgDrawHeight = canvasHeight
                                            imgDrawWidth = canvasHeight * srcRatio
                                        }

                                        val left = (canvasWidth - imgDrawWidth) / 2f
                                        val top = (canvasHeight - imgDrawHeight) / 2f

                                        drawImage(
                                            image = imageBitmap,
                                            dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
                                            dstSize = androidx.compose.ui.unit.IntSize(imgDrawWidth.toInt(), imgDrawHeight.toInt())
                                        )
                                    }

                                    val overlayPath = Path().apply {
                                        addRect(Rect(0f, 0f, canvasWidth, canvasHeight))
                                    }
                                    val cropPath = Path().apply {
                                        addRect(Rect(rectLeft, rectTop, rectLeft + drawRectWidth, rectTop + drawRectHeight))
                                    }

                                    val finalPath = Path.combine(
                                        operation = PathOperation.Difference,
                                        path1 = overlayPath,
                                        path2 = cropPath
                                    )

                                    // Dark scrim for outside area
                                    drawPath(path = finalPath, color = Color.Black.copy(alpha = 0.6f))
                                    // White frame for crop area
                                    drawPath(path = cropPath, color = Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        // Zoom Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { scale = (scale - 0.1f).coerceAtLeast(1f) }) {
                                Icon(Icons.Rounded.Remove, null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = scale,
                                onValueChange = { scale = it },
                                valueRange = 1f..5f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                            IconButton(onClick = { scale = (scale + 0.1f).coerceAtMost(5f) }) {
                                Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Buttons
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(
                                onClick = onDismiss,
                                shapes = ButtonDefaults.shapes(),
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text(stringResource(R.string.btn_cancel))
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (!isSaving && bitmap != null) {
                                        isSaving = true
                                        scope.launch(Dispatchers.Default) {
                                            val size = containerSize

                                            // Re-calculate the exact rect for cropping
                                            val canvasW = size.width
                                            val canvasH = size.height
                                            val margin = 20f * density.density
                                            val maxRectWidth = canvasW - (margin * 2)
                                            val maxRectHeight = canvasH - (margin * 2)

                                            var rectW = maxRectWidth
                                            var rectH = maxRectWidth / targetRatio

                                            if (rectH > maxRectHeight) {
                                                rectH = maxRectHeight
                                                rectW = rectH * targetRatio
                                            }

                                            val cx = canvasW / 2f
                                            val cy = canvasH / 2f

                                            val cropRect = RectF(
                                                cx - rectW / 2f,
                                                cy - rectH / 2f,
                                                cx + rectW / 2f,
                                                cy + rectH / 2f
                                            )

                                            val state = ImageState(scale, offsetX, offsetY)

                                            val result = BitmapUtils.cropBitmap(
                                                source = bitmap,
                                                cropRect = cropRect,
                                                imageState = state,
                                                viewWidth = size.width,
                                                viewHeight = size.height,
                                                targetWidth = 1240,
                                                targetHeight = 260
                                            )

                                            withContext(Dispatchers.Main) {
                                                onSave(result)
                                            }
                                        }
                                    }
                                },
                                shapes = ButtonDefaults.shapes(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                enabled = !isSaving
                            ) {
                                if (isSaving) {
                                    LoadingIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Text(stringResource(R.string.btn_save))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

