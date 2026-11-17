import SwiftUI
import AppKit
import UniformTypeIdentifiers
import DaylineCore

struct SettingsView: View {
    var body: some View {
        TabView {
            GeneralSettings().tabItem { Label("General", systemImage: "gearshape") }
            RemindersSettings().tabItem { Label("Reminders", systemImage: "bell") }
            FocusSettings().tabItem { Label("Focus", systemImage: "timer") }
            TagsSettings().tabItem { Label("Tags", systemImage: "number") }
            SyncSettings().tabItem { Label("Sync", systemImage: "arrow.triangle.2.circlepath") }
            DataSettings().tabItem { Label("Backup", systemImage: "externaldrive") }
            AboutSettings().tabItem { Label("About", systemImage: "info.circle") }
        }
        .frame(width: 520)
        .padding(.vertical, 6)
    }
}

/// Binding to a shared (phone-synced) setting.
@MainActor
func settingBinding<T>(_ store: Store, _ key: String, _ get: @escaping (Store) -> T, _ set: @escaping (T) -> JSONValue?) -> Binding<T> {
    Binding(get: { get(store) }, set: { store.set(key, set($0)) })
}

struct GeneralSettings: View {
    @EnvironmentObject var store: Store
    var body: some View {
        Form {
            TextField("Your name", text: settingBinding(store, "user_name", { $0.userName }, { .string($0) }), prompt: Text("Used in the greeting on Today"))
            Picker("Appearance", selection: settingBinding(store, "theme_mode", { $0.themeMode }, { .string($0) })) {
                Text("System").tag("SYSTEM"); Text("Light").tag("LIGHT"); Text("Dark").tag("DARK")
            }
            LabeledContent("Accent") {
                HStack(spacing: 8) {
                    ForEach(Palette.accents.indices, id: \.self) { i in
                        let c = Palette.accents[i]
                        Circle().fill(Color(argb: c)).frame(width: 20, height: 20)
                            .overlay(Circle().strokeBorder(Color.primary, lineWidth: c == store.accentColor ? 2 : 0).padding(-3))
                            .onTapGesture { store.set("accent_color", .int(Int64(c))) }
                            .help(Palette.accentNames[i])
                    }
                }
            }
            Picker("Week starts on", selection: settingBinding(store, "week_start", { $0.weekStart.rawValue }, { .int(Int64($0)) })) {
                ForEach(Weekday.allCases, id: \.self) { Text($0.name).tag($0.rawValue) }
            }
            Picker("Date format", selection: settingBinding(store, "date_format", { $0.dateFormat }, { .string($0) })) {
                Text("Nov 12, 2026").tag("MEDIUM"); Text("12 Nov 2026").tag("DAY_MONTH"); Text("12/11/2026").tag("NUMERIC_DMY")
                Text("11/12/2026").tag("NUMERIC_MDY"); Text("2026-11-12").tag("ISO")
            }
            Picker("Time format", selection: settingBinding(store, "time_format", { $0.timeFormat }, { .string($0) })) {
                Text("System").tag("SYSTEM"); Text("12-hour").tag("H12"); Text("24-hour").tag("H24")
            }
            Picker("Default priority", selection: settingBinding(store, "default_priority", { $0.defaultPriority }, { .string($0.rawValue) })) {
                ForEach(Priority.allCases, id: \.self) { Text($0.label).tag($0) }
            }
            Toggle("Show Dayline in the menu bar", isOn: Binding(get: { UserDefaults.standard.object(forKey: "menuBarExtra") as? Bool ?? true },
                                                                    set: { UserDefaults.standard.set($0, forKey: "menuBarExtra"); store.objectWillChange.send() }))
            Text("These settings are shared with Dayline on your phone when sync is on.").font(.caption).foregroundStyle(.secondary)
        }
        .formStyle(.grouped)
    }
}

