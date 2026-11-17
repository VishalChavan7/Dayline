import SwiftUI
import AppKit
import DaylineCore

/// Habits, like the phone: each habit with its streak, a check button, and this week's days.
struct HabitsView: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Navigator
    @State private var selected: Int64?
    @State private var showArchived = false

    var body: some View {
        let all = store.activeHabits
        let archived = store.data.habits.filter(\.isArchived)
        Group {
            if all.isEmpty && archived.isEmpty {
                VStack(spacing: 0) {
                    EmptyState(symbol: "figure.mind.and.body", title: "Grow a new habit", message: "Track every day, specific days, or a number of times a week. Streaks forgive the days you didn't plan.")
                    Button { newHabit() } label: { Label("New Habit", systemImage: "plus") }.buttonStyle(.filled).padding(.bottom, 80)
                }
            } else {
                HStack(spacing: 0) {
                    list(all, archived)
                    if let id = selected, store.habit(id) != nil {
                        HabitDetailPane(habitId: id) { selected = nil }
                            .frame(width: 340)
                            .surface()
                            .padding([.vertical, .trailing], 16)
                    }
                }
            }
        }
        .navigationTitle("Habits")
    }

    func newHabit() {
        nav.editingHabit = Habit(id: 0, name: "", color: Palette.itemColors[store.data.habits.count % Palette.itemColors.count], startDate: store.today)
    }

    func list(_ all: [Habit], _ archived: [Habit]) -> some View {
        let today = store.today
        let due = all.filter { HabitStreaks.isScheduled($0, today) }
        let done = due.filter { HabitStreaks.isDone($0, store.count($0.id, on: today)) }.count
        let week = DateRules.weekDays(today, store.weekStart)
        return ScrollView {
            LazyVStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .bottom) {
                    ScreenTitle(title: "Habits", subtitle: due.isEmpty ? "Nothing planned today" : "\(done) of \(due.count) done today")
                    Spacer()
                    Button { newHabit() } label: { Label("New Habit", systemImage: "plus") }.buttonStyle(.filled)
                }
                .padding(.bottom, 6)
                HStack(spacing: WeekDots.spacing) {
                    Spacer()
                    ForEach(week, id: \.self) { d in
                        Text(d.weekday.letter).font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(d == today ? Color.accentColor : .secondary)
                            .frame(width: WeekDots.size)
                    }
                }
                .padding(.trailing, 16)
                ForEach(all) { h in
                    HabitCard(habit: h, week: week, selected: selected == h.id) { selected = h.id }
                        .opacity(HabitStreaks.isScheduled(h, today) ? 1 : 0.6)
                }
                if !archived.isEmpty {
                    DisclosureGroup("Archived (\(archived.count))", isExpanded: $showArchived) {
                        VStack(spacing: 10) { ForEach(archived) { h in HabitCard(habit: h, week: week, selected: selected == h.id) { selected = h.id }.opacity(0.6) } }
                            .padding(.top, 8)
                    }
                    .padding(.top, 8)
                }
            }
            .padding(.horizontal, 28).padding(.vertical, 22)
            .frame(maxWidth: 760)
            .frame(maxWidth: .infinity)
        }
    }
}

/// This week's days for one habit; click a past day to tick it.
struct WeekDots: View {
    static let size: CGFloat = 22
    static let spacing: CGFloat = 8
    @EnvironmentObject var store: Store
    let habit: Habit
    let week: [Day]
    var body: some View {
        let color = Color(argb: habit.color)
        HStack(spacing: WeekDots.spacing) {
            ForEach(week, id: \.self) { d in
                let n = store.count(habit.id, on: d)
                let done = HabitStreaks.isDone(habit, n)
                let future = d > store.today
                ZStack {
                    if done {
                        Circle().fill(color)
                        Image(systemName: "checkmark").font(.system(size: 9, weight: .heavy)).foregroundStyle(.white)
                    } else {
                        Circle().strokeBorder(n > 0 ? color : Color.secondary.opacity(future ? 0.2 : 0.45), lineWidth: 1.5)
                        if n > 0 { Circle().fill(color.opacity(0.25)).padding(3) }
                    }
                }
                .frame(width: WeekDots.size, height: WeekDots.size)
                .contentShape(Circle())
                .onTapGesture { if !future { store.toggle(habit.id, on: d) } }
                .help(d.date().formatted(.dateTime.weekday(.wide).month().day()))
            }
        }
    }
}

/// One habit: icon, name, streak, a check button for today, and the week underneath.
struct HabitCard: View {
    @EnvironmentObject var store: Store
    @Environment(\.scheme) private var scheme
    let habit: Habit
    let week: [Day]
    let selected: Bool
    let open: () -> Void

