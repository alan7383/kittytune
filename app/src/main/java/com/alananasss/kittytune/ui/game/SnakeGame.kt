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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import com.alananasss.kittytune.R
import com.alananasss.kittytune.audio.haptics.HapticUtil
import com.alananasss.kittytune.data.wave.MyWave
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.player.PlaybackContext
import com.alananasss.kittytune.ui.player.PlayerViewModel
import kotlinx.coroutines.delay
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
    var speedMs by mutableStateOf(230f)
    var lastEaten by mutableStateOf<Track?>(null)

    private val turns = ArrayDeque<SnakeDirection>()
    private var pool: List<Track> = tracks.distinctBy { it.id }.shuffled()
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

    /** A fresh run on the same menu: the tracks are shuffled again, the snake goes home. */
    fun reset() {
        turns.clear()
        pool = pool.shuffled()
        poolIndex = 0
        direction = SnakeDirection.RIGHT
        score = 0
        speedMs = 230f
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
            speedMs = (speedMs - 7f).coerceAtLeast(115f)
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
        const val FOODS_ON_BOARD = 3
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

    val sourceTracks: List<Track>? = when (request.mode) {
        SnakeGameMode.PLAYLIST -> request.tracks
        SnakeGameMode.WAVE -> waveTracks
    }

    var best by remember { mutableStateOf(SnakeGameLauncher.bestScore(context)) }

    Surface(modifier = Modifier.fillMaxSize(), color = GameColors.Background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            // Header: title, close
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Rounded.SportsEsports,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (isWave) R.string.game_wave_title else R.string.game_eat_playlist
                        ),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = request.title,
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { onClose() },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.08f),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.btn_close))
                }
            }

            Spacer(Modifier.height(10.dp))

            when {
                isWave && waveTracks == null -> GameMessage(
                    text = stringResource(R.string.game_loading),
                    icon = { GameSpinner() },
                )
                sourceTracks.isNullOrEmpty() -> GameMessage(
                    text = stringResource(R.string.game_empty),
                )
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
    val scheme = MaterialTheme.colorScheme

    // Eating a cover plays it. The first bite starts the whole list as the queue (in the wave
    // context, so the wave keeps topping itself up); the rest are jumps inside that queue.
    var queueStarted by remember(sourceTracks) { mutableStateOf(false) }
    val waveContext = PlaybackContext(
        displayText = request.title,
        navigationId = MY_WAVE_NAV_ID,
    )
    val onEat: (Track) -> Unit = remember(sourceTracks) { { track ->
        HapticUtil.performUIHaptic(context)
        if (!queueStarted) {
            val index = sourceTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            playerViewModel.playPlaylist(sourceTracks, index, if (isWave) waveContext else null)
            queueStarted = true
        } else if (!playerViewModel.playTrackInQueue(track.id)) {
            playerViewModel.playPlaylist(listOf(track), 0, if (isWave) waveContext else null)
        }
    } }

    val engine = remember(sourceTracks) {
        SnakeEngine(COLS, ROWS, sourceTracks, onEat)
    }

    val restart: () -> Unit = {
        if (isWave) onRestartWave() // the wave loads a fresh batch; the board re-enters on its own
        engine.reset()
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

    // Score pop.
    val scoreScale = remember { Animatable(1f) }
    LaunchedEffect(engine.score) {
        if (engine.score > 0) {
            scoreScale.snapTo(1.3f)
            scoreScale.animateTo(1f, tween(240))
        }
    }

    var dragAccum by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Score row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.08f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.game_score, engine.score),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.graphicsLayer {
                            scaleX = scoreScale.value
                            scaleY = scoreScale.value
                        },
                    )
                }
            }
            Surface(shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = 0.08f)) {
                Text(
                    text = stringResource(R.string.game_best, best),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            if (engine.trackCount > 0) {
                Text(
                    text = stringResource(R.string.game_of_total, engine.trackCount),
                    color = Color.White.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.weight(1f))
            FilledTonalIconButton(
                onClick = { engine.pause() },
                enabled = engine.phase == SnakePhase.RUNNING,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color.White.copy(alpha = 0.08f),
                    contentColor = Color.White,
                    disabledContainerColor = Color.White.copy(alpha = 0.04f),
                    disabledContentColor = Color.White.copy(alpha = 0.3f),
                ),
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.Rounded.Pause, contentDescription = stringResource(R.string.game_paused), modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(6.dp))

        // Now playing banner
        AnimatedVisibility(
            visible = engine.lastEaten != null,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
        ) {
            val eaten = engine.lastEaten
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = scheme.primary.copy(alpha = 0.16f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    AsyncImage(
                        model = trackImageModel(eaten),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.1f)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.game_now_playing),
                            color = scheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = eaten?.let { "${it.title.orEmpty()} • ${it.displayArtist}" }.orEmpty(),
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // The board
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            val maxW = constraints.maxWidth.toFloat()
            val maxH = constraints.maxHeight.toFloat()
            val cell = min(maxW / COLS, maxH / ROWS)
            val cellDp = with(density) { cell.toDp() }
            val boardW = with(density) { (cell * COLS).toDp() }
            val boardH = with(density) { (cell * ROWS).toDp() }

            Box(
                modifier = Modifier
                    .size(boardW, boardH)
                    .clip(RoundedCornerShape(20.dp))
                    .background(GameColors.Board)
                    .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(20.dp))
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
                // Grid, food glow and snake
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawGrid(cell)
                    drawFoodsGlow(engine.foods, cell)
                    drawSnake(engine.snake, cell, scheme.primary)
                }

                // Food covers as real images over the canvas
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
                            .offset {
                                IntOffset(
                                    (food.cell.x * cell).roundToInt(),
                                    (food.cell.y * cell).roundToInt(),
                                )
                            }
                            .size(cellDp)
                            .padding(2.5.dp)
                            .graphicsLayer {
                                scaleX = appeared.value
                                scaleY = appeared.value
                            }
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.12f)),
                    )
                }

                // Overlays
                val overlayAlpha by animateFloatAsState(
                    targetValue = if (engine.phase == SnakePhase.RUNNING) 0f else 1f,
                    label = "overlay",
                )
                if (overlayAlpha > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = overlayAlpha }
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (engine.phase) {
                            SnakePhase.IDLE -> OverlayIdle(
                                onStart = { engine.ensureRunning() },
                                isWave = isWave,
                            )
                            SnakePhase.PAUSED -> OverlayPaused(onResume = { engine.ensureRunning() })
                            SnakePhase.OVER -> OverlayOver(
                                score = engine.score,
                                best = best,
                                onRestart = restart,
                                onClose = onClose,
                            )
                            SnakePhase.WIN -> OverlayWin(
                                score = engine.score,
                                onRestart = restart,
                                onClose = onClose,
                                isWave = isWave,
                            )
                            SnakePhase.RUNNING -> Unit
                        }
                    }
                }
            }
        }

        // D-pad
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            DpadButton(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.game_up)) {
                engine.turn(SnakeDirection.UP); engine.ensureRunning()
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                DpadButton(Icons.Rounded.KeyboardArrowLeft, stringResource(R.string.game_left)) {
                    engine.turn(SnakeDirection.LEFT); engine.ensureRunning()
                }
                Spacer(Modifier.width(14.dp))
                DpadButton(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.game_down)) {
                    engine.turn(SnakeDirection.DOWN); engine.ensureRunning()
                }
                Spacer(Modifier.width(14.dp))
                DpadButton(Icons.Rounded.KeyboardArrowRight, stringResource(R.string.game_right)) {
                    engine.turn(SnakeDirection.RIGHT); engine.ensureRunning()
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.game_hint),
                color = Color.White.copy(alpha = 0.4f),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ─── Overlays & small pieces ────────────────────────────────────────────────────────────

