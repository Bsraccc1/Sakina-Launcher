package app.sakinalauncher.ui

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import app.sakinalauncher.R
import app.sakinalauncher.data.NoteMessage
import app.sakinalauncher.data.NotePanelRow
import app.sakinalauncher.data.NotePanelRows
import app.sakinalauncher.data.TodoItem
import app.sakinalauncher.databinding.AdapterNoteDaySeparatorBinding
import app.sakinalauncher.databinding.AdapterNoteMessageBinding
import app.sakinalauncher.databinding.AdapterTodoItemBinding

class NotePanelAdapter(
    private val onNoteClick: (NoteMessage) -> Unit,
    private val onTodoClick: (TodoItem) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<NotePanelRow> = emptyList()
    private var selectedNoteIds: Set<String> = emptySet()

    init {
        setHasStableIds(true)
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemId(position: Int): Long {
        return stableRowId(rows[position])
    }

    override fun getItemViewType(position: Int): Int {
        return when (rows[position]) {
            is NotePanelRow.DaySeparator,
            is NotePanelRow.SectionSeparator -> VIEW_TYPE_SEPARATOR
            is NotePanelRow.Message -> VIEW_TYPE_NOTE
            is NotePanelRow.Todo -> VIEW_TYPE_TODO
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SEPARATOR -> SeparatorViewHolder(AdapterNoteDaySeparatorBinding.inflate(inflater, parent, false))
            VIEW_TYPE_TODO -> TodoViewHolder(AdapterTodoItemBinding.inflate(inflater, parent, false))
            else -> NoteViewHolder(AdapterNoteMessageBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is NotePanelRow.DaySeparator -> (holder as SeparatorViewHolder).bind(row.label)
            is NotePanelRow.SectionSeparator -> (holder as SeparatorViewHolder).bind(row.label)
            is NotePanelRow.Message -> (holder as NoteViewHolder).bind(
                row = row,
                isSelected = row.note.id in selectedNoteIds,
                clickListener = onNoteClick,
            )
            is NotePanelRow.Todo -> (holder as TodoViewHolder).bind(
                todo = row.item,
                isSelected = row.item.id in selectedNoteIds,
                clickListener = onTodoClick,
            )
        }
    }

    fun setRows(rows: List<NotePanelRow>, selectedNoteIds: Set<String> = emptySet()) {
        val oldRows = this.rows
        val oldSelectedIds = this.selectedNoteIds
        val nextSelectedIds = selectedNoteIds.toSet()
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldRows.size

            override fun getNewListSize(): Int = rows.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return stableRowId(oldRows[oldItemPosition]) == stableRowId(rows[newItemPosition])
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldRow = oldRows[oldItemPosition]
                val newRow = rows[newItemPosition]
                return oldRow == newRow &&
                    isSelected(oldRow, oldSelectedIds) == isSelected(newRow, nextSelectedIds)
            }
        })
        this.rows = rows
        this.selectedNoteIds = nextSelectedIds
        diff.dispatchUpdatesTo(this)
    }

    private fun stableRowId(row: NotePanelRow): Long {
        return when (row) {
            is NotePanelRow.DaySeparator -> "day:${row.dayStartMillis}".hashCode().toLong()
            is NotePanelRow.SectionSeparator -> "section:${row.label}".hashCode().toLong()
            is NotePanelRow.Message -> "note:${row.note.id}".hashCode().toLong()
            is NotePanelRow.Todo -> "todo:${row.item.id}".hashCode().toLong()
        }
    }

    private fun NotePanelRow.selectedId(): String? {
        return when (this) {
            is NotePanelRow.Message -> note.id
            is NotePanelRow.Todo -> item.id
            else -> null
        }
    }

    private fun isSelected(row: NotePanelRow, selectedIds: Set<String>): Boolean {
        return row.selectedId()?.let { it in selectedIds } ?: false
    }

    class SeparatorViewHolder(private val binding: AdapterNoteDaySeparatorBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(label: String) = with(binding) {
            dayLabel.text = label
        }
    }

    class NoteViewHolder(private val binding: AdapterNoteMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(
            row: NotePanelRow.Message,
            isSelected: Boolean,
            clickListener: (NoteMessage) -> Unit,
        ) = with(binding) {
            val note = row.note
            messageText.text = note.text
            messageTime.text = NotePanelRows.timeLabel(note.createdAtMillis)
            messageText.paintFlags = if (note.isDone) {
                messageText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                messageText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            messageText.alpha = if (note.isDone) 0.58f else 1.0f
            messageTime.alpha = if (note.isDone) 0.58f else 1.0f
            messageBubble.setBackgroundResource(
                if (isSelected) R.drawable.bg_note_bubble_selected else R.drawable.bg_note_bubble
            )
            repeatHint.isVisible = row.repeatLabel != null
            repeatHint.text = row.repeatLabel
            root.animate().cancel()
            root.alpha = if (isSelected && !note.isDone) 0.8f else 1f
            root.setOnClickListener { clickListener(note) }
        }
    }

    class TodoViewHolder(private val binding: AdapterTodoItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(
            todo: TodoItem,
            isSelected: Boolean,
            clickListener: (TodoItem) -> Unit,
        ) = with(binding) {
            todoCard.setBackgroundResource(
                if (isSelected) R.drawable.bg_note_bubble_selected else R.drawable.bg_note_bubble
            )
            todoText.text = todo.text
            todoText.paintFlags = if (todo.isDone) {
                todoText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                todoText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            todoText.alpha = if (todo.isDone) 0.55f else 1.0f
            todoCheck.alpha = if (todo.isDone) 0.9f else 1.0f
            todoCheck.setBackgroundResource(
                if (todo.isDone) R.drawable.bg_todo_checkbox_done else R.drawable.bg_todo_checkbox
            )
            todoCheckIcon.isVisible = todo.isDone
            root.animate().cancel()
            root.alpha = if (isSelected && !todo.isDone) 0.8f else 1f
            root.setOnClickListener { clickListener(todo) }
        }
    }

    companion object {
        private const val VIEW_TYPE_SEPARATOR = 0
        private const val VIEW_TYPE_NOTE = 1
        private const val VIEW_TYPE_TODO = 2
    }
}
