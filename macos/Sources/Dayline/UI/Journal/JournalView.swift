import SwiftUI
import DaylineCore

/// Writing prompts, in the same order as the phone, so both show the same prompt each day.
enum JournalPrompts {
    static let all = [
        "What made you smile today?",
        "Describe a small win from this week.",
        "What's on your mind right now?",
        "Who are you grateful for, and why?",
        "What would make tomorrow great?",
        "Write about a place that feels like home.",
        "What did you learn today?",
        "What are you looking forward to?",
        "Describe today in three words, then explain.",
        "What drained your energy today, and what gave it back?",
        "What's a moment you'd like to remember from this week?",
        "What would you tell yourself a year ago?",
    ]
    static func forDay(_ day: Day, offset: Int = 0) -> Int {
        let n = all.count
        return ((day.epochDay + offset) % n + n) % n
    }
}

/// Which journal entry is open, and the prompt a new one starts with.
@MainActor
final class JournalUI: ObservableObject {
    @Published var prompt: String?
}

/// Journal, inspired by Apple's Journal app: insights, a daily prompt and your entries by month,
/// with the entry open beside them. Entries are notes labelled "journal", so they sync with the phone.
struct JournalView: View {
    @ObservedObject var ui: NotesUI
    @EnvironmentObject var store: Store
    @EnvironmentObject var services: Services
    @Environment(\.scheme) private var scheme
    @State private var promptOffset = 0
    @State private var bookmarkedOnly = false

    var body: some View {
        GeometryReader { geo in
            let columns = shown(width: geo.size.width)
            HStack(spacing: 0) {
                timeline(ui)
                    .frame(width: 380)
                    .slidingPane(width: 380, shown: columns >= 2)
                JournalEditorHost(ui: ui, shownColumns: columns)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            .background(SwipeCatcher { leftward in
                if leftward { ui.collapse(services.nav, shown: columns) } else { ui.expand(services.nav) }
            })
            .onAppear { ui.capacity = capacity(width: geo.size.width) }
            .onChange(of: capacity(width: geo.size.width)) { _, c in ui.capacity = c }
        }
        .navigationTitle("Journal")
    }

    /// 2 = timeline and entry, 1 = the entry alone (chosen, or when the window is too narrow for both).
    func shown(width: CGFloat) -> Int { min(ui.layout, capacity(width: width)) }
    /// Columns there's room for at this width.
    func capacity(width: CGFloat) -> Int { width - 380 >= 400 ? 2 : 1 }

    func newEntry(_ ui: NotesUI, prompt: String? = nil) {
        services.journal.prompt = prompt
        ui.newNote()
    }

    func timeline(_ ui: NotesUI) -> some View {
        let all = store.journalEntries
        let entries = bookmarkedOnly ? all.filter(\.isPinned) : all
        let today = store.today
        let days = Set(all.map { Day($0.createdAt) })
        var streak = 0
        var d = days.contains(today) ? today : today.minusDays(1)
        while days.contains(d) { streak += 1; d = d.minusDays(1) }
        let thisYear = all.filter { Day($0.createdAt).year == today.year }.count
        let words = all.reduce(0) { $0 + NoteCodec.plainText($1.body).split(whereSeparator: \.isWhitespace).count }
        let prompt = JournalPrompts.forDay(today, offset: promptOffset)
        let months = Dictionary(grouping: entries) { Day($0.createdAt).firstOfMonth }
        return ScrollView {
            LazyVStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .bottom) {
                    ScreenTitle(title: "Journal", subtitle: "\(all.count) entr\(all.count == 1 ? "y" : "ies")")
                    Spacer()
                    IconButton(symbol: "square.and.pencil", help: "New entry (⇧⌘J)", size: 30) { newEntry(ui) }
                }
                .padding(.bottom, 4)
                HStack(spacing: 8) {
                    insight("flame.fill", "\(streak)", "day streak", StatusColor.streak)
                    insight("book.pages", "\(thisYear)", "this year", scheme.primary)
                    insight("textformat.size", words < 1000 ? "\(words)" : String(format: "%.1fk", Double(words) / 1000), "words", scheme.tertiary)
                }
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Label("Today's prompt", systemImage: "sparkles").font(.system(size: 12, weight: .semibold))
                        Spacer()
                        Button { promptOffset += 1 } label: { Image(systemName: "shuffle") }.buttonStyle(.plain).help("Another prompt")
                    }
                    Text(JournalPrompts.all[prompt]).font(.system(size: 16, weight: .semibold)).fixedSize(horizontal: false, vertical: true)
                    Button { newEntry(ui, prompt: JournalPrompts.all[prompt]) } label: { Label("Start writing", systemImage: "pencil") }
                        .buttonStyle(.filled).padding(.top, 2)
                }
                .foregroundStyle(scheme.onTertiaryContainer)
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).fill(scheme.tertiaryContainer))
                HStack(spacing: 6) {
                    Chip(text: "All entries", on: !bookmarkedOnly) { bookmarkedOnly = false }
                    Chip(text: "Bookmarked", symbol: "bookmark.fill", on: bookmarkedOnly) { bookmarkedOnly = true }
                }
                .padding(.top, 2)
                if entries.isEmpty {
                    Text(bookmarkedOnly ? "No bookmarked entries yet." : "No entries yet. Write a few lines about your day.")
                        .font(.callout).foregroundStyle(.secondary).padding(.top, 12)
                }
                ForEach(months.keys.sorted(by: >), id: \.self) { m in
                    Text(m.date().formatted(m.year == today.year ? .dateTime.month(.wide) : .dateTime.month(.wide).year()))
                        .font(.system(size: 13, weight: .semibold)).foregroundStyle(scheme.primary)
                        .padding(.top, 10).padding(.leading, 2)
                    ForEach(months[m] ?? []) { n in
                        JournalCard(note: n, selected: ui.selected == n.id)
                            .onTapGesture { ui.open(n.id) }
                            .contextMenu {
                                Button(n.isPinned ? "Remove Bookmark" : "Bookmark") { store.setPinned(note: n.id, !n.isPinned) }
                                Divider()
                                Button("Delete Entry", role: .destructive) { if ui.selected == n.id { ui.selected = nil }; store.deleteNote(n.id) }
                            }
                    }
                }
            }
            .padding(.leading, Page.h).padding(.trailing, 16).padding(.top, Page.top).padding(.bottom, 24)
        }
    }

    func insight(_ symbol: String, _ value: String, _ label: String, _ color: Color) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Image(systemName: symbol).font(.system(size: 13, weight: .semibold)).foregroundStyle(color)
            Text(value).font(.system(size: 18, weight: .semibold).monospacedDigit()).padding(.top, 2)
            Text(label).font(.system(size: 11)).foregroundStyle(.secondary).lineLimit(1)
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .surface()
    }
}

