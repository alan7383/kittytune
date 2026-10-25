package com.alananasss.kittytune.data.audio

import android.content.Context
import com.google.gson.Gson
import java.io.File
import java.net.URLDecoder
import java.text.Normalizer
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.ln
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * A headphone model present in AutoEq's index.
 *
 * AutoEq aggregates measurements from oratory1990, crinacle, Rtings, DHRME, HypetheSonics,
 * Super Review, Regan Cipher, and Squiglink contributors. Each entry points to a folder
 * containing the GraphicEQ file resampled onto KittyTune's 16 fixed bands.
 */
data class AutoEqProfile(
    val id: String,
    val name: String,
    val path: String,
    val source: String,
    val rig: String?,
    val formFactor: String,
) {
    val attribution: String
        get() = rig?.takeIf { it.isNotBlank() }?.let { "$source · $it" } ?: source
}

/** A profile downloaded from AutoEq or loaded locally, reduced to KittyTune's 16 bands. */
data class AutoEqProfileData(
    val profile: AutoEqProfile,
    val gainsDb: List<Float>,
    val preampDb: Float,
)

/** What is remembered for a paired headphone so a later connection can restore its EQ. */
data class SavedEqProfile(
    val profileId: String,
    val profileName: String,
    val source: String,
    val deviceKey: String,
    val deviceName: String,
    val gainsDb: List<Float>,
    val preampDb: Float,
)

/**
 * Reads AutoEq directly from its published GitHub index (INDEX.md with 8,850+ profiles).
 *
 * The full index is cached on disk for a week; individual profiles are cached for a month.
 * Seed profiles and precomputed gains are bundled as an offline fallback so popular models
 * work instantly without requiring any internet connection.
 */
object AutoEqRepository {
    private const val FULL_INDEX_URL = "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results/INDEX.md"
    private const val FALLBACK_INDEX_URL = "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results/README.md"
    private const val RESULTS_BASE = "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results/"
    private const val INDEX_MAX_AGE_MS = 7L * 24L * 60L * 60L * 1000L
    private const val PROFILE_MAX_AGE_MS = 30L * 24L * 60L * 60L * 1000L
    private const val CACHE_DIR = "autoeq"
    private const val INDEX_FILE = "index_v2.md"
    const val BAND_COUNT = 16

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val indexMutex = Mutex()

    @Volatile
    private var memoryIndex: List<AutoEqProfile>? = null

