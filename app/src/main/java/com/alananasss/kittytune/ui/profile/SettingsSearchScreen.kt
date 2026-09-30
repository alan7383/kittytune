package com.alananasss.kittytune.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsHighlightManager
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.player.PlayerViewModel

/**
 * Dedicated settings search screen, reproducing AOSP Settings / Settings Intelligence 1:1.
 *
 * Zero-state behaviour (expressive variant, API 36):
 *  - If no recent items: expressive scalloped badge illustration.
 *  - If recents exist: "Recent search results" header + "Clear" TextButton, then the actual
 *    setting items that were previously clicked (same SettingsItem rows, same icon), followed
 *    by a "Clear history" row (✕ icon + label in colorSecondary) at the bottom.
 *
 * On result click: records the clicked [SearchSettingEntry] to history then navigates/acts.
 */
@Composable
fun SettingsSearchScreen(
    navController: NavController,
    onBackClick: () -> Unit,
    playerViewModel: PlayerViewModel
) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var preferenceVersion by remember { mutableIntStateOf(0) }

    // Live recent-searches state — refreshed on every add/remove
    var recentItems by remember { mutableStateOf(prefs.getSettingsRecentSearches()) }

    val allSearchItems = rememberSettingsSearchCatalog(
        navController = navController,
        playerViewModel = playerViewModel,
        preferenceVersion = preferenceVersion,
        onPreferenceChange = { preferenceVersion++ }
    )

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    BackHandler {
        focusManager.clearFocus()
        onBackClick()
    }

    val normalizedQuery = remember(searchQuery) { normalizeSearchText(searchQuery) }
    val queryTokens = remember(normalizedQuery) {
        normalizedQuery.split(WHITESPACE_REGEX).filter { it.isNotBlank() }
    }

    val matches = remember(normalizedQuery, queryTokens, allSearchItems) {
        if (queryTokens.isEmpty()) {
            emptyList()
        } else {
            allSearchItems
                .mapNotNull { item ->
                    if (queryTokens.all { token -> item.searchCorpus.contains(token) }) {
                        val score = when {
                            item.normTitle == normalizedQuery -> 100
                            item.normTitle.startsWith(normalizedQuery) -> 80
                            item.normTitle.contains(normalizedQuery) -> 60
                            queryTokens.all { item.normTitle.contains(it) } -> 50
                            item.normKeywords.any { it == normalizedQuery } -> 40
                            item.normKeywords.any { it.startsWith(normalizedQuery) } -> 30
                            item.normKeywords.any { it.contains(normalizedQuery) } -> 20
                            else -> 10
                        }
                        item to score
                    } else null
                }
                .sortedWith(
                    compareByDescending<Pair<SearchSettingEntry, Int>> { it.second }
                        .thenBy { it.first.title }
                )
                .map { it.first }
        }
    }

    /** Records the clicked item to recent history then navigates/acts. */
    fun onResultClick(item: SearchSettingEntry) {
        val entry = PlayerPreferences.RecentSettingsEntry(
            title = item.title,
            subtitle = item.subtitle,
            categoryName = item.categoryName,
            route = item.route,
            highlightKey = item.highlightKey,
            iconRes = item.iconRes
        )
        prefs.addSettingsRecentSearch(entry)
        recentItems = prefs.getSettingsRecentSearches()

        if (item.highlightKey != null) {
            SettingsHighlightManager.setHighlightKey(item.highlightKey)
        }
        if (item.onClick != null) {
            item.onClick.invoke()
        } else if (item.route != null) {
            navController.navigate(item.route)
        }
    }

    /** Navigates directly from a recent item without re-searching. */
    fun onRecentClick(entry: PlayerPreferences.RecentSettingsEntry) {
        val catalogItem = allSearchItems.find { it.title == entry.title }
        val highlightKey = entry.highlightKey ?: catalogItem?.highlightKey
        if (highlightKey != null) {
            SettingsHighlightManager.setHighlightKey(highlightKey)
        }
        if (catalogItem?.onClick != null) {
            catalogItem.onClick.invoke()
        } else if (entry.route != null) {
            navController.navigate(entry.route)
        } else if (catalogItem?.route != null) {
            navController.navigate(catalogItem.route)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            focusManager.clearFocus()
                            onBackClick()
                        },
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.btn_back),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { focusManager.clearFocus() }
                            ),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.homepage_search),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Normal
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }

                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.btn_clear),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when {
                // ── Zero-state: query is blank ──────────────────────────────────────────
                searchQuery.isBlank() -> {
                    if (recentItems.isEmpty()) {
                        // No history yet → expressive illustration
                        SettingsZeroStateIllustration(
                            title = stringResource(R.string.search_no_recent_results),
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Recent items list (SettingsComponents parity)
                        RecentSettingsList(
                            recentItems = recentItems,
                            allSearchItems = allSearchItems,
                            onItemClick = { onRecentClick(it) },
                            onClearAll = {
                                prefs.clearSettingsRecentSearches()
                                recentItems = emptyList()
                            }
                        )
                    }
                }

                // ── No matches ─────────────────────────────────────────────────────────
                matches.isEmpty() -> {
                    SettingsZeroStateIllustration(
                        title = stringResource(R.string.search_suggestion_no_match, searchQuery),
                        subtitle = stringResource(R.string.settings_search_no_results),
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // ── Results list ───────────────────────────────────────────────────────
                else -> {
                    val grouped = remember(matches) { matches.groupBy { it.categoryName } }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp)
                    ) {
                        grouped.forEach { (catName, itemsInCat) ->
                            item(key = "search-cat-$catName") {
                                SettingsGroup(
                                    title = catName,
                                    items = itemsInCat.map { searchItem ->
                                        { shape ->
                                            val hasAction = searchItem.onClick != null || searchItem.route != null
                                            SettingsItem(
                                                shape = shape,
                                                title = searchItem.title,
                                                subtitle = searchItem.subtitle,
                                                icon = searchItem.icon ?: (if (searchItem.iconRes == null) Icons.Rounded.Settings else null),
                                                iconRes = searchItem.iconRes,
                                                hasSwitch = searchItem.hasSwitch,
                                                switchState = searchItem.switchState,
                                                onSwitchChange = searchItem.onSwitchChange,
                                                onClick = if (hasAction) {
                                                    { onResultClick(searchItem) }
                                                } else null
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Recent items list (SettingsComponents parity) ───────────────────────────

/**
 * Zero-state list of recently-accessed settings items.
 *
 * Uses the exact same card container ("cases") design as Kittytune's SettingsComponents:
 *  1. Header with "Recent search results" + "Clear" action button
 *  2. SettingsGroup containing SettingsItem cards with rounded corners (24dp),
 *     circular colored icon badge, title, subtitle / breadcrumb, and navigation arrow
 *  3. "Clear history" button at the bottom
 */
@Composable
private fun RecentSettingsList(
    recentItems: List<PlayerPreferences.RecentSettingsEntry>,
    allSearchItems: List<SearchSettingEntry>,
    onItemClick: (PlayerPreferences.RecentSettingsEntry) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp)
    ) {
        // ── Header: Recent search results + Clear button ─────────────────────────
        item(key = "recent-header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.settings_search_recent_header),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp)
                )
                TextButton(
                    onClick = onClearAll,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_search_clear_recent),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // ── Setting item cards — exact same SettingsGroup & SettingsItem as normal settings ──
        item(key = "recent-group") {
            SettingsGroup(
                items = recentItems.map { entry ->
                    { shape ->
                        val catalogEntry = allSearchItems.find { it.title == entry.title }
                        val subtitleText = when {
                            !entry.subtitle.isNullOrBlank() -> "${entry.subtitle} • ${entry.categoryName}"
                            else -> entry.categoryName
                        }
                        val iconVector = catalogEntry?.icon ?: (if (entry.iconRes == null && catalogEntry?.iconRes == null) Icons.Rounded.Settings else null)
                        val iconDrawable = catalogEntry?.iconRes ?: entry.iconRes
                        SettingsItem(
                            shape = shape,
                            title = entry.title,
                            subtitle = subtitleText,
                            icon = iconVector,
                            iconRes = iconDrawable,
                            onClick = { onItemClick(entry) }
                        )
                    }
                }
            )
        }
    }
}

// ─── Zero-state illustration ───────────────────────────────────────────────────

/**
 * 1:1 copy of AOSP Expressive Zero-State illustration (search_zero_state_expressive.xml):
 * - Scalloped 8-petal polygon badge (settingslib_expressive_zerostate_background)
 * - Centered search icon (ic_search_24dp)
 * - Title and optional subtitle below
 */
@Composable
fun SettingsZeroStateIllustration(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(160.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.settingslib_expressive_zerostate_background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                tint = MaterialTheme.colorScheme.surfaceContainerHigh
            )
            Icon(
                painter = painterResource(R.drawable.ic_search_24dp),
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        if (subtitle != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
