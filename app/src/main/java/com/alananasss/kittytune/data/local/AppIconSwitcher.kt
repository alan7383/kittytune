package com.alananasss.kittytune.data.local

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object AppIconSwitcher {

    val ICON_IDS: List<String> = listOf(
        "default",
        "black", "blue", "chrome", "hot_pink", "lavender", "leopard", "lime", "og",
        "rose_gold", "silver", "soft_purple", "sunset", "tie_dye",
        "algeria", "argentina", "australia", "austria", "belgium", "bosnia_herzegovina",
        "brazil", "canada", "cape_verde", "colombia", "croatia", "curacao", "czechia",
        "dr_congo", "ecuador", "egypt", "england", "france", "germany", "ghana", "haiti",
        "iran", "iraq", "ivory_coast", "japan", "jordan", "mexico", "morocco",
        "netherlands", "new_zealand", "norway", "panama", "paraguay", "portugal", "qatar",
        "saudi_arabia", "scotland", "senegal", "south_africa", "south_korea", "spain",
        "sweden", "switzerland", "tunisia", "turkey", "united_states", "uruguay", "uzbekistan"
    )

    fun aliasName(id: String): String {
        if (id == "default") return "LauncherIconDefaultAlias"
        return "LauncherIcon${toCamelCase(id)}Alias"
    }

    private fun toCamelCase(id: String): String =
        id.split('_').joinToString("") { part -> part.replaceFirstChar { it.uppercaseChar() } }

    private fun aliasComponentName(packageName: String, id: String): ComponentName =
        ComponentName(packageName, "$packageName.${aliasName(id)}")

    /**
     * Enables the alias matching [iconId] and disables every other launcher alias.
     * MainActivity itself is never disabled, so the SoundCloud OAuth deep links keep working.
     */
    fun applyIcon(context: Context, iconId: String) {
        val packageManager = context.packageManager
        val packageName = context.packageName
        ICON_IDS.forEach { id ->
            val component = aliasComponentName(packageName, id)
            val enabled = id == iconId
            packageManager.setComponentEnabledSetting(
                component,
                if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                },
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
