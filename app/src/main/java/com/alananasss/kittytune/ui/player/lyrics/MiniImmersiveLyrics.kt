package com.alananasss.kittytune.ui.player.lyrics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.hapticClickable
import com.alananasss.kittytune.ui.player.InlineLyricsContent
import com.alananasss.kittytune.ui.player.PlayerViewModel

// Mini-style lyrics layout: "text left - compact pane right" in landscape and
// "text top - pane bottom" in portrait, with a fullscreen (immersive) button
// that hides everything except the lyrics, the cover with the track title,
// the mode pills and the progress slider.
//
// Plugs into the Pixel Player, Apple Music, KittyTune Modern and classic designs.
@Composable
fun MiniImmersiveLyricsView(
    viewModel: PlayerViewModel,
    isImmersive: Boolean,
    onToggleImmersive: () -> Unit,
    onCloseLyrics: () -> Unit,
    onOpenMenu: () -> Unit,
    accentColor: Color = Color.White.copy(alpha = 0.22f),
    onAccentColor: Color = Color.White,
    sliderComposable: @Composable () -> Unit,
    codecBadgeComposable: (@Composable () -> Unit)? = null,
    playbackControlsComposable: @Composable () -> Unit,
    bottomRowComposable: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    // Applies the system insets itself (false - when the host has already done it).
    applyInsets: Boolean = true
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight
        val isTablet = maxWidth >= 600.dp && maxHeight >= 600.dp
        val insetsModifier = if (applyInsets) {
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
        } else {
            Modifier
        }

        if (isLandscape) {
            // LANDSCAPE MODE (phone and tablet)
            // Left: lyrics | Right: cover beside the title + slider
            // (+ buttons hidden by the fullscreen (immersive) mode)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .then(insetsModifier)
                    .padding(
                        start = if (isTablet) 36.dp else 20.dp,
                        end = if (isTablet) 36.dp else 20.dp,
                        top = if (isTablet) 16.dp else 8.dp,
                        bottom = if (isTablet) 16.dp else 8.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left column: lyrics
                Box(
                    modifier = Modifier
                        .weight(0.54f)
                        .fillMaxHeight()
                        .padding(end = if (isTablet) 32.dp else 18.dp)
                ) {
                    InlineLyricsContent(viewModel = viewModel)
                }

                // Right column: the mini-style side pane (cover beside the title, pills, slider, controls)
                MiniLyricsSidePane(
                    viewModel = viewModel,
                    isImmersive = isImmersive,
                    onToggleImmersive = onToggleImmersive,
                    onCloseLyrics = onCloseLyrics,
                    onOpenMenu = onOpenMenu,
                    accentColor = accentColor,
                    onAccentColor = onAccentColor,
                    sliderComposable = sliderComposable,
                    codecBadgeComposable = codecBadgeComposable,
                    playbackControlsComposable = playbackControlsComposable,
                    bottomRowComposable = bottomRowComposable,
                    modifier = Modifier
                        .weight(0.46f)
                        .fillMaxHeight()
                )
            }
        } else {
            // PORTRAIT MODE (phone and tablet)
            // Top: lyrics | Bottom: cover beside the title + slider (+ buttons)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(insetsModifier)
                    .padding(horizontal = if (isTablet) 48.dp else 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Lyrics area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    InlineLyricsContent(viewModel = viewModel)
                }

                // Bottom pane (width-capped on tablets)
                MiniLyricsSidePane(
                    viewModel = viewModel,
                    isImmersive = isImmersive,
                    onToggleImmersive = onToggleImmersive,
                    onCloseLyrics = onCloseLyrics,
                    onOpenMenu = onOpenMenu,
                    accentColor = accentColor,
                    onAccentColor = onAccentColor,
                    sliderComposable = sliderComposable,
                    codecBadgeComposable = codecBadgeComposable,
                    playbackControlsComposable = playbackControlsComposable,
                    bottomRowComposable = bottomRowComposable,
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 10.dp)
                )
            }
        }
    }
}

