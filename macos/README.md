# Dayline for Mac

A native SwiftUI version of Dayline for macOS 14 (Sonoma) or newer on Apple silicon. It has the same tasks, calendar, habits, focus timer, notes and statistics as the Android app, and it syncs with your phone through the same private file in your Google Drive.

## Build

You only need Apple's free Command Line Tools (`xcode-select --install`); Xcode is optional.

```sh
cd macos
swift run DaylineChecks   # core self-checks (dates, repeats, streaks, sync file format…)
./build.sh                # builds build/Dayline.app and ../outputs/Dayline-mac-<version>.zip
./screenshots.sh          # optional: opens the app with sample data and saves every screen
```

`VERSION` holds the app version. Releases are tagged `mac-v<version>` with a `Dayline-mac-<version>.zip` asset; the app checks GitHub Releases for these and updates itself.

## Layout

- `Sources/DaylineCore`: data model, the sync/backup file format (identical to the phone's), repeats, habit streaks, statistics, quick-add shortcuts, the focus timer and note formatting.
- `Sources/Dayline`: the SwiftUI app (screens, menu bar item, notifications, Google Drive sync, updates).
- `Sources/DaylineChecks`: self-checks for `DaylineCore`.
