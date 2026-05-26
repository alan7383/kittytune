    package com.alananasss.kittytune.ui.home
    
    import android.content.Intent
    import androidx.compose.animation.animateColorAsState
    import androidx.compose.animation.core.tween
    import androidx.compose.foundation.BorderStroke
    import androidx.compose.foundation.background
    import androidx.compose.foundation.clickable
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.lazy.LazyColumn
    import androidx.compose.foundation.lazy.LazyRow
    import androidx.compose.foundation.lazy.items
    import androidx.compose.foundation.lazy.itemsIndexed
    import androidx.compose.foundation.shape.CircleShape
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.automirrored.filled.ArrowBack
    import androidx.compose.material.icons.filled.MoreVert
    import androidx.compose.material.icons.filled.Person
    import androidx.compose.material.icons.filled.Radio
    import androidx.compose.material.icons.filled.Shuffle
    import androidx.compose.material.icons.outlined.Share
    import androidx.compose.material.icons.rounded.Check
    import androidx.compose.material.icons.rounded.KeyboardArrowDown
    import androidx.compose.material.icons.rounded.Verified
    import androidx.compose.material3.*
    import androidx.compose.runtime.*
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.draw.clip
    import androidx.compose.ui.graphics.Brush
    import androidx.compose.ui.graphics.Color
    import androidx.compose.ui.graphics.vector.ImageVector
    import androidx.compose.ui.input.nestedscroll.nestedScroll
    import androidx.compose.ui.layout.ContentScale
    import androidx.compose.ui.platform.LocalContext
    import androidx.compose.ui.res.stringResource
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.text.style.TextOverflow
    import androidx.compose.ui.unit.dp
    import androidx.compose.ui.unit.sp
    import androidx.lifecycle.viewmodel.compose.viewModel
    import androidx.palette.graphics.Palette
    import coil.compose.AsyncImage
    import coil.imageLoader
    import coil.request.ImageRequest
    import com.alananasss.kittytune.R
    import com.alananasss.kittytune.data.ChartsData
    import com.alananasss.kittytune.domain.User
    import com.alananasss.kittytune.ui.common.SquareCardShimmer
    import com.alananasss.kittytune.ui.player.PlayerViewModel
    import com.alananasss.kittytune.ui.profile.ArtistAvatar
    import java.util.Locale

    @Composable
    fun rememberDominantColor(url: String?, defaultColor: Color = MaterialTheme.colorScheme.surface): State<Color> {
        val context = LocalContext.current
        val color = remember(url) { mutableStateOf(defaultColor) }
    
        LaunchedEffect(url) {
            if (url != null) {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .build()
    
                val result = context.imageLoader.execute(request).drawable
                if (result != null) {
                    val bitmap = (result as android.graphics.drawable.BitmapDrawable).bitmap
                    Palette.from(bitmap).generate { palette ->
                        val dominant = palette?.darkVibrantSwatch?.rgb ?: palette?.vibrantSwatch?.rgb ?: defaultColor.hashCode()
                        color.value = Color(dominant)
                    }
                }
            }
        }
        return color
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ChartsScreen(
        onBackClick: () -> Unit,
        onPlaylistClick: (Long) -> Unit,
        onNavigate: (String) -> Unit,
        playerViewModel: PlayerViewModel,
        viewModel: ChartsViewModel = viewModel()
    ) {
        var showCountrySelector by remember { mutableStateOf(false) }
        var showArtistMenu by remember { mutableStateOf<User?>(null) }
    
        val currentCountry = ChartsData.charts[viewModel.selectedCountryIndex]
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        val context = LocalContext.current
    
        // country selector sheet
        if (showCountrySelector) {
            ModalBottomSheet(
                onDismissRequest = { showCountrySelector = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp)
                        .navigationBarsPadding()
                ) {
                    Text(
                        text = stringResource(R.string.charts_select_country),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .align(Alignment.CenterHorizontally)
                    )
    
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(ChartsData.charts) { index, chartData ->
                            val isSelected = index == viewModel.selectedCountryIndex
                            CountrySelectionCard(
                                countryName = chartData.countryName,
                                flagEmoji = chartData.flagEmoji,
                                isSelected = isSelected,
                                onClick = {
                                    viewModel.loadCountryCharts(index)
                                    showCountrySelector = false
                                }
                            )
                        }
                    }
                }
            }
        }
    
        // artist options menu with new design
        if (showArtistMenu != null) {
            val user = showArtistMenu!!
            ModalBottomSheet(
                onDismissRequest = { showArtistMenu = null },
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(modifier = Modifier.padding(bottom = 32.dp)) {
                    // header stylized similar to player menu
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(horizontal = 24.dp, vertical = 20.dp)
                            .fillMaxWidth()
                    ) {
                        ArtistAvatar(
                            avatarUrl = user.avatarUrl,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                text = user.username ?: stringResource(R.string.unknown_artist),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.lib_artists),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (user.verified) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.Verified,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(8.dp))
    
                    // options list
                    ArtistMenuOption(
                        icon = Icons.Default.Shuffle,
                        text = stringResource(R.string.btn_shuffle),
                        onClick = {
                            viewModel.fetchArtistTopTracks(user.id) { tracks ->
                                if (tracks.isNotEmpty()) {
                                    playerViewModel.playPlaylist(tracks.shuffled(), 0)
                                }
                            }
                            showArtistMenu = null
                        }
                    )
    
                    ArtistMenuOption(
                        icon = Icons.Default.Radio,
                        text = stringResource(R.string.radio),
                        onClick = {
                            onNavigate("station_artist:${user.id}")
                            showArtistMenu = null
                        }
                    )
    
                    ArtistMenuOption(
                        icon = Icons.Default.Person,
                        text = stringResource(R.string.menu_go_artist),
                        onClick = {
                            onNavigate("profile:${user.id}")
                            showArtistMenu = null
                        }
                    )
    
                    ArtistMenuOption(
                        icon = Icons.Outlined.Share,
                        text = stringResource(R.string.btn_share),
                        onClick = {
                            val url = user.permalinkUrl ?: "https://soundcloud.com/${user.username}"
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, url)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.share_via)))
                            showArtistMenu = null
                        }
                    )
                }
            }
        }
    
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.home_charts),
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        )
                    },
                    navigationIcon = {
                        FilledTonalIconButton(
                            onClick = onBackClick,
                            shapes = IconButtonDefaults.shapes(),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_back))
                        }
                    },
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    scrollBehavior = scrollBehavior
                )
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier.padding(innerPadding),
                contentPadding = PaddingValues(bottom = 180.dp)
            ) {
                // country selector button
                item {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            onClick = { showCountrySelector = true },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 20.dp)
                                    .fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = currentCountry.flagEmoji, style = MaterialTheme.typography.titleLarge)
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = currentCountry.countryName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Icon(Icons.Rounded.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
    
                if (viewModel.isLoading) {
                    // loading skeleton
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(4) { SquareCardShimmer() }
                        }
                    }
                } else {
                    if (viewModel.chartPlaylists.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.explorer_charts),
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                            )
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(viewModel.chartPlaylists) { playlist ->
                                    ChartPlaylistCard(playlist = playlist, onClick = { onPlaylistClick(playlist.id) })
                                }
                            }
                        }
                    }
    
                    // --- 2. TOP ARTISTS (horizontal swipe, columns of 4) ---
                    if (viewModel.topArtists.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.charts_top_artists),
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
                            )
                        }
    
                        item {
                            // split list into chunks of 4 for the vertical columns
                            val chunkedArtists = remember(viewModel.topArtists) {
                                viewModel.topArtists.chunked(4)
                            }
    
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(chunkedArtists) { columnGroup ->
                                    Column(
                                        modifier = Modifier.width(340.dp), // wide column like youtube music
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        columnGroup.forEach { artistRanking ->
                                            ArtistRankRow(
                                                ranking = artistRanking,
                                                onClick = { onNavigate("profile:${artistRanking.user.id}") },
                                                onMenuClick = { showArtistMenu = artistRanking.user }
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
    
    @Composable
    fun ArtistRankRow(
        ranking: ArtistRanking,
        onClick: () -> Unit,
        onMenuClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // rank number
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(40.dp)
            ) {
                Text(
                    text = "${ranking.rank}",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
                // static visual dot (grey) to match screenshot
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                )
            }
    
            Spacer(Modifier.width(12.dp))
    
            // avatar
            ArtistAvatar(
                avatarUrl = ranking.user.avatarUrl,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
            )
    
            Spacer(Modifier.width(16.dp))
    
            // info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ranking.user.username ?: stringResource(R.string.unknown_artist),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // changed to display follower count instead of plays
                Text(
                    text = formatCompactNumber(ranking.user.followersCount.toLong()) + " " + stringResource(R.string.profile_followers).lowercase(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
    
            // menu three dots
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.btn_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    
    @Composable
    fun ArtistMenuOption(icon: ImageVector, text: String, onClick: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(20.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
    
    @Composable
    fun CountrySelectionCard(
        countryName: String,
        flagEmoji: String,
        isSelected: Boolean,
        onClick: () -> Unit
    ) {
        val backgroundColor by animateColorAsState(
            targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainer,
            label = "bgColor"
        )
        val contentColor by animateColorAsState(
            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            label = "textColor"
        )
        val borderColor by animateColorAsState(
            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
            label = "borderColor"
        )
    
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(20.dp),
            color = backgroundColor,
            border = BorderStroke(1.5.dp, borderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = flagEmoji, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = countryName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = contentColor
                    )
                }
    
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.desc_selected),
                        tint = contentColor
                    )
                }
            }
        }
    }
    
    @Composable
    fun ChartPlaylistCard(
        playlist: com.alananasss.kittytune.domain.Playlist,
        onClick: () -> Unit
    ) {
        val dominantColor by rememberDominantColor(url = playlist.fullResArtwork)
    
        val animatedColor by animateColorAsState(
            targetValue = dominantColor,
            animationSpec = tween(500),
            label = "dominantColor"
        )
    
        // reduced width card for inline scrolling
        Card(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .width(160.dp) // fixed width for horizontal scroll
                .wrapContentHeight()
        ) {
            Column {
                Box(modifier = Modifier.size(160.dp)) {
                    AsyncImage(
                        model = playlist.fullResArtwork,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        animatedColor.copy(alpha = 0.1f),
                                        animatedColor.copy(alpha = 0.4f)
                                    )
                                )
                            )
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = playlist.title?.uppercase() ?: stringResource(R.string.home_charts).uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.playlist_num_tracks, playlist.trackCount ?: 0),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    
    // helper to format numbers nicely (1.2m, 14k)
    fun formatCompactNumber(count: Long): String {
        if (count < 1000) return count.toString()
        val k = count / 1000.0
        val m = count / 1000000.0
        return when {
            m >= 1.0 -> String.format(Locale.US, "%.1fM", m)
            k >= 1.0 -> String.format(Locale.US, "%.1fk", k)
            else -> count.toString()
        }
    }


