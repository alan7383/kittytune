package com.alananasss.kittytune.data.lyrics

import com.alananasss.kittytune.data.LyricsMatcher
import com.alananasss.kittytune.ui.player.lyrics.LyricLine
import com.alananasss.kittytune.ui.player.lyrics.LyricSinger

/**
 * Who sings which line, read from Genius.
 *
 * The desktop app's copy of this file, kept identical on purpose: the two apps should agree
 * about who sings what, or the same song turns into a duet on one screen and a solo on the other.
 *
 * Genius heads every part of a song with who sings it: "[Куплет 1: 9mice]", "[Припев: Kai Angel & 9mice]".
 * When a part is shared, the header also says who sings which line through formatting: in
 * "[Куплет 1: Kai Angel, *9mice* & **Gabbriette**]" the plain lines are Kai Angel's, the italic ones 9mice's and
 * the bold ones Gabbriette's. Read as plain text that was lost, every shared part was drawn as both singing in
 * the middle, and a verse the two trade line by line jumped between the middle and one side (issue #66). The
 * page is read as HTML for that reason, and each line gets the singers its own formatting names.
 *
 * Synced lyrics are then lined up against the page as a whole sequence, so a chorus matches its own repeat and
 * a pre-chorus two singers share word for word goes to whoever sings it at that point of the song.
 */
object GeniusVoices {

    /** One line of a Genius page and who sings it; empty when the page does not say. */
    data class PageLine(val text: String, val singers: List<String>)

    /**
     * The voice for each of [lines], in order, or null when Genius says nothing usable about who sings.
     *
     * Two main voices give a duet: the first artist the track credits (else the first to sing) on the left for the
     * whole song, the second on the right, both together in the middle. Anyone else, a few words in an intro or an
     * outro, is in the middle too. One voice throughout gives no duet: every line [LyricSinger.DEFAULT].
     *
     * @param creditedArtists the track's artists in their credited order, for which voice goes left.
     */
    fun voicesFor(lines: List<LyricLine>, page: List<PageLine>, creditedArtists: List<String>): List<LyricSinger>? {
        if (lines.isEmpty() || page.none { it.singers.isNotEmpty() }) return null
        val people = mainSingers(page, creditedArtists)
        if (people.size < 2) return List(lines.size) { LyricSinger.DEFAULT }
        val first = key(people[0])
        val second = key(people[1])

        val pageIndexFor = alignToPage(lines, page)
        val voices = lines.mapIndexed { i, line ->
            if (line.isInstrumental || line.text.isBlank()) return@mapIndexed LyricSinger.DEFAULT
            val singers = pageIndexFor[i]?.let { page[it].singers }.orEmpty().map(::key)
            voiceOf(singers, first, second)
        }
        return voices.takeIf { it.any { v -> v == LyricSinger.SINGER_1 } && it.any { v -> v == LyricSinger.SINGER_2 } }
            ?: voices.takeIf { it.none { v -> v == LyricSinger.SINGER_2 || v == LyricSinger.BOTH } }?.map { LyricSinger.DEFAULT }
    }

    /** A plain-text page: who sings comes from the headers alone. */
    fun parsePlain(page: String): List<PageLine> =
        parseLines(page.lines().map { listOf(Segment(it, Style.PLAIN)) })

    /** A page as Genius's `text_format=html` gives it, with the formatting that tells shared lines apart. */
    fun parseHtml(html: String): List<PageLine> = parseLines(GeniusMarkup.styledLines(html))

    fun splitNames(credit: String): List<String> =
        credit.split(NAME_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }

    private fun parseLines(styledLines: List<List<Segment>>): List<PageLine> {
        var header = emptyList<NamedSinger>()
        val result = mutableListOf<PageLine>()
        for (segments in styledLines) {
            val text = segments.joinToString("") { it.text }.trim()
            if (text.isEmpty()) continue
            if (HEADER.matchEntire(text) != null) {
                header = headerSingers(segments)
                continue
            }
            result += PageLine(text, singersOf(segments, header))
        }
        return result
    }

    /** The names after a header's colon, each with the formatting it is written in. */
    private fun headerSingers(segments: List<Segment>): List<NamedSinger> {
        val chars = segments.flatMap { s -> s.text.map { it to s.style } }
        val text = chars.joinToString("") { it.first.toString() }
        val colon = text.indexOf(':').takeIf { it >= 0 } ?: return emptyList()
        val end = text.lastIndexOf(']').takeIf { it > colon } ?: text.length
        val names = mutableListOf<NamedSinger>()
        var start = colon + 1
        fun addName(from: Int, to: Int) {
            val raw = text.substring(from, to)
            val name = raw.trim()
            if (name.isEmpty()) return
            val lead = from + raw.indexOf(name)
            val style = dominantStyle(chars.subList(lead, lead + name.length).map { it.second }) ?: Style.PLAIN
            names += NamedSinger(name, style)
        }
        for (match in NAME_SEPARATOR.findAll(text.substring(0, end), colon + 1)) {
            addName(start, match.range.first)
            start = match.range.last + 1
        }
        addName(start, end)
        return names
    }

