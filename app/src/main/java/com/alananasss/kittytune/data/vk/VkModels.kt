package com.alananasss.kittytune.data.vk

import com.alananasss.kittytune.domain.Format
import com.alananasss.kittytune.domain.Media
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.TrackPublisherMetadata
import com.alananasss.kittytune.domain.Transcoding
import com.alananasss.kittytune.domain.User
import org.json.JSONArray
import org.json.JSONObject

/**
 * Layout of VK's audio tuple, copied from `Static.AudioObject` in MeridiusCore.
 * VK ships tracks as positional arrays, so these indices are the schema.
 */
object VkIndex {
    const val ID = 0
    const val OWNER_ID = 1
    const val URL = 2
    const val TITLE = 3
    const val PERFORMER = 4
    const val DURATION = 5
    const val ALBUM_ID = 6
    const val AUTHOR_LINK = 8
    const val LYRICS = 9
    const val FLAGS = 10
    const val CONTEXT = 11
    const val EXTRA = 12
    const val HASHES = 13
    const val COVER_URL = 14
    const val ADS = 15
    const val SUBTITLE = 16
    const val MAIN_ARTISTS = 17
    const val FEAT_ARTISTS = 18
    const val ALBUM = 19
    const val TRACK_CODE = 20
    const val RESTRICTION = 21
    const val CHART = 25

    const val FLAG_CAN_ADD = 2
    const val FLAG_CLAIMED = 4
    const val FLAG_HQ = 16
    const val FLAG_UMA = 128
    const val FLAG_REPLACEABLE = 512
    const val FLAG_EXPLICIT = 1024
}

/**
 * The `/` separated hash bundle VK puts at index 13.
 *
 * Components 2 (`action`) and 5 (`url`) are what `reload_audios` needs to hand back a playable
 * stream, so the whole raw string is carried on [Track.secretToken] and parsed back here. Keeping
 * only one hash — as KittyTune did — makes stream refresh impossible.
 */
data class VkHashes(
    val add: String = "",
    val edit: String = "",
    val action: String = "",
    val delete: String = "",
    val replace: String = "",
    val url: String = ""
) {
    val raw: String get() = listOf(add, edit, action, delete, replace, url).joinToString("/")

    companion object {
        /** Separates the hash bundle from the track code inside [Track.secretToken]. */
        const val TRACK_CODE_SEPARATOR = '|'

        fun parse(raw: String?): VkHashes {
            if (raw.isNullOrBlank()) return VkHashes()
            val parts = raw.substringBefore(TRACK_CODE_SEPARATOR).split("/")
            return VkHashes(
                add = parts.getOrNull(0).orEmpty(),
                edit = parts.getOrNull(1).orEmpty(),
                action = parts.getOrNull(2).orEmpty(),
                delete = parts.getOrNull(3).orEmpty(),
                replace = parts.getOrNull(4).orEmpty(),
                url = parts.getOrNull(5).orEmpty()
            )
        }

        /** VK's analytics id, which `act=add` and `act=delete_audio` echo back. */
        fun trackCodeOf(raw: String?): String =
            raw?.substringAfter(TRACK_CODE_SEPARATOR, "").orEmpty()

        fun encode(hashes: VkHashes, trackCode: String): String =
            if (trackCode.isBlank()) hashes.raw else "${hashes.raw}$TRACK_CODE_SEPARATOR$trackCode"
    }
}

data class VkArtist(
    val id: String?,
    val name: String
) {
    /** VK artist pages live at `/artist/<id>`, where the id is a slug. */
    val slug: String? get() = id?.takeIf { it.isNotBlank() }
}

data class VkAlbum(
    val id: Long,
    val title: String,
    val ownerId: Long,
    val accessHash: String?,
    val thumbUrl: String?
)

/** Artist page as returned by `Artists.get`. */
data class VkArtistPage(
    val slug: String,
    val name: String,
    val coverUrl: String? = null,
    val tracks: List<VkAudioItem> = emptyList(),
    val more: VkMore? = null
)

