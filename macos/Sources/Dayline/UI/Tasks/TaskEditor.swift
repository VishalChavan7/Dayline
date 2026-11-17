import SwiftUI
import AppKit
import DaylineCore

/// Details for an existing task, edited live.
struct TaskInspector: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Navigator
    let taskId: Int64

    var body: some View {
        if let current = store.draft(for: taskId) {
            let binding = Binding<TaskDraft>(get: { store.draft(for: taskId) ?? current }, set: { store.save($0) })
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    HStack(alignment: .top, spacing: 10) {
                        Button { store.setCompleted(taskId, !current.task.isCompleted) } label: {
                            CheckCircle(done: current.task.isCompleted, color: current.task.priority.color, size: 22)
                        }.buttonStyle(.plain).padding(.top, 4)
                        TextField("Title", text: binding.task.title, axis: .vertical)
                            .font(.title3.weight(.semibold)).textFieldStyle(.plain)
                    }
                    TaskForm(draft: binding, isNew: false)
                    Divider()
                    HStack {
                        Button { FocusTimer.shared?.start(taskId: taskId); nav.go(.focus) } label: { Label("Focus", systemImage: "timer") }
                        Button { store.duplicate(taskId) } label: { Label("Duplicate", systemImage: "plus.square.on.square") }
                        Spacer()
                        Button(role: .destructive) { deleteTask(store, nav, taskId) } label: { Label("Delete", systemImage: "trash") }
                    }
                    .buttonStyle(.borderless)
                    Text("Created \(current.task.createdAt.formatted(date: .abbreviated, time: .shortened)) · Edited \(current.task.updatedAt.formatted(.relative(presentation: .named)))")
                        .font(.caption).foregroundStyle(.tertiary)
                }
                .padding(16)
            }
        }
    }
}

/// Sheet for a new task.
struct TaskEditorSheet: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Navigator
    @Environment(\.dismiss) var dismiss
    @State var draft: TaskDraft

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    TextField("What do you need to do?", text: $draft.task.title, axis: .vertical)
                        .font(.title2.weight(.semibold)).textFieldStyle(.plain)
                    TaskForm(draft: $draft, isNew: true)
                }
                .padding(20)
            }
            Divider()
            HStack {
                Spacer()
                Button("Cancel") { dismiss() }.keyboardShortcut(.cancelAction)
                Button("Add Task") {
                    let id = store.save(draft)
                    if id != 0 { nav.selectedTask = id; dismiss() }
                }
                .keyboardShortcut(.defaultAction)
                .disabled(draft.task.title.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            .padding(14)
        }
        .frame(width: 520, height: 640)
    }
}

struct TaskForm: View {
    @EnvironmentObject var store: Store
    @Binding var draft: TaskDraft
    let isNew: Bool
    @State private var newSubtask = ""
    @State private var newTag = ""
    @State private var newLink = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            TextField("Description", text: $draft.task.description, axis: .vertical)
                .lineLimit(1...6).textFieldStyle(.plain).foregroundStyle(.secondary)

            group("Schedule", "calendar") {
                DayField(label: "Due", day: $draft.task.dueDate, time: $draft.task.dueTime)
                DayField(label: "Start", day: $draft.task.startDate, time: $draft.task.startTime)
                HStack(spacing: 6) {
                    ForEach([("Today", 0), ("Tomorrow", 1), ("Next week", 7)], id: \.0) { label, days in
                        Button(label) { draft.task.dueDate = days == 7 ? DateRules.startOfWeek(store.today, store.weekStart).plusWeeks(1) : store.today.plusDays(days) }
                    }
                    Button("No date") { draft.task.dueDate = nil; draft.task.startDate = nil; draft.task.dueTime = nil; draft.task.startTime = nil }
                }
                .buttonStyle(.bordered).controlSize(.small)
            }

            group("Priority", "flag") {
                Picker("Priority", selection: $draft.task.priority) {
                    ForEach(Priority.allCases, id: \.self) { p in Text(p.label).tag(p) }
                }.pickerStyle(.segmented).labelsHidden()
            }

            group("Organize", "folder") {
                Picker("Project", selection: Binding(get: { draft.task.projectId ?? 0 }, set: { draft.task.projectId = $0 == 0 ? nil : $0 })) {
                    Text("None").tag(Int64(0))
                    ForEach(store.activeProjects) { p in Text(p.name).tag(p.id) }
                }
                Picker("List", selection: Binding(get: { draft.task.listId ?? 0 }, set: { draft.task.listId = $0 == 0 ? nil : $0 })) {
                    Text("None").tag(Int64(0))
                    ForEach(store.data.lists) { l in Text(l.name).tag(l.id) }
                }
                tagsEditor
            }

