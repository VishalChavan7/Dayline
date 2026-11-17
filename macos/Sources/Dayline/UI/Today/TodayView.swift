import SwiftUI
import Charts
import DaylineCore

struct TodayView: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Navigator
    @State private var customizing = false

    var body: some View {
        let today = store.today
        let hidden = store.hiddenSections
        let order = store.dashboardOrder.filter { !hidden.contains($0) && $0 != .PROGRESS }
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                HeroBanner()
                QuickAddField(date: today, placeholder: "Add a task for today — try “call Sam !high #work”")
                WeekStrip()
                if !hidden.contains(.PROGRESS) { AtAGlance() }
                Masonry(columns: 2, spacing: 16) {
                    ForEach(order) { s in section(s) }
                }
            }
            .padding(24)
            .frame(maxWidth: 1180)
            .frame(maxWidth: .infinity)
        }
        .scrollContentBackground(.hidden)
        .navigationTitle("Today")
        .toolbar { Button { customizing = true } label: { Label("Customize", systemImage: "slider.horizontal.3") }.help("Choose and order the sections on Today") }
        .sheet(isPresented: $customizing) { DashboardCustomizer() }
    }

    @ViewBuilder func section(_ s: DashboardSection) -> some View {
        let today = store.today
        let open = store.openTasks
        let collapsed = store.collapsedSections.contains(s)
        switch s {
        case .PROGRESS:
            EmptyView()
        case .OVERDUE:
            let tasks = open.filter { $0.isOverdue(today) }.sorted(by: TaskGrouping.chronological)
            if !tasks.isEmpty { TaskCard(section: s, title: "Overdue", symbol: "exclamationmark.circle.fill", tasks: tasks, color: StatusColor.overdue, collapsed: collapsed, action: ("Move all to today", { tasks.forEach { store.reschedule($0.id, to: today) } })) }
        case .TODAY:
            let tasks = TaskGrouping.onDay(store.data.tasks, today).filter { !$0.isOverdue(today) }
                .sorted { a, b in a.isCompleted != b.isCompleted ? !a.isCompleted : (store.groupTodayByPriority && a.priority != b.priority ? a.priority > b.priority : TaskGrouping.chronological(a, b)) }
            TaskCard(section: s, title: "Today", symbol: "sun.max.fill", tasks: tasks, color: Destination.today.tint, collapsed: collapsed, emptyText: "Nothing planned for today. Enjoy the space, or add something above.")
        case .INBOX:
            let tasks = open.filter { $0.scheduledDate == nil && $0.projectId == nil && $0.listId == nil }.sorted { $0.sortOrder < $1.sortOrder }
            if !tasks.isEmpty { TaskCard(section: s, title: "Inbox", symbol: "tray.fill", tasks: Array(tasks.prefix(6)), color: Destination.inbox.tint, collapsed: collapsed, more: tasks.count > 6 ? { nav.go(.inbox) } : nil) }
        case .DEADLINES:
            let tasks = open.filter { t in t.dueDate.map { $0 > today && !($0 > today.plusDays(7)) } ?? false }.sorted(by: TaskGrouping.chronological)
            if !tasks.isEmpty { TaskCard(section: s, title: "Coming up", symbol: "calendar.badge.clock", tasks: tasks, color: Destination.upcoming.tint, collapsed: collapsed) }
        case .FOCUS:
            FocusCard()
        case .WEEKLY:
            WeeklyCard()
        case .QUOTE:
            QuoteCard()
        }
    }
}