    /**
     * Who sings a line under [header]: when the header gives its names different formatting, the names written
     * like the line itself (its parentheses, the ad-libs, aside); otherwise everyone the header names.
     */
    private fun singersOf(segments: List<Segment>, header: List<NamedSinger>): List<String> {
        if (header.size < 2 || header.map { it.style }.distinct().size < 2) return header.map { it.name }
        val style = lineStyle(segments) ?: return header.map { it.name }
        val exact = header.filter { it.style == style }
        if (exact.isNotEmpty()) return exact.map { it.name }
        // Bold italic is two people at once.
        if (style == Style.BOLD_ITALIC) {
            val both = header.filter { it.style == Style.BOLD || it.style == Style.ITALIC }
            if (both.isNotEmpty()) return both.map { it.name }
        }
        return header.map { it.name }
    }

    /** The formatting most of a line is written in, not counting what it puts in parentheses. */
    private fun lineStyle(segments: List<Segment>): Style? {
        val outside = mutableListOf<Style>()
        val all = mutableListOf<Style>()
        var depth = 0
        for (segment in segments) for (c in segment.text) {
            when (c) {
                '(' -> depth++
                ')' -> depth = (depth - 1).coerceAtLeast(0)
                else -> if (c.isLetterOrDigit()) {
                    all += segment.style
                    if (depth == 0) outside += segment.style
                }
            }
        }
        return dominantStyle(outside) ?: dominantStyle(all)
    }

    private fun dominantStyle(styles: List<Style>): Style? =
        styles.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

    /**
     * For each synced line, the page line it is: the best-scoring alignment of the two sequences in order, so a
     * line can only match a page line at or after the previous match. Several synced lines may share one page
     * line, which Genius often writes as one ("Ha, what the fuck? Ha, what the fuck?"), at a small cost so a
     * repeated chorus still matches its own repeat. A synced line with no match takes the page line at the same
     * place between its neighbours' matches.
     */
    private fun alignToPage(lines: List<LyricLine>, page: List<PageLine>): List<Int?> {
        val n = lines.size
        val m = page.size
        val similarity = Array(n) { i ->
            FloatArray(m) { j ->
                if (lines[i].isInstrumental || lines[i].text.isBlank()) 0f
                else maxOf(
                    LyricsMatcher.similarity(lines[i].text, page[j].text),
                    // A synced line that is part of a longer page line.
                    if (containsWords(page[j].text, lines[i].text)) PART_OF_LINE_SIMILARITY else 0f,
                ).takeIf { it >= MATCH_THRESHOLD } ?: 0f
            }
        }
        val best = Array(n + 1) { FloatArray(m + 1) }
        val move = Array(n + 1) { ByteArray(m + 1) }
        for (i in 1..n) for (j in 1..m) {
            val s = similarity[i - 1][j - 1]
            var score = best[i - 1][j]
            var step = SKIP_LINE
            if (best[i][j - 1] > score) { score = best[i][j - 1]; step = SKIP_PAGE }
            if (s > 0f && best[i - 1][j - 1] + s > score) { score = best[i - 1][j - 1] + s; step = MATCH }
            if (s > 0f && move[i - 1][j] == MATCH && best[i - 1][j] + s * SHARED_LINE_WEIGHT > score) {
                score = best[i - 1][j] + s * SHARED_LINE_WEIGHT; step = SHARE
            }
            best[i][j] = score
            move[i][j] = step
        }
        val matches = arrayOfNulls<Int>(n)
        var i = n
        var j = m
        while (i > 0 && j > 0) {
            when (move[i][j]) {
                MATCH -> { matches[i - 1] = j - 1; i--; j-- }
                SHARE -> { matches[i - 1] = j - 1; i-- }
                SKIP_PAGE -> j--
                else -> i--
            }
        }
        return fillGaps(matches.toList(), m)
    }

    /** Whether every word of [part] appears in [whole], in a part long enough to mean something. */
    private fun containsWords(whole: String, part: String): Boolean {
        val partWords = LyricsMatcher.normalize(part).split(' ').filter { it.isNotBlank() }
        if (partWords.size < 2) return false
        val wholeText = " " + LyricsMatcher.normalize(whole) + " "
        return wholeText.contains(" " + partWords.joinToString(" ") + " ")
    }

    private fun fillGaps(matches: List<Int?>, pageSize: Int): List<Int?> {
        val known = matches.indices.filter { matches[it] != null }
        if (known.isEmpty()) return matches
        return matches.indices.map { i ->
            matches[i] ?: run {
                val before = known.lastOrNull { it < i }
                val after = known.firstOrNull { it > i }
                when {
                    before != null && after != null -> {
                        val from = matches[before]!!
                        val to = matches[after]!!
                        from + ((to - from) * (i - before).toFloat() / (after - before)).toInt()
                    }
                    before != null -> (matches[before]!! + (i - before)).coerceAtMost(pageSize - 1)
                    else -> (matches[after!!]!! - (after - i)).coerceAtLeast(0)
                }
            }
        }
    }

