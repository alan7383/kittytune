    package com.alananasss.kittytune.ui.library
    
    import android.content.Context
    import android.net.ConnectivityManager
    import android.net.Network
    import android.net.NetworkCapabilities
    import android.net.NetworkRequest
    import androidx.compose.foundation.background
    import androidx.compose.foundation.clickable
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.lazy.grid.GridCells
    import androidx.compose.foundation.lazy.grid.GridItemSpan
    import androidx.compose.foundation.lazy.grid.LazyGridState
    import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
    import androidx.compose.foundation.lazy.grid.items
    import androidx.compose.foundation.lazy.grid.rememberLazyGridState
    import androidx.compose.foundation.shape.CircleShape
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.filled.GridView
    import androidx.compose.material.icons.filled.SdStorage
    import androidx.compose.material.icons.filled.Search
    import androidx.compose.material.icons.filled.ViewList
    import androidx.compose.material.icons.outlined.ExitToApp
    import androidx.compose.material.icons.rounded.*
    import androidx.compose.material3.*
    import androidx.compose.runtime.*
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.draw.clip
    import androidx.compose.ui.graphics.Color
    import androidx.compose.ui.graphics.vector.ImageVector
    import androidx.compose.ui.layout.ContentScale
    import androidx.compose.ui.platform.LocalContext
    import androidx.compose.ui.res.stringResource
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.text.style.TextOverflow
    import androidx.compose.ui.unit.dp
    import androidx.lifecycle.Lifecycle
    import androidx.lifecycle.LifecycleEventObserver
    import androidx.lifecycle.compose.LocalLifecycleOwner
    import androidx.lifecycle.viewmodel.compose.viewModel
    import coil.compose.AsyncImage
