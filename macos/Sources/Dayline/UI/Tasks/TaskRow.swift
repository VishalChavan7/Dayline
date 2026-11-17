import SwiftUI
import DaylineCore

/// Round completion check tinted by priority, like the phone.
struct CheckCircle: View {
    let done: Bool
    let color: Color
    var size: CGFloat = 20
    var body: some View {
        ZStack {
            Circle().strokeBorder(color, lineWidth: 1.8)
            if done {
                Circle().fill(color)
                Image(systemName: "checkmark").font(.system(size: size * 0.5, weight: .bold)).foregroundStyle(.white)
            }
        }
        .frame(width: size, height: size)
        .contentShape(Circle())
    }
}

struct TaskRow: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Navigator
    let task: TaskItem
    var showDate = true

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Button { complete() } label: { CheckCircle(done: task.isCompleted, color: task.priority.color) }
                .buttonStyle(.plain)
                .padding(.top, 1)
                .help(task.isCompleted ? "Mark as not done" : "Complete")
                .accessibilityLabel(task.isCompleted ? "Completed" : "Complete \(task.title)")
            VStack(alignment: .leading, spacing: 3) {
                Text(task.title)
                    .font(.system(size: 13.5))
                    .strikethrough(task.isCompleted, color: .secondary)
                    .foregroundStyle(task.isCompleted ? .secondary : .primary)
                    .lineLimit(2)
                TaskMeta(task: task, showDate: showDate)
            }
            Spacer(minLength: 6)
            if !task.isCompleted && (task.priority == .HIGH || task.priority == .URGENT) {
                Image(systemName: "flag.fill").font(.system(size: 11)).foregroundStyle(task.priority.color).padding(.top, 2)
            }
        }
        .padding(.vertical, 5)
        .contentShape(Rectangle())
        .contextMenu { TaskMenu(task: task) }
        .draggable(TaskDragItem(id: task.id)) {
            Text(task.title).padding(6).background(RoundedRectangle(cornerRadius: 6).fill(Color(nsColor: .windowBackgroundColor)))
        }
    }

    func complete() {
        let id = task.id, was = task.isCompleted
        store.setCompleted(id, !was)
        if !was && store.task(id)?.isCompleted == true {
            nav.undo = ("Completed “\(task.title)”", { store.setCompleted(id, false) })
        }
    }
}

/// Date, repeat, subtasks, reminders, files and tags under a task title (the phone's meta row).
struct TaskMeta: View {
    @EnvironmentObject var store: Store
    @Environment(\.scheme) private var scheme
    let task: TaskItem
    var showDate = true

    var body: some View {
        let subs = store.subtasks(of: task.id)
        let tags = store.tags(of: task.id)
        let blocked = !task.isCompleted && store.isBlocked(task.id)
        let reminders = task.isCompleted ? 0 : store.reminders(of: task.id).count
        let files = store.attachmentCount(of: task.id)
        let f = Formats(store: store)
        let dateText: String? = {
            if showDate, let d = task.scheduledDate { return f.date(d) + ((task.dueTime ?? task.startTime).map { " " + f.time($0) } ?? "") }
            return (task.dueTime ?? task.startTime).map { f.time($0) }
        }()
        if dateText != nil || blocked || task.recurrence != nil || !subs.isEmpty || reminders > 0 || files > 0 || !tags.isEmpty {
            HStack(spacing: 10) {
                if let dateText {
                    let overdue = !task.isCompleted && task.isOverdue(store.today)
                    Label(dateText, systemImage: showDate && task.scheduledDate != nil ? "calendar" : "clock")
                        .foregroundStyle(overdue ? StatusColor.overdue : .secondary)
                }
                if blocked { Label("Blocked", systemImage: "lock.fill").foregroundStyle(StatusColor.overdue) }
                if let r = task.recurrence { Image(systemName: "repeat").foregroundStyle(.secondary).help(r.describe()) }
                if !subs.isEmpty { Label("\(subs.filter(\.isDone).count)/\(subs.count)", systemImage: "checklist").foregroundStyle(.secondary) }
                if reminders > 0 { Image(systemName: "bell.fill").foregroundStyle(.secondary) }
                if files > 0 { Label("\(files)", systemImage: "paperclip").foregroundStyle(.secondary) }
                ForEach(tags.prefix(3), id: \.id) { t in Text("#" + t.name).foregroundStyle(scheme.tertiary) }
                if tags.count > 3 { Text("+\(tags.count - 3)").foregroundStyle(.secondary) }
            }
            .font(.system(size: 11.5))
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
    @Environment(\.scheme) private var scheme
    var date: Day?
    var placeholder = "Add a task — try “tomorrow !high #work”"
    @State private var text = ""
    @FocusState private var focused: Bool

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "plus").font(.system(size: 14, weight: .semibold)).foregroundStyle(scheme.primary)
            TextField(placeholder, text: $text)
                .textFieldStyle(.plain)
                .font(.system(size: 14))
                .focused($focused)
                .onSubmit(add)
            if let hint { Text(hint).font(.caption).foregroundStyle(.secondary) }
        }
        .padding(.horizontal, 14)
        .frame(height: 48)
        .surface(Radius.card)
        .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).strokeBorder(focused ? Color.accentColor.opacity(0.6) : .clear, lineWidth: 1.5))
        .contentShape(Rectangle())
        .onTapGesture { focused = true }
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
        store.quickAdd(t, date: date)
        text = ""
    }
}

/// A titled group of task rows (used by every task screen).
struct TaskSection: View {
    let title: String?
    let tasks: [TaskItem]
    var color: Color?
    var showDate = true
    var body: some View {
        if !tasks.isEmpty {
            Section {
                // The heading is an ordinary row: macOS section headers float with their own background bar.
                if let title {
                    SectionHeader(title: title, count: tasks.count, color: color)
                        .padding(.top, 10)
                        .selectionDisabled()
                        .listRowSeparator(.hidden)
                }
                ForEach(tasks) { t in
                    TaskRow(task: t, showDate: showDate).tag(t.id)
                        .listRowSeparator(.hidden)
                }
            }
        }
    }
}

/// Standard task screen: title, quick add, grouped list, details on double-click.
struct TaskScreen<Sections: View>: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Navigator
    let title: String
    var subtitle: String?
    var quickAdd: QuickAddField?
    var isEmpty: Bool
    var empty: EmptyState
    @ViewBuilder var sections: Sections

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            VStack(alignment: .leading, spacing: 14) {
                ScreenTitle(title: title, subtitle: subtitle)
                if let quickAdd { quickAdd }
            }
            .padding(.horizontal, 28).padding(.top, 20).padding(.bottom, 8)
            if isEmpty {
                empty
            } else {
                List(selection: $nav.selectedTask) { sections }
                    .listStyle(.inset)
                    .scrollContentBackground(.hidden)
                    .environment(\.defaultMinListRowHeight, 34)
                    .contextMenu(forSelectionType: Int64.self) { _ in } primaryAction: { ids in
                        if let id = ids.first { nav.open(id) }
                    }
                    .onDeleteCommand { if let id = nav.selectedTask { deleteTask(store, nav, id) } }
                    .padding(.horizontal, 12)
            }
        }
        .frame(maxWidth: 860, maxHeight: .infinity, alignment: .topLeading)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .navigationTitle(title)
    }
}
