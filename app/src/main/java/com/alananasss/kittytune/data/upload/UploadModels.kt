package com.alananasss.kittytune.data.upload

import androidx.annotation.StringRes
import com.alananasss.kittytune.R
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

data class UploadEligibilityResponse(
    @SerializedName("can_upload") val canUpload: Boolean,
    @SerializedName("reasons") val reasons: List<String>? = null
)

data class UploadPolicyRequest(
    @SerializedName("filename") val filename: String,
    @SerializedName("filesize") val filesize: Long
)

data class UploadPolicyResponse(
    @SerializedName("url") val url: String,
    @SerializedName("method") val method: String,
    @SerializedName("uid") val uid: String,
    @SerializedName("headers") val headers: Map<String, String>
)

enum class TrackPrivacy { PUBLIC, PRIVATE }

enum class CommerceOption { BUY_LINK, STOREFRONT }

enum class TrackLicense(val apiValue: String, val displayName: String) {
    ALL_RIGHTS_RESERVED("all-rights-reserved", "All rights reserved"),
    CC_BY("cc-by", "CC BY"),
    CC_BY_NC("cc-by-nc", "CC BY-NC"),
    CC_BY_ND("cc-by-nd", "CC BY-ND"),
    CC_BY_SA("cc-by-sa", "CC BY-SA"),
    CC_BY_NC_ND("cc-by-nc-nd", "CC BY-NC-ND"),
    CC_BY_NC_SA("cc-by-nc-sa", "CC BY-NC-SA"),
    NO_RIGHTS_RESERVED("no-rights-reserved", "CC0");

    val isCreativeCommons: Boolean
        get() = this != ALL_RIGHTS_RESERVED

    val isBy: Boolean
        get() = this in listOf(CC_BY, CC_BY_NC, CC_BY_ND, CC_BY_SA, CC_BY_NC_ND, CC_BY_NC_SA)

    val isNc: Boolean
        get() = this in listOf(CC_BY_NC, CC_BY_NC_ND, CC_BY_NC_SA)

    val isNd: Boolean
        get() = this in listOf(CC_BY_ND, CC_BY_NC_ND)

    val isSa: Boolean
        get() = this in listOf(CC_BY_SA, CC_BY_NC_SA)

    companion object {
        fun fromCreativeCommons(
            by: Boolean,
            nc: Boolean,
            nd: Boolean,
            sa: Boolean
        ): TrackLicense {
            return when {
                !by && !nc && !nd && !sa -> NO_RIGHTS_RESERVED
                by && !nc && !nd && !sa -> CC_BY
                by && nc && !nd && !sa -> CC_BY_NC
                by && !nc && nd && !sa -> CC_BY_ND
                by && !nc && !nd && sa -> CC_BY_SA
                by && nc && nd && !sa -> CC_BY_NC_ND
                by && nc && !nd && sa -> CC_BY_NC_SA
                nc && nd -> CC_BY_NC_ND
                nc && sa -> CC_BY_NC_SA
                nc -> CC_BY_NC
                nd -> CC_BY_ND
                sa -> CC_BY_SA
                else -> CC_BY
            }
        }
    }
}

data class UploadMetadata(
    val title: String = "",
    val permalink: String = "",
    val artist: String = "",
    val description: String = "",
    val genre: String = "",
    val tags: List<String> = emptyList(),
    val privacy: TrackPrivacy = TrackPrivacy.PUBLIC,
    val caption: String = "",
    val labelName: String = "",
    val releaseDate: String? = null,
    val license: TrackLicense = TrackLicense.ALL_RIGHTS_RESERVED,
    val downloadable: Boolean = false,
    val offlineListening: Boolean = true,
    val feedable: Boolean = false,
    val embeddable: Boolean = true,
    val apiStreamable: Boolean = true,
    val commentable: Boolean = true,
    val revealComments: Boolean = true,
    val revealStats: Boolean = true,
    val containsMusic: Boolean = true,
    val albumTitle: String = "",
    val isrc: String = "",
    val iswc: String = "",
    val upcOrEan: String = "",
    val publisher: String = "",
    val composer: String = "",
    val releaseTitle: String = "",
    val pLine: String = "",
    val cLine: String = "",
    val explicitContent: Boolean = false,
    val purchaseTitle: String = "",
    val purchaseUrl: String = "",
    val isScheduled: Boolean = false,
    val scheduledDateEpochMs: Long? = null,
    val scheduledTimezone: String? = null,
    val snippetStartSeconds: Int? = null,
    val snippetEndSeconds: Int? = null,
    val geoBlockingMode: GeoBlockingMode = GeoBlockingMode.EVERYWHERE,
    val geoBlockingRegions: String = "",
)

