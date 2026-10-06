package com.alananasss.kittytune

import com.alananasss.kittytune.ui.navigation.AyuPredictiveBackGeometry
import com.alananasss.kittytune.ui.navigation.AyuRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Guards the AyuGram predictive-back port against the Java it was ported from.
 *
 * [Reference] is a near-verbatim transliteration of
 * `com.exteragram.messenger.utils.ui.PredictiveBackAnimationHelper` (decompiled from
 * `vanilla-AyuGram-full-20261005.apk`, classes.dex), with the Android interpolators replaced by
 * deterministic equivalents that are also injected into the port. Everything else — the rect
 * arithmetic, the clamps, the lerps, the constants — is copied from the decompilation, so any
 * divergence in [AyuPredictiveBackGeometry] shows up as a failing assertion.
 */
private const val TEST_DENSITY = 2.75f

private fun dp(density: Float, v: Float) = ceil(density * v)

/** `AndroidUtilities.lerp(f, f2, f3)` = `f + (f3 * (f2 - f))` */
private fun lerp(f: Float, f2: Float, f3: Float) = f + (f3 * (f2 - f))

/** `Utilities.clamp(f, f2, f3)` = `max(min(f, f2), f3)` */
private fun tClamp(f: Float, f2: Float, f3: Float) = max(min(f, f2), f3)

private fun cubicBezier(x1: Float, y1: Float, x2: Float, y2: Float, x: Float): Float {
    if (x <= 0f) return 0f
    if (x >= 1f) return 1f
    var t = x
    for (i in 0 until 8) {
        val mt = 1f - t
        val bx = 3f * mt * mt * t * x1 + 3f * mt * t * t * x2 + t * t * t
        val dx = 3f * mt * mt * x1 + 6f * mt * t * (x2 - x1) + 3f * t * t * (1f - x2)
        if (dx != 0f) t -= (bx - x) / dx
        t = t.coerceIn(0f, 1f)
    }
    val mt = 1f - t
    return 3f * mt * mt * t * y1 + 3f * mt * t * t * y2 + t * t * t
}

/** Stands in for `DecelerateInterpolator()` (decelerate, factor 1). */
private fun decelerate(f: Float): Float = 1f - (1f - f) * (1f - f)

/** Stands in for `PathInterpolator(path)` built from AyuGram's two cubic segments. */
private fun emphasized(x: Float): Float {
    fun seg(
        t: Float, x0: Float, x1: Float, x2: Float, x3: Float,
        y0: Float, y1: Float, y2: Float, y3: Float
    ): Float {
        val u = ((t - x0) / (x3 - x0)).coerceIn(0f, 1f)
        val mu = 1f - u
        return y0 + 3f * mu * mu * u * (y1 - y0) + 3f * mu * u * u * (y2 - y0) + u * u * u * (y3 - y0)
    }
    return if (x <= 0.166666f) {
        seg(x, 0f, 0.05f, 0.133333f, 0.166666f, 0f, 0f, 0.06f, 0.4f)
    } else {
        seg(x, 0.166666f, 0.208333f, 0.25f, 1f, 0.4f, 0.82f, 1f, 1f)
    }
}

private val GESTURE_EASE: (Float) -> Float = { cubicBezier(0.1f, 0.1f, 0f, 1f, it) }
private val COMMIT_EASE: (Float) -> Float = { emphasized(it) }
private val VERTICAL_EASE: (Float) -> Float = { decelerate(it) }

/** `android.graphics.RectF` stand-in for the reference implementation. */
private class RefRect(
    var l: Float = 0f,
    var t: Float = 0f,
    var r: Float = 0f,
    var b: Float = 0f
) {
    fun set(ll: Float, tt: Float, rr: Float, bb: Float): RefRect {
        l = ll; t = tt; r = rr; b = bb
        return this
    }

    fun set(o: RefRect): RefRect = set(o.l, o.t, o.r, o.b)
    fun offset(dx: Float, dy: Float): RefRect { l += dx; r += dx; t += dy; b += dy; return this }
    fun width() = r - l
    fun height() = b - t
    fun centerX() = (l + r) * 0.5f
    fun centerY() = (t + b) * 0.5f
}

private fun interpolate(o: RefRect, from: RefRect, to: RefRect, f: Float) = o.set(
    lerp(from.l, to.l, f), lerp(from.t, to.t, f), lerp(from.r, to.r, f), lerp(from.b, to.b, f)
)

private fun scaleCentered(r: RefRect, f: Float) {
    val cx = r.centerX()
    val cy = r.centerY()
    val hw = (r.width() * f) / 2f
    val hh = (r.height() * f) / 2f
    r.set(cx - hw, cy - hh, cx + hw, cy + hh)
}

/**
 * Line-for-line transliteration of the decompiled
 * `com.exteragram.messenger.utils.ui.PredictiveBackAnimationHelper`.
 */
private class Reference(private val density: Float) {

    private fun dp(v: Float) = ceil(density * v)

    private var initialTouchY = 0f
    private var interpolatedProgress = 0f
    private var startCornerRadius = 0f
    private var targetCornerRadius = 0f
    private var currentCornerRadius = 0f
    private val startClosingRect = RefRect()
    private val targetClosingRect = RefRect()
    private val currentClosingRect = RefRect()
    private val startEnteringRect = RefRect()
    private val targetEnteringRect = RefRect()
    private val currentEnteringRect = RefRect()
    private val commitStartClosingRect = RefRect()
    private val commitTargetClosingRect = RefRect()
    private val commitStartEnteringRect = RefRect()
    private val commitTargetEnteringRect = RefRect()

    var closingAlpha = 1f
    var scrimAlphaMultiplier = 1f
    val closing: RefRect get() = currentClosingRect
    val entering: RefRect get() = currentEnteringRect
    val cornerRadius: Float get() = currentCornerRadius
    val closingScale: Float get() = currentClosingRect.width() / startClosingRect.width()
    val enteringScale: Float get() = currentEnteringRect.width() / startClosingRect.width()
    val slideDistance: Float get() = interpolatedProgress * dp(336f)

    fun start(i: Int, i2: Int, f: Float, z: Boolean, f2: Float) {
        initialTouchY = f
        interpolatedProgress = 0f
        closingAlpha = 1f
        scrimAlphaMultiplier = 1f
        startCornerRadius = f2
        targetCornerRadius = max(f2, dp(40f))
        currentCornerRadius = f2
        startClosingRect.set(0f, 0f, max(1, i).toFloat(), max(1, i2).toFloat())
        targetClosingRect.set(startClosingRect)
        scaleCentered(targetClosingRect, 0.85f)
        if (z) {
            targetClosingRect.offset((startClosingRect.r - targetClosingRect.r) - dp(8f), 0f)
        }
        currentClosingRect.set(startClosingRect)
        startEnteringRect.set(startClosingRect)
        scaleCentered(
            startEnteringRect,
            tClamp(
                (startClosingRect.height() - (f2 * 2f)) / startClosingRect.height(),
                0.95f,
                0.85f
            )
        )
        startEnteringRect.offset(-max(startEnteringRect.width() * 0.14999998f, dp(96f)), 0f)
        targetEnteringRect.set(startEnteringRect)
        scaleCentered(targetEnteringRect, 0.85f)
        currentEnteringRect.set(startEnteringRect)
    }

    fun update(f: Float, f2: Float) {
        val c = tClamp(f, 1f, 0f)
        interpolatedProgress = GESTURE_EASE(c)
        closingAlpha = 1f
        scrimAlphaMultiplier = 1f
        interpolate(currentClosingRect, startClosingRect, targetClosingRect, interpolatedProgress)
        // Matches the decompiled `update()`: one Y offset from the closing height, applied to both.
        val y = yOffset(currentClosingRect.height(), f2)
        currentClosingRect.offset(0f, y)
        interpolate(currentEnteringRect, startEnteringRect, targetEnteringRect, interpolatedProgress)
        currentEnteringRect.offset(0f, y)
        currentCornerRadius = lerp(startCornerRadius, targetCornerRadius, interpolatedProgress)
    }

    private fun yOffset(f: Float, f2: Float): Float {
        val h = startClosingRect.height()
        val f3 = f2 - initialTouchY
        val f4 = h / 2f
        return (if (f3 < 0f) -1f else 1f) *
            VERTICAL_EASE(min(f4, abs(f3)) / f4) *
            max(0f, ((h - f) / 2f) - dp(8f))
    }

    fun prepareCommit() {
        commitStartClosingRect.set(currentClosingRect)
        commitStartEnteringRect.set(currentEnteringRect)
        commitTargetEnteringRect.set(startClosingRect)
        commitTargetClosingRect.set(startClosingRect)
        commitTargetClosingRect.offset(currentClosingRect.l + dp(96f), 0f)
    }

