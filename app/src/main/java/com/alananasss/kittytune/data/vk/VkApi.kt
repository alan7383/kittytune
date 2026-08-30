package com.alananasss.kittytune.data.vk

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * VK audio API client, modelled on MeridiusCore's `HTTPClient` + `Static` pair.
 *
 * Everything goes through the shared [VkHttp] client so the cookie jar established at login is the
 * same one used by search, artist pages and stream resolution. `vk.ru` is always tried first and
 * `vk.com` only as a fallback, matching MeridiusCore's configuration.
 */
class VkApi(context: Context) {

    private val appContext = context.applicationContext
    private val tokenManager = VkTokenManager(appContext)

    private val cookieJar: VkCookieJar get() = VkHttp.cookieJar(appContext)
    private val client get() = VkHttp.client(appContext)

    // ------------------------------------------------------------------ transport

    /**
     * POSTs (or GETs) a VK web endpoint, walking the host list until one answers *usefully*.
     *
     * @param pathOf turns a host such as `https://vk.ru` into the full URL.
     * @param accept decides whether a body is the answer we wanted. A host that replies 200 with a
     *   logged-out or gated page is not an answer, so the next domain is tried instead of returning
     *   it — that is what makes the `vk.ru` first / `vk.com` fallback order safe.
     */
    private fun webCall(
        hosts: List<String> = VkEndpoints.WEB_HOSTS,
        params: Map<String, String>? = null,
        ajax: Boolean = true,
        accept: (String) -> Boolean = { it.isNotBlank() },
        pathOf: (String) -> String
    ): String? {
        var fallback: String? = null

        for (host in hosts) {
            val url = pathOf(host)
            try {
                val builder = Request.Builder()
                    .url(url)
                    .header("User-Agent", VkEndpoints.BROWSER_USER_AGENT)
                    .header("Accept-Language", "ru-RU,ru;q=0.8,en-US;q=0.5,en;q=0.3")
                    .header("Origin", VkEndpoints.origin(host))
                    .header("Referer", VkEndpoints.referer(host))

                if (ajax) {
                    builder.header("Accept", "*/*")
                    builder.header("X-Requested-With", "XMLHttpRequest")
                } else {
                    // Page requests must look like a navigation, otherwise VK answers with an AJAX
                    // envelope and the `sectionId` the catalogue loader needs is missing.
                    builder.header(
                        "Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8"
                    )
                }

                // OkHttp fills the Cookie header from the jar; this covers the case where the jar is
                // still empty but a session exists in the legacy preferences.
                if (cookieJar.cookieHeader(url).isBlank()) {
                    val legacy = tokenManager.getCookieHeader(url)
                    if (legacy.isNotBlank()) builder.header("Cookie", legacy)
                }

                if (params != null) {
                    val form = FormBody.Builder()
                    params.forEach { (k, v) -> form.add(k, v) }
                    builder.post(form.build())
                }

                client.newCall(builder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "HTTP ${response.code} for $url")
                        return@use
                    }
                    val body = response.body.string()
                    if (body.isBlank()) {
                        Log.w(TAG, "Empty body for $url")
                        return@use
                    }
                    if (accept(body)) return body
                    Log.w(TAG, "Unusable response from $url (${body.length} bytes)")
                    if (fallback == null) fallback = body
                }
            } catch (e: Exception) {
                Log.w(TAG, "Request to $url failed: ${e.message}")
            }
        }
        return fallback
    }

    /** Port of `Static.request` — a POST to `al_audio.php` (or another VK ajax entry point). */
    suspend fun request(
        params: Map<String, String>,
        file: String = "al_audio.php"
    ): VkPayload? = withContext(Dispatchers.IO) {
        val body = webCall(
            params = params,
            // Only a parseable envelope counts, so a logged-out HTML page from one domain does not
            // stop us from trying the other.
            accept = { VkPayload.parse(it)?.payload != null }
        ) { host -> "$host/${file.trimStart('/')}" }

        val payload = VkPayload.parse(body)
        if (payload == null) {
            Log.w(
                TAG,
                "No VK payload for act=${params["act"]} " +
                        "(session=${cookieJar.hasSession()}, token=${!tokenManager.accessToken.isNullOrBlank()})"
            )
        }
        payload
    }

    /**
     * Loads a VK page (artist page, playlist page) so its `sectionId` can be read.
     *
     * @param requireSectionId when true a page without a `sectionId` is treated as a miss, so the
     *   other domain gets a chance instead of us giving up on a gated response.
     */
    suspend fun fetchPage(
        path: String,
        requireSectionId: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        webCall(
            params = emptyMap(),
            ajax = false,
            accept = { body ->
                body.isNotBlank() && (!requireSectionId || VkMore.sectionIdOf(body) != null)
            }
        ) { host -> "$host/${path.trimStart('/')}" }
    }

    // ------------------------------------------------------------------ catalog

    /** Port of `Static.loadCatalogSection`. */
    suspend fun loadCatalogSection(sectionId: String, startFrom: String? = null): VkPayload? {
        val params = mutableMapOf(
            "act" to "load_catalog_section",
            "al" to "1",
            "section_id" to sectionId
        )
        if (!startFrom.isNullOrBlank()) params["start_from"] = startFrom
        return request(params)
    }

    /**
     * Port of `Static.loadCatalogSectionFromPage`: fetch the page, scrape its `sectionId`, then ask
     * for that section as JSON. This is the only way to get an artist's catalogue, and doing it in
     * one step (as KittyTune did with `act=load_catalog_section&url=/artist/x`) silently returns just
     * the first page.
     */
    suspend fun loadCatalogSectionFromPage(path: String): VkPayload? {
        val page = fetchPage(path, requireSectionId = true) ?: return null
        val sectionId = VkMore.sectionIdOf(page) ?: return null
        return loadCatalogSection(sectionId)
    }

    /** Port of `Static.getSection` — `act=section`, used for search, my music, recoms, explore. */
    suspend fun getSection(
        section: String,
        ownerId: Long,
        extra: Map<String, String> = emptyMap()
    ): VkPayload? {
        val params = mutableMapOf(
            "act" to "section",
            "al" to "1",
            "claim" to "0",
            "is_layer" to "0",
            "owner_id" to ownerId.toString(),
            "section" to section
        )
        params.putAll(extra)
        return request(params)
    }

    /**
     * Walks the `next_from` cursor, collecting raw audio tuples until VK runs out of pages or
     * [limit] tracks have been gathered.
     */
    private suspend fun collectCatalog(
        first: VkPayload?,
        limit: Int
    ): Pair<List<JSONArray>, VkMore?> {
        val collected = mutableListOf<JSONArray>()
        var payload = first ?: return collected to null
        var more = VkMore.from(payload) ?: VkMore.fromHtml(payload.html)
        var guard = 0

        collected.addAll(rawAudios(payload))

        while (collected.size < limit && more != null && more.isValid && guard++ < MAX_PAGES) {
            payload = loadCatalogSection(more.sectionId, more.nextFrom) ?: break
            val page = rawAudios(payload)
            // VK occasionally answers an empty page while it warms the section up; Meridius retries.
            if (page.isEmpty()) {
                delay(400)
                val retry = loadCatalogSection(more.sectionId, more.nextFrom) ?: break
                val retried = rawAudios(retry)
                if (retried.isEmpty()) break
                collected.addAll(retried)
                payload = retry
            } else {
                collected.addAll(page)
            }
            val next = VkMore.from(payload) ?: VkMore.fromHtml(payload.html)
            if (next == null || next.nextFrom == more.nextFrom) {
                more = null
                break
            }
            more = next
        }

        // VK repeats rows across page boundaries now and then; keeping duplicates would show the
        // same track twice on an artist page.
        val seen = mutableSetOf<String>()
        val unique = collected.filter { item ->
            seen.add("${item.optLong(VkIndex.OWNER_ID)}_${item.optLong(VkIndex.ID)}")
        }
        return unique to more
    }

    /** Track tuples from a payload, preferring the JSON list and falling back to the HTML rows. */
    private fun rawAudios(payload: VkPayload): List<JSONArray> {
        val list = payload.list
        if (list != null && list.length() > 0) {
            val out = mutableListOf<JSONArray>()
            for (i in 0 until list.length()) {
                list.optJSONArray(i)?.let { out.add(it) }
            }
            if (out.isNotEmpty()) return out
        }
        return VkHtmlAudio.parse(payload.html)
    }

    // ------------------------------------------------------------------ normalisation

    /**
     * Port of `AudioRequests.getById` — `act=reload_audios`.
     *
     * The composite id VK expects is `ownerId_audioId_actionHash_urlHash`. Sending only
     * `ownerId_audioId` (what KittyTune did) makes VK answer `no_audios`, which is the real reason
     * VK-only tracks failed with "Playback failed: please check your internet connection".
     */
    suspend fun reloadAudios(compositeIds: List<String>): List<JSONArray> {
        if (compositeIds.isEmpty()) return emptyList()
        val out = mutableListOf<JSONArray>()

        for (chunk in compositeIds.chunked(RELOAD_CHUNK)) {
            val ids = chunk.joinToString(",")
            var attempt = 0
            while (attempt++ < RELOAD_ATTEMPTS) {
                val payload = request(
                    mapOf(
                        "act" to "reload_audios",
                        "al" to "1",
                        "audio_ids" to ids
                    )
                )
                val items = payload?.firstArray
                if (items == null) {
                    if (payload != null && payload.html.contains("no_audios")) {
                        delay(RELOAD_RETRY_DELAY_MS)
                        continue
                    }
                    break
                }
                for (i in 0 until items.length()) {
                    items.optJSONArray(i)?.let { out.add(it) }
                }
                break
            }
        }
        return out
    }

    /**
     * Port of `AudioRequests.normalize`: tracks that arrive without a usable stream URL are
     * re-requested through `reload_audios` and the fresh fields are merged into the original tuple.
     *
     * MeridiusCore reloads every track; here only the ones whose URL cannot be unmasked into a
     * playable link are reloaded. On a phone — often behind a VPN — that difference is the gap
     * between one round trip and a dozen, and the reload exists purely to obtain a URL.
     */
    private suspend fun normalize(raw: List<JSONArray>): List<VkAudioItem> {
        if (raw.isEmpty()) return emptyList()

        val needsReload = mutableMapOf<Int, String>()
        raw.forEachIndexed { index, item ->
            val hashes = item.optString(VkIndex.HASHES, "").split("/")
            val urlHash = hashes.getOrNull(5).orEmpty()
            if (urlHash.isBlank()) return@forEachIndexed

            val decoded = VkAudioDecoder.exposeSource(
                item.optString(VkIndex.URL, ""),
                tokenManager.userId
            )
            val playable = decoded.startsWith("http") && !VkAudioDecoder.isMaskedUrl(decoded)
            if (playable) return@forEachIndexed

            val actionHash = hashes.getOrNull(2).orEmpty()
            needsReload[index] =
                "${item.optLong(VkIndex.OWNER_ID)}_${item.optLong(VkIndex.ID)}_${actionHash}_$urlHash"
        }

        if (needsReload.isEmpty()) {
            return raw.mapNotNull { VkAudioItem.fromJsonArray(it, tokenManager.userId) }
        }

        val reloaded = reloadAudios(needsReload.values.toList())
        val byId = mutableMapOf<String, JSONArray>()
        for (item in reloaded) {
            byId["${item.optLong(VkIndex.OWNER_ID)}_${item.optLong(VkIndex.ID)}"] = item
        }

        return raw.mapIndexedNotNull { index, item ->
            val key = "${item.optLong(VkIndex.OWNER_ID)}_${item.optLong(VkIndex.ID)}"
            val fresh = if (needsReload.containsKey(index)) byId[key] else null
            val merged = if (fresh == null) item else mergeTuples(item, fresh)
            VkAudioItem.fromJsonArray(merged, tokenManager.userId)
        }
    }

    /** `item.map((value, i) => value || fresh[i])` — keeps existing fields, fills in the blanks. */
    private fun mergeTuples(original: JSONArray, fresh: JSONArray): JSONArray {
        val size = maxOf(original.length(), fresh.length())
        val out = JSONArray()
        for (i in 0 until size) {
            val value = original.opt(i)
            val isBlank = value == null || value == JSONObject.NULL ||
                    (value is String && value.isEmpty()) ||
                    (value is Number && value.toDouble() == 0.0)
            out.put(if (isBlank) fresh.opt(i) else value)
        }
        return out
    }

    // ------------------------------------------------------------------ my music

    /**
     * The signed-in user's (or another owner's) uploaded audio.
     *
     * Port of `AudioRequests.get`, which uses two different sections: `general` for your own music
     * (whose `playlist.id` is then a catalogue section id) and `all` for somebody else's. KittyTune
     * used `section=my` for both, which is not a section VK serves this way.
     *
     * [count] is a soft target: the cursor is followed until enough tracks are collected, which is
     * what lifts the hard 50-track ceiling on VK profiles.
     */
    suspend fun getUserAudios(
        userId: Long = tokenManager.userId,
        offset: Int = 0,
        count: Int = DEFAULT_PAGE
    ): VkAudioSectionResult = withContext(Dispatchers.IO) {
        val isSelf = userId == tokenManager.userId && userId != 0L

        val first = if (isSelf) {
            val general = getSection("general", userId)
            val sectionId = general?.section?.optJSONObject("playlist")?.optString("id")
                ?: VkMore.sectionIdOf(general?.html)
            if (sectionId.isNullOrBlank()) general else loadCatalogSection(sectionId)
        } else {
            getSection("all", userId)
        }

        val (raw, more) = collectCatalog(first, count)
        val items = normalize(raw)

        if (items.isEmpty()) {
            val rest = restAudioGet(userId, offset, count)
            if (rest.isNotEmpty()) {
                return@withContext VkAudioSectionResult(
                    tracks = rest.map { it.toTrack() },
                    hasMore = rest.size >= count,
                    nextOffset = offset + rest.size,
                    totalCount = rest.size
                )
            }
        }

        VkAudioSectionResult(
            tracks = items.map { it.toTrack() },
            hasMore = more?.isValid == true,
            nextOffset = offset + items.size,
            totalCount = totalCountOf(first) ?: items.size,
            more = more
        )
    }

    private fun totalCountOf(payload: VkPayload?): Int? {
        val section = payload?.section ?: return null
        val direct = section.optInt("totalCount", 0).takeIf { it > 0 }
            ?: section.optInt("total_count", 0).takeIf { it > 0 }
            ?: section.optJSONObject("playlist")?.optInt("totalCount", 0)?.takeIf { it > 0 }
        return direct
    }

    // ------------------------------------------------------------------ playlists

    /**
     * The playlists owned by [userId].
     *
     * `act=owner_playlists` answers `payload[1] = [playlists, totalCount]`, so the playlists are the
     * elements of `payload[1][0]` — each one either a positional array or an object, both of which
     * `getPlaylistInfo` accepts in MeridiusCore. Reading `payload[1][0][0]` instead (as KittyTune
     * did) walks one level too deep and yields nothing.
     */
    suspend fun getPlaylists(
        userId: Long = tokenManager.userId,
        offset: Int = 0
    ): List<VkPlaylist> = withContext(Dispatchers.IO) {
        val playlists = mutableListOf<VkPlaylist>()

        val ownerPayload = request(
            mapOf(
                "access_hash" to "",
                "act" to "owner_playlists",
                "al" to "1",
                "is_attach" to "0",
                "offset" to offset.toString(),
                "owner_id" to userId.toString(),
                "isPlaylist" to "true"
            )
        )
        ownerPayload?.firstArray?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optJSONArray(i)?.let { raw -> VkPlaylist.fromJsonArray(raw, userId)?.let(playlists::add) }
                    ?: arr.optJSONObject(i)?.let { obj -> VkPlaylist.fromJsonObject(obj, userId)?.let(playlists::add) }
            }
        }
        if (playlists.isNotEmpty()) return@withContext playlists

        val payload = getSection("playlists", userId) ?: return@withContext emptyList()
        val block = payload.payload?.optJSONArray(1) ?: return@withContext emptyList()
        for (i in 0 until block.length()) {
            val secObj = block.optJSONObject(i) ?: continue
            val arr = secObj.optJSONArray("playlists")
                ?: secObj.optJSONObject("playlists")?.optJSONArray("list")
                ?: secObj.optJSONArray("list")
                ?: continue
            for (j in 0 until arr.length()) {
                arr.optJSONObject(j)?.let { obj ->
                    VkPlaylist.fromJsonObject(obj, userId)?.let(playlists::add)
                }
            }
        }
        playlists
    }

    /**
     * Every track of a playlist.
     *
     * Port of `PlaylistsRequest.getPlaylist`: `is_loading_all=1` makes VK return the whole playlist
     * in one response, `from_id` is the *session* user (not the owner), and `context` is empty when an
     * access hash is supplied. KittyTune sent the owner as `from_id` and then paged with offsets,
     * which both confused VK and duplicated rows.
     */
    suspend fun getPlaylistAudios(
        ownerId: Long,
        playlistId: Long,
        accessHash: String = ""
    ): List<VkAudioItem> = withContext(Dispatchers.IO) {
        val contextVal = when {
            accessHash.isNotBlank() -> ""
            ownerId < 0 -> "group_list"
            ownerId == tokenManager.userId -> "my"
            else -> "user_playlists"
        }

        val payload = request(
            mapOf(
                "act" to "load_section",
                "access_hash" to accessHash,
                "al" to "1",
                "claim" to "0",
                "context" to contextVal,
                "from_id" to tokenManager.userId.toString(),
                "is_loading_all" to "1",
                "is_preload" to "0",
                "offset" to "0",
                "owner_id" to ownerId.toString(),
                "playlist_id" to playlistId.toString(),
                "type" to "playlist"
            )
        ) ?: return@withContext emptyList()

        normalize(rawAudios(payload))
    }

    // ------------------------------------------------------------------ search

    /**
     * Audio search.
     *
     * VK returns one page (20 items when authenticated through the browser flow, 50 through the
     * app flow) and a `next_from` cursor. Following that cursor is what removes the 20/50 result
     * ceilings the testers hit. Because callers page with a plain offset, the cursor for a query is
     * remembered here.
     */
    suspend fun searchAudios(
        query: String,
        offset: Int = 0,
        count: Int = DEFAULT_SEARCH_COUNT
    ): VkSearchResult = withContext(Dispatchers.IO) {
        val ownerId = tokenManager.userId
        val cacheKey = query.trim().lowercase()
        val cursor = searchCursors[cacheKey]?.takeIf { offset > 0 && it.isValid }

        val first = (if (cursor != null) loadCatalogSection(cursor.sectionId, cursor.nextFrom) else null)
            ?: getSection("search", ownerId, mapOf("q" to query))

        val (raw, more) = collectCatalog(first, count)
        val items = normalize(raw).toMutableList()

        if (more != null && more.isValid) searchCursors[cacheKey] = more else searchCursors.remove(cacheKey)

        // The official Android endpoint surfaces licensed tracks the web catalogue hides, so its
        // results are merged in (deduplicated) rather than used as a fallback.
        val token = validToken()
        if (!token.isNullOrBlank()) {
            for (extra in restAudioSearch(query, token, offset, count)) {
                if (items.none { it.id == extra.id && it.ownerId == extra.ownerId }) items.add(extra)
            }
        }

        if (items.isEmpty()) {
            Log.w(
                TAG,
                "VK search returned nothing for \"$query\" " +
                        "(session=${cookieJar.hasSession()}, userId=$ownerId, token=${!token.isNullOrBlank()})"
            )
        }

        VkSearchResult(
            tracks = items.map { it.toTrack() },
            playlists = parsePlaylists(first, ownerId),
            moreOffset = more?.nextFrom,
            more = more
        )
    }

    private fun parsePlaylists(payload: VkPayload?, ownerId: Long): List<VkPlaylist> {
        val arr = payload?.section?.optJSONArray("playlists") ?: return emptyList()
        val out = mutableListOf<VkPlaylist>()
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { obj -> VkPlaylist.fromJsonObject(obj, ownerId)?.let(out::add) }
        }
        return out
    }

    // ------------------------------------------------------------------ artists

    /**
     * Full artist page: name, cover, follow state and the whole discography.
     *
     * Port of `Artists.get` — load `/artist/<slug>`, read its `sectionId`, then page the catalogue.
     */
    suspend fun getArtist(
        artistName: String,
        maxTracks: Int = MAX_ARTIST_TRACKS
    ): VkArtistPage? = withContext(Dispatchers.IO) {
        val slug = artistSlug(artistName)
        val page = fetchPage("/artist/$slug") ?: return@withContext null
        val sectionId = VkMore.sectionIdOf(page)

        val first = if (sectionId != null) loadCatalogSection(sectionId) else null
        val (raw, more) = collectCatalog(first, maxTracks)

        // Sessions VK serves without a catalogue `sectionId` still get the first tracks rendered
        // straight into the page, so the rows are scraped from it rather than showing an empty artist.
        val tuples = raw.ifEmpty { VkHtmlAudio.parse(page) }
        val items = normalize(tuples)

        VkArtistPage(
            slug = slug,
            name = artistNameFrom(page) ?: artistName,
            coverUrl = artistCoverFrom(page),
            tracks = items,
            more = more
        )
    }

    /** Kept for the existing call sites; falls back to a plain search when the slug does not exist. */
    suspend fun getArtistAudios(artistName: String): VkSearchResult {
        val artist = getArtist(artistName)
        if (artist != null && artist.tracks.isNotEmpty()) {
            return VkSearchResult(
                tracks = artist.tracks.map { it.toTrack() },
                more = artist.more
            )
        }
        return searchAudios(artistName)
    }

    /** VK slugs are lowercase and space-separated words are kept as-is inside the URL path. */
    private fun artistSlug(name: String): String = name.trim()
        .removePrefix("vk:artist:")
        .lowercase()
        .let { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    private fun artistNameFrom(html: String): String? {
        val block = Regex(
            "MusicAuthor_block__title[^>]*>(.*?)<",
            RegexOption.DOT_MATCHES_ALL
        ).find(html)?.groupValues?.getOrNull(1)
        return block?.let { VkAudioItem.unescapeHtml(it.trim()) }?.takeIf { it.isNotBlank() }
    }

    private fun artistCoverFrom(html: String): String? {
        val cover = Regex(
            "MusicAuthor_block__cover[^\"]*\"[^>]*style=\"(.*?)\"",
            RegexOption.DOT_MATCHES_ALL
        ).find(html)?.groupValues?.getOrNull(1)
        return VkHtmlAudio.backgroundImage(cover)
    }

    // ------------------------------------------------------------------ feeds

    suspend fun getRecommendations(
        userId: Long = tokenManager.userId,
        count: Int = DEFAULT_PAGE
    ): List<VkAudioItem> = withContext(Dispatchers.IO) {
        val (raw, _) = collectCatalog(getSection("recoms", userId), count)
        normalize(raw)
    }

    suspend fun getExplore(
        userId: Long = tokenManager.userId,
        count: Int = DEFAULT_PAGE
    ): List<VkAudioItem> = withContext(Dispatchers.IO) {
        val (raw, _) = collectCatalog(getSection("explore", userId), count)
        normalize(raw)
    }

    // ------------------------------------------------------------------ single track

    /**
     * Refreshes one track so it gets a playable URL.
     *
     * [actionHash] and [urlHash] are hash components 2 and 5 of the audio tuple; without them VK
     * answers `no_audios`.
     */
    suspend fun reloadAudio(
        ownerId: Long,
        audioId: Long,
        actionHash: String = "",
        urlHash: String = ""
    ): VkAudioItem? = withContext(Dispatchers.IO) {
        val candidates = buildList {
            if (urlHash.isNotBlank()) add("${ownerId}_${audioId}_${actionHash}_$urlHash")
            add("${ownerId}_$audioId")
        }

        for (id in candidates) {
            val items = reloadAudios(listOf(id))
            val match = items.firstOrNull {
                it.optLong(VkIndex.ID) == audioId && it.optLong(VkIndex.OWNER_ID) == ownerId
            } ?: items.firstOrNull()
            val item = match?.let { VkAudioItem.fromJsonArray(it, tokenManager.userId) }
            if (item != null && item.url.isNotBlank()) return@withContext item
        }
        null
    }

    // ------------------------------------------------------------------ mutations

    /**
     * Saves a track to the signed-in user's music. Port of `AudioRequests.add`.
     *
     * `from`, `group_id` and `track_code` are part of the request MeridiusCore sends; VK is picky
     * about the shape of the audio mutations.
     */
    suspend fun addAudio(
        audioId: Long,
        ownerId: Long,
        hash: String,
        trackCode: String = ""
    ): Boolean = request(
        mapOf(
            "act" to "add",
            "al" to "1",
            "audio_id" to audioId.toString(),
            "audio_owner_id" to ownerId.toString(),
            "from" to "user_list",
            "group_id" to (if (ownerId < 0) ownerId else 0L).toString(),
            "hash" to hash,
            "track_code" to trackCode
        )
    ) != null

    /**
     * Removes a track from the signed-in user's music. Port of `AudioRequests.delete`.
     *
     * Note the parameter names: VK wants `aid`/`oid` here, not `audio_id`/`audio_owner_id`, so the
     * previous request was silently rejected.
     */
    suspend fun deleteAudio(
        audioId: Long,
        ownerId: Long,
        hash: String,
        trackCode: String = ""
    ): Boolean = request(
        mapOf(
            "act" to "delete_audio",
            "aid" to audioId.toString(),
            "al" to "1",
            "hash" to hash,
            "oid" to ownerId.toString(),
            "restore" to "1",
            "track_code" to trackCode
        )
    ) != null

    /**
     * Lyrics for a track.
     *
     * MeridiusCore asks for `act=get_lyrics&aid=<owner_id>_<audio_id>` — no separate lyrics id and no
     * hash — and reads the result out of `payload[1][0]`, which holds either timestamped lines or a
     * plain list of lines.
     */
    suspend fun getLyrics(ownerId: Long, audioId: Long): String? {
        val payload = request(
            mapOf(
                "act" to "get_lyrics",
                "aid" to "${ownerId}_$audioId",
                "al" to "1"
            )
        ) ?: return null

        val block = payload.payload?.optJSONArray(1)?.optJSONObject(0) ?: return null
        val lyrics = block.optJSONObject("lyrics") ?: return null

        lyrics.optJSONArray("timestamps")?.let { timestamps ->
            val lines = (0 until timestamps.length()).mapNotNull { i ->
                timestamps.optJSONObject(i)?.optString("line")?.takeIf { it.isNotBlank() }
            }
            if (lines.isNotEmpty()) return lines.joinToString("\n") { VkAudioItem.unescapeHtml(it) }
        }

        for (key in listOf("text", "ugc")) {
            lyrics.optJSONArray(key)?.let { arr ->
                val lines = (0 until arr.length()).map { arr.optString(it) }
                    .filter { it.isNotBlank() }
                if (lines.isNotEmpty()) {
                    return lines.joinToString("\n") { VkAudioItem.unescapeHtml(it) }
                }
            }
        }
        return null
    }

    // ------------------------------------------------------------------ official API

    /**
     * Returns a usable `access_token`, re-minting it when the cached one is stale.
     *
     * VK web tokens are short-lived *and* tied to the IP they were issued for, which is exactly why
     * search died about fifteen minutes after login and again whenever a tester restarted their VPN.
     */
    private suspend fun validToken(): String? {
        val cached = tokenManager.accessToken
        if (!cached.isNullOrBlank() && !tokenManager.isTokenStale()) return cached
        val fresh = obtainWebToken()
        return fresh?.takeIf { it.isNotBlank() } ?: cached
    }

    /** Calls `api.vk.ru/method/<method>`, re-authorising once if VK rejects the token. */
    private suspend fun restCall(
        method: String,
        params: Map<String, String>,
        allowRetry: Boolean = true
    ): JSONObject? = withContext(Dispatchers.IO) {
        val token = validToken() ?: return@withContext null

        for (host in VkEndpoints.API_HOSTS) {
            val builder = ("$host/method/$method").toHttpUrlOrNull()?.newBuilder() ?: continue
            params.forEach { (k, v) -> builder.addQueryParameter(k, v) }
            builder.addQueryParameter("access_token", token)
            builder.addQueryParameter("v", VkEndpoints.API_VERSION)

            try {
                val request = Request.Builder()
                    .url(builder.build())
                    .header("User-Agent", VkEndpoints.ANDROID_USER_AGENT)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val json = JSONObject(response.body.string())
                    val error = json.optJSONObject("error")
                    if (error != null) {
                        val code = error.optInt("error_code")
                        Log.d(TAG, "$method failed with VK error $code: ${error.optString("error_msg")}")
                        if (code in AUTH_ERROR_CODES) {
                            if (allowRetry) {
                                tokenManager.invalidateToken()
                                return@withContext restCall(method, params, allowRetry = false)
                            }
                            // The retry failed too: the token is dead and cannot be re-minted, so
                            // stop carrying it around and paying for it on every single search.
                            Log.i(TAG, "Dropping a token VK keeps rejecting (error $code)")
                            tokenManager.accessToken = null
                        }
                        return@use
                    }
                    return@withContext json
                }
            } catch (e: Exception) {
                Log.d(TAG, "$method on $host failed: ${e.message}")
            }
        }
        null
    }

    private suspend fun restAudioSearch(
        query: String,
        @Suppress("UNUSED_PARAMETER") token: String,
        offset: Int,
        count: Int
    ): List<VkAudioItem> {
        val json = restCall(
            "audio.search",
            mapOf("q" to query, "count" to count.toString(), "offset" to offset.toString())
        ) ?: return emptyList()
        return parseRestItems(json)
    }

    private suspend fun restAudioGet(ownerId: Long, offset: Int, count: Int): List<VkAudioItem> {
        val json = restCall(
            "audio.get",
            mapOf(
                "owner_id" to ownerId.toString(),
                "count" to count.toString(),
                "offset" to offset.toString()
            )
        ) ?: return emptyList()
        return parseRestItems(json)
    }

    private fun parseRestItems(json: JSONObject): List<VkAudioItem> {
        val items = json.optJSONObject("response")?.optJSONArray("items")
            ?: json.optJSONArray("response")
            ?: return emptyList()
        val out = mutableListOf<VkAudioItem>()
        for (i in 0 until items.length()) {
            items.optJSONObject(i)?.let { obj ->
                VkAudioItem.fromJsonObject(obj, tokenManager.userId)?.let(out::add)
            }
        }
        return out
    }

    /**
     * Official-API lookup for a single track.
     *
     * Used as a safety net for tracks saved before the hash bundle was persisted: without
     * `action_hash`/`url_hash` the web `reload_audios` call cannot succeed, but `audio.getById`
     * only needs the `owner_id_audio_id` pair.
     */
    suspend fun getAudioById(ownerId: Long, audioId: Long): VkAudioItem? {
        val json = restCall("audio.getById", mapOf("audios" to "${ownerId}_$audioId")) ?: return null
        return parseRestItems(json).firstOrNull()
    }

    // ------------------------------------------------------------------ profile / auth

    suspend fun fetchUserProfile(userId: Long): VkUser? = withContext(Dispatchers.IO) {
        val json = restCall(
            "users.get",
            mapOf("user_ids" to userId.toString(), "fields" to "photo_max,screen_name,status")
        )
        val userObj = json?.optJSONArray("response")?.optJSONObject(0)
        if (userObj != null) {
            val first = userObj.optString("first_name", "")
            val last = userObj.optString("last_name", "")
            if (VkTokenManager.isValidDisplayName(first) || VkTokenManager.isValidDisplayName(last)) {
                return@withContext VkUser(
                    id = userObj.optLong("id", userId),
                    firstName = first,
                    lastName = last,
                    screenName = userObj.optString("screen_name")
                        .takeIf { VkTokenManager.isValidDisplayName(it) },
                    photoMax = userObj.optString("photo_max").takeIf { it.startsWith("http") },
                    status = userObj.optString("status")
                )
            }
        }
        scrapeUserProfile(userId)
    }

    /** Last-resort profile read from the HTML page, for accounts the API refuses to describe. */
    private fun scrapeUserProfile(userId: Long): VkUser? {
        val html = webCall(params = null, ajax = false) { host -> "$host/id$userId" } ?: return null

        val ogTitle = Regex("""<meta\s+property=["']og:title["']\s+content=["'](.*?)["']""",
            RegexOption.IGNORE_CASE).find(html)?.groupValues?.getOrNull(1)?.trim()
        val ogImage = Regex("""<meta\s+property=["']og:image["']\s+content=["'](.*?)["']""",
            RegexOption.IGNORE_CASE).find(html)?.groupValues?.getOrNull(1)?.trim()

        var fullName = ogTitle
        if (!VkTokenManager.isValidDisplayName(fullName)) {
            fullName = Regex("""<h[1-3][^>]*class=["'][^"']*page_name[^"']*["'][^>]*>(.*?)</h[1-3]>""",
                RegexOption.IGNORE_CASE).find(html)?.groupValues?.getOrNull(1)?.trim()
        }
        if (!VkTokenManager.isValidDisplayName(fullName)) {
            fullName = Regex("""<title>(.*?)</title>""", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.getOrNull(1)
                ?.replace("| ВКонтакте", "")?.replace("| VK", "")?.trim()
        }
        if (!VkTokenManager.isValidDisplayName(fullName)) return null

        val parts = VkAudioItem.unescapeHtml(fullName!!).split(" ")
        return VkUser(
            id = userId,
            firstName = parts.firstOrNull().orEmpty(),
            lastName = parts.drop(1).joinToString(" "),
            photoMax = ogImage?.takeIf { it.startsWith("http") }
        )
    }

    /**
     * Mints an API token from the web session cookies.
     *
     * This is a *bonus* path, not a requirement: MeridiusCore exposes `webToken` but never calls it,
     * and drives the whole audio catalogue from `al_audio.php` + cookies alone. KittyTune only uses
     * the token to supplement search with the official `audio.search`, so a failure here must never
     * degrade anything else.
     *
     * MeridiusCore's shape is a POST with the parameters in the body; a GET with them in the query
     * also works on some sessions, so both are tried.
     */
    suspend fun obtainWebToken(): String? = withContext(Dispatchers.IO) {
        if (!cookieJar.hasSession()) return@withContext null

        // Sessions VK refuses here are refused persistently. Hammering the endpoint on every search
        // only wasted requests and risked throttling the whole session.
        val sinceFailure = System.currentTimeMillis() - lastWebTokenFailure
        if (lastWebTokenFailure != 0L && sinceFailure < WEB_TOKEN_COOLDOWN_MS) {
            Log.d(TAG, "Skipping web_token, last attempt failed ${sinceFailure / 1000}s ago")
            return@withContext null
        }

        val params = mapOf(
            "app_id" to VkEndpoints.WEB_TOKEN_APP_ID.toString(),
            "v" to VkEndpoints.API_VERSION,
            "version" to "1"
        )

        for (host in VkEndpoints.LOGIN_HOSTS) {
            for (asPost in listOf(true, false)) {
                val url = if (asPost) {
                    "$host/?act=web_token"
                } else {
                    "$host/?act=web_token&" + params.entries.joinToString("&") { "${it.key}=${it.value}" }
                }
                try {
                    val builder = Request.Builder()
                        .url(url)
                        .header("User-Agent", VkEndpoints.BROWSER_USER_AGENT)
                        .header("Origin", VkEndpoints.ID_HOSTS.first())
                        .header("Referer", VkEndpoints.ID_HOSTS.first() + "/")
                        .header("Accept", "*/*")

                    if (asPost) {
                        val form = FormBody.Builder()
                        params.forEach { (k, v) -> form.add(k, v) }
                        builder.post(form.build())
                    }

                    client.newCall(builder.build()).execute().use { response ->
                        if (!response.isSuccessful) return@use
                        val data = JSONObject(response.body.string()).optJSONObject("data") ?: return@use
                        val token = data.optString("access_token")
                        val uid = data.optLong("user_id", 0L)
                        if (uid > 0L) tokenManager.userId = uid
                        if (token.isNotBlank()) {
                            tokenManager.saveToken(token)
                            lastWebTokenFailure = 0L
                            return@withContext token
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "web_token ${if (asPost) "POST" else "GET"} on $host failed: ${e.message}")
                }
            }
        }
        lastWebTokenFailure = System.currentTimeMillis()
        null
    }

    // ------------------------------------------------------------------ diagnostics

    /**
     * Step-by-step connectivity report for the VK account screen.
     *
     * Debugging VK from outside Russia is guesswork, so this runs the exact calls search depends on
     * and says which host answered, with what status, and how many tracks came back. A tester can
     * paste the result into an issue instead of us shipping another blind build.
     */
    suspend fun selfTest(query: String = "test"): String = withContext(Dispatchers.IO) {
        val report = StringBuilder()

        fun line(text: String) = report.append(text).append('\n')

        line("VK diagnostics")
        line("user id: ${tokenManager.userId}")
        line("session cookies: ${if (cookieJar.hasSession()) "yes" else "NO"}")
        val header = cookieJar.cookieHeader(VkEndpoints.WEB_HOSTS.first() + "/")
        line("cookies sent to vk.ru: ${header.split("; ").mapNotNull { it.substringBefore('=').takeIf { n -> n.isNotBlank() } }}")
        val token = tokenManager.accessToken
        line("token: ${if (token.isNullOrBlank()) "none" else "present"}${if (tokenManager.isTokenStale()) " (stale)" else ""}")

        // A diagnostic must measure VK, not our own back-off state.
        lastWebTokenFailure = 0L

        // Ask for more than one VK page so the number also proves pagination works.
        line("search (clean session): ${searchAudios(query, count = DIAGNOSTIC_COUNT).tracks.size} tracks")

        for (host in VkEndpoints.WEB_HOSTS) {
            val url = "$host/al_audio.php"
            val form = FormBody.Builder()
                .add("act", "section").add("al", "1").add("claim", "0")
                .add("is_layer", "0").add("owner_id", tokenManager.userId.toString())
                .add("section", "search").add("q", query)
                .build()
            try {
                val request = Request.Builder()
                    .url(url)
                    .post(form)
                    .header("User-Agent", VkEndpoints.BROWSER_USER_AGENT)
                    .header("Accept", "*/*")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Origin", host)
                    .header("Referer", "$host/")
                    .apply {
                        if (cookieJar.cookieHeader(url).isBlank()) {
                            val legacy = tokenManager.getCookieHeader(url)
                            if (legacy.isNotBlank()) header("Cookie", legacy)
                        }
                    }
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body.string()
                    val payload = VkPayload.parse(body)
                    val rows = payload?.let { rawAudios(it).size } ?: 0
                    line(
                        "$host -> HTTP ${response.code}, ${body.length} bytes, " +
                                "envelope=${if (payload?.payload != null) "ok" else "no"}, rows=$rows"
                    )
                }
            } catch (e: Exception) {
                line("$host -> failed: ${e.javaClass.simpleName}: ${e.message}")
            }
        }

        // Each attempt is reported separately: "failed" on its own could not tell a refusal by VK
        // apart from our own back-off skipping the call.
        line("web_token attempts:")
        for (host in VkEndpoints.LOGIN_HOSTS) {
            for (asPost in listOf(true, false)) {
                val method = if (asPost) "POST" else "GET"
                val url = if (asPost) {
                    "$host/?act=web_token"
                } else {
                    "$host/?act=web_token&app_id=${VkEndpoints.WEB_TOKEN_APP_ID}" +
                            "&v=${VkEndpoints.API_VERSION}&version=1"
                }
                try {
                    val builder = Request.Builder()
                        .url(url)
                        .header("User-Agent", VkEndpoints.BROWSER_USER_AGENT)
                        .header("Origin", VkEndpoints.ID_HOSTS.first())
                        .header("Referer", VkEndpoints.ID_HOSTS.first() + "/")
                        .header("Accept", "*/*")
                    if (asPost) {
                        builder.post(
                            FormBody.Builder()
                                .add("app_id", VkEndpoints.WEB_TOKEN_APP_ID.toString())
                                .add("v", VkEndpoints.API_VERSION)
                                .add("version", "1")
                                .build()
                        )
                    }
                    client.newCall(builder.build()).execute().use { response ->
                        val body = response.body.string()
                        val data = runCatching { JSONObject(body).optJSONObject("data") }.getOrNull()
                        val hasToken = !data?.optString("access_token").isNullOrBlank()
                        val error = runCatching {
                            JSONObject(body).optJSONObject("error")?.optString("error_msg")
                        }.getOrNull()
                        line(
                            "  $host $method -> HTTP ${response.code}, token=${if (hasToken) "yes" else "no"}" +
                                    (error?.takeIf { it.isNotBlank() }?.let { ", error=$it" } ?: "")
                        )
                    }
                } catch (e: Exception) {
                    line("  $host $method -> ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }

        // Why search stops at one page for some sessions: report the cursor VK actually sent.
        val searchPayload = getSection("search", tokenManager.userId, mapOf("q" to query))
        val section = searchPayload?.section
        if (section == null) {
            line("search cursor: no section object at payload[1][1]")
        } else {
            line("search section keys: ${section.keys().asSequence().toList().sorted()}")
            val playlist = section.optJSONObject("playlist")
            if (playlist != null) {
                line("  playlist keys: ${playlist.keys().asSequence().toList().sorted()}")
            }
            val more = section.optJSONObject("more")
            if (more != null) {
                line("  more keys: ${more.keys().asSequence().toList().sorted()}")
            }
            val cursor = VkMore.from(searchPayload)
            line("  cursor parsed: ${cursor?.let { "sectionId=${it.sectionId} next=${it.nextFrom}" } ?: "none"}")
        }

        // Artist pages: check whether this session gets one with a catalogue section at all.
        val probeSlug = searchAudios(query, count = 5).tracks
            .firstOrNull()?.user?.permalink?.takeIf { it.isNotBlank() }
        if (probeSlug != null) {
            val page = fetchPage("/artist/$probeSlug")
            line(
                "artist page /artist/$probeSlug -> ${page?.length ?: 0} bytes, " +
                        "sectionId=${VkMore.sectionIdOf(page) ?: "none"}, " +
                        "rows in page=${VkHtmlAudio.parse(page).size}"
            )
        }

        lastWebTokenFailure = 0L
        line("web_token: ${if (obtainWebToken() != null) "ok" else "failed"}")

        val restCount = restCall("audio.search", mapOf("q" to query, "count" to "5"))
            ?.let { parseRestItems(it).size }
        line("audio.search (official API): ${restCount?.toString() ?: "failed"}")

        // Same call as the first one. A drop here means one of the probes above invalidated the
        // session rather than the catalogue being unavailable.
        line("search (after probes): ${searchAudios(query, count = DIAGNOSTIC_COUNT).tracks.size} tracks")
        line("session cookies still present: ${if (cookieJar.hasSession()) "yes" else "NO"}")

        report.toString().trimEnd()
    }

    companion object {
        private const val TAG = "VkApi"
        /** VK pages the audio catalogue 50 items at a time. */
        const val DEFAULT_PAGE = 100
        const val DEFAULT_SEARCH_COUNT = 50
        const val MAX_ARTIST_TRACKS = 400

        private const val MAX_PAGES = 20
        private const val RELOAD_CHUNK = 25
        private const val RELOAD_ATTEMPTS = 4
        private const val RELOAD_RETRY_DELAY_MS = 350L

        /** VK auth failures that a fresh `web_token` can fix. */
        private val AUTH_ERROR_CODES = setOf(3, 5, 15, 17, 28)

        /** How long a failed `web_token` attempt suppresses further attempts. */
        private const val WEB_TOKEN_COOLDOWN_MS = 5L * 60 * 1000

        /** Crosses at least one VK page boundary, so the diagnostic also exercises paging. */
        private const val DIAGNOSTIC_COUNT = 60

        @Volatile
        private var lastWebTokenFailure = 0L

        private val searchCursors = java.util.concurrent.ConcurrentHashMap<String, VkMore>()

        const val BROWSER_USER_AGENT = VkEndpoints.BROWSER_USER_AGENT
        const val VK_ANDROID_USER_AGENT = VkEndpoints.ANDROID_USER_AGENT
    }
}
