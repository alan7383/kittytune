package com.alananasss.kittytune.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.hapticAction
import com.alananasss.kittytune.ui.common.hapticClickable
import com.alananasss.kittytune.data.local.AppIconSwitcher
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import com.alananasss.kittytune.ui.common.SettingsScaffold

enum class AppIconCategory {
    OFFICIAL,
    COMMUNITY
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppIconSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    var appIcon by remember { mutableStateOf(prefs.getAppIconId()) }

    var selectedCategory by remember {
        mutableStateOf(
            if (AppIconSwitcher.isCommunity(appIcon)) AppIconCategory.COMMUNITY
            else AppIconCategory.OFFICIAL
        )
    }

    SettingsScaffold(
        title = stringResource(R.string.pref_app_icon_title),
        onBackClick = onBackClick
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                ExpressiveConnectedButtonGroup(
                    options = listOf(AppIconCategory.OFFICIAL, AppIconCategory.COMMUNITY),
                    selectedOption = selectedCategory,
                    onOptionSelected = { selectedCategory = it },
                    modifier = Modifier.fillMaxWidth(),
                    labelProvider = { category ->
                        Text(
                            text = when (category) {
                                AppIconCategory.OFFICIAL -> stringResource(R.string.icon_category_official)
                                AppIconCategory.COMMUNITY -> stringResource(R.string.icon_category_community)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    iconProvider = { category ->
                        Icon(
                            imageVector = when (category) {
                                AppIconCategory.OFFICIAL -> Icons.Rounded.Apps
                                AppIconCategory.COMMUNITY -> Icons.Rounded.Palette
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }

            AnimatedContent(
                targetState = selectedCategory,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "icon_category_transition"
            ) { category ->
                val iconList = when (category) {
                    AppIconCategory.OFFICIAL -> AppIconSwitcher.OFFICIAL_ICON_IDS
                    AppIconCategory.COMMUNITY -> AppIconSwitcher.COMMUNITY_ICON_IDS
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 180.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(iconList, key = { it }) { id ->
                        val selected = id == appIcon
                        val author = AppIconSwitcher.getAuthor(id)

                        FilledTonalButton(
                            onClick = hapticAction(HapticKind.UI) {
                                appIcon = id
                                prefs.setAppIconId(id)
                                AppIconSwitcher.applyIcon(context, id)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 76.dp),
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
                                    Surface(
                                        shape = CircleShape,
                                        shadowElevation = 1.dp,
                                        color = Color.Transparent,
                                        modifier = Modifier.size(46.dp)
                                    ) {
                                        Image(
                                            bitmap = iconBitmap,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = getAppIconDisplayName(context, id),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (author != null) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = stringResource(R.string.icon_by_artist, author),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
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
        val drawable = ContextCompat.getDrawable(context, res)
        val sizePx = (72 * context.resources.displayMetrics.density).toInt()
        if (drawable is AdaptiveIconDrawable) {
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        } else if (drawable != null) {
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        } else {
            null
        }
    }
}
