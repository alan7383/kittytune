package com.alananasss.kittytune.ui.profile

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.util.Calendar

data class ArchiveWrappedYear(
    val year: Int,
    val titleRes: Int,
    val subtitleRes: Int,
    val hasInteractiveStory: Boolean,
    val playlistUrn: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WrappedHubScreen(
    user: User?,
    isGuest: Boolean,
    onBackClick: () -> Unit,
    onLaunchStory: (Int) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }

    // Extract the account creation year from user.createdAt ISO date (e.g. "2025-04-12T...")
    val accountCreationYear = remember(user, isGuest, currentYear) {
        user?.createdAt?.take(4)?.toIntOrNull() ?: if (isGuest) currentYear else 2025
    }

    val userId = user?.id?.toString().orEmpty()

    // Dynamically check whether past Playback playlists were generated for this user account on SoundCloud
    val availabilityMap by produceState<Map<Int, Boolean>>(
        initialValue = mapOf(2025 to true, 2024 to true),
        key1 = userId
    ) {
        if (userId.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                val api = RetrofitClient.create(context)
                val years = listOf(2025, 2024, 2023)
                val checked = years.map { yr ->
                    async {
                        val exists = try {
                            val pl = api.getSystemPlaylist("soundcloud:system-playlists:your-playback:$userId:$yr")
                            pl.id > 0 || (pl.trackCount ?: 0) > 0 || pl.tracks?.isNotEmpty() == true
                        } catch (e: Exception) {
                            false
                        }
                        yr to exists
                    }
                }.awaitAll().toMap()
                value = checked
            }
        }
    }

    // All past years supported by SoundCloud's system-playlists architecture
    val archiveYears = remember(userId) {
        listOf(
            ArchiveWrappedYear(
                year = 2025,
                titleRes = R.string.yearly_playback_year_title,
                subtitleRes = R.string.yearly_playback_archive_2025_subtitle,
                hasInteractiveStory = true,
                playlistUrn = if (userId.isNotEmpty()) "soundcloud:system-playlists:your-playback:$userId:2025" else "soundcloud:playlists:playback_2025"
            ),
            ArchiveWrappedYear(
                year = 2024,
                titleRes = R.string.yearly_playback_playback_year_title,
                subtitleRes = R.string.yearly_playback_archive_2024_subtitle,
                hasInteractiveStory = false,
                playlistUrn = if (userId.isNotEmpty()) "soundcloud:system-playlists:your-playback:$userId:2024" else "soundcloud:playlists:playback_2024"
            ),
            ArchiveWrappedYear(
                year = 2023,
                titleRes = R.string.yearly_playback_playback_year_title,
                subtitleRes = R.string.yearly_playback_archive_2023_subtitle,
                hasInteractiveStory = false,
                playlistUrn = if (userId.isNotEmpty()) "soundcloud:system-playlists:your-playback:$userId:2023" else "soundcloud:playlists:playback_2023"
            )
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.yearly_playback_title),
                            fontWeight = FontWeight.Bold
                        )
                        if (user?.createdAt != null) {
                            Text(
                                text = stringResource(R.string.yearly_playback_member_since, accountCreationYear.toString()),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.yearly_playback_hub_subtitle),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.yearly_playback_close)
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 180.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ──────────────────────────────────────────────────────────────────
            // EN COURS : In Preparation Card (prominent featured card)
            // ──────────────────────────────────────────────────────────────────
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        // Top: Year Avatar
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "'${currentYear.toString().takeLast(2)}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Title
                        Text(
                            text = stringResource(R.string.yearly_playback_in_prep_title, currentYear.toString()),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(Modifier.height(6.dp))

                        // Description
                        Text(
                            text = stringResource(R.string.yearly_playback_in_prep_desc, currentYear.toString()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )

                        Spacer(Modifier.height(14.dp))

                        // End-of-year timing info bar
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.yearly_playback_in_prep_status),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ──────────────────────────────────────────────────────────────────
            // ARCHIVES SECTION HEADER
            // ──────────────────────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.yearly_playback_archives_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.yearly_playback_archives_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(2.dp))
            }

            // ──────────────────────────────────────────────────────────────────
            // ARCHIVES YEARS
            // ──────────────────────────────────────────────────────────────────
            items(archiveYears, key = { it.year }) { item ->
                val isEligible = item.year >= accountCreationYear
                val isAvailable = availabilityMap[item.year] ?: (item.year >= 2024)

                ArchiveYearCard(
                    item = item,
                    isEligible = isEligible,
                    isAvailable = isAvailable,
                    accountCreationYear = accountCreationYear,
                    onLaunchStory = onLaunchStory,
                    onOpenPlaylist = onOpenPlaylist
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ArchiveYearCard(
    item: ArchiveWrappedYear,
    isEligible: Boolean,
    isAvailable: Boolean,
    accountCreationYear: Int,
    onLaunchStory: (Int) -> Unit,
    onOpenPlaylist: (String) -> Unit
) {
    val context = LocalContext.current
    val isCardActive = isEligible && isAvailable
    var isMenuExpanded by remember { mutableStateOf(false) }

    val hasBothStoryAndPlaylist = isCardActive && item.hasInteractiveStory && item.playlistUrn.isNotEmpty()

    val (badgeContainerColor, badgeContentColor) = when {
        !isEligible || !isAvailable -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
        item.hasInteractiveStory -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }

    Surface(
        onClick = {
            if (!isEligible) {
                val msg = context.getString(R.string.yearly_playback_locked_toast, accountCreationYear.toString())
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            } else if (!isAvailable) {
                val msg = context.getString(R.string.yearly_playback_unavailable_toast, item.year.toString())
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            } else {
                if (item.hasInteractiveStory) {
                    onLaunchStory(item.year)
                } else {
                    onOpenPlaylist(item.playlistUrn)
                }
            }
        },
        shape = RoundedCornerShape(18.dp),
        color = if (isCardActive) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!isCardActive) Modifier.alpha(0.6f) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Year avatar / icon using pure Material 3 tokens
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = badgeContainerColor,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (!isEligible) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = badgeContentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text(
                            text = "'${item.year.toString().takeLast(2)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = badgeContentColor
                        )
                    }
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(item.titleRes, item.year),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isCardActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(2.dp))

                val subtitleText = when {
                    !isEligible -> stringResource(R.string.yearly_playback_locked_account_desc, accountCreationYear.toString())
                    !isAvailable -> stringResource(R.string.yearly_playback_unavailable_desc)
                    else -> stringResource(item.subtitleRes)
                }

                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            if (!isEligible) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        text = stringResource(R.string.yearly_playback_locked_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } else if (!isAvailable) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        text = stringResource(R.string.yearly_playback_unavailable_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } else if (hasBothStoryAndPlaylist) {
                // Split button (bouton découpé) identical to the folders component!
                Box {
                    val splitColors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    SplitButtonLayout(
                        leadingButton = {
                            SplitButtonDefaults.LeadingButton(
                                onClick = {
                                    onLaunchStory(item.year)
                                },
                                colors = splitColors
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.yearly_playback_launch_story),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        },
                        trailingButton = {
                            SplitButtonDefaults.TrailingButton(
                                checked = isMenuExpanded,
                                onCheckedChange = { isMenuExpanded = it },
                                colors = splitColors
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowDropDown,
                                    contentDescription = stringResource(R.string.yearly_playback_view_playlist),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    )

                    DropdownMenu(
                        expanded = isMenuExpanded,
                        onDismissRequest = { isMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.yearly_playback_view_playlist)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Headphones,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                isMenuExpanded = false
                                onOpenPlaylist(item.playlistUrn)
                            }
                        )
                    }
                }
            } else if (item.hasInteractiveStory) {
                // Story only
                Button(
                    onClick = {
                        onLaunchStory(item.year)
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.yearly_playback_launch_story),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            } else {
                // Single playlist button using Material 3 secondaryContainer
                FilledTonalButton(
                    onClick = {
                        onOpenPlaylist(item.playlistUrn)
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(
                        Icons.Rounded.Headphones,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.yearly_playback_view_playlist),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}
