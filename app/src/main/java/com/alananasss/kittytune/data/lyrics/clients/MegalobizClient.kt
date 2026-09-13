package com.alananasss.kittytune.data.lyrics.clients

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.alananasss.kittytune.data.lyrics.parsers.QRCParser

object MegalobizClient {
    private val client by lazy {
        OkHttpClient.Builder()
            .callTimeout(8, TimeUnit.SECONDS)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val LRC_PATH_REGEX = Regex("""href=["'](/lrc/maker/download/[^"']+)["']""")
    private val LRC_SPAN_REGEX = Regex("""id=["']lrc_[^"']*_details["'][^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
    private val HTML_TAG_REGEX = Regex("""<[^>]+>""")
    private val LINE_REGEX = Regex("""^\[\d{2}:\d{2}\.\d{2,3}].*""")

    suspend fun getLyrics(
        title: String,
        artist: String,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val query = "$artist $title".trim()
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val searchUrl = "https://www.megalobiz.com/searchall?qry=$encodedQuery"

                val request =
                    Request.Builder()
                        .url(searchUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .build()

                val searchHtml = fetchHtml(request) ?: return@runCatching null

                val match = LRC_PATH_REGEX.find(searchHtml) ?: return@runCatching null
                val lrcUrl = "https://www.megalobiz.com" + match.groupValues[1]

                val lrcRequest =
                    Request.Builder()
                        .url(lrcUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .build()

                val detailHtml = fetchHtml(lrcRequest) ?: return@runCatching null

                val rawLrcText = LRC_SPAN_REGEX.find(detailHtml)?.groupValues?.get(1) ?: detailHtml

                val cleanedText =
                    rawLrcText
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&amp;", "&")
                        .replace("<br>", "\n")
                        .replace("<br/>", "\n")
                        .replace("<br />", "\n")
                        .replace(HTML_TAG_REGEX, "")
                        .trim()

                if (isLineSynced(cleanedText)) {
                    cleanedText
                } else {
                    null
                }
            }.mapCatching {
                it ?: throw Exception("Lyrics not found on Megalobiz")
            }.onFailure { if (it is CancellationException) throw it }
        }

    private fun isLineSynced(text: String): Boolean {
        if (QRCParser.isQrc(text)) return true
        return text.lineSequence().any { line ->
            LINE_REGEX.matches(line.trim())
        }
    }

    private suspend fun fetchHtml(request: Request): String? =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val body = response.use {
                            if (it.isSuccessful) it.body?.string() else null
                        }
                        if (continuation.isActive) continuation.resume(body)
                    } catch (e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                }
            })
        }
}
