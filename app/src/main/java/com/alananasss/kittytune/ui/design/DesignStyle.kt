package com.alananasss.kittytune.ui.design

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.core.content.edit
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.glass.GlassPreferences
import com.alananasss.kittytune.ui.glass.LocalGlassSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The three design languages the app can be drawn in. Exactly one is active: picking MIUIX turns
 * Liquid Glass off and the other way round, so components never mix two systems.
 */
enum class DesignStyle(@param:StringRes val title: Int, @param:StringRes val summary: Int) {
    MATERIAL3(R.string.design_style_material, R.string.design_style_material_desc),
    LIQUID_GLASS(R.string.design_style_glass, R.string.design_style_glass_desc),
    MIUIX(R.string.design_style_miuix, R.string.design_style_miuix_desc),
}

/** The active design language. */
@Composable
fun currentDesignStyle(): DesignStyle = when {
    LocalGlassSettings.current.enabled -> DesignStyle.LIQUID_GLASS
    LocalMiuixSettings.current.enabled -> DesignStyle.MIUIX
    else -> DesignStyle.MATERIAL3
}

/** Switches the whole app to [style]. */
fun setDesignStyle(context: Context, style: DesignStyle) {
    GlassPreferences.update(context) { it.copy(enabled = style == DesignStyle.LIQUID_GLASS) }
    MiuixPreferences.update(context) { it.copy(enabled = style == DesignStyle.MIUIX) }
}

/**
 * The parts of the interface MIUIX can draw. Like Liquid Glass, every part is its own switch.
 */
enum class MiuixComponent(
    val key: String,
    @param:StringRes val title: Int,
    @param:StringRes val summary: Int,
    val group: MiuixComponentGroup,
    val defaultEnabled: Boolean = true,
) {
    NAVIGATION("navigation", R.string.miuix_component_navigation, R.string.miuix_component_navigation_desc, MiuixComponentGroup.CHROME),
    MINI_PLAYER("mini_player", R.string.miuix_component_mini_player, R.string.miuix_component_mini_player_desc, MiuixComponentGroup.CHROME),
    TOP_BARS("top_bars", R.string.miuix_component_top_bars, R.string.miuix_component_top_bars_desc, MiuixComponentGroup.CHROME),
    SNACKBARS("snackbars", R.string.miuix_component_snackbars, R.string.miuix_component_snackbars_desc, MiuixComponentGroup.CHROME),

    BUTTONS("buttons", R.string.miuix_component_buttons, R.string.miuix_component_buttons_desc, MiuixComponentGroup.CONTROLS),
    ICON_BUTTONS("icon_buttons", R.string.miuix_component_icon_buttons, R.string.miuix_component_icon_buttons_desc, MiuixComponentGroup.CONTROLS),
    FLOATING_BUTTONS("floating_buttons", R.string.miuix_component_fabs, R.string.miuix_component_fabs_desc, MiuixComponentGroup.CONTROLS),
    SWITCHES("switches", R.string.miuix_component_switches, R.string.miuix_component_switches_desc, MiuixComponentGroup.CONTROLS),
    SLIDERS("sliders", R.string.miuix_component_sliders, R.string.miuix_component_sliders_desc, MiuixComponentGroup.CONTROLS),
    CHECKBOXES("checkboxes", R.string.miuix_component_checkboxes, R.string.miuix_component_checkboxes_desc, MiuixComponentGroup.CONTROLS),
    CHIPS("chips", R.string.miuix_component_chips, R.string.miuix_component_chips_desc, MiuixComponentGroup.CONTROLS),
    TEXT_FIELDS("text_fields", R.string.miuix_component_text_fields, R.string.miuix_component_text_fields_desc, MiuixComponentGroup.CONTROLS),

    SETTINGS("settings", R.string.miuix_component_settings, R.string.miuix_component_settings_desc, MiuixComponentGroup.SURFACES),
    CARDS("cards", R.string.miuix_component_cards, R.string.miuix_component_cards_desc, MiuixComponentGroup.SURFACES),
    DIALOGS("dialogs", R.string.miuix_component_dialogs, R.string.miuix_component_dialogs_desc, MiuixComponentGroup.SURFACES),
    SHEETS("sheets", R.string.miuix_component_sheets, R.string.miuix_component_sheets_desc, MiuixComponentGroup.SURFACES),
    MENUS("menus", R.string.miuix_component_menus, R.string.miuix_component_menus_desc, MiuixComponentGroup.SURFACES),
    ;

    companion object {
        val defaults: Set<MiuixComponent> = entries.filter { it.defaultEnabled }.toSet()
    }
}

