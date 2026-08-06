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
    import androidx.compose.foundation.lazy.items
    import androidx.compose.foundation.lazy.itemsIndexed
    import androidx.compose.foundation.lazy.rememberLazyListState
    import androidx.compose.foundation.shape.CircleShape
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.foundation.text.ClickableText
    import androidx.compose.foundation.text.KeyboardOptions
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.automirrored.filled.ArrowBack
    import androidx.compose.material.icons.automirrored.filled.ArrowForward
    import androidx.compose.material.icons.filled.*
    import androidx.compose.material.icons.outlined.Delete
    import androidx.compose.material.icons.outlined.Edit
    import androidx.compose.material.icons.outlined.FavoriteBorder
    import androidx.compose.material.icons.outlined.LocationOn
    import androidx.compose.material.icons.outlined.Share
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
        var showEditSheet by remember { mutableStateOf(false) }
        val uriHandler = LocalUriHandler.current
        val context = LocalContext.current
    
        LaunchedEffect(userId) {
            val id = userId.toLongOrNull()
            if (id != null) profileViewModel.loadProfile(id)
        }
    
        val user = profileViewModel.user
    
        // handle back press if section expanded
        BackHandler(enabled = expandedSection != null) {
            expandedSection = null
        }
    
        // create playback context
        val artistText = stringResource(R.string.generic_artist)
        val artistPlaybackContext = remember(user, artistText) {
            user?.let {
                PlaybackContext(
                    displayText = "$artistText • ${it.username}",
                    navigationId = "profile:${it.id}",
                    imageUrl = it.avatarUrl,
                    artistName = it.username,
                    isVerified = it.verified
                )
            }
        }
    
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (profileViewModel.isLoading && user == null) {
                ProfileScreenShimmer(onBackClick)
            } else if (user != null) {
                // overlay for expanded sections
                AnimatedVisibility(
                    visible = expandedSection != null,
                    enter = slideInHorizontally { it },
                    exit = slideOutHorizontally { it },
                    modifier = Modifier.zIndex(2f)
                ) {
                    if (expandedSection == "comments") {
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
                            "likes" -> stringResource(R.string.profile_tab_likes, user.username ?: "") to profileViewModel.likedTracks.toList()
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
    
                    // bio section
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
                        item { SectionTitle(title = stringResource(R.string.profile_tab_popular), showMore = profileViewModel.popularTracks.size > 5, onMoreClick = { expandedSection = "popular" }) }
                        itemsIndexed(profileViewModel.popularTracks.take(5)) { index, track ->
                            ProfileTrackItem(track, index, playerViewModel, downloadProgress, profileViewModel.popularTracks, artistPlaybackContext)
                        }
                    }
    
                    if (profileViewModel.allTracks.isNotEmpty()) {
                        item { SectionTitle(title = stringResource(R.string.profile_latest_tracks), showMore = true, onMoreClick = { expandedSection = "tracks" }) }
                        itemsIndexed(profileViewModel.allTracks.take(5)) { index, track ->
                            ProfileTrackItem(track, index, playerViewModel, downloadProgress, profileViewModel.allTracks, artistPlaybackContext)
                        }
                    }
    
                    if (profileViewModel.albums.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.profile_tab_albums)) }
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                items(profileViewModel.albums) { playlist -> SquareCard(playlist) { onNavigate(playlist.id.toString()) } }
                            }
                        }
                    }
    
                    if (profileViewModel.playlists.isNotEmpty()) {
                        item {
                            val name = user.username ?: stringResource(R.string.generic_artist)
                            SectionTitle(stringResource(R.string.profile_playlists_by_user, name))
                        }
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                items(profileViewModel.playlists) { playlist -> SquareCard(playlist) { onNavigate(playlist.id.toString()) } }
                            }
                        }
                    }
    
                    if (profileViewModel.likedTracks.isNotEmpty()) {
                        item {
                            val name = user.username ?: stringResource(R.string.generic_artist)
                            SectionTitle(title = stringResource(R.string.profile_likes_by_user, name), showMore = true, onMoreClick = { expandedSection = "likes" })
                        }
                        itemsIndexed(profileViewModel.likedTracks.take(3)) { index, track ->
                            ProfileTrackItem(track, index, playerViewModel, downloadProgress, profileViewModel.likedTracks, null)
                        }
                    }
    
                    if (profileViewModel.repostedTracks.isNotEmpty()) {
                        item {
                            SectionTitle(title = stringResource(R.string.profile_tab_reposts), showMore = true, onMoreClick = { expandedSection = "reposts" })
                        }
                        itemsIndexed(profileViewModel.repostedTracks.take(5)) { index, track ->
                            ProfileTrackItem(track, index, playerViewModel, downloadProgress, profileViewModel.repostedTracks, artistPlaybackContext)
                        }
                    }
                    if (profileViewModel.userComments.isNotEmpty()) {
                        item {
                            SectionTitle(
                                title = stringResource(R.string.profile_tab_comments),
                                showMore = profileViewModel.userComments.size > 3, // Affiche "Voir tout" si > 3
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
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                items(profileViewModel.similarArtists) { artist -> ArtistCircle(artist) { onNavigate("profile:${artist.id}") } }
                            }
                        }
                    }
                }
    
                // dynamic app bar logic
                val showBarBackground by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 300 } }
                val barColor by animateColorAsState(if (showBarBackground) MaterialTheme.colorScheme.surface.copy(alpha = 0.98f) else Color.Transparent, label = "bar")
                val contentColor by animateColorAsState(if (showBarBackground) MaterialTheme.colorScheme.onSurface else Color.White, label = "content")
    
                val isArtistSaved by DownloadManager.isArtistSavedFlow(user.id).collectAsState(initial = null)
    
                CenterAlignedTopAppBar(
                    title = {
                        AnimatedVisibility(visible = showBarBackground, enter = fadeIn(), exit = fadeOut()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(user.username ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                if (user.verified) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBackClick,
                            shapes = IconButtonDefaults.shapes(),
                            colors = IconButtonDefaults.iconButtonColors(containerColor = if (showBarBackground) Color.Transparent else Color.Black.copy(alpha = 0.3f), contentColor = contentColor)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_back))
                        }
                    },
                    actions = {
                        if (profileViewModel.isCurrentUser) {
                            AnimatedVisibility(visible = showBarBackground, enter = fadeIn(), exit = fadeOut()) {
                                IconButton(onClick = { showEditSheet = true }, shapes = IconButtonDefaults.shapes()) {
                                    Icon(Icons.Outlined.Edit, stringResource(R.string.profile_edit), tint = contentColor)
                                }
                            }
                        } else {
                            IconButton(
                                onClick = { DownloadManager.toggleSaveArtist(user) },
                                shapes = IconButtonDefaults.shapes(),
                                colors = IconButtonDefaults.iconButtonColors(containerColor = if (showBarBackground) Color.Transparent else Color.Black.copy(alpha = 0.3f), contentColor = if(isArtistSaved != null) Color(0xFFFF4081) else contentColor)
                            ) {
                                Icon(if (isArtistSaved != null) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, stringResource(R.string.btn_follow))
                            }
                        }
    
                        IconButton(
                            onClick = {
                                val cleanUsername = user.username?.replace(" ", "")?.lowercase() ?: "user"
                                val shareUrl = user.permalinkUrl ?: "https://soundcloud.com/$cleanUsername"
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, shareUrl)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.share_via)))
                            },
                            shapes = IconButtonDefaults.shapes(),
                            colors = IconButtonDefaults.iconButtonColors(containerColor = if (showBarBackground) Color.Transparent else Color.Black.copy(alpha = 0.3f), contentColor = contentColor)
                        ) {
                            Icon(Icons.Outlined.Share, stringResource(R.string.btn_share))
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = barColor, titleContentColor = MaterialTheme.colorScheme.onSurface, actionIconContentColor = MaterialTheme.colorScheme.onSurface),
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
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ProfileScreenShimmer(onBackClick: () -> Unit) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(userScrollEnabled = false) {
                item {
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .height(480.dp)) {
                        Box(modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant))
                        Box(modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.5f), MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background), startY = 0f)))
    
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 24.dp)
                                .padding(bottom = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            ArtistAvatar(avatarUrl = null, modifier = Modifier
                                .size(140.dp)
                                .clip(CircleShape))
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
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.3f), contentColor = Color.White)
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
    fun ArtistAvatar(modifier: Modifier = Modifier, avatarUrl: String?, enableViewer: Boolean = true) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!avatarUrl.isNullOrEmpty()) {
                val fullUrl = avatarUrl.replace("large", "t500x500")
                AsyncImage(
                    model = fullUrl,
                    contentDescription = stringResource(R.string.profile_avatar),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().let { if (enableViewer) it.viewableCover(fullUrl) else it }
                )
            } else {
                Icon(
                    Icons.Default.Person,
                    contentDescription = stringResource(R.string.profile_avatar),
                    modifier = Modifier.fillMaxSize(0.6f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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
        Box(modifier = Modifier
            .fillMaxWidth()
            .height(480.dp)) {
            val bgModel = user.bannerUrl ?: user.avatarUrl
            AsyncImage(model = bgModel, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier
                .fillMaxSize()
                .blur(60.dp)
                .alpha(0.6f))
            Box(modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.5f), MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background), startY = 0f)))
    
            Column(modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    
                Box {
                    Surface(shape = CircleShape, shadowElevation = 12.dp, color = Color.Transparent, modifier = Modifier.size(140.dp)) {
                        ArtistAvatar(
                            avatarUrl = user.avatarUrl,
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
                            Icon(Icons.Outlined.Edit, stringResource(R.string.profile_edit), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier
                                .padding(8.dp)
                                .size(20.dp))
                        }
                    }
                }
    
                Spacer(Modifier.height(20.dp))
    
                // verified icon row
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text(text = user.username ?: stringResource(R.string.unknown_artist), style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground)
                    if (user.verified) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    }
                }
    
                if (!user.city.isNullOrBlank() || !user.countryCode.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LocationOn, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(text = listOfNotNull(user.city, user.countryCode).joinToString(", "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
    
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text(
                        text = "${NumberFormat.getNumberInstance(Locale.US).format(user.followersCount)} ${stringResource(R.string.profile_followers)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onNavigate("followers:${user.id}") }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    Text(text = " • ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Text(
                        text = "${NumberFormat.getNumberInstance(Locale.US).format(user.followingsCount)} ${stringResource(R.string.profile_followings)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onNavigate("followings:${user.id}") }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    Text(text = " • ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Text(text = "${NumberFormat.getNumberInstance(Locale.US).format(user.trackCount)} ${stringResource(R.string.profile_tracks)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
    
                Spacer(Modifier.height(32.dp))
    
                if (isCurrentUser) {
                    Button(
                        onClick = onEditClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    ) {
                        Text(stringResource(R.string.profile_edit), fontWeight = FontWeight.SemiBold)
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
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                            ) {
                                Icon(Icons.Default.Shuffle, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp));
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
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                            ) {
                                Icon(Icons.Default.Radio, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp));
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
                    } catch(e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        )
    
        // banner picker
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
                    } catch(e: Exception) { e.printStackTrace() }
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
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.btn_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
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
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.btn_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteBannerConfirm = false }) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                }
            )
        }
    
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = null
        ) {
            Column(modifier = Modifier
                .fillMaxWidth()
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
                    Text(stringResource(R.string.profile_edit_title), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.btn_close)) }
                }
    
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)) {
    
                    // banner area
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
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Edit, stringResource(R.string.profile_edit), tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(32.dp))
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
    
                    // avatar container
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
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Edit, stringResource(R.string.profile_edit), tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                        }
    
                        val hasCustomAvatar = user.avatarUrl != null && !user.avatarUrl.contains("default_avatar")
    
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
                        shape = RoundedCornerShape(50),
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
        Scaffold(
            topBar = {
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
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
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
                            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.btn_play))
                        }
                        FilledTonalButton(
                            onClick = { playerViewModel.playPlaylist(tracks.shuffled(), context = context) },
                            modifier = Modifier.weight(1f),
                            shapes = ButtonDefaults.shapes()
                        ) {
                            Icon(Icons.Default.Shuffle, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.btn_shuffle))
                        }
                    }
                }
                itemsIndexed(tracks) { index, track ->
                    ProfileTrackItem(track, index, playerViewModel, downloadProgress, tracks, context)
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
            showVerifiedBadge = false, // hide checkmark in profile lists
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
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.btn_see_all), fontWeight = FontWeight.Bold)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
    
    @Composable
    fun SquareCard(playlist: Playlist, onClick: () -> Unit) {
        Column(modifier = Modifier
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
            val tracksText = stringResource(R.string.playlist_num_tracks, playlist.trackCount ?: 0)
            val likesText = if (playlist.likesCount != null && playlist.likesCount > 0) " • ${playlist.likesCount} likes" else ""
            Text(
                text = "$tracksText$likesText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    
    @Composable
    fun ArtistCircle(user: User, onClick: () -> Unit) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier
            .width(120.dp)
            .clickable { onClick() }) {
            ArtistAvatar(
                avatarUrl = user.avatarUrl,
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = user.username ?: stringResource(R.string.generic_artist), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                if (user.verified) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
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
                            "URL" -> { onUrlClick(annotation.item); isAnnotationClicked = true }
                            "MENTION" -> { onMentionClick(annotation.item); isAnnotationClicked = true }
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
                .padding(horizontal = 16.dp, vertical = 6.dp) // Espacement externe
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
                        currentLocale.getDisplayLanguage(currentLocale).replaceFirstChar { if (it.isLowerCase()) it.titlecase(currentLocale) else it.toString() }
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
                                        val res = com.alananasss.kittytune.data.network.FreeTranslator.translateMissing(listOf(comment.body), langCode)
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
                            text = if (showTranslation) stringResource(R.string.comment_see_original) else stringResource(R.string.comment_translate, langName),
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
        Scaffold(
            topBar = {
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
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
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


