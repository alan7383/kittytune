package com.alananasss.kittytune.ui.profile

import android.content.Context
import java.text.Normalizer
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import com.alananasss.kittytune.data.local.PlayerPreferences

internal val DIACRITICS_REGEX = "\\p{M}+".toRegex()
internal val WHITESPACE_REGEX = "\\s+".toRegex()

internal fun normalizeSearchText(input: String): String {
    val nfd = Normalizer.normalize(input, Normalizer.Form.NFD)
    return DIACRITICS_REGEX.replace(nfd, "").lowercase().trim()
}

/**
 * One searchable item representation with direct action or route.
 * Keywords and searchCorpus are normalized at construction time to make
 * keystroke filtering completely zero-allocation and instant.
 */
internal data class SearchSettingEntry(
    val title: String,
    val subtitle: String? = null,
    val categoryName: String,
    val icon: ImageVector? = null,
    @DrawableRes val iconRes: Int? = null,
    val route: String? = null,
    val highlightKey: String? = null,
    val keywords: List<String> = emptyList(),
    val hasSwitch: Boolean = false,
    val switchState: Boolean = false,
    val onSwitchChange: ((Boolean) -> Unit)? = null,
    val onClick: (() -> Unit)? = null,
    val normTitle: String = normalizeSearchText(title),
    val normKeywords: List<String> = keywords.map { normalizeSearchText(it) },
    val searchCorpus: String = buildString {
        append(normTitle).append(' ')
        subtitle?.let { append(normalizeSearchText(it)).append(' ') }
        append(normalizeSearchText(categoryName)).append(' ')
        for (nkw in normKeywords) {
            append(nkw).append(' ')
        }
    }
)

/**
 * Declarative definition of an application setting.
 *
 * Each setting is declared as a typed data object specifying its identity, strings,
 * category, icons, navigation route and optional keywords.
 *
 * This provides a Single Source of Truth:
 * - Search bar indexes all registered settings automatically.
 * - Screen composables can render or read settings directly from definitions.
 * - Keywords are 100% optional since title, subtitle, and category are indexed automatically.
 */
internal sealed interface SettingDefinition {
    val id: String
    @get:StringRes val titleRes: Int
    @get:StringRes val subtitleRes: Int?
    val category: SettingsCategory
    val route: String
    val icon: ImageVector?
    @get:DrawableRes val iconRes: Int?
    val keywords: List<String>

    /**
     * A boolean preference toggle (switch).
     */
    data class Switch(
        override val id: String,
        @StringRes override val titleRes: Int,
        @StringRes override val subtitleRes: Int? = null,
        override val category: SettingsCategory,
        override val route: String,
        override val icon: ImageVector? = null,
        @DrawableRes override val iconRes: Int? = null,
        val get: (PlayerPreferences) -> Boolean,
        val set: (PlayerPreferences, Boolean) -> Unit,
        val onToggleIntercept: ((context: Context, targetState: Boolean, fallbackSet: (Boolean) -> Unit, navigate: (String) -> Unit) -> Unit)? = null,
        override val keywords: List<String> = emptyList()
    ) : SettingDefinition

    /**
     * A navigable entry row that opens a sub-screen.
     */
    data class Navigation(
        override val id: String,
        @StringRes override val titleRes: Int,
        @StringRes override val subtitleRes: Int? = null,
        override val category: SettingsCategory,
        override val route: String,
        override val icon: ImageVector? = null,
        @DrawableRes override val iconRes: Int? = null,
        override val keywords: List<String> = emptyList()
    ) : SettingDefinition

    /**
     * An informational or action setting row (e.g. slider, dialog trigger).
     */
    data class Action(
        override val id: String,
        @StringRes override val titleRes: Int,
        @StringRes override val subtitleRes: Int? = null,
        override val category: SettingsCategory,
        override val route: String,
        override val icon: ImageVector? = null,
        @DrawableRes override val iconRes: Int? = null,
        val formatTitleArgs: ((PlayerPreferences) -> Array<Any>)? = null,
        val formatSubtitleArgs: ((PlayerPreferences) -> Array<Any>)? = null,
        val onClick: ((context: Context, navigate: (String) -> Unit) -> Unit)? = null,
        override val keywords: List<String> = emptyList()
    ) : SettingDefinition
}

/**
 * Converts a [SettingDefinition] to a runtime [SearchSettingEntry] with live states and localized strings.
 */