enum class MiuixComponentGroup(@param:StringRes val title: Int) {
    CHROME(R.string.glass_group_chrome),
    CONTROLS(R.string.glass_group_controls),
    SURFACES(R.string.glass_group_surfaces),
}

/** Where the MIUIX palette comes from. */
enum class MiuixColorSource(@param:StringRes val title: Int) {
    /** The stock HyperOS blue and greys. */
    HYPEROS(R.string.miuix_colors_hyperos),

    /** Monet generated from the app's own theme colour (follows KittyTune's colour settings). */
    APP_THEME(R.string.miuix_colors_app),

    /** Monet from the system wallpaper colours. */
    WALLPAPER(R.string.miuix_colors_wallpaper),
}

@Immutable
data class MiuixSettings(
    val enabled: Boolean = false,
    val components: Set<MiuixComponent> = MiuixComponent.defaults,
    val colorSource: MiuixColorSource = MiuixColorSource.HYPEROS,
    /** Replaces the Material type scale with HyperOS sizes and weights. */
    val hyperOsTypography: Boolean = true,
    /** HyperOS spring overscroll on every list. */
    val bounceOverscroll: Boolean = true,
    /** HyperOS press feedback (grey wash) instead of Material ripples. */
    val pressFeedback: Boolean = true,
    /** Swaps common icons for the MIUIX icon set. */
    val miuixIcons: Boolean = true,
    /** Pure black background in dark mode, like HyperOS. */
    val trueBlack: Boolean = true,
) {
    fun isOn(component: MiuixComponent): Boolean = enabled && component in components

    companion object {
        val Disabled = MiuixSettings()
    }
}

val LocalMiuixSettings = compositionLocalOf { MiuixSettings.Disabled }

@Composable
fun miuixEnabled(component: MiuixComponent): Boolean = LocalMiuixSettings.current.isOn(component)

object MiuixPreferences {
    private const val PREFS = "miuix_design"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_COMPONENTS = "components"
    private const val KEY_COLORS = "color_source"
    private const val KEY_TYPOGRAPHY = "hyperos_typography"
    private const val KEY_BOUNCE = "bounce_overscroll"
    private const val KEY_PRESS = "press_feedback"
    private const val KEY_ICONS = "miuix_icons"
    private const val KEY_TRUE_BLACK = "true_black"

    @Volatile
    private var state: MutableStateFlow<MiuixSettings>? = null

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun mutable(context: Context): MutableStateFlow<MiuixSettings> =
        state ?: synchronized(this) {
            state ?: MutableStateFlow(read(prefs(context))).also { state = it }
        }

    fun flow(context: Context): StateFlow<MiuixSettings> = mutable(context).asStateFlow()

    fun update(context: Context, transform: (MiuixSettings) -> MiuixSettings) {
        val flow = mutable(context)
        val next = transform(flow.value)
        write(prefs(context), next)
        flow.value = next
    }

    fun setComponent(context: Context, component: MiuixComponent, on: Boolean) = update(context) {
        it.copy(components = if (on) it.components + component else it.components - component)
    }

    fun reset(context: Context) = update(context) { MiuixSettings(enabled = it.enabled) }

    private fun read(prefs: SharedPreferences): MiuixSettings {
        val components = prefs.getStringSet(KEY_COMPONENTS, null)
            ?.mapNotNull { key -> MiuixComponent.entries.firstOrNull { it.key == key } }
            ?.toSet()
            ?: MiuixComponent.defaults
        return MiuixSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            components = components,
            colorSource = prefs.getString(KEY_COLORS, null)
                ?.let { name -> MiuixColorSource.entries.firstOrNull { it.name == name } }
                ?: MiuixColorSource.HYPEROS,
            hyperOsTypography = prefs.getBoolean(KEY_TYPOGRAPHY, true),
            bounceOverscroll = prefs.getBoolean(KEY_BOUNCE, true),
            pressFeedback = prefs.getBoolean(KEY_PRESS, true),
            miuixIcons = prefs.getBoolean(KEY_ICONS, true),
            trueBlack = prefs.getBoolean(KEY_TRUE_BLACK, true),
        )
    }

    private fun write(prefs: SharedPreferences, s: MiuixSettings) = prefs.edit {
        putBoolean(KEY_ENABLED, s.enabled)
        putStringSet(KEY_COMPONENTS, s.components.map { it.key }.toSet())
        putString(KEY_COLORS, s.colorSource.name)
        putBoolean(KEY_TYPOGRAPHY, s.hyperOsTypography)
        putBoolean(KEY_BOUNCE, s.bounceOverscroll)
        putBoolean(KEY_PRESS, s.pressFeedback)
        putBoolean(KEY_ICONS, s.miuixIcons)
        putBoolean(KEY_TRUE_BLACK, s.trueBlack)
    }
}
