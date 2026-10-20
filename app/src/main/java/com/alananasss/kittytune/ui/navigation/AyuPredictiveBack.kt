package com.alananasss.kittytune.ui.navigation

import android.graphics.Path
import android.os.Build
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Faithful port of AyuGram's back-navigation animations.
 *
 * Sources — decompiled from `vanilla-AyuGram-full-20261005.apk`, code only:
 *  - `com.exteragram.messenger.utils.ui.PredictiveBackAnimationHelper` (classes.dex)
 *  - `org.telegram.ui.ActionBar.ActionBarLayout`, methods `onBackStarted`, `onBackProgress`,
 *    `onBackInvoked`, `onBackCancelled`, `animateBackEndAnimation`,
 *    `animatePredictiveBackEndAnimation`, `applySpringProgress`, `drawChild`, `dispatchDraw`,
 *    `setInnerTranslationX` (classes3.dex)
 *  - `org.telegram.ui.Components.CubicBezierInterpolator.EASE_OUT_QUINT` (classes3.dex)
 *  - `org.telegram.messenger.Utilities.clamp/clamp01`, `AndroidUtilities.lerp/dp` (classes.dex)
 *
 * Constants are copied verbatim. The Android `PathInterpolator` / `DecelerateInterpolator` classes
 * are reused as-is so the curves are identical to AyuGram's (Material's "emphasized" easing is
 * *not* used: AyuGram builds that curve from a two-segment `Path`).
 */
internal object AyuBack {
    /** `PredictiveBackAnimationHelper.getPostCommitDuration()` */
    const val POST_COMMIT_DURATION_MS = 375

    /** AyuGram ViewPagerFixed manual scroll duration: 320ms (`ViewPagerActivityPagerLayout.getManualScrollDuration() = 320L`). */
    const val TAB_SCROLL_DURATION_MS = 320

    /**
     * `drawChild()`, second scrim base alpha:
     * `int i4 = ExteraConfig.getSpringSwipeback() ? 102 : 120;`
     *
     * `getSpringSwipeback()` is `transitionAnimation != DEFAULT`, and AyuGram ships
     * `transitionAnimation = SPRING`, so the constant in use is **102**.
     */
    const val SPRING_SCRIM_BASE = 102f

    /** `ActionBarLayout.animatePredictiveBackEndAnimation(true)`: `max(120, progress * 220)`. */
    const val CANCEL_MIN_DURATION_MS = 120
    const val CANCEL_DURATION_PER_PROGRESS_MS = 220

    /**
     * Spring params shared by the back-press path (`animateBackEndAnimation` when no gesture ran:
     * `setStiffness(900)`, `setDampingRatio(1)`) and the forward-open path (`startLayoutAnimation`
     * with `TransitionAnimation.SPRING`, AyuGram's default: stiffness `900`, damping `1` since the
     * preview branch is not taken). Both animate a 0..1000 range that
     * [AyuPredictiveBackGeometry.springProgress] divides back down to 0..1.
     */
    const val SPRING_STIFFNESS = 900f
    const val SPRING_DAMPING_RATIO = 1f

    /** `AndroidUtilities.dp()` = `ceil(density * value)`. */
    fun dp(density: Float, value: Float): Float = ceil(density * value)

    /** `AndroidUtilities.dpf2()` = `density * value` as a float (no `ceil`, unlike [dp]). */
    fun dpf2(density: Float, value: Float): Float = if (value == 0f) 0f else density * value

    /**
     * `ActionBarLayout.drawChild` RTL (`isRightLayout`) branch: only the entering screen's
     * *left* corners fade with the slide, as
     * `Utilities.clamp01(abs((int) innerTranslationX) / dpf2(12))`.
     */
    fun pushRtlLeftFactor(density: Float, innerTranslationX: Float): Float =
        clamp01(abs(innerTranslationX.toInt()).toFloat() / dpf2(density, 12f))

    /** `Utilities.clamp(value, upperBound, lowerBound)` = `max(min(value, upper), lower)`. */
    fun clamp(value: Float, upper: Float, lower: Float): Float {
        if (value.isNaN()) return lower
        if (value.isInfinite()) return upper
        return max(min(value, upper), lower)
    }

    fun clamp01(value: Float): Float = clamp(value, 1f, 0f)

    /** `AndroidUtilities.lerp(start, stop, amount)`. */
    fun lerp(start: Float, stop: Float, amount: Float): Float =
        start + amount * (stop - start)

    /** `org.telegram.ui.Theme.MathUtils.clamp(value, min, max)` */
    fun clampMinMax(value: Float, min: Float, max: Float): Float =
        min(max(value, min), max)

    /** `PredictiveBackAnimationHelper.gestureInterpolator = new PathInterpolator(0.1f, 0.1f, 0f, 1f)` */
    val gestureInterpolator: Interpolator by lazy { PathInterpolator(0.1f, 0.1f, 0.0f, 1.0f) }

    /**
     * `LaunchActivity$8.onBackProgressed`: the animation only starts once the raw gesture progress
     * passes `0.015`, and the remaining travel is remapped so the animation spans the whole gesture:
     * `progress = max(0, rawProgress - 0.015) / 0.985`.
     */
    const val GESTURE_DEAD_ZONE = 0.015f
    const val GESTURE_REMAP_SPAN = 0.985f

    /** `CubicBezierInterpolator.EASE_OUT_QUINT`, used by the predictive-back cancel animator. */
    val easeOutQuint: Interpolator by lazy { PathInterpolator(0.23f, 1.0f, 0.32f, 1.0f) }

    /** [easeOutQuint] exposed as a Compose [Easing]. */
    val easeOutQuintEasing: Easing by lazy {
        Easing { fraction -> easeOutQuint.getInterpolation(fraction) }
    }

    /** `PredictiveBackAnimationHelper.verticalMoveInterpolator = new DecelerateInterpolator()` */
    val verticalMoveInterpolator: Interpolator by lazy { DecelerateInterpolator() }

    /** `PredictiveBackAnimationHelper.createEmphasizedInterpolator()` */
    val postCommitInterpolator: Interpolator by lazy {
        val path = Path()
        path.moveTo(0f, 0f)
        path.cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
        path.cubicTo(0.208333f, 0.82f, 0.25f, 1.0f, 1.0f, 1.0f)
        PathInterpolator(path)
    }
}

/**
 * Plain `android.graphics.RectF` stand-in so the ported geometry stays unit-testable off-device
 * and allocation-free per frame. Semantics match `RectF` for the operations used below.
 */