enum class GeoBlockingMode {
    EVERYWHERE,
    EXCLUSIVE,
    BLOCKED
}

data class GeoBlockingInput(
    @SerializedName("exclusiveRegions") val exclusiveRegions: List<String>? = null,
    @SerializedName("blockedRegions") val blockedRegions: List<String>? = null
)

data class TrackScheduleInput(
    @SerializedName("scheduledPublicDate") val scheduledPublicDate: String? = null,
    @SerializedName("scheduledTimezone") val scheduledTimezone: String? = null,
)

data class TrackSnippetPresetsInput(
    @SerializedName("startSeconds") val startSeconds: Int?,
    @SerializedName("endSeconds") val endSeconds: Int?
)

data class CreateTrackGraphQlRequest(
    @SerializedName("query") val query: String = CREATE_TRACK_MUTATION,
    @SerializedName("variables") val variables: CreateTrackVariables
)

data class CreateTrackVariables(
    @SerializedName("createTrackInput") val createTrackInput: CreateTrackInputPayload
)

data class CreateTrackInputPayload(
    @SerializedName("uid") val uid: String,
    @SerializedName("trackInput") val trackInput: TrackInputData
)

data class TrackInputData(
    @SerializedName("title") val title: String,
    @SerializedName("originalFilename") val originalFilename: String? = null,
    @SerializedName("permalink") val permalink: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("genre") val genre: String? = null,
    @SerializedName("tagList") val tagList: String? = null,
    @SerializedName("shareAccess") val shareAccess: String? = null, // "PUBLIC" | "PRIVATE"
    @SerializedName("commentable") val commentable: Boolean? = null,
    @SerializedName("revealComments") val revealComments: Boolean? = null,
    @SerializedName("revealStats") val revealStats: Boolean? = null,
    @SerializedName("downloadable") val downloadable: Boolean? = null,
    @SerializedName("feedable") val feedable: Boolean? = null,
    @SerializedName("embeddable") val embeddable: Boolean? = null,
    @SerializedName("apiStreamable") val apiStreamable: Boolean? = null,
    @SerializedName("caption") val caption: String? = null,
    @SerializedName("labelName") val labelName: String? = null,
    @SerializedName("license") val license: String? = null,
    @SerializedName("releaseDate") val releaseDate: String? = null,
    @SerializedName("purchaseTitle") val purchaseTitle: String? = null,
    @SerializedName("purchaseUrl") val purchaseUrl: String? = null,
    @SerializedName("publisherMetadata") val publisherMetadata: PublisherMetadataInput? = null,
    @SerializedName("schedule") val schedule: TrackScheduleInput? = null,
    @SerializedName("snippetPresets") val snippetPresets: TrackSnippetPresetsInput? = null,
    @SerializedName("geoBlockings") val geoBlocking: GeoBlockingInput? = null,
)

data class PublisherMetadataInput(
    @SerializedName("artist") val artist: String? = null,
    @SerializedName("albumTitle") val albumTitle: String? = null,
    @SerializedName("containsMusic") val containsMusic: Boolean? = true,
    @SerializedName("publisher") val publisher: String? = null,
    @SerializedName("iswc") val iswc: String? = null,
    @SerializedName("upcOrEan") val upcOrEan: String? = null,
    @SerializedName("explicit") val explicit: Boolean? = false,
    @SerializedName("cLine") val cLine: String? = null,
    @SerializedName("pLine") val pLine: String? = null,
    @SerializedName("writerComposer") val composer: String? = null,
    @SerializedName("releaseTitle") val releaseTitle: String? = null,
    @SerializedName("isrc") val isrc: String? = null,
)

const val CREATE_TRACK_MUTATION = """
mutation CreateTrack(${'$'}createTrackInput: TrackCreateInput!) {
    createTrack(trackCreate: ${'$'}createTrackInput){
        urn
    }
}
"""

data class CreateTrackGraphQlResponse(
    @SerializedName("data") val data: CreateTrackResponseData?,
    @SerializedName("errors") val errors: List<GraphQlError>?
)

