import SwiftUI
import Charts
import DaylineCore

/// Today, laid out like the phone's dashboard: a progress card, then the sections you chose.
struct TodayView: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Navigator
    @State private var customizing = false

    var body: some View {
        let today = store.today
        let hidden = store.hiddenSections
        let order = store.dashboardOrder.filter { !hidden.contains($0) && $0 != .PROGRESS }
        let model = TodayModel(store: store)
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .bottom) {
                    ScreenTitle(title: today.weekday.name, subtitle: "\(greeting) · \(Formats(store: store).date(today, relative: false))")
                    Spacer()
                }
                .padding(.bottom, 4)
                QuickAddField(date: today, placeholder: "Add a task for today — try “call Sam !high #work”")
                if !hidden.contains(.PROGRESS) { ProgressCard(model: model) }
                Masonry(columns: 2, spacing: 14) {
                    ForEach(order) { s in section(s, model) }
                }
            }
            .padding(.horizontal, 28).padding(.vertical, 22)
            .frame(maxWidth: 1080)
            .frame(maxWidth: .infinity)
        }
        .navigationTitle("Today")
        .toolbar { Button { customizing = true } label: { Label("Customize", systemImage: "slider.horizontal.3") }.help("Choose and order the sections on Today") }
        .sheet(isPresented: $customizing) { DashboardCustomizer() }
    }

    var greeting: String {
        let hour = Calendar.current.component(.hour, from: Date())
        let part = hour < 5 ? "Good night" : hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening"
        let name = store.userName.trimmingCharacters(in: .whitespaces)
        return name.isEmpty ? part : "\(part), \(name)"
    }

    @ViewBuilder func section(_ s: DashboardSection, _ m: TodayModel) -> some View {
        switch s {
        case .PROGRESS: EmptyView()
        case .OVERDUE: if !m.overdue.isEmpty { OverdueCard(model: m) }
        case .TODAY: TodayTasksCard(model: m)
        case .INBOX: InboxCard(model: m)
        case .DEADLINES: DeadlinesCard(model: m)
        case .FOCUS: FocusCard()
        case .WEEKLY: WeeklyCard()
        case .QUOTE: QuoteCard()
        }
    }
}

/// Everything the dashboard shows, worked out once per redraw.
@MainActor
struct TodayModel {
    let today: Day
    let overdue: [TaskItem]
    let todayTasks: [TaskItem]
    let completedToday: [TaskItem]
    let inbox: [TaskItem]
    let deadlines: [TaskItem]
    let habitsDue: Int
    let habitsDone: Int
    let focusMinutes: Int

    init(store: Store) {
        let today = store.today
        self.today = today
        let open = store.openTasks
        overdue = open.filter { $0.isOverdue(today) }.sorted(by: TaskGrouping.chronological)
        let onDay = TaskGrouping.onDay(store.data.tasks, today).filter { !$0.isOverdue(today) }
        todayTasks = onDay.filter { !$0.isCompleted }.sorted(by: TaskGrouping.chronological)
        completedToday = store.data.tasks.filter { t in t.isCompleted && t.completedAt.map { Day($0) == today } ?? false }
        inbox = open.filter { $0.scheduledDate == nil && $0.projectId == nil && $0.listId == nil }.sorted { $0.sortOrder < $1.sortOrder }
        deadlines = open.filter { t in t.dueDate.map { $0 > today && !($0 > today.plusDays(7)) } ?? false }.sorted(by: TaskGrouping.chronological)
        let habits = store.activeHabits.filter { HabitStreaks.dueOn($0, today, store.completions(of: $0.id), weekStart: store.weekStart) }
        habitsDue = habits.count
        habitsDone = habits.filter { HabitStreaks.isDone($0, store.count($0.id, on: today)) }.count
        focusMinutes = store.focusMinutes(on: today)
    }

    var planned: Int { todayTasks.count + overdue.count + completedToday.count }
    var done: Int { completedToday.count }
    var progress: Double { planned == 0 ? 0 : Double(done) / Double(planned) }
}