internal class AyuRect(
    var left: Float = 0f,
    var top: Float = 0f,
    var right: Float = 0f,
    var bottom: Float = 0f
) {
    fun set(l: Float, t: Float, r: Float, b: Float): AyuRect {
        left = l; top = t; right = r; bottom = b
        return this
    }

    fun set(other: AyuRect): AyuRect = set(other.left, other.top, other.right, other.bottom)

    fun offset(dx: Float, dy: Float): AyuRect {
        left += dx; right += dx; top += dy; bottom += dy
        return this
    }

    fun width(): Float = right - left

    fun height(): Float = bottom - top

    fun centerX(): Float = (left + right) * 0.5f

    fun centerY(): Float = (top + bottom) * 0.5f
}

/**
 * Line-by-line port of `com.exteragram.messenger.utils.ui.PredictiveBackAnimationHelper`.
 *
 * Owns the *geometry*: the rect the closing screen is drawn at, the rect the entering screen is
 * drawn at, the shared corner radius and the scrim multiplier. `ActionBarLayout.drawChild()` then
 * consumes those values on the canvas; [AyuBackState.frame] is the Compose equivalent.
 */
internal class AyuPredictiveBackGeometry(
    private val density: Float,
    private val gesture: Interpolator = AyuBack.gestureInterpolator,
    private val postCommit: Interpolator = AyuBack.postCommitInterpolator,
    private val verticalMove: Interpolator = AyuBack.verticalMoveInterpolator
) {

    private fun dp(value: Float) = AyuBack.dp(density, value)

    private var initialTouchY = 0f
    private var interpolatedProgress = 0f
    private var startCornerRadius = 0f
    private var targetCornerRadius = 0f
    private var currentCornerRadius = 0f

    private val startClosingRect = AyuRect()
    private val targetClosingRect = AyuRect()
    private val currentClosingRect = AyuRect()
    private val startEnteringRect = AyuRect()
    private val targetEnteringRect = AyuRect()
    private val currentEnteringRect = AyuRect()
    private val commitStartClosingRect = AyuRect()
    private val commitTargetClosingRect = AyuRect()
    private val commitStartEnteringRect = AyuRect()
    private val commitTargetEnteringRect = AyuRect()

    /** `getClosingAlpha()` — only modified by [updateCommitProgress]. */
    var closingAlpha = 1f
        private set

    /** `getScrimAlphaMultiplier()` */
    var scrimAlphaMultiplier = 1f
        private set

    val closingRect: AyuRect get() = currentClosingRect
    val enteringRect: AyuRect get() = currentEnteringRect
    val cornerRadius: Float get() = currentCornerRadius
    val closingScale: Float get() = currentClosingRect.width() / startClosingRect.width()
    val enteringScale: Float get() = currentEnteringRect.width() / startClosingRect.width()

    /** `getSlideDistance()` = `interpolatedProgress * dp(336)` */
    val slideDistance: Float get() = interpolatedProgress * dp(336f)

    /** `PredictiveBackAnimationHelper.start(int i, int i2, float f, boolean z, float f2)` */
    fun start(width: Int, height: Int, touchY: Float, fromLeft: Boolean, cornerRadius: Float) {
        initialTouchY = touchY
        interpolatedProgress = 0f
        closingAlpha = 1f
        scrimAlphaMultiplier = 1f
        startCornerRadius = cornerRadius
        targetCornerRadius = max(cornerRadius, dp(40f))
        currentCornerRadius = cornerRadius

        startClosingRect.set(0f, 0f, max(1, width).toFloat(), max(1, height).toFloat())

        targetClosingRect.set(startClosingRect)
        scaleCentered(targetClosingRect, 0.85f)
        if (fromLeft) {
            targetClosingRect.offset((startClosingRect.right - targetClosingRect.right) - dp(8f), 0f)
        }
        currentClosingRect.set(startClosingRect)

        startEnteringRect.set(startClosingRect)
        scaleCentered(
            startEnteringRect,
            AyuBack.clamp(
                (startClosingRect.height() - (cornerRadius * 2f)) / startClosingRect.height(),
                0.95f,
                0.85f
            )
        )
        startEnteringRect.offset(-max(startEnteringRect.width() * 0.14999998f, dp(96f)), 0f)

        targetEnteringRect.set(startEnteringRect)
        scaleCentered(targetEnteringRect, 0.85f)
        currentEnteringRect.set(startEnteringRect)
    }

    /** `PredictiveBackAnimationHelper.update(float f, float f2)` */
    fun update(progress: Float, touchY: Float) {
        val clamped = AyuBack.clamp01(progress)
        interpolatedProgress = gesture.getInterpolation(clamped)
        closingAlpha = 1f
        scrimAlphaMultiplier = 1f

        interpolate(currentClosingRect, startClosingRect, targetClosingRect, interpolatedProgress)
        // Decompiled `update()` computes the Y offset once from the *closing* height and
        // applies that same offset to both rects — the entering rect never recomputes it
        // from its own height.
        val y = yOffset(currentClosingRect.height(), touchY)
        currentClosingRect.offset(0f, y)

        interpolate(currentEnteringRect, startEnteringRect, targetEnteringRect, interpolatedProgress)
        currentEnteringRect.offset(0f, y)

        currentCornerRadius = AyuBack.lerp(startCornerRadius, targetCornerRadius, interpolatedProgress)
    }

    /** `PredictiveBackAnimationHelper.getYOffset(float f, float f2)` */
    private fun yOffset(height: Float, touchY: Float): Float {
        val startHeight = startClosingRect.height()
        val delta = touchY - initialTouchY
        val half = startHeight / 2f
        val sign = if (delta < 0f) -1f else 1f
        val interpolation = verticalMove.getInterpolation(min(half, abs(delta)) / half)
        return sign * interpolation * max(0f, ((startHeight - height) / 2f) - dp(8f))
    }

    /** `PredictiveBackAnimationHelper.prepareCommit()` */
    fun prepareCommit() {
        commitStartClosingRect.set(currentClosingRect)
        commitStartEnteringRect.set(currentEnteringRect)
        commitTargetEnteringRect.set(startClosingRect)
        commitTargetClosingRect.set(startClosingRect)
        commitTargetClosingRect.offset(currentClosingRect.left + dp(96f), 0f)
    }

    /** `PredictiveBackAnimationHelper.updateCommitProgress(float f)` */
    fun updateCommitProgress(progress: Float) {
        val clamped = AyuBack.clamp01(progress)
        val interpolation = postCommit.getInterpolation(clamped)
        closingAlpha = max(1f - (5f * clamped), 0f)
        scrimAlphaMultiplier = 1f - clamped
        interpolate(currentClosingRect, commitStartClosingRect, commitTargetClosingRect, interpolation)
        interpolate(currentEnteringRect, commitStartEnteringRect, commitTargetEnteringRect, interpolation)
        currentCornerRadius = AyuBack.lerp(targetCornerRadius, startCornerRadius, interpolation)
    }

    /**
     * `ActionBarLayout.drawChild()`:
     * `Color.argb((int) (getScrimAlpha(dark) * clamp((width - paddingRight) / width, 0f, 0.8f)), 0, 0, 0)`
     * with `paddingRight = (int) innerTranslationX + getPaddingRight()`.
     *
     * This is the *predictive* branch of `drawChild` (`newBackTransitions() && view == foreground`),
     * and it is driven by `innerTranslationX`, which `applyPredictiveBackProgress` keeps at
     * `getSlideDistance()` — so the dim tracks the finger and fades out while the gesture runs.
     */
    fun scrimAlphaFraction(
        isDark: Boolean,
        innerTranslationX: Float,
        multiplier: Float = scrimAlphaMultiplier
    ): Float {
        val base = ((if (isDark) 0.8f else 0.2f) * 255f * multiplier).toInt()
        val width = startClosingRect.width()
        val paddingRight = innerTranslationX.toInt()
        val factor = AyuBack.clampMinMax((width - paddingRight) / width, 0f, 0.8f)
        return (base * factor).toInt() / 255f
    }

    /**
     * `getScrimAlpha(boolean)` alone, i.e. the full-screen scrim `drawChild` draws for the
     * foreground view when no predictive gesture is running:
     * `Color.argb((int) ((dark ? 0.8f : 0.2f) * 255f * scrimAlphaMultiplier), 0, 0, 0)`.
     *
     * That is the forward-open and the back-press spring: the screen underneath stays dimmed at a
     * constant 0.8 (dark) / 0.2 (light) for the whole transition, and the fragment view that draws
     * it is removed when the animation ends — so there is nothing to fade.
     */
    fun scrimAlpha(isDark: Boolean, multiplier: Float = scrimAlphaMultiplier): Float {
        val base = ((if (isDark) 0.8f else 0.2f) * 255f * multiplier).toInt()
        return base / 255f
    }

    /** Full-screen width the animation works with, i.e. `containerView.getMeasuredWidth()`. */
    val containerWidth: Float get() = startClosingRect.width()

    private fun interpolate(out: AyuRect, from: AyuRect, to: AyuRect, amount: Float) {
        out.set(
            AyuBack.lerp(from.left, to.left, amount),
            AyuBack.lerp(from.top, to.top, amount),
            AyuBack.lerp(from.right, to.right, amount),
            AyuBack.lerp(from.bottom, to.bottom, amount)
        )
    }

    private fun scaleCentered(rect: AyuRect, factor: Float) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val halfWidth = rect.width() * factor / 2f
        val halfHeight = rect.height() * factor / 2f
        rect.set(cx - halfWidth, cy - halfHeight, cx + halfWidth, cy + halfHeight)
    }
}

/** Which of the two `ActionBarLayout` containers a destination currently represents. */
internal enum class AyuScreenRole {
    /** `ActionBarLayout.getForegroundView()` = `containerView`: the screen being dismissed. */
    Closing,

    /** `ActionBarLayout.getBackgroundView()` = `containerViewBack`: the screen underneath. */
    Entering,

    /** `containerView` during a forward open: the screen sliding in from the right. */
    PushEntering,

    /** `containerViewBack` during a forward open: the screen shifting left underneath. */
    PushExiting,

    /** AyuGram `ViewPagerFixed`: target tab sliding in horizontally. */
    TabEntering,

    /** AyuGram `ViewPagerFixed`: previous tab sliding out horizontally. */
    TabExiting,

    None
}

/** Everything one screen needs for a single frame of the back animation. */
internal class AyuScreenFrame(
    val translationX: Float,
    val translationY: Float,
    val scale: Float,
    /** Pre-divided by [scale], like `getCornerRadius() / getClosingScale()`. */
    val cornerRadius: Float,
    val alpha: Float,
    /** Only non-zero for the *foreground* screen; drawn *under* it, *over* the screen below. */
    val scrimAlpha: Float,
    /**
     * The predictive paths do `canvas.translate(rect.left, rect.top); canvas.scale(s, s)`, i.e. they
     * scale about the top-left corner. The spring path instead uses `View.setScaleX/Y`, which
     * pivots on the view centre.
     */
    val centerPivot: Boolean = false,
    /**
     * `ActionBarLayout.drawChild` only clips the containers to a rounded rect in the predictive
     * (`zNewBackTransitions`) and pre-API-31 paths — on API 31+ the system already clips the window,
     * so the spring path must not clip at all or you get visible cut-off corners.
     */
    val clip: Boolean = true
) {
    companion object {
        val Identity = AyuScreenFrame(0f, 0f, 1f, 0f, 1f, 0f)

        /** Keeps a dismissed screen invisible until `AnimatedContent` disposes it. */
        val Hidden = AyuScreenFrame(0f, 0f, 1f, 0f, 0f, 0f)
    }
}

internal enum class AyuBackPhase {
    Idle,

    /** `ActionBarLayout.predictiveBackInProgress && predictiveBackHasProgress` */
    Gesture,

    /** `animatePredictiveBackEndAnimation(false)` */
    Commit,

    /** `animatePredictiveBackEndAnimation(true)` */
    Cancel,

    /** `animateBackEndAnimation(...)` spring path, i.e. a back press with no gesture. */
    Spring,

    /** `startLayoutAnimation` forward-open spring (`TransitionAnimation.SPRING`, the default). */
    Push,

    /** AyuGram `ViewPagerFixed.scrollToPosition`: 320ms ease-out-quint directional tab slide. */
    TabSwitch
}

/**
 * Shared state driving the back animation.
 *
 * A single instance lives next to the `NavHost`; every destination reads its own frame through
 * [LocalAyuBackState]. That mirrors `ActionBarLayout`, which owns one `PredictiveBackAnimationHelper`
 * shared by its two containers.
 */