data class CreateTrackResponseData(
    @SerializedName("createTrack") val createTrack: CreatedTrackResult?
)

data class CreatedTrackResult(
    @SerializedName("urn") val urn: String
)

data class EditTrackGraphQlRequest(
    @SerializedName("query") val query: String = EDIT_TRACK_MUTATION,
    @SerializedName("variables") val variables: EditTrackVariables
)

data class EditTrackVariables(
    @SerializedName("trackEditInput") val trackEditInput: EditTrackInputPayload
)

data class EditTrackInputPayload(
    @SerializedName("urn") val urn: String,
    @SerializedName("trackInput") val trackInput: TrackInputData,
    @SerializedName("replacingUid") val replacingUid: String? = null,
    @SerializedName("replacingOriginalFilename") val replacingOriginalFilename: String? = null
)

const val EDIT_TRACK_MUTATION = """
mutation EditTrack(${'$'}trackEditInput: TrackEditInput!){
  editTrack(trackEdit: ${'$'}trackEditInput){
    urn
  }
}
"""

data class EditTrackGraphQlResponse(
    @SerializedName("data") val data: EditTrackResponseData?,
    @SerializedName("errors") val errors: List<GraphQlError>?
)

data class EditTrackResponseData(
    @SerializedName("editTrack") val editTrack: CreatedTrackResult?
)

data class FetchEditableTrackGraphQlRequest(
    @SerializedName("query") val query: String = FETCH_EDITABLE_TRACK_QUERY,
    @SerializedName("variables") val variables: FetchEditableTrackVariables
)

data class FetchEditableTrackVariables(
    @SerializedName("allTracksInput") val allTracksInput: FetchEditableTrackInputWrapper
)

data class FetchEditableTrackInputWrapper(
    @SerializedName("trackKeys") val trackKeys: FetchEditableTrackKeys
)

data class FetchEditableTrackKeys(
    @SerializedName("urn") val urn: String
)

const val FETCH_EDITABLE_TRACK_QUERY = """
query AllTracks(${'$'}allTracksInput: AllTracksInput!) {
  allTracks(allTracksInput: ${'$'}allTracksInput) {
    urn
    title
    public
    share {
      access
    }
    artworkUrlTemplate
    permalink
    description
    genre
    userTags
    artist
    originalFilename
    schedule {
      makePublicAt
      timezone
    }
    geoBlockings {
      exclusiveRegions
      blockedRegions
    }
  }
}
"""

data class FetchEditableTrackGraphQlResponse(
    @SerializedName("data") val data: FetchEditableTrackResponseData?,
    @SerializedName("errors") val errors: List<GraphQlError>?
)

data class FetchEditableTrackResponseData(
    @SerializedName("allTracks") val allTracks: List<EditableTrackItemData>?
)

data class EditableTrackItemData(
    @SerializedName("urn") val urn: String,
    @SerializedName("title") val title: String? = null,
    @SerializedName("public") val isPublic: Boolean? = null,
    @SerializedName("share") val share: EditableTrackShare? = null,
    @SerializedName("artworkUrlTemplate") val artworkUrlTemplate: String? = null,
    @SerializedName("permalink") val permalink: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("caption") val caption: String? = null,
    @SerializedName("genre") val genre: String? = null,
    @SerializedName("userTags") val userTags: List<String>? = null,
    @SerializedName("artist") val artist: String? = null,
    @SerializedName("originalFilename") val originalFilename: String? = null,
    @SerializedName("labelName") val labelName: String? = null,
    @SerializedName("releaseDate") val releaseDate: String? = null,
    @SerializedName("license") val license: String? = null,
    @SerializedName("purchaseTitle") val purchaseTitle: String? = null,
    @SerializedName("purchaseUrl") val purchaseUrl: String? = null,
    @SerializedName("commentable") val commentable: Boolean? = null,
    @SerializedName("revealComments") val revealComments: Boolean? = null,
    @SerializedName("displayStats") val displayStats: Boolean? = null,
    @SerializedName("revealStats") val revealStats: Boolean? = null,
    @SerializedName("downloadable") val downloadable: Boolean? = null,
    @SerializedName("feedable") val feedable: Boolean? = null,
    @SerializedName("embeddable") val embeddable: Boolean? = null,
    @SerializedName("apiStreamable") val apiStreamable: Boolean? = null,
    @SerializedName("publisherMetadata") val publisherMetadata: PublisherMetadataInput? = null,
    @SerializedName("schedule") val schedule: EditableTrackScheduleResponse? = null,
    @SerializedName("geoBlockings") val geoBlocking: GeoBlockingInput? = null,
)

