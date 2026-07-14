package app.sakinalauncher.data

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
    val isDone: Boolean = false,
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
    WIDGETS,
}

sealed class NotePanelRow {
    data class DaySeparator(val label: String, val dayStartMillis: Long) : NotePanelRow()
    data class SectionSeparator(val label: String) : NotePanelRow()
    data class Message(val note: NoteMessage, val repeatLabel: String? = null) : NotePanelRow()
    data class Todo(val item: TodoItem) : NotePanelRow()
}

object NotePanelRows {
    /**
     * Localized UI chrome for list separators. Defaults stay English for unit tests
     * without Android resources; production passes [android.content.res.Resources] strings.
     */
    data class Labels(
        val pinned: String = "Pinned",
        val active: String = "Active",
        val done: String = "Done",
        val today: String = "Today",
        val yesterday: String = "Yesterday",
        /** Format with one arg: the previous occurrence label. */
        val lastFormat: String = "Last: %s",
    )

    fun noteRows(
        notes: List<NoteMessage>,
        nowMillis: Long = System.currentTimeMillis(),
        locale: Locale = Locale.getDefault(),
        labels: Labels = Labels(),
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
                val previousWhen = repeatLabel(previous.createdAtMillis, nowMillis, locale, labels)
                repeatLabelsById[note.id] = labels.lastFormat.format(previousWhen)
            }
            previousByText[key] = note
        }

        val pinnedNotes = sortedNotes.filter { it.isPinned }
        val regularNotes = sortedNotes.filterNot { it.isPinned }
        if (pinnedNotes.isNotEmpty()) {
            rows.add(NotePanelRow.SectionSeparator(labels.pinned))
            pinnedNotes.forEach { note ->
                rows.add(NotePanelRow.Message(note, repeatLabelsById[note.id]))
            }
        }

        regularNotes.forEach { note ->
            val dayStart = dayStartMillis(note.createdAtMillis)
            if (dayStart != previousDayStart) {
                rows.add(
                    NotePanelRow.DaySeparator(
                        formatDayLabel(dayStart, nowMillis, locale, labels),
                        dayStart,
                    ),
                )
                previousDayStart = dayStart
            }
            rows.add(NotePanelRow.Message(note, repeatLabelsById[note.id]))
        }
        return rows
    }

    fun todoRows(todos: List<TodoItem>, labels: Labels = Labels()): List<NotePanelRow> {
        val rows = mutableListOf<NotePanelRow>()
        val activeTodos = todos.filterNot { it.isDone }.sortedBy { it.createdAtMillis }
        val doneTodos = todos.filter { it.isDone }.sortedBy { it.createdAtMillis }
        if (activeTodos.isNotEmpty()) {
            rows.add(NotePanelRow.SectionSeparator(labels.active))
            rows.addAll(activeTodos.map { NotePanelRow.Todo(it) })
        }
        if (doneTodos.isNotEmpty()) {
            rows.add(NotePanelRow.SectionSeparator(labels.done))
            rows.addAll(doneTodos.map { NotePanelRow.Todo(it) })
        }
        return rows
    }

    fun timeLabel(millis: Long, locale: Locale = Locale.getDefault()): String {
        return SimpleDateFormat("HH:mm", locale).format(Date(millis))
    }

    private fun formatDayLabel(
        dayStartMillis: Long,
        nowMillis: Long,
        locale: Locale,
        labels: Labels,
    ): String {
        return when ((dayStartMillis(nowMillis) - dayStartMillis) / Constants.ONE_DAY_IN_MILLIS) {
            0L -> labels.today
            1L -> labels.yesterday
            else -> SimpleDateFormat("EEE, d MMM", locale).format(Date(dayStartMillis))
        }
    }

    private fun repeatLabel(
        millis: Long,
        nowMillis: Long,
        locale: Locale,
        labels: Labels,
    ): String {
        val dayLabel = formatDayLabel(dayStartMillis(millis), nowMillis, locale, labels)
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
