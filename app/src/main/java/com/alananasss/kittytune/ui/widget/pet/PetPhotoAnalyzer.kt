package com.alananasss.kittytune.ui.widget.pet

import android.graphics.Bitmap
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/** Where the eyes and nose are on the cropped photo, as fractions of its size (0..1). */
data class PetLandmarks(
    val leftEyeX: Float, val leftEyeY: Float,
    val rightEyeX: Float, val rightEyeY: Float,
    val noseX: Float, val noseY: Float,
) {
    companion object {
        val DEFAULT = PetLandmarks(0.35f, 0.44f, 0.65f, 0.44f, 0.5f, 0.6f)
    }
}

/** What the photo analysis found: palette, a per-pixel coat, the overall pattern and maybe the eye colour. */
data class PetPhotoResult(
    val palette: PetPalette,
    val pattern: PetPattern,
    val eyeColor: Int?,
    val coat: PetCoat?,
    /** Overall look the coat resembles most, for the summary. */
    val looksLike: PetPattern,
)

/**
 * Turns a photo of a cat into its pixel twin.
 *
 * 1. Global pass (64×64, CIELAB k-means): which colours are the cat (present in the middle rather
 *    than on the edges), its white parts, markings, overall pattern, white balance and eyes.
 * 2. Region pass: the sprite head is laid over the photo using the eyes and nose the user placed
 *    (x follows the eye line; y uses the eye spacing above the eyes, the eye→nose distance down to
 *    the nose and a longer step for the muzzle and chin). The face is split in hand-drawn regions
 *    (forehead sides, blaze halves, around each eye, cheeks, muzzle halves, chin…). Each region's
 *    median colour is compared to the coat (the colour shared by most of the face): much lighter
 *    and washed out = white, much darker (and not just a shaded ginger) = markings. Subtle stripes
 *    are found from the forehead's lightness texture. The result is clean pixel art that still
 *    keeps asymmetric details like a white patch beside one eye.
 * 3. Body and tail, unseen on a face photo, follow the overall pattern (white chest when the chin is
 *    white, stripes for tabbies, dark points for colourpoints…). Everything stays editable.
 */
object PetPhotoAnalyzer {

    private class Lab(val l: Double, val a: Double, val b: Double) {
        val chroma get() = hypot(a, b)
        fun dist(o: Lab) = sqrt((l - o.l).pow(2) + (a - o.a).pow(2) + (b - o.b).pow(2))
    }

    private class Global(
        val base: Lab, val mark: Lab, val white: Lab?, val pattern: PetPattern,
        val wa: Double, val wb: Double, val eye: Int?,
        val centers: List<Lab>, val catClusters: Set<Int>,
    ) {
        /** Whether a colour belongs to the cat rather than to the background around it. */
        fun isCat(c: Lab): Boolean = centers.indices.minBy { c.dist(centers[it]) } in catClusters
    }

    fun analyze(source: Bitmap, marks: PetLandmarks = PetLandmarks.DEFAULT): PetPhotoResult {
        val g = analyzeGlobal(source)
        val n = 256
        val img = Bitmap.createScaledBitmap(source, n, n, true)
        try {
            return analyzeCoat(img, n, marks, g)
        } finally {
            if (img !== source) img.recycle()
        }
    }

    // ---------------------------------------------------------------------------------------
    // head coat, region by region

    /**
     * Face regions on the 22×15 sprite head. Each one gets a single role from the photo, so the
     * result stays clean pixel art while keeping asymmetric details (a white patch beside one eye,
     * a half-dark face, a blaze on one side…).
     *  L/R ears · f/g forehead sides · b forehead centre · l/r around the eyes · i/j between the
     *  eyes (left/right half) · c/d cheeks · m/n muzzle halves · h chin
     */
    private val REGIONS = arrayOf(
        "......................",
        "......................",
        "......................",
        "..L..L..........R..R..",
        "..LLffffffbbggggggRR..",
        ".fffffffffbbggggggggg.",
        ".fffffffffbbggggggggg.",
        ".ffflllllijjjrrrrrggg.",
        ".ccllllllijjjrrrrrrdd.",
        ".ccllllllijjjrrrrrrdd.",
        ".ccllllliijjjrrrrrrdd.",
        ".ccccmmmmm..nnnnndddd.",
        "..cccmmmmmmnnnnnnddd..",
        "...ccchhhhhhhhhhddd...",
        "......................",
    )

