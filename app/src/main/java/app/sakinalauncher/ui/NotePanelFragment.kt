package app.sakinalauncher.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.text.InputType
import android.util.TypedValue
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.sakinalauncher.MainViewModel
import app.sakinalauncher.R
import app.sakinalauncher.data.Constants
import app.sakinalauncher.data.NoteMessage
import app.sakinalauncher.data.NotePanelMode
import app.sakinalauncher.data.NotePanelRows
import app.sakinalauncher.data.NotePanelStore
import app.sakinalauncher.data.Prefs
import app.sakinalauncher.data.TodoItem
import app.sakinalauncher.databinding.FragmentNotePanelBinding
import app.sakinalauncher.helper.hideKeyboard
import app.sakinalauncher.helper.launchSwipeApp
import app.sakinalauncher.helper.showKeyboard
import app.sakinalauncher.helper.showToast
import app.sakinalauncher.listener.OnSwipeTouchListener
import java.util.Locale
import kotlin.math.abs

class NotePanelFragment : Fragment() {

    private lateinit var store: NotePanelStore
    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: NotePanelAdapter
    private var mode: NotePanelMode = NotePanelMode.NOTES
    private var noteDraft: String = ""
    private var todoDraft: String = ""
    private var sourceDirection: String? = null
    private var timer: CountDownTimer? = null
    private var timerTotalMillis: Long = 0L
    private var timerRemainingMillis: Long = 0L
    private var timerRunning: Boolean = false
    private var selectedNoteIds: MutableSet<String> = mutableSetOf()
    private val smoothInterpolator = PathInterpolator(0.2f, 0f, 0f, 1f)

    private var _binding: FragmentNotePanelBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentNotePanelBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        store = NotePanelStore(requireContext())
        prefs = Prefs(requireContext())
        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")
        sourceDirection = savedInstanceState?.getString(KEY_SOURCE_DIRECTION)
            ?: arguments?.getString(Constants.Key.SWIPE_DIRECTION)
        mode = readMode(savedInstanceState?.getString(KEY_MODE))
            ?: readMode(arguments?.getString(Constants.Key.NOTE_PANEL_MODE))
            ?: NotePanelMode.NOTES
        noteDraft = savedInstanceState?.getString(KEY_NOTE_DRAFT).orEmpty()
        todoDraft = savedInstanceState?.getString(KEY_TODO_DRAFT).orEmpty()
        restoreTimerState(savedInstanceState)

