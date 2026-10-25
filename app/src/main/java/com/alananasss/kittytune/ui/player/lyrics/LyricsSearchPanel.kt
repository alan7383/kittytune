package com.alananasss.kittytune.ui.player.lyrics

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.PlayerBackgroundStyle
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.lyrics.providers.PreferredLyricsProvider
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.ShimmerBox
import com.alananasss.kittytune.ui.common.ShimmerLine
import com.alananasss.kittytune.ui.common.hapticAction
import com.alananasss.kittytune.ui.player.ManualLyricsSearch
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.player.UnifiedLyricResult
import com.alananasss.kittytune.ui.utils.fadedHorizontalEdges
import com.alananasss.kittytune.utils.makeTimeString

/**
 * Transparent, glassmorphism lyrics search overlay.
 *
 * Renders cleanly above the active player / lyrics background (fluid artwork, blurred cover, or mesh)
 * without an opaque modal box.
 */
@Composable
fun SearchLyricsView(
    viewModel: PlayerViewModel,
    onCloseSearch: () -> Unit,
    modifier: Modifier = Modifier,
    isThemeBg: Boolean? = null,
    applySystemInsets: Boolean = true,
) {
    val context = LocalContext.current
    val effectiveIsThemeBg = isThemeBg ?: remember(context) {
        PlayerPreferences(context).getPlayerStyle() == PlayerBackgroundStyle.THEME
    }

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

    // Atmospheric scrim gradient that guarantees text & card contrast while preserving
    // the dynamic background animations underneath.
    val surfaceColor = MaterialTheme.colorScheme.surface
    val scrimBrush = remember(effectiveIsThemeBg, surfaceColor) {
        if (effectiveIsThemeBg) {
            Brush.verticalGradient(
                listOf(
                    surfaceColor.copy(alpha = 0.90f),
                    surfaceColor.copy(alpha = 0.96f)
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color.Black.copy(alpha = 0.45f),
                    Color.Black.copy(alpha = 0.25f),
                    Color.Black.copy(alpha = 0.55f)
                )
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scrimBrush)
            .then(if (applySystemInsets) Modifier.statusBarsPadding() else Modifier)
    ) {
        SearchField(
            query = query,
            onQueryChange = { query = it },
            onSearch = { runSearch(viewModel.manualSearchProvider) },
            onClose = {
                focusManager.clearFocus()
                onCloseSearch()
            },
            isThemeBg = effectiveIsThemeBg
        )

        SourceChips(
            viewModel = viewModel,
            onPick = runSearch,
            isThemeBg = effectiveIsThemeBg
        )

        SearchStatus(
            viewModel = viewModel,
            isThemeBg = effectiveIsThemeBg
        )

        SearchResults(
            viewModel = viewModel,
            onUpload = { showUpload = true },
            isThemeBg = effectiveIsThemeBg
        )
    }
}

/**
 * Modern frosted glass search header bar.
 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClose: () -> Unit,
    isThemeBg: Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val textColor = if (isThemeBg) MaterialTheme.colorScheme.onSurface else Color.White
    val placeholderColor = if (isThemeBg) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    } else {
        Color.White.copy(alpha = 0.5f)
    }
    val iconTint = if (isThemeBg) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = 0.75f)

    val searchBarBg = if (isThemeBg) {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
    } else {
        Color.White.copy(alpha = if (isFocused) 0.16f else 0.12f)
    }

    val searchBarBorder = if (isFocused) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    } else {
        if (isThemeBg) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        else Color.White.copy(alpha = 0.18f)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
    ) {
        // Frosted Glass Circle Back Button
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    if (isThemeBg) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f)
                    else Color.White.copy(alpha = 0.12f)
                )
                .border(
                    1.dp,
                    if (isThemeBg) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    else Color.White.copy(alpha = 0.18f),
                    CircleShape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, radius = 21.dp),
                    onClick = hapticAction(HapticKind.UI) { onClose() }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.common_close),
                tint = textColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(10.dp))

        // Frosted Glass Pill Search Box
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .height(44.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = textColor,
                fontWeight = FontWeight.Medium
            ),
            singleLine = true,
            cursorBrush = SolidColor(if (isThemeBg) MaterialTheme.colorScheme.primary else Color.White),
            interactionSource = interactionSource,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(searchBarBg)
                        .border(1.dp, searchBarBorder, CircleShape)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(19.dp)
                    )

                    Spacer(Modifier.width(10.dp))

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.lyrics_search_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = placeholderColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }

                    AnimatedVisibility(
                        visible = query.isNotEmpty(),
                        enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.8f),
                        exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.8f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isThemeBg) MaterialTheme.colorScheme.surfaceVariant
                                    else Color.White.copy(alpha = 0.20f)
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = true, radius = 12.dp),
                                    onClick = hapticAction(HapticKind.UI) { onQueryChange("") }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.lyrics_search_clear),
                                tint = textColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        )
    }
}

/**
 * Filter row for lyrics sources with smooth pill glass design.
 */
@Composable
private fun SourceChips(
    viewModel: PlayerViewModel,
    onPick: (String) -> Unit,
    isThemeBg: Boolean
) {
    val context = LocalContext.current
    val sources = ManualLyricsSearch.availableProviders(viewModel.playerPrefs, context)
    val selected = viewModel.manualSearchProvider
    val isAll = selected.equals(ManualLyricsSearch.ALL, ignoreCase = true)
    val selectedProvider = PreferredLyricsProvider.fromName(selected)

    LaunchedEffect(sources, selected) {
        if (!isAll && selectedProvider != null && selectedProvider !in sources) {
            onPick(ManualLyricsSearch.ALL)
        }
    }
    val rowState = rememberLazyListState()

    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .fadedHorizontalEdges(state = rowState, fadeLength = 24.dp),
    ) {
        item(key = ManualLyricsSearch.ALL) {
            GlassSourceChip(
                label = stringResource(R.string.lyrics_provider_all),
                selected = isAll,
                searching = isAll && viewModel.isManualSearchLoading,
                onClick = hapticAction(HapticKind.UI) { onPick(ManualLyricsSearch.ALL) },
                isThemeBg = isThemeBg
            )
        }
        items(sources, key = { it.name }) { source ->
            GlassSourceChip(
                label = source.displayName,
                selected = !isAll && PreferredLyricsProvider.fromName(selected) == source,
                searching = source in viewModel.pendingLyricSources,
                onClick = hapticAction(HapticKind.UI) { onPick(source.name) },
                isThemeBg = isThemeBg
            )
        }
    }
}

