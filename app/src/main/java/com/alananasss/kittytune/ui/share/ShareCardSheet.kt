package com.alananasss.kittytune.ui.share

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.hapticAction
import com.alananasss.kittytune.ui.common.hapticClickable
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
    allLyrics: List<String> = emptyList(),
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

    // Which lines go on the card. Starts at the two around the playhead; the user picks any
    // others below (capped, so the card stays a card and not a wall of text).
    var selectedLineIndices by remember(allLyrics, lyrics) {
        val initial = if (allLyrics.isEmpty()) {
            emptySet()
        } else {
            lyrics.mapNotNull { snippet -> allLyrics.indexOfFirst { it == snippet } }
                .filter { it >= 0 }
                .toSet()
        }
        mutableStateOf(initial)
    }
    val selectedLyrics = remember(allLyrics, selectedLineIndices) {
        allLyrics.filterIndexed { index, _ -> index in selectedLineIndices }
    }

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Captured at the size it is shown. The card is laid out in dp and rendered at the
            // screen's density, so a denser screen yields a larger file rather than a different
            // composition - the proportions hold either way.
            //
            // The wide 4:5 card: almost the full sheet width, so whole lyric
            // lines fit without ellipsis.
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .clip(RoundedCornerShape(20.dp))
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
                    lyrics = selectedLyrics.takeIf { lyricsFace && it.isNotEmpty() } ?: lyrics.takeIf { lyricsFace && allLyrics.isEmpty() && it.isNotEmpty() },
                )
            }

            if (lyrics.isNotEmpty()) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(top = 20.dp)) {
                    SegmentedButton(
                        selected = !lyricsFace,
                        onClick = hapticAction(HapticKind.UI) { lyricsFace = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text(stringResource(R.string.share_card_face_song)) }
                    SegmentedButton(
                        selected = lyricsFace,
                        onClick = hapticAction(HapticKind.UI) { lyricsFace = true },
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
                                .hapticClickable { selected = style }
                        )
                    }
                }
            }

            if (qrCover != null && !lyricsFace) {
                FilterChip(
                    selected = asQr,
                    onClick = hapticAction(HapticKind.UI) { asQr = !asQr },
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

            // Line picker: the lyrics face is only as good as the lines on it, and a fixed
            // two-line snippet around the playhead was too rigid (issue: "card is small, text
            // can't be chosen, font too large"). Tap lines to build the card's text.
            if (lyricsFace && allLyrics.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.share_card_pick_lines),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        // Lyric counter: how many of the five lines are picked.
                        Text(
                            text = "${selectedLineIndices.size}/$MAX_SHARE_LINES",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 190.dp)
                    ) {
                        itemsIndexed(allLyrics, key = { i, line -> "$i-${line.hashCode()}" }) { index, line ->
                            val isSelected = index in selectedLineIndices
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(onClick = hapticAction(HapticKind.UI) {
                                        selectedLineIndices = if (isSelected) {
                                            selectedLineIndices - index
                                        } else if (selectedLineIndices.size < MAX_SHARE_LINES) {
                                            selectedLineIndices + index
                                        } else {
                                            selectedLineIndices
                                        }
                                    })
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            if (lyricsFace && selectedLyrics.isNotEmpty()) {
                OutlinedButton(
                    onClick = hapticAction(HapticKind.UI) {
                        val text = buildString {
                            selectedLyrics.forEach { appendLine(it) }
                            appendLine()
                            append(title)
                            append(" — ")
                            append(artist)
                            if (!trackUrl.isNullOrBlank()) {
                                appendLine()
                                append(trackUrl)
                            }
                        }
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(
                            android.content.Intent.createChooser(
                                intent,
                                context.getString(R.string.share_via)
                            )
                        )
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FormatQuote,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.share_card_as_text))
                }
            }

            Button(
                onClick = hapticAction(HapticKind.UI) {
                    if (busy) return@hapticAction
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

// Line cap: five lines is the most a wide card carries while staying a card.
private const val MAX_SHARE_LINES = 5

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
