import AppKit
import DaylineCore

/// How blocks look in the editor: quotes, callouts and code are AppKit text blocks (drawn by the text system,
/// so they wrap and select like text); tables are AppKit text tables; a divider is a full-width line.
public enum NoteBlocks {

    public static func quote(style: NoteStyle) -> NSTextBlock {
        let b = NSTextBlock()
        // Without a content width AppKit ignores the block (no bar, shading or padding).
        b.setContentWidth(100, type: .percentageValueType)
        b.setWidth(3, type: .absoluteValueType, for: .border, edge: .minX)
        b.setWidth(14, type: .absoluteValueType, for: .padding, edge: .minX)
        b.setWidth(2, type: .absoluteValueType, for: .padding, edge: .minY)
        b.setWidth(2, type: .absoluteValueType, for: .padding, edge: .maxY)
        b.setWidth(4, type: .absoluteValueType, for: .margin, edge: .minY)
        b.setWidth(4, type: .absoluteValueType, for: .margin, edge: .maxY)
        b.setBorderColor(style.quoteBar, for: .minX)
        return b
    }

    public static func callout(_ kind: String, style: NoteStyle) -> NSTextBlock {
        let b = NSTextBlock()
        // Without a content width AppKit ignores the block (no bar, shading or padding).
        b.setContentWidth(100, type: .percentageValueType)
        let c = style.callout(kind)
        b.backgroundColor = c.fill
        b.setWidth(4, type: .absoluteValueType, for: .border, edge: .minX)
        b.setBorderColor(c.accent, for: .minX)
        b.setWidth(12, type: .absoluteValueType, for: .padding)
        b.setWidth(8, type: .absoluteValueType, for: .padding, edge: .minY)
        b.setWidth(8, type: .absoluteValueType, for: .padding, edge: .maxY)
        b.setWidth(6, type: .absoluteValueType, for: .margin, edge: .minY)
        b.setWidth(6, type: .absoluteValueType, for: .margin, edge: .maxY)
        return b
    }

    public static func code(style: NoteStyle) -> NSTextBlock {
        let b = NSTextBlock()
        // Without a content width AppKit ignores the block (no bar, shading or padding).
        b.setContentWidth(100, type: .percentageValueType)
        b.backgroundColor = style.codeBackground
        b.setWidth(12, type: .absoluteValueType, for: .padding)
        b.setWidth(6, type: .absoluteValueType, for: .margin, edge: .minY)
        b.setWidth(6, type: .absoluteValueType, for: .margin, edge: .maxY)
        return b
    }

    /// Updates block colours in place after the appearance changes.
    static func restyleBlocks(_ s: NSAttributedString, style: NoteStyle, range: NSRange) {
        var seen = Set<ObjectIdentifier>()
        s.enumerateAttribute(.paragraphStyle, in: range) { v, r, _ in
            guard let ps = v as? NSParagraphStyle, let tb = ps.textBlocks.first, seen.insert(ObjectIdentifier(tb)).inserted else { return }
            let kind = (s.attribute(.dlBlock, at: r.location, effectiveRange: nil) as? String) ?? ""
            if kind == "quote" { tb.setBorderColor(style.quoteBar, for: .minX) }
            else if kind.hasPrefix("callout:") {
                let c = style.callout(String(kind.dropFirst(8)))
                tb.backgroundColor = c.fill; tb.setBorderColor(c.accent, for: .minX)
            } else if kind.hasPrefix("code:") { tb.backgroundColor = style.codeBackground }
            else if let cell = tb as? NSTextTableBlock {
                cell.setBorderColor(style.rule)
                if s.attribute(.dlHeader, at: r.location, effectiveRange: nil) != nil { cell.backgroundColor = style.tableHeader }
            }
        }
    }

    // MARK: Lists

    public static func isOrdered(_ l: NSTextList) -> Bool { l.markerFormat.rawValue.contains("decimal") }

    public static func list(ordered: Bool, depth: Int) -> NSTextList {
        if ordered { return NSTextList(markerFormat: NSTextList.MarkerFormat("{decimal}."), options: 0) }
        return NSTextList(markerFormat: [.disc, .circle, .square][min(max(depth, 1), 3) - 1], options: 0)
    }

    public static func marker(ordered: Bool, number: Int, depth: Int) -> String {
        ordered ? "\(number)." : ["•", "◦", "▪"][min(max(depth, 1), 3) - 1]
    }

    public static let indentStep: CGFloat = 24

    static func applyList(_ ps: NSMutableParagraphStyle, levels: [Bool]) {
        ps.textLists = levels.enumerated().map { list(ordered: $0.element, depth: $0.offset + 1) }
        setListIndent(ps, depth: levels.count)
    }

    public static func setListIndent(_ ps: NSMutableParagraphStyle, depth: Int) {
        let d = CGFloat(depth)
        ps.firstLineHeadIndent = indentStep * (d - 1)
        ps.headIndent = indentStep * d
        ps.tabStops = depth == 0 ? NSParagraphStyle.default.tabStops
            : [NSTextTab(textAlignment: .right, location: indentStep * (d - 1) + 16), NSTextTab(textAlignment: .left, location: indentStep * d)]
    }

