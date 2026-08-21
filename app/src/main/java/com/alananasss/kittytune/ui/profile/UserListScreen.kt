package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alananasss.kittytune.R
import com.alananasss.kittytune.domain.User
import java.text.NumberFormat
import java.util.Locale
import androidx.compose.ui.input.nestedscroll.nestedScroll

enum class UserFilterType {
    ALL,
    ARTISTS,
    PROFILES
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UserListScreen(
    userId: Long,
    type: String,
    onBack: () -> Unit,
    onUserClick: (Long) -> Unit,
    viewModel: UserListViewModel = viewModel()
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(userId, type) {
        viewModel.loadUsers(userId, type)
    }

    val listState = rememberLazyListState()
    var selectedFilter by remember { mutableStateOf(UserFilterType.ALL) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex ->
                if (lastIndex != null && lastIndex >= viewModel.users.size - 5) {
                    viewModel.loadMore()
                }
            }
    }

    val title =
        if (type == "followers") stringResource(R.string.profile_followers) else stringResource(R.string.profile_followings)

    val artistsCount = remember(viewModel.users) { viewModel.users.count { it.isArtist } }
    val profilesCount = remember(viewModel.users) { viewModel.users.count { !it.isArtist } }

    val filteredUsers = remember(viewModel.users, selectedFilter) {
        when (selectedFilter) {
            UserFilterType.ALL -> viewModel.users
            UserFilterType.ARTISTS -> viewModel.users.filter { it.isArtist }
            UserFilterType.PROFILES -> viewModel.users.filter { !it.isArtist }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
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
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.btn_back)
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (viewModel.isLoading && viewModel.users.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 120.dp),
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                if (viewModel.users.isNotEmpty()) {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedFilter == UserFilterType.ALL,
                                    onClick = { selectedFilter = UserFilterType.ALL },
                                    label = {
                                        Text("${stringResource(R.string.filter_all)} (${viewModel.users.size})")
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                            item {
                                FilterChip(
                                    selected = selectedFilter == UserFilterType.ARTISTS,
                                    onClick = { selectedFilter = UserFilterType.ARTISTS },
                                    label = {
                                        Text("${stringResource(R.string.filter_artists)} ($artistsCount)")
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Rounded.MusicNote,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                            item {
                                FilterChip(
                                    selected = selectedFilter == UserFilterType.PROFILES,
                                    onClick = { selectedFilter = UserFilterType.PROFILES },
                                    label = {
                                        Text("${stringResource(R.string.filter_profiles)} ($profilesCount)")
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Rounded.Person,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }
                }

                items(filteredUsers, key = { "user_${it.id}_${it.urn}" }) { user ->
                    UserRow(user = user, onClick = { onUserClick(user.numericId) })
                }

                if (viewModel.isLoadingMore) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserRow(user: User, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(54.dp)
        ) {
            ArtistAvatar(
                avatarUrl = user.avatarUrl,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = user.username ?: stringResource(R.string.unknown_artist),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (user.verified) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Rounded.Verified,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

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

            Spacer(Modifier.height(4.dp))

            val formattedFollowers = NumberFormat.getNumberInstance(Locale.US).format(user.followersCount)
            val statsDetails = buildString {
                append("$formattedFollowers ${stringResource(R.string.profile_followers)}")
                if (user.isArtist && user.trackCount > 0) {
                    append(" • ${user.trackCount} ${stringResource(R.string.profile_tracks)}")
                } else if (!user.isArtist && user.playlistCount > 0) {
                    append(" • ${user.playlistCount} ${stringResource(R.string.lib_playlists)}")
                }
                val location = listOfNotNull(user.city, user.countryCode ?: user.country).joinToString(", ")
                if (location.isNotBlank()) {
                    append(" • $location")
                }
            }

            Text(
                text = statsDetails,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
