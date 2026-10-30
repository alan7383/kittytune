package com.alananasss.kittytune.ui.together

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import com.alananasss.kittytune.ui.design.Button
import androidx.compose.material3.ButtonDefaults
import com.alananasss.kittytune.ui.design.FilledIconButton
import com.alananasss.kittytune.ui.design.FilledTonalButton
import com.alananasss.kittytune.ui.design.FilledTonalIconButton
import androidx.compose.material3.Icon
import com.alananasss.kittytune.ui.design.IconButton
import androidx.compose.material3.IconButtonDefaults
import com.alananasss.kittytune.ui.design.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import com.alananasss.kittytune.ui.design.OutlinedTextField
import androidx.compose.material3.Scaffold
import com.alananasss.kittytune.ui.design.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.hapticAction
import com.alananasss.kittytune.ui.common.hapticClickable
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.data.together.SharedTrack
import com.alananasss.kittytune.data.together.Together
import com.alananasss.kittytune.data.together.TogetherWire
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.player.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The shared playlists this listener has, and the ways to make or join one (issue #66, ported from desktop).
 * Each card says who is listening right now, live, so a friend already playing is seen from here.
 */
@Composable
fun TogetherHomeScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val saved by Together.saved.collectAsState()
    val rooms by Together.rooms.collectAsState()
    var newName by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }
    var myName by remember { mutableStateOf(Together.myName) }

    // The name is kept in memory while typing and persisted debounced, plus once more when this
    // screen goes away: a SharedPreferences write per keystroke kept the disk busy for nothing.
    LaunchedEffect(Unit) {
        snapshotFlow { myName }.collectLatest { name ->
            delay(800)
            if (name != Together.myName) Together.myName = name
        }
    }
    DisposableEffect(Unit) {
        onDispose { Together.myName = myName }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.together_title),
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                    )
                },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_back))
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 180.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.together_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                ActionCard(
                    title = stringResource(R.string.together_create),
                    icon = Icons.Rounded.Add,
                    value = newName,
                    onValueChange = { newName = it },
                    hint = stringResource(R.string.together_name_hint),
                    button = stringResource(R.string.btn_create),
                ) {
                    val created = Together.create(newName)
                    newName = ""
                    copyCode(context, created.code)
                    onOpen(created.code)
                }
            }
            item {
                ActionCard(
                    title = stringResource(R.string.together_join),
                    icon = Icons.Rounded.Login,
                    value = joinCode,
                    onValueChange = { joinCode = it.uppercase().take(12) },
                    hint = stringResource(R.string.together_code_hint),
                    button = stringResource(R.string.together_join),
                ) {
                    Together.join(joinCode)?.let {
                        joinCode = ""
                        onOpen(it.code)
                    } ?: Toast.makeText(context, context.getString(R.string.together_bad_code), Toast.LENGTH_SHORT).show()
                }
            }
            item {
                OutlinedTextField(
                    value = myName,
                    onValueChange = { myName = it },
                    label = { Text(stringResource(R.string.together_your_name)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.widthIn(max = 360.dp),
                )
            }
            items(saved, key = { it.code }) { entry ->
                val room = rooms[entry.code]
                SavedRoomCard(
                    name = room?.name?.ifBlank { entry.name }?.ifBlank { "" } ?: entry.name,
                    code = entry.code,
                    listeners = room?.listeners?.map { it.name }.orEmpty(),
                    cover = room?.nowPlaying?.artworkUrl ?: entry.tracks.firstOrNull()?.artworkUrl,
                    onClick = hapticAction(HapticKind.UI) { onOpen(entry.code) },
                )
            }
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    button: String,
    onAction: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                }
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(hint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAction() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = onAction, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text(button) }
        }
    }
}