internal fun SettingDefinition.toSearchSettingEntry(
    context: Context,
    prefs: PlayerPreferences,
    navController: NavController,
    onPreferenceChanged: (() -> Unit)? = null
): SearchSettingEntry {
    val categoryName = context.getString(category.titleRes)
    val title = when (this) {
        is SettingDefinition.Action -> {
            val args = formatTitleArgs?.invoke(prefs)
            if (args != null) context.getString(titleRes, *args) else context.getString(titleRes)
        }
        else -> context.getString(titleRes)
    }
    val subtitle = subtitleRes?.let { resId ->
        when (this) {
            is SettingDefinition.Action -> {
                val args = formatSubtitleArgs?.invoke(prefs)
                if (args != null) context.getString(resId, *args) else context.getString(resId)
            }
            else -> context.getString(resId)
        }
    }

    return when (this) {
        is SettingDefinition.Switch -> {
            SearchSettingEntry(
                title = title,
                subtitle = subtitle,
                categoryName = categoryName,
                icon = icon,
                iconRes = iconRes,
                route = route,
                highlightKey = id,
                keywords = keywords,
                hasSwitch = true,
                switchState = get(prefs),
                onSwitchChange = { targetState ->
                    if (onToggleIntercept != null) {
                        onToggleIntercept.invoke(
                            context,
                            targetState,
                            { set(prefs, it); onPreferenceChanged?.invoke() },
                            { dest -> navController.navigate(dest) }
                        )
                    } else {
                        set(prefs, targetState)
                        onPreferenceChanged?.invoke()
                    }
                }
            )
        }
        is SettingDefinition.Navigation -> {
            SearchSettingEntry(
                title = title,
                subtitle = subtitle,
                categoryName = categoryName,
                icon = icon,
                iconRes = iconRes,
                route = route,
                highlightKey = id,
                keywords = keywords
            )
        }
        is SettingDefinition.Action -> {
            SearchSettingEntry(
                title = title,
                subtitle = subtitle,
                categoryName = categoryName,
                icon = icon,
                iconRes = iconRes,
                route = route,
                highlightKey = id,
                keywords = keywords,
                onClick = {
                    com.alananasss.kittytune.ui.common.SettingsHighlightManager.setHighlightKey(id)
                    if (onClick != null) {
                        onClick.invoke(context) { dest -> navController.navigate(dest) }
                    } else {
                        navController.navigate(route)
                    }
                }
            )
        }
    }
}

internal val PlayerCustomizationSettingDefinitions: List<SettingDefinition> = listOf(
    SettingDefinition.Navigation(
        id = "player_design_page",
        titleRes = com.alananasss.kittytune.R.string.pref_player_design,
        subtitleRes = com.alananasss.kittytune.R.string.settings_page_player_sub,
        category = SettingsCategory.INTERFACE,
        route = "player_design_settings",
        icon = Icons.Rounded.PlayCircle
    ),
    SettingDefinition.Action(
        id = "notif_player_extra_button",
        titleRes = com.alananasss.kittytune.R.string.pref_notif_extra_button_title,
        subtitleRes = com.alananasss.kittytune.R.string.pref_notif_extra_button_subtitle,
        category = SettingsCategory.INTERFACE,
        route = "player_design_settings",
        iconRes = com.alananasss.kittytune.R.drawable.ic_heart_broken,
        keywords = listOf("notification", "dislike", "like", "block", "extra", "button", "player", "notif")
    ),
    SettingDefinition.Action(
        id = "mini_player_swipe_action",
        titleRes = com.alananasss.kittytune.R.string.pref_mini_player_swipe_action_title,
        subtitleRes = com.alananasss.kittytune.R.string.pref_mini_player_title,
        category = SettingsCategory.INTERFACE,
        route = "bottom_bar_settings",
        icon = Icons.Rounded.PlayCircle,
        keywords = listOf("mini", "player", "swipe", "gesture", "track", "skip", "next", "previous", "dismiss", "chanson", "morceau", "balayage")
    )
)

/**
 * Central registry gathering declarative definitions across the app.
 */
internal object SettingsRegistry {
    /**
     * All registered settings definitions.
     * To register new settings from any module, simply append the module's definition list below.
     */
    val allDefinitions: List<SettingDefinition>
        get() = buildList {
            addAll(ContentFilterSettingDefinitions)
            addAll(PlayerCustomizationSettingDefinitions)
        }
}