/// The illustrated header: a sky that follows the time of day, the greeting and today's summary.
struct HeroBanner: View {
    @EnvironmentObject var store: Store
    var body: some View {
        let today = store.today
        let hour = Calendar.current.component(.hour, from: Date())
        let part = hour < 5 ? "Good night" : hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening"
        let name = store.userName.trimmingCharacters(in: .whitespaces)
        let tasksLeft = TaskGrouping.onDay(store.openTasks, today).count + store.openTasks.filter { $0.isOverdue(today) }.count
        let habits = store.activeHabits.filter { HabitStreaks.dueOn($0, today, store.completions(of: $0.id), weekStart: store.weekStart) }
        let habitsLeft = habits.filter { !HabitStreaks.isDone($0, store.count($0.id, on: today)) }.count
        let dark = DayPart.now == .night || DayPart.now == .dusk
        ZStack(alignment: .bottomLeading) {
            Landscape(part: .now)
            LinearGradient(colors: [.black.opacity(dark ? 0.25 : 0.0), .clear], startPoint: .bottom, endPoint: .center)
            VStack(alignment: .leading, spacing: 6) {
                Text(Date().formatted(.dateTime.weekday(.wide).month(.wide).day()).uppercased())
                    .font(.system(size: 12, weight: .semibold, design: .rounded)).tracking(1.2)
                    .foregroundStyle(dark ? .white.opacity(0.8) : .black.opacity(0.55))
                Text(name.isEmpty ? part : "\(part), \(name)")
                    .font(.system(size: 36, weight: .bold, design: .rounded))
                    .foregroundStyle(dark ? .white : Color(hex: 0x1C1B2E))
                Text(summary(tasksLeft, habitsLeft))
                    .font(.system(size: 15, weight: .medium, design: .rounded))
                    .foregroundStyle(dark ? .white.opacity(0.85) : .black.opacity(0.6))
            }
            .padding(26)
        }
        .frame(height: 220)
        .clipShape(RoundedRectangle(cornerRadius: Radius.panel, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: Radius.panel, style: .continuous).strokeBorder(.white.opacity(0.35), lineWidth: 1))
        .shadow(color: .black.opacity(0.12), radius: 20, y: 10)
    }

    func summary(_ tasks: Int, _ habits: Int) -> String {
        switch (tasks, habits) {
        case (0, 0): return "Everything's done for today. Take a breath."
        case (_, 0): return "\(tasks) task\(tasks == 1 ? "" : "s") left today."
        case (0, _): return "\(habits) habit\(habits == 1 ? "" : "s") left today."
        default: return "\(tasks) task\(tasks == 1 ? "" : "s") and \(habits) habit\(habits == 1 ? "" : "s") left today."
        }
    }
}

/// This week at a glance; click a day to open it in the calendar.
struct WeekStrip: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Navigator
    var body: some View {
        let days = DateRules.weekDays(store.today, store.weekStart)
        HStack(spacing: 8) {
            ForEach(days, id: \.self) { d in
                let tasks = TaskGrouping.onDay(store.data.tasks, d)
                let isToday = d == store.today
                Button { nav.go(.calendar) } label: {
                    VStack(spacing: 4) {
                        Text(d.weekday.short.uppercased()).font(.system(size: 10, weight: .semibold, design: .rounded)).foregroundStyle(isToday ? .white.opacity(0.85) : .secondary)
                        Text("\(d.dayOfMonth)").font(.system(size: 18, weight: .bold, design: .rounded)).foregroundStyle(isToday ? .white : .primary)
                        HStack(spacing: 2) {
                            ForEach(0..<min(3, tasks.count), id: \.self) { i in
                                Circle().fill(isToday ? .white : (tasks[i].isCompleted ? Color.secondary.opacity(0.5) : tasks[i].priority.color)).frame(width: 4, height: 4)
                            }
                        }.frame(height: 4)
                    }
                    .frame(maxWidth: .infinity).padding(.vertical, 10)
                    .background {
                        if isToday { RoundedRectangle(cornerRadius: Radius.inner, style: .continuous).fill(Color.accentColor.gradient) }
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(8)
        .glass(Radius.card)
    }
}

/// Three rings for today's tasks, habits and focus, plus one-click habit check-ins.
struct AtAGlance: View {
    @EnvironmentObject var store: Store
    var body: some View {
        let today = store.today
        let tasks = TaskGrouping.onDay(store.data.tasks, today)
        let doneTasks = tasks.filter(\.isCompleted).count
        let habits = store.activeHabits.filter { HabitStreaks.dueOn($0, today, store.completions(of: $0.id), weekStart: store.weekStart) }
        let doneHabits = habits.filter { HabitStreaks.isDone($0, store.count($0.id, on: today)) }.count
        let focus = store.focusMinutes(on: today), goal = max(1, store.focusConfig.dailyGoalMinutes)
        Card {
            VStack(alignment: .leading, spacing: 16) {
                HStack(spacing: 22) {
                    GlanceRing(value: tasks.isEmpty ? 0 : Double(doneTasks) / Double(tasks.count), color: Destination.today.tint, title: "Tasks", detail: "\(doneTasks)/\(tasks.count)")
                    GlanceRing(value: habits.isEmpty ? 0 : Double(doneHabits) / Double(habits.count), color: Destination.habits.tint, title: "Habits", detail: "\(doneHabits)/\(habits.count)")
                    GlanceRing(value: Double(focus) / Double(goal), color: Destination.focus.tint, title: "Focus", detail: Formats.minutes(focus))
                    Spacer()
                    let overdue = store.openTasks.filter { $0.isOverdue(today) }.count
                    if overdue > 0 {
                        Label("\(overdue) overdue", systemImage: "exclamationmark.circle.fill").font(.callout.weight(.semibold)).foregroundStyle(StatusColor.overdue)
                    }
                }
                if !habits.isEmpty {
                    FlowRow(spacing: 8) { ForEach(habits) { h in HabitChip(habit: h, day: today) } }
                }
            }
        }
    }
}

struct GlanceRing: View {
    let value: Double
    let color: Color
    let title: String
    let detail: String
    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                Ring(progress: value, lineWidth: 8, color: color)
                Text("\(Int((min(1, value) * 100).rounded()))%").font(.system(size: 12, weight: .bold, design: .rounded).monospacedDigit())
            }
            .frame(width: 58, height: 58)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.system(size: 13, weight: .semibold, design: .rounded)).foregroundStyle(.secondary)
                Text(detail).font(.system(size: 20, weight: .bold, design: .rounded).monospacedDigit())
            }
        }
    }
}