@Composable
private fun SavedRoomCard(name: String, code: String, listeners: List<String>, cover: String?, onClick: () -> Unit) {
    val live = listeners.isNotEmpty()
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .hapticClickable(onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            TogetherCover(cover, isLive = live, size = 64)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name.ifBlank { stringResource(R.string.together_title) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (listeners.isEmpty()) stringResource(R.string.together_nobody)
                    else stringResource(R.string.together_listening_now, listeners.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (listeners.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.together_code, TogetherWire.displayCode(code)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            if (live) ListeningBars(isPlaying = true, modifier = Modifier.size(22.dp), color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** A shared playlist's cover: the song playing in it, on the two-person mark, with live bars over it. */
@Composable
private fun TogetherCover(url: String?, isLive: Boolean, size: Int, elevated: Boolean = false) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .then(if (elevated) Modifier.shadow(12.dp, RoundedCornerShape(16.dp)) else Modifier)
            .clip(RoundedCornerShape(if (elevated) 16.dp else (size / 4).dp))
            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(Icons.Rounded.Groups, null, tint = Color.White, modifier = Modifier.size((size / 2).dp))
        if (isLive && url != null) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) { ListeningBars(isPlaying = true, modifier = Modifier.size((size / 3).dp)) }
        }
    }
}

/** Three bars that dance while the shared playlist has someone listening. */
@Composable
internal fun ListeningBars(isPlaying: Boolean, modifier: Modifier = Modifier, color: Color = Color.White) {
    val transition = rememberInfiniteTransition(label = "listening")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Restart),
        label = "phase"
    )
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        listOf(0f, 0.33f, 0.66f).forEach { offset ->
            val h = 0.35f + 0.65f * (0.5f + 0.5f * kotlin.math.sin((phase + offset) * 2f * Math.PI.toFloat()))
            Box(
                Modifier
                    .width(3.dp)
                    .height((24 * h).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

private fun copyCode(context: Context, code: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("KittyTune", TogetherWire.displayCode(code)))
    Toast.makeText(context, context.getString(R.string.together_code_copied), Toast.LENGTH_SHORT).show()
}

/**
 * One shared playlist (issue #66): who is in it and who hosts, the song playing for everyone, the host's
 * suggestions to answer, what plays next for everyone, the playlist's own tracks, and a search to add more.
 */
@Composable
fun TogetherScreen(code: String, playerViewModel: PlayerViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val rooms by Together.rooms.collectAsState()
    val active by Together.active.collectAsState()
    val connected by Together.isConnected.collectAsState()
    val room = rooms[code] ?: Together.Room(code, "")
    val isListening = active == code
    val isHost = room.hostId == Together.memberId
    var query by remember { mutableStateOf("") }
    val results = remember { mutableStateListOf<Track>() }
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 180.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { RoomHeader(room, isListening, isHost, connected, playerViewModel, onBack, onCopy = { copyCode(context, code) }) }

        if (isHost && room.suggestions.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.together_suggestions)) }
            itemsIndexed(room.suggestions, key = { i, s -> "s$i-${s.track.id}" }) { _, s ->
                SharedTrackRow(s.track, subtitle = stringResource(R.string.together_suggested_by, s.byName)) {
                    IconButton(onClick = hapticAction(HapticKind.UI) { Together.listenToSuggestion(code, s) }) {
                        Icon(Icons.Rounded.PlayArrow, stringResource(R.string.together_preview))
                    }
                    FilledTonalIconButton(onClick = hapticAction(HapticKind.UI) { Together.acceptSuggestion(code, s) }) {
                        Icon(Icons.Rounded.Check, stringResource(R.string.together_accept))
                    }
                    IconButton(onClick = hapticAction(HapticKind.UI) { Together.declineSuggestion(code, s) }) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.together_decline))
                    }
                }
            }
        }

        if (room.upcoming.isNotEmpty() && room.hasLiveHost) {
            item { SectionTitle(stringResource(R.string.together_up_next)) }
            itemsIndexed(room.upcoming.take(8), key = { i, t -> "u$i-${t.id}" }) { _, t -> SharedTrackRow(t) {} }
        }

        item { SectionTitle(stringResource(R.string.together_tracks)) }
        if (room.playlist.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.together_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        itemsIndexed(room.playlist, key = { i, t -> "p$i-${t.id}" }) { index, t ->
            SharedTrackRow(t, index = index, onClick = hapticAction(HapticKind.UI) {
                if (isListening && isHost) playerViewModel.hostPlay(room.playlist, index)
                else if (isListening) Together.playNext(t)
                else Together.startListening(code, index)
            }) {
                if (isListening) {
                    IconButton(onClick = hapticAction(HapticKind.UI) { Together.playNext(t) }) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, stringResource(R.string.together_play_next))
                    }
                }
            }
        }

        item {
            SectionTitle(stringResource(R.string.together_add_tracks))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text(stringResource(R.string.search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    scope.launch {
                        val found = runCatching {
                            RetrofitClient.create(context).searchTracks(query, limit = 20).collection
                        }.getOrDefault(emptyList())
                        results.clear()
                        results.addAll(com.alananasss.kittytune.data.BlockManager.filterBlocked(found))
                    }
                }),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(),
            )
        }
        itemsIndexed(results, key = { i, t -> "r$i-${t.id}" }) { _, track ->
            val shared = SharedTrack.of(track)
            SharedTrackRow(shared) {
                if (isListening) {
                    IconButton(onClick = hapticAction(HapticKind.UI) { Together.playNext(shared) }) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, stringResource(R.string.together_play_next))
                    }
                }
                FilledTonalIconButton(onClick = hapticAction(HapticKind.UI) { Together.suggest(code, shared) }) {
                    Icon(Icons.Rounded.Add, stringResource(R.string.together_suggest))
                }
            }
        }
    }
}