@Composable
private fun GlassSourceChip(
    label: String,
    selected: Boolean,
    searching: Boolean,
    onClick: () -> Unit,
    isThemeBg: Boolean
) {
    val bgAnim by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primary
            isThemeBg -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f)
            else -> Color.White.copy(alpha = 0.10f)
        },
        animationSpec = tween(200),
        label = "chipBg"
    )

    val contentColorAnim by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.onPrimary
            isThemeBg -> MaterialTheme.colorScheme.onSurface
            else -> Color.White.copy(alpha = 0.90f)
        },
        animationSpec = tween(200),
        label = "chipContent"
    )

    val borderAnim by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primary
            isThemeBg -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            else -> Color.White.copy(alpha = 0.15f)
        },
        animationSpec = tween(200),
        label = "chipBorder"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .height(34.dp)
            .clip(CircleShape)
            .background(bgAnim)
            .border(1.dp, borderAnim, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick
            )
            .padding(horizontal = 14.dp)
    ) {
        if (searching) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = contentColorAnim,
                modifier = Modifier
                    .size(13.dp)
                    .padding(end = 2.dp)
            )
            Spacer(Modifier.width(6.dp))
        } else if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = contentColorAnim,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(5.dp))
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColorAnim,
            maxLines = 1
        )
    }
}

/**
 * Elegant status and progress indicator row.
 */
