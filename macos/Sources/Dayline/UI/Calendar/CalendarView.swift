import SwiftUI
import DaylineCore

enum CalendarMode: String, CaseIterable { case month = "Month", week = "Week", year = "Year" }

struct CalendarView: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Navigator
    @State private var mode: CalendarMode = .month
    @State private var anchor = Day.today()
    @State private var selected = Day.today()

    var body: some View {
        HSplitView {
            VStack(spacing: 0) {
                header
                Divider()
                switch mode {
                case .month: MonthGrid(anchor: anchor, selected: $selected, entries: entries(for:))
                case .week: WeekColumns(anchor: anchor, selected: $selected, entries: entries(for:))
                case .year: YearGrid(year: anchor.year, selected: $selected) { d in selected = d; anchor = d; mode = .month }
                }
            }
            .frame(minWidth: 520, maxWidth: .infinity, maxHeight: .infinity)
            DayPanel(day: selected, entries: entries(for: selected))
                .frame(minWidth: 260, idealWidth: 300, maxWidth: 380, maxHeight: .infinity)
        }
        .navigationTitle("Calendar")
        .toolbar {
            Picker("View", selection: $mode) { ForEach(CalendarMode.allCases, id: \.self) { Text($0.rawValue).tag($0) } }
                .pickerStyle(.segmented).fixedSize()
        }
    }

    var title: String {
        switch mode {
        case .month: return anchor.date().formatted(.dateTime.month(.wide).year())
        case .week:
            let days = DateRules.weekDays(anchor, store.weekStart)
            return "\(days[0].date().formatted(.dateTime.month(.abbreviated).day())) – \(days[6].date().formatted(.dateTime.month(.abbreviated).day().year()))"
        case .year: return "\(anchor.year)"
        }
    }

    var header: some View {
        HStack {
            Text(title).font(.title2.weight(.semibold))
            Spacer()
            Button { move(-1) } label: { Image(systemName: "chevron.left") }.help("Previous")
            Button("Today") { anchor = store.today; selected = store.today }
            Button { move(1) } label: { Image(systemName: "chevron.right") }.help("Next")
        }
        .buttonStyle(.bordered)
        .padding(.horizontal, 16).padding(.vertical, 10)
    }

    func move(_ n: Int) {
        switch mode {
        case .month: anchor = anchor.plusMonths(n)
        case .week: anchor = anchor.plusWeeks(n)
        case .year: anchor = anchor.plusYears(n)
        }
    }

    /// Tasks on a day, plus projected future copies of repeating tasks (shown faded).
    func entries(for day: Day) -> [CalendarEntry] {
        var out = TaskGrouping.onDay(store.data.tasks, day).map { CalendarEntry(task: $0, projected: false) }
        if day > store.today {
            for t in store.openTasks {
                guard let rule = t.recurrence, let a = t.scheduledDate, a < day else { continue }
                if RecurrenceEngine.projected(rule, anchor: a, from: day, to: day, weekStart: store.weekStart, limit: 1).first == day {
                    out.append(CalendarEntry(task: t, projected: true))
                }
            }
        }
        return out.sorted { a, b in a.task.isCompleted != b.task.isCompleted ? !a.task.isCompleted : TaskGrouping.chronological(a.task, b.task) }
    }
}

struct CalendarEntry: Identifiable {
    let task: TaskItem
    let projected: Bool
    var id: String { "\(task.id)-\(projected)" }
}

struct EntryChip: View {
    @EnvironmentObject var store: Store
    let entry: CalendarEntry
    var body: some View {
        let t = entry.task
        HStack(spacing: 4) {
            RoundedRectangle(cornerRadius: 1.5).fill(t.priority.color).frame(width: 3, height: 12)
            if entry.projected { Image(systemName: "repeat").font(.system(size: 8)) }
            Text(t.title).lineLimit(1).strikethrough(t.isCompleted)
        }
        .font(.caption)
        .padding(.vertical, 2).padding(.horizontal, 4)
        .frame(maxWidth: .infinity, alignment: .leading)
        .fixedSize(horizontal: false, vertical: true)
        .background(RoundedRectangle(cornerRadius: 4).fill((store.project(t.projectId).map { Color(argb: $0.color) } ?? t.priority.color).opacity(0.12)))
        .opacity(entry.projected || t.isCompleted ? 0.5 : 1)
    }
}