    fun updateCommitProgress(f: Float) {
        val c = tClamp(f, 1f, 0f)
        val ip = COMMIT_EASE(c)
        closingAlpha = max(1f - (5f * c), 0f)
        scrimAlphaMultiplier = 1f - c
        interpolate(currentClosingRect, commitStartClosingRect, commitTargetClosingRect, ip)
        interpolate(currentEnteringRect, commitStartEnteringRect, commitTargetEnteringRect, ip)
        currentCornerRadius = lerp(targetCornerRadius, startCornerRadius, ip)
    }

    /** `ActionBarLayout.drawChild`'s scrim colour computation, returning 0..255. */
    fun scrimAlpha255(isDark: Boolean, innerTranslationX: Float): Int {
        val base = ((if (isDark) 0.8f else 0.2f) * 255f * scrimAlphaMultiplier).toInt()
        val width = startClosingRect.width()
        val paddingRight = innerTranslationX.toInt()
        val factor = min(max((width - paddingRight) / width, 0f), 0.8f)
        return (base * factor).toInt()
    }
}

class AyuPredictiveBackGeometryTest {

    private fun port() = AyuPredictiveBackGeometry(
        density = TEST_DENSITY,
        gesture = { GESTURE_EASE(it) },
        postCommit = { COMMIT_EASE(it) },
        verticalMove = { VERTICAL_EASE(it) }
    )

    private fun assertRectEq(expected: RefRect, actual: AyuRect, message: String, eps: Float = 1e-3f) {
        assertEquals("$message left", expected.l, actual.left, eps)
        assertEquals("$message top", expected.t, actual.top, eps)
        assertEquals("$message right", expected.r, actual.right, eps)
        assertEquals("$message bottom", expected.b, actual.bottom, eps)
    }

    private fun assertClose(message: String, expected: Float, actual: Float, eps: Float = 1e-4f) {
        assertEquals(message, expected, actual, eps)
    }

    @Test
    fun startMatchesDecompiledReference() {
        val ref = Reference(TEST_DENSITY)
        val geo = port()

        ref.start(1080, 2340, 1500f, true, 84f)
        geo.start(1080, 2340, 1500f, true, 84f)

        assertRectEq(ref.closing, geo.closingRect, "closing@start")
        assertRectEq(ref.entering, geo.enteringRect, "entering@start")
        assertClose("closingScale@start", ref.closingScale, geo.closingScale)
        assertClose("enteringScale@start", ref.enteringScale, geo.enteringScale)
        assertClose("cornerRadius@start", ref.cornerRadius, geo.cornerRadius)
    }

    @Test
    fun updateTracksGestureAndCommit() {
        val ref = Reference(TEST_DENSITY)
        val geo = port()
        ref.start(1080, 2340, 1200f, true, 84f)
        geo.start(1080, 2340, 1200f, true, 84f)

        val progresses = floatArrayOf(0f, 0.13f, 0.37f, 0.62f, 0.88f, 1f)
        val touchYs = floatArrayOf(1200f, 1330f, 1180f, 1400f, 1250f, 1300f)
        for (i in progresses.indices) {
            val p = progresses[i]
            ref.update(p, touchYs[i])
            geo.update(p, touchYs[i])
            assertRectEq(ref.closing, geo.closingRect, "closing@p=$p")
            assertRectEq(ref.entering, geo.enteringRect, "entering@p=$p")
            assertClose("closingScale@p=$p", ref.closingScale, geo.closingScale)
            assertClose("enteringScale@p=$p", ref.enteringScale, geo.enteringScale)
            assertClose("cornerRadius@p=$p", ref.cornerRadius, geo.cornerRadius)
            assertClose("slideDistance@p=$p", ref.slideDistance, geo.slideDistance)
        }

        assertClose("cornerRadius@end", max(84f, dp(TEST_DENSITY, 40f)), geo.cornerRadius)
        assertClose("closingScale@end", 0.85f, geo.closingScale, 1e-3f)

        val slideAtCommit = geo.slideDistance
        ref.prepareCommit()
        geo.prepareCommit()
        for (t in floatArrayOf(0f, 0.2f, 0.5f, 0.8f, 1f)) {
            ref.updateCommitProgress(t)
            geo.updateCommitProgress(t)
            assertRectEq(ref.closing, geo.closingRect, "closing@commit=$t")
            assertRectEq(ref.entering, geo.enteringRect, "entering@commit=$t")
            assertClose("cornerRadius@commit=$t", ref.cornerRadius, geo.cornerRadius)
            assertClose("closingAlpha@commit=$t", ref.closingAlpha, geo.closingAlpha)
            assertClose("scrimMult@commit=$t", ref.scrimAlphaMultiplier, geo.scrimAlphaMultiplier)
            val inner = slideAtCommit + (1080f - slideAtCommit) * t
            assertEquals(
                "scrim@commit=$t",
                ref.scrimAlpha255(true, inner),
                (geo.scrimAlphaFraction(true, inner) * 255f).toInt()
            )
        }
        assertEquals(0f, geo.closingAlpha, 1e-5f)
        assertRectEq(RefRect(0f, 0f, 1080f, 2340f), geo.enteringRect, "entering@commitEnd")
    }

