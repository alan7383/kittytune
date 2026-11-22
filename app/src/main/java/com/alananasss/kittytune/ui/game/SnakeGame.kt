@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.alananasss.kittytune.ui.game

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.SentimentDissatisfied
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.hapticAction
import com.alananasss.kittytune.R
import com.alananasss.kittytune.audio.haptics.HapticUtil
import com.alananasss.kittytune.data.BlockManager
import com.alananasss.kittytune.data.wave.MyWave
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.player.PlaybackContext
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.widget.pet.PetConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * "Eat this playlist" — the KittyTune snake (inspired by Spotify's game of the same name).
 * Every cover the snake swallows starts playing: the playlist becomes the game, the game
 * becomes the playlist. Also runs as a MyWave mode ([SnakeGameMode.WAVE]) where the menu is
 * fresh finds the listener has never heard, so hunting for new music plays like a game.
 */
enum class SnakeGameMode { PLAYLIST, WAVE }

data class SnakeGameRequest(
    val title: String,
    val mode: SnakeGameMode,
    val tracks: List<Track> = emptyList(),
)

/** Shared launch point (like LikeAllPrompt): ask anywhere, one host shows the game. */
object SnakeGameLauncher {
    const val PREFS = "snake_game"
    const val KEY_BEST = "best_score"

    var pending by mutableStateOf<SnakeGameRequest?>(null)
        private set

    fun launch(request: SnakeGameRequest) {
        if (request.mode == SnakeGameMode.PLAYLIST && request.tracks.isEmpty()) return
        pending = request
    }

    fun dismiss() {
        pending = null
    }

    fun bestScore(context: Context): Int =
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_BEST, 0)
        }.getOrDefault(0)

    fun saveBestScore(context: Context, score: Int) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_BEST, score).apply()
        }
    }
}

// ─── Game model ─────────────────────────────────────────────────────────────────────────

enum class SnakePhase { IDLE, RUNNING, PAUSED, OVER, WIN }

enum class SnakeDirection { UP, DOWN, LEFT, RIGHT }

data class SnakeCell(val x: Int, val y: Int)

data class SnakeFood(val track: Track, val cell: SnakeCell)

/**
 * The snake itself. Pure state, no UI: the screen ticks it and reads the state back.
 * [onEat] fires once per swallowed track, on the tick that swallowed it.
 */
