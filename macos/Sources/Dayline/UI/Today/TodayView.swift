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
        let order = store.dashboardOrder.filter { !hidden.contains($0) }
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                greeting
                QuickAddField(date: today, placeholder: "Add a task for today — try “call Sam !high #work”")
                Masonry(columns: 2, spacing: 16) {
                    ForEach(order) { s in section(s) }
                }
            }
            .padding(24)
            .frame(maxWidth: 1100)
            .frame(maxWidth: .infinity)
        }
        .navigationTitle("Today")
        .navigationSubtitle(Formats(store: store).date(today, relative: false))
        .toolbar { Button { customizing = true } label: { Label("Customize", systemImage: "slider.horizontal.3") }.help("Choose and order the sections on Today") }
        .sheet(isPresented: $customizing) { DashboardCustomizer() }
    }

    var greeting: some View {
        let hour = Calendar.current.component(.hour, from: Date())
        let part = hour < 5 ? "Good night" : hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening"
        let name = store.userName.trimmingCharacters(in: .whitespaces)
        return VStack(alignment: .leading, spacing: 4) {
            Text(name.isEmpty ? part : "\(part), \(name)").font(.largeTitle.weight(.bold))
            Text(Date().formatted(.dateTime.weekday(.wide).month(.wide).day())).font(.title3).foregroundStyle(.secondary)
        }
    }

    @ViewBuilder func section(_ s: DashboardSection) -> some View {
        let today = store.today
        let open = store.openTasks
        let collapsed = store.collapsedSections.contains(s)
        switch s {
        case .PROGRESS:
            ProgressCard()
        case .OVERDUE:
            let tasks = open.filter { $0.isOverdue(today) }.sorted(by: TaskGrouping.chronological)
            if !tasks.isEmpty { TaskCard(section: s, title: "Overdue", tasks: tasks, color: StatusColor.overdue, collapsed: collapsed, action: ("Move all to today", { tasks.forEach { store.reschedule($0.id, to: today) } })) }
        case .TODAY:
            let tasks = TaskGrouping.onDay(store.data.tasks, today).filter { !$0.isOverdue(today) }
                .sorted { a, b in a.isCompleted != b.isCompleted ? !a.isCompleted : (store.groupTodayByPriority && a.priority != b.priority ? a.priority > b.priority : TaskGrouping.chronological(a, b)) }
            TaskCard(section: s, title: "Today", tasks: tasks, collapsed: collapsed, emptyText: "Nothing planned for today. Enjoy the space, or add something above.")
        case .INBOX:
            let tasks = open.filter { $0.scheduledDate == nil && $0.projectId == nil && $0.listId == nil }.sorted { $0.sortOrder < $1.sortOrder }
            if !tasks.isEmpty { TaskCard(section: s, title: "Inbox", tasks: Array(tasks.prefix(6)), collapsed: collapsed, more: tasks.count > 6 ? { nav.go(.inbox) } : nil) }
        case .DEADLINES:
            let tasks = open.filter { t in t.dueDate.map { $0 > today && !($0 > today.plusDays(7)) } ?? false }.sorted(by: TaskGrouping.chronological)
            if !tasks.isEmpty { TaskCard(section: s, title: "Upcoming deadlines", tasks: tasks, collapsed: collapsed) }
        case .FOCUS:
            FocusCard()
        case .WEEKLY:
            WeeklyCard()
        case .QUOTE:
            let q = Quotes.of(today)
            Card {
                VStack(alignment: .leading, spacing: 8) {
                    Image(systemName: "quote.opening").foregroundStyle(.tint)
                    Text(q.text).font(.title3).fixedSize(horizontal: false, vertical: true)
                    Text("— \(q.author)").foregroundStyle(.secondary)
                }
            }
        }
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
    let tasks: [TaskItem]
    var color: Color = .primary
    var collapsed = false
    var emptyText: String?
    var action: (String, () -> Void)?
    var more: (() -> Void)?

    var body: some View {
        Card {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text(title).font(.headline).foregroundStyle(color)
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
                            .background(RoundedRectangle(cornerRadius: 8).fill(nav.selectedTask == t.id ? Color.accentColor.opacity(0.12) : .clear))
                    }
                    if let more { Button("Show all", action: more).buttonStyle(.borderless) }
                }
            }
        }
    }
}

struct ProgressCard: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Navigator
    var body: some View {
        let today = store.today
        let tasks = TaskGrouping.onDay(store.data.tasks, today)
        let doneTasks = tasks.filter(\.isCompleted).count
        let habits = store.activeHabits.filter { HabitStreaks.dueOn($0, today, store.completions(of: $0.id), weekStart: store.weekStart) }
        let doneHabits = habits.filter { HabitStreaks.isDone($0, store.count($0.id, on: today)) }.count
        let total = tasks.count + habits.count, done = doneTasks + doneHabits
        Card {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 18) {
                    ZStack {
                        Ring(progress: total == 0 ? 0 : Double(done) / Double(total), lineWidth: 11)
                        VStack(spacing: 0) {
                            Text(total == 0 ? "–" : "\(Int((Double(done) / Double(max(1, total)) * 100).rounded()))%").font(.title2.weight(.bold).monospacedDigit())
                            Text("done").font(.caption).foregroundStyle(.secondary)
                        }
                    }.frame(width: 92, height: 92)
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Daily progress").font(.headline)
                        Label("\(doneTasks) of \(tasks.count) tasks", systemImage: "checkmark.circle").foregroundStyle(.secondary)
                        Label("\(doneHabits) of \(habits.count) habits", systemImage: "flame").foregroundStyle(.secondary)
                        let overdue = store.openTasks.filter { $0.isOverdue(today) }.count
                        if overdue > 0 { Label("\(overdue) overdue", systemImage: "exclamationmark.circle").foregroundStyle(StatusColor.overdue) }
                    }
                    Spacer()
                }
                if !habits.isEmpty {
                    Divider()
                    FlowRow(spacing: 8) {
                        ForEach(habits) { h in HabitChip(habit: h, day: today) }
                    }
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