/// The open entry (or a friendly empty page), with its own selection so Notes isn't disturbed.
private struct JournalEditorHost: View {
    @ObservedObject var ui: NotesUI
    var shownColumns: Int
    @EnvironmentObject var services: Services
    @EnvironmentObject var store: Store
    var body: some View {
        if let id = ui.selected {
            NotePane(noteId: id, presetLabel: Store.journalLabel, shownColumns: shownColumns, journal: true, promptTitle: services.journal.prompt)
                .id(ui.token)
                .environmentObject(ui)
        } else {
            EmptyState(art: .journal, title: store.journalEntries.isEmpty ? "Start your journal" : "Pick an entry",
                       message: "Capture moments, reflect on your day and look back later. Entries sync with Dayline on your phone.",
                       action: ("New Entry", { services.journal.prompt = nil; ui.newNote() }))
        }
    }
}

/// An entry in the timeline: a date block, the title and a few lines.
struct JournalCard: View {
    @Environment(\.scheme) private var scheme
    let note: Note
    let selected: Bool
    var body: some View {
        let text = NoteCodec.plainText(note.body)
        let lines = text.split(separator: "\n").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        let title = note.title.isEmpty ? (lines.first ?? "Untitled entry") : note.title
        let preview = (note.title.isEmpty ? Array(lines.dropFirst()) : lines).joined(separator: " ")
        let words = text.split(whereSeparator: \.isWhitespace).count
        HStack(alignment: .top, spacing: 12) {
            VStack(spacing: 0) {
                Text(note.createdAt.formatted(.dateTime.weekday(.abbreviated)).uppercased()).font(.system(size: 9.5, weight: .semibold))
                Text(note.createdAt.formatted(.dateTime.day())).font(.system(size: 19, weight: .semibold).monospacedDigit())
            }
            .foregroundStyle(scheme.onPrimaryContainer)
            .frame(width: 42, height: 46)
            .background(RoundedRectangle(cornerRadius: Radius.inner, style: .continuous).fill(scheme.primaryContainer))
            VStack(alignment: .leading, spacing: 3) {
                HStack(alignment: .top) {
                    Text(title).font(.system(size: 13.5, weight: .semibold)).lineLimit(2)
                    Spacer(minLength: 4)
                    if note.isPinned { Image(systemName: "bookmark.fill").font(.system(size: 10)).foregroundStyle(scheme.primary) }
                }
                if !preview.isEmpty { Text(preview).font(.system(size: 12.5)).foregroundStyle(.secondary).lineLimit(3) }
                Text("\(words) word\(words == 1 ? "" : "s")").font(.system(size: 10.5)).foregroundStyle(.tertiary)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .surface()
        .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).strokeBorder(selected ? scheme.accent : .clear, lineWidth: 2))
        .contentShape(Rectangle())
    }
}
