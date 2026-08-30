package com.alananasss.kittytune.ui.history

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.navigation.Screen
import com.alananasss.kittytune.ui.player.PlaybackContext
import com.alananasss.kittytune.ui.player.PlayerViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HistoryScreen(
    onBackClick: () -> Unit,
    onNavigate: (String) -> Unit,
    playerViewModel: PlayerViewModel,
    historyViewModel: HistoryViewModel = viewModel()
) {
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }

    val currentTab = historyViewModel.selectedTab
    val displayedTracks = historyViewModel.displayedTracks
    val displayedContexts = historyViewModel.displayedContexts

    val tracksListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val contextsListState = androidx.compose.foundation.lazy.rememberLazyListState()

    val groupedTracks = remember(displayedTracks.toList(), context) {
        displayedTracks.groupBy { item ->
            formatDateHeader(item.playedAt, context)
        }
    }

    val groupedContexts = remember(displayedContexts.toList(), context) {
        displayedContexts.groupBy { item ->
            formatDateHeader(item.playedAt, context)
        }
    }

    val shouldLoadMoreTracks by remember {
        derivedStateOf {
            val layoutInfo = tracksListState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisible >= totalItems - 8
        }
    }

    val shouldLoadMoreContexts by remember {
        derivedStateOf {
            val layoutInfo = contextsListState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisible >= totalItems - 8
        }
    }

    LaunchedEffect(shouldLoadMoreTracks) {
        if (shouldLoadMoreTracks && !historyViewModel.isLoadingMoreTracks && historyViewModel.canLoadMoreTracks) {
            historyViewModel.loadMoreTracks()
        }
    }

    LaunchedEffect(shouldLoadMoreContexts) {
        if (shouldLoadMoreContexts && !historyViewModel.isLoadingMoreContexts && historyViewModel.canLoadMoreContexts) {
            historyViewModel.loadMoreContexts()
        }
    }

    if (showClearDialog) {
        val dialogTitle = if (currentTab == HistoryTab.TRACKS) {
            stringResource(R.string.history_clear_tracks_title)
        } else {
            stringResource(R.string.history_clear_contexts_title)
        }

        val dialogDesc = if (currentTab == HistoryTab.TRACKS) {
            stringResource(R.string.history_clear_tracks_desc)
        } else {
            stringResource(R.string.history_clear_contexts_desc)
        }

        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(dialogTitle, fontWeight = FontWeight.Bold) },
            text = { Text(dialogDesc) },
            confirmButton = {
                Button(
                    onClick = {
                        historyViewModel.clearHistoryForCurrentTab()
                        showClearDialog = false
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(R.string.btn_clear))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearDialog = false },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.history_title),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.btn_back)
                        )
                    }
                },
                actions = {
                    val hasItems = if (currentTab == HistoryTab.TRACKS) {
                        historyViewModel.tracksHistory.isNotEmpty()
                    } else {
                        historyViewModel.contextsHistory.isNotEmpty()
                    }

                    if (currentTab == HistoryTab.TRACKS && historyViewModel.tracksHistory.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                val tracks = historyViewModel.tracksHistory.map { it.track }
                                playerViewModel.prepareBulkAdd(tracks)
                            },
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.menu_add_playlist)
                            )
                        }
                    }

                    if (hasItems) {
                        IconButton(
                            onClick = { showClearDialog = true },
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = stringResource(R.string.btn_clear),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    IconButton(
                        onClick = { historyViewModel.loadData(forceRefresh = true) },
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.btn_retry)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .background(MaterialTheme.colorScheme.background)
        ) {
            OutlinedTextField(
                value = historyViewModel.searchQuery,
                onValueChange = { historyViewModel.searchQuery = it },
                placeholder = {
                    Text(
                        stringResource(R.string.history_search_hint),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (historyViewModel.searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { historyViewModel.searchQuery = "" },
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                singleLine = true
            )

            com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup(
                options = listOf(HistoryTab.TRACKS, HistoryTab.CONTEXTS),
                selectedOption = currentTab,
                onOptionSelected = { historyViewModel.selectedTab = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                labelProvider = { tab ->
                    Text(
                        text = when (tab) {
                            HistoryTab.TRACKS -> stringResource(R.string.history_tab_tracks)
                            HistoryTab.CONTEXTS -> stringResource(R.string.history_tab_contexts)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )

            val miniPlayerPadding = if (playerViewModel.currentTrack != null) 180.dp else 100.dp

            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    if (targetState == HistoryTab.CONTEXTS) {
                        (slideInHorizontally(animationSpec = tween(280, easing = FastOutSlowInEasing)) { width -> (width * 0.15f).toInt() } + fadeIn(animationSpec = tween(280)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing)) { width -> (-width * 0.15f).toInt() } + fadeOut(animationSpec = tween(180)))
                    } else {
                        (slideInHorizontally(animationSpec = tween(280, easing = FastOutSlowInEasing)) { width -> (-width * 0.15f).toInt() } + fadeIn(animationSpec = tween(280)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing)) { width -> (width * 0.15f).toInt() } + fadeOut(animationSpec = tween(180)))
                    }
                },
                label = "HistoryTabTransition",
                modifier = Modifier.fillMaxSize()
            ) { tab ->
                when (tab) {
                    HistoryTab.TRACKS -> {
                        if (historyViewModel.isLoadingTracks && displayedTracks.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (displayedTracks.isEmpty()) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                if (historyViewModel.isGuest) {
                                    GuestHistoryBanner(onNavigate)
                                }
                                EmptyHistoryView(
                                    title = stringResource(R.string.history_empty_tracks),
                                    subtitle = if (historyViewModel.searchQuery.isNotBlank()) stringResource(R.string.no_results) else "",
                                    onExploreClick = { onNavigate(Screen.Home.route) }
                                )
                            }
                        } else {
                            LazyColumn(
                                state = tracksListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = miniPlayerPadding, top = 8.dp)
                            ) {
                                if (historyViewModel.isGuest) {
                                    item(key = "guest_banner") {
                                        GuestHistoryBanner(onNavigate)
                                    }
                                }
                                groupedTracks.forEach { (dateHeader, items) ->
                                    item(key = "header_$dateHeader") {
                                        Text(
                                            text = dateHeader,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp)
                                        )
                                    }

                                    itemsIndexed(
                                        items = items,
                                        key = { index, historyTrack ->
                                            "${dateHeader}_track_${historyTrack.track.id}_${historyTrack.playedAt}_$index"
                                        }
                                    ) { _, historyTrack ->
                                        val isPlaying = playerViewModel.currentTrack?.id == historyTrack.track.id
                                        HistoryTrackRow(
                                            item = historyTrack,
                                            isPlaying = isPlaying,
                                            onClick = {
                                                val tracksList = displayedTracks.map { it.track }
                                                val clickedIndex =
                                                    tracksList.indexOfFirst { it.id == historyTrack.track.id }
                                                if (clickedIndex != -1) {
                                                    val queue = tracksList.drop(clickedIndex)
                                                    playerViewModel.playPlaylist(
                                                        tracks = queue,
                                                        startIndex = 0,
                                                        context = PlaybackContext(
                                                            displayText = context.getString(R.string.history_title),
                                                            navigationId = "history"
                                                        )
                                                    )
                                                }
                                            },
                                            onMoreClick = {
                                                playerViewModel.showTrackOptions(historyTrack.track)
                                            }
                                        )
                                    }
                                }

                                if (historyViewModel.isLoadingMoreTracks) {
                                    item(key = "loader_more_tracks") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.5.dp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HistoryTab.CONTEXTS -> {
                        if (historyViewModel.isLoadingContexts && displayedContexts.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (displayedContexts.isEmpty()) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                if (historyViewModel.isGuest) {
                                    GuestHistoryBanner(onNavigate)
                                }
                                EmptyHistoryView(
                                    title = stringResource(R.string.history_empty_contexts),
                                    subtitle = if (historyViewModel.searchQuery.isNotBlank()) stringResource(R.string.no_results) else "",
                                    onExploreClick = { onNavigate(Screen.Home.route) }
                                )
                            }
                        } else {
                            LazyColumn(
                                state = contextsListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = miniPlayerPadding, top = 8.dp)
                            ) {
                                if (historyViewModel.isGuest) {
                                    item(key = "guest_banner_ctx") {
                                        GuestHistoryBanner(onNavigate)
                                    }
                                }
                                groupedContexts.forEach { (dateHeader, items) ->
                                    item(key = "header_ctx_$dateHeader") {
                                        Text(
                                            text = dateHeader,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp)
                                        )
                                    }

                                    itemsIndexed(
                                        items = items,
                                        key = { index, contextItem ->
                                            "${dateHeader}_ctx_${contextItem.id}_${contextItem.playedAt}_$index"
                                        }
                                    ) { _, contextItem ->
                                        HistoryContextRow(
                                            item = contextItem,
                                            onClick = {
                                                onNavigate(contextItem.targetNavId)
                                            }
                                        )
                                    }
                                }

                                if (historyViewModel.isLoadingMoreContexts) {
                                    item(key = "loader_more_contexts") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.5.dp
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
    }
}

@Composable
fun HistoryTrackRow(
    item: HistoryTrackItem,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val track = item.track
    val timeStr = remember(item.playedAt) {
        val millis = if (item.playedAt in 1..99_999_999_999L) item.playedAt * 1000L else item.playedAt
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
    }

    val likedTracks by com.alananasss.kittytune.data.LikeRepository.likedTracks.collectAsState()
    val isTrackLiked = remember(track.id, likedTracks) { com.alananasss.kittytune.data.LikeRepository.isTrackLiked(track.id) }

    val socialLikersMap by com.alananasss.kittytune.data.SocialProofRepository.socialLikersMap.collectAsState()
    val socialLikers = socialLikersMap[track.id]

    LaunchedEffect(track.id) {
        if (track.id > 0 && track.source != "youtube") {
            com.alananasss.kittytune.data.SocialProofRepository.requestSocialProof(track.id)
        }
    }

    Surface(
        onClick = onClick,
        color = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val artworkModel = track.artworkUrl?.takeIf { it.isNotBlank() && !it.contains("picsum.photos") }
                    ?: track.fullResArtwork.takeIf { it.isNotBlank() && !it.contains("picsum.photos") }
                    ?: track.user?.avatarUrl?.takeIf { it.isNotBlank() && !it.contains("picsum.photos") }

                if (!artworkModel.isNullOrBlank()) {
                    AsyncImage(
                        model = artworkModel,
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title ?: stringResource(R.string.history_untitled_track),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = track.user?.username ?: stringResource(R.string.history_unknown_artist),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (track.user?.verified == true) {
                        Icon(
                            imageVector = Icons.Rounded.Verified,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Text(
                        text = "• $timeStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )

                    if (isTrackLiked) {
                        Text(text = "·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                    }

                    if (!socialLikers.isNullOrEmpty()) {
                        Text(text = "·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        com.alananasss.kittytune.ui.common.MiniSocialProofAvatars(likers = socialLikers)
                    }
                }
            }

            IconButton(
                onClick = onMoreClick,
                shapes = IconButtonDefaults.shapes()
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun HistoryContextRow(
    item: HistoryContextItem,
    onClick: () -> Unit
) {
    val timeStr = remember(item.playedAt) {
        val millis = if (item.playedAt in 1..99_999_999_999L) item.playedAt * 1000L else item.playedAt
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
    }

    val typeLabel = when (item.type) {
        HistoryContextType.PLAYLIST -> stringResource(R.string.history_type_playlist)
        HistoryContextType.ALBUM -> stringResource(R.string.lib_albums)
        HistoryContextType.ARTIST_STATION, HistoryContextType.TRACK_STATION -> stringResource(R.string.history_type_station)
        HistoryContextType.ARTIST -> stringResource(R.string.history_type_artist)
        HistoryContextType.LIKES -> stringResource(R.string.history_type_likes)
        else -> stringResource(R.string.history_type_playlist)
    }

    val typeIcon = when (item.type) {
        HistoryContextType.PLAYLIST, HistoryContextType.ALBUM -> Icons.Rounded.QueueMusic
        HistoryContextType.ARTIST_STATION, HistoryContextType.TRACK_STATION -> Icons.Rounded.Radio
        HistoryContextType.ARTIST -> Icons.Rounded.Person
        HistoryContextType.LIKES -> Icons.Rounded.Favorite
        else -> Icons.Rounded.Folder
    }

    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val isCircle = item.type == HistoryContextType.ARTIST
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(if (isCircle) CircleShape else RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                if (!item.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = typeIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionChip(
                        onClick = onClick,
                        label = {
                            Text(typeLabel, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = null,
                        modifier = Modifier.height(22.dp)
                    )

                    if (item.subtitle.isNotBlank() && item.subtitle != typeLabel) {
                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }

                    Text(
                        text = "• $timeStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun EmptyHistoryView(
    title: String,
    subtitle: String,
    onExploreClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onExploreClick,
                shapes = ButtonDefaults.shapes()
            ) {
                Icon(Icons.Rounded.Explore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.explorer_title))
            }
        }
    }
}

private fun formatDateHeader(timestamp: Long, context: android.content.Context): String {
    if (timestamp <= 0L) return context.getString(R.string.date_today)
    val millis = if (timestamp in 1..99_999_999_999L) timestamp * 1000L else timestamp

    val calendar = Calendar.getInstance()
    val today = calendar.get(Calendar.DAY_OF_YEAR)
    val year = calendar.get(Calendar.YEAR)

    calendar.timeInMillis = millis
    val itemDay = calendar.get(Calendar.DAY_OF_YEAR)
    val itemYear = calendar.get(Calendar.YEAR)

    return if (year == itemYear) {
        when (today - itemDay) {
            0 -> context.getString(R.string.date_today)
            1 -> context.getString(R.string.date_yesterday)
            else -> SimpleDateFormat("dd MMMM", Locale.getDefault()).format(Date(millis))
        }
    } else {
        SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date(millis))
    }
}

@Composable
private fun GuestHistoryBanner(onNavigate: (String) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.lib_guest_mode),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = stringResource(R.string.history_guest_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { onNavigate(Screen.Login.route) },
                modifier = Modifier.fillMaxWidth(),
                shapes = ButtonDefaults.shapes()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Login,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.login_soundcloud),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