/**
 * The top of a shared playlist: a large cover, the name big, a line of facts, and under them one round play
 * button with the quieter actions beside it — a playlist's page, shared.
 */
@Composable
private fun RoomHeader(
    room: Together.Room,
    isListening: Boolean,
    isHost: Boolean,
    connected: Boolean,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onCopy: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(scheme.primaryContainer.copy(alpha = 0.55f), scheme.background)))
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(
                onClick = onBack,
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = scheme.surfaceVariant.copy(alpha = 0.6f),
                    contentColor = scheme.onSurfaceVariant,
                )
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.btn_back))
            }
            Spacer(Modifier.weight(1f))
            if (!connected) Text(stringResource(R.string.together_offline), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            TogetherCover(
                room.nowPlaying?.artworkUrl ?: room.playlist.firstOrNull()?.artworkUrl,
                isLive = room.listeners.isNotEmpty() || isListening,
                size = 140,
                elevated = true,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.together_title).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurfaceVariant
                )
                Text(
                    room.name.ifBlank { stringResource(R.string.together_title) },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val totalMs = room.playlist.sumOf { it.durationMs ?: 0L }
                val facts = listOfNotNull(
                    stringResource(R.string.playlist_num_tracks, room.playlist.size),
                    formatTotalDuration(totalMs).takeIf { totalMs > 0 },
                )
                Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                MembersRow(room, isHost)
            }
        }
        AnimatedVisibility(visible = room.nowPlaying != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            room.nowPlaying?.let { now ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (room.isPlaying) ListeningBars(isPlaying = true, modifier = Modifier.size(18.dp), color = scheme.primary)
                    else Icon(Icons.Rounded.Headphones, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        listOfNotNull(now.title, now.artist).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            // One round button, as a playlist has: listen, or leave while listening.
            FilledIconButton(
                onClick = hapticAction(HapticKind.UI) { if (isListening) Together.stopListening() else Together.startListening(room.code) },
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    if (isListening) Icons.Rounded.Logout else Icons.Rounded.PlayArrow,
                    if (isListening) stringResource(R.string.together_leave) else stringResource(R.string.together_listen),
                    modifier = Modifier.size(30.dp)
                )
            }
            Surface(onClick = onCopy, shape = CircleShape, color = scheme.surfaceContainerHighest) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.together_code, TogetherWire.displayCode(room.code)),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            if (isListening && !isHost) {
                FilledTonalButton(onClick = hapticAction(HapticKind.UI) { Together.claimHost(room.code) }) {
                    Icon(Icons.Rounded.Star, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.together_become_host))
                }
            }
            IconButton(onClick = hapticAction(HapticKind.UI) { Together.forget(room.code); onBack() }) {
                Icon(Icons.Rounded.Delete, stringResource(R.string.together_forget), tint = scheme.onSurfaceVariant)
            }
        }
        if (isListening && !isHost) {
            Text(stringResource(R.string.together_automix_host_only), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
    }
}

private fun formatTotalDuration(totalMs: Long): String {
    val totalSec = totalMs / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    return if (h > 0) String.format("%dh %02dm", h, m) else String.format("%dm", m)
}

@Composable
private fun MembersRow(room: Together.Room, isHost: Boolean) {
    val listeners = room.listeners
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (isHost) MemberChip(Together.myName, isHost = true, isMe = true)
        listeners.forEach { member -> MemberChip(member.name, isHost = member.id == room.hostId, isMe = false) }
        if (!isHost && listeners.isEmpty()) {
            Text(stringResource(R.string.together_nobody), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MemberChip(name: String, isHost: Boolean, isMe: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = CircleShape, color = if (isHost) scheme.tertiaryContainer else scheme.surfaceContainerHighest) {
        Row(
            Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(avatarColour(name)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.take(1).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isMe) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isHost) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.Star, null, tint = scheme.tertiary, modifier = Modifier.size(14.dp).offset(y = (-1).dp))
            }
        }
    }
}

private fun avatarColour(name: String): Color {
    val palette = listOf(Color(0xFF7C3AED), Color(0xFF0EA5E9), Color(0xFFF97316), Color(0xFF10B981), Color(0xFFE11D48), Color(0xFFEAB308))
    return palette[Math.floorMod(name.hashCode(), palette.size)]
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

@Composable
private fun SharedTrackRow(
    track: SharedTrack,
    index: Int? = null,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.hapticClickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            Text(
                (index + 1).toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(32.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        AsyncImage(
            model = track.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle ?: track.artist.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        track.durationMs?.takeIf { it > 0 }?.let { ms ->
            Text(
                String.format("%d:%02d", ms / 60000, (ms / 1000) % 60),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}
