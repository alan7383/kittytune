package com.alananasss.kittytune.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.AppIconSwitcher
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.SettingsScaffold

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppIconSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    var appIcon by remember { mutableStateOf(prefs.getAppIconId()) }

    SettingsScaffold(
        title = stringResource(R.string.pref_app_icon_title),
        onBackClick = onBackClick
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 180.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(AppIconSwitcher.ICON_IDS) { id ->
                val selected = id == appIcon
                FilledTonalButton(
                    onClick = {
                        appIcon = id
                        prefs.setAppIconId(id)
                        AppIconSwitcher.applyIcon(context, id)
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        contentColor = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val iconBitmap = rememberAdaptiveIconBitmap(context, id)
                        if (iconBitmap != null) {
                            Image(
                                bitmap = iconBitmap,
                                contentDescription = null,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = getAppIconDisplayName(context, id),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                        )
                        Spacer(Modifier.weight(1f))
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

fun getAppIconDisplayName(context: Context, id: String): String {
    val res = context.resources.getIdentifier("icon_name_$id", "string", context.packageName)
    return if (res != 0) context.getString(res) else id
}

private fun getAppIconPreviewRes(context: Context, id: String): Int {
    if (id == "default") return R.mipmap.ic_launcher
    val res = context.resources.getIdentifier("sc_ic_$id", "mipmap", context.packageName)
    return if (res != 0) res else R.mipmap.ic_launcher
}

@Composable
private fun rememberAdaptiveIconBitmap(context: Context, id: String): ImageBitmap? {
    val res = getAppIconPreviewRes(context, id)
    return remember(res) {
        val drawable = ContextCompat.getDrawable(context, res) as? AdaptiveIconDrawable
        drawable?.let { d ->
            val sizePx = (64 * context.resources.displayMetrics.density).toInt()
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            d.setBounds(0, 0, sizePx, sizePx)
            d.draw(canvas)
            bitmap.asImageBitmap()
        }
    }
}
