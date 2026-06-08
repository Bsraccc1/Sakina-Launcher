# Sakina Launcher — Architecture Review

Surfaces architectural friction and **deepening opportunities** — refactors that turn
shallow modules into deep ones, improving *locality* (change/bugs concentrate in one
place) and *leverage* (callers get more behaviour per unit of interface). Vocabulary
follows the team's `CONTEXT.md` domain terms and the deep-module language (module,
interface, implementation, seam, adapter, depth, leverage, locality).

Recommendation strength legend:
- **Strong** — clear win, low risk, do it next.
- **Worth exploring** — likely valuable, needs a design pass before committing.
- **Speculative** — plausible but unproven; revisit if the pain recurs.

---

## Re-verification pass (latest)

Re-ran the feedback loop (`gradlew.bat lint assembleDebug --no-daemon`):

- **`assembleDebug` → BUILD SUCCESSFUL.**
- **Lint → zero `Error`/`Fatal` issues.** Remaining items are all `Warning`-level and
  non-blocking: `UnusedResources` (82), `Untranslatable` (37), `GradleDependency` (18),
  `ClickableViewAccessibility` (13, tracked as Candidate 5), `UnusedTranslation` (12), etc.
- **Dead-code claim confirmed.** The only surviving `setPlainWallpaper`/
  `setPlainWallpaperByTheme` functions are live (called from `HomeFragment` theme change);
  the duplicated/unused copies were already removed in the prior pass.

No new *safe* quick-win refactor was applied in this pass: the outstanding candidates
below (split `Utils.kt`, favourite-slot module, Activity Result migration) all carry
cross-file blast radius and should land as their own dedicated, individually build-verified
commits rather than being bundled into the current UI/feature change set.

---

## Already done in this pass (safe, build-verified)

These were low-risk and are already applied:

1. **Dead code removed.** `MainActivity.setPlainWallpaper()` (no-arg, never called) and
   `SettingsFragment.setPlainWallpaper(appTheme)` (an exact duplicate of
   `Utils.setPlainWallpaperByTheme`, never called) deleted, plus their now-unused imports
   (`setPlainWallpaper`, `isDarkThemeOn`). Obsolete `SDK_INT >= N` branch in
   `Utils.setPlainWallpaper` removed (minSdk is 24 = N, so the branch was dead).
2. **Real lint errors fixed.** The three `MissingPermission` errors on
   `WallpaperManager.getDrawable/peekDrawable/getFastDrawable` were suppressed at the one
   call site (`MainActivity.loadUserWallpaper`) where every call is already wrapped in
   try/catch with a null fallback. Two `DefaultLocale` warnings fixed with
   `Locale.getDefault()`.
3. **Prefs boilerplate collapsed (the headline deepening).** See candidate 1 below — this
   was applied because it is mechanical and behaviour-preserving.

---

## Candidate 1 — `Prefs` as a deep settings store (APPLIED)

**Files:** `data/Prefs.kt`

**Problem.** `Prefs` was ~725 lines, almost entirely the same 3-line shape repeated ~100
times:

```kotlin
var firstOpen: Boolean
    get() = prefs.getBoolean(FIRST_OPEN, true)
    set(value) = prefs.edit { putBoolean(FIRST_OPEN, value).apply() }
```

The interface (one typed property per setting) is good and deep — callers just say
`prefs.firstOpen`. But the *implementation* was shallow and noisy: every new setting cost
three near-identical lines, and the repeated `.apply()` inside `edit {}` was redundant.
The boilerplate buried the few properties that actually carry logic (swipe targets,
pomodoro coercion, computed defaults).

**Solution (applied).** Introduced private typed delegate factories
(`boolPref/intPref/longPref/floatPref/stringPref/nullableStringPref`) so each simple
setting is one line:

```kotlin
var firstOpen: Boolean by boolPref(FIRST_OPEN, true)
```

