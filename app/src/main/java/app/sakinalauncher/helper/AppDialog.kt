package app.sakinalauncher.helper

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import app.sakinalauncher.R

/**
 * Helpers that build dialogs sharing a single, consistent visual family:
 * a [R.drawable.bg_unified_dialog] surface, Poppins typography and themed
 * colors. Use these instead of the default Material AlertDialog so that every
 * dialog in the app (language, font, prayer pickers, message dialogs, ...)
 * looks like it belongs together.
 */
object AppDialog {

    /**
     * Wraps an arbitrary [contentView] in a borderless, transparent-window
     * [Dialog] so that only the view's own background (typically
     * bg_unified_dialog) is visible.
     */
    fun create(context: Context, contentView: View, matchHeight: Boolean = false): Dialog {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(contentView)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // Dim the content behind the dialog so settings underneath are no
            // longer visible through the (now opaque) dialog surface, keeping
            // focus on the dialog itself.
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.6f)
            // Resize the dialog window when the soft keyboard appears so the
            // input field is never hidden behind it.
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                if (matchHeight) ViewGroup.LayoutParams.MATCH_PARENT
                else ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        return dialog
    }
}

/**
 * Shows a unified single-choice list dialog (title + tappable option rows).
 * Replaces the default Material list AlertDialog used for language, font,
 * gesture targets, prayer provider, etc.
 */
fun Context.showAppListDialog(
    title: CharSequence,
    options: List<CharSequence>,
    onSelected: (Int) -> Unit,
): Dialog {
    val inflater = LayoutInflater.from(this)
    val view = inflater.inflate(R.layout.dialog_app_list, null)
    view.findViewById<TextView>(R.id.dialogTitle).text = title
    val container = view.findViewById<LinearLayout>(R.id.dialogOptions)

    val dialog = AppDialog.create(this, view)
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
