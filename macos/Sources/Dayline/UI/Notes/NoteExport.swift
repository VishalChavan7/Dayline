import SwiftUI
import AppKit
import UniformTypeIdentifiers
import DaylineCore
import DaylineEditor

/// Export, import and printing of notes.
@MainActor
enum NoteExport {
    enum Format: String, CaseIterable, Identifiable {
        case markdown, html, pdf, text
        var id: String { rawValue }
        var title: String { switch self { case .markdown: "Markdown"; case .html: "HTML"; case .pdf: "PDF"; case .text: "Plain Text" } }
        var ext: String { switch self { case .markdown: "md"; case .html: "html"; case .pdf: "pdf"; case .text: "txt" } }
    }

    static func fileName(_ store: Store, _ n: Note) -> String {
        let t = store.noteTitle(n).replacingOccurrences(of: "[/\\\\:*?\"<>|\\n]", with: "-", options: .regularExpression)
            .trimmingCharacters(in: .whitespaces)
        return String((t.isEmpty ? "Note" : t).prefix(80))
    }

    static func markdown(_ n: Note) -> String { NoteFormat.toMarkdown(title: n.title, body: n.body) }
    static func text(_ n: Note) -> String {
        let body = NoteCodec.plainText(n.body)
        return n.title.isEmpty ? body + "\n" : n.title + "\n\n" + body + "\n"
    }

    /// Asks where to save, then writes the note.
    static func export(_ n: Note, as f: Format, store: Store) {
        let panel = NSSavePanel()
        panel.nameFieldStringValue = fileName(store, n) + "." + f.ext
        panel.allowedContentTypes = [UTType(filenameExtension: f.ext) ?? .data]
        panel.canCreateDirectories = true
        guard panel.runModal() == .OK, let url = panel.url else { return }
        do { try write(n, as: f, to: url) } catch { NSAlert(error: error).runModal() }
    }

    static func write(_ n: Note, as f: Format, to url: URL) throws {
        switch f {
        case .markdown: try markdown(n).write(to: url, atomically: true, encoding: .utf8)
        case .html: try NoteFormat.toHtmlPage(title: n.title, body: n.body).write(to: url, atomically: true, encoding: .utf8)
        case .text: try text(n).write(to: url, atomically: true, encoding: .utf8)
        case .pdf:
            let info = printInfo()
            info.jobDisposition = .save
            info.dictionary()[NSPrintInfo.AttributeKey.jobSavingURL] = url
            let op = NSPrintOperation(view: printView(n, info: info), printInfo: info)
            op.showsPrintPanel = false
            op.showsProgressPanel = false
            if !op.run() { throw CocoaError(.fileWriteUnknown) }
        }
    }

    static func print(_ n: Note) {
        let info = printInfo()
        let op = NSPrintOperation(view: printView(n, info: info), printInfo: info)
        op.jobTitle = n.title.isEmpty ? "Note" : n.title
        op.showsPrintPanel = true
        if let w = NSApp.keyWindow { op.runModal(for: w, delegate: nil, didRun: nil, contextInfo: nil) } else { op.run() }
    }

    static func printInfo() -> NSPrintInfo {
        let info = (NSPrintInfo.shared.copy() as? NSPrintInfo) ?? NSPrintInfo()
        info.horizontalPagination = .fit
        info.verticalPagination = .automatic
        info.isHorizontallyCentered = false
        info.isVerticallyCentered = false
        info.topMargin = 48; info.bottomMargin = 48; info.leftMargin = 54; info.rightMargin = 54
        return info
    }