    var body: some View {
        let today = store.today
        let count = store.count(habit.id, on: today)
        let target = max(1, habit.targetPerDay)
        let done = HabitStreaks.isDone(habit, count)
        let stats = HabitStreaks.stats(habit, store.completions(of: habit.id), today: today, weekStart: store.weekStart)
        let p = ItemColor.pair(habit.color)
        let color = Color(argb: habit.color)
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 14) {
                ColorBadge(color: habit.color, symbol: Icons.symbol(habit.icon), size: 40)
                VStack(alignment: .leading, spacing: 2) {
                    Text(habit.name).font(.system(size: 14.5, weight: .semibold))
                    HStack(spacing: 4) {
                        Image(systemName: "flame.fill").foregroundStyle(stats.currentStreak > 0 ? StatusColor.streak : .secondary)
                        Text("\(stats.currentStreak) \(stats.streakUnit == "weeks" ? (stats.currentStreak == 1 ? "week" : "weeks") : (stats.currentStreak == 1 ? "day" : "days")) · \(frequency)")
                            .foregroundStyle(.secondary)
                    }
                    .font(.system(size: 12))
                }
                Spacer()
                if target > 1 {
                    IconButton(symbol: "minus", help: "Remove one", size: 28) { store.checkIn(habit.id, on: today, delta: -1) }
                        .disabled(count == 0)
                    Text("\(count)/\(target)").font(.system(size: 14, weight: .semibold).monospacedDigit()).foregroundStyle(p.content).frame(minWidth: 36)
                    Button { store.checkIn(habit.id, on: today, delta: 1) } label: {
                        Image(systemName: done ? "checkmark" : "plus").font(.system(size: 14, weight: .bold))
                            .foregroundStyle(done ? .white : p.content)
                            .frame(width: 36, height: 36)
                            .background(Circle().fill(done ? color : p.container))
                    }
                    .buttonStyle(.plain).help("Add a check-in")
                } else {
                    Button { store.toggle(habit.id, on: today) } label: {
                        Image(systemName: "checkmark").font(.system(size: 15, weight: .bold))
                            .foregroundStyle(done ? .white : p.content)
                            .frame(width: 40, height: 40)
                            .background(RoundedRectangle(cornerRadius: Radius.inner + 2, style: .continuous).fill(done ? color : p.container))
                    }
                    .buttonStyle(.plain)
                    .help(done ? "Done — click to undo" : "Mark as done")
                }
            }
            HStack { Spacer(); WeekDots(habit: habit, week: week) }
        }
        .padding(14)
        .surface()
        .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).strokeBorder(selected ? color.opacity(0.7) : .clear, lineWidth: 1.5))
        .contentShape(Rectangle())
        .onTapGesture(perform: open)
        .contextMenu {
            Button("Show Details") { open() }
            Button(habit.isArchived ? "Unarchive" : "Archive") { store.setArchived(habit: habit.id, !habit.isArchived) }
            Divider()
            Button("Delete", role: .destructive) { store.deleteHabit(habit.id) }
        }
    }

    var frequency: String {
        var s: String
        switch habit.frequency {
        case .DAILY: s = "Daily"
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
                            Text(h.name).font(.system(size: 17, weight: .bold))
                            if !h.description.isEmpty { Text(h.description).font(.callout).foregroundStyle(.secondary) }
                        }
                        Spacer()
                        IconButton(symbol: "xmark", help: "Close", size: 26, action: close)
                    }
                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible())], spacing: 10) {
                        tile("Current streak", "\(s.currentStreak)", s.streakUnit, "flame.fill", StatusColor.streak)
                        tile("Best streak", "\(s.longestStreak)", s.streakUnit, "trophy.fill", Color(hex: 0xEAB308))
                        tile("Last 30 days", "\(Int((s.consistency * 100).rounded()))%", "completed", "chart.line.uptrend.xyaxis", color)
                        tile("Total", "\(s.totalCompletions)", "days done", "checkmark.seal.fill", StatusColor.success)
                    }
                    HStack {
                        Text(month.date().formatted(.dateTime.month(.wide).year())).font(.system(size: 14, weight: .semibold))
                        Spacer()
                        Button { month = month.minusMonths(1) } label: { Image(systemName: "chevron.left") }.buttonStyle(.borderless)
                        Button { month = month.plusMonths(1) } label: { Image(systemName: "chevron.right") }.buttonStyle(.borderless).disabled(month.firstOfMonth >= store.today.firstOfMonth)
                    }
                    MonthDots(habit: h, month: month)
                    HStack {
                        Button("Edit") { nav.editingHabit = h }.buttonStyle(.tonal)
                        Button(h.isArchived ? "Unarchive" : "Archive") { store.setArchived(habit: h.id, !h.isArchived) }.buttonStyle(.tonal)
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
            Text(value).font(.system(size: 22, weight: .semibold).monospacedDigit())
            Text(unit).font(.caption).foregroundStyle(.secondary)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: Radius.inner, style: .continuous).fill(Color.primary.opacity(0.045)))
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
                            .font(.system(size: 11, weight: done ? .bold : .regular).monospacedDigit())
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
