package com.alananasss.kittytune.ui.profile.recap

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.stats.ListeningRecap
import com.alananasss.kittytune.data.stats.ReportArtist
import com.alananasss.kittytune.data.stats.ReportTrack
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.hapticAction
import com.alananasss.kittytune.ui.common.rememberAppHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** How long a slide stays before the story moves on by itself. */
private const val SLIDE_MS = 7_000

/**
 * A recap as a story: full-screen slides that advance by themselves, with a segmented progress bar on
 * top. Tap the right of the screen for the next slide, the left for the previous one, hold to pause.
 * The last slide sums it up and can be shared as an image.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecapStoryScreen(
    recapId: String,
    onClose: () -> Unit,
    onTrackClick: (ReportTrack) -> Unit,
    onArtistClick: (ReportArtist) -> Unit,
) {
    val viewModel: RecapStoryViewModel = viewModel()
    LaunchedEffect(recapId) { viewModel.load(recapId) }
    val recap = viewModel.recap

    BackHandler(onBack = onClose)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        when {
            recap != null -> Story(recap, onClose, onTrackClick, onArtistClick)
            viewModel.isMissing -> Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.recap_missing), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onClose, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.btn_close)) }
            }
            else -> LoadingIndicator(Modifier.align(Alignment.Center).size(72.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Story(
    recap: ListeningRecap,
    onClose: () -> Unit,
    onTrackClick: (ReportTrack) -> Unit,
    onArtistClick: (ReportArtist) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val slides = remember(recap) { slidesFor(recap) }
    var index by rememberSaveable(recap.period.id) { mutableIntStateOf(0) }
    if (index > slides.lastIndex) index = slides.lastIndex
    var paused by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val haptics = rememberAppHaptics()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shareLayer = rememberGraphicsLayer()

    val slide = slides[index]
    val isLast = index == slides.lastIndex

    fun go(to: Int) {
        val target = to.coerceIn(0, slides.lastIndex)
        if (target != index) haptics.perform(HapticKind.LIGHT)
        index = target
    }

    // The clock of the current slide: it starts empty on each new slide, runs while not paused, and
    // moves on when it is full. The last slide stays — it is the one with something to do.
    var clockIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(index, paused) {
        if (clockIndex != index) {
            progress.snapTo(0f)
            clockIndex = index
        }
        if (paused) return@LaunchedEffect
        val remaining = ((1f - progress.value) * SLIDE_MS).toInt()
        progress.animateTo(1f, tween(remaining.coerceAtLeast(1), easing = LinearEasing))
        if (index < slides.lastIndex) index++
    }
    val shareTitle = stringResource(R.string.recap_share_text, recap.period.title())

    val palette = slide.palette(scheme, recap)
    val container by animateColorAsState(palette.container, tween(500), label = "slideContainer")
    val content by animateColorAsState(palette.onContainer, tween(500), label = "slideContent")
    val accent by animateColorAsState(palette.accent, tween(500), label = "slideAccent")

    Box(
        Modifier
            .fillMaxSize()
            .background(container)
            .pointerInput(slides.size) {
                detectTapGestures(
                    onPress = {
                        paused = true
                        tryAwaitRelease()
                        paused = false
                    },
                    onTap = { offset ->
                        if (offset.x < size.width * 0.3f) go(index - 1) else go(index + 1)
                    },
                )
            }
    ) {
        MorphingBackdrop(slide.polygon(recap), accent.copy(alpha = 0.18f))

        CompositionLocalProvider(LocalContentColor provides content) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                StoryTopBar(
                    recap = recap,
                    count = slides.size,
                    index = index,
                    progress = { progress.value },
                    paused = paused,
                    onTogglePause = { paused = !paused },
                    onClose = onClose,
                )
                AnimatedContent(
                    targetState = index,
                    transitionSpec = {
                        val forward = targetState > initialState
                        (slideInHorizontally(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)) { if (forward) it / 3 else -it / 3 } +
                            fadeIn(tween(260)) + scaleIn(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow), initialScale = 0.94f)) togetherWith
                            (slideOutHorizontally(tween(220)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(160)))
                    },
                    label = "recapSlide",
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { shown ->
                    // Scrollable for small screens and large fonts, centred when it fits.
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val viewport = maxHeight
                        Box(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .heightIn(min = viewport)
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                        Box(
                            Modifier
                                .widthIn(max = 560.dp)
                                .fillMaxWidth()
                                .then(
                                    if (slides[shown] == RecapSlide.SUMMARY) {
                                        Modifier.drawWithContent {
                                            shareLayer.record { this@drawWithContent.drawContent() }
                                            drawLayer(shareLayer)
                                        }
                                    } else Modifier
                                ),
                        ) {
                            SlideContent(
                                slide = slides[shown],
                                recap = recap,
                                accent = accent,
                                onTrackClick = onTrackClick,
                                onArtistClick = onArtistClick,
                            )
                        }
                        }
                    }
                }
                if (isLast) {
                    StoryActions(
                        onShare = {
                            scope.launch {
                                val bitmap = runCatching { shareLayer.toImageBitmap() }.getOrNull()
                                shareRecap(context, bitmap, recap, shareTitle)
                            }
                        },
                        onReplay = { go(0) },
                    )
                } else {
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/** The segmented progress bar, the recap's name, and pause and close. */
@Composable
private fun StoryTopBar(
    recap: ListeningRecap,
    count: Int,
    index: Int,
    progress: () -> Float,
    paused: Boolean,
    onTogglePause: () -> Unit,
    onClose: () -> Unit,
) {
    val content = LocalContentColor.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().height(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(count) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(content.copy(alpha = 0.25f))
                ) {
                    val fill = when {
                        i < index -> 1f
                        i == index -> progress()
                        else -> 0f
                    }
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fill)
                            .clip(CircleShape)
                            .background(content)
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(recap.period.polygon().toShape())
                    .background(content.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(recap.period.icon(), contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(recap.period.title(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(recap.period.kindLabel(), style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.75f), maxLines = 1)
            }
            val buttonColors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = content.copy(alpha = 0.14f),
                contentColor = content,
            )
            FilledTonalIconButton(
                onClick = hapticAction(HapticKind.UI) { onTogglePause() },
                shapes = IconButtonDefaults.shapes(),
                colors = buttonColors,
            ) {
                Icon(
                    if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    contentDescription = stringResource(if (paused) R.string.recap_resume else R.string.recap_pause),
                )
            }
            Spacer(Modifier.width(4.dp))
            FilledTonalIconButton(
                onClick = hapticAction(HapticKind.UI) { onClose() },
                shapes = IconButtonDefaults.shapes(),
                colors = buttonColors,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.btn_close))
            }
        }
    }
}

