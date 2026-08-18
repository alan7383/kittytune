package com.alananasss.kittytune.ui.upload

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.data.upload.CommerceOption
import com.alananasss.kittytune.data.upload.GeoBlockingMode
import com.alananasss.kittytune.data.upload.TrackLicense
import com.alananasss.kittytune.data.upload.TrackPrivacy
import com.alananasss.kittytune.data.upload.TranscodingStatus
import com.alananasss.kittytune.data.upload.UploadMetadata
import com.alananasss.kittytune.data.upload.UploadRepository
import com.alananasss.kittytune.data.upload.UploadState
import com.alananasss.kittytune.data.upload.UploadStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import android.util.Base64
import com.alananasss.kittytune.data.upload.BuyModuleType

import com.alananasss.kittytune.data.DownloadManager
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.profile.ProfileViewModel

class UploadViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = UploadRepository(application)
    private val tokenManager = TokenManager(application)
    val isLoggedIn: Boolean
        get() = !tokenManager.isGuestMode() && !tokenManager.getAccessToken().isNullOrEmpty()
    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState = _uploadState.asStateFlow()

    var editingTrack by mutableStateOf<Track?>(null)
    var editingTrackId by mutableStateOf<Long?>(null)
    var editingTrackUrn by mutableStateOf<String?>(null)
    var existingArtworkUrl by mutableStateOf<String?>(null)
    val isEditMode: Boolean get() = editingTrackId != null
    var isSavingEdit by mutableStateOf(false)
    var isDeletingTrack by mutableStateOf(false)
    var showDeleteConfirmationDialog by mutableStateOf(false)

    var title by mutableStateOf("")
    var userPermalink by mutableStateOf("")
    var permalink by mutableStateOf("")
    var isPermalinkManuallyEdited by mutableStateOf(false)
    var artist by mutableStateOf("")

    var description by mutableStateOf("")
    var genre by mutableStateOf("")
    var tagInput by mutableStateOf("")
    var tags by mutableStateOf(listOf<String>())
    var privacy by mutableStateOf(TrackPrivacy.PUBLIC)
    var downloadable by mutableStateOf(false)
    var offlineListening by mutableStateOf(true)
    var feedable by mutableStateOf(false)
    var embeddable by mutableStateOf(true)
    var apiStreamable by mutableStateOf(true)
    var commentable by mutableStateOf(true)
    var revealComments by mutableStateOf(true)
    var revealStats by mutableStateOf(true)
    var caption by mutableStateOf("")
    var labelName by mutableStateOf("")
    var releaseDate by mutableStateOf("")
    var license by mutableStateOf(TrackLicense.ALL_RIGHTS_RESERVED)
    var isrc by mutableStateOf("")
    var iswc by mutableStateOf("")
    var publisher by mutableStateOf("")
    var composer by mutableStateOf("")
    var purchaseTitle by mutableStateOf("")
    var purchaseUrl by mutableStateOf("")
    var explicitContent by mutableStateOf(false)
    var containsMusic by mutableStateOf(true)
    var albumTitle by mutableStateOf("")
    var releaseTitle by mutableStateOf("")
    var upcOrEan by mutableStateOf("")
    var pLine by mutableStateOf("")
    var cLine by mutableStateOf("")
    var selectedCategoryTab by mutableIntStateOf(0)
    var showAdvancedFields by mutableStateOf(false)
    var isSchedulingEnabled by mutableStateOf(false)
    var scheduledEpochMs by mutableStateOf<Long?>(null)
    var scheduledTimezone by mutableStateOf(java.util.TimeZone.getDefault().id)
    var artworkBitmap by mutableStateOf<Bitmap?>(null)
    var artworkUri by mutableStateOf<Uri?>(null)
    var tempArtworkBitmap by mutableStateOf<Bitmap?>(null)

    // Storefront (Affichez vos produits) & Commerce Option (Lien d'achat vs Vitrine)
    var selectedCommerceOption by mutableStateOf(CommerceOption.BUY_LINK)
    var hasStorefront by mutableStateOf(false)
    var storefrontType by mutableStateOf(BuyModuleType.DIGITAL)
    var storefrontTitle by mutableStateOf("")
    var storefrontPrice by mutableStateOf("")
    var storefrontLink by mutableStateOf("")
    var storefrontLinkTitle by mutableStateOf("")
    var storefrontDescription by mutableStateOf("")
    var storefrontImageUri by mutableStateOf<Uri?>(null)
    var storefrontBitmap by mutableStateOf<Bitmap?>(null)
    var storefrontImageUrl by mutableStateOf<String?>(null)
    var isSavingStorefront by mutableStateOf(false)

    // Geo Blocking (Restrictions géographiques)
    var geoBlockingMode by mutableStateOf(GeoBlockingMode.EVERYWHERE)
    var geoBlockingRegions by mutableStateOf("")

    fun toggleCountryCode(code: String) {
        val upper = code.trim().uppercase()
        if (upper.isBlank()) return
        val currentList = geoBlockingRegions
            .split(",")
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
            .toMutableList()

        if (currentList.contains(upper)) {
            currentList.remove(upper)
        } else {
            currentList.add(upper)
        }
        geoBlockingRegions = currentList.joinToString(", ")
    }
    var isDeletingStorefront by mutableStateOf(false)
    var storefrontErrorMessage by mutableStateOf<String?>(null)

    // Snippet preview (Avancé tab)
    var snippetStartSeconds by mutableStateOf(0)
    var snippetEndSeconds by mutableStateOf(20)
    var trackDurationSeconds by mutableStateOf(0)
    var isSnippetCustomized by mutableStateOf(false)
    var waveformUrl by mutableStateOf<String?>(null)
    var isPlayingSnippet by mutableStateOf(false)
    var editingTrackModel by mutableStateOf<com.alananasss.kittytune.domain.Track?>(null)

    var selectedFileUri by mutableStateOf<Uri?>(null)
    var selectedFileName by mutableStateOf("")
    var selectedFileSizeBytes by mutableStateOf(0L)
    var uploadFileProgress by mutableStateOf(0f)

    val isTitleValid: Boolean get() = title.isNotBlank()
    val isArtistValid: Boolean get() = artist.isNotBlank()
    val isPermalinkValid: Boolean
        get() = permalink.isBlank() || (permalink.matches(Regex("^[a-zA-Z0-9-_]+$")) && permalink.any { it.isLetter() })
    val isPurchaseUrlValid: Boolean get() = purchaseUrl.isBlank() || isValidUrl(purchaseUrl)
    val isStorefrontLinkValid: Boolean get() = !hasStorefront || storefrontLink.isBlank() || isValidUrl(storefrontLink)

    val canUpload: Boolean get() = isTitleValid && isArtistValid && isPermalinkValid && isPurchaseUrlValid && selectedFileUri != null
    val canSubmit: Boolean
        get() = if (isEditMode) {
            isTitleValid && isArtistValid && isPermalinkValid && isPurchaseUrlValid && isStorefrontLinkValid && !isSavingEdit && !isDeletingTrack
        } else {
            canUpload
        }

    private var uploadJob: Job? = null

    init {
        loadUserProfile()
    }

    private fun loadUserProfile() {
        if (!isLoggedIn) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val api = RetrofitClient.create(getApplication())
                val me = api.getMe()
                if (artist.isBlank()) {
                    artist = me.username ?: ""
                }
                userPermalink = me.permalink ?: (if (me.id != 0L) "user-${me.id}" else "")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onTitleChanged(newTitle: String) {
        title = newTitle
        if (!isPermalinkManuallyEdited) {
            permalink = generateSlug(newTitle)
        }
    }

    fun onPermalinkChanged(newPermalink: String) {
        permalink = newPermalink.lowercase().replace("\n", "").replace(" ", "-")
        isPermalinkManuallyEdited = true
    }

    private fun generateSlug(input: String): String {
        return input.lowercase()
            .replace(Regex("[^a-z0-9-_]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
    }

    fun onFileSelected(uri: Uri, context: Context) {
        selectedFileUri = uri
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use { c ->
            val nameIdx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            val sizeIdx = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (c.moveToFirst()) {
                selectedFileName = c.getString(nameIdx) ?: "audio_file"
                selectedFileSizeBytes = if (sizeIdx >= 0) c.getLong(sizeIdx) else 0L
            }
        }
        if (title.isBlank()) {
            val baseName = selectedFileName
                .substringBeforeLast(".")
                .replace("_", " ")
                .replace("-", " ")
                .trim()
            onTitleChanged(baseName)
        }
        _uploadState.value = UploadState.FileSelected(selectedFileName, selectedFileSizeBytes)
    }

    fun addTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isNotBlank() && !tags.contains(trimmed) && tags.size < 10) {
            tags = tags + trimmed
        }
        tagInput = ""
    }

    fun removeTag(tag: String) {
        tags = tags - tag
    }

    fun toggleScheduling(enabled: Boolean) {
        isSchedulingEnabled = enabled
        if (enabled) {
            privacy = TrackPrivacy.PRIVATE
            if (scheduledEpochMs == null) {
                val calendar = java.util.Calendar.getInstance().apply {
                    add(java.util.Calendar.DAY_OF_MONTH, 1)
                }
                scheduledEpochMs = calendar.timeInMillis
            }
        }
    }

    fun updateScheduledDate(dateMillis: Long) {
        val calendar = java.util.Calendar.getInstance()
        val currentScheduled = scheduledEpochMs ?: System.currentTimeMillis()
        val currentCal = java.util.Calendar.getInstance().apply { timeInMillis = currentScheduled }
        calendar.timeInMillis = dateMillis
        calendar.set(java.util.Calendar.HOUR_OF_DAY, currentCal.get(java.util.Calendar.HOUR_OF_DAY))
        calendar.set(java.util.Calendar.MINUTE, currentCal.get(java.util.Calendar.MINUTE))
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        scheduledEpochMs = calendar.timeInMillis
    }

    fun updateScheduledTime(hour: Int, minute: Int) {
        val currentScheduled = scheduledEpochMs ?: (System.currentTimeMillis() + 86400000L)
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = currentScheduled
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        scheduledEpochMs = calendar.timeInMillis
    }

    fun onArtworkSelected(uri: Uri, context: Context) {
        artworkUri = uri
        tempArtworkBitmap = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun setCroppedArtwork(bitmap: Bitmap) {
        artworkBitmap = bitmap
        tempArtworkBitmap = null
    }

    fun cancelArtworkCrop() {
        tempArtworkBitmap = null
        if (artworkBitmap == null) {
            artworkUri = null
        }
    }

    fun startUpload(context: Context) {
        if (!canUpload || !isLoggedIn) return

        uploadJob = viewModelScope.launch {
            try {
                val fileUri = selectedFileUri ?: return@launch
                val fileName = selectedFileName
                val fileSize = selectedFileSizeBytes

                val eligibility = repo.checkEligibility().getOrNull()
                if (eligibility != null && !eligibility.canUpload) {
                    val reasons = eligibility.reasons.orEmpty()
                    when {
                        reasons.any { it.contains("email", ignoreCase = true) } -> {
                            _uploadState.value = UploadState.Error(R.string.upload_eligibility_not_confirmed)
                            return@launch
                        }

                        reasons.any {
                            it.contains("limit", ignoreCase = true) || it.contains(
                                "quota",
                                ignoreCase = true
                            )
                        } -> {
                            _uploadState.value = UploadState.Error(R.string.upload_eligibility_limit_reached)
                            return@launch
                        }
                    }
                }

                val tempFile = uriToTempFile(context, fileUri, fileName)
                    ?: run {
                        _uploadState.value = UploadState.Error(R.string.upload_error_file_access)
                        return@launch
                    }

                _uploadState.value = UploadState.Uploading(UploadStep.FETCHING_POLICY, 0f)
                uploadFileProgress = 0f

                val policy = repo.fetchUploadPolicy(fileName, fileSize).getOrElse { e ->
                    _uploadState.value = UploadState.Error(R.string.upload_error_policy, e.message)
                    tempFile.delete()
                    return@launch
                }

                _uploadState.value = UploadState.Uploading(UploadStep.UPLOADING_FILE, 0f)

                val uid = repo.uploadFileToS3(tempFile, policy) { progress ->
                    uploadFileProgress = progress
                    _uploadState.value = UploadState.Uploading(UploadStep.UPLOADING_FILE, progress)
                }.getOrElse { e ->
                    _uploadState.value = UploadState.Error(R.string.upload_error_s3, e.message)
                    tempFile.delete()
                    return@launch
                }

                tempFile.delete()

                _uploadState.value = UploadState.Uploading(UploadStep.CREATING_TRACK, 0f)

                val metadata = buildMetadata()
                val createdTrack = repo.createTrack(uid, metadata, fileName).getOrElse { e ->
                    _uploadState.value = UploadState.Error(R.string.upload_error_creation, e.message)
                    return@launch
                }

                val trackUrn = createdTrack.urn
                val trackTitle = metadata.title

                _uploadState.value = UploadState.Uploading(UploadStep.TRANSCODING, 0f)

                repo.createTranscoding(uid).getOrElse { e ->
                    _uploadState.value = UploadState.Error(R.string.upload_error_transcoding, e.message)
                    return@launch
                }

                var transcodingSuccess = false
                repo.pollTranscodingStatus(uid).collect { status ->
                    when (status) {
                        TranscodingStatus.FINISHED -> {
                            transcodingSuccess = true
                        }

                        TranscodingStatus.FAILURE -> {
                            _uploadState.value = UploadState.Error(R.string.upload_error_transcoding_server)
                        }

                        else -> {
                        }
                    }
                }

                if (!transcodingSuccess) {
                    if (_uploadState.value !is UploadState.Error) {
                        _uploadState.value = UploadState.Error(R.string.upload_error_transcoding_server)
                    }
                    return@launch
                }

                val artwork = artworkBitmap
                if (artwork != null) {
                    _uploadState.value = UploadState.Uploading(UploadStep.UPLOADING_ARTWORK, 0f)
                    repo.uploadArtwork(trackUrn, artwork)
                        .onFailure {}
                }
                ProfileViewModel.triggerRefresh()
                _uploadState.value = UploadState.Success(
                    trackUrn = trackUrn,
                    trackTitle = trackTitle
                )

            } catch (e: Exception) {
                _uploadState.value = UploadState.Error(R.string.upload_error_unknown, e.message)
            }
        }
    }

    fun loadTrackForEditing(track: Track) {
        editingTrack = track
        editingTrackId = track.id
        editingTrackUrn = "soundcloud:tracks:${track.id}"
        existingArtworkUrl = track.fullResArtwork
        title = track.title.orEmpty()
        val rawSlug = track.permalink?.takeIf { it.isNotBlank() }
            ?: run {
                val url = track.permalinkUrl.orEmpty().trimEnd('/')
                if (url.contains("/s-")) {
                    url.substringBeforeLast("/s-").substringAfterLast("/")
                } else {
                    url.substringAfterLast("/")
                }
            }
        permalink = rawSlug.lowercase()
        isPermalinkManuallyEdited = true
        artist = track.displayArtist.ifBlank { track.user?.username.orEmpty() }
        if (userPermalink.isBlank() && !track.user?.permalink.isNullOrBlank()) {
            userPermalink = track.user?.permalink!!
        }
        genre = track.genre.orEmpty()
        description = track.description.orEmpty()
        tags = if (!track.tagList.isNullOrBlank()) {
            parseTagList(track.tagList)
        } else emptyList()
        privacy = if (track.sharing?.equals("private", ignoreCase = true) == true) {
            TrackPrivacy.PRIVATE
        } else {
            TrackPrivacy.PUBLIC
        }
        isSchedulingEnabled = false
        scheduledEpochMs = null
        scheduledTimezone = java.util.TimeZone.getDefault().id
        if (!track.releaseDate.isNullOrBlank()) {
            try {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                val date = sdf.parse(track.releaseDate)
                if (date != null && date.time > System.currentTimeMillis()) {
                    isSchedulingEnabled = true
                    scheduledEpochMs = date.time
                    privacy = TrackPrivacy.PRIVATE
                }
            } catch (_: Exception) {
            }
        }
        containsMusic = track.publisherMetadata?.containsMusic ?: true
        albumTitle = track.publisherMetadata?.albumTitle.orEmpty()
        releaseTitle = track.publisherMetadata?.releaseTitle.orEmpty()
        upcOrEan = track.publisherMetadata?.upcOrEan.orEmpty()
        pLine = track.publisherMetadata?.pLine.orEmpty()
        cLine = track.publisherMetadata?.cLine.orEmpty()
        isrc = track.publisherMetadata?.isrc.orEmpty()
        iswc = track.publisherMetadata?.iswc.orEmpty()
        publisher = track.publisherMetadata?.publisher.orEmpty()
        composer = track.publisherMetadata?.composer.orEmpty()
        explicitContent = track.publisherMetadata?.explicit ?: false
        labelName = track.labelName.orEmpty()
        releaseDate = track.releaseDate.orEmpty()
        purchaseTitle = track.purchaseTitle.orEmpty()
        purchaseUrl = track.purchaseUrl.orEmpty()
        track.license?.let { licStr ->
            TrackLicense.entries.firstOrNull { it.apiValue.equals(licStr, ignoreCase = true) }?.let { license = it }
        }
        track.commentable?.let { commentable = it }
        track.revealComments?.let { revealComments = it }
        track.displayStats?.let { revealStats = it }
        track.downloadable?.let { downloadable = it }
        track.feedable?.let { feedable = it }
        track.embeddable?.let { embeddable = it }
        track.streamable?.let { apiStreamable = it }
        hasStorefront = false
        storefrontTitle = ""
        storefrontPrice = ""
        storefrontLink = ""
        storefrontLinkTitle = ""
        storefrontDescription = ""
        storefrontBitmap = null
        storefrontImageUrl = null
        storefrontType = BuyModuleType.DIGITAL
        selectedFileName = track.title.orEmpty()
        selectedFileSizeBytes = 0L
        artworkBitmap = null
        artworkUri = null
        _uploadState.value = UploadState.FileSelected(selectedFileName, 0L)

        val trackUrn = editingTrackUrn
        if (trackUrn != null) {
            viewModelScope.launch {
                val cleanId = trackUrn.substringAfterLast(":")
                repo.getTrackById(cleanId).onSuccess { track ->
                    if (track != null && editingTrackUrn == trackUrn) {
                        editingTrackModel = track
                        track.title?.let { if (it.isNotBlank()) title = it }
                        track.permalink?.let { if (it.isNotBlank()) permalink = it }
                        track.displayArtist.let { if (it.isNotBlank()) artist = it }
                        track.description?.let { description = it }
                        track.caption?.let { caption = it }
                        track.genre?.let { genre = it }
                        track.labelName?.let { labelName = it }
                        track.releaseDate?.let { releaseDate = it }
                        track.license?.let { licStr ->
                            TrackLicense.entries.firstOrNull { it.apiValue.equals(licStr, ignoreCase = true) }
                                ?.let { license = it }
                        }
                        track.purchaseTitle?.let { purchaseTitle = it }
                        track.purchaseUrl?.let { purchaseUrl = it }
                        track.commentable?.let { commentable = it }
                        track.revealComments?.let { revealComments = it }
                        track.displayStats?.let { revealStats = it }
                        track.downloadable?.let { downloadable = it }
                        track.feedable?.let { feedable = it }
                        track.embeddable?.let { embeddable = it }
                        track.streamable?.let { apiStreamable = it }
                        track.publisherMetadata?.let { pub ->
                            pub.artist?.let { if (it.isNotBlank()) artist = it }
                            pub.albumTitle?.let { albumTitle = it }
                            pub.containsMusic?.let { containsMusic = it }
                            pub.publisher?.let { publisher = it }
                            pub.iswc?.let { iswc = it }
                            pub.upcOrEan?.let { upcOrEan = it }
                            pub.explicit?.let { explicitContent = it }
                            pub.cLine?.let { cLine = it }
                            pub.pLine?.let { pLine = it }
                            pub.composer?.let { composer = it }
                            pub.releaseTitle?.let { releaseTitle = it }
                            pub.isrc?.let { isrc = it }
                        }
                        if (!track.tagList.isNullOrBlank()) {
                            tags = parseTagList(track.tagList)
                        }
                        if (track.sharing != null) {
                            privacy = if (track.sharing.equals(
                                    "private",
                                    ignoreCase = true
                                )
                            ) TrackPrivacy.PRIVATE else TrackPrivacy.PUBLIC
                        }
                        track.waveformUrl?.let { waveformUrl = it }
                        val durSec = ((track.durationMs ?: track.fullDuration ?: 0L) / 1000L).toInt()
                        if (durSec > 0) {
                            trackDurationSeconds = durSec
                            snippetEndSeconds = (snippetStartSeconds + 20).coerceAtMost(durSec)
                        }
                    }
                }
                repo.getEditableTrack(trackUrn).onSuccess { editable ->
                    if (editable != null && editingTrackUrn == trackUrn) {
                        editable.title?.let { if (it.isNotBlank()) title = it }
                        editable.permalink?.let { if (it.isNotBlank()) permalink = it }
                        editable.artist?.let { if (it.isNotBlank()) artist = it }
                        editable.description?.let { description = it }
                        editable.genre?.let { genre = it }
                        if (!editable.userTags.isNullOrEmpty()) {
                            tags = editable.userTags
                        }
                        if (editable.isPublic != null) {
                            privacy = if (editable.isPublic) TrackPrivacy.PUBLIC else TrackPrivacy.PRIVATE
                        } else if (editable.share?.access != null) {
                            privacy = if (editable.share.access.equals(
                                    "private",
                                    ignoreCase = true
                                )
                            ) TrackPrivacy.PRIVATE else TrackPrivacy.PUBLIC
                        }
                        val makePublicAt = editable.schedule?.makePublicAt
                        if (!makePublicAt.isNullOrBlank()) {
                            try {
                                val sdf =
                                    java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                                        .apply {
                                            timeZone = java.util.TimeZone.getTimeZone("UTC")
                                        }
                                val date = sdf.parse(makePublicAt)
                                if (date != null) {
                                    isSchedulingEnabled = true
                                    scheduledEpochMs = date.time
                                    privacy = TrackPrivacy.PRIVATE
                                    editable.schedule.timezone?.takeIf { it.isNotBlank() }?.let { tz ->
                                        scheduledTimezone = tz
                                    }
                                }
                            } catch (_: Exception) {
                            }
                        }
                        editable.geoBlocking?.let { gb ->
                            if (!gb.exclusiveRegions.isNullOrEmpty()) {
                                geoBlockingMode = GeoBlockingMode.EXCLUSIVE
                                geoBlockingRegions = gb.exclusiveRegions.joinToString(", ")
                            } else if (!gb.blockedRegions.isNullOrEmpty()) {
                                geoBlockingMode = GeoBlockingMode.BLOCKED
                                geoBlockingRegions = gb.blockedRegions.joinToString(", ")
                            } else {
                                geoBlockingMode = GeoBlockingMode.EVERYWHERE
                                geoBlockingRegions = ""
                            }
                        }
                    }
                }
                repo.getBuyModule(trackUrn).onSuccess { module ->
                    if (module != null && !module.title.isNullOrBlank() && editingTrackUrn == trackUrn) {
                        hasStorefront = true
                        selectedCommerceOption = CommerceOption.STOREFRONT
                        storefrontType = BuyModuleType.fromValue(module.type)
                        storefrontTitle = module.title.orEmpty()
                        storefrontPrice = module.price.orEmpty()
                        storefrontLink = module.link.orEmpty()
                        storefrontLinkTitle = module.linkTitle.orEmpty()
                        storefrontDescription = module.description.orEmpty()
                        storefrontImageUrl = module.imageUrl?.replace("{size}", "t500x500")
                            ?.replace("{format}", "t500x500")
                    }
                }
            }
        }
    }

    private fun parseTagList(raw: String): List<String> {
        if (raw.contains(",")) {
            return raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        }
        val regex = Regex("\"([^\"]*)\"|(\\S+)")
        return regex.findAll(raw).map { match ->
            match.groupValues[1].takeIf { it.isNotEmpty() } ?: match.groupValues[2]
        }.filter { it.isNotBlank() }.toList()
    }

    fun saveTrackEdits(context: Context, onSuccess: (() -> Unit)? = null) {
        val trackUrn = editingTrackUrn ?: return
        if (isSavingEdit || isDeletingTrack) return
        isSavingEdit = true

        uploadJob = viewModelScope.launch {
            try {
                val newFileUri = selectedFileUri
                val replacingUid = if (newFileUri != null) {
                    val fileName = selectedFileName
                    val fileSize = selectedFileSizeBytes

                    val tempFile = uriToTempFile(context, newFileUri, fileName)
                        ?: run {
                            isSavingEdit = false
                            _uploadState.value = UploadState.Error(R.string.upload_error_file_access)
                            return@launch
                        }

                    _uploadState.value = UploadState.Uploading(UploadStep.FETCHING_POLICY, 0f)
                    uploadFileProgress = 0f

                    val policy = repo.fetchUploadPolicy(fileName, fileSize).getOrElse { e ->
                        isSavingEdit = false
                        _uploadState.value = UploadState.Error(R.string.upload_error_policy, e.message)
                        tempFile.delete()
                        return@launch
                    }

                    _uploadState.value = UploadState.Uploading(UploadStep.UPLOADING_FILE, 0f)

                    val uid = repo.uploadFileToS3(tempFile, policy) { progress ->
                        uploadFileProgress = progress
                        _uploadState.value = UploadState.Uploading(UploadStep.UPLOADING_FILE, progress)
                    }.getOrElse { e ->
                        isSavingEdit = false
                        _uploadState.value = UploadState.Error(R.string.upload_error_s3, e.message)
                        tempFile.delete()
                        return@launch
                    }

                    tempFile.delete()
                    uid
                } else null

                val metadata = buildMetadata()
                val result = repo.editTrack(
                    trackUrn = trackUrn,
                    metadata = metadata,
                    replacingUid = replacingUid,
                    replacingFilename = if (replacingUid != null) selectedFileName else null
                )

                if (result.isFailure) {
                    isSavingEdit = false
                    val errorMsg = result.exceptionOrNull()?.message
                    if (isSchedulingEnabled && (errorMsg?.contains("Failed to edit track", ignoreCase = true) == true ||
                                errorMsg?.contains("permission", ignoreCase = true) == true ||
                                errorMsg?.contains("forbidden", ignoreCase = true) == true ||
                                errorMsg?.contains("pro", ignoreCase = true) == true ||
                                errorMsg?.contains("schedule", ignoreCase = true) == true)
                    ) {
                        _uploadState.value = UploadState.Error(R.string.upload_error_schedule_next_pro)
                    } else if (replacingUid != null && (errorMsg?.contains(
                            "Failed to edit track",
                            ignoreCase = true
                        ) == true ||
                                errorMsg?.contains("permission", ignoreCase = true) == true ||
                                errorMsg?.contains("forbidden", ignoreCase = true) == true ||
                                errorMsg?.contains("pro", ignoreCase = true) == true)
                    ) {
                        _uploadState.value = UploadState.Error(R.string.upload_error_replace_file_next_pro)
                    } else {
                        _uploadState.value = UploadState.Error(R.string.upload_error_edit, errorMsg)
                    }
                    return@launch
                }

                if (replacingUid != null) {
                    _uploadState.value = UploadState.Uploading(UploadStep.TRANSCODING, 0f)

                    repo.createTranscoding(replacingUid).getOrElse { e ->
                        isSavingEdit = false
                        _uploadState.value = UploadState.Error(R.string.upload_error_transcoding, e.message)
                        return@launch
                    }

                    var transcodingSuccess = false
                    repo.pollTranscodingStatus(replacingUid).collect { status ->
                        when (status) {
                            TranscodingStatus.FINISHED -> transcodingSuccess = true
                            TranscodingStatus.FAILURE -> {
                                _uploadState.value = UploadState.Error(R.string.upload_error_transcoding_server)
                            }

                            else -> {
                            }
                        }
                    }

                    if (!transcodingSuccess) {
                        isSavingEdit = false
                        if (_uploadState.value !is UploadState.Error) {
                            _uploadState.value = UploadState.Error(R.string.upload_error_transcoding_server)
                        }
                        return@launch
                    }
                }

                val artwork = artworkBitmap
                if (artwork != null) {
                    if (replacingUid != null) {
                        _uploadState.value = UploadState.Uploading(UploadStep.UPLOADING_ARTWORK, 0f)
                    }
                    val artworkResult = repo.uploadArtwork(trackUrn, artwork)
                    if (artworkResult.isFailure) {
                        isSavingEdit = false
                        _uploadState.value = UploadState.Error(
                            R.string.upload_error_edit,
                            artworkResult.exceptionOrNull()?.message
                        )
                        return@launch
                    }
                }

                val cleanId = trackUrn.substringAfterLast(":").toLongOrNull() ?: editingTrackId ?: 0L
                val updatedPublisherMeta = com.alananasss.kittytune.domain.TrackPublisherMetadata(
                    artist = artist.takeIf { it.isNotBlank() }
                )
                val updatedTrack = (editingTrack ?: Track(
                    id = cleanId,
                    title = title,
                    artworkUrl = existingArtworkUrl,
                    durationMs = 0L,
                    user = User(id = 0L, username = artist, avatarUrl = null)
                )).copy(
                    id = cleanId,
                    title = title,
                    publisherMetadata = updatedPublisherMeta,
                    user = (editingTrack?.user ?: User(
                        id = 0L,
                        username = artist,
                        avatarUrl = null
                    )).copy(username = artist),
                    genre = genre,
                    description = description,
                    sharing = if (privacy == TrackPrivacy.PRIVATE) "private" else "public"
                )

                MusicManager.updateTrackMetadata(updatedTrack)
                ProfileViewModel.triggerRefresh()
                isSavingEdit = false
                _uploadState.value = UploadState.Success(
                    trackUrn = trackUrn,
                    trackTitle = title.ifBlank { "Track" }
                )
                onSuccess?.invoke()
            } catch (e: Exception) {
                isSavingEdit = false
                _uploadState.value = UploadState.Error(R.string.upload_error_unknown, e.message)
            }
        }
    }

    fun deleteTrack(onSuccess: () -> Unit) {
        val trackUrn = editingTrackUrn ?: return
        if (isDeletingTrack || isSavingEdit) return
        isDeletingTrack = true

        viewModelScope.launch {
            try {
                val result = repo.deleteTrack(trackUrn)
                if (result.isFailure) {
                    isDeletingTrack = false
                    _uploadState.value =
                        UploadState.Error(R.string.upload_error_unknown, result.exceptionOrNull()?.message)
                    return@launch
                }
                val cleanId = trackUrn.substringAfterLast(":").toLongOrNull() ?: editingTrackId ?: 0L
                DownloadManager.deleteTrack(cleanId)
                MusicManager.notifyTrackDeleted(cleanId)

                ProfileViewModel.triggerRefresh()
                isDeletingTrack = false
                onSuccess()
            } catch (e: Exception) {
                isDeletingTrack = false
                _uploadState.value = UploadState.Error(R.string.upload_error_unknown, e.message)
            }
        }
    }

    fun cancelUpload() {
        uploadJob?.cancel()
        resetToFileSelected()
    }

    fun resetAll() {
        uploadJob?.cancel()
        _uploadState.value = UploadState.Idle
        editingTrack = null
        editingTrackId = null
        editingTrackUrn = null
        existingArtworkUrl = null
        isSavingEdit = false
        isDeletingTrack = false
        showDeleteConfirmationDialog = false
        title = ""
        permalink = ""
        isPermalinkManuallyEdited = false
        description = ""
        genre = ""
        tagInput = ""
        tags = emptyList()
        privacy = TrackPrivacy.PUBLIC
        downloadable = false
        offlineListening = true
        feedable = false
        embeddable = true
        apiStreamable = true
        commentable = true
        revealComments = true
        revealStats = true
        caption = ""
        labelName = ""
        releaseDate = ""
        license = TrackLicense.ALL_RIGHTS_RESERVED
        artworkBitmap = null
        artworkUri = null
        tempArtworkBitmap = null
        selectedFileUri = null
        selectedFileName = ""
        selectedFileSizeBytes = 0L
        isrc = ""
        iswc = ""
        publisher = ""
        composer = ""
        purchaseTitle = ""
        purchaseUrl = ""
        explicitContent = false
        containsMusic = true
        albumTitle = ""
        releaseTitle = ""
        upcOrEan = ""
        pLine = ""
        cLine = ""
        selectedCategoryTab = 0
        showAdvancedFields = false
        isSchedulingEnabled = false
        scheduledEpochMs = null
        scheduledTimezone = java.util.TimeZone.getDefault().id
        uploadFileProgress = 0f
        hasStorefront = false
        storefrontType = BuyModuleType.DIGITAL
        storefrontTitle = ""
        storefrontPrice = ""
        storefrontLink = ""
        storefrontLinkTitle = ""
        storefrontDescription = ""
        storefrontImageUri = null
        storefrontBitmap = null
        storefrontImageUrl = null
        isSavingStorefront = false
        isDeletingStorefront = false
        storefrontErrorMessage = null
        geoBlockingMode = GeoBlockingMode.EVERYWHERE
        geoBlockingRegions = ""
    }

    fun saveStorefront(onSuccess: (() -> Unit)? = null) {
        val trackUrn = editingTrackUrn ?: return
        val rawLink = storefrontLink.trim()
        if (storefrontTitle.isBlank() || storefrontPrice.isBlank() || rawLink.isBlank() || !isValidUrl(rawLink)) {
            return
        }
        if (isSavingStorefront || isDeletingStorefront) return
        isSavingStorefront = true
        storefrontErrorMessage = null

        val normalizedLink = normalizeUrl(rawLink)

        viewModelScope.launch {
            try {
                val base64Image = storefrontBitmap?.let { bmp ->
                    val baos = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                    Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                }

                repo.createOrUpdateBuyModule(
                    trackUrn = trackUrn,
                    type = storefrontType.value,
                    title = storefrontTitle.trim(),
                    price = storefrontPrice.trim(),
                    link = normalizedLink,
                    description = storefrontDescription.trim().takeIf { it.isNotBlank() },
                    linkTitle = storefrontLinkTitle.trim().takeIf { it.isNotBlank() },
                    imageData = base64Image,
                    imageUrl = storefrontImageUrl
                ).onSuccess {
                    hasStorefront = true
                    selectedCommerceOption = CommerceOption.STOREFRONT
                    storefrontLink = normalizedLink
                    purchaseUrl = normalizedLink
                    purchaseTitle =
                        storefrontLinkTitle.trim().ifBlank { storefrontTitle.trim().ifBlank { storefrontType.value } }
                    isSavingStorefront = false
                    onSuccess?.invoke()
                }.onFailure { err ->
                    isSavingStorefront = false
                    val errorMsg = err.message ?: ""
                    if (errorMsg.contains("permission", ignoreCase = true) ||
                        errorMsg.contains("forbidden", ignoreCase = true) ||
                        errorMsg.contains("unauthorized", ignoreCase = true) ||
                        errorMsg.contains("pro", ignoreCase = true) ||
                        errorMsg.contains("UnknownError", ignoreCase = true)
                    ) {
                        storefrontErrorMessage =
                            getApplication<Application>().getString(R.string.upload_error_storefront_next_pro)
                    } else {
                        storefrontErrorMessage = err.message ?: getApplication<Application>().getString(R.string.upload_error_storefront_save)
                    }
                }
            } catch (e: Exception) {
                isSavingStorefront = false
                val errorMsg = e.message ?: ""
                if (errorMsg.contains("permission", ignoreCase = true) ||
                    errorMsg.contains("forbidden", ignoreCase = true) ||
                    errorMsg.contains("unauthorized", ignoreCase = true) ||
                    errorMsg.contains("pro", ignoreCase = true) ||
                    errorMsg.contains("UnknownError", ignoreCase = true)
                ) {
                    storefrontErrorMessage =
                        getApplication<Application>().getString(R.string.upload_error_storefront_next_pro)
                } else {
                    storefrontErrorMessage = e.message ?: getApplication<Application>().getString(R.string.upload_error_storefront_save)
                }
            }
        }
    }

    fun deleteStorefront(onSuccess: (() -> Unit)? = null) {
        val trackUrn = editingTrackUrn ?: return
        if (isSavingStorefront || isDeletingStorefront) return
        isDeletingStorefront = true
        storefrontErrorMessage = null

        viewModelScope.launch {
            try {
                repo.deleteBuyModule(trackUrn).onSuccess {
                    hasStorefront = false
                    selectedCommerceOption = CommerceOption.BUY_LINK
                    storefrontTitle = ""
                    storefrontPrice = ""
                    storefrontLink = ""
                    storefrontLinkTitle = ""
                    storefrontDescription = ""
                    storefrontImageUri = null
                    storefrontBitmap = null
                    storefrontImageUrl = null
                    purchaseUrl = ""
                    purchaseTitle = ""
                    isDeletingStorefront = false
                    onSuccess?.invoke()
                }.onFailure { err ->
                    isDeletingStorefront = false
                    storefrontErrorMessage = err.message ?: getApplication<Application>().getString(R.string.upload_error_storefront_delete)
                }
            } catch (e: Exception) {
                isDeletingStorefront = false
                storefrontErrorMessage = e.message ?: getApplication<Application>().getString(R.string.upload_error_storefront_delete)
            }
        }
    }

    fun resetToFileSelected() {
        if (selectedFileName.isNotBlank()) {
            uploadJob?.cancel()
            _uploadState.value = UploadState.FileSelected(selectedFileName, selectedFileSizeBytes)
        } else {
            resetAll()
        }
    }

    private fun buildMetadata() = UploadMetadata(
        title = title,
        permalink = permalink,
        artist = artist,
        description = description,
        genre = genre,
        tags = tags,
        privacy = if (isSchedulingEnabled) TrackPrivacy.PRIVATE else privacy,
        downloadable = downloadable,
        offlineListening = offlineListening,
        feedable = feedable,
        embeddable = embeddable,
        apiStreamable = apiStreamable,
        commentable = commentable,
        revealComments = revealComments,
        revealStats = revealStats,
        caption = caption,
        labelName = labelName.trim(),
        releaseDate = releaseDate.trim().takeIf { it.isNotBlank() },
        license = license,
        containsMusic = containsMusic,
        albumTitle = albumTitle.trim(),
        isrc = isrc.trim(),
        iswc = iswc.trim(),
        upcOrEan = upcOrEan.trim(),
        publisher = publisher.trim(),
        composer = composer.trim(),
        releaseTitle = releaseTitle.trim(),
        pLine = pLine.trim(),
        cLine = cLine.trim(),
        explicitContent = explicitContent,
        purchaseTitle = if (selectedCommerceOption == CommerceOption.STOREFRONT && hasStorefront && storefrontLink.isNotBlank()) {
            storefrontLinkTitle.trim().ifBlank { storefrontTitle.trim().ifBlank { storefrontType.value } }
        } else if (selectedCommerceOption == CommerceOption.BUY_LINK) {
            purchaseTitle.trim()
        } else {
            ""
        },
        purchaseUrl = if (selectedCommerceOption == CommerceOption.STOREFRONT && hasStorefront && storefrontLink.isNotBlank()) {
            val trimmed = storefrontLink.trim()
            if (isValidUrl(trimmed)) normalizeUrl(trimmed) else ""
        } else if (selectedCommerceOption == CommerceOption.BUY_LINK && purchaseUrl.isNotBlank()) {
            val trimmed = purchaseUrl.trim()
            if (isValidUrl(trimmed)) normalizeUrl(trimmed) else ""
        } else {
            ""
        },
        isScheduled = isSchedulingEnabled,
        scheduledDateEpochMs = if (isSchedulingEnabled) scheduledEpochMs else null,
        scheduledTimezone = if (isSchedulingEnabled) scheduledTimezone else null,
        snippetStartSeconds = if (isSnippetCustomized) snippetStartSeconds else null,
        snippetEndSeconds = if (isSnippetCustomized) snippetEndSeconds else null,
        geoBlockingMode = geoBlockingMode,
        geoBlockingRegions = geoBlockingRegions.trim()
    )

    private fun uriToTempFile(context: Context, uri: Uri, fileName: String): File? {
        return try {
            val ext = fileName.substringAfterLast(".", "mp3")
            val temp = File.createTempFile("kittytune_upload_", ".$ext", context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { output ->
                    input.copyTo(output)
                }
            }
            temp
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private val URL_REGEX = Regex(
            "^(https?://)?((([a-z\\d]([a-z\\d-]*[a-z\\d])*)\\.)+[a-z]{2,}|((\\d{1,3}\\.){3}\\d{1,3}))(:\\d+)?(/[-a-z\\d%_.~+]*)*(\\?[;&a-z\\d%_.~+=-]*)?(#[-a-z\\d_]*)?$",
            RegexOption.IGNORE_CASE
        )

        fun isValidUrl(url: String): Boolean {
            val trimmed = url.trim()
            if (trimmed.isBlank()) return false
            return URL_REGEX.matches(trimmed) || android.util.Patterns.WEB_URL.matcher(trimmed).matches()
        }

        fun normalizeUrl(url: String): String {
            val trimmed = url.trim()
            if (trimmed.isBlank()) return ""
            return if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith(
                    "https://",
                    ignoreCase = true
                )
            ) {
                trimmed
            } else {
                "https://$trimmed"
            }
        }
    }
}
