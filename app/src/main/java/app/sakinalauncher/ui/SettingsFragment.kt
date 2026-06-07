package app.sakinalauncher.ui

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.bundleOf
import androidx.core.os.LocaleListCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import app.sakinalauncher.BuildConfig
import app.sakinalauncher.MainViewModel
import app.sakinalauncher.R
import app.sakinalauncher.data.Constants
import app.sakinalauncher.data.Prefs
import app.sakinalauncher.data.muslim.GlobalPrayerLocation
import app.sakinalauncher.data.muslim.PrayerApiClient
import app.sakinalauncher.data.muslim.PrayerCity
import app.sakinalauncher.data.muslim.PrayerProvider
import app.sakinalauncher.data.muslim.PrayerTimeRepository
import app.sakinalauncher.data.muslim.PrayerTimeStore
import app.sakinalauncher.databinding.FragmentSettingsBinding
import app.sakinalauncher.helper.animateAlpha
import app.sakinalauncher.helper.appUsagePermissionGranted
import app.sakinalauncher.helper.getColorFromAttr
import app.sakinalauncher.helper.isAccessServiceEnabled
import app.sakinalauncher.helper.isDarkThemeOn
import app.sakinalauncher.helper.isEinkDisplay
import app.sakinalauncher.helper.isOlauncherDefault
import app.sakinalauncher.helper.isTablet
import app.sakinalauncher.helper.openAppInfo
import app.sakinalauncher.helper.openUrl
import app.sakinalauncher.helper.PrayerLocationHelper
import app.sakinalauncher.helper.rateApp
import app.sakinalauncher.helper.setPlainWallpaper
import app.sakinalauncher.helper.shareApp
import app.sakinalauncher.helper.showToast
import app.sakinalauncher.listener.DeviceAdmin
import kotlinx.coroutines.launch
import java.util.Locale

class SettingsFragment : Fragment(), View.OnClickListener, View.OnLongClickListener {

    private lateinit var prefs: Prefs
    private lateinit var prayerStore: PrayerTimeStore
    private lateinit var prayerRepository: PrayerTimeRepository
    private lateinit var viewModel: MainViewModel
    private lateinit var deviceManager: DevicePolicyManager
    private lateinit var componentName: ComponentName

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val showPentastic = System.currentTimeMillis() % 2 == 0L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        prayerStore = PrayerTimeStore(requireContext())
        prayerRepository = PrayerTimeRepository(PrayerApiClient.api, PrayerApiClient.aladhanApi, prayerStore)
        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")
        viewModel.isOlauncherDefault()

        deviceManager = requireContext().getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        componentName = ComponentName(requireContext(), DeviceAdmin::class.java)
        checkAdminPermission()

        binding.homeAppsNum.text = prefs.homeAppsNum.toString()
        populateProMessage()
        populateKeyboardText()
        populateScreenTimeOnOff()
        populateLockSettings()
        populateHomeButtonRecents()
        populateWallpaperText()
        populateAppThemeText()
        populateLanguage()
        populateTextSize()
        populateAlignment()
        populateStatusBar()
        populateDateTime()
        populateSwipeApps()
        populatePrayerRegion()
        populateSwipeDownAction()
        populateActionHints()
        initClickListeners()
        initObservers()

