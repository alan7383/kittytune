package com.alananasss.kittytune.ui.home

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.hapticAction
import com.alananasss.kittytune.ui.common.hapticClickable
import com.alananasss.kittytune.data.SearchCategory
import com.alananasss.kittytune.data.search.RecentVisits
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.player.PlayerViewModel

private const val COLLAPSED_RECENT_SEARCHES = 3

@Composable
fun SearchLanding(
    homeViewModel: HomeViewModel,
    playerViewModel: PlayerViewModel,
    onNavigate: (String) -> Unit,
    onCategoryClick: (SearchCategory) -> Unit,
    onOpenCharts: () -> Unit = { onNavigate("charts") },
    onOpenNewReleases: () -> Unit = { onNavigate("new_releases") },
    modifier: Modifier = Modifier,
) {
    val visits by homeViewModel.recentVisits.collectAsState()
    val searches = homeViewModel.recentSearches

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 180.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. Recent searches & visits (same as KittyTuneDesktop)
        if (searches.isNotEmpty() || visits.isNotEmpty()) {
            item {
                RecentSearchesSection(
                    visits = visits,
                    searches = searches,
                    onRun = { homeViewModel.runRecentSearch(it) },
                    onForget = { homeViewModel.forgetSearch(it) },
                    onOpenVisit = { visit ->
                        when (visit.kind) {
                            RecentVisits.Kind.TRACK ->
                                visit.track?.let { playerViewModel.playPlaylist(listOf(it), 0) }
                            RecentVisits.Kind.ARTIST,
                            RecentVisits.Kind.PLAYLIST ->
                                visit.destination?.let { onNavigate(it) }
                            null -> visit.destination?.let { onNavigate(it) }
                        }
                    },
                    onForgetVisit = { homeViewModel.forgetVisit(it) },
                    onClearAll = { homeViewModel.clearRecentSearches() },
                )
            }
        }

        // 2. Explorer door cards (Classements & Nouveautés - same as KittyTuneDesktop)
        item {
            Column {
                LandingHeader(title = stringResource(R.string.explorer_title), action = null)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LandingDoorCard(
                        title = stringResource(R.string.explorer_charts),
                        icon = Icons.Rounded.TrendingUp,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenCharts,
                    )
                    LandingDoorCard(
                        title = stringResource(R.string.explorer_new_releases),
                        icon = Icons.Rounded.NewReleases,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenNewReleases,
                    )
                }
            }
        }

        // 3. Pour toi (Personalized categories - same as KittyTuneDesktop)
        if (homeViewModel.personalizedCategories.isNotEmpty()) {
            item {
                Column {
                    LandingHeader(title = stringResource(R.string.search_section_personalized), action = null)
                    Spacer(Modifier.height(4.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(homeViewModel.personalizedCategories) { cat ->
                            Button(
                                onClick = hapticAction(HapticKind.UI) { onCategoryClick(cat) },
                                shapes = ButtonDefaults.shapes(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                modifier = Modifier.height(38.dp),
                            ) {
                                Text(
                                    text = cat.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Ambiances (Visual category cards with official artwork)
        val visualCategories = homeViewModel.visualCategories
        if (visualCategories.isNotEmpty()) {
            item {
                LandingHeader(title = stringResource(R.string.category_ambiances_title), action = null)
            }

            val pairs = visualCategories.chunked(2)
            items(pairs) { pair ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    pair.forEach { cat ->
                        Box(modifier = Modifier.weight(1f)) {
                            SearchCategoryVisualCard(
                                category = cat,
                                onClick = hapticAction(HapticKind.UI) { onCategoryClick(cat) }
                            )
                        }
                    }
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // 5. Genres (Genre pills with "Tout afficher" / See all)
        item {
            Column {
                LandingHeader(
                    title = stringResource(R.string.search_section_genres),
                    action = stringResource(R.string.category_see_all) to { onNavigate("genres") }
                )
                Spacer(Modifier.height(4.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(homeViewModel.genreCategories) { category ->
                        SearchCategoryPill(category = category) { onCategoryClick(category) }
                    }
                }
            }
        }
    }
}

@Composable
fun LandingHeader(
    title: String,
    action: Pair<String, () -> Unit>? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (action != null) {
            TextButton(
                onClick = action.second,
                shapes = ButtonDefaults.shapes(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = action.first,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun LandingDoorCard(
    title: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = tint.copy(alpha = 0.16f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.height(68.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
            )
        }
    }
}

@Composable
fun SearchCategoryVisualCard(
    category: SearchCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = com.alananasss.kittytune.data.GenreData.parseBorderColor(category.borderColor)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.2.dp, borderColor.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .height(92.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val isDark = isSystemInDarkTheme()
            val artwork = if (!isDark && !category.artworkUrlLight.isNullOrBlank()) {
                category.artworkUrlLight
            } else {
                category.artworkUrl
            }
            if (!artwork.isNullOrBlank()) {
                AsyncImage(
                    model = artwork,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(76.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .padding(end = 6.dp),
                    contentScale = ContentScale.Crop
                )
            } else if (category.icon != null) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = borderColor.copy(alpha = 0.25f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(68.dp)
                        .offset(x = 10.dp, y = 10.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.62f)
                    .padding(start = 14.dp, top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SearchCategoryPill(
    category: SearchCategory,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
        modifier = Modifier.height(42.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            category.icon?.let { icon ->
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = category.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun RecentSearchesSection(
    visits: List<RecentVisits.Visit>,
    searches: List<String>,
    onRun: (String) -> Unit,
    onForget: (String) -> Unit,
    onOpenVisit: (RecentVisits.Visit) -> Unit,
    onForgetVisit: (RecentVisits.Visit) -> Unit,
    onClearAll: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column {
        LandingHeader(
            title = stringResource(R.string.search_recent_searches),
            action = stringResource(R.string.search_clear_all) to onClearAll,
        )

        val validVisits = visits.filter { it.kind != null && !it.title.isNullOrBlank() }
        val total = validVisits.size + searches.size
        val shownVisits = if (expanded) validVisits else validVisits.take(COLLAPSED_RECENT_SEARCHES)
        shownVisits.forEach { visit ->
            RecentVisitRow(
                visit = visit,
                onOpen = { onOpenVisit(visit) },
                onForget = { onForgetVisit(visit) },
            )
        }
        val roomForTerms = if (expanded) searches.size else (COLLAPSED_RECENT_SEARCHES - shownVisits.size).coerceAtLeast(0)
        searches.take(roomForTerms).forEach { term ->
            RecentSearchRow(
                term = term,
                onRun = { onRun(term) },
                onForget = { onForget(term) },
            )
        }

        if (total > COLLAPSED_RECENT_SEARCHES) {
            val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "seeMoreChevron")
            TextButton(
                onClick = hapticAction(HapticKind.UI) { expanded = !expanded },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.padding(start = 12.dp, top = 2.dp),
            ) {
                Text(
                    text = stringResource(if (expanded) R.string.search_see_less else R.string.search_see_more),
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(rotation),
                )
            }
        }
    }
}

@Composable
private fun RecentVisitRow(
    visit: RecentVisits.Visit,
    onOpen: () -> Unit,
    onForget: () -> Unit,
) {
    val kind = visit.kind
    val isArtist = kind == RecentVisits.Kind.ARTIST
    val subtitle = when (kind) {
        RecentVisits.Kind.ARTIST -> stringResource(R.string.search_kind_artist)
        RecentVisits.Kind.TRACK -> stringResource(R.string.search_kind_track, visit.subtitle.orEmpty())
        RecentVisits.Kind.PLAYLIST ->
            listOfNotNull(stringResource(R.string.search_kind_playlist), visit.subtitle?.takeIf { it.isNotBlank() }).joinToString(" · ")
        null -> visit.subtitle.orEmpty()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = visit.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(if (isArtist) CircleShape else RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = visit.title.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (visit.isVerified) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Rounded.Verified,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onForget),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.search_remove_recent),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun RecentSearchRow(
    term: String,
    onRun: () -> Unit,
    onForget: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onRun)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = term,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onForget),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.search_remove_recent),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun SearchTopMatchHeroCard(
    user: User,
    onArtistClick: () -> Unit,
    onPlayClick: (() -> Unit)? = null,
    isSpotify: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onArtistClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = user.avatarUrl,
                    contentDescription = user.username,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.search_top_result),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = user.username.orEmpty(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (user.verified) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Rounded.Verified,
                                contentDescription = null,
                                tint = if (isSpotify) Color(0xFF1DB954) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    val followers = user.followersCount
                    if (followers > 0) {
                        val followersText = when {
                            followers >= 1_000_000 -> "${followers / 1_000_000}M ${stringResource(R.string.profile_followers)}"
                            followers >= 1_000 -> "${followers / 1_000}K ${stringResource(R.string.profile_followers)}"
                            else -> "$followers ${stringResource(R.string.profile_followers)}"
                        }
                        Text(
                            text = followersText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (onPlayClick != null) {
                    FilledIconButton(
                        onClick = onPlayClick,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}
