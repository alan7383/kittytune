package com.alananasss.kittytune.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import java.io.File

/**
 * Writing a rendered share card out and handing it to another app.
 *
 * The card is captured from the composition itself rather than drawn a second time on a
 * [android.graphics.Canvas]: the preview the user approves and the file they send are then the
 * same pixels, and the layout only has to exist once.
 */
object ShareCardExport {

    private const val TAG = "ShareCardExport"
    private const val DIR = "share_cards"

    /**
     * Captures [layer] and writes it to a PNG in the cache.
     *
     * PNG rather than JPEG because the card carries flat colour and text, which JPEG's blocks
     * smear; at this size the difference in bytes does not matter.
     */
    suspend fun writeCard(context: Context, layer: GraphicsLayer, trackId: Long): Uri? {
        return try {
            val bitmap: Bitmap = layer.toImageBitmap().asAndroidBitmap()
            val dir = File(context.cacheDir, DIR).apply { mkdirs() }
            // One file per track, overwritten on each share: the cache is not a gallery, and a
            // card is stale the moment the user changes the style and shares again.
            val file = File(dir, "kittytune_share_$trackId.png")
            file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (e: Exception) {
            Log.w(TAG, "Could not write the share card", e)
            null
        }
    }

    /**
     * Opens the system share sheet with the card and a link to the track.
     *
     * Both go in: an app that shows images uses the card, one that only takes text falls back to
     * the link, and the ones that take both - messengers above all - send a card that is still
     * tappable through to the track.
     */
    fun share(context: Context, cardUri: Uri, trackUrl: String?, chooserTitle: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, cardUri)
            if (!trackUrl.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, trackUrl)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    }

    /**
     * Clears cards written earlier.
     *
     * The cache survives the share, and a card holds artwork and a track title, so it is worth
     * not leaving a pile of them behind. Called when the sheet closes rather than on each write,
     * so the file being shared is never pulled out from under the receiving app.
     */
    fun clearOlderThan(context: Context, keep: Uri?) {
        try {
            val dir = File(context.cacheDir, DIR)
            if (!dir.isDirectory) return
            val keepName = keep?.lastPathSegment?.substringAfterLast('/')
            dir.listFiles()?.forEach { file ->
                if (file.name != keepName) file.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not clear old share cards", e)
        }
    }
}
