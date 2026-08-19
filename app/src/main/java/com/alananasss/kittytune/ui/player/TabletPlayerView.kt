package com.alananasss.kittytune.ui.player

import android.content.Context
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewSidebar
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.domain.Comment
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.common.viewableCover
import com.alananasss.kittytune.utils.makeTimeString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.text.NumberFormat
import java.util.Locale

/**
 * Sound wave animation indicator (ılı) for currently playing queue track
 */
@Composable
fun SoundWaveIndicator(
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "soundwave")
    val bar1Height by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatModeAnimation.Reverse
        ),
        label = "bar1"
    )
    val bar2Height by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(350, easing = LinearEasing),
            repeatMode = RepeatModeAnimation.Reverse
        ),
        label = "bar2"
    )
    val bar3Height by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = LinearEasing),
            repeatMode = RepeatModeAnimation.Reverse
        ),
        label = "bar3"
    )

    Row(
        modifier = modifier.size(width = 18.dp, height = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(bar1Height)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(bar3Height)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(bar2Height)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
    }
}

typealias RepeatModeAnimation = androidx.compose.animation.core.RepeatMode

/**
 * Right Side Player Panel for Tablet (Matching KittyTune Desktop NowPlayingPanel)
 */
@Composable
fun TabletSidePlayerPanel(
    viewModel: PlayerViewModel,
    onExpandFullScreen: () -> Unit,
    onClose: () -> Unit,
    onOpenFullLyrics: () -> Unit,
    onNavigateToArtist: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val track = viewModel.currentTrack ?: return
    var selectedTab by remember { mutableIntStateOf(0) }

    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onExpandFullScreen,
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.OpenInFull,
                            contentDescription = "Full screen",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.btn_close),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Text(
                    text = viewModel.currentContext?.displayText ?: track.title ?: "",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )

                IconButton(
                    onClick = { viewModel.showTrackOptions(track, fromPlayer = true) },
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.btn_options),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            val tabs = listOf(
                Triple(0, stringResource(R.string.detail_track_title), Icons.Rounded.MusicNote),
                Triple(1, stringResource(R.string.player_queue), Icons.AutoMirrored.Rounded.QueueMusic),
                Triple(2, stringResource(R.string.player_lyrics), Icons.Rounded.Description),
                Triple(3, stringResource(R.string.player_effects), Icons.Rounded.Tune)
            )

            SecondaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEach { (index, title, _) ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedTab) {
                    0 -> DesktopTrackInfoTabContent(
                        viewModel = viewModel,
                        onNavigateToArtist = onNavigateToArtist,
                        onSelectTab = { selectedTab = it }
                    )

                    1 -> TabletQueueList(
                        viewModel = viewModel,
                        onNavigateToArtist = onNavigateToArtist,
                        onOpenExpandedQueue = { viewModel.navigateToExpandedQueue() }
                    )

                    2 -> {
                        if (viewModel.lyricsLines.isNotEmpty()) {
                            SyncedLyricsView(viewModel = viewModel, showControls = true)
                        } else {
                            PlainLyricsView(viewModel = viewModel, showControls = true)
                        }
                    }

                    3 -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            AudioControlDock(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full Desktop TrackInfoTab implementation for Tablet with complete threaded & guest-aware comments
 */
@Composable
fun DesktopTrackInfoTabContent(
    viewModel: PlayerViewModel,
    onNavigateToArtist: (Long) -> Unit,
    onSelectTab: ((Int) -> Unit)? = null
) {
    val track = viewModel.currentTrack ?: return
    val context = LocalContext.current
    val myId = viewModel.currentUserId
    val isGuest = myId == 0L
    val loginToCommentMsg = stringResource(R.string.login_to_comment)
    val loginToInteractMsg = stringResource(R.string.login_to_interact)
    val replyingTo = viewModel.replyingToComment

    val animatedColor by animateColorAsState(
        targetValue = viewModel.backgroundColor,
        animationSpec = tween(durationMillis = 600, easing = LinearEasing),
        label = "bgColor"
    )

    var commentToDelete by remember { mutableStateOf<Comment?>(null) }
    var newCommentText by remember { mutableStateOf("") }

    LaunchedEffect(replyingTo) {
        if (replyingTo != null) {
            val username = replyingTo.user?.username ?: ""
            newCommentText = "@$username: "
        }
    }

    LaunchedEffect(track.id) {
        if (track.id > 0L) {
            viewModel.loadComments(refresh = true, specificTrack = track)
        }
    }

    val organizedComments = remember(viewModel.commentsList.toList()) {
        val list = mutableListOf<Comment>()
        for (comment in viewModel.commentsList) {
            if (comment.body.trim().startsWith("@") && list.isNotEmpty()) {
                val parentIndex = list.indexOfLast { it.trackTimestamp == comment.trackTimestamp }
                if (parentIndex != -1) {
                    val parent = list[parentIndex]
                    list[parentIndex] = parent.copy(replies = (parent.replies ?: emptyList()) + comment)
                    continue
                }
            }
            list.add(comment)
        }
        list
    }

    if (commentToDelete != null) {
        AlertDialog(
            onDismissRequest = { commentToDelete = null },
            title = { Text(stringResource(R.string.dialog_delete_comment_title)) },
            text = { Text(stringResource(R.string.dialog_delete_comment_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteComment(commentToDelete!!)
                        commentToDelete = null
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.btn_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { commentToDelete = null },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
    ) {
        item {
            AsyncImage(
                model = track.fullResArtwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .shadow(12.dp, RoundedCornerShape(16.dp), spotColor = animatedColor)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .viewableCover(track.fullResArtwork)
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PremiumMarqueeText(
                    text = track.title ?: stringResource(R.string.untitled_track),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        track.user?.id?.let { if (it > 0) onNavigateToArtist(it) }
                    }
                ) {
                    Text(
                        text = track.displayArtist.ifBlank { stringResource(R.string.unknown_artist) },
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (track.user?.verified == true) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.Verified,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatItem(
                    icon = Icons.Rounded.PlayArrow,
                    count = track.playbackCount
                )
                StatItem(
                    icon = Icons.Rounded.Favorite,
                    count = track.likesCount,
                    tint = if (viewModel.isLiked) animatedColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { viewModel.navigateToTrackDetails(track.id, 0) }
                )
                StatItem(
                    icon = Icons.Rounded.Repeat,
                    count = track.repostsCount,
                    onClick = { viewModel.navigateToTrackDetails(track.id, 1) }
                )
                StatItem(
                    icon = Icons.AutoMirrored.Rounded.Comment,
                    count = track.commentCount
                )
                IconButton(
                    onClick = { viewModel.navigateToTrackDetails(track.id, 0) },
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = "Details",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PlayerProgress(viewModel, MaterialTheme.colorScheme.onSurface)

                PlayerControls(
                    viewModel = viewModel,
                    animatedMainColor = animatedColor,
                    contentColorOverride = MaterialTheme.colorScheme.onSurface,
                    onEffectsClick = {
                        if (onSelectTab != null) onSelectTab(3) else viewModel.showDetailsSheet = true
                    },
                    onQueueClick = {
                        if (onSelectTab != null) onSelectTab(1) else viewModel.showDetailsSheet = true
                    }
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                track.releaseDate?.let { dateStr ->
                    val formattedDate = remember(dateStr) { formatReleaseDate(dateStr) }
                    if (formattedDate.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${stringResource(R.string.detail_release_date)}: $formattedDate",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                track.genre?.let { genre ->
                    Row(
                        modifier = Modifier.clickable { viewModel.navigateToTag(genre) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${stringResource(R.string.detail_genre)}: $genre",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        track.tagList?.let { tagStr ->
            if (tagStr.isNotBlank()) {
                val tags = tagStr.split(" ", ",").filter { it.isNotBlank() }
                if (tags.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tags.forEach { tag ->
                                SuggestionChip(
                                    onClick = { viewModel.navigateToTag(tag.removePrefix("#")) },
                                    label = { Text(tag) }
                                )
                            }
                        }
                    }
                }
            }
        }

        track.description?.let { desc ->
            if (desc.isNotBlank()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${stringResource(R.string.menu_comments)} (${track.commentCount ?: organizedComments.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    var isSortMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { isSortMenuExpanded = true },
                            shapes = ButtonDefaults.shapes(),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Sort,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(viewModel.commentSort.labelResId),
                                style = MaterialTheme.typography.labelSmall
                            )
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                        }

                        DropdownMenu(
                            expanded = isSortMenuExpanded,
                            onDismissRequest = { isSortMenuExpanded = false }
                        ) {
                            CommentSort.entries.forEach { sortOption ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(sortOption.labelResId)) },
                                    onClick = {
                                        viewModel.onCommentSortChanged(sortOption)
                                        isSortMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                AnimatedVisibility(visible = replyingTo != null && !isGuest) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.comment_replying_to,
                                    "@${replyingTo?.user?.username ?: ""}"
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            IconButton(
                                onClick = { viewModel.cancelReplying(); newCommentText = "" },
                                shapes = IconButtonDefaults.shapes(),
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                if (isGuest) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { Toast.makeText(context, loginToCommentMsg, Toast.LENGTH_SHORT).show() }
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = loginToCommentMsg,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newCommentText,
                            onValueChange = { newCommentText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(if (replyingTo != null) "Write a reply..." else stringResource(R.string.add_comment_hint))
                            },
                            singleLine = true,
                            enabled = !viewModel.isPostingComment,
                            shape = RoundedCornerShape(20.dp)
                        )
                        IconButton(
                            onClick = {
                                if (newCommentText.isNotBlank()) {
                                    viewModel.postComment(newCommentText, null)
                                    newCommentText = ""
                                }
                            },
                            shapes = IconButtonDefaults.shapes(),
                            enabled = !viewModel.isPostingComment && newCommentText.isNotBlank(),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            if (viewModel.isPostingComment) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "Send",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (viewModel.isCommentsLoading && organizedComments.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        } else if (organizedComments.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.comment_no_comments),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        } else {
            itemsIndexed(organizedComments, key = { _, comment -> comment.id }) { index, comment ->
                if (index >= organizedComments.size - 2 && !viewModel.isCommentsLoading && viewModel.commentNextHref != null) {
                    LaunchedEffect(index) {
                        viewModel.loadComments(refresh = false)
                    }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    DesktopCommentRow(
                        comment = comment,
                        isReply = false,
                        isMine = (comment.user?.id == myId && myId != 0L),
                        isGuest = isGuest,
                        viewModel = viewModel,
                        context = context,
                        loginToInteractMsg = loginToInteractMsg,
                        onNavigateToArtist = onNavigateToArtist,
                        onDelete = { commentToDelete = comment }
                    )

                    comment.replies?.forEach { reply ->
                        DesktopCommentRow(
                            comment = reply,
                            isReply = true,
                            isMine = (reply.user?.id == myId && myId != 0L),
                            isGuest = isGuest,
                            viewModel = viewModel,
                            context = context,
                            loginToInteractMsg = loginToInteractMsg,
                            onNavigateToArtist = onNavigateToArtist,
                            onDelete = { commentToDelete = reply }
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 48.dp, top = 8.dp, bottom = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                    )
                }
            }

            if (viewModel.isCommentsLoading && organizedComments.isNotEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopCommentRow(
    comment: Comment,
    isReply: Boolean,
    isMine: Boolean,
    isGuest: Boolean,
    viewModel: PlayerViewModel,
    context: Context,
    loginToInteractMsg: String,
    onNavigateToArtist: (Long) -> Unit,
    onDelete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var translatedText by remember { mutableStateOf<String?>(null) }
    var showTranslation by remember { mutableStateOf(false) }
    var isTranslating by remember { mutableStateOf(false) }

    val currentLocale = Locale.getDefault()
    val langName = remember(currentLocale) {
        currentLocale.getDisplayLanguage(currentLocale)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(currentLocale) else it.toString() }
    }
    val langCode = currentLocale.language

    var isTargetLanguage by remember(comment.body, langCode) { mutableStateOf(false) }
    LaunchedEffect(comment.body, langCode) {
        val cleanText = comment.body.replace(Regex("[^\\p{L}\\p{Nd}\\s]"), "").trim()
        if (cleanText.isBlank()) {
            isTargetLanguage = true
        } else {
            val languageIdentifier = com.google.mlkit.nl.languageid.LanguageIdentification.getClient()
            languageIdentifier.identifyLanguage(cleanText)
                .addOnSuccessListener { language ->
                    if (language == langCode || language == "und") {
                        isTargetLanguage = true
                    }
                }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (isReply) 36.dp else 0.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AsyncImage(
            model = comment.user?.avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(if (isReply) 28.dp else 36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { comment.user?.id?.let { if (it > 0) onNavigateToArtist(it) } }
        )

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = comment.user?.username ?: stringResource(R.string.comment_anonymous),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clickable { comment.user?.id?.let { if (it > 0) onNavigateToArtist(it) } }
                )
                if (comment.user?.verified == true) {
                    Icon(
                        Icons.Rounded.Verified,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                }

                comment.trackTimestamp?.let { ts ->
                    if (ts > 0) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.clickable { viewModel.seekTo(ts) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(10.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    text = makeTimeString(ts),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                Text(
                    text = getRelativeTime(comment.createdAt, context),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            val displayBody = if (showTranslation && !translatedText.isNullOrEmpty()) translatedText!! else comment.body
            CommentBodyText(
                body = displayBody,
                onMentionClick = { mention ->
                    viewModel.resolveAndNavigateToArtist(mention)
                }
            )

            if (translatedText == null && !isTranslating && !isTargetLanguage) {
                Text(
                    text = stringResource(R.string.comment_translate, langName),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            isTranslating = true
                            scope.launch(Dispatchers.IO) {
                                val res = com.alananasss.kittytune.data.network.FreeTranslator.translateMissing(
                                    listOf(comment.body),
                                    langCode
                                )
                                val t = res[comment.body.trim()]
                                withContext(Dispatchers.Main) {
                                    if (t != null && t.lowercase() != comment.body.trim().lowercase()) {
                                        translatedText = t
                                        showTranslation = true
                                    } else {
                                        translatedText = ""
                                    }
                                    isTranslating = false
                                }
                            }
                        }
                        .padding(vertical = 2.dp)
                )
            } else if (isTranslating) {
                Text(
                    text = stringResource(R.string.comment_translating),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            } else if (!translatedText.isNullOrEmpty()) {
                Text(
                    text = if (showTranslation) stringResource(R.string.comment_see_original) else stringResource(
                        R.string.comment_translate,
                        langName
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { showTranslation = !showTranslation }
                        .padding(vertical = 2.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isMine) {
                    IconButton(
                        onClick = onDelete,
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            if (isGuest) {
                                Toast.makeText(context, loginToInteractMsg, Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.startReplying(comment)
                            }
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Reply,
                        contentDescription = "Reply",
                        tint = if (isGuest) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.comment_reply),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isGuest) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.weight(1f))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            if (isGuest) {
                                Toast.makeText(context, loginToInteractMsg, Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.toggleCommentLike(comment)
                            }
                        }
                        .padding(4.dp)
                ) {
                    val isLiked = comment.isLiked
                    Icon(
                        imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLiked) Color(0xFFFF4081) else MaterialTheme.colorScheme.onSurfaceVariant.let {
                            if (isGuest) it.copy(alpha = 0.3f) else it
                        },
                        modifier = Modifier.size(14.dp)
                    )
                    if (comment.likesCount > 0) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = formatCount(comment.likesCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentBodyText(body: String, onMentionClick: (String) -> Unit) {
    val mentionPattern = remember { """@[\w-]+""".toRegex() }
    val urlPattern = remember { """https?://[^\s]+""".toRegex() }
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val primaryColor = MaterialTheme.colorScheme.primary

    val annotatedString = remember(body, tertiaryColor, primaryColor) {
        buildAnnotatedString {
            append(body)
            for (match in mentionPattern.findAll(body)) {
                val username = match.value.removePrefix("@")
                addLink(
                    LinkAnnotation.Clickable(
                        tag = "MENTION",
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = tertiaryColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        ),
                        linkInteractionListener = { onMentionClick(username) }
                    ),
                    match.range.first, match.range.last + 1
                )
            }
            for (match in urlPattern.findAll(body)) {
                addLink(
                    LinkAnnotation.Url(
                        url = match.value,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = primaryColor,
                                textDecoration = TextDecoration.Underline
                            )
                        )
                    ),
                    match.range.first, match.range.last + 1
                )
            }
        }
    }

    SelectionContainer {
        Text(
            text = annotatedString,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 19.sp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int?,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = tint)
        Text(
            text = formatCount(count ?: 0),
            style = MaterialTheme.typography.labelMedium,
            color = tint
        )
    }
}

private fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format(Locale.getDefault(), "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.getDefault(), "%.1fK", count / 1_000.0)
        else -> NumberFormat.getNumberInstance(Locale.getDefault()).format(count)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabletFullScreenPlayerView(
    viewModel: PlayerViewModel,
    onClose: () -> Unit,
    onToggleSplitMode: () -> Unit,
    mainContentColor: Color,
    subContentColor: Color,
    iconTint: Color,
    animatedColor: Color,
    isBlurMode: Boolean,
    modifier: Modifier = Modifier
) {
    val track = viewModel.currentTrack ?: return
    var showEffectsSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleSplitMode, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ViewSidebar,
                        contentDescription = "Split View",
                        tint = mainContentColor
                    )
                }
                IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.btn_close),
                        tint = mainContentColor
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.player_playing_now),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = subContentColor
                )
                Text(
                    text = viewModel.currentContext?.displayText ?: track.title ?: "",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = mainContentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = { viewModel.showTrackOptions(track, fromPlayer = true) },
                shapes = IconButtonDefaults.shapes()
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.btn_options),
                    tint = mainContentColor
                )
            }
        }

        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = viewModel.showInlineLyrics,
                    transitionSpec = {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                    },
                    label = "CoverOrLyrics"
                ) { isLyrics ->
                    if (isLyrics) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            InlineLyricsContent(viewModel = viewModel)
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(1f, matchHeightConstraintsFirst = true)
                                .shadow(
                                    elevation = 20.dp,
                                    shape = RoundedCornerShape(20.dp),
                                    spotColor = if (isBlurMode) Color.Black else animatedColor
                                )
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    if (viewModel.hasLyrics) {
                                        viewModel.openLyrics()
                                    }
                                }
                        ) {
                            AsyncImage(
                                model = track.fullResArtwork,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .viewableCover(track.fullResArtwork)
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        PremiumMarqueeText(
                            text = track.title ?: stringResource(R.string.untitled_track),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = mainContentColor,
                            edgeGradientWidth = 20.dp,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                track.user?.id?.let { if (it > 0) viewModel.navigateToArtist(it) }
                            }
                        ) {
                            Text(
                                text = track.displayArtist.ifBlank { stringResource(R.string.unknown_artist) },
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = subContentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (track.user?.verified == true) {
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Rounded.Verified,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedVisibility(
                            visible = viewModel.hasLyrics,
                            enter = fadeIn(tween(300)),
                            exit = fadeOut(tween(200))
                        ) {
                            IconButton(
                                onClick = { viewModel.openLyrics() },
                                shapes = IconButtonDefaults.shapes()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Description,
                                    contentDescription = stringResource(R.string.player_lyrics),
                                    tint = if (viewModel.showInlineLyrics) animatedColor else iconTint.copy(alpha = 0.8f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.toggleLike() },
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(
                                imageVector = if (viewModel.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Like",
                                tint = if (viewModel.isLiked) animatedColor else iconTint,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    PlayerProgress(viewModel, mainContentColor)
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    PlayerControls(
                        viewModel = viewModel,
                        animatedMainColor = animatedColor,
                        contentColorOverride = mainContentColor,
                        onEffectsClick = { showEffectsSheet = true },
                        onQueueClick = { showQueueSheet = true }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.showSleepTimerDialog = true },
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Timer,
                            contentDescription = stringResource(R.string.sleep_timer_title),
                            tint = iconTint.copy(alpha = 0.75f),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                viewModel.trackForMenu = track
                                viewModel.tracksToAddInBulk = null
                                viewModel.showAddToPlaylistSheet = true
                            },
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                contentDescription = stringResource(R.string.add_to_playlist_title_single),
                                tint = iconTint.copy(alpha = 0.75f),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.shareTrack(track) },
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = stringResource(R.string.btn_share),
                                tint = iconTint.copy(alpha = 0.75f),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(
                            onClick = onToggleSplitMode,
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ViewSidebar,
                                contentDescription = "Sidebar",
                                tint = iconTint.copy(alpha = 0.75f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEffectsSheet) {
        com.alananasss.kittytune.ui.common.KittyModalBottomSheet(
            onDismissRequest = { showEffectsSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            AudioControlDock(viewModel)
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showQueueSheet) {
        com.alananasss.kittytune.ui.common.KittyModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            QueueContent(
                viewModel = viewModel,
                isQueueOpen = true,
                onCloseQueue = { showQueueSheet = false },
                onOpenExpandedQueue = {
                    showQueueSheet = false
                    viewModel.navigateToExpandedQueue()
                }
            )
        }
    }
}

@Composable
private fun TabletQueueList(
    viewModel: PlayerViewModel,
    onNavigateToArtist: (Long) -> Unit,
    onOpenExpandedQueue: () -> Unit
) {
    val view = LocalView.current
    val listState = rememberLazyListState()
    val queue = viewModel.queueState
    val currentIndex = viewModel.currentQueueIndex
    val currentTrack = viewModel.currentTrack

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            viewModel.moveQueueItem(from.index, to.index)
            view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
        }
    )

    var lastScrolledTrackId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(currentTrack?.id) {
        val trackId = currentTrack?.id
        if (trackId != null && trackId != lastScrolledTrackId && queue.isNotEmpty()) {
            lastScrolledTrackId = trackId
            val index = queue.indexOfFirst { it.id == trackId }
            if (index >= 0) {
                listState.animateScrollToItem(kotlin.math.max(0, index - 2))
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "${stringResource(R.string.player_queue)} (${queue.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (queue.isNotEmpty()) {
                    val totalDuration = remember(queue) {
                        val sumMs = queue.sumOf { it.durationMs ?: 0L }
                        if (sumMs > 0) makeTimeString(sumMs) else null
                    }
                    totalDuration?.let { dur ->
                        Text(
                            text = dur,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { viewModel.toggleShuffle() },
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (viewModel.shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(Modifier.width(4.dp))

                IconButton(
                    onClick = onOpenExpandedQueue,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInFull,
                        contentDescription = "Expand Queue",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (queue.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Text(
                        text = stringResource(R.string.player_queue),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                itemsIndexed(
                    items = queue,
                    key = { _, trackItem -> trackItem.id }
                ) { index, trackItem ->
                    ReorderableItem(
                        state = reorderableState,
                        key = trackItem.id
                    ) { isDragging ->
                        val isCurrent = index == currentIndex || trackItem.id == currentTrack?.id
                        val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elevation")
                        val backgroundColor = when {
                            isDragging -> MaterialTheme.colorScheme.surfaceContainerHighest
                            isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            else -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = backgroundColor,
                            shadowElevation = elevation,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.skipToQueueItem(index) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = trackItem.artworkUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    )
                                    if (isCurrent && viewModel.isPlaying) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color.Black.copy(alpha = 0.4f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            SoundWaveIndicator(color = Color.White)
                                        }
                                    }
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = trackItem.title ?: stringResource(R.string.untitled_track),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable {
                                            trackItem.user?.id?.let { if (it > 0) onNavigateToArtist(it) }
                                        }
                                    ) {
                                        Text(
                                            text = trackItem.user?.username ?: stringResource(R.string.unknown_artist),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (trackItem.user?.verified == true) {
                                            Spacer(Modifier.width(3.dp))
                                            Icon(
                                                Icons.Rounded.Verified,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }

                                trackItem.durationMs?.let { durMs ->
                                    if (durMs > 0) {
                                        Text(
                                            text = makeTimeString(durMs),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { viewModel.showTrackOptions(trackItem, fromPlayer = true) },
                                    shapes = IconButtonDefaults.shapes(),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = "Options",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Rounded.DragHandle,
                                    contentDescription = stringResource(R.string.desc_move),
                                    tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                        alpha = 0.4f
                                    ),
                                    modifier = Modifier
                                        .size(28.dp)
                                        .draggableHandle(
                                            onDragStarted = {
                                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            },
                                            onDragStopped = {
                                                view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
                                            }
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatReleaseDate(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val date = runCatching { java.time.Instant.parse(raw).let { java.util.Date.from(it) } }.getOrNull()
        ?: runCatching {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).parse(raw)
        }.getOrNull()
        ?: runCatching {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).parse(raw)
        }.getOrNull()
        ?: runCatching {
            java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss Z", java.util.Locale.US).parse(raw)
        }.getOrNull()
        ?: runCatching { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(raw) }.getOrNull()
        ?: return raw

    val displayFormat = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.getDefault())
    return displayFormat.format(date)
}
