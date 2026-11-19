import AppKit
import DaylineCore
import DaylineEditor

/// Checks for the Mac note editor, run without a window: HTML round trips (including what the phone writes),
/// text colours in both themes, paste, every formatting command, undo, and a random command fuzz.
@MainActor
func runEditorChecks() {
    let light = NoteStyle(dark: false), dark = NoteStyle(dark: true)
    func roundTrip(_ html: String) -> String { NoteHTML.html(from: NoteHTML.attributed(from: html, style: light)) }
    func rt(_ input: String, _ expected: String? = nil, _ what: String, line: Int = #line) {
        let out = roundTrip(input)
        eq(out, expected ?? input, "editor round trip: \(what)", line: line)
        eq(roundTrip(out), out, "editor round trip is stable: \(what)", line: line)
    }

    // MARK: HTML in and out (the phone's exact output must come back unchanged)
    rt("<p>Hello <b>bold</b> <i>it</i> <u>u</u> <s>s</s></p>", nil, "inline styles")
    rt("<p><span style=\"color: rgba(217, 48, 37, 1.0);\">red</span></p>", nil, "phone text colour")
    rt("<p><span style=\"color: rgba(31, 31, 31, 1.0); background: rgba(255, 245, 157, 1.0);\">hi</span></p>", nil, "phone highlight")
    rt("<p><span style=\"font-size: 21.0px;\"><b>Two</b></span></p>", nil, "phone heading 2")
    rt("<p><span style=\"font-size: 26.0px;\"><b>One</b></span></p>", nil, "phone heading 1")
    rt("<p><span style=\"font-size: 18.0px; font-weight: 600;\">Three</span></p>", nil, "phone heading 3")
    rt("<h1>One</h1><h4>Four</h4>", "<p><span style=\"font-size: 26.0px;\"><b>One</b></span></p><p><span style=\"font-size: 16.5px;\"><b>Four</b></span></p>", "h tags")
    rt("<p><span style=\"font-size: 2.0em;\"><b>Em</b></span></p>", "<p><span style=\"font-size: 26.0px;\"><b>Em</b></span></p>", "em heading")
    rt("<ul><li>a</li><li>b</li></ul><ol><li>x</li><li>y</li></ol>", nil, "lists")
    rt("<ul><li>a<ul><li>b</li></ul></li><li>c</li></ul>", nil, "nested list")
    rt("<ul><li>a</li><ul><li>b</li></ul></ul><ul><li>c</li></ul>", "<ul><li>a<ul><li>b</li></ul></li><li>c</li></ul>", "phone nested list")
    rt("<p>x <a href=\"https://example.com/a?b=1&amp;c=2\">Dayline</a></p>", nil, "link")
    rt("<p><code>let x</code> y</p>", nil, "inline code")
    rt("<p style=\"text-align: center;\">c</p><p style=\"text-align: right;\">r</p>", nil, "alignment")
    rt("<p>\u{2610} a</p><p>\u{2611}\u{FE0E} b</p>", nil, "checklist lines")
    rt("<p>a</p><br><p>b</p>", nil, "empty line")
    rt("<p>a &amp; b &lt; c &gt; d</p>", nil, "entities")
    rt("<p><strong>s</strong> <em>e</em> <del>d</del></p>", "<p><b>s</b> <i>e</i> <s>d</s></p>", "other tag names")
    rt("<p><span style=\"color: #1A73E8\">x</span></p>", "<p><span style=\"color: rgba(26, 115, 232, 1.0);\">x</span></p>", "hex colour")
    rt("<p><mark>m</mark></p>", "<p><span style=\"color: rgba(31, 31, 31, 1.0); background: rgba(255, 241, 118, 1.0);\">m</span></p>", "mark")
    rt("line one\nline two", "<p>line one</p><p>line two</p>", "plain text note")
    rt("", "", "empty note")
    rt("<p>a<br>b</p>", nil, "line break")
    rt("<hr>", nil, "divider")
    rt("<blockquote><p>q <b>b</b></p><p>two</p></blockquote>", nil, "quote")
    rt("<blockquote><p>one</p></blockquote><blockquote><p>two</p></blockquote>", nil, "two quotes stay two")
    rt("<div class=\"callout\" data-kind=\"warning\"><p>hot</p></div>", nil, "callout")
    rt("<pre><code class=\"language-swift\">let a = 1 &lt; 2\n  indented\n\nblank above</code></pre>", nil, "code block")
    rt("<table><tr><th>A</th><th>B</th></tr><tr><td>1</td><td>2 &amp; 3</td></tr></table>", nil, "table")
    rt("<table><tr><td>a</td><td></td></tr></table>", nil, "table without header, empty cell")
    let mixed = "<p>Intro <b>bold</b></p><blockquote><p>Quoted</p></blockquote><div class=\"callout\" data-kind=\"tip\"><p>Careful</p></div>" +
        "<pre><code class=\"language-kotlin\">val a = 1</code></pre><table><tr><th>A</th><th>B</th></tr><tr><td>1</td><td>2</td></tr></table><hr><p>Outro</p>"
    rt(mixed, nil, "every block")
    rt("<p>\u{1F600} emoji \u{1F469}\u{200D}\u{1F4BB} and RTL \u{05E9}\u{05DC}\u{05D5}\u{05DD}</p>", nil, "emoji and right-to-left")
    rt("<p><b><i><u>all</u></i></b></p>", "<p><u><i><b>all</b></i></u></p>", "nested inline")
    rt("<p>unclosed <b>bold</p><p>next</p>", "<p>unclosed <b>bold</b></p><p>next</p>", "unclosed tag")
    for junk in ["<", "<<p>>", "<p", "</p>", "<b></i>", "<ul><li>", "<table><tr><td>x", "<pre><code>", "&#99999999;", "<span style=\"color:\">x</span>",
                 String(repeating: "<b>", count: 300) + "deep", "<p>" + String(repeating: "x", count: 20000) + "</p>"] {
        _ = roundTrip(junk); _ = roundTrip(roundTrip(junk))
    }
    passed += 1

    // MARK: Colours: never white on white, never black on black
    func inkRuns(_ a: NSAttributedString) -> [NSColor] {
        var out: [NSColor] = []
        a.enumerateAttributes(in: NSRange(location: 0, length: a.length)) { at, _, _ in
            if at[.dlColor] == nil && at[.dlHighlight] == nil { out.append(at[.foregroundColor] as? NSColor ?? .clear) }
        }
        return out
    }
    let sample = "<p>plain</p><blockquote><p>quote</p></blockquote><pre><code>code</code></pre><table><tr><td>cell</td></tr></table>"
    check(inkRuns(NoteHTML.attributed(from: sample, style: light)).allSatisfy { $0.isClose(to: light.ink) }, "light theme: all plain text is dark ink")
    check(inkRuns(NoteHTML.attributed(from: sample, style: dark)).allSatisfy { $0.isClose(to: dark.ink) }, "dark theme: all plain text is light ink")
    let hl = NoteHTML.attributed(from: "<p><span style=\"color: rgba(31, 31, 31, 1.0); background: rgba(255, 245, 157, 1.0);\">hi</span></p>", style: dark)
    check((hl.attribute(.foregroundColor, at: 0, effectiveRange: nil) as? NSColor)?.isClose(to: NoteStyle.highlightInk) == true, "dark theme: highlighted text stays dark")

    // A new note: typing gets ink in both themes, and switching theme recolours it.
    let tv = NoteTextView.make(style: light)
    tv.ownUndoManager = UndoManager()
    tv.load(html: "")
    tv.insertText("Typed into a new note.", replacementRange: tv.selectedRange())
    check(inkRuns(tv.textStorage!).allSatisfy { $0.isClose(to: light.ink) } && !inkRuns(tv.textStorage!).isEmpty, "new note: typed text is dark in light theme")
    tv.setStyle(dark)
    check(inkRuns(tv.textStorage!).allSatisfy { $0.isClose(to: dark.ink) }, "new note: text turns light in dark theme")
    tv.insertText(" More.", replacementRange: NSRange(location: tv.string.utf16.count, length: 0))
    check(inkRuns(tv.textStorage!).allSatisfy { $0.isClose(to: dark.ink) }, "dark theme: typed text is light")
    tv.setStyle(light)
    check(inkRuns(tv.textStorage!).allSatisfy { $0.isClose(to: light.ink) }, "back to light: all text dark again")
    eq(tv.appearance?.name, .aqua, "text view appearance follows the theme")
    // Text that arrives with a stray colour (white from another app) is recoloured.
    tv.textStorage!.append(NSAttributedString(string: " white", attributes: [.foregroundColor: NSColor.white, .font: NSFont.systemFont(ofSize: 15)]))
    check(inkRuns(tv.textStorage!).allSatisfy { $0.isClose(to: light.ink) }, "stray white text is recoloured to ink")

    // MARK: Paste from other apps keeps only basic styles
    let foreign = NSMutableAttributedString(string: "Big white bold", attributes: [.foregroundColor: NSColor.white, .backgroundColor: NSColor.black,
                                                                                    .font: NSFont.boldSystemFont(ofSize: 30)])
    let clean = tv.sanitize(foreign)
    check((clean.attribute(.foregroundColor, at: 0, effectiveRange: nil) as? NSColor)?.isClose(to: light.ink) == true, "paste: colour dropped")
    check(clean.attribute(.backgroundColor, at: 0, effectiveRange: nil) == nil, "paste: background dropped")
    let pf = clean.attribute(.font, at: 0, effectiveRange: nil) as? NSFont
    check(pf?.pointSize == NoteStyle.bodySize && NSFontManager.shared.traits(of: pf!).contains(.boldFontMask), "paste: size dropped, bold kept")
    let pb = NSPasteboard(name: NSPasteboard.Name("dayline-checks-\(UUID().uuidString)"))
    pb.clearContents()
    pb.setData(foreign.rtf(from: NSRange(location: 0, length: foreign.length)), forType: .rtf)
    tv.load(html: "<p>x</p>")
    _ = tv.readSelection(from: pb, type: .rtf)
    eq(tv.html, "<p>x<b>Big white bold</b></p>", "paste RTF: text kept, colours gone")
    check(inkRuns(tv.textStorage!).allSatisfy { $0.isClose(to: light.ink) }, "paste RTF: ink colour")
    // Copy and paste between notes keeps blocks.
    tv.load(html: "<blockquote><p>q</p></blockquote>")
    tv.setSelectedRange(NSRange(location: 0, length: 1))
    pb.clearContents(); _ = tv.writeSelection(to: pb, type: .daylineNote)
    eq(pb.string(forType: .daylineNote), "<blockquote><p>q</p></blockquote>", "copy writes note HTML")
    pb.releaseGlobally()

    // MARK: Commands
    func doc(_ html: String, select: NSRange? = nil) -> NoteTextView {
        let t = NoteTextView.make(style: light)
        t.ownUndoManager = UndoManager()
        t.load(html: html)
        if let select { t.setSelectedRange(select) }
        return t
    }
    func all(_ t: NoteTextView) { t.setSelectedRange(NSRange(location: 0, length: t.string.utf16.count)) }

    var t = doc("<p>Title</p>", select: NSRange(location: 2, length: 0))
    t.setHeading(2); eq(t.html, "<p><span style=\"font-size: 21.0px;\"><b>Title</b></span></p>", "heading 2")
    t.setHeading(2); eq(t.html, "<p>Title</p>", "heading 2 again: body")
    t.setHeading(5); eq(t.html, "<p><span style=\"font-size: 15.5px;\"><b>Title</b></span></p>", "heading 5")
    t.setHeading(0); eq(t.html, "<p>Title</p>", "body text")

    t = doc("<p>one two</p>", select: NSRange(location: 4, length: 3))
    t.toggleTrait(.boldFontMask); eq(t.html, "<p>one <b>two</b></p>", "bold")
    t.toggleTrait(.boldFontMask); eq(t.html, "<p>one two</p>", "bold off")
    t.toggleTrait(.italicFontMask); t.toggleLine(.underlineStyle); t.toggleLine(.strikethroughStyle)
    eq(t.html, "<p>one <s><u><i>two</i></u></s></p>", "italic, underline, strikethrough")
    t.clearFormatting(); eq(t.html, "<p>one two</p>", "clear formatting")
    t.setTextColor(NoteStyle.textColors[0]); eq(t.html, "<p>one <span style=\"color: rgba(217, 48, 37, 1.0);\">two</span></p>", "text colour")
    t.setTextColor(nil); eq(t.html, "<p>one two</p>", "text colour off")
    t.setHighlight(NoteStyle.highlightColors[1]); eq(t.html, "<p>one <span style=\"color: rgba(31, 31, 31, 1.0); background: rgba(165, 214, 167, 1.0);\">two</span></p>", "highlight")
    t.setHighlight(nil); eq(t.html, "<p>one two</p>", "highlight off")
    t.toggleInlineCode(); eq(t.html, "<p>one <code>two</code></p>", "inline code")
    t.toggleInlineCode(); eq(t.html, "<p>one two</p>", "inline code off")
    t.setLink(URL(string: "https://dayline.app")); eq(t.html, "<p>one <a href=\"https://dayline.app\">two</a></p>", "link")
    t.setLink(nil); eq(t.html, "<p>one two</p>", "link removed")
    // Typing after choosing a colour with nothing selected.
    t = doc("<p>a</p>", select: NSRange(location: 1, length: 0))
    t.setTextColor(NoteStyle.textColors[4]); t.insertText("b", replacementRange: t.selectedRange())
    eq(t.html, "<p>a<span style=\"color: rgba(26, 115, 232, 1.0);\">b</span></p>", "typing in a chosen colour")
    t.setTextColor(nil); t.insertText("c", replacementRange: t.selectedRange())
    eq(t.html, "<p>a<span style=\"color: rgba(26, 115, 232, 1.0);\">b</span>c</p>", "back to ink while typing")

    t = doc("<p>a</p><p>b</p>"); all(t)
    t.toggleList(ordered: false); eq(t.html, "<ul><li>a</li><li>b</li></ul>", "bullets")
    all(t); t.toggleList(ordered: true); eq(t.html, "<ol><li>a</li><li>b</li></ol>", "numbers")
    eq(t.string, "\t1.\ta\n\t2.\tb", "numbers are shown 1, 2")
    t.setSelectedRange(NSRange(location: t.string.utf16.count, length: 0))
    t.indent(1); eq(t.html, "<ol><li>a<ol><li>b</li></ol></li></ol>", "indent")
    t.indent(-1); eq(t.html, "<ol><li>a</li><li>b</li></ol>", "outdent")
    all(t); t.toggleList(ordered: true); eq(t.html, "<p>a</p><p>b</p>", "list off")
    all(t); t.toggleChecklist(); eq(t.html, "<p>\u{2610} a</p><p>\u{2610} b</p>", "checklist")
    t.toggleCheckbox(near: 0); eq(t.html, "<p>\u{2611}\u{FE0E} a</p><p>\u{2610} b</p>", "tick a box")
    all(t); t.toggleChecklist(); eq(t.html, "<p>a</p><p>b</p>", "checklist off (ticked or not)")
    t.setSelectedRange(NSRange(location: 3, length: 0)); t.toggleChecklist(); eq(t.html, "<p>a</p><p>\u{2610} b</p>", "checklist on one line")

    // Return key behaviour.
    t = doc("<p>\u{2610} milk</p>"); t.setSelectedRange(NSRange(location: t.string.utf16.count, length: 0))
    t.insertNewline(nil); t.insertText("eggs", replacementRange: t.selectedRange())
    eq(t.html, "<p>\u{2610} milk</p><p>\u{2610} eggs</p>", "return continues a checklist")
    t.insertNewline(nil); t.insertNewline(nil)
    eq(t.html, "<p>\u{2610} milk</p><p>\u{2610} eggs</p>", "return on an empty box ends the checklist")
    t = doc("<h2>Head</h2>"); t.setSelectedRange(NSRange(location: 4, length: 0))
    t.insertNewline(nil); t.insertText("body", replacementRange: t.selectedRange())
    eq(t.html, "<p><span style=\"font-size: 21.0px;\"><b>Head</b></span></p><p>body</p>", "line after a heading is body text")
    t = doc("<blockquote><p>q</p></blockquote>"); t.setSelectedRange(NSRange(location: 1, length: 0))
    t.insertNewline(nil); t.insertText("r", replacementRange: t.selectedRange())
    eq(t.html, "<blockquote><p>q</p><p>r</p></blockquote>", "return inside a quote stays in it")
    t.insertNewline(nil); t.insertNewline(nil); t.insertText("out", replacementRange: t.selectedRange())
    eq(t.html, "<blockquote><p>q</p><p>r</p></blockquote><p>out</p>", "return on an empty quote line leaves it")
    t = doc("<pre><code>a</code></pre>"); t.setSelectedRange(NSRange(location: 1, length: 0))
    t.insertNewline(nil); t.insertText("  b", replacementRange: t.selectedRange())
    eq(t.html, "<pre><code>a\n  b</code></pre>", "return inside code")

    // Blocks.
    t = doc("<p>a</p><p>b</p>"); all(t)
    t.toggleBlock("quote"); eq(t.html, "<blockquote><p>a</p><p>b</p></blockquote>", "quote")
    all(t); t.toggleBlock("quote"); eq(t.html, "<p>a</p><p>b</p>", "quote off")
    all(t); t.toggleBlock("callout:tip"); eq(t.html, "<div class=\"callout\" data-kind=\"tip\"><p>a</p><p>b</p></div>", "callout")
    all(t); t.toggleBlock("callout:warning"); eq(t.html, "<div class=\"callout\" data-kind=\"warning\"><p>a</p><p>b</p></div>", "callout kind change")
    all(t); t.toggleBlock("callout:warning"); eq(t.html, "<p>a</p><p>b</p>", "callout off")
    t = doc("<p>x = <b>1</b></p><p>y</p>"); all(t)
    t.toggleBlock("code:"); eq(t.html, "<pre><code>x = 1\ny</code></pre>", "code block from text")
    all(t); t.toggleBlock("code:"); eq(t.html, "<p>x = 1</p><p>y</p>", "code block off")
    t = doc("<p>a</p>"); t.setSelectedRange(NSRange(location: 1, length: 0))
    t.insertDivider(); t.insertText("b", replacementRange: t.selectedRange())
    eq(t.html, "<p>a</p><hr><p>b</p>", "divider")
    t = doc("<p>a</p>"); t.setSelectedRange(NSRange(location: 1, length: 0))
    t.insertTable(rows: 2, columns: 2)
    t.insertText("H1", replacementRange: t.selectedRange()); t.insertTab(nil); t.insertText("H2", replacementRange: t.selectedRange())
    t.insertTab(nil); t.insertText("c", replacementRange: t.selectedRange()); t.insertTab(nil); t.insertText("d", replacementRange: t.selectedRange())
    eq(t.html, "<p>a</p><table><tr><th>H1</th><th>H2</th></tr><tr><td>c</td><td>d</td></tr></table>", "table typing and tab")
    t.insertTab(nil); t.insertText("e", replacementRange: t.selectedRange())
    eq(t.html, "<p>a</p><table><tr><th>H1</th><th>H2</th></tr><tr><td>c</td><td>d</td></tr><tr><td>e</td><td></td></tr></table>", "tab in the last cell adds a row")
    t.editTable(.addColumn); eq(t.html, "<p>a</p><table><tr><th>H1</th><th></th><th>H2</th></tr><tr><td>c</td><td></td><td>d</td></tr><tr><td>e</td><td></td><td></td></tr></table>", "add column after the caret's column")
    t.editTable(.deleteRow); eq(t.html, "<p>a</p><table><tr><th>H1</th><th></th><th>H2</th></tr><tr><td>c</td><td></td><td>d</td></tr></table>", "delete row")
    t.editTable(.deleteColumn); eq(t.html, "<p>a</p><table><tr><th>H1</th><th>H2</th></tr><tr><td>c</td><td>d</td></tr></table>", "delete column")
    t.editTable(.toggleHeader); eq(t.html, "<p>a</p><table><tr><td>H1</td><td>H2</td></tr><tr><td>c</td><td>d</td></tr></table>", "header off")
    t.editTable(.delete); eq(t.html, "<p>a</p>", "delete table")

    // Moving lines.
    t = doc("<p>a</p><p>b</p><p>c</p>"); t.setSelectedRange(NSRange(location: t.string.utf16.count, length: 0))
    t.moveLines(up: true); eq(t.html, "<p>a</p><p>c</p><p>b</p>", "move up")
    t.moveLines(up: true); eq(t.html, "<p>c</p><p>a</p><p>b</p>", "move up again")
    t.moveLines(up: true); eq(t.html, "<p>c</p><p>a</p><p>b</p>", "move up at the top does nothing")
    t.moveLines(up: false); t.moveLines(up: false); eq(t.html, "<p>a</p><p>b</p><p>c</p>", "move down")
    t.moveLines(up: false); eq(t.html, "<p>a</p><p>b</p><p>c</p>", "move down at the end does nothing")

    // Wiki links.
    t = doc("<p>see </p>"); t.setSelectedRange(NSRange(location: 4, length: 0))
    t.insertWikiLink(); t.insertText("Plan", replacementRange: t.selectedRange())
    eq(t.html, "<p>see [[Plan]]</p>", "wiki link")
    eq(t.wikiTitle(at: 7), "Plan", "wiki link title under the caret")

    // Undo returns to the start after many commands.
    t = doc(mixed)
    let before = t.html
    all(t); t.toggleTrait(.boldFontMask); t.setSelectedRange(NSRange(location: 2, length: 0)); t.setHeading(1); t.toggleList(ordered: false)
    t.setSelectedRange(NSRange(location: 0, length: 3)); t.setTextColor(NoteStyle.textColors[2]); t.toggleBlock("quote"); t.moveLines(up: false)
    while t.ownUndoManager!.canUndo { t.ownUndoManager!.undo() }
    eq(NoteFormat.split(t.html), NoteFormat.split(before), "undo everything gives the note back")

    // Random commands never crash and always give HTML that reads back the same.
    var stable = true
    for seed: UInt64 in [20261119, 7, 42, 1999, 31337] {
    var rng = SeededRandom(seed: seed)
    t = doc(seed % 2 == 0 ? mixed : "<p>plain start</p>")
    for step in 0..<400 {
        let len = t.string.utf16.count
        let a = len == 0 ? 0 : Int(rng.next() % UInt64(len + 1))
        let b = min(len, a + Int(rng.next() % 12))
        t.setSelectedRange(NSRange(location: a, length: b - a))
        switch rng.next() % 24 {
        case 0: t.toggleTrait(.boldFontMask)
        case 1: t.toggleTrait(.italicFontMask)
        case 2: t.toggleLine(.underlineStyle)
        case 3: t.setHeading(Int(rng.next() % 7))
        case 4: t.setTextColor(rng.next() % 2 == 0 ? nil : NoteStyle.textColors[Int(rng.next() % 8)])
        case 5: t.setHighlight(rng.next() % 2 == 0 ? nil : NoteStyle.highlightColors[Int(rng.next() % 5)])
        case 6: t.toggleList(ordered: rng.next() % 2 == 0)
        case 7: t.toggleChecklist()
        case 8: t.indent(rng.next() % 2 == 0 ? 1 : -1)
        case 9: t.toggleBlock(["quote", "callout:note", "callout:caution", "code:"][Int(rng.next() % 4)])
        case 10: t.insertDivider()
        case 11: t.insertTable(rows: 1 + Int(rng.next() % 3), columns: 1 + Int(rng.next() % 3))
        case 12: t.editTable([.addRow, .addColumn, .deleteRow, .deleteColumn, .toggleHeader][Int(rng.next() % 5)])
        case 13: t.moveLines(up: rng.next() % 2 == 0)
        case 14: t.insertNewline(nil)
        case 15: t.insertTab(nil)
        case 16: t.insertText(["x", "Hello ", "☐ ", "#tag ", "[[Link]]", "\u{1F600}", "<b>&"][Int(rng.next() % 7)], replacementRange: t.selectedRange())
        case 17: t.deleteBackward(nil)
        case 18: t.toggleInlineCode()
        case 19: t.setAlignment([.natural, .center, .right][Int(rng.next() % 3)])
        case 20: t.clearFormatting()
        case 21: t.toggleCheckbox(near: a)
        case 22: t.setStyle(rng.next() % 2 == 0 ? dark : light)
        default: t.deleteForward(nil)
        }
        let h = t.html
        if roundTrip(h) != h { stable = false; print("FAIL fuzz step \(step): not stable\n  \(h)\n  \(roundTrip(h))"); break }
        if !inkRuns(t.textStorage!).allSatisfy({ $0.isClose(to: t.style.ink) }) { stable = false; print("FAIL fuzz step \(step): text not in ink"); break }
    }
    if !stable { print("  (seed \(seed))"); break }
    }
    check(stable, "random editing stays readable and stable")
}