private class SnakeEngine(
    val cols: Int,
    val rows: Int,
    tracks: List<Track>,
    val onEat: (Track) -> Unit,
) {
    var phase by mutableStateOf(SnakePhase.IDLE)
    var snake by mutableStateOf(listOf<SnakeCell>())
    var direction by mutableStateOf(SnakeDirection.RIGHT)
    var foods by mutableStateOf(listOf<SnakeFood>())
    var score by mutableStateOf(0)
    var speedMs by mutableStateOf(START_SPEED_MS)
    var lastEaten by mutableStateOf<Track?>(null)

    private val turns = ArrayDeque<SnakeDirection>()
    // The menu is eaten in list order: the track that is already playing is not on the board,
    // so the first bite is the NEXT track of the list, then the one after it, and so on.
    private val pool = tracks
    private var poolIndex = 0

    init {
        placeSnake()
        spawnInitialFoods()
    }

    val trackCount: Int get() = pool.size

    private fun placeSnake() {
        val startY = rows / 2
        val startX = cols / 2
        snake = listOf(
            SnakeCell(startX + 1, startY),
            SnakeCell(startX, startY),
            SnakeCell(startX - 1, startY),
        )
    }

    private fun spawnInitialFoods() {
        foods = emptyList()
        repeat(min(FOODS_ON_BOARD, pool.size)) { spawnFood()?.let { foods = foods + it } }
    }

    /** A fresh run on the same menu: the board is restocked in list order, the snake goes home. */
    fun reset() {
        turns.clear()
        poolIndex = 0
        direction = SnakeDirection.RIGHT
        score = 0
        speedMs = START_SPEED_MS
        lastEaten = null
        placeSnake()
        spawnInitialFoods()
        phase = SnakePhase.IDLE
    }

    /** Any input or tap starts (or resumes) the run. */
    fun ensureRunning() {
        if (phase == SnakePhase.IDLE || phase == SnakePhase.PAUSED) phase = SnakePhase.RUNNING
    }

    fun pause() {
        if (phase == SnakePhase.RUNNING) phase = SnakePhase.PAUSED
    }

    /** Queues a turn; a direct reverse and a no-op are dropped so the snake cannot bite itself on the spot. */
    fun turn(dir: SnakeDirection) {
        val last = turns.lastOrNull() ?: direction
        if (dir == last || dir == opposite(last)) return
        if (turns.size < 2) turns.add(dir)
    }

    fun step() {
        if (phase != SnakePhase.RUNNING) return
        if (turns.isNotEmpty()) direction = turns.removeFirst()

        val head = snake.first()
        val newHead = when (direction) {
            SnakeDirection.UP -> SnakeCell(head.x, head.y - 1)
            SnakeDirection.DOWN -> SnakeCell(head.x, head.y + 1)
            SnakeDirection.LEFT -> SnakeCell(head.x - 1, head.y)
            SnakeDirection.RIGHT -> SnakeCell(head.x + 1, head.y)
        }

        if (newHead.x !in 0 until cols || newHead.y !in 0 until rows) {
            phase = SnakePhase.OVER
            return
        }

        val food = foods.firstOrNull { it.cell == newHead }
        if (food == null) {
            val body = snake.dropLast(1) // the tail cell is vacated this tick, it is safe to enter
            if (body.any { it == newHead }) {
                phase = SnakePhase.OVER
                return
            }
            snake = listOf(newHead) + body
        } else {
            if (snake.any { it == newHead }) {
                phase = SnakePhase.OVER
                return
            }
            snake = listOf(newHead) + snake
            foods = foods - food
            score++
            speedMs = (speedMs - SPEED_STEP_MS).coerceAtLeast(MIN_SPEED_MS)
            lastEaten = food.track
            onEat(food.track)
            spawnFood()?.let { foods = foods + it }
            if (foods.isEmpty()) phase = SnakePhase.WIN
        }
    }

    private fun spawnFood(): SnakeFood? {
        if (poolIndex >= pool.size) return null
        val occupied = snake.toHashSet() + foods.map { it.cell }
        val free = mutableListOf<SnakeCell>()
        for (x in 0 until cols) for (y in 0 until rows) {
            val cell = SnakeCell(x, y)
            if (cell !in occupied) free.add(cell)
        }
        if (free.isEmpty()) return null
        val track = pool[poolIndex++]
        return SnakeFood(track, free.random())
    }

    private fun opposite(dir: SnakeDirection): SnakeDirection = when (dir) {
        SnakeDirection.UP -> SnakeDirection.DOWN
        SnakeDirection.DOWN -> SnakeDirection.UP
        SnakeDirection.LEFT -> SnakeDirection.RIGHT
        SnakeDirection.RIGHT -> SnakeDirection.LEFT
    }

    private companion object {
        const val FOODS_ON_BOARD = 1
        /** Very slow at first, ramps up with every swallowed track. */
        const val START_SPEED_MS = 460f
        const val SPEED_STEP_MS = 8f
        const val MIN_SPEED_MS = 110f
    }
}

// ─── Host ───────────────────────────────────────────────────────────────────────────────

