package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alananasss.kittytune.R
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import java.text.NumberFormat
import java.util.Locale

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

    val title =
        if (type == "followers") stringResource(R.string.profile_followers) else stringResource(R.string.profile_followings)

    val artistsCount = remember(viewModel.users.size) { viewModel.users.count { it.isArtist } }
    val profilesCount = remember(viewModel.users.size) { viewModel.users.count { !it.isArtist } }

    val filteredUsers = remember(viewModel.users.size, selectedFilter) {
        when (selectedFilter) {
            UserFilterType.ALL -> viewModel.users
            UserFilterType.ARTISTS -> viewModel.users.filter { it.isArtist }
            UserFilterType.PROFILES -> viewModel.users.filter { !it.isArtist }
        }
    }

    LaunchedEffect(filteredUsers.size, selectedFilter, viewModel.hasMore) {
        if (filteredUsers.size < 15 && viewModel.hasMore && !viewModel.isLoadingMore && !viewModel.isLoading) {
            viewModel.loadMore()
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
                contentPadding = PaddingValues(top = 4.dp, bottom = 180.dp),
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                if (viewModel.users.isNotEmpty()) {
                    item {
                        ExpressiveConnectedButtonGroup(
                            options = listOf(UserFilterType.ALL, UserFilterType.ARTISTS, UserFilterType.PROFILES),
                            selectedOption = selectedFilter,
                            onOptionSelected = { selectedFilter = it },
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            labelProvider = { filter ->
                                Text(
                                    text = when (filter) {
                                        UserFilterType.ALL -> "${stringResource(R.string.filter_all)} (${viewModel.users.size})"
                                        UserFilterType.ARTISTS -> "${stringResource(R.string.filter_artists)} ($artistsCount)"
                                        UserFilterType.PROFILES -> "${stringResource(R.string.filter_profiles)} ($profilesCount)"
                                    },
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            iconProvider = { filter ->
                                when (filter) {
                                    UserFilterType.ALL -> Icon(
                                        Icons.Rounded.People,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    UserFilterType.ARTISTS -> Icon(
                                        Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    UserFilterType.PROFILES -> Icon(
                                        Icons.Rounded.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        )
                    }
                }

                if (filteredUsers.isEmpty() && viewModel.users.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.no_results),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                itemsIndexed(filteredUsers, key = { _, user -> "user_${user.id}_${user.urn}" }) { index, user ->
                    if (index >= filteredUsers.size - 4) {
                        LaunchedEffect(Unit) {
                            viewModel.loadMore()
                        }
                    }
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(52.dp)
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = user.effectiveDisplayName.ifBlank { user.username ?: stringResource(R.string.unknown_artist) },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (user.isVerifiedUser) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Rounded.Verified,
                        contentDescription = stringResource(R.string.user_type_verified_artist),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                if (user.isProUser && !user.isVerifiedUser) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = "PRO",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(3.dp))

            val formattedFollowers = NumberFormat.getNumberInstance(Locale.getDefault()).format(user.followersCount)
            val followersLabel = if (user.followersCount <= 1) {
                stringResource(R.string.profile_followers).removeSuffix("s")
            } else {
                stringResource(R.string.profile_followers)
            }

            val tracksLabel = if (user.trackCount <= 1) {
                stringResource(R.string.profile_tracks).removeSuffix("s")
            } else {
                stringResource(R.string.profile_tracks)
            }

            val playlistsLabel = stringResource(R.string.lib_playlists)

            val city = user.city?.trim()?.takeIf { it.isNotBlank() }
            val country = (user.countryCode?.trim() ?: user.country?.trim())?.takeIf { it.isNotBlank() }
            val location = listOfNotNull(city, country).joinToString(", ").takeIf { it.isNotBlank() }

            val statsDetails = buildList {
                add("$formattedFollowers $followersLabel")
                if (user.isArtist && user.trackCount > 0) {
                    add("${user.trackCount} $tracksLabel")
                } else if (!user.isArtist && user.playlistCount > 0) {
                    add("${user.playlistCount} $playlistsLabel")
                }
                if (location != null) {
                    add(location)
                }
            }.joinToString(" • ")

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