/// The phone's progress card: one ring, how much is done, and what's left.
struct ProgressCard: View {
    let model: TodayModel
    @Environment(\.scheme) private var scheme
    var body: some View {
        let m = model
        let on = scheme.onPrimaryContainer
        HStack(spacing: 20) {
            ZStack {
                Ring(progress: m.progress, lineWidth: 10, color: scheme.primary, track: on.opacity(0.12))
                Text("\(Int((m.progress * 100).rounded()))%").font(.system(size: 20, weight: .semibold).monospacedDigit()).foregroundStyle(on)
            }
            .frame(width: 88, height: 88)
            VStack(alignment: .leading, spacing: 4) {
                Text(headline).font(.system(size: 20, weight: .semibold)).foregroundStyle(on)
                Text(detail).font(.system(size: 13)).foregroundStyle(on.opacity(0.8))
                HStack(spacing: 16) {
                    mini("timer", Formats.minutes(m.focusMinutes), on)
                    if m.habitsDue > 0 { mini("figure.mind.and.body", "\(m.habitsDone)/\(m.habitsDue)", on) }
                    if !m.overdue.isEmpty { mini("exclamationmark.triangle", "\(m.overdue.count)", StatusColor.overdue) }
                }
                .padding(.top, 4)
            }
            Spacer(minLength: 0)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).fill(scheme.primaryContainer))
    }

    var headline: String {
        if model.planned == 0 { return "A clear day" }
        if model.done == model.planned { return "All done for today" }
        return "\(model.done) of \(model.planned) done"
    }

    var detail: String {
        let m = model
        var parts: [String] = []
        let left = m.todayTasks.count + m.overdue.count
        if left > 0 { parts.append("\(left) task\(left == 1 ? "" : "s") left") }
        let habitsLeft = m.habitsDue - m.habitsDone
        if habitsLeft > 0 { parts.append("\(habitsLeft) habit\(habitsLeft == 1 ? "" : "s") to go") }
        if !m.overdue.isEmpty { parts.append("\(m.overdue.count) overdue") }
        if m.planned == 0 { parts.append("Nothing scheduled yet") }
        return parts.joined(separator: " · ")
    }

    func mini(_ symbol: String, _ text: String, _ color: Color) -> some View {
        Label(text, systemImage: symbol).font(.system(size: 12.5, weight: .semibold).monospacedDigit()).foregroundStyle(color).labelStyle(CompactLabel())
    }
}

/// A dashboard section whose body folds away; the folded state syncs with the phone.
struct CollapsibleCard<Trailing: View, Content: View>: View {
    @EnvironmentObject var store: Store
    let section: DashboardSection
    let title: String
    let symbol: String
    var accent: Color
    var badge: Int?
    @ViewBuilder var trailing: Trailing
    @ViewBuilder var content: Content

    var body: some View {
        let collapsed = store.collapsedSections.contains(section)
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 12) {
                IconBadge(symbol: symbol, color: accent, size: 30)
                Text(title).font(.system(size: 14.5, weight: .semibold))
                if let badge, badge > 0 { CountPill(text: "\(badge)", color: accent) }
                Spacer()
                trailing
                Image(systemName: "chevron.down").font(.system(size: 11, weight: .semibold)).foregroundStyle(.secondary)
                    .rotationEffect(.degrees(collapsed ? -90 : 0))
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
            .contentShape(Rectangle())
            .onTapGesture {
                var c = store.collapsedSections
                if collapsed { c.remove(section) } else { c.insert(section) }
                store.setDashboard(collapsed: c)
            }
            .help(collapsed ? "Expand \(title)" : "Collapse \(title)")
            if !collapsed {
                VStack(alignment: .leading, spacing: 0) { content }
                    .padding(.horizontal, 14).padding(.bottom, 12)
            }
        }
        .surface()
    }
}

/// Task rows inside dashboard cards. Click to open the details.
struct CardTaskRows: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Navigator
    @Environment(\.scheme) private var scheme
    let tasks: [TaskItem]
    var showDate = true
    var showSubtasks = false

    var body: some View {
        ForEach(tasks) { t in
            TaskRow(task: t, showDate: showDate)
                .padding(.horizontal, 6)
                .background(RoundedRectangle(cornerRadius: Radius.inner, style: .continuous).fill(nav.selectedTask == t.id && nav.showInspector ? scheme.selection : .clear))
                .onTapGesture { nav.open(t.id) }
            if showSubtasks && !t.isCompleted {
                ForEach(store.subtasks(of: t.id).filter { !$0.isDone }.prefix(3), id: \.id) { s in
                    HStack(spacing: 10) {
                        Button { store.setSubtaskDone(s.id, true) } label: { CheckCircle(done: false, color: t.priority.color, size: 15) }.buttonStyle(.plain)
                        Text(s.title).font(.system(size: 12.5)).foregroundStyle(.secondary).lineLimit(1)
                    }
                    .padding(.leading, 34).padding(.vertical, 3)
                }
            }
        }
    }
}

struct InlineEmpty: View {
    let text: String
    var body: some View {
        Text(text).font(.callout).foregroundStyle(.secondary).padding(.horizontal, 6).padding(.vertical, 8)
    }
}

