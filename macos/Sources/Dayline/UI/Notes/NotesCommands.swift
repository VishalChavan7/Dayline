import SwiftUI
import AppKit
import DaylineCore
import DaylineEditor

/// Menu bar commands for notes: File (import, export, print), Edit, View (lists, layout, panels) and Format.
struct NotesCommands: Commands {
    let services: Services

    var ui: NotesUI { services.notesUI }
    var hub: EditorHub { EditorHub.shared }
    /// Notes or Journal, whichever is on screen.
    var paneUI: NotesUI? {
        switch services.nav.destination { case .notes: ui; case .journal: services.journalUI; default: nil }
    }

    var body: some Commands {
        CommandGroup(replacing: .importExport) {
            Button("Import Notes…") { importNotes() }
            Menu("Export Note") {
                ForEach(NoteExport.Format.allCases) { f in
                    Button(f.title + "…") { withNote { NoteExport.export($0, as: f, store: services.store) } }
                }
            }
        }
        CommandGroup(replacing: .printItem) {
            Button("Print Note…") { withNote { NoteExport.print($0) } }.keyboardShortcut("p")
        }
        CommandGroup(after: .pasteboard) {
            Button("Copy As Markdown") { withNote { NoteExport.copyMarkdown($0) } }.keyboardShortcut("c", modifiers: [.command, .option, .shift])
            Divider()
            Button("Move Line Up") { hub.run { $0.moveLines(up: true) } }.keyboardShortcut(.upArrow, modifiers: [.command, .option])
            Button("Move Line Down") { hub.run { $0.moveLines(up: false) } }.keyboardShortcut(.downArrow, modifiers: [.command, .option])
            Button("Shift Right") { hub.run { $0.indent(1) } }.keyboardShortcut("]")
            Button("Shift Left") { hub.run { $0.indent(-1) } }.keyboardShortcut("[")
            Divider()
            Button("Move Note to Trash") { withNote(unlocked: false) { services.store.trashNote($0.id) } }
            Button("Restore Note") { withNote(unlocked: false) { services.store.restoreNote($0.id) } }
        }
        CommandGroup(before: .toolbar) {
            ForEach(NotesFilter.views, id: \.self) { f in
                Button("Show \(f == .all ? "All Notes" : f.title)") { services.nav.go(.notes); ui.filter = f }
                    .keyboardShortcut(KeyEquivalent(f.key), modifiers: [.command, .option])
            }
            Divider()
            Button("Quick Open Note…") { services.nav.go(.notes); ui.quickOpen = true }.keyboardShortcut("o")
            Button("Back") { ui.goBack { services.store.note($0) != nil } }.keyboardShortcut("[", modifiers: [.command, .option])
            Button("Forward") { ui.goForward { services.store.note($0) != nil } }.keyboardShortcut("]", modifiers: [.command, .option])
            Divider()
            Button("Zoom In") { ui.zoom = min(2.5, (ui.zoom + 0.1).rounded(toPlaces: 1)) }.keyboardShortcut("=")
            Button("Zoom Out") { ui.zoom = max(0.6, (ui.zoom - 0.1).rounded(toPlaces: 1)) }.keyboardShortcut("-")
            Button("Actual Size") { ui.zoom = 1 }.keyboardShortcut("0")
            Divider()
            Button("Show Editor Only") { services.nav.go(.notes); ui.layout = 1 }.keyboardShortcut("1", modifiers: .control)
            Button("Show Notes and Editor") { services.nav.go(.notes); ui.layout = 2 }.keyboardShortcut("2", modifiers: .control)
            Button("Show Tags, Notes and Editor") { services.nav.go(.notes); ui.layout = 3 }.keyboardShortcut("3", modifiers: .control)
            Button("Hide a Pane") { if let p = paneUI { p.collapse(services.nav) } else { NSSound.beep() } }
                .keyboardShortcut(.leftArrow, modifiers: [.command, .control])
            Button("Show a Pane") { if let p = paneUI { p.expand(services.nav) } else { NSSound.beep() } }
                .keyboardShortcut(.rightArrow, modifiers: [.command, .control])
            Divider()
            Button("Statistics") { ui.togglePanel(.info) }.keyboardShortcut("i", modifiers: [.command, .shift])
            Button("Table of Contents") { ui.togglePanel(.contents) }.keyboardShortcut("a", modifiers: [.command, .shift])
            Button("Backlinks") { ui.togglePanel(.backlinks) }.keyboardShortcut("b", modifiers: [.command, .shift])
            Menu("Sort Notes By") {
                ForEach(NoteSort.allCases, id: \.self) { s in Button(s.title) { ui.sort = s } }
                Divider()
                Button(ui.pinnedFirst ? "Don't Keep Pinned Notes First" : "Keep Pinned Notes First") { ui.pinnedFirst.toggle() }
            }
            Menu("Sort Tags By") {
                ForEach(TagSort.allCases, id: \.self) { s in Button(s.title) { ui.tagSort = s } }
            }
            Divider()
        }
        CommandMenu("Format") {
            Menu("Headings") {
                Button("Body Text") { hub.run { $0.setHeading(0) } }.keyboardShortcut("0", modifiers: [.command, .control])
                ForEach(1...6, id: \.self) { l in
                    Button("Heading \(l)") { hub.run { $0.setHeading(l) } }.keyboardShortcut(KeyEquivalent(Character("\(l)")), modifiers: [.command, .control])
                }
            }
            Divider()
            Button("Bold") { hub.run(needsFocus: true) { $0.toggleTrait(.boldFontMask) } }.keyboardShortcut("b")
            Button("Italic") { hub.run(needsFocus: true) { $0.toggleTrait(.italicFontMask) } }.keyboardShortcut("i")
            Button("Underline") { hub.run(needsFocus: true) { $0.toggleLine(.underlineStyle) } }.keyboardShortcut("u")
            Button("Strikethrough") { hub.run(needsFocus: true) { $0.toggleLine(.strikethroughStyle) } }.keyboardShortcut("e", modifiers: [.command, .shift])
            Button("Highlight") {
                hub.run(needsFocus: true) { $0.setHighlight($0.formatState.highlight == nil ? NoteStyle.highlightColors[0] : nil) }
            }.keyboardShortcut("h", modifiers: [.command, .control])
            Menu("Text Colour") {
                Button("Default") { hub.run { $0.setTextColor(nil) } }
                ForEach(Array(zip(["Red", "Orange", "Yellow", "Green", "Blue", "Purple", "Brown", "Grey"], NoteStyle.textColors)), id: \.0) { name, c in
                    Button(name) { hub.run { $0.setTextColor(c) } }
                }
            }
            Button("Inline Code") { hub.run(needsFocus: true) { $0.toggleInlineCode() } }.keyboardShortcut("c", modifiers: [.command, .control])
            Divider()
            Button("Link…") { hub.run { tv in LinkPrompt.run(tv) } }.keyboardShortcut("k")
            Button("Link to Note") { hub.run { $0.insertWikiLink() } }.keyboardShortcut("d")
            Divider()
            Button("Bulleted List") { hub.run { $0.toggleList(ordered: false) } }.keyboardShortcut("l")
            Button("Numbered List") { hub.run { $0.toggleList(ordered: true) } }.keyboardShortcut("l", modifiers: [.command, .shift])
            Button("Checklist") { hub.run { $0.toggleChecklist() } }.keyboardShortcut("t")
            Button("Quote") { hub.run { $0.toggleBlock("quote") } }.keyboardShortcut("t", modifiers: [.command, .shift])
            Menu("Callout") {
                ForEach(NoteFormat.calloutKinds, id: \.self) { k in Button(k.capitalized) { hub.run { $0.toggleBlock("callout:" + k) } } }
            }
            Button("Code Block") { hub.run { $0.toggleBlock("code:") } }.keyboardShortcut("c", modifiers: [.command, .option])
            Menu("Table") {
                Button("Insert Table") { hub.run { $0.insertTable(rows: 3, columns: 3) } }.keyboardShortcut("t", modifiers: [.command, .option])
                Divider()
                Button("Add Row") { hub.run { $0.editTable(.addRow) } }
                Button("Add Column") { hub.run { $0.editTable(.addColumn) } }
                Button("Delete Row") { hub.run { $0.editTable(.deleteRow) } }
                Button("Delete Column") { hub.run { $0.editTable(.deleteColumn) } }
                Button("Header Row On/Off") { hub.run { $0.editTable(.toggleHeader) } }
                Button("Delete Table") { hub.run { $0.editTable(.delete) } }
            }
            Button("Line Separator") { hub.run { $0.insertDivider() } }.keyboardShortcut("s", modifiers: [.command, .option])
            Button("Current Date") { hub.run { $0.insertPlain(Date().formatted(date: .long, time: .omitted)) } }.keyboardShortcut("d", modifiers: [.command, .shift])
            Divider()
            Menu("Align") {
                Button("Left") { hub.run { $0.setAlignment(.natural) } }
                Button("Centre") { hub.run { $0.setAlignment(.center) } }
                Button("Right") { hub.run { $0.setAlignment(.right) } }
            }
            Button("Clear Formatting") { hub.run { $0.clearFormatting() } }
        }
    }

