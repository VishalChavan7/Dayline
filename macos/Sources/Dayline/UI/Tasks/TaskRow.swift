import SwiftUI
import DaylineCore

struct CheckCircle: View {
    let done: Bool
    let color: Color
    var size: CGFloat = 18
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.28, style: .continuous).strokeBorder(color, lineWidth: 1.6)
                .background(RoundedRectangle(cornerRadius: size * 0.28, style: .continuous).fill(done ? color : color.opacity(0.08)))
            if done { Image(systemName: "checkmark").font(.system(size: size * 0.55, weight: .heavy)).foregroundStyle(.white) }
        }
        .frame(width: size, height: size)
        .contentShape(Rectangle())
        .animation(.snappy(duration: 0.2), value: done)
    }
}

struct TaskRow: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Navigator
    let task: TaskItem
    var showProject = true
    var showDate = true

    @State private var hovering = false

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            Button { complete() } label: { CheckCircle(done: task.isCompleted, color: task.priority.color) }
                .buttonStyle(.plain)
                .alignmentGuide(.firstTextBaseline) { $0[VerticalAlignment.center] + 4 }
                .help(task.isCompleted ? "Mark as not done" : "Complete")
                .accessibilityLabel(task.isCompleted ? "Completed" : "Complete \(task.title)")
            VStack(alignment: .leading, spacing: 3) {
                Text(task.title)
                    .font(.system(size: 13.5, weight: .medium))
                    .strikethrough(task.isCompleted, color: .secondary)
                    .foregroundStyle(task.isCompleted ? .secondary : .primary)
                    .lineLimit(2)
                meta
            }
            Spacer(minLength: 6)
            if showDate, let due = dueText {
                Text(due).font(.system(size: 12, weight: .medium)).foregroundStyle(dueColor).lineLimit(1)
            }
        }
        .padding(.vertical, 4)
        .contentShape(Rectangle())
        .onHover { hovering = $0 }
        .contextMenu { TaskMenu(task: task) }
        .draggable(TaskDragItem(id: task.id)) {
            Text(task.title).padding(6).background(.regularMaterial, in: RoundedRectangle(cornerRadius: 6))
        }
    }

    var dueText: String? { Formats(store: store).due(task) }
    var dueColor: Color {
        guard let d = task.scheduledDate, !task.isCompleted else { return .secondary }
        if d < store.today { return StatusColor.overdue }
        if d == store.today { return .accentColor }
        return .secondary
    }

    func complete() {
        let id = task.id, was = task.isCompleted
        withAnimation(.snappy) { store.setCompleted(id, !was) }
        if !was && store.task(id)?.isCompleted == true {
            nav.undo = ("Completed “\(task.title)”", { store.setCompleted(id, false) })
        }
    }

    @ViewBuilder var meta: some View {
        let subs = store.subtasks(of: task.id)
        let tags = store.tags(of: task.id)
        let items: [AnyView] = [
            task.recurrence != nil ? AnyView(Image(systemName: "repeat").foregroundStyle(.secondary).help(task.recurrence!.describe())) : nil,
            !store.reminders(of: task.id).isEmpty && !task.isCompleted ? AnyView(Image(systemName: "bell").foregroundStyle(.secondary)) : nil,
            subs.isEmpty ? nil : AnyView(Label("\(subs.filter(\.isDone).count)/\(subs.count)", systemImage: "checklist").foregroundStyle(.secondary)),
            showProject ? store.project(task.projectId).map { p in
                AnyView(HStack(spacing: 4) { Circle().fill(Color(argb: p.color)).frame(width: 7, height: 7); Text(p.name) }.foregroundStyle(.secondary))
            } : nil,
            store.list(task.listId).map { l in AnyView(Label(l.name, systemImage: "list.bullet").foregroundStyle(.secondary)) },
            tags.isEmpty ? nil : AnyView(Text(tags.map { "#" + $0.name }.joined(separator: " ")).foregroundStyle(.tint)),
            store.blockers(of: task.id).isEmpty || task.isCompleted ? nil : AnyView(Label("Waiting", systemImage: "link").foregroundStyle(.orange)),
        ].compactMap { $0 }
        if !items.isEmpty {
            HStack(spacing: 10) { ForEach(items.indices, id: \.self) { items[$0] } }
                .font(.caption)
                .labelStyle(CompactLabel())
                .lineLimit(1)
        }
    }
}

struct CompactLabel: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: 3) { configuration.icon.imageScale(.small); configuration.title }
    }
}

/// Drag a task onto a calendar day to reschedule it.
struct TaskDragItem: Codable, Transferable {
    let id: Int64
    static var transferRepresentation: some TransferRepresentation { CodableRepresentation(contentType: .json) }
}

