package app.sakinalauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class NotePanelRowsTest {

    @Test
    fun duplicateTextAcrossDifferentDaysKeepsSeparateRows() {
        val monday = millis(2026, Calendar.JUNE, 1, 9, 0)
        val tuesday = millis(2026, Calendar.JUNE, 2, 10, 30)
        val rows = NotePanelRows.noteRows(
            notes = listOf(
                NoteMessage(id = "monday", text = "abcd", createdAtMillis = monday),
                NoteMessage(id = "tuesday", text = "abcd", createdAtMillis = tuesday),
            ),
            nowMillis = tuesday,
            locale = Locale.US,
        )

        assertEquals(4, rows.size)
        assertEquals(2, rows.count { it is NotePanelRow.DaySeparator })
        assertEquals(2, rows.count { it is NotePanelRow.Message })
        assertEquals(
            listOf("abcd", "abcd"),
            rows.filterIsInstance<NotePanelRow.Message>().map { it.note.text }
        )
        assertEquals(
            "Last: Yesterday 09:00",
            rows.filterIsInstance<NotePanelRow.Message>()[1].repeatLabel
        )
    }

    @Test
    fun todoRowsGroupActiveAndDoneItems() {
        val rows = NotePanelRows.todoRows(
            listOf(
                TodoItem(id = "done", text = "done", createdAtMillis = 1000L, isDone = true),
                TodoItem(id = "open", text = "open", createdAtMillis = 2000L),
            )
        )

        assertEquals("Active", (rows[0] as NotePanelRow.SectionSeparator).label)
        assertEquals("open", (rows[1] as NotePanelRow.Todo).item.text)
        assertEquals("Done", (rows[2] as NotePanelRow.SectionSeparator).label)
        assertEquals("done", (rows[3] as NotePanelRow.Todo).item.text)
    }

    @Test
    fun pinnedNotesAreShownBeforeRegularDayGroups() {
        val monday = millis(2026, Calendar.JUNE, 1, 9, 0)
        val tuesday = millis(2026, Calendar.JUNE, 2, 10, 30)
        val rows = NotePanelRows.noteRows(
            notes = listOf(
                NoteMessage(id = "regular", text = "regular", createdAtMillis = tuesday),
                NoteMessage(id = "pinned", text = "pinned", createdAtMillis = monday, isPinned = true),
            ),
            nowMillis = tuesday,
            locale = Locale.US,
        )

        assertEquals("Pinned", (rows[0] as NotePanelRow.SectionSeparator).label)
        assertEquals("pinned", (rows[1] as NotePanelRow.Message).note.text)
        assertTrue(rows[2] is NotePanelRow.DaySeparator)
        assertEquals("regular", (rows[3] as NotePanelRow.Message).note.text)
    }

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute)
        }.timeInMillis
    }
}
