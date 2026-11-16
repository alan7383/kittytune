package com.alananasss.kittytune.ui.glass

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import com.alananasss.kittytune.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The parts of the interface that can be drawn as Liquid Glass.
 *
 * Every component is opt-in on its own, so a user can keep, say, glass navigation and switches
 * while leaving cards and text fields Material. [defaultEnabled] is the set a user gets when they
 * first turn Liquid Glass on: the floating chrome and the controls, where glass reads best.
 */
enum class GlassComponent(
    val key: String,
    @param:StringRes val title: Int,
    @param:StringRes val summary: Int,
    val group: GlassComponentGroup,
    val defaultEnabled: Boolean = true,
) {
    NAVIGATION("navigation", R.string.glass_component_navigation, R.string.glass_component_navigation_desc, GlassComponentGroup.CHROME),
    MINI_PLAYER("mini_player", R.string.glass_component_mini_player, R.string.glass_component_mini_player_desc, GlassComponentGroup.CHROME),
    TOP_BARS("top_bars", R.string.glass_component_top_bars, R.string.glass_component_top_bars_desc, GlassComponentGroup.CHROME),
    SNACKBARS("snackbars", R.string.glass_component_snackbars, R.string.glass_component_snackbars_desc, GlassComponentGroup.CHROME),
    PLAYER("player", R.string.glass_component_player, R.string.glass_component_player_desc, GlassComponentGroup.CHROME),

    BUTTONS("buttons", R.string.glass_component_buttons, R.string.glass_component_buttons_desc, GlassComponentGroup.CONTROLS),
    ICON_BUTTONS("icon_buttons", R.string.glass_component_icon_buttons, R.string.glass_component_icon_buttons_desc, GlassComponentGroup.CONTROLS),
    FLOATING_BUTTONS("floating_buttons", R.string.glass_component_fabs, R.string.glass_component_fabs_desc, GlassComponentGroup.CONTROLS),
    SWITCHES("switches", R.string.glass_component_switches, R.string.glass_component_switches_desc, GlassComponentGroup.CONTROLS),
    SLIDERS("sliders", R.string.glass_component_sliders, R.string.glass_component_sliders_desc, GlassComponentGroup.CONTROLS),
    CHIPS("chips", R.string.glass_component_chips, R.string.glass_component_chips_desc, GlassComponentGroup.CONTROLS),
    TEXT_FIELDS("text_fields", R.string.glass_component_text_fields, R.string.glass_component_text_fields_desc, GlassComponentGroup.CONTROLS, defaultEnabled = false),

    DIALOGS("dialogs", R.string.glass_component_dialogs, R.string.glass_component_dialogs_desc, GlassComponentGroup.SURFACES),
    SHEETS("sheets", R.string.glass_component_sheets, R.string.glass_component_sheets_desc, GlassComponentGroup.SURFACES),
    MENUS("menus", R.string.glass_component_menus, R.string.glass_component_menus_desc, GlassComponentGroup.SURFACES),
    CARDS("cards", R.string.glass_component_cards, R.string.glass_component_cards_desc, GlassComponentGroup.SURFACES, defaultEnabled = false),
    ;

    companion object {
        val defaults: Set<GlassComponent> = entries.filter { it.defaultEnabled }.toSet()
    }
}

enum class GlassComponentGroup(@param:StringRes val title: Int) {
    CHROME(R.string.glass_group_chrome),
    CONTROLS(R.string.glass_group_controls),
    SURFACES(R.string.glass_group_surfaces),
}

/**
 * How much of the glass is material and how much is see-through.
 *
 * Mirrors the system choice on iOS 26: Clear keeps the content behind as visible as possible,
 * Regular balances legibility and transparency, Tinted lays the theme's colour over the glass so it
 * blends with Material You.
 */
enum class GlassMaterial(@param:StringRes val title: Int) {
    CLEAR(R.string.glass_material_clear),
    REGULAR(R.string.glass_material_regular),
    TINTED(R.string.glass_material_tinted),
}