@Composable
private fun SearchStatus(viewModel: PlayerViewModel, isThemeBg: Boolean) {
    val source = viewModel.currentLyricsSource?.let { PreferredLyricsProvider.fromName(it)?.displayName ?: it }
    val pending = viewModel.pendingLyricSources.size
    val found = viewModel.unifiedLyricSearchResults.size
    val isSearching = viewModel.isManualSearchLoading

    val textColor = if (isThemeBg) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = 0.70f)
    val highlightColor = if (isThemeBg) MaterialTheme.colorScheme.primary else Color.White

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = if (isSearching) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.8f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            if (source != null) {
                Text(
                    text = stringResource(R.string.lyrics_search_now_showing, source),
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = stringResource(R.string.lyrics_searching),
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (found > 0 || pending > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (pending > 0) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = if (pending > 0) "$found found · $pending searching" else "$found found",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (pending > 0) MaterialTheme.colorScheme.primary else highlightColor.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun SearchResults(
    viewModel: PlayerViewModel,
    onUpload: () -> Unit,
    isThemeBg: Boolean
) {
    val results = viewModel.unifiedLyricSearchResults
    val searching = viewModel.isManualSearchLoading
    val listState = rememberLazyListState()

    if (results.isEmpty() && !searching) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(
                        if (isThemeBg) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)
                        else Color.White.copy(alpha = 0.10f)
                    )
                    .border(
                        1.dp,
                        if (isThemeBg) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        else Color.White.copy(alpha = 0.15f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SearchOff,
                    contentDescription = null,
                    tint = if (isThemeBg) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.no_results),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isThemeBg) MaterialTheme.colorScheme.onSurface else Color.White,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.lyrics_no_data),
                style = MaterialTheme.typography.bodySmall,
                color = if (isThemeBg) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = 0.65f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            UploadOwnLyricsButton(onClick = onUpload, isThemeBg = isThemeBg)
        }
        return
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 6.dp,
            bottom = 36.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        items(
            items = results,
            key = { it.provider + "/" + it.id + "/" + it.name }
        ) { result ->
            val isActive = viewModel.currentLyricsSource?.equals(result.provider, ignoreCase = true) == true
            ResultCard(
                result = result,
                isActive = isActive,
                onClick = hapticAction(HapticKind.UI) { viewModel.selectUnifiedLyricResult(result) },
                isThemeBg = isThemeBg,
                modifier = Modifier.animateItem(),
            )
        }

        if (searching) {
            items(PLACEHOLDER_ROWS, key = { "placeholder-$it" }) {
                ResultPlaceholder(isThemeBg = isThemeBg, modifier = Modifier.animateItem())
            }
        }

        item(key = "upload-own") {
            Spacer(Modifier.height(4.dp))
            UploadOwnLyricsButton(onClick = onUpload, isThemeBg = isThemeBg)
        }
    }
}

/**
 * Translucent Glass Card displaying lyrics result with rich media icons and sync badges.
 */
