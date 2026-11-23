package com.alananasss.kittytune.data.together

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.alananasss.kittytune.R
import java.net.URLDecoder

/**
 * Shareable links and deep-link parsing for Live Listen Along / Together sessions.
 *
 * Uses the HTTPS KittyTune website URL (`https://alan7383.github.io/kittytune-website/?together=ABCD-EFGH`)
 * as the primary invite URL so that:
 * 1. Discord Rich Presence accepts it in `metadata.button_urls` (Discord requires `https://`).
 * 2. Every chat app (Discord, WhatsApp, Telegram, SMS) renders it as a clickable link.
 * 3. Android intercepts it via `<intent-filter>` (alongside `kittytune://together/ABCD-EFGH`),
 *    while desktop/web visitors without the app land cleanly on the KittyTune website instead of a 404.
 */
object TogetherLinks {

    const val WEB_BASE_URL = "https://alan7383.github.io/kittytune-website/"

    /** The invite worker (Cloudflare, free plan) that gives invite links a personal preview. */
    const val INVITE_BASE_URL = "https://kittytune-invite.kt-net.workers.dev/"
    const val APP_SCHEME = "kittytune"
    const val APP_HOST_TOGETHER = "together"

    private val URL_QUERY_REGEX = Regex(
        """(?:[?&](?:together|listen_along)=([A-Za-z0-9-]{8,12}))""",
        RegexOption.IGNORE_CASE
    )
    private val DIRECT_LINK_REGEX = Regex(
        """(?:(?:kittytune|sc)://(?:together|listen)(?:/|\?(?:code|together)=)|kittytune-website/(?:together|listen)(?:/|\?(?:code|together)=))([A-Za-z0-9-]{8,12})""",
        RegexOption.IGNORE_CASE
    )
    private val LABELED_CODE_REGEX = Regex(
        """(?:code|kode|kod|kód|código|код|mã)\s*[:=]\s*([A-Za-z0-9]{4}-[A-Za-z0-9]{4}|[A-Za-z0-9]{8})""",
        RegexOption.IGNORE_CASE
    )
    private val HYPHENATED_CODE_REGEX = Regex(
        """\b([A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4})\b""",
        RegexOption.IGNORE_CASE
    )

    fun inviteUrl(code: String): String {
        val formatted = TogetherWire.displayCode(code)
        // Through the invite worker (website/invite-worker): link previews (Discord, WhatsApp…) get a card
        // naming the host and the session, and people are sent on to the session on the website. Same
        // `?together=` shape as before, so every version can still read the code out of a pasted link.
        val from = runCatching { Together.displayName("") }.getOrNull()?.trim()?.take(32)?.takeIf { it.isNotBlank() }
        val name = from?.let { "&from=" + java.net.URLEncoder.encode(it, Charsets.UTF_8.name()).replace("+", "%20") }.orEmpty()
        return "${INVITE_BASE_URL}?together=$formatted$name"
    }

    fun appDeepLink(code: String): String =
        "$APP_SCHEME://$APP_HOST_TOGETHER/${TogetherWire.displayCode(code)}"

    fun extractCodeFromUri(uri: Uri?): String? {
        if (uri == null) return null
        return extractCode(uri.toString(), allowRawCode = false)
    }

    /**
     * Extracts a normalized 8-character Together room code from a URL, deep link, shared message,
     * or (when [allowRawCode] is true) a raw user-typed code like `ABCD-EFGH` or `ABCDEFGH`.
     */
    fun extractCode(input: String?, allowRawCode: Boolean = true): String? {
        if (input.isNullOrBlank()) return null
        val decoded = runCatching { URLDecoder.decode(input.trim(), "UTF-8") }.getOrDefault(input.trim())

        URL_QUERY_REGEX.find(decoded)?.groupValues?.getOrNull(1)?.let { candidate ->
            validNormalized(candidate)?.let { return it }
        }

        DIRECT_LINK_REGEX.find(decoded)?.groupValues?.getOrNull(1)?.let { candidate ->
            validNormalized(candidate)?.let { return it }
        }

        if (allowRawCode) {
            LABELED_CODE_REGEX.find(decoded)?.groupValues?.getOrNull(1)?.let { candidate ->
                validNormalized(candidate)?.let { return it }
            }
            HYPHENATED_CODE_REGEX.find(decoded)?.groupValues?.getOrNull(1)?.let { candidate ->
                validNormalized(candidate)?.let { return it }
            }
            val direct = TogetherWire.normalizeCode(decoded)
            if (direct.length == TogetherWire.CODE_LENGTH && decoded.length <= 12) {
                return direct
            }
        }

        return null
    }

    private fun validNormalized(raw: String): String? {
        val normalized = TogetherWire.normalizeCode(raw)
        return normalized.takeIf { it.length == TogetherWire.CODE_LENGTH }
    }

    fun copyInviteLink(context: Context, code: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("KittyTune Listen Along", inviteUrl(code)))
        Toast.makeText(context, context.getString(R.string.together_link_copied), Toast.LENGTH_SHORT).show()
    }

    fun shareInviteLink(context: Context, code: String) {
        val url = inviteUrl(code)
        val display = TogetherWire.displayCode(code)
        val message = context.getString(R.string.together_share_message, url, display)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        val chooser = Intent.createChooser(intent, context.getString(R.string.together_share_link)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