/** Put once near the top of the window; shows the game whenever [SnakeGameLauncher.launch] has been called. */
@Composable
fun SnakeGameHost(playerViewModel: PlayerViewModel) {
    val request = SnakeGameLauncher.pending ?: return
    Dialog(
        onDismissRequest = { SnakeGameLauncher.dismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        SnakeGameScreen(
            request = request,
            playerViewModel = playerViewModel,
            onClose = { SnakeGameLauncher.dismiss() },
        )
    }
}

// ─── Screen ─────────────────────────────────────────────────────────────────────────────

private const val COLS = 15
private const val ROWS = 21

/** Same navigation id the "My Wave" card uses, so wave playback keeps topping itself up. */
private const val MY_WAVE_NAV_ID = "my_wave"

@Composable
fun SnakeGameScreen(
    request: SnakeGameRequest,
    playerViewModel: PlayerViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val isWave = request.mode == SnakeGameMode.WAVE

    // The wave gathers its own menu: fresh finds, one batch per run.
    var waveReloadKey by remember { mutableStateOf(0) }
    var waveTracks by remember { mutableStateOf<List<Track>?>(null) }
    if (isWave) {
        LaunchedEffect(waveReloadKey) {
            waveTracks = runCatching {
                MyWave.nextBatch(MyWave.WaveMode.DISCOVER, exclude = emptySet())
            }.getOrDefault(emptyList())
        }
    }

    val menu = when (request.mode) {
        SnakeGameMode.PLAYLIST -> request.tracks
        SnakeGameMode.WAVE -> waveTracks
    }
    val sourceTracks = remember(menu) {
        menu?.let { BlockManager.filterBlocked(it).distinctBy(Track::id) }
    }

    var best by remember { mutableStateOf(SnakeGameLauncher.bestScore(context)) }

    Surface(modifier = Modifier.fillMaxSize(), color = scheme.surfaceContainerLow) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                GameBadge()
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(if (isWave) R.string.game_wave_title else R.string.game_eat_playlist),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        request.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(
                    onClick = hapticAction(HapticKind.UI, onClose),
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.btn_close))
                }
            }
            Spacer(Modifier.height(14.dp))

            when {
                isWave && waveTracks == null -> GameMessage(stringResource(R.string.game_loading), loading = true)
                sourceTracks.isNullOrEmpty() -> GameMessage(stringResource(R.string.game_empty))
                else -> SnakeGameBoardArea(
                    sourceTracks = sourceTracks,
                    request = request,
                    playerViewModel = playerViewModel,
                    isWave = isWave,
                    best = best,
                    onBest = {
                        if (it > best) {
                            best = it
                            SnakeGameLauncher.saveBestScore(context, it)
                        }
                    },
                    onRestartWave = {
                        waveReloadKey++
                        waveTracks = null
                    },
                    onClose = onClose,
                )
            }
        }
    }
}

