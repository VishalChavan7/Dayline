# Dayline — all-in-one productivity for Android

Dayline is a native Android app that puts tasks, daily planning, calendar, habits, focus sessions, notes, reminders and analytics in one place. Its productivity-first layout draws on Memorigi, and it is built with **Material 3 Expressive**: the Expressive theme and motion scheme, Google Sans Flex type, large rounded surfaces, wavy progress indicators and the expressive loading indicator.

It works fully offline, needs no account, and keeps all data on the device.

---

## Build & run

**Requirements:** Android Studio (2025.2 or newer, with AGP 9 support) or just a JDK 17+ and the Android SDK (platform 36). Gradle is downloaded automatically by the wrapper.

```bash
# from the project root
./gradlew :app:assembleDebug            # → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug             # install on a connected phone (USB debugging on)
./gradlew :app:testDebugUnitTest        # unit, repository, database, ViewModel, timer & reminder tests
./gradlew :app:recordRoborazziDebug     # renders screens on 7 phone configurations → app/build/outputs/roborazzi/
```

In Android Studio, use **File → Open**, pick the `Dayline` folder, wait for Gradle sync, then press **Run ▶**.

The debug build installs as **Dayline** with the id `com.dayline.app.debug`, so it can sit alongside a future release build.

**CI:** `.github/workflows/android.yml` builds the APK, runs every test, records the screenshots, and uploads all three as artifacts on each push.

### Toolchain

| Piece | Version |
|---|---|
| Android Gradle Plugin | 9.3.2 (built-in Kotlin) |
| Kotlin / KSP | 2.3.0 / 2.3.4 |
| Gradle | 9.7.1 |
| Compose BOM (alpha channel, for Material 3 Expressive) | 2025.09.01 |
| Room / Hilt / WorkManager / DataStore | 2.8.3 / 2.59 / 2.10.0 / 1.2.0 |
| Navigation Compose | 2.8.5 |
| Tests | JUnit 4, Robolectric 4.16, Roborazzi 1.56, Turbine, coroutines-test |

minSdk 26 (Android 8.0) · targetSdk 36 · compileSdk 36

---

## Architecture

```
app/src/main/java/com/dayline/app/
├── core/
│   ├── model/        Plain Kotlin domain models (Task, Habit, Project, Reminder, settings…)
│   ├── domain/       Pure business rules — no Android: recurrence, habit streaks, statistics,
│   │                 focus-timer state machine, reminder planning, grouping, quick-add parser
│   ├── data/
│   │   ├── db/       Room entities, DAOs, converters, database, entity↔model mappers
│   │   ├── repository/  Repositories (single source of truth, Flow-based)
│   │   ├── prefs/    DataStore-backed settings + persisted focus-timer state
│   │   └── backup/   JSON export / validated import
│   └── common/       Clock abstraction, app clock (midnight rollover), scheduler bridge
├── notifications/    Channels, AlarmManager scheduling, receivers, WorkManager safety net
├── focus/            FocusTimerController (background-safe timer)
├── di/               Hilt modules
└── ui/               Compose screens + ViewModels (MVVM), theme/design tokens, components
```

- **MVVM + clean layering:** screens are split into a ViewModel-backed `Screen` and a stateless `Content` composable. The stateless part is what the screenshot tests render.
- **Offline-first:** Room is the single source of truth. Every screen observes `Flow`s, so a change anywhere shows up everywhere immediately.
- **Ready for sync later:** repositories are the only writers and every row has timestamps, so a sync layer can sit behind the repositories without changing the UI.

### Database (Room, schema v1, exported to `app/schemas`)

`ProjectEntity`, `ListEntity`, `TaskEntity`, `SubtaskEntity`, `TagEntity`, `TaskTagCrossRef`, `TaskDependencyEntity`, `AttachmentEntity`, `ReminderEntity`, `HabitEntity`, `HabitCompletionEntity`, `NoteEntity`, `FocusSessionEntity`.