@Stable
internal class AyuBackState(
    private val density: Float,
    // Injectable so unit tests can supply a real gesture curve: the JVM tests run with
    // `isReturnDefaultValues`, which stubs `PathInterpolator.getInterpolation` to 0.
    private val geometry: AyuPredictiveBackGeometry = AyuPredictiveBackGeometry(density)
) {

    var phase by mutableStateOf(AyuBackPhase.Idle)
        private set

    /** Gesture progress, commit `t`, cancel progress, or spring value (0..1000). */
    var progress by mutableFloatStateOf(0f)
        private set

    /** `ActionBarLayout.predictiveBackY` — the live touch Y of the system gesture. */
    var touchY by mutableFloatStateOf(0f)
        private set

    /** `NavBackStackEntry.id` of the screen being dismissed, if any. */
    var closingEntryId by mutableStateOf<String?>(null)
        private set

    /** `NavBackStackEntry.id` of the screen underneath, if any. */
    var enteringEntryId by mutableStateOf<String?>(null)
        private set

    /**
     * `NavBackStackEntry.id` of the destination a *forward* navigation just brought in. Only that
     * one gets the device corner radius while it slides in; a screen revealed by a back animation
     * must not flash rounded corners once the animation ends.
     */
    var pushEnteringEntryId by mutableStateOf<String?>(null)
        private set

    /** `NavBackStackEntry.id` of the screen sitting underneath it during a forward open. */
    var pushExitingEntryId by mutableStateOf<String?>(null)
        private set

    /** Ordered list of root tab routes for directional slide calculation (AyuGram ViewPager style). */
    var tabRoutes by mutableStateOf<List<String>>(emptyList())
    private val entryRoutes = mutableMapOf<String, String>()
    private var lastTopRoute: String? = null

    var tabEnteringEntryId by mutableStateOf<String?>(null)
        private set

    var tabExitingEntryId by mutableStateOf<String?>(null)
        private set

    /** Direction of tab slide: +1 for rightward/forward, -1 for leftward/backward. */
    var tabDirection by mutableIntStateOf(1)
        private set

    /**
     * The back-stack ids as of the last navigation absorbed by [syncWithBackStack].
     * [shouldStartGesture] compares the live stack against it to catch a navigation that has not
     * been absorbed into a phase yet — AyuGram sets its transition flag synchronously in
     * `presentFragment`, so a back landing in that gap is rejected. Every destination also reads
     * it through [isOnStack] to know whether it is still on the stack.
     */
    var syncedStackIds by mutableStateOf<List<String>>(emptyList())
        internal set

    /** The list instance the ids above were read from, used to skip untouched passes. */
    private var lastSyncedStack: List<NavBackStackEntry>? = null

    /**
     * Whether [entryId] is on the back stack.
     *
     * `AnimatedContent` keeps a popped destination composed for the whole length of its own exit
     * transition, and Navigation gives that content the *higher* zIndex — a pop lowers the zIndex
     * of the screen it reveals. A destination that is no longer on the stack while no close
     * animation owns it would therefore sit on top of the revealed screen until the transition
     * got rid of it: the "stays on the old tab, then jumps by itself" glitch.
     *
     * An empty list means "never read", which is the state of the very first composition — the
     * `SideEffect` that fills it only runs afterwards — so nothing may be hidden yet.
     */
    fun isOnStack(entryId: String): Boolean =
        syncedStackIds.isEmpty() || syncedStackIds.contains(entryId)

    /**
     * Bumped whenever a new animation starts (phase or retarget change). The [LaunchedEffect]
     * that runs each phase captures its own epoch; [onFinished] only resets the state when
     * the epoch has not advanced since the animation began — prevents a finishing coroutine
     * from overwriting a newer phase that started mid-flight.
     */
    var animEpoch by mutableStateOf(0)
        private set

    private val hiddenEntryIds = mutableSetOf<String>()

    fun isHidden(entryId: String): Boolean = hiddenEntryIds.contains(entryId)

    fun onEntryDisposed(entryId: String) {
        hiddenEntryIds.remove(entryId)
    }

    private var width = 0
    private var height = 0
    private var cornerRadiusPx = 0f
    private var cancelFrom = 0f

    /**
     * `lambda$animatePredictiveBackEndAnimation$5`: the slide distance the commit starts from,
     * captured in `animatePredictiveBackEndAnimation(false)` before the animator is started:
     * `setInnerTranslationX(slideDistance + (width - slideDistance) * fraction)`.
     */
    private var commitStartSlideDistance = 0f

    /**
     * Whether the closing screen must stay invisible once its animation is over.
     *
     * `AnimatedContent` keeps the popped destination composed for the whole length of its own
     * (no-op) transition — 400ms here — while our commit or spring animation only runs for 375ms
     * / ~300ms. Resetting the layer to its identity values at that point made the dismissed screen
     * flash back on top of the new one for the remaining frames. A cancelled gesture is the
     * opposite: the screen stays, so it must go back to its normal state.
     */
    private var closingHiddenWhenIdle = false

    private val hasSize: Boolean get() = width > 0 && height > 0

    /**
     * Fed by every destination with its measured size. Both screens share the same size, so the
     * reference rects are only rebuilt when the geometry actually changed.
     *
     * AyuGram recomputes `containerView.getMeasuredWidth/Height()` only when the layout changes
     * (onMeasure → `onAnimationEndCheck(true)` which snaps the running animation before
     * re-measuring). We mirror that: only reset the geometry from `Idle` so a mid-flight
     * animation (rotation, fold) is not corrupted.
     */
    fun updateSize(width: Int, height: Int, cornerRadiusPx: Float) {
        if (width == this.width && height == this.height && cornerRadiusPx == this.cornerRadiusPx) {
            return
        }
        this.width = width
        this.height = height
        this.cornerRadiusPx = cornerRadiusPx
        // Only re-initialise the geometry reference rects when idle: resetting mid-animation
        // would corrupt the commit/spring rects and produce a visible jump.
        if (hasSize && phase == AyuBackPhase.Idle) {
            geometry.start(width, height, touchY, fromLeft = true, cornerRadius = cornerRadiusPx)
        }
    }

    fun roleFor(entryId: String): AyuScreenRole = when (entryId) {
        closingEntryId -> AyuScreenRole.Closing
        enteringEntryId -> AyuScreenRole.Entering
        pushEnteringEntryId -> AyuScreenRole.PushEntering
        pushExitingEntryId -> AyuScreenRole.PushExiting
        tabEnteringEntryId -> AyuScreenRole.TabEntering
        tabExitingEntryId -> AyuScreenRole.TabExiting
        else -> AyuScreenRole.None
    }

    fun isPushTarget(entryId: String): Boolean = entryId == pushEnteringEntryId

    /**
     * Whether a back gesture may start now: only from [AyuBackPhase.Idle], and only when no
     * navigation is still in flight (tap-then-swipe race — AyuGram's synchronous transition flag
     * would already be set).
     */
    fun shouldStartGesture(liveStackIds: List<String>): Boolean =
        phase == AyuBackPhase.Idle && liveStackIds == syncedStackIds

    /**
     * Whether back input must be swallowed: in AyuGram, onBackPressed / closeLastFragment only
     * bails if a close transition is already in progress (`closeLastFragment`:
     * `onCloseAnimationEndRunnable != null && checkTransitionAnimation()`), or if a pop has
     * already been submitted to the back stack (`liveStackIds.size < syncedStackIds.size`).
     * It NEVER vetoes back during an open transition (Push): AyuGram snaps the open and starts the
     * close spring immediately.
     */
    fun shouldVetoBack(liveStackIds: List<String>): Boolean {
        if (phase == AyuBackPhase.Spring || phase == AyuBackPhase.Commit || phase == AyuBackPhase.TabSwitch) return true
        if (syncedStackIds.isNotEmpty() && liveStackIds.size < syncedStackIds.size) return true
        return false
    }

    /**
     * A forward navigation added [enteringId] on top of [exitingId]. Starts the shared open
     * spring (`startLayoutAnimation` with `TransitionAnimation.SPRING`, AyuGram's default): a
     * single 0..1000 spring drives `applySpringProgress(layout, opening)` for both screens.
     */
    fun onPush(enteringId: String, exitingId: String?) {
        hiddenEntryIds.remove(enteringId)
        closingEntryId = null
        enteringEntryId = null
        tabEnteringEntryId = null
        tabExitingEntryId = null
        pushEnteringEntryId = enteringId
        pushExitingEntryId = exitingId
        if (!hasSize) return
        closingHiddenWhenIdle = false
        progress = 0f
        // A new open retargets the running spring from zero, like AyuGram resetting
        // `animationProgress` when `startLayoutAnimation` is entered with `z2 = true`.
        animEpoch++
        phase = AyuBackPhase.Push
    }

    /**
     * Switching between root tabs in the bottom menu (`ViewPagerFixed.scrollToPosition`).
     * Slides horizontally in the direction of the tab over 320ms with EaseOutQuint.
     */
    fun onTabSwitch(enteringId: String, exitingId: String, direction: Int) {
        hiddenEntryIds.remove(enteringId)
        hiddenEntryIds.add(exitingId)
        closingEntryId = null
        enteringEntryId = null
        pushEnteringEntryId = null
        pushExitingEntryId = null
        tabEnteringEntryId = enteringId
        tabExitingEntryId = exitingId
        tabDirection = direction
        if (!hasSize) return
        closingHiddenWhenIdle = false
        progress = 0f
        animEpoch++
        phase = AyuBackPhase.TabSwitch
    }

    /**
     * Absorbs one navigation: the back stack moved from [syncedStackIds] to [stack].
     *
     * Called from a `SideEffect` of [AyuBackGestureBridge], i.e. in the same frame as the
     * composition that retargets the `NavHost`. AyuGram is synchronous here (`presentFragment`
     * either snaps the running transition shut with `onAnimationEndCheck(true)` or is rejected),
     * so every navigation reaches the animation exactly once, in order, on the frame it happens.
     * Observing the back stack from a coroutine cannot offer that: a resumed collector can land
     * after the frame was already composed, or conflate two navigations that happened inside the
     * same dispatch window into one, leaving the animation bound to destinations that are no
     * longer on screen.
     */
    internal fun syncWithBackStack(stack: List<NavBackStackEntry>) {
        // The controller hands out a fresh list per navigation, so reference equality is enough to
        // skip the work of a pass that changed nothing.
        if (stack === lastSyncedStack) return
        lastSyncedStack = stack

        for (entry in stack) {
            entry.destination.route?.let { entryRoutes[entry.id] = it }
        }

        val ids = stack.map { it.id }
        if (ids == syncedStackIds) return
        val previous = syncedStackIds
        syncedStackIds = ids
        val poppedId = previous.lastOrNull()
        val currentId = ids.lastOrNull() ?: return

        val prevRoute = lastTopRoute ?: poppedId?.let { entryRoutes[it] }
        val currentRoute = stack.lastOrNull()?.destination?.route ?: entryRoutes[currentId]
        lastTopRoute = currentRoute

        // Nothing to animate: the first sync only learns the initial stack, and a stack whose top
        // entry did not change (a `popUpTo` deeper down, a graph restore) is not a transition.
        if (poppedId == null || poppedId == currentId) return

        val prevIndex = if (prevRoute != null) tabRoutes.indexOf(prevRoute) else -1
        val currIndex = if (currentRoute != null) tabRoutes.indexOf(currentRoute) else -1

        val isGestureBack = phase == AyuBackPhase.Gesture || phase == AyuBackPhase.Cancel
        val isTabSwitch = !isGestureBack && prevIndex >= 0 && currIndex >= 0 && prevIndex != currIndex

        // Determine direction:
        //  • A gesture/cancel in progress ⇒ always a back pop (the system already committed it).
        //  • Stack shrank and top changed ⇒ real pop.
        //  • Stack grew or same size with a new top ⇒ forward push or tab switch.
        val isBackPop = isGestureBack ||
            (ids.size < previous.size && currentId in previous && poppedId !in ids)
        if (isTabSwitch) {
            val direction = if (currIndex > prevIndex) 1 else -1
            onTabSwitch(enteringId = currentId, exitingId = poppedId, direction = direction)
        } else if (isBackPop) {
            onPop(closingEntryId = poppedId, enteringEntryId = currentId)
        } else {
            onPush(enteringId = currentId, exitingId = previous.lastOrNull())
        }
    }

    /** `ActionBarLayout.onBackStarted(float touchX, float touchY)`. */
    fun onGestureStart(
        touchX: Float,
        touchY: Float,
        progress: Float,
        closingEntryId: String?,
        enteringEntryId: String?
    ) {
        // AyuGram's `onBackStarted` returns false while any transition runs
        // (`animationInProgress`): a back gesture started mid-animation is rejected outright.
        if (phase != AyuBackPhase.Idle) return
        if (!hasSize) return
        val fromLeft = touchX < (width / 2f) // `predictiveBackLeft = touchX < displaySize.x / 2`
        geometry.start(width, height, touchY, fromLeft, cornerRadiusPx)
        this.touchY = touchY
        this.progress = AyuBack.clamp01(progress)
        this.closingEntryId = closingEntryId
        this.enteringEntryId = enteringEntryId
        enteringEntryId?.let { hiddenEntryIds.remove(it) }
        this.phase = AyuBackPhase.Gesture
    }

    /** `ActionBarLayout.onBackProgress(float progress, float touchY)`. */
    fun onGestureProgress(progress: Float, touchY: Float) {
        if (phase != AyuBackPhase.Gesture) return
        this.touchY = touchY
        this.progress = AyuBack.clamp01(progress)
    }

    /**
     * A destination was popped. With a live gesture this is AyuGram's
     * `animatePredictiveBackEndAnimation(false)`; without one it is the spring branch of
     * `animateBackEndAnimation`.
     */
    fun onPop(closingEntryId: String?, enteringEntryId: String?) {
        if (closingEntryId != null) {
            hiddenEntryIds.add(closingEntryId)
        }
        if (enteringEntryId != null) {
            hiddenEntryIds.remove(enteringEntryId)
        }
        pushEnteringEntryId = null
        pushExitingEntryId = null
        tabEnteringEntryId = null
        tabExitingEntryId = null
        animEpoch++
        if (phase == AyuBackPhase.Gesture || phase == AyuBackPhase.Cancel) {
            if (!hasSize) return
            geometry.update(progress, touchY)
            commitStartSlideDistance = geometry.slideDistance
            geometry.prepareCommit()
            progress = 0f
            this.closingEntryId = closingEntryId
            this.enteringEntryId = enteringEntryId
            closingHiddenWhenIdle = true
            phase = AyuBackPhase.Commit
        } else {
            // Idle: back-press spring. Push: AyuGram snaps the open shut (`onAnimationEndCheck`
            // cancels it) and then runs a full close — the same spring here, never a cut.
            // Commit/Cancel/Spring: mirror `backAnimator.end()` — snap the running animation,
            // then honor the pop as a back-press spring (a retargeted spring restarts from zero
            // via [animEpoch]).
            if (!hasSize) return
            geometry.start(width, height, touchY, fromLeft = false, cornerRadius = cornerRadiusPx)
            this.closingEntryId = closingEntryId
            this.enteringEntryId = enteringEntryId
            closingHiddenWhenIdle = true
            progress = 0f
            phase = AyuBackPhase.Spring
        }
    }

    /** The gesture was cancelled: `animatePredictiveBackEndAnimation(true)`. */
    fun onCancel() {
        if (phase != AyuBackPhase.Gesture) return
        cancelFrom = progress
        animEpoch++
        closingHiddenWhenIdle = false
        phase = AyuBackPhase.Cancel
    }

    /**
     * The animation identified by [expectedEpoch] is over; the closing entry stays bound until
     * the next navigation.
     *
     * The epoch guard prevents a finishing coroutine from resetting the state machine after a
     * new phase has already started — e.g. a commit finishing 375ms after a push has already
     * been dispatched would otherwise clobber `phase = Push` back to `Idle`.
     */
    fun onFinished(expectedEpoch: Int = animEpoch) {
        if (animEpoch != expectedEpoch) return
        phase = AyuBackPhase.Idle
        progress = 0f
        cancelFrom = 0f
    }

    /** Writes the animated value for the running phase (called from the frame clock). */
    internal fun setProgressInternal(value: Float) {
        progress = value
    }

    fun frame(
        role: AyuScreenRole,
        isDark: Boolean,
        entryId: String? = null,
        onStack: Boolean = true
    ): AyuScreenFrame {
        // AnimatedContent keeps a popped destination composed for the length of its own exit
        // transition, and Navigation draws it ABOVE the screen revealed underneath (a pop lowers
        // the zIndex of its target). It must therefore stay invisible unless a close animation
        // owns it: hiddenEntryIds covers a navigation onPop absorbed, !onStack one that never
        // reached the state machine at all, and the Closing role the animation itself.
        val isEnteringRole = role == AyuScreenRole.Entering ||
            role == AyuScreenRole.PushEntering ||
            role == AyuScreenRole.TabEntering
        val isExitingRole = role == AyuScreenRole.Closing ||
            role == AyuScreenRole.PushExiting ||
            role == AyuScreenRole.TabExiting

        val popped = !onStack && !isEnteringRole && !isExitingRole
        val hidden = entryId != null && hiddenEntryIds.contains(entryId) &&
            !isEnteringRole && (role != AyuScreenRole.TabExiting || phase == AyuBackPhase.Idle)
        if ((popped || hidden) && (role != AyuScreenRole.Closing || phase == AyuBackPhase.Idle) &&
            (role != AyuScreenRole.TabExiting || phase == AyuBackPhase.Idle)) {
            return AyuScreenFrame.Hidden
        }
        if (role == AyuScreenRole.None || !hasSize) return AyuScreenFrame.Identity

        if (phase == AyuBackPhase.Idle) {
            // The animation is over but `AnimatedContent` has not disposed the popped destination
            // yet. The screen underneath is simply back to normal; the one on top must stay gone.
            return if ((role == AyuScreenRole.Closing && closingHiddenWhenIdle) ||
                (role == AyuScreenRole.TabExiting) ||
                (entryId != null && hiddenEntryIds.contains(entryId) &&
                    entryId != enteringEntryId &&
                    entryId != tabEnteringEntryId &&
                    entryId != pushEnteringEntryId)
            ) {
                AyuScreenFrame.Hidden
            } else {
                AyuScreenFrame.Identity
            }
        }

        return when (phase) {
            AyuBackPhase.Gesture -> {
                geometry.update(progress, touchY)
                buildFrame(role, isDark)
            }

            AyuBackPhase.Cancel -> {
                geometry.update(progress, touchY)
                val multiplier =
                    if (cancelFrom != 0f) (progress / cancelFrom).coerceIn(0f, 1f) else 0f
                buildFrame(role, isDark, forcedScrimMultiplier = multiplier)
            }

            AyuBackPhase.Commit -> {
                geometry.updateCommitProgress(progress)
                // `setInnerTranslationX(slideDistance + (containerWidth - slideDistance) * t)`:
                // the geometric part of the scrim fades out over the commit as well.
                val innerTranslationX = commitStartSlideDistance +
                    (geometry.containerWidth - commitStartSlideDistance) * progress
                buildFrame(role, isDark, innerTranslationX)
            }

            AyuBackPhase.Spring -> buildSpringFrame(role, isDark)

            AyuBackPhase.Push -> buildPushFrame(role, isDark)

            AyuBackPhase.TabSwitch -> buildTabSwitchFrame(role)

            AyuBackPhase.Idle -> AyuScreenFrame.Identity
        }
    }

    /**
     * AyuGram `ViewPagerFixed.scrollToPosition`:
     * Slides horizontally 100% of container width over 320ms using `CubicBezierInterpolator.EASE_OUT_QUINT`.
     * No scaling, no rounded corners, no scrim dimming.
     */
    private fun buildTabSwitchFrame(role: AyuScreenRole): AyuScreenFrame {
        val t = AyuBack.clamp01(progress)
        val w = width.toFloat()
        return when (role) {
            AyuScreenRole.TabEntering -> {
                val dx = if (tabDirection > 0) (1f - t) * w else -(1f - t) * w
                AyuScreenFrame(
                    translationX = dx,
                    translationY = 0f,
                    scale = 1f,
                    cornerRadius = 0f,
                    alpha = 1f,
                    scrimAlpha = 0f,
                    centerPivot = false,
                    clip = false
                )
            }

            AyuScreenRole.TabExiting -> {
                val dx = if (tabDirection > 0) -t * w else t * w
                AyuScreenFrame(
                    translationX = dx,
                    translationY = 0f,
                    scale = 1f,
                    cornerRadius = 0f,
                    alpha = 1f,
                    scrimAlpha = 0f,
                    centerPivot = false,
                    clip = false
                )
            }

            else -> AyuScreenFrame.Identity
        }
    }

    /**
     * `ActionBarLayout.applySpringProgress` for the forward-open (layout, non-preview) case, which
     * vanilla AyuGram runs with `TransitionAnimation.SPRING`: the entering screen goes
     * `translationX = (1 - p) * width` with scale `lerp(0.85, 1, p)` about its centre while the
     * screen underneath shifts to `-0.35 * width`. `drawChild` then draws a full-screen scrim
     * *under* the entering screen (`getScrimAlpha(dark) * clamp(p, 0, 0.8)`) and clips both
     * screens to the cached (device) corner radii — on every API level, since
     * `springRouteBackgroundDrawable` is set.
     */
    private fun buildPushFrame(role: AyuScreenRole, isDark: Boolean): AyuScreenFrame {
        val q = AyuBack.clamp01(progress / 1000f) // applySpringProgress(): clamp01(f / 1000f)
        val w = width.toFloat()
        return when (role) {
            AyuScreenRole.PushEntering -> {
                val scale = AyuBack.lerp(0.85f, 1f, q)
                AyuScreenFrame(
                    translationX = (1f - q) * w,
                    translationY = 0f,
                    scale = scale,
                    cornerRadius = cornerRadiusPx / max(0.01f, scale),
                    alpha = 1f,
                    // The entering screen slides in from the right: it is the foreground view and has NO scrim.
                    scrimAlpha = 0f,
                    centerPivot = true,
                    clip = true
                )
            }

            AyuScreenRole.PushExiting -> {
                // The screen underneath shifts left by 35% and dims smoothly from 0 to 0.8 * base
                val base = if (isDark) 0.8f else 0.2f
                val factor = AyuBack.clampMinMax(q, 0f, 0.8f)
                val scrim = base * factor
                AyuScreenFrame(
                    translationX = -q * 0.35f * w,
                    translationY = 0f,
                    scale = 1f,
                    cornerRadius = cornerRadiusPx,
                    alpha = 1f,
                    scrimAlpha = scrim,
                    centerPivot = true,
                    clip = true
                )
            }

            else -> AyuScreenFrame.Identity
        }
    }

    /**
     * `ActionBarLayout.applySpringProgress` for the back-press (non-layout, non-preview) case:
     * the closing screen slides right by `width * p` while scaling to `lerp(1, 0.85, p)`, and the
     * screen underneath only shifts left by `35%` of the remaining distance.
     */
    private fun buildSpringFrame(role: AyuScreenRole, isDark: Boolean): AyuScreenFrame {
        val closing = role == AyuScreenRole.Closing
        val entering = role == AyuScreenRole.Entering
        val p = AyuBack.clamp01(progress / 1000f) // applySpringProgress(): clamp01(f / 1000f)
        val w = geometry.containerWidth
        val scale = if (closing) AyuBack.lerp(1f, 0.85f, p) else 1f
        return AyuScreenFrame(
            translationX = if (closing) w * p else -(w - (w * p)) * 0.35f,
            // springRouteYRatio is 0 for a back press, so translationY stays 0.
            translationY = 0f,
            scale = scale,
            cornerRadius = cornerRadiusPx / max(0.01f, scale),
            alpha = 1f,
            // Drawn over the revealed view (Entering) after its own content — fading out from 32% to 0
            scrimAlpha = if (entering) springScrimAlpha() else 0f,
            centerPivot = true,
            // `ActionBarLayout.drawChild` only reaches the rounded clip path when
            // `Build.VERSION.SDK_INT < 31`; above that the window clip does the job.
            clip = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        )
    }

    /**
     * `drawChild()`, second scrim — the one drawn *on top of* the revealed screen:
     * `Color.argb((int) (i4 * clamp((width - innerTranslationX) / width, 0f, 0.8f)), 0, 0, 0)`
     * with `i4 = 102` while `ExteraConfig.getSpringSwipeback()` holds (AyuGram's default, since
     * `transitionAnimation != DEFAULT`), and `innerTranslationX = width * p` as
     * `applySpringProgress()` pushes it with `setInnerTranslationX()`.
     *
     * Unlike `getScrimAlpha(dark)` this one is not themed — the same faint veil on light and
     * dark. It starts at 32% black on a still screen and fades to 0 as the old screen leaves.
     */
    private fun springScrimAlpha(): Float {
        val p = AyuBack.clamp01(progress / 1000f)
        val w = geometry.containerWidth
        val innerTranslationX = w * p // setInnerTranslationX(measuredWidth * f)
        val factor = AyuBack.clampMinMax((w - innerTranslationX) / w, 0f, 0.8f)
        return (AyuBack.SPRING_SCRIM_BASE * factor).toInt() / 255f
    }

    private fun buildFrame(
        role: AyuScreenRole,
        isDark: Boolean,
        innerTranslationX: Float = geometry.slideDistance,
        forcedScrimMultiplier: Float? = null
    ): AyuScreenFrame {
        val closing = role == AyuScreenRole.Closing
        val entering = role == AyuScreenRole.Entering
        val rect = if (closing) geometry.closingRect else geometry.enteringRect
        // In AyuGram: the scrim is drawn OVER the background/entering screen (containerViewBack).
        // The closing screen (foreground view in the user's hand) is NOT dimmed.
        // It starts at full scrim (0.8 dark / 0.2 light * 0.8 clamp = 0.64 dark / 0.16 light)
        // and tracks innerTranslationX as the gesture runs.
        val scrim = if (entering) {
            if (phase == AyuBackPhase.Gesture && progress == 0f) {
                // `predictiveBackHasProgress` is false at exactly 0, so no branch draws a scrim.
                0f
            } else {
                geometry.scrimAlphaFraction(
                    isDark = isDark,
                    innerTranslationX = innerTranslationX,
                    multiplier = forcedScrimMultiplier ?: geometry.scrimAlphaMultiplier
                )
            }
        } else {
            0f
        }
        val scale = if (closing) geometry.closingScale else geometry.enteringScale
        return AyuScreenFrame(
            translationX = rect.left,
            translationY = rect.top,
            scale = scale,
            cornerRadius = geometry.cornerRadius / max(0.01f, scale),
            alpha = if (closing) geometry.closingAlpha else 1f,
            scrimAlpha = scrim
        )
    }
}

