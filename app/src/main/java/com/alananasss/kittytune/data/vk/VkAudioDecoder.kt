package com.alananasss.kittytune.data.vk

import android.util.Log

/**
 * Audio URL unmasking engine for VKontakte.
 *
 * Port of `Static.ExposeSource` from MeridiusCore (`meridius-core/dist/core.js`, shipped inside
 * meridius-3.4.0.exe). VK hands out `.../audio_api_unavailable.mp3?extra=<payload>#<ops>` for most
 * tracks; both halves are a custom base64 and `<ops>` is a tab-separated list of transformations
 * that must be replayed in reverse to recover the real stream URL.
 */
object VkAudioDecoder {

    private const val TAG = "VkAudioDecoder"

    /** VK's alphabet — note the swapped `O`/`0`, it is not standard base64. */
    private const val VK_ALPHABET =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMN0PQRSTUVWXYZO123456789+/="

    private const val OPS_SEPARATOR = '\t'          // String.fromCharCode(9)
    private const val ARGS_SEPARATOR = '\u000B'     // String.fromCharCode(11)

    private val UNAVAILABLE_REGEX = Regex("audio_api_unavailable")

    fun isMaskedUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return UNAVAILABLE_REGEX.containsMatchIn(url)
    }

    /**
     * @param userId the id of the *logged in* user — the `i` operation mixes it into the shuffle
     *   seed, so passing the track owner instead of the session user yields garbage.
     */
    fun exposeSource(src: String?, userId: Long = 0L): String {
        if (src.isNullOrBlank()) return ""
        if (!isMaskedUrl(src)) return src

        return try {
            val extraSplit = src.split("?extra=", limit = 2)
            if (extraSplit.size < 2) return src

            val hashSplit = extraSplit[1].split("#", limit = 2)
            val encodedUrl = hashSplit[0]
            val encodedOps = hashSplit.getOrNull(1).orEmpty()

            val ops = if (encodedOps.isEmpty()) "" else unmask(encodedOps) ?: return src
            var url = unmask(encodedUrl)
            if (url.isNullOrEmpty()) return src

            val opList = if (ops.isEmpty()) emptyList() else ops.split(OPS_SEPARATOR)

            for (index in opList.indices.reversed()) {
                val parts = opList[index].split(ARGS_SEPARATOR)
                val name = parts.firstOrNull() ?: return src
                val args = parts.drop(1)

                url = when (name) {
                    "v" -> opV(url!!)
                    "r" -> opR(url!!, args.firstOrNull()?.toIntOrNull() ?: 0)
                    "s" -> opS(url!!, args.firstOrNull()?.toIntOrNull() ?: 0)
                    "i" -> opI(url!!, args.firstOrNull()?.toIntOrNull() ?: 0, userId)
                    "x" -> opX(url!!, args.firstOrNull().orEmpty())
                    // An unknown operation means VK changed the scheme: bail out rather than
                    // returning a half-decoded URL, exactly like MeridiusCore does.
                    else -> return src
                }
            }

            val result = url.orEmpty()
            if (result.startsWith("http")) result else src
        } catch (e: Exception) {
            Log.w(TAG, "exposeSource failed: ${e.message}")
            src
        }
    }

    /** VK's custom base64 decoder. Returns null when the payload length is invalid. */
    private fun unmask(input: String): String? {
        if (input.isEmpty() || input.length % 4 == 1) return null

        val out = StringBuilder()
        var acc = 0
        var count = 0

        for (char in input) {
            val index = VK_ALPHABET.indexOf(char)
            if (index == -1) continue

            acc = if (count % 4 != 0) acc * 64 + index else index
            val hadRemainder = count % 4 != 0
            count++
            if (hadRemainder) {
                out.append(((acc shr ((-2 * count) and 6)) and 255).toChar())
            }
        }
        return out.toString()
    }

    private fun opV(value: String): String = value.reversed()

    /**
     * Rotate every character inside VK's doubled alphabet. `substr(pos - shift, 1)` in JavaScript
     * counts from the end for a negative start, which is what the modulo below reproduces.
     */
    private fun opR(value: String, shift: Int): String {
        val alphabet = VK_ALPHABET + VK_ALPHABET
        val chars = value.toCharArray()
        for (i in chars.indices.reversed()) {
            val pos = alphabet.indexOf(chars[i])
            if (pos == -1) continue
            var target = pos - shift
            if (target < 0) target += alphabet.length
            if (target in alphabet.indices) chars[i] = alphabet[target]
        }
        return String(chars)
    }

    /**
     * Seeded shuffle. The map formula is
     * `seed = ((len * i + len) xor (seed + i)) % len` walking `i` from `len - 1` down to `0`.
     */
    private fun shuffleMap(value: String, seedParam: Int): IntArray {
        val len = value.length
        val map = IntArray(len)
        if (len == 0) return map

        var seed = kotlin.math.abs(seedParam)
        for (i in len - 1 downTo 0) {
            seed = ((len * i + len) xor (seed + i)) % len
            map[i] = seed
        }
        return map
    }

    private fun opS(value: String, seed: Int): String {
        if (value.isEmpty()) return value
        val map = shuffleMap(value, seed)
        val chars = value.toCharArray()
        for (i in 1 until chars.size) {
            val target = map[chars.size - 1 - i]
            if (target !in chars.indices) continue
            val swap = chars[i]
            chars[i] = chars[target]
            chars[target] = swap
        }
        return String(chars)
    }

    /** `i` is `s` with the session user id mixed into the seed. */
    private fun opI(value: String, shift: Int, userId: Long): String =
        opS(value, shift xor userId.toInt())

    private fun opX(value: String, key: String): String {
        if (key.isEmpty()) return value
        val keyCode = key[0].code
        val chars = CharArray(value.length)
        for (i in value.indices) {
            chars[i] = (value[i].code xor keyCode).toChar()
        }
        return String(chars)
    }
}