struct MonthGrid: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Navigator
    let anchor: Day
    @Binding var selected: Day
    let entries: (Day) -> [CalendarEntry]
    @State private var dropTarget: Day?

    var body: some View {
        let grid = DateRules.monthGrid(anchor, store.weekStart)
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                ForEach(DateRules.orderedWeekdays(store.weekStart), id: \.self) { w in
                    Text(w.short).font(.caption.weight(.semibold)).foregroundStyle(.secondary).frame(maxWidth: .infinity).padding(.vertical, 6)
                }
            }
            GeometryReader { geo in
                let rowH = geo.size.height / 6
                VStack(spacing: 0) {
                    ForEach(0..<6, id: \.self) { r in
                        HStack(spacing: 0) {
                            ForEach(0..<7, id: \.self) { c in
                                let d = grid[r * 7 + c]
                                cell(d, inMonth: d.month == anchor.month, height: rowH)
                            }
                        }
                    }
                }
            }
        }
    }

    func cell(_ d: Day, inMonth: Bool, height: CGFloat) -> some View {
        let list = entries(d)
        let maxChips = max(0, Int((height - 26) / 18))
        let isToday = d == store.today
        return VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text("\(d.dayOfMonth)")
                    .font(.callout.weight(isToday ? .bold : .regular).monospacedDigit())
                    .foregroundStyle(isToday ? .white : inMonth ? .primary : .secondary)
                    .frame(width: 24, height: 22)
                    .background(Circle().fill(isToday ? Color.accentColor : .clear))
                Spacer()
            }
            ForEach(list.prefix(maxChips)) { e in
                EntryChip(entry: e)
                    .draggable(TaskDragItem(id: e.task.id))
                    .onTapGesture(count: 2) { nav.open(e.task.id) }
            }
            if list.count > maxChips && maxChips >= 0 {
                Text("+\(list.count - maxChips) more").font(.caption2).foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
        }
        .padding(4)
        .frame(maxWidth: .infinity, minHeight: height, maxHeight: height, alignment: .topLeading)
        .background(selected == d ? Color.accentColor.opacity(0.10) : dropTarget == d ? Color.accentColor.opacity(0.18) : inMonth ? Color.clear : Color.primary.opacity(0.025))
        .overlay(Rectangle().strokeBorder(Color.primary.opacity(0.06), lineWidth: 0.5))
        .contentShape(Rectangle())
        .onTapGesture { selected = d }
        .dropDestination(for: TaskDragItem.self) { items, _ in
            items.forEach { store.reschedule($0.id, to: d) }
            return true
        } isTargeted: { dropTarget = $0 ? d : (dropTarget == d ? nil : dropTarget) }
    }
}

