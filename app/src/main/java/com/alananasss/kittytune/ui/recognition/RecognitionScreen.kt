package com.alananasss.kittytune.ui.recognition

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.AchievementManager
import com.alananasss.kittytune.data.LikeRepository
import com.alananasss.kittytune.music.recognition.AudioCaptureManager
import com.alananasss.kittytune.music.recognition.RecognitionAudioSource
import com.alananasss.kittytune.music.recognition.RecognitionState
import com.alananasss.kittytune.music.recognition.RecognitionViewModel
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import com.alananasss.kittytune.ui.player.PlayerViewModel

@Composable
fun RecognitionScreen(
    onBackClick: () -> Unit,
    playerViewModel: PlayerViewModel,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val viewModel: RecognitionViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return RecognitionViewModel(context.applicationContext) as T
            }
        }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val audioSource by viewModel.audioSource.collectAsStateWithLifecycle()

    BackHandler {
        if (state is RecognitionState.Searching) {
            viewModel.cancelRecognition()
        } else {
            onBackClick()
        }
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            viewModel.startRecognition()
        }
    }

    val mediaProjectionManager = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        } else null
    }

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            AudioCaptureManager.startCapture(
                context = context,
                resultCode = result.resultCode,
                data = result.data!!,
                onReady = { projection ->
                    viewModel.startRecognition(mediaProjection = projection)
                },
                onError = { errorMsg ->
                    Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    val startListening = {
        if (audioSource == RecognitionAudioSource.MIC) {
            if (hasPermission) {
                viewModel.startRecognition()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        } else {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                Toast.makeText(
                    context,
                    context.getString(R.string.recognition_device_not_supported),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                val captureIntent = mediaProjectionManager?.createScreenCaptureIntent()
                if (captureIntent != null) {
                    screenCaptureLauncher.launch(captureIntent)
                } else {
                    Toast.makeText(context, "MediaProjection not available", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val isErrorOrSuccess = state is RecognitionState.Error || state is RecognitionState.Success
    val isSearching = state is RecognitionState.Searching
    // Matches original: idle = secondaryContainer, searching = primaryContainer, result = surface
    val bgColor = when {
        isErrorOrSuccess -> MaterialTheme.colorScheme.surface
        isSearching -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val animatedBgColor by animateColorAsState(
        targetValue = bgColor,
        animationSpec = tween(1000),
        label = "bg_color"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(animatedBgColor)
    ) {
        val bgComposition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.background_animation))
        // Original HomeFragment.java: lottie scales 1→5 on motionEasingEmphasizedDecelerate over 1000ms when listening starts.
        val lottieScale = remember { androidx.compose.animation.core.Animatable(1f) }
        LaunchedEffect(isSearching) {
            if (isSearching) {
                lottieScale.animateTo(
                    5f,
                    tween(1000, easing = androidx.compose.animation.core.CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f))
                )
            } else {
                lottieScale.snapTo(1f)
            }
        }
        // From HomeFragment.java lines 89-149 (exact reverse-engineered code):
        //   private static final fdy aw = new fdy("**", ".primary", "**");    → COLOR + OPACITY 95
        //   private static final fdy ax = new fdy("**", ".secondary", "**");  → COLOR + OPACITY 95
        //   private static final fdy ay = new fdy("**", ".tertiary", "**");   → COLOR + OPACITY 90
        //   fav.a = 1 = LottieProperty.COLOR; fav.d = 4 = LottieProperty.OPACITY
        //   Colors: colorPrimary, colorSecondary, colorTertiary
        val lottiePrimary   = MaterialTheme.colorScheme.primary.copy(alpha = 1f).toArgb()
        val lottieSecondary = MaterialTheme.colorScheme.secondary.copy(alpha = 1f).toArgb()
        val lottieTertiary  = MaterialTheme.colorScheme.tertiary.copy(alpha = 1f).toArgb()
        val bgDynamicProps = rememberLottieDynamicProperties(
            // .primary layers: colorPrimary fill, 95% opacity
            rememberLottieDynamicProperty(
                property = LottieProperty.COLOR,
                value = lottiePrimary,
                keyPath = arrayOf("**", ".primary", "**"),
            ),
            rememberLottieDynamicProperty(
                property = LottieProperty.OPACITY,
                value = 95,
                keyPath = arrayOf("**", ".primary", "**"),
            ),
            // .secondary layers: colorSecondary fill, 95% opacity
            rememberLottieDynamicProperty(
                property = LottieProperty.COLOR,
                value = lottieSecondary,
                keyPath = arrayOf("**", ".secondary", "**"),
            ),
            rememberLottieDynamicProperty(
                property = LottieProperty.OPACITY,
                value = 95,
                keyPath = arrayOf("**", ".secondary", "**"),
            ),
            // .tertiary layer: colorTertiary fill, 90% opacity
            rememberLottieDynamicProperty(
                property = LottieProperty.COLOR,
                value = lottieTertiary,
                keyPath = arrayOf("**", ".tertiary", "**"),
            ),
            rememberLottieDynamicProperty(
                property = LottieProperty.OPACITY,
                value = 90,
                keyPath = arrayOf("**", ".tertiary", "**"),
            ),
        )

        AnimatedVisibility(
            visible = !isErrorOrSuccess,
            enter = fadeIn(tween(800)),
            exit = fadeOut(tween(500))
        ) {
            LottieAnimation(
                composition = bgComposition,
                iterations = LottieConstants.IterateForever,
                dynamicProperties = bgDynamicProps,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = lottieScale.value
                        scaleY = lottieScale.value
                    },
                contentScale = ContentScale.Crop
            )
        }

        AnimatedVisibility(
            visible = !isErrorOrSuccess,
            enter = fadeIn(tween(800)),
            exit = fadeOut(tween(500)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            GlowView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }

        FilledTonalIconButton(
            onClick = {
                viewModel.cancelRecognition()
                onBackClick()
            },
            shapes = IconButtonDefaults.shapes(),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Retour"
            )
        }

        FilledTonalIconButton(
            onClick = { onNavigate("recognition_history") },
            shapes = IconButtonDefaults.shapes(),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp)
        ) {
            Icon(
                Icons.Rounded.History,
                contentDescription = "Historique"
            )
        }

        val currentPage = when (val s = state) {
            is RecognitionState.Idle, is RecognitionState.Searching -> RecognitionUiPage.Main
            is RecognitionState.Success -> RecognitionUiPage.Success(s)
            is RecognitionState.Error -> RecognitionUiPage.Error(s.message)
        }

        AnimatedContent(
            targetState = currentPage,
            transitionSpec = {
                (fadeIn(animationSpec = tween(400)) + 
                        slideInVertically(animationSpec = tween(400), initialOffsetY = { it / 16 })) togetherWith
                (fadeOut(animationSpec = tween(300)) + 
                        slideOutVertically(animationSpec = tween(300), targetOffsetY = { -it / 16 }))
            },
            label = "state_content",
            modifier = Modifier.fillMaxSize()
        ) { page ->
            Box(modifier = Modifier.fillMaxSize()) {
                when (page) {
                    is RecognitionUiPage.Main -> RecognitionHomeView(
                        isSearching = isSearching,
                        audioSource = audioSource,
                        onSourceChange = { viewModel.setAudioSource(it) },
                        onTap = startListening,
                        onCancel = { viewModel.cancelRecognition() }
                    )
                    is RecognitionUiPage.Success -> SuccessView(
                        state = page.state,
                        onPlayClick = {
                            page.state.soundcloudTrack?.let { track ->
                                playerViewModel.playPlaylist(listOf(track), 0)
                                onBackClick()
                            }
                        },
                        onRetry = startListening
                    )
                    is RecognitionUiPage.Error -> ErrorView(
                        error = page.message,
                        onRetry = startListening
                    )
                }
            }
        }
    }
}

private sealed class RecognitionUiPage {
    object Main : RecognitionUiPage()
    data class Success(val state: RecognitionState.Success) : RecognitionUiPage()
    data class Error(val message: String) : RecognitionUiPage()
}

@Composable
private fun RecognitionHomeView(
    isSearching: Boolean,
    audioSource: RecognitionAudioSource,
    onSourceChange: (RecognitionAudioSource) -> Unit,
    onTap: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val btnScale by animateFloatAsState(targetValue = if (isPressed && !isSearching) 0.92f else 1f, label = "scale")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // From gam.java + gai.java (exact reverse-engineering):
        //   Idle:      button bg = music_recognition_button_background (?colorSecondary circle)
        //              icon tint = ?colorOnSecondary
        //   Searching: gai.onAnimationStart → button bg = music_recognition_active_background_new
        //              music_recognition_active_background_new fillColor = ?colorOnPrimaryContainer
        //              icon tint = ?colorPrimaryContainer (drawn on the dark blob)
        val buttonColor = if (isSearching)
            MaterialTheme.colorScheme.onPrimaryContainer  // dark blob on primaryContainer bg
        else
            MaterialTheme.colorScheme.secondary           // circle on secondaryContainer bg
        val iconTint = if (isSearching)
            MaterialTheme.colorScheme.primaryContainer    // light icon on dark blob
        else
            MaterialTheme.colorScheme.onSecondary         // icon on secondary circle

        // When searching, the label slides down (+44dp) to make room for the bloomed button (236dp)
        // and its breathing aura, keeping a clean visual separation without pushing everything away.
        val labelOffset by animateDpAsState(
            targetValue = if (isSearching) 44.dp else 0.dp,
            animationSpec = tween(500, easing = FastOutSlowInEasing),
            label = "label_offset"
        )

        NowPlayingListenButton(
            active = isSearching,
            color = buttonColor,
            haloColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .size(180.dp)
                .scale(btnScale)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        if (isSearching) {
                            onCancel()
                        } else {
                            onTap()
                        }
                    }
                ),
        ) {
            // Original: button src = avd_nowplaying_searching (animated 3-bar icon) when searching,
            // or gs_mic_off_64 (mic-off icon, which is the music note) when idle.
            if (isSearching) {
                SearchingBarsIcon(
                    color = iconTint,
                    modifier = Modifier.size(56.dp)
                )
            } else {
                Icon(
                    imageVector = NowPlayingNote,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = iconTint
                )
            }
        }

        Spacer(Modifier.height(36.dp + labelOffset))

        Crossfade(
            targetState = isSearching,
            animationSpec = tween(300),
            label = "label_crossfade"
        ) { searching ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Original: musicRecognitionButtonLabel changes text to music_recognition_button_running_label
                // while searching, and there is no sublabel shown during recognition.
                val labelColor = if (searching)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSecondaryContainer
                Text(
                    text = stringResource(
                        if (searching) R.string.recognition_listening
                        else if (audioSource == RecognitionAudioSource.MIC) R.string.recognition_tap_to_identify
                        else R.string.recognition_tap_to_identify_device
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    color = labelColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                // Original: sublabel is visibility=gone while searching.
                if (!searching) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            if (audioSource == RecognitionAudioSource.MIC) R.string.recognition_listening_desc
                            else R.string.recognition_listening_device_desc
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !isSearching,
            enter = fadeIn(animationSpec = tween(350, easing = FastOutSlowInEasing)) +
                    scaleIn(animationSpec = tween(350, easing = FastOutSlowInEasing), initialScale = 0.85f) +
                    slideInVertically(animationSpec = tween(350, easing = FastOutSlowInEasing)) { it / 2 },
            exit = fadeOut(animationSpec = tween(250, easing = FastOutSlowInEasing)) +
                    scaleOut(animationSpec = tween(250, easing = FastOutSlowInEasing), targetScale = 0.85f) +
                    slideOutVertically(animationSpec = tween(250, easing = FastOutSlowInEasing)) { it / 2 }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(24.dp))

                ExpressiveConnectedButtonGroup(
                    options = listOf(RecognitionAudioSource.MIC, RecognitionAudioSource.DEVICE),
                    selectedOption = audioSource,
                    onOptionSelected = { source ->
                        if (!isSearching) {
                            if (source == RecognitionAudioSource.DEVICE && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.recognition_device_not_supported),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                onSourceChange(source)
                            }
                        }
                    },
                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                    uncheckedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                    uncheckedContentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    iconSpacing = 8.dp,
                    modifier = Modifier
                        .widthIn(max = 350.dp)
                        .padding(horizontal = 32.dp),
                    iconProvider = { source ->
                        Icon(
                            imageVector = when (source) {
                                RecognitionAudioSource.MIC -> Icons.Rounded.Mic
                                RecognitionAudioSource.DEVICE -> Icons.Rounded.PhoneAndroid
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    labelProvider = { source ->
                        Text(
                            text = stringResource(
                                when (source) {
                                    RecognitionAudioSource.MIC -> R.string.recognition_source_mic
                                    RecognitionAudioSource.DEVICE -> R.string.recognition_source_device
                                }
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (audioSource == source) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SuccessView(
    state: RecognitionState.Success,
    onPlayClick: () -> Unit,
    onRetry: () -> Unit
) {
    val shazamResult = state.result
    val soundcloudTrack = state.soundcloudTrack
    val imageUrl = soundcloudTrack?.fullResArtwork ?: shazamResult.coverArtHqUrl ?: shazamResult.coverArtUrl
    val title = soundcloudTrack?.title ?: shazamResult.title
    val artist = soundcloudTrack?.user?.username ?: shazamResult.artist

    val likedTracks by LikeRepository.likedTracks.collectAsStateWithLifecycle()
    val isLiked = soundcloudTrack?.let { track -> likedTracks.any { it.id == track.id } } == true

    Box(modifier = Modifier.fillMaxSize()) {

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.statusBarsPadding())
            Spacer(modifier = Modifier.height(72.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = artist,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (soundcloudTrack != null) {
                    Button(
                        onClick = onPlayClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.recognition_listen_on_kittytune),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.btn_retry),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Button(
                    onClick = {
                        soundcloudTrack?.let { track ->
                            if (isLiked) {
                                LikeRepository.removeLike(track.id)
                            } else {
                                LikeRepository.addLike(track)
                                AchievementManager.increment("liker_50")
                                AchievementManager.increment("liker_1000")
                                AchievementManager.increment("liker_5000")
                            }
                        }
                    },
                    modifier = Modifier.height(52.dp),
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = stringResource(R.string.player_like_action), 
                        modifier = Modifier.padding(horizontal = 24.dp),
                        tint = if (isLiked) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }

                if (soundcloudTrack != null) {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.height(52.dp),
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.btn_retry), modifier = Modifier.padding(horizontal = 24.dp))
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                ElevatedCard(
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 18.dp),
                    modifier = Modifier.size(260.dp)
                ) {
                    if (imageUrl != null) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.MusicNote, null,
                                Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(120.dp))
        }
    }
}

@Composable
private fun ErrorView(error: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Faithful reproduction of home_not_found_illustration from Google Pixel Now Playing
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.recognition_track_not_found),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(Modifier.height(48.dp))

        FilledTonalButton(
            onClick = onRetry,
            shapes = ButtonDefaults.shapes()
        ) {
            Text(stringResource(R.string.btn_retry))
        }
    }
}
