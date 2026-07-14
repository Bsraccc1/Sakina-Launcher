package app.sakinalauncher.helper

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import app.sakinalauncher.R
import kotlin.math.roundToInt

/**
 * Helpers that build dialogs sharing a single, consistent visual family:
 * a [R.drawable.bg_unified_dialog] surface, Poppins typography and themed
 * colors.
 */
object AppDialog {

    /**
     * @param widthScale fraction of screen width for the dialog card (0.55–1.0).
     *   Productive dialogs pass [app.sakinalauncher.data.Prefs.productiveDialogWidthScale]
     *   (0.70 / 0.80 / 0.90 / 1.0). When null, the window is full-width and the
     *   card uses a modest default inset so Settings pickers stay readable.
     * @param onShow optional callback after size is applied (e.g. show keyboard).
     */
    fun create(
        context: Context,
        contentView: View,
        matchHeight: Boolean = false,
        widthScale: Float? = null,
        onShow: ((Dialog) -> Unit)? = null,
    ): Dialog {
        // Theme with windowMinWidth* = 0% so scale is not clamped by the system.
        val dialog = Dialog(context, R.style.AppDialogTheme)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(contentView)

        val screenW = screenWidthPx(context)
        // Explicit scale: map 70/80/90/100% faithfully. No high floor that collapses steps.
        val cardWidthPx: Int? = when {
            matchHeight -> null
            widthScale != null -> {
                val scale = widthScale.coerceIn(0.55f, 1.0f)
                (screenW * scale).roundToInt().coerceIn(dp(context, 200), screenW)
            }
            else -> {
                // Unscaled dialogs: ~92% with visible side gutters.
                (screenW * 0.92f).roundToInt().coerceIn(dp(context, 240), screenW)
            }
        }

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.55f)
            @Suppress("DEPRECATION")
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }

        if (!matchHeight) {
            ViewCompat.setOnApplyWindowInsetsListener(contentView) { view, insets ->
                val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                view.updatePadding(bottom = ime)
                insets
            }
        }

        fun applyChrome() {
            applyWindowSize(dialog, matchHeight, cardWidthPx)
            if (cardWidthPx != null && !matchHeight) {
                applyCenteredCard(contentView, cardWidthPx)
            }
        }

        dialog.setOnShowListener {
            applyChrome()
            // OEM window managers often re-apply min-width after show — force again.
            dialog.window?.decorView?.post {
                applyChrome()
                dialog.window?.decorView?.minimumWidth = 0
                contentView.minimumWidth = 0
                dialog.window?.decorView?.let { ViewCompat.requestApplyInsets(it) }
            }
            onShow?.invoke(dialog)
        }
        applyChrome()

        return dialog
    }

    private fun applyWindowSize(dialog: Dialog, matchHeight: Boolean, fixedWidthPx: Int?) {
        val window = dialog.window ?: return
        // Clear platform min-width so 70% vs 100% is actually different.
        window.decorView.minimumWidth = 0
        window.decorView.minimumHeight = 0

        if (matchHeight) {
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            return
        }

        val width = fixedWidthPx ?: ViewGroup.LayoutParams.MATCH_PARENT
        val attrs = window.attributes
        attrs.gravity = Gravity.CENTER
        attrs.x = 0
        attrs.y = 0
        if (fixedWidthPx != null) {
            attrs.width = fixedWidthPx
            attrs.height = WindowManager.LayoutParams.WRAP_CONTENT
        } else {
            attrs.width = WindowManager.LayoutParams.MATCH_PARENT
            attrs.height = WindowManager.LayoutParams.WRAP_CONTENT
        }
        window.attributes = attrs
        window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.CENTER)
    }

    private fun applyCenteredCard(contentView: View, cardWidthPx: Int) {
        contentView.minimumWidth = 0
        val card = (contentView as? ViewGroup)?.findViewById(R.id.dialogCard)
            ?: (contentView as? ViewGroup)?.getChildAt(0)
            ?: contentView
        card.minimumWidth = 0

        contentView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        )

        val lp = card.layoutParams
            ?: FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        when (lp) {
            is FrameLayout.LayoutParams -> {
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                lp.marginStart = 0
                lp.marginEnd = 0
                lp.leftMargin = 0
                lp.rightMargin = 0
                lp.gravity = Gravity.CENTER_HORIZONTAL
                card.layoutParams = lp
            }
            is ViewGroup.MarginLayoutParams -> {
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                lp.marginStart = 0
                lp.marginEnd = 0
                lp.leftMargin = 0
                lp.rightMargin = 0
                card.layoutParams = lp
            }
            else -> {
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                card.layoutParams = lp
            }
        }
        // Tag for debugging / verification of applied width.
        contentView.setTag(R.id.dialogCard, cardWidthPx)
    }

    private fun screenWidthPx(context: Context): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = context.getSystemService(WindowManager::class.java)
            wm?.currentWindowMetrics?.bounds?.width()
                ?: context.resources.displayMetrics.widthPixels
        } else {
            @Suppress("DEPRECATION")
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .defaultDisplay.getMetrics(metrics)
            metrics.widthPixels
        }
    }

    private fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).roundToInt()
    }
}

/**
 * Shows a unified single-choice list dialog (title + tappable option rows).
 */
fun Context.showAppListDialog(
    title: CharSequence,
    options: List<CharSequence>,
    widthScale: Float? = null,
    onSelected: (Int) -> Unit,
): Dialog {
    val inflater = LayoutInflater.from(this)
    val view = inflater.inflate(R.layout.dialog_app_list, null)
    view.findViewById<TextView>(R.id.dialogTitle).text = title
    val container = view.findViewById<LinearLayout>(R.id.dialogOptions)

    val dialog = AppDialog.create(this, view, widthScale = widthScale)
    options.forEachIndexed { index, label ->
        val row = inflater.inflate(R.layout.item_app_dialog_option, container, false) as TextView
        row.text = label
        row.setOnClickListener {
            onSelected(index)
            dialog.dismiss()
        }
        container.addView(row)
    }
    dialog.show()
    return dialog
}