            group("Repeat", "repeat") { RepeatEditor(rule: $draft.task.recurrence) }

            group("Reminders", "bell") { RemindersEditor(reminders: $draft.reminders, hasDate: draft.task.scheduledDate != nil) }

            group("Subtasks", "checklist") {
                ForEach($draft.subtasks) { $s in
                    HStack {
                        Toggle("", isOn: $s.isDone).toggleStyle(.checkbox).labelsHidden()
                        TextField("Subtask", text: $s.title).textFieldStyle(.plain).strikethrough(s.isDone)
                        Button { draft.subtasks.removeAll { $0.id == s.id && $0.title == s.title } } label: { Image(systemName: "minus.circle") }
                            .buttonStyle(.borderless).foregroundStyle(.secondary)
                    }
                }
                HStack {
                    Image(systemName: "plus").foregroundStyle(.secondary)
                    TextField("Add a subtask", text: $newSubtask).textFieldStyle(.plain).onSubmit {
                        let t = newSubtask.trimmingCharacters(in: .whitespaces)
                        guard !t.isEmpty else { return }
                        let tempId = -Int64(draft.subtasks.count + 1) - Int64(Date().timeIntervalSince1970)
                        draft.subtasks.append(Subtask(id: isNew ? tempId : 0, taskId: draft.task.id, title: t, position: draft.subtasks.count))
                        newSubtask = ""
                    }
                }
                if draft.subtasks.isEmpty {
                    HStack {
                        Text("Progress").foregroundStyle(.secondary)
                        Slider(value: Binding(get: { Double(draft.task.progress) }, set: { draft.task.progress = Int(($0 / 5).rounded()) * 5 }), in: 0...100)
                        Text("\(draft.task.progress)%").monospacedDigit().frame(width: 40, alignment: .trailing)
                    }
                }
            }

            group("Depends on", "link") { dependencies }

            group("Links and files", "paperclip") { attachments }

            group("Notes", "note.text") {
                TextField("Notes", text: $draft.task.notes, axis: .vertical).lineLimit(3...12).textFieldStyle(.roundedBorder)
            }
        }
    }

    func group<C: View>(_ title: String, _ symbol: String, @ViewBuilder _ content: () -> C) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Label(title, systemImage: symbol).font(.subheadline.weight(.semibold)).foregroundStyle(.secondary)
            content()
        }
    }

    var tagsEditor: some View {
        HStack(alignment: .firstTextBaseline) {
            Text("Tags")
            FlowRow(spacing: 6) {
                ForEach(draft.tagNames, id: \.self) { t in
                    HStack(spacing: 3) {
                        Text("#" + t)
                        Button { draft.tagNames.removeAll { $0 == t } } label: { Image(systemName: "xmark").font(.caption2) }.buttonStyle(.borderless)
                    }
                    .padding(.horizontal, 8).padding(.vertical, 3)
                    .background(Capsule().fill(Color.accentColor.opacity(0.14)))
                }
                TextField("Add tag", text: $newTag).textFieldStyle(.plain).frame(width: 90).onSubmit {
                    let t = newTag.trimmingCharacters(in: .whitespaces).droppingPrefix("#")
                    if !t.isEmpty && !draft.tagNames.contains(where: { $0.caseInsensitiveCompare(t) == .orderedSame }) { draft.tagNames.append(t) }
                    newTag = ""
                }
            }
        }
    }

    var dependencies: some View {
        let others = store.openTasks.filter { $0.id != draft.task.id && !draft.dependsOnIds.contains($0.id) }.sorted(by: TaskGrouping.chronological)
        return VStack(alignment: .leading, spacing: 6) {
            ForEach(draft.dependsOnIds, id: \.self) { id in
                if let t = store.task(id) {
                    HStack {
                        Image(systemName: t.isCompleted ? "checkmark.circle.fill" : "circle").foregroundStyle(t.isCompleted ? StatusColor.success : .secondary)
                        Text(t.title)
                        Spacer()
                        Button { draft.dependsOnIds.removeAll { $0 == id } } label: { Image(systemName: "minus.circle") }.buttonStyle(.borderless)
                    }
                }
            }
            Menu("Add a task this one waits on") {
                ForEach(others.prefix(40)) { t in Button(t.title) { draft.dependsOnIds.append(t.id) } }
            }
            .fixedSize()
            .disabled(others.isEmpty)
        }
    }

    var attachments: some View {
        VStack(alignment: .leading, spacing: 6) {
            ForEach(Array(draft.attachments.enumerated()), id: \.offset) { i, a in
                HStack {
                    Image(systemName: a.kind == .LINK ? "link" : "doc")
                    if let url = URL(string: a.uri), a.uri.hasPrefix("http") || a.uri.hasPrefix("file:") {
                        Link(a.displayName, destination: url)
                    } else {
                        Text(a.displayName).help("This file is on your phone")
                    }
                    Spacer()
                    Button { draft.attachments.remove(at: i) } label: { Image(systemName: "minus.circle") }.buttonStyle(.borderless)
                }
            }
            HStack {
                TextField("Paste a link", text: $newLink).textFieldStyle(.roundedBorder).onSubmit(addLink)
                Button("Add File…") {
                    let panel = NSOpenPanel()
                    panel.allowsMultipleSelection = false
                    if panel.runModal() == .OK, let url = panel.url {
                        var a = Attachment(taskId: draft.task.id, kind: .FILE, uri: url.absoluteString, displayName: url.lastPathComponent)
                        a.sizeBytes = (try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize).map { Int64($0) }
                        a.addedAt = Date()
                        draft.attachments.append(a)
                    }
                }
            }
        }
    }

    func addLink() {
        var s = newLink.trimmingCharacters(in: .whitespaces)
        guard !s.isEmpty else { return }
        if !s.contains("://") { s = "https://" + s }
        var a = Attachment(taskId: draft.task.id, kind: .LINK, uri: s, displayName: URL(string: s)?.host ?? s)
        a.addedAt = Date()
        draft.attachments.append(a)
        newLink = ""
    }
}

