package app.sakinalauncher.data.muslim

import org.junit.Assert.assertTrue
import org.junit.Test

class DhikrContentTest {
    @Test
    fun morningAndEveningCardsHaveDisplayContent() {
        val periods = listOf(DhikrPeriod.MORNING, DhikrPeriod.EVENING)

        periods.forEach { period ->
            val cards = DhikrContent.cardsFor(period)
            assertTrue(cards.isNotEmpty())
            assertTrue(cards.all { it.arabic.isNotBlank() && it.latin.isNotBlank() && it.meaningId.isNotBlank() })
            assertTrue(cards.none { it.arabic.contains("Ø") || it.arabic.contains("Ù") })
        }
    }
}