    val POPULAR_SEED_PROFILES: List<AutoEqProfile> = listOf(
        AutoEqProfile(
            id = "./DHRME/in-ear/Google%20Pixel%20Buds%20Pro%202",
            name = "Google Pixel Buds Pro 2",
            path = "./DHRME/in-ear/Google%20Pixel%20Buds%20Pro%202",
            source = "DHRME",
            rig = null,
            formFactor = "In-ear"
        ),
        AutoEqProfile(
            id = "./oratory1990/over-ear/Sony%20WH-1000XM5",
            name = "Sony WH-1000XM5",
            path = "./oratory1990/over-ear/Sony%20WH-1000XM5",
            source = "oratory1990",
            rig = null,
            formFactor = "Over-ear"
        ),
        AutoEqProfile(
            id = "./crinacle/711%20in-ear/Apple%20AirPods%20Pro%202%20(ANC%20mode)",
            name = "Apple AirPods Pro 2 (ANC mode)",
            path = "./crinacle/711%20in-ear/Apple%20AirPods%20Pro%202%20(ANC%20mode)",
            source = "crinacle",
            rig = "711",
            formFactor = "In-ear"
        ),
        AutoEqProfile(
            id = "./DHRME/in-ear/Samsung%20Galaxy%20Buds3%20Pro",
            name = "Samsung Galaxy Buds3 Pro",
            path = "./DHRME/in-ear/Samsung%20Galaxy%20Buds3%20Pro",
            source = "DHRME",
            rig = null,
            formFactor = "In-ear"
        ),
        AutoEqProfile(
            id = "./Kuulokenurkka/over-ear/Bose%20QuietComfort%20Ultra%20(ANC%20on)",
            name = "Bose QuietComfort Ultra (ANC on)",
            path = "./Kuulokenurkka/over-ear/Bose%20QuietComfort%20Ultra%20(ANC%20on)",
            source = "Kuulokenurkka",
            rig = null,
            formFactor = "Over-ear"
        ),
        AutoEqProfile(
            id = "./Rtings/Bruel%20&%20Kjaer%205128%20earbud/Apple%20AirPods%204",
            name = "Apple AirPods 4",
            path = "./Rtings/Bruel%20&%20Kjaer%205128%20earbud/Apple%20AirPods%204",
            source = "Rtings",
            rig = "Bruel & Kjaer 5128",
            formFactor = "Earbuds"
        ),
        AutoEqProfile(
            id = "./oratory1990/in-ear/Sony%20WF-1000XM5",
            name = "Sony WF-1000XM5",
            path = "./oratory1990/in-ear/Sony%20WF-1000XM5",
            source = "oratory1990",
            rig = null,
            formFactor = "In-ear"
        ),
        AutoEqProfile(
            id = "./oratory1990/over-ear/Sennheiser%20HD%20650",
            name = "Sennheiser HD 650",
            path = "./oratory1990/over-ear/Sennheiser%20HD%20650",
            source = "oratory1990",
            rig = null,
            formFactor = "Over-ear"
        ),
        AutoEqProfile(
            id = "./oratory1990/over-ear/Anker%20Soundcore%20Space%20Q45%20(ANC%20on)",
            name = "Anker Soundcore Space Q45 (ANC on)",
            path = "./oratory1990/over-ear/Anker%20Soundcore%20Space%20Q45%20(ANC%20on)",
            source = "oratory1990",
            rig = "GRAS 43AG-7",
            formFactor = "Over-ear"
        )
    )

    private val SEED_DATA: Map<String, Pair<List<Float>, Float>> = mapOf(
        "./DHRME/in-ear/Google%20Pixel%20Buds%20Pro%202" to Pair(
            listOf(-2.1f, -2.7f, -3.5f, -4.3f, -5.0f, -3.9f, -3.8f, -7.9f, -5.5f, -4.1f, -6.9f, -6.3f, -7.4f, -6.7f, -4.1f, -0.2f),
            0.0f
        ),
        "./oratory1990/over-ear/Sony%20WH-1000XM5" to Pair(
            listOf(-9.8f, -9.4f, -11.6f, -11.4f, -7.8f, -5.7f, -5.1f, -2.0f, -1.5f, -7.1f, -6.0f, -4.5f, -2.6f, -1.9f, -2.2f, -3.4f),
            0.0f
        ),
        "./crinacle/711%20in-ear/Apple%20AirPods%20Pro%202%20(ANC%20mode)" to Pair(
            listOf(-2.9f, -1.8f, -3.1f, -4.5f, -5.4f, -5.9f, -5.6f, -2.8f, -2.6f, -2.5f, -0.2f, -1.7f, -0.5f, -2.2f, -6.2f, -11.5f),
            0.0f
        ),
        "./DHRME/in-ear/Samsung%20Galaxy%20Buds3%20Pro" to Pair(
            listOf(-4.3f, -4.0f, -3.1f, -2.9f, -1.1f, -0.5f, -3.1f, -5.5f, -4.5f, -3.4f, -1.1f, -4.5f, -5.5f, -5.9f, -5.3f, -3.8f),
            0.0f
        ),
        "./Kuulokenurkka/over-ear/Bose%20QuietComfort%20Ultra%20(ANC%20on)" to Pair(
            listOf(-12.0f, -12.0f, -9.4f, -6.2f, -2.6f, -1.6f, -1.2f, -4.9f, -2.2f, -1.7f, -3.7f, -2.1f, -0.6f, -2.4f, -6.4f, -11.6f),
            0.0f
        ),
        "./Rtings/Bruel%20&%20Kjaer%205128%20earbud/Apple%20AirPods%204" to Pair(
            listOf(-5.5f, -7.4f, -9.0f, -8.3f, -9.0f, -7.7f, -7.6f, -8.3f, -6.3f, -0.6f, -0.6f, -5.1f, -7.3f, -8.1f, -7.6f, -6.0f),
            0.0f
        ),
        "./oratory1990/in-ear/Sony%20WF-1000XM5" to Pair(
            listOf(-4.9f, -6.3f, -8.5f, -8.5f, -7.2f, -5.6f, -5.2f, -6.0f, -7.2f, -5.3f, -5.7f, -4.9f, -6.8f, -5.6f, -2.6f, -0.3f),
            0.0f
        ),
        "./oratory1990/over-ear/Sennheiser%20HD%20650" to Pair(
            listOf(-0.2f, -2.9f, -7.0f, -8.3f, -7.4f, -6.5f, -6.4f, -7.2f, -5.3f, -7.6f, -6.2f, -1.9f, -1.6f, -3.9f, -9.2f, -12.0f),
            0.0f
        ),
        "./oratory1990/over-ear/Anker%20Soundcore%20Space%20Q45%20(ANC%20on)" to Pair(
            listOf(-12.0f, -10.2f, -10.8f, -6.8f, -5.3f, -6.8f, -7.2f, -8.2f, -5.5f, -1.4f, -6.0f, -2.8f, -4.8f, -7.1f, -10.9f, -12.0f),
            0.0f
        )
    )

