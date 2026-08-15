    package com.alananasss.kittytune.ui.profile

    import android.graphics.Bitmap
    import android.graphics.RectF
    import androidx.compose.foundation.Canvas
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
    import androidx.compose.ui.graphics.ClipOp
    import androidx.compose.ui.graphics.Color
    import androidx.compose.ui.graphics.Path
    import androidx.compose.ui.graphics.PathOperation
    import androidx.compose.ui.graphics.asImageBitmap
    import androidx.compose.ui.graphics.drawscope.clipPath
    import androidx.compose.ui.graphics.drawscope.translate
    import androidx.compose.ui.graphics.drawscope.withTransform
    import androidx.compose.ui.input.pointer.pointerInput
    import androidx.compose.ui.layout.onGloballyPositioned
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
    fun AvatarCropDialog(
        bitmap: Bitmap?,
        username: String,
        onDismiss: () -> Unit,
        onSave: (Bitmap) -> Unit
    ) {
        // zoom and pan state
        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }

        // canvas container size
        var containerSize by remember { mutableStateOf(Size.Zero) }

        val scope = rememberCoroutineScope()
        var isSaving by remember { mutableStateOf(false) }

        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            // global dim background
            Box(
                modifier = Modifier
                    .fillMaxSize()
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
                        // header
                        Text(
                            text = username,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.profile_avatar_upload_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(24.dp))

                        // crop area (canvas)
                        // boxwithconstraints ensures the canvas has a finite size
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f) // square aspect ratio
                                .clip(RoundedCornerShape(12.dp))
                                .onGloballyPositioned { coordinates ->
                                    containerSize = coordinates.size.toSize()
                                }
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        if (bitmap != null && containerSize.width > 0 && containerSize.height > 0) {
                                            // update scale with limits
                                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                                            scale = newScale

                                            // logic to calculate image boundaries
                                            val imageWidth = bitmap.width.toFloat()
                                            val imageHeight = bitmap.height.toFloat()
                                            val canvasWidth = containerSize.width
                                            val canvasHeight = containerSize.height

                                            val srcRatio = imageWidth / imageHeight
                                            val dstRatio = canvasWidth / canvasHeight

                                            // calculate the base dimensions of the image as displayed (before zoom)
                                            val baseWidth: Float
                                            val baseHeight: Float

                                            if (srcRatio > dstRatio) {
                                                baseWidth = canvasWidth
                                                baseHeight = canvasWidth / srcRatio
                                            } else {
                                                baseHeight = canvasHeight
                                                baseWidth = canvasHeight * srcRatio
                                            }

                                            // calculate the dimensions with the current zoom applied
                                            val scaledWidth = baseWidth * scale
                                            val scaledHeight = baseHeight * scale

                                            // calculate the maximum allowed offset
                                            // this allows moving the image only as far as its edge hits the container edge
                                            // if the image is smaller than container, max offset is 0 (locks center)
                                            val maxOffsetX = (scaledWidth - canvasWidth) / 2f
                                            val maxOffsetY = (scaledHeight - canvasHeight) / 2f

                                            // enforce limits using coerceIn.
                                            // max(0f, ...) ensures we don't get negative limits if image is smaller than box
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
                                    val circleRadius = min(canvasWidth, canvasHeight) / 2f - 20f // inner margin

                                    withTransform({
                                        // canvas center
                                        val px = canvasWidth / 2f
                                        val py = canvasHeight / 2f

                                        // apply user transformations
                                        translate(left = offsetX, top = offsetY)
                                        scale(scale, scale, pivot = Offset(px, py))
                                    }) {
                                        // calculate "fit" scaling to center image initially
                                        val imageWidth = imageBitmap.width.toFloat()
                                        val imageHeight = imageBitmap.height.toFloat()

                                        val srcRatio = imageWidth / imageHeight
                                        val dstRatio = canvasWidth / canvasHeight

                                        val drawWidth: Float
                                        val drawHeight: Float

                                        if (srcRatio > dstRatio) {
                                            drawWidth = canvasWidth
                                            drawHeight = canvasWidth / srcRatio
                                        } else {
                                            drawHeight = canvasHeight
                                            drawWidth = canvasHeight * srcRatio
                                        }

                                        // center the image
                                        val left = (canvasWidth - drawWidth) / 2f
                                        val top = (canvasHeight - drawHeight) / 2f

                                        drawImage(
                                            image = imageBitmap,
                                            dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
                                            dstSize = androidx.compose.ui.unit.IntSize(drawWidth.toInt(), drawHeight.toInt())
                                        )
                                    }

                                    // create a path: rectangle minus the central circle
                                    val overlayPath = Path().apply {
                                        // full rectangle
                                        addRect(Rect(0f, 0f, canvasWidth, canvasHeight))
                                    }

                                    val circlePath = Path().apply {
                                        addOval(
                                            Rect(
                                                center = center,
                                                radius = circleRadius
                                            )
                                        )
                                    }

                                    // boolean op: overlay - circle
                                    val finalPath = Path.combine(
                                        operation = PathOperation.Difference,
                                        path1 = overlayPath,
                                        path2 = circlePath
                                    )

                                    // draw semi-transparent scrim
                                    drawPath(
                                        path = finalPath,
                                        color = Color.Black.copy(alpha = 0.6f)
                                    )

                                    // draw white circle outline
                                    drawPath(
                                        path = circlePath,
                                        color = Color.White,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        // zoom slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { scale = (scale - 0.1f).coerceAtLeast(1f) }) {
                                Icon(Icons.Rounded.Remove, null, tint = MaterialTheme.colorScheme.primary)
                            }

                            Slider(
                                value = scale,
                                onValueChange = {
                                    // update scale, but we reset offsets to 0 if zooming out prevents bounds issues
                                    // simpler approach: just update scale, let pan logic handle clamps next touch
                                    scale = it
                                    // optionally clamp offset immediately to avoid visual glitch if zooming out fast
                                    // but for smooth slider ux, letting it stay is usually fine or requires complex recalc here
                                },
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

                        Spacer(Modifier.height(24.dp))

                        // action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = onDismiss,
                                shapes = ButtonDefaults.shapes(),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
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
                                            // replicate display logic for final crop
                                            val radius = min(size.width, size.height) / 2f - 20f

                                            val cropRect = RectF(
                                                size.width / 2f - radius,
                                                size.height / 2f - radius,
                                                size.width / 2f + radius,
                                                size.height / 2f + radius
                                            )

                                            val state = ImageState(scale, offsetX, offsetY)

                                            val result = BitmapUtils.cropBitmap(
                                                source = bitmap,
                                                cropRect = cropRect,
                                                imageState = state,
                                                viewWidth = size.width,
                                                viewHeight = size.height,
                                                targetWidth = 2048,
                                                targetHeight = 2048
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
                                )
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