struct WeekColumns: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Navigator
    let anchor: Day
    @Binding var selected: Day
    let entries: (Day) -> [CalendarEntry]
    @State private var dropTarget: Day?

    var body: some View {
        HStack(spacing: 0) {
            ForEach(DateRules.weekDays(anchor, store.weekStart), id: \.self) { d in
                let isToday = d == store.today
                VStack(alignment: .leading, spacing: 6) {
                    VStack(alignment: .leading, spacing: 0) {
                        Text(d.weekday.short).font(.caption.weight(.semibold)).foregroundStyle(isToday ? Color.accentColor : .secondary)
                        Text("\(d.dayOfMonth)").font(.title2.weight(isToday ? .bold : .regular)).foregroundStyle(isToday ? Color.accentColor : .primary)
                    }
                    ScrollView {
                        VStack(spacing: 4) {
                            ForEach(entries(d)) { e in
                                VStack(alignment: .leading, spacing: 2) {
                                    EntryChip(entry: e)
                                    if let time = e.task.dueTime ?? e.task.startTime {
                                        Text(Formats(store: store).time(time)).font(.caption2).foregroundStyle(.secondary).padding(.leading, 8)
                                    }
                                }
                                .draggable(TaskDragItem(id: e.task.id))
                                .onTapGesture(count: 2) { nav.open(e.task.id) }
                            }
                        }
                    }
                }
                .padding(8)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                .background(selected == d ? Color.accentColor.opacity(0.08) : dropTarget == d ? Color.accentColor.opacity(0.18) : .clear)
                .overlay(alignment: .trailing) { Rectangle().fill(Color.primary.opacity(0.06)).frame(width: 0.5) }
                .contentShape(Rectangle())
                .onTapGesture { selected = d }
                .dropDestination(for: TaskDragItem.self) { items, _ in
                    items.forEach { store.reschedule($0.id, to: d) }
                    return true
                } isTargeted: { dropTarget = $0 ? d : (dropTarget == d ? nil : dropTarget) }
            }
        }
    }
}

struct YearGrid: View {
    @EnvironmentObject var store: Store
    let year: Int
    @Binding var selected: Day
    let open: (Day) -> Void

    var body: some View {
        let counts = Dictionary(grouping: store.data.tasks.compactMap(\.scheduledDate), by: { $0 }).mapValues(\.count)
        ScrollView {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 190), spacing: 18)], spacing: 18) {
                ForEach(1...12, id: \.self) { m in
                    let first = Day(year: year, month: m, day: 1)
                    VStack(alignment: .leading, spacing: 6) {
                        Text(first.date().formatted(.dateTime.month(.wide))).font(.headline)
                            .foregroundStyle(store.today.year == year && store.today.month == m ? Color.accentColor : .primary)
                        let grid = DateRules.monthGrid(first, store.weekStart)
                        Grid(horizontalSpacing: 2, verticalSpacing: 2) {
                            ForEach(0..<6, id: \.self) { r in
                                GridRow {
                                    ForEach(0..<7, id: \.self) { c in
                                        let d = grid[r * 7 + c]
                                        let n = counts[d] ?? 0
                                        Text(d.month == m ? "\(d.dayOfMonth)" : "")
                                            .font(.system(size: 10).monospacedDigit())
                                            .frame(width: 22, height: 18)
                                            .foregroundStyle(d == store.today ? .white : .primary)
                                            .background(RoundedRectangle(cornerRadius: 4).fill(
                                                d == store.today ? Color.accentColor : (d.month == m && n > 0 ? Color.accentColor.opacity(min(0.6, 0.15 + Double(n) * 0.12)) : .clear)))
                                            .onTapGesture { if d.month == m { open(d) } }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            .padding(18)
        }
    }
}

struct DayPanel: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Navigator
    let day: Day
    let entries: [CalendarEntry]

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(day.date().formatted(.dateTime.weekday(.wide).month(.wide).day())).font(.title3.weight(.semibold))
            QuickAddField(date: day, placeholder: "Add a task on this day")
            if entries.isEmpty {
                Text("Nothing on this day.").foregroundStyle(.secondary).padding(.top, 8)
            }
            List(selection: $nav.selectedTask) {
                ForEach(entries) { e in
                    if e.projected {
                        HStack { Image(systemName: "repeat").foregroundStyle(.secondary); Text(e.task.title).foregroundStyle(.secondary) }
                            .help("A future repeat of this task")
                    } else {
                        TaskRow(task: e.task, showDate: false).tag(e.task.id)
                    }
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .contextMenu(forSelectionType: Int64.self) { _ in } primaryAction: { ids in if let id = ids.first { nav.open(id) } }
            let habits = store.activeHabits.filter { HabitStreaks.isScheduled($0, day) }
            if !habits.isEmpty && !(day > store.today) {
                Divider()
                Text("Habits").font(.headline)
                FlowRow(spacing: 6) { ForEach(habits) { HabitChip(habit: $0, day: day) } }
            }
        }
        .padding(14)
    }
}
