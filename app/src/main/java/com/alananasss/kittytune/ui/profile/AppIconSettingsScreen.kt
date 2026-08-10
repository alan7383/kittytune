package com.alananasss.kittytune.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(AppIconSwitcher.ICON_IDS) { id ->
                val selected = id == appIcon
                FilledTonalButton(
                    onClick = {
                        appIcon = id
                        prefs.setAppIconId(id)
                        AppIconSwitcher.applyIcon(context, id)
                    },
                    modifier = Modifier.fillMaxWidth(),
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
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val iconBitmap = rememberAdaptiveIconBitmap(context, id)
                        if (iconBitmap != null) {
                            Image(
                                bitmap = iconBitmap,
                                contentDescription = null,
                                modifier = Modifier.size(52.dp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = getAppIconDisplayName(context, id),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