struct TaskMenu: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Navigator
    let task: TaskItem

    var body: some View {
        Button(task.isCompleted ? "Mark as Not Done" : "Complete") { store.setCompleted(task.id, !task.isCompleted) }
        Button("Show Details") { nav.open(task.id) }
        Divider()
        Menu("Schedule") {
            Button("Today") { store.reschedule(task.id, to: store.today) }
            Button("Tomorrow") { store.reschedule(task.id, to: store.today.plusDays(1)) }
            Button("Next Week") { store.reschedule(task.id, to: DateRules.startOfWeek(store.today, store.weekStart).plusWeeks(1)) }
            Divider()
            Button("No Date") { store.reschedule(task.id, to: nil) }
        }
        Menu("Priority") {
            ForEach(Priority.allCases.reversed(), id: \.self) { p in
                Button { store.update(task.id) { $0.priority = p } } label: {
                    if p == task.priority { Label(p.label, systemImage: "checkmark") } else { Text(p.label) }
                }
            }
        }
        Menu("Move to") {
            Button("Inbox (no project)") { store.update(task.id) { $0.projectId = nil; $0.listId = nil } }
            if !store.activeProjects.isEmpty { Divider() }
            ForEach(store.activeProjects) { p in Button(p.name) { store.update(task.id) { $0.projectId = p.id; $0.listId = nil } } }
            if !store.data.lists.isEmpty { Divider() }
            ForEach(store.data.lists) { l in Button(l.name) { store.update(task.id) { $0.listId = l.id; $0.projectId = l.projectId } } }
        }
        Button("Start Focus Session") { FocusTimer.shared?.start(taskId: task.id); nav.go(.focus) }
        Button("Duplicate") { store.duplicate(task.id) }
        Divider()
        Button("Delete", role: .destructive) { deleteTask(store, nav, task.id) }
    }
}

@MainActor
func deleteTask(_ store: Store, _ nav: Navigator, _ id: Int64) {
    guard let title = store.task(id)?.title, let snapshot = store.deleteTask(id) else { return }
    if nav.selectedTask == id { nav.selectedTask = nil }
    nav.undo = ("Deleted “\(title)”", { store.restore(snapshot) })
}

/// "Add a task" field with the phone's shortcuts.
struct QuickAddField: View {
    @EnvironmentObject var store: Store
    var date: Day?
    var projectId: Int64?
    var listId: Int64?
    var placeholder = "Add a task — try “tomorrow !high #work”"
    @State private var text = ""
    @FocusState private var focused: Bool

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "plus.circle.fill").font(.title3).foregroundStyle(.tint)
            TextField(placeholder, text: $text)
                .textFieldStyle(.plain)
                .focused($focused)
                .onSubmit(add)
            if let hint { Text(hint).font(.caption).foregroundStyle(.secondary).transition(.opacity) }
        }
        .padding(.horizontal, 14).padding(.vertical, 11)
        .glass(Radius.inner + 4, tint: focused ? .accentColor : nil)
        .overlay(RoundedRectangle(cornerRadius: Radius.inner + 4, style: .continuous).strokeBorder(focused ? Color.accentColor.opacity(0.55) : .clear, lineWidth: 1.5))
    }

    var hint: String? {
        guard !text.isEmpty else { return nil }
        let p = QuickAddParser.parse(text, today: store.today, weekStart: store.weekStart)
        let parts = [p.date.map { Formats(store: store).date($0) }, p.priority.map { $0.label }, p.tags.isEmpty ? nil : p.tags.map { "#" + $0 }.joined(separator: " ")].compactMap { $0 }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }

    func add() {
        let t = text.trimmingCharacters(in: .whitespaces)
        guard !t.isEmpty else { return }
        store.quickAdd(t, date: date, projectId: projectId, listId: listId)
        text = ""
    }
}

/// A titled group of task rows (used by every task screen).
struct TaskSection: View {
    let title: String
    let tasks: [TaskItem]
    var color: Color = .secondary
    var showProject = true
    var showDate = true
    var body: some View {
        if !tasks.isEmpty {
            Section {
                ForEach(tasks) { t in TaskRow(task: t, showProject: showProject, showDate: showDate).tag(t.id) }
            } header: {
                SectionHeader(title: title, count: tasks.count, color: color)
                    .font(.system(size: 13, weight: .bold, design: .rounded))
                    .padding(.top, 6)
            }
        }
    }
}

/// Standard task screen: header, quick add, grouped list, details on double-click.
struct TaskScreen<Header: View, Sections: View>: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Navigator
    let title: String
    var subtitle: String?
    var quickAdd: QuickAddField?
    var isEmpty: Bool
    var empty: EmptyState
    @ViewBuilder var header: Header
    @ViewBuilder var sections: Sections

    var body: some View {
        VStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 14) {
                if Header.self == EmptyView.self { ScreenTitle(title: title, subtitle: subtitle) } else { header }
                if let quickAdd { quickAdd }
            }
            .padding(.horizontal, 24).padding(.top, 18).padding(.bottom, 10)
            if isEmpty {
                empty
            } else {
                List(selection: $nav.selectedTask) { sections }
                    .listStyle(.inset)
                    .scrollContentBackground(.hidden)
                    .contextMenu(forSelectionType: Int64.self) { _ in } primaryAction: { ids in
                        if let id = ids.first { nav.open(id) }
                    }
                    .onDeleteCommand { if let id = nav.selectedTask { deleteTask(store, nav, id) } }
                    .padding(.horizontal, 8)
            }
        }
        .navigationTitle(title)
        .navigationSubtitle(subtitle ?? "")
    }
}