    private fun voiceOf(singers: List<String>, first: String, second: String): LyricSinger {
        val hasFirst = first in singers
        val hasSecond = second in singers
        return when {
            hasFirst && hasSecond -> LyricSinger.BOTH
            hasSecond -> LyricSinger.SINGER_2
            hasFirst -> LyricSinger.SINGER_1
            // Someone else, named: a guest voice in an intro or outro, in the middle.
            singers.isNotEmpty() -> LyricSinger.BOTH
            else -> LyricSinger.DEFAULT
        }
    }

    /**
     * The two people the song is by: the track's credited artists that the page names, in their credited order
     * (so the same one is on the left for the whole song), topped up with whoever sings the most lines. Anyone
     * else is a guest and is drawn in the middle.
     */
    private fun mainSingers(page: List<PageLine>, credited: List<String>): List<String> {
        val lineCount = mutableMapOf<String, Int>()
        val spelling = mutableMapOf<String, String>()
        for (line in page) for (name in line.singers) {
            spelling.putIfAbsent(key(name), name)
            lineCount[key(name)] = (lineCount[key(name)] ?: 0) + 1
        }
        val creditedKeys = credited.map(::key).distinct().filter { it in lineCount }
        val byLines = lineCount.entries.sortedByDescending { it.value }.map { it.key }
        return (creditedKeys + byLines).distinct().take(2).map { spelling.getValue(it) }
    }

    private fun key(name: String): String = LyricsMatcher.normalize(name).replace(" ", "")

    private data class NamedSinger(val name: String, val style: Style)

    /** "[Part: names]" or "[Part]", the names after the colon. */
    private val HEADER = Regex("""^\[[^\]:]*(?::\s*([^\]]*))?]$""")

    private val NAME_SEPARATOR = Regex("""\s*(?:&|,|\s+x\s+|\s+и\s+|\s+and\s+|\s*\+\s*|\s+feat\.?\s+|\s+ft\.?\s+|\s+with\s+)\s*""", RegexOption.IGNORE_CASE)

    private const val MATCH_THRESHOLD = 0.5f

    /** How a synced line found inside a longer page line scores. */
    private const val PART_OF_LINE_SIMILARITY = 0.75f

    /** What a second synced line on the same page line is worth, against a page line of its own. */
    private const val SHARED_LINE_WEIGHT = 0.8f

    private const val SKIP_LINE: Byte = 0
    private const val SKIP_PAGE: Byte = 1
    private const val MATCH: Byte = 2
    private const val SHARE: Byte = 3
}

/** How a run of a Genius line is written; on a shared part it says who sings it. */
internal enum class Style { PLAIN, ITALIC, BOLD, BOLD_ITALIC }

internal data class Segment(val text: String, val style: Style)

/** Genius's lyrics HTML as lines of styled runs: links, ads and paragraphs dropped, italics and bold kept. */
internal object GeniusMarkup {

    fun styledLines(html: String): List<List<Segment>> {
        val cleaned = html
            .replace(AD_UNIT, "")
            .replace(Regex("""\r?\n"""), "")
        val lines = mutableListOf<List<Segment>>()
        var current = mutableListOf<Segment>()
        var italic = 0
        var bold = 0
        var at = 0
        fun style() = when {
            italic > 0 && bold > 0 -> Style.BOLD_ITALIC
            italic > 0 -> Style.ITALIC
            bold > 0 -> Style.BOLD
            else -> Style.PLAIN
        }
        fun flushText(until: Int) {
            if (until > at) {
                val text = unescape(cleaned.substring(at, until))
                if (text.isNotEmpty()) current += Segment(text, style())
            }
        }
        fun endLine() {
            lines += current
            current = mutableListOf()
        }
        for (tag in TAG.findAll(cleaned)) {
            flushText(tag.range.first)
            at = tag.range.last + 1
            val closing = tag.groupValues[1] == "/"
            when (tag.groupValues[2].lowercase()) {
                "br" -> endLine()
                "p", "div" -> if (closing) endLine()
                "i", "em" -> italic = (italic + if (closing) -1 else 1).coerceAtLeast(0)
                "b", "strong" -> bold = (bold + if (closing) -1 else 1).coerceAtLeast(0)
            }
        }
        flushText(cleaned.length)
        endLine()
        return lines
    }

    private fun unescape(text: String): String =
        ENTITY.replace(text) { m ->
            val body = m.groupValues[1]
            when {
                body.startsWith("#x", ignoreCase = true) -> body.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
                body.startsWith("#") -> body.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) }
                else -> NAMED_ENTITIES[body]
            } ?: m.value
        }

    private val AD_UNIT = Regex("""<dfp-unit.*?</dfp-unit>""", RegexOption.DOT_MATCHES_ALL)
    private val TAG = Regex("""<(/?)([a-zA-Z][a-zA-Z0-9-]*)[^>]*>""")
    private val ENTITY = Regex("""&(#?[a-zA-Z0-9]+);""")
    private val NAMED_ENTITIES = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ")
}
