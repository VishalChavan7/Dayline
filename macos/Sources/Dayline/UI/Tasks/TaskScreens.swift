import SwiftUI
import DaylineCore

struct UpcomingView: View {
    @EnvironmentObject var store: Store
    var body: some View {
        let today = store.today
        let open = store.openTasks
        let overdue = open.filter { $0.isOverdue(today) }.sorted(by: TaskGrouping.chronological)
        let todays = TaskGrouping.onDay(open, today).filter { !$0.isOverdue(today) }.sorted(by: TaskGrouping.chronological)
        var buckets: [UpcomingBucket: [TaskItem]] = [:]
        for t in open { if let d = t.scheduledDate, let b = TaskGrouping.bucket(d, today: today, weekStart: store.weekStart) { buckets[b, default: []].append(t) } }
        let isEmpty = overdue.isEmpty && todays.isEmpty && buckets.isEmpty
        return TaskScreen(title: "Upcoming", subtitle: "Everything with a date", quickAdd: QuickAddField(date: today.plusDays(1), placeholder: "Add a task for tomorrow — or type a day"),
                          isEmpty: isEmpty, empty: EmptyState(symbol: "calendar.badge.clock", title: "Nothing planned", message: "Tasks with a date show up here, grouped by when they're due.")) {
            EmptyView()
        } sections: {
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
        TaskScreen(title: "Inbox", subtitle: "Tasks without a date, project or list", quickAdd: QuickAddField(),
                   isEmpty: tasks.isEmpty, empty: EmptyState(symbol: "tray", title: "Inbox zero", message: "Capture anything here and sort it out later.")) {
            EmptyView()
        } sections: {
            TaskSection(title: "Tasks", tasks: tasks)
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
        TaskScreen(title: "Completed", subtitle: "\(done.count) done", quickAdd: nil,
                   isEmpty: done.isEmpty, empty: EmptyState(symbol: "checkmark.circle", title: "Nothing completed yet", message: "Finished tasks are kept here.")) {
            EmptyView()
        } sections: {
            ForEach(days.prefix(60), id: \.self) { d in
                TaskSection(title: d.epochDay == 0 ? "Earlier" : Formats(store: store).date(d), tasks: groups[d] ?? [])
            }
        }
    }
}

struct ProjectView: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Navigator
    let projectId: Int64
    @State private var sort: TaskSort = .manual
    @State private var showDone = false
    @State private var confirmDelete = false

    var body: some View {
        if let p = store.project(projectId) {
            let all = store.data.tasks.filter { $0.projectId == p.id }
            let open = TaskGrouping.sort(all.filter { !$0.isCompleted }, sort)
            let lists = store.data.lists.filter { $0.projectId == p.id }
            let done = all.filter(\.isCompleted)
            TaskScreen(title: p.name, subtitle: p.description, quickAdd: QuickAddField(projectId: p.id, placeholder: "Add a task to \(p.name)"),
                       isEmpty: all.isEmpty, empty: EmptyState(symbol: Icons.symbol(p.icon), title: "No tasks yet", message: "Add the first task for this project above.")) {
                ProjectHeader(project: p, done: done.count, total: all.count)
            } sections: {
                TaskSection(title: "Tasks", tasks: open.filter { t in t.listId == nil || !lists.contains { $0.id == t.listId } }, showProject: false)
                ForEach(lists) { l in TaskSection(title: l.name, tasks: open.filter { $0.listId == l.id }, showProject: false) }
                if showDone { TaskSection(title: "Completed", tasks: done, showProject: false) }
            }
            .toolbar {
                SortPicker(sort: $sort)
                Toggle(isOn: $showDone) { Label("Show Completed", systemImage: "checkmark.circle") }.help("Show completed tasks")
                Menu {
                    Button("Edit Project…") { nav.editingProject = p }
                    Button("New List in Project…") { var l = TaskList(id: 0, name: "", color: p.color); l.projectId = p.id; nav.editingList = l }
                    Button(p.isArchived ? "Unarchive" : "Archive") { var q = p; q.isArchived.toggle(); store.save(q) }
                    Divider()
                    Button("Delete Project…", role: .destructive) { confirmDelete = true }
                } label: { Label("Project", systemImage: "ellipsis.circle") }
            }
            .confirmationDialog("Delete “\(p.name)”?", isPresented: $confirmDelete) {
                Button("Delete Project and Its Tasks", role: .destructive) { store.deleteProject(p.id, deleteTasks: true); nav.go(.today) }
                Button("Delete Project, Keep Tasks in Inbox") { store.deleteProject(p.id, deleteTasks: false); nav.go(.inbox) }
            } message: { Text("This can't be undone.") }
        } else {
            EmptyState(symbol: "folder", title: "Project not found")
        }
    }
}

struct ProjectHeader: View {
    @EnvironmentObject var store: Store
    let project: Project
    let done: Int
    let total: Int
    var body: some View {
        HStack(spacing: 14) {
            ColorBadge(color: project.color, symbol: Icons.symbol(project.icon), size: 40)
            VStack(alignment: .leading, spacing: 4) {
                Text(project.name).font(.title2.weight(.semibold))
                HStack(spacing: 12) {
                    Text("\(done) of \(total) done").foregroundStyle(.secondary)
                    if let d = project.deadline {
                        Label("Due \(Formats(store: store).date(d, relative: false))", systemImage: "flag")
                            .foregroundStyle(d < store.today ? StatusColor.overdue : .secondary)
                    }
                }.font(.callout)
            }
            Spacer()
            if total > 0 {
                Ring(progress: Double(done) / Double(total), lineWidth: 6, color: Color(argb: project.color)).frame(width: 36, height: 36)
            }
        }
    }
}

struct ListView: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Navigator
    let listId: Int64
    @State private var sort: TaskSort = .manual
    var body: some View {
        if let l = store.list(listId) {
            let tasks = TaskGrouping.sort(store.openTasks.filter { $0.listId == l.id }, sort)
            TaskScreen(title: l.name, subtitle: store.project(l.projectId)?.name, quickAdd: QuickAddField(projectId: l.projectId, listId: l.id, placeholder: "Add to \(l.name)"),
                       isEmpty: tasks.isEmpty, empty: EmptyState(symbol: "list.bullet", title: "This list is empty")) {
                EmptyView()
            } sections: {
                TaskSection(title: "Tasks", tasks: tasks)
            }
            .toolbar {
                SortPicker(sort: $sort)
                Button { nav.editingList = l } label: { Label("Edit List", systemImage: "pencil") }
            }
        } else {
            EmptyState(symbol: "list.bullet", title: "List not found")
        }
    }
}