    // MARK: Divider

    public static func dividerString() -> NSAttributedString {
        let att = NSTextAttachment()
        att.attachmentCell = DividerCell()
        return NSAttributedString(attachment: att)
    }

    // MARK: Tables

    static func tableParagraphs(rows: [[String]], header: Bool, style: NoteStyle) -> [NoteHTML.Para] {
        let cols = max(1, rows.map(\.count).max() ?? 1)
        let table = NSTextTable()
        table.numberOfColumns = cols
        table.layoutAlgorithm = .automaticLayoutAlgorithm
        table.collapsesBorders = true
        table.hidesEmptyCells = false
        table.setContentWidth(100, type: .percentageValueType)
        table.setWidth(6, type: .absoluteValueType, for: .margin, edge: .minY)
        table.setWidth(6, type: .absoluteValueType, for: .margin, edge: .maxY)
        var out: [NoteHTML.Para] = []
        for (ri, row) in rows.enumerated() {
            for ci in 0..<cols {
                let cell = NSTextTableBlock(table: table, startingRow: ri, rowSpan: 1, startingColumn: ci, columnSpan: 1)
                cell.setWidth(1, type: .absoluteValueType, for: .border)
                cell.setBorderColor(style.rule)
                cell.setWidth(6, type: .absoluteValueType, for: .padding)
                cell.setValue(100 / CGFloat(cols), type: .percentageValueType, for: .width)
                let isHeader = header && ri == 0
                if isHeader { cell.backgroundColor = style.tableHeader }
                var attrs: [NSAttributedString.Key: Any] = [.font: NoteStyle.font(bold: isHeader)]
                if isHeader { attrs[.dlHeader] = true }
                var p = NoteHTML.Para(text: NSMutableAttributedString(string: ci < row.count ? row[ci].replacingOccurrences(of: "\n", with: " ") : "", attributes: attrs))
                p.block = "table"; p.textBlock = cell
                if isHeader { p.lineAttrs = [.dlHeader: true, .font: NoteStyle.font(bold: true)] }
                out.append(p)
            }
        }
        return out
    }

    /// A table's cell text by row and column, from its paragraphs.
    static func tableRows(_ a: NSAttributedString, _ paras: [NSRange]) -> ([[String]], Bool) {
        let ns = a.string as NSString
        var cells: [Int: [Int: String]] = [:]
        var header = false
        for r in paras {
            let at = min(r.location, max(0, a.length - 1))
            guard let cell = (a.attribute(.paragraphStyle, at: at, effectiveRange: nil) as? NSParagraphStyle)?.textBlocks.first as? NSTextTableBlock else { continue }
            let text = ns.substring(with: r).replacingOccurrences(of: "\u{2028}", with: " ").trimmingCharacters(in: .whitespacesAndNewlines)
            // Several paragraphs in one cell (Return pressed in a cell) join with a space.
            if let old = cells[cell.startingRow]?[cell.startingColumn], !old.isEmpty {
                if !text.isEmpty { cells[cell.startingRow]![cell.startingColumn] = old + " " + text }
            }
            else { cells[cell.startingRow, default: [:]][cell.startingColumn] = text }
            if cell.startingRow == 0, a.attribute(.dlHeader, at: at, effectiveRange: nil) != nil { header = true }
        }
        let rowCount = (cells.keys.max() ?? -1) + 1
        let cols = max(1, cells.values.flatMap(\.keys).max().map { $0 + 1 } ?? 1)
        let rows = (0..<max(1, rowCount)).map { r in (0..<cols).map { cells[r]?[$0] ?? "" } }
        return (rows, header)
    }

    /// The table as attributed text, every cell ending with its newline.
    public static func tableString(rows: [[String]], header: Bool, style: NoteStyle) -> NSAttributedString {
        var paras = tableParagraphs(rows: rows, header: header, style: style)
        paras.append(NoteHTML.Para(text: NSMutableAttributedString()))   // gives the last cell its newline; not part of the result
        let s = NoteHTML.assemble(paras, style: style)
        NoteHTML.applyTheme(s, style: style)
        return s
    }
}

/// Draws a divider across the text column.
final class DividerCell: NSTextAttachmentCell {
    override func cellFrame(for textContainer: NSTextContainer, proposedLineFragment lineFrag: NSRect, glyphPosition position: NSPoint, characterIndex charIndex: Int) -> NSRect {
        NSRect(x: 0, y: -4, width: max(20, lineFrag.width - position.x - textContainer.lineFragmentPadding * 2 - 2), height: 20)
    }
    override func cellSize() -> NSSize { NSSize(width: 200, height: 20) }
    override func cellBaselineOffset() -> NSPoint { NSPoint(x: 0, y: -4) }
    override func draw(withFrame cellFrame: NSRect, in controlView: NSView?) {
        let dark = controlView?.effectiveAppearance.bestMatch(from: [.aqua, .darkAqua]) == .darkAqua
        NoteStyle(dark: dark).rule.setFill()
        NSRect(x: cellFrame.minX, y: cellFrame.midY.rounded(), width: cellFrame.width, height: 1).fill()
    }
    override func wantsToTrackMouse() -> Bool { false }
}
