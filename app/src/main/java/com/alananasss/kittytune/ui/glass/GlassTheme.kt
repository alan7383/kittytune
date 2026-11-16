package com.alananasss.kittytune.ui.glass

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.isRenderEffectSupported

/** The user's Liquid Glass configuration. [GlassSettings.Disabled] outside a [LiquidGlassRoot]. */
val LocalGlassSettings = compositionLocalOf { GlassSettings.Disabled }

/**
 * What a glass surface refracts.
 *
 * `null` means there is nothing recorded behind this point of the tree, which is the normal case
 * for a control that sits *inside* scrolling content: the content cannot be sampled from within
 * itself (it would be a render loop). Such a surface is drawn as translucent material with rim
 * light and press optics, and the real pixels underneath show through it untouched.
 *
 * Floating chrome (navigation, mini player, snackbars), the player and the surfaces of a glass
 * container get a real backdrop and refract what is behind them.
 */
val LocalGlassBackdrop = compositionLocalOf<Backdrop?> { null }

/**
 * Set inside a glass container that already draws the glass for its children (a top bar action
 * group, a dialog button row...). A child control then renders flat, without a second rim.
 */
val LocalGlassFlattened = compositionLocalOf { false }

/** The app window, recorded so dialogs, sheets and menus can refract it from their own window. */
val LocalGlassWindowCapture = staticCompositionLocalOf<GlassWindowCapture?> { null }

/** True when this device can draw the refraction (AGSL, Android 13+). */
val isGlassLensSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/** True when this device can blur a backdrop (RenderEffect, Android 12+). */
val isGlassBlurSupported: Boolean get() = isRenderEffectSupported()

@Composable
fun glassEnabled(component: GlassComponent): Boolean = LocalGlassSettings.current.isOn(component)

/**
 * The colours of the glass material, derived from the Material You scheme so glass keeps the
 * app's identity instead of borrowing iOS blue.
 */
@Immutable
data class GlassColors(
    val isDark: Boolean,
    /** The base material laid over the refracted backdrop. */
    val surface: Color,
    /** Floating navigation chrome: a little denser than a control, it sits over busy content. */
    val chrome: Color,
    /** Dialogs, sheets and menus: legibility first. */
    val thick: Color,
    /** Barely-there material of a clear button. */
    val clear: Color,
    val content: Color,
    val contentVariant: Color,
    val accent: Color,
    val onAccent: Color,
    /** Off track of a toggle or a slider. */
    val track: Color,
    /** The resting selection pill of tabs before it is pressed. */
    val selection: Color,
    val dim: Color,
)

@Composable
fun rememberGlassColors(material: GlassMaterial = LocalGlassSettings.current.material): GlassColors {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme, material) {
        val isDark = scheme.background.luminance() < 0.5f
        val neutral = if (isDark) Color(0xFF121212) else Color(0xFFFAFAFA)
        val base = when (material) {
            GlassMaterial.TINTED -> lerp(neutral, scheme.primaryContainer, if (isDark) 0.38f else 0.30f)
            else -> lerp(neutral, scheme.surfaceContainer, 0.35f)
        }
        val (surfaceAlpha, chromeAlpha, thickAlpha, clearAlpha) = when (material) {
            GlassMaterial.CLEAR -> if (isDark) Alphas(0.14f, 0.22f, 0.42f, 0.06f) else Alphas(0.16f, 0.24f, 0.46f, 0.08f)
            GlassMaterial.REGULAR -> if (isDark) Alphas(0.34f, 0.42f, 0.56f, 0.10f) else Alphas(0.40f, 0.48f, 0.64f, 0.14f)
            GlassMaterial.TINTED -> if (isDark) Alphas(0.40f, 0.48f, 0.60f, 0.14f) else Alphas(0.46f, 0.54f, 0.68f, 0.18f)
        }
        GlassColors(
            isDark = isDark,
            surface = base.copy(alpha = surfaceAlpha),
            chrome = base.copy(alpha = chromeAlpha),
            thick = base.copy(alpha = thickAlpha),
            clear = (if (isDark) Color.White else Color.White).copy(alpha = clearAlpha),
            content = scheme.onSurface,
            contentVariant = scheme.onSurfaceVariant,
            accent = scheme.primary,
            onAccent = scheme.onPrimary,
            track = if (isDark) Color(0xFF787880).copy(alpha = 0.36f) else Color(0xFF787878).copy(alpha = 0.20f),
            selection = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f),
            dim = if (isDark) Color(0xFF121212).copy(alpha = 0.56f) else Color(0xFF29293A).copy(alpha = 0.23f),
        )
    }
}

