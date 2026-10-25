package com.alananasss.kittytune.data.musicimport

import java.net.URLDecoder
import java.text.Normalizer
import java.util.Locale

/**
 * One line of a TXT/CSV export, before it has been matched to a catalogue
 * track. A row may carry a service URL, a title/artist pair, or both.
 */
data class PortableTrackEntry(
    val title: String,
    val artist: String = "",
    val album: String? = null,
    val url: String? = null,
    val isrc: String? = null,
    val durationMs: Long? = null,
    val raw: String = "",
) {
    /** What is sent to each provider's text search. */
    val query: String
        get() = listOf(artist, title)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")

    val hasIdentity: Boolean
        get() = title.isNotBlank() || !url.isNullOrBlank()
}

data class PortableImportFile(
    val name: String,
    val entries: List<PortableTrackEntry>,
)

/**
 * Reads the TXT/CSV files people export from Spotify, Deezer, TIDAL, Qobuz,
 * YouTube Music and SoundCloud.
 *
 * There is no single format: a CSV can be comma, semicolon or tab separated,
 * can put the URL in the first or last column, can call the title "Track name"
 * or "Titre", and a TXT can be `Artist - Title`, a bare list of links, or an
 * M3U playlist. The parser therefore looks for a header first, falls back to
 * column heuristics, and finally treats every line as a track.
 *
 * This object is deliberately free of Android and network dependencies so the
 * formats can be tested without an emulator.
 */
object PortablePlaylistParser {

    private val URL_PATTERN = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
    private val PROVIDER_URI_PATTERN = Regex(
        """(?:spotify|deezer|tidal|qobuz|youtube|yt|soundcloud):[^\s]+""",
        RegexOption.IGNORE_CASE,
    )
    private val TRACK_NUMBER = Regex("""^\s*(?:\[?\d{1,4}\]?[.)\-:]?\s+)""")

    private val TITLE_KEYS = setOf(
        "track name", "track", "track title", "title", "song", "song name",
        "song title", "name", "titre", "nom", "titulo", "titel",
    )
    private val ARTIST_KEYS = setOf(
        "artist name", "artist", "artists", "artist(s)", "artists name",
        "album artist", "album artists", "artiste", "artistes", "interpret",
        "artista", "interprete",
    )
    private val ALBUM_KEYS = setOf(
        "album name", "album", "album title", "release", "release name",
        "release title", "album/playlist", "playlist", "playlist name",
        "playliste", "album du titre",
    )
    private val URL_KEYS = setOf(
        "url", "link", "web url", "share url", "track url", "song url",
        "spotify url", "spotify link", "deezer url", "deezer link",
        "tidal url", "tidal link", "qobuz url", "qobuz link",
        "youtube url", "youtube link", "uri", "track uri", "spotify uri",
        "spotify track uri", "isrc url", "permalink", "permalink url",
    )
    private val ISRC_KEYS = setOf("isrc", "isrc code", "isrc id", "isrc number")
    private val DURATION_KEYS = setOf(
        "duration", "duration (ms)", "duration ms", "duration (s)",
        "duration seconds", "length", "track duration", "time", "duree",
        "durée", "length (ms)", "length ms",
    )

    /**
     * @param fileName used only to decide whether a headerless file is a CSV,
     *   and as the default playlist title.
     */
    fun parse(fileName: String, rawContent: String): PortableImportFile {
        val content = rawContent
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')

        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val lines = content.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.isEmpty()) return PortableImportFile(playlistName(fileName), emptyList())