data class EditableTrackShare(
    @SerializedName("access") val access: String? = null
)

data class EditableTrackScheduleResponse(
    @SerializedName("makePublicAt") val makePublicAt: String? = null,
    @SerializedName("timezone") val timezone: String? = null
)

data class DeleteTrackGraphQlRequest(
    @SerializedName("query") val query: String = DELETE_TRACK_MUTATION,
    @SerializedName("variables") val variables: DeleteTrackVariables
)

data class DeleteTrackVariables(
    @SerializedName("urn") val urn: String
)

const val DELETE_TRACK_MUTATION = """
mutation deleteTrack(${'$'}urn: ID!) {
  deleteTrack(urn: ${'$'}urn)
}
"""

data class DeleteTrackGraphQlResponse(
    @SerializedName("data") val data: DeleteTrackResponseData?,
    @SerializedName("errors") val errors: List<GraphQlError>?
)

data class DeleteTrackResponseData(
    @SerializedName("deleteTrack") val deleteTrack: Boolean?
)

data class CreateTranscodingGraphQlRequest(
    @SerializedName("query") val query: String = CREATE_TRANSCODING_MUTATION,
    @SerializedName("variables") val variables: TranscodingInputVariables
)

data class TranscodingInputVariables(
    @SerializedName("input") val input: TranscodingUidInput
)

data class TranscodingUidInput(
    @SerializedName("uid") val uid: String
)

const val CREATE_TRANSCODING_MUTATION = """
mutation CreateTranscoding(${'$'}input: TrackTranscodingInput!) {
  createTranscoding(trackTranscoding: ${'$'}input) {
    ... on CreateTranscodingSuccess {
        transcodingStatus {
          status
          percentage
        }
    }
  }
}
"""

data class CreateTranscodingGraphQlResponse(
    @SerializedName("data") val data: CreateTranscodingData?,
    @SerializedName("errors") val errors: List<GraphQlError>?
)

data class CreateTranscodingData(
    @SerializedName("createTranscoding") val createTranscoding: CreateTranscodingWrapper?
)

data class CreateTranscodingWrapper(
    @SerializedName("transcodingStatus") val transcodingStatus: TranscodingStatusPayload?
)

data class TranscodingStatusGraphQlRequest(
    @SerializedName("query") val query: String = TRANSCODING_STATUS_QUERY,
    @SerializedName("variables") val variables: TranscodingInputVariables
)

const val TRANSCODING_STATUS_QUERY = """
query TranscodingStatus(${'$'}input: TrackTranscodingInput!) {
  transcodingStatus(input: ${'$'}input){
    status
    percentage
  }
}
"""

data class TranscodingStatusGraphQlResponse(
    @SerializedName("data") val data: TranscodingStatusData?,
    @SerializedName("errors") val errors: List<GraphQlError>?
)

data class TranscodingStatusData(
    @SerializedName("transcodingStatus") val transcodingStatus: TranscodingStatusPayload?
)

data class TranscodingStatusPayload(
    @SerializedName("status") val status: String?,
    @SerializedName("percentage") val percentage: Int?
)

enum class TranscodingStatus {
    QUEUED, PREPARING, TRANSCODING, FINISHED, FAILURE, UNKNOWN;

    companion object {
        fun from(value: String?): TranscodingStatus = when (value?.uppercase()) {
            "QUEUED" -> QUEUED
            "PREPARING" -> PREPARING
            "TRANSCODING" -> TRANSCODING
            "FINISHED" -> FINISHED
            "FAILURE", "ERROR", "NOT_FOUND" -> FAILURE
            else -> UNKNOWN
        }
    }
}

data class GraphQlError(
    @SerializedName("message") val message: String
)

data class ArtworkUploadRequest(
    @SerializedName("image_data") val imageData: String
)

sealed class UploadState {
    data object Idle : UploadState()
    data class FileSelected(val fileName: String, val fileSizeBytes: Long) : UploadState()
    data class Uploading(
        val step: UploadStep,
        val progress: Float,
    ) : UploadState()

    data class Success(val trackUrn: String, val trackTitle: String) : UploadState()
    data class Error(val messageRes: Int, val formatArg: String? = null) : UploadState()
}

