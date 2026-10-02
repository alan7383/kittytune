package com.alananasss.kittytune.ui.home

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.GenreData
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.mix.MixEngine
import com.alananasss.kittytune.ui.player.PlaybackContext
import com.alananasss.kittytune.ui.player.PlayerViewModel
import kotlinx.coroutines.launch

/** One mood option on the card. */
private data class VibeStation(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val recipe: MixEngine.Recipe
)

/** What the card is doing. Not an enum, because two of the four carry something to say. */
private sealed interface MixState {
    data object Idle : MixState
    data object Building : MixState
    data class Done(val count: Int, val from: String?) : MixState
    data class Empty(val message: String) : MixState
}

/**
 * "Ton mix" / "Start mixing" card on the home screen.
 *
 * Implements recommendation logic:
 * - Samples from real listening history (collaborative filtering via related tracks, artist stations, genre queries)
 * - Excludes already liked / familiar tracks
 * - Multi-station mood selector (My Vibe, Rock, Sad, Rap, Dance, Pop, Like Artist)
 * - Customization dialog with trusted sources toggle, artist style search, and tag search
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StartMixingCard(
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<MixState>(MixState.Idle) }
    var showOptions by remember { mutableStateOf(false) }

    val basis by produceState<MixEngine.Basis?>(initialValue = null) {
        value = MixEngine.basis()
    }

    val topArtist = basis?.topArtists?.firstOrNull()
    val stations = remember(topArtist, context) {
        buildList {
            add(
                VibeStation(
                    id = "my_vibe",
                    title = context.getString(R.string.mix_vibe_my_taste),
                    subtitle = context.getString(R.string.mix_vibe_my_taste_sub),
                    icon = Icons.Rounded.AutoAwesome,
                    recipe = MixEngine.Recipe.MyTaste
                )
            )
            add(
                VibeStation(
                    id = "rock",
                    title = context.getString(R.string.mix_vibe_rock),
                    subtitle = context.getString(R.string.mix_vibe_rock_sub),
                    icon = Icons.Rounded.ElectricBolt,
                    recipe = MixEngine.Recipe.InGenre("rock")
                )
            )
            add(
                VibeStation(
                    id = "sad",
                    title = context.getString(R.string.mix_vibe_sad),
                    subtitle = context.getString(R.string.mix_vibe_sad_sub),
                    icon = Icons.Rounded.WaterDrop,
                    recipe = MixEngine.Recipe.InGenre("sad")
                )
            )
            add(
                VibeStation(
                    id = "rap",
                    title = context.getString(R.string.mix_vibe_rap),
                    subtitle = context.getString(R.string.mix_vibe_rap_sub),
                    icon = Icons.Rounded.Mic,
                    recipe = MixEngine.Recipe.InGenre("hiphop")
                )
            )
            add(
                VibeStation(
                    id = "dance",
                    title = context.getString(R.string.mix_vibe_dance),
                    subtitle = context.getString(R.string.mix_vibe_dance_sub),
                    icon = Icons.Rounded.MusicNote,
                    recipe = MixEngine.Recipe.InGenre("electronic")
                )
            )
            add(
                VibeStation(
                    id = "pop",
                    title = context.getString(R.string.mix_vibe_pop),
                    subtitle = context.getString(R.string.mix_vibe_pop_sub),
                    icon = Icons.Filled.Favorite,
                    recipe = MixEngine.Recipe.InGenre("pop")
                )
            )
            if (topArtist != null) {
                add(
                    VibeStation(
                        id = "artist",
                        title = context.getString(R.string.mix_vibe_artist, topArtist.artistName),
                        subtitle = "",
                        icon = Icons.Rounded.Person,
                        recipe = MixEngine.Recipe.LikeArtist(topArtist.artistId, topArtist.artistName)
                    )
                )
            }
        }
    }

    var selectedStationIndex by remember { mutableIntStateOf(0) }
    var playingStationIndex by remember { mutableStateOf<Int?>(null) }
    val currentStation = stations.getOrElse(selectedStationIndex) { stations.first() }

    val isMixActive = playerViewModel.isYourMixActive
    val isMixPlaying = isMixActive && playerViewModel.isPlaying

    fun start(recipe: MixEngine.Recipe) {
        if (state is MixState.Building) return
        state = MixState.Building
        scope.launch {
            val result = MixEngine.mix(recipe)
            state = when (result) {
                is MixEngine.Result.Mixed -> {
                    val mixContext = PlaybackContext(
                        displayText = context.getString(R.string.mix_title),
                        navigationId = "your_mix",
                        imageUrl = null,
                        artistName = null
                    )
                    playerViewModel.playPlaylist(result.tracks, 0, context = mixContext)
                    MixState.Done(result.tracks.size, result.describedBy.takeIf { it.isNotBlank() })
                }
                MixEngine.Result.NotEnoughHistory ->
                    MixState.Empty(context.getString(R.string.mix_needs_history))
                is MixEngine.Result.NothingFound -> MixState.Empty(
                    when (result.stage) {
                        MixEngine.Result.Stage.NO_SEEDS -> context.getString(R.string.mix_no_seeds)
                        MixEngine.Result.Stage.NO_CANDIDATES -> context.getString(R.string.mix_nothing_found)
                        MixEngine.Result.Stage.ALL_FILTERED -> context.getString(R.string.mix_all_known)
                    }
                )
            }
        }
    }

    if (showOptions) {
        MixOptionsDialog(
            onDismiss = { showOptions = false },
            onPick = { recipe ->
                showOptions = false
                start(recipe)
            }
        )
    }

    val controlsPlayingMix = isMixActive && (playingStationIndex == null || playingStationIndex == selectedStationIndex)
    val statusText = when (val current = state) {
        is MixState.Empty -> current.message
        is MixState.Done ->
            if (current.from == null) context.getString(R.string.mix_track_count, current.count)
            else context.getString(R.string.mix_done, current.count, current.from)
        MixState.Building -> context.getString(R.string.mix_building)
        MixState.Idle -> if (isMixActive) {
            if (isMixPlaying) context.getString(R.string.mix_playing) else context.getString(R.string.mix_resume)
        } else context.getString(R.string.mix_card_subtitle)
    }
    val statusIsNews = state is MixState.Done || (state is MixState.Idle && isMixActive)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .animateContentSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.mix_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (statusIsNews) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FilledTonalIconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        showOptions = true
                    },
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = stringResource(R.string.mix_customise)
                    )
                }
            }

            CompositionLocalProvider(
                LocalMinimumInteractiveComponentSize provides Dp.Unspecified
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    stations.forEachIndexed { index, station ->
                        val chipInteraction = remember { MutableInteractionSource() }
                        FilterChip(
                            selected = index == selectedStationIndex,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                selectedStationIndex = index
                            },
                            interactionSource = chipInteraction,
                            shape = RoundedCornerShape(10.dp),
                            label = { Text(station.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = {
                                Icon(
                                    imageVector = station.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        )
                    }
                }
            }

            basis?.takeIf { it.topArtists.isNotEmpty() }?.let { MixBasisRow(it) }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        if (controlsPlayingMix) {
                            playerViewModel.togglePlayPause()
                        } else {
                            playingStationIndex = selectedStationIndex
                            start(currentStation.recipe)
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                    enabled = state !is MixState.Building
                ) {
                    when {
                        state is MixState.Building -> CircularWavyProgressIndicator(
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                            color = androidx.compose.material3.LocalContentColor.current
                        )
                        controlsPlayingMix && isMixPlaying ->
                            Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        else ->
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    }
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(
                        when {
                            !controlsPlayingMix -> stringResource(R.string.mix_enable)
                            isMixPlaying -> stringResource(R.string.mix_playing)
                            else -> stringResource(R.string.mix_resume)
                        }
                    )
                }
                if (currentStation.subtitle.isNotBlank()) {
                    Text(
                        text = currentStation.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * The artists the mix is leaning on: overlapping avatars and names.
 */