    private val ALIASES = mapOf(
        "qc" to "quietcomfort",
        "xm5" to "1000xm5",
        "xm4" to "1000xm4",
        "xm3" to "1000xm3",
    )

    private val NOISE_WORDS = setOf(
        "the", "a", "an", "and", "s", "bluetooth", "wireless", "headphone", "headphones",
        "earphone", "earphones", "earbud", "earbuds", "headset", "edition", "casque", "ecouteurs"
    )

    suspend fun profiles(context: Context): List<AutoEqProfile> = withContext(Dispatchers.IO) {
        memoryIndex?.let { return@withContext it }
        indexMutex.withLock {
            memoryIndex?.let { return@withContext it }
            val cache = File(context.filesDir, "$CACHE_DIR/$INDEX_FILE")
            val text = readOrFetch(
                cache = cache,
                maxAgeMs = INDEX_MAX_AGE_MS,
                primaryUrl = FULL_INDEX_URL,
                fallbackUrl = FALLBACK_INDEX_URL,
            )
            parseIndex(text).also { memoryIndex = it }
        }
    }

    suspend fun search(context: Context, query: String, limit: Int = 30): List<AutoEqProfile> {
        val cleaned = cleanQuery(query)
        if (normalize(cleaned).isBlank()) return emptyList()
        return searchIn(profiles(context), query, limit)
    }