Properties with real logic (`swipeLeftTarget`/`swipeRightTarget` side effects,
`pomodoro*` coercion and computed defaults, `hiddenApps` set) were left explicit, so the
file now visually separates "plain storage" from "behaviour." Public property names,
types, keys, and defaults are unchanged — no migration, no behaviour change.

**Benefits.** 725 → 482 lines. Adding a setting is now one line at one place
(*locality*). The interface callers depend on is identical (*leverage* preserved). The
non-trivial settings are no longer camouflaged.

**Before / After**

```
Before: ~100 settings × 3 boilerplate lines, logic-bearing ones hidden in the noise
        [getBoolean/putBoolean]  [getInt/putInt]  [getString/putString] ...repeated...

After:  6 small delegate factories  +  one line per simple setting
        logic-bearing settings stand out as the only explicit get()/set() blocks
```

**Recommendation strength:** Strong (done).

---

## Candidate 2 — Split `Utils.kt` into concern-named modules

**Files:** `helper/Utils.kt` (688 lines, ~40 top-level functions)

**Problem.** `Utils.kt` is a grab-bag grouped only by being "utilities." It mixes at
least six unrelated concerns:
- App listing & profiles: `getAppsList`, `getPrivateSpaceApps`, `isPackageInstalled`,
  `getUserHandleFromString`, private-space helpers.
- Wallpaper: `setPlainWallpaper`, `setPlainWallpaperByTheme`, `getWallpaperBitmap`,
  `setWallpaper`, `getTodaysWallpaper`, `getBackupWallpaper`, `getBitmapFromURL`.
- Default-launcher detection: `isOlauncherDefault`, `getDefaultLauncherPackage`.
- System intents: `openSearch`, `openDialerApp`, `openCameraApp`, `openAlarmApp`,
  `openCalendar`, `expandNotificationDrawer`.
- Theme/display: `getChangedAppTheme`, `isDarkThemeOn`, `isTablet`,
  `getScreenDimensions`.
- View/Context extensions: `animateAlpha`, `addPressScale`, `copyToClipboard`,
  `openUrl`, `shareApp`, `rateApp`, `getColorFromAttr`, `uninstall`.

Understanding any one concept means scrolling past five others. The file has no single
*interface*; it is many shallow modules sharing a filename. There is no seam to test, say,
wallpaper logic without dragging in app-listing dependencies.

**Solution.** Split by concern into focused files (same package, so imports update
mechanically and no call sites change semantics):
`WallpaperUtils.kt`, `AppListUtils.kt`, `SystemIntents.kt`, `LauncherDefaultUtils.kt`,
`DisplayUtils.kt`, `ViewExtensions.kt`. Keep extension-function names identical.

**Benefits.** Each file becomes a small deep module with a nameable interface
(*locality*: wallpaper bugs live in one file). Tests can target one concern. Navigation
("where does X live?") matches the domain vocabulary in `CONTEXT.md`.

**Why not done now.** It touches imports across many files. Mechanical but broad; deserves
its own commit with a green build at each step. Low semantic risk, moderate churn.

**Recommendation strength:** Worth exploring (lean Strong — the only reason it is not done
here is blast radius on imports).

---

## Candidate 3 — A single favourite-app *slot* module instead of 1..8 fan-out

**Files:** `data/Prefs.kt` (the `appName1..8`, `appPackage1..8`,
`appActivityClassName1..8`, `appUser1..8`, `isShortcut1..8`, `shortcutId1..8` families and
their `when(location)` dispatchers), callers in `MainViewModel`, `HomeFragment`,
`SettingsFragment`.

**Problem.** A favourite "slot" is really one record (name, package, activity, user,
shortcut flag, shortcut id) addressed by an index 1..8, but it is spread across ~48 flat
properties plus six `when(location) { 1 -> …; 8 -> … }` dispatchers. Adding a 9th slot, or
a new per-slot field, means editing the constant block, the property block, and every
dispatcher. The "slot" concept has no module — it leaks across `Prefs` and every caller.

