# Productive Widgets + RTL Implementation Plan

> **For agentic workers:** Inline execution in session. Design: `docs/superpowers/specs/2026-07-12-productive-widgets-rtl-design.md`

**Goal:** Productive 4th Widgets tab with AppWidgetHost, panel/dialog size settings, module toggles, Arabic/RTL polish, note store cache.

**Architecture:** NotePanelFragment shell hosts Notes/Todo/Timer/Widgets; ProductiveWidgetStore + AppWidgetHost; Prefs for size/toggles; Settings Productive section.

**Tech Stack:** Kotlin, ViewBinding, AppWidgetHost API 24+, SharedPreferences

---

See design §9 for task order. Implementing inline per user request.
