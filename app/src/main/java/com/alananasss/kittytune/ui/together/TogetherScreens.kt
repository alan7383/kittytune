@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.alananasss.kittytune.ui.together

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.BlockManager
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.data.together.SharedTrack
import com.alananasss.kittytune.data.together.Together
import com.alananasss.kittytune.data.together.TogetherLinks
import com.alananasss.kittytune.data.together.TogetherWire
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.hapticAction
import com.alananasss.kittytune.ui.player.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class HomeActionMode { JOIN, CREATE }
private enum class RoomSectionTab { QUEUE, PLAYLIST, SUGGESTIONS, ADD }

// ─── Together Home Screen (Material 3 Expressive, Gradient-Free) ────────────────────────────

@Composable
fun TogetherHomeScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    playerViewModel: PlayerViewModel? = null,
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val saved by Together.saved.collectAsState()
    val rooms by Together.rooms.collectAsState()
    val activeCode by Together.active.collectAsState()

    var actionMode by rememberSaveable { mutableStateOf(HomeActionMode.JOIN) }
    var newName by rememberSaveable { mutableStateOf("") }
    var joinCode by rememberSaveable { mutableStateOf("") }
    var myName by remember { mutableStateOf(Together.myName) }
    var showNameDialog by remember { mutableStateOf(false) }

    val clipboardInviteCode = remember(saved, activeCode) {
        runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val text = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
            TogetherLinks.extractCode(text, allowRawCode = false)?.takeIf { code ->
                code != activeCode && saved.none { it.code == code }
            }
        }.getOrNull()
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.together_title),
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                    )
                },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = hapticAction(HapticKind.UI, onBack),
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = cs.surfaceContainerHigh,
                            contentColor = cs.onSurface,
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.btn_back))
                    }
                },
                actions = {
                    Surface(
                        onClick = hapticAction(HapticKind.UI) { showNameDialog = true },
                        shape = CircleShape,
                        color = cs.secondaryContainer,
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = cs.onSecondaryContainer,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = myName,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = cs.onSecondaryContainer,
                                maxLines = 1,
                            )
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = stringResource(R.string.together_edit_name),
                                tint = cs.onSecondaryContainer,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cs.background,
                    scrolledContainerColor = cs.surfaceContainer,
                ),
            )
        },
        containerColor = cs.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 180.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. Live Broadcast Hero Card (Expressive-Keyboard HeroCard + Essentials StackedActionCard)
            item {
                val liveRoom = activeCode?.let { rooms[it] ?: Together.Room(it, "") }
                LiveBroadcastHeroCard(
                    activeCode = activeCode,
                    activeRoom = liveRoom,
                    onStartLive = {
                        val code = if (playerViewModel != null) {
                            playerViewModel.startLiveListenAlong()
                        } else {
                            Together.startLiveBroadcast()
                        }
                        onOpen(code)
                    },
                    onOpenRoom = { code -> onOpen(code) },
                    onShareLink = { code -> TogetherLinks.shareInviteLink(context, code) },
                    onStopLive = { Together.stopListening() },
                )
            }

            // 2. Clipboard Invite Banner (when an external invite URL is on clipboard)
            if (clipboardInviteCode != null) {
                item {
                    Surface(
                        onClick = hapticAction(HapticKind.MEDIUM) {
                            Together.joinAndListen(clipboardInviteCode)?.let { onOpen(it.code) }
                        },
                        shape = RoundedCornerShape(26.dp),
                        color = cs.tertiaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            ExpressiveCookieBadge(
                                isActive = true,
                                size = 46.dp,
                                activeColor = cs.tertiary,
                                inactiveColor = cs.tertiary,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Link,
                                    contentDescription = null,
                                    tint = cs.onTertiary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(
                                        R.string.together_clipboard_banner,
                                        TogetherWire.displayCode(clipboardInviteCode),
                                    ),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = cs.onTertiaryContainer,
                                )
                                Text(
                                    text = stringResource(R.string.together_listen),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onTertiaryContainer.copy(alpha = 0.8f),
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = cs.onTertiaryContainer,
                            )
                        }
                    }
                }
            }

            // 3. Connected Join / Create Card (M3 Expressive Segmented Picker + Single Focused Form)
            item {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = cs.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        TogetherSegmentedTabs(
                            options = listOf(
                                TogetherTabOption(
                                    value = HomeActionMode.JOIN,
                                    label = stringResource(R.string.together_join),
                                    icon = Icons.AutoMirrored.Rounded.Login,
                                ),
                                TogetherTabOption(
                                    value = HomeActionMode.CREATE,
                                    label = stringResource(R.string.btn_create),
                                    icon = Icons.Rounded.Add,
                                ),
                            ),
                            selected = actionMode,
                            onSelect = { actionMode = it },
                            containerColor = cs.surfaceContainerHigh,
                        )

                        AnimatedContent(
                            targetState = actionMode,
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                            label = "homeActionModeContent",
                        ) { mode ->
                            when (mode) {
                                HomeActionMode.JOIN -> {
                                    val submitJoin = {
                                        val joined = Together.joinAndListen(joinCode)
                                        if (joined != null) {
                                            joinCode = ""
                                            onOpen(joined.code)
                                        } else {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.together_bad_code),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        OutlinedTextField(
                                            value = joinCode,
                                            onValueChange = { joinCode = it },
                                            placeholder = {
                                                Text(
                                                    stringResource(R.string.together_link_or_code_hint),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Rounded.Link, contentDescription = null)
                                            },
                                            trailingIcon = {
                                                if (joinCode.isNotBlank()) {
                                                    IconButton(onClick = { joinCode = "" }) {
                                                        Icon(Icons.Rounded.Close, contentDescription = null)
                                                    }
                                                } else {
                                                    IconButton(
                                                        onClick = hapticAction(HapticKind.UI) {
                                                            val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                                                                ?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                                                            if (!clip.isNullOrBlank()) {
                                                                joinCode = clip
                                                            }
                                                        },
                                                    ) {
                                                        Icon(
                                                            Icons.Rounded.ContentPaste,
                                                            contentDescription = stringResource(R.string.together_paste),
                                                        )
                                                    }
                                                }
                                            },
                                            singleLine = true,
                                            shape = RoundedCornerShape(18.dp),
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                            keyboardActions = KeyboardActions(onDone = { submitJoin() }),
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        Button(
                                            onClick = hapticAction(HapticKind.MEDIUM) { submitJoin() },
                                            enabled = joinCode.isNotBlank(),
                                            shapes = ButtonDefaults.shapes(),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(50.dp),
                                        ) {
                                            Icon(Icons.Rounded.Headphones, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                stringResource(R.string.together_listen),
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }

                                HomeActionMode.CREATE -> {
                                    val submitCreate = {
                                        val created = Together.create(newName)
                                        newName = ""
                                        TogetherLinks.copyInviteLink(context, created.code)
                                        onOpen(created.code)
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        OutlinedTextField(
                                            value = newName,
                                            onValueChange = { newName = it },
                                            placeholder = {
                                                Text(
                                                    stringResource(R.string.together_name_hint),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null)
                                            },
                                            singleLine = true,
                                            shape = RoundedCornerShape(18.dp),
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                            keyboardActions = KeyboardActions(onDone = { submitCreate() }),
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        Button(
                                            onClick = hapticAction(HapticKind.MEDIUM) { submitCreate() },
                                            shapes = ButtonDefaults.shapes(),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(50.dp),
                                        ) {
                                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                stringResource(R.string.together_create),
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Saved Sessions Group (Android 16 / Essentials 28.dp container with 2.dp spring items)
            item {
                TogetherGroupSection(
                    title = stringResource(R.string.together_saved_sessions),
                ) {
                    if (saved.isEmpty()) {
                        TogetherGroupItem {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                TogetherIconBadge(icon = Icons.Rounded.Groups)
                                Text(
                                    text = stringResource(R.string.together_no_saved_sessions),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = cs.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    } else {
                        saved.forEach { entry ->
                            val room = rooms[entry.code]
                            val isActiveHere = activeCode == entry.code
                            val listeners = room?.listeners?.map { it.name }.orEmpty()
                            val live = isActiveHere || listeners.isNotEmpty()
                            val displayName = room?.name?.ifBlank { entry.name }?.ifBlank { "" }
                                ?: entry.name

                            TogetherGroupItem(
                                highlighted = isActiveHere,
                                onClick = hapticAction(HapticKind.UI) { onOpen(entry.code) },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    TogetherArtwork(
                                        url = room?.nowPlaying?.artworkUrl
                                            ?: entry.tracks.firstOrNull()?.artworkUrl,
                                        isLive = live,
                                        size = 56.dp,
                                        cornerRadius = 16.dp,
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                text = displayName.ifBlank { stringResource(R.string.together_title) },
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false),
                                            )
                                            if (live) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = cs.primary,
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.together_live_badge),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = cs.onPrimary,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                    )
                                                }
                                            }
                                        }
                                        val statusText = when {
                                            room?.nowPlaying != null -> listOfNotNull(
                                                room.nowPlaying.title,
                                                room.nowPlaying.artist,
                                            ).joinToString(" · ")
                                            listeners.isNotEmpty() -> stringResource(
                                                R.string.together_listening_now,
                                                listeners.joinToString(", "),
                                            )
                                            isActiveHere -> stringResource(R.string.together_listen)
                                            else -> stringResource(R.string.together_nobody)
                                        }
                                        Text(
                                            text = statusText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (live) cs.primary else cs.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = TogetherWire.displayCode(entry.code),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = cs.outline,
                                        )
                                    }
                                    IconButton(
                                        onClick = hapticAction(HapticKind.UI) {
                                            TogetherLinks.shareInviteLink(context, entry.code)
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Share,
                                            contentDescription = stringResource(R.string.together_share_link),
                                            tint = cs.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNameDialog) {
        var draft by remember(myName) { mutableStateOf(myName) }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text(stringResource(R.string.together_your_name)) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleaned = draft.trim()
                        if (cleaned.isNotBlank()) {
                            myName = cleaned
                            Together.myName = cleaned
                        }
                        showNameDialog = false
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
        )
    }
}

@Composable
private fun LiveBroadcastHeroCard(
    activeCode: String?,
    activeRoom: Together.Room?,
    onStartLive: () -> Unit,
    onOpenRoom: (String) -> Unit,
    onShareLink: (String) -> Unit,
    onStopLive: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val isLive = activeCode != null
    val containerColor by animateColorAsState(
        targetValue = if (isLive) cs.primaryContainer else cs.surfaceContainerHigh,
        animationSpec = tween(280),
        label = "liveHeroContainer",
    )
    val titleColor = if (isLive) cs.onPrimaryContainer else cs.onSurface
    val subColor = if (isLive) cs.onPrimaryContainer.copy(alpha = 0.82f) else cs.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(containerColor)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ExpressiveCookieBadge(
                isActive = isLive,
                size = 76.dp,
                activeColor = cs.primary,
                inactiveColor = cs.secondaryContainer,
            ) {
                if (isLive) {
                    ListeningBars(
                        isPlaying = true,
                        modifier = Modifier.size(28.dp),
                        color = cs.onPrimary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Podcasts,
                        contentDescription = null,
                        tint = cs.onSecondaryContainer,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = if (isLive) {
                        activeRoom?.name?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.together_live_title)
                    } else {
                        stringResource(R.string.together_live_title)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (isLive && activeCode != null) {
                        val listeners = activeRoom?.listeners?.map { it.name }.orEmpty()
                        if (listeners.isNotEmpty()) {
                            stringResource(R.string.together_listening_now, listeners.joinToString(", "))
                        } else {
                            stringResource(R.string.together_code, TogetherWire.displayCode(activeCode))
                        }
                    } else {
                        stringResource(R.string.together_hint)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = subColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (isLive && activeCode != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = hapticAction(HapticKind.UI) { onOpenRoom(activeCode) },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = cs.primary,
                        contentColor = cs.onPrimary,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.together_open_room), fontWeight = FontWeight.Bold, maxLines = 1)
                }
                FilledTonalIconButton(
                    onClick = hapticAction(HapticKind.UI) { onShareLink(activeCode) },
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = cs.surface,
                        contentColor = cs.onSurface,
                    ),
                ) {
                    Icon(Icons.Rounded.Share, stringResource(R.string.together_share_link))
                }
                FilledTonalIconButton(
                    onClick = hapticAction(HapticKind.MEDIUM, onStopLive),
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = cs.errorContainer,
                        contentColor = cs.onErrorContainer,
                    ),
                ) {
                    Icon(Icons.Rounded.StopCircle, stringResource(R.string.together_stop_live))
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = hapticAction(HapticKind.MEDIUM, onStartLive),
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                ) {
                    Icon(Icons.Rounded.Podcasts, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.together_start_live), fontWeight = FontWeight.Bold, maxLines = 1)
                }
                FilledTonalIconButton(
                    onClick = hapticAction(HapticKind.UI) { onShareLink(Together.personalLiveCode) },
                    modifier = Modifier.size(50.dp),
                ) {
                    Icon(Icons.Rounded.Share, stringResource(R.string.together_share_link))
                }
            }
        }
    }
}

// ─── Equalizer Bars Indicator ───────────────────────────────────────────────────────────────

@Composable
internal fun ListeningBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
) {
    val transition = rememberInfiniteTransition(label = "listening")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Restart),
        label = "phase",
    )
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        listOf(0f, 0.33f, 0.66f).forEach { offset ->
            val h = if (isPlaying) {
                0.35f + 0.65f * (0.5f + 0.5f * kotlin.math.sin((phase + offset) * 2f * Math.PI.toFloat()))
            } else {
                0.35f
            }
            Box(
                Modifier
                    .width(3.dp)
                    .height((22 * h).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

internal fun copyCode(context: Context, code: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("KittyTune", TogetherWire.displayCode(code)))
    Toast.makeText(context, context.getString(R.string.together_code_copied), Toast.LENGTH_SHORT).show()
}

// ─── Together Room / Live Session Screen (Material 3 Expressive, No Gradients!) ─────────────

@Composable
fun TogetherScreen(
    code: String,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val normalizedCode = TogetherWire.normalizeCode(code)
    val rooms by Together.rooms.collectAsState()
    val active by Together.active.collectAsState()
    val connected by Together.isConnected.collectAsState()
    val hostLockedPref by Together.isHostLocked.collectAsState()

    val room = rooms[normalizedCode] ?: Together.Room(normalizedCode, "")
    val isListening = active == normalizedCode
    val isHost = room.hostId == Together.memberId
    val effectiveHostLocked = if (isHost) hostLockedPref else room.hostLocked

    val defaultTab = when {
        room.upcoming.isNotEmpty() || room.hasLiveHost || isListening -> RoomSectionTab.QUEUE
        room.playlist.isNotEmpty() -> RoomSectionTab.PLAYLIST
        else -> RoomSectionTab.ADD
    }
    var selectedTab by rememberSaveable(normalizedCode) { mutableStateOf(defaultTab) }
    var showAllPlaylistTracks by rememberSaveable(normalizedCode) { mutableStateOf(false) }

    var query by rememberSaveable(normalizedCode) { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    val results = remember { mutableStateListOf<Track>() }
    val scope = rememberCoroutineScope()

    // Debounced live search when typing on the Add tab
    LaunchedEffect(query, selectedTab) {
        if (selectedTab != RoomSectionTab.ADD) return@LaunchedEffect
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            if (trimmed.isEmpty()) results.clear()
            isSearching = false
            return@LaunchedEffect
        }
        delay(360L)
        isSearching = true
        val found = runCatching {
            RetrofitClient.create(context).searchTracks(trimmed, limit = 20).collection
        }.getOrDefault(emptyList())
        results.clear()
        results.addAll(BlockManager.filterBlocked(found))
        isSearching = false
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = room.name.ifBlank { stringResource(R.string.together_title) },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (!connected) {
                            Surface(shape = CircleShape, color = cs.errorContainer) {
                                Text(
                                    text = stringResource(R.string.together_offline),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = cs.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = hapticAction(HapticKind.UI, onBack),
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = cs.surfaceContainerHigh,
                            contentColor = cs.onSurface,
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.btn_back))
                    }
                },
                actions = {
                    FilledTonalIconButton(
                        onClick = hapticAction(HapticKind.UI) {
                            TogetherLinks.shareInviteLink(context, normalizedCode)
                        },
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = cs.secondaryContainer,
                            contentColor = cs.onSecondaryContainer,
                        ),
                    ) {
                        Icon(Icons.Rounded.Share, stringResource(R.string.together_share_link))
                    }
                    IconButton(
                        onClick = hapticAction(HapticKind.UI) {
                            Together.forget(normalizedCode)
                            onBack()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.together_forget),
                            tint = cs.onSurfaceVariant,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cs.background,
                    scrolledContainerColor = cs.surfaceContainer,
                ),
            )
        },
        containerColor = cs.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 180.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. Session Hero Card (Pure Solid Tonal Surfaces — Zero Gradients!)
            item {
                RoomExpressiveHeroCard(
                    room = room,
                    isListening = isListening,
                    isHost = isHost,
                    isHostLocked = effectiveHostLocked,
                    onCopyCode = { copyCode(context, normalizedCode) },
                    onShareLink = { TogetherLinks.shareInviteLink(context, normalizedCode) },
                    onToggleListen = {
                        if (isListening) Together.stopListening() else Together.startListening(normalizedCode)
                    },
                    onToggleLock = { Together.setHostLocked(!hostLockedPref) },
                    onClaimHost = { Together.claimHost(normalizedCode) },
                )
            }

            // 2. Host Pending Suggestions Alert Banner (always visible immediately when non-empty)
            if (isHost && room.suggestions.isNotEmpty() && selectedTab != RoomSectionTab.SUGGESTIONS) {
                item {
                    TogetherGroupSection(
                        title = "${stringResource(R.string.together_suggestions)} (${room.suggestions.size})",
                    ) {
                        room.suggestions.forEach { suggestion ->
                            TogetherGroupItem {
                                ExpressiveTrackRow(
                                    track = suggestion.track,
                                    subtitle = stringResource(R.string.together_suggested_by, suggestion.byName),
                                ) {
                                    IconButton(
                                        onClick = hapticAction(HapticKind.UI) {
                                            Together.listenToSuggestion(normalizedCode, suggestion)
                                        },
                                    ) {
                                        Icon(Icons.Rounded.PlayArrow, stringResource(R.string.together_preview))
                                    }
                                    FilledTonalIconButton(
                                        onClick = hapticAction(HapticKind.MEDIUM) {
                                            Together.acceptSuggestion(normalizedCode, suggestion)
                                        },
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = cs.primaryContainer,
                                            contentColor = cs.onPrimaryContainer,
                                        ),
                                    ) {
                                        Icon(Icons.Rounded.Check, stringResource(R.string.together_accept))
                                    }
                                    IconButton(
                                        onClick = hapticAction(HapticKind.UI) {
                                            Together.declineSuggestion(normalizedCode, suggestion)
                                        },
                                    ) {
                                        Icon(Icons.Rounded.Close, stringResource(R.string.together_decline))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Connected M3 Expressive Navigation Tabs
            item {
                val tabs = buildList {
                    add(
                        TogetherTabOption(
                            value = RoomSectionTab.QUEUE,
                            label = stringResource(R.string.together_tab_queue),
                            badgeCount = room.upcoming.size,
                        )
                    )
                    add(
                        TogetherTabOption(
                            value = RoomSectionTab.PLAYLIST,
                            label = stringResource(R.string.together_tab_playlist),
                            badgeCount = room.playlist.size,
                        )
                    )
                    if (isHost && room.suggestions.isNotEmpty()) {
                        add(
                            TogetherTabOption(
                                value = RoomSectionTab.SUGGESTIONS,
                                label = stringResource(R.string.together_suggestions),
                                badgeCount = room.suggestions.size,
                            )
                        )
                    }
                    add(
                        TogetherTabOption(
                            value = RoomSectionTab.ADD,
                            label = stringResource(R.string.together_tab_search),
                            icon = Icons.Rounded.Add,
                        )
                    )
                }
                val activeTab = if (tabs.any { it.value == selectedTab }) selectedTab else RoomSectionTab.QUEUE
                TogetherSegmentedTabs(
                    options = tabs,
                    selected = activeTab,
                    onSelect = { selectedTab = it },
                )
            }

            // 4. Tab Content inside Android 16 / Essentials 28.dp Grouped Containers
            when (selectedTab) {
                RoomSectionTab.QUEUE -> {
                    item {
                        if (room.upcoming.isEmpty()) {
                            TogetherGroupSection(title = stringResource(R.string.together_up_next)) {
                                TogetherGroupItem {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 18.dp, vertical = 20.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    ) {
                                        TogetherIconBadge(icon = Icons.AutoMirrored.Rounded.QueueMusic)
                                        Text(
                                            text = stringResource(R.string.together_empty),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = cs.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                        FilledTonalButton(
                                            onClick = hapticAction(HapticKind.UI) { selectedTab = RoomSectionTab.ADD },
                                            shapes = ButtonDefaults.shapes(),
                                        ) {
                                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(stringResource(R.string.together_tab_search))
                                        }
                                    }
                                }
                            }
                        } else {
                            TogetherGroupSection(title = stringResource(R.string.together_up_next)) {
                                room.upcoming.take(20).forEachIndexed { index, track ->
                                    TogetherGroupItem {
                                        ExpressiveTrackRow(
                                            track = track,
                                            index = index,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                RoomSectionTab.PLAYLIST -> {
                    item {
                        if (room.playlist.isEmpty()) {
                            TogetherGroupSection(title = stringResource(R.string.together_tracks)) {
                                TogetherGroupItem {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 18.dp, vertical = 20.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    ) {
                                        TogetherIconBadge(icon = Icons.Rounded.MusicNote)
                                        Text(
                                            text = stringResource(R.string.together_empty),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = cs.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                        FilledTonalButton(
                                            onClick = hapticAction(HapticKind.UI) { selectedTab = RoomSectionTab.ADD },
                                            shapes = ButtonDefaults.shapes(),
                                        ) {
                                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(stringResource(R.string.together_tab_search))
                                        }
                                    }
                                }
                            }
                        } else {
                            val visibleTracks = if (showAllPlaylistTracks) {
                                room.playlist
                            } else {
                                room.playlist.take(25)
                            }
                            TogetherGroupSection(title = stringResource(R.string.together_tracks)) {
                                visibleTracks.forEachIndexed { index, track ->
                                    val isCurrent = room.nowPlaying?.id == track.id
                                    TogetherGroupItem(
                                        highlighted = isCurrent,
                                        onClick = hapticAction(HapticKind.UI) {
                                            if (isListening && isHost) {
                                                playerViewModel.hostPlay(room.playlist, index)
                                            } else if (isListening && !effectiveHostLocked) {
                                                Together.playNext(track)
                                            } else if (!isListening) {
                                                Together.startListening(normalizedCode, index)
                                            }
                                        },
                                    ) {
                                        ExpressiveTrackRow(
                                            track = track,
                                            index = index,
                                        ) {
                                            if (isListening && (isHost || !effectiveHostLocked)) {
                                                IconButton(
                                                    onClick = hapticAction(HapticKind.UI) {
                                                        Together.playNext(track)
                                                    },
                                                ) {
                                                    Icon(
                                                        Icons.AutoMirrored.Rounded.PlaylistPlay,
                                                        stringResource(R.string.together_play_next),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (!showAllPlaylistTracks && room.playlist.size > 25) {
                                    TogetherGroupItem(
                                        onClick = hapticAction(HapticKind.UI) { showAllPlaylistTracks = true },
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 14.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = stringResource(
                                                    R.string.together_show_all_tracks,
                                                    room.playlist.size,
                                                ),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = cs.primary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                RoomSectionTab.SUGGESTIONS -> {
                    item {
                        TogetherGroupSection(title = stringResource(R.string.together_suggestions)) {
                            room.suggestions.forEach { suggestion ->
                                TogetherGroupItem {
                                    ExpressiveTrackRow(
                                        track = suggestion.track,
                                        subtitle = stringResource(R.string.together_suggested_by, suggestion.byName),
                                    ) {
                                        IconButton(
                                            onClick = hapticAction(HapticKind.UI) {
                                                Together.listenToSuggestion(normalizedCode, suggestion)
                                            },
                                        ) {
                                            Icon(Icons.Rounded.PlayArrow, stringResource(R.string.together_preview))
                                        }
                                        FilledTonalIconButton(
                                            onClick = hapticAction(HapticKind.MEDIUM) {
                                                Together.acceptSuggestion(normalizedCode, suggestion)
                                            },
                                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                containerColor = cs.primaryContainer,
                                                contentColor = cs.onPrimaryContainer,
                                            ),
                                        ) {
                                            Icon(Icons.Rounded.Check, stringResource(R.string.together_accept))
                                        }
                                        IconButton(
                                            onClick = hapticAction(HapticKind.UI) {
                                                Together.declineSuggestion(normalizedCode, suggestion)
                                            },
                                        ) {
                                            Icon(Icons.Rounded.Close, stringResource(R.string.together_decline))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                RoomSectionTab.ADD -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (isSearching) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    } else if (query.isNotBlank()) {
                                        IconButton(onClick = { query = ""; results.clear() }) {
                                            Icon(Icons.Rounded.Close, contentDescription = null)
                                        }
                                    }
                                },
                                placeholder = {
                                    Text(
                                        stringResource(R.string.search_hint),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(22.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    scope.launch {
                                        val trimmed = query.trim()
                                        if (trimmed.isBlank()) return@launch
                                        isSearching = true
                                        val found = runCatching {
                                            RetrofitClient.create(context).searchTracks(trimmed, limit = 20).collection
                                        }.getOrDefault(emptyList())
                                        results.clear()
                                        results.addAll(BlockManager.filterBlocked(found))
                                        isSearching = false
                                    }
                                }),
                                modifier = Modifier.fillMaxWidth(),
                            )

                            if (results.isNotEmpty()) {
                                TogetherGroupSection(title = stringResource(R.string.together_add_tracks)) {
                                    results.forEach { track ->
                                        val shared = SharedTrack.of(track)
                                        TogetherGroupItem {
                                            ExpressiveTrackRow(track = shared) {
                                                if (isListening && (isHost || !effectiveHostLocked)) {
                                                    IconButton(
                                                        onClick = hapticAction(HapticKind.UI) {
                                                            Together.playNext(shared)
                                                        },
                                                    ) {
                                                        Icon(
                                                            Icons.AutoMirrored.Rounded.PlaylistPlay,
                                                            stringResource(R.string.together_play_next),
                                                        )
                                                    }
                                                }
                                                FilledTonalIconButton(
                                                    onClick = hapticAction(HapticKind.MEDIUM) {
                                                        Together.suggest(normalizedCode, shared)
                                                    },
                                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                        containerColor = cs.primaryContainer,
                                                        contentColor = cs.onPrimaryContainer,
                                                    ),
                                                ) {
                                                    Icon(Icons.Rounded.Add, stringResource(R.string.together_suggest))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomExpressiveHeroCard(
    room: Together.Room,
    isListening: Boolean,
    isHost: Boolean,
    isHostLocked: Boolean,
    onCopyCode: () -> Unit,
    onShareLink: () -> Unit,
    onToggleListen: () -> Unit,
    onToggleLock: () -> Unit,
    onClaimHost: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val isLive = isListening || room.hasLiveHost || room.listeners.isNotEmpty()
    val now = room.nowPlaying

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = cs.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Top Status Strip: Scalloped Cookie + LIVE badge + Tappable Code Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ExpressiveCookieBadge(
                        isActive = isLive,
                        size = 40.dp,
                        activeColor = cs.primary,
                        inactiveColor = cs.secondaryContainer,
                    ) {
                        if (isLive && room.isPlaying) {
                            ListeningBars(
                                isPlaying = true,
                                modifier = Modifier.size(16.dp),
                                color = cs.onPrimary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Podcasts,
                                contentDescription = null,
                                tint = if (isLive) cs.onPrimary else cs.onSecondaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Column {
                        Text(
                            text = if (isLive) {
                                stringResource(R.string.together_live_badge)
                            } else {
                                stringResource(R.string.together_title).uppercase()
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isLive) cs.primary else cs.onSurfaceVariant,
                        )
                        val listenerTotal = (room.listeners.size + if (isListening && room.listeners.none { it.id == Together.memberId }) 1 else 0)
                        if (listenerTotal > 0) {
                            Text(
                                text = "$listenerTotal",
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant,
                            )
                        }
                    }
                }

                Surface(
                    onClick = hapticAction(HapticKind.UI, onCopyCode),
                    shape = CircleShape,
                    color = cs.secondaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = TogetherWire.displayCode(room.code),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = cs.onSecondaryContainer,
                        )
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(R.string.together_copy_code),
                            tint = cs.onSecondaryContainer,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
                TogetherInviteQrButton(room.code)
            }

            if (isListening) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TogetherReactionButton(roomCode = room.code)
                    TogetherSyncBadge(roomCode = room.code)
                }
            }

            // Now Playing Showcase Card (Solid SurfaceContainerHigh — Zero Gradients)
            if (now != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(cs.surfaceContainerHigh)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        TogetherArtwork(
                            url = now.artworkUrl,
                            isLive = room.isPlaying,
                            size = 76.dp,
                            cornerRadius = 18.dp,
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = now.title ?: stringResource(R.string.untitled_track),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = cs.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = now.artist.orEmpty().ifBlank { stringResource(R.string.unknown_artist) },
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    TogetherLiveProgressBar(room = room)
                }
            }

            // Connected Members Row (Horizontal Scroll, High-Contrast Tonal Pills)
            val selfAvatarUrl by Together.myAvatarUrlFlow.collectAsState()
            val allMembers = remember(room.listeners, isHost, isListening, room.hostId, selfAvatarUrl) {
                buildList {
                    if (isHost || isListening) {
                        add(Together.Member(Together.memberId, Together.myName, true, System.currentTimeMillis(), selfAvatarUrl))
                    }
                    room.listeners.filterNot { it.id == Together.memberId }.forEach { add(it) }
                }
            }
            if (allMembers.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    allMembers.forEach { member ->
                        val isSelf = member.id == Together.memberId
                        TogetherMemberPill(
                            name = member.name,
                            isHost = member.id == room.hostId || (isHost && isSelf),
                            isMe = isSelf,
                            avatarUrl = if (isSelf) (selfAvatarUrl ?: Together.myAvatarUrl) else member.avatarUrl,
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.together_nobody),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }

            // Host Lock Toggle / Guest Lock Info Row
            if (isHost) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(cs.surfaceContainerHigh)
                        .clickable(onClick = hapticAction(HapticKind.UI, onToggleLock))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TogetherIconBadge(
                        icon = if (isHostLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                        containerColor = if (isHostLocked) cs.primaryContainer else cs.secondaryContainer,
                        contentColor = if (isHostLocked) cs.onPrimaryContainer else cs.onSecondaryContainer,
                        size = 36.dp,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.together_lock_controls),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.together_lock_controls_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Switch(
                        checked = isHostLocked,
                        onCheckedChange = { onToggleLock() },
                        thumbContent = if (isHostLocked) {
                            { Icon(Icons.Rounded.Check, null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                        } else null,
                    )
                }
            } else if (isListening) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(cs.secondaryContainer)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = if (isHostLocked) Icons.Rounded.Lock else Icons.Rounded.Headphones,
                            contentDescription = null,
                            tint = cs.onSecondaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(
                                if (isHostLocked) R.string.together_listen_only_badge
                                else R.string.together_automix_host_only
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSecondaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        if (!isHostLocked) {
                            Surface(
                                onClick = hapticAction(HapticKind.UI, onClaimHost),
                                shape = CircleShape,
                                color = cs.surface,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(Icons.Rounded.Star, null, tint = cs.primary, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = stringResource(R.string.together_become_host),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Primary Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isListening) {
                    FilledTonalButton(
                        onClick = hapticAction(HapticKind.MEDIUM, onToggleListen),
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = cs.errorContainer,
                            contentColor = cs.onErrorContainer,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.together_leave),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else {
                    Button(
                        onClick = hapticAction(HapticKind.MEDIUM, onToggleListen),
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                    ) {
                        Icon(Icons.Rounded.Headphones, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.together_listen),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                FilledTonalButton(
                    onClick = hapticAction(HapticKind.UI, onShareLink),
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.height(52.dp),
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.together_share_link),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpressiveTrackRow(
    track: SharedTrack,
    index: Int? = null,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            Text(
                text = (index + 1).toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = cs.onSurfaceVariant,
                modifier = Modifier.width(28.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        TogetherArtwork(
            url = track.artworkUrl,
            isLive = false,
            size = 48.dp,
            cornerRadius = 12.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle ?: track.artist.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        track.durationMs?.takeIf { it > 0 }?.let { ms ->
            Text(
                text = formatTrackClock(ms),
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            actions()
        }
    }
}
