    package com.alananasss.kittytune.ui.profile
    
    import android.content.Intent
    import android.net.Uri
    import androidx.compose.animation.*
    import androidx.compose.foundation.clickable
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.lazy.LazyColumn
    import androidx.compose.foundation.lazy.LazyRow
    import androidx.compose.foundation.lazy.items
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.automirrored.rounded.ArrowBack
    import androidx.compose.material.icons.automirrored.rounded.OpenInNew
    import androidx.compose.material.icons.rounded.*
    import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
    import androidx.compose.runtime.*
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.draw.clip
    import androidx.compose.ui.graphics.Color
    import androidx.compose.ui.input.nestedscroll.nestedScroll
    import androidx.compose.ui.platform.LocalContext
    import androidx.compose.ui.res.stringResource
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.text.style.TextOverflow
    import androidx.compose.ui.unit.dp
    import com.alananasss.kittytune.R
    import com.mikepenz.aboutlibraries.Libs
    import com.mikepenz.aboutlibraries.util.withContext
    
    data class OpenSourceLibrary(
        val id: String,
        val name: String,
        val author: String,
        val license: String,
        val url: String,
        val version: String
    )
    
    @Composable
    private fun getLicenseBadgeColors(license: String): Pair<Color, Color> {
        val scheme = MaterialTheme.colorScheme
        return when {
            license.contains("Apache", true) -> scheme.primaryContainer to scheme.onPrimaryContainer
            license.contains("MIT", true) -> scheme.tertiaryContainer to scheme.onTertiaryContainer
            license.contains("GPL", true) -> scheme.errorContainer to scheme.onErrorContainer
            else -> scheme.secondaryContainer to scheme.onSecondaryContainer
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun LicensesScreen(onBackClick: () -> Unit) {
        val context = LocalContext.current
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    
        val unknownString = stringResource(R.string.unknown)
        val unknownLicenseString = stringResource(R.string.license_unknown)
    
        val libs = remember { Libs.Builder().withContext(context).build() }
    
        val allLibraries = remember(libs) {
            libs.libraries.map { lib ->
                OpenSourceLibrary(
                    id = lib.uniqueId,
                    name = lib.name,
                    author = lib.developers.firstOrNull()?.name
                        ?: lib.organization?.name
                        ?: unknownString,
                    license = lib.licenses.firstOrNull()?.name
                        ?: unknownLicenseString,
                    url = lib.website ?: lib.scm?.url ?: "",
                    version = lib.artifactVersion ?: ""
                )
            }.sortedBy { it.name.lowercase() }
        }
    
        var searchQuery by remember { mutableStateOf("") }
        var activeFilter by remember { mutableStateOf<String?>(null) }
    
        val licenseTypes = remember(allLibraries) {
            allLibraries.map { it.license }.distinct().sorted()
        }
    
        val filteredList = remember(allLibraries, searchQuery, activeFilter) {
            allLibraries.filter { lib ->
                val matchSearch = searchQuery.isBlank() ||
                        lib.name.contains(searchQuery, true) ||
                        lib.author.contains(searchQuery, true)
                val matchFilter = activeFilter == null || lib.license == activeFilter
                matchSearch && matchFilter
            }
        }
    
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.about_licenses_title),
                            fontWeight = FontWeight.SemiBold
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
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                        }
                    },
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    scrollBehavior = scrollBehavior
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        SearchBarCustom(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it }
                        )
    
                        Spacer(Modifier.height(16.dp))
    
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                val isSelected = activeFilter == null
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { activeFilter = null },
                                    label = { Text(stringResource(R.string.filter_all)) },
                                    leadingIcon = if (isSelected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    border = null,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                            items(licenseTypes) { type ->
                                val isSelected = activeFilter == type
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { activeFilter = if (isSelected) null else type },
                                    label = { Text(type) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    border = null,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }
                }
    
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.libraries_count_format, filteredList.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
    
                if (filteredList.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 64.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Rounded.SearchOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.no_libraries_found),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(filteredList, key = { it.id }) { lib ->
                        LibraryItemExpressive(
                            library = lib,
                            onClick = {
                                if (lib.url.isNotEmpty()) {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(lib.url))
                                    context.startActivity(intent)
                                }
                            }
                        )
                    }
                }
    
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    }
                }
            }
        }
    }
    
    @Composable
    fun SearchBarCustom(
        query: String,
        onQueryChange: (String) -> Unit
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(
                    stringResource(R.string.search_libraries_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = null)
                    }
                }
            } else null,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50)),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            singleLine = true
        )
    }
    
    @Composable
    fun LibraryItemExpressive(
        library: OpenSourceLibrary,
        onClick: () -> Unit
    ) {
        val (badgeBg, badgeContent) = getLicenseBadgeColors(library.license)
        val initial = library.name.firstOrNull()?.uppercase() ?: "?"
    
        ListItem(
            modifier = Modifier
                .clickable(onClick = onClick)
                .fillMaxWidth(),
            headlineContent = {
                Text(
                    text = library.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = library.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = badgeBg,
                            contentColor = badgeContent,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(20.dp),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            ) {
                                Text(
                                    text = library.license,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (library.version.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "v${library.version}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            },
            leadingContent = {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = initial,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            trailingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }


