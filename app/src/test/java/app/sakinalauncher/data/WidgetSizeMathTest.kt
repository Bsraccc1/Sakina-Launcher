package app.sakinalauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the Productive widget resize bug: a committed drag height
 * must survive the round-trip store -> re-inflate instead of snapping back to the
 * size the widget had when it was first added.
 */
class WidgetSizeMathTest {

    private val density = 2.75f // Pixel-class density

    @Test
    fun commitHeightDp_convertsPixelsToDp() {
        // 385px @2.75 = 140dp
        assertEquals(140, WidgetSizeMath.commitHeightDp(385, density))
    }

    @Test
    fun commitHeightDp_clampsToAllowedRange() {
        assertEquals(WidgetSizeMath.MIN_HEIGHT_DP, WidgetSizeMath.commitHeightDp(1, density))
        assertEquals(WidgetSizeMath.MAX_HEIGHT_DP, WidgetSizeMath.commitHeightDp(999_999, density))
    }

    @Test
    fun commitHeightDp_survivesZeroDensity() {
        assertEquals(WidgetSizeMath.MIN_HEIGHT_DP, WidgetSizeMath.commitHeightDp(400, 0f))
    }

    @Test
    fun resolveHeightPx_returnsNullWhenNoUserOverride() {
        assertNull(WidgetSizeMath.resolveHeightPx(0, density))
        assertNull(WidgetSizeMath.resolveHeightPx(-5, density))
    }

    @Test
    fun resolveHeightPx_usesStoredOverride() {
        assertEquals(385, WidgetSizeMath.resolveHeightPx(140, density))
    }

    /** The actual bug: drag -> commit -> re-inflate must show the dragged height. */
    @Test
    fun committedHeight_roundTripsThroughStoreValue() {
        val draggedPx = 512
        val storedDp = WidgetSizeMath.commitHeightDp(draggedPx, density)
        val rendered = WidgetSizeMath.resolveHeightPx(storedDp, density)
        assertTrue(rendered != null)
        // Round-trip stays within one dp of the drag (rounding only), never reverts.
        assertTrue(
            "expected ~$draggedPx px, got $rendered",
            Math.abs(rendered!! - draggedPx) <= Math.ceil(density.toDouble()).toInt(),
        )
    }

    @Test
    fun committedHeight_isStableAcrossRepeatedReinflates() {
        var dp = WidgetSizeMath.commitHeightDp(512, density)
        repeat(5) {
            val px = WidgetSizeMath.resolveHeightPx(dp, density)!!
            dp = WidgetSizeMath.commitHeightDp(px, density)
        }
        assertEquals(WidgetSizeMath.commitHeightDp(512, density), dp)
    }

    @Test
    fun shouldRebuildForWidth_ignoresHeightOnlyChanges() {
        // A resize changes height, not width — rebuilding would discard the new size.
        assertFalse(WidgetSizeMath.shouldRebuildForWidth(1080, 1080))
    }

    @Test
    fun shouldRebuildForWidth_rebuildsOnRealWidthChange() {
        assertTrue(WidgetSizeMath.shouldRebuildForWidth(2160, 1080))
    }

    @Test
    fun shouldRebuildForWidth_ignoresUnmeasuredContainer() {
        assertFalse(WidgetSizeMath.shouldRebuildForWidth(0, 1080))
    }
}