@Composable
private fun MixBasisRow(basis: MixEngine.Basis) {
    val ring = MaterialTheme.colorScheme.surfaceContainer
    val names = basis.topArtists.joinToString(", ") { it.artistName }
    val rest = (basis.artistCount - basis.topArtists.size).coerceAtLeast(0)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            basis.topArtists.forEachIndexed { index, artist ->
                Box(
                    modifier = Modifier
                        .padding(start = (index * 15).dp)
                        .size(24.dp)
                        .border(2.dp, ring, CircleShape)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (artist.artworkUrl.isNullOrBlank()) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                    } else {
                        AsyncImage(
                            model = artist.artworkUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.mix_basis, names).let {
                if (rest > 0) "$it · ${stringResource(R.string.mix_basis_more, rest)}" else it
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Dialog to customize the mix by artist or genre, with trusted sources filter.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MixOptionsDialog(
    onDismiss: () -> Unit,
    onPick: (MixEngine.Recipe) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val playerPrefs = remember { PlayerPreferences(context) }
    var prioritizeTrusted by remember { mutableStateOf(playerPrefs.getMixPrioritizeTrusted()) }
    var artist by remember { mutableStateOf("") }
    var genreQuery by remember { mutableStateOf("") }

    val all = remember { GenreData.getGenres(context) }
    val needle = genreQuery.trim()
    val matches = remember(needle, all) {
        if (needle.isEmpty()) all
        else all.filter { it.title.contains(needle, true) || it.query.contains(needle, true) }
    }
    val custom = needle.takeIf {
        it.isNotEmpty() && all.none { known -> known.title.equals(it, true) || known.query.equals(it, true) }
    }

    fun startArtist() {
        if (artist.isNotBlank()) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            focusManager.clearFocus()
            onPick(
                MixEngine.Recipe.LikeArtist(
                    artistId = null,
                    artistName = artist.trim()
                )
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 520.dp)
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.mix_customise),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.mix_customise_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onDismiss()
                        },
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.btn_close))
                    }
                }

                Spacer(Modifier.height(18.dp))
                val cardShape = RoundedCornerShape(18.dp)
                Card(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        val next = !prioritizeTrusted
                        prioritizeTrusted = next
                        playerPrefs.setMixPrioritizeTrusted(next)
                    },
                    shape = cardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(cardShape)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Verified,
                                contentDescription = null,
                                tint = if (prioritizeTrusted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.mix_trusted_sources_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.mix_trusted_sources_sub),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = prioritizeTrusted,
                            onCheckedChange = null
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                MixSectionLabel(Icons.Rounded.Person, stringResource(R.string.mix_in_the_style_of))
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text(stringResource(R.string.mix_artist_hint)) },
                    trailingIcon = {
                        if (artist.isNotBlank()) {
                            IconButton(onClick = { startArtist() }, shapes = IconButtonDefaults.shapes()) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = stringResource(R.string.mix_start),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { startArtist() }),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(24.dp))
                MixSectionLabel(Icons.Rounded.MusicNote, stringResource(R.string.mix_by_genre))
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = genreQuery,
                    onValueChange = { genreQuery = it },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text(stringResource(R.string.mix_genre_search_hint)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (genreQuery.isNotEmpty()) {
                            IconButton(onClick = { genreQuery = "" }, shapes = IconButtonDefaults.shapes()) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.btn_close))
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            val target = custom ?: matches.firstOrNull()?.query
                            if (target != null) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                focusManager.clearFocus()
                                onPick(MixEngine.Recipe.InGenre(target))
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(14.dp))
                Box(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        custom?.let { tag ->
                            AssistChip(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    onPick(MixEngine.Recipe.InGenre(tag))
                                },
                                label = { Text(stringResource(R.string.mix_genre_custom, tag)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Tag,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                    labelColor = MaterialTheme.colorScheme.primary,
                                    leadingIconContentColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                        matches.forEach { genre ->
                            AssistChip(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    onPick(MixEngine.Recipe.InGenre(genre.query))
                                },
                                label = { Text(genre.title) },
                                leadingIcon = {
                                    Icon(genre.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Heading inside customization dialog: an icon, a label, and a subtle divider. */
@Composable
private fun MixSectionLabel(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(12.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    }
}
