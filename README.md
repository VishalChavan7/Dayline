<p align="center">
  <img src="docs/hero.png" alt="Dayline: tasks, calendar, habits, focus and notes for Android" width="100%">
</p>

<h1 align="center">Dayline</h1>

<p align="center">
  <b>Your whole day, in one calm place.</b><br>
  Tasks, calendar, habits, focus sessions and notes in one native Android app, built with Material 3 Expressive.
</p>

<p align="center">
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-Material%203%20Expressive-4285F4?logo=jetpackcompose&logoColor=white">
  <img alt="Offline first" src="https://img.shields.io/badge/offline-first-1757D0">
  <img alt="No ads, no tracking" src="https://img.shields.io/badge/ads%20%26%20tracking-none-555">
  <a href="https://github.com/VishalChavan7/Dayline/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/VishalChavan7/Dayline?label=download&color=1757D0"></a>
  <a href="LICENSE"><img alt="MIT License" src="https://img.shields.io/badge/license-MIT-green"></a>
  <a href="CONTRIBUTING.md"><img alt="Contributions welcome" src="https://img.shields.io/badge/contributions-welcome-brightgreen"></a>
</p>

---

## Demo

https://github.com/user-attachments/assets/e3f5f510-7ffb-4237-b1e9-05389b6c7303

## Download