        val csvLike = extension == "csv" || extension == "tsv" ||
            looksLikeCsv(lines.first())
        val parsed = if (csvLike) parseCsv(content) else parseText(lines)
        val cleaned = parsed
            .mapNotNull(::clean)
            .distinctBy { entry ->
                listOf(entry.title, entry.artist, entry.url.orEmpty())
                    .joinToString("\u0001")
                    .lowercase(Locale.ROOT)
            }
        return PortableImportFile(playlistName(fileName), cleaned)
    }

    /** Builds the name shown in the import preview and used for the playlist. */
    fun playlistName(fileName: String): String {
        val withoutPath = fileName.substringAfterLast('/').substringAfterLast('\\')
        val withoutExtension = withoutPath.substringBeforeLast('.', withoutPath)
        val decoded = runCatching { URLDecoder.decode(withoutExtension, "UTF-8") }
            .getOrDefault(withoutExtension)
        return decoded
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "Imported playlist" }
    }

    private fun looksLikeCsv(firstLine: String): Boolean {
        val lower = normalizeHeader(firstLine)
        val hasHeaderWord = TITLE_KEYS.any { it in lower } ||
            ARTIST_KEYS.any { it in lower } ||
            URL_KEYS.any { it in lower }
        val delimiters = listOf(',', ';', '\t').count { firstLine.contains(it) }
        return hasHeaderWord && delimiters > 0
    }

    // ---------------------------------------------------------------- CSV

    private fun parseCsv(content: String): List<PortableTrackEntry> {
        val delimiter = detectDelimiter(content)
        val rows = parseDelimited(content, delimiter)
        if (rows.isEmpty()) return emptyList()

        val header = rows.first()
        val titleIndex = headerIndexOf(header, TITLE_KEYS)
        val artistIndex = headerIndexOf(header, ARTIST_KEYS)
        val albumIndex = headerIndexOf(header, ALBUM_KEYS)
        val urlIndex = headerIndexOf(header, URL_KEYS)
        val isrcIndex = headerIndexOf(header, ISRC_KEYS)
        val durationIndex = headerIndexOf(header, DURATION_KEYS)
        val hasHeader = titleIndex >= 0 || artistIndex >= 0 || urlIndex >= 0 ||
            isrcIndex >= 0 || durationIndex >= 0

        val dataRows = if (hasHeader) rows.drop(1) else rows
        val inferred = if (hasHeader) {
            InferredColumns(titleIndex, artistIndex, albumIndex, urlIndex, isrcIndex, durationIndex)
        } else {
            inferColumns(rows.first())
        }

        return dataRows.mapNotNull { row ->
            val title = row.cell(inferred.title)
            val artist = row.cell(inferred.artist)
            val album = row.cell(inferred.album).takeIf { it.isNotBlank() }
            val url = row.cell(inferred.url).let(::extractUrl)
            val isrc = row.cell(inferred.isrc).takeIf { it.isNotBlank() }
            val duration = parseDuration(row.cell(inferred.duration))
            if (title.isBlank() && url == null && isrc == null) null
            else PortableTrackEntry(
                title = title.ifBlank { url.orEmpty() },
                artist = artist,
                album = album,
                url = url,
                isrc = isrc,
                durationMs = duration,
                raw = row.joinToString(delimiter.toString()),
            )
        }
    }

    private data class InferredColumns(
        val title: Int = -1,
        val artist: Int = -1,
        val album: Int = -1,
        val url: Int = -1,
        val isrc: Int = -1,
        val duration: Int = -1,
    )

    private fun inferColumns(firstRow: List<String>): InferredColumns {
        val urlIndex = firstRow.indexOfFirst { extractUrl(it) != null }
        val candidates = firstRow.indices.filter { it != urlIndex }
        val titleIndex = candidates.firstOrNull() ?: -1
        val artistIndex = candidates.drop(1).firstOrNull() ?: -1
        val albumIndex = candidates.drop(2).firstOrNull() ?: -1
        return InferredColumns(titleIndex, artistIndex, albumIndex, urlIndex)
    }

    private fun headerIndexOf(header: List<String>, keys: Set<String>): Int =
        header.indexOfFirst { normalizeHeader(it) in keys }

    private fun List<String>.cell(index: Int): String =
        if (index in indices) this[index].trim() else ""

    private fun detectDelimiter(content: String): Char {
        val firstLine = content.lineSequence().firstOrNull().orEmpty()
        var inQuotes = false
        val counts = mutableMapOf(',' to 0, ';' to 0, '\t' to 0)
        firstLine.forEach { char ->
            when {
                char == '"' -> inQuotes = !inQuotes
                !inQuotes && char in counts -> counts[char] = (counts[char] ?: 0) + 1
            }
        }
        return counts.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: ','
    }

    /**
     * A real CSV parser rather than `split(',')`: quoted fields may contain the
     * delimiter, escaped quotes, and newlines (Spotify exports include multiline
     * album names).
     */
    private fun parseDelimited(content: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0

        fun endCell() {
            row.add(cell.toString())
            cell.clear()
        }

        fun endRow() {
            endCell()
            if (row.any { it.isNotBlank() }) rows.add(row)
            row = mutableListOf()
        }

        while (i < content.length) {
            val char = content[i]
            when {
                char == '"' -> {
                    if (quoted && i + 1 < content.length && content[i + 1] == '"') {
                        cell.append('"')
                        i++
                    } else {
                        quoted = !quoted
                    }
                }
                char == delimiter && !quoted -> endCell()
                (char == '\n' || char == '\r') && !quoted -> {
                    if (char == '\r' && i + 1 < content.length && content[i + 1] == '\n') i++
                    endRow()
                }
                else -> cell.append(char)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    // ---------------------------------------------------------------- TXT / M3U

    private fun parseText(lines: List<String>): List<PortableTrackEntry> {
        val entries = mutableListOf<PortableTrackEntry>()
        var pendingDuration: Long? = null
        var pendingTitle: String? = null
        var pendingArtist: String = ""

        fun flushPending(url: String? = null) {
            val title = pendingTitle
            if (title != null || url != null) {
                entries.add(
                    PortableTrackEntry(
                        title = title.orEmpty(),
                        artist = pendingArtist,
                        url = url,
                        durationMs = pendingDuration,
                        raw = title.orEmpty(),
                    )
                )
            }
            pendingDuration = null
            pendingTitle = null
            pendingArtist = ""
        }

        for (line in lines) {
            if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                val payload = line.substringAfter(':')
                pendingDuration = payload.substringBefore(',')
                    .trim()
                    .toLongOrNull()
                    ?.times(1000L)
                val label = payload.substringAfter(',', "").trim()
                if (label.isNotEmpty()) {
                    val parsed = parseLine(label)
                    pendingTitle = parsed.first
                    pendingArtist = parsed.second
                }
                continue
            }
            if (line.startsWith("#")) continue

            val url = extractUrl(line)
            if (url != null && pendingTitle == null) {
                val label = line.replace(url, "")
                    .trim()
                    .trim('-', '–', '—', ':', ',', ';')
                    .trim()
                if (label.isNotBlank()) {
                    val (title, artist) = parseLine(label)
                    entries.add(
                        PortableTrackEntry(
                            title = title.ifBlank { url },
                            artist = artist,
                            url = url,
                            raw = line,
                        )
                    )
                } else {
                    flushPending(url)
                }
                continue
            }
            if (url != null && line == url) {
                flushPending(url)
                continue
            }
            if (pendingTitle != null) {
                // The line after #EXTINF is usually the file path; the label is
                // the useful part. A URL here is still worth keeping.
                entries.add(
                    PortableTrackEntry(
                        title = pendingTitle.orEmpty(),
                        artist = pendingArtist,
                        url = if (url != null) url else null,
                        durationMs = pendingDuration,
                        raw = line,
                    )
                )
                pendingDuration = null
                pendingTitle = null
                pendingArtist = ""
                continue
            }

            val (title, artist) = parseLine(line)
            if (title.isBlank() && url == null) continue
            entries.add(
                PortableTrackEntry(
                    title = title.ifBlank { url.orEmpty() },
                    artist = artist,
                    url = url,
                    raw = line,
                )
            )
        }
        if (pendingTitle != null) flushPending()
        return entries
    }

    /**
     * `Artist - Title` is what most TXT exports use, so the left side is the
     * artist. `Title by Artist` is the other common shape and is handled too.
     */
    private fun parseLine(line: String): Pair<String, String> {
        val clean = line
            .replace(TRACK_NUMBER, "")
            .trim()
            .trim('"')
            .trim()

        val byMatch = Regex("""^(.+?)\s+by\s+(.+)$""", RegexOption.IGNORE_CASE).matchEntire(clean)
        if (byMatch != null) {
            return byMatch.groupValues[1].trim() to byMatch.groupValues[2].trim()
        }

        val parts = clean.split(Regex("""\s+[-–—]\s+"""), limit = 2)
        if (parts.size == 2) {
            val left = parts[0].trim()
            val right = parts[1].trim()
            if (left.isNotEmpty() && right.isNotEmpty()) return right to left
        }
        return clean to ""
    }

    // ---------------------------------------------------------------- values

    private fun clean(entry: PortableTrackEntry): PortableTrackEntry? {
        val title = entry.title
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('"')
        val artist = entry.artist
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('"')
        val url = entry.url?.trim()?.trim('"')?.takeIf { it.isNotEmpty() }
        if (title.isBlank() && url == null && entry.isrc.isNullOrBlank()) return null
        return entry.copy(
            title = title.ifBlank { url.orEmpty() },
            artist = artist,
            album = entry.album?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() },
            url = url,
        )
    }

    private fun extractUrl(raw: String): String? {
        val value = raw.trim().trim('"').trim()
        if (value.isEmpty()) return null
        URL_PATTERN.find(value)?.let { return it.value.trimEnd('.', ',', ')', ']') }
        PROVIDER_URI_PATTERN.find(value)?.let { return it.value.trimEnd('.', ',', ')', ']') }
        return null
    }

    private fun parseDuration(raw: String): Long? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        if (value.contains(':')) {
            val parts = value.split(':').mapNotNull { it.trim().toLongOrNull() }
            if (parts.isEmpty()) return null
            var seconds = 0L
            parts.forEach { seconds = seconds * 60 + it }
            return seconds * 1000L
        }
        val number = value.toLongOrNull() ?: return null
        return if (number > 10_000L) number else number * 1000L
    }

    /** Lowercase, accent-folded, punctuation collapsed to single spaces. */
    internal fun normalizeHeader(raw: String): String = Normalizer
        .normalize(raw.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}