struct QuoteCard: View {
    @EnvironmentObject var store: Store
    var body: some View {
        let q = Quotes.of(store.today)
        ZStack(alignment: .bottomLeading) {
            Landscape(part: .dawn, sunPosition: CGPoint(x: 0.82, y: 0.35)).opacity(0.9)
            LinearGradient(colors: [.white.opacity(0.0), .white.opacity(0.55)], startPoint: .top, endPoint: .bottom)
            VStack(alignment: .leading, spacing: 8) {
                Image(systemName: "quote.opening").foregroundStyle(Color(hex: 0x6E4B8E))
                Text(q.text).font(.system(size: 18, weight: .semibold, design: .serif)).foregroundStyle(Color(hex: 0x1C1B2E)).fixedSize(horizontal: false, vertical: true)
                Text("— \(q.author)").font(.callout).foregroundStyle(Color(hex: 0x1C1B2E).opacity(0.65))
            }
            .padding(20)
        }
        .frame(minHeight: 170)
        .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).strokeBorder(.white.opacity(0.4)))
    }
}

/// Two-column layout that keeps cards at their natural heights.
struct Masonry: Layout {
    var columns: Int
    var spacing: CGFloat
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? 800
        let cols = width < 640 ? 1 : columns
        let colWidth = (width - spacing * CGFloat(cols - 1)) / CGFloat(cols)
        var heights = Array(repeating: CGFloat(0), count: cols)
        for v in subviews {
            let h = v.sizeThatFits(ProposedViewSize(width: colWidth, height: nil)).height
            if h < 1 { continue }
            let i = heights.firstIndex(of: heights.min()!)!
            heights[i] += h + spacing
        }
        return CGSize(width: width, height: max(0, (heights.max() ?? 0) - spacing))
    }
    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let cols = bounds.width < 640 ? 1 : columns
        let colWidth = (bounds.width - spacing * CGFloat(cols - 1)) / CGFloat(cols)
        var heights = Array(repeating: CGFloat(0), count: cols)
        for v in subviews {
            let size = v.sizeThatFits(ProposedViewSize(width: colWidth, height: nil))
            if size.height < 1 { v.place(at: bounds.origin, proposal: .zero); continue }
            let i = heights.firstIndex(of: heights.min()!)!
            v.place(at: CGPoint(x: bounds.minX + CGFloat(i) * (colWidth + spacing), y: bounds.minY + heights[i]), proposal: ProposedViewSize(width: colWidth, height: size.height))
            heights[i] += size.height + spacing
        }
    }
}

