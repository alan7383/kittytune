package com.alananasss.kittytune.ui.upload

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import com.alananasss.kittytune.data.upload.BuyModuleType
import com.alananasss.kittytune.data.upload.SOUNDCLOUD_GENRES
import com.alananasss.kittytune.data.upload.SOUNDCLOUD_QUICK_GENRES
import com.alananasss.kittytune.data.upload.CommerceOption
import com.alananasss.kittytune.data.upload.getGenreStringRes
import com.alananasss.kittytune.data.upload.GeoBlockingMode
import com.alananasss.kittytune.data.upload.TrackLicense
import com.alananasss.kittytune.data.upload.TrackPrivacy
import com.alananasss.kittytune.data.upload.UploadState
import com.alananasss.kittytune.data.upload.UploadStep
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UploadScreen(
    onBackClick: () -> Unit,
    onLoginClick: () -> Unit,
    trackToEdit: com.alananasss.kittytune.domain.Track? = null,
    viewModel: UploadViewModel = viewModel()
) {
    val context = LocalContext.current
    val uploadState by viewModel.uploadState.collectAsStateWithLifecycle()

    LaunchedEffect(trackToEdit) {
        if (trackToEdit != null && viewModel.editingTrackId != trackToEdit.id) {
            viewModel.loadTrackForEditing(trackToEdit)
        }
    }

    if (!viewModel.isLoggedIn) {
        UploadLoginRequired(onLoginClick = onLoginClick, onBackClick = onBackClick)
        return
    }

    var tempArtworkBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showCropDialog by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.onFileSelected(it, context) }
    }

    val artworkPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                try {
                    val bitmap = if (Build.VERSION.SDK_INT < 28) {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    } else {
                        val source = ImageDecoder.createSource(context.contentResolver, uri)
                        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                            decoder.isMutableRequired = true
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                    }
                    if (bitmap != null) {
                        tempArtworkBitmap = bitmap
                        showCropDialog = true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    )

    if (showCropDialog && tempArtworkBitmap != null) {
        TrackArtworkCropDialog(
            bitmap = tempArtworkBitmap,
            onDismiss = {
                showCropDialog = false
                tempArtworkBitmap = null
            },
            onSave = { croppedBitmap ->
                viewModel.artworkBitmap = croppedBitmap
                viewModel.artworkUri = null
                showCropDialog = false
                tempArtworkBitmap = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (viewModel.isEditMode) stringResource(R.string.edit_track_title) else stringResource(R.string.upload_screen_title),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.btn_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        when (val state = uploadState) {
            is UploadState.Idle -> {
                UploadFilePicker(
                    onPickFile = { filePicker.launch("audio/*") },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            is UploadState.FileSelected -> {
                UploadDetailsForm(
                    viewModel = viewModel,
                    fileName = state.fileName,
                    fileSizeBytes = state.fileSizeBytes,
                    onPickArtwork = {
                        artworkPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onChangeFile = { filePicker.launch("audio/*") },
                    onUpload = { viewModel.startUpload(context) },
                    onBackClick = onBackClick,
                    modifier = Modifier.padding(innerPadding)
                )
            }

            is UploadState.Uploading -> {
                UploadProgressScreen(
                    step = state.step,
                    s3Progress = viewModel.uploadFileProgress,
                    hasArtwork = viewModel.artworkBitmap != null || viewModel.artworkUri != null,
                    onCancel = { viewModel.cancelUpload() },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            is UploadState.Success -> {
                UploadSuccessScreen(
                    trackTitle = state.trackTitle,
                    isEditMode = viewModel.isEditMode,
                    onUploadAnother = { viewModel.resetAll() },
                    onBack = {
                        com.alananasss.kittytune.ui.profile.ProfileViewModel.triggerRefresh()
                        viewModel.resetAll()
                        onBackClick()
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            is UploadState.Error -> {
                val errorMessage = if (state.formatArg != null) {
                    stringResource(state.messageRes, state.formatArg)
                } else {
                    stringResource(state.messageRes)
                }
                UploadErrorScreen(
                    message = errorMessage,
                    isEditMode = viewModel.isEditMode,
                    onRetry = { viewModel.resetToFileSelected() },
                    onBack = onBackClick,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UploadLoginRequired(
    onLoginClick: () -> Unit,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_upload)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.btn_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(100.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            Text(
                stringResource(R.string.upload_login_required_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.upload_login_required_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onLoginClick,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(0.7f)
            ) {
                Icon(Icons.AutoMirrored.Rounded.Login, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.upload_login_btn), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UploadFilePicker(
    onPickFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(120.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.AudioFile,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(Modifier.height(32.dp))
        Text(
            stringResource(R.string.upload_picker_hero_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.upload_picker_hero_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(40.dp))

        Button(
            onClick = onPickFile,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(56.dp)
        ) {
            Icon(Icons.Rounded.FileUpload, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.upload_picker_btn),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.upload_picker_max_size),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class
)
@Composable
private fun UploadDetailsForm(
    viewModel: UploadViewModel,
    fileName: String,
    fileSizeBytes: Long,
    onPickArtwork: () -> Unit,
    onChangeFile: () -> Unit,
    onUpload: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    var showGenrePickerSheet by remember { mutableStateOf(false) }
    var showChecklistBottomSheet by remember { mutableStateOf(false) }
    var showStorefrontSheet by remember { mutableStateOf(false) }
    val isTitleComplete = viewModel.title.isNotBlank()
    val isArtworkComplete =
        viewModel.artworkBitmap != null || viewModel.artworkUri != null || !viewModel.existingArtworkUrl.isNullOrBlank()
    val isGenreComplete = viewModel.genre.isNotBlank()
    val isDescriptionComplete = viewModel.description.isNotBlank()
    val completedSteps = listOf(isTitleComplete, isArtworkComplete, isGenreComplete, isDescriptionComplete).count { it }

    if (showChecklistBottomSheet) {
        TrackInfoChecklistBottomSheet(
            completedSteps = completedSteps,
            isTitleComplete = isTitleComplete,
            isArtworkComplete = isArtworkComplete,
            isGenreComplete = isGenreComplete,
            isDescriptionComplete = isDescriptionComplete,
            onDismiss = { showChecklistBottomSheet = false }
        )
    }

    if (showGenrePickerSheet) {
        GenrePickerBottomSheet(
            selectedGenre = viewModel.genre,
            onGenreSelected = { viewModel.genre = it },
            onDismiss = { showGenrePickerSheet = false }
        )
    }

    if (showStorefrontSheet) {
        ArtistStorefrontBottomSheet(
            viewModel = viewModel,
            onDismiss = { showStorefrontSheet = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
            .padding(bottom = 180.dp)
    ) {
        TrackInfoChecklistBanner(
            completedSteps = completedSteps,
            onClick = { showChecklistBottomSheet = true }
        )

        Spacer(Modifier.height(20.dp))
        UploadDetailsHeader(
            viewModel = viewModel,
            fileName = fileName,
            fileSizeBytes = fileSizeBytes,
            onPickArtwork = onPickArtwork,
            onChangeFile = onChangeFile
        )

        Spacer(Modifier.height(16.dp))

        ExpressiveConnectedButtonGroup(
            options = listOf(0, 1, 2, 3),
            selectedOption = viewModel.selectedCategoryTab,
            onOptionSelected = { viewModel.selectedCategoryTab = it },
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
            labelProvider = { tabIndex ->
                Text(
                    text = when (tabIndex) {
                        0 -> stringResource(R.string.upload_tab_basic_info)
                        1 -> stringResource(R.string.upload_tab_metadata)
                        2 -> stringResource(R.string.upload_tab_permissions)
                        3 -> stringResource(R.string.upload_tab_advanced)
                        else -> ""
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (viewModel.selectedCategoryTab == tabIndex) FontWeight.Bold else FontWeight.Medium
                )
            },
            iconProvider = { tabIndex ->
                val isSelected = viewModel.selectedCategoryTab == tabIndex
                if (tabIndex == 3) {
                    BadgedBox(
                        badge = {
                            Badge(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(6.dp)
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = when (tabIndex) {
                            0 -> Icons.Rounded.Info
                            1 -> Icons.Rounded.Sell
                            2 -> Icons.Rounded.Shield
                            else -> Icons.Rounded.Info
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        )

        Spacer(Modifier.height(16.dp))

        when (viewModel.selectedCategoryTab) {
            0 -> {
                OutlinedTextField(
                    value = viewModel.title,
                    onValueChange = { viewModel.onTitleChanged(it) },
                    label = { Text(stringResource(R.string.upload_field_title)) },
                    minLines = 1,
                    maxLines = 3,
                    isError = viewModel.title.isBlank(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                if (viewModel.title.isBlank()) {
                    Text(
                        stringResource(R.string.upload_field_title_required),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))
                val userPrefix =
                    "https://soundcloud.com/${if (viewModel.userPermalink.isNotBlank()) viewModel.userPermalink else "user"}/"
                OutlinedTextField(
                    value = viewModel.permalink,
                    onValueChange = { viewModel.onPermalinkChanged(it) },
                    label = { Text(stringResource(R.string.upload_field_permalink)) },
                    visualTransformation = PermaLinkVisualTransformation(
                        prefix = userPrefix,
                        prefixColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    ),
                    isError = !viewModel.isPermalinkValid,
                    minLines = 1,
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                if (!viewModel.isPermalinkValid) {
                    Text(
                        stringResource(R.string.upload_field_permalink_invalid),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = viewModel.artist,
                    onValueChange = { viewModel.artist = it },
                    label = { Text(stringResource(R.string.upload_field_artist)) },
                    supportingText = {
                        Text(
                            stringResource(R.string.upload_field_hint_artist),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    minLines = 1,
                    maxLines = 3,
                    isError = viewModel.artist.isBlank(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                if (viewModel.artist.isBlank()) {
                    Text(
                        stringResource(R.string.upload_field_artist_required),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(R.string.upload_field_genre),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        Button(
                            onClick = { showGenrePickerSheet = true },
                            shapes = ButtonDefaults.shapes(),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.upload_field_genre_pick).uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (viewModel.genre.isNotBlank() && !SOUNDCLOUD_QUICK_GENRES.any {
                            it.equals(
                                viewModel.genre,
                                ignoreCase = true
                            )
                        }) {
                        item {
                            val stringRes = getGenreStringRes(viewModel.genre)
                            val customDisplayName =
                                if (stringRes != null) stringResource(stringRes) else viewModel.genre
                            FilterChip(
                                selected = true,
                                onClick = { viewModel.genre = "" },
                                label = { Text(customDisplayName.uppercase(), fontWeight = FontWeight.Bold) },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }

                    items(SOUNDCLOUD_QUICK_GENRES) { genreKey ->
                        val isSelected = viewModel.genre.equals(genreKey, ignoreCase = true)
                        val stringRes = getGenreStringRes(genreKey)
                        val displayName = if (stringRes != null) stringResource(stringRes) else genreKey

                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                viewModel.genre = if (isSelected) "" else genreKey
                            },
                            label = {
                                Text(
                                    displayName.uppercase(),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = viewModel.tagInput,
                    onValueChange = { viewModel.tagInput = it },
                    label = { Text(stringResource(R.string.upload_field_tags)) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        viewModel.addTag(viewModel.tagInput)
                    }),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (viewModel.tags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(viewModel.tags) { tag ->
                            InputChip(
                                selected = true,
                                onClick = { viewModel.removeTag(tag) },
                                label = { Text(tag) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.upload_field_tags_delete),
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { viewModel.removeTag(tag) }
                                    )
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = viewModel.description,
                    onValueChange = { viewModel.description = it },
                    label = { Text(stringResource(R.string.upload_field_description)) },
                    minLines = 3,
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = viewModel.caption,
                    onValueChange = { if (it.length <= 140) viewModel.caption = it },
                    label = { Text(stringResource(R.string.upload_field_caption)) },
                    placeholder = { Text(stringResource(R.string.upload_field_caption_hint)) },
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.upload_field_caption_helper),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                            Text(
                                text = "${viewModel.caption.length}/140",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    minLines = 2,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(R.string.upload_visibility_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                ExpressiveConnectedButtonGroup(
                    options = listOf(TrackPrivacy.PUBLIC, TrackPrivacy.PRIVATE),
                    selectedOption = viewModel.privacy,
                    onOptionSelected = { viewModel.privacy = it },
                    labelProvider = { privacy ->
                        Text(
                            text = when (privacy) {
                                TrackPrivacy.PUBLIC -> stringResource(R.string.upload_visibility_public)
                                TrackPrivacy.PRIVATE -> stringResource(R.string.upload_visibility_private)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (viewModel.privacy == privacy) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    iconProvider = { privacy ->
                        Icon(
                            imageVector = when (privacy) {
                                TrackPrivacy.PUBLIC -> Icons.Rounded.Public
                                TrackPrivacy.PRIVATE -> Icons.Rounded.Lock
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )

                Spacer(Modifier.height(16.dp))
                ScheduleSection(viewModel = viewModel)
            }

            1 -> {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.upload_field_contains_music),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = viewModel.containsMusic,
                                onCheckedChange = { viewModel.containsMusic = it }
                            )
                        }
                        Text(
                            stringResource(R.string.upload_field_contains_music_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = viewModel.albumTitle,
                            onValueChange = { viewModel.albumTitle = it },
                            label = { Text(stringResource(R.string.upload_field_album_title)) },
                            placeholder = { Text(stringResource(R.string.upload_field_album_title_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = viewModel.releaseTitle,
                            onValueChange = { viewModel.releaseTitle = it },
                            label = { Text(stringResource(R.string.upload_field_release_title)) },
                            placeholder = { Text(stringResource(R.string.upload_field_release_title_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(Modifier.height(12.dp))
                        var showReleaseDatePicker by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showReleaseDatePicker = true }
                        ) {
                            OutlinedTextField(
                                value = viewModel.releaseDate,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.upload_field_release_date)) },
                                placeholder = { Text(stringResource(R.string.upload_field_release_date_hint)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.CalendarToday,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (viewModel.releaseDate.isNotBlank()) {
                                        IconButton(onClick = { viewModel.releaseDate = "" }) {
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    } else {
                                        IconButton(onClick = { showReleaseDatePicker = true }) {
                                            Icon(
                                                Icons.Rounded.Event,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = false,
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledLeadingIconColor = MaterialTheme.colorScheme.primary,
                                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        if (showReleaseDatePicker) {
                            val initialDateMillis = remember(viewModel.releaseDate) {
                                if (viewModel.releaseDate.isNotBlank()) {
                                    try {
                                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
                                            timeZone = java.util.TimeZone.getTimeZone("UTC")
                                        }
                                        sdf.parse(viewModel.releaseDate)?.time ?: System.currentTimeMillis()
                                    } catch (_: Exception) {
                                        System.currentTimeMillis()
                                    }
                                } else {
                                    System.currentTimeMillis()
                                }
                            }
                            val datePickerState = rememberDatePickerState(
                                initialSelectedDateMillis = initialDateMillis
                            )
                            DatePickerDialog(
                                onDismissRequest = { showReleaseDatePicker = false },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            datePickerState.selectedDateMillis?.let { selected ->
                                                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                                    .apply {
                                                        timeZone = java.util.TimeZone.getTimeZone("UTC")
                                                    }
                                                viewModel.releaseDate = sdf.format(java.util.Date(selected))
                                            }
                                            showReleaseDatePicker = false
                                        },
                                        shapes = ButtonDefaults.shapes()
                                    ) {
                                        Text(stringResource(android.R.string.ok), fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = { showReleaseDatePicker = false },
                                        shapes = ButtonDefaults.shapes()
                                    ) {
                                        Text(stringResource(android.R.string.cancel))
                                    }
                                }
                            ) {
                                DatePicker(state = datePickerState)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Rounded.Business,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.upload_section_copyright),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = viewModel.labelName,
                            onValueChange = { viewModel.labelName = it },
                            label = { Text(stringResource(R.string.upload_field_record_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = viewModel.publisher,
                            onValueChange = { viewModel.publisher = it },
                            label = { Text(stringResource(R.string.upload_field_publisher_meta)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = viewModel.composer,
                            onValueChange = { viewModel.composer = it },
                            label = { Text(stringResource(R.string.upload_field_composer_meta)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = viewModel.pLine,
                            onValueChange = { viewModel.pLine = it },
                            label = { Text(stringResource(R.string.upload_field_p_line)) },
                            placeholder = { Text(stringResource(R.string.upload_field_p_line_hint)) },
                            supportingText = {
                                Text(
                                    text = stringResource(R.string.upload_field_p_line_info),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = viewModel.cLine,
                            onValueChange = { viewModel.cLine = it },
                            label = { Text(stringResource(R.string.upload_field_c_line)) },
                            placeholder = { Text(stringResource(R.string.upload_field_c_line_hint)) },
                            supportingText = {
                                Text(
                                    text = stringResource(R.string.upload_field_c_line_info),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Rounded.QrCode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.upload_section_codes_ids),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = viewModel.isrc,
                            onValueChange = { viewModel.isrc = it },
                            label = { Text(stringResource(R.string.upload_field_isrc)) },
                            placeholder = { Text(stringResource(R.string.upload_field_isrc_hint)) },
                            supportingText = {
                                Text(
                                    text = stringResource(R.string.upload_field_isrc_info),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = viewModel.iswc,
                            onValueChange = { viewModel.iswc = it },
                            label = { Text(stringResource(R.string.upload_field_iswc)) },
                            placeholder = { Text(stringResource(R.string.upload_field_iswc_hint)) },
                            supportingText = {
                                Text(
                                    text = stringResource(R.string.upload_field_iswc_info),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = viewModel.upcOrEan,
                            onValueChange = { viewModel.upcOrEan = it },
                            label = { Text(stringResource(R.string.upload_field_upc_or_ean)) },
                            placeholder = { Text(stringResource(R.string.upload_field_upc_or_ean_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Rounded.Explicit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.upload_toggle_explicit),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = viewModel.explicitContent,
                                onCheckedChange = { viewModel.explicitContent = it }
                            )
                        }
                        Text(
                            stringResource(R.string.upload_field_explicit_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.upload_license_group_title),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.upload_license_info),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.license = TrackLicense.ALL_RIGHTS_RESERVED }
                                .padding(vertical = 6.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = viewModel.license == TrackLicense.ALL_RIGHTS_RESERVED,
                                onClick = { viewModel.license = TrackLicense.ALL_RIGHTS_RESERVED }
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.upload_license_all_rights),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (viewModel.license == TrackLicense.ALL_RIGHTS_RESERVED) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (viewModel.license == TrackLicense.ALL_RIGHTS_RESERVED) {
                                        viewModel.license = TrackLicense.CC_BY_NC_SA
                                    }
                                }
                                .padding(vertical = 6.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = viewModel.license != TrackLicense.ALL_RIGHTS_RESERVED,
                                onClick = {
                                    if (viewModel.license == TrackLicense.ALL_RIGHTS_RESERVED) {
                                        viewModel.license = TrackLicense.CC_BY_NC_SA
                                    }
                                }
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stringResource(R.string.upload_license_cc),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (viewModel.license != TrackLicense.ALL_RIGHTS_RESERVED) FontWeight.Bold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (viewModel.license != TrackLicense.ALL_RIGHTS_RESERVED) {
                                        Spacer(Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ) {
                                            Text(
                                                text = viewModel.license.displayName,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = stringResource(R.string.upload_license_cc_some_rights),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        AnimatedVisibility(
                            visible = viewModel.license != TrackLicense.ALL_RIGHTS_RESERVED,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    PermissionCheckboxRow(
                                        title = stringResource(R.string.upload_license_attribution),
                                        description = stringResource(R.string.upload_license_attribution_desc),
                                        checked = viewModel.license.isBy,
                                        onCheckedChange = { isChecked ->
                                            viewModel.license = TrackLicense.fromCreativeCommons(
                                                by = isChecked,
                                                nc = viewModel.license.isNc,
                                                nd = viewModel.license.isNd,
                                                sa = viewModel.license.isSa
                                            )
                                        }
                                    )

                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    )
                                    PermissionCheckboxRow(
                                        title = stringResource(R.string.upload_license_noncommercial),
                                        description = stringResource(R.string.upload_license_noncommercial_desc),
                                        checked = viewModel.license.isNc,
                                        onCheckedChange = { isChecked ->
                                            viewModel.license = TrackLicense.fromCreativeCommons(
                                                by = viewModel.license.isBy,
                                                nc = isChecked,
                                                nd = viewModel.license.isNd,
                                                sa = viewModel.license.isSa
                                            )
                                        }
                                    )

                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    )
                                    PermissionCheckboxRow(
                                        title = stringResource(R.string.upload_license_no_derivatives),
                                        description = stringResource(R.string.upload_license_no_derivatives_desc),
                                        checked = viewModel.license.isNd,
                                        onCheckedChange = { isChecked ->
                                            viewModel.license = TrackLicense.fromCreativeCommons(
                                                by = viewModel.license.isBy,
                                                nc = viewModel.license.isNc,
                                                nd = isChecked,
                                                sa = if (isChecked) false else viewModel.license.isSa
                                            )
                                        }
                                    )

                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    )
                                    PermissionCheckboxRow(
                                        title = stringResource(R.string.upload_license_share_alike),
                                        description = stringResource(R.string.upload_license_share_alike_desc),
                                        checked = viewModel.license.isSa,
                                        onCheckedChange = { isChecked ->
                                            viewModel.license = TrackLicense.fromCreativeCommons(
                                                by = viewModel.license.isBy,
                                                nc = viewModel.license.isNc,
                                                nd = if (isChecked) false else viewModel.license.isNd,
                                                sa = isChecked
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Rounded.ShoppingCart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.upload_section_commerce_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        ExpressiveConnectedButtonGroup(
                            options = listOf(CommerceOption.BUY_LINK, CommerceOption.STOREFRONT),
                            selectedOption = viewModel.selectedCommerceOption,
                            onOptionSelected = { viewModel.selectedCommerceOption = it },
                            labelProvider = { option ->
                                Text(
                                    text = when (option) {
                                        CommerceOption.BUY_LINK -> stringResource(R.string.upload_commerce_choice_buy_link)
                                        CommerceOption.STOREFRONT -> stringResource(R.string.upload_commerce_choice_storefront)
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (viewModel.selectedCommerceOption == option) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            iconProvider = { option ->
                                Icon(
                                    imageVector = when (option) {
                                        CommerceOption.BUY_LINK -> Icons.Rounded.ShoppingCart
                                        CommerceOption.STOREFRONT -> Icons.Rounded.ShoppingBag
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )

                        Spacer(Modifier.height(12.dp))

                        if (viewModel.selectedCommerceOption == CommerceOption.BUY_LINK) {
                            OutlinedTextField(
                                value = viewModel.purchaseTitle,
                                onValueChange = { viewModel.purchaseTitle = it },
                                label = { Text(stringResource(R.string.upload_field_purchase_title)) },
                                placeholder = { Text(stringResource(R.string.upload_field_purchase_title_hint)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(Modifier.height(12.dp))
                            val isPurchaseUrlValid =
                                viewModel.purchaseUrl.isBlank() || UploadViewModel.isValidUrl(viewModel.purchaseUrl)
                            OutlinedTextField(
                                value = viewModel.purchaseUrl,
                                onValueChange = { viewModel.purchaseUrl = it },
                                label = { Text(stringResource(R.string.upload_field_purchase_url)) },
                                placeholder = { Text(stringResource(R.string.upload_field_url_hint)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                isError = viewModel.purchaseUrl.isNotBlank() && !isPurchaseUrlValid,
                                supportingText = {
                                    if (viewModel.purchaseUrl.isNotBlank() && !isPurchaseUrlValid) {
                                        Text(
                                            text = stringResource(R.string.upload_field_url_invalid),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        } else {
                            StorefrontSectionContent(
                                viewModel = viewModel,
                                onOpenStorefrontSheet = { showStorefrontSheet = true }
                            )
                        }
                    }
                }
            }

            2 -> {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Rounded.VpnLock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.upload_perm_section_access),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        PermissionCheckboxRow(
                            title = stringResource(R.string.upload_perm_direct_downloads),
                            description = if (viewModel.downloadable) {
                                stringResource(R.string.upload_perm_direct_downloads_desc_on)
                            } else {
                                stringResource(R.string.upload_perm_direct_downloads_desc_off)
                            },
                            checked = viewModel.downloadable,
                            onCheckedChange = { viewModel.downloadable = it }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                        PermissionCheckboxRow(
                            title = stringResource(R.string.upload_perm_offline_listening),
                            description = if (viewModel.offlineListening) {
                                stringResource(R.string.upload_perm_offline_listening_desc_on)
                            } else {
                                stringResource(R.string.upload_perm_offline_listening_desc_off)
                            },
                            checked = viewModel.offlineListening,
                            onCheckedChange = { viewModel.offlineListening = it }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                        PermissionCheckboxRow(
                            title = stringResource(R.string.upload_perm_rss_feed),
                            description = if (viewModel.feedable) {
                                stringResource(R.string.upload_perm_rss_feed_desc_on)
                            } else {
                                stringResource(R.string.upload_perm_rss_feed_desc_off)
                            },
                            checked = viewModel.feedable,
                            onCheckedChange = { viewModel.feedable = it }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                        PermissionCheckboxRow(
                            title = stringResource(R.string.upload_perm_embed_code),
                            description = if (viewModel.embeddable) {
                                stringResource(R.string.upload_perm_embed_code_desc_on)
                            } else {
                                stringResource(R.string.upload_perm_embed_code_desc_off)
                            },
                            checked = viewModel.embeddable,
                            onCheckedChange = { viewModel.embeddable = it }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                        PermissionCheckboxRow(
                            title = stringResource(R.string.upload_perm_app_playback),
                            description = if (viewModel.apiStreamable) {
                                stringResource(R.string.upload_perm_app_playback_desc_on)
                            } else {
                                stringResource(R.string.upload_perm_app_playback_desc_off)
                            },
                            checked = viewModel.apiStreamable,
                            onCheckedChange = { viewModel.apiStreamable = it }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Comment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.upload_perm_section_quiet_mode),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        PermissionCheckboxRow(
                            title = stringResource(R.string.upload_perm_enable_comments),
                            description = if (viewModel.commentable) {
                                stringResource(R.string.upload_perm_enable_comments_desc_on)
                            } else {
                                stringResource(R.string.upload_perm_enable_comments_desc_off)
                            },
                            checked = viewModel.commentable,
                            onCheckedChange = { viewModel.commentable = it }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                        PermissionCheckboxRow(
                            title = stringResource(R.string.upload_perm_reveal_comments),
                            description = if (viewModel.revealComments) {
                                stringResource(R.string.upload_perm_reveal_comments_desc_on)
                            } else {
                                stringResource(R.string.upload_perm_reveal_comments_desc_off)
                            },
                            checked = viewModel.revealComments,
                            onCheckedChange = { viewModel.revealComments = it }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                        PermissionCheckboxRow(
                            title = stringResource(R.string.upload_perm_display_stats),
                            description = if (viewModel.revealStats) {
                                stringResource(R.string.upload_perm_display_stats_desc_on)
                            } else {
                                stringResource(R.string.upload_perm_display_stats_desc_off)
                            },
                            checked = viewModel.revealStats,
                            onCheckedChange = { viewModel.revealStats = it }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                GeoBlockingSection(viewModel = viewModel)
            }

            3 -> {
                AdvancedSnippetSection(viewModel = viewModel)
            }
        }

        Spacer(Modifier.height(24.dp))

        val context = LocalContext.current
        Button(
            onClick = {
                if (viewModel.isEditMode) {
                    viewModel.saveTrackEdits(context = context)
                } else {
                    onUpload()
                }
            },
            enabled = viewModel.canSubmit,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            if (viewModel.isSavingEdit) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.5.dp
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.edit_track_save_button),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            } else {
                Icon(
                    imageVector = if (viewModel.isEditMode) Icons.Rounded.Save else Icons.Rounded.CloudUpload,
                    contentDescription = null
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(if (viewModel.isEditMode) R.string.edit_track_save_button else R.string.upload_btn_submit),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        if (viewModel.isEditMode) {
            Spacer(Modifier.height(14.dp))
            FilledTonalButton(
                onClick = { viewModel.showDeleteConfirmationDialog = true },
                enabled = !viewModel.isDeletingTrack && !viewModel.isSavingEdit,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (viewModel.isDeletingTrack) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.edit_track_delete_button),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                } else {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.edit_track_delete_button),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }

        if (viewModel.showDeleteConfirmationDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.showDeleteConfirmationDialog = false },
                icon = {
                    Icon(
                        Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = {
                    Text(stringResource(R.string.edit_track_delete_dialog_title))
                },
                text = {
                    Text(stringResource(R.string.edit_track_delete_dialog_message))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.showDeleteConfirmationDialog = false
                            viewModel.deleteTrack(onSuccess = onBackClick)
                        },
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(
                            stringResource(R.string.btn_delete),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.showDeleteConfirmationDialog = false },
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                }
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

enum class PieProgressSize(val size: Dp, val strokeWidth: Dp) {
    REGULAR(54.dp, 5.dp),
    LARGE(76.dp, 7.dp)
}

@Composable
fun PieProgressFourStep(
    completedSteps: Int,
    modifier: Modifier = Modifier,
    size: PieProgressSize = PieProgressSize.REGULAR,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    Box(
        modifier = modifier.size(size.size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gapAngle = 8f
            val sweepAngle = (360f - (4 * gapAngle)) / 4f
            val stroke = Stroke(
                width = size.strokeWidth.toPx(),
                cap = StrokeCap.Round
            )
            val padding = size.strokeWidth.toPx() / 2f
            val arcSize = Size(
                width = this.size.width - padding * 2,
                height = this.size.height - padding * 2
            )
            val topLeft = Offset(padding, padding)

            for (i in 0 until 4) {
                val startAngle = -90f + i * (sweepAngle + gapAngle) + gapAngle / 2f
                val color = if (i < completedSteps) activeColor else inactiveColor
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke
                )
            }
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$completedSteps",
                style = if (size == PieProgressSize.LARGE) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "/4",
                style = if (size == PieProgressSize.LARGE) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = if (size == PieProgressSize.LARGE) 2.dp else 1.dp)
            )
        }
    }
}

@Composable
private fun TrackInfoChecklistBanner(
    completedSteps: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.track_info_checklist_banner_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.track_info_checklist_banner_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
            PieProgressFourStep(
                completedSteps = completedSteps,
                size = PieProgressSize.REGULAR
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TrackInfoChecklistBottomSheet(
    completedSteps: Int,
    isTitleComplete: Boolean,
    isArtworkComplete: Boolean,
    isGenreComplete: Boolean,
    isDescriptionComplete: Boolean,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            Text(
                text = stringResource(R.string.track_info_checklist_bottomsheet_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.track_info_checklist_bottomsheet_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                PieProgressFourStep(
                    completedSteps = completedSteps,
                    size = PieProgressSize.LARGE
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ChecklistItem(
                        title = stringResource(R.string.track_info_checklist_bottomsheet_label_title),
                        tip = stringResource(R.string.track_info_checklist_bottomsheet_label_title_tip),
                        isCompleted = isTitleComplete
                    )
                    ChecklistItem(
                        title = stringResource(R.string.track_info_checklist_bottomsheet_label_artwork),
                        isCompleted = isArtworkComplete
                    )
                    ChecklistItem(
                        title = stringResource(R.string.upload_field_genre),
                        isCompleted = isGenreComplete
                    )
                    ChecklistItem(
                        title = stringResource(R.string.upload_field_description),
                        isCompleted = isDescriptionComplete
                    )
                }
            }

            Spacer(Modifier.height(36.dp))

            Button(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = stringResource(R.string.track_info_checklist_bottomsheet_button),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun ChecklistItem(
    title: String,
    tip: String? = null,
    isCompleted: Boolean
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (isCompleted) Icons.Rounded.CheckCircle else Icons.Outlined.Circle,
            contentDescription = stringResource(
                if (isCompleted) R.string.track_info_checklist_completed_step
                else R.string.track_info_checklist_not_completed_step
            ),
            tint = if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .size(24.dp)
                .padding(top = 2.dp)
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!tip.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = tip,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UploadDetailsHeader(
    viewModel: UploadViewModel,
    fileName: String,
    fileSizeBytes: Long,
    onPickArtwork: () -> Unit,
    onChangeFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(116.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable { onPickArtwork() }
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            val artworkModel = viewModel.artworkBitmap ?: viewModel.artworkUri ?: viewModel.existingArtworkUrl
            if (artworkModel != null) {
                AsyncImage(
                    model = artworkModel,
                    contentDescription = stringResource(R.string.track_details_pick_image_content_description),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.PhotoCamera,
                        contentDescription = stringResource(R.string.upload_artwork_change),
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Rounded.AddPhotoAlternate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.upload_artwork_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(if (viewModel.isEditMode) R.string.upload_field_title else R.string.track_details_file_name),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = fileName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!viewModel.isEditMode || fileSizeBytes > 0L) {
                Text(
                    text = formatFileSize(fileSizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(2.dp))
            OutlinedButton(
                onClick = onChangeFile,
                shapes = ButtonDefaults.shapes()
            ) {
                Text(
                    text = stringResource(R.string.track_details_replace_file),
                    fontWeight = FontWeight.Medium
                )
            }
            if (viewModel.isEditMode) {
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Rounded.Star,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.upload_replace_file_next_pro_notice),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UploadProgressScreen(
    step: UploadStep,
    s3Progress: Float,
    hasArtwork: Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = when (step) {
            UploadStep.FETCHING_POLICY -> 0.05f
            UploadStep.UPLOADING_FILE -> (0.05f + s3Progress * 0.70f).coerceIn(0.05f, 0.75f)
            UploadStep.CREATING_TRACK -> 0.82f
            UploadStep.TRANSCODING -> 0.92f
            UploadStep.UPLOADING_ARTWORK -> 0.97f
            UploadStep.DONE -> 1f
        },
        animationSpec = tween(300),
        label = "upload_progress"
    )

    val steps = remember(hasArtwork) {
        buildList {
            add(UploadStep.FETCHING_POLICY)
            add(UploadStep.UPLOADING_FILE)
            add(UploadStep.CREATING_TRACK)
            add(UploadStep.TRANSCODING)
            if (hasArtwork) {
                add(UploadStep.UPLOADING_ARTWORK)
            }
        }
    }

    val currentStepIndex = steps.indexOf(step).let { if (it == -1) steps.size else it }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(130.dp)
        ) {
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.size(130.dp),
                strokeWidth = 8.dp,
                strokeCap = StrokeCap.Round,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.primary
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${(animatedProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                steps.forEachIndexed { index, itemStep ->
                    val isDone = index < currentStepIndex || step == UploadStep.DONE
                    val isCurrent = index == currentStepIndex && step != UploadStep.DONE
                    val isPending = index > currentStepIndex

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(28.dp)
                        ) {
                            when {
                                isDone -> {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                isCurrent -> {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }

                                else -> {
                                    Icon(
                                        imageVector = Icons.Rounded.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outlineVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        val labelText = when {
                            itemStep == UploadStep.UPLOADING_FILE && isCurrent -> {
                                "${stringResource(itemStep.labelRes)} (${(s3Progress * 100).toInt()}%)"
                            }

                            else -> stringResource(itemStep.labelRes)
                        }

                        Text(
                            text = labelText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isCurrent) FontWeight.Bold else if (isDone) FontWeight.Medium else FontWeight.Normal,
                            color = when {
                                isCurrent -> MaterialTheme.colorScheme.onSurface
                                isDone -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.outline
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        OutlinedButton(
            onClick = onCancel,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth(0.6f)
        ) {
            Icon(Icons.Rounded.Close, null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.upload_btn_cancel))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UploadSuccessScreen(
    trackTitle: String,
    isEditMode: Boolean = false,
    onUploadAnother: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(120.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(if (isEditMode) R.string.edit_track_success_title else R.string.upload_success_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(
                if (isEditMode) R.string.edit_track_success_message else R.string.upload_success_message,
                trackTitle
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = onBack,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.upload_btn_back_to_profile), fontWeight = FontWeight.SemiBold)
        }

        if (!isEditMode) {
            Spacer(Modifier.height(12.dp))

            FilledTonalButton(
                onClick = onUploadAnother,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.upload_btn_upload_another))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UploadErrorScreen(
    message: String,
    isEditMode: Boolean = false,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.size(100.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Error,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(if (isEditMode) R.string.edit_track_error_title else R.string.upload_error_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onRetry,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.Refresh, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.upload_btn_retry))
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onBack,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.upload_btn_cancel))
        }
    }
}


private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024f * 1024f))} MB"
        else -> "${"%.2f".format(bytes / (1024f * 1024f * 1024f))} GB"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleSection(
    viewModel: UploadViewModel,
    modifier: Modifier = Modifier
) {
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showTimePickerDialog by remember { mutableStateOf(false) }
    val currentEpoch = viewModel.scheduledEpochMs ?: (System.currentTimeMillis() + 86400000L)
    val calendar = remember(currentEpoch) {
        Calendar.getInstance().apply { timeInMillis = currentEpoch }
    }

    val locale = Locale.getDefault()
    val dateFormat = remember(locale) {
        SimpleDateFormat("d MMMM yyyy", locale)
    }
    val timeFormat = remember(locale) {
        SimpleDateFormat("HH:mm", locale)
    }
    val dateText = remember(currentEpoch, locale) {
        dateFormat.format(Date(currentEpoch))
    }
    val timeText = remember(currentEpoch, locale) {
        timeFormat.format(Date(currentEpoch))
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.upload_schedule_section_title),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = stringResource(R.string.upload_schedule_toggle_title),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.upload_schedule_toggle_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = viewModel.isSchedulingEnabled,
                        onCheckedChange = { viewModel.toggleScheduling(it) }
                    )
                }

                AnimatedVisibility(
                    visible = viewModel.isSchedulingEnabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDatePickerDialog = true },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.weight(1.3f)
                        ) {
                            Icon(
                                Icons.Rounded.CalendarToday,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(text = dateText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        OutlinedButton(
                            onClick = { showTimePickerDialog = true },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.weight(0.9f)
                        ) {
                            Icon(
                                Icons.Rounded.AccessTime,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(text = timeText, maxLines = 1)
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(top = 2.dp)
                        )
                        Text(
                            text = stringResource(R.string.upload_schedule_free_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }

    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = currentEpoch
        )
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selected ->
                            viewModel.updateScheduledDate(selected)
                        }
                        showDatePickerDialog = false
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.btn_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePickerDialog = false },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                title = {
                    Text(
                        text = stringResource(R.string.upload_schedule_date_picker_title),
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp)
                    )
                }
            )
        }
    }

    if (showTimePickerDialog) {
        val initialHour = calendar.get(Calendar.HOUR_OF_DAY)
        val initialMinute = calendar.get(Calendar.MINUTE)
        val timePickerState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = true
        )
        TimePickerDialog(
            onDismissRequest = { showTimePickerDialog = false },
            onConfirm = {
                viewModel.updateScheduledTime(timePickerState.hour, timePickerState.minute)
                showTimePickerDialog = false
            }
        ) {
            TimePicker(state = timePickerState)
        }
    }
}

@Composable
private fun TimePickerDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                shapes = ButtonDefaults.shapes()
            ) {
                Text(stringResource(R.string.btn_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                shapes = ButtonDefaults.shapes()
            ) {
                Text(stringResource(R.string.btn_cancel))
            }
        },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                content()
            }
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StorefrontSectionContent(
    viewModel: UploadViewModel,
    onOpenStorefrontSheet: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        Text(
            text = stringResource(R.string.upload_storefront_banner_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (viewModel.hasStorefront && viewModel.storefrontTitle.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val bitmap = viewModel.storefrontBitmap
                    val imgUrl = viewModel.storefrontImageUrl
                    if (bitmap != null || !imgUrl.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow),
                            contentAlignment = Alignment.Center
                        ) {
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (!imgUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = imgUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ShoppingBag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = viewModel.storefrontTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = viewModel.storefrontType.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (viewModel.storefrontPrice.isNotBlank()) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = viewModel.storefrontPrice,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Button(
            onClick = onOpenStorefrontSheet,
            shapes = ButtonDefaults.shapes(),
            colors = if (viewModel.hasStorefront && viewModel.storefrontTitle.isNotBlank()) {
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            } else {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = if (viewModel.hasStorefront && viewModel.storefrontTitle.isNotBlank()) Icons.Rounded.Edit else Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(
                    if (viewModel.hasStorefront && viewModel.storefrontTitle.isNotBlank()) R.string.upload_storefront_btn_edit
                    else R.string.upload_storefront_btn_add
                ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistStorefrontBottomSheet(
    viewModel: UploadViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    var tempStorefrontBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showStorefrontCropDialog by remember { mutableStateOf(false) }

    val storefrontImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                try {
                    val bitmap = if (Build.VERSION.SDK_INT < 28) {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    } else {
                        val source = ImageDecoder.createSource(context.contentResolver, uri)
                        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                            decoder.isMutableRequired = true
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                    }
                    if (bitmap != null) {
                        tempStorefrontBitmap = bitmap
                        showStorefrontCropDialog = true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    )

    if (showStorefrontCropDialog && tempStorefrontBitmap != null) {
        TrackArtworkCropDialog(
            bitmap = tempStorefrontBitmap,
            onDismiss = {
                showStorefrontCropDialog = false
                tempStorefrontBitmap = null
            },
            onSave = { croppedBitmap ->
                viewModel.storefrontBitmap = croppedBitmap
                viewModel.storefrontImageUri = null
                showStorefrontCropDialog = false
                tempStorefrontBitmap = null
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(stringResource(R.string.upload_storefront_btn_delete)) },
            text = { Text(stringResource(R.string.upload_storefront_delete_confirmation)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteStorefront(onSuccess = onDismiss)
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.btn_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = false },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.upload_storefront_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.btn_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.upload_storefront_preview_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val bitmap = viewModel.storefrontBitmap
                    val imgUrl = viewModel.storefrontImageUrl

                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (!imgUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = imgUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Cloud,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "· ${viewModel.storefrontType.displayName}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = viewModel.storefrontTitle.ifBlank { stringResource(R.string.upload_storefront_field_title) },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (viewModel.storefrontPrice.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = viewModel.storefrontPrice,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    IconButton(
                        onClick = {},
                        enabled = viewModel.storefrontLink.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = if (viewModel.storefrontLink.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = 0.4f
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val bitmap = viewModel.storefrontBitmap
                val imgUrl = viewModel.storefrontImageUrl

                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable {
                            storefrontImagePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else if (!imgUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = imgUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.PhotoCamera,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                OutlinedTextField(
                    value = viewModel.storefrontPrice,
                    onValueChange = {
                        if (it.length <= 15) viewModel.storefrontPrice = it
                    },
                    label = { Text(stringResource(R.string.upload_storefront_field_price) + " *") },
                    supportingText = {
                        Text(
                            text = "${viewModel.storefrontPrice.length}/15",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    singleLine = true,
                    isError = viewModel.storefrontPrice.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(BuyModuleType.entries) { type ->
                    val isSelected = viewModel.storefrontType == type
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.storefrontType = type },
                        label = {
                            Text(
                                text = type.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = viewModel.storefrontTitle,
                onValueChange = {
                    if (it.length <= 25) viewModel.storefrontTitle = it
                },
                label = { Text(stringResource(R.string.upload_storefront_field_title) + " *") },
                supportingText = {
                    Text(
                        text = "${viewModel.storefrontTitle.length}/25",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                singleLine = true,
                isError = viewModel.storefrontTitle.isBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = viewModel.storefrontDescription,
                onValueChange = {
                    if (it.length <= 140) viewModel.storefrontDescription = it
                },
                label = { Text(stringResource(R.string.upload_storefront_field_description)) },
                supportingText = {
                    Text(
                        text = "${viewModel.storefrontDescription.length}/140",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(12.dp))
            val isLinkValid = viewModel.storefrontLink.isBlank() || UploadViewModel.isValidUrl(viewModel.storefrontLink)
            OutlinedTextField(
                value = viewModel.storefrontLink,
                onValueChange = { viewModel.storefrontLink = it },
                label = { Text(stringResource(R.string.upload_storefront_field_link) + " *") },
                placeholder = { Text(stringResource(R.string.upload_field_url_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                isError = viewModel.storefrontLink.isBlank() || !isLinkValid,
                supportingText = {
                    if (viewModel.storefrontLink.isNotBlank() && !isLinkValid) {
                        Text(
                            text = stringResource(R.string.upload_field_url_invalid),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = viewModel.storefrontLinkTitle,
                onValueChange = {
                    if (it.length <= 25) viewModel.storefrontLinkTitle = it
                },
                label = { Text(stringResource(R.string.upload_storefront_field_button_title)) },
                placeholder = { Text(stringResource(R.string.upload_storefront_field_button_title_hint)) },
                supportingText = {
                    Text(
                        text = "${viewModel.storefrontLinkTitle.length}/25",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            if (!viewModel.storefrontErrorMessage.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = viewModel.storefrontErrorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (viewModel.hasStorefront) {
                    OutlinedButton(
                        onClick = { showDeleteConfirmDialog = true },
                        enabled = !viewModel.isSavingStorefront && !viewModel.isDeletingStorefront,
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (viewModel.isDeletingStorefront) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.upload_storefront_btn_delete),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                val canSave = viewModel.storefrontTitle.isNotBlank() &&
                        viewModel.storefrontPrice.isNotBlank() &&
                        viewModel.storefrontLink.isNotBlank() &&
                        UploadViewModel.isValidUrl(viewModel.storefrontLink) &&
                        !viewModel.isSavingStorefront &&
                        !viewModel.isDeletingStorefront

                Button(
                    onClick = {
                        viewModel.saveStorefront(onSuccess = onDismiss)
                    },
                    enabled = canSave,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(if (viewModel.hasStorefront) 1f else 2f)
                ) {
                    if (viewModel.isSavingStorefront) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.upload_storefront_btn_save),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCheckboxRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(top = 2.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AdvancedSnippetSection(
    viewModel: UploadViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasAudio = viewModel.selectedFileUri != null || viewModel.isEditMode
    val snippetDuration = 20
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val surfaceContainerLow = MaterialTheme.colorScheme.surfaceContainerLow
    val surfaceContainerLowest = MaterialTheme.colorScheme.surfaceContainerLowest
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    var waveformSamples by remember { mutableStateOf<FloatArray?>(null) }
    var waveformLoading by remember { mutableStateOf(false) }

    val waveformUrl = viewModel.waveformUrl
    LaunchedEffect(waveformUrl) {
        if (waveformUrl.isNullOrBlank()) return@LaunchedEffect
        waveformLoading = true
        waveformSamples = null
        withContext(Dispatchers.IO) {
            try {
                val client = com.alananasss.kittytune.data.network.ProxyManager.getOkHttpClient()
                val req = okhttp3.Request.Builder().url(waveformUrl).build()
                val body = client.newCall(req).execute().use { resp ->
                    resp.body?.string()
                } ?: return@withContext
                val json = org.json.JSONObject(body)
                val height = json.optDouble("height", 140.0)
                val samplesArr = json.getJSONArray("samples")
                val count = samplesArr.length()
                if (count == 0) return@withContext
                val result = FloatArray(count) { i ->
                    val s = samplesArr.getDouble(i)
                    Math.pow((s / height).coerceIn(0.0, 1.0), 1.5).toFloat().coerceIn(0.02f, 1f)
                }
                waveformSamples = result
            } catch (_: Exception) {
            }
        }
        waveformLoading = false
    }
    val fallbackBars = remember {
        val rng = java.util.Random(42L)
        val arr = FloatArray(120)
        for (i in arr.indices) {
            val base = (Math.sin(i * 0.15) * 0.35 + 0.5).toFloat()
            val noise = (rng.nextFloat() - 0.5f) * 0.3f
            arr[i] = (base + noise).coerceIn(0.08f, 0.95f)
        }
        arr
    }
    val exoPlayer = remember {
        ExoPlayer.Builder(context.applicationContext).build()
    }
    var isPlaying by remember { mutableStateOf(false) }
    var isLoadingAudio by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var playJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) {
                    isLoadingAudio = false
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                    isPlaying = false
                    isLoadingAudio = false
                } else if (playbackState == Player.STATE_READY) {
                    isLoadingAudio = false
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("UploadSnippet", "ExoPlayer error: ${error.message}", error)
                isPlaying = false
                isLoadingAudio = false
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            playJob?.cancel()
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    fun stopAudioPreview() {
        playJob?.cancel()
        playJob = null
        try {
            exoPlayer.stop()
        } catch (_: Exception) {
        }
        isPlaying = false
        isLoadingAudio = false
    }

    fun startAudioPreview() {
        stopAudioPreview()
        isLoadingAudio = true
        playJob = scope.launch {
            try {
                val uriToPlay: Uri? = when {
                    viewModel.selectedFileUri != null -> viewModel.selectedFileUri
                    viewModel.isEditMode -> {
                        val cleanId = viewModel.editingTrackUrn?.substringAfterLast(":")
                        if (cleanId.isNullOrBlank()) null
                        else {
                            withContext(Dispatchers.IO) {
                                val trackId = cleanId.toLongOrNull() ?: 0L
                                val track = viewModel.editingTrackModel ?: com.alananasss.kittytune.domain.Track(
                                    id = trackId,
                                    title = viewModel.title,
                                    artworkUrl = null,
                                    durationMs = viewModel.trackDurationSeconds.toLong() * 1000L,
                                    user = null
                                )
                                val resolved =
                                    com.alananasss.kittytune.data.StreamResolver.resolveSoundCloudDirect(context, track)
                                resolved?.url?.let { Uri.parse(it) }
                            }
                        }
                    }

                    else -> null
                }

                if (uriToPlay == null) {
                    withContext(Dispatchers.Main) {
                        isLoadingAudio = false
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.upload_error_audio_stream_load),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    val mediaItem = MediaItem.fromUri(uriToPlay)
                    exoPlayer.setMediaItem(mediaItem)
                    val startMs = (viewModel.snippetStartSeconds * 1000L).coerceAtLeast(0L)
                    exoPlayer.seekTo(startMs)
                    exoPlayer.prepare()
                    exoPlayer.play()
                }
                delay(snippetDuration * 1000L)
                withContext(Dispatchers.Main) {
                    if (exoPlayer.isPlaying) {
                        exoPlayer.stop()
                        isPlaying = false
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("UploadSnippet", "Error during snippet playback", e)
                withContext(Dispatchers.Main) {
                    isLoadingAudio = false
                    isPlaying = false
                }
            }
        }
    }

    if (!hasAudio) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceContainerLow),
            modifier = modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(32.dp)
            ) {
                Icon(Icons.Rounded.Audiotrack, null, tint = onSurfaceVariant, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.upload_advanced_snippet_no_audio),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val duration = viewModel.trackDurationSeconds.coerceAtLeast(60)
    val startFrac = (viewModel.snippetStartSeconds.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    val windowFrac = (snippetDuration.toFloat() / duration.toFloat()).coerceIn(0.01f, 1f)

    Column(modifier = modifier.fillMaxWidth()) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceContainerLow),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = primaryColor, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.upload_advanced_snippet_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.weight(1f))
                    if (waveformLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = primaryColor
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = primaryContainer
                    ) {
                        Text(
                            stringResource(R.string.upload_advanced_snippet_current),
                            style = MaterialTheme.typography.labelSmall,
                            color = onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                var waveformWidthPx by remember { mutableStateOf(1f) }
                val barWidthDp = 2.dp
                val spaceWidthDp = 1.dp
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .pointerInput(duration) {
                            waveformWidthPx = size.width.toFloat()
                            detectHorizontalDragGestures { change, dragAmount ->
                                change.consume()
                                if (waveformWidthPx > 0f) {
                                    val fracDelta = dragAmount / waveformWidthPx
                                    val secDelta = (fracDelta * duration).toInt()
                                    val newStart = (viewModel.snippetStartSeconds + secDelta)
                                        .coerceIn(0, (duration - snippetDuration).coerceAtLeast(0))
                                    viewModel.snippetStartSeconds = newStart
                                    viewModel.snippetEndSeconds = newStart + snippetDuration
                                    viewModel.isSnippetCustomized = true
                                    if (isPlaying) {
                                        stopAudioPreview()
                                    }
                                }
                            }
                        }
                ) {
                    waveformWidthPx = size.width
                    val cW = size.width
                    val cH = size.height

                    val barWidthPx = barWidthDp.toPx()
                    val spaceWidthPx = spaceWidthDp.toPx()
                    val stepPx = barWidthPx + spaceWidthPx
                    val totalBars = (cW / stepPx).toInt().coerceAtLeast(10)
                    val baselineY = cH * 0.68f
                    val gapPx = 1.5.dp.toPx()
                    val raw = waveformSamples
                    val barHeights = FloatArray(totalBars) { i ->
                        if (raw != null && raw.isNotEmpty()) {
                            val startIdx = (i * raw.size) / totalBars
                            val endIdx =
                                (((i + 1) * raw.size) / totalBars).coerceAtMost(raw.size).coerceAtLeast(startIdx + 1)
                            var maxV = 0f
                            for (idx in startIdx until endIdx) {
                                if (raw[idx] > maxV) maxV = raw[idx]
                            }
                            maxV.coerceIn(0.04f, 1f)
                        } else {
                            fallbackBars[i % fallbackBars.size]
                        }
                    }

                    val winL = startFrac * cW
                    val winR = (startFrac + windowFrac).coerceAtMost(1f) * cW
                    for (i in 0 until totalBars) {
                        val x = i * stepPx
                        val h = barHeights[i]

                        val topBarH = (baselineY * h * 0.95f).coerceAtLeast(2f)
                        val bottomBarH = ((cH - baselineY - gapPx) * h * 0.70f).coerceAtLeast(1.5f)

                        val inWindow = (x + barWidthPx >= winL && x <= winR)

                        val topColor = if (inWindow) primaryColor else onSurfaceVariant.copy(alpha = 0.35f)
                        val bottomColor =
                            if (inWindow) primaryColor.copy(alpha = 0.60f) else onSurfaceVariant.copy(alpha = 0.16f)
                        drawRoundRect(
                            color = topColor,
                            topLeft = Offset(x, baselineY - topBarH),
                            size = Size(barWidthPx, topBarH),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidthPx / 2f)
                        )
                        drawRoundRect(
                            color = bottomColor,
                            topLeft = Offset(x, baselineY + gapPx),
                            size = Size(barWidthPx, bottomBarH),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidthPx / 2f)
                        )
                    }
                    val frameWidth = (winR - winL).coerceAtLeast(10f)
                    drawRoundRect(
                        color = primaryContainer.copy(alpha = 0.20f),
                        topLeft = Offset(winL, 0f),
                        size = Size(frameWidth, cH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                    )
                    drawRoundRect(
                        color = primaryColor,
                        topLeft = Offset(winL, 0f),
                        size = Size(frameWidth, cH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                    val hw = 8.dp.toPx()
                    val midY = cH / 2f
                    val strokeW = 2.5.dp.toPx()
                    drawLine(
                        primaryColor,
                        Offset(winL + hw, midY - 10.dp.toPx()),
                        Offset(winL + 2.dp.toPx(), midY),
                        strokeW,
                        StrokeCap.Round
                    )
                    drawLine(
                        primaryColor,
                        Offset(winL + 2.dp.toPx(), midY),
                        Offset(winL + hw, midY + 10.dp.toPx()),
                        strokeW,
                        StrokeCap.Round
                    )
                    drawLine(
                        primaryColor,
                        Offset(winR - hw, midY - 10.dp.toPx()),
                        Offset(winR - 2.dp.toPx(), midY),
                        strokeW,
                        StrokeCap.Round
                    )
                    drawLine(
                        primaryColor,
                        Offset(winR - 2.dp.toPx(), midY),
                        Offset(winR - hw, midY + 10.dp.toPx()),
                        strokeW,
                        StrokeCap.Round
                    )
                }

                Spacer(Modifier.height(10.dp))
                val s = viewModel.snippetStartSeconds
                val e = viewModel.snippetEndSeconds
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "%d:%02d".format(s / 60, s % 60),
                        style = MaterialTheme.typography.labelLarge,
                        color = primaryColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.upload_advanced_snippet_current),
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "%d:%02d".format(e / 60, e % 60),
                        style = MaterialTheme.typography.labelLarge,
                        color = primaryColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            if (isPlaying) {
                                stopAudioPreview()
                            } else {
                                startAudioPreview()
                            }
                        },
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isPlaying) primaryColor else primaryContainer,
                            contentColor = if (isPlaying) onPrimaryColor else onPrimaryContainer
                        )
                    ) {
                        if (isLoadingAudio) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = if (isPlaying) onPrimaryColor else onPrimaryContainer
                            )
                        } else {
                            Icon(
                                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isPlaying) stringResource(R.string.upload_advanced_snippet_pause) else stringResource(R.string.upload_advanced_snippet_play),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (viewModel.isSnippetCustomized) {
                        TextButton(
                            onClick = {
                                viewModel.snippetStartSeconds = 0
                                viewModel.snippetEndSeconds = snippetDuration
                                viewModel.isSnippetCustomized = false
                                if (isPlaying) stopAudioPreview()
                            },
                            shapes = ButtonDefaults.shapes()
                        ) {
                            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.upload_advanced_snippet_reset),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceContainerLowest),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Info, null, tint = primaryColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.upload_advanced_snippet_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun GeoBlockingSection(viewModel: UploadViewModel) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Rounded.Public,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        stringResource(R.string.upload_perm_geo_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.upload_perm_geo_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            ExpressiveConnectedButtonGroup(
                options = listOf(
                    GeoBlockingMode.EVERYWHERE,
                    GeoBlockingMode.EXCLUSIVE,
                    GeoBlockingMode.BLOCKED
                ),
                selectedOption = viewModel.geoBlockingMode,
                onOptionSelected = { viewModel.geoBlockingMode = it },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                labelProvider = { mode ->
                    Text(
                        text = when (mode) {
                            GeoBlockingMode.EVERYWHERE -> stringResource(R.string.upload_perm_geo_mode_everywhere)
                            GeoBlockingMode.EXCLUSIVE -> stringResource(R.string.upload_perm_geo_mode_exclusive)
                            GeoBlockingMode.BLOCKED -> stringResource(R.string.upload_perm_geo_mode_blocked)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (viewModel.geoBlockingMode == mode) FontWeight.Bold else FontWeight.Medium
                    )
                },
                iconProvider = { mode ->
                    Icon(
                        imageVector = when (mode) {
                            GeoBlockingMode.EVERYWHERE -> Icons.Rounded.Public
                            GeoBlockingMode.EXCLUSIVE -> Icons.Rounded.Language
                            GeoBlockingMode.BLOCKED -> Icons.Rounded.Block
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )

            AnimatedVisibility(
                visible = viewModel.geoBlockingMode != GeoBlockingMode.EVERYWHERE,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text(
                        text = if (viewModel.geoBlockingMode == GeoBlockingMode.EXCLUSIVE) {
                            stringResource(R.string.upload_perm_geo_exclusive_explanation)
                        } else {
                            stringResource(R.string.upload_perm_geo_blocked_explanation)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = viewModel.geoBlockingRegions,
                        onValueChange = { viewModel.geoBlockingRegions = it.uppercase() },
                        label = {
                            Text(
                                if (viewModel.geoBlockingMode == GeoBlockingMode.EXCLUSIVE) {
                                    stringResource(R.string.upload_perm_geo_field_exclusive_label)
                                } else {
                                    stringResource(R.string.upload_perm_geo_field_blocked_label)
                                }
                            )
                        },
                        placeholder = { Text(stringResource(R.string.upload_perm_geo_field_hint)) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (viewModel.geoBlockingMode == GeoBlockingMode.EXCLUSIVE) {
                                    Icons.Rounded.CheckCircle
                                } else {
                                    Icons.Rounded.Cancel
                                },
                                contentDescription = null,
                                tint = if (viewModel.geoBlockingMode == GeoBlockingMode.EXCLUSIVE) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        },
                        trailingIcon = {
                            if (viewModel.geoBlockingRegions.isNotBlank()) {
                                IconButton(onClick = { viewModel.geoBlockingRegions = "" }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = null)
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(Modifier.height(10.dp))

                    Text(
                        stringResource(R.string.upload_perm_geo_suggestions_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(Modifier.height(6.dp))

                    val quickCountries = listOf("FR", "BE", "CH", "CA", "US", "GB", "DE", "ES", "IT", "JP")
                    val selectedList = viewModel.geoBlockingRegions
                        .split(",")
                        .map { it.trim().uppercase() }
                        .filter { it.isNotBlank() }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(quickCountries) { country ->
                            val isSelected = selectedList.contains(country)
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.toggleCountryCode(country) },
                                label = { Text(country, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            Icons.Rounded.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else null,
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

