package app.olauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotePanelCodecTest {

    @Test
    fun notesRoundTripInChronologicalOrder() {
        val notes = listOf(
            NoteMessage(id = "second", text = "abcd", createdAtMillis = 2000L, updatedAtMillis = 2500L, isPinned = true),
            NoteMessage(id = "first", text = "hello", createdAtMillis = 1000L),
        )

        val decoded = NotePanelCodec.decodeNotes(NotePanelCodec.encodeNotes(notes))

        assertEquals(listOf("first", "second"), decoded.map { it.id })
        assertEquals("abcd", decoded[1].text)
        assertEquals(2500L, decoded[1].updatedAtMillis)
        assertTrue(decoded[1].isPinned)
    }

    @Test
    fun corruptNotePayloadReturnsEmptyList() {
        val decoded = NotePanelCodec.decodeNotes("not-json")

        assertTrue(decoded.isEmpty())
    }

    @Test
    fun todosRoundTripWithDoneState() {
        val todos = listOf(
            TodoItem(id = "one", text = "buy milk", createdAtMillis = 1000L, isDone = true),
            TodoItem(id = "two", text = "ship app", createdAtMillis = 2000L),
        )

        val decoded = NotePanelCodec.decodeTodos(NotePanelCodec.encodeTodos(todos))

        assertEquals(2, decoded.size)
        assertTrue(decoded.first { it.id == "one" }.isDone)
        assertEquals("ship app", decoded.first { it.id == "two" }.text)
    }
}