/// Optional date + optional time.
struct DayField: View {
    let label: String
    @Binding var day: Day?
    @Binding var time: TimeOfDay?
    var allowsTime = true

    var body: some View {
        HStack {
            Toggle(label, isOn: Binding(get: { day != nil }, set: { day = $0 ? (day ?? Day.today()) : nil; if !$0 { time = nil } }))
                .toggleStyle(.checkbox).frame(width: 70, alignment: .leading)
            if let d = day {
                DatePicker("", selection: Binding(get: { d.date() }, set: { day = Day($0) }), displayedComponents: .date)
                    .labelsHidden().datePickerStyle(.field)
                if allowsTime {
                Toggle("at", isOn: Binding(get: { time != nil }, set: { time = $0 ? (time ?? TimeOfDay(hour: 9, minute: 0)) : nil }))
                    .toggleStyle(.checkbox)
                if let t = time {
                    DatePicker("", selection: Binding(get: { Day.today().date(at: t) }, set: { time = TimeOfDay($0) }), displayedComponents: .hourAndMinute)
                        .labelsHidden().datePickerStyle(.field)
                }
                }
            }
            Spacer()
        }
    }
}

struct RepeatEditor: View {
    @Binding var rule: RecurrenceRule?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Picker("Repeat", selection: Binding(get: { rule?.frequency.rawValue ?? "NONE" }, set: { v in
                if let f = RecurrenceFrequency(rawValue: v) { rule = RecurrenceRule(frequency: f, interval: rule?.interval ?? 1, weekdays: f == .WEEKLY ? rule?.weekdays ?? [] : [], fromCompletion: rule?.fromCompletion ?? false, endDate: rule?.endDate) }
                else { rule = nil }
            })) {
                Text("Never").tag("NONE")
                ForEach(RecurrenceFrequency.allCases, id: \.self) { f in Text(f.label).tag(f.rawValue) }
            }
            .labelsHidden()
            .fixedSize()
            if let r = rule {
                HStack {
                    Stepper("Every \(r.interval) \(r.frequency.unit)\(r.interval == 1 ? "" : "s")", value: Binding(get: { r.interval }, set: { rule?.interval = max(1, $0) }), in: 1...99)
                }
                if r.frequency == .WEEKLY {
                    HStack(spacing: 4) {
                        ForEach(Weekday.allCases, id: \.self) { d in
                            let on = r.weekdays.contains(d)
                            Button(d.short.prefix(2)) { if on { rule?.weekdays.remove(d) } else { rule?.weekdays.insert(d) } }
                                .buttonStyle(.plain)
                                .frame(width: 32, height: 24)
                                .background(RoundedRectangle(cornerRadius: 6).fill(on ? Color.accentColor : Color.primary.opacity(0.07)))
                                .foregroundStyle(on ? .white : .primary)
                        }
                    }
                }
                Toggle("Count from when I complete it", isOn: Binding(get: { r.fromCompletion }, set: { rule?.fromCompletion = $0 }))
                DayField(label: "Ends", day: Binding(get: { r.endDate }, set: { rule?.endDate = $0 }), time: .constant(nil), allowsTime: false)
                Text(r.describe()).font(.caption).foregroundStyle(.secondary)
            }
        }
    }
}

