package com.alananasss.kittytune.ui.profile

import android.content.Context
import android.content.res.Configuration
import java.text.Normalizer
import java.util.Locale
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
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
 * Returns a Context configured for English if the current app locale is not English,
 * enabling automatic bilingual (localized + English) indexing for all settings.
 */
internal fun getEnglishContext(context: Context): Context? {
    val locales = context.resources.configuration.locales
    if (!locales.isEmpty && locales[0].language.equals("en", ignoreCase = true)) {
        return null
    }
    return try {
        val config = Configuration(context.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        }
        context.createConfigurationContext(config)
    } catch (_: Exception) {
        null
    }
}

/**
 * One searchable item representation with direct action or route.
 * Keywords, localized tags, and cross-language searchCorpus are normalized
 * at construction time to make keystroke filtering completely zero-allocation and instant.
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
    @StringRes val keywordsRes: Int? = null,
    val hasSwitch: Boolean = false,
    val switchState: Boolean = false,
    val onSwitchChange: ((Boolean) -> Unit)? = null,
    val onClick: (() -> Unit)? = null,
    val altTitles: List<String> = emptyList(),
    val altSubtitles: List<String> = emptyList(),
    val localizedKeywords: List<String> = emptyList()
) {
    val normTitle: String = normalizeSearchText(title)
    val normKeywords: List<String> = (keywords + localizedKeywords).map { normalizeSearchText(it) }
    val searchCorpus: String = buildString {
        append(normTitle).append(' ')
        subtitle?.let { append(normalizeSearchText(it)).append(' ') }
        append(normalizeSearchText(categoryName)).append(' ')
        for (nkw in normKeywords) {
            append(nkw).append(' ')
        }
        for (alt in altTitles) {
            append(normalizeSearchText(alt)).append(' ')
        }
        for (altSub in altSubtitles) {
            append(normalizeSearchText(altSub)).append(' ')
        }
    }
}

/**
 * Helper to build a [SearchSettingEntry] with automatic cross-language (English) indexing
 * and localized keywords resolution.
 */
internal fun createSearchEntry(
    context: Context,
    englishContext: Context?,
    @StringRes titleRes: Int,
    @StringRes subtitleRes: Int? = null,
    categoryName: String,
    icon: ImageVector? = null,
    @DrawableRes iconRes: Int? = null,
    route: String? = null,
    highlightKey: String? = null,
    @StringRes keywordsRes: Int? = null,
    keywords: List<String> = emptyList(),
    hasSwitch: Boolean = false,
    switchState: Boolean = false,
    onSwitchChange: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null
): SearchSettingEntry {
    val title = context.getString(titleRes)
    val subtitle = subtitleRes?.let { context.getString(it) }

    val altTitles = if (englishContext != null) {
        listOfNotNull(runCatching { englishContext.getString(titleRes) }.getOrNull()).filter { it != title }
    } else emptyList()

    val altSubtitles = if (englishContext != null && subtitleRes != null) {
        listOfNotNull(runCatching { englishContext.getString(subtitleRes) }.getOrNull()).filter { it != subtitle }
    } else emptyList()

    val localizedKeywords = buildList {
        keywordsRes?.let { kRes ->
            runCatching { context.getString(kRes) }.getOrNull()?.let { raw ->
                addAll(raw.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() })
            }
            if (englishContext != null) {
                runCatching { englishContext.getString(kRes) }.getOrNull()?.let { raw ->
                    addAll(raw.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() })
                }
            }
        }
    }

    return SearchSettingEntry(
        title = title,
        subtitle = subtitle,
        categoryName = categoryName,
        icon = icon,
        iconRes = iconRes,
        route = route,
        highlightKey = highlightKey,
        keywords = keywords,
        keywordsRes = keywordsRes,
        hasSwitch = hasSwitch,
        switchState = switchState,
        onSwitchChange = onSwitchChange,
        onClick = onClick,
        altTitles = altTitles,
        altSubtitles = altSubtitles,
        localizedKeywords = localizedKeywords
    )
}

/**
 * Declarative definition of an application setting.
 *
 * Each setting is declared as a typed data object specifying its identity, strings,
 * category, icons, navigation route and optional keywords.
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
    @get:StringRes val keywordsRes: Int?

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
        @StringRes override val keywordsRes: Int? = null,
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
        @StringRes override val keywordsRes: Int? = null,
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
        @StringRes override val keywordsRes: Int? = null,
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
    englishContext: Context? = null,
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

    val altTitles = if (englishContext != null) {
        listOfNotNull(runCatching { englishContext.getString(titleRes) }.getOrNull()).filter { it != title }
    } else emptyList()

    val altSubtitles = if (englishContext != null && subtitleRes != null) {
        listOfNotNull(runCatching { englishContext.getString(subtitleRes!!) }.getOrNull()).filter { it != subtitle }
    } else emptyList()

    val localizedKeywords = buildList {
        keywordsRes?.let { kRes ->
            runCatching { context.getString(kRes) }.getOrNull()?.let { raw ->
                addAll(raw.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() })
            }
            if (englishContext != null) {
                runCatching { englishContext.getString(kRes) }.getOrNull()?.let { raw ->
                    addAll(raw.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() })
                }
            }
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
                keywordsRes = keywordsRes,
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
                },
                altTitles = altTitles,
                altSubtitles = altSubtitles,
                localizedKeywords = localizedKeywords
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
                keywords = keywords,
                keywordsRes = keywordsRes,
                altTitles = altTitles,
                altSubtitles = altSubtitles,
                localizedKeywords = localizedKeywords
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
                keywordsRes = keywordsRes,
                onClick = {
                    com.alananasss.kittytune.ui.common.SettingsHighlightManager.setHighlightKey(id)
                    if (onClick != null) {
                        onClick.invoke(context) { dest -> navController.navigate(dest) }
                    } else {
                        navController.navigate(route)
                    }
                },
                altTitles = altTitles,
                altSubtitles = altSubtitles,
                localizedKeywords = localizedKeywords
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
        icon = Icons.Rounded.PlayCircle,
        keywordsRes = com.alananasss.kittytune.R.string.keywords_player
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
        keywordsRes = com.alananasss.kittytune.R.string.keywords_mini_player_swipe,
        keywords = listOf("mini", "player", "swipe", "gesture", "track", "skip", "dismiss")
    ),
    SettingDefinition.Action(
        id = "pref_full_player_source",
        titleRes = com.alananasss.kittytune.R.string.pref_full_player_source_title,
        subtitleRes = com.alananasss.kittytune.R.string.pref_full_player_source_desc,
        category = SettingsCategory.INTERFACE,
        route = "player_design_settings",
        icon = Icons.Rounded.GraphicEq,
        keywords = listOf("audio", "source", "badge", "indicator", "youtube", "soundcloud", "deezer", "tidal", "qobuz", "logo")
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