data class VkAudioItem(
    val id: Long,
    val ownerId: Long,
    val url: String = "",
    val title: String = "",
    val performer: String = "",
    val durationSeconds: Int = 0,
    val albumId: Long = 0L,
    val lyricsId: Long = 0L,
    val flags: Int = 0,
    val hashes: VkHashes = VkHashes(),
    val coverUrl: String? = null,
    val subtitle: String = "",
    val mainArtists: List<VkArtist> = emptyList(),
    val featArtists: List<VkArtist> = emptyList(),
    val album: VkAlbum? = null,
    val trackCode: String = "",
    val authorLink: String = "",
    val isRestricted: Boolean = false
) {
    val fullId: String get() = "${ownerId}_$id"

    val hasLyrics: Boolean get() = lyricsId != 0L
    val isExplicit: Boolean get() = flags and VkIndex.FLAG_EXPLICIT != 0
    val isHq: Boolean get() = flags and VkIndex.FLAG_HQ != 0
    val canAdd: Boolean get() = flags and VkIndex.FLAG_CAN_ADD != 0

    /** Slug for the VK artist page, preferring the structured artist list over the display name. */
    val artistSlug: String
        get() = mainArtists.firstNotNullOfOrNull { it.slug }
            ?: authorLink.takeIf { it.isNotBlank() }
            ?: performer

    val displayArtists: String
        get() {
            val names = (mainArtists.map { it.name } + featArtists.map { it.name })
                .filter { it.isNotBlank() }
            return if (names.isEmpty()) performer else names.joinToString(", ")
        }

    fun toTrack(): Track {
        val durationMs = durationSeconds.toLong() * 1000L
        val displayTitle = if (subtitle.isNotBlank()) "$title ($subtitle)" else title
        val cover = coverUrl?.takeIf { it.isNotBlank() }
        val artistName = displayArtists.ifBlank { "VKontakte" }

        val playable = url.takeIf {
            it.isNotBlank() && it.startsWith("http") && !VkAudioDecoder.isMaskedUrl(it)
        }
        val transcodings = playable?.let {
            val hls = it.contains(".m3u8")
            listOf(
                Transcoding(
                    url = it,
                    preset = if (isHq) "mp3_1_0" else "mp3_0_0",
                    format = Format(
                        protocol = if (hls) "hls" else "progressive",
                        mimeType = if (hls) "application/x-mpegURL" else "audio/mpeg"
                    )
                )
            )
        } ?: emptyList()

        return Track(
            id = id,
            title = displayTitle,
            artworkUrl = cover,
            durationMs = durationMs,
            user = User(
                id = ownerId,
                username = artistName,
                avatarUrl = cover,
                urn = "vk:artist:$artistSlug",
                // vk.com is used for anything a human may open or share: it resolves worldwide and
                // redirects to vk.ru inside Russia. API traffic still goes to vk.ru.
                permalinkUrl = "https://vk.com/artist/$artistSlug",
                permalink = artistSlug
            ),
            media = Media(transcodings = transcodings),
            publisherMetadata = TrackPublisherMetadata(
                artist = artistName,
                albumTitle = album?.title,
                explicit = isExplicit
            ),
            permalinkUrl = "https://vk.com/audio${ownerId}_$id",
            permalink = "audio${ownerId}_$id",
            // The whole hash bundle travels with the track so the stream can be refreshed later,
            // together with the track code `act=add` / `act=delete_audio` expect.
            secretToken = VkHashes.encode(hashes, trackCode),
            source = SOURCE,
            fullDuration = durationMs
        )
    }

    companion object {
        const val SOURCE = "vk"

        /** Port of `AudioStatic.getAudioAsObject`. */
        fun fromJsonArray(arr: JSONArray, sessionUserId: Long = 0L): VkAudioItem? {
            try {
                if (arr.length() < 6) return null
                val id = arr.optLong(VkIndex.ID)
                val ownerId = arr.optLong(VkIndex.OWNER_ID)
                if (id == 0L) return null

                val rawUrl = arr.optString(VkIndex.URL, "")
                val hashes = VkHashes.parse(arr.optString(VkIndex.HASHES, ""))

                return VkAudioItem(
                    id = id,
                    ownerId = ownerId,
                    // The `i` operation seeds the shuffle with the *session* user, never the owner.
                    url = VkAudioDecoder.exposeSource(rawUrl, sessionUserId),
                    title = unescapeHtml(arr.optString(VkIndex.TITLE, "")),
                    performer = unescapeHtml(arr.optString(VkIndex.PERFORMER, "")),
                    durationSeconds = arr.optInt(VkIndex.DURATION, 0),
                    albumId = arr.optLong(VkIndex.ALBUM_ID, 0L),
                    lyricsId = arr.optLong(VkIndex.LYRICS, 0L),
                    flags = arr.optInt(VkIndex.FLAGS, 0),
                    hashes = hashes,
                    coverUrl = parseCoverUrl(arr.optString(VkIndex.COVER_URL, "")),
                    subtitle = unescapeHtml(arr.optString(VkIndex.SUBTITLE, "")),
                    mainArtists = parseArtists(arr.optJSONArray(VkIndex.MAIN_ARTISTS)),
                    featArtists = parseArtists(arr.optJSONArray(VkIndex.FEAT_ARTISTS)),
                    album = parseAlbum(arr.optJSONArray(VkIndex.ALBUM)),
                    trackCode = arr.optString(VkIndex.TRACK_CODE, ""),
                    authorLink = parseAuthorLink(arr.optString(VkIndex.AUTHOR_LINK, "")),
                    isRestricted = arr.optInt(VkIndex.RESTRICTION, 0) != 0
                )
            } catch (e: Exception) {
                return null
            }
        }

        /** Objects come from the official REST API (`audio.get`, `audio.search`). */
        fun fromJsonObject(obj: JSONObject, sessionUserId: Long = 0L): VkAudioItem? {
            try {
                val id = obj.optLong("id")
                val ownerId = obj.optLong("owner_id", obj.optLong("ownerId", 0L))
                if (id == 0L) return null

                val albumObj = obj.optJSONObject("album")
                val thumb = albumObj?.optJSONObject("thumb")
                val albumCover = listOf("photo_1200", "photo_600", "photo_300", "photo_135")
                    .firstNotNullOfOrNull { key -> thumb?.optString(key)?.takeIf { it.isNotBlank() } }
                    ?: albumObj?.optString("thumb")?.takeIf { it.isNotBlank() }

                val cover = listOf("coverUrl_p", "coverUrl_s", "thumb")
                    .firstNotNullOfOrNull { key -> obj.optString(key).takeIf { it.isNotBlank() } }
                    ?: albumCover

                return VkAudioItem(
                    id = id,
                    ownerId = ownerId,
                    url = VkAudioDecoder.exposeSource(obj.optString("url", ""), sessionUserId),
                    title = unescapeHtml(obj.optString("title", "")),
                    performer = unescapeHtml(
                        obj.optString("artist", obj.optString("performer", ""))
                    ),
                    durationSeconds = obj.optInt("duration", 0),
                    albumId = obj.optLong("album_id", 0L),
                    lyricsId = obj.optLong("lyrics_id", 0L),
                    hashes = VkHashes(
                        add = obj.optString("add_hash", ""),
                        edit = obj.optString("edit_hash", ""),
                        action = obj.optString("action_hash", ""),
                        delete = obj.optString("delete_hash", ""),
                        url = obj.optString("url_hash", "")
                    ),
                    coverUrl = cover?.replace("&amp;", "&"),
                    subtitle = unescapeHtml(obj.optString("subtitle", "")),
                    mainArtists = parseArtists(obj.optJSONArray("main_artists")),
                    featArtists = parseArtists(obj.optJSONArray("featured_artists")),
                    isRestricted = obj.optInt("content_restricted", 0) != 0
                )
            } catch (e: Exception) {
                return null
            }
        }

        private fun parseArtists(arr: JSONArray?): List<VkArtist> {
            if (arr == null) return emptyList()
            val out = mutableListOf<VkArtist>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = unescapeHtml(obj.optString("name", ""))
                if (name.isBlank()) continue
                out.add(VkArtist(id = obj.optString("id").takeIf { it.isNotBlank() }, name = name))
            }
            return out
        }

        private fun parseAlbum(arr: JSONArray?): VkAlbum? {
            if (arr == null || arr.length() < 3) return null
            val albumId = arr.optLong(0)
            if (albumId == 0L) return null
            return VkAlbum(
                id = albumId,
                title = unescapeHtml(arr.optString(1, "")),
                ownerId = arr.optLong(2),
                accessHash = arr.optString(3, "").takeIf { it.isNotBlank() },
                thumbUrl = arr.optString(4, "").takeIf { it.isNotBlank() }
            )
        }

        /** Index 8 holds markup such as `<a href="/artist/some-slug">Name</a>`. */
        private fun parseAuthorLink(raw: String): String {
            if (raw.isBlank()) return ""
            return Regex("/artist/([^\"?&]+)").find(raw)?.groupValues?.getOrNull(1).orEmpty()
        }

        /** Index 14 is `small,large[,extra]`; the largest entry is preferred. */
        private fun parseCoverUrl(raw: String): String? {
            if (raw.isBlank()) return null
            val parts = raw.split(",").map { it.trim() }.filter { it.startsWith("http") }
            return (parts.lastOrNull() ?: parts.firstOrNull())?.replace("&amp;", "&")
        }

        /**
         * MeridiusCore hands the string to a real HTML parser (`jsdom`) to decode entities, so every
         * numeric entity is handled here too rather than only a hand-picked list of named ones.
         */
        fun unescapeHtml(text: String): String {
            if (text.isBlank()) return text
            var out = text
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ")
                .replace("\\/", "/")

            if (out.contains("&#")) {
                out = NUMERIC_ENTITY.replace(out) { match ->
                    val hex = match.groupValues[1].isNotEmpty()
                    val digits = match.groupValues[2]
                    val code = digits.toIntOrNull(if (hex) 16 else 10)
                    if (code == null || code !in 1..0x10FFFF) match.value else String(Character.toChars(code))
                }
            }

            // Ampersands last, so "&amp;#39;" does not turn into a quote.
            return out.replace("&amp;", "&").trim()
        }

        private val NUMERIC_ENTITY = Regex("&#(x|X)?([0-9a-fA-F]+);")
    }
}

