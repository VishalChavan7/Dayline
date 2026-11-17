import SwiftUI
import AppKit
import DaylineCore

enum HabitPeriod: String, CaseIterable, Identifiable {
    case all = "All", morning = "Morning", afternoon = "Afternoon", evening = "Evening", anytime = "Anytime"
    var id: String { rawValue }
    var symbol: String {
        switch self { case .all: "square.grid.2x2"; case .morning: "sunrise"; case .afternoon: "sun.max"; case .evening: "moon.stars"; case .anytime: "clock" }
    }
    static func of(_ h: Habit) -> HabitPeriod {
        guard let t = h.reminderTime else { return .anytime }
        return t.hour < 12 ? .morning : t.hour < 17 ? .afternoon : .evening
    }
}

/// Habits, inspired by habit-tracker apps: pick a day, tap a ring to check in, see streaks on the right.
struct HabitsView: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Navigator
    @State private var day = Day.today()
    @State private var period: HabitPeriod = .all
    @State private var selected: Int64?
    @State private var showArchived = false

    var body: some View {
        let all = store.activeHabits
        let archived = store.data.habits.filter(\.isArchived)
        Group {
            if all.isEmpty && archived.isEmpty {
                IllustratedEmpty(title: "Grow a new habit", message: "Track every day, specific days, or a number of times a week. Streaks forgive the days you didn't plan.") {
                    SproutArt(color: Destination.habits.tint)
                }
                .overlay(alignment: .bottom) {
                    Button { newHabit() } label: { Label("New Habit", systemImage: "plus") }.glassButton(prominent: true).controlSize(.large).padding(.bottom, 70)
                }
            } else {
                HStack(spacing: 0) {
                    list(all, archived)
                    if let id = selected, store.habit(id) != nil {
                        HabitDetailPane(habitId: id, day: day) { selected = nil }
                            .frame(width: 340)
                            .glass(Radius.panel)
                            .padding([.vertical, .trailing], 16)
                            .transition(.move(edge: .trailing).combined(with: .opacity))
                    }
                }
                .animation(.snappy, value: selected)
            }
        }
        .navigationTitle("Habits")
    }

    func newHabit() {
        nav.editingHabit = Habit(id: 0, name: "", color: Palette.itemColors[store.data.habits.count % Palette.itemColors.count], startDate: store.today)
    }

    func list(_ all: [Habit], _ archived: [Habit]) -> some View {
        let due = all.filter { HabitStreaks.isScheduled($0, day) && (period == .all || HabitPeriod.of($0) == period) }
        let open = due.filter { !HabitStreaks.isDone($0, store.count($0.id, on: day)) }
        let done = due.filter { HabitStreaks.isDone($0, store.count($0.id, on: day)) }
        let other = all.filter { !HabitStreaks.isScheduled($0, day) && (period == .all || HabitPeriod.of($0) == period) }
        return ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack(alignment: .firstTextBaseline) {
                    ScreenTitle(title: "Habits", subtitle: subtitle(done.count, due.count))
                    Spacer()
                    Button { newHabit() } label: { Label("New Habit", systemImage: "plus") }.glassButton(prominent: true)
                }
                DayPicker(day: $day)
                HStack(spacing: 6) {
                    ForEach(HabitPeriod.allCases) { p in
                        Button { period = p } label: {
                            Label(p.rawValue, systemImage: p.symbol).font(.system(size: 12.5, weight: .semibold, design: .rounded))
                                .padding(.horizontal, 12).padding(.vertical, 6)
                                .foregroundStyle(period == p ? .white : .primary)
                                .background(Capsule().fill(period == p ? Destination.habits.tint : Color.primary.opacity(0.06)))
                        }.buttonStyle(.plain)
                    }
                }
                if due.isEmpty && other.isEmpty {
                    Text("No habits here.").foregroundStyle(.secondary).padding(.top, 20)
                }
                ForEach(open) { h in HabitCard(habit: h, day: day, selected: selected == h.id) { selected = h.id } }
                if !done.isEmpty {
                    Text("Done · \(done.count)").font(.system(size: 13, weight: .bold, design: .rounded)).foregroundStyle(.secondary).padding(.top, 4)
                    ForEach(done) { h in HabitCard(habit: h, day: day, selected: selected == h.id) { selected = h.id } }
                }
                if !other.isEmpty {
                    Text("Not planned for this day").font(.system(size: 13, weight: .bold, design: .rounded)).foregroundStyle(.secondary).padding(.top, 4)
                    ForEach(other) { h in HabitCard(habit: h, day: day, selected: selected == h.id) { selected = h.id }.opacity(0.55) }
                }
                if !archived.isEmpty {
                    DisclosureGroup("Archived (\(archived.count))", isExpanded: $showArchived) {
                        VStack(spacing: 10) { ForEach(archived) { h in HabitCard(habit: h, day: day, selected: selected == h.id) { selected = h.id }.opacity(0.6) } }
                            .padding(.top, 8)
                    }
                    .padding(.top, 8)
                }
            }
            .padding(24)
            .frame(maxWidth: 820)
            .frame(maxWidth: .infinity)
        }
    }

    func subtitle(_ done: Int, _ total: Int) -> String {
        let when = day == store.today ? "today" : Formats(store: store).date(day, relative: true).lowercased()
        return total == 0 ? "Nothing planned \(when)" : "\(done) of \(total) done \(when)"
    }
}

/// A week of days to pick from, with arrows to move week by week.
struct DayPicker: View {
    @EnvironmentObject var store: Store
    @Binding var day: Day
    var body: some View {
        let days = DateRules.weekDays(day, store.weekStart)
        HStack(spacing: 6) {
            GlassIconButton(symbol: "chevron.left", help: "Previous week", size: 28) { day = day.minusWeeks(1) }
            ForEach(days, id: \.self) { d in
                let sel = d == day, isToday = d == store.today, future = d > store.today
                Button { day = d } label: {
                    VStack(spacing: 2) {
                        Text(d.weekday.short.uppercased()).font(.system(size: 10, weight: .semibold, design: .rounded))
                        Text("\(d.dayOfMonth)").font(.system(size: 16, weight: .bold, design: .rounded))
                    }
                    .foregroundStyle(sel ? .white : isToday ? Destination.habits.tint : .primary)
                    .opacity(future && !sel ? 0.45 : 1)
                    .frame(maxWidth: .infinity).padding(.vertical, 8)
                    .background(RoundedRectangle(cornerRadius: Radius.inner, style: .continuous).fill(sel ? Destination.habits.tint.gradient : Color.clear.gradient))
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .disabled(future)
            }
            GlassIconButton(symbol: "chevron.right", help: "Next week", size: 28) { day = min(store.today, day.plusWeeks(1)) }
        }
        .padding(8)
        .glass(Radius.card)
    }
}

/// One habit for one day: tap the ring to check in.
struct HabitCard: View {
    @EnvironmentObject var store: Store
    let habit: Habit
    let day: Day
    let selected: Bool
    let open: () -> Void

