package app.olauncher.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class NoteMessage(
    val id: String,
    val text: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long? = null,
    val isPinned: Boolean = false,
)

data class TodoItem(
    val id: String,
    val text: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long? = null,
    val isDone: Boolean = false,
)

enum class NotePanelMode {
    NOTES,
    TODO,
    TIMER,
}

sealed class NotePanelRow {
    data class DaySeparator(val label: String, val dayStartMillis: Long) : NotePanelRow()
    data class SectionSeparator(val label: String) : NotePanelRow()
    data class Message(val note: NoteMessage, val repeatLabel: String? = null) : NotePanelRow()
    data class Todo(val item: TodoItem) : NotePanelRow()
}

object NotePanelRows {
    fun noteRows(
        notes: List<NoteMessage>,
        nowMillis: Long = System.currentTimeMillis(),
        locale: Locale = Locale.getDefault(),
    ): List<NotePanelRow> {
        val rows = mutableListOf<NotePanelRow>()
        var previousDayStart: Long? = null

        val sortedNotes = notes.sortedBy { it.createdAtMillis }
        val previousByText = mutableMapOf<String, NoteMessage>()
        val repeatLabelsById = mutableMapOf<String, String>()
        sortedNotes.forEach { note ->
            val key = note.text.normalizedNoteKey()
            val previous = previousByText[key]
            if (previous != null && dayStartMillis(previous.createdAtMillis) != dayStartMillis(note.createdAtMillis)) {
                repeatLabelsById[note.id] = "Last: ${repeatLabel(previous.createdAtMillis, nowMillis, locale)}"
            }
            previousByText[key] = note
        }

        val pinnedNotes = sortedNotes.filter { it.isPinned }
        val regularNotes = sortedNotes.filterNot { it.isPinned }
        if (pinnedNotes.isNotEmpty()) {
            rows.add(NotePanelRow.SectionSeparator("Pinned"))
            pinnedNotes.forEach { note ->
                rows.add(NotePanelRow.Message(note, repeatLabelsById[note.id]))
            }
        }

        regularNotes.forEach { note ->
            val dayStart = dayStartMillis(note.createdAtMillis)
            if (dayStart != previousDayStart) {
                rows.add(NotePanelRow.DaySeparator(formatDayLabel(dayStart, nowMillis, locale), dayStart))
                previousDayStart = dayStart
            }
            rows.add(NotePanelRow.Message(note, repeatLabelsById[note.id]))
        }
        return rows
    }

    fun todoRows(todos: List<TodoItem>): List<NotePanelRow> {
        val rows = mutableListOf<NotePanelRow>()
        val activeTodos = todos.filterNot { it.isDone }.sortedBy { it.createdAtMillis }
        val doneTodos = todos.filter { it.isDone }.sortedBy { it.createdAtMillis }
        if (activeTodos.isNotEmpty()) {
            rows.add(NotePanelRow.SectionSeparator("Active"))
            rows.addAll(activeTodos.map { NotePanelRow.Todo(it) })
        }
        if (doneTodos.isNotEmpty()) {
            rows.add(NotePanelRow.SectionSeparator("Done"))
            rows.addAll(doneTodos.map { NotePanelRow.Todo(it) })
        }
        return rows
    }

    fun timeLabel(millis: Long, locale: Locale = Locale.getDefault()): String {
        return SimpleDateFormat("HH:mm", locale).format(Date(millis))
    }

    private fun formatDayLabel(dayStartMillis: Long, nowMillis: Long, locale: Locale): String {
        return when ((dayStartMillis(nowMillis) - dayStartMillis) / Constants.ONE_DAY_IN_MILLIS) {
            0L -> "Today"
            1L -> "Yesterday"
            else -> SimpleDateFormat("EEE, d MMM", locale).format(Date(dayStartMillis))
        }
    }

    private fun repeatLabel(millis: Long, nowMillis: Long, locale: Locale): String {
        val dayLabel = formatDayLabel(dayStartMillis(millis), nowMillis, locale)
        return "$dayLabel ${timeLabel(millis, locale)}"
    }

    private fun String.normalizedNoteKey(): String = trim().lowercase(Locale.ROOT)

    private fun dayStartMillis(millis: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