// The lyrics side pane without the lyrics text: the cover beside the track title,
// the dual [Fullscreen | Menu] pill pair, the progress slider, the codec badge
// and - only outside fullscreen mode - the playback controls.
//
// Used standalone in those landscape layouts where the lyrics already live
// in the left column (Modern, the classic design, tablet).
@Composable
fun MiniLyricsSidePane(
    viewModel: PlayerViewModel,
    isImmersive: Boolean,
    onToggleImmersive: () -> Unit,
    onCloseLyrics: () -> Unit,
    onOpenMenu: () -> Unit,
    accentColor: Color = Color.White.copy(alpha = 0.22f),
    onAccentColor: Color = Color.White,
    sliderComposable: @Composable () -> Unit,
    codecBadgeComposable: (@Composable () -> Unit)? = null,
    playbackControlsComposable: @Composable () -> Unit,
    bottomRowComposable: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val track = viewModel.uiCurrentTrack

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Cover + title/artist + pills (Fullscreen / Menu)
        MiniTrackHeaderWithCover(
            title = track?.title ?: "",
            artist = track?.displayArtist ?: "",
            artworkUrl = track?.fullResArtwork,
            isImmersive = isImmersive,
            accentColor = accentColor,
            onAccentColor = onAccentColor,
            onToggleImmersive = onToggleImmersive,
            onOpenMenu = onOpenMenu,
            onExitLyrics = onCloseLyrics
        )

        Spacer(Modifier.height(14.dp))

        // Progress slider (with time codes - the passed-in composable draws those itself)
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            sliderComposable()
        }

        // Codec/quality badge centred under the slider (as in the mini style)
        if (codecBadgeComposable != null) {
            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                codecBadgeComposable()
            }
        }

        // Playback controls - hidden in fullscreen (immersive) mode
        AnimatedVisibility(
            visible = !isImmersive,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(10.dp))
                playbackControlsComposable()
                if (bottomRowComposable != null) {
                    Spacer(Modifier.height(10.dp))
                    bottomRowComposable()
                }
            }
        }
    }
}

// The lyrics header: a mini cover next to the track title and the artist,
// and on the right a grouped dual pill [Fullscreen | Menu "•••"],
// as in the mini style.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniTrackHeaderWithCover(
    title: String,
    artist: String,
    artworkUrl: String?,
    isImmersive: Boolean,
    accentColor: Color,
    onAccentColor: Color = Color.White,
    onToggleImmersive: () -> Unit,
    onOpenMenu: () -> Unit,
    onExitLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Mini track cover next to the title (as in the mini style): tapping it closes the lyrics
        AsyncImage(
            model = artworkUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .hapticClickable(kind = HapticKind.UI) { onExitLyrics() }
        )

        Spacer(Modifier.width(12.dp))

        // Title and artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.basicMarquee()
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = artist,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(12.dp))

        // Grouped dual pill: [Fullscreen | Menu "•••"]
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val leadingShape = RoundedCornerShape(
                topStart = 19.dp,
                bottomStart = 19.dp,
                topEnd = 5.dp,
                bottomEnd = 5.dp
            )
            val trailingShape = RoundedCornerShape(
                topStart = 5.dp,
                bottomStart = 5.dp,
                topEnd = 19.dp,
                bottomEnd = 19.dp
            )

            Surface(
                shape = leadingShape,
                color = accentColor,
                modifier = Modifier
                    .height(38.dp)
                    .width(44.dp)
                    .clip(leadingShape)
                    .hapticClickable(kind = HapticKind.UI) { onToggleImmersive() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isImmersive) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                        contentDescription = null,
                        tint = onAccentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Surface(
                shape = trailingShape,
                color = accentColor,
                modifier = Modifier
                    .height(38.dp)
                    .width(44.dp)
                    .clip(trailingShape)
                    .hapticClickable(kind = HapticKind.UI) { onOpenMenu() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.MoreHoriz,
                        contentDescription = null,
                        tint = onAccentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
