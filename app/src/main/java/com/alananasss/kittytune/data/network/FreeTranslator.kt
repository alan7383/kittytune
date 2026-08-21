package com.alananasss.kittytune.data.network

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

object FreeTranslator {
    private val client: okhttp3.OkHttpClient
        get() = ProxyManager.getOkHttpClient()

    suspend fun translateMissing(
        linesToTranslate: List<String>,
        targetLang: String
    ): Map<String, String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (linesToTranslate.isEmpty()) return@withContext emptyMap()

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
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext emptyMap()

                val rootArray = com.google.gson.JsonParser.parseString(body).asJsonArray
                val textBlocks = rootArray.get(0).asJsonArray

                val translatedFull = java.lang.StringBuilder()
                for (i in 0 until textBlocks.size()) {
                    translatedFull.append(textBlocks.get(i).asJsonArray.get(0).asString)
                }

                val translatedLines = translatedFull.toString().split("\n")

                for (i in 0 until minOf(linesToTranslate.size, translatedLines.size)) {
                    val original = linesToTranslate[i].trim()
                    val translated = translatedLines[i].trim()
                    if (original.isNotEmpty() && translated.isNotEmpty()) {
                        resultMap[original] = translated
                    }
                }
            }
        } catch (e: Exception) {
            println("Google Translate Error: \${e.message}")
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
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@async
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
                    println("Romanization line error: \${e.message}")
                }
            }
        }
        deferreds.awaitAll()
        return@withContext resultMap
    }
}
