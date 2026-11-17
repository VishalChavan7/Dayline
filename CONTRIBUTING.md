# Contributing to Dayline

Thanks for helping make Dayline better! Bug reports, ideas, design feedback, translations and code are all welcome.

## Before you start

- **Found a bug?** [Open a bug report](https://github.com/VishalChavan7/Dayline/issues/new?template=bug_report.yml) with your device (phone model and Android version, or Mac model and macOS version) and steps to reproduce.
- **Have an idea?** [Suggest a feature](https://github.com/VishalChavan7/Dayline/issues/new?template=feature_request.yml) first, so we can agree on the approach before you spend time on it.
- **Want to code?** Pick an open issue (look for `good first issue` or `help wanted`) and comment that you're working on it.

## Set up the Android app

1. Install **Android Studio 2025.2+** (or JDK 17 and Android SDK 36).
2. Fork this repository and clone your fork.
3. Open the folder in Android Studio, wait for Gradle sync, and press **Run ▶**.

The debug build installs as `com.dayline.app.debug`, so it can sit next to the released app on your phone.

```bash
./gradlew :app:assembleDebug        # build
./gradlew :app:testDebugUnitTest    # unit, repository, ViewModel and screenshot tests
```

Google sync is optional for development. To try it, create your own Google Cloud project and add an Android OAuth client for `com.dayline.app.debug` with your debug key's SHA-1 (printed to `outputs/google-sync-setup.txt` after a build). Everything else works without it.

## Set up the Mac app

The Mac app lives in [`macos/`](macos). It's a SwiftUI app for macOS 14+ that builds with Apple's free Command Line Tools (`xcode-select --install`); Xcode is optional.

```bash
cd macos
swift run DaylineChecks   # self-checks for the shared rules and the sync file format
./build.sh                # builds Dayline.app
```

The Mac and Android apps share one data format (the backup / Google Drive sync file), so a change to the data model needs to land in both. See [macos/README.md](macos/README.md) for details.

## How the code is organised

```
app/src/main/java/com/dayline/app/
├── core/          models, pure-Kotlin domain rules, Room database, repositories, backup
├── notifications/ reminders and alarm scheduling
├── focus/         focus timer
├── sync/          Google Drive sync
├── widget/        Glance home-screen widgets
└── ui/            Compose screens, ViewModels, theme and shared components
```

- **Screens** are split into a ViewModel-backed `XxxScreen` and a stateless `XxxContent` composable. Put UI in `Content` so it can be previewed and screenshot-tested.
- **Business rules** (recurrence, streaks, statistics, quick-add parsing) live in `core/domain` with no Android dependencies, and each has unit tests.
- **Repositories** are the only code that writes to the database. Screens observe `Flow`s.
- **Design:** use the Material 3 Expressive components, theme colours and `Dimens` spacing tokens rather than hard-coded values, and check your screen in dark mode and at large font sizes.

## Making a change

1. Create a branch from `main`, for example `fix/habit-counter` or `feature/subtask-reorder`.
2. Keep each pull request focused on one change.
3. Add or update tests for logic changes. For UI changes, the screenshot tests in `PhoneSizeScreenshotTest` render your screen on 9 phone set-ups, from a 320 dp phone to a foldable.
4. Make sure `./gradlew :app:testDebugUnitTest` passes.
5. Open a pull request using the template and include before and after screenshots for anything visual.

## Principles

Dayline is **offline-first and private by design**. Please don't add analytics, ads, trackers or any server that receives user data. New permissions need a clear reason in the pull request.

## Code of conduct

Be kind and constructive. Assume good intent, review the code rather than the person, and help newcomers feel welcome. Maintainers may remove comments or contributions that are disrespectful.

## License

By contributing, you agree that your contributions are licensed under the project's [MIT License](LICENSE).