Get the latest APK from **[Releases](https://github.com/VishalChavan7/Dayline/releases/latest)**, open it on your phone and allow installing from your browser or file manager when Android asks. Dayline runs on Android 8.0 and newer. From version 1.1.0 on, Dayline updates itself from here.

## Dayline for Mac

A native Mac version, built with SwiftUI for Apple silicon (macOS 14 Sonoma or newer). It has the same Today view, tasks, calendar (month, week and year, with drag-to-reschedule), habits, focus timer, notes and statistics. It adds a menu bar item for today's tasks, quick add and the focus timer, and it syncs with your phone through the same private file in your Google Drive.

<p align="center">
  <img src="docs/mac-today.png" alt="Dayline for Mac: Today" width="49%">
  <img src="docs/mac-calendar-dark.png" alt="Dayline for Mac: Calendar in dark mode" width="49%">
</p>

**Install:** download `Dayline-mac-<version>.zip` from the [Mac release](https://github.com/VishalChavan7/Dayline/releases?q=mac&expanded=true), unzip it and drag **Dayline** into Applications. The app isn't notarized by Apple yet, so the first time macOS says it can't verify it: open **System Settings → Privacy & Security** and click **Open Anyway**. After that, Dayline for Mac updates itself. To build it yourself, see [macos/README.md](macos/README.md).

## Why Dayline

Most people juggle a to-do app, a calendar, a habit tracker, a timer and a notes app. Dayline puts all five on one screen-friendly home, so planning the day, doing the work and building routines happen in the same place.

- **Students:** track assignments with due dates, plan the exam week on the calendar, and study in focus sessions.
- **Professionals:** capture tasks in a tap, set priorities and dependencies, and keep meeting notes with checklists next to them.
- **Habit builders:** check in daily, count reps or glasses of water, and watch streaks grow on the home screen.
- **Anyone who wants calm:** one clear Today view instead of five apps, and no account, ads or tracking.

## Features

### ☀️ Today
- A daily dashboard with a progress ring for tasks **and** habits, focus time and overdue count.
- Overdue, Today and Completed sections, grouped by priority, with subtasks shown inline.
- Swipe right to complete and swipe left to delete, with undo.

### ✅ Tasks
- **Quick capture:** tap **+** to add a task, or press and hold for a note, habit or focus session.
- Natural typing: `tomorrow`, `!high` and `#tag` are understood as you write.
- Due and start dates, times, repeats (daily, weekly, monthly, custom), multiple reminders and attachments.
- **Dependencies:** a task waiting on another can't be completed or deleted until its blocker is done.
- Inbox, Upcoming (Tomorrow / This week / Next week), All tasks and Completed views.

### 📅 Calendar
- **Month, week and year** views with coloured task dots.
- Long-press a task in week view and drag it to another day to reschedule it.

### 🔥 Habits
- Daily, x-times-a-week or specific-day habits, plus counters (for example 5/8 glasses).
- Streaks, a week-at-a-glance row of check-ins, and a satisfying haptic on every check-in.

### ⏱️ Focus
- A Pomodoro-style focus timer with an expressive ring that changes shape as time passes.
- Gentle vibration in the last seconds, and it keeps running safely in the background.

### 📊 Statistics
- Day, week and month views: completed vs. remaining, completion rate, missed deadlines, focus time and habit consistency, with trends against the previous period.

### 📝 Notes
- Rich text: bold, italic, underline, strikethrough, text colour, highlight, alignment and lists.
- Inline checklists, `#labels` with custom icons, colour-coded notes, pinning and search.
- Add images, drawings and voice notes. Press and hold **New Note** to start with one.

### 🧩 Home-screen widgets
Seven widgets: **Today's tasks, Daily progress, Next up, Habit streaks, Streak flower, Focus timer** and **Quick add**. They resize to fit any home-screen grid (4×5, 5×5, 5×6 and more), and each can have its own artistic background in Settings.

### 🎨 Design
- Material 3 Expressive throughout: Google Sans Flex, large rounded surfaces, springy motion and haptics.
- Themes: dynamic colour (Material You), monochrome, five gradients and a dynamic gradient, in light or dark.
- Tested on screens from 320 dp small phones and 130% font size to foldables and landscape.

### 🔄 Automatic updates
- Dayline checks this repository's releases in the background, downloads new versions and lets you know with a notification.
- Open the app and a popup offers a one-tap update. You can also check any time in **More → Check for updates**.

### 🔒 Sync and privacy
- Works fully offline. Everything is stored on your phone.
- Optional **Google sync** keeps a private copy in your own Google Drive's hidden app folder and keeps your devices in step. Switch accounts or disconnect any time. Nothing ever goes to a Dayline server, because there isn't one.
- Export and import a full JSON backup at any time.
- [Privacy policy](https://vishalchavan7.github.io/privacy.html)

## Screenshots

**Plan your day**
<p align="center"><img src="docs/gallery-plan.png" alt="Today, quick capture, task details and upcoming tasks" width="100%"></p>

**Calendar and habits**
<p align="center"><img src="docs/gallery-calendar.png" alt="Calendar month, week and year views, and habits" width="100%"></p>

**Insights and notes**
<p align="center"><img src="docs/gallery-more.png" alt="Statistics, notes, onboarding and the More page" width="100%"></p>

**Dark mode**
<p align="center"><img src="docs/gallery-dark.png" alt="Today, calendar, habits and notes in dark mode" width="100%"></p>

## Tech stack

| | |
|---|---|
| Language & UI | Kotlin 2.3, Jetpack Compose, Material 3 Expressive |
| Architecture | MVVM with clean layers: pure-Kotlin domain rules, Room repositories as the single source of truth, `Flow`-driven screens |
| Data | Room, DataStore, JSON backup |
| Background work | WorkManager, AlarmManager (exact reminders), a background-safe focus timer |
| Widgets | Jetpack Glance |
| Sync | Google Identity authorization and the Drive REST API (`drive.appdata` scope only) |
| DI & tests | Hilt; JUnit, Robolectric, Turbine and Roborazzi screenshot tests across 9 phone set-ups |

```
app/src/main/java/com/dayline/app/
├── core/          models, domain rules (recurrence, streaks, stats, quick-add parser), Room, repositories, backup
├── notifications/ reminders, channels, alarm scheduling
├── focus/         focus timer controller
├── sync/          Google Drive sync
├── widget/        home-screen widgets
└── ui/            Compose screens, ViewModels, theme and components
```

## Getting started

**Requirements:** Android Studio 2025.2+ (or JDK 17 and Android SDK 36). Gradle downloads itself through the wrapper.

```bash
./gradlew :app:assembleDebug        # build the APK
./gradlew :app:installDebug         # install on a connected phone
./gradlew :app:testDebugUnitTest    # run all unit, flow and screenshot tests
```

In Android Studio: **File → Open**, choose this folder, wait for Gradle sync, then press **Run ▶**.

### Google sync (developer setup)
Sync needs a one-time Google Cloud setup: enable the **Google Drive API**, configure the OAuth consent screen with the `drive.appdata` scope, and add an **Android OAuth client** for `com.dayline.app` with your signing key's SHA-1. Every build writes that SHA-1 to `outputs/google-sync-setup.txt`. Users then connect with one tap in **Settings → Google sync**.

## Contributing

Contributions are welcome, whether that's a bug report, an idea, a translation, a design tweak or code.

1. Look through [open issues](https://github.com/VishalChavan7/Dayline/issues) or open a new one to discuss your idea.
2. Fork the repo, create a branch, make your change, and run `./gradlew :app:testDebugUnitTest`.
3. Open a pull request with a short description and, for UI changes, before and after screenshots.

See **[CONTRIBUTING.md](CONTRIBUTING.md)** for the full guide, code style and project conventions.

## License

Dayline is released under the **[MIT License](LICENSE)**. The bundled Google Sans Flex font is licensed separately under the [SIL Open Font License](FONT_LICENSE_OFL.txt).

---

<p align="center">Made by <a href="https://github.com/VishalChavan7">Vishal Chavan</a> · <a href="https://vishalchavan7.github.io">vishalchavan7.github.io</a></p>