@Composable
private fun ResultCard(
    result: UnifiedLyricResult,
    isActive: Boolean,
    onClick: () -> Unit,
    isThemeBg: Boolean,
    modifier: Modifier = Modifier
) {
    val wordSynced = result.hasWordSync
    val lineSynced = result.hasLineSync && !wordSynced

    val textColor = if (isThemeBg) MaterialTheme.colorScheme.onSurface else Color.White
    val textMutedColor = if (isThemeBg) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = 0.70f)

    val colorScheme = MaterialTheme.colorScheme
    val cardBrush = remember(wordSynced, lineSynced, isThemeBg, colorScheme) {
        when {
            wordSynced -> {
                if (isThemeBg) {
                    Brush.horizontalGradient(
                        listOf(
                            colorScheme.primaryContainer.copy(alpha = 0.35f),
                            colorScheme.surfaceContainerHigh.copy(alpha = 0.8f)
                        )
                    )
                } else {
                    Brush.horizontalGradient(
                        listOf(
                            colorScheme.primary.copy(alpha = 0.22f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    )
                }
            }
            lineSynced -> {
                SolidColor(
                    if (isThemeBg) colorScheme.surfaceContainerHigh.copy(alpha = 0.75f)
                    else Color.White.copy(alpha = 0.09f)
                )
            }
            else -> {
                SolidColor(
                    if (isThemeBg) colorScheme.surfaceContainer.copy(alpha = 0.65f)
                    else Color.White.copy(alpha = 0.06f)
                )
            }
        }
    }

    val borderColor = when {
        isActive -> MaterialTheme.colorScheme.primary
        wordSynced -> MaterialTheme.colorScheme.primary.copy(alpha = if (isThemeBg) 0.5f else 0.45f)
        lineSynced -> if (isThemeBg) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.16f)
        else -> if (isThemeBg) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.10f)
    }

    val cardShape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(cardBrush)
            .border(1.dp, borderColor, cardShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick
            )
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Leading Icon Capsule (46 x 46 dp)
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            when {
                                wordSynced -> Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                )
                                lineSynced -> SolidColor(
                                    if (isThemeBg) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                    else Color.White.copy(alpha = 0.14f)
                                )
                                else -> SolidColor(
                                    if (isThemeBg) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    else Color.White.copy(alpha = 0.08f)
                                )
                            }
                        )
                        .border(
                            1.dp,
                            when {
                                wordSynced -> Color.Transparent
                                lineSynced -> if (isThemeBg) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.18f)
                                else -> if (isThemeBg) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.10f)
                            },
                            RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val iconVector = when {
                        wordSynced -> Icons.Rounded.Lyrics
                        lineSynced -> Icons.Rounded.Timer
                        else -> Icons.AutoMirrored.Rounded.Notes
                    }
                    val iconTint = when {
                        wordSynced -> MaterialTheme.colorScheme.onPrimary
                        lineSynced -> if (isThemeBg) MaterialTheme.colorScheme.primary else Color.White
                        else -> if (isThemeBg) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = 0.75f)
                    }
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                // Title and Metadata
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = result.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = textColor,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isActive) {
                            Spacer(Modifier.width(6.dp))
                            ActiveIndicator()
                        }
                    }

                    Spacer(Modifier.height(2.dp))

                    val details = listOfNotNull(
                        result.artistName.takeIf { it.isNotBlank() },
                        result.albumName?.takeIf { it.isNotBlank() },
                        makeTimeString((result.durationSec * 1000).toLong()).takeIf { result.durationSec > 0.0 }
                    )
                    Text(
                        text = details.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = textMutedColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Badges Column
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val providerDisplayName = PreferredLyricsProvider.fromName(result.provider)?.displayName ?: result.provider
                    ProviderBadge(text = providerDisplayName, isThemeBg = isThemeBg)
                    TimingBadge(result = result, isThemeBg = isThemeBg)
                }
            }

            // Lyric Preview Quote Snippet
            val snippet = result.previewText?.lineSequence()?.firstOrNull { it.isNotBlank() }
            if (!snippet.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isThemeBg) MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f)
                            else Color.White.copy(alpha = 0.06f)
                        )
                        .border(
                            1.dp,
                            if (isThemeBg) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                            else Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "“$snippet”",
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = textMutedColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(10.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = "Active",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ProviderBadge(text: String, isThemeBg: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(
                if (isThemeBg) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                else Color.White.copy(alpha = 0.12f)
            )
            .border(
                1.dp,
                if (isThemeBg) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                else Color.White.copy(alpha = 0.15f),
                RoundedCornerShape(7.dp)
            )
            .padding(horizontal = 7.dp, vertical = 2.5.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            fontWeight = FontWeight.SemiBold,
            color = if (isThemeBg) MaterialTheme.colorScheme.onSecondaryContainer else Color.White,
            maxLines = 1
        )
    }
}

@Composable
private fun TimingBadge(result: UnifiedLyricResult, isThemeBg: Boolean) {
    val (icon, label) = when {
        result.hasWordSync -> Pair(Icons.Rounded.Lyrics, stringResource(R.string.lyrics_badge_word_sync))
        result.hasLineSync -> Pair(Icons.Rounded.Timer, stringResource(R.string.lyrics_badge_line_sync))
        else -> Pair(Icons.AutoMirrored.Rounded.Notes, stringResource(R.string.lyrics_badge_plain))
    }

    val containerColor = when {
        result.hasWordSync -> MaterialTheme.colorScheme.primary.copy(alpha = if (isThemeBg) 0.25f else 0.28f)
        result.hasLineSync -> if (isThemeBg) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.12f)
        else -> if (isThemeBg) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f)
    }

    val contentColor = when {
        result.hasWordSync -> if (isThemeBg) MaterialTheme.colorScheme.primary else Color.White
        result.hasLineSync -> if (isThemeBg) MaterialTheme.colorScheme.onTertiaryContainer else Color.White.copy(alpha = 0.9f)
        else -> if (isThemeBg) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = 0.7f)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(containerColor)
            .border(
                1.dp,
                contentColor.copy(alpha = 0.2f),
                RoundedCornerShape(7.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(11.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1
        )
    }
}