enum class UploadStep(@StringRes val labelRes: Int) {
    FETCHING_POLICY(R.string.upload_step_fetching_policy),
    UPLOADING_FILE(R.string.upload_step_uploading_file),
    CREATING_TRACK(R.string.upload_step_creating_track),
    TRANSCODING(R.string.upload_step_transcoding),
    UPLOADING_ARTWORK(R.string.upload_step_uploading_artwork),
    DONE(R.string.upload_step_done)
}

val SOUNDCLOUD_MUSIC_GENRES = listOf(
    "All Music Genres",
    "Alternative Rock",
    "Ambient",
    "Classical",
    "Country",
    "Dance & EDM",
    "Dancehall",
    "Deep House",
    "Disco",
    "Drum & Bass",
    "Dubstep",
    "Electronic",
    "Folk & Singer-Songwriter",
    "Hip-hop & Rap",
    "House",
    "Indie",
    "Jazz & Blues",
    "Latin",
    "Metal",
    "Piano",
    "Pop",
    "R&B & Soul",
    "Reggae",
    "Reggaeton",
    "Rock",
    "Soundtrack",
    "Techno",
    "Trance",
    "Trap",
    "Triphop",
    "World & Global",
    "Other"
)

val SOUNDCLOUD_AUDIO_GENRES = listOf(
    "Audiobooks",
    "Business",
    "Comedy",
    "Entertainment",
    "Learning",
    "News & Politics",
    "Religion & Spirituality",
    "Science",
    "Sports",
    "Storytelling",
    "Technology"
)

val SOUNDCLOUD_QUICK_GENRES = listOf(
    "All Music Genres",
    "Hip-hop & Rap",
    "Electronic",
    "Rock",
    "Pop",
    "R&B & Soul",
    "Dance & EDM",
    "House",
    "Trap",
    "Alternative Rock",
    "Indie",
    "Latin",
    "Jazz & Blues",
    "Ambient",
    "Classical",
    "Reggae",
    "Metal",
    "Country",
    "Other"
)

val SOUNDCLOUD_GENRES = SOUNDCLOUD_MUSIC_GENRES + SOUNDCLOUD_AUDIO_GENRES

fun getGenreStringRes(genre: String): Int? = when (genre.lowercase().trim()) {
    "all music genres" -> R.string.genre_all_music
    "alternative rock" -> R.string.genre_alternative_rock
    "ambient" -> R.string.genre_ambient
    "classical" -> R.string.genre_classical
    "country" -> R.string.genre_country
    "dance & edm" -> R.string.genre_dance_edm
    "dancehall" -> R.string.genre_dancehall
    "deep house" -> R.string.genre_deep_house
    "disco" -> R.string.genre_disco
    "drum & bass" -> R.string.genre_drum_bass
    "dubstep" -> R.string.genre_dubstep
    "electronic" -> R.string.genre_electronic
    "folk & singer-songwriter" -> R.string.genre_folk
    "hip-hop & rap" -> R.string.genre_hip_hop_rap
    "house" -> R.string.genre_house
    "indie" -> R.string.genre_indie
    "jazz & blues" -> R.string.genre_jazz_blues
    "latin" -> R.string.genre_latin
    "metal" -> R.string.genre_metal
    "piano" -> R.string.genre_piano
    "pop" -> R.string.genre_pop
    "r&b & soul" -> R.string.genre_r_b_soul
    "reggae" -> R.string.genre_reggae
    "reggaeton" -> R.string.genre_reggaeton
    "rock" -> R.string.genre_rock
    "soundtrack" -> R.string.genre_soundtrack
    "techno" -> R.string.genre_techno
    "trance" -> R.string.genre_trance
    "trap" -> R.string.genre_trap
    "triphop", "trip hop", "trip-hop" -> R.string.genre_triphop
    "world & global", "world" -> R.string.genre_world
    "other" -> R.string.genre_other

    "audiobooks" -> R.string.genre_audiobooks
    "business" -> R.string.genre_business
    "comedy" -> R.string.genre_comedy
    "entertainment" -> R.string.genre_entertainment
    "learning" -> R.string.genre_learning
    "news & politics" -> R.string.genre_news_politics
    "religion & spirituality" -> R.string.genre_religion_spirituality
    "science" -> R.string.genre_science
    "sports" -> R.string.genre_sports
    "storytelling" -> R.string.genre_storytelling
    "technology" -> R.string.genre_technology
    else -> null
}