    private fun analyzeCoat(img: Bitmap, n: Int, m: PetLandmarks, g: Global): PetPhotoResult {
        val hw = PetCoatPresets.HEAD_W
        val hh = PetCoatPresets.HEAD_H

        // mapping sprite cell -> photo pixel: x follows the eye line; y uses the eye spacing above
        // the eyes, the eye→nose distance down to the nose, and a longer step for muzzle and chin
        val lex = m.leftEyeX * n; val ley = m.leftEyeY * n
        val rex = m.rightEyeX * n; val rey = m.rightEyeY * n
        val span = hypot((rex - lex).toDouble(), (rey - ley).toDouble()).coerceAtLeast(8.0)
        val ux = (rex - lex) / span; val uy = (rey - ley) / span
        val vx = -uy; val vy = ux
        val mx = (lex + rex) / 2.0; val my = (ley + rey) / 2.0
        val sx = span / 8.0 // sprite eyes are 8 cells apart
        val noseDown = (m.noseX * n - mx) * vx + (m.noseY * n - my) * vy
        val syDown = max(noseDown / 2.0, sx * 0.8) // sprite nose is 2 rows below the eyes
        val radius = max(1.0, sx * 0.45)

        fun rim(r: Int, c: Int) = c <= 2 || c >= hw - 3 || r <= 4
        val regionSamples = HashMap<Char, MutableList<Lab>>()
        for (r in 0 until hh) for (c in 0 until hw) {
            if (!PetCoat.isHeadCoat(r, c) || rim(r, c)) continue
            val region = REGIONS[r][c]
            if (region == '.') continue
            val dc = c + 0.5 - 11.0
            val dr = r + 0.5 - 9.5
            val down = when {
                dr < 0 -> dr * sx
                dr <= 2 -> dr * syDown
                else -> 2 * syDown + (dr - 2) * syDown * 1.25
            }
            val x = mx + dc * sx * ux + down * vx
            val y = my + dc * sx * uy + down * vy
            if (region == 'h') {
                // chins vary a lot in length: scan down the muzzle and keep the lightest patch
                // (a cream / white chin), not the neck shadow below it
                var best: Lab? = null
                for (k in 0..6) {
                    val d2 = 2 * syDown + (0.8 + k * 0.4) * syDown
                    val s2 = sample(img, mx + dc * sx * ux + d2 * vx, my + dc * sx * uy + d2 * vy, radius)
                    if (s2 != null && (best == null || s2.l > best.l)) best = s2
                }
                best?.let { regionSamples.getOrPut(region) { ArrayList() }.add(it) }
            } else {
                sample(img, x, y, radius)?.let { regionSamples.getOrPut(region) { ArrayList() }.add(it) }
            }
        }
        if (regionSamples.values.sumOf { it.size } < 20) return fallback(g)

        // regions are represented by their median; the chin by its lightest third (a cream chin is
        // narrower than the sprite's, its sides see the cheeks)
        val med = regionSamples.mapValues { (k, v) ->
            val sorted = v.sortedBy { it.l }
            if (k == 'h') mean(sorted.takeLast(max(1, sorted.size / 3))) else sorted[sorted.size / 2]
        }
        // the coat: the colour shared by the most face cells
        val coatKey = med.keys.maxBy { k -> med.keys.filter { med.getValue(it).dist(med.getValue(k)) < 15 }.sumOf { regionSamples.getValue(it).size } }
        val coatRegions = med.keys.filter { med.getValue(it).dist(med.getValue(coatKey)) < 15 }
        val coatLab = mean(coatRegions.flatMap { regionSamples.getValue(it) })
        val cL = coatLab.l
        val cC = coatLab.chroma

        fun classify(x: Lab): Char {
            val washed = x.chroma < cC * 0.55 && x.l > cL + 4
            val lighter = x.l > cL + 12 && x.chroma < max(14.0, cC * 0.7)
            if (x.l > 58 && (washed || lighter)) return '2'
            // nothing is darker than a black cat; and on a colourful (ginger) coat, darker but still
            // colourful is shading or stripes, not a patch
            if (cL >= 30 && x.l < cL - 14 && (cC < 25 || x.chroma < cC * 0.6)) return '1'
            return '0'
        }
        val role = HashMap<Char, Char>()
        med.forEach { (k, v) -> role[k] = classify(v) }
        // ears and rims follow the neighbouring face region (they often see the background)
        role['L'] = role['f'] ?: '0'
        role['R'] = role['g'] ?: '0'

        // tabby: lightness texture of the forehead coat (stripes can be subtle on gingers)
        val fore = "fgb".filter { role[it] == '0' }.flatMap { regionSamples[it].orEmpty() }
        var tabby = false
        var stripes: List<Lab> = emptyList()
        if (fore.size > 10 && cL >= 28 && role.values.none { it == '1' }) {
            val meanL = fore.sumOf { it.l } / fore.size
            val sd = sqrt(fore.sumOf { (it.l - meanL).pow(2) } / fore.size)
            stripes = fore.filter { it.l < meanL - max(4.0, sd * 0.8) }
            val share = stripes.size.toDouble() / fore.size
            tabby = sd > 4.5 && share in 0.1..0.6
        }
        // clean hand-drawn shapes for what the photo shows, plus the forehead stripes
        val features = PetFace.featuresFrom(role)
        val head = PetFace.build(features, tabby)

        // colours
        fun meanRole(rr: Char): Lab? = role.filter { it.value == rr }.keys.flatMap { regionSamples[it].orEmpty() }.takeIf { it.isNotEmpty() }?.let { mean(it) }
        var markLab = meanRole('1') ?: (if (tabby && stripes.isNotEmpty()) mean(stripes) else null)
            ?: Lab(max(8.0, cL - 20), coatLab.a * 1.1, coatLab.b * 1.1)
        if (markLab.l > cL - 12) markLab = Lab(cL - 14, markLab.a * 1.1, markLab.b * 1.1) // stripes must read at pixel size
        // a darker half of the face is a darker coat, not black: keep it close enough to stay soft
        if (PetFace.Feature.HALF_L in features || PetFace.Feature.HALF_R in features) {
            markLab = Lab(max(markLab.l, cL - 22), markLab.a, markLab.b)
        }
        val whiteLab = meanRole('2')
        val wa = (whiteLab?.a ?: 0.0) * 0.6
        val wb = (whiteLab?.b ?: 0.0) * 0.6
        val gain = whiteLab?.let { (94.0 / it.l).coerceIn(1.0, 1.3) } ?: 1.0
        fun fix(c: Lab) = Lab((c.l * gain).coerceAtMost(98.0), c.a - wa, c.b - wb)
        // a little more colour than the photo: pixel art reads better (and cuter) a bit vivid
        fun vivid(c: Lab) = Lab(c.l, c.a * 1.15, c.b * 1.15)
        var fur = vivid(fix(coatLab))
        var mark = vivid(fix(markLab))
        if (fur.l < 22) { // black cats: charcoal so the face stays readable
            fur = Lab(22.0, fur.a, fur.b)
            mark = Lab(12.0, mark.a, mark.b)
        }
        val white = whiteLab?.let { fix(it) }?.let { Lab(max(it.l, 94.0), it.a * 0.4, it.b * 0.4) } ?: Lab(97.0, 0.0, 2.0)
        val shade = Lab(fur.l - 10, fur.a * 1.05, fur.b * 1.05)

        // body & tail follow the face
        val hasWhite = head.any { it == '2' }
        val hasMark = features.any { it in setOf(PetFace.Feature.HALF_L, PetFace.Feature.HALF_R, PetFace.Feature.PATCH_L, PetFace.Feature.PATCH_R, PetFace.Feature.MASK) }
        val looksLike = when {
            hasWhite -> PetPattern.BICOLOR
            tabby -> PetPattern.TABBY
            hasMark -> PetPattern.CLASSIC
            else -> PetPattern.SOLID
        }
        val preset = PetCoat.fromPreset(looksLike)
        // tail tip / rings in the marking colour: every cat gets a little detail there too
        val tail = if (looksLike == PetPattern.SOLID) PetCoat.fromPreset(PetPattern.CLASSIC).tail else preset.tail
        val coat = PetCoat(head, preset.body, tail)

        return PetPhotoResult(
            palette = PetPalette(toArgb(fur), toArgb(shade), toArgb(mark), toArgb(white)),
            pattern = PetPattern.CUSTOM,
            eyeColor = g.eye,
            coat = coat,
            looksLike = looksLike,
        )
    }

