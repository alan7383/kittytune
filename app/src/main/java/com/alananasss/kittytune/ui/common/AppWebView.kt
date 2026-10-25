package com.alananasss.kittytune.ui.common

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Standard, safe WebView implementation for KittyTune.
 *
 * Enforces LAYER_TYPE_SOFTWARE to prevent the native Android OS RenderThread crash
 * (SIGSEGV in libhwui.so SkSurface::getCanvas() via GLFunctorDrawable::onDraw)
 * when multiple WebViews or off-screen WebViews coexist in a hardware-accelerated Compose hierarchy.
 */
@SuppressLint("SetJavaScriptEnabled")
open class SafeWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    init {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        isFocusable = true
        isFocusableInTouchMode = true
        // Completely bypasses GLFunctorDrawable in libhwui.so to prevent native crashes
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    /**
     * Keeps the view on the software path no matter what a screen asks for.
     *
     * [WebView] renders through a `GLFunctorDrawable` as soon as it is put back on
     * a hardware layer. On devices/emulators where the GPU driver is missing or
     * broken (for example Mesa logging `Failed to open rendernode`), that functor
     * dereferences a null `SkSurface` and the whole process dies with SIGSEGV in
     * RenderThread. Screens must not be able to opt back into that path, so the
     * layer type is pinned here instead of only being set once in `init`.
     */
    override fun setLayerType(layerType: Int, paint: Paint?) {
        super.setLayerType(View.LAYER_TYPE_SOFTWARE, paint)
    }
}

/**
 * Helper to instantiate a configured SafeWebView anywhere programmatically
 * (e.g., in WebChromeClient.onCreateWindow popup callbacks).
 */
fun createSafeWebView(context: Context): SafeWebView {
    return SafeWebView(context)
}

/**
 * Centralized Compose wrapper for WebViews in KittyTune.
 *
 * Guarantees safe software rendering, consistent layout params, and proper disposal.
 */
@Composable
fun AppWebView(
    modifier: Modifier = Modifier,
    onCreated: (SafeWebView) -> Unit = {},
    update: (SafeWebView) -> Unit = {},
    destroyOnDispose: Boolean = true
) {
    var webViewRef: SafeWebView? = null

    AndroidView(
        modifier = modifier,
        factory = { context ->
            SafeWebView(context).apply {
                webViewRef = this
                onCreated(this)
            }
        },
        update = update
    )

    if (destroyOnDispose) {
        DisposableEffect(Unit) {
            onDispose {
                webViewRef?.let { wv ->
                    wv.stopLoading()
                    wv.loadUrl("about:blank")
                }
            }
        }
    }
}
