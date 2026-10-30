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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WaterDrop
import com.alananasss.kittytune.ui.design.AssistChip
import androidx.compose.material3.AssistChipDefaults
import com.alananasss.kittytune.ui.design.Button
import androidx.compose.material3.ButtonDefaults
import com.alananasss.kittytune.ui.design.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import com.alananasss.kittytune.ui.design.FilterChip
import androidx.compose.material3.FilterChipDefaults
import com.alananasss.kittytune.ui.design.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.alananasss.kittytune.ui.design.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import com.alananasss.kittytune.ui.design.OutlinedTextField
import com.alananasss.kittytune.ui.design.Surface
import com.alananasss.kittytune.ui.design.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.alananasss.kittytune.audio.haptics.performAppHaptic
import com.alananasss.kittytune.data.BlockManager
import com.alananasss.kittytune.data.GenreData
import com.alananasss.kittytune.data.ListeningStatsRepository
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.mix.MixEngine
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.ui.player.PlaybackContext
import com.alananasss.kittytune.ui.player.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

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
    val isMixPlaying = isMixActive && playerViewModel.uiIsPlaying

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
                        view.performAppHaptic(HapticFeedbackConstants.CLOCK_TICK)
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
                                view.performAppHaptic(HapticFeedbackConstants.CLOCK_TICK)
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
                        if (controlsPlayingMix) {
                            val hasTrackToResume = playerViewModel.uiCurrentTrack != null
                                || playerViewModel.dismissedTrack != null
                                || playerViewModel.queueState.isNotEmpty()
                            if (hasTrackToResume) {
                                playerViewModel.togglePlayPause()
                            } else {
                                view.performAppHaptic(HapticFeedbackConstants.CLOCK_TICK)
                                playingStationIndex = selectedStationIndex
                                start(currentStation.recipe)
                            }
                        } else {
                            view.performAppHaptic(HapticFeedbackConstants.CLOCK_TICK)
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
                if (controlsPlayingMix) {
                    FilledTonalIconButton(
                        onClick = {
                            view.performAppHaptic(HapticFeedbackConstants.CLOCK_TICK)
                            playingStationIndex = selectedStationIndex
                            start(currentStation.recipe)
                        },
                        shapes = IconButtonDefaults.shapes(),
                        enabled = state !is MixState.Building
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.btn_reset)
                        )
                    }
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

private fun compactCount(value: Long): String {
    return when {
        value >= 1_000_000_000L -> String.format(Locale.getDefault(), "%.1fB", value / 1_000_000_000.0)
        value >= 1_000_000L -> String.format(Locale.getDefault(), "%.1fM", value / 1_000_000.0)
        value >= 1_000L -> String.format(Locale.getDefault(), "%.1fK", value / 1_000.0)
        else -> NumberFormat.getNumberInstance(Locale.getDefault()).format(value)
    }
}

private data class ArtistSuggestionItem(
    val id: Long?,
    val name: String,
    val avatarUrl: String?,
    val followersCount: Int? = null,
    val isLocal: Boolean = false,
    val verified: Boolean = false
)