@Composable
private fun SnakeGameBoardArea(
    sourceTracks: List<Track>,
    request: SnakeGameRequest,
    playerViewModel: PlayerViewModel,
    isWave: Boolean,
    best: Int,
    onBest: (Int) -> Unit,
    onRestartWave: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    // The snake wears the pet cat's coat: fur for the body, its eyes and markings for the face.
    val pet = remember { PetConfig.load(context) }

    val engine = remember(sourceTracks) {
        SnakeEngine(COLS, ROWS, sourceTracks.drop(1).ifEmpty { sourceTracks }) {
            HapticUtil.performUIHaptic(context)
        }
    }

    // Start and steering share the same entry point. Pausing the game keeps the selected song.
    val bannerTrack = if (engine.phase == SnakePhase.IDLE) null else engine.lastEaten ?: sourceTracks.first()
    var queueStarted by remember(engine) { mutableStateOf(false) }
    LaunchedEffect(engine, bannerTrack) {
        val track = bannerTrack ?: return@LaunchedEffect
        val startMs = TrackHighlights.startMs(context, track)
        ensureActive()
        if (!queueStarted || !playerViewModel.playTrackInQueue(track.id, startAtPositionMs = startMs)) {
            playerViewModel.playPlaylist(
                tracks = sourceTracks,
                startIndex = sourceTracks.indexOfFirst { it.id == track.id },
                context = if (isWave) PlaybackContext(request.title, navigationId = MY_WAVE_NAV_ID) else null,
                maintainPlayerState = true,
                respectShuffle = false,
                startAtPositionMs = startMs,
            )
            queueStarted = true
        }
    }

    val restart: () -> Unit = {
        if (isWave) onRestartWave() // the wave loads a fresh batch; the board re-enters on its own
        engine.reset()
    }
    val togglePause: () -> Unit = {
        when (engine.phase) {
            SnakePhase.RUNNING -> engine.pause()
            SnakePhase.IDLE, SnakePhase.PAUSED -> engine.ensureRunning()
            SnakePhase.OVER, SnakePhase.WIN -> restart()
        }
    }

    // The ticker: slower at first, frantic once the menu is half eaten.
    LaunchedEffect(engine, engine.phase, engine.speedMs) {
        while (engine.phase == SnakePhase.RUNNING) {
            delay(engine.speedMs.toLong())
            engine.step()
        }
    }

    // Records the best the moment a run ends.
    LaunchedEffect(engine.phase) {
        if (engine.phase == SnakePhase.OVER || engine.phase == SnakePhase.WIN) onBest(engine.score)
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScoreCards(engine.score, best, engine.trackCount)
        NowPlayingCard(bannerTrack) {
            onClose()
            playerViewModel.isPlayerExpanded = true
        }
        SnakeBoard(
            engine = engine,
            pet = pet,
            isWave = isWave,
            best = best,
            onStart = engine::ensureRunning,
            onRestart = restart,
            onClose = onClose,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        DirectionPad(
            phase = engine.phase,
            onTurn = { direction ->
                engine.turn(direction)
                engine.ensureRunning()
            },
            onTogglePause = togglePause,
        )
    }
}

/** The board itself: the grid, the covers to eat, the snake, and what to do when it is not running. */
@Composable
private fun SnakeBoard(
    engine: SnakeEngine,
    pet: com.alananasss.kittytune.ui.widget.pet.PetSettings,
    isWave: Boolean,
    best: Int,
    onStart: () -> Unit,
    onRestart: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    var dragAccum by remember { mutableStateOf(Offset.Zero) }
    val gridColor = scheme.outlineVariant.copy(alpha = 0.45f)
    val glowColor = scheme.tertiary

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val cell = min(constraints.maxWidth.toFloat() / COLS, constraints.maxHeight.toFloat() / ROWS)
        val cellDp = with(density) { cell.toDp() }
        val boardW = with(density) { (cell * COLS).toDp() }
        val boardH = with(density) { (cell * ROWS).toDp() }

        Box(
            modifier = Modifier
                .size(boardW, boardH)
                .clip(RoundedCornerShape(28.dp))
                .background(scheme.surfaceContainerLowest)
                .pointerInput(engine) {
                    detectDragGestures(
                        onDragStart = { dragAccum = Offset.Zero },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragAccum += dragAmount
                            val threshold = cell * 0.85f
                            if (abs(dragAccum.x) > threshold || abs(dragAccum.y) > threshold) {
                                val dir = if (abs(dragAccum.x) > abs(dragAccum.y)) {
                                    if (dragAccum.x > 0) SnakeDirection.RIGHT else SnakeDirection.LEFT
                                } else {
                                    if (dragAccum.y > 0) SnakeDirection.DOWN else SnakeDirection.UP
                                }
                                engine.turn(dir)
                                engine.ensureRunning()
                                dragAccum = Offset.Zero
                            }
                        },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawGrid(cell, gridColor)
                drawFoodsGlow(engine.foods, cell, glowColor)
                drawSnake(
                    engine.snake, cell,
                    fur = Color(pet.palette.fur),
                    shade = Color(pet.palette.shade),
                    mark = Color(pet.palette.mark),
                    eyeColor = Color(pet.eyeColor),
                )
            }

            engine.foods.forEach { food ->
                val appeared = remember(food.track.id, food.cell) { Animatable(0.4f) }
                LaunchedEffect(food.track.id, food.cell) {
                    appeared.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f))
                }
                AsyncImage(
                    model = trackImageModel(food.track),
                    contentDescription = food.track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .offset { IntOffset((food.cell.x * cell).roundToInt(), (food.cell.y * cell).roundToInt()) }
                        .size(cellDp)
                        .padding(2.5.dp)
                        .graphicsLayer {
                            scaleX = appeared.value
                            scaleY = appeared.value
                        }
                        .clip(RoundedCornerShape(8.dp))
                        .background(scheme.surfaceContainerHighest),
                )
            }

            val overlayAlpha by animateFloatAsState(
                targetValue = if (engine.phase == SnakePhase.RUNNING) 0f else 1f,
                label = "overlay",
            )
            if (overlayAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = overlayAlpha }
                        .background(scheme.scrim.copy(alpha = 0.45f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when (engine.phase) {
                        SnakePhase.IDLE -> GameOverlayCard(
                            title = stringResource(if (isWave) R.string.game_wave_idle else R.string.game_idle),
                            body = stringResource(R.string.game_eat_hint),
                            primary = { BigPlayButton(onStart) },
                        )
                        SnakePhase.PAUSED -> GameOverlayCard(
                            title = stringResource(R.string.game_paused),
                            primary = { BigPlayButton(onStart) },
                        )
                        SnakePhase.OVER -> GameOverlayCard(
                            title = stringResource(R.string.game_over),
                            body = stringResource(R.string.game_score, engine.score) + "  ·  " + stringResource(R.string.game_best, best),
                            icon = Icons.Rounded.SentimentDissatisfied,
                            primary = { RestartButtons(onRestart, onClose) },
                        )
                        SnakePhase.WIN -> GameOverlayCard(
                            title = stringResource(if (isWave) R.string.game_wave_finished else R.string.game_finished_title),
                            body = stringResource(R.string.game_finished_text, engine.score),
                            icon = Icons.Rounded.EmojiEvents,
                            primary = { RestartButtons(onRestart, onClose) },
                        )
                        SnakePhase.RUNNING -> Unit
                    }
                }
            }
        }
    }
}

