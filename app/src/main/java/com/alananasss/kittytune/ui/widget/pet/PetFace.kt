package com.alananasss.kittytune.ui.widget.pet

/**
 * Builds a cute, clean head coat from a few recognisable features, each drawn with a hand-made
 * shape (instead of copying the photo cell by cell, which reads as noise at this size).
 * Every face gets the little forehead stripes of the classic pattern.
 */
internal object PetFace {

    enum class Feature { MUZZLE, CHIN, BLAZE, CHEEK_L, CHEEK_R, HALF_L, HALF_R, PATCH_L, PATCH_R, MASK }

    private val W = PetCoatPresets.HEAD_W
    private val H = PetCoatPresets.HEAD_H

    private fun rows(vararg spec: Triple<Int, Int, Int>): Set<Int> =
        spec.flatMap { (r, a, b) -> (a..b).map { r * W + it } }.toSet()

    private fun Set<Int>.mirrored(): Set<Int> = map { (it / W) * W + (W - 1 - it % W) }.toSet()

    private val coat: Set<Int> = (0 until W * H).filter { PetCoat.isHeadCoat(it / W, it % W) }.toSet()

    private val MUZZLE = rows(Triple(10, 8, 13), Triple(11, 6, 15), Triple(12, 4, 17), Triple(13, 5, 16))
    private val CHIN = rows(Triple(12, 8, 13), Triple(13, 7, 14))
    private val BLAZE = rows(Triple(5, 10, 11), Triple(6, 10, 11), Triple(7, 10, 11), Triple(8, 9, 12), Triple(9, 9, 12), Triple(10, 8, 13))
    private val CHEEK_L = rows(Triple(9, 1, 2), Triple(10, 1, 4), Triple(11, 1, 5), Triple(12, 2, 6), Triple(13, 3, 6))
    private val CHEEK_R = CHEEK_L.mirrored()
    private val PATCH_L = rows(Triple(6, 4, 8), Triple(7, 3, 9), Triple(8, 3, 9), Triple(9, 3, 9), Triple(10, 3, 9), Triple(11, 5, 7))
    private val PATCH_R = PATCH_L.mirrored()
    private val HALF_L = coat.filter { it % W <= 10 }.toSet()
    private val HALF_R = coat.filter { it % W >= 11 }.toSet()
    private val MASK = PATCH_L + PATCH_R + rows(Triple(10, 8, 13), Triple(11, 7, 14), Triple(12, 7, 14), Triple(13, 8, 13))
    private val EARS = setOf(3 * W + 2, 3 * W + 5, 3 * W + 16, 3 * W + 19, 4 * W + 2, 4 * W + 3, 4 * W + 18, 4 * W + 19)
    /** The little forehead stripes of the classic pattern (same pixels as the preset). */
    private val FOREHEAD_STRIPES = setOf(4 * W + 8, 4 * W + 10, 4 * W + 11, 4 * W + 13, 5 * W + 10, 5 * W + 11)
    private val TABBY_CHEEKS = setOf(8 * W + 1, 8 * W + 2, 10 * W + 1, 10 * W + 2, 8 * W + 19, 8 * W + 20, 10 * W + 19, 10 * W + 20)

    /**
     * Features from the per-region roles of the photo ('0' coat, '1' darker, '2' white).
     * Regions: m/n muzzle halves, h chin, i/j between the eyes, b forehead centre, c/d cheeks,
     * l/r around the eyes, f/g forehead sides.
     */
    fun featuresFrom(role: Map<Char, Char>): Set<Feature> {
        fun g(k: Char) = role[k] ?: '0'
        val f = HashSet<Feature>()
        val muzzleWhite = listOf(g('m'), g('n'), g('h')).count { it == '2' }
        if (muzzleWhite >= 2) f += Feature.MUZZLE else if (g('h') == '2') f += Feature.CHIN
        if (g('i') == '2' || g('j') == '2' || g('b') == '2') f += Feature.BLAZE
        if (g('c') == '2') f += Feature.CHEEK_L
        if (g('d') == '2') f += Feature.CHEEK_R
        val rightDark = listOf(g('g'), g('r'), g('d')).count { it == '1' } >= 2
        val leftDark = listOf(g('f'), g('l'), g('c')).count { it == '1' } >= 2
        when {
            rightDark && !leftDark -> f += Feature.HALF_R
            leftDark && !rightDark -> f += Feature.HALF_L
            g('l') == '1' && g('r') == '1' && (g('m') == '1' || g('n') == '1') -> f += Feature.MASK
            else -> {
                if (g('l') == '1') f += Feature.PATCH_L
                if (g('r') == '1') f += Feature.PATCH_R
            }
        }
        // cuteness rule: white on the face always comes with a white muzzle
        if (f.any { it == Feature.BLAZE || it == Feature.CHEEK_L || it == Feature.CHEEK_R }) {
            f -= Feature.CHIN
            f += Feature.MUZZLE
        }
        return f
    }

    /** The head role string ('0' coat, '1' markings, '2' white) for these features. */
    fun build(features: Set<Feature>, tabby: Boolean): String {
        val head = CharArray(W * H) { '0' }
        fun paint(cells: Set<Int>, role: Char, onlyOver: Char? = null) {
            for (i in cells) if (i in coat && (onlyOver == null || head[i] == onlyOver)) head[i] = role
        }
        if (Feature.HALF_R in features) paint(HALF_R, '1')
        if (Feature.HALF_L in features) paint(HALF_L, '1')
        if (Feature.PATCH_L in features) paint(PATCH_L, '1')
        if (Feature.PATCH_R in features) paint(PATCH_R, '1')
        if (Feature.MASK in features) { paint(MASK, '1'); paint(EARS, '1') }
        paint(FOREHEAD_STRIPES, '1', onlyOver = '0')
        if (tabby) paint(TABBY_CHEEKS, '1', onlyOver = '0')
        if (Feature.MUZZLE in features) paint(MUZZLE, '2')
        if (Feature.CHIN in features) paint(CHIN, '2')
        if (Feature.BLAZE in features) paint(BLAZE, '2')
        if (Feature.CHEEK_L in features) paint(CHEEK_L, '2')
        if (Feature.CHEEK_R in features) paint(CHEEK_R, '2')
        return String(head)
    }
}
