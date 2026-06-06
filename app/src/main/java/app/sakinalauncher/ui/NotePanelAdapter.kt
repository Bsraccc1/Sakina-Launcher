package app.sakinalauncher.ui

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
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
    private val onNoteLongClick: (NoteMessage, View) -> Unit,
    private val onTodoClick: (TodoItem) -> Unit,
    private val onTodoLongClick: (TodoItem, View) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<NotePanelRow> = emptyList()

    override fun getItemCount(): Int = rows.size

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
            is NotePanelRow.Message -> (holder as NoteViewHolder).bind(row, onNoteLongClick)
            is NotePanelRow.Todo -> (holder as TodoViewHolder).bind(row.item, onTodoClick, onTodoLongClick)
        }
    }

    fun setRows(rows: List<NotePanelRow>) {
        this.rows = rows
        notifyDataSetChanged()
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
            longClickListener: (NoteMessage, View) -> Unit,
        ) = with(binding) {
            val note = row.note
            messageText.text = note.text
            messageTime.text = NotePanelRows.timeLabel(note.createdAtMillis)
            repeatHint.isVisible = row.repeatLabel != null
            repeatHint.text = row.repeatLabel
            root.setOnLongClickListener {
                longClickListener(note, it)
                true
            }
        }
    }

    class TodoViewHolder(private val binding: AdapterTodoItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(
            todo: TodoItem,
            clickListener: (TodoItem) -> Unit,
            longClickListener: (TodoItem, View) -> Unit,
        ) = with(binding) {
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
            root.setOnClickListener { clickListener(todo) }
            root.setOnLongClickListener {
                longClickListener(todo, it)
                true
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_SEPARATOR = 0
        private const val VIEW_TYPE_NOTE = 1
        private const val VIEW_TYPE_TODO = 2
    }
}