// ─── Pieces ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GameBadge() {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(MaterialShapes.Cookie9Sided.toShape())
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.SportsEsports, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(26.dp))
    }
}

/** Songs eaten (the score), the best run, and how far through the menu this run is. */
@Composable
private fun ScoreCards(score: Int, best: Int, total: Int) {
    val scheme = MaterialTheme.colorScheme
    val scoreScale = remember { Animatable(1f) }
    LaunchedEffect(score) {
        if (score > 0) {
            scoreScale.snapTo(1.25f)
            scoreScale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 600f))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(22.dp), color = scheme.primaryContainer, modifier = Modifier.weight(1f)) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.MusicNote, null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(stringResource(R.string.game_score_label), style = MaterialTheme.typography.labelSmall, color = scheme.onPrimaryContainer)
                    Text(
                        if (total > 0) "$score / $total" else "$score",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onPrimaryContainer,
                        modifier = Modifier.graphicsLayer {
                            scaleX = scoreScale.value
                            scaleY = scoreScale.value
                        },
                    )
                }
            }
        }
        Surface(shape = RoundedCornerShape(22.dp), color = scheme.surfaceContainerHigh, modifier = Modifier.weight(1f)) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.EmojiEvents, null, tint = scheme.tertiary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(stringResource(R.string.game_best_label), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                    Text("$best", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** The song the last cover played; a tap opens the player on it. */
@Composable
private fun NowPlayingCard(track: Track?, onOpenPlayer: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    AnimatedVisibility(
        visible = track != null,
        enter = fadeIn() + scaleIn(initialScale = 0.92f),
        exit = fadeOut() + scaleOut(targetScale = 0.92f),
    ) {
        FilledTonalButton(
            onClick = hapticAction(HapticKind.UI, onOpenPlayer),
            shapes = ButtonDefaults.shapes(),
            contentPadding = PaddingValues(10.dp),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = scheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            AsyncImage(
                model = trackImageModel(track),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(scheme.surfaceContainerHighest),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.game_now_playing), style = MaterialTheme.typography.labelSmall, color = scheme.primary, fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(track?.title, track?.displayArtist?.takeIf { it.isNotBlank() }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
        }
    }
}

/** A cross of arrows around pause / play, under the thumb. Swiping on the board steers too. */
@Composable
private fun DirectionPad(phase: SnakePhase, onTurn: (SnakeDirection) -> Unit, onTogglePause: () -> Unit) {
    val running = phase == SnakePhase.RUNNING
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        PadButton(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.game_up)) { onTurn(SnakeDirection.UP) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PadButton(Icons.Rounded.KeyboardArrowLeft, stringResource(R.string.game_left)) { onTurn(SnakeDirection.LEFT) }
            FilledIconButton(
                onClick = hapticAction(HapticKind.MEDIUM, onTogglePause),
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(PAD_BUTTON_SIZE),
            ) {
                Icon(
                    if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (running) R.string.action_pause else R.string.btn_play),
                    modifier = Modifier.size(28.dp),
                )
            }
            PadButton(Icons.Rounded.KeyboardArrowRight, stringResource(R.string.game_right)) { onTurn(SnakeDirection.RIGHT) }
        }
        PadButton(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.game_down)) { onTurn(SnakeDirection.DOWN) }
    }
}

