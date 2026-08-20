package app.sakinalauncher.data

/**
 * Pure size math for hosted AppWidgets. Kept free of Android dependencies so the
 * resize/commit rules are covered by plain JVM unit tests.
 */
object WidgetSizeMath {

    const val MIN_HEIGHT_DP = 80
    const val MAX_HEIGHT_DP = 600

    /**
     * Convert a committed drag height in pixels to the dp value that gets persisted.
     * Clamped to the same range the host frame enforces, so a stored value always
     * survives a round-trip through [resolveHeightPx].
     */
    fun commitHeightDp(heightPx: Int, density: Float): Int {
        if (density <= 0f) return MIN_HEIGHT_DP
        val dp = Math.round(heightPx / density)
        return dp.coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)
    }

    /**
     * Resolve a stored height back to pixels. Returns null when the widget has no
     * user override (heightDp == 0) and provider defaults should be used instead.
     */
    fun resolveHeightPx(heightDp: Int, density: Float): Int? {
        if (heightDp <= 0 || density <= 0f) return null
        val minPx = Math.round(MIN_HEIGHT_DP * density)
        val maxPx = Math.round(MAX_HEIGHT_DP * density)
        return Math.round(heightDp * density).coerceIn(minPx, maxPx)
    }

    /**
     * True when a re-inflate triggered by a container width change is pointless.
     * Rebuilding on an unchanged width throws away live host views for nothing.
     */
    fun shouldRebuildForWidth(newWidthPx: Int, lastWidthPx: Int): Boolean {
        if (newWidthPx <= 0) return false
        return newWidthPx != lastWidthPx
    }
}