        if (showPentastic)
            binding.footer.text = getText(R.string.new_app_minimal_todo_lists)
    }

    override fun onClick(view: View) {
        binding.appsNumSelectLayout.visibility = View.GONE
        binding.dateTimeSelectLayout.visibility = View.GONE
        binding.appThemeSelectLayout.visibility = View.GONE
        binding.swipeDownSelectLayout.visibility = View.GONE
        if (view.id != R.id.textSizeMinus && view.id != R.id.textSizePlus) {
            if (binding.textSizesLayout.isVisible) {
                binding.textSizesLayout.visibility = View.GONE
                applyTextSizeScale()
            }
        }
        if (view.id != R.id.alignmentBottom)
            binding.alignmentSelectLayout.visibility = View.GONE

        when (view.id) {
            R.id.olauncherHiddenApps -> showHiddenApps()
            R.id.moreFeatures -> viewModel.showDialog.postValue(Constants.Dialog.PRO_MESSAGE)
            R.id.screenTimeOnOff -> viewModel.showDialog.postValue(Constants.Dialog.DIGITAL_WELLBEING)
            R.id.appInfo -> openAppInfo(requireContext(), Process.myUserHandle(), BuildConfig.APPLICATION_ID)
            R.id.setLauncher -> viewModel.resetLauncherLiveData.call()
            R.id.toggleLock -> toggleLockMode()
            R.id.homeButtonRecents -> toggleHomeButtonRecents()
            R.id.autoShowKeyboard -> toggleKeyboardText()
            R.id.homeAppsNum -> binding.appsNumSelectLayout.visibility = View.VISIBLE
            R.id.dailyWallpaperUrl -> requireContext().openUrl(prefs.dailyWallpaperUrl)
            R.id.dailyWallpaper -> toggleDailyWallpaperUpdate()
            R.id.alignment -> binding.alignmentSelectLayout.visibility = View.VISIBLE
            R.id.alignmentLeft -> viewModel.updateHomeAlignment(Gravity.START)
            R.id.alignmentCenter -> viewModel.updateHomeAlignment(Gravity.CENTER)
            R.id.alignmentRight -> viewModel.updateHomeAlignment(Gravity.END)
            R.id.alignmentBottom -> updateHomeBottomAlignment()
            R.id.statusBar -> toggleStatusBar()
            R.id.dateTime -> binding.dateTimeSelectLayout.visibility = View.VISIBLE
            R.id.dateTimeOn -> toggleDateTime(Constants.DateTime.ON)
            R.id.dateTimeOff -> toggleDateTime(Constants.DateTime.OFF)
            R.id.dateOnly -> toggleDateTime(Constants.DateTime.DATE_ONLY)
            R.id.appThemeText -> binding.appThemeSelectLayout.visibility = View.VISIBLE
            R.id.themeLight -> updateTheme(AppCompatDelegate.MODE_NIGHT_NO)
            R.id.themeDark -> updateTheme(AppCompatDelegate.MODE_NIGHT_YES)
            R.id.themeSystem -> updateTheme(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            R.id.textSizeValue -> binding.textSizesLayout.visibility = View.VISIBLE
            R.id.actionAccessibility -> openAccessibilityService()
            R.id.closeAccessibility -> toggleAccessibilityVisibility(false)
            R.id.notWorking -> requireContext().openUrl(Constants.URL_DOUBLE_TAP)

            R.id.tvGestures -> binding.flSwipeDown.visibility = View.VISIBLE

            R.id.maxApps0 -> updateHomeAppsNum(0)
            R.id.maxApps1 -> updateHomeAppsNum(1)
            R.id.maxApps2 -> updateHomeAppsNum(2)
            R.id.maxApps3 -> updateHomeAppsNum(3)
            R.id.maxApps4 -> updateHomeAppsNum(4)
            R.id.maxApps5 -> updateHomeAppsNum(5)
            R.id.maxApps6 -> updateHomeAppsNum(6)
            R.id.maxApps7 -> updateHomeAppsNum(7)
            R.id.maxApps8 -> updateHomeAppsNum(8)

            R.id.textSizeMinus -> adjustTextSizePreview(-0.1f)
            R.id.textSizePlus -> adjustTextSizePreview(0.1f)

            R.id.swipeLeftApp -> showSwipeTargetPicker(isLeft = true)
            R.id.swipeRightApp -> showSwipeTargetPicker(isLeft = false)
            R.id.swipeDownAction -> binding.swipeDownSelectLayout.visibility = View.VISIBLE
            R.id.notifications -> updateSwipeDownAction(Constants.SwipeDownAction.NOTIFICATIONS)
            R.id.search -> updateSwipeDownAction(Constants.SwipeDownAction.SEARCH)
            R.id.prayerRegion -> showPrayerRegionPicker()
            R.id.prayerProvider -> showPrayerProviderPicker()
            R.id.prayerAutoDetect -> autoDetectPrayerRegion()
            R.id.languageText -> showLanguagePicker()

            R.id.aboutOlauncher -> {
                prefs.aboutClicked = true
                requireContext().openUrl(Constants.URL_ABOUT_OLAUNCHER)
            }

            R.id.share -> requireActivity().shareApp()
            R.id.rate -> {
                prefs.rateClicked = true
                requireActivity().rateApp()
            }

            R.id.twitter -> requireContext().openUrl(Constants.URL_TWITTER_TANUJ)
            R.id.github -> requireContext().openUrl(Constants.URL_OLAUNCHER_GITHUB)
            R.id.privacy -> requireContext().openUrl(Constants.URL_OLAUNCHER_PRIVACY)
            R.id.footer -> {
                requireContext().openUrl(
                    if (showPentastic) Constants.URL_PENTASTIC else Constants.URL_NTS
                )
            }
        }
    }

    override fun onLongClick(view: View): Boolean {
        when (view.id) {
            R.id.alignment -> {
                prefs.appLabelAlignment = prefs.homeAlignment
                findNavController().navigate(R.id.action_settingsFragment_to_appListFragment)
                requireContext().showToast(getString(R.string.alignment_changed))
            }

            R.id.dailyWallpaper -> removeWallpaper()
            R.id.appThemeText -> {
                binding.appThemeSelectLayout.visibility = View.VISIBLE
                binding.themeSystem.visibility = View.VISIBLE
            }

            R.id.swipeLeftApp -> showSwipeAppList(isLeft = true)
            R.id.swipeRightApp -> showSwipeAppList(isLeft = false)
            R.id.toggleLock -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        return true
    }

    private fun initClickListeners() {
        binding.olauncherHiddenApps.setOnClickListener(this)
        binding.scrollLayout.setOnClickListener(this)
        binding.appInfo.setOnClickListener(this)
        binding.setLauncher.setOnClickListener(this)
        binding.aboutOlauncher.setOnClickListener(this)
        binding.moreFeatures.setOnClickListener(this)
        binding.autoShowKeyboard.setOnClickListener(this)
        binding.toggleLock.setOnClickListener(this)
        binding.homeButtonRecents.setOnClickListener(this)
        binding.homeAppsNum.setOnClickListener(this)
        binding.screenTimeOnOff.setOnClickListener(this)
        binding.dailyWallpaperUrl.setOnClickListener(this)
        binding.dailyWallpaper.setOnClickListener(this)
        binding.alignment.setOnClickListener(this)
        binding.alignmentLeft.setOnClickListener(this)
        binding.alignmentCenter.setOnClickListener(this)
        binding.alignmentRight.setOnClickListener(this)
        binding.alignmentBottom.setOnClickListener(this)
        binding.statusBar.setOnClickListener(this)
        binding.dateTime.setOnClickListener(this)
        binding.dateTimeOn.setOnClickListener(this)
        binding.dateTimeOff.setOnClickListener(this)
        binding.dateOnly.setOnClickListener(this)
        binding.swipeLeftApp.setOnClickListener(this)
        binding.swipeRightApp.setOnClickListener(this)
        binding.prayerRegion.setOnClickListener(this)
        binding.prayerProvider.setOnClickListener(this)
        binding.prayerAutoDetect.setOnClickListener(this)
        binding.swipeDownAction.setOnClickListener(this)
        binding.search.setOnClickListener(this)
        binding.notifications.setOnClickListener(this)
        binding.appThemeText.setOnClickListener(this)
        binding.languageText.setOnClickListener(this)
        binding.themeLight.setOnClickListener(this)
        binding.themeDark.setOnClickListener(this)
        binding.themeSystem.setOnClickListener(this)
        binding.textSizeValue.setOnClickListener(this)
        binding.actionAccessibility.setOnClickListener(this)
        binding.closeAccessibility.setOnClickListener(this)
        binding.notWorking.setOnClickListener(this)

        binding.share.setOnClickListener(this)
        binding.rate.setOnClickListener(this)
        binding.twitter.setOnClickListener(this)
        binding.github.setOnClickListener(this)
        binding.privacy.setOnClickListener(this)
        binding.footer.setOnClickListener(this)

        binding.maxApps0.setOnClickListener(this)
        binding.maxApps1.setOnClickListener(this)
        binding.maxApps2.setOnClickListener(this)
        binding.maxApps3.setOnClickListener(this)
        binding.maxApps4.setOnClickListener(this)
        binding.maxApps5.setOnClickListener(this)
        binding.maxApps6.setOnClickListener(this)
        binding.maxApps7.setOnClickListener(this)
        binding.maxApps8.setOnClickListener(this)

        binding.textSizeMinus.setOnClickListener(this)
        binding.textSizePlus.setOnClickListener(this)

        binding.dailyWallpaper.setOnLongClickListener(this)
        binding.alignment.setOnLongClickListener(this)
        binding.appThemeText.setOnLongClickListener(this)
        binding.swipeLeftApp.setOnLongClickListener(this)
        binding.swipeRightApp.setOnLongClickListener(this)
        binding.toggleLock.setOnLongClickListener(this)
    }

    private fun initObservers() {
        if (prefs.firstSettingsOpen) {
            viewModel.showDialog.postValue(Constants.Dialog.ABOUT)
            prefs.firstSettingsOpen = false
        }
        viewModel.isOlauncherDefault.observe(viewLifecycleOwner) {
            if (it) {
                binding.setLauncher.text = getString(R.string.change_default_launcher)
                prefs.toShowHintCounter += 1
            }
        }
        viewModel.homeAppAlignment.observe(viewLifecycleOwner) {
            populateAlignment()
        }
        viewModel.updateSwipeApps.observe(viewLifecycleOwner) {
            populateSwipeApps()
        }
    }

    private fun toggleSwipeLeft() {
        prefs.swipeLeftTarget = if (prefs.swipeLeftTarget == Constants.SwipeTarget.OFF) {
            Constants.SwipeTarget.PRODUCTIVE
        } else {
            Constants.SwipeTarget.OFF
        }
        populateSwipeApps()
    }

    private fun toggleSwipeRight() {
        prefs.swipeRightTarget = if (prefs.swipeRightTarget == Constants.SwipeTarget.OFF) {
            Constants.SwipeTarget.MUSLIM_CENTER
        } else {
            Constants.SwipeTarget.OFF
        }
        populateSwipeApps()
    }

    private fun toggleStatusBar() {
        prefs.showStatusBar = !prefs.showStatusBar
        populateStatusBar()
    }

    private fun populateStatusBar() {
        if (prefs.showStatusBar) {
            showStatusBar()
            binding.statusBar.text = getString(R.string.on)
        } else {
            hideStatusBar()
            binding.statusBar.text = getString(R.string.off)
        }
    }

    private fun toggleDateTime(selected: Int) {
        prefs.dateTimeVisibility = selected
        populateDateTime()
        viewModel.toggleDateTime()
    }

    private fun populateDateTime() {
        binding.dateTime.text = getString(
            when (prefs.dateTimeVisibility) {
                Constants.DateTime.DATE_ONLY -> R.string.date
                Constants.DateTime.ON -> R.string.on
                else -> R.string.off
            }
        )
    }

    private fun showStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            requireActivity().window.insetsController?.show(WindowInsets.Type.statusBars())
        else
            @Suppress("DEPRECATION", "InlinedApi")
            requireActivity().window.decorView.apply {
                systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            }
    }

    private fun hideStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            requireActivity().window.insetsController?.hide(WindowInsets.Type.statusBars())
        else {
            @Suppress("DEPRECATION")
            requireActivity().window.decorView.apply {
                systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_FULLSCREEN
            }
        }
    }

    private fun showHiddenApps() {
        if (prefs.hiddenApps.isEmpty()) {
            requireContext().showToast(getString(R.string.no_hidden_apps))
            return
        }
        viewModel.getHiddenApps()
        findNavController().navigate(
            R.id.action_settingsFragment_to_appListFragment,
            bundleOf(Constants.Key.FLAG to Constants.FLAG_HIDDEN_APPS)
        )
    }

    private fun checkAdminPermission() {
        val isAdmin: Boolean = deviceManager.isAdminActive(componentName)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
            prefs.lockModeOn = isAdmin
    }

    private fun toggleAccessibilityVisibility(show: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            binding.notWorking.visibility = View.VISIBLE
        if (isAccessServiceEnabled(requireContext()))
            binding.actionAccessibility.text = getString(R.string.disable)
        binding.accessibilityLayout.isVisible = show
        binding.scrollView.animateAlpha(if (show) 0.5f else 1f)
    }

    private fun openAccessibilityService() {
        toggleAccessibilityVisibility(false)
        // prefs.lockModeOn = true
        populateLockSettings()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun toggleLockMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (!prefs.lockModeOn && !isAccessServiceEnabled(requireContext())) {
                toggleAccessibilityVisibility(true)
                return
            }
            prefs.lockModeOn = !prefs.lockModeOn
        } else {
            val isAdmin: Boolean = deviceManager.isAdminActive(componentName)
            if (isAdmin) {
                removeActiveAdmin("Admin permission removed.")
                prefs.lockModeOn = false
            } else {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                intent.putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    getString(R.string.admin_permission_message)
                )
                requireActivity().startActivityForResult(intent, Constants.REQUEST_CODE_ENABLE_ADMIN)
            }
        }
        populateLockSettings()
    }

    private fun removeActiveAdmin(toastMessage: String? = null) {
        try {
            deviceManager.removeActiveAdmin(componentName) // for backward compatibility
            requireContext().showToast(toastMessage)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeWallpaper() {
        if (requireContext().isEinkDisplay()) {
            prefs.appTheme = AppCompatDelegate.MODE_NIGHT_NO
            setPlainWallpaper(requireContext(), android.R.color.white)
        } else {
            prefs.appTheme = AppCompatDelegate.MODE_NIGHT_YES
            setPlainWallpaper(requireContext(), android.R.color.black)
        }
        if (!prefs.dailyWallpaper) return
        prefs.dailyWallpaper = false
        populateWallpaperText()
        viewModel.cancelWallpaperWorker()
    }

    private fun toggleDailyWallpaperUpdate() {
        if (prefs.dailyWallpaper.not() && prefs.appTheme == AppCompatDelegate.MODE_NIGHT_YES && viewModel.isOlauncherDefault.value == false) {
            requireContext().showToast(R.string.set_as_default_launcher_first)
            return
        }
        prefs.dailyWallpaper = !prefs.dailyWallpaper
        populateWallpaperText()
        if (prefs.dailyWallpaper) {
            viewModel.setWallpaperWorker()
            showWallpaperToasts()
        } else viewModel.cancelWallpaperWorker()
    }

    private fun showWallpaperToasts() {
        if (isOlauncherDefault(requireContext()))
            requireContext().showToast(getString(R.string.your_wallpaper_will_update_shortly))
        else
            requireContext().showToast(getString(R.string.olauncher_is_not_default_launcher), Toast.LENGTH_LONG)
    }

    private fun updateHomeAppsNum(num: Int) {
        binding.homeAppsNum.text = num.toString()
        binding.appsNumSelectLayout.visibility = View.GONE
        prefs.homeAppsNum = num
        viewModel.refreshHome(true)
    }

    private var pendingTextSizeScale: Float = -1f

    private fun adjustTextSizePreview(delta: Float) {
        val maxScale = if (isTablet(requireContext())) 2.0f else 1.5f
        val current = if (pendingTextSizeScale > 0) pendingTextSizeScale else prefs.textSizeScale
        val newScale = Math.round((current + delta) * 10f) / 10f
        val clamped = newScale.coerceIn(0.5f, maxScale)
        if (clamped == current) return
        pendingTextSizeScale = clamped
        val formatted = String.format("%.1f", clamped)
        binding.textSizeValue.text = formatted
        binding.textSizeCurrent.text = formatted
    }

    private fun applyTextSizeScale() {
        if (pendingTextSizeScale < 0 || prefs.textSizeScale == pendingTextSizeScale) {
            pendingTextSizeScale = -1f
            return
        }
        prefs.textSizeScale = pendingTextSizeScale
        pendingTextSizeScale = -1f
        requireActivity().recreate()
    }

    private fun toggleKeyboardText() {
        if (prefs.autoShowKeyboard && prefs.keyboardMessageShown.not()) {
            viewModel.showDialog.postValue(Constants.Dialog.KEYBOARD)
            prefs.keyboardMessageShown = true
        } else {
            prefs.autoShowKeyboard = !prefs.autoShowKeyboard
            populateKeyboardText()
        }
    }

    private fun updateTheme(appTheme: Int) {
        if (AppCompatDelegate.getDefaultNightMode() == appTheme) return
        prefs.appTheme = appTheme
        populateAppThemeText(appTheme)
        setAppTheme(appTheme)
    }

    private fun setAppTheme(theme: Int) {
        if (AppCompatDelegate.getDefaultNightMode() == theme) return
        if (prefs.dailyWallpaper) {
            setPlainWallpaper(theme)
            viewModel.setWallpaperWorker()
        }
        requireActivity().recreate()
    }

    private fun setPlainWallpaper(appTheme: Int) {
        when (appTheme) {
            AppCompatDelegate.MODE_NIGHT_YES -> setPlainWallpaper(requireContext(), android.R.color.black)
            AppCompatDelegate.MODE_NIGHT_NO -> setPlainWallpaper(requireContext(), android.R.color.white)
            else -> {
                if (requireContext().isDarkThemeOn())
                    setPlainWallpaper(requireContext(), android.R.color.black)
                else setPlainWallpaper(requireContext(), android.R.color.white)
            }
        }
    }

    private fun populateAppThemeText(appTheme: Int = prefs.appTheme) {
        when (appTheme) {
            AppCompatDelegate.MODE_NIGHT_YES -> binding.appThemeText.text = getString(R.string.dark)
            AppCompatDelegate.MODE_NIGHT_NO -> binding.appThemeText.text = getString(R.string.light)
            else -> binding.appThemeText.text = getString(R.string.system_default)
        }
    }

    private fun populateLanguage() {
        val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        binding.languageText.text = if (tag.startsWith("in") || tag.startsWith("id")) {
            getString(R.string.language_indonesian)
        } else {
            getString(R.string.language_english)
        }
    }

    private fun showLanguagePicker() {
        val tags = arrayOf("en", "in")
        val labels = arrayOf(getString(R.string.language_english), getString(R.string.language_indonesian))
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.language)
            .setItems(labels) { _, which ->
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags[which]))
                populateLanguage()
                requireActivity().recreate()
            }
            .show()
    }

    private fun populateTextSize() {
        val formatted = String.format("%.1f", prefs.textSizeScale)
        binding.textSizeValue.text = formatted
        binding.textSizeCurrent.text = formatted
    }

    private fun populateScreenTimeOnOff() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (requireContext().appUsagePermissionGranted()) binding.screenTimeOnOff.text = getString(R.string.on)
            else binding.screenTimeOnOff.text = getString(R.string.off)
        } else binding.screenTimeLayout.visibility = View.GONE
    }

    private fun populateKeyboardText() {
        if (prefs.autoShowKeyboard) binding.autoShowKeyboard.text = getString(R.string.on)
        else binding.autoShowKeyboard.text = getString(R.string.off)
    }

    private fun populateWallpaperText() {
        if (prefs.dailyWallpaper) binding.dailyWallpaper.text = getString(R.string.on)
        else binding.dailyWallpaper.text = getString(R.string.off)
    }

    private fun updateHomeBottomAlignment() {
        if (viewModel.isOlauncherDefault.value != true) {
            requireContext().showToast(getString(R.string.please_set_olauncher_as_default_first), Toast.LENGTH_LONG)
            return
        }
        prefs.homeBottomAlignment = !prefs.homeBottomAlignment
        populateAlignment()
        viewModel.updateHomeAlignment(prefs.homeAlignment)
    }

    private fun populateAlignment() {
        when (prefs.homeAlignment) {
            Gravity.START -> binding.alignment.text = getString(R.string.left)
            Gravity.CENTER -> binding.alignment.text = getString(R.string.center)
            Gravity.END -> binding.alignment.text = getString(R.string.right)
        }
        binding.alignmentBottom.text = if (prefs.homeBottomAlignment)
            getString(R.string.bottom_on)
        else getString(R.string.bottom_off)
    }

    private fun toggleHomeButtonRecents() {
        if (!prefs.homeButtonShowRecents && !isAccessServiceEnabled(requireContext())) {
            toggleAccessibilityVisibility(true)
            return
        }
        prefs.homeButtonShowRecents = !prefs.homeButtonShowRecents
        populateHomeButtonRecents()
    }

    private fun populateHomeButtonRecents() {
        binding.homeButtonRecents.text = getString(
            if (prefs.homeButtonShowRecents && isAccessServiceEnabled(requireContext())) R.string.on
            else R.string.off
        )
    }

    private fun populateLockSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            binding.toggleLock.text = getString(
                if (prefs.lockModeOn && isAccessServiceEnabled(requireContext())) R.string.on
                else R.string.off
            )
        } else {
            binding.toggleLock.text = getString(
                if (prefs.lockModeOn) R.string.on
                else R.string.off
            )
        }
    }

    private fun populateSwipeDownAction() {
        binding.swipeDownAction.text = when (prefs.swipeDownAction) {
            Constants.SwipeDownAction.NOTIFICATIONS -> getString(R.string.notifications)
            else -> getString(R.string.search)
        }
    }

    private fun updateSwipeDownAction(swipeDownFor: Int) {
        if (prefs.swipeDownAction == swipeDownFor) return
        prefs.swipeDownAction = swipeDownFor
        populateSwipeDownAction()
    }

    private fun populateSwipeApps() {
        binding.swipeLeftApp.text = swipeTargetSummary(isLeft = true)
        binding.swipeRightApp.text = swipeTargetSummary(isLeft = false)
        binding.swipeLeftApp.setTextColor(
            requireContext().getColorFromAttr(
                if (prefs.swipeLeftTarget == Constants.SwipeTarget.OFF) R.attr.primaryColorTrans50
                else R.attr.primaryColor
            )
        )
        binding.swipeRightApp.setTextColor(
            requireContext().getColorFromAttr(
                if (prefs.swipeRightTarget == Constants.SwipeTarget.OFF) R.attr.primaryColorTrans50
                else R.attr.primaryColor
            )
        )
    }

    private fun showSwipeTargetPicker(isLeft: Boolean) {
        val targetValues = intArrayOf(
            Constants.SwipeTarget.PRODUCTIVE,
            Constants.SwipeTarget.TODO,
            Constants.SwipeTarget.TIMER,
            Constants.SwipeTarget.MUSLIM_CENTER,
            Constants.SwipeTarget.APP,
            Constants.SwipeTarget.OFF,
        )
        val labels = targetValues.map { swipeTargetLabel(it) }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(if (isLeft) R.string.swipe_left_for else R.string.swipe_right_for)
            .setItems(labels) { _, which ->
                if (isLeft) {
                    prefs.swipeLeftTarget = targetValues[which]
                } else {
                    prefs.swipeRightTarget = targetValues[which]
                }
                populateSwipeApps()
                when (targetValues[which]) {
                    Constants.SwipeTarget.OFF -> { /* nothing */ }
                    else -> showSwipeAppList(isLeft)
                }
            }
            .show()
    }

    private fun swipeTargetSummary(isLeft: Boolean): String {
        val target = if (isLeft) prefs.swipeLeftTarget else prefs.swipeRightTarget
        val appName = if (isLeft) prefs.appNameSwipeLeft else prefs.appNameSwipeRight
        if (target == Constants.SwipeTarget.APP) {
            return appName.ifBlank { swipeTargetLabel(target) }
        }
        if (target == Constants.SwipeTarget.OFF) {
            return swipeTargetLabel(target)
        }
        return "${swipeTargetLabel(target)} / ${appName.ifBlank { swipeTargetLabel(Constants.SwipeTarget.APP) }}"
    }

    private fun swipeTargetLabel(target: Int): String {
        return getString(
            when (target) {
                Constants.SwipeTarget.APP -> R.string.app
                Constants.SwipeTarget.PRODUCTIVE -> R.string.productive
                Constants.SwipeTarget.NOTES -> R.string.notes
                Constants.SwipeTarget.TODO -> R.string.todo
                Constants.SwipeTarget.TIMER -> R.string.timer
                Constants.SwipeTarget.MUSLIM_CENTER -> R.string.muslim
                else -> R.string.off
            }
        )
    }

    private fun populatePrayerRegion() {
        binding.prayerProviderValue.text = getString(
            when (prayerStore.provider) {
                PrayerProvider.KEMENAG -> R.string.prayer_provider_kemenag
                PrayerProvider.GLOBAL -> R.string.prayer_provider_global
            }
        )
        binding.prayerRegionValue.text = when (prayerStore.provider) {
            PrayerProvider.KEMENAG -> {
                val fallbackCity = prayerStore.cityQuery.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                }
                prayerStore.cityLabel.ifBlank { fallbackCity }
            }
            PrayerProvider.GLOBAL -> listOf(prayerStore.globalLocationLabel, prayerStore.globalCountry)
                .filter { it.isNotBlank() }
                .joinToString(", ")
        }
        binding.prayerAutoDetectValue.text = getString(
            if (prayerStore.autoDetectLocation) R.string.on else R.string.off
        )
    }

    private fun showPrayerProviderPicker() {
        val providers = arrayOf(PrayerProvider.KEMENAG, PrayerProvider.GLOBAL)
        val labels = arrayOf(getString(R.string.prayer_provider_kemenag), getString(R.string.prayer_provider_global))
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.prayer_source_setting)
            .setItems(labels) { _, which ->
                prayerStore.provider = providers[which]
                prayerStore.autoDetectLocation = false
                populatePrayerRegion()
                refreshSelectedPrayerProvider()
            }
            .show()
    }

    private fun refreshSelectedPrayerProvider() {
        viewLifecycleOwner.lifecycleScope.launch {
            when (prayerStore.provider) {
                PrayerProvider.KEMENAG -> prayerRepository.refreshToday()
                PrayerProvider.GLOBAL -> prayerRepository.selectGlobalLocation(
                    GlobalPrayerLocation(
                        label = prayerStore.globalLocationLabel,
                        country = prayerStore.globalCountry,
                        latitude = prayerStore.globalLatitude,
                        longitude = prayerStore.globalLongitude,
                        timeZoneId = prayerStore.globalTimeZoneId,
                        method = prayerStore.globalMethod,
                    )
                )
            }
        }
    }

    private fun showPrayerRegionPicker() {
        when (prayerStore.provider) {
            PrayerProvider.KEMENAG -> showKemenagRegionPicker()
            PrayerProvider.GLOBAL -> showGlobalRegionPicker()
        }
    }

    private fun showKemenagRegionPicker() {
        val presetQueries = arrayOf("jakarta", "bandung", "surabaya", "yogyakarta", "medan", "makassar")
        viewLifecycleOwner.lifecycleScope.launch {
            requireContext().showToast(getString(R.string.loading_locations))
            val fallbackCities = presetQueries.mapNotNull { query ->
                prayerRepository.searchCities(query).firstOrNull()
            }.distinctBy { it.id }
            val cities = prayerRepository.allCities().ifEmpty { fallbackCities }
            if (cities.isEmpty()) {
                requireContext().showToast(getString(R.string.unable_to_load_prayer_times))
                return@launch
            }

            val dialogView = layoutInflater.inflate(R.layout.dialog_prayer_city_picker, null)
            val searchInput = dialogView.findViewById<android.widget.EditText>(R.id.searchInput)
            val cityList = dialogView.findViewById<android.widget.ListView>(R.id.cityList)
            val emptyView = dialogView.findViewById<android.widget.TextView>(R.id.emptyView)

            val adapter = android.widget.ArrayAdapter(
                requireContext(),
                R.layout.adapter_prayer_location,
                cities.map { it.label }.toMutableList()
            )
            cityList.adapter = adapter

            val dialog = AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create()

            searchInput.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val query = s?.toString()?.lowercase() ?: ""
                    val filtered = if (query.isEmpty()) {
                        cities
                    } else {
                        cities.filter {
                            it.label.lowercase().contains(query)
                        }
                    }

                    if (filtered.isEmpty()) {
                        cityList.visibility = View.GONE
                        emptyView.visibility = View.VISIBLE
                    } else {
                        cityList.visibility = View.VISIBLE
                        emptyView.visibility = View.GONE
                        adapter.clear()
                        adapter.addAll(filtered.map { it.label })
                        adapter.notifyDataSetChanged()
                    }
                }
            })

            cityList.setOnItemClickListener { _, _, position, _ ->
                val query = searchInput.text.toString().lowercase()
                val filtered = if (query.isEmpty()) {
                    cities
                } else {
                    cities.filter {
                        it.label.lowercase().contains(query)
                    }
                }
                if (position < filtered.size) {
                    selectPrayerCity(filtered[position], autoDetected = false)
                    dialog.dismiss()
                }
            }

            dialog.show()
            searchInput.requestFocus()
        }
    }

    private fun showGlobalRegionPicker() {
        val locations = PrayerTimeRepository.globalPresetLocations
        val dialogView = layoutInflater.inflate(R.layout.dialog_prayer_city_picker, null)
        val searchInput = dialogView.findViewById<android.widget.EditText>(R.id.searchInput)
        val cityList = dialogView.findViewById<android.widget.ListView>(R.id.cityList)
        val emptyView = dialogView.findViewById<android.widget.TextView>(R.id.emptyView)
        val adapter = android.widget.ArrayAdapter(
            requireContext(),
            R.layout.adapter_prayer_location,
            locations.map { "${it.label}, ${it.country}" }.toMutableList()
        )
        cityList.adapter = adapter
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        fun filteredLocations(): List<GlobalPrayerLocation> {
            val query = searchInput.text.toString().lowercase()
            return if (query.isBlank()) locations else locations.filter {
                "${it.label} ${it.country}".lowercase().contains(query)
            }
        }
        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val filtered = filteredLocations()
                cityList.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
                emptyView.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
                adapter.clear()
                adapter.addAll(filtered.map { "${it.label}, ${it.country}" })
                adapter.notifyDataSetChanged()
            }
        })
        cityList.setOnItemClickListener { _, _, position, _ ->
            val filtered = filteredLocations()
            if (position < filtered.size) {
                selectGlobalPrayerLocation(filtered[position], autoDetected = false)
                dialog.dismiss()
            }
        }
        dialog.show()
        searchInput.hint = getString(R.string.search_city_country)
        searchInput.requestFocus()
    }

    private fun autoDetectPrayerRegion() {
        if (!PrayerLocationHelper.hasLocationPermission(requireContext())) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_LOCATION_PERMISSION
            )
            return
        }
        if (prayerStore.provider == PrayerProvider.GLOBAL) {
            detectGlobalPrayerRegionFromLocation()
        } else {
            detectPrayerRegionFromLocation()
        }
    }

    private fun detectPrayerRegionFromLocation() {
        viewLifecycleOwner.lifecycleScope.launch {
            val cityQuery = PrayerLocationHelper.detectCityQuery(requireContext())
            if (cityQuery.isNullOrBlank()) {
                requireContext().showToast(getString(R.string.location_not_found))
                return@launch
            }
            val city = prayerRepository.searchCities(cityQuery).firstOrNull()
            if (city == null) {
                requireContext().showToast(getString(R.string.location_not_found))
                return@launch
            }
            selectPrayerCity(city, autoDetected = true)
        }
    }

    private fun detectGlobalPrayerRegionFromLocation() {
        viewLifecycleOwner.lifecycleScope.launch {
            val detected = PrayerLocationHelper.detectLocation(requireContext())
            if (detected == null) {
                requireContext().showToast(getString(R.string.location_not_found))
                return@launch
            }
            selectGlobalPrayerLocation(
                GlobalPrayerLocation(
                    label = detected.cityQuery,
                    country = detected.country,
                    latitude = detected.latitude,
                    longitude = detected.longitude,
                    timeZoneId = detected.timeZoneId,
                    method = 3,
                ),
                autoDetected = true,
            )
        }
    }

    private fun selectPrayerCity(city: PrayerCity, autoDetected: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            prayerStore.autoDetectLocation = autoDetected
            prayerRepository.selectCity(city)
            populatePrayerRegion()
            requireContext().showToast(city.label)
        }
    }

    private fun selectGlobalPrayerLocation(location: GlobalPrayerLocation, autoDetected: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            prayerStore.autoDetectLocation = autoDetected
            prayerRepository.selectGlobalLocation(location)
            populatePrayerRegion()
            requireContext().showToast("${location.label}, ${location.country}")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_LOCATION_PERMISSION) return
        if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
            if (prayerStore.provider == PrayerProvider.GLOBAL) {
                detectGlobalPrayerRegionFromLocation()
            } else {
                detectPrayerRegionFromLocation()
            }
        } else {
            prayerStore.autoDetectLocation = false
            populatePrayerRegion()
            requireContext().showToast(getString(R.string.location_permission_denied))
        }
    }


    private fun showSwipeAppList(isLeft: Boolean) {
        val flag = if (isLeft) Constants.FLAG_SET_SWIPE_LEFT_APP else Constants.FLAG_SET_SWIPE_RIGHT_APP
        viewModel.getAppList(true)
        findNavController().navigate(
            R.id.action_settingsFragment_to_appListFragment,
            bundleOf(Constants.Key.FLAG to flag)
        )
    }

    private fun populateActionHints() {
        if (prefs.aboutClicked.not())
            binding.aboutOlauncher.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_info, 0)
        if (viewModel.isOlauncherDefault.value != true) return
        if (prefs.rateClicked.not() && prefs.toShowHintCounter > Constants.HINT_RATE_US && prefs.toShowHintCounter < Constants.HINT_RATE_US + 100)
            binding.rate.setCompoundDrawablesWithIntrinsicBounds(0, android.R.drawable.arrow_down_float, 0, 0)
    }

    private fun populateProMessage() {
        if (prefs.proMessageShown.not() && prefs.userState == Constants.UserState.SHARE) {
            prefs.proMessageShown = true
            viewModel.showDialog.postValue(Constants.Dialog.PRO_MESSAGE)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        viewModel.checkForMessages.call()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_LOCATION_PERMISSION = 901
    }
}
