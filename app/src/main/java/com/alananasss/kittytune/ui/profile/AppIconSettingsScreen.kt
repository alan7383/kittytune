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
    val resId = when (id) {
        "default" -> R.string.icon_name_default
        "black" -> R.string.icon_name_black
        "blue" -> R.string.icon_name_blue
        "chrome" -> R.string.icon_name_chrome
        "hot_pink" -> R.string.icon_name_hot_pink
        "lavender" -> R.string.icon_name_lavender
        "leopard" -> R.string.icon_name_leopard
        "lime" -> R.string.icon_name_lime
        "og" -> R.string.icon_name_og
        "rose_gold" -> R.string.icon_name_rose_gold
        "silver" -> R.string.icon_name_silver
        "soft_purple" -> R.string.icon_name_soft_purple
        "sunset" -> R.string.icon_name_sunset
        "tie_dye" -> R.string.icon_name_tie_dye
        "algeria" -> R.string.icon_name_algeria
        "argentina" -> R.string.icon_name_argentina
        "australia" -> R.string.icon_name_australia
        "austria" -> R.string.icon_name_austria
        "belgium" -> R.string.icon_name_belgium
        "bosnia_herzegovina" -> R.string.icon_name_bosnia_herzegovina
        "brazil" -> R.string.icon_name_brazil
        "canada" -> R.string.icon_name_canada
        "cape_verde" -> R.string.icon_name_cape_verde
        "colombia" -> R.string.icon_name_colombia
        "croatia" -> R.string.icon_name_croatia
        "curacao" -> R.string.icon_name_curacao
        "czechia" -> R.string.icon_name_czechia
        "dr_congo" -> R.string.icon_name_dr_congo
        "ecuador" -> R.string.icon_name_ecuador
        "egypt" -> R.string.icon_name_egypt
        "england" -> R.string.icon_name_england
        "france" -> R.string.icon_name_france
        "germany" -> R.string.icon_name_germany
        "ghana" -> R.string.icon_name_ghana
        "haiti" -> R.string.icon_name_haiti
        "iran" -> R.string.icon_name_iran
        "iraq" -> R.string.icon_name_iraq
        "ivory_coast" -> R.string.icon_name_ivory_coast
        "japan" -> R.string.icon_name_japan
        "jordan" -> R.string.icon_name_jordan
        "mexico" -> R.string.icon_name_mexico
        "morocco" -> R.string.icon_name_morocco
        "netherlands" -> R.string.icon_name_netherlands
        "new_zealand" -> R.string.icon_name_new_zealand
        "norway" -> R.string.icon_name_norway
        "panama" -> R.string.icon_name_panama
        "paraguay" -> R.string.icon_name_paraguay
        "portugal" -> R.string.icon_name_portugal
        "qatar" -> R.string.icon_name_qatar
        "saudi_arabia" -> R.string.icon_name_saudi_arabia
        "scotland" -> R.string.icon_name_scotland
        "senegal" -> R.string.icon_name_senegal
        "south_africa" -> R.string.icon_name_south_africa
        "south_korea" -> R.string.icon_name_south_korea
        "spain" -> R.string.icon_name_spain
        "sweden" -> R.string.icon_name_sweden
        "switzerland" -> R.string.icon_name_switzerland
        "tunisia" -> R.string.icon_name_tunisia
        "turkey" -> R.string.icon_name_turkey
        "united_states" -> R.string.icon_name_united_states
        "uruguay" -> R.string.icon_name_uruguay
        "uzbekistan" -> R.string.icon_name_uzbekistan
        "shiro_headphones" -> R.string.icon_name_shiro_headphones
        "shiro_musical_dream" -> R.string.icon_name_shiro_musical_dream
        "shiro_pastel_cat" -> R.string.icon_name_shiro_pastel_cat
        "shiro_cap_red" -> R.string.icon_name_shiro_cap_red
        "shiro_cap_purple" -> R.string.icon_name_shiro_cap_purple
        "shiro_kt_logo" -> R.string.icon_name_shiro_kt_logo
        "shiro_cozy_nap" -> R.string.icon_name_shiro_cozy_nap
        "shiro_cool_cat" -> R.string.icon_name_shiro_cool_cat
        else -> null
    }
    if (resId != null) return context.getString(resId)
    val res = context.resources.getIdentifier("icon_name_$id", "string", context.packageName)
    return if (res != 0) context.getString(res) else id
}

