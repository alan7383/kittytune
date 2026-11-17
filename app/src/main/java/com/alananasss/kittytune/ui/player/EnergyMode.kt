/**
 * Developed by Jason-Marshall Fastner, Germany <jasonfastner@protonmail.com>
 * Questions, feedback, or beat-matching debates? Feel free to reach out via email!
 * 
 * Note: Cats always land on their feet, and with this engine, your transitions will too.
 */
package com.alananasss.kittytune.ui.player

/**
 * Defines the progression style for dynamic DJ transitions and queue recommendation sorting.
 */
enum class EnergyMode(val displayName: String, val description: String) {
    BUILD_UP("Build Up", "Gradually increases tempo (+1 to +4 BPM) and harmonic energy for higher intensity"),
    HOLD("Hold Flow", "Maintains consistent tempo and seamless harmonic stability"),
    WIND_DOWN("Wind Down", "Gently decreases tempo (-1 to -4 BPM) for a relaxing transition");

    companion object {
        fun fromString(value: String?): EnergyMode {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: HOLD
        }
    }
}