    private fun fallback(g: Global): PetPhotoResult {
        val shade = Lab(g.base.l - 10, g.base.a, g.base.b)
        return PetPhotoResult(
            PetPalette(toArgb(g.base), toArgb(shade), toArgb(g.mark), toArgb(g.white ?: Lab(97.0, 0.0, 2.0))),
            g.pattern, g.eye, null, g.pattern,
        )
    }

    /** Median colour of a fur patch, ignoring pupils, nostrils, irises and the pink nose when possible. */
    private fun sample(img: Bitmap, x: Double, y: Double, radius: Double): Lab? {
        val all = ArrayList<Lab>()
        val r = radius.toInt()
        for (yy in (y - r).toInt()..(y + r).toInt()) for (xx in (x - r).toInt()..(x + r).toInt()) {
            if (xx in 0 until img.width && yy in 0 until img.height) all.add(toLab(img.getPixel(xx, yy)))
        }
        if (all.isEmpty()) return null
        val keep = all.filter { it.l > 22 && !(it.a > 25 && it.a > it.b * 0.8) } // no pupils, no pink nose
        val use = (if (keep.size >= all.size * 0.3) keep else all).sortedBy { it.l }
        return use[use.size / 2]
    }

    // ---------------------------------------------------------------------------------------
    // global pass

