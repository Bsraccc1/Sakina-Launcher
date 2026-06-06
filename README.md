# Sakina Launcher

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="Sakina Launcher Icon" width="120" height="120">
</p>

A minimal Android launcher with integrated Islamic features for mindful productivity.

![Sakina Launcher](Assets/screenshots/home.png)

---

## Table of Contents

- [Introduction](#introduction)
- [Features](#features)
  - [Muslim Center](#muslim-center)
  - [Dhikr & Remembrance](#dhikr--remembrance)
  - [Productivity Tools](#productivity-tools)
  - [Launcher Features](#launcher-features)
- [Screenshots](#screenshots)
- [Installation](#installation)
- [Build Instructions](#build-instructions)
- [Project Structure](#project-structure)
- [Tech Stack](#tech-stack)
- [Credits](#credits)
- [License](#license)

---

## Introduction

Sakina Launcher is a minimal, distraction-free Android launcher designed for Muslims who want to maintain their spiritual practice while managing digital life mindfully. Built on the foundation of Olauncher, it integrates prayer times, dhikr reminders, and productivity tools into a clean, gesture-based interface.

**Key Philosophy:**
- Minimal distractions, maximum focus
- Islamic features integrated naturally
- Privacy-first: no data collection, no ads
- Open source and always free

---

## Features

### Muslim Center

**Prayer Times**
- 5 daily prayer times (Fajr, Dhuhr, Asr, Maghrib, Isha)
- Next prayer indicator
- Location-based scheduling
- Multiple sources: Kemenag/MyQuran (Indonesia) and Aladhan Global
- Auto-detect location or manual city selection
- Cached schedules for offline access

### Dhikr & Remembrance

**Morning & Evening Dhikr**
- Authentic dhikr collections from almanhaj.or.id
- **Swipeable cards** - swipe left/right to navigate between dhikr
- Arabic text with transliteration and translation
- Built-in tasbih counter for each dhikr
- Progress tracking per reading
- Auto-advance after completing repetitions

### Productivity Tools

**Timer**
- Simple countdown timer
- Custom duration (minutes and seconds)
- Start, pause, resume, and reset controls

**Notes**
- Quick note-taking
- Pin important notes
- Move notes to todo list
- Clean, distraction-free interface

**Todo List**
- Simple task management
- Check off completed items
- Edit and delete tasks

### Launcher Features

**Minimal Home Screen**
- Up to 8 favorite apps
- Swipe gestures (left, right, up, down) to open apps
- Date and time display
- Battery percentage
- Screen time tracking
- Customizable text alignment and size

**App Drawer**
- Full app list with search
- Type to auto-launch apps
- Hide unwanted apps
- Rename apps
- Uninstall directly

**Customization**
- Theme modes (Light, Dark, System)
- Text color (Black/White)
- Text size adjustment
- App alignment (Left, Center, Right)
- Status bar visibility

**Gestures**
- Swipe up: App drawer
- Swipe left/right/down: Configurable app shortcuts
- Double tap: Lock screen (requires accessibility permission)
- Home button: Recent apps (requires accessibility permission)
- Long press: Settings

**Privacy & Wellbeing**
- No ads, no tracking, no data collection
- Screen time display (optional)
- Daily wallpaper updates
- Hidden apps support
- Private space support (Android 15+)

---

## Screenshots

### Home Screen
![Home Screen](Assets/screenshots/home.png)

### Muslim Center
![Muslim Center](Assets/screenshots/muslim_center_latest.png)

### Dhikr Cards (Swipeable)
![Dhikr Cards](Assets/screenshots/muslim_center_after_swipe.png)

### Productive Panel
![Productive Panel](Assets/screenshots/productive_panel.png)

### Timer
![Timer](Assets/screenshots/timer_panel_latest.png)

---

## Installation

### Install from APK

1. Download the latest APK from [Releases](https://github.com/YOUR_USERNAME/Sakina-Launcher/releases)
2. Enable "Install from Unknown Sources" in your device settings
3. Open the APK and install
4. Set Sakina Launcher as your default launcher

### Build from Source

See [Build Instructions](#build-instructions) below.

---

## Build Instructions

**Requirements:**
- Android Studio (latest version recommended)
- JDK 17
- Android SDK 24-35

**Steps:**

1. Clone the repository:
```bash
git clone https://github.com/YOUR_USERNAME/Sakina-Launcher.git
cd Sakina-Launcher
```

2. Open the project in Android Studio

3. Build debug APK:
```bash
./gradlew assembleDebug
```

4. Build release APK:
```bash
./gradlew assembleRelease
```

5. Install debug to device:
```bash
./gradlew installDebug
```

6. Clean build:
```bash
./gradlew clean
```

**On Windows, use `gradlew.bat` instead of `./gradlew`**

---

## Project Structure

### Architecture Pattern
Single Activity (MainActivity) + Fragments via Navigation Component:
- `HomeFragment` - Main launcher screen with favorite apps and gestures
- `AppDrawerFragment` - Full app list with search
- `SettingsFragment` - Configuration
- `MuslimCenterFragment` - Prayer times and dhikr
- `DhikrPagerFragment` - Swipeable dhikr cards
- `NotePanelFragment` - Notes and todos with timer

### Key Components

**Data Layer** (`data/`)
- `Prefs.kt` - SharedPreferences wrapper
- `Constants.kt` - App-wide constants
- `AppModel.kt` - App metadata model
- `muslim/` - Prayer times and dhikr data models

**ViewModel**
- `MainViewModel.kt` - Shared state management, app list, usage stats

**Helpers** (`helper/`)
- `Utils.kt` - Launcher operations
- `Extensions.kt` - Kotlin extensions
- `AppUsageStats.kt` - Screen time tracking
- `WallpaperWorker.kt` - Daily wallpaper updates
- `PrayerLocationHelper.kt` - Location and prayer time management

**UI** (`ui/`)
- Fragment implementations
- Adapters for RecyclerViews
- Custom views (CircularTimerView)

---

## Tech Stack

**Language:** Kotlin

**Architecture:** MVVM with Single Activity + Navigation Component

**Key Libraries:**
- AndroidX Core, AppCompat, RecyclerView
- Lifecycle (ViewModel, LiveData)
- Navigation Component
- WorkManager (background tasks)
- Retrofit + OkHttp (API calls for prayer times)
- Gson (JSON parsing)
- Material Design Components

**Build:**
- Gradle with Java 17
- Min SDK: 24 (Android 7.0)
- Target SDK: 35 (Android 15)
- ViewBinding enabled

---

## Credits

### Original Project

**Based on [Olauncher](https://github.com/tanujnotes/Olauncher)** by [@tanujnotes](https://github.com/tanujnotes)

Sakina Launcher builds upon the excellent foundation of Olauncher, adding Islamic features and productivity tools while maintaining its minimal, distraction-free philosophy. All core launcher functionality, gesture controls, and privacy-first design principles are inherited from Olauncher.

### Islamic Content Sources

- **Dhikr content**: [almanhaj.or.id](https://almanhaj.or.id) - Morning and Evening remembrance
- **Prayer times API**: 
  - Kemenag RI / MyQuran API (Indonesia)
  - Aladhan Global API (worldwide)

### App Icon

Icon designed specifically for Sakina Launcher - featuring Arabic calligraphy "سكينة" (tranquility) on teal background.
---

## License

GNU General Public License v3.0

See [LICENSE](LICENSE) for details.

---

**Made with ❤️ for Muslims seeking digital minimalism and mindful productivity**
