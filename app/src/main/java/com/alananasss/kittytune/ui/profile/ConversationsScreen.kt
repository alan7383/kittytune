    package com.alananasss.kittytune.ui.profile
    
    import androidx.compose.foundation.clickable
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.lazy.LazyColumn
    import androidx.compose.foundation.lazy.items
    import androidx.compose.foundation.shape.CircleShape
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.rounded.ArrowBack
    import androidx.compose.material.icons.rounded.ChatBubbleOutline
    import androidx.compose.material3.*
    import androidx.compose.runtime.Composable
    import androidx.compose.runtime.LaunchedEffect
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.draw.clip
    import androidx.compose.ui.input.nestedscroll.nestedScroll
    import androidx.compose.ui.res.stringResource
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.text.style.TextOverflow
    import androidx.compose.ui.unit.dp
    import androidx.lifecycle.viewmodel.compose.viewModel
    import com.alananasss.kittytune.R
    import com.alananasss.kittytune.domain.InboxConversation
import com.alananasss.kittytune.domain.parseUserIdFromUrn
    import com.alananasss.kittytune.ui.library.getRelativeTime
    
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun ConversationsScreen(
        onBackClick: () -> Unit,
        onConversationClick: (String, String, String) -> Unit, // conversationId, otherUserId, username
        viewModel: ConversationsViewModel = viewModel()
    ) {
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    
        LaunchedEffect(Unit) {
            viewModel.loadConversations()
        }
    
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.messages_title),
                            fontWeight = FontWeight.Bold
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
            if (viewModel.isLoading) {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    ContainedLoadingIndicator()
                }
            } else if (viewModel.conversations.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.ChatBubbleOutline,
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
                        .fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(viewModel.conversations) { conversation ->
                        ConversationItemCard(
                            conversation = conversation,
                            myUrn = viewModel.currentUserUrn,
                            onClick = { conversationId, otherId, username -> onConversationClick(conversationId, parseUserIdFromUrn(otherId)?.toString() ?: otherId, username) }
                        )
                    }
                }
            }
        }
    }
    
    @Composable
    fun ConversationItemCard(
        conversation: InboxConversation,
        myUrn: String,
        onClick: (String, String, String) -> Unit
    ) {
        val otherUser = conversation.getOtherUsername(myUrn)
        val otherUrn = conversation.getOtherUserUrn(myUrn)
        val otherAvatar = conversation.getOtherAvatar(myUrn)
        val lastMsg = conversation.lastMessage
        val context = androidx.compose.ui.platform.LocalContext.current
    
        if (otherUser != null && otherUrn != null) {
            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(conversation.id, parseUserIdFromUrn(otherUrn)?.toString() ?: otherUrn, otherUser) },
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                headlineContent = {
                    Text(
                        text = otherUser,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                supportingContent = {
                    if (lastMsg.content.isNotEmpty()) {
                        Text(
                            text = lastMsg.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                leadingContent = {
                    ArtistAvatar(
                        avatarUrl = otherAvatar,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                    )
                },
                trailingContent = {
                    if (lastMsg.sentAt != null) {
                        Text(
                            text = getRelativeTime(lastMsg.sentAt, context),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    }