**Solution.** Model the slot explicitly: a `FavouriteSlot` data class plus
`fun favourite(location: Int): FavouriteSlot` / `fun setFavourite(location, slot)` on
`Prefs`, backed by indexed keys internally. The deletion test passes: deleting this module
would scatter slot knowledge back across `Prefs` and the fragments.

**Benefits.** One place defines what a favourite *is* (*locality*); callers manipulate a
typed record instead of juggling four parallel strings (*leverage*). Slot count becomes a
loop bound, not copy-paste.

**Why not done now.** Behaviour-preserving but reaches into several fragments; needs care
that the `String?` activity-class-name nullability and empty-string defaults are kept
exactly. Should follow Candidate 1's style.

**Recommendation strength:** Worth exploring.

---

## Candidate 4 — Migrate deprecated result/permission APIs to the Activity Result API

**Files:** `MainActivity.kt` (`onActivityResult`, `REQUEST_CODE_*`),
`SettingsFragment.kt:448` (`startActivityForResult`), `SettingsFragment.kt:955`/`1028`
(`requestPermissions` / `onRequestPermissionsResult`).

**Problem.** These rely on the deprecated request-code result/permission flow (compiler
flags them deprecated). It works today but is fragile (manual request-code matching) and
will eventually be removed. `MainActivity` already uses the modern
`ActivityResultContracts` for the wallpaper permission, so the codebase is half-migrated.

**Solution.** Replace each remaining `startActivityForResult` / `requestPermissions` with
a registered `ActivityResultLauncher`. This also deletes the request-code constants and
the deprecated overrides, removing a small seam of manual plumbing.

**Benefits.** Removes deprecated surface, makes results local to where they are launched
(*locality*), and finishes a migration that is already in progress.

**Recommendation strength:** Worth exploring.

---

## Candidate 5 — Give swipe gestures a `performClick` seam (accessibility)

**Files:** `listener/OnSwipeTouchListener.kt`, `listener/ViewSwipeTouchListener.kt`,
plus the `setOnTouchListener` call sites in `HomeFragment`, `DhikrPagerFragment`,
`NotePanelFragment` (13 `ClickableViewAccessibility` warnings).

**Problem.** Custom touch listeners consume touch events without ever calling
`View.performClick()`, so TalkBack / accessibility services cannot trigger the same
actions. The gesture logic is the launcher's primary input path, so this is a real
accessibility gap, not just lint noise.

**Solution.** Have the touch listeners call `performClick()` on tap detection and have the
hosting views override `performClick()` to route to the same action. This creates one
seam where "what a tap does" is defined for both touch and accessibility.

**Benefits.** Accessibility parity; a single place defines tap behaviour (*locality*).

**Recommendation strength:** Worth exploring.

---

## Candidate 6 — Prune unused resources and stale translations

**Files:** `res/` (lint reports 74 `UnusedResources`, 12 `UnusedTranslation`,
37 `Untranslatable`, plus duplicate/oversized launcher icons).

**Problem.** Dead drawables/strings/layouts inflate the APK and add navigation noise.
`UnusedResources` can have false positives (resources referenced only by name from code or
by other resources), so a blind delete is risky for a launcher.

**Solution.** Remove resources confirmed unused (cross-check each against code and XML
references), and either translate or mark `translatable="false"` the new Sakina strings.
Note: `MissingTranslation` is currently disabled in `app/build.gradle` so the build is not
blocked by intentionally-untranslated new feature strings — revisit if real translations
land.

**Recommendation strength:** Speculative (verify each resource before deleting).

---

## Top recommendation

Tackle **Candidate 2 (split `Utils.kt`)** next. It is the highest *locality* win for daily
development, it is almost entirely mechanical (same package, rename-by-move), and it makes
the codebase map onto the `CONTEXT.md` domain vocabulary so the next person — or the next
AI pass — can find wallpaper, app-listing, and intent code by name instead of scrolling a
688-line grab-bag. Do it as one commit per extracted file with a green build between each.
