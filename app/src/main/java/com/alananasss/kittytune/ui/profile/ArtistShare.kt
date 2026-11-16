package com.alananasss.kittytune.ui.profile

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import com.alananasss.kittytune.ui.design.AlertDialog
import com.alananasss.kittytune.ui.design.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import com.alananasss.kittytune.ui.design.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.hapticAction
import com.alananasss.kittytune.ui.common.hapticClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat

/**
 * A card for sharing an artist (ported from desktop, PR #77, rewritten for Android's canvas): the name, the
 * followers, the top songs, on a gradient of the theme's accent — one picture that says who they are.
 */
object ArtistShare {

    data class Card(
        val name: String,
        val followers: String?,
        val avatarUrl: String?,
        val topTracks: List<String>,
        val accentArgb: Int,
        val link: String,
    )

    private const val WIDTH = 900
    private const val HEIGHT = 560

    /** Draws the card as a bitmap: avatar circle, name, followers, top-3, KittyTune mark. */
    fun render(card: Card, avatar: Bitmap?): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val dark = Color.rgb(16, 16, 20)
        val accent = card.accentArgb

        // The background: a diagonal wash of the accent over near-black, with a glow off the corner.
        canvas.drawColor(dark)
        val gradient = android.graphics.LinearGradient(
            0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(),
            mix(dark, accent, 0.55f), mix(dark, accent, 0.12f),
            android.graphics.Shader.TileMode.CLAMP
        )
        val bg = Paint().apply { shader = gradient }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), bg)
        val glow = Paint().apply {
            shader = RadialGradient(
                WIDTH * 0.82f, HEIGHT * 0.18f, WIDTH * 0.55f,
                mix(dark, accent, 0.7f), dark, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), glow)

        // Rounded corners.
        canvas.save()
        canvas.clipRect(0, 0, WIDTH, HEIGHT)

        // The avatar: a circle, from the URL when it loaded, else the first letter.
        val avatarRadius = 110f
        val cx = 56f + avatarRadius
        val cy = 56f + avatarRadius
        if (avatar != null) {
            val shader = android.graphics.BitmapShader(avatar, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val scale = maxOf(avatarRadius * 2 / avatar.width, avatarRadius * 2 / avatar.height)
            val matrix = android.graphics.Matrix()
            matrix.setScale(scale, scale)
            matrix.postTranslate(cx - avatar.width * scale / 2f, cy - avatar.height * scale / 2f)
            shader.setLocalMatrix(matrix)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
            canvas.drawCircle(cx, cy, avatarRadius, paint)
        } else {
            val initial = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
            canvas.drawCircle(cx, cy, avatarRadius, initial)
            val letter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 110f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            val metrics = letter.fontMetrics
            canvas.drawText(card.name.take(1).uppercase(), cx, cy - (metrics.ascent + metrics.descent) / 2f, letter)
        }

        // The name, ellipsized to the width beside the avatar.
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 54f
            typeface = Typeface.DEFAULT_BOLD
        }
        val textLeft = cx + avatarRadius + 36f
        val maxWidth = WIDTH - textLeft - 48f
        canvas.drawText(ellipsize(card.name, namePaint, maxWidth), textLeft, 100f + 54f / 2f, namePaint)

        // The followers under the name.
        card.followers?.let {
            val followersPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(190, 255, 255, 255)
                textSize = 28f
                typeface = Typeface.DEFAULT
            }
            canvas.drawText(it, textLeft, 190f, followersPaint)
        }

        // The top songs, numbered.
        val topPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("TOP", 56f, 330f, topPaint)
        val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
        }
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 34f
            typeface = Typeface.DEFAULT
        }
        card.topTracks.take(3).forEachIndexed { index, title ->
            val y = 350f + 40f + index * 60f
            canvas.drawText("${index + 1}", 56f, y, numberPaint)
            canvas.drawText(ellipsize(title, trackPaint, WIDTH - 56f - 140f), 104f, y, trackPaint)
        }

        // The mark.
        val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 255, 255, 255)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("KittyTune", WIDTH - 40f, HEIGHT - 32f, mark)

        canvas.restore()

        // Round the final bitmap's corners.
        val rounded = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val c = Canvas(rounded)
        c.save()
        c.clipPath(android.graphics.Path().apply {
            addRoundRect(RectF(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat()), 48f, 48f, android.graphics.Path.Direction.CW)
        })
        c.drawBitmap(bitmap, 0f, 0f, null)
        c.restore()
        return rounded
    }

    /** The avatar at [url], or null: shared cards are best-effort, not a loading state. */
    suspend fun fetchAvatar(url: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (url == null) return@withContext null
        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.inputStream.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var cut = text.length
        while (cut > 1 && paint.measureText(text.take(cut) + "…") > maxWidth) cut--
        return text.take(cut) + "…"
    }

    private fun mix(a: Int, b: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt(),
        )
    }
}

/** The followers line, as the card shows it. */
fun followersLabel(followers: Long?): String? =
    followers?.takeIf { it > 0 }?.let { NumberFormat.getNumberInstance().format(it) + " followers" }

/** Saves [bitmap] as a PNG in the cache dir, for the share sheet. */
suspend fun saveCardForSharing(context: android.content.Context, bitmap: Bitmap): File? = withContext(Dispatchers.IO) {
    runCatching {
        val file = File(context.cacheDir, "artist_share_${System.currentTimeMillis()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        file
    }.getOrNull()
}

/**
 * The artist share dialog: a preview of the card plus the choice between the plain link (the default,
 * what most people want to send) and the picture. Sharing a profile must never force the image.
 */
@Composable
fun ArtistShareDialog(
    card: ArtistShare.Card,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(card) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(card) {
        val avatar = ArtistShare.fetchAvatar(card.avatarUrl)
        bitmap = ArtistShare.render(card, avatar)
    }

    fun shareLink() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, card.link)
        }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_via)))
    }

    fun shareCard() {
        val current = bitmap ?: return
        scope.launch {
            val file = saveCardForSharing(context, current) ?: return@launch
            val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, card.link)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, context.getString(R.string.share_via)))
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (bitmap != null) {
                    TextButton(onClick = hapticAction(HapticKind.UI) { shareCard() }) {
                        Text(stringResource(R.string.share_card_send))
                    }
                }
                Button(
                    onClick = hapticAction(HapticKind.UI) {
                        shareLink()
                        onDismiss()
                    }
                ) { Text(stringResource(R.string.share_link)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_close))
            }
        },
        title = { Text(stringResource(R.string.btn_share)) },
        text = {
            bitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            } ?: CircularProgressIndicator()
        },
    )
}