    private fun analyzeGlobal(source: Bitmap): Global {
        val n = 64
        val small = Bitmap.createScaledBitmap(source, n, n, true)
        val labs = ArrayList<Lab>(n * n)
        val xs = IntArray(n * n)
        val ys = IntArray(n * n)
        val rad = DoubleArray(n * n)
        var k = 0
        for (y in 0 until n) for (x in 0 until n) {
            labs.add(toLab(small.getPixel(x, y)))
            xs[k] = x; ys[k] = y
            rad[k] = hypot((x - n / 2.0 + 0.5) / (n / 2.0), (y - n / 2.0 + 0.5) / (n / 2.0))
            k++
        }
        val (centers, labels) = kmeans(labs, 7)
        val innerN = rad.count { it < 0.6 }.coerceAtLeast(1)
        val borderN = rad.count { it > 0.85 }.coerceAtLeast(1)
        val catClusters = centers.indices.filter { c ->
            var inner = 0; var border = 0
            for (i in labs.indices) if (labels[i] == c) {
                if (rad[i] < 0.6) inner++
                if (rad[i] > 0.85) border++
            }
            val fi = inner.toDouble() / innerN
            val fb = border.toDouble() / borderN
            fi > 0.02 && fi >= 0.75 * fb
        }.toSet().ifEmpty { centers.indices.toSet() }

        val catIdx = labs.indices.filter { labels[it] in catClusters && rad[it] < 0.8 }
        val fur = catIdx.filter { labs[it].l > 18 }.ifEmpty { catIdx }
        val medianL = fur.map { labs[it].l }.sorted().let { it[it.size / 2] }
        val whiteIdx = fur.filter { labs[it].l >= max(66.0, medianL + 12) && labs[it].chroma < 24 }
        val whiteShare = whiteIdx.size.toDouble() / fur.size
        var coat = if (whiteShare >= 0.15) fur - whiteIdx.toSet() else fur
        if (coat.isEmpty()) coat = fur
        var base = mean(coat.map { labs[it] })
        var white: Lab? = null
        var pattern: PetPattern? = null
        if (whiteShare >= 0.15 && mean(whiteIdx.map { labs[it] }).l - base.l > 20) {
            white = mean(whiteIdx.map { labs[it] })
            val colourful = coat.count { labs[it].chroma > 20 }
            pattern = if (colourful > 0.25 * coat.size && colourful < 0.8 * coat.size && base.chroma > 12) PetPattern.CALICO else PetPattern.BICOLOR
        } else {
            coat = fur
            base = mean(coat.map { labs[it] })
        }
        val darker = coat.filter { labs[it].l < base.l - 14 }
        val lighter = coat.filter { labs[it].l >= base.l - 14 }
        val mark: Lab
        if (darker.size > 0.12 * coat.size) {
            mark = mean(darker.map { labs[it] })
            if (lighter.isNotEmpty()) base = mean(lighter.map { labs[it] })
        } else {
            mark = Lab(max(8.0, base.l - 22), base.a * 1.15, base.b * 1.15)
        }
        if (pattern == null) {
            val coatSet = coat.map { xs[it] to ys[it] }.toHashSet()
            val darkSet = darker.map { xs[it] to ys[it] }.toHashSet()
            var switches = 0; var same = 0
            for (p in coatSet) for (q in listOf(p.first + 1 to p.second, p.first to p.second + 1)) {
                if (q in coatSet) {
                    if ((p in darkSet) != (q in darkSet)) switches++ else same++
                }
            }
            val rate = switches.toDouble() / max(1, switches + same)
            val darkShare = darker.size.toDouble() / max(1, coat.size)
            pattern = when {
                base.l > 66 && mark.l < 42 && darkShare < 0.45 -> PetPattern.POINTS
                darkShare > 0.12 && rate > 0.18 -> PetPattern.TABBY
                darkShare > 0.12 -> PetPattern.CLASSIC
                else -> PetPattern.SOLID
            }
        }
        val wa = (white?.a ?: 0.0) * 0.6
        val wb = (white?.b ?: 0.0) * 0.6

        // eyes: small, saturated amber / green / blue spots in the upper middle
        val m = 128
        val big = Bitmap.createScaledBitmap(source, m, m, true)
        val eyes = ArrayList<Pair<Double, Lab>>()
        for (y in (m * 0.15).toInt() until (m * 0.6).toInt()) for (x in (m * 0.12).toInt() until (m * 0.88).toInt()) {
            if (hypot((x - m / 2.0) / (m / 2.0), (y - m / 2.0) / (m / 2.0)) > 0.7) continue
            val c = toLab(big.getPixel(x, y)).let { Lab(it.l, it.a - wa, it.b - wb) }
            val h = Math.toDegrees(atan2(c.b, c.a)).let { if (it < 0) it + 360 else it }
            if (c.l in 25.0..85.0 && c.chroma > 11 && h in 60.0..250.0) eyes.add(c.chroma to c)
        }
        val eye = if (eyes.size >= 5) {
            val top = eyes.sortedByDescending { it.first }.take(max(5, eyes.size / 3)).map { it.second }
            val med = Lab(top.map { it.l }.sorted()[top.size / 2], top.map { it.a }.sorted()[top.size / 2], top.map { it.b }.sorted()[top.size / 2])
            toArgb(Lab(med.l.coerceIn(55.0, 75.0), med.a * 1.8, med.b * 1.8))
        } else null
        if (small !== source) small.recycle()
        if (big !== source) big.recycle()
        return Global(base, mark, white, pattern, wa, wb, eye, centers, catClusters)
    }