    @Test
    fun verticalDragFollowsTheFinger() {
        val ref = Reference(TEST_DENSITY)
        val geo = port()
        ref.start(1080, 2340, 1000f, true, 84f)
        geo.start(1080, 2340, 1000f, true, 84f)

        // No vertical drag: the shrunken screen stays vertically centred.
        ref.update(1f, 1000f)
        geo.update(1f, 1000f)
        val centredTop = geo.closingRect.top

        // Dragging up lifts it, dragging down pushes it down, both through a decelerate curve
        // capped at `min(halfHeight, |delta|) / halfHeight`.
        ref.update(1f, 600f)
        geo.update(1f, 600f)
        assertRectEq(ref.closing, geo.closingRect, "closing@up")
        assertTrue("drag up must lift the screen", geo.closingRect.top < centredTop)

        ref.update(1f, 1400f)
        geo.update(1f, 1400f)
        assertRectEq(ref.closing, geo.closingRect, "closing@down")
        assertTrue("drag down must push the screen", geo.closingRect.top > centredTop)
    }

    @Test
    fun edgeStartIsNotOffset() {
        val ref = Reference(TEST_DENSITY)
        val geo = port()
        ref.start(1080, 2340, 1200f, false, 0f)
        geo.start(1080, 2340, 1200f, false, 0f)
        assertRectEq(ref.closing, geo.closingRect, "closing@edgeStart")

        ref.update(1f, 1200f)
        geo.update(1f, 1200f)
        assertRectEq(ref.closing, geo.closingRect, "closing@edgeStartEnd")
        // A right-edge swipe gets no `dp(8)` nudge, so it ends exactly centred at 85%.
        assertClose("edgeStart.left", (1080f * 0.15f) / 2f, geo.closingRect.left)
        assertClose("edgeStart.cornerRadius", dp(TEST_DENSITY, 40f), geo.cornerRadius)
    }

    @Test
    fun bothScreensShareOneVerticalOffset() {
        val geo = port()
        geo.start(1080, 2340, 1000f, true, 84f)

        // Same gesture progress, different finger heights: the decompiled `update()` applies the
        // closing rect's Y offset to the entering rect too, so their gap never changes.
        geo.update(0.6f, 1000f)
        val gapCentred = geo.enteringRect.top - geo.closingRect.top
        geo.update(0.6f, 600f)
        val gapUp = geo.enteringRect.top - geo.closingRect.top
        geo.update(0.6f, 1400f)
        val gapDown = geo.enteringRect.top - geo.closingRect.top
        assertEquals("entering/closing gap must not depend on touchY", gapCentred, gapUp, 1e-3f)
        assertEquals("entering/closing gap must not depend on touchY", gapCentred, gapDown, 1e-3f)
    }

    @Test
    fun gestureCurveIsTheAyuGramCurve() {
        // PathInterpolator(0.1, 0.1, 0, 1) is an ease-out shape: a quarter of the gesture has
        // already covered ~70% of the travel, which is why AyuGram's screen reaches its 85% scale
        // very early in the swipe.
        assertTrue("quarter of the gesture is already ~70% done", GESTURE_EASE(0.25f) in 0.6f..0.8f)
        assertTrue("half of the gesture is ~90% done", GESTURE_EASE(0.5f) in 0.85f..0.95f)
        assertClose("gesture ends at 1", 1f, GESTURE_EASE(1f))
    }
}