    /**
     * Resilient search supporting:
     * - Automatic stripping of possessive Bluetooth prefixes ("Alan's Pixel Buds")
     * - Typo tolerance via Levenshtein distance on words >= 4 letters ("piixel" -> "pixel")
     * - Common abbreviation expansion ("qc" -> "quietcomfort", "xm5" -> "1000xm5")
     * - Number matching enforcement (e.g. "650", "2", "3")
     * - Measurement source quality weighting
     */
    internal fun searchIn(all: List<AutoEqProfile>, rawQuery: String, limit: Int = 30): List<AutoEqProfile> {
        val cleaned = cleanQuery(rawQuery)
        val normalizedQuery = normalize(cleaned)
        if (normalizedQuery.isBlank()) return emptyList()

        val rawTokens = normalizedQuery.split(' ').filter { it.isNotBlank() }
        val tokens = rawTokens.map { ALIASES[it] ?: it }
            .filter { it !in NOISE_WORDS || rawTokens.size == 1 }
            .ifEmpty { rawTokens }

        val compactQuery = tokens.joinToString("")
        val queryHasDigits = tokens.any { it.any { c -> c.isDigit() } }

        return all.asSequence()
            .mapNotNull { profile ->
                val name = normalize(profile.name)
                val nameWords = name.split(' ').filter { it.isNotBlank() }
                val compactName = name.replace(" ", "")
                val source = normalize(profile.source)

                var matchedTokens = 0
                for (t in tokens) {
                    if (t in name || t in source) {
                        matchedTokens++
                    } else if (t.length >= 4 && nameWords.any { nw -> nw.length >= 4 && levenshteinLeq1(t, nw) }) {
                        matchedTokens++
                    }
                }

                val compactMatch = compactQuery.length >= 3 && compactQuery in compactName
                val tokenRatio = if (tokens.isNotEmpty()) matchedTokens.toFloat() / tokens.size else 0f

                if (queryHasDigits) {
                    val digitTokens = tokens.filter { it.any { c -> c.isDigit() } }
                    val hasAllDigits = digitTokens.all { dt -> dt in name || dt in source || compactName.contains(dt) }
                    if (!hasAllDigits) return@mapNotNull null
                }

                if (!compactMatch && tokenRatio < 0.70f) return@mapNotNull null

                val baseScore = when {
                    name == normalizedQuery -> 10_000
                    name.startsWith(normalizedQuery) -> 7_500
                    compactMatch && compactName.startsWith(compactQuery) -> 6_500
                    compactMatch -> 5_000
                    tokenRatio == 1.0f -> 4_200
                    else -> (tokenRatio * 3_000).toInt()
                }

                val score = baseScore + sourcePriority(profile.source) * 10 - (profile.name.length / 4)
                profile to score
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(limit)
            .toList()
    }

    suspend fun load(context: Context, profile: AutoEqProfile): AutoEqProfileData =
        withContext(Dispatchers.IO) {
            val cache = File(context.cacheDir, "$CACHE_DIR/profiles/${profile.path.hashCode()}.json")
            if (cache.exists() && System.currentTimeMillis() - cache.lastModified() < PROFILE_MAX_AGE_MS) {
                runCatching { gson.fromJson(cache.readText(), AutoEqProfileData::class.java) }
                    .getOrNull()
                    ?.takeIf { it.gainsDb.size == BAND_COUNT }
                    ?.let { return@withContext it }
            }

            // Check if profile is pre-seeded for offline reliability
            val seed = SEED_DATA[profile.path] ?: SEED_DATA[profile.id]

            val text = try {
                fetch(profileFileUrl(profile))
            } catch (e: Exception) {
                if (seed != null) {
                    return@withContext AutoEqProfileData(profile, seed.first, seed.second)
                }
                throw e
            }

            val (gains, preamp) = parseGraphicEq(text, bands = EQUALIZER_BANDS)
            val data = AutoEqProfileData(
                profile = profile,
                gainsDb = gains,
                preampDb = preamp,
            )
            runCatching {
                cache.parentFile?.mkdirs()
                cache.writeText(gson.toJson(data))
            }
            data
        }

    /**
     * Parses custom GraphicEQ curves or frequency-gain files from Squiglink, AutoEq web,
     * or Equalizer APO exports.
     */
    fun loadFromText(name: String, text: String): AutoEqProfileData {
        val (gains, preamp) = parseGraphicEq(text, bands = EQUALIZER_BANDS)
        val profile = AutoEqProfile(
            id = "custom:${name.hashCode()}",
            name = name,
            path = "custom",
            source = "Custom GraphicEQ",
            rig = null,
            formFactor = "Custom"
        )
        return AutoEqProfileData(profile, gains, preamp)
    }

    internal fun parseIndex(text: String): List<AutoEqProfile> {
        val seen = HashSet<String>()
        val result = ArrayList<AutoEqProfile>(9000)
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (!line.startsWith("- [")) return@forEach
            val open = line.indexOf("](")
            if (open < 0) return@forEach
            val name = line.substring(3, open).trim()
            val after = line.substring(open + 2)
            val byMarker = after.lastIndexOf(") by ")
            val close = if (byMarker >= 0) byMarker else after.lastIndexOf(')')
            if (close < 0) return@forEach
            val path = after.substring(0, close).trim()
            if (name.isBlank() || path.isBlank() || !seen.add(path)) return@forEach
            // Fixed offset: length of ") by " is 5 chars
            val by = if (byMarker >= 0) after.substring(byMarker + 5).trim() else ""
            val source = by.substringBefore(" on ", missingDelimiterValue = by).trim()
                .takeIf { it.isNotBlank() }
                ?: path.removePrefix("./").substringBefore('/').takeIf { it.isNotBlank() }
                ?: "AutoEq"
            val rig = by.substringAfter(" on ", missingDelimiterValue = "").trim()
                .takeIf { it.isNotBlank() }
            result += AutoEqProfile(
                id = path,
                name = name,
                path = path,
                source = source,
                rig = rig,
                formFactor = formFactor(path),
            )
        }
        return if (result.isEmpty()) POPULAR_SEED_PROFILES else result
    }

