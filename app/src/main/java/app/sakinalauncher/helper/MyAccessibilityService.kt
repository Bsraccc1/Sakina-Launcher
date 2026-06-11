package app.sakinalauncher.helper

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.sakinalauncher.R
import app.sakinalauncher.data.Prefs

/**
 * Runs in a SEPARATE process (":serviceProcess", see AndroidManifest).
 * Because of that, it cannot share static state with the UI process — the
 * launcher triggers a lock by firing a TYPE_VIEW_CLICKED event on a hidden
 * placeholder view whose contentDescription we match here, then we call
 * performGlobalAction from inside the live service instance (the only context
 * where GLOBAL_ACTION_LOCK_SCREEN reliably works).
 */
class MyAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MyA11yService"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onServiceConnected() {
        Prefs(applicationContext).lockModeOn = true
        super.onServiceConnected()
        Log.d(TAG, "onServiceConnected: accessibility service bound")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            val source: AccessibilityNodeInfo = event.source ?: return
            if (source.className != "android.widget.FrameLayout") return

            when (source.contentDescription) {
                getString(R.string.lock_layout_description) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val ok = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                        Log.d(TAG, "lock: GLOBAL_ACTION_LOCK_SCREEN result=$ok")
                    }
                }
                getString(R.string.recents_layout_description) -> {
                    val ok = performGlobalAction(GLOBAL_ACTION_RECENTS)
                    Log.d(TAG, "recents: GLOBAL_ACTION_RECENTS result=$ok")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "onAccessibilityEvent error", e)
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt")
    }
}