@Composable
private fun OverlayIdle(onStart: () -> Unit, isWave: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onStart,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier.size(72.dp),
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(if (isWave) R.string.game_wave_idle else R.string.game_idle),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.game_eat_hint),
            color = Color.White.copy(alpha = 0.65f),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OverlayPaused(onResume: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onResume,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier.size(72.dp),
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.game_paused),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun OverlayOver(score: Int, best: Int, onRestart: () -> Unit, onClose: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.game_over),
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.game_score, score) + "  •  " + stringResource(R.string.game_best, best),
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(14.dp))
        FilledTonalButton(onClick = onRestart) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.game_restart))
        }
        TextButton(onClick = onClose) {
            Text(stringResource(R.string.btn_close), color = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun OverlayWin(score: Int, onRestart: () -> Unit, onClose: () -> Unit, isWave: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(if (isWave) R.string.game_wave_finished else R.string.game_finished_title),
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.game_finished_text, score),
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        FilledTonalButton(onClick = onRestart) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.game_restart))
        }
        TextButton(onClick = onClose) {
            Text(stringResource(R.string.btn_close), color = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun DpadButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = Color.White.copy(alpha = 0.08f),
            contentColor = Color.White,
        ),
        modifier = Modifier.size(52.dp),
    ) {
        Icon(icon, contentDescription = desc)
    }
}