/// Text button in the accent colour ("Add to today", "All").
struct TextAction: View {
    let title: String
    var symbol: String?
    let action: () -> Void
    @Environment(\.scheme) private var scheme
    var body: some View {
        Button(action: action) {
            HStack(spacing: 5) {
                if let symbol { Image(systemName: symbol).font(.system(size: 11, weight: .bold)) }
                Text(title)
            }
            .font(.system(size: 12.5, weight: .semibold))
            .foregroundStyle(scheme.primary)
            .padding(.horizontal, 6).padding(.vertical, 5)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

struct OverdueCard: View {
    let model: TodayModel
    var body: some View {
        CollapsibleCard(section: .OVERDUE, title: "Overdue", symbol: "exclamationmark.triangle.fill", accent: StatusColor.overdue, badge: model.overdue.count) {
            EmptyView()
        } content: {
            CardTaskRows(tasks: model.overdue, showDate: true)
        }
    }
}

struct TodayTasksCard: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var services: Services
    @Environment(\.scheme) private var scheme
    let model: TodayModel
    @State private var showDone = false

    var body: some View {
        CollapsibleCard(section: .TODAY, title: "Today", symbol: "sun.max.fill", accent: scheme.primary, badge: model.todayTasks.count) {
            EmptyView()
        } content: {
            if model.todayTasks.isEmpty {
                InlineEmpty(text: model.completedToday.isEmpty ? "Nothing planned for today. Add a task or enjoy the calm." : "Everything planned for today is done.")
            } else if store.groupTodayByPriority {
                ForEach(Priority.allCases.reversed(), id: \.self) { p in
                    let group = model.todayTasks.filter { $0.priority == p }
                    if !group.isEmpty {
                        HStack(spacing: 6) {
                            Circle().fill(p.color).frame(width: 7, height: 7)
                            Text(p.label.uppercased()).font(.system(size: 11, weight: .semibold)).foregroundStyle(p.color)
                        }
                        .padding(.leading, 6).padding(.top, 8).padding(.bottom, 2)
                        CardTaskRows(tasks: group, showDate: false, showSubtasks: true)
                    }
                }
            } else {
                CardTaskRows(tasks: model.todayTasks, showDate: false, showSubtasks: true)
            }
            HStack(spacing: 4) {
                TextAction(title: "Add to today", symbol: "plus") { services.create(.task) }
                if !model.completedToday.isEmpty {
                    TextAction(title: showDone ? "Hide completed" : "Completed today (\(model.completedToday.count))", symbol: "checkmark") { showDone.toggle() }
                }
            }
            .padding(.top, 6)
            if showDone { CardTaskRows(tasks: model.completedToday, showDate: false) }
        }
    }
}

struct InboxCard: View {
    @EnvironmentObject var nav: Navigator
    @Environment(\.scheme) private var scheme
    let model: TodayModel
    var body: some View {
        CollapsibleCard(section: .INBOX, title: "Inbox", symbol: "tray.fill", accent: scheme.primary, badge: model.inbox.count) {
            TextAction(title: "All") { nav.go(.inbox) }
        } content: {
            if model.inbox.isEmpty { InlineEmpty(text: "Inbox zero. Ideas without a date land here.") }
            else { CardTaskRows(tasks: Array(model.inbox.prefix(5)), showDate: false) }
            if model.inbox.count > 5 { TextAction(title: "\(model.inbox.count - 5) more in Inbox") { nav.go(.inbox) } }
        }
    }
}

struct DeadlinesCard: View {
    @EnvironmentObject var nav: Navigator
    @Environment(\.scheme) private var scheme
    let model: TodayModel
    var body: some View {
        CollapsibleCard(section: .DEADLINES, title: "Upcoming deadlines", symbol: "calendar.badge.clock", accent: scheme.tertiary, badge: model.deadlines.count) {
            TextAction(title: "All") { nav.go(.upcoming) }
        } content: {
            if model.deadlines.isEmpty { InlineEmpty(text: "No deadlines in the next 7 days.") }
            else { CardTaskRows(tasks: Array(model.deadlines.prefix(6)), showDate: true) }
        }
    }
}

struct FocusCard: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Navigator
    @EnvironmentObject var timer: FocusTimer
    @Environment(\.scheme) private var scheme
    var body: some View {
        let minutes = store.focusMinutes(on: store.today)
        let goal = max(1, store.focusConfig.dailyGoalMinutes)
        let sessions = store.data.focusSessions.filter { Day($0.startedAt) == store.today && $0.completed }.count
        let running = timer.state.status == .RUNNING
        CollapsibleCard(section: .FOCUS, title: "Focus", symbol: "timer", accent: scheme.tertiary) {
            EmptyView()
        } content: {
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(Formats.minutes(minutes)).font(.system(size: 26, weight: .semibold).monospacedDigit())
                    Text("of \(Formats.minutes(goal)) goal").font(.callout).foregroundStyle(.secondary)
                }
                ProgressBar(value: Double(minutes) / Double(goal), color: scheme.tertiary)
                HStack {
                    Text(running ? "\(timer.state.phase.label) in progress" : sessions > 0 ? "\(sessions) session\(sessions == 1 ? "" : "s") completed" : "No sessions yet today")
                        .font(.callout).foregroundStyle(.secondary)
                    Spacer()
                    Button {
                        if !running { timer.start(taskId: timer.state.taskId) }
                        nav.go(.focus)
                    } label: { Label(running ? "Open" : "Start", systemImage: "play.fill") }
                    .buttonStyle(.tonal)
                }
            }
            .padding(.horizontal, 6)
        }
    }
}

