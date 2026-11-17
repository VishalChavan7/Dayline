import SwiftUI
import Charts
import DaylineCore

struct FocusView: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var timer: FocusTimer
    @Environment(\.scheme) private var scheme

    var phaseColor: Color { timer.state.phase == .FOCUS ? scheme.tertiary : scheme.primary }

    var body: some View {
        let s = timer.state
        ScrollView {
            VStack(spacing: 24) {
                HStack { ScreenTitle(title: "Focus timer", subtitle: "\(Formats.minutes(store.focusMinutes(on: store.today))) focused today"); Spacer() }
                    .frame(maxWidth: 820)
                Picker("Phase", selection: Binding(get: { s.phase }, set: { timer.switchPhase($0) })) {
                    ForEach(FocusPhase.allCases, id: \.self) { Text($0.label).tag($0) }
                }
                .pickerStyle(.segmented).labelsHidden().frame(width: 360)
                .disabled(s.status != .IDLE)

                ZStack {
                    Ring(progress: timer.progress, lineWidth: 18, color: phaseColor)
                    VStack(spacing: 6) {
                        Text(timer.remainingText)
                            .font(.system(size: 64, weight: .semibold, design: .rounded).monospacedDigit())
                            .contentTransition(.numericText())
                        Text(s.status == .PAUSED ? "Paused" : s.phase.label).font(.title3).foregroundStyle(.secondary)
                        cycleDots
                    }
                }
                .frame(width: 300, height: 300)
                .padding(24)
                .background(Circle().fill(scheme.card))
                .overlay(Circle().strokeBorder(scheme.cardStroke, lineWidth: 1))

                HStack(spacing: 14) {
                    if s.status != .IDLE {
                        Button { timer.stop() } label: { Image(systemName: "stop.fill").frame(width: 24, height: 24) }
                            .help(s.phase == .FOCUS ? "Stop and save the time so far" : "Stop")
                    } else {
                        Button { timer.reset() } label: { Image(systemName: "arrow.counterclockwise").frame(width: 24, height: 24) }.help("Reset")
                    }
                    Button { timer.toggle() } label: {
                        Label(s.status == .RUNNING ? "Pause" : s.status == .PAUSED ? "Resume" : "Start", systemImage: s.status == .RUNNING ? "pause.fill" : "play.fill")
                            .frame(width: 120, height: 28)
                    }
                    .buttonStyle(.borderedProminent).tint(phaseColor).controlSize(.large)
                    .keyboardShortcut(.space, modifiers: [])
                    Button { timer.skip() } label: { Image(systemName: "forward.end.fill").frame(width: 24, height: 24) }.help("Skip to the next phase")
                }
                .controlSize(.large)

                if s.status == .IDLE {
                    Stepper("Length: \(s.durationMillis / 60_000) min", value: Binding(get: { Int(s.durationMillis / 60_000) }, set: { timer.setMinutes($0) }), in: 1...240, step: 5)
                        .fixedSize()
                }

                taskPicker

                todayStats
            }
            .padding(.horizontal, 28).padding(.vertical, 22)
            .frame(maxWidth: .infinity)
        }
        .scrollContentBackground(.hidden)
        .navigationTitle("Focus")
    }

    var cycleDots: some View {
        let n = max(1, timer.config.sessionsBeforeLongBreak)
        return HStack(spacing: 6) {
            ForEach(0..<n, id: \.self) { i in
                Circle().fill(i < timer.state.completedFocusInCycle % n || (timer.state.phase == .LONG_BREAK) ? phaseColor : Color.primary.opacity(0.15))
                    .frame(width: 8, height: 8)
            }
        }
        .help("Focus sessions until a long break")
    }

    var taskPicker: some View {
        let open = store.openTasks.sorted(by: TaskGrouping.chronological)
        return HStack {
            Image(systemName: "target").foregroundStyle(.secondary)
            Picker("Working on", selection: Binding(get: { timer.state.taskId ?? 0 }, set: { timer.link(taskId: $0 == 0 ? nil : $0) })) {
                Text("Nothing in particular").tag(Int64(0))
                ForEach(open.prefix(60)) { t in Text(t.title).tag(t.id) }
                if let id = timer.state.taskId, !open.contains(where: { $0.id == id }), let t = store.task(id) { Text(t.title).tag(id) }
            }
            .frame(maxWidth: 380)
            if let id = timer.state.taskId, let t = store.task(id), !t.isCompleted {
                Button("Complete") { store.setCompleted(id, true) }
            }
        }
    }

    var todayStats: some View {
        let today = store.today
        let sessions = store.data.focusSessions.filter { Day($0.startedAt) == today }.sorted { $0.startedAt > $1.startedAt }
        let minutes = store.focusMinutes(on: today)
        let goal = max(1, timer.config.dailyGoalMinutes)
        let week = DateRules.weekDays(today, store.weekStart)
        return HStack(alignment: .top, spacing: 16) {
            Card {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Today").font(.headline)
                    HStack(alignment: .firstTextBaseline) {
                        Text(Formats.minutes(minutes)).font(.title.weight(.bold))
                        Text("of \(Formats.minutes(goal)) goal").foregroundStyle(.secondary)
                    }
                    ProgressBar(value: Double(minutes) / Double(goal), color: scheme.tertiary)
                    ForEach(sessions.prefix(6), id: \.startedAt) { f in
                        HStack {
                            Image(systemName: f.completed ? "checkmark.circle.fill" : "circle.lefthalf.filled").foregroundStyle(f.completed ? StatusColor.success : .secondary)
                            Text(f.startedAt.formatted(date: .omitted, time: .shortened))
                            Text(store.task(f.taskId)?.title ?? "").foregroundStyle(.secondary).lineLimit(1)
                            Spacer()
                            Text(Formats.minutes(f.focusedMinutes)).monospacedDigit()
                        }.font(.callout)
                    }
                }
            }
            Card {
                VStack(alignment: .leading, spacing: 10) {
                    Text("This week").font(.headline)
                    Chart {
                        ForEach(week, id: \.self) { d in
                            BarMark(x: .value("Day", d.weekday.short), y: .value("Minutes", store.focusMinutes(on: d)))
                                .foregroundStyle(d == today ? scheme.tertiary : scheme.tertiary.opacity(0.4)).cornerRadius(4)
                        }
                        RuleMark(y: .value("Goal", goal)).lineStyle(StrokeStyle(lineWidth: 1, dash: [4])).foregroundStyle(.secondary)
                    }
                    .frame(height: 150)
                }
            }
        }
        .frame(maxWidth: 820)
    }
}
