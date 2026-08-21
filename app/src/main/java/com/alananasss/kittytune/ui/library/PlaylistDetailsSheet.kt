package com.alananasss.kittytune.ui.library

import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.profile.ArtistAvatar
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaylistDetailsSheet(
    playlistId: String,
    onDismiss: () -> Unit,
    onViewAll: (Int) -> Unit,
    onNavigate: (String) -> Unit,
    onMentionClick: (String) -> Unit,
    viewModel: PlaylistInfoViewModel = viewModel()
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(playlistId) {
        viewModel.loadPlaylistDetails(playlistId)
    }

    val playlist = viewModel.playlistDetails

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist?.title ?: stringResource(R.string.menu_details),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (playlist?.user != null) {
                    Spacer(Modifier.height(8.dp))
                    val creator = playlist.user
                    val isArtist = creator.isArtist
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (isArtist) {
                                    Modifier.clickable {
                                        onDismiss()
                                        val navTarget = when {
                                            creator.urn?.startsWith("spotify:artist:") == true -> creator.urn
                                            creator.permalink != null && creator.permalink.isNotBlank() -> creator.permalink
                                            creator.numericId > 0L -> creator.numericId.toString()
                                            else -> creator.id.toString()
                                        }
                                        onNavigate("profile:$navTarget")
                                    }
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(24.dp)
                            ) {
                                ArtistAvatar(
                                    avatarUrl = creator.avatarUrl,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = creator.username ?: stringResource(R.string.unknown),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (isArtist) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (creator.verified) {
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            Icons.Rounded.Verified,
                                            null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = stringResource(R.string.playlist_creator_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp),
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    Icons.Rounded.Close,
                    stringResource(R.string.btn_close),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (viewModel.isLoading) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                ContainedLoadingIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp)
            ) {

                if (playlist != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val saves = (playlist.likesCount ?: 0).takeIf { it > 0 } ?: viewModel.likers.size
                        if (saves > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.Favorite,
                                    null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(
                                        R.string.playlist_details_saves,
                                        NumberFormat.getNumberInstance(Locale.getDefault()).format(saves)
                                    ),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        val count = playlist.trackCount ?: playlist.tracks?.size ?: 0
                        if (count > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.QueueMusic,
                                    null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.playlist_num_tracks, count),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        val dateStr = playlist.lastModified ?: playlist.createdAt
                        if (dateStr != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.CalendarToday,
                                    null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Updated ${getRelativeTime(dateStr, context)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        val tags = remember(playlist.tagList, playlist.genre) {
                            val list = parseSoundCloudTags(playlist.tagList).toMutableList()
                            if (!playlist.genre.isNullOrBlank() && !list.contains(playlist.genre)) {
                                list.add(0, playlist.genre)
                            }
                            list
                        }

                        if (tags.isNotEmpty()) {
                            FlowRow(modifier = Modifier.fillMaxWidth()) {
                                tags.take(10).forEach { tag ->
                                    SuggestionChip(
                                        onClick = {
                                            onDismiss()
                                            onNavigate("tag:$tag")
                                        },
                                        label = {
                                            Text(
                                                text = tag.uppercase(),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        },
                                        icon = {
                                            Icon(
                                                Icons.Rounded.Tag,
                                                null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                            labelColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        border = null,
                                        modifier = Modifier.padding(end = 8.dp, bottom = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )
                    Spacer(Modifier.height(24.dp))
                }

                if (!playlist?.description.isNullOrBlank()) {
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        Text(
                            text = stringResource(R.string.detail_description),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))

                        ExpandableDescription(
                            text = playlist.description!!,
                            onUrlClick = { url -> uriHandler.openUri(url) },
                            onMentionClick = { username ->
                                onDismiss()
                                onMentionClick(username)
                            }
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )
                    Spacer(Modifier.height(24.dp))
                }

                if (viewModel.likers.isEmpty() && viewModel.reposters.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.Info,
                                null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.no_details_available),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    if (viewModel.likers.isNotEmpty()) {
                        UserSection(
                            title = stringResource(R.string.detail_stats_likes),
                            icon = Icons.Rounded.Favorite,
                            users = viewModel.likers,
                            onViewAll = { onViewAll(0) }
                        )
                        Spacer(Modifier.height(32.dp))
                    }

                    if (viewModel.reposters.isNotEmpty()) {
                        UserSection(
                            title = stringResource(R.string.detail_stats_reposts),
                            icon = Icons.Rounded.Repeat,
                            users = viewModel.reposters,
                            onViewAll = { onViewAll(1) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UserSection(
    title: String,
    icon: ImageVector,
    users: List<User>,
    onViewAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onViewAll() }
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = stringResource(R.string.btn_see_all),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy((-12).dp)
        ) {
            items(users.take(10)) { user ->
                AvatarItem(user.avatarUrl)
            }

            item {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable { onViewAll() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "+",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun AvatarItem(url: String?) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(2.dp)
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
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
                style = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold),
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
                style = SpanStyle(color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold),
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
            maxLines = if (isExpanded) Int.MAX_VALUE else 3,
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

        if (text.length > 150 || text.lines().size > 3) {
            Text(
                text = if (isExpanded) stringResource(R.string.detail_show_less) else stringResource(R.string.detail_show_more),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { isExpanded = !isExpanded }
            )
        }
    }
}

fun parseSoundCloudTags(tagList: String?): List<String> {
    if (tagList.isNullOrBlank()) return emptyList()
    val tags = mutableListOf<String>()
    val pattern = Pattern.compile("\"([^\"]*)\"|(\\S+)")
    val matcher = pattern.matcher(tagList)
    while (matcher.find()) {
        if (matcher.group(1) != null) {
            tags.add(matcher.group(1)!!)
        } else {
            tags.add(matcher.group(2)!!)
        }
    }
    return tags
}

fun getRelativeTime(dateStr: String, context: Context): String {
    try {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        val date = format.parse(dateStr) ?: return ""
        val diff = System.currentTimeMillis() - date.time

        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        val weeks = days / 7
        val months = days / 30
        val years = days / 365

        return when {
            seconds < 60 -> context.getString(R.string.time_now)
            minutes < 60 -> context.getString(R.string.time_minutes_ago, minutes)
            hours < 24 -> context.getString(R.string.time_hours_ago, hours)
            days < 7 -> context.getString(R.string.time_days_ago, days)
            weeks < 5 -> context.getString(R.string.time_weeks_ago, weeks)
            months < 12 -> context.getString(R.string.time_months_ago, months)
            years == 1L -> context.getString(R.string.time_one_year_ago)
            else -> context.getString(R.string.time_years_ago, years)
        }
    } catch (e: Exception) {
        return ""
    }
}

