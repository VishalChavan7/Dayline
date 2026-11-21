import SwiftUI
import AppKit

/// Decides whether a two-finger trackpad gesture is a sideways swipe, and which way.
/// Kept apart from the view so the rules can be tested.
struct SwipeTracker {
    enum State { case idle, deciding, sideways, ignored, fired }
    private(set) var state: State = .idle
    private var dx: CGFloat = 0
    private var dy: CGFloat = 0

    /// Movement before the gesture is judged sideways or not.
    static let decideAfter: CGFloat = 8
    /// Sideways movement that counts as a swipe.
    static let fireAfter: CGFloat = 32

    mutating func begin() { state = .deciding; dx = 0; dy = 0 }
    mutating func end() { state = .idle }

    /// `mx`: finger movement, positive = to the right. Returns the direction once per gesture
    /// (`true` = right to left); vertical scrolling is left alone.
    mutating func move(mx: CGFloat, my: CGFloat) -> Bool? {
        guard state == .deciding || state == .sideways else { return nil }
        dx += mx; dy += abs(my)
        if state == .deciding {
            guard abs(dx) + dy >= Self.decideAfter else { return nil }
            state = abs(dx) >= dy ? .sideways : .ignored
            if state == .ignored { return nil }
        }
        guard abs(dx) >= Self.fireAfter else { return nil }
        state = .fired
        return dx < 0
    }
}

/// Reports a two-finger sideways swipe on the trackpad anywhere over the view it sits behind,
/// including over the note editor. `true` = fingers moved right to left. One report per swipe;
/// vertical scrolling and views that scroll sideways themselves (a row of chips) are left alone.
struct SwipeCatcher: NSViewRepresentable {
    var onSwipe: (_ leftward: Bool) -> Void

    func makeNSView(context: Context) -> CatcherView {
        let v = CatcherView()
        v.onSwipe = onSwipe
        return v
    }

    func updateNSView(_ v: CatcherView, context: Context) { v.onSwipe = onSwipe }

    final class CatcherView: NSView {
        var onSwipe: ((Bool) -> Void)?
        private var monitor: Any?
        private var tracker = SwipeTracker()

        override func hitTest(_ point: NSPoint) -> NSView? { nil }   // never takes clicks

        override func viewDidMoveToWindow() {
            super.viewDidMoveToWindow()
            if window != nil, monitor == nil {
                monitor = NSEvent.addLocalMonitorForEvents(matching: .scrollWheel) { [weak self] e in
                    self?.handle(e)
                    return e
                }
            } else if window == nil, let m = monitor {
                NSEvent.removeMonitor(m)
                monitor = nil
            }
        }

        deinit { if let monitor { NSEvent.removeMonitor(monitor) } }

        func handle(_ e: NSEvent) {
            guard let window, e.window === window, !isHiddenOrHasHiddenAncestor else { return }
            // Momentum after the fingers lift never starts or continues a swipe.
            guard e.momentumPhase.isEmpty else { tracker.end(); return }
            if e.phase.contains(.mayBegin) || e.phase.contains(.began) {
                let p = convert(e.locationInWindow, from: nil)
                if bounds.contains(p) && !scrollsSideways(at: e.locationInWindow) { tracker.begin() } else { tracker.end() }
            }
            if e.phase.contains(.ended) || e.phase.contains(.cancelled) { tracker.end(); return }
            // Finger movement: positive = to the right, with natural scrolling on or off.
            let mx = e.isDirectionInvertedFromDevice ? e.scrollingDeltaX : -e.scrollingDeltaX
            let my = e.scrollingDeltaY
            if let leftward = tracker.move(mx: mx, my: my) { onSwipe?(leftward) }
        }

        /// True over a view that scrolls sideways itself (content wider than its frame).
        /// The note editor never does, so swipes over the text always work.
        private func scrollsSideways(at point: NSPoint) -> Bool {
            var v = window?.contentView?.hitTest(point)
            while let view = v {
                if let s = view as? NSScrollView, let doc = s.documentView, !(doc is NSTextView),
                   doc.frame.width > s.contentView.bounds.width + 8 { return true }
                v = view.superview
            }
            return false
        }
    }
}
