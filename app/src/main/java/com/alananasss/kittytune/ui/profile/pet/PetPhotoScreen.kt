package com.alananasss.kittytune.ui.profile.pet

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.theme.GoogleSansRounded
import com.alananasss.kittytune.ui.widget.pet.PetActivity
import com.alananasss.kittytune.ui.widget.pet.PetCoat
import com.alananasss.kittytune.ui.widget.pet.PetColor
import com.alananasss.kittytune.ui.widget.pet.PetLandmarks
import com.alananasss.kittytune.ui.widget.pet.PetPattern
import com.alananasss.kittytune.ui.widget.pet.PetPhotoAnalyzer
import com.alananasss.kittytune.ui.widget.pet.PetPhotoResult
import com.alananasss.kittytune.ui.widget.pet.PetSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private sealed interface PhotoStep {
    data object Pick : PhotoStep
    data class Crop(val bitmap: Bitmap) : PhotoStep
    data class Markers(val crop: Bitmap) : PhotoStep
    data class Analyzing(val crop: Bitmap) : PhotoStep
    data class Result(val crop: Bitmap, val result: PetPhotoResult) : PhotoStep
    data class Edit(val crop: Bitmap, val result: PetPhotoResult) : PhotoStep
}

/** Experimental: turn a photo of your cat into its pixel look (coat colours, pattern, eyes). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PetPhotoScreen(
    settings: PetSettings,
    onApply: (PetSettings) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf<PhotoStep>(PhotoStep.Pick) }
    var error by remember { mutableStateOf(false) }

    fun load(uri: Uri?) {
        if (uri == null) return
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { runCatching { decodeOriented(context, uri) }.getOrNull() }
            if (bmp != null) { error = false; step = PhotoStep.Crop(bmp) } else error = true
        }
    }

    val cameraFile = remember { File(context.cacheDir, "pet_photo.jpg") }
    val cameraUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.provider", cameraFile) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) load(cameraUri) }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) takePicture.launch(cameraUri)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> load(uri) }

    fun openCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            takePicture.launch(cameraUri)
        } else askCamera.launch(Manifest.permission.CAMERA)
    }

    BackHandler {
        step = when (val s = step) {
            is PhotoStep.Crop, is PhotoStep.Markers, is PhotoStep.Result, is PhotoStep.Analyzing -> PhotoStep.Pick
            is PhotoStep.Edit -> PhotoStep.Result(s.crop, s.result)
            PhotoStep.Pick -> { onClose(); PhotoStep.Pick }
        }
    }

    // the pixel editor has its own top bar: show it full screen
    (step as? PhotoStep.Edit)?.let { s ->
        val look = settings.copy(colorId = PetColor.CUSTOM, customPalette = s.result.palette)
        PetCoatEditor(
            settings = look,
            initial = s.result.coat ?: PetCoat.fromPreset(s.result.looksLike),
            onSave = { coat -> step = PhotoStep.Result(s.crop, s.result.copy(coat = coat, pattern = PetPattern.CUSTOM)) },
            onClose = { step = PhotoStep.Result(s.crop, s.result) }
        )
        return
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                IconButton(onClick = {
                    if (step is PhotoStep.Pick) onClose() else step = PhotoStep.Pick
                }, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                }
                Text(
                    stringResource(R.string.pet_photo_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = GoogleSansRounded),
                    modifier = Modifier.weight(1f)
                )
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text(
                        stringResource(R.string.pet_experimental),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
            }

            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                contentKey = { it::class },
                label = "photoStep",
                modifier = Modifier.weight(1f)
            ) { s ->
                when (s) {
                    PhotoStep.Pick -> PickStep(settings, error, onCamera = ::openCamera, onGallery = {
                        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    })

                    is PhotoStep.Crop -> CropStep(s.bitmap, onRetake = { step = PhotoStep.Pick }) { crop ->
                        step = PhotoStep.Markers(crop)
                    }

                    is PhotoStep.Markers -> MarkersStep(s.crop, onBack = { step = PhotoStep.Pick }) { marks ->
                        step = PhotoStep.Analyzing(s.crop)
                        scope.launch {
                            val started = System.currentTimeMillis()
                            val result = withContext(Dispatchers.Default) { PetPhotoAnalyzer.analyze(s.crop, marks) }
                            delay((1_800 - (System.currentTimeMillis() - started)).coerceAtLeast(0))
                            step = PhotoStep.Result(s.crop, result)
                        }
                    }

                    is PhotoStep.Edit -> Unit

                    is PhotoStep.Analyzing -> AnalyzingStep(s.crop, settings)

                    is PhotoStep.Result -> ResultStep(
                        crop = s.crop,
                        result = s.result,
                        settings = settings,
                        onRetry = { step = PhotoStep.Pick },
                        onEdit = { step = PhotoStep.Edit(s.crop, s.result) },
                        onApply = { onApply(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PickStep(settings: PetSettings, error: Boolean, onCamera: () -> Unit, onGallery: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PetSprite(PetActivity.WAVE, settings, Modifier.widthIn(max = 380.dp).fillMaxWidth())
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.pet_photo_headline),
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = GoogleSansRounded),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.pet_photo_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.widthIn(max = 420.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(R.string.pet_photo_tip_face, R.string.pet_photo_tip_light, R.string.pet_photo_tip_filter).forEach {
                    Text("•  " + stringResource(it), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (error) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.pet_photo_error), color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onCamera, shapes = ButtonDefaults.shapes(), modifier = Modifier.widthIn(min = 240.dp)) {
            Icon(Icons.Rounded.PhotoCamera, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.pet_photo_camera))
        }
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = onGallery, shapes = ButtonDefaults.shapes(), modifier = Modifier.widthIn(min = 240.dp)) {
            Icon(Icons.Rounded.PhotoLibrary, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.pet_photo_gallery))
        }
    }
}

/** Pan / pinch the photo under a round guide; the visible square is what gets analysed. */
@Composable
private fun CropStep(bitmap: Bitmap, onRetake: () -> Unit, onDone: (Bitmap) -> Unit) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxPx by remember { mutableFloatStateOf(1f) }

    fun baseScale() = max(boxPx / bitmap.width, boxPx / bitmap.height)
    fun clamp(o: Offset, z: Float): Offset {
        val s = baseScale() * z
        val maxX = max(0f, (bitmap.width * s - boxPx) / 2)
        val maxY = max(0f, (bitmap.height * s - boxPx) / 2)
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.pet_photo_crop_hint),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        BoxWithConstraints(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.Black)
        ) {
            val density = LocalDensity.current
            boxPx = with(density) { maxWidth.toPx() }
            val primary = MaterialTheme.colorScheme.primary
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(bitmap) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            zoom = (zoom * gestureZoom).coerceIn(1f, 6f)
                            offset = clamp(offset + pan, zoom)
                        }
                    }
            ) {
                val s = baseScale() * zoom
                val w = bitmap.width * s
                val h = bitmap.height * s
                val left = size.width / 2 - w / 2 + offset.x
                val top = size.height / 2 - h / 2 + offset.y
                drawImage(image, dstOffset = IntOffset(left.roundToInt(), top.roundToInt()), dstSize = IntSize(w.roundToInt(), h.roundToInt()))
                val circle = Path().apply {
                    addOval(androidx.compose.ui.geometry.Rect(Offset(size.width * 0.06f, size.height * 0.06f), Size(size.width * 0.88f, size.height * 0.88f)))
                }
                clipPath(circle, clipOp = ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.45f)) }
                drawPath(circle, primary, style = Stroke(width = 3.dp.toPx()))
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRetake, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Rounded.Refresh, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.pet_photo_retake))
            }
            Button(onClick = {
                val s = baseScale() * zoom
                val left = boxPx / 2 - bitmap.width * s / 2 + offset.x
                val top = boxPx / 2 - bitmap.height * s / 2 + offset.y
                val x = ((0 - left) / s).roundToInt().coerceIn(0, bitmap.width - 1)
                val y = ((0 - top) / s).roundToInt().coerceIn(0, bitmap.height - 1)
                val side = (boxPx / s).roundToInt().coerceAtMost(min(bitmap.width - x, bitmap.height - y)).coerceAtLeast(8)
                onDone(Bitmap.createBitmap(bitmap, x, y, side, side))
            }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.pet_photo_next))
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null)
            }
        }
    }
}