/**
 * Dialog to customize the mix by multiple artists or genres, with autocomplete and trusted sources filter.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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

    val selectedArtists = remember { mutableStateListOf<MixEngine.ArtistSeed>() }
    val selectedGenres = remember { mutableStateListOf<String>() }

    var artistQuery by remember { mutableStateOf("") }
    var genreQuery by remember { mutableStateOf("") }

    var artistSuggestions by remember { mutableStateOf<List<ArtistSuggestionItem>>(emptyList()) }
    var isSearchingArtists by remember { mutableStateOf(false) }

    LaunchedEffect(artistQuery) {
        val q = artistQuery.trim()
        if (q.isEmpty()) {
            isSearchingArtists = false
            val since = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
            val top = withContext(Dispatchers.IO) {
                runCatching {
                    ListeningStatsRepository.getTopArtists(since, limit = 6)
                }.getOrDefault(emptyList())
            }
            val blocked = BlockManager.blockedArtistIdsFlow.value
            artistSuggestions = top
                .filter { it.artistId == null || it.artistId !in blocked }
                .map {
                    ArtistSuggestionItem(
                        id = it.artistId,
                        name = it.artistName,
                        avatarUrl = it.artworkUrl,
                        isLocal = true
                    )
                }
            return@LaunchedEffect
        }

        delay(250)
        isSearchingArtists = true
        val blocked = BlockManager.blockedArtistIdsFlow.value
        val results = withContext(Dispatchers.IO) {
            val list = mutableListOf<ArtistSuggestionItem>()
            val since = System.currentTimeMillis() - 180L * 24 * 60 * 60 * 1000
            val localMatches = runCatching {
                ListeningStatsRepository.getTopArtists(since, limit = 20)
                    .filter { it.artistName.contains(q, ignoreCase = true) }
            }.getOrDefault(emptyList())

            localMatches.forEach {
                if (it.artistId == null || it.artistId !in blocked) {
                    list.add(
                        ArtistSuggestionItem(
                            id = it.artistId,
                            name = it.artistName,
                            avatarUrl = it.artworkUrl,
                            isLocal = true
                        )
                    )
                }
            }

            runCatching {
                val api = RetrofitClient.create()
                val res = api.searchUsers(q, limit = 8)
                res.collection.forEach { user ->
                    if (user.id !in blocked && list.none { it.id == user.id || it.name.equals(user.username, ignoreCase = true) }) {
                        list.add(
                            ArtistSuggestionItem(
                                id = user.id,
                                name = user.username ?: q,
                                avatarUrl = user.avatarUrl,
                                followersCount = user.followersCount,
                                verified = user.verified,
                                isLocal = false
                            )
                        )
                    }
                }
            }
            list
        }
        artistSuggestions = results
        isSearchingArtists = false
    }

    fun addArtist(item: ArtistSuggestionItem) {
        if (selectedArtists.none { it.artistName.equals(item.name, ignoreCase = true) || (it.artistId != null && it.artistId == item.id) }) {
            selectedArtists.add(
                MixEngine.ArtistSeed(
                    artistId = item.id,
                    artistName = item.name,
                    avatarUrl = item.avatarUrl
                )
            )
        }
        artistQuery = ""
        view.performAppHaptic(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun addTypedArtist() {
        val q = artistQuery.trim()
        if (q.isNotEmpty()) {
            if (selectedArtists.none { it.artistName.equals(q, ignoreCase = true) }) {
                selectedArtists.add(
                    MixEngine.ArtistSeed(
                        artistId = null,
                        artistName = q,
                        avatarUrl = null
                    )
                )
            }
            artistQuery = ""
            view.performAppHaptic(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    var showAllGenres by remember { mutableStateOf(false) }

    val all = remember { GenreData.getGenres(context) }
    val needle = genreQuery.trim()
    val matches = remember(needle, all) {
        if (needle.isEmpty()) all
        else all.filter { it.title.contains(needle, true) || it.query.contains(needle, true) }
    }
    val custom = needle.takeIf {
        it.isNotEmpty() && all.none { known -> known.title.equals(it, true) || known.query.equals(it, true) }
    }

    val visibleGenres = remember(needle, matches, all, showAllGenres, selectedGenres.toList()) {
        if (needle.isNotEmpty()) {
            matches
        } else if (showAllGenres) {
            all
        } else {
            val selectedSet = selectedGenres.toSet()
            val selectedStandard = all.filter { it.query in selectedSet }
            val remaining = all.filter { it.query !in selectedSet }
            (selectedStandard + remaining).take(10)
        }
    }
    val canExpandGenres = needle.isEmpty() && matches.size > visibleGenres.size

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
                .heightIn(max = 680.dp)
                .padding(vertical = 16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 18.dp)
                ) {
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
                            view.performAppHaptic(HapticFeedbackConstants.CLOCK_TICK)
                            onDismiss()
                        },
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.btn_close))
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 18.dp)
                ) {
                    val cardShape = RoundedCornerShape(18.dp)
                    Card(
                        onClick = {
                            view.performAppHaptic(HapticFeedbackConstants.CLOCK_TICK)
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
                    MixSectionLabel(Icons.Rounded.Person, stringResource(R.string.mix_artists_section))
                    Spacer(Modifier.height(10.dp))

                    if (selectedArtists.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            selectedArtists.forEach { artistSeed ->
                                InputChip(
                                    selected = true,
                                    onClick = {
                                        view.performAppHaptic(HapticFeedbackConstants.CLOCK_TICK)
                                        selectedArtists.remove(artistSeed)
                                    },
                                    label = { Text(artistSeed.artistName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    avatar = {
                                        if (!artistSeed.avatarUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = artistSeed.avatarUrl,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.Person,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.mix_selected_chip_remove, artistSeed.artistName),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    OutlinedTextField(
                        value = artistQuery,
                        onValueChange = { artistQuery = it },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        placeholder = { Text(stringResource(R.string.mix_artist_search_placeholder)) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (artistQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        view.performAppHaptic(HapticFeedbackConstants.CLOCK_TICK)
                                        artistQuery = ""
                                    },
                                    shapes = IconButtonDefaults.shapes()
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.btn_close))
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addTypedArtist() }),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (artistQuery.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                val exactMatch = artistSuggestions.any { it.name.equals(artistQuery.trim(), ignoreCase = true) }
                                if (!exactMatch && artistQuery.isNotBlank()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { addTypedArtist() }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Add,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            text = stringResource(R.string.mix_genre_custom, artistQuery.trim()),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                artistSuggestions.forEach { item ->
                                    val isAlreadyAdded = selectedArtists.any {
                                        it.artistName.equals(item.name, ignoreCase = true) || (it.artistId != null && it.artistId == item.id)
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (isAlreadyAdded) {
                                                    selectedArtists.removeAll {
                                                        it.artistName.equals(item.name, ignoreCase = true) || (it.artistId != null && it.artistId == item.id)
                                                    }
                                                } else {
                                                    addArtist(item)
                                                }
                                            }
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!item.avatarUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = item.avatarUrl,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Rounded.Person,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = item.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (item.verified) {
                                                    Spacer(Modifier.width(4.dp))
                                                    Icon(
                                                        imageVector = Icons.Rounded.Verified,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                            val subtitle = when {
                                                item.isLocal -> stringResource(R.string.mix_in_library)
                                                item.followersCount != null && item.followersCount > 0 ->
                                                    stringResource(R.string.mix_followers_count, compactCount(item.followersCount.toLong()))
                                                else -> null
                                            }
                                            if (subtitle != null) {
                                                Text(
                                                    text = subtitle,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = if (isAlreadyAdded) Icons.Rounded.Check else Icons.Rounded.Add,
                                            contentDescription = null,
                                            tint = if (isAlreadyAdded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else if (artistSuggestions.isNotEmpty()) {
                        val unselectedSuggestions = artistSuggestions.filter { sug ->
                            selectedArtists.none { it.artistName.equals(sug.name, ignoreCase = true) || (it.artistId != null && it.artistId == sug.id) }
                        }
                        if (unselectedSuggestions.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = stringResource(R.string.mix_suggested_artists),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                unselectedSuggestions.forEach { item ->
                                    AssistChip(
                                        onClick = { addArtist(item) },
                                        label = { Text(item.name) },
                                        leadingIcon = {
                                            if (!item.avatarUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = item.avatarUrl,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clip(CircleShape)
                                                )
                                            } else {
                                                Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                                            }
                                        },
                                        trailingIcon = {
                                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    MixSectionLabel(Icons.Rounded.MusicNote, stringResource(R.string.mix_genres_section))
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
                                IconButton(
                                    onClick = {
                                        view.performAppHaptic(HapticFeedbackConstants.CLOCK_TICK)
                                        genreQuery = ""
                                    },
                                    shapes = IconButtonDefaults.shapes()
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.btn_close))
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val target = custom ?: matches.firstOrNull()?.query
                                if (target != null) {
                                    view.performAppHaptic(HapticFeedbackConstants.CLOCK_TICK)
                                    if (target !in selectedGenres) selectedGenres.add(target)
                                    genreQuery = ""
                                }
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        custom?.let { tag ->
                            val isCustomSelected = tag in selectedGenres
                            FilterChip(
                                selected = isCustomSelected,
                                onClick = {
                                    view.performAppHaptic(HapticFeedbackConstants.CLOCK_TICK)
                                    if (isCustomSelected) selectedGenres.remove(tag) else selectedGenres.add(tag)
                                },
                                label = { Text(stringResource(R.string.mix_genre_custom, tag)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isCustomSelected) Icons.Rounded.Check else Icons.Rounded.Tag,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                                    )
                                }
                            )
                        }

                        selectedGenres.filter { g -> all.none { it.query.equals(g, ignoreCase = true) } && g != custom }.forEach { customTag ->
                            FilterChip(
                                selected = true,
                                onClick = {
                                    view.performAppHaptic(HapticFeedbackConstants.CLOCK_TICK)
                                    selectedGenres.remove(customTag)
                                },
                                label = { Text(customTag) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
                                }
                            )
                        }

                        visibleGenres.forEach { genre ->
                            val isSelected = genre.query in selectedGenres
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    view.performAppHaptic(HapticFeedbackConstants.CLOCK_TICK)
                                    if (isSelected) selectedGenres.remove(genre.query) else selectedGenres.add(genre.query)
                                },
                                label = { Text(genre.title) },
                                leadingIcon = {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = genre.icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                                        )
                                    }
                                }
                            )
                        }

                        if (canExpandGenres) {
                            AssistChip(
                                onClick = {
                                    view.performAppHaptic(HapticFeedbackConstants.CLOCK_TICK)
                                    showAllGenres = true
                                },
                                label = { Text(stringResource(R.string.detail_show_more)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        } else if (needle.isEmpty() && showAllGenres) {
                            AssistChip(
                                onClick = {
                                    view.performAppHaptic(HapticFeedbackConstants.CLOCK_TICK)
                                    showAllGenres = false
                                },
                                label = { Text(stringResource(R.string.detail_show_less)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.ExpandLess,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                val pendingArtist = artistQuery.trim()
                val hasPendingArtist = pendingArtist.isNotEmpty() && selectedArtists.none { it.artistName.equals(pendingArtist, ignoreCase = true) }
                val hasPendingCustomGenre = custom != null && custom !in selectedGenres
                val totalSelected = selectedArtists.size + selectedGenres.size +
                    (if (hasPendingArtist) 1 else 0) +
                    (if (hasPendingCustomGenre) 1 else 0)

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    Button(
                        onClick = {
                            view.performAppHaptic(HapticFeedbackConstants.CLOCK_TICK)
                            focusManager.clearFocus()
                            val finalArtists = selectedArtists.toMutableList()
                            if (hasPendingArtist) {
                                finalArtists.add(MixEngine.ArtistSeed(artistName = pendingArtist))
                            }
                            val finalGenres = selectedGenres.toMutableList()
                            if (custom != null && custom !in finalGenres) {
                                finalGenres.add(custom)
                            }
                            if (finalArtists.isNotEmpty() || finalGenres.isNotEmpty()) {
                                onPick(MixEngine.Recipe.Custom(artists = finalArtists, genres = finalGenres))
                            } else {
                                onPick(MixEngine.Recipe.MyTaste)
                            }
                        },
                        enabled = totalSelected > 0,
                        modifier = Modifier.fillMaxWidth(),
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(
                            text = if (totalSelected > 0) {
                                stringResource(R.string.mix_start_with_count, totalSelected)
                            } else {
                                stringResource(R.string.mix_start)
                            },
                            fontWeight = FontWeight.SemiBold
                        )
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