/**
 * Frosted glass action button for uploading custom lyrics.
 */
@Composable
private fun UploadOwnLyricsButton(onClick: () -> Unit, isThemeBg: Boolean) {
    val cardShape = RoundedCornerShape(18.dp)
    val cardBg = if (isThemeBg) {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)
    } else {
        Color.White.copy(alpha = 0.09f)
    }
    val borderColor = if (isThemeBg) {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    } else {
        Color.White.copy(alpha = 0.18f)
    }
    val contentColor = if (isThemeBg) MaterialTheme.colorScheme.onSurface else Color.White
    val textMutedColor = if (isThemeBg) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = 0.70f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(cardBg)
            .border(1.dp, borderColor, cardShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    if (isThemeBg) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                )
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.UploadFile,
                contentDescription = null,
                tint = if (isThemeBg) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.dialog_upload_yaml_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Load custom .lrc, .yaml or .txt file",
                style = MaterialTheme.typography.bodySmall,
                color = textMutedColor
            )
        }

        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = textMutedColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

private const val PLACEHOLDER_ROWS = 2

@Composable
private fun ResultPlaceholder(isThemeBg: Boolean, modifier: Modifier = Modifier) {
    val cardShape = RoundedCornerShape(20.dp)
    val cardBg = if (isThemeBg) {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
    } else {
        Color.White.copy(alpha = 0.06f)
    }
    val borderColor = if (isThemeBg) {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
    } else {
        Color.White.copy(alpha = 0.10f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(cardBg)
            .border(1.dp, borderColor, cardShape)
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            ShimmerBox(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
            )

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ShimmerLine(Modifier.fillMaxWidth(0.6f))
                ShimmerLine(Modifier.fillMaxWidth(0.4f).height(12.dp))
            }

            Spacer(Modifier.width(12.dp))

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ShimmerBox(Modifier.width(54.dp).height(18.dp).clip(RoundedCornerShape(7.dp)))
                ShimmerBox(Modifier.width(68.dp).height(18.dp).clip(RoundedCornerShape(7.dp)))
            }
        }
    }
}

/**
 * The search as a floating panel fallback (e.g. for desktop or tablet views).
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
            .background(Color.Black.copy(alpha = 0.6f))
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
            Box(
                modifier = Modifier
                    .width(panelWidth)
                    .height(panelHeight)
                    .entersGently(0.93f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(28.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
            ) {
                SearchLyricsView(
                    viewModel = viewModel,
                    onCloseSearch = onDismiss,
                    isThemeBg = true,
                    applySystemInsets = false
                )
            }
        }
    }
}

@Composable
private fun Modifier.entersGently(fromScale: Float): Modifier {
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        alpha = progress.value
        val scale = fromScale + (1f - fromScale) * progress.value
        scaleX = scale
        scaleY = scale
    }
}