internal val LocalAyuBackState = androidx.compose.runtime.compositionLocalOf { AyuBackState(1f) }

/**
 * Feeds [AyuBackState] from the system back gesture and from every `NavController` pop.
 *
 * `NavHost` registers the only `NavigationEventHandler`, and the dispatcher delivers a back event to
 * exactly one handler, so a second handler would starve `NavHost`. Instead we observe the dispatcher
 * state that handler feeds — precisely the `BackEvent` data AyuGram gets in
 * `onBackStarted(touchX, touchY)` / `onBackProgress(progress, touchY)` — plus the back stack itself.
 *
 * @param gesturesEnabled must be false while an overlay that consumes the back gesture itself is
 * open (expanded player, lyrics sheet): the overlay's own `PredictiveBackHandler` drives the
 * dispatcher's `transitionState` too, and without this gate the NavHost screen *behind* the
 * overlay would play a ghost predictive animation for a pop that never happens.
 */
@Composable
internal fun AyuBackGestureBridge(
    navController: NavHostController,
    state: AyuBackState,
    gesturesEnabled: Boolean = true
) {
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher

    LaunchedEffect(dispatcher, gesturesEnabled) {
        // A gesture rejected below keeps emitting progress until the finger lifts. AyuGram keeps
        // ignoring its remainder (`predictiveInput` stays false for the whole gesture), so latch
        // it instead of jumping onto the finger mid-flight once the transition ends.
        var suppressed = false
        var cancelJob: Job? = null
        dispatcher?.transitionState?.collect { transitionState ->
            val gesture = transitionState as? NavigationEventTransitionState.InProgress
            if (gesture == null || gesture.direction != NavigationEventTransitionState.TRANSITIONING_BACK) {
                suppressed = false
                if (state.phase == AyuBackPhase.Gesture) {
                    // `onBackCompleted` pops the back stack before the dispatcher goes idle, but under
                    // rapid navigation or recomposition load, currentBackStack collection can take
                    // more than one frame. Debounce cancel so an incoming pop can commit cleanly.
                    cancelJob?.cancel()
                    cancelJob = launch {
                        delay(60)
                        if (state.phase == AyuBackPhase.Gesture) state.onCancel()
                    }
                }
                return@collect
            }
            cancelJob?.cancel()
            cancelJob = null
            if (!gesturesEnabled) {
                // The gesture belongs to the overlay, not to NavHost: drop it. Recover a
                // half-started gesture gracefully instead of leaving it stuck.
                if (state.phase == AyuBackPhase.Gesture) state.onCancel()
                return@collect
            }
            val event = gesture.latestEvent
            // AyuGram ignores the first 1.5% of the travel and then stretches the rest over
            // the full animation range.
            val remapped =
                max(0f, event.progress - AyuBack.GESTURE_DEAD_ZONE) / AyuBack.GESTURE_REMAP_SPAN
            if (state.phase == AyuBackPhase.Gesture) {
                suppressed = false
                state.onGestureProgress(remapped, event.touchY)
            } else if (suppressed) {
                // Rejected gesture still in flight: keep ignoring it.
            } else if (event.progress > AyuBack.GESTURE_DEAD_ZONE) {
                // Synchronous race guard: a tap that committed a push a frame ago may not be
                // absorbed yet (AyuGram's flag is set synchronously). Starting the gesture on the
                // stale stack hijacks the entering screen and plays the enter backwards.
                val liveIds = navController.currentBackStack.value.map { it.id }
                val canStart = state.shouldStartGesture(liveIds)
                if (canStart) {
                    state.onGestureStart(
                        touchX = event.touchX,
                        touchY = event.touchY,
                        progress = remapped,
                        closingEntryId = navController.currentBackStackEntry?.id,
                        enteringEntryId = navController.previousBackStackEntry?.id
                    )
                } else {
                    suppressed = true
                }
            }
        }
    }

    // `NavController` keeps its back stack in a StateFlow, whose value Compose cannot observe,
    // and a collector would only resume a frame later. Every navigation therefore bumps this
    // snapshot counter, and the SideEffect below — which runs right after this composition, before
    // the frame is drawn — absorbs the (by then fresh) stack into the state machine. That is what
    // keeps the animation locked to the very navigation the `NavHost` is being composed for:
    // AyuGram is synchronous here, so a navigation must never reach the animation twice, out of
    // order, or a frame late.
    val navigationTick = remember { mutableIntStateOf(0) }
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, _, _ ->
            navigationTick.intValue++
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
    key(navigationTick.intValue) {
        val backStack = navController.currentBackStack.value
        SideEffect {
            state.syncWithBackStack(backStack)
        }
    }

    // Observe phase AND animEpoch directly: animEpoch is a mutableStateOf, so Compose will
    // always re-run this composable when it changes, and the LaunchedEffect will cancel/restart.
    LaunchedEffect(state.phase, state.animEpoch) {
        // Snapshot the epoch inside the coroutine body so `onFinished` can guard against a stale
        // coroutine finishing after a newer phase has already started.
        val capturedEpoch = state.animEpoch
        when (state.phase) {
            // `ValueAnimator.ofFloat(0f, 1f)` with the default (linear) interpolator — the
            // emphasized curve is applied inside `updateCommitProgress`.
            AyuBackPhase.Commit -> {
                animate(
                    0f,
                    1f,
                    animationSpec = tween(
                        AyuBack.POST_COMMIT_DURATION_MS,
                        easing = LinearEasing
                    )
                ) { value, _ -> state.setProgressInternal(value) }
                state.onFinished(capturedEpoch)
            }

            AyuBackPhase.Cancel -> {
                val from = state.progress
                val duration = max(
                    AyuBack.CANCEL_MIN_DURATION_MS,
                    (from * AyuBack.CANCEL_DURATION_PER_PROGRESS_MS).toInt()
                )
                animate(
                    from,
                    0f,
                    animationSpec = tween(duration, easing = AyuBack.easeOutQuintEasing)
                ) { value, _ ->
                    state.setProgressInternal(value)
                }
                state.onFinished(capturedEpoch)
            }

            // One shared `SpringAnimation` (`setStiffness(900).setDampingRatio(1)`, start velocity
            // 0) over a 0..1000 range drives `applySpringProgress` for both screens — whether it is
            // the back-press path or the forward-open path (`startLayoutAnimation` layout branch).
            AyuBackPhase.Spring, AyuBackPhase.Push -> {
                animate(
                    0f,
                    1000f,
                    animationSpec = spring(
                        dampingRatio = AyuBack.SPRING_DAMPING_RATIO,
                        stiffness = AyuBack.SPRING_STIFFNESS
                    )
                ) { value, _ -> state.setProgressInternal(value) }
                state.onFinished(capturedEpoch)
            }

            // AyuGram `ViewPagerFixed.scrollToPosition`: 320ms ease-out-quint directional tab slide
            AyuBackPhase.TabSwitch -> {
                animate(
                    0f,
                    1f,
                    animationSpec = tween(
                        AyuBack.TAB_SCROLL_DURATION_MS,
                        easing = AyuBack.easeOutQuintEasing
                    )
                ) { value, _ ->
                    state.setProgressInternal(value)
                }
                state.onFinished(capturedEpoch)
            }

            else -> Unit
        }
    }
}