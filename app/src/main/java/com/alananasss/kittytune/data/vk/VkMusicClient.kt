package com.alananasss.kittytune.data.vk

import com.alananasss.kittytune.data.network.ProxyManager
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import java.nio.charset.Charset

/** One track as VK lists it: what is needed to find the same recording elsewhere. */
data class VkTrack(
    val title: String,
    /** The credit line as shown, e.g. "Anel Renaldi, HIRO". */
    val artist: String,
    /** The main artists alone, without features, when VK knows them. */
    val mainArtists: List<String>,
    /** VK's version line: "Remix", "Slowed", "prod. X". Empty for most tracks. */
    val subtitle: String,
    val durationSec: Int,
)

data class VkImportedPlaylist(
    val title: String,
    val author: String,
    val coverUrl: String?,
    val tracks: List<VkTrack>,
)

/**
 * Reads a VK Music playlist without signing in.
 *
 * Uses the playlist widget endpoint al_audio.php?act=load_section.
 */
object VkMusicClient {

    sealed interface Result {
        data class Found(val playlist: VkImportedPlaylist) : Result
        /** Deleted, private, or a link without the access hash a private playlist needs. */
        data object NotAvailable : Result
        data class Failed(val message: String) : Result
    }

    private const val SECTION_URL = "https://vk.com/al_audio.php?act=load_section"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"

    /** A page is up to 2000 tracks; this bounds the loop for a list that never says it has ended. */
    private const val MAX_PAGES = 50

    // Tuple positions in VK's audio arrays.
    private const val TITLE = 3
    private const val PERFORMER = 4
    private const val DURATION = 5
    private const val SUBTITLE = 16
    private const val MAIN_ARTISTS = 17

    suspend fun fetchPlaylist(ref: VkPlaylistRef): Result = withContext(Dispatchers.IO) {
        try {
            var offset = 0
            var title = ""
            var author = ""
            var cover: String? = null
            val tracks = mutableListOf<VkTrack>()
            repeat(MAX_PAGES) {
                val page = loadSection(ref, offset) ?: return@withContext if (tracks.isEmpty()) Result.NotAvailable else Result.Found(VkImportedPlaylist(title, author, cover, tracks))
                if (offset == 0) {
                    title = decode(page.string("title"))
                    author = decode(page.string("authorName"))
                    cover = page.string("coverUrl").takeIf { it.isNotBlank() }
                }
                page.getAsJsonArray("list")?.forEach { element -> parseTrack(element)?.let(tracks::add) }
                val hasMore = page.get("hasMore")?.takeIf { !it.isJsonNull }?.let { it.asJsonPrimitive.let { p -> if (p.isBoolean) p.asBoolean else p.asInt != 0 } } ?: false
                val next = page.get("nextOffset")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                if (!hasMore || next <= offset) return@withContext Result.Found(VkImportedPlaylist(title, author, cover, tracks))
                offset = next
            }
            Result.Found(VkImportedPlaylist(title, author, cover, tracks))
        } catch (e: Exception) {
            Result.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /** A vk.cc short link's destination, or the link itself when it does not redirect anywhere useful. */
    suspend fun expandShortLink(link: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(if ("://" in link) link else "https://$link").header("User-Agent", USER_AGENT).build()
            ProxyManager.getOkHttpClient().newCall(request).execute().use { it.request.url.toString() }
        }.getOrDefault(link)
    }

    /** One page of the playlist, or null when VK has nothing to show for it. */
    private fun loadSection(ref: VkPlaylistRef, offset: Int): JsonObject? {
        val form = FormBody.Builder()
            .add("act", "load_section")
            .add("al", "1")
            .add("claim", "0")
            .add("is_loading_all", "1")
            .add("offset", offset.toString())
            .add("owner_id", ref.ownerId.toString())
            .add("playlist_id", ref.playlistId.toString())
            .add("type", "playlist")
            .add("access_hash", ref.accessHash)
            .build()
        val request = Request.Builder()
            .url(SECTION_URL)
            .post(form)
            .header("User-Agent", USER_AGENT)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Origin", "https://vk.com")
            .header("Referer", "https://vk.com/widget_playlist.php?oid=${ref.ownerId}&pid=${ref.playlistId}")
            .build()
        val body = ProxyManager.getOkHttpClient().newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("VK answered ${response.code}")
            val bytes = response.body.bytes()
            val charset = response.body.contentType()?.charset() ?: Charset.forName("windows-1251")
            String(bytes, charset)
        }
        val payload = JsonParser.parseString(body).asJsonObject.getAsJsonArray("payload") ?: return null
        val sections = payload.get(1)?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        val first = sections.firstOrNull() ?: return null
        return first.takeIf { it.isJsonObject }?.asJsonObject
    }

    private fun parseTrack(element: JsonElement): VkTrack? {
        val tuple = element.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        val title = decode(tuple.stringAt(TITLE))
        if (title.isBlank()) return null
        val mainArtists = tuple.getOrNull(MAIN_ARTISTS)?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject?.get("name")?.asString?.let(::decode) }
            .orEmpty()
        return VkTrack(
            title = title,
            artist = decode(tuple.stringAt(PERFORMER)),
            mainArtists = mainArtists,
            subtitle = decode(tuple.stringAt(SUBTITLE)),
            durationSec = tuple.getOrNull(DURATION)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: 0,
        )
    }

    private fun JsonArray.getOrNull(index: Int): JsonElement? = if (index < size()) get(index) else null

    private fun JsonArray.stringAt(index: Int): String =
        getOrNull(index)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString.orEmpty()

    private fun JsonObject.string(name: String): String =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

    private val ENTITY = Regex("""&(#x[0-9a-fA-F]+|#\d+|amp|lt|gt|quot|apos|nbsp);""")
    private val TAG = Regex("<[^>]+>")

    /** Undoes VK's HTML escaping (and drops any markup) so a name compares as the name it is. */
    internal fun decode(raw: String): String {
        var text = raw
        repeat(2) {
            text = ENTITY.replace(text) { match ->
                when (val entity = match.groupValues[1]) {
                    "amp" -> "&"
                    "lt" -> "<"
                    "gt" -> ">"
                    "quot" -> "\""
                    "apos" -> "'"
                    "nbsp" -> " "
                    else -> {
                        val code = if (entity.startsWith("#x")) entity.drop(2).toIntOrNull(16) else entity.drop(1).toIntOrNull()
                        code?.let { String(Character.toChars(it)) } ?: match.value
                    }
                }
            }
        }
        return TAG.replace(text, "").trim()
    }
}