struct TaskCard: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Navigator
    let section: DashboardSection
    let title: String
    var symbol: String = "checklist"
    let tasks: [TaskItem]
    var color: Color = .accentColor
    var collapsed = false
    var emptyText: String?
    var action: (String, () -> Void)?
    var more: (() -> Void)?

    var body: some View {
        Card {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    IconTile(symbol: symbol, color: color, size: 24)
                    Text(title).font(.system(size: 16, weight: .bold, design: .rounded))
                    Text("\(tasks.filter { !$0.isCompleted }.count)").font(.subheadline.monospacedDigit()).foregroundStyle(.secondary)
                    Spacer()
                    if let action, !collapsed { Button(action.0, action: action.1).buttonStyle(.borderless).font(.callout) }
                    Button {
                        var c = store.collapsedSections
                        if collapsed { c.remove(section) } else { c.insert(section) }
                        withAnimation(.snappy) { store.setDashboard(collapsed: c) }
                    } label: { Image(systemName: "chevron.down").rotationEffect(.degrees(collapsed ? -90 : 0)) }
                        .buttonStyle(.borderless).help(collapsed ? "Expand" : "Collapse")
                }
                if !collapsed {
                    if tasks.isEmpty, let emptyText {
                        Text(emptyText).foregroundStyle(.secondary).padding(.vertical, 6)
                    }
                    ForEach(tasks) { t in
                        TaskRow(task: t, showDate: section != .TODAY)
                            .onTapGesture(count: 2) { nav.open(t.id) }
                            .onTapGesture { nav.selectedTask = t.id; if nav.showInspector { nav.open(t.id) } }
                            .padding(.horizontal, 6).padding(.vertical, 2)
                            .background(RoundedRectangle(cornerRadius: Radius.small, style: .continuous).fill(nav.selectedTask == t.id ? Color.accentColor.opacity(0.14) : .clear))
                    }
                    if let more { Button("Show all", action: more).buttonStyle(.borderless) }
                }
            }
        }
    }
}

struct HabitChip: View {
    @EnvironmentObject var store: Store
    let habit: Habit
    let day: Day
    var body: some View {
        let count = store.count(habit.id, on: day)
        let done = HabitStreaks.isDone(habit, count)
        Button {
            withAnimation(.snappy) {
                if habit.targetPerDay > 1 && !done { store.checkIn(habit.id, on: day, delta: 1) } else { store.toggle(habit.id, on: day) }
            }
        } label: {
            HStack(spacing: 6) {
                Image(systemName: done ? "checkmark.circle.fill" : Icons.symbol(habit.icon))
                Text(habit.name)
                if habit.targetPerDay > 1 { Text("\(count)/\(habit.targetPerDay)").monospacedDigit().foregroundStyle(done ? .white.opacity(0.85) : .secondary) }
            }
            .font(.callout)
            .padding(.horizontal, 10).padding(.vertical, 6)
            .foregroundStyle(done ? .white : .primary)
            .background(Capsule().fill(done ? Color(argb: habit.color) : Color(argb: habit.color).opacity(0.14)))
        }
        .buttonStyle(.plain)
        .help(done ? "Done today" : habit.targetPerDay > 1 ? "Add a check-in" : "Mark as done")
    }
}

/// Wrapping row of chips.
struct FlowRow: Layout {
    var spacing: CGFloat = 8
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxW = proposal.width ?? 600
        var x: CGFloat = 0, y: CGFloat = 0, row: CGFloat = 0
        for v in subviews {
            let s = v.sizeThatFits(.unspecified)
            if x > 0 && x + s.width > maxW { x = 0; y += row + spacing; row = 0 }
            x += s.width + spacing; row = max(row, s.height)
        }
        return CGSize(width: maxW, height: y + row)
    }
    func placeSubviews(in b: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x: CGFloat = 0, y: CGFloat = 0, row: CGFloat = 0
        for v in subviews {
            let s = v.sizeThatFits(.unspecified)
            if x > 0 && x + s.width > b.width { x = 0; y += row + spacing; row = 0 }
            v.place(at: CGPoint(x: b.minX + x, y: b.minY + y), proposal: ProposedViewSize(s))
            x += s.width + spacing; row = max(row, s.height)
        }
    }
}