import kotlinx.coroutines.launch

    import com.alananasss.kittytune.R
    import com.alananasss.kittytune.data.DownloadManager
    import com.alananasss.kittytune.data.TokenManager
    import com.alananasss.kittytune.data.local.LocalArtist
    import com.alananasss.kittytune.domain.Playlist
    import com.alananasss.kittytune.domain.User
    import com.alananasss.kittytune.ui.common.SquareCardShimmer
    import com.alananasss.kittytune.ui.player.PlayerViewModel
    import com.alananasss.kittytune.ui.profile.ArtistAvatar
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun LibraryScreen(
        onLoginClick: () -> Unit,
        onPlaylistClick: (String) -> Unit,
        onLikedTracksClick: () -> Unit,
        onProfileClick: () -> Unit,
        playerViewModel: PlayerViewModel,
        libraryViewModel: LibraryViewModel = viewModel()
    ) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
    
        val listState = rememberLazyGridState()
        // collapse fab text when scrolling
        val fabExpanded by remember {
            derivedStateOf {
                listState.firstVisibleItemIndex == 0
            }
        }
    
        // keep an eye on network status
        DisposableEffect(context) {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (libraryViewModel.isOfflineMode || libraryViewModel.userProfile?.id == 0L) {
                        libraryViewModel.loadData(forceRefresh = true)
                    }
                }
                override fun onLost(network: Network) {
                    libraryViewModel.isOfflineMode = true
                }
            }
            val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
            try { connectivityManager.registerNetworkCallback(request, networkCallback) } catch (e: Exception) { e.printStackTrace() }
            onDispose { try { connectivityManager.unregisterNetworkCallback(networkCallback) } catch (e: Exception) {} }
        }
    
        LaunchedEffect(Unit) {
            libraryViewModel.loadData()
        }

        LaunchedEffect(libraryViewModel.selectedFilter) {
            listState.scrollToItem(0)
        }
    
        // refresh when coming back to the screen
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    libraryViewModel.loadData(forceRefresh = false)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    
        var showCreateDialog by remember { mutableStateOf(false) }
        var newPlaylistName by remember { mutableStateOf("") }
        var isCreatingPlaylist by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
    
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { if (!isCreatingPlaylist) showCreateDialog = false },
                title = { Text(stringResource(R.string.lib_create_playlist_title)) },
                text = {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text(stringResource(R.string.lib_create_playlist_hint)) },
                        singleLine = true,
                        enabled = !isCreatingPlaylist
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newPlaylistName.isNotBlank() && !isCreatingPlaylist) {
                                val playlistName = newPlaylistName
                                isCreatingPlaylist = true
                                scope.launch {
                                    val id = DownloadManager.createUserPlaylist(playlistName)
                                    if (id > 0) {
                                        val api = com.alananasss.kittytune.data.network.RetrofitClient.create(context)
                                        for (i in 0..15) {
                                            try {
                                                api.getPlaylist(id)
                                                break
                                            } catch (e: Exception) {
                                                kotlinx.coroutines.delay(1000)
                                            }
                                        }
                                    }
                                    isCreatingPlaylist = false
                                    showCreateDialog = false
                                    newPlaylistName = ""
                                    val navId = if (id < 0) "local_playlist:$id" else id.toString()
                                    onPlaylistClick(navId)
                                }
                            }
                        },
                        enabled = !isCreatingPlaylist
                    ) {
                        if (isCreatingPlaylist) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(R.string.btn_create))
                        }
                    }
                },
                dismissButton = { 
                    TextButton(
                        onClick = { showCreateDialog = false },
                        enabled = !isCreatingPlaylist
                    ) { 
                        Text(stringResource(R.string.btn_cancel)) 
                    } 
                }
            )
        }
        val showLogin = libraryViewModel.userProfile == null && !libraryViewModel.isLoading && !libraryViewModel.isOfflineMode
        val isGuest = libraryViewModel.isGuestUser
    
        Scaffold(
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .statusBarsPadding()
                        .padding(top = 16.dp, bottom = 8.dp)
                ) {
                    // offline banner
                    if (libraryViewModel.isOfflineMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Rounded.WifiOff, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.lib_offline_mode), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
    
                    // guest banner
                    if (isGuest && !libraryViewModel.isOfflineMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable { onLoginClick() }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.lib_guest_mode), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
    
                    SearchBarHeader(
                        query = libraryViewModel.searchQuery,
                        onQueryChange = { libraryViewModel.searchQuery = it },
                        avatarUrl = libraryViewModel.userProfile?.avatarUrl,
                        onProfileClick = onProfileClick,
                        isGuest = isGuest
                    )
    
                    FilterChipsRow(libraryViewModel)
                }
            },
            floatingActionButton = {
                if (!showLogin || isGuest) {
                    // bump up fab so bottom nav and miniplayer don't cover it
                    val bottomNavHeight = 90.dp
                    val miniPlayerHeight = if (playerViewModel.currentTrack != null) 72.dp else 0.dp
                    val totalBottomPadding = bottomNavHeight + miniPlayerHeight
    
                    ExtendedFloatingActionButton(
                        onClick = { showCreateDialog = true },
                        icon = { Icon(Icons.Rounded.Add, stringResource(R.string.lib_create_playlist_title)) },
                        text = { Text(stringResource(R.string.lib_create_playlist_title)) },
                        expanded = fabExpanded,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(bottom = totalBottomPadding)
                    )
                }
            }
        ) { innerPadding ->
            if (showLogin && !isGuest) {
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onLoginClick) { Text(stringResource(R.string.login_soundcloud)) }
                    }
                }
            } else {
                Column(modifier = Modifier.padding(innerPadding)) {
                    SortAndLayoutControls(libraryViewModel)
    
                    if (libraryViewModel.isLoading && libraryViewModel.displayedItems.isEmpty()) {
                        LibraryShimmerGrid(isGridLayout = libraryViewModel.isGridLayout)
                    } else {
                        LibraryContentGrid(
                            listState = listState,
                            viewModel = libraryViewModel,
                            onLikedTracksClick = onLikedTracksClick,
                            onPlaylistClick = onPlaylistClick,
                            onArtistClick = { artistId -> onPlaylistClick("profile:$artistId") },
                            isGuest = isGuest
                        )
                    }
                }
            }
        }
    }
    
    @Composable
    fun LibraryContentGrid(
        listState: LazyGridState,
        viewModel: LibraryViewModel,
        onLikedTracksClick: () -> Unit,
        onPlaylistClick: (String) -> Unit,
        onArtistClick: (Long) -> Unit,
        isGuest: Boolean
    ) {
        val columns = if (viewModel.isGridLayout) GridCells.Fixed(2) else GridCells.Fixed(1)
        val isSyncing by viewModel.isSyncing.collectAsState()
    
        // grab strings here before using them in logic
        val playlistsFilter = stringResource(R.string.lib_playlists)
        val shouldShowPlaylists = viewModel.selectedFilter == null || viewModel.selectedFilter == playlistsFilter
    
        LazyVerticalGrid(
            state = listState,
            columns = columns,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 180.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (shouldShowPlaylists) {
                item(span = { GridItemSpan(1) }, key = "liked_tracks") {
                    Box(modifier = Modifier.animateItem()) {
                        val subtitle = if (isGuest) stringResource(R.string.lib_liked_subtitle_local)
                        else if(isSyncing) stringResource(R.string.lib_liked_subtitle_syncing)
                        else stringResource(R.string.lib_liked_subtitle)
                        StaticLibraryCard(
                            title = stringResource(R.string.lib_liked_tracks),
                            subtitle = subtitle,
                            icon = Icons.Rounded.Favorite,
                            isGrid = viewModel.isGridLayout,
                            onClick = onLikedTracksClick,
                            isLoading = isSyncing
                        )
                    }
                }
    
                item(span = { GridItemSpan(1) }, key = "downloads") {
                    Box(modifier = Modifier.animateItem()) {
                        StaticLibraryCard(
                            title = stringResource(R.string.lib_downloads),
                            subtitle = stringResource(R.string.lib_downloads_subtitle),
                            icon = Icons.Rounded.Folder,
                            isGrid = viewModel.isGridLayout,
                            onClick = { onPlaylistClick("downloads") },
                            isLoading = false
                        )
                    }
                }
    
                if (viewModel.showLocalMedia) {
                    item(span = { GridItemSpan(1) }, key = "local_media") {
                        Box(modifier = Modifier.animateItem()) {
                            StaticLibraryCard(
                                title = stringResource(R.string.lib_local_media),
                                subtitle = stringResource(R.string.lib_local_media_subtitle),
                                icon = Icons.Default.SdStorage,
                                isGrid = viewModel.isGridLayout,
                                onClick = { onPlaylistClick("local_files") },
                                isLoading = false
                            )
                        }
                    }
                }
            }
    
            items(
                items = viewModel.displayedItems,
                key = { item ->
                    when (item) {
                        is LibraryItem.PlaylistItem -> "playlist_${item.playlist.id}_${item.playlist.permalinkUrl ?: ""}"
                        is LibraryItem.ArtistItem -> "artist_${item.artist.id}"
                    }
                }
            ) { item ->
                Box(modifier = Modifier.animateItem()) {
                    when (item) {
                        is LibraryItem.PlaylistItem -> {
                            val permalink = item.playlist.permalinkUrl
                            val isYoutubeShortcut = permalink != null && permalink.startsWith("yt_radio:")
        
                            val navId = if (isYoutubeShortcut) {
                                android.net.Uri.encode(permalink!!)
                            } else if (item.playlist.urn?.startsWith("soundcloud:system-playlists:") == true) {
                                "system_playlist:${item.playlist.urn}"
                            } else {
                                if (item.playlist.id < 0) "local_playlist:${item.playlist.id}" else item.playlist.id.toString()
                            }
        
                            DynamicPlaylistCard(
                                playlist = item.playlist,
                                isGrid = viewModel.isGridLayout,
                                onClick = { onPlaylistClick(navId) }
                            )
                        }
                        is LibraryItem.ArtistItem -> {
                            ArtistLibraryCard(
                                artist = item.artist,
                                isGrid = viewModel.isGridLayout,
                                onClick = { onArtistClick(item.artist.id) }
                            )
                        }
                    }
                }
            }
        }
    }
    
    @Composable
    fun LibraryShimmerGrid(isGridLayout: Boolean) {
        val columns = if (isGridLayout) GridCells.Fixed(2) else GridCells.Fixed(1)
        LazyVerticalGrid(
            columns = columns,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 180.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false
        ) {
            items(10) {
                SquareCardShimmer()
            }
        }
    }
    
    @Composable
    fun SearchBarHeader(
        query: String,
        onQueryChange: (String) -> Unit,
        avatarUrl: String?,
        onProfileClick: () -> Unit,
        isGuest: Boolean
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text(stringResource(R.string.search_library_hint)) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_library_hint))
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                    Box(modifier = Modifier
                        .padding(end = 8.dp)
                        .clip(CircleShape)
                        .clickable { onProfileClick() }
                    ) {
                        ArtistAvatar(
                            avatarUrl = if (isGuest) null else avatarUrl,
                            modifier = Modifier.size(32.dp).clip(CircleShape)
                        )
                    }
                }
            },
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            singleLine = true
        )
    }
    
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun FilterChipsRow(viewModel: LibraryViewModel) {
        val playlistsLabel = stringResource(R.string.lib_playlists)
        val albumsLabel = stringResource(R.string.lib_albums)
        val artistsLabel = stringResource(R.string.lib_artists)
        val stationsLabel = stringResource(R.string.lib_stations)
    
        val filters = remember(playlistsLabel, albumsLabel, artistsLabel, stationsLabel) {
            listOf(playlistsLabel, albumsLabel, artistsLabel, stationsLabel)
        }
    
        val view = androidx.compose.ui.platform.LocalView.current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEach { label ->
                val isSelected = viewModel.selectedFilter == label
                
                val containerColor by androidx.compose.animation.animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                    label = "chip_container_color"
                )
                val contentColor by androidx.compose.animation.animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "chip_content_color"
                )

                Button(
                    onClick = { 
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        viewModel.selectedFilter = if (isSelected) null else label 
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = containerColor,
                        contentColor = contentColor
                    )
                ) {
                    Text(label)
                }
            }
        }
    }
    
    @Composable
    fun SortAndLayoutControls(viewModel: LibraryViewModel) {
        val playlistsLabel = stringResource(R.string.lib_playlists)
        val albumsLabel = stringResource(R.string.lib_albums)
        val shouldShowOwnershipFilter = viewModel.selectedFilter == null || viewModel.selectedFilter == playlistsLabel || viewModel.selectedFilter == albumsLabel

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (shouldShowOwnershipFilter) {
                val filterText = when (viewModel.ownershipFilter) {
                    OwnershipFilter.ALL -> stringResource(R.string.filter_all)
                    OwnershipFilter.CREATED -> stringResource(R.string.filter_created)
                    OwnershipFilter.LIKED -> stringResource(R.string.filter_liked)
                }
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                        viewModel.ownershipFilter = when (viewModel.ownershipFilter) {
                            OwnershipFilter.ALL -> OwnershipFilter.CREATED
                            OwnershipFilter.CREATED -> OwnershipFilter.LIKED
                            OwnershipFilter.LIKED -> OwnershipFilter.ALL
                        }
                    }.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = filterText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Rounded.FilterList,
                        contentDescription = filterText, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { viewModel.isSortDescending = !viewModel.isSortDescending }.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.sort_date_added), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (viewModel.isSortDescending) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                        contentDescription = stringResource(R.string.sort_date_added), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = { viewModel.isGridLayout = !viewModel.isGridLayout }) {
                Icon(
                    imageVector = if (viewModel.isGridLayout) Icons.Default.ViewList else Icons.Default.GridView,
                    contentDescription = stringResource(R.string.btn_options), tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    
    @Composable
    fun StaticLibraryCard(title: String, subtitle: String, icon: ImageVector, isGrid: Boolean, isLoading: Boolean, onClick: () -> Unit) {
        val height = if (isGrid) 160.dp else 80.dp
        Card(
            onClick = onClick, shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.height(height).fillMaxWidth()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isGrid) {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Row(modifier = Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                // fix applied here
                if (isLoading) {
                    Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                        LinearWavyProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.Transparent
                        )
                    }
                }
            }
        }
    }
    
    @Composable
    fun DynamicPlaylistCard(playlist: Playlist, isGrid: Boolean, onClick: () -> Unit) {
        val art = playlist.fullResArtwork
        val isRadioShortcut = playlist.permalinkUrl?.startsWith("yt_radio:") == true
        val subtitleText = if (isRadioShortcut) {
            stringResource(R.string.radio)
        } else {
            stringResource(R.string.playlist_num_tracks, playlist.trackCount ?: 0)
        }
    
        val authorText = playlist.user?.username ?: stringResource(R.string.me_artist)
        val likesText = if (playlist.likesCount != null && playlist.likesCount > 0) " • ${playlist.likesCount} likes" else ""
        val finalSubtitle = if (isRadioShortcut) "$subtitleText • YouTube" else "$subtitleText • $authorText$likesText"
    
        if (isGrid) {
            Column(modifier = Modifier.clickable(onClick = onClick).fillMaxWidth()) {
                AsyncImage(
                    model = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(modifier = Modifier.height(8.dp))
    
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = playlist.title ?: stringResource(R.string.app_name),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (playlist.sharing == "private" || playlist.sharing == "secret") {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Rounded.Lock,
                            contentDescription = "Private",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = finalSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = playlist.title ?: stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                        if (playlist.sharing == "private" || playlist.sharing == "secret") {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Rounded.Lock,
                                contentDescription = "Private",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = finalSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    
    @Composable
    fun ArtistLibraryCard(artist: LocalArtist, isGrid: Boolean, onClick: () -> Unit) {
        if (isGrid) {
            Column(modifier = Modifier.clickable(onClick = onClick).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                ArtistAvatar(
                    avatarUrl = artist.avatarUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = artist.username, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = stringResource(R.string.menu_go_artist), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ArtistAvatar(
                    avatarUrl = artist.avatarUrl,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = artist.username, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(text = stringResource(R.string.menu_go_artist) + " • " + stringResource(R.string.playlist_num_tracks, artist.trackCount), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }


