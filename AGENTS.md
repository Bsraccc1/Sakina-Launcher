# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

Olauncher is a minimal Android launcher app. "AF" = Ad-Free. Single-activity app with fragment navigation.

**Package**: `app.sakinalauncher`
**Min SDK**: 24 (Android 7.0)
**Target SDK**: 35 (Android 15)
**Language**: Kotlin
**Build**: Gradle with Java 17

## Build Commands

```bash
# Build debug APK
cd Olauncher
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install debug to device
./gradlew installDebug

# Clean build
./gradlew clean

# Check for errors without building
./gradlew check
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

## Project Structure

### Architecture Pattern
Single Activity (MainActivity) + 3 Fragments via Navigation Component:
- `HomeFragment` - Main launcher screen with favorite apps and swipe gestures
- `AppDrawerFragment` - Full app list with search
- `SettingsFragment` - App configuration

### Key Components

**Data Layer** (`data/`)
- `Prefs.kt` - SharedPreferences wrapper for all app settings
- `AppModel.kt` - App metadata model
- `Constants.kt` - App-wide constants

**ViewModel**
- `MainViewModel.kt` - Shared ViewModel managing app state, app list, usage stats, and wallpaper sync

**Helpers** (`helper/`)
- `Utils.kt` - Utility functions for launcher operations
- `Extensions.kt` - Kotlin extension functions
- `AppUsageStats.kt` - Usage stats and screen time tracking
- `WallpaperWorker.kt` - WorkManager for automatic wallpaper changes
- `MyAccessibilityService.kt` - Accessibility service for automatic app opening/closing
- `FakeHomeActivity.kt` - Temporary launcher for "Choose default launcher" flow

**Usage Stats** (`helper/usageStats/`)
- Custom implementation for tracking app foreground time
- `EventLogWrapper.kt` - Wraps usage events with timing

**UI Adapters**
- `AppDrawerAdapter.kt` - RecyclerView adapter for app drawer

**Listeners**
- `DeviceAdmin.kt` - Device admin receiver for screen lock gestures
- `OnSwipeTouchListener.kt`, `ViewSwipeTouchListener.kt` - Gesture detection

## Key Features Implementation

### Launcher Setup
MainActivity declares `HOME` category in AndroidManifest to act as launcher. FakeHomeActivity used temporarily to force launcher chooser dialog.

### Swipe Gestures
Home screen supports swipes in all directions to open configured apps. Implemented via `ViewSwipeTouchListener`.

### App Hiding
Hidden apps stored in Prefs, filtered out in MainViewModel when loading app list.

### Usage Stats
Requires `PACKAGE_USAGE_STATS` permission. Custom tracking in `helper/usageStats/` for accurate foreground time.

### Auto Wallpaper
WorkManager periodic task downloads and sets wallpapers. Configured via `WallpaperWorker`.

### Private Space Support
Android 15+ feature. Requires `ACCESS_HIDDEN_PROFILES` permission. Apps loaded via `getPrivateSpaceApps()`.

## View Binding
Enabled in build.gradle. All layouts use ViewBinding (no findViewById).

## Proguard
Release builds use minification with proguard-rules.pro.

## Debugging
Debug build uses `.debug` suffix for applicationId, allowing side-by-side install with release version.
