# Design: Productive Widgets, RTL/Arabic, Panel Sizing

**Date:** 2026-07-12  
**Status:** Ready for user review (self-reviewed)  
**Package:** `app.sakinalauncher`  
**Min SDK / Target:** 24 / 35  

## 1. Problem

Sakinah Launcher’s Productive panel (Notes / Todo / Timer) needs to be more polished, correctly laid out for Arabic (RTL), configurable in size, and capable of hosting real Android AppWidgets. Users who set Arabic as the default language see misaligned chrome and untranslated Productive strings. The launcher currently rejects widget pin requests.

## 2. Goals (this phase)

1. **Productive shell polish** — seamless, precise, calm visual hierarchy for the Productive overlay.
2. **Arabic / RTL correctness** — layouts, strings, mirrored icons, and segment control work for RTL locales.
3. **Productive Widgets tab** — full `AppWidgetHost` inside Productive as a fourth module (vertical stack).
4. **Size settings** — panel height (Compact / Comfortable / Full) and dialog width scale via Prefs + Settings UI.
5. **Module toggles** — show/hide Notes, Todo, Timer, Widgets; keep at least one enabled.
6. **Efficiency** — cache note/todo JSON reads; inflate widgets only when the Widgets tab is visible; no new network; timer stays `elapsedRealtime`-based.
7. **API 24–35** — version-safe AppWidget bind/pick paths; no crashes on oldest or newest supported Android.

## 3. Non-goals (explicitly deferred)

- Free-form home grid mixing apps and widgets.
- Full home-screen widget host on the main Home fragment.
- Broader Home + Settings visual redesign beyond Productive-adjacent chrome.
- Cloud sync, accounts, or analytics.
- Publishing standalone AppWidgetProviders for other launchers (out of scope).

## 4. Architecture

### 4.1 Productive shell

`NotePanelFragment` remains the single Productive overlay (scrim + content). It hosts **four modules**:

| Module | Mode enum | Content |
|--------|-----------|---------|
| Notes | `NOTES` | Existing notes list + composer |
| Todo | `TODO` | Existing todos + composer |
| Timer | `TIMER` | Existing pomodoro UI |
| Widgets | `WIDGETS` | New AppWidget host stack |

Segment control gains a fourth tab when the Widgets module is enabled in Prefs. Disabled modules hide their tabs; if the active mode is disabled, switch to the first enabled module.

Panel height is controlled by a top spacer weight derived from `Prefs.productivePanelSize` (`Constants.ProductivePanelSize`: COMPACT / COMFORTABLE / FULL). Content weight fractions:

- Compact → ~0.72 screen height for content  
- Comfortable → ~0.86  
- Full → ~0.95  

Dialogs opened from Productive (edit note, duration, etc.) use `AppDialog.create(..., widthScale = prefs.productiveDialogWidthScale)` (0.70–1.0, default 0.92).

### 4.2 Widgets host (vertical stack)

New components (names indicative; implement under existing packages):

| Unit | Responsibility |
|------|----------------|
| `ProductiveWidgetStore` | Persist list of bound widgets: `appWidgetId`, provider component, optional height cells |
| `ProductiveWidgetHost` (or thin wrapper around `AppWidgetHost`) | Host lifecycle: start/stop listening with fragment lifecycle |
| Widgets UI inside `NotePanelFragment` (or small helper class) | RecyclerView / LinearLayout stack of `AppWidgetHostView`; Add / long-press remove |

**User flow**

1. Open Productive (existing swipe targets).  
2. Select **Widgets** tab.  
3. Empty state: localized hint + **Add widget**.  
4. Add → system widget picker / bind flow (API-aware).  
5. On success → allocate id, bind, append to store, attach host view.  
6. Long-press → remove (delete id, detach view, update store).  
7. Resize when provider allows: optional height adjustment stored per id (recommended path from design approval).

**Persistence**

- Local SharedPreferences (or the existing note-panel prefs file) JSON array:  
  `[{ "id": int, "provider": "pkg/class", "minHeightDp": int? }, ...]`  
- On restore: rebind only if provider still installed; drop orphaned ids.

**Lifecycle / battery**

- Call `AppWidgetHost.startListening()` only while the Widgets tab is visible (or fragment resumed *and* mode == WIDGETS).  
- `stopListening()` when leaving the tab or destroying the view.  
- Do not keep host views inflated when Notes/Todo/Timer is active (detach and reattach on return is acceptable for battery).

### 4.3 Settings

New **Productive** section in `SettingsFragment` (portrait; mirror in land layout):

- Panel size → list dialog: Compact / Comfortable / Full  
- Dialog width → fixed list only: 70% / 80% / 90% / 100% (maps to 0.70 / 0.80 / 0.90 / 1.0)  
- Module toggles: Notes, Todo, Timer, Widgets (on/off); enforce ≥1 enabled with toast `productive_keep_one_widget`

No cloud; all keys in `Prefs`.

### 4.4 Arabic / RTL

- Mark Productive strings translatable; complete `values-ar/strings.xml` (and keep English defaults in `values/strings.xml`).  
- Prefer `start`/`end` over `left`/`right` (already largely true; fix remaining code paths).  
- `android:autoMirrored="true"` on directional icons (send, chevrons).  
- Segment indicator: compute translation using layout direction (`ViewCompat.getLayoutDirection`) so the pill tracks the active tab in RTL.  
- Arabic Quran/dhikr text keeps `TextArabic` / `textDirection=rtl` where already set; do not force LTR on Arabic content.  
- Note bubbles and action bars: gravity/padding start-end so selection chrome mirrors correctly.

### 4.5 Performance and data

- **NotePanelStore:** in-memory cache of decoded notes/todos; invalidate on write. Avoid re-parsing JSON on every `render()`.  
- **No new network** for this feature set.  
- **Timer:** keep `SystemClock.elapsedRealtime` end timestamps (already present).  
- **Widgets:** no polling; rely on AppWidget framework updates.  
- **User data:** stay on-device SharedPreferences only; no third-party telemetry.

### 4.6 Android version matrix

| Concern | Approach |
|---------|----------|
| AppWidgetHost | Available API 24+ |
| Bind / pick | Use `AppWidgetManager` + activity contract / legacy intent as needed per API |
| PinItemActivity | Accept `REQUEST_TYPE_APPWIDGET` by routing into Productive widget store when possible; otherwise clear localized toast |
| Soft input on dialogs | Keep `SOFT_INPUT_ADJUST_RESIZE` with suppression where deprecated; Productive already pads for IME insets |
| Screen width for dialog scale | `WindowMetrics` on API 30+; `DisplayMetrics` fallback below |

## 5. Data model (Prefs keys)

| Key | Type | Default | Meaning |
|-----|------|---------|---------|
| `PRODUCTIVE_PANEL_SIZE` | int | COMFORTABLE (1) | Compact / Comfortable / Full |
| `PRODUCTIVE_DIALOG_WIDTH_SCALE` | float | 0.92 | Dialog width fraction |
| `PRODUCTIVE_WIDGET_NOTES` | bool | true | Notes module enabled |
| `PRODUCTIVE_WIDGET_TODO` | bool | true | Todo module enabled |
| `PRODUCTIVE_WIDGET_TIMER` | bool | true | Timer module enabled |
| `PRODUCTIVE_WIDGET_WIDGETS` | bool | true | Widgets module enabled (new; partial WIP only has notes/todo/timer) |
| `PRODUCTIVE_LAST_MODE` | string | `NOTES` | Last Productive tab name for reopen |
| Widget store file `app.sakinalauncher.productive_widgets` | JSON | `[]` | Bound app widget descriptors |

`NotePanelMode` gains `WIDGETS`.

`Constants.SwipeTarget.PRODUCTIVE` continues to open Productive. Opening mode rules:

1. Prefer the last Productive mode stored in Prefs (`PRODUCTIVE_LAST_MODE`), if that module is still enabled.  
2. Else first enabled module in order: Notes → Todo → Timer → Widgets.

## 6. UI / aesthetic principles (Productive only)

- Preserve minimal Sakina language: soft scrim, unified cards, Poppins for UI, Quran font only for Arabic scripture.  
- Segment control: equal-width tabs that reflow when modules are hidden; sliding indicator RTL-aware.  
- Widgets stack: generous vertical spacing, rounded container matching `bg_unified_card` / note bubble family.  
- Empty and error states use localized strings, never hardcoded English.  
- Reduce motion on e-ink if `isEinkDisplay()` already used elsewhere.

## 7. Error handling

| Case | Behavior |
|------|----------|
| User disables last module | Toast; leave previous state |
| Widget bind denied / cancelled | No-op; optional short toast |
| Provider uninstalled | Drop entry on next restore |
| Host start fails | Show empty state + retry Add |
| Corrupt widget JSON | Reset to empty list; log only in debug |

## 8. Testing

- Unit: Prefs coerce ranges; widget store encode/decode; NotePanelStore cache hit after write.  
- Manual: API 24 emulator + modern device; RTL `ar` locale — segment, send icon, dialogs, Settings Productive section.  
- Manual: add/remove widget, leave tab, return, process death restore.  
- Manual: panel size and dialog width changes reflected immediately on next open.

## 9. Implementation sequence (for writing-plans)

1. Finish Prefs / Constants / strings (including `productiveWidgetWidgets` toggle) + Arabic.  
2. Productive panel size + dialog width application in shell and `AppDialog`.  
3. Module toggles + 4th tab plumbing (no host yet).  
4. Widget store + host + UI stack + picker/bind.  
5. Settings Productive section (portrait + land).  
6. RTL segment indicator + layout polish pass.  
7. NotePanelStore cache + host listen lifecycle.  
8. PinItemActivity widget path + regression check.  
9. Build `assembleDebug` + focused tests.

## 10. Partial work already in tree

Uncommitted changes already touch:

- `Constants.ProductivePanelSize`, Prefs panel/dialog/module keys (notes/todo/timer)  
- `AppDialog` width scale  
- Arabic productive strings  
- `autoMirrored` on send/chevrons  
- dimens for productive  

These should be **aligned** with this spec (add Widgets module toggle, wire fragment/settings, implement host) rather than discarded, unless review finds them incorrect.

## 11. Success criteria

- Arabic locale: Productive labels correct; tabs, composer, action bar, dialogs aligned for RTL.  
- User can enable Widgets, add at least one system widget, leave Productive, return, and still see it.  
- Panel size and dialog width change from Settings without restart (or on next open if recreate is simpler).  
- Debug build succeeds; no new network permissions; notes/todos/timer behavior preserved.
