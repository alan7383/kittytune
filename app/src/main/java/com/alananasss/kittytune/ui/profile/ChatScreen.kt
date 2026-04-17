    package com.alananasss.kittytune.ui.profile
    
    import androidx.compose.animation.core.Spring
    import androidx.compose.animation.core.spring
    import androidx.compose.foundation.ExperimentalFoundationApi
    import androidx.compose.foundation.background
    import androidx.compose.foundation.clickable
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.lazy.LazyColumn
    import androidx.compose.foundation.lazy.items
    import androidx.compose.foundation.lazy.rememberLazyListState
    import androidx.compose.foundation.shape.CircleShape
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.foundation.text.BasicTextField
    import androidx.compose.foundation.text.KeyboardActions
    import androidx.compose.foundation.text.KeyboardOptions
    import androidx.compose.ui.graphics.SolidColor
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.automirrored.rounded.ArrowBack
    import androidx.compose.material.icons.automirrored.rounded.Send
    import androidx.compose.material.icons.rounded.GraphicEq
    import androidx.compose.material.icons.rounded.Person
    import androidx.compose.material.icons.rounded.PlayArrow
    import androidx.compose.material.icons.rounded.QueueMusic
    import androidx.compose.material3.*
    import androidx.compose.runtime.*
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.draw.clip
    import androidx.compose.ui.graphics.graphicsLayer
    import androidx.compose.ui.layout.ContentScale
    import androidx.compose.ui.res.stringResource
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.text.input.ImeAction
    import androidx.compose.ui.unit.sp
    import androidx.compose.ui.text.style.TextOverflow
    import androidx.compose.ui.unit.dp
    import androidx.lifecycle.viewmodel.compose.viewModel
    import android.text.format.DateFormat
    import coil.compose.AsyncImage
    import com.alananasss.kittytune.R
    import com.alananasss.kittytune.domain.Message
    import com.alananasss.kittytune.domain.Playlist
    import com.alananasss.kittytune.domain.Track
    import com.alananasss.kittytune.domain.User
    import com.alananasss.kittytune.ui.player.PlayerViewModel
    import kotlinx.coroutines.launch
    import java.util.regex.Pattern
    import androidx.compose.animation.core.animateDpAsState
    
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun ChatScreen(
        otherUserId: String,
        username: String,
        onBackClick: () -> Unit,
        onProfileClick: (String) -> Unit,
        playerViewModel: PlayerViewModel,
        viewModel: ChatViewModel = viewModel(),
        onNavigate: (String) -> Unit = {}
    ) {
        LaunchedEffect(otherUserId) {
            viewModel.loadMessages(otherUserId)
        }
    
        DisposableEffect(Unit) {
            onDispose { viewModel.stopPolling() }
        }
    
        var messageText by remember { mutableStateOf("") }
        val isMiniPlayerVisible = playerViewModel.currentTrack != null
    
        val otherUserAvatar by remember {
            derivedStateOf {
                viewModel.messages.find { it.sender?.username == username }?.sender?.avatarUrl
            }
        }
    
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
    
        LaunchedEffect(viewModel.messages.size) {
            if (viewModel.messages.isNotEmpty()) {
                scope.launch { listState.animateScrollToItem(0) }
            }
        }
    
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onProfileClick(otherUserId) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            ArtistAvatar(
                                avatarUrl = otherUserAvatar,
                                modifier = Modifier.size(32.dp).clip(CircleShape)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = username,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBackClick,
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.btn_back))
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) { innerPadding ->
    
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val baseInputHeight = 90.dp
                val playerHeight = if (isMiniPlayerVisible) 80.dp else 0.dp
    
                val listBottomPadding = baseInputHeight + playerHeight
    
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = listBottomPadding
                    ),
                    reverseLayout = true,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom)
                ) {
                    items(
                        items = viewModel.messages,
                        key = { it.hashCode() }
                    ) { message ->
                        val isMe = message.sender?.id == viewModel.myUser?.id
                        MessageBubble(
                            message = message,
                            isMe = isMe,
                            viewModel = viewModel,
                            onPreviewClick = { data ->
                                when (data) {
                                    is Track -> playerViewModel.playPlaylist(listOf(data), 0)
                                    is Playlist -> onNavigate("playlist_detail/${data.id}")
                                    is User -> onProfileClick(data.id.toString())
                                }
                            },
                            modifier = Modifier.animateItem(
                                placementSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            )
                        )
                    }
                }
    
                ChatInputBar(
                    text = messageText,
                    onTextChange = { messageText = it },
                    onSend = {
                        if (messageText.isNotBlank()) {
                            viewModel.sendMessage(messageText)
                            messageText = ""
                        }
                    },
                    isLoading = viewModel.isSending,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = playerHeight)
                )
            }
        }
    }
    
    @Composable
    fun MessageBubble(
        message: Message,
        isMe: Boolean,
        viewModel: ChatViewModel,
        onPreviewClick: (Any) -> Unit,
        modifier: Modifier = Modifier
    ) {
        val bubbleShape = if (isMe) {
            RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
        } else {
            RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
        }
    
        val containerColor = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
        val contentColor = if (isMe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    
        val content = message.content ?: ""
        val formattedTime = formatMessageDate(message.sentAt)
    
        val urlPattern = Pattern.compile("(https?://(on\\.)?soundcloud\\.com/\\S+)")
        val matcher = urlPattern.matcher(content)
        val foundUrl = if (matcher.find()) matcher.group(1) else null
    
        if (foundUrl != null) {
            LaunchedEffect(foundUrl) {
                viewModel.fetchLinkMetadata(foundUrl)
            }
        }
    
        val richData = if (foundUrl != null) viewModel.linkMetadataCache[foundUrl] else null
    
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
        ) {
            if (richData != null) {
                Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
                    SoundCloudPreviewCard(
                        data = richData,
                        isMe = isMe,
                        onClick = { onPreviewClick(richData) }
                    )
                    if (formattedTime.isNotEmpty()) {
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
                        )
                    }
                }
    
                if (content.trim() != foundUrl) {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        color = containerColor,
                        shape = bubbleShape,
                        modifier = Modifier.widthIn(max = 280.dp),
                        shadowElevation = 0.dp
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = content.replace(foundUrl!!, "").trim(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = contentColor
                            )
                            if (formattedTime.isNotEmpty()) {
                                Text(
                                    text = formattedTime,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = contentColor.copy(alpha = 0.6f),
                                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Surface(
                    color = containerColor,
                    shape = bubbleShape,
                    modifier = Modifier.widthIn(max = 280.dp),
                    shadowElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp)
                    ) {
                        Text(
                            text = content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = contentColor
                        )
    
                        if (formattedTime.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formattedTime,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = contentColor.copy(alpha = 0.6f),
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }
            }
        }
    }
    
    @Composable
    fun SoundCloudPreviewCard(
        data: Any,
        isMe: Boolean,
        onClick: () -> Unit
    ) {
        val cardBgColor = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer
        val cardContentColor = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    
        val (title, subtitle, imageUrl, icon) = when (data) {
            is Track -> Quadruple(
                data.title ?: "Track",
                data.user?.username ?: "Artist",
                data.fullResArtwork,
                Icons.Rounded.GraphicEq
            )
            is Playlist -> Quadruple(
                data.title ?: "Playlist",
                "${data.trackCount} tracks • ${data.user?.username}",
                data.fullResArtwork,
                Icons.Rounded.QueueMusic
            )
            is User -> Quadruple(
                data.username ?: "User",
                "${data.followersCount} followers",
                data.avatarUrl?.replace("large", "t500x500"),
                Icons.Rounded.Person
            )
            else -> Quadruple("SoundCloud Link", "", null, Icons.Rounded.GraphicEq)
        }
    
        Card(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = cardBgColor,
                contentColor = cardContentColor
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .widthIn(max = 300.dp)
                .padding(vertical = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
    
                Spacer(Modifier.width(12.dp))
    
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = cardContentColor.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
    
                Spacer(Modifier.width(8.dp))
    
                Surface(
                    shape = CircleShape,
                    color = cardContentColor.copy(alpha = 0.1f),
                    contentColor = cardContentColor,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (data is User) Icons.AutoMirrored.Rounded.ArrowBack else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp).let { if (data is User) it.rotate(180f) else it }
                        )
                    }
                }
            }
        }
    }
    
    fun Modifier.rotate(degrees: Float) = this.then(
        Modifier.graphicsLayer(rotationZ = degrees)
    )
    
    data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
    
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun ChatInputBar(
        text: String,
        onTextChange: (String) -> Unit,
        onSend: () -> Unit,
        isLoading: Boolean,
        modifier: Modifier = Modifier
    ) {
        val isKeyboardVisible = WindowInsets.isImeVisible
        val bottomPadding by animateDpAsState(
            targetValue = if (isKeyboardVisible) 32.dp else 2.dp,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "keyboardBottomPadding"
        )
    
        Box(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding) // ← use bottomPadding
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp, max = 120.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.chat_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
    
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onSend() })
                    )
                }
    
                Spacer(Modifier.width(4.dp))
    
                FilledIconButton(
                    onClick = onSend,
                    enabled = text.isNotBlank() && !isLoading,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Rounded.Send,
                            contentDescription = stringResource(R.string.chat_send),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
    
    @Composable
    fun formatMessageDate(dateStr: String?): String {
        if (dateStr.isNullOrEmpty()) return ""
        val context = androidx.compose.ui.platform.LocalContext.current
    
        return remember(dateStr) {
            try {
                val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                inputFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val date = inputFormat.parse(dateStr) ?: return@remember ""
    
                val now = java.util.Calendar.getInstance()
                val msgTime = java.util.Calendar.getInstance().apply { time = date }
    
                val isToday = now.get(java.util.Calendar.YEAR) == msgTime.get(java.util.Calendar.YEAR) &&
                        now.get(java.util.Calendar.DAY_OF_YEAR) == msgTime.get(java.util.Calendar.DAY_OF_YEAR)
    
                // Get the user's preferred time format (12h or 24h)
                val timeFormat = DateFormat.getTimeFormat(context)
                val timeStr = timeFormat.format(date)
    
                if (isToday) {
                    timeStr // e.g., "14:30" or "2:30 PM"
                } else {
                    // Get the preferred short date format (e.g., 07/02 or Feb 7)
                    val dateFormat = DateFormat.getMediumDateFormat(context)
                    val dateStrFormatted = dateFormat.format(date)
                    // We remove the year to make it shorter if wanted, or keep as is
                    // To keep it simple here: Date + Time
                    "$dateStrFormatted, $timeStr"
                }
            } catch (e: Exception) {
                ""
            }
        }
    }


