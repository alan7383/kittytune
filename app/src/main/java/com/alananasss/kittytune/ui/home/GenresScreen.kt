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
    import androidx.compose.material.icons.automirrored.filled.ViewList
    import androidx.compose.material.icons.filled.GridView
    import androidx.compose.material3.*
    import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
    import androidx.compose.runtime.Composable
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.input.nestedscroll.nestedScroll
    import androidx.compose.ui.res.stringResource
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.unit.dp
    import androidx.lifecycle.viewmodel.compose.viewModel
    import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.hapticAction
import com.alananasss.kittytune.ui.common.hapticClickable

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun GenresScreen(
        onBackClick: () -> Unit,
        onNavigate: (String) -> Unit,
        viewModel: GenresViewModel = viewModel()
    ) {
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        val columns = if (viewModel.isGridLayout) GridCells.Fixed(2) else GridCells.Fixed(1)

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    title = { Text(stringResource(R.string.explorer_title), fontWeight = FontWeight.Bold) },
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
                    actions = {
                        FilledTonalIconButton(
                            onClick = hapticAction(HapticKind.UI) { viewModel.toggleLayout() },
                            shapes = IconButtonDefaults.shapes(),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(
                                imageVector = if (viewModel.isGridLayout) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                contentDescription = stringResource(R.string.btn_options)
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
            LazyVerticalGrid(
                columns = columns,
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 180.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Official SoundCloud genres / categories (with official logos)
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(R.string.search_section_genres),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
                    )
                }

                items(viewModel.officialCategories) { category ->
                    SearchCategoryCard(category = category) {
                        val encodedTitle = Uri.encode(category.title)
                        val encodedQuery = Uri.encode(category.query)
                        onNavigate("genre_detail/$encodedTitle/$encodedQuery")
                    }
                }

                // Manual / custom categories (the ones created with icons)
                if (viewModel.manualCategories.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = stringResource(R.string.search_section_other_genres),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                        )
                    }

                    items(viewModel.manualCategories) { category ->
                        SearchCategoryCard(category = category) {
                            val encodedTitle = Uri.encode(category.title)
                            val encodedQuery = Uri.encode(category.query)
                            onNavigate("genre_detail/$encodedTitle/$encodedQuery")
                        }
                    }
                }
            }
        }
    }

