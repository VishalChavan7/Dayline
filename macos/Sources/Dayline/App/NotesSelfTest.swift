import SwiftUI
import AppKit
import DaylineCore
import DaylineEditor

/// Checks the notes features inside the running app (screenshot mode only, on its sample data): lists, tags,
/// Trash, lock, links, history, export to every format, import, and an edit saved through the live editor.
/// Results go to `notes-selftest.txt`; any line starting with FAIL is a bug.
@MainActor
enum NotesSelfTest {
    static func run(_ s: Services, window: NSWindow, dir: URL) async {
        var lines: [String] = []
        var failed = 0
        func expect(_ ok: Bool, _ what: String) {
            lines.append((ok ? "ok   " : "FAIL ") + what)
            if !ok { failed += 1 }
        }
        let store = s.store, ui = s.notesUI
        func make(_ title: String, _ body: String = "", tags: String? = nil) -> Int64 {
            var n = Note(id: 0, title: title); n.body = body; n.category = tags
            return store.save(n)
        }

        // Lists and tags
        let a = make("ST Alpha", "<p>Links to [[ST Beta]]</p>", tags: "st/one")
        let b = make("ST Beta", "<p>\u{2610} open item</p>", tags: "st")
        let c = make("ST Gamma", "<p>secret words</p>")
        let d = make("ST Delta")
        store.setLocked(note: c, true)
        store.archiveNote(d)
        func ids(_ f: NotesFilter, _ q: String = "") -> Set<Int64> { Set(store.notes(in: f, query: q).map(\.id)).intersection([a, b, c, d]) }
        expect(ids(.all) == [a, b, c], "Notes list leaves out archived notes")
        expect(ids(.archive) == [d], "Archive list")
        expect(ids(.locked) == [c], "Locked list")
        expect(ids(.tag("st")) == [a, b], "#st includes #st/one")
        expect(ids(.tag("st/one")) == [a], "#st/one")
        expect(ids(.all, "secret").isEmpty, "a locked note's text isn't searched")
        expect(ids(.all, "ST Gamma") == [c], "a locked note is found by title")
        expect(store.search("secret").notes.allSatisfy { $0.id != c }, "global search skips locked text")
        let tree = store.tagTree(sort: .name)
        expect(tree.first { $0.path == "st" }?.children.map(\.path) == ["st/one"], "tag tree nests #st/one under #st")
        expect(store.backlinks(to: store.note(b)!).map(\.id) == [a], "backlinks")
        expect(store.note(titled: "st beta")?.id == b, "[[link]] finds a note by title, any case")
        expect(store.noteTitle(store.note(c)!) == "ST Gamma", "title of a locked note")

        // Trash
        store.trashNote(a)
        expect(ids(.trash) == [a] && !ids(.all).contains(a), "Move to Trash")
        expect(store.note(titled: "ST Alpha") == nil, "links don't point into the Trash")
        store.restoreNote(a)
        expect(ids(.all).contains(a) && ids(.trash).isEmpty, "Restore")
        store.trashNote(d)
        store.emptyTrash()
        expect(store.note(d) == nil, "Empty Trash deletes for good")
        let before = store.note(b)!.updatedAt
        try? await Task.sleep(for: .milliseconds(20))
        store.archiveNote(b); store.unarchiveNote(b)
        expect(store.note(b)!.updatedAt > before, "archiving counts as an edit for sync")

        // History and lock
        ui.open(a); ui.unlocked.insert(c); ui.open(c); ui.open(b)
        ui.goBack { store.note($0) != nil }
        expect(ui.selected == c, "Back")
        ui.goForward { store.note($0) != nil }
        expect(ui.selected == b, "Forward")
        expect(!ui.unlocked.contains(c), "a locked note locks again when you leave it")

        // Links typed into the link box
        expect(NoteLinks.url("dayline.app")?.absoluteString == "https://dayline.app", "link without https")
        expect(NoteLinks.url("javascript:alert(1)") == nil && NoteLinks.url("file:///etc/hosts") == nil, "unsafe links refused")

        // Export to every format, then import
        let tmp = FileManager.default.temporaryDirectory.appendingPathComponent("dayline-selftest-\(UUID().uuidString)")
        try? FileManager.default.createDirectory(at: tmp, withIntermediateDirectories: true)
        var rich = Note(id: 0, title: "ST Export")
        rich.body = "<p><b>Bold</b> text</p><blockquote><p>quote</p></blockquote><table><tr><th>A</th></tr><tr><td>1</td></tr></table>"
        for f in NoteExport.Format.allCases {
            let url = tmp.appendingPathComponent("note." + f.ext)
            do { try NoteExport.write(rich, as: f, to: url) } catch { expect(false, "export \(f.title): \(error)") ; continue }
            let data = (try? Data(contentsOf: url)) ?? Data()
            switch f {
            case .pdf: expect(data.starts(with: Data("%PDF".utf8)) && data.count > 1000, "export PDF")
            case .markdown: expect(String(data: data, encoding: .utf8)?.contains("**Bold**") == true, "export Markdown")
            case .html: expect(String(data: data, encoding: .utf8)?.contains("<blockquote>") == true, "export HTML")
            case .text: expect(String(data: data, encoding: .utf8)?.hasPrefix("ST Export\n\nBold text") == true, "export text")
            }
        }
        let md = tmp.appendingPathComponent("Imported plan.md")
        try? "# Plan\n\n- [ ] ship\n\n| A | B |\n|---|---|\n| 1 | 2 |".write(to: md, atomically: true, encoding: .utf8)
        let imp = NoteExport.note(from: md)
        expect(imp?.title == "Plan" && imp?.body.contains("<table>") == true && imp?.body.contains("\u{2610}") == true, "import Markdown")
        let html = tmp.appendingPathComponent("page.html")
        try? "<html><body><script>alert(1)</script><p>Hello <b>there</b></p></body></html>".write(to: html, atomically: true, encoding: .utf8)
        let ih = NoteExport.note(from: html)
        expect(ih?.body == "<p>Hello <b>there</b></p>", "import HTML drops scripts (got \(ih?.body ?? "nil"))")
        try? FileManager.default.removeItem(at: tmp)

        // An edit made with the Format menu, saved through the real editor and store
        s.nav.go(.notes)
        let e = make("ST Edit", "<p>make me a heading</p><p>quote me</p>")
        ui.open(e)
        try? await Task.sleep(for: .seconds(1.2))
        if let tv = EditorHub.shared.current {
            window.makeFirstResponder(tv)
            tv.setSelectedRange(NSRange(location: 2, length: 0))
            EditorHub.shared.run { $0.setHeading(2) }
            tv.setSelectedRange(NSRange(location: (tv.string as NSString).length, length: 0))
            EditorHub.shared.run { $0.toggleBlock("callout:tip") }
            EditorHub.shared.run { $0.insertTable(rows: 2, columns: 2) }
            tv.insertText("cell", replacementRange: tv.selectedRange())
            try? await Task.sleep(for: .seconds(1.5))   // the editor saves 0.7 s after the last change
            let body = store.note(e)?.body ?? ""
            expect(body.contains("<span style=\"font-size: 21.0px;\"><b>make me a heading</b></span>"), "heading saved")
            expect(body.contains("<div class=\"callout\" data-kind=\"tip\"><p>quote me</p></div>"), "callout saved")
            expect(body.contains("<table><tr><th>cell</th><th></th></tr><tr><td></td><td></td></tr></table>"), "table saved (\(body))")

            // The phone edits the open note (a sync): the editor shows it, and leaving doesn't save the old text back.
            if var m = store.note(e) {
                m.body = "<p>written on the phone</p>"
                store.save(m)
                try? await Task.sleep(for: .seconds(0.6))
                expect(EditorHub.shared.current?.string.contains("written on the phone") == true, "an open note shows the phone's edit")
                ui.open(b)
                try? await Task.sleep(for: .seconds(0.6))
                expect(store.note(e)?.body == "<p>written on the phone</p>", "leaving the note keeps the phone's edit (\(store.note(e)?.body ?? "nil"))")
            }
        } else { expect(false, "editor opened") }
        // Opening a note and leaving it without typing changes nothing (so it can't overwrite a newer copy).
        let viewed = store.note(a)
        ui.open(a)
        try? await Task.sleep(for: .seconds(0.8))
        ui.open(b)
        try? await Task.sleep(for: .seconds(0.4))
        expect(store.note(a) == viewed, "viewing a note doesn't save it")
        ui.close()

        // Collapsing panes: the app's sidebar, then tags, then the list; expanding brings them back in reverse.
        let panes = NotesUI(layoutKey: "selftest.notesLayout", maxLayout: 3), nav = Navigator()
        panes.layout = 3; nav.sidebar = .all
        var steps: [String] = []
        func state() -> String { "\(nav.sidebar == .detailOnly ? "-" : "S")\(panes.layout)" }
        for _ in 0..<4 { panes.collapse(nav); steps.append(state()) }
        expect(!panes.canCollapse(nav), "nothing left to hide")
        for _ in 0..<4 { panes.expand(nav); steps.append(state()) }
        expect(!panes.canExpand(nav), "everything shown again")
        expect(steps == ["-3", "-2", "-1", "-1", "-2", "-3", "S3", "S3"], "collapse and expand order (\(steps))")
        panes.layout = 3; nav.sidebar = .detailOnly; panes.collapse(nav, shown: 2)
        expect(panes.layout == 1, "a narrow window that already hides the tags hides the list next")
        UserDefaults.standard.removeObject(forKey: "selftest.notesLayout")
        let journalPanes = NotesUI(layoutKey: "selftest.journalLayout", maxLayout: 2)
        nav.sidebar = .detailOnly; journalPanes.layout = 2
        journalPanes.collapse(nav)
        expect(journalPanes.layout == 1 && !journalPanes.canCollapse(nav), "Journal: entry alone")
        journalPanes.expand(nav); journalPanes.expand(nav)
        expect(journalPanes.layout == 2 && nav.sidebar == .all, "Journal: timeline, then the sidebar")
        UserDefaults.standard.removeObject(forKey: "selftest.journalLayout")

        // Lists: Todo and Untagged are gone from Notes
        expect(NotesFilter.views.map(\.title) == ["Notes", "Today", "Locked", "Pinned", "Archive", "Trash"], "note lists")

        // Empty-page pictures
        for k in [ArtKind.inbox, .calendar, .checklist, .search, .sprout, .notebook, .journal, .chart, .hello] {
            let els = ArtShapes.scene(k)
            expect(els.count >= 5 && els.allSatisfy { !$0.path.isEmpty } && els.contains { $0.stroke == "i" && $0.path.boundingRect.width > 30 },
                   "picture \(k.rawValue) (\(els.count) parts)")
        }

        for id in [a, b, c, e] { store.deleteNote(id) }
        lines.insert(failed == 0 ? "All \(lines.count) notes self-tests passed." : "\(failed) of \(lines.count) notes self-tests FAILED.", at: 0)
        try? lines.joined(separator: "\n").write(to: dir.appendingPathComponent("notes-selftest.txt"), atomically: true, encoding: .utf8)
    }
}