        initAdapter()
        initSwipeFlow()
        initClickListeners()
        binding.input.setText(draftForMode())
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        storeCurrentDraft()
        outState.putString(KEY_MODE, mode.name)
        outState.putString(KEY_NOTE_DRAFT, noteDraft)
        outState.putString(KEY_TODO_DRAFT, todoDraft)
        outState.putString(KEY_SOURCE_DIRECTION, sourceDirection)
        outState.putLong(KEY_TIMER_TOTAL, timerTotalMillis)
        outState.putLong(KEY_TIMER_REMAINING, timerRemainingMillis)
        outState.putBoolean(KEY_TIMER_RUNNING, timerRunning)
        super.onSaveInstanceState(outState)
    }

    private fun initAdapter() {
        adapter = NotePanelAdapter(
            onNoteClick = ::selectNote,
            onTodoClick = {
                store.toggleTodo(it.id)
                render()
            },
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.recyclerView.itemAnimator = null
    }

    private fun initClickListeners() {
        binding.notesTab.setOnClickListener { switchMode(NotePanelMode.NOTES) }
        binding.todoTab.setOnClickListener { switchMode(NotePanelMode.TODO) }
        binding.timerTab.setOnClickListener { switchMode(NotePanelMode.TIMER) }
        binding.timerValue.setOnClickListener { showDurationDialog() }
        binding.timerStart.setOnClickListener { startTimer() }
        binding.timerPause.setOnClickListener { togglePauseResume() }
        binding.timerReset.setOnClickListener { resetTimer() }
        binding.send.setOnClickListener { submitInput() }
        binding.noteActionDelete.setOnClickListener {
            if (selectedNoteIds.size == 1) {
                deleteSingleSelected()
            } else {
                deleteSelectedNotes()
            }
        }
        binding.noteActionEdit.setOnClickListener {
            if (selectedNoteIds.size == 1) {
                showEditNoteById(selectedNoteIds.first())
            }
        }
        binding.noteActionCopy.setOnClickListener {
            if (selectedNoteIds.size == 1) {
                copySingleSelected()
            }
        }
        binding.noteActionDone.setOnClickListener {
            if (selectedNoteIds.size == 1) {
                store.toggleNoteDone(selectedNoteIds.first())
                render()
            } else {
                doneSelectedNotes()
            }
        }
        binding.noteActionClose.setOnClickListener { clearNoteSelection() }
        binding.noteActionSelectAll.setOnClickListener { selectAllNotes() }
        binding.input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitInput()
                true
            } else {
                false
            }
        }
        binding.input.setOnKeyListener { _, keyCode, event ->
            val isSingleLineEnter = keyCode == KeyEvent.KEYCODE_ENTER &&
                    event.action == KeyEvent.ACTION_DOWN &&
                    event.isShiftPressed.not() &&
                    binding.input.text?.contains('\n') != true
            if (isSingleLineEnter) {
                submitInput()
                true
            } else {
                false
            }
        }
    }

    private fun initSwipeFlow() {
        binding.root.setOnTouchListener(object : OnSwipeTouchListener(requireContext()) {
            override fun onSwipeLeft() {
                handleHorizontalSwipe(Constants.SwipeDirection.LEFT)
            }

            override fun onSwipeRight() {
                handleHorizontalSwipe(Constants.SwipeDirection.RIGHT)
            }
        })

        val detector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float,
            ): Boolean {
                val start = e1 ?: return false
                val diffX = e2.x - start.x
                val diffY = e2.y - start.y
                if (abs(diffX) > abs(diffY) && abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    handleHorizontalSwipe(
                        if (diffX < 0) Constants.SwipeDirection.LEFT else Constants.SwipeDirection.RIGHT
                    )
                    return true
                }
                return false
            }
        })

        binding.recyclerView.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                detector.onTouchEvent(e)
                return false
            }
        })
    }

    private fun handleHorizontalSwipe(direction: String) {
        if (direction == sourceDirection) {
            launchSwipeApp(
                context = requireContext(),
                viewModel = viewModel,
                prefs = prefs,
                isLeft = direction == Constants.SwipeDirection.LEFT
            )
        } else {
            closePanel()
        }
    }

    private fun submitInput() {
        if (mode == NotePanelMode.TIMER) return
        val text = binding.input.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) {
            requireContext().showToast(
                getString(
                    if (mode == NotePanelMode.NOTES) R.string.note_empty_message
                    else R.string.todo_empty_message
                )
            )
            return
        }

        if (mode == NotePanelMode.NOTES) {
            store.addNote(text)
            noteDraft = ""
        } else {
            store.addTodo(text)
            todoDraft = ""
        }
        binding.input.text?.clear()
        render(scrollToBottom = true)
    }

    private fun render(scrollToBottom: Boolean = false) {
        val notes = if (mode == NotePanelMode.NOTES) store.getNotes() else emptyList()
        val todos = if (mode == NotePanelMode.TODO) store.getTodos() else emptyList()
        if (mode != NotePanelMode.NOTES && selectedNoteIds.isNotEmpty()) {
            selectedNoteIds.clear()
        }
        val rows = when (mode) {
            NotePanelMode.NOTES -> NotePanelRows.noteRows(notes)
            NotePanelMode.TODO -> NotePanelRows.todoRows(todos)
            NotePanelMode.TIMER -> emptyList()
        }
        val hasSelection = selectedNoteIds.isNotEmpty()

        binding.title.text = getString(
            when (mode) {
                NotePanelMode.NOTES -> R.string.notes
                NotePanelMode.TODO -> R.string.todo
                NotePanelMode.TIMER -> R.string.timer
            }
        )
        binding.input.hint = getString(if (mode == NotePanelMode.NOTES) R.string.write_note else R.string.write_todo)
        binding.send.contentDescription = getString(if (mode == NotePanelMode.NOTES) R.string.send else R.string.add)
        binding.notesTab.setBackgroundResource(
            if (mode == NotePanelMode.NOTES) R.drawable.bg_note_segment_selected else 0
        )
        binding.todoTab.setBackgroundResource(
            if (mode == NotePanelMode.TODO) R.drawable.bg_note_segment_selected else 0
        )
        binding.timerTab.setBackgroundResource(
            if (mode == NotePanelMode.TIMER) R.drawable.bg_note_segment_selected else 0
        )
        binding.notesTab.alpha = if (mode == NotePanelMode.NOTES) 1.0f else 0.62f
        binding.todoTab.alpha = if (mode == NotePanelMode.TODO) 1.0f else 0.62f
        binding.timerTab.alpha = if (mode == NotePanelMode.TIMER) 1.0f else 0.62f
        binding.recyclerView.isVisible = mode != NotePanelMode.TIMER
        binding.composer.isVisible = mode != NotePanelMode.TIMER && !hasSelection
        binding.noteSelectionCount.text = selectedNoteIds.size.toString()
        renderNoteActionBar(hasSelection, selectedNoteIds.size > 1)
        binding.timerLayout.isVisible = mode == NotePanelMode.TIMER
        binding.emptyState.text =
            getString(if (mode == NotePanelMode.NOTES) R.string.no_notes_yet else R.string.no_todos_yet)
        binding.emptyState.isVisible = rows.isEmpty() && mode != NotePanelMode.TIMER
        adapter.setRows(rows, selectedNoteIds)
        renderTimer()

        if (scrollToBottom && rows.isNotEmpty()) {
            binding.recyclerView.post { binding.recyclerView.scrollToPosition(rows.lastIndex) }
        }
    }

    private fun renderNoteActionBar(isSelected: Boolean, isMultiSelect: Boolean) {
        if (isSelected) {
            binding.noteActionDelete.isVisible = !isMultiSelect
            binding.noteActionEdit.isVisible = !isMultiSelect
            binding.noteActionCopy.isVisible = !isMultiSelect
            binding.noteActionDone.isVisible = true
            if (binding.noteActionBar.isVisible.not()) {
                binding.noteActionBar.alpha = 0f
                binding.noteActionBar.translationY = dp(18).toFloat()
                binding.noteActionBar.scaleX = 0.96f
                binding.noteActionBar.scaleY = 0.96f
                binding.noteActionBar.isVisible = true
            }
            binding.noteActionBar.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(190L)
                .setInterpolator(smoothInterpolator)
                .start()
        } else if (binding.noteActionBar.isVisible) {
            binding.noteActionBar.animate()
                .alpha(0f)
                .translationY(dp(18).toFloat())
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(150L)
                .setInterpolator(smoothInterpolator)
                .withEndAction {
                    if (selectedNoteIds.isEmpty()) binding.noteActionBar.isVisible = false
                }
                .start()
        }
    }

    private fun selectNote(note: NoteMessage) {
        if (note.id in selectedNoteIds) {
            selectedNoteIds.remove(note.id)
        } else {
            selectedNoteIds.add(note.id)
        }
        binding.input.hideKeyboard()
        render()
    }

    private fun clearNoteSelection() {
        selectedNoteIds.clear()
        render()
    }

    private fun deleteSingleSelected() {
        val id = selectedNoteIds.first()
        store.deleteNote(id)
        selectedNoteIds.remove(id)
        render()
    }

    private fun showEditNoteById(id: String) {
        val note = store.getNotes().firstOrNull { it.id == id } ?: return
        showEditDialog(
            title = getString(R.string.edit_note),
            initialText = note.text,
            emptyMessage = getString(R.string.note_empty_message),
        ) { text ->
            store.updateNote(id, text)
            selectedNoteIds.remove(id)
            render()
        }
    }

    private fun copySingleSelected() {
        val id = selectedNoteIds.first()
        val note = store.getNotes().firstOrNull { it.id == id } ?: return
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.notes), note.text))
        requireContext().showToast(getString(R.string.copied))
    }

    private fun copyNote(note: NoteMessage) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.notes), note.text))
        requireContext().showToast(getString(R.string.copied))
    }

    private fun deleteNote(note: NoteMessage) {
        store.deleteNote(note.id)
        selectedNoteIds.remove(note.id)
        render()
    }

    private fun showEditNote(note: NoteMessage, clearSelectionAfterSave: Boolean) {
        showEditDialog(
            title = getString(R.string.edit_note),
            initialText = note.text,
            emptyMessage = getString(R.string.note_empty_message),
        ) { text ->
            store.updateNote(note.id, text)
            if (clearSelectionAfterSave) selectedNoteIds.remove(note.id)
            render()
        }
    }

    private fun selectAllNotes() {
        val allIds = if (mode == NotePanelMode.NOTES) {
            store.getNotes().sortedByDescending { it.createdAtMillis }.map { it.id }
        } else {
            store.getTodos().sortedByDescending { it.createdAtMillis }.map { it.id }
        }
        selectedNoteIds.clear()
        selectedNoteIds.addAll(allIds)
        binding.input.hideKeyboard()
        render()
    }

    private fun deleteSelectedNotes() {
        selectedNoteIds.toList().forEach { store.deleteNote(it) }
        selectedNoteIds.clear()
        render()
    }

    private fun doneSelectedNotes() {
        selectedNoteIds.toList().forEach { store.toggleNoteDone(it) }
        selectedNoteIds.clear()
        render()
    }

    private fun switchMode(nextMode: NotePanelMode) {
        if (mode == nextMode) return
        storeCurrentDraft()
        mode = nextMode
        if (mode == NotePanelMode.TIMER) binding.input.hideKeyboard()
        binding.input.setText(draftForMode())
        binding.input.setSelection(binding.input.text?.length ?: 0)
        render()
    }

    private fun storeCurrentDraft() {
        val text = binding.input.text?.toString().orEmpty()
        when (mode) {
            NotePanelMode.NOTES -> noteDraft = text
            NotePanelMode.TODO -> todoDraft = text
            NotePanelMode.TIMER -> Unit
        }
    }

    private fun draftForMode(): String {
        return when (mode) {
            NotePanelMode.NOTES -> noteDraft
            NotePanelMode.TODO -> todoDraft
            NotePanelMode.TIMER -> ""
        }
    }

    private fun closePanel() {
        binding.input.hideKeyboard()
        runCatching {
            if (findNavController().popBackStack().not()) {
                findNavController().navigate(R.id.mainFragment)
            }
        }
    }

    private fun startTimer() {
        if (timerRemainingMillis <= 0L) {
            timerTotalMillis = prefs.pomodoroFocusMillis
            timerRemainingMillis = timerTotalMillis
        }
        timer?.cancel()
        timerRunning = true
        prefs.pomodoroTimerTotalMillis = timerTotalMillis
        prefs.pomodoroTimerEndElapsedRealtime = SystemClock.elapsedRealtime() + timerRemainingMillis
        prefs.pomodoroTimerRemainingMillis = timerRemainingMillis
        timer = object : CountDownTimer(timerRemainingMillis, TIMER_TICK_MS) {
            override fun onTick(millisUntilFinished: Long) {
                timerRemainingMillis = millisUntilFinished
                prefs.pomodoroTimerRemainingMillis = millisUntilFinished
                renderTimer()
            }

            override fun onFinish() {
                timerRunning = false
                timerRemainingMillis = 0L
                prefs.pomodoroTimerRemainingMillis = 0L
                prefs.pomodoroTimerEndElapsedRealtime = 0L
                renderTimer()
                requireContext().showToast(getString(R.string.timer))
            }
        }.start()
        renderTimer()
    }

    private fun togglePauseResume() {
        if (timerRunning) {
            timer?.cancel()
            timerRunning = false
            prefs.pomodoroTimerEndElapsedRealtime = 0L
            prefs.pomodoroTimerRemainingMillis = timerRemainingMillis
        } else {
            startTimer()
            return
        }
        renderTimer()
    }

    private fun resetTimer() {
        timer?.cancel()
        timerRunning = false
        timerTotalMillis = prefs.pomodoroFocusMillis
        timerRemainingMillis = timerTotalMillis
        prefs.pomodoroTimerTotalMillis = timerTotalMillis
        prefs.pomodoroTimerRemainingMillis = timerRemainingMillis
        prefs.pomodoroTimerEndElapsedRealtime = 0L
        renderTimer()
    }

    private fun renderTimer() {
        if (_binding == null) return
        val total = timerTotalMillis.coerceAtLeast(1L)
        val completed = 1f - (timerRemainingMillis.toFloat() / total.toFloat())
        binding.timerProgress.progress = completed
        binding.timerValue.text = formatTimer(timerRemainingMillis)
        binding.timerStart.isVisible = timerRunning.not()
        binding.timerControls.isVisible = timerRunning || timerRemainingMillis != timerTotalMillis
        binding.timerPause.text = getString(if (timerRunning) R.string.pause else R.string.resume)
    }

    private fun showDurationDialog() {
        val context = requireContext()
        val currentSeconds = (timerRemainingMillis.coerceAtLeast(0L) + 999L) / 1000L
        val minutesInput = AppCompatEditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.minutes)
            setText((currentSeconds / 60L).toString())
            setSelectAllOnFocus(true)
        }
        val secondsInput = AppCompatEditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.seconds)
            setText((currentSeconds % 60L).toString())
            setSelectAllOnFocus(true)
        }
        val padding = (18 * resources.displayMetrics.density).toInt()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, 0, padding, 0)
            addView(minutesInput)
            addView(secondsInput)
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.duration)
            .setView(container)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.close, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val minutes = minutesInput.text?.toString()?.toIntOrNull()?.coerceIn(0, 999) ?: 0
                val seconds = secondsInput.text?.toString()?.toIntOrNull()?.coerceIn(0, 59) ?: 0
                if (minutes == 0 && seconds == 0) {
                    context.showToast(getString(R.string.timer_duration_empty))
                    return@setOnClickListener
                }
                prefs.pomodoroFocusMinutes = minutes
                prefs.pomodoroFocusSeconds = seconds
                timer?.cancel()
                timerRunning = false
                timerTotalMillis = prefs.pomodoroFocusMillis
                timerRemainingMillis = timerTotalMillis
                prefs.pomodoroTimerTotalMillis = timerTotalMillis
                prefs.pomodoroTimerRemainingMillis = timerRemainingMillis
                prefs.pomodoroTimerEndElapsedRealtime = 0L
                renderTimer()
                dialog.dismiss()
            }
            minutesInput.showKeyboard()
        }
        dialog.show()
    }

    private fun formatTimer(millis: Long): String {
        val totalSeconds = ((millis.coerceAtLeast(0L) + 999L) / 1000L)
        return String.format(Locale.getDefault(), "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L)
    }

    private fun readMode(value: String?): NotePanelMode? {
        return value?.let { runCatching { NotePanelMode.valueOf(it) }.getOrNull() }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun themeColor(attr: Int): Int {
        val outValue = TypedValue()
        requireContext().theme.resolveAttribute(attr, outValue, true)
        return outValue.data
    }

    private fun restoreTimerState(savedInstanceState: Bundle?) {
        timerTotalMillis = savedInstanceState?.getLong(KEY_TIMER_TOTAL, 0L)?.takeIf { it > 0L }
            ?: prefs.pomodoroTimerTotalMillis
        timerRemainingMillis = savedInstanceState?.getLong(KEY_TIMER_REMAINING, 0L)?.takeIf { it >= 0L }
            ?: prefs.pomodoroTimerRemainingMillis
        val savedRunning = savedInstanceState?.getBoolean(KEY_TIMER_RUNNING)
        val endElapsed = prefs.pomodoroTimerEndElapsedRealtime
        if (savedRunning == true || endElapsed > 0L) {
            timerRemainingMillis = (endElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            if (timerRemainingMillis > 0L) {
                startTimer()
            } else {
                timerRunning = false
                prefs.pomodoroTimerEndElapsedRealtime = 0L
                prefs.pomodoroTimerRemainingMillis = 0L
            }
        }
        if (timerTotalMillis <= 0L) timerTotalMillis = prefs.pomodoroFocusMillis
        if (timerRemainingMillis < 0L) timerRemainingMillis = timerTotalMillis
    }

    private fun showEditDialog(
        title: String,
        initialText: String,
        emptyMessage: String,
        onSave: (String) -> Unit,
    ) {
        val input = AppCompatEditText(requireContext()).apply {
            setText(initialText)
            setSelectAllOnFocus(true)
            maxLines = 4
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(input)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.close, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = input.text?.toString()?.trim().orEmpty()
                if (text.isBlank()) {
                    requireContext().showToast(emptyMessage)
                    return@setOnClickListener
                }
                onSave(text)
                dialog.dismiss()
            }
            input.showKeyboard()
        }
        dialog.show()
    }

    override fun onStop() {
        binding.input.hideKeyboard()
        super.onStop()
    }

    override fun onDestroyView() {
        timer?.cancel()
        timer = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val KEY_MODE = "note_panel_mode"
        private const val KEY_NOTE_DRAFT = "note_panel_note_draft"
        private const val KEY_TODO_DRAFT = "note_panel_todo_draft"
        private const val KEY_SOURCE_DIRECTION = "note_panel_source_direction"
        private const val KEY_TIMER_TOTAL = "note_panel_timer_total"
        private const val KEY_TIMER_REMAINING = "note_panel_timer_remaining"
        private const val KEY_TIMER_RUNNING = "note_panel_timer_running"
        private const val TIMER_TICK_MS = 250L
        private const val SWIPE_THRESHOLD = 100
        private const val SWIPE_VELOCITY_THRESHOLD = 100
    }
}
