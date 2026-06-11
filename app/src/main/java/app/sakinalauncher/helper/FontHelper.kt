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

    /** Returns the base [Typeface] for the currently selected font family, or null for system. */
    fun typefaceFor(context: Context, fontFamily: Int): Typeface? {
        return when (fontFamily) {
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
        val base = typefaceFor(root.context, prefs.fontFamily) ?: return
        apply(root, base)
    }

    private fun apply(view: View, base: Typeface) {
        when (view) {
            is ViewGroup -> {
                for (i in 0 until view.childCount) {
                    apply(view.getChildAt(i), base)
                }
            }

            is TextView -> {
                if (isArabic(view)) return
                val style = view.typeface?.style ?: Typeface.NORMAL
                view.typeface = Typeface.create(base, style)
            }
        }
    }

    private fun isArabic(view: TextView): Boolean {
        return view.tag == ARABIC_TAG
    }
}
