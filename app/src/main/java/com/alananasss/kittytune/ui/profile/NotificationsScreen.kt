    package com.alananasss.kittytune.ui.profile
    
    import androidx.compose.foundation.background
    import androidx.compose.foundation.clickable
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.lazy.LazyColumn
    import androidx.compose.foundation.lazy.itemsIndexed
    import androidx.compose.foundation.shape.CircleShape
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.automirrored.rounded.Comment
    import androidx.compose.material.icons.rounded.*
    import androidx.compose.material3.*
    import androidx.compose.runtime.Composable
    import androidx.compose.runtime.LaunchedEffect
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.draw.clip
    import androidx.compose.ui.graphics.Color
    import androidx.compose.ui.input.nestedscroll.nestedScroll
    import androidx.compose.ui.layout.ContentScale
    import androidx.compose.ui.res.stringResource
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.text.style.TextOverflow
    import androidx.compose.ui.unit.dp
    import androidx.lifecycle.viewmodel.compose.viewModel
    import coil.compose.AsyncImage
    import com.alananasss.kittytune.R
    import com.alananasss.kittytune.domain.ActivityItem
    import com.alananasss.kittytune.ui.library.getRelativeTime
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun NotificationsScreen(
        onBackClick: () -> Unit,
        onNavigate: (String) -> Unit,
        viewModel: NotificationsViewModel = viewModel()
    ) {
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.notifications_title),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        FilledTonalIconButton(
                            onClick = onBackClick,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(Icons.Rounded.ArrowBack, stringResource(R.string.btn_back))
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
    
            if (viewModel.isLoading && viewModel.activities.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    ContainedLoadingIndicator()
                }
            } else if (viewModel.activities.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.NotificationsNone,
                            null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.no_results),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(viewModel.activities) { index, item ->
                        if (index >= viewModel.activities.size - 5) {
                            LaunchedEffect(Unit) { viewModel.loadMore() }
                        }
    
                        NotificationItemCard(item, onNavigate)
                    }

                    if (viewModel.isLoadingMore) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                LoadingIndicator(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
    
    @Composable
    fun NotificationItemCard(item: ActivityItem, onNavigate: (String) -> Unit) {
        val user = item.user ?: return
    
        // 1. Determine resource ID, Icon and Color
        val (textResId, icon, iconColor) = when (item.type) {
            "affiliation" -> Triple(
                R.string.notif_type_follow,
                Icons.Rounded.PersonAdd,
                MaterialTheme.colorScheme.primary
            )
            "track-like" -> Triple(
                R.string.notif_type_like_track,
                Icons.Rounded.Favorite,
                Color(0xFFFF4081)
            )
            "playlist-like" -> Triple(
                R.string.notif_type_like_playlist,
                Icons.Rounded.Favorite,
                Color(0xFFFF4081)
            )
            "track-repost" -> Triple(
                R.string.notif_type_repost_track,
                Icons.Rounded.Repeat,
                MaterialTheme.colorScheme.tertiary
            )
            "playlist-repost" -> Triple(
                R.string.notif_type_repost_playlist,
                Icons.Rounded.Repeat,
                MaterialTheme.colorScheme.tertiary
            )
            "mention" -> Triple(
                R.string.notif_type_mention,
                Icons.Rounded.AlternateEmail,
                MaterialTheme.colorScheme.secondary
            )
            "comment" -> Triple(
                R.string.notif_type_comment,
                Icons.AutoMirrored.Rounded.Comment,
                MaterialTheme.colorScheme.secondary
            )
            else -> Triple(
                R.string.notif_unknown,
                Icons.Rounded.Notifications,
                MaterialTheme.colorScheme.onSurface
            )
        }
    
        // 2. Prepare arguments
        val username = user.username ?: ""
        val targetName = item.track?.title ?: item.playlist?.title ?: ""
    
        // 3. Call stringResource with correct arguments based on type
        val formattedText = when (item.type) {
            "affiliation", "mention" -> stringResource(textResId, username)
            "track-like", "playlist-like", "track-repost", "playlist-repost", "comment" -> stringResource(textResId, username, targetName)
            else -> stringResource(textResId) // Default case (no args)
        }
    
        val targetImage = item.track?.fullResArtwork ?: item.playlist?.fullResArtwork
        val timeString = getRelativeTime(item.createdAt, androidx.compose.ui.platform.LocalContext.current)
    
        val onClick = {
            when {
                item.track != null -> onNavigate("track_detail/${item.track.id}")
                item.playlist != null -> onNavigate("playlist_detail/${item.playlist.id}")
                else -> onNavigate("profile:${user.id}")
            }
        }
    
        Card(
            onClick = onClick,
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar with Badge
                Box(modifier = Modifier.size(52.dp)) {
                    ArtistAvatar(
                        avatarUrl = user.avatarUrl,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .align(Alignment.Center)
                    )
    
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(20.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shadowElevation = 0.dp
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(2.dp)
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconColor,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
    
                Spacer(Modifier.width(16.dp))
    
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formattedText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
    
                if (targetImage != null) {
                    Spacer(Modifier.width(12.dp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        AsyncImage(
                            model = targetImage,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                }
            }
        }
    }


