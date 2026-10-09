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
import androidx.compose.ui.unit.IntOffset
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

    BackHandler(enabled = state is RecognitionState.Searching) {
        viewModel.cancelRecognition()
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
        val lottiePrimary   = MaterialTheme.colorScheme.primary.copy(alpha = 1f).toArgb()
        val lottieSecondary = MaterialTheme.colorScheme.secondary.copy(alpha = 1f).toArgb()
        val lottieTertiary  = MaterialTheme.colorScheme.tertiary.copy(alpha = 1f).toArgb()
        val bgDynamicProps = rememberLottieDynamicProperties(
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
                if (state is RecognitionState.Searching) {
                    viewModel.cancelRecognition()
                } else {
                    onBackClick()
                }
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

    val buttonColor = if (isSearching)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.secondary
    val iconTint = if (isSearching)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.onSecondary

    val labelColor by animateColorAsState(
        targetValue = if (isSearching)
            MaterialTheme.colorScheme.onPrimaryContainer
        else
            MaterialTheme.colorScheme.onSecondaryContainer,
        animationSpec = tween(300),
        label = "label_color"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Spacer(modifier = Modifier.weight(1f))

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

        val homeLabelTopPadding by animateDpAsState(
            targetValue = if (isSearching) 21.dp else (-31).dp,
            animationSpec = tween(1000, easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)),
            label = "HomeLabelTopPadding"
        )

        Spacer(Modifier.height(67.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset { IntOffset(x = 0, y = homeLabelTopPadding.roundToPx()) }
        ) {
            AnimatedContent(
                targetState = if (isSearching) {
                    stringResource(R.string.recognition_listening)
                } else if (audioSource == RecognitionAudioSource.MIC) {
                    stringResource(R.string.recognition_tap_to_identify)
                } else {
                    stringResource(R.string.recognition_tap_to_identify_device)
                },
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "title_crossfade"
            ) { titleText ->
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleLarge,
                    color = labelColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            val subtitleAlpha by animateFloatAsState(
                targetValue = if (isSearching) 0f else 1f,
                animationSpec = tween(250),
                label = "subtitle_alpha"
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    if (audioSource == RecognitionAudioSource.MIC) R.string.recognition_listening_desc
                    else R.string.recognition_listening_device_desc
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f * subtitleAlpha),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .graphicsLayer { alpha = subtitleAlpha }
            )
        }

        val controlsAlpha by animateFloatAsState(
            targetValue = if (isSearching) 0f else 1f,
            animationSpec = tween(250, easing = FastOutSlowInEasing),
            label = "controls_alpha"
        )
        val controlsSlideY by animateFloatAsState(
            targetValue = if (isSearching) 24f else 0f,
            animationSpec = tween(250, easing = FastOutSlowInEasing),
            label = "controls_slide"
        )

        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = controlsAlpha
                    translationY = controlsSlideY
                }
        ) {
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
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                iconSpacing = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
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
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier
                .height(56.dp)
                .defaultMinSize(minWidth = 0.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.btn_retry),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
