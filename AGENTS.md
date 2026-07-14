# AGENTS.md

Guidance for coding agents working in this repository.

## Project Overview

**Sakinah Launcher** is a minimal Android home-screen replacement (launcher) with Muslim-focused features (prayer times, dhikr) and a Productive panel (notes, todo, timer, widgets). It is based on the open-source [Olauncher](https://github.com/tanujnotes/Olauncher) foundation, but this repo is **not** upstream Olauncher — app identity, package, and features are Sakinah’s.

| | |
|--|--|
| **App name** | Sakinah Launcher |
| **Package** | `app.sakinalauncher` |
| **Min SDK** | 24 (Android 7.0) |
| **Target SDK** | 35 (Android 15) |
| **Language** | Kotlin |
| **Build** | Gradle, Java 17+ (JDK 21 OK) |

## Build Commands

From the **repo root** (`sakina-launcher` / `Sakina-Launcher`), not an `Olauncher` subfolder:

```bash
# Windows
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
.\gradlew.bat installDebug
.\gradlew.bat clean
.\gradlew.bat check

# Unix
./gradlew assembleDebug
```

Requires `JAVA_HOME` and Android SDK (`local.properties` → `sdk.dir`, or `ANDROID_HOME`).

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`  
Debug `applicationId` suffix: `.debug` (side-by-side with release).

## Project Structure

Single Activity (`MainActivity`) + Navigation Component fragments:

- `HomeFragment` — home screen, favourites, swipe gestures  
- `AppDrawerFragment` — full app list + search  
- `SettingsFragment` — configuration  
- `NotePanelFragment` — **Productive** (Notes / Todo / Timer / Widgets)  
- `MuslimCenterFragment` / `DhikrPagerFragment` — Islamic features  

**Data:** `data/Prefs.kt`, `data/Constants.kt`, `data/NotePanel*`, `data/ProductiveWidgetStore.kt`, `data/muslim/`  
**ViewModel:** `MainViewModel.kt`  
**Helpers:** `helper/` (launcher utils, AppDialog, widget host, accessibility, etc.)

## Key features (short)

- HOME intent launcher; `FakeHomeActivity` for default-launcher chooser  
- Swipe targets: app, Productive, Muslim Center, OFF  
- Productive: panel size + dialog width in Settings; module toggles; AppWidget host tab  
- Hidden apps, usage stats, daily wallpaper, private space (API 35+)  
- ViewBinding; ProGuard on release  

## Naming note

Older docs or tests may still mention “Olauncher” or package `app.olauncher` in unit-test folders. **Production code is `app.sakinalauncher`.** Prefer Sakinah naming in new code and docs.
