// Frozen codec from 65c641bb, renamed only, for regression comparison.
package com.alananasss.kittytune.data

import com.google.gson.Gson
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.StringReader
import java.io.Writer
import java.lang.reflect.Type
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Lossless queue-history storage. Legacy JSON stays readable without rewriting existing rows. */
internal class UnbufferedQueueSnapshotCodec(private val gson: Gson) {
    companion object {
        private const val PREFIX = "kittytune-queue:gzip:1:"
    }

    fun encode(value: Any): String {
        val bytes = ByteArrayOutputStream()
        val base64 = Base64.getEncoder().wrap(bytes)
        val gzip = object : GZIPOutputStream(base64) {
            init { def.setLevel(Deflater.BEST_SPEED) }
        }
        // Do not materialize the (potentially multi-megabyte) uncompressed JSON String.
        UnbufferedSurrogateEscapingWriter(OutputStreamWriter(gzip, Charsets.UTF_8)).use { writer ->
            gson.toJson(value, writer)
        }
        return PREFIX + bytes.toString(Charsets.US_ASCII.name())
    }

    fun <T> decode(payload: String, type: Type): T? {
        if (!payload.startsWith(PREFIX)) {
            return StringReader(payload).use { gson.fromJson<T>(it, type) }
        }
        val bytes = payload.substring(PREFIX.length).toByteArray(Charsets.US_ASCII)
        val compressed = Base64.getDecoder().wrap(ByteArrayInputStream(bytes))
        return InputStreamReader(GZIPInputStream(compressed), Charsets.UTF_8).use {
            gson.fromJson<T>(it, type)
        }
    }
}

/** Preserve even isolated UTF-16 surrogates, which a UTF-8 encoder would replace with '?'. */
private class UnbufferedSurrogateEscapingWriter(private val out: Writer) : Writer() {
    override fun write(chars: CharArray, offset: Int, length: Int) {
        var start = offset
        val end = offset + length
        for (i in offset until end) {
            if (chars[i].isSurrogate()) {
                out.write(chars, start, i - start)
                out.write("\\u" + chars[i].code.toString(16).padStart(4, '0'))
                start = i + 1
            }
        }
        out.write(chars, start, end - start)
    }

    override fun flush() = out.flush()
    override fun close() = out.close()
}
