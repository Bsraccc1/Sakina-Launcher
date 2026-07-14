package app.sakinalauncher.data

import app.sakinalauncher.data.BoundWidget
import app.sakinalauncher.data.ProductiveWidgetStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductiveWidgetStoreTest {

    @Test
    fun encodeDecode_roundTripsWidgets() {
        val widgets = listOf(
            BoundWidget(12, "com.example/.MyWidget", heightDp = 140, widthDp = 220),
            BoundWidget(34, "com.other/.Clock", heightDp = 0, widthDp = 0),
        )
        val json = ProductiveWidgetStore.encode(widgets)
        val decoded = ProductiveWidgetStore.decode(json)
        assertEquals(widgets, decoded)
        assertEquals(220, decoded[0].widthDp)
        assertEquals(140, decoded[0].heightDp)
    }

    @Test
    fun decode_legacyWithoutWidth_defaultsToZero() {
        val json = """[{"id":9,"provider":"pkg/cls","heightDp":120}]"""
        val decoded = ProductiveWidgetStore.decode(json)
        assertEquals(1, decoded.size)
        assertEquals(0, decoded[0].widthDp)
        assertEquals(120, decoded[0].heightDp)
    }

    @Test
    fun decode_invalidPayload_returnsEmpty() {
        assertTrue(ProductiveWidgetStore.decode("not-json").isEmpty())
        assertTrue(ProductiveWidgetStore.decode(null).isEmpty())
        assertTrue(ProductiveWidgetStore.decode("[]").isEmpty())
    }

    @Test
    fun decode_skipsBrokenEntries() {
        val json = """[{"id":1,"provider":"a/b"},{"id":-1,"provider":"x"},{"provider":"only"}]"""
        val decoded = ProductiveWidgetStore.decode(json)
        assertEquals(1, decoded.size)
        assertEquals(1, decoded[0].appWidgetId)
    }
}
