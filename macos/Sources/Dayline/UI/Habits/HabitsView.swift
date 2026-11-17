import SwiftUI
import AppKit
import DaylineCore

struct HabitsView: View {
    @EnvironmentObject var store: Store
    @State private var editing: Habit?
    @State private var detail: Int64?
    @State private var showArchived = false

    var body: some View {
        let habits = store.activeHabits
        let archived = store.data.habits.filter(\.isArchived)
        Group {
            if habits.isEmpty && archived.isEmpty {
                EmptyState(symbol: "flame", title: "Build a habit", message: "Track daily habits, specific days, or a number of times a week — with streaks that forgive unscheduled days.")
                    .overlay(alignment: .bottom) { Button("New Habit") { editing = newHabit() }.buttonStyle(.borderedProminent).padding(.bottom, 80) }
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        HabitWeekHeader()
                        ForEach(habits) { h in HabitRow(habit: h, onOpen: { detail = h.id }, onEdit: { editing = h }) }
                        if !archived.isEmpty {
                            DisclosureGroup("Archived (\(archived.count))", isExpanded: $showArchived) {
                                ForEach(archived) { h in HabitRow(habit: h, onOpen: { detail = h.id }, onEdit: { editing = h }).opacity(0.6) }
                            }
                            .padding(.top, 8)
                        }
                    }
                    .padding(24)
                    .frame(maxWidth: 980)
                    .frame(maxWidth: .infinity)
                }
            }
        }
        .navigationTitle("Habits")
        .toolbar { Button { editing = newHabit() } label: { Label("New Habit", systemImage: "flame").labelStyle(.titleAndIcon) } }
        .sheet(item: $editing) { h in HabitEditor(habit: h) }
        .sheet(item: Binding(get: { detail.map(IdBox.init) }, set: { detail = $0?.id })) { b in HabitDetail(habitId: b.id) }
    }

    func newHabit() -> Habit { Habit(id: 0, name: "", color: Palette.itemColors[store.data.habits.count % Palette.itemColors.count], startDate: store.today) }
}

struct IdBox: Identifiable { let id: Int64 }

struct HabitWeekHeader: View {
    @EnvironmentObject var store: Store
    var body: some View {
        HStack(spacing: 12) {
            Spacer()
            ForEach(lastDays(store.today), id: \.self) { d in
                VStack(spacing: 1) {
                    Text(d.weekday.short.prefix(2)).font(.caption2)
                    Text("\(d.dayOfMonth)").font(.caption.weight(d == store.today ? .bold : .regular))
                }
                .foregroundStyle(d == store.today ? Color.accentColor : .secondary)
                .frame(width: 36)
            }
            // Matches the streak and consistency columns (12 + 70 + 12 + 44 + card padding 12).
            Spacer().frame(width: 138)
        }
    }
}

func lastDays(_ today: Day, _ n: Int = 7) -> [Day] { (0..<n).map { today.minusDays(n - 1 - $0) } }

struct HabitRow: View {
    @EnvironmentObject var store: Store
    let habit: Habit
    let onOpen: () -> Void
    let onEdit: () -> Void

    var body: some View {
        let comps = store.completions(of: habit.id)
        let stats = HabitStreaks.stats(habit, comps, today: store.today, weekStart: store.weekStart)
        let color = Color(argb: habit.color)
        Card(padding: 12) {
            HStack(spacing: 12) {
                ColorBadge(color: habit.color, symbol: Icons.symbol(habit.icon), size: 34)
                VStack(alignment: .leading, spacing: 2) {
                    Text(habit.name).font(.headline)
                    Text(frequencyText).font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
                ForEach(lastDays(store.today), id: \.self) { d in DayCell(habit: habit, day: d, color: color) }
                HStack(spacing: 4) {
                    Image(systemName: "flame.fill").foregroundStyle(stats.currentStreak > 0 ? StatusColor.streak : .secondary.opacity(0.4))
                    Text("\(stats.currentStreak)").font(.title3.weight(.semibold).monospacedDigit())
                    Text(stats.streakUnit == "weeks" ? "wk" : "d").font(.caption).foregroundStyle(.secondary)
                }
                .frame(width: 70, alignment: .trailing)
                .help("Current streak · best \(stats.longestStreak) \(stats.streakUnit)")
                Text("\(Int((stats.consistency * 100).rounded()))%").font(.callout.monospacedDigit()).foregroundStyle(.secondary).frame(width: 44, alignment: .trailing)
                    .help("Consistency over the last 30 days")
            }
        }
        .contentShape(Rectangle())
        .onTapGesture(count: 2, perform: onOpen)
        .contextMenu {
            Button("Details", action: onOpen)
            Button("Edit…", action: onEdit)
            Button(habit.isArchived ? "Unarchive" : "Archive") { store.setArchived(habit: habit.id, !habit.isArchived) }
            Divider()
            Button("Delete", role: .destructive) { store.deleteHabit(habit.id) }
        }
    }

    var frequencyText: String {
        var s: String
        switch habit.frequency {
        case .DAILY: s = "Every day"
        case .SPECIFIC_DAYS: s = habit.weekdays.sorted().map(\.short).joined(separator: ", ")
        case .WEEKLY_TARGET: s = "\(habit.timesPerWeek)× a week"
        }
        if habit.targetPerDay > 1 { s += " · \(habit.targetPerDay) a day" }
        if let t = habit.reminderTime { s += " · " + Formats(store: store).time(t) }
        return s
    }
}

struct DayCell: View {
    @EnvironmentObject var store: Store
    let habit: Habit
    let day: Day
    let color: Color