    /**
     * AutoEq publishes a dense `GraphicEQ: freq gain; ...` curve. The processor has 16 fixed bands,
     * so each band is sampled at its centre with log-frequency interpolation, which preserves the
     * shape of the correction instead of taking the nearest measurement point.
     */
    internal fun parseGraphicEq(text: String, bands: FloatArray = EQUALIZER_BANDS): Pair<List<Float>, Float> {
        val body = if (text.contains("GraphicEQ:")) text.substringAfter("GraphicEQ:") else text
        val points = Regex("""(-?\d+(?:\.\d+)?)\s+(-?\d+(?:\.\d+)?)""")
            .findAll(body)
            .mapNotNull { m ->
                val f = m.groupValues[1].toFloatOrNull() ?: return@mapNotNull null
                val g = m.groupValues[2].toFloatOrNull() ?: return@mapNotNull null
                if (f <= 0f) null else f to g
            }
            .sortedBy { it.first }
            .toList()
        if (points.isEmpty()) return List(bands.size) { 0f } to 0f

        val gains = bands.map { band -> interpolateLog(points, band).coerceIn(-12f, 12f) }

        // Check for explicit preamp line, e.g. "Preamp: -6.0 dB"
        val preampMatch = Regex("""Preamp:\s*(-?\d+(?:\.\d+)?)\s*dB""", RegexOption.IGNORE_CASE).find(text)
        val explicitPreamp = preampMatch?.groupValues?.get(1)?.toFloatOrNull()

        // Keep the loudest boosted band under 0 dBFS; AutoEq's cuts already need no headroom.
        val preamp = explicitPreamp?.coerceIn(-12f, 0f)
            ?: (-maxOf(0f, gains.maxOrNull() ?: 0f)).coerceIn(-12f, 0f)
        return gains to preamp
    }

    internal fun profileFileUrl(profile: AutoEqProfile): String {
        val segments = profile.path.removePrefix("./").split('/').map(::decodeSegment)
        val base = RESULTS_BASE.toHttpUrl().newBuilder()
        segments.forEach { base.addPathSegment(it) }
        base.addPathSegment("${segments.last()} GraphicEQ.txt")
        return base.build().toString()
    }

    private fun interpolateLog(points: List<Pair<Float, Float>>, frequency: Float): Float {
        if (frequency <= points.first().first) return points.first().second
        if (frequency >= points.last().first) return points.last().second
        for (i in 0 until points.size - 1) {
            val (f0, g0) = points[i]
            val (f1, g1) = points[i + 1]
            if (frequency in f0..f1) {
                val span = ln(f1.toDouble()) - ln(f0.toDouble())
                if (span <= 0.0) return g0
                val t = ((ln(frequency.toDouble()) - ln(f0.toDouble())) / span).toFloat()
                return g0 + (g1 - g0) * t
            }
        }
        return points.last().second
    }

