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
    import androidx.compose.material.icons.rounded.CalendarToday
    import androidx.compose.material.icons.rounded.Close
    import androidx.compose.material.icons.rounded.Favorite
    import androidx.compose.material.icons.rounded.Info
    import androidx.compose.material.icons.rounded.Repeat
    import androidx.compose.material.icons.rounded.Tag
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
    import java.text.SimpleDateFormat
    import java.util.Locale
    import java.util.regex.Pattern
    
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun PlaylistDetailsSheet(
        playlistId: Long,
        onDismiss: () -> Unit,
        onViewAll: (Int) -> Unit, // 0 = likes, 1 = reposts
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
                        Text(
                            text = stringResource(R.string.playlist_by_user, playlist.user.username ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clickable {
                                    onDismiss()
                                    onNavigate("profile:${playlist.user.id}")
                                }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
    
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                        .size(32.dp)
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        stringResource(R.string.btn_close),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface
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
    
                    // --- INFO PLAYLIST (DATE & TAGS) ---
                    if (playlist != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // DATE
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
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceContainer,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .padding(end = 8.dp, bottom = 8.dp)
                                                .clickable {
                                                    onDismiss()
                                                    onNavigate("tag:$tag")
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Tag,
                                                    null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = tag.uppercase(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
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
    
                    // --- DESCRIPTION ---
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
    
                    // --- STATS (LIKES & REPOSTS) ---
                    if (viewModel.likers.isEmpty() && viewModel.reposters.isEmpty()) {
                        Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.Info, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.surfaceVariant)
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
                addStringAnnotation(tag = "URL", annotation = urlMatcher.group(), start = urlMatcher.start(), end = urlMatcher.end())
                addStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold), start = urlMatcher.start(), end = urlMatcher.end())
            }
    
            val mentionMatcher = mentionPattern.matcher(fullText)
            while (mentionMatcher.find()) {
                addStringAnnotation(tag = "MENTION", annotation = mentionMatcher.group(), start = mentionMatcher.start(), end = mentionMatcher.end())
                addStyle(style = SpanStyle(color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold), start = mentionMatcher.start(), end = mentionMatcher.end())
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
                            "URL" -> { onUrlClick(annotation.item); isAnnotationClicked = true }
                            "MENTION" -> { onMentionClick(annotation.item); isAnnotationClicked = true }
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
        } catch (e: Exception) { return "" }
    }