    // ---------------------------------------------------------------------------------------
    // helpers

    private fun mean(items: List<Lab>): Lab {
        if (items.isEmpty()) return Lab(70.0, 0.0, 0.0)
        var l = 0.0; var a = 0.0; var b = 0.0
        for (i in items) { l += i.l; a += i.a; b += i.b }
        return Lab(l / items.size, a / items.size, b / items.size)
    }

    private fun kmeans(points: List<Lab>, k: Int, iterations: Int = 12): Pair<List<Lab>, IntArray> {
        val random = Random(1)
        val centers = ArrayList<Lab>()
        centers.add(points[random.nextInt(points.size)])
        while (centers.size < k) {
            val d2 = points.map { p -> centers.minOf { c -> p.dist(c) }.pow(2) }
            var r = random.nextDouble() * d2.sum()
            var chosen = points.last()
            for (i in points.indices) {
                r -= d2[i]
                if (r <= 0) { chosen = points[i]; break }
            }
            centers.add(chosen)
        }
        val labels = IntArray(points.size)
        repeat(iterations) {
            for (i in points.indices) labels[i] = centers.indices.minBy { points[i].dist(centers[it]) }
            for (c in centers.indices) {
                val members = points.indices.filter { labels[it] == c }
                if (members.isNotEmpty()) centers[c] = mean(members.map { points[it] })
            }
        }
        return centers to labels
    }

    private fun toLab(argb: Int): Lab {
        fun lin(v: Int): Double {
            val c = v / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        val r = lin(argb shr 16 and 0xFF); val g = lin(argb shr 8 and 0xFF); val b = lin(argb and 0xFF)
        val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
        val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
        fun f(t: Double) = if (t > 0.008856) cbrt(t) else 7.787 * t + 16.0 / 116
        val fx = f(x); val fy = f(y); val fz = f(z)
        return Lab(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    private fun toArgb(lab: Lab): Int {
        val fy = (lab.l + 16) / 116
        val fx = fy + lab.a / 500
        val fz = fy - lab.b / 200
        fun inv(t: Double) = if (t.pow(3) > 0.008856) t.pow(3) else (t - 16.0 / 116) / 7.787
        val x = inv(fx) * 0.95047; val y = inv(fy); val z = inv(fz) * 1.08883
        val r = 3.2406 * x - 1.5372 * y - 0.4986 * z
        val g = -0.9689 * x + 1.8758 * y + 0.0415 * z
        val b = 0.0557 * x - 0.2040 * y + 1.0570 * z
        fun gam(v: Double): Int {
            val c = min(1.0, max(0.0, v))
            return (255 * (if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1 / 2.4) - 0.055)).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (gam(r) shl 16) or (gam(g) shl 8) or gam(b)
    }
}
