package com.alananasss.kittytune.data.audio

/**
 * Identifies headphone form-factors (In-ear earbuds vs Over-ear headphones)
 * for offline UI icons.
 */
object HeadphoneDeviceType {

    /**
     * Returns true if the form factor or model name represents in-ear earbuds / earphones.
     */
    fun isInEar(formFactor: String?, name: String? = null): Boolean {
        val f = formFactor?.lowercase()
        if (f != null && (f.contains("in-ear") || f.contains("earbud") || f.contains("iem"))) {
            return true
        }
        val n = name?.lowercase()
        if (n != null && (
            n.contains("bud") ||
            n.contains("airpod") ||
            n.contains("earphone") ||
            n.contains("in-ear") ||
            n.contains("iem") ||
            n.contains("wf-") ||
            n.contains("linkbud") ||
            n.contains("freebud") ||
            n.contains("écouteur")
        )) {
            return true
        }
        return false
    }
}
