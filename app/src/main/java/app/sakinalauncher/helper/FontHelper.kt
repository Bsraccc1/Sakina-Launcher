package app.sakinalauncher.helper

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import app.sakinalauncher.R
import app.sakinalauncher.data.Constants
import app.sakinalauncher.data.Prefs

/**
 * Applies the user-selected default font to the latin/UI text of the app at runtime.
 *
 * Arabic / Quran text is preserved: any [TextView] tagged with [ARABIC_TAG] (or that already
 * uses the dedicated Quran typeface via the TextArabic style) keeps its mushaf font.
 *
 * The original text style (bold / italic) of each view is preserved so the careful weight
 * hierarchy defined in the layouts is not flattened.
 */
object FontHelper {

    const val ARABIC_TAG = "arabic"

    /**
     * Base typefaces by font family, and styled variants by (family, style).
     *
     * [applyFont] runs on every HomeFragment resume and walks the whole tree, so without
     * this every return to home re-ran [ResourcesCompat.getFont] plus a
     * [Typeface.create] per TextView. Typefaces are immutable and process-wide, so
     * caching them here is safe for the app's lifetime.
     */
    private val baseCache = HashMap<Int, Typeface>(4)
    private val styledCache = HashMap<Long, Typeface>(8)

    /** Returns the base [Typeface] for the currently selected font family, or null for system. */
    fun typefaceFor(context: Context, fontFamily: Int): Typeface? {
        baseCache[fontFamily]?.let { return it }
        val resolved = when (fontFamily) {
            Constants.FontFamily.POPPINS ->
                runCatching { ResourcesCompat.getFont(context, R.font.poppins_regular) }.getOrNull()
                    ?: Typeface.SANS_SERIF
            Constants.FontFamily.OUTFIT ->
                runCatching { ResourcesCompat.getFont(context, R.font.outfit_regular) }.getOrNull()
                    ?: Typeface.SANS_SERIF
            Constants.FontFamily.SERIF -> Typeface.SERIF
            Constants.FontFamily.MONOSPACE -> Typeface.MONOSPACE
            else -> Typeface.SANS_SERIF
        }
        if (resolved != null) baseCache[fontFamily] = resolved
        return resolved
    }

    /** Human readable label for a font family option. */
    fun labelFor(context: Context, fontFamily: Int): String {
        val res = when (fontFamily) {
            Constants.FontFamily.POPPINS -> R.string.font_poppins
            Constants.FontFamily.OUTFIT -> R.string.font_outfit
            Constants.FontFamily.SERIF -> R.string.font_serif
            Constants.FontFamily.MONOSPACE -> R.string.font_monospace
            else -> R.string.font_system_default
        }
        return context.getString(res)
    }

    /** Applies the font stored in prefs to every non-Arabic [TextView] under [root]. */
    fun applyFont(root: View?, prefs: Prefs) {
        root ?: return
        val family = prefs.fontFamily
        val base = typefaceFor(root.context, family) ?: return
        apply(root, family, base)
    }

    private fun apply(view: View, family: Int, base: Typeface) {
        when (view) {
            is ViewGroup -> {
                for (i in 0 until view.childCount) {
                    apply(view.getChildAt(i), family, base)
                }
            }

            is TextView -> {
                if (isArabic(view)) return
                val style = view.typeface?.style ?: Typeface.NORMAL
                view.typeface = styled(family, base, style)
            }
        }
    }

    private fun styled(family: Int, base: Typeface, style: Int): Typeface {
        val key = family.toLong() shl 32 or (style.toLong() and 0xFFFFFFFFL)
        return styledCache.getOrPut(key) { Typeface.create(base, style) }
    }

    private fun isArabic(view: TextView): Boolean {
        return view.tag == ARABIC_TAG
    }
}
