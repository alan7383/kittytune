    package com.alananasss.kittytune.ui.home
    
    import android.net.Uri
    import androidx.compose.foundation.layout.Arrangement
    import androidx.compose.foundation.layout.PaddingValues
    import androidx.compose.foundation.layout.fillMaxSize
    import androidx.compose.foundation.layout.padding
    import androidx.compose.foundation.lazy.grid.GridCells
    import androidx.compose.foundation.lazy.grid.GridItemSpan
    import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
    import androidx.compose.foundation.lazy.grid.items
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.automirrored.filled.ArrowBack
    import androidx.compose.material3.*
    import androidx.compose.runtime.Composable
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.input.nestedscroll.nestedScroll
    import androidx.compose.ui.res.stringResource
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.unit.dp
    import androidx.compose.foundation.lazy.grid.itemsIndexed
    import androidx.lifecycle.viewmodel.compose.viewModel
    import com.alananasss.kittytune.R
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun GenresScreen(
        onBackClick: () -> Unit,
        onNavigate: (String) -> Unit,
        viewModel: GenresViewModel = viewModel()
    ) {
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    title = { Text(stringResource(R.string.explorer_title), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_back))
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
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // moods section
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(R.string.search_section_moods),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
                    )
                }
    
                items(viewModel.moodCategories) { category ->
                    SearchCategoryCard(category) {
                        val encodedTitle = Uri.encode(category.title)
                        val encodedQuery = Uri.encode(category.query)
                        onNavigate("genre_playlists/$encodedTitle/$encodedQuery")
                    }
                }
    
                // genres section
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(R.string.search_section_genres),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                    )
                }
    
                items(viewModel.genreCategories) { category ->
                    SearchCategoryCard(category) {
                        val encodedTitle = Uri.encode(category.title)
                        val encodedQuery = Uri.encode(category.query)
                        onNavigate("genre_detail/$encodedTitle/$encodedQuery")
                    }
                }
            }
        }
    }


