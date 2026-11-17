import SwiftUI
import DaylineCore

private func countText(_ n: Int) -> String { "\(n) task\(n == 1 ? "" : "s")" }

struct UpcomingView: View {
    @EnvironmentObject var store: Store
    var body: some View {
        let today = store.today
        let open = store.openTasks
        let overdue = open.filter { $0.isOverdue(today) }.sorted(by: TaskGrouping.chronological)
        let todays = TaskGrouping.onDay(open, today).filter { !$0.isOverdue(today) }.sorted(by: TaskGrouping.chronological)
        var buckets: [UpcomingBucket: [TaskItem]] = [:]
        for t in open { if let d = t.scheduledDate, let b = TaskGrouping.bucket(d, today: today, weekStart: store.weekStart) { buckets[b, default: []].append(t) } }
        let total = overdue.count + todays.count + buckets.values.reduce(0) { $0 + $1.count }
        return TaskScreen(title: "Upcoming", subtitle: countText(total), quickAdd: QuickAddField(date: today.plusDays(1), placeholder: "Add a task for tomorrow — or type a day"),
                          isEmpty: total == 0, empty: EmptyState(symbol: "calendar.badge.clock", title: "Nothing planned", message: "Tasks with a date show up here, grouped by when they're due.")) {
            TaskSection(title: "Overdue", tasks: overdue, color: StatusColor.overdue)
            TaskSection(title: "Today", tasks: todays)
            ForEach(UpcomingBucket.allCases, id: \.self) { b in
                TaskSection(title: b.label, tasks: (buckets[b] ?? []).sorted(by: TaskGrouping.chronological))
            }
        }
    }
}

struct InboxView: View {
    @EnvironmentObject var store: Store
    @State private var sort: TaskSort = .manual
    var body: some View {
        let tasks = TaskGrouping.sort(store.openTasks.filter { $0.scheduledDate == nil && $0.projectId == nil && $0.listId == nil }, sort)
        TaskScreen(title: "Inbox", subtitle: countText(tasks.count), quickAdd: QuickAddField(),
                   isEmpty: tasks.isEmpty, empty: EmptyState(symbol: "tray", title: "Inbox zero", message: "Ideas without a date land here. Sort them out later.")) {
            TaskSection(title: nil, tasks: tasks)
        }
        .toolbar { SortPicker(sort: $sort) }
    }
}

struct AllTasksView: View {
    @EnvironmentObject var store: Store
    @State private var sort: TaskSort = .dueDate
    var body: some View {
        let today = store.today
        let open = TaskGrouping.sort(store.openTasks, sort)
        TaskScreen(title: "All Tasks", subtitle: countText(open.count), quickAdd: QuickAddField(),
                   isEmpty: open.isEmpty, empty: EmptyState(symbol: "checkmark.circle", title: "No open tasks", message: "Everything's done. Add a task above.")) {
            if sort == .dueDate {
                TaskSection(title: "Overdue", tasks: open.filter { $0.isOverdue(today) }, color: StatusColor.overdue)
                TaskSection(title: "Scheduled", tasks: open.filter { !$0.isOverdue(today) && $0.scheduledDate != nil })
                TaskSection(title: "No date", tasks: open.filter { $0.scheduledDate == nil })
            } else {
                TaskSection(title: nil, tasks: open)
            }
        }
        .toolbar { SortPicker(sort: $sort) }
    }
}

struct SortPicker: View {
    @Binding var sort: TaskSort
    var body: some View {
        Menu {
            Picker("Sort by", selection: $sort) { ForEach(TaskSort.allCases, id: \.self) { Text($0.label).tag($0) } }.pickerStyle(.inline)
        } label: { Label("Sort", systemImage: "arrow.up.arrow.down") }
        .help("Sort tasks")
    }
}

struct CompletedView: View {
    @EnvironmentObject var store: Store
    var body: some View {
        let done = store.data.tasks.filter(\.isCompleted).sorted { ($0.completedAt ?? .distantPast) > ($1.completedAt ?? .distantPast) }
        let groups = Dictionary(grouping: done) { $0.completedAt.map { Day($0) } ?? Day(epochDay: 0) }
        let days = groups.keys.sorted(by: >)
        TaskScreen(title: "Completed", subtitle: "\(done.count) completed", quickAdd: nil,
                   isEmpty: done.isEmpty, empty: EmptyState(symbol: "checkmark.circle", title: "Nothing completed yet", message: "Finished tasks are kept here.")) {
            ForEach(days.prefix(60), id: \.self) { d in
                TaskSection(title: d.epochDay == 0 ? "Earlier" : Formats(store: store).date(d), tasks: groups[d] ?? [], showDate: false)
            }
        }
    }
}

struct SearchView: View {
    @EnvironmentObject var services: Services
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Navigator
    @EnvironmentObject var notesUI: NotesUI
    let query: String
    var body: some View {
        let r = store.search(query)
        TaskScreen(title: "Search", subtitle: "“\(query)”", quickAdd: nil, isEmpty: r.tasks.isEmpty && r.notes.isEmpty,
                   empty: EmptyState(symbol: "magnifyingglass", title: "No results", message: "Nothing matches “\(query)”.")) {
            TaskSection(title: "Tasks", tasks: r.tasks)
            if !r.notes.isEmpty {
                Section {
                    SectionHeader(title: "Notes", count: r.notes.count).padding(.horizontal, 10).padding(.top, 14).padding(.bottom, 6).taskListRow()
                    ForEach(r.notes) { n in
                        Button {
                            if store.isJournal(n) { nav.go(.journal); services.journalUI.open(n.id) } else { nav.go(.notes); notesUI.open(n.id) }
                        } label: {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(n.title.isEmpty ? "Untitled note" : n.title).fontWeight(.medium)
                                Text(NoteCodec.plainText(n.body)).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                            }
                            .padding(.horizontal, 10).padding(.vertical, 6)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .taskListRow()
                    }
                }
            }
        }
    }
}