/** Drag the two eye rings and the nose dot onto the cat: the sprite head is laid over them. */
@Composable
private fun MarkersStep(crop: Bitmap, onBack: () -> Unit, onDone: (PetLandmarks) -> Unit) {
    val image = remember(crop) { crop.asImageBitmap() }
    val d = PetLandmarks.DEFAULT
    val points = remember { mutableStateListOf(Offset(d.leftEyeX, d.leftEyeY), Offset(d.rightEyeX, d.rightEyeY), Offset(d.noseX, d.noseY)) }
    var dragging by remember { mutableIntStateOf(-1) }
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.pet_photo_markers_hint),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        val eyeColor = Color(0xFF7CC45A)
        val noseColor = Color(0xFFFF6F9C)
        Box(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(28.dp))
        ) {
            Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { o ->
                                val rel = Offset(o.x / size.width, o.y / size.height)
                                dragging = points.indices.minBy { (points[it] - rel).getDistance() }
                            },
                            onDragEnd = { dragging = -1 },
                            onDrag = { change, amount ->
                                if (dragging >= 0) {
                                    val p = points[dragging] + Offset(amount.x / size.width, amount.y / size.height)
                                    points[dragging] = Offset(p.x.coerceIn(0f, 1f), p.y.coerceIn(0f, 1f))
                                }
                                change.consume()
                            }
                        )
                    }
            ) {
                val r = size.width * 0.06f
                points.forEachIndexed { i, p ->
                    val c = Offset(p.x * size.width, p.y * size.height)
                    val color = if (i < 2) eyeColor else noseColor
                    val radius = if (i < 2) r else r * 0.6f
                    drawCircle(Color.Black.copy(alpha = 0.35f), radius = radius + 3f, center = c, style = Stroke(width = 9f))
                    drawCircle(color, radius = radius, center = c, style = Stroke(width = 5f))
                    drawCircle(color, radius = 5f, center = c)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendDot(eyeColor, stringResource(R.string.pet_photo_marker_eyes))
            LegendDot(noseColor, stringResource(R.string.pet_photo_marker_nose))
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Rounded.Refresh, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.pet_photo_retake))
            }
            Button(onClick = {
                val (l, r) = if (points[0].x <= points[1].x) points[0] to points[1] else points[1] to points[0]
                onDone(PetLandmarks(l.x, l.y, r.x, r.y, points[2].x, points[2].y))
            }, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Rounded.AutoAwesome, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.pet_photo_make))
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(14.dp)
                .border(3.dp, color, CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AnalyzingStep(crop: Bitmap, settings: PetSettings) {
    val image = remember(crop) { crop.asImageBitmap() }
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            LoadingIndicator(Modifier.size(200.dp))
            Image(
                image, null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.pet_photo_analyzing, settings.name),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ResultStep(
    crop: Bitmap,
    result: PetPhotoResult,
    settings: PetSettings,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onApply: (PetSettings) -> Unit,
) {
    val image = remember(crop) { crop.asImageBitmap() }
    val look = settings.copy(
        colorId = PetColor.CUSTOM,
        customPalette = result.palette,
        pattern = result.pattern,
        coat = result.coat ?: settings.coat,
        eyeColor = result.eyeColor ?: settings.eyeColor,
    )
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(max = 460.dp)) {
            Image(
                image, null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
            )
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.padding(horizontal = 8.dp))
            PetSprite(PetActivity.LOVE, look, Modifier.weight(1f), corner = 20)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.pet_photo_result),
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = GoogleSansRounded),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetectedRow(stringResource(R.string.pet_settings_pattern), stringResource(result.looksLike.label))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.pet_photo_colors), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    listOf(result.palette.fur, result.palette.shade, result.palette.mark, result.palette.white).forEach {
                        Box(
                            Modifier
                                .padding(start = 6.dp)
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(it))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.pet_settings_eyes), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (result.eyeColor != null) {
                        Box(
                            Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(result.eyeColor))
                        )
                    } else {
                        Text(stringResource(R.string.pet_photo_eyes_unknown), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.pet_photo_tweak),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = onEdit, shapes = ButtonDefaults.shapes()) {
            Icon(Icons.Rounded.Brush, null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.pet_photo_edit))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Rounded.Refresh, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.pet_photo_retry))
            }
            Button(onClick = { onApply(look) }, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Rounded.Check, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.pet_photo_apply))
            }
        }
    }
}

@Composable
private fun DetectedRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
    }
}

/** Decodes [uri] at a sensible size (≤ 2048 px) with its EXIF rotation applied. */
private fun decodeOriented(context: Context, uri: Uri): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2048) sample *= 2
    val bmp = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    val rotation = runCatching {
        resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
    }.getOrDefault(0f)
    if (rotation == 0f) return bmp
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true)
}