Foreign keys keep relationships consistent:
- Deleting a task cascades to its subtasks, tags links, dependencies, attachments and reminders.
- Deleting a project or list clears the link, so its tasks move to the Inbox (or are deleted, if you choose that).
- Notes and focus sessions survive the deletion of a linked task.

Dates are stored as sortable primitives (epoch day, second-of-day, epoch millis) so range queries run in SQL. Destructive migration is deliberately **off**, so user data is never wiped silently. `DaylineDatabase.MIGRATIONS` is where future schema changes go.

---

## Feature summary

**Navigation:** a bottom bar with Today · Calendar · Inbox · Habits · More. On screens ≥ 600 dp (unfolded foldables) it becomes a navigation rail and content is width-capped. The global **Add task** button is on Today, Calendar, Inbox and More.

**Today dashboard** has 10 sections that you can collapse, reorder (drag or arrows) and hide:
- Daily progress ring
- Overdue
- Today's tasks, optionally grouped by priority, with inline incomplete subtasks
- Upcoming deadlines (7 days)
- Today's habits, with one-tap check-in and counters
- Focus progress and quick start
- Week calendar strip
- Weekly completion chart vs last week
- Pinned notes and standalone reminders
- Daily quote, offline and public-domain

The greeting changes with the time of day, and the date rolls over at midnight.

**Tasks** support title, description, start/due date and time, four priorities, category, tags, project and list, subtasks (add, edit, reorder, check), recurrence, reminders, file attachments and web links, personal notes, linked notes, dependencies, and progress (from subtasks, or a manual slider). Created, modified and completed timestamps are shown in the editor.
- **Operations:** create, edit, delete with Undo, duplicate, complete/reopen, move, change priority, reschedule, reorder (Inbox drag mode), search, filter and sort.
- **Gestures:** swipe right to complete, swipe left to delete, long-press for contextual actions. All of them are also exposed as accessibility actions.
- **Quick Add** understands `today`, `tomorrow`, weekday names, `next week`, `!high` / `!1`–`!4`, `#tag` and `@List`.
- **Recurrence:** daily / weekly / monthly / yearly, custom intervals, specific weekdays, "repeat after completion" and an end date. Completing a recurring task archives that instance and creates the next one, with subtasks reset and tags, links and reminders carried over. Missed occurrences are skipped instead of piling up.
- **Dependencies** show a *Blocked* state until the blocking tasks are done. Cycles are prevented.

**Calendar:**
- **Views:** Month (primary), Week, Day, Agenda (primary) and Year.
- Priority dots, recurring-task projections, all-day vs. timed tasks, a timeline with focus-session blocks and a "now" line.
- Swipe left or right to change period; one tap jumps to today.
- Add a task to any date. In Week view, long-press a task and drag it to another day to reschedule.

**Inbox / Upcoming / All / Completed:**
- **Inbox** holds tasks with no project, list or date.
- **Upcoming** groups tasks into Tomorrow / This week / Next week / Later, following your week-start setting.
- **All** filters by status, priority, date, project, category and tags, and sorts by manual, due date, priority, recently created or title.
- **Completed** groups tasks by completion date, is searchable, and lets you reopen them.

**Projects & lists:**
- Projects have name, description, colour, icon, start date and deadline.
- Each project has a dashboard: progress ring and wavy bar, completed/pending/focus tiles, a deadline countdown, tasks grouped by list, and linked notes.
- Projects can be archived or restored. Deleting a project lets you either move its tasks to the Inbox or delete them too.

**Habits:**
- **Schedules:** daily, specific weekdays, or N times per week.
- **Targets:** a daily target, e.g. 8 glasses of water, with +/- counters.
- **Extras:** icon, colour, start date and a reminder time.
- **Streaks:** current and longest streak (in days, or weeks for weekly targets). Today never breaks a streak before the day is over.
- **Progress views:** 30- and 90-day consistency, a weekly strip, and a monthly calendar where you can fix a missed past day.
- **History:** recent check-ins, plus archiving.