    var body: some View {
        let count = store.count(habit.id, on: day)
        let done = HabitStreaks.isDone(habit, count)
        let scheduled = HabitStreaks.isScheduled(habit, day)
        let future = day > store.today
        Button {
            withAnimation(.snappy) {
                if habit.targetPerDay > 1 && !done && NSEvent.modifierFlags.contains(.option) == false { store.checkIn(habit.id, on: day, delta: 1) }
                else { store.toggle(habit.id, on: day) }
            }
        } label: {
            ZStack {
                Circle().fill(done ? color : (scheduled ? color.opacity(0.12) : Color.clear))
                Circle().strokeBorder(color.opacity(scheduled ? 0.35 : 0.12), lineWidth: 1)
                if done { Image(systemName: "checkmark").font(.system(size: 11, weight: .bold)).foregroundStyle(.white) }
                else if count > 0 { Text("\(count)").font(.system(size: 10, weight: .semibold).monospacedDigit()).foregroundStyle(color) }
            }
            .frame(width: 26, height: 26)
            .frame(width: 36)
        }
        .buttonStyle(.plain)
        .disabled(future || day < habit.startDate)
        .opacity(future ? 0.3 : 1)
        .help(habit.targetPerDay > 1 ? "\(count) of \(habit.targetPerDay) — click to add, ⌥-click to clear" : (done ? "Done" : "Mark as done"))
        .contextMenu {
            if habit.targetPerDay > 1 {
                Button("Add One") { store.checkIn(habit.id, on: day, delta: 1) }
                Button("Remove One") { store.checkIn(habit.id, on: day, delta: -1) }
            }
            Button(done ? "Mark as Not Done" : "Mark as Done") { store.toggle(habit.id, on: day) }
        }
    }
}

struct HabitDetail: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) var dismiss
    let habitId: Int64
    @State private var editing: Habit?

    var body: some View {
        if let h = store.habit(habitId) {
            let comps = store.completions(of: h.id)
            let s = HabitStreaks.stats(h, comps, today: store.today, weekStart: store.weekStart)
            VStack(alignment: .leading, spacing: 18) {
                HStack(spacing: 12) {
                    ColorBadge(color: h.color, symbol: Icons.symbol(h.icon), size: 44)
                    VStack(alignment: .leading) {
                        Text(h.name).font(.title2.weight(.semibold))
                        if !h.description.isEmpty { Text(h.description).foregroundStyle(.secondary) }
                    }
                    Spacer()
                    Button("Edit") { editing = h }
                    Button("Done") { dismiss() }.keyboardShortcut(.defaultAction)
                }
                HStack(spacing: 12) {
                    stat("Current streak", "\(s.currentStreak)", s.streakUnit, "flame.fill", StatusColor.streak)
                    stat("Best streak", "\(s.longestStreak)", s.streakUnit, "trophy.fill", .yellow)
                    stat("Consistency", "\(Int((s.consistency * 100).rounded()))%", "last 30 days", "chart.line.uptrend.xyaxis", .accentColor)
                    stat("Check-ins", "\(s.totalCompletions)", "days done", "checkmark.circle.fill", StatusColor.success)
                }
                Text("Last 26 weeks").font(.headline)
                HabitHeatmap(habit: h, completions: comps)
            }
            .padding(22)
            .frame(width: 640)
            .sheet(item: $editing) { HabitEditor(habit: $0) }
        }
    }

    func stat(_ title: String, _ value: String, _ unit: String, _ symbol: String, _ color: Color) -> some View {
        Card(padding: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Label(title, systemImage: symbol).font(.caption).foregroundStyle(color)
                Text(value).font(.title.weight(.bold).monospacedDigit())
                Text(unit).font(.caption).foregroundStyle(.secondary)
            }
        }
    }
}

