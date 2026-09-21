package com.alananasss.kittytune.ui.share

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.theme.ArtworkPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Preview of the share card, with the styles the artwork allows, and a button to send it.
 *
 * The user sees the card before it leaves the app. That matters more here than in most sheets:
 * what is shared carries their name by association, and a card that crops the artwork badly or
 * picks an unreadable colour is not something to discover afterwards in someone else's feed.
 */
@Composable
fun ShareCardSheet(
    artwork: Bitmap?,
    title: String,
    artist: String,
    trackId: Long,
    trackUrl: String?,
    lyrics: List<String> = emptyList(),
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val graphicsLayer = rememberGraphicsLayer()

    val styles = remember(artwork) { artwork.toShareCardStyles() }
    var selected by remember(styles) { mutableStateOf(styles.first()) }
    var busy by remember { mutableStateOf(false) }
    var asQr by remember { mutableStateOf(false) }
    var lyricsFace by remember(lyrics) { mutableStateOf(false) }
    var qrCover by remember(artwork, trackUrl) { mutableStateOf<Bitmap?>(null) }

    // Rendered once per track rather than on every toggle: the encode and the per-module draw
    // are cheap individually but add up to a visible stutter if they run on each tap. Off the
    // main thread, since both touch the artwork bitmap.
    LaunchedEffect(artwork, trackUrl) {
        qrCover = trackUrl?.takeIf { it.isNotBlank() }?.let { url ->
            withContext(Dispatchers.Default) {
                val prefs = PlayerPreferences(context)
                val style = when (prefs.getShareCardCodeMode()) {
                    1 -> QrCoverRenderer.CoverCodeStyle.SOLID
                    2 -> QrCoverRenderer.CoverCodeStyle.HALFTONE
                    else -> QrCoverRenderer.chooseStyle(artwork)
                }
                QrCoverRenderer.render(content = url, cover = artwork, sizePx = 1024, style = style)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Captured at the size it is shown. The card is laid out in dp and rendered at the
            // screen's density, so a denser screen yields a larger file rather than a different
            // composition - the proportions hold either way.
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .clip(RoundedCornerShape(18.dp))
                    .drawWithContent {
                        graphicsLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(graphicsLayer)
                    }
            ) {
                ShareCard(
                    artwork = artwork,
                    title = title,
                    artist = artist,
                    style = selected,
                    modifier = Modifier.fillMaxWidth(),
                    qrCover = qrCover.takeIf { asQr && !lyricsFace },
                    lyrics = lyrics.takeIf { lyricsFace && it.isNotEmpty() },
                )
            }

            if (lyrics.isNotEmpty()) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(top = 20.dp)) {
                    SegmentedButton(
                        selected = !lyricsFace,
                        onClick = { lyricsFace = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text(stringResource(R.string.share_card_face_song)) }
                    SegmentedButton(
                        selected = lyricsFace,
                        onClick = { lyricsFace = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text(stringResource(R.string.share_card_face_lyrics)) }
                }
            }

            if (styles.size > 1) {
                Row(
                    modifier = Modifier.padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    styles.forEach { style ->
                        val isSelected = style == selected
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(style.background)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = CircleShape,
                                )
                                .clickable { selected = style }
                        )
                    }
                }
            }

            if (qrCover != null && !lyricsFace) {
                FilterChip(
                    selected = asQr,
                    onClick = { asQr = !asQr },
                    label = { Text(stringResource(R.string.share_card_qr)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.QrCode2,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            Button(
                onClick = {
                    if (busy) return@Button
                    busy = true
                    scope.launch {
                        val uri = ShareCardExport.writeCard(context, graphicsLayer, trackId)
                        busy = false
                        if (uri != null) {
                            ShareCardExport.share(
                                context = context,
                                cardUri = uri,
                                trackUrl = trackUrl,
                                chooserTitle = context.getString(R.string.share_via),
                            )
                            onDismiss()
                        }
                    }
                },
                enabled = !busy,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
            ) {
                Text(stringResource(R.string.share_card_send))
            }
        }
    }

    LaunchedEffect(Unit) {
        ShareCardExport.clearOlderThan(context, keep = null)
    }
}

/**
 * Derives the offered styles from the artwork.
 *
 * [ArtworkPalette.meshPalette] returns its shades brightest first with the darkest last, which
 * is the order this wants anyway: the darkest shade is the one a cover usually sits best on, so
 * it leads, and the brighter ones follow for covers that need the contrast the other way round.
 * Text colour is chosen per shade by luminance rather than fixed, since a pale shade with white
 * text on it is exactly the unreadable card this is meant to avoid.
 */
private fun Bitmap?.toShareCardStyles(): List<ShareCardStyle> {
    val bitmap = this ?: return listOf(ShareCardStyle.Neutral)
    val shades = try {
        ArtworkPalette.meshPalette(bitmap, count = 4)
    } catch (_: Exception) {
        emptyList()
    }
    if (shades.isEmpty()) return listOf(ShareCardStyle.Neutral)

    return shades
        .asReversed()
        .map { argb ->
            val color = Color(argb)
            ShareCardStyle(
                background = color,
                onBackground = if (color.isLight()) Color(0xFF101010) else Color.White,
            )
        }
        .distinctBy { it.background.value }
}

/** Whether text on this colour should be dark. Rec. 709 luma, the usual weighting for this. */
private fun Color.isLight(): Boolean = (0.2126f * red + 0.7152f * green + 0.0722f * blue) > 0.6f