**Focus timer:**
- **Modes:** Pomodoro focus, short and long breaks, and custom durations.
- **Controls:** start, pause, resume, stop & save, reset, skip, plus auto-start options.
- **Linking:** attach a session to a task or project.
- **Tracking:** daily goal, weekly chart, average and total, and session history.
- The timer runs on wall-clock timestamps backed by an exact alarm. It keeps working in the background, survives the app being killed, shows an ongoing countdown notification with Pause/Stop, and alerts you when a phase ends.

**Notes:** create, edit, delete (with Undo), pin to the dashboard, search, filter by category, and link to a task and/or project. Shown in a staggered grid.

**Reminders & notifications:**
- **Reminder types:** task (start), deadline (before due, with a default time for all-day tasks), custom standalone reminders, habit reminders, and focus alerts.
- **Channels:** five notification channels, plus a runtime permission prompt on Android 13+.
- **Scheduling:**
  - Uses `setExactAndAllowWhileIdle` when the exact-alarm permission is granted, and falls back to inexact alarms that still fire during Doze.
  - Alarms are rebuilt after reboot, app update, time/time-zone change and permission changes.
  - A 12-hour WorkManager job acts as a safety net.
- **Notification actions:** *Complete*, *Snooze 10 min*, *Mark habit done*.

**Statistics:** day / week / month filters with previous/next navigation. Includes:
- Completed, remaining, completion rate, overdue/missed deadlines, focus time and habit consistency, each compared with the previous period
- Bar chart of completions and line chart of focus
- Overdue trend, priority donut and an 18-week calendar heatmap
- Project-wise progress with focus minutes, and habit streaks

Everything is calculated from stored data. With no data, the screen shows an empty state instead of placeholder numbers.

**Search:** searches task titles, descriptions, notes and categories, plus projects, lists, tags and notes. It filters by scope, can include completed tasks, and updates live.

**Settings:**
- **Appearance:** light / dark / system theme, wallpaper dynamic colour (Android 12+), and 8 accent colours. Each accent generates a full light and dark scheme.
- **Task defaults:** default priority, default list, week start (Mon/Sun/Sat), 5 date formats, 12/24h/system time.
- **Notifications:** notification toggles, a precise-reminders permission shortcut, default reminder offset, and the time used for all-day tasks.
- **Focus timer and dashboard:** all focus-timer values, and dashboard customisation.
- **Tags and data:** tag rename/delete, JSON **export/restore**. A restore validates the whole file first, keeps a safety copy, and rebuilds alarms.
- **About:** app info.

**Quality:**
- Every screen has empty and loading states, confirmation dialogs for destructive actions, and snackbar feedback with Undo.
- **Accessibility:** content descriptions, state descriptions, live region on the timer, custom accessibility actions, and 48 dp touch targets.
- Layouts are responsive, and the design tokens are centralised (`ui/theme`).

---

## Tests

| Suite | What it covers |
|---|---|
| `core/domain/*Test` (55 tests) | Recurrence (incl. month-end, intervals, weekdays, after-completion, end date, projections), habit streaks & consistency, statistics, focus-timer state machine, reminder planning, upcoming grouping, quick-add parsing |
| `data/TaskRepositoryTest` | Save with children, validation, edit semantics, recurring completion, reopen, delete + undo, duplicate, dependencies & cycle prevention, reschedule span, reorder, quick-add defaults, project deletion modes |
| `data/DatabaseAndBackupTest` | Converters, cascade/SET NULL integrity, full backup round-trip, rejection of invalid backups without data loss |
| `notifications/ReminderSchedulerTest` | Exact trigger times, past/completed cancellation, settings toggles, habit reminder rollover, snooze |
| `focus/FocusTimerControllerTest` | Alarm-backed completion while backgrounded, idempotent completion, survival across process death, partial sessions |
| `ui/ViewModelTests` | Today dashboard assembly, Inbox/All filtering, live ViewModel updates (Turbine) |
| `screenshots/PhoneSizeScreenshotTest` | 10 screens × 7 configurations: **Galaxy S25 (360×780 dp)**, S25 dark, S25 at 130% font, S25 "large display size" (320 dp), Pixel 9, S25 Ultra, unfolded foldable |

