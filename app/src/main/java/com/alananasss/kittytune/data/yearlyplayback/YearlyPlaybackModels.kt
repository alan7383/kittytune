package com.alananasss.kittytune.data.yearlyplayback

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName

/**
 * Target persona for the yearly playback: Fan or Creator.
 * Direct parity with SoundCloud decompiled:
 * com.soundcloud.android.yearlyplayback.pub.YearlyPlaybackTarget
 */
enum class YearlyPlaybackTarget {
    FAN,
    CREATOR
}

/**
 * Playback variant: current year (2025), previous year (2024), or mock.
 * Direct parity with SoundCloud decompiled:
 * com.soundcloud.android.yearlyplayback.pub.YearlyPlaybackVariant
 */
enum class YearlyPlaybackVariant {
    CURRENT_YEAR,
    PREVIOUS_YEAR,
    MOCK
}

/**
 * Error types matching SoundCloud decompiled:
 * com.soundcloud.android.yearlyplayback.model.ErrorType
 */
enum class ErrorType {
    NETWORK,
    SERVER
}

/**
 * Key-value text run used in ArtBoard slides.
 * Direct parity with SoundCloud:
 * com.soundcloud.android.yearlyplayback.model.ApiYearlyPlaybackTextRun
 */
@Immutable
data class ApiYearlyPlaybackTextRun(
    @SerializedName("key") val key: String,
    @SerializedName("value") val value: String
)

@Immutable
data class ArtBoardTextRun(
    val key: String,
    val value: String
)

/**
 * Asset model matching SoundCloud:
 * com.soundcloud.android.yearlyplayback.model.ApiYearlyPlaybackAsset
 */
@Immutable
data class ApiYearlyPlaybackAsset(
    @SerializedName("assetId") val assetId: String,
    @SerializedName("url") val url: String
)

/**
 * Background track model matching SoundCloud:
 * com.soundcloud.android.yearlyplayback.model.ApiYearlyPlaybackBackgroundTrack
 */
@Immutable
data class ApiYearlyPlaybackBackgroundTrack(
    @SerializedName("backgroundTrackId") val backgroundTrackId: String,
    @SerializedName("track") val track: ApiYearlyTrackStub? = null
)

@Immutable
data class ApiYearlyTrackStub(
    @SerializedName("urn") val urn: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("permalinkUrl") val permalinkUrl: String? = null,
    @SerializedName("artworkUrlTemplate") val artworkUrlTemplate: String? = null,
    @SerializedName("waveformUrl") val waveformUrl: String? = null,
    @SerializedName("user") val user: ApiYearlyUserStub? = null,
    @SerializedName("fullDuration") val fullDuration: Long? = null,
    @SerializedName("snipDuration") val snipDuration: Long? = null
)

@Immutable
data class ApiYearlyUserStub(
    @SerializedName("urn") val urn: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("permalinkUrl") val permalinkUrl: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null
)

/**
 * Timeline item base for SoundCloud GraphQL response.
 * Direct parity with:
 * com.soundcloud.android.yearlyplayback.model.ApiYearlyPlaybackTimeline
 */
sealed class ApiYearlyPlaybackTimelineItem {
    abstract val backgroundTrackId: String?
    abstract val durationMs: Int

    data class ArtboardItem(
        override val backgroundTrackId: String?,
        override val durationMs: Int,
        val artboardId: String,
        val textRuns: List<ApiYearlyPlaybackTextRun> = emptyList()
    ) : ApiYearlyPlaybackTimelineItem()

    data class SavePlaylistItem(
        override val backgroundTrackId: String?,
        override val durationMs: Int,
        val artworkUrl: String,
        val backgroundImageUrl: String,
        val text: String,
        val activeTitle: String,
        val inactiveTitle: String,
        val urn: String
    ) : ApiYearlyPlaybackTimelineItem()

    data class OpenInsightsItem(
        override val backgroundTrackId: String?,
        override val durationMs: Int,
        val backgroundImageUrl: String,
        val text: String,
        val title: String
    ) : ApiYearlyPlaybackTimelineItem()

    object Unknown : ApiYearlyPlaybackTimelineItem() {
        override val backgroundTrackId: String? = null
        override val durationMs: Int = 5000
    }
}

/**
 * Domain block model consumed by the Compose UI.
 * Direct parity with SoundCloud:
 * com.soundcloud.android.yearlyplayback.model.Block
 */
@Immutable
sealed class Block {
    abstract val artboardId: String
    abstract val durationMs: Int
    abstract val index: Int
    abstract val backgroundTrack: ApiYearlyTrackStub?

    data class ArtBoard(
        override val artboardId: String,
        override val durationMs: Int,
        override val index: Int,
        override val backgroundTrack: ApiYearlyTrackStub?,
        val textRuns: List<ArtBoardTextRun> = emptyList()
    ) : Block()

    data class SavePlaylist(
        override val artboardId: String,
        override val durationMs: Int,
        override val index: Int,
        override val backgroundTrack: ApiYearlyTrackStub?,
        val playlistArtwork: String,
        val backgroundImageUrl: String,
        val text: String,
        val activeTitle: String,
        val inactiveTitle: String,
        val playlistUrn: String,
        val isLiked: Boolean = false
    ) : Block()

    data class OpenInsights(
        override val artboardId: String,
        override val durationMs: Int,
        override val index: Int,
        override val backgroundTrack: ApiYearlyTrackStub?,
        val backgroundImageUrl: String,
        val text: String,
        val title: String
    ) : Block()
}

/**
 * Encapsulates the entire fetched Yearly Playback payload from SoundCloud GraphQL:
 * timeline blocks, the Rive vector animation file asset, and image assets.
 */
@Immutable
data class YearlyPlaybackData(
    val blocks: List<Block>,
    val riveAssetUrl: String? = null,
    val imageAssets: List<ApiYearlyPlaybackAsset> = emptyList()
)

/**
 * Presentation state machine for Yearly Playback.
 * Direct parity with:
 * com.soundcloud.android.yearlyplayback.model.State
 */
@Immutable
sealed class YearlyPlaybackState {
    sealed class Prepare : YearlyPlaybackState() {
        object InitData : Prepare()
        data class InitRive(val data: YearlyPlaybackData) : Prepare()
        data class InitDownloadImages(val data: YearlyPlaybackData, val riveFile: app.rive.runtime.kotlin.core.File) : Prepare()
    }

    data class Display(
        val block: Block,
        val totalBlocks: Int,
        val durationMs: Int
    ) : YearlyPlaybackState()

    object Empty : YearlyPlaybackState()

    data class Error(val errorType: ErrorType) : YearlyPlaybackState()
}

/**
 * Analytics and interaction events.
 * Direct parity with SoundCloud:
 * com.soundcloud.android.yearlyplayback.tracking.YearlyPlaybackEvent
 */
sealed class YearlyPlaybackEvent {
    object Start : YearlyPlaybackEvent()
    data class Next(val fromIndex: Int, val toIndex: Int) : YearlyPlaybackEvent()
    data class Previous(val fromIndex: Int, val toIndex: Int) : YearlyPlaybackEvent()
    data class Pause(val index: Int) : YearlyPlaybackEvent()
    data class Resume(val index: Int) : YearlyPlaybackEvent()
    data class Share(val index: Int, val blockId: String) : YearlyPlaybackEvent()
    data class LikePlaylist(val urn: String, val liked: Boolean) : YearlyPlaybackEvent()
    object Close : YearlyPlaybackEvent()
}
