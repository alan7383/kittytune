package com.alananasss.kittytune.ui.player.lyrics

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.lyrics.providers.PreferredLyricsProvider
import com.alananasss.kittytune.ui.common.ShimmerBox
import com.alananasss.kittytune.ui.common.ShimmerLine
import com.alananasss.kittytune.ui.player.ManualLyricsSearch
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.player.UnifiedLyricResult
import com.alananasss.kittytune.ui.utils.fadedHorizontalEdges
import com.alananasss.kittytune.utils.makeTimeString

/**
 * Lyrics search by hand.
 *
 * Opens on results already there: the search runs in the background once a track's lyrics are settled.
 * Each source's results join the list as soon as that source answers, with the right song first and the best
 * timings next, and the source chips show which are still searching.
 */
@Composable
fun SearchLyricsView(
    viewModel: PlayerViewModel,
    onCloseSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    var query by remember(viewModel.currentTrack?.id, viewModel.manualSearchQuery) {
        mutableStateOf(viewModel.manualSearchQuery)
    }
    val runSearch: (String) -> Unit = { source ->
        viewModel.searchLyricsManual(query, source)
        focusManager.clearFocus()
    }

    LaunchedEffect(viewModel.currentTrack?.id) {
        val nothingYet = viewModel.unifiedLyricSearchResults.isEmpty() && !viewModel.isManualSearchLoading
        if (nothingYet && query.isNotBlank()) viewModel.searchLyricsManual(query, viewModel.manualSearchProvider)
    }

    var showUpload by remember { mutableStateOf(false) }
    if (showUpload) {
        CustomLyricsDialog(viewModel = viewModel, onDismiss = { showUpload = false })
    }

    Column(modifier = modifier.fillMaxSize().entersGently(0.985f).background(MaterialTheme.colorScheme.surface)) {
        SearchField(
            query = query,
            onQueryChange = { query = it },
            onSearch = { runSearch(viewModel.manualSearchProvider) },
            onClose = onCloseSearch,
        )
        SourceChips(viewModel, onPick = runSearch)
        SearchStatus(viewModel)
        SearchResults(viewModel, onUpload = { showUpload = true })
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
    ) {
        IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_close), tint = MaterialTheme.colorScheme.onSurface)
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.lyrics_search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                AnimatedVisibility(query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                    IconButton(onClick = { onQueryChange("") }, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.lyrics_search_clear))
                    }
                }
            },
            singleLine = true,
            shape = CircleShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        )
    }
}

/** "All sources" and each source on its own; a source still answering spins in its chip. */
@Composable
private fun SourceChips(viewModel: PlayerViewModel, onPick: (String) -> Unit) {
    val sources = remember {
        val prefs = viewModel.playerPrefs
        val all = (prefs.getLyricsProviderOrder() + PreferredLyricsProvider.entries).distinct()
        val (enabled, disabled) = all.partition { prefs.getLyricsProviderEnabled(it) }
        enabled + disabled
    }
    val selected = viewModel.manualSearchProvider
    val isAll = selected.equals(ManualLyricsSearch.ALL, ignoreCase = true)
    val rowState = rememberLazyListState()

    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .fadedHorizontalEdges(state = rowState, fadeLength = 28.dp),
    ) {
        item(key = ManualLyricsSearch.ALL) {
            SourceChip(
                label = stringResource(R.string.lyrics_provider_all),
                selected = isAll,
                searching = isAll && viewModel.isManualSearchLoading,
                onClick = { onPick(ManualLyricsSearch.ALL) },
            )
        }
        items(sources, key = { it.name }) { source ->
            SourceChip(
                label = source.displayName,
                selected = !isAll && PreferredLyricsProvider.fromName(selected) == source,
                searching = source in viewModel.pendingLyricSources,
                onClick = { onPick(source.name) },
            )
        }
    }
}

@Composable
private fun SourceChip(label: String, selected: Boolean, searching: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        leadingIcon = when {
            searching -> {
                { CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(14.dp)) }
            }
            selected -> {
                { Icon(Icons.Rounded.Check, null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
            }
            else -> null
        },
        shape = CircleShape,
    )
}

/** Where the lyrics on screen came from, and how the search is going. */
@Composable
private fun SearchStatus(viewModel: PlayerViewModel) {
    val source = viewModel.currentLyricsSource?.let { PreferredLyricsProvider.fromName(it)?.displayName ?: it }
    val pending = viewModel.pendingLyricSources.size
    val found = viewModel.unifiedLyricSearchResults.size
    val parts = buildList {
        if (source != null) add(stringResource(R.string.lyrics_search_now_showing, source))
        if (pending > 0) add(stringResource(R.string.lyrics_search_progress, found, pending))
    }
    Text(
        text = parts.joinToString("  ·  "),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(horizontal = 20.dp, vertical = if (parts.isEmpty()) 2.dp else 8.dp),
    )
}

