import SwiftUI
import DaylineCore

enum CalendarMode: String, CaseIterable { case month = "Month", week = "Week", year = "Year" }

struct CalendarView: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Navigator
    @AppStorage("calendar.mode") private var mode: CalendarMode = .month
    @State private var anchor = Day.today()
    @State private var selected = Day.today()

    var body: some View {
        GeometryReader { geo in
            let wide = geo.size.width >= 860
            let layout = wide ? AnyLayout(HStackLayout(spacing: 14)) : AnyLayout(VStackLayout(spacing: 14))
            layout {
                VStack(spacing: 0) {
                    header(compact: geo.size.width < 640)
                    switch mode {
                    case .month: MonthGrid(anchor: anchor, selected: $selected, entries: entries(for:))
                    case .week: WeekTimeGrid(anchor: anchor, selected: $selected, entries: entries(for:))
                    case .year: YearGrid(year: anchor.year, selected: $selected) { d in selected = d; anchor = d; mode = .month }
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
                .surface()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                DayPanel(day: selected, entries: entries(for: selected))
                    .surface()
                    .frame(width: wide ? min(340, max(280, geo.size.width * 0.3)) : nil, height: wide ? nil : 280)
                    .frame(maxWidth: wide ? nil : .infinity, maxHeight: wide ? .infinity : nil)
            }
            .padding(16)
        }
        .navigationTitle("Calendar")
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

    func header(compact: Bool) -> some View {
        let layout = compact ? AnyLayout(VStackLayout(alignment: .leading, spacing: 10)) : AnyLayout(HStackLayout(spacing: 10))
        return layout {
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(.system(size: 22, weight: .bold))
                Text(todaySummary).font(.system(size: 12.5, weight: .medium)).foregroundStyle(.secondary)
            }
            if !compact { Spacer() }
            HStack(spacing: 10) {
            Segmented(options: CalendarMode.allCases, selection: $mode) { $0.rawValue }
            HStack(spacing: 2) {
                IconButton(symbol: "chevron.left", help: "Previous") { move(-1) }
                Button("Today") { anchor = store.today; selected = store.today }.buttonStyle(.tonal)
                IconButton(symbol: "chevron.right", help: "Next") { move(1) }
            }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16).padding(.vertical, 12)
    }

    var todaySummary: String {
        let n = TaskGrouping.onDay(store.data.tasks, store.today).count
        let m = store.focusMinutes(on: store.today)
        return "Today · \(n) task\(n == 1 ? "" : "s")" + (m > 0 ? " · \(Formats.minutes(m)) focused" : "")
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
        .background(RoundedRectangle(cornerRadius: 4).fill(t.priority.color.opacity(0.13)))
        .opacity(entry.projected || t.isCompleted ? 0.5 : 1)
    }
}

struct MonthGrid: View {
    @EnvironmentObject var store: Store
    @Environment(\.scheme) private var scheme
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
        let fits = max(0, Int((height - 28) / 17))
        let maxChips = list.count > fits ? max(0, fits - 1) : fits
        let isToday = d == store.today
        return VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text("\(d.dayOfMonth)")
                    .font(.callout.weight(isToday ? .bold : .regular).monospacedDigit())
                    .foregroundStyle(isToday ? scheme.onAccent : inMonth ? .primary : .secondary)
                    .frame(width: 24, height: 22)
                    .background(Circle().fill(isToday ? scheme.accent : .clear))
                Spacer()
            }
            ForEach(list.prefix(maxChips)) { e in
                EntryChip(entry: e)
                    .draggable(TaskDragItem(id: e.task.id))
                    .onTapGesture { selected = d; nav.open(e.task.id) }
            }
            if list.count > maxChips {
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

struct YearGrid: View {
    @EnvironmentObject var store: Store
    @Environment(\.scheme) private var scheme
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
                                            .foregroundStyle(d == store.today ? scheme.onAccent : .primary)
                                            .background(RoundedRectangle(cornerRadius: 4).fill(
                                                d == store.today ? scheme.accent : (d.month == m && n > 0 ? Color.accentColor.opacity(min(0.6, 0.15 + Double(n) * 0.12)) : .clear)))
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
        let f = Formats(store: store)
        let n = entries.filter { !$0.projected }.count
        let focus = store.focusMinutes(on: day)
        VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 1) {
                Text(f.date(day)).font(.system(size: 20, weight: .bold))
                Text(day.date().formatted(.dateTime.weekday(.wide).month(.wide).day()) + " · \(n) task\(n == 1 ? "" : "s")" + (focus > 0 ? " · \(Formats.minutes(focus)) focused" : ""))
                    .font(.system(size: 12.5, weight: .medium)).foregroundStyle(.secondary)
            }
            QuickAddField(date: day, placeholder: "Add a task on this day")
            if entries.isEmpty {
                Text("Nothing on this day.").foregroundStyle(.secondary).padding(.top, 4)
                Spacer()
            } else {
                List {
                    ForEach(entries) { e in
                        Group {
                            if e.projected {
                                HStack(spacing: 12) {
                                    Image(systemName: "repeat").foregroundStyle(.secondary).frame(width: 20)
                                    Text(e.task.title).foregroundStyle(.secondary)
                                }
                                .padding(.horizontal, 10).padding(.vertical, 7)
                                .help("A future repeat of this task")
                            } else {
                                TaskRow(task: e.task, showDate: false)
                            }
                        }
                        .taskListRow()
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
                .padding(.horizontal, -8)
            }
        }
        .padding(16)
    }
}


/// Week as a time grid (TickTick-style): an all-day row, then hours with timed tasks as blocks.
/// Drag a task onto a day and time to move it there.
struct WeekTimeGrid: View {
    @EnvironmentObject var store: Store
    @Environment(\.scheme) private var scheme
    @EnvironmentObject var nav: Navigator
    let anchor: Day
    @Binding var selected: Day
    let entries: (Day) -> [CalendarEntry]
    private let hourHeight: CGFloat = 52
    private let gutter: CGFloat = 56

    var body: some View {
        let days = DateRules.weekDays(anchor, store.weekStart)
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                Spacer().frame(width: gutter)
                ForEach(days, id: \.self) { d in
                    let isToday = d == store.today
                    VStack(spacing: 2) {
                        Text(d.weekday.short.uppercased()).font(.system(size: 10, weight: .semibold, design: .rounded)).foregroundStyle(isToday ? Color.accentColor : .secondary)
                        Text("\(d.dayOfMonth)").font(.system(size: 18, weight: .bold, design: .rounded))
                            .foregroundStyle(isToday ? scheme.onAccent : .primary)
                            .frame(width: 32, height: 32)
                            .background(Circle().fill(isToday ? scheme.accent : .clear))
                    }
                    .frame(maxWidth: .infinity)
                    .contentShape(Rectangle())
                    .onTapGesture { selected = d }
                }
            }
            .padding(.vertical, 6)
            // All-day row
            HStack(alignment: .top, spacing: 0) {
                Text("all-day").font(.system(size: 10, weight: .medium)).foregroundStyle(.secondary).frame(width: gutter, alignment: .trailing).padding(.trailing, 6)
                ForEach(days, id: \.self) { d in
                    VStack(spacing: 3) {
                        ForEach(entries(d).filter { ($0.task.dueTime ?? $0.task.startTime) == nil }.prefix(4)) { e in
                            EntryChip(entry: e).draggable(TaskDragItem(id: e.task.id)).onTapGesture { nav.open(e.task.id) }
                        }
                    }
                    .frame(maxWidth: .infinity, minHeight: 28, alignment: .top)
                    .padding(.horizontal, 3)
                    .dropDestination(for: TaskDragItem.self) { items, _ in
                        items.forEach { i in store.reschedule(i.id, to: d); store.update(i.id) { $0.dueTime = nil; $0.startTime = nil } }
                        return true
                    }
                }
            }
            .padding(.bottom, 6)
            Divider()
            ScrollViewReader { proxy in
                ScrollView {
                    ZStack(alignment: .topLeading) {
                        VStack(spacing: 0) {
                            ForEach(0..<24, id: \.self) { h in
                                HStack(alignment: .top, spacing: 0) {
                                    Text(h == 0 ? "" : hourLabel(h)).font(.system(size: 10, weight: .medium).monospacedDigit()).foregroundStyle(.secondary)
                                        .frame(width: gutter - 8, alignment: .trailing).offset(y: -6)
                                    Rectangle().fill(Color.primary.opacity(0.07)).frame(height: 0.5).frame(maxWidth: .infinity).padding(.leading, 8)
                                }
                                .frame(height: hourHeight, alignment: .top)
                                .id(h)
                            }
                        }
                        HStack(spacing: 0) {
                            Spacer().frame(width: gutter)
                            ForEach(days, id: \.self) { d in dayColumn(d) }
                        }
                    }
                    .padding(.top, 10)
                }
                .onAppear { proxy.scrollTo(7, anchor: .top) }
            }
        }
    }

    func hourLabel(_ h: Int) -> String {
        let twentyFour = store.timeFormat == "H24" || (store.timeFormat == "SYSTEM" && !(DateFormatter.dateFormat(fromTemplate: "j", options: 0, locale: .current) ?? "").contains("a"))
        return Formats.formatter(twentyFour ? "HH:mm" : "h a").string(from: Day.today().date(at: TimeOfDay(hour: h, minute: 0)))
    }

    func dayColumn(_ d: Day) -> some View {
        let timed = entries(d).filter { ($0.task.dueTime ?? $0.task.startTime) != nil }
        return ZStack(alignment: .topLeading) {
            Rectangle().fill(d == store.today ? Color.accentColor.opacity(0.05) : Color.clear)
            if d == store.today {
                let now = TimeOfDay(Date())
                Rectangle().fill(StatusColor.overdue).frame(height: 1.5).offset(y: CGFloat(now.seconds) / 3600 * hourHeight)
            }
            ForEach(timed) { e in
                let sp = span(e.task)
                block(e, minutes: sp.1)
                    .offset(y: CGFloat(sp.0.seconds) / 3600 * hourHeight)
            }
        }
        .frame(maxWidth: .infinity, minHeight: hourHeight * 24, maxHeight: hourHeight * 24, alignment: .top)
        .overlay(alignment: .leading) { Rectangle().fill(Color.primary.opacity(0.06)).frame(width: 0.5) }
        .contentShape(Rectangle())
        .onTapGesture { selected = d }
        .dropDestination(for: TaskDragItem.self) { items, location in
            let minutes = max(0, min(23 * 60 + 45, Int((location.y / hourHeight * 60 / 15).rounded()) * 15))
            for i in items {
                store.reschedule(i.id, to: d)
                store.update(i.id) { t in
                    if t.dueDate != nil { t.dueTime = TimeOfDay(hour: minutes / 60, minute: minutes % 60) } else { t.startTime = TimeOfDay(hour: minutes / 60, minute: minutes % 60) }
                }
            }
            return true
        }
    }

    /// Start time and length: start→due on the same day, otherwise an hour.
    func span(_ t: TaskItem) -> (TimeOfDay, Int) {
        if let s = t.startTime, let e = t.dueTime, t.startDate == nil || t.startDate == t.dueDate, e > s {
            return (s, max(30, (e.seconds - s.seconds) / 60))
        }
        return (t.dueTime ?? t.startTime ?? TimeOfDay(hour: 9, minute: 0), 60)
    }

    func block(_ e: CalendarEntry, minutes: Int) -> some View {
        let t = e.task
        let color = t.priority.color
        return VStack(alignment: .leading, spacing: 1) {
            Text(t.title).font(.system(size: 11.5, weight: .semibold)).lineLimit(minutes >= 60 ? 2 : 1).strikethrough(t.isCompleted)
            if minutes >= 45, let time = t.dueTime ?? t.startTime { Text(Formats(store: store).time(time)).font(.system(size: 10)).opacity(0.8) }
        }
        .padding(.horizontal, 6).padding(.vertical, 4)
        .frame(maxWidth: .infinity, minHeight: max(22, CGFloat(minutes) / 60 * hourHeight - 2), maxHeight: max(22, CGFloat(minutes) / 60 * hourHeight - 2), alignment: .topLeading)
        .background(RoundedRectangle(cornerRadius: 8, style: .continuous).fill(color.opacity(e.projected || t.isCompleted ? 0.10 : 0.20)))
        .overlay(alignment: .leading) { RoundedRectangle(cornerRadius: 2).fill(color).frame(width: 3).padding(.vertical, 3) }
        .padding(.horizontal, 2)
        .opacity(e.projected || t.isCompleted ? 0.6 : 1)
        .draggable(TaskDragItem(id: t.id))
        .onTapGesture { nav.open(t.id) }
        .help(t.title)
    }
}