/** Share and replay, under the last slide. */
@Composable
private fun StoryActions(onShare: () -> Unit, onReplay: () -> Unit) {
    val content = LocalContentColor.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonDefaults.IconSpacing, Alignment.CenterHorizontally),
    ) {
        FilledTonalButton(
            onClick = hapticAction(HapticKind.UI) { onReplay() },
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = content.copy(alpha = 0.14f), contentColor = content),
            contentPadding = ButtonDefaults.MediumContentPadding,
            modifier = Modifier.height(ButtonDefaults.MediumContainerHeight),
        ) {
            Icon(Icons.Rounded.Replay, contentDescription = null)
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.recap_replay))
        }
        Button(
            onClick = hapticAction(HapticKind.CONFIRM) { onShare() },
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.buttonColors(containerColor = content, contentColor = MaterialTheme.colorScheme.surface),
            contentPadding = ButtonDefaults.MediumContentPadding,
            modifier = Modifier.height(ButtonDefaults.MediumContainerHeight).weight(1f, fill = false),
        ) {
            Icon(Icons.Rounded.Share, contentDescription = null)
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.recap_share))
        }
    }
}

/**
 * A big expressive shape drifting behind the slides, morphing into the next slide's shape as the story
 * moves on, and turning slowly all the while.
 */
@Composable
private fun MorphingBackdrop(target: RoundedPolygon, color: Color) {
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val morphProgress = remember { Animatable(1f) }
    LaunchedEffect(target) {
        if (target == to) return@LaunchedEffect
        from = to
        to = target
        morphProgress.snapTo(0f)
        morphProgress.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessVeryLow))
    }
    val morph = remember(from, to) { Morph(from, to) }
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            rotation.animateTo(rotation.value + 360f, tween(40_000, easing = LinearEasing))
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = maxWidth * 1.15f
        Box(
            Modifier
                .size(side)
                .offset(x = maxWidth - side * 0.62f, y = maxHeight * 0.18f)
                .graphicsLayer { rotationZ = rotation.value }
                .clip(MorphShape(morph, morphProgress.value))
                .background(color)
        )
    }
}

/** Writes the summary to the cache and offers it to whatever the user wants to share it with. */
private suspend fun shareRecap(context: Context, bitmap: ImageBitmap?, recap: ListeningRecap, title: String) {
    val uri = bitmap?.let {
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "recaps").apply { mkdirs() }
                val file = File(dir, "recap-${recap.period.id}.png")
                file.outputStream().use { out -> it.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out) }
                FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            }.getOrNull()
        }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        if (uri != null) {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            type = "text/plain"
        }
        putExtra(Intent.EXTRA_TEXT, title)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.recap_share)))
}