@Composable
private fun PadButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = hapticAction(HapticKind.LIGHT, onClick),
        shapes = IconButtonDefaults.shapes(),
        modifier = Modifier.size(PAD_BUTTON_SIZE),
    ) {
        Icon(icon, contentDescription = desc, modifier = Modifier.size(30.dp))
    }
}

private val PAD_BUTTON_SIZE = 60.dp

@Composable
private fun GameOverlayCard(
    title: String,
    body: String? = null,
    icon: ImageVector? = null,
    primary: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(32.dp), color = scheme.surfaceContainerHigh, modifier = Modifier.widthIn(max = 320.dp)) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (icon != null) {
                Box(
                    modifier = Modifier.size(64.dp).clip(MaterialShapes.SoftBurst.toShape()).background(scheme.tertiaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = scheme.onTertiaryContainer, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.height(14.dp))
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            if (body != null) {
                Spacer(Modifier.height(6.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(18.dp))
            primary()
        }
    }
}

@Composable
private fun BigPlayButton(onClick: () -> Unit) {
    FilledIconButton(
        onClick = hapticAction(HapticKind.STRONG, onClick),
        shapes = IconButtonDefaults.shapes(),
        modifier = Modifier.size(80.dp),
    ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.btn_play), modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun RestartButtons(onRestart: () -> Unit, onClose: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(onClick = hapticAction(HapticKind.MEDIUM, onRestart), shapes = ButtonDefaults.shapes(), modifier = Modifier.height(52.dp)) {
            Icon(Icons.Rounded.Refresh, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.game_restart))
        }
        TextButton(onClick = hapticAction(HapticKind.UI, onClose), shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.btn_close))
        }
    }
}

