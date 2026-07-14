package app.sakinalauncher.helper

import android.appwidget.AppWidgetManager
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import app.sakinalauncher.R
import app.sakinalauncher.data.BoundWidget
import app.sakinalauncher.data.ProductiveWidgetStore

class PinItemActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setBackgroundDrawable(null)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            showToast(getString(R.string.pin_shortcuts_not_supported))
            finish()
            return
        }

        val launcherApps = getSystemService(LauncherApps::class.java)
        val pinItemRequest = launcherApps.getPinItemRequest(intent)

        when (pinItemRequest != null) {
            true -> handleRequestType(pinItemRequest)
            false -> showToast(getString(R.string.invalid_pin_request))
        }

        finish()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleRequestType(pinItemRequest: LauncherApps.PinItemRequest) {
        when (pinItemRequest.requestType) {
            LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT ->
                handleShortcutRequest(pinItemRequest)

            LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET ->
                handleWidgetRequest(pinItemRequest)

            else -> showToast(getString(R.string.widgets_not_supported))
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleShortcutRequest(pinItemRequest: LauncherApps.PinItemRequest) {
        val shortcutInfo = pinItemRequest.shortcutInfo
        if (shortcutInfo != null) {
            val success = pinItemRequest.accept()
            showToast(
                getString(
                    if (success) R.string.shortcut_pinned else R.string.shortcut_pin_failed,
                ),
            )
        } else {
            showToast(getString(R.string.invalid_pin_request))
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleWidgetRequest(pinItemRequest: LauncherApps.PinItemRequest) {
        // API 26+: getAppWidgetProviderInfo requires a Context argument.
        val info = pinItemRequest.getAppWidgetProviderInfo(this)
        if (info == null) {
            showToast(getString(R.string.widget_add_failed))
            return
        }
        val accepted = pinItemRequest.accept()
        if (!accepted) {
            showToast(getString(R.string.widget_add_failed))
            return
        }
        val extras = pinItemRequest.extras
        val appWidgetId = extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
        val provider = info.provider?.flattenToString().orEmpty()
        if (appWidgetId >= 0 && provider.isNotBlank()) {
            ProductiveWidgetStore(this).addWidget(BoundWidget(appWidgetId, provider))
            showToast(getString(R.string.add_widget))
        } else {
            showToast(getString(R.string.add_widget))
        }
    }
}
