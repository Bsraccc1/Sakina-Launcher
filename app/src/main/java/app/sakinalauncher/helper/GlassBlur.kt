package app.sakinalauncher.helper

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Real backdrop blur for the glass surfaces.
 *
 * Until now "glass" here meant a flat alpha tint: correct for contrast, but the
 * frosted read depends entirely on whether the wallpaper behind it happens to have
 * detail. Over a smooth gradient a tinted panel is indistinguishable from a solid
 * one, which is why the panels looked flat in review.
 *
 * [RenderEffect] (API 31+) blurs whatever is drawn *behind* a view inside the same
 * window, so applying it to the scrim under a panel gives a genuine frosted
 * backdrop at no cost in contrast — the alpha tokens still carry legibility.
 *
 * On API < 31 there is no cheap equivalent (RenderScript is deprecated and a
 * software blur of a full-screen wallpaper drops frames on low-end devices), so
 * those devices keep the tint-only look. That is a deliberate graceful
 * degradation, not a missing feature: min SDK here is 24.
 */
object GlassBlur {

    /** Blur radii in dp, tuned so text stays crisp while the backdrop dissolves. */
    const val RADIUS_PANEL_DP = 28f
    const val RADIUS_CHROME_DP = 18f

    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * Blurs [view]'s own content. Apply to a backdrop layer, never to a view whose
     * text must stay readable — a RenderEffect blurs the view's children too.
     */
    fun apply(view: View, radiusDp: Float = RADIUS_PANEL_DP) {
        if (!isSupported) return
        applyBlur(view, radiusDp)
    }

    fun clear(view: View) {
        if (!isSupported) return
        clearBlur(view)
    }

    /**
     * Frosts everything drawn *behind the window*, which for a launcher is the system
     * wallpaper.
     *
     * This is the only approach that actually works here. A [RenderEffect] can blur
     * views inside our own hierarchy, but when Sakinah is the default launcher the
     * wallpaper is composited by the system *behind* our translucent window, so it is
     * not in our view tree at all and no in-app effect can touch it.
     *
     * Silently degrades: the device must be API 31+ and the compositor must report
     * blur support (`ro.surface_flinger.supports_background_blur`), and the user must
     * not have "reduce transparency"/battery saver forcing blurs off. The alpha tokens
     * carry legibility on their own, so losing the blur costs polish, not usability.
     */
    fun applyToWindow(window: Window, radiusDp: Float) {
        if (!isSupported) return
        applyWindowBlur(window, radiusDp)
    }

    fun clearWindow(window: Window) {
        if (!isSupported) return
        applyWindowBlur(window, 0f)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun applyWindowBlur(window: Window, radiusDp: Float) {
        val density = window.context.resources.displayMetrics.density
        val px = (radiusDp * density).toInt().coerceAtLeast(0)
        // Two different APIs, and both are needed.
        //
        // setBackgroundBlurRadius blurs what is behind the window *within the window's
        // own bounds* — that is the one that frosts the wallpaper under a full-screen
        // panel. blurBehindRadius + FLAG_BLUR_BEHIND blurs everything behind the window
        // including outside its bounds, which is what makes the edges of the panel
        // dissolve instead of ending on a hard line.
        window.setBackgroundBlurRadius(px)
        window.attributes = window.attributes.apply { blurBehindRadius = px }
        if (px > 0) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun applyBlur(view: View, radiusDp: Float) {
        val px = radiusDp * view.resources.displayMetrics.density
        if (px <= 0f) {
            view.setRenderEffect(null)
            return
        }
        view.setRenderEffect(
            RenderEffect.createBlurEffect(px, px, Shader.TileMode.CLAMP)
        )
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun clearBlur(view: View) {
        view.setRenderEffect(null)
    }
}

/**
 * Turns [this] into a frosted backdrop: a blurred snapshot of the wallpaper behind
 * the window, drawn under the panel's own tint.
 *
 * The caller is responsible for having a separate, un-blurred view on top holding
 * the content — blur inherits down the view tree.
 */
fun View.asFrostedBackdrop(radiusDp: Float = GlassBlur.RADIUS_PANEL_DP) {
    GlassBlur.apply(this, radiusDp)
}

/**
 * Frosts the wallpaper for as long as this fragment is resumed, and restores the clear
 * wallpaper when it is not.
 *
 * Panels (Productive, Muslim Center, dhikr reader) want a frosted backdrop; the home
 * screen wants the user's wallpaper untouched, so this is opt-in per fragment and is
 * tied to the resume/pause pair rather than view creation — otherwise a panel left on
 * the back stack would keep the blur on while the home screen is showing.
 */
fun Fragment.frostWallpaperWhileResumed(radiusDp: Float = GlassBlur.RADIUS_PANEL_DP) {
    if (!GlassBlur.isSupported) return
    viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            activity?.window?.let { GlassBlur.applyToWindow(it, radiusDp) }
        }

        override fun onPause(owner: LifecycleOwner) {
            activity?.window?.let { GlassBlur.clearWindow(it) }
        }
    })
}