**Status:** all 154 tests pass (`./gradlew :app:testDebugUnitTest`), and the debug APK builds cleanly on macOS with Android SDK 36.

`build-helper.sh` is a small optional script used during development: it runs only the fixed Gradle tasks above when a request file appears in `.agent/`.

---

## Known limitations

- **Cloud sync:** none yet, by design (offline-first). The repository layer is where a sync engine would plug in.
- **Attachments:** the app keeps a persistent permission to the file you picked rather than copying it. If the original file is deleted, the attachment can't be opened.
- **Task reordering:** manual drag-reordering is in the Inbox. Other lists use sorting.
- **Calendar dragging:** drag-and-drop rescheduling is in Week view. In other views, use the long-press menu (Today / Tomorrow / Next week / Pick date).
- **Monthly repeats:** a task due on the 31st moves to the last day of shorter months and then stays on that day of the month.
- **Quotes:** the daily quote list is small (21 classic quotes) and bundled offline.
- **Font:** Google Sans Flex is bundled as five static instances (Latin/Latin-Extended subset) to keep the APK small. Its licence is in `FONT_LICENSE_OFL.txt`.

## Round 2 additions

- **Smoother:** an optimised release build (`app-release.apk`, R8 + library baseline profiles), with tidier list rendering.
- **Motion:** Material 3 Expressive springs throughout — cards squish and bounce, the check pops with a burst, completed titles get a hand-drawn strike-through, and lists animate items in and out.
- **Navigation:** custom bottom bar and segmented controls with a gliding indicator; tabs cross-fade and detail screens slide in iOS-style.
- **Consistent headers:** every screen uses the same large-title header (`DaylineTopBar`) that collapses into a compact title on scroll.
- **Add button:** tap = Quick Add; press and hold = bounce plus a menu (task, note, habit, focus, project, list). It hides while scrolling down and returns on the way up. Quick Add closes after saving, with a vibration and a snackbar.
- **Lists vs projects:** separate Lists and Projects screens, a list detail screen, and both on the dashboard (new dashboard sections, reorderable as before).
- **First run:** a five-page tutorial, and a playful sunrise launch animation that continues from the system splash.
- **Widgets (Glance):** Today's tasks 4×3 (tick off from the home screen), Habit streaks 2×2, Focus timer 4×2 (live countdown, start/pause/skip), Daily progress 2×2 and Quick add 1×1.

## Google sync (one-time setup)

Dayline keeps a private copy of all your data (tasks, projects, lists, habits, notes, focus history and settings)
in your own Google Drive's hidden app folder and syncs it across devices — after every change and every hour.
Google only lets an app use Drive once its signing key is registered, so this is a one-time, ~5-minute setup:

1. Open https://console.cloud.google.com and create a project (e.g. "Dayline").
2. **APIs & Services → Library →** enable **Google Drive API**.
3. **APIs & Services → OAuth consent screen →** External; app name "Dayline"; your email; add the scope
   `.../auth/drive.appdata`. Add your Google account as a test user, or publish the app ("In production") so
   you aren't asked to reconnect every 7 days.
4. **Credentials → Create credentials → OAuth client ID → Android:** package `com.dayline.app` and the
   SHA-1 from `outputs/google-sync-setup.txt` (written by every build). Add a second one for
   `com.dayline.app.debug` if you also use the debug APK.
5. In Dayline: **More → Settings → Google sync → Connect Google account.** Do the same on your other devices
   (install the same APK so the signing key matches).

How conflicts are handled: if two devices changed things before syncing, the device you're using keeps its
version and the other version is saved on the phone — **Settings → Google sync → Restore the other device's
version** brings it back. Nothing is silently lost.
