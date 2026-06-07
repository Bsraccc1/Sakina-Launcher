package app.sakinalauncher.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import app.sakinalauncher.MainViewModel
import app.sakinalauncher.R
import app.sakinalauncher.data.Constants
import app.sakinalauncher.data.Prefs
import app.sakinalauncher.data.muslim.DhikrContent
import app.sakinalauncher.data.muslim.DhikrPeriod
import app.sakinalauncher.data.muslim.PrayerApiClient
import app.sakinalauncher.data.muslim.PrayerName
import app.sakinalauncher.data.muslim.PrayerSchedule
import app.sakinalauncher.data.muslim.PrayerScheduleResult
import app.sakinalauncher.data.muslim.PrayerTimeRepository
import app.sakinalauncher.data.muslim.PrayerTimeStore
import app.sakinalauncher.databinding.FragmentMuslimCenterBinding
import app.sakinalauncher.helper.launchSwipeApp
import app.sakinalauncher.helper.openUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MuslimCenterFragment : Fragment() {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var prayerStore: PrayerTimeStore
    private lateinit var repository: PrayerTimeRepository
    private var sourceDirection: String? = null
    private var currentSchedule: PrayerSchedule? = null
    private var prayerTickerJob: Job? = null

    private var _binding: FragmentMuslimCenterBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMuslimCenterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        prayerStore = PrayerTimeStore(requireContext())
        repository = PrayerTimeRepository(
            kemenagApi = PrayerApiClient.api,
            aladhanApi = PrayerApiClient.aladhanApi,
            store = prayerStore
        )
        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")
        sourceDirection = savedInstanceState?.getString(KEY_SOURCE_DIRECTION)
            ?: arguments?.getString(Constants.Key.SWIPE_DIRECTION)
        bindCachedContent()
        refreshPrayerTimes()
        initSwipe()
        binding.source.setOnClickListener {
            requireContext().openUrl(Constants.URL_DZIKIR_ALMANHAJ)
        }
        binding.location.setOnClickListener { openSettings() }
        binding.prayerCard.setOnClickListener { openSettings() }
        binding.morningDhikrCard.setOnClickListener { openDhikr(DhikrPeriod.MORNING) }
        binding.eveningDhikrCard.setOnClickListener { openDhikr(DhikrPeriod.EVENING) }
        startPrayerTicker()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_SOURCE_DIRECTION, sourceDirection)
        super.onSaveInstanceState(outState)
    }

    private fun bindCachedContent() {
        val cached = repository.cachedSchedule()
        if (cached == null) {
            binding.nextPrayer.text = getString(R.string.loading_prayer_times)
            binding.nextPrayerName.text = getString(R.string.prayer_times)
            clearPrayerChips()
            binding.prayerSource.text = getString(R.string.prayer_source)
        } else {
            renderSchedule(cached, getString(R.string.cached_schedule))
        }
        renderDhikrSummary()
    }

    private fun refreshPrayerTimes() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                when (val result = repository.refreshToday()) {
                    is PrayerScheduleResult.Fresh -> renderSchedule(result.schedule)
                    is PrayerScheduleResult.Cached -> renderSchedule(
                        result.schedule,
                        getString(R.string.cached_schedule)
                    )

                    is PrayerScheduleResult.Error -> renderPrayerError(result.message)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                renderPrayerError(error.message ?: getString(R.string.unable_to_load_prayer_times))
            }
        }
    }

    private fun renderSchedule(schedule: PrayerSchedule, badge: String? = null) {
        currentSchedule = schedule
        val nextPrayer = schedule.nextPrayer()
        binding.nextPrayerName.text = prayerNameLabel(nextPrayer.name)
        binding.nextPrayer.text = nextPrayer.time
        binding.location.text = listOf(schedule.city, schedule.province, schedule.dateLabel)
            .filter { it.isNotBlank() }
            .joinToString(" - ")
        renderPrayerChips(schedule, nextPrayer.name)
        binding.prayerSource.text = badge?.let {
            getString(R.string.prayer_source_cached, schedule.source, it, schedule.updatedLabel())
        } ?: getString(R.string.prayer_source_live, schedule.source, schedule.updatedLabel())
    }

    private fun renderPrayerError(message: String) {
        currentSchedule = null
        binding.nextPrayer.text = getString(R.string.unable_to_load_prayer_times)
        binding.nextPrayerName.text = getString(R.string.prayer_times)
        binding.location.text = message
        clearPrayerChips()
        binding.prayerSource.text = getString(R.string.prayer_source)
    }

    private fun renderPrayerChips(schedule: PrayerSchedule, activeName: PrayerName) {
        prayerChipViews().forEach { (name, view) ->
            val prayerTime = schedule.times.firstOrNull { it.name == name }
            view.text = getString(R.string.prayer_chip_value, prayerNameLabel(name), prayerTime?.time ?: "--:--")
            view.setBackgroundResource(
                if (name == activeName) R.drawable.bg_prayer_time_chip_active else R.drawable.bg_prayer_time_chip
            )
            view.alpha = if (name == activeName) 1f else 0.78f
        }
    }

    private fun clearPrayerChips() {
        prayerChipViews().forEach { (name, view) ->
            view.text = getString(R.string.prayer_chip_value, prayerNameLabel(name), "--:--")
            view.setBackgroundResource(R.drawable.bg_prayer_time_chip)
            view.alpha = 0.62f
        }
    }

    private fun prayerChipViews(): Map<PrayerName, TextView> {
        return mapOf(
            PrayerName.FAJR to binding.subuhTime,
            PrayerName.DHUHR to binding.dzuhurTime,
            PrayerName.ASR to binding.asharTime,
            PrayerName.MAGHRIB to binding.maghribTime,
            PrayerName.ISHA to binding.isyaTime,
        )
    }

    private fun prayerNameLabel(name: PrayerName): String {
        return getString(
            when (name) {
                PrayerName.FAJR -> R.string.prayer_fajr
                PrayerName.DHUHR -> R.string.prayer_dhuhr
                PrayerName.ASR -> R.string.prayer_asr
                PrayerName.MAGHRIB -> R.string.prayer_maghrib
                PrayerName.ISHA -> R.string.prayer_isha
            }
        )
    }

    private fun startPrayerTicker() {
        prayerTickerJob?.cancel()
        prayerTickerJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(60_000L)
                val schedule = currentSchedule ?: continue
                if (schedule.isFetchedToday()) {
                    renderSchedule(schedule)
                } else {
                    refreshPrayerTimes()
                }
            }
        }
    }

    private fun renderDhikrSummary() {
        binding.morningDhikr.text = getString(
            R.string.dzikir_morning_summary,
            DhikrContent.cardsFor(DhikrPeriod.MORNING).size
        )
        binding.eveningDhikr.text = getString(
            R.string.dzikir_evening_summary,
            DhikrContent.cardsFor(DhikrPeriod.EVENING).size
        )
    }

    private fun openSettings() {
        findNavController().navigate(R.id.settingsFragment)
    }

    private fun openDhikr(period: DhikrPeriod) {
        findNavController().navigate(
            R.id.dhikrPagerFragment,
            bundleOf(Constants.Key.DHIKR_PERIOD to period.name)
        )
    }

    private fun initSwipe() {
        binding.scrollView.onHorizontalSwipeLeft = {
            handleHorizontalSwipe(Constants.SwipeDirection.LEFT)
        }
        binding.scrollView.onHorizontalSwipeRight = {
            handleHorizontalSwipe(Constants.SwipeDirection.RIGHT)
        }
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
            closeCenter()
        }
    }

    private fun closeCenter() {
        runCatching {
            if (findNavController().popBackStack().not()) {
                findNavController().navigate(R.id.mainFragment)
            }
        }
    }

    override fun onDestroyView() {
        prayerTickerJob?.cancel()
        prayerTickerJob = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val KEY_SOURCE_DIRECTION = "muslim_center_source_direction"
    }
}