data class VkUser(
    val id: Long,
    val firstName: String = "",
    val lastName: String = "",
    val screenName: String? = null,
    val photoMax: String? = null,
    val status: String? = null
) {
    val fullName: String
        get() = "$firstName $lastName".trim().ifBlank { screenName ?: "VKontakte User" }

    /** Shareable profile link. */
    val permalinkUrl: String
        get() = "https://vk.com/" + (screenName?.takeIf { it.isNotBlank() } ?: "id$id")
}

data class VkPlaylist(
    val id: Long,
    val ownerId: Long,
    val title: String,
    val description: String? = null,
    val count: Int = 0,
    val coverUrl: String? = null,
    val accessHash: String? = null
) {
    val permalinkUrl: String
        get() = "https://vk.com/music/playlist/${ownerId}_$id" +
                (accessHash?.takeIf { it.isNotBlank() }?.let { "_$it" } ?: "")

    companion object {
        /** `owner_playlists` returns positional arrays. */
        fun fromJsonArray(arr: JSONArray, fallbackOwnerId: Long): VkPlaylist? {
            if (arr.length() < 3) return null
            val id = arr.optLong(0)
            if (id == 0L) return null
            return VkPlaylist(
                id = id,
                ownerId = arr.optLong(1, fallbackOwnerId),
                title = VkAudioItem.unescapeHtml(arr.optString(2, "Playlist")),
                description = VkAudioItem.unescapeHtml(arr.optString(3, "")).takeIf { it.isNotBlank() },
                count = arr.optInt(4, 0),
                coverUrl = arr.optString(5, "").takeIf { it.startsWith("http") },
                accessHash = arr.optString(6, "").takeIf { it.isNotBlank() }
            )
        }

        fun fromJsonObject(obj: JSONObject, fallbackOwnerId: Long): VkPlaylist? {
            val id = obj.optLong("id")
            if (id == 0L) return null
            return VkPlaylist(
                id = id,
                ownerId = obj.optLong("owner_id", fallbackOwnerId),
                title = VkAudioItem.unescapeHtml(obj.optString("title", "Playlist")),
                description = VkAudioItem.unescapeHtml(obj.optString("description", ""))
                    .takeIf { it.isNotBlank() },
                count = obj.optInt("count", obj.optInt("totalCount", 0)),
                coverUrl = listOf("cover_url", "coverUrl", "thumb")
                    .firstNotNullOfOrNull { key -> obj.optString(key).takeIf { it.startsWith("http") } }
                    ?.replace("&amp;", "&"),
                accessHash = obj.optString("access_hash", "").takeIf { it.isNotBlank() }
            )
        }
    }
}

data class VkSearchResult(
    val tracks: List<Track> = emptyList(),
    val playlists: List<VkPlaylist> = emptyList(),
    val moreOffset: String? = null,
    val more: VkMore? = null
) {
    val hasMore: Boolean get() = more?.isValid == true
}

data class VkAudioSectionResult(
    val tracks: List<Track> = emptyList(),
    val hasMore: Boolean = false,
    val nextOffset: Int = 0,
    val totalCount: Int = 0,
    val more: VkMore? = null
)