    private fun readOrFetch(cache: File, maxAgeMs: Long, primaryUrl: String, fallbackUrl: String): String {
        val fresh = cache.exists() && System.currentTimeMillis() - cache.lastModified() < maxAgeMs
        if (fresh) return cache.readText()
        return try {
            fetch(primaryUrl).also { text ->
                if (text.isNotBlank() && text.contains("](.")) {
                    cache.parentFile?.mkdirs()
                    cache.writeText(text)
                }
            }
        } catch (_: Exception) {
            try {
                fetch(fallbackUrl).also { text ->
                    if (text.isNotBlank() && text.contains("](.")) {
                        cache.parentFile?.mkdirs()
                        cache.writeText(text)
                    }
                }
            } catch (_: Exception) {
                if (cache.exists()) cache.readText() else ""
            }
        }
    }

    private fun fetch(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "KittyTune-Android")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("AutoEq HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    private fun decodeSegment(segment: String): String =
        runCatching { URLDecoder.decode(segment.replace("+", "%2B"), "UTF-8") }.getOrDefault(segment)

    private fun formFactor(path: String): String = when {
        "/in-ear/" in path -> "In-ear"
        "/over-ear/" in path -> "Over-ear"
        "/on-ear/" in path -> "On-ear"
        "/earbud/" in path || "/earbuds/" in path -> "Earbuds"
        else -> "Headphones"
    }

    private fun sourcePriority(source: String): Int = when {
        source.contains("oratory1990", ignoreCase = true) -> 60
        source.contains("crinacle", ignoreCase = true) -> 50
        source.contains("Rtings", ignoreCase = true) -> 45
        source.contains("DHRME", ignoreCase = true) -> 40
        source.contains("HypetheSonics", ignoreCase = true) -> 30
        source.contains("Super Review", ignoreCase = true) -> 25
        source.contains("Kuulokenurkka", ignoreCase = true) -> 20
        source.contains("Regan Cipher", ignoreCase = true) -> 15
        else -> 0
    }

    internal fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .lowercase()
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .trim()

    /**
     * Cleans up raw query strings or Bluetooth device names by removing owner prefixes,
     * BLE suffixes, and trailing connection tags.
     */
    fun cleanQuery(raw: String): String {
        var s = raw.trim()
        s = s.replace(Regex("""^[a-zA-Z0-9_\-\s]+['’]s\s+""", RegexOption.IGNORE_CASE), "")
        s = s.replace(Regex("""^(?:casque|ecouteurs|écouteurs)\s+(?:de\s+)?(?:[a-zA-Z0-9_\-]+\s+)?(?:-\s*)?""", RegexOption.IGNORE_CASE), "")
        s = s.replace(Regex("""\([A-Fa-f0-9:]{4,}\)"""), "")
        s = s.replace(Regex("""\s*-\s*Find\s*My""", RegexOption.IGNORE_CASE), "")
        s = s.replace(Regex("""\s*\(A2DP\)""", RegexOption.IGNORE_CASE), "")
        s = s.replace(Regex("""\s+Hands-Free""", RegexOption.IGNORE_CASE), "")
        return s.trim()
    }

    internal fun levenshteinLeq1(s1: String, s2: String): Boolean {
        if (abs(s1.length - s2.length) > 1) return false
        if (s1 == s2) return true
        val (short, long) = if (s1.length <= s2.length) s1 to s2 else s2 to s1
        var i = 0
        var j = 0
        var diff = 0
        while (i < short.length && j < long.length) {
            if (short[i] != long[j]) {
                diff++
                if (diff > 1) return false
                if (short.length == long.length) {
                    i++
                    j++
                } else {
                    j++
                }
            } else {
                i++
                j++
            }
        }
        return true
    }

    /** Same centres as [com.alananasss.kittytune.ui.player.audio.EqualizerAudioProcessor]. */
    internal val EQUALIZER_BANDS = floatArrayOf(
        32f, 64f, 125f, 200f, 315f, 500f, 800f, 1250f,
        2000f, 3150f, 5000f, 8000f, 10000f, 12500f, 16000f, 20000f,
    )
}