struct TagView: View {
    @EnvironmentObject var store: Store
    let tagId: Int64
    @State private var renaming = false
    @State private var newName = ""
    var body: some View {
        let tag = store.data.tags.first { $0.id == tagId }
        let ids = Set(store.data.taskTags.filter { $0.tagId == tagId }.map(\.taskId))
        let tasks = store.data.tasks.filter { ids.contains($0.id) }
        TaskScreen(title: "#" + (tag?.name ?? ""), subtitle: nil, quickAdd: QuickAddField(placeholder: "Add a task tagged #\(tag?.name ?? "")"),
                   isEmpty: tasks.isEmpty, empty: EmptyState(symbol: "number", title: "No tasks with this tag")) {
            EmptyView()
        } sections: {
            TaskSection(title: "Open", tasks: tasks.filter { !$0.isCompleted }.sorted(by: TaskGrouping.chronological))
            TaskSection(title: "Completed", tasks: tasks.filter(\.isCompleted))
        }
        .toolbar { Button { newName = tag?.name ?? ""; renaming = true } label: { Label("Rename Tag", systemImage: "pencil") } }
        .alert("Rename tag", isPresented: $renaming) {
            TextField("Name", text: $newName)
            Button("Rename") { store.renameTag(tagId, newName) }
            Button("Cancel", role: .cancel) {}
        }
    }
}

struct SearchView: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Navigator
    @EnvironmentObject var notesUI: NotesUI
    let query: String
    var body: some View {
        let r = store.search(query)
        let empty = r.tasks.isEmpty && r.projects.isEmpty && r.lists.isEmpty && r.tags.isEmpty && r.notes.isEmpty
        TaskScreen(title: "Search", subtitle: "“\(query)”", quickAdd: nil, isEmpty: empty,
                   empty: EmptyState(symbol: "magnifyingglass", title: "No results", message: "Nothing matches “\(query)”.")) {
            EmptyView()
        } sections: {
            if !r.projects.isEmpty || !r.lists.isEmpty || !r.tags.isEmpty {
                Section {
                    ForEach(r.projects) { p in
                        Button { nav.go(.project(p.id)) } label: { Label { Text(p.name) } icon: { ColorBadge(color: p.color, symbol: Icons.symbol(p.icon), size: 18) } }.buttonStyle(.plain)
                    }
                    ForEach(r.lists) { l in Button { nav.go(.list(l.id)) } label: { Label(l.name, systemImage: "list.bullet") }.buttonStyle(.plain) }
                    ForEach(r.tags) { t in Button { nav.go(.tag(t.id)) } label: { Label(t.name, systemImage: "number") }.buttonStyle(.plain) }
                } header: { SectionHeader(title: "Projects, lists and tags") }
            }
            TaskSection(title: "Tasks", tasks: r.tasks)
            if !r.notes.isEmpty {
                Section {
                    ForEach(r.notes) { n in
                        Button { nav.go(.notes); notesUI.editing = n.id } label: {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(n.title.isEmpty ? "Untitled note" : n.title).fontWeight(.medium)
                                Text(NoteCodec.plainText(n.body)).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                            }
                        }.buttonStyle(.plain)
                    }
                } header: { SectionHeader(title: "Notes", count: r.notes.count) }
            }
        }
    }
}