@Composable
private fun SearchResults(viewModel: PlayerViewModel, onUpload: () -> Unit) {
    val results = viewModel.unifiedLyricSearchResults
    val searching = viewModel.isManualSearchLoading
    val listState = rememberLazyListState()

    if (results.isEmpty() && !searching) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            UploadOwnLyricsButton(onUpload)
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(results, key = { it.provider + "/" + it.id + "/" + it.name }) { result ->
                ResultCard(
                    result = result,
                    onClick = { viewModel.selectUnifiedLyricResult(result) },
                    modifier = Modifier.animateItem(),
                )
            }
            if (searching) {
                items(PLACEHOLDER_ROWS, key = { "placeholder-$it" }) {
                    ResultPlaceholder(Modifier.animateItem())
                }
            }
            item(key = "upload-own") {
                Spacer(Modifier.height(8.dp))
                UploadOwnLyricsButton(onUpload)
            }
        }
    }
}

@Composable
private fun UploadOwnLyricsButton(onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.fillMaxWidth().height(44.dp),
    ) {
        Icon(Icons.Rounded.UploadFile, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.dialog_upload_yaml_title), fontWeight = FontWeight.SemiBold)
    }
}

private const val PLACEHOLDER_ROWS = 2

@Composable
private fun ResultCard(result: UnifiedLyricResult, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val wordSynced = result.hasWordSync
    val lineSynced = result.hasLineSync && !wordSynced
    val accent = when {
        wordSynced -> MaterialTheme.colorScheme.primary
        lineSynced -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }
    val container = when {
        wordSynced -> MaterialTheme.colorScheme.surfaceContainerHighest
        lineSynced -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = container,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClick = onClick),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
            Column(Modifier.weight(1f).padding(start = 14.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = result.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val details = listOfNotNull(
                            result.artistName.takeIf { it.isNotBlank() },
                            result.albumName?.takeIf { it.isNotBlank() },
                            makeTimeString((result.durationSec * 1000).toLong()).takeIf { result.durationSec > 0.0 },
                        )
                        Text(
                            text = details.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Badge(
                            text = PreferredLyricsProvider.fromName(result.provider)?.displayName ?: result.provider,
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            content = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        TimingBadge(result)
                    }
                }
                if (!result.previewText.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = result.previewText.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
                        style = MaterialTheme.typography.labelMedium.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.5f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TimingBadge(result: UnifiedLyricResult) {
    val (icon, label, synced) = when {
        result.hasWordSync -> Triple(Icons.Rounded.Lyrics, stringResource(R.string.lyrics_badge_word_sync), true)
        result.hasLineSync -> Triple(Icons.Rounded.Timer, stringResource(R.string.lyrics_badge_line_sync), true)
        else -> Triple(Icons.Rounded.Notes, stringResource(R.string.lyrics_badge_plain), false)
    }
    Badge(
        text = label,
        icon = icon,
        container = if (synced) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        content = if (synced) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Badge(text: String, container: Color, content: Color, icon: ImageVector? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, tint = content, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = content, maxLines = 1)
    }
}

@Composable
private fun ResultPlaceholder(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ShimmerLine(Modifier.fillMaxWidth(0.45f))
            ShimmerLine(Modifier.fillMaxWidth(0.3f).height(12.dp))
            ShimmerBox(Modifier.fillMaxWidth().height(30.dp))
        }
    }
}

/**
 * The search as a floating panel, for the full player where there is no lyrics screen to put it in.
 */
@Composable
fun SearchLyricsDialog(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .entersGently(1f)
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            val panelWidth = min(720.dp, maxWidth * 0.94f)
            val panelHeight = min(680.dp, maxHeight * 0.90f)
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .width(panelWidth)
                    .height(panelHeight)
                    .entersGently(0.93f)
                    .clip(RoundedCornerShape(28.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
            ) {
                SearchLyricsView(viewModel = viewModel, onCloseSearch = onDismiss)
            }
        }
    }
}

@Composable
private fun Modifier.entersGently(fromScale: Float): Modifier {
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
    return graphicsLayer {
        alpha = progress.value
        val scale = fromScale + (1f - fromScale) * progress.value
        scaleX = scale
        scaleY = scale
    }
}
