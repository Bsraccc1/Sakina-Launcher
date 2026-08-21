package app.sakinalauncher.helper

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import app.sakinalauncher.R

/**
 * Ink pairing for the glass surface system.
 *
 * `?attr/glassActive` is a near-opaque fill of the *same* hue as `?attr/primaryColor`
 * (white ink on dark themes, black ink on light ones). Text left at `primaryColor`
 * on top of that fill is therefore invisible — white on white. Anything sitting on
 * an active glass fill must flip to `primaryInverseColor` and drop the legibility
 * halo, because a halo over a solid fill reads as an emboss.
 *
 * Use this instead of hand-rolling the flip per fragment.
 */
fun Context.themeColorAttr(@AttrRes attr: Int): Int {
    val value = TypedValue()
    theme.resolveAttribute(attr, value, true)
    return if (value.resourceId != 0) ContextCompat.getColor(this, value.resourceId) else value.data
}

/** Shared motion curve: fast out, settle slow. Matches @interpolator/sakina_smooth. */
val sakinaSmooth: PathInterpolator by lazy { PathInterpolator(0.2f, 0f, 0f, 1f) }

private val INK_ANIM_KEY = R.id.glass_ink_animator

/**
 * Applies the correct foreground ink for a view that gains an active glass fill.
 *
 * The colour is *animated*, not snapped. The selection indicator slides for 200ms, so
 * an instant text flip means the label turns inverse before the capsule has arrived
 * under it — the one frame everyone notices. Both halves of the transition have to
 * run on the same clock.
 *
 * @param active whether the view currently sits on `?attr/glassActive`
 * @param activeAlpha alpha when active
 * @param inactiveAlpha alpha when inactive (keeps unselected chrome recessive)
 * @param animate false on first layout / restore, where there is nothing to animate from
 */
fun TextView.applyGlassInk(
    active: Boolean,
    activeAlpha: Float = 1f,
    inactiveAlpha: Float = 0.62f,
    animate: Boolean = true,
    durationMs: Long = 200L,
) {
    val targetColor = context.themeColorAttr(
        if (active) R.attr.primaryInverseColor else R.attr.primaryColor
    )
    val targetAlpha = if (active) activeAlpha else inactiveAlpha
    val haloColor = context.themeColorAttr(R.attr.primaryTextShadowColor)

    // Cancel any in-flight ink animation on this view, or two rapid taps leave the
    // label mid-way between the two colours.
    (getTag(INK_ANIM_KEY) as? ValueAnimator)?.cancel()

    fun applyHalo(fraction: Float) {
        // Fade the legibility halo out as the fill arrives: a halo over a solid fill
        // reads as an emboss, but removing it instantly makes the label flicker.
        val radius = if (active) 1.5f * (1f - fraction) else 1.5f * fraction
        if (radius <= 0.01f) setShadowLayer(0f, 0f, 0f, 0) else setShadowLayer(radius, 0f, 0f, haloColor)
    }

    if (!animate || !isLaidOut) {
        setTextColor(targetColor)
        alpha = targetAlpha
        applyHalo(1f)
        isSelected = active
        return
    }

    val startColor = currentTextColor
    val startAlpha = alpha
    if (startColor == targetColor && startAlpha == targetAlpha) {
        isSelected = active
        return
    }

    val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = durationMs
        interpolator = sakinaSmooth
        addUpdateListener { anim ->
            val f = anim.animatedFraction
            setTextColor(ArgbEvaluator().evaluate(f, startColor, targetColor) as Int)
            alpha = startAlpha + (targetAlpha - startAlpha) * f
            applyHalo(f)
        }
    }
    setTag(INK_ANIM_KEY, animator)
    isSelected = active
    animator.start()
}

/**
 * Press feedback for any tappable surface: a small scale-down on touch, released with
 * a slight settle. Keeps taps feeling physical without a ripple, which would fight the
 * glass surfaces.
 *
 * Unlike [addPressScale] this does not consume the touch listener slot on views that
 * already need one — callers pass the down/up hooks from their own listener.
 */
fun View.pressDown(scale: Float = 0.96f) {
    animate().cancel()
    animate().scaleX(scale).scaleY(scale).setDuration(110L).setInterpolator(sakinaSmooth).start()
}

fun View.pressUp() {
    animate().cancel()
    animate().scaleX(1f).scaleY(1f).setDuration(160L).setInterpolator(sakinaSmooth).start()
}