@Composable
private fun GameMessage(text: String, icon: (@Composable () -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        icon?.invoke()
        if (icon != null) Spacer(Modifier.height(14.dp))
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GameSpinner() {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Restart),
        label = "angle",
    )
    Icon(
        Icons.Rounded.SportsEsports,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .size(42.dp)
            .graphicsLayer { rotationZ = angle },
    )
}

// ─── Canvas drawing ─────────────────────────────────────────────────────────────────────

private object GameColors {
    val Background = Color(0xFF0D0D12)
    val Board = Color(0xFF15151C)
    val Grid = Color(0xFF23232D)
    val FoodGlow = Color(0xFFFFC96B)
}

private fun DrawScope.drawGrid(cell: Float) {
    for (x in 1 until COLS) {
        for (y in 1 until ROWS) {
            drawCircle(
                color = GameColors.Grid,
                radius = cell * 0.045f,
                center = Offset(x * cell, y * cell),
            )
        }
    }
}

private fun DrawScope.drawFoodsGlow(foods: List<SnakeFood>, cell: Float) {
    foods.forEach { food ->
        drawCircle(
            color = GameColors.FoodGlow.copy(alpha = 0.26f),
            radius = cell * 0.72f,
            center = Offset(
                food.cell.x * cell + cell / 2f,
                food.cell.y * cell + cell / 2f,
            ),
        )
    }
}

private fun DrawScope.drawSnake(snake: List<SnakeCell>, cell: Float, bodyColor: Color) {
    val inset = cell * 0.06f
    val size = cell - inset * 2
    val radius = CornerRadius(cell * 0.22f, cell * 0.22f)
    snake.forEachIndexed { index, segment ->
        val color = if (index == 0) {
            bodyColor
        } else {
            bodyColor.copy(alpha = (0.92f - index * 0.02f).coerceAtLeast(0.45f))
        }
        drawRoundRect(
            color = color,
            topLeft = Offset(segment.x * cell + inset, segment.y * cell + inset),
            size = Size(size, size),
            cornerRadius = radius,
        )
    }

    // The head is a little cat face: ears, eyes, nose — KittyTune runs on cats.
    val head = snake.firstOrNull() ?: return
    val cx = head.x * cell + cell / 2f
    val cy = head.y * cell + cell / 2f
    val earSize = cell * 0.2f
    drawTriangle(
        a = Offset(cx - cell * 0.26f, cy - cell * 0.12f),
        b = Offset(cx - cell * 0.3f, cy - cell * 0.12f - earSize),
        c = Offset(cx - cell * 0.06f, cy - cell * 0.26f),
        color = bodyColor,
    )
    drawTriangle(
        a = Offset(cx + cell * 0.26f, cy - cell * 0.12f),
        b = Offset(cx + cell * 0.3f, cy - cell * 0.12f - earSize),
        c = Offset(cx + cell * 0.06f, cy - cell * 0.26f),
        color = bodyColor,
    )
    val eyeRadius = cell * 0.07f
    val eyeColor = Color(0xFF101015)
    drawCircle(eyeColor, eyeRadius, Offset(cx - cell * 0.16f, cy + cell * 0.02f))
    drawCircle(eyeColor, eyeRadius, Offset(cx + cell * 0.16f, cy + cell * 0.02f))
    drawCircle(eyeColor, eyeRadius * 0.6f, Offset(cx, cy + cell * 0.18f))
}

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