private data class Alphas(val surface: Float, val chrome: Float, val thick: Float, val clear: Float)

/** Readable content colour on top of a glass tint. */
fun Color.contentOnGlassTint(fallbackDark: Color = Color(0xFF111111)): Color =
    if (compositeOver(Color.White).luminance() > 0.55f) fallbackDark else Color.White

/**
 * Root of the Liquid Glass system. Reads the user's settings, records the app window for the
 * surfaces that live in their own window (dialogs, sheets, menus) and provides both to the tree.
 */
@Composable
fun LiquidGlassRoot(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val settings by remember(context) { GlassPreferences.flow(context) }.collectAsState()
    val layer = rememberGraphicsLayer()
    val capture = remember(layer) { GlassWindowCapture(layer) }

    CompositionLocalProvider(
        LocalGlassSettings provides settings,
        LocalGlassWindowCapture provides capture,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (settings.enabled) Modifier.glassWindowCapture(capture) else Modifier)
        ) {
            content()
        }
    }
}

/** A recording of the app window, kept in a [GraphicsLayer] at no extra drawing cost. */
@Stable
class GlassWindowCapture internal constructor(val layer: GraphicsLayer) {
    internal var coordinates: LayoutCoordinates? = null
    // Plain field on purpose: it is written while drawing, where a state write would invalidate.
    @Volatile
    var isRecording: Boolean = false
        internal set

    /** Where the window content sits on screen, to line a snapshot up from another window. */
    val positionOnScreen: Offset
        get() = coordinates?.takeIf { it.isAttached }?.positionOnScreen() ?: Offset.Zero

    suspend fun snapshot(): ImageBitmap? =
        if (!isRecording || layer.size.width <= 0 || layer.size.height <= 0) null
        else runCatching { layer.toImageBitmap() }.getOrNull()
}

private fun Modifier.glassWindowCapture(capture: GlassWindowCapture): Modifier = this
    .onGloballyPositioned { capture.coordinates = it }
    .drawWithContent {
        capture.layer.record { this@drawWithContent.drawContent() }
        capture.isRecording = true
        drawLayer(capture.layer)
    }

/**
 * A still of the app window, lined up on screen with whatever surface draws it. Dialogs, bottom
 * sheets and dropdown menus are separate windows, so they cannot sample the live app layer; they
 * refract this snapshot instead, taken as they open.
 */
@Immutable
private class WindowSnapshotBackdrop(
    private val bitmap: ImageBitmap,
    private val windowOrigin: Offset,
    private val alpha: () -> Float,
) : Backdrop {
    override val isCoordinatesDependent: Boolean = true

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        val position = coordinates?.takeIf { it.isAttached }?.positionOnScreen() ?: return
        val a = alpha()
        if (a <= 0f) return
        translate(windowOrigin.x - position.x, windowOrigin.y - position.y) {
            drawImage(bitmap, alpha = a)
        }
    }
}

/**
 * The backdrop for a surface in its own window: a snapshot of the app window taken when the
 * surface appears, faded in over the first frames so the glass does not pop.
 */
@Composable
fun rememberWindowSnapshotBackdrop(): Backdrop? {
    val capture = LocalGlassWindowCapture.current ?: return null
    if (!LocalGlassSettings.current.enabled || !isGlassBlurSupported) return null
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val origin = remember { mutableStateOf(Offset.Zero) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(capture) {
        origin.value = capture.positionOnScreen
        bitmap = capture.snapshot()
        if (bitmap != null) fade.animateTo(1f, tween(220))
    }
    val snapshot = bitmap ?: return null
    return remember(snapshot) { WindowSnapshotBackdrop(snapshot, origin.value) { fade.value } }
}