/** Strength of the lens at the glass rim. */
enum class GlassRefraction(@param:StringRes val title: Int, val scale: Float) {
    SUBTLE(R.string.glass_refraction_subtle, 0.6f),
    STANDARD(R.string.glass_refraction_standard, 1f),
    STRONG(R.string.glass_refraction_strong, 1.45f),
}

@Immutable
data class GlassSettings(
    val enabled: Boolean = false,
    val components: Set<GlassComponent> = GlassComponent.defaults,
    val material: GlassMaterial = GlassMaterial.REGULAR,
    val refraction: GlassRefraction = GlassRefraction.STANDARD,
    /** Splits the rim into colour fringes, like real glass. */
    val chromaticAberration: Boolean = true,
    /** Press-to-stretch, finger-following light and the elastic selection indicator. */
    val interactiveMotion: Boolean = true,
    /** Specular rim light that catches the edge of every surface. */
    val specularHighlights: Boolean = true,
) {
    fun isOn(component: GlassComponent): Boolean = enabled && component in components

    companion object {
        val Disabled = GlassSettings()
    }
}

/**
 * Process-wide store for [GlassSettings].
 *
 * One [StateFlow] backs every reader, so toggling a component in the settings screen re-renders
 * the whole interface on the next frame without each screen opening its own preferences listener.
 */
object GlassPreferences {
    private const val PREFS = "liquid_glass"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_COMPONENTS = "components"
    private const val KEY_MATERIAL = "material"
    private const val KEY_REFRACTION = "refraction"
    private const val KEY_CHROMATIC = "chromatic_aberration"
    private const val KEY_MOTION = "interactive_motion"
    private const val KEY_SPECULAR = "specular_highlights"

    @Volatile
    private var state: MutableStateFlow<GlassSettings>? = null

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun mutable(context: Context): MutableStateFlow<GlassSettings> =
        state ?: synchronized(this) {
            state ?: MutableStateFlow(read(prefs(context))).also { state = it }
        }

    fun flow(context: Context): StateFlow<GlassSettings> = mutable(context).asStateFlow()

    fun current(context: Context): GlassSettings = mutable(context).value

    fun update(context: Context, transform: (GlassSettings) -> GlassSettings) {
        val flow = mutable(context)
        val next = transform(flow.value)
        write(prefs(context), next)
        flow.value = next
    }

    fun setComponent(context: Context, component: GlassComponent, on: Boolean) = update(context) {
        it.copy(components = if (on) it.components + component else it.components - component)
    }

    fun reset(context: Context) = update(context) { GlassSettings(enabled = it.enabled) }

    private fun read(prefs: SharedPreferences): GlassSettings {
        val stored = prefs.getStringSet(KEY_COMPONENTS, null)
        val components = stored
            ?.mapNotNull { key -> GlassComponent.entries.firstOrNull { it.key == key } }
            ?.toSet()
            ?: GlassComponent.defaults
        return GlassSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            components = components,
            material = enumOrDefault(prefs.getString(KEY_MATERIAL, null), GlassMaterial.REGULAR),
            refraction = enumOrDefault(prefs.getString(KEY_REFRACTION, null), GlassRefraction.STANDARD),
            chromaticAberration = prefs.getBoolean(KEY_CHROMATIC, true),
            interactiveMotion = prefs.getBoolean(KEY_MOTION, true),
            specularHighlights = prefs.getBoolean(KEY_SPECULAR, true),
        )
    }

    private fun write(prefs: SharedPreferences, settings: GlassSettings) = prefs.edit {
        putBoolean(KEY_ENABLED, settings.enabled)
        putStringSet(KEY_COMPONENTS, settings.components.map { it.key }.toSet())
        putString(KEY_MATERIAL, settings.material.name)
        putString(KEY_REFRACTION, settings.refraction.name)
        putBoolean(KEY_CHROMATIC, settings.chromaticAberration)
        putBoolean(KEY_MOTION, settings.interactiveMotion)
        putBoolean(KEY_SPECULAR, settings.specularHighlights)
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
}
