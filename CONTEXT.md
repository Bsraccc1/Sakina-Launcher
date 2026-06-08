# Sakina Launcher — Domain Context

A minimal, distraction-reducing Android home-screen replacement (a *launcher*) with
added Muslim-focused features (prayer times, dzikir, qibla-oriented content). Built on
the Olauncher base. Single Activity + Navigation Component, Kotlin, ViewBinding.

Use these terms consistently across code, commits, and reviews.

## Core launcher domain

- **Launcher** — The app acting as the device home screen. Sakina becomes the launcher
  by declaring the `HOME` intent category in `AndroidManifest`. `isOlauncherDefault()` /
  `isDefaultLauncher()` report whether Sakina is the current default.
- **Home** — The main launcher screen (`HomeFragment`). Shows the clock/date, a short
  list of favourite apps, and reacts to swipe gestures. Backed by `MainViewModel`.
- **App Drawer** — The full, searchable list of installed apps (`AppDrawerFragment` +
  `AppDrawerAdapter`). Hidden apps and (on Android 15+) private-space apps are filtered
  here.
- **Favourite Apps / Home Apps** — The 1–8 apps pinned to Home. Persisted in `Prefs` as
  indexed slots (`appName1..8`, `appPackage1..8`, `appActivityClassName1..8`,
  `appUser1..8`). `homeAppsNum` controls how many are shown.
- **App slot / location** — An integer 1..8 addressing a favourite-app slot. `Prefs`
  exposes `getAppName(location)`, `getAppPackage(location)`, etc.
- **AppModel** — Metadata for one launchable app (label, package, activity class name,
  user handle). See `data/AppModel.kt`.
- **Swipe gesture / Swipe target** — Home supports swipe left/right/down actions.
  A *swipe target* (`Constants.SwipeTarget`) is what a swipe opens: an app, the
  productive view, the Muslim Center, or OFF. `SwipeLaunchHelper` resolves and launches
  the target. Detection lives in `OnSwipeTouchListener` / `ViewSwipeTouchListener`.
- **Shortcut** — A pinned app shortcut (Android `ShortcutInfo`) usable as a favourite or
  swipe target. Slots mirror favourites (`isShortcut1..8`, `shortcutId1..8`).
- **Hidden apps** — Packages the user has hidden from the drawer. Stored as a string set
  in `Prefs.hiddenApps`; filtered out by `MainViewModel` when building the app list.
- **Private Space** — Android 15+ isolated work/personal profile. Apps fetched via
  `getPrivateSpaceApps()`; availability toggled by `ACTION_PROFILE_AVAILABLE/UNAVAILABLE`
  broadcasts handled in `MainActivity`.

## Settings, theming, and presentation

- **Prefs** — The single `SharedPreferences` wrapper (`data/Prefs.kt`) holding *all*
  persisted settings as named, typed properties (delegate-backed). The public property
  names are load-bearing: many fragments read/write them directly.
- **Constants** — App-wide enums/constants (`data/Constants.kt`): user states, swipe
  targets, date/time visibility, font families, swipe-down actions.
- **AppTheme** — Light/dark/system mode (`AppCompatDelegate` night modes), persisted as
  `Prefs.appTheme`.
- **WallpaperLayer** — A full-screen `ImageView` (`binding.wallpaperLayer`) used to paint
  the wallpaper ourselves when Sakina is *not* the default launcher. When it is default,
  the system composites the wallpaper behind the translucent window and the layer stays
  hidden. Managed by `MainActivity.applySolidBackground()` and `loadUserWallpaper()`.
- **Daily Wallpaper** — Optional auto-changing wallpaper fetched on a schedule by
  `WallpaperWorker` (WorkManager). Toggled by `Prefs.dailyWallpaper`.
- **FontFamily** — Selectable typeface (`Constants.FontFamily`, applied via
  `FontHelper`).

## Muslim Center domain (`data/muslim/`, `ui/MuslimCenterFragment`)

- **Muslim Center** — Hub screen for Islamic features, reachable as a swipe target.
- **PrayerTime** — The five daily prayer times (Fajr, Dhuhr, Asr, Maghrib, Isha).
  Models in `PrayerModels.kt`; fetched/cached by `PrayerTimeRepository` over `PrayerApi`
  and stored in `PrayerTimeStore`. Source can be live or cached, region-based
  (Kemenag for Indonesia, or global).
- **PrayerLocation** — The city/region used to compute prayer times. Resolved by
  `PrayerLocationHelper` (auto-detect or manual city search).
- **Dzikir / Dhikr** — Remembrance phrases recited morning (*dzikir pagi*) and evening
  (*dzikir petang*). Content in `DhikrModels.kt`; presented and counted in
  `DhikrPagerFragment` with a tap counter and repetition tracking.
- **Note Panel** — A lightweight notes feature (`NotePanelFragment`, `NotePanelAdapter`,
  `NotePanelModel`, `NotePanelStore`).
- **Pomodoro** — A focus timer; its state (focus minutes/seconds, remaining/total millis,
  end timestamp) is persisted in `Prefs` under the `pomodoro*` properties.

## Services & system integration (`helper/`)

- **MyAccessibilityService** — Accessibility service enabling automatic app open/close
  and the swipe-down lock action.
- **DeviceAdmin** — Device-admin receiver allowing screen-lock on gesture.
- **AppUsageStats / usageStats** — Foreground-time / screen-time tracking
  (`PACKAGE_USAGE_STATS`). `EventLogWrapper` wraps raw usage events with timing.
- **FakeHomeActivity** — Temporary HOME activity used only to force the "choose default
  launcher" chooser dialog, then disabled.
- **PinItemActivity** — Handles requests to pin app shortcuts/widgets.

## Architecture shape

- **MainActivity** — Single entry point; hosts the nav graph, owns the wallpaper layer,
  the profile receiver, and theme/restart bookkeeping.
- **MainViewModel** — Shared `ViewModel` across fragments; owns app-list loading, hidden/
  private-space filtering, usage stats, and wallpaper-worker scheduling. Communicates via
  `LiveData` and `SingleLiveEvent` (one-shot events).