struct HabitHeatmap: View {
    @EnvironmentObject var store: Store
    let habit: Habit
    let completions: [HabitCompletion]
    var body: some View {
        let today = store.today
        let start = DateRules.startOfWeek(today, store.weekStart).minusWeeks(25)
        let counts = Dictionary(completions.map { ($0.date, $0.count) }, uniquingKeysWith: +)
        let color = Color(argb: habit.color)
        HStack(alignment: .top, spacing: 3) {
            ForEach(0..<26, id: \.self) { w in
                VStack(spacing: 3) {
                    ForEach(0..<7, id: \.self) { d in
                        let day = start.plusDays(w * 7 + d)
                        let c = counts[day] ?? 0
                        RoundedRectangle(cornerRadius: 3)
                            .fill(day > today ? Color.clear : c == 0 ? Color.primary.opacity(HabitStreaks.isScheduled(habit, day) ? 0.08 : 0.03)
                                  : color.opacity(HabitStreaks.isDone(habit, c) ? 1 : 0.45))
                            .frame(width: 18, height: 18)
                            .help("\(Formats(store: store).date(day, relative: false)): \(c)")
                    }
                }
            }
        }
    }
}

struct HabitEditor: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) var dismiss
    @State var habit: Habit

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(habit.id == 0 ? "New Habit" : "Edit Habit").font(.title2.weight(.semibold))
            HStack(spacing: 12) {
                ColorBadge(color: habit.color, symbol: Icons.symbol(habit.icon), size: 36)
                TextField("Name, e.g. Read 20 pages", text: $habit.name).textFieldStyle(.roundedBorder).font(.title3)
            }
            TextField("Description (optional)", text: $habit.description).textFieldStyle(.roundedBorder)
            ColorPickerRow(color: $habit.color)
            IconPickerGrid(icon: $habit.icon, color: habit.color)
            Picker("How often", selection: $habit.frequency) {
                ForEach(HabitFrequency.allCases, id: \.self) { Text($0.label).tag($0) }
            }.pickerStyle(.segmented)
            if habit.frequency == .SPECIFIC_DAYS {
                HStack(spacing: 4) {
                    ForEach(DateRules.orderedWeekdays(store.weekStart), id: \.self) { d in
                        let on = habit.weekdays.contains(d)
                        Button(d.short) { if on { habit.weekdays.remove(d) } else { habit.weekdays.insert(d) } }
                            .buttonStyle(.plain).frame(width: 44, height: 26)
                            .background(RoundedRectangle(cornerRadius: 6).fill(on ? Color(argb: habit.color) : Color.primary.opacity(0.07)))
                            .foregroundStyle(on ? .white : .primary)
                    }
                }
            }
            if habit.frequency == .WEEKLY_TARGET {
                Stepper("\(habit.timesPerWeek) times a week", value: $habit.timesPerWeek, in: 1...7)
            }
            Stepper("Done after \(habit.targetPerDay) check-in\(habit.targetPerDay == 1 ? "" : "s") a day", value: $habit.targetPerDay, in: 1...99)
            HStack {
                Toggle("Remind me at", isOn: Binding(get: { habit.reminderTime != nil }, set: { habit.reminderTime = $0 ? (habit.reminderTime ?? .nine) : nil }))
                    .toggleStyle(.checkbox)
                if let t = habit.reminderTime {
                    DatePicker("", selection: Binding(get: { Day.today().date(at: t) }, set: { habit.reminderTime = TimeOfDay($0) }), displayedComponents: .hourAndMinute)
                        .labelsHidden().datePickerStyle(.field)
                }
            }
            HStack {
                Text("Started").foregroundStyle(.secondary)
                DatePicker("", selection: Binding(get: { habit.startDate.date() }, set: { habit.startDate = Day($0) }), displayedComponents: .date).labelsHidden()
            }
            HStack {
                Spacer()
                Button("Cancel") { dismiss() }.keyboardShortcut(.cancelAction)
                Button(habit.id == 0 ? "Create" : "Save") { if store.save(habit) != 0 { dismiss() } }
                    .keyboardShortcut(.defaultAction)
                    .disabled(habit.name.trimmingCharacters(in: .whitespaces).isEmpty)
            }
        }
        .padding(20)
        .frame(width: 460)
    }
}
