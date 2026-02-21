    package com.alananasss.kittytune.ui.library
    
    import androidx.compose.foundation.ExperimentalFoundationApi
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.pager.HorizontalPager
    import androidx.compose.foundation.pager.rememberPagerState
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.automirrored.filled.ArrowBack
    import androidx.compose.material3.*
    import androidx.compose.runtime.*
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.res.stringResource
    import androidx.compose.ui.text.style.TextOverflow
    import androidx.lifecycle.viewmodel.compose.viewModel
    import com.alananasss.kittytune.R
    import com.alananasss.kittytune.ui.track.UserList // importing userlist from trackdetailscreen
    import kotlinx.coroutines.launch
    
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
    @Composable
    fun PlaylistFansScreen(
        playlistId: Long,
        initialTab: Int = 0,
        onBackClick: () -> Unit,
        onNavigate: (String) -> Unit,
        viewModel: PlaylistInfoViewModel = viewModel() // using the updated viewmodel
    ) {
        val pagerState = rememberPagerState(initialPage = initialTab) { 2 }
        val scope = rememberCoroutineScope()
        val tabs = listOf(
            stringResource(R.string.detail_likers),
            stringResource(R.string.detail_reposters)
        )
    
        LaunchedEffect(playlistId) {
            viewModel.loadPlaylistDetails(playlistId)
        }
    
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.menu_details), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_close))
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {
                TabRow(selectedTabIndex = pagerState.currentPage) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(text = title) }
                        )
                    }
                }
    
                HorizontalPager(state = pagerState) { page ->
                    when (page) {
                        0 -> UserList(
                            users = viewModel.likers,
                            onNavigate = onNavigate,
                            onLoadMore = { viewModel.loadMoreLikers() }, // wiring here
                            isLoadingMore = viewModel.isLikersLoadingMore // and here
                        )
                        1 -> UserList(
                            users = viewModel.reposters,
                            onNavigate = onNavigate,
                            onLoadMore = { viewModel.loadMoreReposters() }, // wiring here
                            isLoadingMore = viewModel.isRepostersLoadingMore // and here
                        )
                    }
                }
            }
        }
    }


