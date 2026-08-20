package app.sakinalauncher.data.muslim

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DhikrContentTest {
    @Test
    fun everyPeriodHasDisplayContent() {
        DhikrPeriod.entries.forEach { period ->
            val cards = DhikrContent.cardsFor(period)
            assertTrue("cards empty for $period", cards.isNotEmpty())
            assertTrue(cards.all { it.arabic.isNotBlank() && it.latin.isNotBlank() && it.meaningId.isNotBlank() })
            assertTrue(cards.none { it.arabic.contains("Ø") || it.arabic.contains("Ù") })
        }
    }

    @Test
    fun afterPrayerCardsAreComplete() {
        val cards = DhikrContent.cardsFor(DhikrPeriod.AFTER_PRAYER)

        assertEquals(AFTER_PRAYER_CARD_COUNT, cards.size)
        assertTrue(cards.all { it.repetitionCount >= 1 })
    }

    @Test
    fun afterPrayerCardsHaveBothLocaleTexts() {
        val indonesian = Locale.forLanguageTag("id")
        val english = Locale.ENGLISH

        DhikrContent.cardsFor(DhikrPeriod.AFTER_PRAYER).forEach { card ->
            assertTrue(card.arabic.isNotBlank())
            assertTrue(card.latin.isNotBlank())
            assertTrue(card.titleId.isNotBlank() && card.titleEn.isNotBlank())
            assertTrue(card.meaningId.isNotBlank() && card.meaningEn.isNotBlank())
            assertTrue(card.title(indonesian).isNotBlank())
            assertTrue(card.title(english).isNotBlank())
            assertTrue(card.meaning(indonesian).isNotBlank())
            assertTrue(card.meaning(english).isNotBlank())
        }
    }

    @Test
    fun tasbihTahmidTakbirAreCountedThirtyThreeTimesEach() {
        val cards = DhikrContent.cardsFor(DhikrPeriod.AFTER_PRAYER)
        val thirtyThree = cards.filter { it.repetitionCount == 33 }

        assertEquals(3, thirtyThree.size)
        assertTrue(thirtyThree.any { it.latin.startsWith("Subhanallah") })
        assertTrue(thirtyThree.any { it.latin.startsWith("Alhamdulillah") })
        assertTrue(thirtyThree.any { it.latin.startsWith("Allahu akbar") })
        assertTrue(thirtyThree.all { it.arabic.isNotBlank() })
    }

    @Test
    fun afterPrayerCardTitlesAreUnique() {
        val cards = DhikrContent.cardsFor(DhikrPeriod.AFTER_PRAYER)

        assertEquals(cards.size, cards.map { it.titleId }.distinct().size)
        assertEquals(cards.size, cards.map { it.titleEn }.distinct().size)
    }

    @Test
    fun afterPrayerReusesSharedRecitationText() {
        val morning = DhikrContent.cardsFor(DhikrPeriod.MORNING)
        val afterPrayer = DhikrContent.cardsFor(DhikrPeriod.AFTER_PRAYER)
        val reused = listOf("Ayat Kursi", "Surat Al-Ikhlas", "Surat Al-Falaq", "Surat An-Nas")

        reused.forEach { title ->
            val source = morning.first { it.titleId == title }
            val target = afterPrayer.first { it.titleId == title }
            assertEquals(source.arabic, target.arabic)
            assertEquals(source.latin, target.latin)
            assertEquals(1, target.repetitionCount)
        }
    }

    @Test
    fun afterPrayerMentionsItsHadithReferences() {
        val meanings = DhikrContent.cardsFor(DhikrPeriod.AFTER_PRAYER).map { it.meaningId }

        assertTrue(meanings.any { it.contains("Muslim no. 591") })
        assertTrue(meanings.any { it.contains("Muslim no. 597") })
        assertTrue(meanings.any { it.contains("Ibnu Majah no. 925") })
        assertTrue(meanings.any { it.contains("Shubuh") })
    }

    private companion object {
        const val AFTER_PRAYER_CARD_COUNT = 12
    }
}
