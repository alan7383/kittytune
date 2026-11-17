package com.alananasss.kittytune.ui.profile.passport

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import java.io.FileOutputStream
import kotlin.math.min
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.hapticAction
import com.alananasss.kittytune.ui.profile.ArtistShare
import com.alananasss.kittytune.ui.profile.saveCardForSharing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Music Passport: the whole listening history as a shareable passport. The preview is the very
 * image that gets shared — there is one renderer, so what the listener sees is exactly what lands in
 * the share sheet or the gallery.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPassportScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: MusicPassportViewModel = viewModel()
    val passport = viewModel.passport

    val accent = MaterialTheme.colorScheme.primary.toArgb()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }

    // Re-render whenever the passport data or the theme accent changes. The avatar fills in a moment
    // after the rest, which simply re-runs this.
    LaunchedEffect(passport, accent) {
        val card = passport ?: return@LaunchedEffect
        bitmap = withContext(Dispatchers.Default) {
            val avatar = ArtistShare.fetchAvatar(card.avatarUrl)
            MusicPassportRender.render(context, card.copy(accentArgb = accent), avatar)
        }
    }
    // The card settles in once drawn rather than popping over the spinner.
    val appear by animateFloatAsState(
        targetValue = if (bitmap != null) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow),
        label = "passportAppear",
    )

    fun share() {
        val current = bitmap ?: return
        scope.launch {
            val file = saveCardForSharing(context, current) ?: return@launch
            val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, context.getString(R.string.share_via)))
        }
    }

    fun saveToGallery() {
        val current = bitmap ?: return
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) { savePassportToGallery(context, current) }
            busy = false
            Toast.makeText(
                context,
                context.getString(if (ok) R.string.passport_saved else R.string.passport_save_failed),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    /** Puts the PNG itself on the clipboard, so it can be pasted straight into a chat. */
    fun copyImage() {
        val current = bitmap ?: return
        scope.launch {
            val file = saveCardForSharing(context, current) ?: return@launch
            val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
            val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return@launch
            clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, context.getString(R.string.passport_title), uri))
            // Android 13+ already confirms a copy with its own overlay.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(context, R.string.passport_image_copied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Opens the passport in whichever photo viewer the device uses. */
    fun openImage() {
        val current = bitmap ?: return
        scope.launch {
            val file = saveCardForSharing(context, current) ?: return@launch
            val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
            val view = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/png")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(view)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, R.string.passport_open_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun print() {
        val current = bitmap ?: return
        if (!printPassport(context, current)) {
            Toast.makeText(context, R.string.passport_print_failed, Toast.LENGTH_SHORT).show()
        }
    }

    fun copyMrz() {
        val card = passport ?: return
        val (line1, line2) = MusicPassportRender.mrzLines(card)
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("MRZ", "$line1\n$line2"))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, R.string.passport_mrz_copied, Toast.LENGTH_SHORT).show()
        }
    }

    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.passport_title), fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(
                        onClick = hapticAction(HapticKind.UI) { onBackClick() },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.btn_close))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .aspectRatio(1080f / 1920f),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = bitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = stringResource(R.string.passport_title),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = appear
                                val scale = 0.92f + 0.08f * appear
                                scaleX = scale
                                scaleY = scale
                                translationY = (1f - appear) * 48.dp.toPx()
                            }
                            .shadow(18.dp, RoundedCornerShape(28.dp))
                            .clip(RoundedCornerShape(28.dp)),
                    )
                } else {
                    CircularProgressIndicator()
                }
            }

            Text(
                text = stringResource(R.string.passport_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            Row(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .padding(bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = hapticAction(HapticKind.UI) { saveToGallery() },
                    shapes = ButtonDefaults.shapes(),
                    enabled = bitmap != null && !busy,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.passport_save_image),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                // Same split button as on desktop: sharing stays one tap away (it is what a phone is
                // for), and the arrow holds the other ways out — copy, print, open, MRZ.
                Box(modifier = Modifier.weight(1f)) {
                    val height = SplitButtonDefaults.MediumContainerHeight
                    SplitButtonLayout(
                        modifier = Modifier.fillMaxWidth(),
                        leadingButton = {
                            SplitButtonDefaults.LeadingButton(
                                onClick = hapticAction(HapticKind.UI) { share() },
                                enabled = bitmap != null,
                                shapes = SplitButtonDefaults.leadingButtonShapesFor(height),
                                contentPadding = SplitButtonDefaults.leadingButtonContentPaddingFor(height),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(height),
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.btn_share), maxLines = 1)
                            }
                        },
                        trailingButton = {
                            SplitButtonDefaults.TrailingButton(
                                checked = menuExpanded,
                                onCheckedChange = { menuExpanded = it },
                                enabled = bitmap != null,
                                shapes = SplitButtonDefaults.trailingButtonShapesFor(height),
                                contentPadding = SplitButtonDefaults.trailingButtonContentPaddingFor(height),
                                modifier = Modifier.height(height),
                            ) {
                                Icon(
                                    Icons.Rounded.ArrowDropDown,
                                    contentDescription = stringResource(R.string.passport_more_options),
                                    modifier = Modifier.size(SplitButtonDefaults.trailingButtonIconSizeFor(height)),
                                )
                            }
                        },
                    )

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        PassportMenuItem(Icons.Rounded.ContentCopy, stringResource(R.string.passport_copy_image)) {
                            menuExpanded = false
                            copyImage()
                        }
                        PassportMenuItem(Icons.Rounded.Print, stringResource(R.string.passport_print)) {
                            menuExpanded = false
                            print()
                        }
                        PassportMenuItem(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(R.string.passport_open_image)) {
                            menuExpanded = false
                            openImage()
                        }
                        PassportMenuItem(Icons.Rounded.Badge, stringResource(R.string.passport_copy_mrz)) {
                            menuExpanded = false
                            copyMrz()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PassportMenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        onClick = hapticAction(HapticKind.UI) { onClick() },
    )
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Hands the passport to the system print dialog, one page, fitted and centred. */
private fun printPassport(context: Context, bitmap: Bitmap): Boolean {
    val activity = context.findActivity()
    if (activity == null) {
        android.util.Log.e("MusicPassport", "findActivity returned null!")
        return false
    }

    // Android's PrintManager checks `if (!(mContext instanceof Activity))` at print() time.
    // When an app wraps base context in attachBaseContext() via createConfigurationContext()
    // (for runtime localization), getSystemService(PRINT_SERVICE) on the wrapped context receives
    // the configuration ContextImpl instead of the Activity itself, triggering IllegalStateException.
    // Querying MainActivity's pristine rawBaseContext directly provides a PrintManager legitimately
    // bound to the Activity without any hidden API reflection.
    val printManager = (activity as? com.alananasss.kittytune.MainActivity)?.getPrintManager()
        ?: activity.getSystemService(PrintManager::class.java)
        ?: return false

    return try {
        val jobName = activity.getString(R.string.passport_title)
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asPortrait())
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build()
        val job = printManager.print(jobName, PassportPrintAdapter(activity, bitmap, jobName), attributes)
        job != null
    } catch (t: Throwable) {
        android.util.Log.e("MusicPassport", "printPassport threw exception", t)
        false
    }
}

private class PassportPrintAdapter(
    private val context: Context,
    private val bitmap: Bitmap,
    private val name: String,
) : PrintDocumentAdapter() {
    private var attributes: PrintAttributes? = null

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        attributes = newAttributes
        val info = PrintDocumentInfo.Builder("$name.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_PHOTO)
            .setPageCount(1)
            .build()
        callback.onLayoutFinished(info, newAttributes != oldAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        val pdf = PrintedPdfDocument(context, attributes ?: return callback.onWriteFailed(null))
        try {
            val page = pdf.startPage(0)
            val area = page.info.contentRect
            val scale = min(area.width().toFloat() / bitmap.width, area.height().toFloat() / bitmap.height)
            val w = bitmap.width * scale
            val h = bitmap.height * scale
            val left = area.left + (area.width() - w) / 2f
            val top = area.top + (area.height() - h) / 2f
            page.canvas.drawBitmap(bitmap, null, RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
            pdf.finishPage(page)
            if (cancellationSignal?.isCanceled == true) {
                callback.onWriteCancelled()
                return
            }
            pdf.writeTo(FileOutputStream(destination.fileDescriptor))
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        } finally {
            pdf.close()
        }
    }
}

/** Writes the passport PNG into the device's Pictures via MediaStore. */
private fun savePassportToGallery(context: android.content.Context, bitmap: Bitmap): Boolean = runCatching {
    val name = "KittyTune_Passport_${System.currentTimeMillis()}.png"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/KittyTune")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        ?: return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }
    true
}.getOrDefault(false)