enum class BuyModuleType(val value: String, val displayName: String) {
    DIGITAL("DIGITAL", "DIGITAL"),
    VINYL("VINYL", "VINYL"),
    CD("CD", "CD"),
    CASSETTE("CASSETTE", "CASSETTE"),
    APPAREL("APPAREL", "APPAREL"),
    SAMPLE_PACK("SAMPLE_PACK", "SAMPLE PACK"),
    SUBSCRIPTION("SUBSCRIPTION", "SUBSCRIPTION"),
    LIVE_EVENT("LIVE_EVENT", "LIVE EVENT"),
    LIVE_STREAM("LIVE_STREAM", "LIVE STREAM");

    companion object {
        fun fromValue(value: String?): BuyModuleType {
            return entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: DIGITAL
        }
    }
}

data class FetchBuyModuleGraphQlRequest(
    @SerializedName("query") val query: String = FETCH_BUY_MODULE_QUERY,
    @SerializedName("variables") val variables: FetchBuyModuleVariables
)

data class FetchBuyModuleVariables(
    @SerializedName("urn") val urn: String
)

const val FETCH_BUY_MODULE_QUERY = """
query BuyModule(${'$'}urn: ID!) {
  buyModule(urn: ${'$'}urn) {
    type
    title
    link
    description
    linkTitle
    imageUrl
    price
  }
}
"""

data class FetchBuyModuleGraphQlResponse(
    @SerializedName("data") val data: FetchBuyModuleResponseData?,
    @SerializedName("errors") val errors: List<GraphQlError>?
)

data class FetchBuyModuleResponseData(
    @SerializedName("buyModule") val buyModule: BuyModuleItemData?
)

data class BuyModuleItemData(
    @SerializedName("type") val type: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("link") val link: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("linkTitle") val linkTitle: String?,
    @SerializedName("imageUrl") val imageUrl: String?,
    @SerializedName("price") val price: String?
)

data class CreateBuyModuleGraphQlRequest(
    @SerializedName("query") val query: String = CREATE_BUY_MODULE_MUTATION,
    @SerializedName("variables") val variables: CreateBuyModuleVariables
)

data class CreateBuyModuleVariables(
    @SerializedName("input") val input: CreateBuyModuleInput
)

data class CreateBuyModuleInput(
    @SerializedName("trackUrn") val trackUrn: String,
    @SerializedName("type") val type: String,
    @SerializedName("title") val title: String,
    @SerializedName("price") val price: String,
    @SerializedName("link") val link: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("linkTitle") val linkTitle: String? = null,
    @SerializedName("imageData") val imageData: String? = null,
    @SerializedName("imageUrl") val imageUrl: String? = null
)

const val CREATE_BUY_MODULE_MUTATION = """
mutation CreateBuyModule(${'$'}input: CreateBuyModuleInput!) {
  createBuyModule(input: ${'$'}input) {
    __typename
  }
}
"""

data class CreateBuyModuleGraphQlResponse(
    @SerializedName("data") val data: CreateBuyModuleResponseData?,
    @SerializedName("errors") val errors: List<GraphQlError>?
)

data class CreateBuyModuleResponseData(
    @SerializedName("createBuyModule") val createBuyModule: JsonObject?
)

data class DeleteBuyModuleGraphQlRequest(
    @SerializedName("query") val query: String = DELETE_BUY_MODULE_MUTATION,
    @SerializedName("variables") val variables: DeleteBuyModuleVariables
)

data class DeleteBuyModuleVariables(
    @SerializedName("input") val input: DeleteBuyModuleInput
)

data class DeleteBuyModuleInput(
    @SerializedName("urn") val urn: String
)

const val DELETE_BUY_MODULE_MUTATION = """
mutation DeleteBuyModule(${'$'}input: DeleteBuyModuleInput!) {
  deleteBuyModule(input: ${'$'}input) {
    __typename
  }
}
"""

data class DeleteBuyModuleGraphQlResponse(
    @SerializedName("data") val data: DeleteBuyModuleResponseData?,
    @SerializedName("errors") val errors: List<GraphQlError>?
)

data class DeleteBuyModuleResponseData(
    @SerializedName("deleteBuyModule") val deleteBuyModule: JsonObject?
)