    /// The open note, unlocked (locked notes ask for Touch ID or the password first).
    func withNote(unlocked: Bool = true, _ f: @escaping (Note) -> Void) {
        guard services.nav.destination == .notes || services.nav.destination == .journal else { NSSound.beep(); return }
        let notesUI = services.nav.destination == .journal ? services.journalUI : ui
        guard let id = notesUI.selected, id != 0, let n = services.store.note(id) else { NSSound.beep(); return }
        if !unlocked || !n.locked || notesUI.unlocked.contains(id) { f(n); return }
        Task { @MainActor in if await NoteLock.authenticate("export a locked note") { notesUI.unlocked.insert(id); f(n) } }
    }

    func importNotes() {
        let ids = NoteExport.importFiles(store: services.store)
        guard let last = ids.last else { return }
        services.nav.go(.notes)
        ui.filter = .all
        ui.open(last)
    }
}

/// ⌘K: asks for a web address for the selection.
@MainActor
enum LinkPrompt {
    static func run(_ tv: NoteTextView) {
        let a = NSAlert()
        a.messageText = tv.formatState.link == nil ? "Add Link" : "Edit Link"
        a.informativeText = "Web address for the selected text."
        let field = NSTextField(frame: NSRect(x: 0, y: 0, width: 280, height: 24))
        field.placeholderString = "https://"
        field.stringValue = tv.formatState.link ?? ""
        a.accessoryView = field
        a.addButton(withTitle: "Apply"); a.addButton(withTitle: "Cancel")
        if tv.formatState.link != nil { a.addButton(withTitle: "Remove Link") }
        a.window.initialFirstResponder = field
        switch a.runModal() {
        case .alertFirstButtonReturn: if let u = NoteLinks.url(field.stringValue) { tv.setLink(u) } else { NSSound.beep() }
        case .alertThirdButtonReturn: tv.setLink(nil)
        default: break
        }
    }
}

extension CGFloat {
    func rounded(toPlaces p: Int) -> CGFloat { let m = pow(10, CGFloat(p)); return (self * m).rounded() / m }
}