/// A small deterministic random generator, so a failing fuzz run can be repeated.
struct SeededRandom {
    var state: UInt64
    init(seed: UInt64) { state = seed }
    mutating func next() -> UInt64 {
        state &+= 0x9E3779B97F4A7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58476D1CE4E5B9
        z = (z ^ (z >> 27)) &* 0x94D049BB133111EB
        return z ^ (z >> 31)
    }
}

/// Quotes, callouts and code blocks are really laid out as blocks (indented, shaded), and the text fits the view.
@MainActor
func runBlockLayoutChecks() {
    let tv = NoteTextView.make(style: NoteStyle(dark: false))
    tv.frame = NSRect(x: 0, y: 0, width: 500, height: 400)
    tv.load(html: "<p>before</p><blockquote><p>quoted</p></blockquote><div class=\"callout\" data-kind=\"tip\"><p>call</p></div><pre><code>code</code></pre><table><tr><td>alpha</td><td>zulu</td></tr></table><p>after</p>")
    let ts = tv.textStorage!, lm = tv.layoutManager!
    lm.ensureLayout(for: tv.textContainer!)
    func frag(_ word: String) -> NSRect {
        let r = (ts.string as NSString).range(of: word)
        return lm.lineFragmentRect(forGlyphAt: lm.glyphIndexForCharacter(at: r.location), effectiveRange: nil)
    }
    eq(frag("before").minX, 0, "body text starts at the margin")
    check(frag("quoted").minX >= 15, "quote is indented past its bar (x = \(frag("quoted").minX))")
    check(frag("call").minX >= 12 && frag("call").maxX <= 500, "callout is padded and fits")
    check(frag("code").minX >= 10, "code block is padded")
    check(frag("zulu").minX > 200 && frag("zulu").maxX <= 500, "table's second column is inside the view")
    for w in ["quoted", "call", "code", "after"] { check(frag(w).maxX <= 500.5, "\(w) fits the width") }
}