@Composable
private fun GameMessage(text: String, loading: Boolean = false) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(32.dp),
    ) {
        if (loading) {
            ContainedLoadingIndicator(Modifier.size(64.dp))
        } else {
            Box(
                modifier = Modifier.size(72.dp).clip(MaterialShapes.Cookie12Sided.toShape()).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.SportsEsports, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

// ─── Canvas drawing ─────────────────────────────────────────────────────────────────────


private fun DrawScope.drawGrid(cell: Float, color: Color) {
    for (x in 1 until COLS) {
        for (y in 1 until ROWS) {
            drawCircle(
                color = color,
                radius = cell * 0.045f,
                center = Offset(x * cell, y * cell),
            )
        }
    }
}

private fun DrawScope.drawFoodsGlow(foods: List<SnakeFood>, cell: Float, color: Color) {
    foods.forEach { food ->
        drawCircle(
            color = color.copy(alpha = 0.26f),
            radius = cell * 0.72f,
            center = Offset(
                food.cell.x * cell + cell / 2f,
                food.cell.y * cell + cell / 2f,
            ),
        )
    }
}

private fun DrawScope.drawSnake(
    snake: List<SnakeCell>,
    cell: Float,
    fur: Color,
    shade: Color,
    mark: Color,
    eyeColor: Color,
) {
    val ink = Color(0xFF101015)
    val inset = cell * 0.06f
    val size = cell - inset * 2
    val radius = CornerRadius(cell * 0.22f, cell * 0.22f)
    snake.forEachIndexed { index, segment ->
        val color = if (index == 0) {
            fur
        } else {
            fur.copy(alpha = (0.92f - index * 0.02f).coerceAtLeast(0.45f))
        }
        drawRoundRect(
            color = color,
            topLeft = Offset(segment.x * cell + inset, segment.y * cell + inset),
            size = Size(size, size),
            cornerRadius = radius,
        )
    }

    // The head is the pet cat's face: its fur, its eye colour, its markings for the nose.
    val head = snake.firstOrNull() ?: return
    val cx = head.x * cell + cell / 2f
    val cy = head.y * cell + cell / 2f
    val earSize = cell * 0.2f
    val leftEar = listOf(
        Offset(cx - cell * 0.26f, cy - cell * 0.12f),
        Offset(cx - cell * 0.3f, cy - cell * 0.12f - earSize),
        Offset(cx - cell * 0.06f, cy - cell * 0.26f),
    )
    val rightEar = listOf(
        Offset(cx + cell * 0.26f, cy - cell * 0.12f),
        Offset(cx + cell * 0.3f, cy - cell * 0.12f - earSize),
        Offset(cx + cell * 0.06f, cy - cell * 0.26f),
    )
    listOf(leftEar, rightEar).forEach { ear ->
        drawTriangle(ear[0], ear[1], ear[2], fur)
        // Inner ear tinted with the coat's shade colour.
        val c = centroid(ear)
        val inner = ear.map { p -> Offset(p.x * 0.45f + c.x * 0.55f, p.y * 0.45f + c.y * 0.55f) }
        drawTriangle(inner[0], inner[1], inner[2], shade)
    }
    // Eyes in the pet's eye colour, with a dark socket and a catchlight.
    val eyeRadius = cell * 0.07f
    listOf(
        Offset(cx - cell * 0.16f, cy + cell * 0.02f),
        Offset(cx + cell * 0.16f, cy + cell * 0.02f),
    ).forEach { center ->
        drawCircle(ink, eyeRadius * 1.45f, center)
        drawCircle(eyeColor, eyeRadius, center)
        drawCircle(
            Color.White.copy(alpha = 0.85f), eyeRadius * 0.32f,
            Offset(center.x - eyeRadius * 0.3f, center.y - eyeRadius * 0.35f),
        )
    }
    // Nose in the coat's marking colour.
    drawCircle(mark, eyeRadius * 0.62f, Offset(cx, cy + cell * 0.18f))
}

private fun centroid(points: List<Offset>): Offset = Offset(
    points.sumOf { it.x.toDouble() }.toFloat() / points.size,
    points.sumOf { it.y.toDouble() }.toFloat() / points.size,
)

private fun DrawScope.drawTriangle(a: Offset, b: Offset, c: Offset, color: Color) {
    val path = Path().apply {
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
        lineTo(c.x, c.y)
        close()
    }
    drawPath(path, color)
}

private fun trackImageModel(track: Track?): Any {
    if (track == null) return ""
    val art = track.thumbnailUrl
    return if (art.startsWith("/")) File(art) else art
}
