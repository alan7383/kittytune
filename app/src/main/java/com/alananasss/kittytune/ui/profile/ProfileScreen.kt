package com.alananasss.kittytune.ui.profile

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.alananasss.kittytune.ui.common.viewableCover
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.DownloadManager
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.common.ShimmerLine
import com.alananasss.kittytune.ui.common.TrackListItemShimmer
import com.alananasss.kittytune.ui.library.TrackListItem
import com.alananasss.kittytune.ui.player.PlaybackContext
import com.alananasss.kittytune.ui.player.PlayerViewModel
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.alananasss.kittytune.domain.isDefaultAvatar
import com.alananasss.kittytune.domain.getHighResAvatarUrl
import com.alananasss.kittytune.domain.Comment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.util.Locale
import java.util.regex.Pattern

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileScreen(
    userId: String,
    onBackClick: () -> Unit,
    playerViewModel: PlayerViewModel,
    onNavigate: (String) -> Unit = {},
    profileViewModel: ProfileViewModel = viewModel()
) {
    val downloadProgress by DownloadManager.downloadProgress.collectAsState()
    val listState = rememberLazyListState()

    var expandedSection by remember { mutableStateOf<String?>(null) }
    var selectedDiscographyFilter by remember { mutableStateOf("popular") }
    var showEditSheet by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val prefs = remember { com.alananasss.kittytune.data.local.PlayerPreferences(context) }

    LaunchedEffect(userId) {
        profileViewModel.loadProfile(userId)
    }

    LaunchedEffect(userId) {
        ProfileViewModel.refreshTrigger.collect { targetUserId ->
            val id = userId.toLongOrNull()
            if (id != null && (targetUserId == 0L || targetUserId == id)) {
                profileViewModel.loadProfile(id, forceRefresh = true)
            } else if (userId.startsWith("spotify")) {
                profileViewModel.loadProfile(userId, forceRefresh = true)
            }
        }
    }

    val user = profileViewModel.user

    BackHandler(enabled = expandedSection != null) {
        expandedSection = null
    }

    val artistText = stringResource(R.string.generic_artist)
    val artistPlaybackContext = remember(user, artistText) {
        user?.let {
            val navId = if (it.urn?.startsWith("spotify:artist:") == true) {
                "spotify_artist:${com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(it.urn)}"
            } else if (it.urn?.startsWith("spotify") == true || it.permalinkUrl?.contains("spotify") == true) {
                val clean = it.permalink ?: it.urn?.removePrefix("spotify:artist:") ?: ""
                "spotify_artist:${com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(clean)}"
            } else {
                "profile:${it.id}"
            }
            PlaybackContext(
                displayText = "$artistText • ${it.username}",
                navigationId = navId,
                imageUrl = it.avatarUrl,
                artistName = it.username,
                isVerified = it.verified
            )
        }
    }

    val windowSizeInfo = com.alananasss.kittytune.ui.common.rememberWindowSizeInfo()

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        if (profileViewModel.isLoading && user == null) {
            ProfileScreenShimmer(onBackClick)
        } else if (user != null) {
            val bgModel = user.bannerUrl ?: user.avatarUrl
            if (bgModel != null) {
                Box(modifier = Modifier.fillMaxWidth().height(480.dp)) {
                    AsyncImage(
                        model = bgModel,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(60.dp)
                            .alpha(0.6f)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                        MaterialTheme.colorScheme.background,
                                        MaterialTheme.colorScheme.background
                                    ),
                                    startY = 0f
                                )
                            )
                    )
                }
            }

            Box(
                modifier = if (windowSizeInfo.isTablet) Modifier.widthIn(max = 840.dp)
                    .fillMaxSize() else Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 180.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        ModernProfileHeader(
                            user = user,
                            isCurrentUser = profileViewModel.isCurrentUser,
                            onEditClick = { showEditSheet = true },
                            playerViewModel = playerViewModel,
                            onNavigate = onNavigate,
                            profileViewModel = profileViewModel,
                            artistContext = artistPlaybackContext
                        )
                    }

                    if (!user.description.isNullOrBlank()) {
                        item {
                            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                                Text(
                                    text = stringResource(R.string.profile_about),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(8.dp))
                                ExpandableDescription(
                                    text = user.description,
                                    onUrlClick = { url -> uriHandler.openUri(url) },
                                    onMentionClick = { username ->
                                        playerViewModel.resolveAndNavigateToArtist(username)
                                    }
                                )
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                    }

                    if (profileViewModel.popularTracks.isNotEmpty()) {
                        item {
                            SectionTitle(
                                title = stringResource(R.string.profile_tab_popular),
                                showMore = profileViewModel.popularTracks.size > 5,
                                onMoreClick = { expandedSection = "popular" })
                        }
                        itemsIndexed(profileViewModel.popularTracks.take(5)) { index, track ->
                            ProfileTrackItem(
                                track,
                                index,
                                playerViewModel,
                                downloadProgress,
                                profileViewModel.popularTracks,
                                artistPlaybackContext
                            )
                        }
                    }

                    if (!profileViewModel.isSpotifyProfile) {
                        if (profileViewModel.allTracks.isNotEmpty()) {
                            item {
                                SectionTitle(
                                    title = stringResource(R.string.profile_latest_tracks),
                                    showMore = true,
                                    onMoreClick = { expandedSection = "tracks" })
                            }
                            itemsIndexed(profileViewModel.allTracks.take(5)) { index, track ->
                                ProfileTrackItem(
                                    track,
                                    index,
                                    playerViewModel,
                                    downloadProgress,
                                    profileViewModel.allTracks,
                                    artistPlaybackContext
                                )
                            }
                        }

                        if (profileViewModel.albums.isNotEmpty()) {
                            item { SectionTitle(stringResource(R.string.profile_tab_albums)) }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(profileViewModel.albums) { playlist ->
                                        SquareCard(playlist) {
                                            onNavigate(
                                                playlist.id.toString()
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (profileViewModel.playlists.isNotEmpty()) {
                            item {
                                val name = user.username ?: stringResource(R.string.generic_artist)
                                SectionTitle(stringResource(R.string.profile_playlists_by_user, name))
                            }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(profileViewModel.playlists) { playlist ->
                                        SquareCard(playlist) {
                                            onNavigate(
                                                playlist.id.toString()
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (profileViewModel.likedTracks.isNotEmpty()) {
                            item {
                                val name = user.username ?: stringResource(R.string.generic_artist)
                                SectionTitle(
                                    title = stringResource(R.string.profile_likes_by_user, name),
                                    showMore = true,
                                    onMoreClick = { expandedSection = "likes" })
                            }
                            itemsIndexed(profileViewModel.likedTracks.take(3)) { index, track ->
                                ProfileTrackItem(
                                    track,
                                    index,
                                    playerViewModel,
                                    downloadProgress,
                                    profileViewModel.likedTracks,
                                    null
                                )
                            }
                        }

                        if (profileViewModel.repostedTracks.isNotEmpty()) {
                            item {
                                SectionTitle(
                                    title = stringResource(R.string.profile_tab_reposts),
                                    showMore = true,
                                    onMoreClick = { expandedSection = "reposts" })
                            }
                            itemsIndexed(profileViewModel.repostedTracks.take(5)) { index, track ->
                                ProfileTrackItem(
                                    track,
                                    index,
                                    playerViewModel,
                                    downloadProgress,
                                    profileViewModel.repostedTracks,
                                    artistPlaybackContext
                                )
                            }
                        }

                        if (profileViewModel.userComments.isNotEmpty()) {
                            item {
                                SectionTitle(
                                    title = stringResource(R.string.profile_tab_comments),
                                    showMore = profileViewModel.userComments.size > 3,
                                    onMoreClick = { expandedSection = "comments" }
                                )
                            }
                            itemsIndexed(profileViewModel.userComments.take(3)) { index, comment ->
                                UserCommentItem(
                                    comment = comment,
                                    onTrackClick = {
                                        comment.track?.let { track ->
                                            playerViewModel.playPlaylist(listOf(track), 0)
                                        }
                                    }
                                )
                            }
                        }

                        if (profileViewModel.similarArtists.isNotEmpty()) {
                            item { Spacer(Modifier.height(24.dp)) }
                            item { SectionTitle(stringResource(R.string.profile_similar_artists)) }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(profileViewModel.similarArtists) { artist ->
                                        ArtistCircle(artist) {
                                            onNavigate(artist.profileNavId)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (profileViewModel.isSpotifyProfile) {
                        val hasDiscography = profileViewModel.popularReleases.isNotEmpty() ||
                                profileViewModel.albums.isNotEmpty() ||
                                profileViewModel.singles.isNotEmpty() ||
                                profileViewModel.compilations.isNotEmpty()

                        if (hasDiscography) {
                            val availableTabs = buildList {
                                if (profileViewModel.popularReleases.isNotEmpty()) add("popular" to R.string.spotify_popular_releases)
                                if (profileViewModel.albums.isNotEmpty()) add("albums" to R.string.profile_tab_albums)
                                if (profileViewModel.singles.isNotEmpty()) add("singles" to R.string.spotify_singles_eps)
                                if (profileViewModel.compilations.isNotEmpty()) add("compilations" to R.string.spotify_compilations)
                            }

                            item {
                                SectionTitle(
                                    title = stringResource(R.string.spotify_discography),
                                    showMore = true,
                                    onMoreClick = { expandedSection = "discography" }
                                )
                            }

                            if (availableTabs.size > 1) {
                                item {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    ) {
                                        items(availableTabs) { (key, labelRes) ->
                                            val isSelected = key == selectedDiscographyFilter
                                            if (isSelected) {
                                                Button(
                                                    onClick = { selectedDiscographyFilter = key },
                                                    shapes = ButtonDefaults.shapes(),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.primary,
                                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                                    modifier = Modifier.height(36.dp)
                                                ) {
                                                    Text(
                                                        text = stringResource(labelRes),
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.labelLarge
                                                    )
                                                }
                                            } else {
                                                FilledTonalButton(
                                                    onClick = { selectedDiscographyFilter = key },
                                                    shapes = ButtonDefaults.shapes(),
                                                    colors = ButtonDefaults.filledTonalButtonColors(
                                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                                    modifier = Modifier.height(36.dp)
                                                ) {
                                                    Text(
                                                        text = stringResource(labelRes),
                                                        fontWeight = FontWeight.Normal,
                                                        style = MaterialTheme.typography.labelLarge
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            val displayedReleases = when (selectedDiscographyFilter) {
                                "albums" -> profileViewModel.albums
                                "singles" -> profileViewModel.singles
                                "compilations" -> profileViewModel.compilations
                                else -> profileViewModel.popularReleases
                            }

                            if (displayedReleases.isNotEmpty()) {
                                item {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(displayedReleases) { playlist ->
                                            SquareCard(playlist) {
                                                val entityId = playlist.permalink ?: ""
                                                if (entityId.isNotBlank()) {
                                                    onNavigate("spotify_album:$entityId")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (profileViewModel.appearsOn.isNotEmpty()) {
                            item { Spacer(Modifier.height(16.dp)) }
                            item { SectionTitle(stringResource(R.string.spotify_appears_on)) }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(profileViewModel.appearsOn) { playlist ->
                                        SquareCard(playlist) {
                                            val entityId = playlist.permalink ?: ""
                                            if (entityId.isNotBlank()) {
                                                onNavigate("spotify_album:$entityId")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (profileViewModel.discoveredOn.isNotEmpty()) {
                            item { Spacer(Modifier.height(16.dp)) }
                            item { SectionTitle(stringResource(R.string.spotify_discovered_on)) }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(profileViewModel.discoveredOn) { playlist ->
                                        SquareCard(playlist) {
                                            val entityId = playlist.permalink ?: ""
                                            if (entityId.isNotBlank()) {
                                                onNavigate("spotify_playlist:$entityId")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (profileViewModel.similarArtists.isNotEmpty()) {
                            item { Spacer(Modifier.height(24.dp)) }
                            item { SectionTitle(stringResource(R.string.spotify_fans_also_like)) }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(profileViewModel.similarArtists) { artist ->
                                        ArtistCircle(artist) {
                                            onNavigate(artist.profileNavId)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val showBarBackground by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 300 } }
                val barColor by animateColorAsState(
                    if (showBarBackground) MaterialTheme.colorScheme.surface.copy(alpha = 0.98f) else Color.Transparent,
                    label = "bar"
                )
                val contentColor by animateColorAsState(
                    if (showBarBackground) MaterialTheme.colorScheme.onSurface else Color.White,
                    label = "content"
                )

                val isArtistSaved by DownloadManager.isArtistSavedFlow(user.id).collectAsState(initial = null)

                CenterAlignedTopAppBar(
                    title = {
                        AnimatedVisibility(visible = showBarBackground, enter = fadeIn(), exit = fadeOut()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    user.username ?: "",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                if (user.verified) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        Icons.Rounded.Verified,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBackClick,
                            shapes = IconButtonDefaults.shapes(),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (showBarBackground) Color.Transparent else Color.Black.copy(
                                    alpha = 0.3f
                                ), contentColor = contentColor
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_back))
                        }
                    },
                    actions = {
                        if (profileViewModel.isSpotifyProfile) {
                            val artistId = user.permalink ?: user.urn?.removePrefix("spotify:artist:") ?: ""
                            var isLikedState by remember(user) { mutableStateOf(prefs.isSpotifyArtistLiked(artistId)) }

                            IconButton(
                                onClick = {
                                    val spotifyId =
                                        user.permalink ?: user.urn?.removePrefix("spotify:artist:") ?: artistId
                                    val stableArtistId = user.numericId.takeIf { it != 0L }
                                        ?: kotlin.math.abs(
                                            spotifyId.hashCode().toLong() shl 16 or (spotifyId.reversed().hashCode()
                                                .toLong() and 0xFFFFL)
                                        )
                                    val targetUser = user.copy(
                                        id = stableArtistId,
                                        permalink = spotifyId,
                                        urn = "spotify:artist:$spotifyId"
                                    )
                                    DownloadManager.toggleSaveArtist(targetUser)
                                    isLikedState = !isLikedState
                                },
                                shapes = IconButtonDefaults.shapes(),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (showBarBackground) Color.Transparent else Color.Black.copy(
                                        alpha = 0.3f
                                    ),
                                    contentColor = if (isLikedState) Color(0xFF1DB954) else contentColor
                                )
                            ) {
                                Icon(
                                    if (isLikedState) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    stringResource(R.string.btn_follow)
                                )
                            }
                        } else if (profileViewModel.isCurrentUser) {
                            AnimatedVisibility(visible = showBarBackground, enter = fadeIn(), exit = fadeOut()) {
                                IconButton(onClick = { showEditSheet = true }, shapes = IconButtonDefaults.shapes()) {
                                    Icon(
                                        Icons.Outlined.Edit,
                                        stringResource(R.string.profile_edit),
                                        tint = contentColor
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onNavigate("upload") },
                                shapes = IconButtonDefaults.shapes(),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (showBarBackground) Color.Transparent else Color.Black.copy(
                                        alpha = 0.3f
                                    ),
                                    contentColor = contentColor
                                )
                            ) {
                                Icon(Icons.Filled.CloudUpload, stringResource(R.string.nav_upload))
                            }
                        } else {
                            IconButton(
                                onClick = { DownloadManager.toggleSaveArtist(user) },
                                shapes = IconButtonDefaults.shapes(),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (showBarBackground) Color.Transparent else Color.Black.copy(
                                        alpha = 0.3f
                                    ), contentColor = if (isArtistSaved != null) Color(0xFFFF4081) else contentColor
                                )
                            ) {
                                Icon(
                                    if (isArtistSaved != null) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    stringResource(R.string.btn_follow)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                val shareUrl = user.permalinkUrl ?: if (profileViewModel.isSpotifyProfile) {
                                    "https://open.spotify.com/artist/${user.permalink}"
                                } else {
                                    val cleanUsername = user.username?.replace(" ", "")?.lowercase() ?: "user"
                                    "https://soundcloud.com/$cleanUsername"
                                }
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, shareUrl)
                                    type = "text/plain"
                                }
                                context.startActivity(
                                    Intent.createChooser(
                                        sendIntent,
                                        context.getString(R.string.share_via)
                                    )
                                )
                            },
                            shapes = IconButtonDefaults.shapes(),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (showBarBackground) Color.Transparent else Color.Black.copy(
                                    alpha = 0.3f
                                ), contentColor = contentColor
                            )
                        ) {
                            Icon(Icons.Outlined.Share, stringResource(R.string.btn_share))
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = barColor,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.align(Alignment.TopCenter).zIndex(1f)
                )

                if (showEditSheet) {
                    EditProfileSheet(
                        user = user,
                        onDismiss = { showEditSheet = false },
                        profileViewModel = profileViewModel,
                        onSave = { name, bio, city ->
                            profileViewModel.updateProfile(name, bio, city, "")
                            showEditSheet = false
                        }
                    )
                }
            }

            AnimatedVisibility(
                visible = expandedSection != null,
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it },
                modifier = Modifier.fillMaxSize().zIndex(10f)
            ) {
                if (expandedSection == "discography") {
                    FullDiscographyScreen(
                        profileViewModel = profileViewModel,
                        initialFilter = selectedDiscographyFilter,
                        onBack = { expandedSection = null },
                        playerViewModel = playerViewModel,
                        onNavigate = onNavigate
                    )
                } else if (expandedSection == "comments") {
                    FullCommentListScreen(
                        comments = profileViewModel.userComments,
                        onBack = { expandedSection = null },
                        playerViewModel = playerViewModel,
                        profileViewModel = profileViewModel
                    )
                } else {
                    val (title, list) = when (expandedSection) {
                        "popular" -> stringResource(R.string.profile_tab_popular) to profileViewModel.popularTracks.toList()
                        "tracks" -> stringResource(R.string.profile_tab_tracks) to profileViewModel.allTracks.toList()
                        "reposts" -> stringResource(R.string.profile_tab_reposts) to profileViewModel.repostedTracks.toList()
                        "likes" -> stringResource(
                            R.string.profile_tab_likes,
                            user.username ?: ""
                        ) to profileViewModel.likedTracks.toList()

                        else -> "" to emptyList<Track>()
                    }

                    val contextForList = if (expandedSection == "likes") null else artistPlaybackContext

                    FullListScreen(
                        title = title,
                        tracks = list,
                        onBack = { expandedSection = null },
                        playerViewModel = playerViewModel,
                        downloadProgress = downloadProgress,
                        context = contextForList
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                IconButton(
                    onClick = onBackClick,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.padding(16.dp).align(Alignment.TopStart)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.btn_back),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Rounded.Person,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.error_generic),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { profileViewModel.loadProfile(userId, forceRefresh = true) },
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Text(stringResource(R.string.btn_retry))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreenShimmer(onBackClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(userScrollEnabled = false) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(480.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                        MaterialTheme.colorScheme.background,
                                        MaterialTheme.colorScheme.background
                                    ), startY = 0f
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ArtistAvatar(
                            avatarUrl = null, modifier = Modifier
                                .size(140.dp)
                                .clip(CircleShape)
                        )
                        Spacer(Modifier.height(20.dp))
                        ShimmerLine(Modifier.width(200.dp).height(30.dp))
                        Spacer(Modifier.height(12.dp))
                        ShimmerLine(Modifier.width(150.dp))
                    }
                }
            }
            item { SectionTitle(title = "...") }
            items(5) {
                TrackListItemShimmer()
            }
        }
        CenterAlignedTopAppBar(
            title = {},
            navigationIcon = {
                IconButton(
                    onClick = onBackClick,
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.3f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_back))
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
            modifier = Modifier.align(Alignment.TopCenter).zIndex(1f)
        )
    }
}

@Composable
fun ArtistAvatar(modifier: Modifier = Modifier, avatarUrl: String?, enableViewer: Boolean = false) {
    val isDefault = remember(avatarUrl) { avatarUrl.isDefaultAvatar() }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (!isDefault && !avatarUrl.isNullOrEmpty()) {
            val fullUrl = avatarUrl.getHighResAvatarUrl() ?: avatarUrl
            AsyncImage(
                model = fullUrl,
                contentDescription = stringResource(R.string.profile_avatar),
                contentScale = ContentScale.Crop,
                error = painterResource(R.drawable.ic_default_user_artwork_placeholder_round),
                fallback = painterResource(R.drawable.ic_default_user_artwork_placeholder_round),
                modifier = Modifier.fillMaxSize().let { if (enableViewer) it.viewableCover(fullUrl) else it }
            )
        } else {
            Image(
                painter = painterResource(R.drawable.ic_default_user_artwork_placeholder_round),
                contentDescription = stringResource(R.string.profile_avatar),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun ModernProfileHeader(
    user: User,
    isCurrentUser: Boolean,
    onEditClick: () -> Unit,
    playerViewModel: PlayerViewModel,
    onNavigate: (String) -> Unit,
    profileViewModel: ProfileViewModel,
    artistContext: PlaybackContext?
) {
    val context = LocalContext.current
    val prefs = remember(context) { com.alananasss.kittytune.data.local.PlayerPreferences(context) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(480.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Box {
                Surface(
                    shape = CircleShape,
                    shadowElevation = 12.dp,
                    color = Color.Transparent,
                    modifier = Modifier.size(140.dp)
                ) {
                    ArtistAvatar(
                        avatarUrl = user.avatarUrl,
                        enableViewer = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (isCurrentUser) {
                    Surface(
                        onClick = onEditClick,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                    ) {
                        Icon(
                            Icons.Outlined.Edit,
                            stringResource(R.string.profile_edit),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            if (profileViewModel.isSpotifyProfile && user.verified) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Icon(
                        Icons.Rounded.Verified,
                        contentDescription = stringResource(R.string.spotify_verified),
                        tint = Color(0xFF1DB954),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.spotify_verified),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF1DB954)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text(
                    text = user.username ?: stringResource(R.string.unknown_artist),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (user.verified) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Rounded.Verified,
                        contentDescription = null,
                        tint = if (profileViewModel.isSpotifyProfile) Color(0xFF1DB954) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            if (!user.city.isNullOrBlank() || !user.countryCode.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.LocationOn,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = listOfNotNull(user.city, user.countryCode).joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!profileViewModel.isSpotifyProfile) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (user.isArtist) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (user.isArtist) Icons.Rounded.MusicNote else Icons.Rounded.Person,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = if (user.isArtist) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(
                                if (user.verified) R.string.user_type_verified_artist
                                else if (user.isArtist) R.string.user_type_artist
                                else R.string.user_type_profile
                            ),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = if (user.isArtist) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            if (profileViewModel.isSpotifyProfile) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    val count = user.followersCount
                    if (count > 0) {
                        Text(
                            text = stringResource(
                                R.string.spotify_monthly_listeners,
                                NumberFormat.getNumberInstance(Locale.getDefault()).format(count)
                            ),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val rank = profileViewModel.spotifyArtist?.worldRank
                    if (rank != null) {
                        Text(
                            text = " • ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = stringResource(R.string.spotify_world_rank, rank),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "${
                            NumberFormat.getNumberInstance(Locale.US).format(user.followersCount)
                        } ${stringResource(R.string.profile_followers)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onNavigate("followers:${user.id}") }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "${
                            NumberFormat.getNumberInstance(Locale.US).format(user.followingsCount)
                        } ${stringResource(R.string.profile_followings)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onNavigate("followings:${user.id}") }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    val playlistCount =
                        if (user.playlistCount > 0) user.playlistCount else profileViewModel.playlists.size
                    if (playlistCount > 0 || user.trackCount == 0) {
                        Text(
                            text = " • ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "${
                                NumberFormat.getNumberInstance(Locale.US).format(playlistCount)
                            } ${stringResource(R.string.lib_playlists)}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    if (user.trackCount > 0) {
                        Text(
                            text = " • ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "${
                                NumberFormat.getNumberInstance(Locale.US).format(user.trackCount)
                            } ${stringResource(R.string.profile_tracks)}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val memberSinceYear = user.createdAt?.take(4)
                if (!memberSinceYear.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.profile_member_since, memberSinceYear),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            if (profileViewModel.isSpotifyProfile) {
                val artistId = user.permalink ?: user.urn?.removePrefix("spotify:artist:") ?: ""
                var isLiked by remember(user) { mutableStateOf(prefs.isSpotifyArtistLiked(artistId)) }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = {
                            if (profileViewModel.popularTracks.isNotEmpty()) {
                                playerViewModel.playPlaylist(
                                    tracks = profileViewModel.popularTracks.toList().shuffled(),
                                    startIndex = 0,
                                    context = artistContext
                                )
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Icon(Icons.Default.Shuffle, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.btn_shuffle),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            val spotifyId = user.permalink ?: user.urn?.removePrefix("spotify:artist:") ?: artistId
                            val cleanId = com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(spotifyId)
                            onNavigate("spotify_radio:$cleanId")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            Icons.Default.Radio,
                            null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.radio),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else if (isCurrentUser) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onEditClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.profile_edit), fontWeight = FontWeight.SemiBold)
                    }

                    FilledTonalButton(
                        onClick = { onNavigate("history") },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.history_title), fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                if (user.trackCount > 0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(
                            onClick = {
                                playerViewModel.playPlaylist(
                                    tracks = profileViewModel.allTracks.toList().shuffled(),
                                    startIndex = 0,
                                    context = artistContext
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp), shapes = ButtonDefaults.shapes(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Shuffle,
                                null,
                                modifier = Modifier.size(20.dp)
                            ); Spacer(Modifier.width(8.dp));
                            Text(
                                stringResource(R.string.btn_shuffle),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        FilledTonalButton(
                            onClick = { onNavigate("station_artist:${user.id}") },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp), shapes = ButtonDefaults.shapes(),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(
                                Icons.Default.Radio,
                                null,
                                modifier = Modifier.size(20.dp)
                            ); Spacer(Modifier.width(8.dp));
                            Text(
                                stringResource(R.string.radio),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileSheet(
    user: User,
    onDismiss: () -> Unit,
    profileViewModel: ProfileViewModel,
    onSave: (name: String, bio: String, city: String) -> Unit
) {
    var name by remember { mutableStateOf(user.username ?: "") }
    var bio by remember { mutableStateOf(user.description ?: "") }
    var city by remember { mutableStateOf(user.city ?: "") }
    val context = LocalContext.current

    var showCropDialog by remember { mutableStateOf(false) }
    var showBannerCropDialog by remember { mutableStateOf(false) }
    var tempBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDeleteBannerConfirm by remember { mutableStateOf(false) }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    val bitmap = if (Build.VERSION.SDK_INT < 28) {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    } else {
                        val source = ImageDecoder.createSource(context.contentResolver, uri)
                        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                            decoder.isMutableRequired = true
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                    }
                    tempBitmap = bitmap
                    showCropDialog = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    )

    val bannerPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    val bitmap = if (Build.VERSION.SDK_INT < 28) {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    } else {
                        val source = ImageDecoder.createSource(context.contentResolver, uri)
                        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                            decoder.isMutableRequired = true
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                    }
                    tempBitmap = bitmap
                    showBannerCropDialog = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.dialog_delete_avatar_title)) },
            text = { Text(stringResource(R.string.dialog_delete_avatar_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        profileViewModel.deleteAvatar(context)
                        showDeleteConfirm = false
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.btn_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (showDeleteBannerConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteBannerConfirm = false },
            title = { Text(stringResource(R.string.dialog_delete_header_title)) },
            text = { Text(stringResource(R.string.dialog_delete_header_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        profileViewModel.deleteBanner(context)
                        showDeleteBannerConfirm = false
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.btn_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteBannerConfirm = false },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    com.alananasss.kittytune.ui.common.KittyModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.profile_edit_title),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.btn_close)) }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            bannerPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                ) {
                    val bannerModel = user.bannerUrl
                    AsyncImage(
                        model = bannerModel,
                        contentDescription = stringResource(R.string.profile_banner),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().alpha(0.7f)
                    )
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Edit,
                            stringResource(R.string.profile_edit),
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    if (bannerModel != null) {
                        Surface(
                            onClick = { showDeleteBannerConfirm = true },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = stringResource(R.string.btn_delete),
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 0.dp)
                        .size(130.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .align(Alignment.Center)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(4.dp)
                            .clip(CircleShape)
                            .clickable {
                                avatarPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                    ) {
                        ArtistAvatar(
                            avatarUrl = user.avatarUrl,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.Edit,
                                stringResource(R.string.profile_edit),
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    val hasCustomAvatar = !user.avatarUrl.isDefaultAvatar()

                    if (hasCustomAvatar) {
                        Surface(
                            onClick = { showDeleteConfirm = true },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.errorContainer,
                            border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 4.dp, end = 4.dp)
                                .size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = stringResource(R.string.btn_delete),
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.profile_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text(stringResource(R.string.profile_city)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text(stringResource(R.string.profile_bio)) },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { onSave(name, bio, city) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(R.string.btn_save_changes))
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showCropDialog && tempBitmap != null) {
        AvatarCropDialog(
            bitmap = tempBitmap,
            username = name.ifBlank { "User" },
            onDismiss = {
                showCropDialog = false
                tempBitmap = null
            },
            onSave = { croppedBitmap ->
                profileViewModel.updateAvatarFromBitmap(context, croppedBitmap)
                showCropDialog = false
                tempBitmap = null
                onDismiss()
            }
        )
    }

    if (showBannerCropDialog && tempBitmap != null) {
        BannerCropDialog(
            bitmap = tempBitmap,
            onDismiss = {
                showBannerCropDialog = false
                tempBitmap = null
            },
            onSave = { croppedBitmap ->
                profileViewModel.updateBannerFromBitmap(context, croppedBitmap)
                showBannerCropDialog = false
                tempBitmap = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FullListScreen(
    title: String,
    tracks: List<Track>,
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel,
    downloadProgress: Map<Long, Int>,
    context: PlaybackContext?
) {
    val windowSizeInfo = com.alananasss.kittytune.ui.common.rememberWindowSizeInfo()

    Scaffold(
        topBar = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = if (windowSizeInfo.isTablet) Modifier.widthIn(max = 840.dp)
                        .fillMaxWidth() else Modifier.fillMaxWidth()
                ) {
                    TopAppBar(
                        title = { Text(title, fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            FilledTonalIconButton(
                                onClick = onBack,
                                shapes = IconButtonDefaults.shapes(),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_back))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(
                modifier = if (windowSizeInfo.isTablet) Modifier.widthIn(max = 840.dp)
                    .fillMaxSize() else Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 180.dp),
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { playerViewModel.playPlaylist(tracks, context = context) },
                                modifier = Modifier.weight(1f),
                                shapes = ButtonDefaults.shapes(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    null
                                ); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.btn_play))
                            }
                            FilledTonalButton(
                                onClick = { playerViewModel.playPlaylist(tracks.shuffled(), context = context) },
                                modifier = Modifier.weight(1f),
                                shapes = ButtonDefaults.shapes()
                            ) {
                                Icon(
                                    Icons.Default.Shuffle,
                                    null
                                ); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.btn_shuffle))
                            }
                        }
                    }
                    itemsIndexed(tracks) { index, track ->
                        ProfileTrackItem(track, index, playerViewModel, downloadProgress, tracks, context)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullDiscographyScreen(
    profileViewModel: ProfileViewModel,
    initialFilter: String = "popular",
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel,
    onNavigate: (String) -> Unit
) {
    val windowSizeInfo = com.alananasss.kittytune.ui.common.rememberWindowSizeInfo()
    val availableTabs = remember(
        profileViewModel.popularReleases.size,
        profileViewModel.albums.size,
        profileViewModel.singles.size,
        profileViewModel.compilations.size
    ) {
        buildList {
            if (profileViewModel.popularReleases.isNotEmpty()) add("popular" to R.string.spotify_popular_releases)
            if (profileViewModel.albums.isNotEmpty()) add("albums" to R.string.profile_tab_albums)
            if (profileViewModel.singles.isNotEmpty()) add("singles" to R.string.spotify_singles_eps)
            if (profileViewModel.compilations.isNotEmpty()) add("compilations" to R.string.spotify_compilations)
        }
    }

    var activeTabKey by remember(availableTabs) {
        mutableStateOf(
            if (availableTabs.any { it.first == initialFilter }) initialFilter else availableTabs.firstOrNull()?.first
                ?: "popular"
        )
    }

    val displayedReleases = when (activeTabKey) {
        "albums" -> profileViewModel.albums
        "singles" -> profileViewModel.singles
        "compilations" -> profileViewModel.compilations
        else -> profileViewModel.popularReleases
    }

    Scaffold(
        topBar = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = if (windowSizeInfo.isTablet) Modifier.widthIn(max = 840.dp)
                        .fillMaxWidth() else Modifier.fillMaxWidth()
                ) {
                    TopAppBar(
                        title = {
                            Text(
                                stringResource(R.string.spotify_discography),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        navigationIcon = {
                            FilledTonalIconButton(
                                onClick = onBack,
                                shapes = IconButtonDefaults.shapes(),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_back))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = if (windowSizeInfo.isTablet) Modifier.widthIn(max = 840.dp)
                    .fillMaxSize() else Modifier.fillMaxSize()
            ) {
                if (availableTabs.size > 1) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = innerPadding.calculateTopPadding())
                    ) {
                        items(availableTabs) { (key, labelRes) ->
                            val isSelected = key == activeTabKey
                            if (isSelected) {
                                Button(
                                    onClick = { activeTabKey = key },
                                    shapes = ButtonDefaults.shapes(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text(
                                        text = stringResource(labelRes),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            } else {
                                FilledTonalButton(
                                    onClick = { activeTabKey = key },
                                    shapes = ButtonDefaults.shapes(),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text(
                                        text = stringResource(labelRes),
                                        fontWeight = FontWeight.Normal,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }

                val topContentPadding = if (availableTabs.size > 1) 8.dp else innerPadding.calculateTopPadding() + 8.dp

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = topContentPadding,
                        bottom = 180.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(displayedReleases) { playlist ->
                        SquareCard(playlist) {
                            val entityId = playlist.permalink ?: ""
                            if (entityId.isNotBlank()) {
                                onNavigate("spotify_album:$entityId")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileTrackItem(
    track: Track,
    index: Int,
    playerViewModel: PlayerViewModel,
    downloadProgress: Map<Long, Int>,
    contextList: List<Track>,
    context: PlaybackContext?
) {
    val currentContext = LocalContext.current
    val progress = downloadProgress[track.id]
    val isDownloading = progress != null
    val isDownloaded = remember(track.id, downloadProgress) {
        File(currentContext.filesDir, "track_${track.id}.mp3").exists()
    }

    TrackListItem(
        track = track,
        currentlyPlayingTrack = playerViewModel.currentTrack,
        index = index,
        isDownloading = isDownloading,
        isDownloaded = isDownloaded,
        downloadProgress = progress ?: 0,
        showVerifiedBadge = false,
        onClick = {
            playerViewModel.playPlaylist(contextList, startIndex = index, context = context)
        },
        onOptionClick = { playerViewModel.showTrackOptions(track) }
    )
}

@Composable
fun SectionTitle(title: String, showMore: Boolean = false, onMoreClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        if (showMore) {
            TextButton(
                onClick = onMoreClick,
                shapes = ButtonDefaults.shapes()
            ) {
                Text(stringResource(R.string.btn_see_all), fontWeight = FontWeight.Bold)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun SquareCard(playlist: Playlist, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable { onClick() }) {
        AsyncImage(
            model = playlist.fullResArtwork,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = playlist.title ?: stringResource(R.string.generic_title),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold
        )
        val subtitleText = when {
            playlist.trackCount != null && playlist.trackCount > 0 -> {
                stringResource(R.string.playlist_num_tracks, playlist.trackCount)
            }
            !playlist.user?.username.isNullOrBlank() -> {
                playlist.user.username
            }
            !playlist.releaseDate.isNullOrBlank() -> {
                playlist.releaseDate.take(4)
            }
            else -> {
                stringResource(R.string.lib_playlists)
            }
        }
        val likesText =
            if (playlist.likesCount != null && playlist.likesCount > 0) " • ${playlist.likesCount} likes" else ""
        Text(
            text = "$subtitleText$likesText",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ArtistCircle(user: User, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier
            .width(120.dp)
            .clickable { onClick() }) {
        ArtistAvatar(
            avatarUrl = user.avatarUrl,
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
        )
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Text(
                text = user.username ?: stringResource(R.string.generic_artist),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (user.verified) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Rounded.Verified,
                    null,
                    tint = if (user.urn?.startsWith("spotify") == true) Color(0xFF1DB954) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun ExpandableDescription(
    text: String,
    onUrlClick: (String) -> Unit,
    onMentionClick: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    val urlPattern = Pattern.compile("((https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|])")
    val mentionPattern = Pattern.compile("@[\\w-]+")

    val annotatedString = buildAnnotatedString {
        val fullText = text
        append(fullText)

        val urlMatcher = urlPattern.matcher(fullText)
        while (urlMatcher.find()) {
            addStringAnnotation(
                tag = "URL",
                annotation = urlMatcher.group(),
                start = urlMatcher.start(),
                end = urlMatcher.end()
            )
            addStyle(
                style = SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                ),
                start = urlMatcher.start(),
                end = urlMatcher.end()
            )
        }

        val mentionMatcher = mentionPattern.matcher(fullText)
        while (mentionMatcher.find()) {
            addStringAnnotation(
                tag = "MENTION",
                annotation = mentionMatcher.group(),
                start = mentionMatcher.start(),
                end = mentionMatcher.end()
            )
            addStyle(
                style = SpanStyle(
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.SemiBold
                ),
                start = mentionMatcher.start(),
                end = mentionMatcher.end()
            )
        }
    }

    Column(modifier = Modifier.animateContentSize()) {
        ClickableText(
            text = annotatedString,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            ),
            maxLines = if (isExpanded) Int.MAX_VALUE else 5,
            overflow = TextOverflow.Ellipsis,
            onClick = { offset ->
                var isAnnotationClicked = false
                annotatedString.getStringAnnotations(start = offset, end = offset).firstOrNull()?.let { annotation ->
                    when (annotation.tag) {
                        "URL" -> {
                            onUrlClick(annotation.item); isAnnotationClicked = true
                        }

                        "MENTION" -> {
                            onMentionClick(annotation.item); isAnnotationClicked = true
                        }
                    }
                }
                if (!isAnnotationClicked) {
                    isExpanded = !isExpanded
                }
            }
        )

        if (text.length > 200) {
            Text(
                text = if (isExpanded) stringResource(R.string.detail_show_less) else stringResource(R.string.detail_show_more),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { isExpanded = !isExpanded }
            )
        }
    }
}

@Composable
fun UserCommentItem(
    comment: Comment,
    onTrackClick: () -> Unit
) {
    val track = comment.track ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Surface(
            onClick = onTrackClick,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = track.fullResArtwork,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(Modifier.width(8.dp))

                    Text(
                        text = stringResource(R.string.profile_comment_on_track, track.title ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (comment.trackTimestamp != null) {
                        Text(
                            text = " • " + com.alananasss.kittytune.utils.makeTimeString(comment.trackTimestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                var translatedText by remember { mutableStateOf<String?>(null) }
                var showTranslation by remember { mutableStateOf(false) }
                var isTranslating by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                Spacer(Modifier.height(12.dp))

                Text(
                    text = if (showTranslation && !translatedText.isNullOrEmpty()) translatedText!! else comment.body,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 20.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )

                val currentLocale = java.util.Locale.getDefault()
                val langName = remember(currentLocale) {
                    currentLocale.getDisplayLanguage(currentLocale)
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(currentLocale) else it.toString() }
                }
                val langCode = currentLocale.language
                val context = LocalContext.current

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
                                        listOf(comment.body), langCode
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

                Text(
                    text = com.alananasss.kittytune.ui.library.getRelativeTime(comment.createdAt, LocalContext.current),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.End).padding(top = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FullCommentListScreen(
    comments: List<Comment>,
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel,
    profileViewModel: ProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val windowSizeInfo = com.alananasss.kittytune.ui.common.rememberWindowSizeInfo()

    Scaffold(
        topBar = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = if (windowSizeInfo.isTablet) Modifier.widthIn(max = 840.dp)
                        .fillMaxWidth() else Modifier.fillMaxWidth()
                ) {
                    TopAppBar(
                        title = {
                            Text(
                                stringResource(R.string.profile_tab_comments),
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            FilledTonalIconButton(
                                onClick = onBack,
                                shapes = IconButtonDefaults.shapes(),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    stringResource(R.string.btn_back)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(
                modifier = if (windowSizeInfo.isTablet) Modifier.widthIn(max = 840.dp)
                    .fillMaxSize() else Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 180.dp),
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                ) {
                    itemsIndexed(comments) { index, comment ->
                        if (index >= comments.size - 5 && !profileViewModel.isCommentsLoadingMore) {
                            LaunchedEffect(Unit) {
                                profileViewModel.loadMoreUserComments()
                            }
                        }

                        UserCommentItem(
                            comment = comment,
                            onTrackClick = {
                                comment.track?.let { track ->
                                    if (comment.trackTimestamp != null && comment.trackTimestamp > 0) {
                                        playerViewModel.playTrackAtPosition(track, comment.trackTimestamp)
                                    } else {
                                        playerViewModel.playPlaylist(listOf(track), 0)
                                    }
                                }
                            }
                        )
                    }

                    if (profileViewModel.isCommentsLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                LoadingIndicator(
                                    modifier = Modifier.size(28.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