struct RemindersSettings: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var services: Services
    @State private var permission = "Checking…"
    var body: some View {
        Form {
            Toggle("Task reminders", isOn: settingBinding(store, "task_reminders", { $0.taskReminders }, { .bool($0) }))
            Toggle("Habit reminders", isOn: settingBinding(store, "habit_reminders", { $0.habitReminders }, { .bool($0) }))
            Toggle("Focus timer alerts", isOn: settingBinding(store, "focus_notifications", { $0.focusNotifications }, { .bool($0) }))
            Picker("Remind new dated tasks", selection: settingBinding(store, "default_reminder_offset", { $0.defaultReminderOffset ?? -1 }, { .int(Int64($0)) })) {
                Text("Don't remind").tag(-1)
                ForEach(ReminderPlanner.standardOffsets, id: \.self) { Text(ReminderPlanner.describeOffset($0)).tag($0) }
            }
            LabeledContent("Time for tasks without a time") {
                DatePicker("", selection: Binding(get: { Day.today().date(at: store.defaultReminderTime) },
                                                  set: { store.set("default_reminder_time", .int(Int64(TimeOfDay($0).seconds))) }), displayedComponents: .hourAndMinute)
                    .labelsHidden()
            }
            LabeledContent("Notifications") {
                HStack {
                    Text(permission).foregroundStyle(.secondary)
                    Button("Open System Settings") {
                        NSWorkspace.shared.open(URL(string: "x-apple.systempreferences:com.apple.Notifications-Settings.extension")!)
                    }
                }
            }
        }
        .formStyle(.grouped)
        .task {
            services.notifier.requestPermission()
            let s = await services.notifier.authorizationStatus()
            permission = s == .authorized || s == .provisional ? "Allowed" : s == .denied ? "Turned off" : "Not decided"
        }
    }
}

struct FocusSettings: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var timer: FocusTimer
    func stepper(_ title: String, _ key: String, _ value: Int, _ range: ClosedRange<Int>, step: Int = 1, unit: String = "min") -> some View {
        Stepper("\(title): \(value) \(unit)", value: Binding(get: { value }, set: { store.set(key, .int(Int64($0))); timer.configChanged() }), in: range, step: step)
    }
    var body: some View {
        let c = store.focusConfig
        Form {
            stepper("Focus", "focus_minutes", c.focusMinutes, 1...240, step: 5)
            stepper("Short break", "short_break", c.shortBreakMinutes, 1...60)
            stepper("Long break", "long_break", c.longBreakMinutes, 1...90, step: 5)
            stepper("Long break after", "sessions_before_long", c.sessionsBeforeLongBreak, 1...12, unit: "sessions")
            stepper("Daily goal", "focus_goal", c.dailyGoalMinutes, 10...720, step: 10)
            Toggle("Start breaks automatically", isOn: settingBinding(store, "auto_breaks", { $0.focusConfig.autoStartBreaks }, { .bool($0) }))
            Toggle("Start the next focus session automatically", isOn: settingBinding(store, "auto_focus", { $0.focusConfig.autoStartFocus }, { .bool($0) }))
        }
        .formStyle(.grouped)
    }
}

struct SyncSettings: View {
    @EnvironmentObject var sync: SyncService
    @State private var confirmDisconnect = false
    var body: some View {
        Form {
            Section {
                if let account = sync.account {
                    LabeledContent("Account", value: account)
                    LabeledContent("Status", value: sync.statusText)
                    HStack {
                        Button("Sync Now") { Task { await sync.syncNow() } }.disabled(sync.status == .syncing)
                        Button("Switch Account…") { sync.switchAccount() }
                        Spacer()
                        Button("Disconnect…", role: .destructive) { confirmDisconnect = true }
                    }
                } else {
                    Text("Keep your Mac and phone in step through your own Google Drive. Dayline stores one private file in Drive's hidden app folder — it isn't visible in your Drive and nothing goes to any other server.")
                        .foregroundStyle(.secondary)
                    Button("Connect Google Account") { sync.connect() }.buttonStyle(.borderedProminent)
                    if case .failed(let m) = sync.status { Text(m).foregroundStyle(StatusColor.overdue) }
                }
            } header: { Text("Google Drive sync") }
            if sync.hasConflictCopy {
                Section("Conflict") {
                    Text("Both devices changed before they could sync. Dayline kept this Mac's version and saved the other one.").foregroundStyle(.secondary)
                    HStack {
                        Button("Restore the Other Copy") { sync.restoreConflictCopy() }
                        Button("Discard It") { sync.discardConflictCopy() }
                    }
                }
            }
            Section {
                Link("Privacy policy", destination: URL(string: "https://vishalchavan7.github.io/privacy.html")!)
            }
        }
        .formStyle(.grouped)
        .confirmationDialog("Disconnect Google sync?", isPresented: $confirmDisconnect) {
            Button("Disconnect", role: .destructive) { sync.disconnect() }
        } message: { Text("This Mac stops syncing. Your data stays on this Mac, and the copy in Google Drive stays where it is.") }
    }
}

