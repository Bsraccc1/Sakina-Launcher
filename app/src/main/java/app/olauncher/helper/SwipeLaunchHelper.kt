package app.olauncher.helper

import android.content.Context
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs

fun launchSwipeApp(
    context: Context,
    viewModel: MainViewModel,
    prefs: Prefs,
    isLeft: Boolean,
) {
    val appName = if (isLeft) prefs.appNameSwipeLeft else prefs.appNameSwipeRight
    val packageName = if (isLeft) prefs.appPackageSwipeLeft else prefs.appPackageSwipeRight
    val activityClassName = if (isLeft) prefs.appActivityClassNameSwipeLeft else prefs.appActivityClassNameRight
    val shortcutId = if (isLeft) prefs.shortcutIdSwipeLeft else prefs.shortcutIdSwipeRight
    val isShortcut = if (isLeft) prefs.isShortcutSwipeLeft else prefs.isShortcutSwipeRight
    val userString = if (isLeft) prefs.appUserSwipeLeft else prefs.appUserSwipeRight

    if (appName.isEmpty()) {
        context.showToast(context.getString(R.string.long_press_to_select_app))
        return
    }

    if (isShortcut && shortcutId.isNotEmpty()) {
        viewModel.selectedApp(
            AppModel.PinnedShortcut(
                shortcutId = shortcutId,
                appLabel = appName,
                user = getUserHandleFromString(context, userString),
                key = null,
                appPackage = packageName,
                isNew = false,
            ),
            Constants.FLAG_LAUNCH_APP
        )
        return
    }

    if (packageName.isNotEmpty()) {
        viewModel.selectedApp(
            AppModel.App(
                appLabel = appName,
                key = null,
                appPackage = packageName,
                activityClassName = activityClassName,
                isNew = false,
                user = getUserHandleFromString(context, userString)
            ),
            Constants.FLAG_LAUNCH_APP
        )
        return
    }

    if (isLeft) openCameraApp(context) else openDialerApp(context)
}