    var body: some View {
        let count = store.count(habit.id, on: day)
        let target = max(1, habit.targetPerDay)
        let done = HabitStreaks.isDone(habit, count)
        let stats = HabitStreaks.stats(habit, store.completions(of: habit.id), today: store.today, weekStart: store.weekStart)
        let color = Color(argb: habit.color)
        HStack(spacing: 14) {
            ColorBadge(color: habit.color, symbol: Icons.symbol(habit.icon), size: 40)
            VStack(alignment: .leading, spacing: 3) {
                Text(habit.name).font(.system(size: 15, weight: .semibold, design: .rounded)).strikethrough(done, color: .secondary)
                HStack(spacing: 10) {
                    Label("\(stats.currentStreak) \(stats.streakUnit == "weeks" ? "wk" : "d") streak", systemImage: "flame.fill")
                        .foregroundStyle(stats.currentStreak > 0 ? StatusColor.streak : .secondary)
                    Text(frequency).foregroundStyle(.secondary)
                }
                .font(.system(size: 12, weight: .medium)).labelStyle(CompactLabel())
            }
            Spacer()
            if target > 1 {
                Button { store.checkIn(habit.id, on: day, delta: -1) } label: { Image(systemName: "minus").frame(width: 24, height: 24) }
                    .buttonStyle(.plain).glass(12, interactive: true).disabled(count == 0).help("Remove one")
                Text("\(count)/\(target)").font(.system(size: 14, weight: .bold, design: .rounded).monospacedDigit()).frame(minWidth: 40)
            }
            Button {
                withAnimation(.snappy) {
                    if target > 1 && !done { store.checkIn(habit.id, on: day, delta: 1) } else { store.toggle(habit.id, on: day) }
                }
            } label: {
                ZStack {
                    Circle().stroke(color.opacity(0.18), lineWidth: 5)
                    Circle().trim(from: 0, to: min(1, Double(count) / Double(target))).stroke(color, style: StrokeStyle(lineWidth: 5, lineCap: .round)).rotationEffect(.degrees(-90))
                    if done { Circle().fill(color).padding(6); Image(systemName: "checkmark").font(.system(size: 14, weight: .heavy)).foregroundStyle(.white) }
                    else { Image(systemName: target > 1 ? "plus" : "checkmark").font(.system(size: 13, weight: .bold)).foregroundStyle(color) }
                }
                .frame(width: 40, height: 40)
                .contentShape(Circle())
                .animation(.snappy, value: count)
            }
            .buttonStyle(.plain)
            .help(done ? "Done — click to undo" : target > 1 ? "Add a check-in" : "Mark as done")
        }
        .padding(14)
        .glass(Radius.card, tint: selected ? color : nil)
        .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).strokeBorder(selected ? color.opacity(0.6) : .clear, lineWidth: 1.5))
        .contentShape(Rectangle())
        .onTapGesture(perform: open)
        .contextMenu {
            Button("Edit…") { open() }
            Button(habit.isArchived ? "Unarchive" : "Archive") { store.setArchived(habit: habit.id, !habit.isArchived) }
            Divider()
            Button("Delete", role: .destructive) { store.deleteHabit(habit.id) }
        }
    }

    var frequency: String {
        var s: String
        switch habit.frequency {
        case .DAILY: s = "Every day"
        case .SPECIFIC_DAYS: s = habit.weekdays.sorted().map(\.short).joined(separator: " ")
        case .WEEKLY_TARGET: s = "\(habit.timesPerWeek)× a week"
        }
        if let t = habit.reminderTime { s += " · " + Formats(store: store).time(t) }
        return s
    }
}