struct RemindersEditor: View {
    @EnvironmentObject var store: Store
    @Binding var reminders: [Reminder]
    let hasDate: Bool
    @State private var customAt = Date().addingTimeInterval(3600)

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            ForEach(Array(reminders.enumerated()), id: \.offset) { i, r in
                HStack {
                    Image(systemName: r.type == .CUSTOM ? "alarm" : "bell")
                    Text(describe(r))
                    Spacer()
                    Button { reminders.remove(at: i) } label: { Image(systemName: "minus.circle") }.buttonStyle(.borderless)
                }
            }
            Menu("Add Reminder Before Due") {
                    ForEach(ReminderPlanner.standardOffsets, id: \.self) { m in
                        Button(ReminderPlanner.describeOffset(m) + " due") { reminders.append(Reminder(id: 0, taskId: nil, type: .DEADLINE, offsetMinutes: m)) }
                            .disabled(!hasDate)
                    }
            }
            .fixedSize()
            HStack {
                Text("Or at").foregroundStyle(.secondary)
                DatePicker("", selection: $customAt).labelsHidden().datePickerStyle(.field)
                Button("Add") { reminders.append(Reminder(id: 0, taskId: nil, type: .CUSTOM, at: DayTime(customAt))) }
            }
            if !hasDate { Text("Give the task a date to add reminders before it's due.").font(.caption).foregroundStyle(.secondary) }
        }
    }

    func describe(_ r: Reminder) -> String {
        switch r.type {
        case .CUSTOM: return r.at.map { $0.date.formatted(date: .abbreviated, time: .shortened) } ?? "Custom"
        case .TASK: return ReminderPlanner.describeOffset(r.offsetMinutes) + " start"
        case .DEADLINE: return ReminderPlanner.describeOffset(r.offsetMinutes) + " due"
        }
    }
}

struct ProjectEditor: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Navigator
    @Environment(\.dismiss) var dismiss
    @State var project: Project

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(project.id == 0 ? "New Project" : "Edit Project").font(.title2.weight(.semibold))
            HStack(spacing: 12) {
                ColorBadge(color: project.color, symbol: Icons.symbol(project.icon), size: 36)
                TextField("Name", text: $project.name).textFieldStyle(.roundedBorder).font(.title3)
            }
            TextField("Description", text: $project.description, axis: .vertical).textFieldStyle(.roundedBorder).lineLimit(2...4)
            ColorPickerRow(color: $project.color)
            IconPickerGrid(icon: $project.icon, color: project.color)
            DayField(label: "Start", day: $project.startDate, time: .constant(nil), allowsTime: false)
            DayField(label: "Deadline", day: $project.deadline, time: .constant(nil), allowsTime: false)
            HStack {
                Spacer()
                Button("Cancel") { dismiss() }.keyboardShortcut(.cancelAction)
                Button(project.id == 0 ? "Create" : "Save") {
                    let id = store.save(project)
                    if id != 0 { if project.id == 0 { nav.go(.project(id)) }; dismiss() }
                }
                .keyboardShortcut(.defaultAction)
                .disabled(project.name.trimmingCharacters(in: .whitespaces).isEmpty)
            }
        }
        .padding(20)
        .frame(width: 440)
    }
}

struct ListEditor: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Navigator
    @Environment(\.dismiss) var dismiss
    @State var list: TaskList

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(list.id == 0 ? "New List" : "Edit List").font(.title2.weight(.semibold))
            TextField("Name", text: $list.name).textFieldStyle(.roundedBorder).font(.title3)
            ColorPickerRow(color: $list.color)
            Picker("Project", selection: Binding(get: { list.projectId ?? 0 }, set: { list.projectId = $0 == 0 ? nil : $0 })) {
                Text("None").tag(Int64(0))
                ForEach(store.activeProjects) { p in Text(p.name).tag(p.id) }
            }
            HStack {
                Spacer()
                Button("Cancel") { dismiss() }.keyboardShortcut(.cancelAction)
                Button(list.id == 0 ? "Create" : "Save") {
                    let id = store.save(list)
                    if id != 0 { if list.id == 0 { nav.go(.list(id)) }; dismiss() }
                }
                .keyboardShortcut(.defaultAction)
                .disabled(list.name.trimmingCharacters(in: .whitespaces).isEmpty)
            }
        }
        .padding(20)
        .frame(width: 400)
    }
}