struct DataSettings: View {
    @EnvironmentObject var store: Store
    @State private var pending: DaylineData?
    @State private var error: String?
    var body: some View {
        Form {
            Section("Backup") {
                Text("Backups use the same file format as the phone app, so you can move data either way.").foregroundStyle(.secondary)
                HStack {
                    Button("Export Backup…") { export() }
                    Button("Restore from Backup…") { pick() }
                }
                if let error { Text(error).foregroundStyle(StatusColor.overdue) }
            }
            Section("Where your data lives") {
                LabeledContent("Folder") {
                    Button("Show in Finder") { NSWorkspace.shared.activateFileViewerSelecting([Store.supportDirectory.appendingPathComponent("dayline.json")]) }
                }
                LabeledContent("Contents", value: BackupCodec.summary(store.data))
            }
        }
        .formStyle(.grouped)
        .alert("Replace everything on this Mac?", isPresented: Binding(get: { pending != nil }, set: { if !$0 { pending = nil } })) {
            Button("Restore", role: .destructive) { if let p = pending { store.replaceAll(with: p) }; pending = nil }
            Button("Cancel", role: .cancel) { pending = nil }
        } message: { Text(pending.map { "The backup has \(BackupCodec.summary($0)). A safety copy of your current data is kept first." } ?? "") }
    }

    func export() {
        let p = NSSavePanel()
        p.allowedContentTypes = [.json]
        p.nameFieldStringValue = "dayline-backup-\(Day.today()).json"
        if p.runModal() == .OK, let url = p.url { try? store.exportJSON().write(to: url, options: .atomic) }
    }

    func pick() {
        let p = NSOpenPanel()
        p.allowedContentTypes = [.json]
        guard p.runModal() == .OK, let url = p.url else { return }
        do { pending = try BackupCodec.decode(Data(contentsOf: url)); error = nil } catch { self.error = error.localizedDescription }
    }
}

struct AboutSettings: View {
    @EnvironmentObject var updater: Updater
    var body: some View {
        Form {
            HStack(spacing: 14) {
                Image(nsImage: NSApp.applicationIconImage).resizable().frame(width: 64, height: 64)
                VStack(alignment: .leading) {
                    Text("Dayline for Mac").font(.title2.weight(.semibold))
                    Text("Tasks, calendar, habits, focus and notes.").foregroundStyle(.secondary)
                }
            }
            LabeledContent("Updates") {
                HStack {
                    Text(updater.caption).foregroundStyle(.secondary)
                    if case .ready = updater.state {
                        Button("Install and Relaunch") { updater.installAndRelaunch() }.buttonStyle(.borderedProminent)
                    } else {
                        Button("Check for Updates") { Task { await updater.check(userInitiated: true) } }.disabled(updater.state == .checking || updater.state == .downloading)
                    }
                }
            }
            Link("Dayline on GitHub", destination: URL(string: "https://github.com/\(Updater.repo)")!)
            Link("Report a problem", destination: URL(string: "https://github.com/\(Updater.repo)/issues/new/choose")!)
            Text("MIT License · Made by Vishal Chavan").font(.caption).foregroundStyle(.secondary)
        }
        .formStyle(.grouped)
    }
}

/// Rename or delete tags (the phone's Settings → Tags).
struct TagsSettings: View {
    @EnvironmentObject var store: Store
    @State private var renaming: Tag?
    @State private var newName = ""
    var body: some View {
        let counts = Dictionary(grouping: store.data.taskTags, by: \.tagId).mapValues(\.count)
        Form {
            if store.data.tags.isEmpty {
                Text("No tags yet. Add #tags to tasks and they show up here.").foregroundStyle(.secondary)
            }
            ForEach(store.data.tags.sorted { $0.name.localizedLowercase < $1.name.localizedLowercase }) { t in
                HStack {
                    Text("#" + t.name)
                    Text("\(counts[t.id] ?? 0) task\((counts[t.id] ?? 0) == 1 ? "" : "s")").foregroundStyle(.secondary)
                    Spacer()
                    Button("Rename…") { newName = t.name; renaming = t }
                    Button(role: .destructive) { store.deleteTag(t.id) } label: { Image(systemName: "trash") }.help("Delete tag")
                }
                .buttonStyle(.borderless)
            }
        }
        .formStyle(.grouped)
        .frame(minHeight: 260)
        .alert("Rename tag", isPresented: Binding(get: { renaming != nil }, set: { if !$0 { renaming = nil } })) {
            TextField("Name", text: $newName)
            Button("Rename") { if let t = renaming { store.renameTag(t.id, newName) }; renaming = nil }
            Button("Cancel", role: .cancel) { renaming = nil }
        }
    }
}
