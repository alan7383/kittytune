package com.alananasss.kittytune.data.zapret

/**
 * Reading and rewriting a zapret-style host list as text. Pure, so it can be tested without files.
 *
 * Ported from KittyTune Desktop. On Android there is no external zapret folder: the same block
 * format backs the in-app bypass domain list, stored in preferences instead of
 * `lists/list-general-user.txt`, so lists can be exported in a format zapret on a PC understands.
 */
object ZapretHostList {
    const val BEGIN = "# KittyTune — begin"
    const val END = "# KittyTune — end"

    fun domains(text: String): List<String> = text.lineSequence()
        .map { it.substringBefore('#').trim().lowercase() }
        .filter { it.isNotEmpty() }
        .toList()

    /** Whether [domain] or one of its parents is in [covered] — host lists match subdomains. */
    fun isCovered(domain: String, covered: Set<String>): Boolean {
        var candidate = domain.lowercase().trim().trimEnd('.')
        while (true) {
            if (candidate in covered) return true
            val dot = candidate.indexOf('.')
            if (dot < 0 || candidate.indexOf('.', dot + 1) < 0) return false
            candidate = candidate.substring(dot + 1)
        }
    }

    fun ownDomains(text: String): List<String> {
        val lines = text.lines()
        val begin = lines.indexOfFirst { it.trim() == BEGIN }
        if (begin < 0) return emptyList()
        val end = lines.drop(begin + 1).indexOfFirst { it.trim() == END }.let { if (it < 0) lines.size else begin + 1 + it }
        return domains(lines.subList(begin + 1, end).joinToString("\n"))
    }

    /**
     * [text] with KittyTune's block replaced by [own] (or removed when empty). Everything outside the block is
     * kept as it was, and a file that did not end in a newline gets one before the block.
     */
    fun withOwnDomains(text: String, own: List<String>): String {
        val lines = text.lines().toMutableList()
        val begin = lines.indexOfFirst { it.trim() == BEGIN }
        if (begin >= 0) {
            val endOffset = lines.drop(begin + 1).indexOfFirst { it.trim() == END }
            val end = if (endOffset < 0) lines.size - 1 else begin + 1 + endOffset
            repeat(end - begin + 1) { lines.removeAt(begin) }
        }
        while (lines.isNotEmpty() && lines.last().isBlank()) lines.removeAt(lines.lastIndex)
        if (own.isNotEmpty()) {
            lines += BEGIN
            lines += own
            lines += END
        }
        // zapret refuses an empty list, which is why the stock file carries a placeholder domain.
        if (lines.none { it.substringBefore('#').isNotBlank() }) lines += "domain.example.abc"
        return lines.joinToString("\n", postfix = "\n")
    }
}