    /// The note laid out for paper, always in dark ink on white.
    static func printView(_ n: Note, info: NSPrintInfo) -> NSView {
        let width = info.paperSize.width - info.leftMargin - info.rightMargin
        let tv = NoteTextView.make(style: NoteStyle(dark: false))
        tv.frame = NSRect(x: 0, y: 0, width: width, height: 100)
        tv.textContainerInset = .zero
        let doc = NSMutableAttributedString()
        if !n.title.isEmpty {
            doc.append(NSAttributedString(string: n.title + "\n", attributes: [.font: NSFont.systemFont(ofSize: 24, weight: .bold),
                                                                                .foregroundColor: NoteStyle(dark: false).ink,
                                                                                .paragraphStyle: { let p = NoteStyle.baseParagraph(); p.paragraphSpacing = 12; return p }()]))
        }
        let d = NoteCodec.decode(n.body)
        doc.append(NoteHTML.attributed(from: d.html, style: NoteStyle(dark: false)))
        for c in d.checklist { doc.append(NSAttributedString(string: "\n" + (c.done ? "☑ " : "☐ ") + c.text, attributes: NoteStyle(dark: false).bodyAttributes)) }
        tv.textStorage?.setAttributedString(doc)
        tv.isEditable = false
        tv.layoutManager?.ensureLayout(for: tv.textContainer!)
        tv.sizeToFit()
        return tv
    }

    static func copyMarkdown(_ n: Note) {
        var md = markdown(n)
        if let tv = EditorHub.shared.current, tv.selectedRange().length > 0, let ts = tv.textStorage {
            md = NoteFormat.toMarkdown(title: "", body: NoteHTML.html(from: ts.attributedSubstring(from: tv.selectedRange())))
        }
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(md, forType: .string)
    }

    /// Markdown, text and HTML files become notes. Returns the new notes' ids.
    @discardableResult
    static func importFiles(store: Store) -> [Int64] {
        let panel = NSOpenPanel()
        panel.allowsMultipleSelection = true
        panel.canChooseDirectories = false
        panel.allowedContentTypes = [UTType(filenameExtension: "md"), UTType(filenameExtension: "markdown"), .plainText, .html].compactMap { $0 }
        panel.message = "Choose Markdown, text or HTML files to add as notes."
        guard panel.runModal() == .OK else { return [] }
        var ids: [Int64] = []
        var failed: [String] = []
        for url in panel.urls {
            guard let n = note(from: url) else { failed.append(url.lastPathComponent); continue }
            let id = store.save(n)
            if id != 0 { ids.append(id) }
        }
        if !failed.isEmpty {
            let a = NSAlert(); a.messageText = "Some files couldn't be imported"; a.informativeText = failed.joined(separator: "\n"); a.runModal()
        }
        return ids
    }

    static func note(from url: URL) -> Note? {
        guard let data = try? Data(contentsOf: url), data.count < 20_000_000,
              let s = String(data: data, encoding: .utf8) ?? String(data: data, encoding: .isoLatin1) else { return nil }
        let name = url.deletingPathExtension().lastPathComponent
        var n = Note(id: 0, title: name)
        switch url.pathExtension.lowercased() {
        case "md", "markdown":
            let r = NoteFormat.fromMarkdown(s, fallbackTitle: name)
            n.title = r.title; n.body = r.html
        case "html", "htm":
            var body = s
            if let r = s.range(of: "<body[^>]*>", options: [.regularExpression, .caseInsensitive]) { body = String(s[r.upperBound...]) }
            if let r = body.range(of: "</body>", options: .caseInsensitive) { body = String(body[..<r.lowerBound]) }
            body = body.replacingOccurrences(of: "<(script|style)[\\s\\S]*?</\\1>", with: "", options: [.regularExpression, .caseInsensitive])
            // Reading it into the editor and back keeps only what notes support.
            n.body = NoteHTML.html(from: NoteHTML.attributed(from: body, style: NoteStyle(dark: false)))
        default:
            n.body = s.replacingOccurrences(of: "\r\n", with: "\n").components(separatedBy: "\n")
                .map { $0.isEmpty ? "<br>" : "<p>" + NoteCodec.escape($0) + "</p>" }.joined()
        }
        if n.title.isEmpty && NoteCodec.plainText(n.body).isEmpty { return nil }
        return n
    }
}