/// Thin rounded progress bar.
struct ProgressBar: View {
    let value: Double
    var color: Color = .accentColor
    var body: some View {
        GeometryReader { g in
            ZStack(alignment: .leading) {
                Capsule().fill(color.opacity(0.18))
                Capsule().fill(color).frame(width: max(6, g.size.width * min(1, max(0, value))))
            }
        }
        .frame(height: 6)
    }
}

struct WeeklyCard: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Navigator
    @Environment(\.scheme) private var scheme
    var body: some View {
        let days = DateRules.weekDays(store.today, store.weekStart)
        let lastWeek = Set(days.map { $0.minusDays(7) })
        var counts: [Day: Int] = [:]
        var last = 0
        for t in store.data.tasks where t.isCompleted {
            guard let at = t.completedAt else { continue }
            let d = Day(at)
            counts[d, default: 0] += 1
            if lastWeek.contains(d) { last += 1 }
        }
        let total = days.reduce(0) { $0 + (counts[$1] ?? 0) }
        let delta = total - last
        return CollapsibleCard(section: .WEEKLY, title: "This week", symbol: "chart.bar.fill", accent: scheme.primary) {
            TextAction(title: "Stats") { nav.go(.stats) }
        } content: {
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text("\(total)").font(.system(size: 26, weight: .semibold).monospacedDigit())
                    Text(last == 0 && total == 0 ? "tasks completed" : last == 0 ? "tasks completed · first week tracked" : "completed · \(delta >= 0 ? "+" : "")\(delta) vs last week")
                        .font(.callout).foregroundStyle(.secondary)
                }
                Chart {
                    ForEach(days, id: \.self) { d in
                        BarMark(x: .value("Day", d.weekday.short), y: .value("Completed", counts[d] ?? 0))
                            .foregroundStyle(d == store.today ? scheme.primary : scheme.primary.opacity(0.35))
                            .cornerRadius(4)
                    }
                }
                .chartYAxis(.hidden)
                .frame(height: 110)
            }
            .padding(.horizontal, 6)
        }
    }
}

struct QuoteCard: View {
    @EnvironmentObject var store: Store
    @Environment(\.scheme) private var scheme
    var body: some View {
        let q = Quotes.of(store.today)
        VStack(alignment: .leading, spacing: 8) {
            Image(systemName: "quote.opening").foregroundStyle(scheme.onTertiaryContainer)
            Text(q.text).font(.system(size: 15, weight: .medium)).foregroundStyle(scheme.onTertiaryContainer).fixedSize(horizontal: false, vertical: true)
            Text("— \(q.author)").font(.system(size: 12.5, weight: .medium)).foregroundStyle(scheme.onTertiaryContainer.opacity(0.75))
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).fill(scheme.tertiaryContainer))
    }
}

/// Two-column layout that keeps cards at their natural heights (one column when narrow).
struct Masonry: Layout {
    var columns: Int
    var spacing: CGFloat
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? 800
        let cols = width < 700 ? 1 : columns
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
        let cols = bounds.width < 700 ? 1 : columns
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

/// Habit check-in chip (menu bar).
struct HabitChip: View {
    @EnvironmentObject var store: Store
    let habit: Habit
    let day: Day
    var body: some View {
        let count = store.count(habit.id, on: day)
        let done = HabitStreaks.isDone(habit, count)
        let p = ItemColor.pair(habit.color)
        Button {
            if habit.targetPerDay > 1 && !done { store.checkIn(habit.id, on: day, delta: 1) } else { store.toggle(habit.id, on: day) }
        } label: {
            HStack(spacing: 6) {
                Image(systemName: done ? "checkmark" : Icons.symbol(habit.icon)).font(.system(size: 11, weight: .bold))
                Text(habit.name)
                if habit.targetPerDay > 1 { Text("\(count)/\(habit.targetPerDay)").monospacedDigit().opacity(0.8) }
            }
            .font(.callout)
            .padding(.horizontal, 10).frame(height: 26)
            .foregroundStyle(done ? .white : p.content)
            .background(RoundedRectangle(cornerRadius: Radius.small, style: .continuous).fill(done ? Color(argb: habit.color) : p.container))
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
