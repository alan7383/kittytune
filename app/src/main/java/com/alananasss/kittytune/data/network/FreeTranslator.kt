package com.alananasss.kittytune.data.network

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

object FreeTranslator {
    private val client: okhttp3.OkHttpClient
        get() = ProxyManager.getOkHttpClient()

    /**
     * Determines the script of the text for scripts with clear language mapping.
     * Note: LATIN is deliberately omitted because English, French, Spanish, German,
     * Italian, etc. all share the Latin alphabet and cannot be distinguished by script alone.
     */
    private fun detectScriptLanguage(text: String): String? {
        if (text.isBlank()) return null
        var cyrillic = 0; var cjk = 0; var arabic = 0
        var hebrew = 0; var hangul = 0; var kana = 0; var devanagari = 0
        var thai = 0; var greek = 0
        for (c in text) {
            when (Character.UnicodeScript.of(c.code)) {
                Character.UnicodeScript.CYRILLIC -> cyrillic++
                Character.UnicodeScript.HAN -> cjk++
                Character.UnicodeScript.ARABIC -> arabic++
                Character.UnicodeScript.HEBREW -> hebrew++
                Character.UnicodeScript.HANGUL -> hangul++
                Character.UnicodeScript.HIRAGANA,
                Character.UnicodeScript.KATAKANA -> kana++
                Character.UnicodeScript.DEVANAGARI -> devanagari++
                Character.UnicodeScript.THAI -> thai++
                Character.UnicodeScript.GREEK -> greek++
                else -> {}
            }
        }
        val candidates = listOf(
            "ru" to cyrillic, "zh" to cjk, "ja" to kana,
            "ko" to hangul, "ar" to arabic, "he" to hebrew,
            "hi" to devanagari, "th" to thai, "el" to greek,
        )
        val total = candidates.sumOf { it.second }
        if (total < 5) return null
        val best = candidates.maxByOrNull { it.second } ?: return null
        return if (best.second.toFloat() / total >= 0.6f) best.first else null
    }

    private fun normalizeLanguage(code: String?): String? {
        if (code.isNullOrBlank()) return null
        val trimmed = code.trim()
        return if (trimmed.length >= 2) trimmed.take(2).lowercase() else null
    }

    suspend fun translateMissing(
        linesToTranslate: List<String>,
        targetLang: String
    ): Map<String, String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (linesToTranslate.isEmpty()) return@withContext emptyMap()

        // Quick check: if the non-Latin script of the text already matches target language, skip translation
        val sample = linesToTranslate.joinToString(" ").take(2000)
        val detectedScript = detectScriptLanguage(sample)
        val normalizedTarget = normalizeLanguage(targetLang)
        if (detectedScript != null && normalizedTarget != null && detectedScript == normalizedTarget) {
            return@withContext emptyMap()
        }

        val resultMap = mutableMapOf<String, String>()

        val combinedText = linesToTranslate.joinToString("\n")

        val requestBody = okhttp3.FormBody.Builder()
            .add("q", combinedText)
            .build()

        val request = okhttp3.Request.Builder()
            .url("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLang&dt=t")
            .post(requestBody)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        try {
            // The body is only read on success, so an unsuccessful response left its connection
            // unreturned to the pool. This endpoint rate-limits readily, which is exactly the path
            // that leaked.
            val body = client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
            if (body != null) {
                val rootArray = com.google.gson.JsonParser.parseString(body).asJsonArray

                // Check Google's detected source language (usually at index 2 of rootArray)
                val detectedLang = if (rootArray.size() > 2 && rootArray.get(2).isJsonPrimitive) {
                    normalizeLanguage(rootArray.get(2).asString)
                } else null

                if (detectedLang != null && normalizedTarget != null && detectedLang == normalizedTarget) {
                    return@withContext emptyMap()
                }

                val textBlocks = rootArray.get(0).asJsonArray

                val translatedFull = java.lang.StringBuilder()
                for (i in 0 until textBlocks.size()) {
                    translatedFull.append(textBlocks.get(i).asJsonArray.get(0).asString)
                }

                val translatedLines = translatedFull.toString().split("\n")

                for (i in 0 until minOf(linesToTranslate.size, translatedLines.size)) {
                    val original = linesToTranslate[i].trim()
                    val translated = translatedLines[i].trim()
                    if (original.isNotEmpty() && translated.isNotEmpty() && !original.equals(translated, ignoreCase = true)) {
                        resultMap[original] = translated
                    }
                }
            }
        } catch (e: Exception) {
            println("Google Translate Error: ${e.message}")
        }
        return@withContext resultMap
    }

    suspend fun getRomanization(
        linesToTranslate: List<String>
    ): Map<String, String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (linesToTranslate.isEmpty()) return@withContext emptyMap()
        val resultMap = java.util.concurrent.ConcurrentHashMap<String, String>()

        val deferreds = linesToTranslate.map { originalLine ->
            async {
                val trimmed = originalLine.trim()
                if (trimmed.isBlank()) return@async
                try {
                    val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=en&dt=rm&q=" + 
                        java.net.URLEncoder.encode(trimmed, "UTF-8")
                    val request = okhttp3.Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0")
                        .build()
                    // One request per lyric line, so an error-path leak here multiplied by the
                    // length of the song.
                    val body = client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) response.body?.string() else null
                    }
                    if (body != null) {
                        val rootArray = com.google.gson.JsonParser.parseString(body).asJsonArray
                        if (rootArray.size() > 0 && rootArray.get(0).isJsonArray) {
                            val textBlocks = rootArray.get(0).asJsonArray
                            var lineRomanized = ""
                            for (i in 0 until textBlocks.size()) {
                                if (!textBlocks.get(i).isJsonArray) continue
                                val block = textBlocks.get(i).asJsonArray
                                if (block.size() > 2 && !block.get(2).isJsonNull && block.get(2).isJsonPrimitive) {
                                    lineRomanized += block.get(2).asString
                                } else if (block.size() > 3 && !block.get(3).isJsonNull && block.get(3).isJsonPrimitive) {
                                    lineRomanized += block.get(3).asString
                                }
                            }
                            val rom = lineRomanized.trim()
                            if (rom.isNotEmpty() && trimmed.lowercase() != rom.lowercase()) {
                                resultMap[trimmed] = rom
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("Romanization line error: ${e.message}")
                }
            }
        }
        deferreds.awaitAll()
        return@withContext resultMap
    }
}