private fun getAppIconPreviewRes(context: Context, id: String): Int {
    if (id == "default") return R.mipmap.ic_launcher
    val resId = when (id) {
        "black" -> R.mipmap.sc_ic_black
        "blue" -> R.mipmap.sc_ic_blue
        "chrome" -> R.mipmap.sc_ic_chrome
        "hot_pink" -> R.mipmap.sc_ic_hot_pink
        "lavender" -> R.mipmap.sc_ic_lavender
        "leopard" -> R.mipmap.sc_ic_leopard
        "lime" -> R.mipmap.sc_ic_lime
        "og" -> R.mipmap.sc_ic_og
        "rose_gold" -> R.mipmap.sc_ic_rose_gold
        "silver" -> R.mipmap.sc_ic_silver
        "soft_purple" -> R.mipmap.sc_ic_soft_purple
        "sunset" -> R.mipmap.sc_ic_sunset
        "tie_dye" -> R.mipmap.sc_ic_tie_dye
        "algeria" -> R.mipmap.sc_ic_algeria
        "argentina" -> R.mipmap.sc_ic_argentina
        "australia" -> R.mipmap.sc_ic_australia
        "austria" -> R.mipmap.sc_ic_austria
        "belgium" -> R.mipmap.sc_ic_belgium
        "bosnia_herzegovina" -> R.mipmap.sc_ic_bosnia_herzegovina
        "brazil" -> R.mipmap.sc_ic_brazil
        "canada" -> R.mipmap.sc_ic_canada
        "cape_verde" -> R.mipmap.sc_ic_cape_verde
        "colombia" -> R.mipmap.sc_ic_colombia
        "croatia" -> R.mipmap.sc_ic_croatia
        "curacao" -> R.mipmap.sc_ic_curacao
        "czechia" -> R.mipmap.sc_ic_czechia
        "dr_congo" -> R.mipmap.sc_ic_dr_congo
        "ecuador" -> R.mipmap.sc_ic_ecuador
        "egypt" -> R.mipmap.sc_ic_egypt
        "england" -> R.mipmap.sc_ic_england
        "france" -> R.mipmap.sc_ic_france
        "germany" -> R.mipmap.sc_ic_germany
        "ghana" -> R.mipmap.sc_ic_ghana
        "haiti" -> R.mipmap.sc_ic_haiti
        "iran" -> R.mipmap.sc_ic_iran
        "iraq" -> R.mipmap.sc_ic_iraq
        "ivory_coast" -> R.mipmap.sc_ic_ivory_coast
        "japan" -> R.mipmap.sc_ic_japan
        "jordan" -> R.mipmap.sc_ic_jordan
        "mexico" -> R.mipmap.sc_ic_mexico
        "morocco" -> R.mipmap.sc_ic_morocco
        "netherlands" -> R.mipmap.sc_ic_netherlands
        "new_zealand" -> R.mipmap.sc_ic_new_zealand
        "norway" -> R.mipmap.sc_ic_norway
        "panama" -> R.mipmap.sc_ic_panama
        "paraguay" -> R.mipmap.sc_ic_paraguay
        "portugal" -> R.mipmap.sc_ic_portugal
        "qatar" -> R.mipmap.sc_ic_qatar
        "saudi_arabia" -> R.mipmap.sc_ic_saudi_arabia
        "scotland" -> R.mipmap.sc_ic_scotland
        "senegal" -> R.mipmap.sc_ic_senegal
        "south_africa" -> R.mipmap.sc_ic_south_africa
        "south_korea" -> R.mipmap.sc_ic_south_korea
        "spain" -> R.mipmap.sc_ic_spain
        "sweden" -> R.mipmap.sc_ic_sweden
        "switzerland" -> R.mipmap.sc_ic_switzerland
        "tunisia" -> R.mipmap.sc_ic_tunisia
        "turkey" -> R.mipmap.sc_ic_turkey
        "united_states" -> R.mipmap.sc_ic_united_states
        "uruguay" -> R.mipmap.sc_ic_uruguay
        "uzbekistan" -> R.mipmap.sc_ic_uzbekistan
        "shiro_headphones" -> R.mipmap.sc_ic_shiro_headphones
        "shiro_musical_dream" -> R.mipmap.sc_ic_shiro_musical_dream
        "shiro_pastel_cat" -> R.mipmap.sc_ic_shiro_pastel_cat
        "shiro_cap_red" -> R.mipmap.sc_ic_shiro_cap_red
        "shiro_cap_purple" -> R.mipmap.sc_ic_shiro_cap_purple
        "shiro_kt_logo" -> R.mipmap.sc_ic_shiro_kt_logo
        "shiro_cozy_nap" -> R.mipmap.sc_ic_shiro_cozy_nap
        "shiro_cool_cat" -> R.mipmap.sc_ic_shiro_cool_cat
        else -> null
    }
    if (resId != null) return resId
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