/// Right-hand panel: streaks, completion rate and a month calendar.
struct HabitDetailPane: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Navigator
    let habitId: Int64
    let day: Day
    let close: () -> Void
    @State private var month = Day.today()
    @State private var confirmDelete = false

    var body: some View {
        if let h = store.habit(habitId) {
            let comps = store.completions(of: h.id)
            let s = HabitStreaks.stats(h, comps, today: store.today, weekStart: store.weekStart)
            let color = Color(argb: h.color)
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    HStack {
                        ColorBadge(color: h.color, symbol: Icons.symbol(h.icon), size: 44)
                        VStack(alignment: .leading) {
                            Text(h.name).font(.system(size: 18, weight: .bold, design: .rounded))
                            if !h.description.isEmpty { Text(h.description).font(.callout).foregroundStyle(.secondary) }
                        }
                        Spacer()
                        GlassIconButton(symbol: "xmark", help: "Close", size: 26, action: close)
                    }
                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible())], spacing: 10) {
                        tile("Current streak", "\(s.currentStreak)", s.streakUnit, "flame.fill", StatusColor.streak)
                        tile("Best streak", "\(s.longestStreak)", s.streakUnit, "trophy.fill", Color(hex: 0xEAB308))
                        tile("Last 30 days", "\(Int((s.consistency * 100).rounded()))%", "completed", "chart.line.uptrend.xyaxis", color)
                        tile("Total", "\(s.totalCompletions)", "days done", "checkmark.seal.fill", StatusColor.success)
                    }
                    HStack {
                        Text(month.date().formatted(.dateTime.month(.wide).year())).font(.system(size: 15, weight: .bold, design: .rounded))
                        Spacer()
                        Button { month = month.minusMonths(1) } label: { Image(systemName: "chevron.left") }.buttonStyle(.borderless)
                        Button { month = month.plusMonths(1) } label: { Image(systemName: "chevron.right") }.buttonStyle(.borderless).disabled(month.firstOfMonth >= store.today.firstOfMonth)
                    }
                    MonthDots(habit: h, month: month)
                    HStack {
                        Button("Edit") { nav.editingHabit = h }.glassButton()
                        Button(h.isArchived ? "Unarchive" : "Archive") { store.setArchived(habit: h.id, !h.isArchived) }.glassButton()
                        Spacer()
                        Button(role: .destructive) { confirmDelete = true } label: { Image(systemName: "trash") }.buttonStyle(.borderless).help("Delete habit")
                    }
                }
                .padding(18)
            }
            .confirmationDialog("Delete “\(h.name)” and its history?", isPresented: $confirmDelete) {
                Button("Delete Habit", role: .destructive) { store.deleteHabit(h.id); close() }
            }
        }
    }

    func tile(_ title: String, _ value: String, _ unit: String, _ symbol: String, _ color: Color) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Label(title, systemImage: symbol).font(.system(size: 11, weight: .semibold)).foregroundStyle(color)
            Text(value).font(.system(size: 24, weight: .bold, design: .rounded).monospacedDigit())
            Text(unit).font(.caption).foregroundStyle(.secondary)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: Radius.inner, style: .continuous).fill(Color.primary.opacity(0.05)))
    }
}

/// A month of days, filled where the habit was done.
struct MonthDots: View {
    @EnvironmentObject var store: Store
    let habit: Habit
    let month: Day
    var body: some View {
        let grid = DateRules.monthGrid(month, store.weekStart)
        let color = Color(argb: habit.color)
        VStack(spacing: 6) {
            HStack(spacing: 0) {
                ForEach(DateRules.orderedWeekdays(store.weekStart), id: \.self) { w in Text(w.letter).font(.caption2.weight(.semibold)).foregroundStyle(.secondary).frame(maxWidth: .infinity) }
            }
            ForEach(0..<6, id: \.self) { r in
                HStack(spacing: 0) {
                    ForEach(0..<7, id: \.self) { c in
                        let d = grid[r * 7 + c]
                        let n = store.count(habit.id, on: d)
                        let done = HabitStreaks.isDone(habit, n)
                        let inMonth = d.month == month.month
                        Text(inMonth ? "\(d.dayOfMonth)" : "")
                            .font(.system(size: 11, weight: done ? .bold : .regular, design: .rounded).monospacedDigit())
                            .foregroundStyle(done ? .white : d > store.today ? .secondary : .primary)
                            .frame(width: 28, height: 28)
                            .background(Circle().fill(done ? color : n > 0 ? color.opacity(0.3) : (inMonth && HabitStreaks.isScheduled(habit, d) && !(d > store.today) ? color.opacity(0.08) : .clear)))
                            .overlay(Circle().strokeBorder(d == store.today && inMonth ? color : .clear, lineWidth: 1.5))
                            .frame(maxWidth: .infinity)
                            .onTapGesture { if inMonth && !(d > store.today) { store.toggle(habit.id, on: d) } }
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
struct IdBox: Identifiable { let id: Int64 }
