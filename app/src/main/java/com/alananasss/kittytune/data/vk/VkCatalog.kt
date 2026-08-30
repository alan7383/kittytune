package com.alananasss.kittytune.data.vk

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * A decoded `al_audio.php` response.
 *
 * VK answers with `<!--{"payload":[code,[html,section,...]], ...}`. `payload[1][0]` is either the
 * rendered HTML (sometimes split across several strings) and `payload[1][1]` the section object that
 * carries the track list and the pagination cursor.
 */
class VkPayload(val root: JSONObject) {

    val payload: JSONArray? get() = root.optJSONArray("payload")

    private val block: JSONArray? get() = payload?.optJSONArray(1)

    /** `payload[1][0]`, with the string array flattened the way MeridiusCore does. */
    val html: String
        get() {
            val first = block?.opt(0) ?: return ""
            return when (first) {
                is String -> first
                is JSONArray -> buildString {
                    for (i in 0 until first.length()) append(first.optString(i, ""))
                }
                // An object there is structured data, not markup — serialising it would only feed
                // the row scraper garbage.
                else -> ""
            }
        }

    /** `payload[1][1]` — the section descriptor. */
    val section: JSONObject? get() = block?.optJSONObject(1)

    /** `payload[1][0]` when VK returns data (not HTML) there, e.g. `reload_audios`. */
    val firstArray: JSONArray? get() = block?.optJSONArray(0)

    /** `payload[1][0]` as an object — that is where `act=load_section` puts the playlist. */
    val firstObject: JSONObject? get() = block?.optJSONObject(0)

    /**
     * The track list, wherever VK decided to put it for this particular act.
     *
     * `act=section` / `act=load_catalog_section` answer with the section descriptor at
     * `payload[1][1]`, while `act=load_section` returns the whole playlist object at `payload[1][0]`.
     * MeridiusCore reads both shapes (`AudioRequests.parsePayload` vs `PlaylistsRequest.getPlaylist`)
     * and missing the second one means playlists come back empty.
     */
    val list: JSONArray?
        get() {
            section?.let { sec ->
                sec.optJSONObject("playlist")?.optJSONArray("list")?.let { return it }
                sec.optJSONObject("playlistData")?.optJSONArray("list")?.let { return it }
                sec.optJSONArray("list")?.let { return it }
            }
            firstObject?.let { first ->
                first.optJSONObject("playlist")?.optJSONArray("list")?.let { return it }
                first.optJSONArray("list")?.let { return it }
            }
            return null
        }

    companion object {
        /** Strips VK's `<!--` guard and parses the envelope. */
        fun parse(raw: String?): VkPayload? {
            if (raw.isNullOrBlank()) return null
            var clean = raw.trim()
            if (clean.startsWith("<!--")) clean = clean.removePrefix("<!--").trim()
            if (clean.endsWith("-->")) clean = clean.removeSuffix("-->").trim()
            return try {
                VkPayload(JSONObject(clean))
            } catch (e: Exception) {
                Log.d("VkPayload", "Not a JSON envelope: ${e.message}")
                null
            }
        }
    }
}

/**
 * Pagination cursor for VK's audio catalog.
 *
 * MeridiusCore's `Static.parseMore` produces exactly this pair, and every "load more" request is
 * `act=load_catalog_section` with `section_id` + `start_from`. KittyTune previously had no cursor at
 * all, which is why artist pages and search stopped at the first page (20 or 50 tracks).
 */
data class VkMore(
    val sectionId: String,
    val nextFrom: String
) {
    val isValid: Boolean get() = sectionId.isNotBlank() && nextFrom.isNotBlank()

    companion object {
        private val SECTION_ID_REGEX = Regex("sectionId\"\\s*:\\s*\"(.*?)\"")
        private val NEXT_FROM_REGEX = Regex("next_from\"\\s*:\\s*\"(.*?)\"")

        /** `parseMore` for the object form: reads the section descriptor. */
        fun from(payload: VkPayload?): VkMore? = payload?.let { from(it.section) }

        fun from(section: JSONObject?): VkMore? {
            if (section == null) return null
            val sectionId = section.optString("section_id").ifBlank {
                section.optString("sectionId")
            }
            val nextFrom = section.optString("next_from")
                .ifBlank { section.optString("nextFrom") }
                .ifBlank { section.optString("nextOffset") }
                .ifBlank { section.optJSONObject("playlist")?.optString("nextOffset").orEmpty() }
            if (sectionId.isBlank() || nextFrom.isBlank()) return null
            return VkMore(sectionId, nextFrom)
        }

        /** `parseMore` for the string form: scrapes both values out of raw HTML/JS. */
        fun fromHtml(html: String?): VkMore? {
            if (html.isNullOrBlank()) return null
            val sectionId = SECTION_ID_REGEX.find(html)?.groupValues?.getOrNull(1) ?: return null
            val nextFrom = NEXT_FROM_REGEX.find(html)?.groupValues?.getOrNull(1).orEmpty()
            if (sectionId.isBlank()) return null
            return VkMore(sectionId, nextFrom)
        }

        fun sectionIdOf(html: String?): String? =
            html?.let { SECTION_ID_REGEX.find(it)?.groupValues?.getOrNull(1) }
    }
}

/**
 * Extracts raw audio arrays from a rendered VK page.
 *
 * Port of `AudioStatic.builderHTML`: every track row carries its whole audio tuple in a
 * `data-audio` attribute, which is the only way to read the tracks off an artist page.
 */
object VkHtmlAudio {

    /** Older VK markup ends the attribute with `" on…`, the current one with `" data…`. */
    private val OLD_ITEMS = Regex("data-audio=\"(.*?)\" on")
    private val NEW_ITEMS = Regex("data-audio=\"(.*?)\" data")

    fun parse(html: String?): List<JSONArray> {
        if (html.isNullOrBlank()) return emptyList()
        val regex = if (OLD_ITEMS.containsMatchIn(html)) OLD_ITEMS else NEW_ITEMS
        val out = mutableListOf<JSONArray>()
        for (match in regex.findAll(html)) {
            val raw = VkAudioItem.unescapeHtml(match.groupValues[1])
            try {
                out.add(JSONArray(raw))
            } catch (_: Exception) {
                // A row whose payload we cannot read is simply skipped.
            }
        }
        return out
    }

    private val COVER_REGEX = Regex("background-image:\\s?url\\('?(.*?)'?\\)")

    fun backgroundImage(styleAttribute: String?): String? = styleAttribute
        ?.let { COVER_REGEX.find(it)?.groupValues?.getOrNull(1) }
        ?.replace("&amp;", "&")
        ?.takeIf { it.isNotBlank() }
}
