package app.sakinalauncher.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.fragment.app.Fragment
import app.sakinalauncher.R
import app.sakinalauncher.data.Constants
import app.sakinalauncher.data.muslim.DhikrContent
import app.sakinalauncher.data.muslim.DhikrPeriod
import app.sakinalauncher.databinding.FragmentDhikrPagerBinding
import app.sakinalauncher.helper.addPressScale
import app.sakinalauncher.listener.OnSwipeTouchListener

class DhikrPagerFragment : Fragment() {

    private var period = DhikrPeriod.MORNING
    private var index = 0
    private val countMap = mutableMapOf<Int, Int>() // Track count per card index
    private lateinit var prefs: app.sakinalauncher.data.Prefs
    private var pendingAdvance: Runnable? = null

    private var _binding: FragmentDhikrPagerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDhikrPagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        period = readPeriod(
            savedInstanceState?.getString(KEY_PERIOD)
                ?: arguments?.getString(Constants.Key.DHIKR_PERIOD)
        )
        prefs = app.sakinalauncher.data.Prefs(requireContext())
        index = savedInstanceState?.getInt(KEY_INDEX, 0) ?: 0
        loadCountProgress()
        initSwipe()
        initTasbihCounter()
        initNavButtons()
        render()
        app.sakinalauncher.helper.FontHelper.applyFont(binding.root, prefs)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_PERIOD, period.name)
        outState.putInt(KEY_INDEX, index)
        super.onSaveInstanceState(outState)
    }

    private fun initSwipe() {
        val listener = object : OnSwipeTouchListener(requireContext()) {
            override fun onSwipeLeft() {
                showNext()
            }

            override fun onSwipeRight() {
                showPrevious()
            }
        }
        // Attach to contentCard instead of rootLayout so ScrollView doesn't intercept
        binding.contentCard.setOnTouchListener(listener)
    }

    private fun initTasbihCounter() {
        binding.tasbihCounter.setOnClickListener {
            incrementCounter()
        }
    }

    private fun initNavButtons() {
        binding.btnPrev.setOnClickListener { showPrevious() }
        binding.btnNext.setOnClickListener { showNext() }
        binding.btnPrev.addPressScale(0.9f)
        binding.btnNext.addPressScale(0.9f)
    }
    private fun incrementCounter() {
        val cards = DhikrContent.cardsFor(period)
        if (cards.isEmpty()) return
        val card = cards[index]
        val cardIndex = index
        
        val currentCount = countMap.getOrDefault(cardIndex, 0)
        val newCount = currentCount + 1
        
        if (newCount >= card.repetitionCount) {
            countMap[cardIndex] = card.repetitionCount
            saveCountProgress(cardIndex, card.repetitionCount)
            render()
            animateCounter()
            pendingAdvance?.let { binding.tasbihCounter.removeCallbacks(it) }
            pendingAdvance = Runnable {
                val currentBinding = _binding ?: return@Runnable
                countMap[cardIndex] = 0
                saveCountProgress(cardIndex, 0)
                if (index == cardIndex) showNext() else currentBinding.root.post { render() }
            }
            binding.tasbihCounter.postDelayed(pendingAdvance, 300)
        } else {
            countMap[cardIndex] = newCount
            saveCountProgress(cardIndex, newCount)
            render()
            animateCounter()
        }
    }

    private fun loadCountProgress() {
        val cards = DhikrContent.cardsFor(period)
        cards.indices.forEach { idx ->
            val key = "dhikr_count_${period.name}_$idx"
            val count = prefs.getInt(key, 0)
            if (count > 0) {
                countMap[idx] = count
            }
        }
    }

    private fun saveCountProgress(index: Int, count: Int) {
        val key = "dhikr_count_${period.name}_$index"
        prefs.putInt(key, count)
    }

    private fun render() {
        val cards = DhikrContent.cardsFor(period)
        if (cards.isEmpty()) return
        index = index.coerceIn(0, cards.lastIndex)
        val card = cards[index]
        binding.title.text = getString(
            if (period == DhikrPeriod.MORNING) R.string.dzikir_pagi else R.string.dzikir_petang
        )
        binding.counter.text = getString(R.string.dhikr_position, index + 1, cards.size)
        binding.cardTitle.text = card.title()
        binding.arabic.text = card.arabic
        binding.latin.text = card.latin
        binding.meaning.text = card.meaning()
        
        val currentCount = countMap.getOrDefault(index, 0)
        binding.repetition.text = getString(R.string.dhikr_repetition, card.repetitionCount)
        binding.tasbihCounter.text = getString(R.string.dhikr_counter, currentCount, card.repetitionCount)
        
        val progress = (currentCount.toFloat() / card.repetitionCount.toFloat() * 100).toInt()
        binding.progressBar.progress = progress
    }

    private fun animateCounter() {
        val counter = _binding?.tasbihCounter ?: return
        counter.animate().cancel()
        counter.alpha = 0.4f
        counter.animate()
            .alpha(1.0f)
            .setDuration(250)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun showNext() {
        val cards = DhikrContent.cardsFor(period)
        index = if (index == cards.lastIndex) 0 else index + 1
        render()
    }

    private fun showPrevious() {
        val cards = DhikrContent.cardsFor(period)
        index = if (index == 0) cards.lastIndex else index - 1
        render()
    }

    private fun readPeriod(value: String?): DhikrPeriod {
        return value?.let { runCatching { DhikrPeriod.valueOf(it) }.getOrNull() } ?: DhikrPeriod.MORNING
    }

    override fun onDestroyView() {
        pendingAdvance?.let { _binding?.tasbihCounter?.removeCallbacks(it) }
        pendingAdvance = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val KEY_PERIOD = "dhikr_period"
        private const val KEY_INDEX = "dhikr_index"
    }
}