struct FocusCard: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Navigator
    @EnvironmentObject var timer: FocusTimer
    var body: some View {
        let minutes = store.focusMinutes(on: store.today)
        let goal = max(1, store.focusConfig.dailyGoalMinutes)
        Card {
            HStack(spacing: 16) {
                ZStack {
                    Ring(progress: Double(minutes) / Double(goal), lineWidth: 8, color: .orange)
                    Image(systemName: "timer").font(.title3).foregroundStyle(.orange)
                }.frame(width: 60, height: 60)
                VStack(alignment: .leading, spacing: 4) {
                    Text("Focus").font(.headline)
                    Text("\(Formats.minutes(minutes)) of \(Formats.minutes(goal)) today").foregroundStyle(.secondary)
                    if timer.state.status != .IDLE {
                        Text("\(timer.state.phase.label) · \(timer.remainingText)").font(.callout.monospacedDigit()).foregroundStyle(.orange)
                    }
                }
                Spacer()
                Button(timer.state.status == .RUNNING ? "Open" : timer.state.status == .PAUSED ? "Resume" : "Start") {
                    if timer.state.status != .RUNNING { timer.start(taskId: timer.state.taskId) }
                    nav.go(.focus)
                }
                .buttonStyle(.borderedProminent).tint(.orange)
            }
        }
    }
}

struct WeeklyCard: View {
    @EnvironmentObject var store: Store
    var body: some View {
        let days = DateRules.weekDays(store.today, store.weekStart)
        let counts = days.map { d in store.data.tasks.filter { $0.isCompleted && $0.completedAt.map { Day($0) } == d }.count }
        let total = counts.reduce(0, +)
        Card {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text("This week").font(.headline)
                    Spacer()
                    Text("\(total) completed").foregroundStyle(.secondary)
                }
                Chart {
                    ForEach(Array(zip(days, counts)), id: \.0) { d, c in
                        BarMark(x: .value("Day", d.weekday.short), y: .value("Completed", c))
                            .foregroundStyle(d == store.today ? Color.accentColor : Color.accentColor.opacity(0.45))
                            .cornerRadius(5)
                    }
                }
                .chartYAxis(.hidden)
                .frame(height: 120)
            }
        }
    }
}

struct DashboardCustomizer: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) var dismiss
    @State private var order: [DashboardSection] = []
    @State private var hidden: Set<DashboardSection> = []
    @State private var byPriority = true

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Customize Today").font(.title2.weight(.semibold))
            Text("Drag to reorder. Untick what you don't need.").foregroundStyle(.secondary)
            List {
                ForEach(order) { s in
                    Toggle(isOn: Binding(get: { !hidden.contains(s) }, set: { on in if on { hidden.remove(s) } else { hidden.insert(s) } })) {
                        VStack(alignment: .leading) { Text(s.label); Text(s.detail).font(.caption).foregroundStyle(.secondary) }
                    }
                }
                .onMove { order.move(fromOffsets: $0, toOffset: $1) }
            }
            .frame(height: 340)
            Toggle("Group today's tasks by priority", isOn: $byPriority)
            HStack {
                Button("Reset") { order = DashboardSection.allCases; hidden = [] }
                Spacer()
                Button("Cancel") { dismiss() }.keyboardShortcut(.cancelAction)
                Button("Done") {
                    store.setDashboard(order: order, hidden: hidden)
                    store.set("group_by_priority", .bool(byPriority))
                    dismiss()
                }.keyboardShortcut(.defaultAction)
            }
        }
        .padding(20)
        .frame(width: 440)
        .onAppear { order = store.dashboardOrder; hidden = store.hiddenSections; byPriority = store.groupTodayByPriority }
    }
}
